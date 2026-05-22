package com.ropeah.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ropeah.vpn.data.ConfigParser
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.data.ServerRepository
import com.ropeah.vpn.vpn.RopeahVpnService
import com.ropeah.vpn.vpn.TrafficSample
import com.ropeah.vpn.vpn.VpnStateBus
import com.ropeah.vpn.vpn.VpnStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServerRepository(app)

    val servers: StateFlow<List<ServerConfig>> = repo.servers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val activeServer: StateFlow<ServerConfig?> = repo.activeServer.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val status: StateFlow<VpnStatus> = VpnStateBus.status
    val traffic: StateFlow<TrafficSample?> = VpnStateBus.traffic
    val connectedSince: StateFlow<Long?> = VpnStateBus.connectedSince

    fun selectServer(server: ServerConfig) {
        viewModelScope.launch { repo.setActive(server.id) }
    }

    fun deleteServer(server: ServerConfig) {
        viewModelScope.launch { repo.delete(server.id) }
    }

    fun restoreServer(server: ServerConfig, index: Int) {
        viewModelScope.launch { repo.restore(server, index) }
    }

    fun addFromUri(uri: String): Boolean = importFromUri(uri) != null

    /** Returns the imported server's display name, or null if the URI couldn't be parsed. */
    fun importFromUri(uri: String): String? {
        val parsed = ConfigParser.parse(uri) ?: return null
        viewModelScope.launch { repo.add(parsed) }
        return parsed.name
    }

    fun startVpn(context: Context) {
        val intent = Intent(context, RopeahVpnService::class.java).apply {
            action = RopeahVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopVpn(context: Context) {
        val intent = Intent(context, RopeahVpnService::class.java).apply {
            action = RopeahVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}
