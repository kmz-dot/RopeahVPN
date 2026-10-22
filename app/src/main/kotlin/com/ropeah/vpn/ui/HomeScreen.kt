package com.ropeah.vpn.ui

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.BuildConfig
import com.ropeah.vpn.R
import com.ropeah.vpn.data.Protocol
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.data.SessionState
import com.ropeah.vpn.ui.neo.ConnectOrb
import com.ropeah.vpn.ui.neo.GlassCard
import com.ropeah.vpn.ui.neo.HudChip
import com.ropeah.vpn.ui.neo.HudTinyStyle
import com.ropeah.vpn.ui.neo.LatencyMeter
import com.ropeah.vpn.ui.neo.MicroLabel
import com.ropeah.vpn.ui.neo.NeoBadge
import com.ropeah.vpn.ui.neo.NeoStatusPill
import com.ropeah.vpn.ui.neo.NeonGradient
import com.ropeah.vpn.ui.neo.formatRate
import com.ropeah.vpn.ui.neo.formatSession
import com.ropeah.vpn.ui.neo.neonBorderGradient
import com.ropeah.vpn.ui.theme.NeoAmber
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import com.ropeah.vpn.ui.theme.NeoTextTertiary
import com.ropeah.vpn.vpn.TrafficSample
import com.ropeah.vpn.vpn.VpnStatus
import kotlinx.coroutines.delay

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

@Composable
fun HomeScreen(
    session: SessionState? = null,
    onOpenAccount: () -> Unit = {},
    viewModel: VpnViewModel = viewModel(),
    onSelectServer: () -> Unit = {},
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val connectedSince by viewModel.connectedSince.collectAsStateWithLifecycle()
    val connectBlocked by viewModel.connectBlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }

    // Confirmation haptic when state transitions land.
    LaunchedEffect(status) {
        when (status) {
            VpnStatus.Connected -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            VpnStatus.Disconnected -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> Unit
        }
    }

    val remainingMs = session?.remainingMs(nowMs) ?: Long.MAX_VALUE
    val canConnect = (server != null || status != VpnStatus.Disconnected) &&
        (session == null || remainingMs > 0L)

    val orbCaption = when (status) {
        VpnStatus.Connected -> stringResource(R.string.neo_secure_tunnel)
        VpnStatus.Connecting -> stringResource(R.string.neo_negotiating)
        VpnStatus.Disconnected -> if (server == null) {
            stringResource(R.string.neo_select_server)
        } else {
            stringResource(R.string.neo_tap_to_connect)
        }
    }
    val orbCaptionColor = when (status) {
        VpnStatus.Connected -> NeoTeal
        VpnStatus.Connecting -> com.ropeah.vpn.ui.theme.NeoCyan
        VpnStatus.Disconnected -> NeoTextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeoStatusPill(status = status)

        Spacer(Modifier.height(10.dp))

        ConnectOrb(
            status = status,
            enabled = canConnect,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.clearConnectBlocked()
                if (status == VpnStatus.Connected || status == VpnStatus.Connecting) {
                    viewModel.stopVpn(context)
                } else {
                    val intent = VpnService.prepare(context)
                    if (intent != null) consentLauncher.launch(intent)
                    else viewModel.startVpn(context)
                }
            },
            size = 258.dp,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = orbCaption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = orbCaptionColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (connectBlocked != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = connectBlocked!!,
                style = MaterialTheme.typography.bodySmall,
                color = com.ropeah.vpn.ui.theme.NeoRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        ServerPanel(
            server = server,
            status = status,
            latencyMs = traffic?.latencyMs,
            onClick = onSelectServer,
        )

        Spacer(Modifier.height(12.dp))

        HudStats(
            traffic = traffic,
            connectedSince = connectedSince,
            nowMs = nowMs,
            status = status,
        )

        if (session != null) {
            Spacer(Modifier.height(12.dp))
            AccountStrip(
                session = session,
                remainingMs = remainingMs,
                onClick = onOpenAccount,
            )
        }
    }
}

@Composable
private fun ServerPanel(
    server: ServerConfig?,
    status: VpnStatus,
    latencyMs: Long?,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        highlighted = status != VpnStatus.Disconnected,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.055f))
                    .border(1.dp, neonBorderGradient(0.36f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (server == null) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = NeoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = protocolInitial(server.protocol),
                        style = MaterialTheme.typography.titleMedium,
                        color = NeoTeal,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server?.name ?: stringResource(R.string.home_select_server_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = NeoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (server != null) {
                        Spacer(Modifier.width(8.dp))
                        NeoBadge(
                            text = stringResource(R.string.neo_active),
                            filled = status == VpnStatus.Connected,
                            accent = if (status == VpnStatus.Connected) NeoTeal else NeoTextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (server == null) {
                        stringResource(R.string.home_select_server_subtitle)
                    } else {
                        "${protocolLabel(server.protocol)}  ·  ${server.address}:${server.port}"
                    },
                    style = HudTinyStyle,
                    color = NeoTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (server != null && BuildConfig.MANAGED_MODE) {
                    Spacer(Modifier.height(4.dp))
                    MicroLabel(
                        text = stringResource(R.string.neo_managed_by_admin),
                        color = NeoTextTertiary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (server != null) {
                Column(horizontalAlignment = Alignment.End) {
                    LatencyMeter(latencyMs = latencyMs)
                    Spacer(Modifier.height(4.dp))
                    MicroLabel(
                        text = stringResource(R.string.neo_server),
                        color = NeoTextTertiary,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = NeoTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun HudStats(
    traffic: TrafficSample?,
    connectedSince: Long?,
    nowMs: Long,
    status: VpnStatus,
) {
    val latency = traffic?.latencyMs
    val pingValue = when {
        latency == null || latency == TrafficSample.LATENCY_UNKNOWN || latency < 0L -> "--"
        else -> "$latency ms"
    }
    val downValue = if (traffic != null) "↓ ${formatRate(traffic.downlinkBps)} Mb/s" else "--"
    val upValue = if (traffic != null) "↑ ${formatRate(traffic.uplinkBps)} Mb/s" else "--"
    val sessionValue = if (connectedSince != null && status == VpnStatus.Connected) {
        formatSession(nowMs - connectedSince)
    } else {
        "--:--:--"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HudChip(
                label = stringResource(R.string.neo_ping),
                value = pingValue,
                modifier = Modifier.weight(1f),
                valueColor = com.ropeah.vpn.ui.neo.latencyColor(latency),
            )
            HudChip(
                label = stringResource(R.string.neo_session),
                value = sessionValue,
                modifier = Modifier.weight(1.35f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HudChip(
                label = stringResource(R.string.neo_down),
                value = downValue,
                modifier = Modifier.weight(1f),
                valueColor = NeoTeal,
            )
            HudChip(
                label = stringResource(R.string.neo_up),
                value = upValue,
                modifier = Modifier.weight(1f),
                valueColor = com.ropeah.vpn.ui.theme.NeoCyan,
            )
        }
    }
}

@Composable
private fun AccountStrip(
    session: SessionState,
    remainingMs: Long,
    onClick: () -> Unit,
) {
    val remainingLabel = when {
        remainingMs <= 0L -> stringResource(R.string.home_expired)
        remainingMs >= DAY_MS -> stringResource(R.string.home_days_left, (remainingMs / DAY_MS).toInt())
        else -> stringResource(
            R.string.home_hours_left,
            ((remainingMs + HOUR_MS - 1) / HOUR_MS).coerceAtLeast(1L).toInt(),
        )
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NeonGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color(0xFF04121A),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.username,
                    style = MaterialTheme.typography.titleSmall,
                    color = NeoTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$remainingLabel · ${stringResource(R.string.home_config_version, session.configVersion)}",
                    style = HudTinyStyle,
                    color = if (remainingMs <= 0L) {
                        com.ropeah.vpn.ui.theme.NeoRed
                    } else {
                        NeoTextSecondary
                    },
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NeoTextSecondary,
            )
        }
        Spacer(Modifier.height(9.dp))
        val progress = (remainingMs.toDouble() / (30.0 * DAY_MS))
            .coerceIn(0.0, 1.0)
            .toFloat()
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.07f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (remainingMs in 1..(3L * DAY_MS)) {
                            Brush.linearGradient(listOf(NeoAmber, NeoRed))
                        } else {
                            NeonGradient
                        },
                    ),
            )
        }
    }
}

private fun protocolInitial(protocol: Protocol): String = when (protocol) {
    Protocol.VMess -> "V"
    Protocol.VLESS -> "L"
    Protocol.Trojan -> "T"
    Protocol.Shadowsocks -> "S"
}

private fun protocolLabel(protocol: Protocol): String = when (protocol) {
    Protocol.VMess -> "VMess"
    Protocol.VLESS -> "VLESS"
    Protocol.Trojan -> "Trojan"
    Protocol.Shadowsocks -> "Shadowsocks"
}
