package com.ropeah.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json

class ApiServerMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dto(
        protocol: String = "vless",
        address: String = "a.example",
        port: Int = 443,
        uri: String? = null,
    ) = ApiServerDto(
        id = "srv-1",
        name = "Singapore",
        protocol = protocol,
        address = address,
        port = port,
        uuid = "11111111-2222-3333-4444-555555555555",
        security = "tls",
        network = "ws",
        path = "/ws",
        host = "a.example",
        sni = "a.example",
        uri = uri,
    )

    @Test
    fun `maps a structured vless server`() {
        val server = dto().toServerConfig()!!

        assertEquals("srv-1", server.id)
        assertEquals(Protocol.VLESS, server.protocol)
        assertEquals("a.example", server.address)
        assertEquals(443, server.port)
        assertEquals(ServerSource.Remote, server.source)
        assertEquals("ws", server.network)
    }

    @Test
    fun `accepts lowercase and mixed-case protocol names`() {
        assertEquals(Protocol.VMess, dto(protocol = "vmess").toServerConfig()!!.protocol)
        assertEquals(Protocol.Trojan, dto(protocol = "Trojan").toServerConfig()!!.protocol)
        assertEquals(Protocol.Shadowsocks, dto(protocol = "shadowsocks").toServerConfig()!!.protocol)
        assertEquals(Protocol.Shadowsocks, dto(protocol = "ss").toServerConfig()!!.protocol)
    }

    @Test
    fun `rejects unknown protocol and unusable address`() {
        assertNull(dto(protocol = "wireguard").toServerConfig())
        assertNull(dto(address = " ").toServerConfig())
        assertNull(dto(port = 0).toServerConfig())
    }

    @Test
    fun `keeps the share uri when the backend sends one`() {
        val server = dto(uri = "vless://x@a.example:443#Singapore").toServerConfig()!!
        assertEquals("vless://x@a.example:443#Singapore", server.rawUri)
    }

    @Test
    fun `maps country metadata from the backend`() {
        val payload = """
            {
              "name": "🇳🇱 NL · Amsterdam",
              "protocol": "vless",
              "address": "a.example",
              "port": 443,
              "country": "NL",
              "countryName": "Netherlands",
              "flag": "🇳🇱",
              "city": "Amsterdam"
            }
        """.trimIndent()
        val server = json
            .decodeFromString<ApiServerDto>(payload)
            .toServerConfig()!!

        assertEquals("NL", server.countryCode)
        assertEquals("Netherlands", server.countryName)
        assertEquals("🇳🇱", server.flag)
        assertEquals("Amsterdam", server.city)
    }

    @Test
    fun `defaults source to local for legacy stored json`() {
        val legacy = """{"id":"old","name":"Old","protocol":"VMess","address":"1.1.1.1","port":443,"rawUri":"vmess://x"}"""
        val server = json
            .decodeFromString(ServerConfig.serializer(), legacy)
        assertEquals(ServerSource.Local, server.source)
        assertNull(server.countryCode)
        assertNull(server.countryName)
        assertNull(server.flag)
        assertNull(server.city)
    }
}
