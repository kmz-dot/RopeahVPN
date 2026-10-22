package com.ropeah.vpn.data

/**
 * Decides whether a fetched config must be pushed into local storage.
 *
 * `appliedVersion` records what is actually stored on the device, which is not
 * the same as the version last *seen* in a login or heartbeat response — the
 * old single counter made first login a no-op and let pushes be skipped.
 */
object SyncPolicy {

    fun shouldApply(force: Boolean, serverVersion: Int, appliedVersion: Int): Boolean =
        force || appliedVersion <= 0 || serverVersion != appliedVersion
}

/**
 * Guards session writes against a stale response landing after logout/login:
 * an update is only applied when it belongs to the session currently stored.
 */
object SessionWriteGuard {

    fun appliesTo(storedToken: String?, expectedToken: String?): Boolean =
        expectedToken == null || storedToken == expectedToken
}
