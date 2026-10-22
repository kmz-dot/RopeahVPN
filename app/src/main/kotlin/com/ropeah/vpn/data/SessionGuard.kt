package com.ropeah.vpn.data

/**
 * Pure expiry policy. All "now" values are device-clock readings; expiry math
 * adds the server offset captured at login/ping so the device clock cannot be
 * used to extend access.
 */
object SessionGuard {

    /** Interim default from the handoff: allow offline connect for 24 h. */
    const val OFFLINE_GRACE_MS = 24L * 60L * 60L * 1000L

    enum class ConnectDecision { Allow, Revalidate, DenyExpired, DenySuspended, DenyNoSession }

    fun effectiveNow(deviceNowMs: Long, serverOffsetMs: Long): Long =
        deviceNowMs + serverOffsetMs

    fun isExpired(session: SessionState?, deviceNowMs: Long): Boolean {
        if (session == null) return true
        return session.effectiveNow(deviceNowMs) >= session.expiresAtMs
    }

    /** Expired, suspended, or no session at all: the app must show the lock screen. */
    fun isLocked(session: SessionState?, deviceNowMs: Long): Boolean {
        if (session == null) return true
        return session.suspended || isExpired(session, deviceNowMs)
    }

    fun remainingMs(session: SessionState, deviceNowMs: Long): Long =
        session.expiresAtMs - effectiveNow(deviceNowMs, session.serverOffsetMs)

    /**
     * Connect policy: local expiry always denies; otherwise a session validated
     * within the grace window may connect offline, and an older one must be
     * revalidated with the backend first.
     */
    fun connectDecision(
        session: SessionState?,
        deviceNowMs: Long,
        graceMs: Long = OFFLINE_GRACE_MS,
    ): ConnectDecision {
        if (session == null) return ConnectDecision.DenyNoSession
        if (session.suspended) return ConnectDecision.DenySuspended
        if (isExpired(session, deviceNowMs)) return ConnectDecision.DenyExpired

        val validatedAge = deviceNowMs - session.lastValidatedMs
        // Negative age means the device clock moved backwards since validation:
        // the grace window cannot be trusted, so require a fresh revalidation.
        if (validatedAge < 0L) return ConnectDecision.Revalidate
        return if (validatedAge <= graceMs) ConnectDecision.Allow else ConnectDecision.Revalidate
    }
}
