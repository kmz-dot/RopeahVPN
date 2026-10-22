package com.ropeah.vpn.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ropeah.vpn.data.ApiClient
import com.ropeah.vpn.data.ApiException
import com.ropeah.vpn.data.ServerRepository
import com.ropeah.vpn.data.SessionRepository
import com.ropeah.vpn.data.SessionState
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

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    init {
        viewModelScope.launch {
            sessionRepo.session.collect { _ready.value = true }
        }
    }

    fun login(username: String, pin: String) {
        if (_loginState.value.loading) return
        val name = username.trim()
        if (name.isEmpty()) {
            _loginState.value = LoginUiState(error = "Enter your username")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginUiState(loading = true)
            val deviceNow = System.currentTimeMillis()
            try {
                val response = api.login(name, pin.trim().takeIf { it.isNotEmpty() })
                sessionRepo.save(
                    SessionState(
                        username = response.username,
                        token = response.token,
                        expiresAtMs = response.expiresAt,
                        serverOffsetMs = response.serverTime - deviceNow,
                        configVersion = response.configVersion,
                        lastValidatedMs = deviceNow,
                        lastSyncMs = 0L,
                    )
                )
                _loginState.value = LoginUiState()
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
        }
    }

    fun stopVpn() {
        val context = getApplication<Application>()
        val intent = Intent(context, RopeahVpnService::class.java).apply {
            action = RopeahVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun setSyncMessage(message: String?) {
        _syncMessage.value = message
    }
}
