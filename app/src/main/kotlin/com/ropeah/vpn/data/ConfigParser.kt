package com.ropeah.vpn.data

import android.net.Uri
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object ConfigParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(uri: String): ServerConfig? {
        return runCatching {
            when {
                uri.startsWith("vmess://") -> parseVmess(uri)
                uri.startsWith("vless://") -> parseVless(uri)
                uri.startsWith("trojan://") -> parseTrojan(uri)
                uri.startsWith("ss://") -> parseShadowsocks(uri)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseVmess(uri: String): ServerConfig? {
        val payload = uri.removePrefix("vmess://").trim()
        val decoded = runCatching {
            String(Base64.decode(payload, Base64.DEFAULT or Base64.URL_SAFE))
        }.getOrNull() ?: return null
        val obj = json.parseToJsonElement(decoded) as? JsonObject ?: return null
        fun str(k: String) = obj[k]?.jsonPrimitive?.contentOrNull
        fun int(k: String) = obj[k]?.jsonPrimitive?.intOrNull
        val address = str("add") ?: return null
        val port = int("port") ?: return null
        return ServerConfig(
            name = str("ps") ?: "$address:$port",
            protocol = Protocol.VMess,
            address = address,
            port = port,
            uuid = str("id"),
            security = str("tls"),
            network = str("net"),
            path = str("path"),
            host = str("host"),
            sni = str("sni"),
            rawUri = uri,
        )
    }

    private fun parseVless(uri: String): ServerConfig? {
        val parsed = Uri.parse(uri)
        val userInfo = parsed.userInfo ?: return null
        val host = parsed.host ?: return null
        val port = parsed.port.takeIf { it > 0 } ?: return null
        return ServerConfig(
            name = parsed.fragment?.let { Uri.decode(it) } ?: "$host:$port",
            protocol = Protocol.VLESS,
            address = host,
            port = port,
            uuid = userInfo,
            security = parsed.getQueryParameter("security"),
            network = parsed.getQueryParameter("type"),
            path = parsed.getQueryParameter("path")?.let { Uri.decode(it) },
            host = parsed.getQueryParameter("host"),
            sni = parsed.getQueryParameter("sni"),
            rawUri = uri,
        )
    }

    private fun parseTrojan(uri: String): ServerConfig? {
        val parsed = Uri.parse(uri)
        val password = parsed.userInfo ?: return null
        val host = parsed.host ?: return null
        val port = parsed.port.takeIf { it > 0 } ?: return null
        return ServerConfig(
            name = parsed.fragment?.let { Uri.decode(it) } ?: "$host:$port",
            protocol = Protocol.Trojan,
            address = host,
            port = port,
            password = password,
            security = "tls",
            sni = parsed.getQueryParameter("sni"),
            rawUri = uri,
        )
    }

    private fun parseShadowsocks(uri: String): ServerConfig? {
        val withoutScheme = uri.removePrefix("ss://")
        val (mainPart, name) = withoutScheme.split("#", limit = 2).let {
            it[0] to it.getOrNull(1)?.let(Uri::decode)
        }
        val (userPart, hostPart) = mainPart.split("@", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return null
        }
        val decodedUser = runCatching {
            String(Base64.decode(userPart, Base64.DEFAULT or Base64.URL_SAFE))
        }.getOrNull() ?: userPart
        val (method, password) = decodedUser.split(":", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return null
        }
        val (host, portStr) = hostPart.split("?")[0].split(":", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return null
        }
        val port = portStr.toIntOrNull() ?: return null
        return ServerConfig(
            name = name ?: "$host:$port",
            protocol = Protocol.Shadowsocks,
            address = host,
            port = port,
            method = method,
            password = password,
            rawUri = uri,
        )
    }
}
