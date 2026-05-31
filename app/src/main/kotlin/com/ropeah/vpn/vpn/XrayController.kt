package com.ropeah.vpn.vpn

import android.content.Context
import android.util.Log
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.data.XrayConfigBuilder
import kotlinx.serialization.json.Json
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

interface XrayController {
    fun start(server: ServerConfig, socksPort: Int, logLevel: String = "debug"): Boolean
    fun stop()
    fun queryStats(tag: String, direction: String): Long
    fun measureDelay(url: String): Long
    val isRunning: Boolean

    companion object {
        @Volatile
        private var instance: XrayController? = null

        fun create(context: Context): XrayController {
            return instance ?: synchronized(this) {
                instance ?: LibV2rayController(context).also { instance = it }
            }
        }
    }
}

private class LibV2rayController(context: Context) : XrayController {

    private val controller: CoreController
    private val json = Json { prettyPrint = false }

    init {
        // Xray reads geoip.dat / geosite.dat from this path when present.
        // The scaffold doesn't ship the dat files yet; an empty filesDir is fine
        // (routing rules that reference geoip/geosite will degrade gracefully).
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
        controller = Libv2ray.newCoreController(LoggingCallback)
    }

    override val isRunning: Boolean
        get() = controller.isRunning

    override fun start(server: ServerConfig, socksPort: Int, logLevel: String): Boolean {
        val configJson = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            XrayConfigBuilder.build(server, socksPort, logLevel),
        )
        Log.i(TAG, "starting xray: protocol=${server.protocol} address=${server.address}:${server.port} " +
            "network=${server.network ?: "tcp"} security=${server.security ?: "none"} sni=${server.sni ?: "-"}")
        Log.d(TAG, "xray config (redacted): " + redact(configJson))
        return runCatching {
            controller.startLoop(configJson, 0)
            Log.i(TAG, "xray startLoop returned (running=${controller.isRunning})")
            true
        }.onFailure { Log.e(TAG, "startLoop threw", it) }.getOrDefault(false)
    }

    private fun redact(s: String): String = s
        .replace(Regex("\"id\"\\s*:\\s*\"[^\"]*\""), "\"id\":\"<redacted>\"")
        .replace(Regex("\"password\"\\s*:\\s*\"[^\"]*\""), "\"password\":\"<redacted>\"")

    override fun stop() {
        runCatching { controller.stopLoop() }
            .onFailure { Log.e(TAG, "stopLoop failed", it) }
    }

    override fun queryStats(tag: String, direction: String): Long =
        runCatching { controller.queryStats(tag, direction) }.getOrDefault(0L)

    override fun measureDelay(url: String): Long =
        runCatching { controller.measureDelay(url) }.getOrDefault(TrafficSample.LATENCY_UNKNOWN)

    private companion object {
        const val TAG = "XrayController"
    }
}

private object LoggingCallback : CoreCallbackHandler {
    override fun startup(): Long = 0
    override fun shutdown(): Long = 0
    override fun onEmitStatus(code: Long, msg: String?): Long {
        Log.i("XrayCallback", "status=$code msg=$msg")
        return 0
    }
}
