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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.BuildConfig
import com.ropeah.vpn.R
import com.ropeah.vpn.data.Protocol
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.data.ServerGeoCache
import com.ropeah.vpn.data.ServerLocation
import com.ropeah.vpn.data.SessionState
import com.ropeah.vpn.data.countryLocation
import com.ropeah.vpn.ui.neo.ConnectOrb
import com.ropeah.vpn.ui.neo.GlassCard
import com.ropeah.vpn.ui.neo.NeoPrimaryButton
import com.ropeah.vpn.ui.neo.RocketOverlay
import com.ropeah.vpn.ui.neo.RocketPhase
import com.ropeah.vpn.ui.neo.arcControl
import com.ropeah.vpn.ui.neo.bezierPoint
import com.ropeah.vpn.ui.neo.ScorchOverlay
import com.ropeah.vpn.ui.neo.TrickleOverlay
import com.ropeah.vpn.ui.neo.rememberReducedMotion
import com.ropeah.vpn.ui.neo.rocketAngle
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
import com.ropeah.vpn.ui.theme.NeoCyan
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
    val geoLocations by ServerGeoCache.locations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Connect rocket animation, ported from the web mockup
    // (v2eon-connect-animation-mockup): card trickle -> rocket climb ->
    // hover above the orb while Connecting -> dart-in on Connected,
    // tip-first plummet + FAILED on failure or the 15 s handshake timeout.
    // Slowness is never a crash: a late reply still lands.
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    var rocketPhase by remember { mutableStateOf(RocketPhase.Idle) }
    var userStopped by remember { mutableStateOf(false) }
    var crashKey by remember { mutableIntStateOf(0) }
    var orbCenter by remember { mutableStateOf(Offset.Zero) }
    var cardCenter by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rocketAngle by remember { mutableStateOf(0f) }
    val rocketPos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val rocketTrail = remember { mutableStateListOf<Offset>() }
    // Fast connect: Connected landed before the rocket launched. The
    // landing then plays compressed instead of cancelling the animation,
    // so the rocket always shows even on a sub-second server.
    var fastConnect by remember { mutableStateOf(false) }
    // Trickle is meant to be SEEN (user feedback 2026-11-03: 500ms was too
    // fast to notice on his fast server). The card trickles for at least
    // TRICKLE_MIN_MS even if Connected lands mid-trickle: the landing waits
    // out the remainder instead of cutting the trickle short.
    val trickleMinMs = 1300L
    var trickleStartMs by remember { mutableLongStateOf(0L) }
    val failed = rocketPhase == RocketPhase.Crashed

    // Status changes drive landing / crash. A user-initiated disconnect
    // never crashes: userStopped is set in connectAction before stopVpn.
    LaunchedEffect(status) {
        when (status) {
            VpnStatus.Connected -> when (rocketPhase) {
                RocketPhase.Hovering -> rocketPhase = RocketPhase.Landing
                RocketPhase.Flying, RocketPhase.Trickling -> {
                    // Never cancel the launch on a fast connect: land
                    // compressed instead, so the rocket always shows. But
                    // never cut the trickle short: if Connected landed
                    // mid-trickle, wait out the trickle minimum first so
                    // the card animation is actually seeable.
                    fastConnect = true
                    if (rocketPhase == RocketPhase.Trickling) {
                        val remaining = trickleMinMs - (System.currentTimeMillis() - trickleStartMs)
                        if (remaining > 0) delay(remaining)
                    }
                    if (rocketPhase == RocketPhase.Trickling || rocketPhase == RocketPhase.Flying) {
                        rocketPhase = RocketPhase.Landing
                    }
                }
                else -> Unit
            }
            VpnStatus.Disconnected -> if (!userStopped) {
                when (rocketPhase) {
                    RocketPhase.Hovering, RocketPhase.Flying -> rocketPhase = RocketPhase.Diving
                    RocketPhase.Trickling -> {
                        rocketPos.snapTo(cardCenter - rootOrigin)
                        crashKey++
                        rocketPhase = RocketPhase.Crashed
                    }
                    else -> Unit
                }
            }
            else -> Unit
        }
    }

    // Session-gate failures never reach Connecting: connectBlocked is the
    // only signal, so it crashes the rocket the same way.
    LaunchedEffect(connectBlocked) {
        if (connectBlocked != null && !userStopped) {
            when (rocketPhase) {
                RocketPhase.Hovering, RocketPhase.Flying -> rocketPhase = RocketPhase.Diving
                RocketPhase.Trickling -> {
                    rocketPos.snapTo(cardCenter - rootOrigin)
                    crashKey++
                    rocketPhase = RocketPhase.Crashed
                }
                else -> Unit
            }
        }
    }

    // Flight-path driver: one phase at a time, each completing into the next.
    LaunchedEffect(rocketPhase) {
        when (rocketPhase) {
            RocketPhase.Trickling -> {
                // Park the rocket on the card every attempt: after a
                // previous landing it sits at the orb, and without this
                // reset later flights animate zero distance (the
                // reconnect bug from the user's video).
                if (cardCenter != Offset.Zero) rocketPos.snapTo(cardCenter - rootOrigin)
                rocketTrail.clear()
                trickleStartMs = System.currentTimeMillis()
                delay(trickleMinMs)
                if (rocketPhase == RocketPhase.Trickling) {
                    rocketPhase = if (reducedMotion) RocketPhase.Hovering else RocketPhase.Flying
                }
            }
            RocketPhase.Flying -> {
                if (reducedMotion || orbCenter == Offset.Zero || cardCenter == Offset.Zero) {
                    rocketPhase = RocketPhase.Hovering
                } else {
                    // Arc flight, mockup-style: the bezier is walked in
                    // segments so the rocket tilts along the curve.
                    val start = cardCenter - rootOrigin
                    val hover = with(density) {
                        Offset(
                            orbCenter.x - rootOrigin.x,
                            orbCenter.y - rootOrigin.y - 258.dp.toPx() / 2f - 64.dp.toPx(),
                        )
                    }
                    val ctrl = with(density) { arcControl(start, hover, 56.dp.toPx()) }
                    rocketPos.snapTo(start)
                    var prev = start
                    for (i in 1..4) {
                        val pt = bezierPoint(start, ctrl, hover, i / 4f)
                        rocketAngle = rocketAngle(prev, pt)
                        rocketPos.animateTo(pt, animationSpec = tween(durationMillis = 200, easing = EaseOutCubic))
                        rocketTrail.add(pt)
                        if (rocketTrail.size > 8) rocketTrail.removeAt(0)
                        prev = pt
                    }
                    rocketPhase = RocketPhase.Hovering
                }
            }
            RocketPhase.Landing -> {
                if (reducedMotion || orbCenter == Offset.Zero) {
                    fastConnect = false
                    rocketPhase = RocketPhase.Idle
                } else {
                    val target = orbCenter - rootOrigin
                    if (fastConnect && cardCenter != Offset.Zero) {
                        // Fast connect re-flies from the card EVERY time
                        // (not only when the rocket never launched): this
                        // is the reconnect fix — otherwise the rocket
                        // starts parked at the orb and the landing is
                        // a zero-distance no-op the eye can't see.
                        val start = cardCenter - rootOrigin
                        val hover = with(density) {
                            Offset(
                                orbCenter.x - rootOrigin.x,
                                orbCenter.y - rootOrigin.y - 258.dp.toPx() / 2f - 64.dp.toPx(),
                            )
                        }
                        val ctrl = with(density) { arcControl(start, hover, 56.dp.toPx()) }
                        rocketTrail.clear()
                        rocketPos.snapTo(start)
                        var prev = start
                        for (i in 1..2) {
                            val pt = bezierPoint(start, ctrl, hover, i / 2f)
                            rocketAngle = rocketAngle(prev, pt)
                            rocketPos.animateTo(pt, animationSpec = tween(durationMillis = 200, easing = EaseOutCubic))
                            rocketTrail.add(pt)
                            prev = pt
                        }
                    }
                    rocketAngle = rocketAngle(rocketPos.value, target)
                    rocketPos.animateTo(target, animationSpec = tween(durationMillis = 300, easing = EaseOutCubic))
                    rocketPhase = RocketPhase.Flash
                }
            }
            RocketPhase.Flash -> {
                delay(600)
                fastConnect = false
                rocketTrail.clear()
                rocketPhase = RocketPhase.Idle
            }
            RocketPhase.Diving -> {
                if (reducedMotion || cardCenter == Offset.Zero) {
                    rocketPos.snapTo(cardCenter - rootOrigin)
                    crashKey++
                    rocketPhase = RocketPhase.Crashed
                } else {
                    val target = cardCenter - rootOrigin
                    rocketAngle = rocketAngle(rocketPos.value, target)
                    rocketPos.animateTo(target, animationSpec = tween(durationMillis = 900, easing = EaseInCubic))
                    crashKey++
                    rocketPhase = RocketPhase.Crashed
                }
            }
            else -> Unit
        }
    }

    // Handshake timeout: still Connecting after 15 s counts as no reply,
    // so the attempt is stopped and the status effect crashes the rocket.
    // A slow server that replies before this lands normally.
    LaunchedEffect(rocketPhase, status) {
        if (rocketPhase == RocketPhase.Hovering && status == VpnStatus.Connecting) {
            delay(15_000)
            viewModel.stopVpn(context)
        }
    }

    val baseLocation = remember(server?.id, server?.countryCode, server?.countryName, server?.flag, server?.name) {
        server?.countryLocation()
    }
    LaunchedEffect(server?.address, baseLocation) {
        val current = server
        if (current != null && baseLocation == null) ServerGeoCache.request(current.address)
    }
    val serverLocation = baseLocation ?: server?.let { geoLocations[it.address] }

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
        } else {
            // Consent denied: no status change will ever arrive, so park
            // the rocket instead of trickling forever.
            rocketPhase = RocketPhase.Idle
        }
    }

    // Declared after consentLauncher: the orb and the RETRY button share it.
    val connectAction: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.clearConnectBlocked()
        if (status == VpnStatus.Connected || status == VpnStatus.Connecting) {
            userStopped = true
            fastConnect = false
            rocketTrail.clear()
            rocketPhase = RocketPhase.Idle
            viewModel.stopVpn(context)
        } else {
            userStopped = false
            fastConnect = false
            rocketPhase = RocketPhase.Trickling
            val intent = VpnService.prepare(context)
            if (intent != null) consentLauncher.launch(intent)
            else viewModel.startVpn(context)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
                enabled = rocketPhase == RocketPhase.Idle || rocketPhase == RocketPhase.Crashed,
            )
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeoStatusPill(status = status)

        // Mockup CONFIG pill: the backend config version, front and centre.
        if (session != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeoBadge(
                    text = stringResource(R.string.home_config_version, session.configVersion),
                    accent = NeoCyan,
                )
                Spacer(Modifier.width(8.dp))
                MicroLabel(
                    text = stringResource(R.string.neo_managed_by_admin),
                    color = NeoTextTertiary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        ConnectOrb(
            status = status,
            enabled = canConnect,
            onClick = connectAction,
            modifier = Modifier.onGloballyPositioned { orbCenter = it.boundsInRoot().center },
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

        if (failed) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.rocket_connect_failed),
                style = MaterialTheme.typography.bodySmall,
                color = com.ropeah.vpn.ui.theme.NeoRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
            NeoPrimaryButton(
                text = stringResource(R.string.action_retry),
                onClick = connectAction,
            )
        }

        Spacer(Modifier.height(20.dp))

        ServerPanel(
            server = server,
            location = serverLocation,
            status = status,
            latencyMs = traffic?.latencyMs,
            trickling = rocketPhase == RocketPhase.Trickling,
            scorched = failed,
            onCardPositioned = { cardCenter = it },
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
        RocketOverlay(
            phase = rocketPhase,
            rocketPos = rocketPos.value,
            rocketAngle = rocketAngle,
            crashKey = crashKey,
            trail = rocketTrail,
            reduced = reducedMotion,
        )
    }
}

@Composable
private fun ServerPanel(
    server: ServerConfig?,
    location: ServerLocation?,
    status: VpnStatus,
    latencyMs: Long?,
    trickling: Boolean,
    scorched: Boolean,
    onCardPositioned: (Offset) -> Unit,
    onClick: () -> Unit,
) {
    val serverMeta = server?.let { current ->
        buildString {
            if (location != null) {
                location.flag?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                append(location.countryName)
                append("  ·  ")
            }
            append(protocolLabel(current.protocol))
            append("  ·  ")
            append(current.address)
            append(':')
            append(current.port)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onCardPositioned(it.boundsInRoot().center) },
    ) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        highlighted = status != VpnStatus.Disconnected,
        borderBrush = if (scorched) {
            Brush.linearGradient(listOf(NeoRed.copy(alpha = 0.8f), NeoAmber.copy(alpha = 0.5f)))
        } else {
            null
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                } else if (!location?.flag.isNullOrBlank()) {
                    Text(
                        text = location.flag,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
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
                        serverMeta.orEmpty()
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
        if (trickling) TrickleOverlay()
        if (scorched) ScorchOverlay()
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
