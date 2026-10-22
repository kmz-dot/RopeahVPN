package com.ropeah.vpn.data

import java.util.UUID

sealed interface SyncResult {
    data class Updated(val version: Int, val serverCount: Int) : SyncResult
    data class Unchanged(val version: Int) : SyncResult
    data class Failed(val message: String, val exception: ApiException? = null) : SyncResult
}

/**
 * Pulls the admin-published server list and stores it atomically. Structured
 * server objects are preferred; share URIs are the fallback when a backend
 * build only emits `uris[]`.
 */
class ConfigSync(
    private val sessionRepo: SessionRepository,
    private val serverRepo: ServerRepository,
    private val api: ApiClient = ApiClient(),
) {

    suspend fun sync(session: SessionState, force: Boolean = false): SyncResult {
        val response = try {
            api.fetchConfig(session.token)
        } catch (e: ApiException) {
            return SyncResult.Failed(e.message ?: "Config request failed", e)
        }

        if (!force && session.configVersion > 0 && response.version <= session.configVersion) {
            return SyncResult.Unchanged(response.version)
        }

        val structured = response.servers.mapNotNull { it.toServerConfig() }
        val servers = if (structured.isNotEmpty()) {
            structured
        } else {
            response.uris.mapNotNull { uri ->
                ConfigParser.parse(uri)?.copy(source = ServerSource.Remote)
            }
        }

        serverRepo.replaceAll(servers)
        sessionRepo.markSynced(response.version, System.currentTimeMillis())
        return SyncResult.Updated(response.version, servers.size)
    }
}

/** Maps a backend server object onto the app's persisted model. */
fun ApiServerDto.toServerConfig(): ServerConfig? {
    val protocolValue = Protocol.fromApi(protocol) ?: return null
    if (address.isBlank() || port <= 0) return null
    return ServerConfig(
        id = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        name = name.ifBlank { "$address:$port" },
        protocol = protocolValue,
        address = address,
        port = port,
        uuid = uuid,
        password = password,
        method = method,
        security = security,
        network = network,
        path = path,
        host = host,
        sni = sni,
        rawUri = uri.orEmpty(),
        source = ServerSource.Remote,
    )
}
