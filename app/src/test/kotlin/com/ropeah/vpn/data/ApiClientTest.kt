package com.ropeah.vpn.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApiClientTest {

    private fun clientFor(
        code: Int,
        body: String,
        throwIo: Boolean = false,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (throwIo) throw IOException("connection reset")
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message("stub")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private fun api(code: Int, body: String, throwIo: Boolean = false) =
        ApiClient(baseUrl = "https://stub.invalid", client = clientFor(code, body, throwIo))

    @Test
    fun `401 surfaces as Unauthorized with the server message`() = runBlocking {
        try {
            api(401, """{"ok":false,"error":"username not found"}""").login("nobody", null)
            fail("expected Unauthorized")
        } catch (e: ApiException.Unauthorized) {
            assertEquals("username not found", e.message)
        }
    }

    @Test
    fun `403 carries expiry and server time`() = runBlocking {
        try {
            api(
                403,
                """{"ok":false,"error":"account suspended","expires_at":9000,"server_time":8000}""",
            ).ping("t", PingRequest(configVersion = 1, connected = false))
            fail("expected Forbidden")
        } catch (e: ApiException.Forbidden) {
            assertEquals(9000L, e.expiresAt)
            assertEquals(8000L, e.serverTime)
            assertEquals("account suspended", e.message)
        }
    }

    @Test
    fun `malformed success body surfaces as Protocol, not a crash`() = runBlocking {
        try {
            api(200, """{"token":""").login("akmal01", "1234")
            fail("expected Protocol")
        } catch (e: ApiException.Protocol) {
            assertTrue(e.message!!.contains("login"))
        }
    }

    @Test
    fun `valid login body is decoded`() = runBlocking {
        val response = api(
            200,
            """{"ok":true,"token":"tok","username":"akmal01","expires_at":1000,"server_time":900,"config_version":3}""",
        ).login("akmal01", "1234")

        assertEquals("tok", response.token)
        assertEquals(3, response.configVersion)
        assertEquals(1000L, response.expiresAt)
    }

    @Test
    fun `transport failure surfaces as Network`() = runBlocking {
        try {
            api(200, "", throwIo = true).login("akmal01", null)
            fail("expected Network")
        } catch (e: ApiException.Network) {
            assertTrue(e.message!!.contains("connection reset"))
        }
    }

    @Test
    fun `403 on config keeps the error body usable`() = runBlocking {
        try {
            api(403, """{"ok":false,"error":"revoked"}""").fetchConfig("t")
            fail("expected Forbidden")
        } catch (e: ApiException.Forbidden) {
            assertEquals("revoked", e.message)
        }
    }
}
