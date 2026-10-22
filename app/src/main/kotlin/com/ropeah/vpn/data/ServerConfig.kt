package com.ropeah.vpn.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Protocol {
    VMess, VLESS, Trojan, Shadowsocks;

    companion object {
        /** Backend sends lowercase protocol names; unknown values are rejected. */
        fun fromApi(value: String): Protocol? = when (value.trim().lowercase()) {
            "vmess" -> VMess
            "vless" -> VLESS
            "trojan" -> Trojan
            "shadowsocks", "ss" -> Shadowsocks
            else -> null
        }
    }
}

/** Where a config came from. Local remains the default so old stored JSON decodes. */
@Serializable
enum class ServerSource { Local, Remote }

@Serializable
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: Protocol,
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
    val rawUri: String = "",
    val source: ServerSource = ServerSource.Local,
)
