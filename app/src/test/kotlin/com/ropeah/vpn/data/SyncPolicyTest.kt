package com.ropeah.vpn.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {

    @Test
    fun `first pull always applies even when versions match`() {
        // Login seeds the seen version; nothing has ever been applied.
        assertTrue(SyncPolicy.shouldApply(force = false, serverVersion = 7, appliedVersion = 0))
    }

    @Test
    fun `newer server version applies`() {
        assertTrue(SyncPolicy.shouldApply(force = false, serverVersion = 8, appliedVersion = 7))
    }

    @Test
    fun `same version does not re-apply`() {
        assertFalse(SyncPolicy.shouldApply(force = false, serverVersion = 7, appliedVersion = 7))
    }

    @Test
    fun `server rollback still applies so the admin is authoritative`() {
        assertTrue(SyncPolicy.shouldApply(force = false, serverVersion = 5, appliedVersion = 7))
    }

    @Test
    fun `manual sync forces apply`() {
        assertTrue(SyncPolicy.shouldApply(force = true, serverVersion = 7, appliedVersion = 7))
    }
}

class SessionWriteGuardTest {

    @org.junit.Test
    fun `write applies when the stored session is the expected one`() {
        assertTrue(SessionWriteGuard.appliesTo(storedToken = "abc", expectedToken = "abc"))
    }

    @org.junit.Test
    fun `stale response cannot overwrite a newer session`() {
        // Logged out and back in while a ping was in flight.
        assertFalse(SessionWriteGuard.appliesTo(storedToken = "new", expectedToken = "old"))
    }

    @org.junit.Test
    fun `call without an expected token always applies`() {
        assertTrue(SessionWriteGuard.appliesTo(storedToken = "abc", expectedToken = null))
    }

    @org.junit.Test
    fun `writer is rejected after logout`() {
        assertFalse(SessionWriteGuard.appliesTo(storedToken = null, expectedToken = "abc"))
    }
}
