package com.ropeah.vpn.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ropeah.vpn.R
import com.ropeah.vpn.data.ApiClient
import com.ropeah.vpn.data.ApiException
import com.ropeah.vpn.data.ConfigSync
import com.ropeah.vpn.data.PingRequest
import com.ropeah.vpn.data.ServerRepository
import com.ropeah.vpn.data.SessionRepository
import com.ropeah.vpn.data.SessionState
import com.ropeah.vpn.data.SyncResult
import com.ropeah.vpn.vpn.RopeahVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val expiredAtMs: Long? = null,
)

class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionRepo = SessionRepository(app)
    private val serverRepo = ServerRepository(app)
    private val api = ApiClient()
    private val configSync = ConfigSync(sessionRepo, serverRepo, api)

    val session: StateFlow<SessionState?> = sessionRepo.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val _ready = MutableStateFlow(false)

    /** False until the first DataStore emission, so the UI doesn't flash the login gate. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    private val _checkingAccess = MutableStateFlow(false)
    val checkingAccess: StateFlow<Boolean> = _checkingAccess

    /** Manual "check again" from the expired lock screen: ping, then update expiry. */
    fun revalidateNow() {
        viewModelScope.launch {
            if (_checkingAccess.value) return@launch
            val current = sessionRepo.readOnce() ?: return@launch
            _checkingAccess.value = true
            val deviceNow = System.currentTimeMillis()
            try {
                val response = api.ping(
                    token = current.token,
                    body = PingRequest(configVersion = current.configVersion, connected = false),
                )
                if (response.revoked) {
                    sessionExpiredLocally()
                } else {
                    sessionRepo.updateFromServer(
                        serverTime = response.serverTime ?: (deviceNow + current.serverOffsetMs),
                        expiresAt = response.expiresAt,
                        configVersion = response.configVersion,
                        deviceNow = deviceNow,
                    )
                }
            } catch (e: ApiException.Unauthorized) {
                sessionExpiredLocally()
            } catch (e: ApiException.Forbidden) {
                val serverTime = e.serverTime
                if (serverTime != null) {
                    sessionRepo.updateFromServer(serverTime, e.expiresAt, null, deviceNow)
                }
            } catch (e: ApiException) {
                _syncMessage.value =
                    getApplication<Application>().getString(R.string.expired_check_failed)
            } finally {
                _checkingAccess.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            sessionRepo.session.collect { _ready.value = true }
        }
    }

    fun login(username: String, pin: String) {
        if (_loginState.value.loading) return
        val name = username.trim()
        if (name.isEmpty()) {
            _loginState.value = LoginUiState(error = getApplication<Application>().getString(R.string.login_username_required))
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginUiState(loading = true)
            val deviceNow = System.currentTimeMillis()
            try {
                val response = api.login(name, pin.trim().takeIf { it.isNotEmpty() })
                val state = SessionState(
                    username = response.username,
                    token = response.token,
                    expiresAtMs = response.expiresAt,
                    serverOffsetMs = response.serverTime - deviceNow,
                    configVersion = response.configVersion,
                    lastValidatedMs = deviceNow,
                    lastSyncMs = 0L,
                )
                sessionRepo.save(state)
                _loginState.value = LoginUiState()
                // Best effort: a failed pull must not invalidate a successful login.
                runSync(state, force = false)
            } catch (e: ApiException.Forbidden) {
                _loginState.value = LoginUiState(
                    error = e.message ?: "Account is not active",
                    expiredAtMs = e.expiresAt,
                )
            } catch (e: ApiException) {
                _loginState.value = LoginUiState(error = e.message ?: "Login failed")
            }
        }
    }

    fun clearLoginError() {
        if (_loginState.value.error != null) _loginState.value = LoginUiState()
    }

    fun syncNow() {
        viewModelScope.launch {
            val current = sessionRepo.readOnce() ?: return@launch
            runSync(current, force = true)
        }
    }

    /** Called on app resume: refresh the pushed list when it is older than [maxAgeMs]. */
    fun syncIfStale(maxAgeMs: Long = 60_000L) {
        viewModelScope.launch {
            val current = sessionRepo.readOnce() ?: return@launch
            val age = System.currentTimeMillis() - current.lastSyncMs
            if (current.configVersion > 0 && age in 0 until maxAgeMs) return@launch
            runSync(current, force = false)
        }
    }

    private suspend fun runSync(session: SessionState, force: Boolean) {
        if (_syncing.value) return
        _syncing.value = true
        _syncMessage.value = null
        val result = configSync.sync(session, force)
        _syncMessage.value = when (result) {
            is SyncResult.Updated ->
                getApplication<Application>().getString(R.string.servers_synced, result.version, result.serverCount)
            is SyncResult.Unchanged ->
                getApplication<Application>().getString(R.string.servers_sync_unchanged, result.version)
            is SyncResult.Failed ->
                getApplication<Application>().getString(R.string.servers_sync_failed, result.message)
        }
        if (result is SyncResult.Failed && result.exception is ApiException.Unauthorized) {
            sessionExpiredLocally()
        }
        _syncing.value = false
    }

    /** 401 on any endpoint means the session is dead: drop the tunnel and lock. */
    suspend fun sessionExpiredLocally() {
        stopVpn()
        sessionRepo.clear()
    }

    fun logout() {
        viewModelScope.launch {
            val current = sessionRepo.readOnce()
            stopVpn()
            if (current != null) {
                runCatching { api.logout(current.token) }
            }
            sessionRepo.clear()
            serverRepo.clearAll()
            _loginState.value = LoginUiState()
            _syncMessage.value = null
        }
    }

    fun stopVpn() {
        val context = getApplication<Application>()
        val intent = Intent(context, RopeahVpnService::class.java).apply {
            action = RopeahVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}
