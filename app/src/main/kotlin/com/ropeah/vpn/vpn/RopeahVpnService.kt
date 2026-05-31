package com.ropeah.vpn.vpn

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ropeah.vpn.data.AppSettings
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.data.ServerRepository
import com.ropeah.vpn.data.SettingsRepository
import com.ropeah.vpn.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RopeahVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
    private val xray by lazy { XrayController.create(applicationContext) }
    private val tun2socks by lazy { Tun2SocksBridge.create(applicationContext) }
    private var startJob: Job? = null
    private var statsJob: Job? = null
    private var activeServerName: String = "—"
    private var connectedSinceMs: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (startJob?.isActive == true) return
        VpnStateBus.set(VpnStatus.Connecting)

        val notif = NotificationHelper.buildOngoing(this, serverName = "—")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID, notif)
        }

        startJob = scope.launch {
            Log.i(TAG, "startVpn: pkg=$packageName uid=${android.os.Process.myUid()}")
            val repo = ServerRepository(applicationContext)
            val server = repo.activeServer.first()
            val settingsRepo = SettingsRepository(applicationContext)
            val settings = settingsRepo.readOnce()
            if (server == null) {
                Log.w(TAG, "no active server; refusing to connect")
                stopSelfSafely()
                return@launch
            }
            Log.i(TAG, "active server: name=${server.name} proto=${server.protocol} " +
                "addr=${server.address}:${server.port}")

            val fd = buildTun(server, settings) ?: run {
                Log.w(TAG, "VpnService.Builder returned null fd")
                stopSelfSafely()
                return@launch
            }
            tunFd = fd
            Log.i(TAG, "tun established: fd=${fd.fd} mtu=${settings.mtu} addr=10.0.0.2/32 route=0.0.0.0/0 " +
                "dns=${settings.dnsPrimary},${settings.dnsSecondary} disallowedApp=$packageName")

            val socksPort = LOCAL_SOCKS_PORT
            if (!xray.start(server, socksPort, settings.logLevel.value)) {
                Log.w(TAG, "Xray start failed")
                stopSelfSafely()
                return@launch
            }
            Log.i(TAG, "xray running at 127.0.0.1:$socksPort")

            if (!tun2socks.start(fd, socksPort)) {
                Log.w(TAG, "tun2socks start failed")
                xray.stop()
                stopSelfSafely()
                return@launch
            }
            Log.i(TAG, "tun2socks bridging tun → 127.0.0.1:$socksPort — connection up")

            NotificationHelper.updateOngoing(this@RopeahVpnService, server.name)
            activeServerName = server.name
            connectedSinceMs = System.currentTimeMillis()
            VpnStateBus.set(VpnStatus.Connected)
            statsJob = launchStatsLoop(settings)
        }
    }

    private fun launchStatsLoop(settings: AppSettings): Job = scope.launch {
        var totalUp = 0L
        var totalDown = 0L
        var lastLatency = TrafficSample.LATENCY_UNKNOWN
        var lastLatencyAt = 0L
        var lastNotifAt = 0L
        while (isActive) {
            val up = xray.queryStats(PROXY_TAG, "uplink")
            val down = xray.queryStats(PROXY_TAG, "downlink")
            totalUp += up
            totalDown += down

            val now = System.currentTimeMillis()
            if (now - lastLatencyAt > LATENCY_INTERVAL_MS) {
                lastLatency = xray.measureDelay(settings.latencyProbeUrl)
                lastLatencyAt = now
            }

            VpnStateBus.publishTraffic(
                TrafficSample(
                    uplinkBps = up,
                    downlinkBps = down,
                    totalUp = totalUp,
                    totalDown = totalDown,
                    latencyMs = lastLatency,
                    timestampMs = now,
                )
            )

            if (now - lastNotifAt >= NOTIF_REFRESH_MS) {
                NotificationHelper.updateOngoing(
                    context = this@RopeahVpnService,
                    serverName = activeServerName,
                    elapsedMs = now - connectedSinceMs,
                    latencyMs = lastLatency,
                )
                lastNotifAt = now
            }

            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun buildTun(server: ServerConfig, settings: AppSettings): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(server.name)
            .setMtu(settings.mtu)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
        if (settings.useCustomDns) {
            builder.addDnsServer(settings.dnsPrimary)
            builder.addDnsServer(settings.dnsSecondary)
        } else {
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
        }
        if (settings.killSwitch) {
            builder.setBlocking(true)
        }
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            Log.e(TAG, "addDisallowedApplication($packageName) FAILED — VPN will loop", e)
            return null
        }
        return builder.establish()
    }

    private fun stopVpn() {
        statsJob?.cancel()
        statsJob = null
        startJob?.cancel()
        tun2socks.stop()
        xray.stop()
        tunFd?.close()
        tunFd = null
        VpnStateBus.set(VpnStatus.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSelfSafely() {
        VpnStateBus.set(VpnStatus.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ropeah.vpn.action.START"
        const val ACTION_STOP = "com.ropeah.vpn.action.STOP"
        private const val TAG = "RopeahVpnService"
        private const val LOCAL_SOCKS_PORT = 10808
        private const val PROXY_TAG = "proxy"
        private const val SAMPLE_INTERVAL_MS = 1000L
        private const val LATENCY_INTERVAL_MS = 10_000L
        private const val NOTIF_REFRESH_MS = 5_000L
    }
}
