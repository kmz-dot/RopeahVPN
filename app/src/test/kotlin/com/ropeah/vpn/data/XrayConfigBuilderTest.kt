package com.ropeah.vpn.data

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The backend's structured server objects can carry a percent-encoded WS path
 * (e.g. `%2Fvless-ntls`). Xray expects the decoded path; without this the
 * dialer requests `/%2Fvless-ntls`, which Cloudflare answers with 404.
 */
class XrayConfigBuilderTest {

    private fun server(path: String?) = ServerConfig(
        name = "test",
        protocol = Protocol.VLESS,
        address = "104.17.147.22",
        port = 80,
        uuid = "11111111-2222-3333-4444-555555555555",
        security = "none",
        network = "ws",
        path = path,
        host = "relay.example",
        sni = null,
        rawUri = "",
    )

    private fun stream(path: String?) =
        XrayConfigBuilder.build(server(path), 1080, "warning")
            .let { it["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject }

    private fun builtPath(path: String?): String? =
        stream(path)["wsSettings"]!!.jsonObject["path"]!!.jsonPrimitive.contentOrNull

    @Test
    fun `decodes a percent-encoded ws path from the backend`() {
        assertEquals("/vless-ntls", builtPath("%2Fvless-ntls"))
    }

    @Test
    fun `decodes encoded utf8 sequences once`() {
        assertEquals("/a b", builtPath("%2Fa%20b"))
    }

    @Test
    fun `leaves an already decoded ws path untouched`() {
        assertEquals("/vless-ntls", builtPath("/vless-ntls"))
    }

    @Test
    fun `leaves malformed percent sequences literal`() {
        assertEquals("/a%zz", builtPath("/a%zz"))
    }

    @Test
    fun `keeps the Host header intact`() {
        val host = stream("%2Fvless-ntls")["wsSettings"]!!.jsonObject["headers"]!!
            .jsonObject["Host"]!!.jsonPrimitive.contentOrNull
        assertEquals("relay.example", host)
    }
}
