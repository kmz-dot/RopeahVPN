package com.ropeah.vpn.data

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ServerGeoResponse(
    val success: Boolean = false,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val flag: ServerGeoFlag? = null,
)

@Serializable
data class ServerGeoFlag(val emoji: String? = null)

fun ServerGeoResponse.toServerLocation(): ServerLocation? {
    if (!success) return null
    return ServerLocations.resolve(
        countryCode = countryCode,
        countryName = country,
        flag = flag?.emoji,
    )
}

/**
 * Best-effort country lookup for a server address. The address is resolved
 * locally; only the resulting IP is sent to the geolocation API.
 */
class ServerGeoClient(
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun lookup(address: String): ServerLocation? = withContext(Dispatchers.IO) {
        val ip = resolveIp(address) ?: return@withContext null
        val url = "https://ipwho.is".toHttpUrl().newBuilder()
            .addPathSegment(ip)
            .addQueryParameter("fields", "success,country,country_code,flag.emoji")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RopeahVPN/1.0")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                json.decodeFromString<ServerGeoResponse>(body).toServerLocation()
            }
        }.getOrNull()
    }

    private fun resolveIp(address: String): String? =
        runCatching { InetAddress.getByName(address).hostAddress }.getOrNull()

    private companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }
}

/**
 * Process-wide, in-memory flag cache. Lookups are attempted only for servers
 * whose config/name has no country, never for every card recomposition.
 */
object ServerGeoCache {
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L

    private val client = ServerGeoClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val inFlight = mutableSetOf<String>()
    private val failedAt = mutableMapOf<String, Long>()
    private val _locations = MutableStateFlow<Map<String, ServerLocation>>(emptyMap())

    val locations: StateFlow<Map<String, ServerLocation>> = _locations

    fun request(address: String) {
        if (address.isBlank()) return
        if (_locations.value.containsKey(address)) return
        synchronized(lock) {
            if (!inFlight.add(address)) return
            val failed = failedAt[address]
            if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) {
                inFlight.remove(address)
                return
            }
        }
        scope.launch {
            val location = client.lookup(address)
            synchronized(lock) {
                inFlight.remove(address)
                if (location == null) {
                    failedAt[address] = System.currentTimeMillis()
                } else {
                    failedAt.remove(address)
                }
            }
            if (location != null) {
                _locations.update { it + (address to location) }
            }
        }
    }
}
