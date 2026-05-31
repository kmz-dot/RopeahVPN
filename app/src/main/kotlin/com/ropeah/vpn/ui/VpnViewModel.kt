package com.ropeah.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ropeah.vpn.data.AppSettings
import com.ropeah.vpn.data.ConfigParser
import com.ropeah.vpn.data.IpScanner
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.data.ServerRepository
import com.ropeah.vpn.data.SettingsRepository
import com.ropeah.vpn.vpn.RopeahVpnService
import com.ropeah.vpn.vpn.TrafficSample
import com.ropeah.vpn.vpn.VpnStateBus
import com.ropeah.vpn.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import com.ropeah.vpn.data.IpScanner.ScanProgress
import com.ropeah.vpn.data.IpScanner.ScanIpResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

class VpnViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServerRepository(app)
    private val settingsRepo = SettingsRepository(app)

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

    val appSettings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    /** Measured TCP ping for each server, keyed by server id. -1 means timeout/timeout. */
    private val _latencyMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val latencyMap: StateFlow<Map<String, Long>> = _latencyMap

    /** Whether a ping is currently in progress for a given server id. */
    private val _pinging = MutableStateFlow<Set<String>>(emptySet())
    val pinging: StateFlow<Set<String>> = _pinging

    // ── IP Scanner state ────────────────────────────────────────────────

    sealed class IpScanState {
        data object Idle : IpScanState()
        data class Running(val progress: ScanProgress) : IpScanState()
        data class Completed(
            val results: List<ScanIpResult>,
            val totalTested: Int,
        ) : IpScanState()
        data class Error(val message: String) : IpScanState()
    }

    private val _ipScanState = MutableStateFlow<IpScanState>(IpScanState.Idle)
    val ipScanState: StateFlow<IpScanState> = _ipScanState

    private var scanJob: Job? = null

    /** Start scanning [ips] using [baseConfig] as the template. */
    fun startIpScan(baseConfig: ServerConfig, ips: List<String>) {
        if (scanJob?.isActive == true) return
        val probeUrl = appSettings.value.latencyProbeUrl
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val scanner = IpScanner(getApplication())
            try {
                scanner.scan(
                    baseConfig = baseConfig,
                    ips = ips,
                    probeUrl = probeUrl,
                    onProgress = { progress ->
                        _ipScanState.value = IpScanState.Running(progress)
                    },
                )
                // After scan completes (not cancelled), emit completed state.
                val final = _ipScanState.value
                if (final is IpScanState.Running) {
                    _ipScanState.value = IpScanState.Completed(
                        results = final.progress.results,
                        totalTested = ips.size,
                    )
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                _ipScanState.value = IpScanState.Idle
            } catch (e: Exception) {
                _ipScanState.value = IpScanState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Cancel an ongoing IP scan. */
    fun cancelIpScan() {
        scanJob?.cancel()
        scanJob = null
        _ipScanState.value = IpScanState.Idle
    }

    /** Reset scan state back to idle (e.g. after viewing results). */
    fun resetIpScan() {
        _ipScanState.value = IpScanState.Idle
    }

    // ── End IP Scanner state ────────────────────────────────────────────

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

    fun updateSettings(block: AppSettings.() -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(block) }
    }

    /** Measure TCP ping latency to a server address:port. */
    fun measureLatency(server: ServerConfig) {
        viewModelScope.launch {
            if (server.id in _pinging.value) return@launch
            _pinging.value = _pinging.value + server.id
            val ms = tcpPing(server.address, server.port)
            _latencyMap.value = _latencyMap.value + (server.id to ms)
            _pinging.value = _pinging.value - server.id
        }
    }

    /** Ping all servers and update the latency map. */
    fun measureAllLatencies(servers: List<ServerConfig>) {
        servers.forEach { server ->
            measureLatency(server)
        }
    }

    private suspend fun tcpPing(address: String, port: Int, timeoutMs: Long = 3000L): Long {
        val start = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                withTimeoutOrNull(timeoutMs) {
                    socket.connect(InetSocketAddress(address, port), timeoutMs.toInt())
                    System.currentTimeMillis() - start
                } ?: -1L
            } catch (_: Exception) {
                -1L
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }
}
