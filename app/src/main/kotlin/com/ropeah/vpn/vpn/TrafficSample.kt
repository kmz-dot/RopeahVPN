package com.ropeah.vpn.vpn

data class TrafficSample(
    val uplinkBps: Long,
    val downlinkBps: Long,
    val totalUp: Long,
    val totalDown: Long,
    val latencyMs: Long,
    val timestampMs: Long,
) {
    companion object {
        const val LATENCY_UNKNOWN = -1L
    }
}
