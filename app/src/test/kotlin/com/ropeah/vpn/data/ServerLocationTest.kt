package com.ropeah.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerLocationTest {

    @Test
    fun `uses backend country fields`() {
        val location = ServerLocations.resolve(
            countryCode = "US",
            countryName = "United States",
            flag = "🇺🇸",
            name = "Cloudflare WS",
        )

        assertEquals("US", location?.countryCode)
        assertEquals("United States", location?.countryName)
        assertEquals("🇺🇸", location?.flag)
    }

    @Test
    fun `derives country from a flag in the server name`() {
        val location = ServerLocations.resolve(name = "🇳🇱 NL · Amsterdam")

        assertEquals("NL", location?.countryCode)
        assertEquals("Netherlands", location?.countryName)
        assertEquals("🇳🇱", location?.flag)
    }

    @Test
    fun `derives country from a country code token`() {
        val location = ServerLocations.resolve(name = "US · Ashburn · Cloudflare")

        assertEquals("US", location?.countryCode)
        assertEquals("United States", location?.countryName)
        assertEquals("🇺🇸", location?.flag)
    }

    @Test
    fun `derives country from a country name in the server name`() {
        val location = ServerLocations.resolve(name = "Netherlands · Amsterdam")

        assertEquals("NL", location?.countryCode)
        assertEquals("Netherlands", location?.countryName)
        assertEquals("🇳🇱", location?.flag)
    }

    @Test
    fun `derives code from an explicit country name`() {
        val location = ServerLocations.resolve(countryName = "Netherlands", name = "Cloudflare WS")

        assertEquals("NL", location?.countryCode)
        assertEquals("Netherlands", location?.countryName)
        assertEquals("🇳🇱", location?.flag)
    }

    @Test
    fun `uses the flag when the country name is unknown`() {
        val location = ServerLocations.resolve(
            countryName = "Unknown",
            flag = "🇺🇸",
            name = "Cloudflare WS",
        )

        assertEquals("US", location?.countryCode)
        assertEquals("United States", location?.countryName)
        assertEquals("🇺🇸", location?.flag)
    }

    @Test
    fun `does not treat storage units as a country`() {
        assertNull(ServerLocations.resolve(name = "1024 GB"))
        assertEquals(
            "GB",
            ServerLocations.resolve(countryCode = "GB", name = "1024 GB")?.countryCode,
        )
    }

    @Test
    fun `still accepts a country code that is also a unit`() {
        val location = ServerLocations.resolve(name = "GB · London")

        assertEquals("GB", location?.countryCode)
        assertEquals("United Kingdom", location?.countryName)
        assertEquals("🇬🇧", location?.flag)
    }

    @Test
    fun `resolves location from a stored server`() {
        val server = ServerConfig(
            name = "🇳🇱 NL · Amsterdam",
            protocol = Protocol.VLESS,
            address = "a.example",
            port = 443,
        )

        val location = server.countryLocation()

        assertEquals("NL", location?.countryCode)
        assertEquals("Netherlands", location?.countryName)
        assertEquals("🇳🇱", location?.flag)
    }

    @Test
    fun `ignores transport abbreviations in the server name`() {
        assertNull(ServerLocations.resolve(name = "Cloudflare WS"))
        assertNull(ServerLocations.resolve(name = "SS · Shadowsocks"))
        assertNull(ServerLocations.resolve(name = "AS13335 Cloudflare"))
    }
}
