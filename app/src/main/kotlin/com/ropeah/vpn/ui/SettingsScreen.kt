package com.ropeah.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.BuildConfig
import com.ropeah.vpn.R
import com.ropeah.vpn.data.AppSettings
import com.ropeah.vpn.data.LogLevel
import com.ropeah.vpn.data.ThemeMode
import com.ropeah.vpn.ui.neo.GlassBorderGradient
import com.ropeah.vpn.ui.neo.GlassCard
import com.ropeah.vpn.ui.neo.HudTinyStyle
import com.ropeah.vpn.ui.neo.HudValueStyle
import com.ropeah.vpn.ui.neo.NeoSectionHeader
import com.ropeah.vpn.ui.neo.NeonGradient
import com.ropeah.vpn.ui.theme.NeoGlassFill
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoSurface
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import com.ropeah.vpn.ui.theme.NeoTextTertiary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: VpnViewModel = viewModel(),
    onOpenCloudflareScan: (() -> Unit)? = null,
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
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 28.dp),
    ) {
        NeoSectionHeader(
            text = stringResource(R.string.settings_section_connection),
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )

        SettingsToggle(
            icon = Icons.Filled.Router,
            title = stringResource(R.string.settings_kill_switch),
            subtitle = stringResource(R.string.settings_kill_switch_desc),
            checked = settings.killSwitch,
            onCheckedChange = { save { copy(killSwitch = it) } },
        )

        Spacer(Modifier.height(8.dp))

        SettingsToggle(
            icon = Icons.Filled.Sync,
            title = stringResource(R.string.settings_auto_reconnect),
            subtitle = stringResource(R.string.settings_auto_reconnect_desc),
            checked = settings.autoReconnect,
            onCheckedChange = { save { copy(autoReconnect = it) } },
        )

        NeoSectionHeader(
            text = stringResource(R.string.settings_section_network),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )

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

        NeoSectionHeader(
            text = stringResource(R.string.settings_section_advanced),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )

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

        NeoSectionHeader(
            text = stringResource(R.string.settings_section_about),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )

        AboutRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_version),
            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )

        if (onOpenCloudflareScan != null) {
            NeoSectionHeader(
                text = stringResource(R.string.neo_admin_tools),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenCloudflareScan,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(icon = Icons.Filled.Cloud)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.nav_cloudflare_scan),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeoTextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.neo_cloudflare_scan_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoTextSecondary,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = NeoTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(NeoTeal.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeoTeal,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon = icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeoTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF04121A),
                    checkedTrackColor = NeoTeal,
                    checkedBorderColor = NeoTeal,
                    uncheckedThumbColor = NeoTextSecondary,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.055f),
                    uncheckedBorderColor = NeoTextTertiary,
                ),
            )
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                SettingsIcon(icon = icon)
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(46.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeoTextPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoTextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val fieldShape = RoundedCornerShape(12.dp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = HudValueStyle.copy(color = NeoTextPrimary),
                    cursorBrush = NeonGradient,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(fieldShape)
                                .background(NeoGlassFill)
                                .border(
                                    1.dp,
                                    if (isError) {
                                        SolidColor(NeoRed.copy(alpha = 0.65f))
                                    } else {
                                        GlassBorderGradient
                                    },
                                    fieldShape,
                                )
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 15.dp),
                        ) {
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    icon: ImageVector,
    title: String,
    selectedLabel: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon = icon)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeoTextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = selectedLabel,
                        style = HudValueStyle,
                        color = NeoTeal,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = NeoTextSecondary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NeoSurface,
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == selectedLabel) NeoTeal else NeoTextPrimary,
                        )
                    },
                    onClick = {
                        onOptionSelected(index)
                        expanded = false
                    },
                )
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon = icon)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoTextSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = HudTinyStyle,
                    color = NeoTextPrimary,
                )
            }
        }
    }
}
