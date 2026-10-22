package com.ropeah.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGuardTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun session(
        expiresAt: Long = now + day,
        offset: Long = 0L,
        lastValidated: Long = now,
    ) = SessionState(
        username = "akmal01",
        token = "t",
        expiresAtMs = expiresAt,
        serverOffsetMs = offset,
        configVersion = 1,
        lastValidatedMs = lastValidated,
        lastSyncMs = now,
    )

    @Test
    fun `expired exactly at expiry instant`() {
        assertTrue(SessionGuard.isExpired(session(expiresAt = now), now))
    }

    @Test
    fun `not expired one millisecond before expiry`() {
        assertFalse(SessionGuard.isExpired(session(expiresAt = now + 1), now))
    }

    @Test
    fun `server offset pulls effective time forward even if device clock is rolled back`() {
        // Server is 2 days ahead of the device, account expired yesterday in server time.
        val s = session(expiresAt = now + day, offset = 2 * day)
        assertTrue(SessionGuard.isExpired(s, now))
    }

    @Test
    fun `negative offset keeps an account alive when device clock is rolled forward`() {
        val s = session(expiresAt = now + day, offset = -2 * day)
        assertFalse(SessionGuard.isExpired(s, now))
    }

    @Test
    fun `connect allows a recently validated session`() {
        val s = session(lastValidated = now - hour)
        assertEquals(SessionGuard.ConnectDecision.Allow, SessionGuard.connectDecision(s, now))
    }

    @Test
    fun `connect requires revalidation after the grace window`() {
        val s = session(lastValidated = now - (25 * hour))
        assertEquals(SessionGuard.ConnectDecision.Revalidate, SessionGuard.connectDecision(s, now))
    }

    @Test
    fun `expiry wins over an otherwise valid grace window`() {
        val s = session(expiresAt = now - 1, lastValidated = now)
        assertEquals(SessionGuard.ConnectDecision.DenyExpired, SessionGuard.connectDecision(s, now))
    }

    @Test
    fun `no session denies connect`() {
        assertEquals(SessionGuard.ConnectDecision.DenyNoSession, SessionGuard.connectDecision(null, now))
    }

    @Test
    fun `remaining time uses server anchored clock`() {
        val s = session(expiresAt = now + day, offset = hour)
        assertEquals(day - hour, s.remainingMs(now))
    }

    @Test
    fun `zero grace disables offline connect`() {
        val s = session(lastValidated = now - 1)
        assertEquals(
            SessionGuard.ConnectDecision.Revalidate,
            SessionGuard.connectDecision(s, now, graceMs = 0L),
        )
    }
}
