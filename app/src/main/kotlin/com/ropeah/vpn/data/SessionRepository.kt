package com.ropeah.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "ropeah_session")

/**
 * Persists the managed-mode session. Expiry is anchored to server time via
 * [SessionState.serverOffsetMs] plus a monotonic high-water mark, so changing
 * the device clock cannot extend access.
 *
 * Writes that follow a network response take the responding session's token so
 * a slow reply from a previous session can never overwrite a newer one.
 */
class SessionRepository(private val context: Context) {

    private val keyUsername = stringPreferencesKey("username")
    private val keyToken = stringPreferencesKey("token")
    private val keyExpiresAt = longPreferencesKey("expires_at_ms")
    private val keyServerOffset = longPreferencesKey("server_offset_ms")
    private val keyConfigVersion = intPreferencesKey("config_version")
    private val keyAppliedVersion = intPreferencesKey("applied_config_version")
    private val keyLastValidated = longPreferencesKey("last_validated_ms")
    private val keyLastSync = longPreferencesKey("last_sync_ms")
    private val keyMaxEffectiveSeen = longPreferencesKey("max_effective_seen_ms")
    private val keySuspended = booleanPreferencesKey("suspended")

    val session: Flow<SessionState?> = context.sessionStore.data.map { prefs ->
        val token = prefs[keyToken].orEmpty()
        val username = prefs[keyUsername].orEmpty()
        if (token.isBlank() || username.isBlank()) {
            null
        } else {
            SessionState(
                username = username,
                token = token,
                expiresAtMs = prefs[keyExpiresAt] ?: 0L,
                serverOffsetMs = prefs[keyServerOffset] ?: 0L,
                configVersion = prefs[keyConfigVersion] ?: 0,
                lastValidatedMs = prefs[keyLastValidated] ?: 0L,
                lastSyncMs = prefs[keyLastSync] ?: 0L,
                maxEffectiveSeenMs = prefs[keyMaxEffectiveSeen] ?: 0L,
                appliedConfigVersion = prefs[keyAppliedVersion] ?: 0,
                suspended = prefs[keySuspended] ?: false,
            )
        }
    }

    /** One-shot read for services and workers. */
    suspend fun readOnce(): SessionState? = session.first()

    suspend fun save(state: SessionState) {
        context.sessionStore.edit { prefs ->
            prefs[keyUsername] = state.username
            prefs[keyToken] = state.token
            prefs[keyExpiresAt] = state.expiresAtMs
            prefs[keyServerOffset] = state.serverOffsetMs
            prefs[keyConfigVersion] = state.configVersion
            prefs[keyAppliedVersion] = state.appliedConfigVersion
            prefs[keyLastValidated] = state.lastValidatedMs
            prefs[keyLastSync] = state.lastSyncMs
            prefs[keyMaxEffectiveSeen] = state.maxEffectiveSeenMs
            prefs[keySuspended] = state.suspended
        }
    }

    /**
     * Applies an authoritative server clock reading from a *successful* call:
     * refreshes the offset/watermark, expiry and seen version, and marks the
     * session validated and active.
     */
    suspend fun updateFromServer(
        serverTime: Long,
        expiresAt: Long?,
        configVersion: Int?,
        deviceNow: Long,
        expectedToken: String? = null,
    ) {
        context.sessionStore.edit { prefs ->
            if (!owns(prefs, expectedToken)) return@edit
            prefs[keyServerOffset] = serverTime - deviceNow
            prefs[keyMaxEffectiveSeen] = maxOf(prefs[keyMaxEffectiveSeen] ?: 0L, serverTime)
            if (expiresAt != null) prefs[keyExpiresAt] = expiresAt
            if (configVersion != null && configVersion > (prefs[keyConfigVersion] ?: 0)) {
                prefs[keyConfigVersion] = configVersion
            }
            prefs[keyLastValidated] = deviceNow
            prefs[keySuspended] = false
        }
    }

    /**
     * Records server time from a *denied* call (403 suspended/expired). It must
     * not refresh [SessionState.lastValidatedMs], otherwise the offline grace
     * window would be re-armed by the very response that denied access.
     */
    suspend fun applyServerClock(
        serverTime: Long,
        expiresAt: Long?,
        deviceNow: Long,
        expectedToken: String? = null,
        suspended: Boolean? = null,
    ) {
        context.sessionStore.edit { prefs ->
            if (!owns(prefs, expectedToken)) return@edit
            prefs[keyServerOffset] = serverTime - deviceNow
            prefs[keyMaxEffectiveSeen] = maxOf(prefs[keyMaxEffectiveSeen] ?: 0L, serverTime)
            if (expiresAt != null) prefs[keyExpiresAt] = expiresAt
            if (suspended != null) prefs[keySuspended] = suspended
        }
    }

    /** Marks the account suspended (403 while the expiry is still in the future). */
    suspend fun markSuspended(expectedToken: String? = null) {
        context.sessionStore.edit { prefs ->
            if (!owns(prefs, expectedToken)) return@edit
            prefs[keySuspended] = true
        }
    }

    /** Records a successful config pull that actually replaced the stored list. */
    suspend fun markApplied(version: Int, deviceNow: Long, expectedToken: String? = null) {
        context.sessionStore.edit { prefs ->
            if (!owns(prefs, expectedToken)) return@edit
            prefs[keyAppliedVersion] = version
            prefs[keyConfigVersion] = maxOf(prefs[keyConfigVersion] ?: 0, version)
            prefs[keyLastSync] = deviceNow
            prefs[keyLastValidated] = deviceNow
        }
    }

    /** Records a successful config pull that had nothing new to apply. */
    suspend fun markChecked(version: Int, deviceNow: Long, expectedToken: String? = null) {
        context.sessionStore.edit { prefs ->
            if (!owns(prefs, expectedToken)) return@edit
            prefs[keyConfigVersion] = maxOf(prefs[keyConfigVersion] ?: 0, version)
            prefs[keyLastSync] = deviceNow
            prefs[keyLastValidated] = deviceNow
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }

    private fun owns(
        prefs: androidx.datastore.preferences.core.Preferences,
        expectedToken: String?,
    ): Boolean = SessionWriteGuard.appliesTo(prefs[keyToken], expectedToken)
}
