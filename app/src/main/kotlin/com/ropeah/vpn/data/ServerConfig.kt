package com.ropeah.vpn.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Protocol { VMess, VLESS, Trojan, Shadowsocks }

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
    val rawUri: String,
)
