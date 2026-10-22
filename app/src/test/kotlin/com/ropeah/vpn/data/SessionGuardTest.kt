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
        maxEffectiveSeen: Long = 0L,
        suspended: Boolean = false,
    ) = SessionState(
        username = "akmal01",
        token = "t",
        expiresAtMs = expiresAt,
        serverOffsetMs = offset,
        configVersion = 1,
        lastValidatedMs = lastValidated,
        lastSyncMs = now,
        maxEffectiveSeenMs = maxEffectiveSeen,
        suspended = suspended,
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

    // ── Clock-rollback hardening (review finding C1) ──────────────────────

    @Test
    fun `effective time never travels back behind the highest server time seen`() {
        // Server time reached now + 2 days before the device clock was rolled back.
        val s = session(maxEffectiveSeen = now + 2 * day)
        assertEquals(now + 2 * day, s.effectiveNow(now))
    }

    @Test
    fun `expired account cannot be revived by rolling the device clock back`() {
        // Last validated two days ago when server time was already past expiry.
        val s = session(
            expiresAt = now + day,
            lastValidated = now - 2 * day,
            maxEffectiveSeen = now + 2 * day,
        )
        assertTrue(SessionGuard.isExpired(s, now))
        assertEquals(SessionGuard.ConnectDecision.DenyExpired, SessionGuard.connectDecision(s, now))
    }

    @Test
    fun `a device clock rollback forces revalidation instead of trusting the grace`() {
        // Device clock moved backwards: validation appears to be in the future.
        val s = session(lastValidated = now + 2 * day)
        assertEquals(SessionGuard.ConnectDecision.Revalidate, SessionGuard.connectDecision(s, now))
    }

    @Test
    fun `suspended session is locked even when expiry is in the future`() {
        val s = session(expiresAt = now + 30 * day, suspended = true)
        assertFalse(SessionGuard.isExpired(s, now))
        assertTrue(SessionGuard.isLocked(s, now))
        assertEquals(SessionGuard.ConnectDecision.DenySuspended, SessionGuard.connectDecision(s, now))
    }

    @Test
    fun `expired session is locked`() {
        assertTrue(SessionGuard.isLocked(session(expiresAt = now), now))
    }
}
