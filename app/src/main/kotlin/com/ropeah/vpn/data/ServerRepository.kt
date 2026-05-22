package com.ropeah.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ropeah_servers")

class ServerRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val keyServers = stringPreferencesKey("servers_json")
    private val keyActive = stringPreferencesKey("active_id")

    val servers: Flow<List<ServerConfig>> = context.dataStore.data.map { prefs ->
        prefs[keyServers]?.let {
            runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    val activeServer: Flow<ServerConfig?> = context.dataStore.data.map { prefs ->
        val active = prefs[keyActive] ?: return@map null
        val list = prefs[keyServers]?.let {
            runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
        } ?: return@map null
        list.firstOrNull { it.id == active }
    }

    suspend fun add(server: ServerConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[keyServers]?.let {
                runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = current + server
            prefs[keyServers] = json.encodeToString(next)
            if (prefs[keyActive].isNullOrEmpty()) {
                prefs[keyActive] = server.id
            }
        }
    }

    suspend fun setActive(id: String) {
        context.dataStore.edit { it[keyActive] = id }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[keyServers]?.let {
                runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = current.filterNot { it.id == id }
            prefs[keyServers] = json.encodeToString(next)
            if (prefs[keyActive] == id) {
                prefs[keyActive] = next.firstOrNull()?.id ?: ""
            }
        }
    }

    suspend fun restore(server: ServerConfig, index: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[keyServers]?.let {
                runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val clamped = index.coerceIn(0, current.size)
            val next = current.toMutableList().apply { add(clamped, server) }
            prefs[keyServers] = json.encodeToString(next)
            if (prefs[keyActive].isNullOrEmpty()) {
                prefs[keyActive] = server.id
            }
        }
    }
}
