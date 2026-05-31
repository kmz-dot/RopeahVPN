package com.ropeah.vpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class CloudflareRange(
    val cidr: String,
    val totalAddresses: Long,
    val sampledIps: List<String>,
)

object CloudflareIpFetcher {

    /**
     * Fetches Cloudflare IPv4 ranges from cloudflare.com/ips-v4 and samples
     * [samplesPerRange] IPs from each range.
     */
    suspend fun fetchAndSample(
        samplesPerRange: Int = 15,
        onProgress: (String) -> Unit = {},
    ): List<CloudflareRange> = withContext(Dispatchers.IO) {
        onProgress("Fetching Cloudflare IP ranges...")
        val text = fetchUrl("https://www.cloudflare.com/ips-v4")
            ?: error("Failed to fetch Cloudflare IPs. Check your internet connection.")
        val ranges = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        if (ranges.isEmpty()) error("No Cloudflare IP ranges found.")

        onProgress("Found ${ranges.size} IPv4 ranges. Sampling IPs...")

        ranges.mapIndexed { index, cidr ->
            onProgress("Sampling ${index + 1}/${ranges.size}: $cidr")
            val ips = sampleIps(cidr, samplesPerRange)
            val total = totalAddresses(cidr)
            CloudflareRange(cidr = cidr, totalAddresses = total, sampledIps = ips)
        }
    }

    private fun fetchUrl(urlString: String): String? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "RopeahVPN/1.0")
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Returns number of IP addresses in a CIDR range. */
    private fun totalAddresses(cidr: String): Long {
        val prefix = cidr.substringAfter("/").toIntOrNull() ?: return 0
        if (prefix < 0 || prefix > 32) return 0
        return 1L shl (32 - prefix)
    }

    /**
     * Samples [n] evenly-spaced IPs from the given CIDR range.
     * Skips network and broadcast addresses when applicable.
     */
    private fun sampleIps(cidr: String, n: Int): List<String> {
        val (ipStr, prefixStr) = cidr.split("/")
        val prefix = prefixStr.toIntOrNull() ?: return emptyList()
        if (prefix < 0 || prefix > 32) return emptyList()

        val parts = ipStr.split(".").map { it.toInt().and(0xFF) }
        val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]

        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val networkAddr = ipInt and mask
        val total = 1L shl (32 - prefix)

        // Determine usable host range (skip network addr; skip broadcast for > /31)
        val firstHost = networkAddr + 1L
        val lastHost = when {
            total <= 2 -> networkAddr + total - 1 // /31 or /32: use all
            else -> networkAddr + total - 2       // skip broadcast
        }
        val hostCount = (lastHost - firstHost + 1L).coerceAtLeast(1L).toInt()

        val count = minOf(n, hostCount)
        if (count <= 0) return emptyList()

        val step = if (hostCount > count) hostCount / count else 1

        return (0 until count).map { i ->
            val host = firstHost + (i * step).toLong().coerceAtMost(lastHost - firstHost)
            formatIp(host.toInt())
        }
    }

    private fun formatIp(ip: Int): String {
        return "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
    }
}
