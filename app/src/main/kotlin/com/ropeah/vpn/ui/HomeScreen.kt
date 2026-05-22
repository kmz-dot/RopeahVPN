package com.ropeah.vpn.ui

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.R
import com.ropeah.vpn.data.Protocol
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.vpn.TrafficSample
import com.ropeah.vpn.vpn.VpnStatus

@Composable
fun HomeScreen(
    viewModel: VpnViewModel = viewModel(),
    onSelectServer: () -> Unit = {},
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val connectedSince by viewModel.connectedSince.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }

    // Confirmation haptic when state transitions land
    LaunchedEffect(status) {
        when (status) {
            VpnStatus.Connected -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            VpnStatus.Disconnected -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> Unit
        }
    }

    val transition = rememberInfiniteTransition(label = "vpnPulse")
    val connectingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectingScale",
    )
    val glowScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowScale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowAlpha",
    )

    val buttonScale = when (status) {
        VpnStatus.Connecting -> connectingScale
        else -> 1f
    }
    val containerColor = when (status) {
        VpnStatus.Connected -> MaterialTheme.colorScheme.primary
        VpnStatus.Connecting -> MaterialTheme.colorScheme.primaryContainer
        VpnStatus.Disconnected -> MaterialTheme.colorScheme.primaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusPill(status = status)

        Spacer(Modifier.height(14.dp))

        ProfileCard(
            server = server,
            latencyMs = traffic?.latencyMs,
            onClick = onSelectServer,
        )

        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (status == VpnStatus.Connected) {
                Box(
                    Modifier
                        .size(200.dp)
                        .scale(glowScale)
                        .alpha(glowAlpha)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (status == VpnStatus.Connected || status == VpnStatus.Connecting) {
                        viewModel.stopVpn(context)
                    } else {
                        val intent = VpnService.prepare(context)
                        if (intent != null) consentLauncher.launch(intent)
                        else viewModel.startVpn(context)
                    }
                },
                shape = CircleShape,
                enabled = server != null || status != VpnStatus.Disconnected,
                modifier = Modifier
                    .size(180.dp)
                    .scale(buttonScale),
                colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            ) {
                Text(
                    text = stringResource(
                        if (status == VpnStatus.Connected || status == VpnStatus.Connecting)
                            R.string.action_disconnect
                        else
                            R.string.action_connect
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        if (status == VpnStatus.Connected) {
            Dashboard(
                connectedSince = connectedSince,
                traffic = traffic,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusPill(status: VpnStatus) {
    val transition = rememberInfiniteTransition(label = "pillDot")
    val dotPulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotPulse",
    )

    val dotColor = when (status) {
        VpnStatus.Connected -> Color(0xFF4ADE80)
        VpnStatus.Connecting -> Color(0xFFFACC15)
        VpnStatus.Disconnected -> Color(0xFFF87171)
    }
    val containerColor = when (status) {
        VpnStatus.Connected -> Color(0xFF4ADE80).copy(alpha = 0.14f)
        VpnStatus.Connecting -> Color(0xFFFACC15).copy(alpha = 0.14f)
        VpnStatus.Disconnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val label = when (status) {
        VpnStatus.Disconnected -> stringResource(R.string.status_disconnected)
        VpnStatus.Connecting -> stringResource(R.string.status_connecting)
        VpnStatus.Connected -> stringResource(R.string.status_connected)
    }
    val dotAlpha = when (status) {
        VpnStatus.Connecting -> dotPulse
        else -> 1f
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Soft halo for connected state
            Box(contentAlignment = Alignment.Center) {
                if (status == VpnStatus.Connected) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(dotColor.copy(alpha = 0.25f), CircleShape),
                    )
                }
                Box(
                    Modifier
                        .size(9.dp)
                        .alpha(dotAlpha)
                        .background(dotColor, CircleShape),
                )
            }
            Spacer(Modifier.size(10.dp))
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "statusLabel",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    server: ServerConfig?,
    latencyMs: Long?,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (server == null) {
                    Text(
                        text = stringResource(R.string.home_select_server_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_select_server_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = server.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = buildSubtitle(server, latencyMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun buildSubtitle(server: ServerConfig, latencyMs: Long?): String {
    val protocolLabel = when (server.protocol) {
        Protocol.VMess -> "VMess"
        Protocol.VLESS -> "VLESS"
        Protocol.Trojan -> "Trojan"
        Protocol.Shadowsocks -> "Shadowsocks"
    }
    val latencyLabel = when {
        latencyMs == null -> stringResource(R.string.home_latency_pending)
        latencyMs == TrafficSample.LATENCY_UNKNOWN -> stringResource(R.string.home_latency_unknown)
        latencyMs < 0 -> stringResource(R.string.home_latency_unknown)
        else -> "${latencyMs} ms"
    }
    return "$protocolLabel · $latencyLabel"
}
