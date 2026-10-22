package com.ropeah.vpn.data

/**
 * Active-server selection when an admin push replaces the whole list.
 *
 * Admin re-exports churn the server `id`, so identity is matched on
 * (address, port, protocol); if the previous active server disappeared the
 * first pushed server becomes active (or none when the list is empty).
 */
object ServerMerge {

    fun pickActiveId(oldActive: ServerConfig?, incoming: List<ServerConfig>): String? {
        if (incoming.isEmpty()) return null
        if (oldActive != null) {
            val match = incoming.firstOrNull {
                it.address == oldActive.address &&
                    it.port == oldActive.port &&
                    it.protocol == oldActive.protocol
            }
            if (match != null) return match.id
        }
        return incoming.first().id
    }
}
