package com.ropeah.vpn.data

import android.content.Context
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
 * [SessionState.serverOffsetMs] so changing the device clock cannot extend it.
 */
class SessionRepository(private val context: Context) {

    private val keyUsername = stringPreferencesKey("username")
    private val keyToken = stringPreferencesKey("token")
    private val keyExpiresAt = longPreferencesKey("expires_at_ms")
    private val keyServerOffset = longPreferencesKey("server_offset_ms")
    private val keyConfigVersion = intPreferencesKey("config_version")
    private val keyLastValidated = longPreferencesKey("last_validated_ms")
    private val keyLastSync = longPreferencesKey("last_sync_ms")

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
            prefs[keyLastValidated] = state.lastValidatedMs
            prefs[keyLastSync] = state.lastSyncMs
        }
    }

    /**
     * Applies an authoritative server clock reading. [expiresAt] may be null on
     * responses that do not carry it; [configVersion] is only advanced.
     */
    suspend fun updateFromServer(
        serverTime: Long,
        expiresAt: Long?,
        configVersion: Int?,
        deviceNow: Long,
    ) {
        context.sessionStore.edit { prefs ->
            if (prefs[keyToken].isNullOrBlank()) return@edit
            prefs[keyServerOffset] = serverTime - deviceNow
            if (expiresAt != null) prefs[keyExpiresAt] = expiresAt
            if (configVersion != null && configVersion > (prefs[keyConfigVersion] ?: 0)) {
                prefs[keyConfigVersion] = configVersion
            }
            prefs[keyLastValidated] = deviceNow
        }
    }

    /** Records a successful config pull without touching the expiry anchor. */
    suspend fun markSynced(version: Int, deviceNow: Long) {
        context.sessionStore.edit { prefs ->
            if (prefs[keyToken].isNullOrBlank()) return@edit
            prefs[keyConfigVersion] = version
            prefs[keyLastSync] = deviceNow
            prefs[keyLastValidated] = deviceNow
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }
}
