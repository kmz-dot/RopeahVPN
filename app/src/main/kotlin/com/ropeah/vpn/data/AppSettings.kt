package com.ropeah.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "ropeah_settings")

enum class ThemeMode { Dark, System }

enum class LogLevel(val value: String) {
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
}

data class AppSettings(
    val useCustomDns: Boolean = false,
    val dnsPrimary: String = "1.1.1.1",
    val dnsSecondary: String = "8.8.8.8",
    val killSwitch: Boolean = false,
    val mtu: Int = 1500,
    val latencyProbeUrl: String = "https://www.gstatic.com/generate_204",
    val autoReconnect: Boolean = false,
    val logLevel: LogLevel = LogLevel.Debug,
    val theme: ThemeMode = ThemeMode.Dark,
)

class SettingsRepository(private val context: Context) {

    private val keyDnsPrimary = stringPreferencesKey("dns_primary")
    private val keyDnsSecondary = stringPreferencesKey("dns_secondary")
    private val keyUseCustomDns = booleanPreferencesKey("use_custom_dns")
    private val keyKillSwitch = booleanPreferencesKey("kill_switch")
    private val keyMtu = intPreferencesKey("mtu")
    private val keyLatencyProbeUrl = stringPreferencesKey("latency_probe_url")
    private val keyAutoReconnect = booleanPreferencesKey("auto_reconnect")
    private val keyLogLevel = stringPreferencesKey("log_level")
    private val keyTheme = stringPreferencesKey("theme")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        mapToSettings(prefs)
    }

    suspend fun update(block: AppSettings.() -> AppSettings) {
        val current = settings.first()
        val updated = block(current)
        context.settingsStore.edit { prefs ->
            prefs[keyUseCustomDns] = updated.useCustomDns
            prefs[keyDnsPrimary] = updated.dnsPrimary
            prefs[keyDnsSecondary] = updated.dnsSecondary
            prefs[keyKillSwitch] = updated.killSwitch
            prefs[keyMtu] = updated.mtu
            prefs[keyLatencyProbeUrl] = updated.latencyProbeUrl
            prefs[keyAutoReconnect] = updated.autoReconnect
            prefs[keyLogLevel] = updated.logLevel.name
            prefs[keyTheme] = updated.theme.name
        }
    }

    /** One-shot read for use in services. */
    suspend fun readOnce(): AppSettings = settings.first()

    private fun mapToSettings(prefs: androidx.datastore.preferences.core.Preferences) = AppSettings(
        useCustomDns = prefs[keyUseCustomDns] ?: false,
        dnsPrimary = prefs[keyDnsPrimary] ?: "1.1.1.1",
        dnsSecondary = prefs[keyDnsSecondary] ?: "8.8.8.8",
        killSwitch = prefs[keyKillSwitch] ?: false,
        mtu = prefs[keyMtu] ?: 1500,
        latencyProbeUrl = prefs[keyLatencyProbeUrl] ?: "https://www.gstatic.com/generate_204",
        autoReconnect = prefs[keyAutoReconnect] ?: false,
        logLevel = try {
            LogLevel.valueOf(prefs[keyLogLevel] ?: "Debug")
        } catch (_: IllegalArgumentException) {
            LogLevel.Debug
        },
        theme = try {
            ThemeMode.valueOf(prefs[keyTheme] ?: "Dark")
        } catch (_: IllegalArgumentException) {
            ThemeMode.Dark
        },
    )
}
