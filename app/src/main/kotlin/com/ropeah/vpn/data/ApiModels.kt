package com.ropeah.vpn.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the V2EON backend API (see HANDOFF §4.1). Names mirror the JSON
 * exactly; unknown fields are ignored by the shared [kotlinx.serialization.Json].
 */

@Serializable
data class LoginRequest(
    val username: String,
    val pin: String? = null,
)

@Serializable
data class LoginResponse(
    val ok: Boolean = true,
    val token: String,
    val username: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("server_time") val serverTime: Long,
    @SerialName("config_version") val configVersion: Int = 0,
)

@Serializable
data class ApiServerDto(
    val id: String? = null,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val method: String? = null,
    val security: String? = null,
    val network: String? = null,
    val path: String? = null,
    val host: String? = null,
    val sni: String? = null,
    val uri: String? = null,
    @SerialName("country") val countryCode: String? = null,
    val countryName: String? = null,
    val flag: String? = null,
    val city: String? = null,
)

@Serializable
data class ConfigResponse(
    val ok: Boolean = true,
    val version: Int,
    @SerialName("pushed_at") val pushedAt: Long? = null,
    val servers: List<ApiServerDto> = emptyList(),
    val uris: List<String> = emptyList(),
)

@Serializable
data class PingRequest(
    @SerialName("config_version") val configVersion: Int,
    val connected: Boolean,
    @SerialName("active_server_id") val activeServerId: String? = null,
    @SerialName("up_delta") val upDelta: Long = 0,
    @SerialName("down_delta") val downDelta: Long = 0,
    @SerialName("latency_ms") val latencyMs: Long? = null,
)

@Serializable
data class PingResponse(
    val ok: Boolean = true,
    val revoked: Boolean = false,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("server_time") val serverTime: Long? = null,
    @SerialName("config_version") val configVersion: Int = 0,
    @SerialName("data_used") val dataUsed: Long = 0,
    @SerialName("data_limit_mb") val dataLimitMb: Long? = null,
)

@Serializable
data class LogoutResponse(val ok: Boolean = true)

@Serializable
data class ApiErrorBody(
    val ok: Boolean = false,
    val error: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("server_time") val serverTime: Long? = null,
)

/**
 * Local, server-anchored login session.
 *
 * [serverOffsetMs] is the last observed (server time − device time). [maxEffectiveSeenMs]
 * is a high-water mark of server time: effective time may never travel backwards, so
 * rolling the device clock back cannot revive an expired account.
 */
data class SessionState(
    val username: String,
    val token: String,
    val expiresAtMs: Long,
    val serverOffsetMs: Long,
    /** Last version the server told us about; used as the ping body's config_version. */
    val configVersion: Int,
    val lastValidatedMs: Long,
    val lastSyncMs: Long,
    val maxEffectiveSeenMs: Long = 0L,
    /** Last version actually pushed into ServerRepository. 0 = nothing applied yet. */
    val appliedConfigVersion: Int = 0,
    /** Backend reported the account as suspended/revoked (403 with a future expiry). */
    val suspended: Boolean = false,
) {
    /**
     * Effective server time. Monotonic within a session: it can lag a rolled-forward
     * device clock but never falls behind the highest server time already observed.
     */
    fun effectiveNow(deviceNowMs: Long): Long =
        maxOf(deviceNowMs + serverOffsetMs, maxEffectiveSeenMs)

    fun remainingMs(deviceNowMs: Long): Long = expiresAtMs - effectiveNow(deviceNowMs)
}
