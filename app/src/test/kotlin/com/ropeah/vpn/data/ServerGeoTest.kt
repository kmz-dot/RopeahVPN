package com.ropeah.vpn.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerGeoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maps ipwho country and flag`() {
        val response = json.decodeFromString<ServerGeoResponse>(
            """{"success":true,"country":"United States","country_code":"US","flag":{"emoji":"🇺🇸"}}"""
        )

        val location = response.toServerLocation()

        assertEquals("US", location?.countryCode)
        assertEquals("United States", location?.countryName)
        assertEquals("🇺🇸", location?.flag)
    }

    @Test
    fun `ignores a failed lookup`() {
        val response = json.decodeFromString<ServerGeoResponse>(
            """{"success":false,"message":"404 not found"}"""
        )

        assertNull(response.toServerLocation())
    }
}
