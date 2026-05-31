package com.ropeah.vpn.data

import android.content.Context
import com.ropeah.vpn.vpn.TrafficSample
import com.ropeah.vpn.vpn.XrayController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Scans a list of IPs by substituting each into a base [ServerConfig] and
 * performing a full proxy test: start Xray, wait for the tunnel, then measure
 * end-to-end latency against the configured probe URL.
 *
 * Results are streamed through [onProgress] callbacks so the UI can display
 * live progress and incremental results.
 */
class IpScanner(private val context: Context) {

    data class ScanProgress(
        val current: Int,
        val total: Int,
        val currentIp: String,
        val successCount: Int,
        val failCount: Int,
        val results: List<ScanIpResult>,
    )

    data class ScanIpResult(
        val ip: String,
        val success: Boolean,
        val latencyMs: Long,
    )

    /**
     * Runs the scan. [baseConfig] is the template config whose `address` gets
     * replaced with each IP. [ips] is the list of IPs to test.
     *
     * The function is **cancellable** — if the calling coroutine is cancelled
     * (e.g. user taps "Cancel"), the scan stops and outstanding resources are
     * cleaned up in the finally block.
     */
    suspend fun scan(
        baseConfig: ServerConfig,
        ips: List<String>,
        probeUrl: String = DEFAULT_PROBE_URL,
        xrayConnectWaitMs: Long = 3_000L,
        onProgress: (ScanProgress) -> Unit,
    ): List<ScanIpResult> {
        val xray = XrayController.create(context)
        val results = mutableListOf<ScanIpResult>()
        var successCount = 0
        var failCount = 0

        try {
            for ((index, ip) in ips.withIndex()) {
                // Respect cancellation so the UI can abort mid-scan
                coroutineContext.ensureActive()

                // Report initial "connecting" state for this IP
                onProgress(
                    ScanProgress(
                        current = index + 1,
                        total = ips.size,
                        currentIp = ip,
                        successCount = successCount,
                        failCount = failCount,
                        results = results.toList(),
                    )
                )

                val modifiedConfig = baseConfig.copy(address = ip)

                // Gracefully stop any previous Xray session
                withTimeoutOrNull(STOP_TIMEOUT_MS) { xray.stop() }
                delay(400L) // Brief cooldown between stop → start

                val started = withTimeoutOrNull(START_TIMEOUT_MS) {
                    xray.start(modifiedConfig, SCAN_SOCKS_PORT, "error")
                } ?: false

                if (started) {
                    // Let the tunnel establish
                    delay(xrayConnectWaitMs)

                    val latency = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                        xray.measureDelay(probeUrl)
                    } ?: TrafficSample.LATENCY_UNKNOWN

                    val success = latency != TrafficSample.LATENCY_UNKNOWN && latency >= 0
                    val result = ScanIpResult(ip, success, latency)
                    results.add(result)
                    if (success) successCount++ else failCount++
                } else {
                    val result = ScanIpResult(ip, success = false, latencyMs = -1L)
                    results.add(result)
                    failCount++
                }

                // Report result for this IP
                onProgress(
                    ScanProgress(
                        current = index + 1,
                        total = ips.size,
                        currentIp = ip,
                        successCount = successCount,
                        failCount = failCount,
                        results = results.toList(),
                    )
                )
            }
        } catch (e: CancellationException) {
            // The caller cancelled — propagate after cleanup
            throw e
        } finally {
            // Always tear down Xray when done or cancelled
            withTimeoutOrNull(STOP_TIMEOUT_MS) { xray.stop() }
        }

        return results
    }

    companion object {
        /** SOCKS port used by Xray during scanning (does not conflict with the service's 10808). */
        const val SCAN_SOCKS_PORT = 10809
        private const val DEFAULT_PROBE_URL = "https://www.gstatic.com/generate_204"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val START_TIMEOUT_MS = 8_000L
        private const val PROBE_TIMEOUT_MS = 10_000L
    }
}
