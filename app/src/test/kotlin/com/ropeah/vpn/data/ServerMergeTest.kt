package com.ropeah.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerMergeTest {

    private fun server(
        id: String,
        address: String = "1.2.3.4",
        port: Int = 443,
        protocol: Protocol = Protocol.VLESS,
    ) = ServerConfig(
        id = id,
        name = id,
        protocol = protocol,
        address = address,
        port = port,
        rawUri = "",
    )

    @Test
    fun `keeps active server across id churn using address port protocol`() {
        val oldActive = server(id = "old-id", address = "a.example", port = 443)
        val incoming = listOf(
            server(id = "new-1", address = "b.example", port = 443),
            server(id = "new-2", address = "a.example", port = 443),
        )

        assertEquals("new-2", ServerMerge.pickActiveId(oldActive, incoming))
    }

    @Test
    fun `does not match when protocol differs`() {
        val oldActive = server(id = "old", address = "a.example", port = 443, protocol = Protocol.VLESS)
        val incoming = listOf(
            server(id = "new-1", address = "a.example", port = 443, protocol = Protocol.Trojan),
        )

        assertEquals("new-1", ServerMerge.pickActiveId(oldActive, incoming))
    }

    @Test
    fun `falls back to first server when active is gone`() {
        val oldActive = server(id = "old", address = "gone.example")
        val incoming = listOf(server(id = "new-1"), server(id = "new-2"))

        assertEquals("new-1", ServerMerge.pickActiveId(oldActive, incoming))
    }

    @Test
    fun `returns null when the pushed list is empty`() {
        assertNull(ServerMerge.pickActiveId(server("old"), emptyList()))
    }

    @Test
    fun `returns first server when there was no active server`() {
        val incoming = listOf(server(id = "new-1"), server(id = "new-2"))

        assertEquals("new-1", ServerMerge.pickActiveId(null, incoming))
    }
}
