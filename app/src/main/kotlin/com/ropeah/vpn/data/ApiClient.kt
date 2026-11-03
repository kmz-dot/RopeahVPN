package com.ropeah.vpn.data

import android.util.Log
import com.ropeah.vpn.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Typed failures so callers can distinguish auth/expiry/transport problems. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(message: String = "Invalid session") : ApiException(message)
    class Forbidden(
        val expiresAt: Long?,
        val serverTime: Long?,
        message: String = "Account not active",
    ) : ApiException(message)
    class RateLimited(message: String = "Too many attempts, try again later") : ApiException(message)
    class Http(val code: Int, message: String) : ApiException(message)

    /** Transport failure: DNS, TLS, timeout, connection reset. */
    class Network(cause: Throwable) : ApiException(cause.message ?: "Network error", cause)

    /** 2xx body that does not match the contract (bad JSON or missing fields). */
    class Protocol(message: String, cause: Throwable? = null) : ApiException(message, cause)
}

/**
 * Thin OkHttp client for the V2EON backend. One instance per process is enough;
 * it is stateless apart from the token passed by the caller.
 */
class ApiClient(
    baseUrl: String = BuildConfig.ADMIN_API_BASE_URL,
    private val client: OkHttpClient = sharedClient,
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun login(username: String, pin: String?): LoginResponse {
        val payload = json.encodeToString(LoginRequest.serializer(), LoginRequest(username, pin))
        val text = execute(
            Request.Builder()
                .url("$base/api/auth/login")
                .post(payload.toRequestBody(jsonMedia))
                .build()
        )
        return decode("login") { json.decodeFromString(LoginResponse.serializer(), text) }
    }

    suspend fun fetchConfig(token: String): ConfigResponse {
        val text = execute(
            Request.Builder()
                .url("$base/api/config")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )
        return decode("config") { json.decodeFromString(ConfigResponse.serializer(), text) }
    }

    /**
     * @param quick use short timeouts (connect 3 s, whole call 5 s) for
     * checks that gate the UI: connect-time revalidation, heartbeat and
     * launch revalidation. A normal call may hang up to 20 s on a dead
     * direct path, which stalls the connect animation.
     */
    suspend fun ping(token: String, body: PingRequest, quick: Boolean = false): PingResponse {
        val payload = json.encodeToString(PingRequest.serializer(), body)
        val text = execute(
            Request.Builder()
                .url("$base/api/session/ping")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(jsonMedia))
                .build(),
            quick = quick,
        )
        return decode("ping") { json.decodeFromString(PingResponse.serializer(), text) }
    }

    suspend fun logout(token: String) {
        execute(
            Request.Builder()
                .url("$base/api/auth/logout")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMedia))
                .build()
        )
    }

    /**
     * Direct call first. On a transport failure, retry once through Xray's
     * local SOCKS port, which forwards through the tunnel: this is what lets
     * the app reach the backend when the phone has no direct data but the
     * tunnel is up. When the tunnel is down the SOCKS connect is refused
     * instantly, so the retry costs nothing there. Only a transport failure
     * retries; an HTTP answer (even 401/403) is the backend speaking and is
     * returned as-is. If both paths fail, the original direct error is kept.
     */
    private suspend fun execute(request: Request, quick: Boolean = false): String =
        withContext(Dispatchers.IO) {
            try {
                runCall(if (quick) shortClient else client, request)
            } catch (e: ApiException.Network) {
                Log.d(TAG, "backend unreachable directly; retrying via tunnel SOCKS")
                try {
                    runCall(if (quick) shortSocksClient else socksClient, request)
                } catch (e2: ApiException.Network) {
                    throw e
                }
            }
        }

    private fun runCall(callClient: OkHttpClient, request: Request): String {
        try {
            callClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = parseError(text)
                    when (response.code) {
                        401 -> throw ApiException.Unauthorized(error?.error ?: "Invalid session")
                        403 -> throw ApiException.Forbidden(
                            expiresAt = error?.expiresAt,
                            serverTime = error?.serverTime,
                            message = error?.error ?: "Account not active",
                        )
                        429 -> throw ApiException.RateLimited(error?.error ?: "Too many attempts")
                        else -> throw ApiException.Http(
                            response.code,
                            error?.error ?: "HTTP ${response.code}",
                        )
                    }
                }
                return text
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException.Network(e)
        }
    }

    private fun <T> decode(label: String, block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw ApiException.Protocol("Malformed $label response", e)
    } catch (e: IllegalArgumentException) {
        throw ApiException.Protocol("Malformed $label response", e)
    }

    private fun parseError(text: String): ApiErrorBody? =
        runCatching { json.decodeFromString(ApiErrorBody.serializer(), text) }.getOrNull()

    companion object {
        private const val TAG = "ApiClient"

        /** Must match RopeahVpnService.LOCAL_SOCKS_PORT (Xray's SOCKS inbound). */
        private const val TUNNEL_SOCKS_PORT = 10808
        private const val TUNNEL_SOCKS_HOST = "127.0.0.1"

        /** Shared across ApiClient instances: one connection pool, one dispatcher. */
        private val sharedClient: OkHttpClient by lazy { buildClient(proxy = null, quick = false) }

        /** Short timeouts for UI-gating checks (connect, heartbeat, launch). */
        private val shortClient: OkHttpClient by lazy { buildClient(proxy = null, quick = true) }

        /** Same timeouts as [sharedClient], but routed via the tunnel's SOCKS port. */
        private val socksClient: OkHttpClient by lazy {
            buildClient(
                proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(TUNNEL_SOCKS_HOST, TUNNEL_SOCKS_PORT)),
                quick = false,
            )
        }

        private val shortSocksClient: OkHttpClient by lazy {
            buildClient(
                proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(TUNNEL_SOCKS_HOST, TUNNEL_SOCKS_PORT)),
                quick = true,
            )
        }

        private fun buildClient(proxy: Proxy?, quick: Boolean): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(if (quick) 3 else 10, TimeUnit.SECONDS)
                .readTimeout(if (quick) 3 else 10, TimeUnit.SECONDS)
                .writeTimeout(if (quick) 3 else 10, TimeUnit.SECONDS)
                .callTimeout(if (quick) 5 else 20, TimeUnit.SECONDS)
            if (proxy != null) builder.proxy(proxy)
            return builder.build()
        }
    }
}
