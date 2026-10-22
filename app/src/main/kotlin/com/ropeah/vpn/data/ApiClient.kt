package com.ropeah.vpn.data

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

    suspend fun ping(token: String, body: PingRequest): PingResponse {
        val payload = json.encodeToString(PingRequest.serializer(), body)
        val text = execute(
            Request.Builder()
                .url("$base/api/session/ping")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(jsonMedia))
                .build()
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

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
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
                text
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
        /** Shared across ApiClient instances: one connection pool, one dispatcher. */
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
