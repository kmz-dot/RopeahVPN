package com.ropeah.vpn.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

interface Tun2SocksBridge {
    fun start(tunFd: ParcelFileDescriptor, socksPort: Int): Boolean
    fun stop()
    val isRunning: Boolean

    companion object {
        fun create(context: Context): Tun2SocksBridge = HevTun2SocksBridge(context)
    }
}

private class HevTun2SocksBridge(private val context: Context) : Tun2SocksBridge {

    @Volatile private var running = false

    override val isRunning: Boolean get() = running

    override fun start(tunFd: ParcelFileDescriptor, socksPort: Int): Boolean {
        if (running) return true
        val confFile = File(context.cacheDir, "tproxy.conf")
        val yaml = buildYamlConfig(socksPort)
        runCatching {
            confFile.writeText(yaml)
            Log.i(TAG, "starting tun2socks: fd=${tunFd.fd} socksPort=$socksPort conf=${confFile.absolutePath}")
            Log.d(TAG, "tproxy yaml:\n$yaml")
            Tun2SocksNative.TProxyStartService(confFile.absolutePath, tunFd.fd)
            running = true
            Log.i(TAG, "tun2socks started")
            return true
        }.onFailure {
            Log.e(TAG, "TProxyStartService failed (conf=${confFile.absolutePath})", it)
        }
        return false
    }

    override fun stop() {
        if (!running) return
        runCatching { Tun2SocksNative.TProxyStopService() }
            .onFailure { Log.e(TAG, "TProxyStopService failed", it) }
        running = false
    }

    private fun buildYamlConfig(socksPort: Int): String = """
        |misc:
        |  task-stack-size: 81920
        |tunnel:
        |  mtu: $TUNNEL_MTU
        |socks5:
        |  port: $socksPort
        |  address: '127.0.0.1'
        |  udp: 'udp'
        |""".trimMargin()

    private companion object {
        const val TAG = "Tun2SocksBridge"
        const val TUNNEL_MTU = 1500
    }
}
