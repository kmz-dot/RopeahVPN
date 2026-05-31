package com.ropeah.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.BuildConfig
import com.ropeah.vpn.R
import com.ropeah.vpn.data.AppSettings
import com.ropeah.vpn.data.LogLevel
import com.ropeah.vpn.data.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: VpnViewModel = viewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val dnsPrimaryValid = isValidIpv4(settings.dnsPrimary)
    val dnsSecondaryValid = isValidIpv4(settings.dnsSecondary)

    fun save(block: AppSettings.() -> AppSettings) {
        scope.launch { viewModel.updateSettings(block) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // ── Connection ──
        SectionHeader(label = stringResource(R.string.settings_section_connection))

        SettingsToggle(
            icon = Icons.Filled.Router,
            title = stringResource(R.string.settings_kill_switch),
            subtitle = stringResource(R.string.settings_kill_switch_desc),
            checked = settings.killSwitch,
            onCheckedChange = { save { copy(killSwitch = it) } },
        )

        SettingsToggle(
            icon = Icons.Filled.Sync,
            title = stringResource(R.string.settings_auto_reconnect),
            subtitle = stringResource(R.string.settings_auto_reconnect_desc),
            checked = settings.autoReconnect,
            onCheckedChange = { save { copy(autoReconnect = it) } },
        )

        Spacer(Modifier.height(8.dp))

        // ── Network ──
        SectionHeader(label = stringResource(R.string.settings_section_network))

        SettingsToggle(
            icon = Icons.Filled.Dns,
            title = stringResource(R.string.settings_custom_dns),
            subtitle = stringResource(R.string.settings_custom_dns_desc),
            checked = settings.useCustomDns,
            onCheckedChange = { save { copy(useCustomDns = it) } },
        )

        AnimatedVisibility(
            visible = settings.useCustomDns,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))

                SettingsTextField(
                    icon = Icons.Filled.Dns,
                    title = stringResource(R.string.settings_dns_primary),
                    value = settings.dnsPrimary,
                    isError = !dnsPrimaryValid,
                    onValueChange = { save { copy(dnsPrimary = it) } },
                )

                Spacer(Modifier.height(8.dp))

                SettingsTextField(
                    icon = null,
                    title = stringResource(R.string.settings_dns_secondary),
                    value = settings.dnsSecondary,
                    isError = !dnsSecondaryValid,
                    onValueChange = { save { copy(dnsSecondary = it) } },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        SettingsTextField(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.settings_mtu),
            subtitle = stringResource(R.string.settings_mtu_desc),
            value = settings.mtu.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.let { mtu ->
                    save { copy(mtu = mtu.coerceIn(1280, 9000)) }
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        SettingsTextField(
            icon = Icons.Filled.SignalCellularAlt,
            title = stringResource(R.string.settings_latency_probe),
            subtitle = stringResource(R.string.settings_latency_probe_desc),
            value = settings.latencyProbeUrl,
            onValueChange = { save { copy(latencyProbeUrl = it) } },
        )

        Spacer(Modifier.height(8.dp))

        // ── Advanced ──
        SectionHeader(label = stringResource(R.string.settings_section_advanced))

        SettingsDropdown(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.settings_log_level),
            selectedLabel = settings.logLevel.name,
            options = LogLevel.entries.map { it.name },
            onOptionSelected = { index ->
                val level = LogLevel.entries.getOrNull(index)
                if (level != null) save { copy(logLevel = level) }
            },
        )

        Spacer(Modifier.height(8.dp))

        SettingsDropdown(
            icon = Icons.Filled.DarkMode,
            title = stringResource(R.string.settings_theme),
            selectedLabel = when (settings.theme) {
                ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                ThemeMode.System -> stringResource(R.string.settings_theme_system)
            },
            options = listOf(
                stringResource(R.string.settings_theme_dark),
                stringResource(R.string.settings_theme_system),
            ),
            onOptionSelected = { index ->
                val mode = if (index == 1) ThemeMode.System else ThemeMode.Dark
                save { copy(theme = mode) }
            },
        )

        Spacer(Modifier.height(8.dp))

        // ── About ──
        SectionHeader(label = stringResource(R.string.settings_section_about))

        AboutRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_version),
            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )

        Spacer(Modifier.height(32.dp))
    }
}

// ── Private composables (unchanged) ──

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun isValidIpv4(ip: String): Boolean {
    if (ip.isBlank()) return true
    val parts = ip.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val num = part.toIntOrNull()
        num != null && num in 0..255 && part == num.toString()
    }
}

@Composable
private fun SettingsTextField(
    icon: ImageVector?,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    subtitle: String? = null,
    isError: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 8.dp),
                )
                Spacer(Modifier.width(12.dp))
            } else {
                // Keep text fields aligned with icon-bearing fields
                Spacer(Modifier.width(36.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    icon: ImageVector,
    title: String,
    selectedLabel: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 8.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onOptionSelected(index)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
