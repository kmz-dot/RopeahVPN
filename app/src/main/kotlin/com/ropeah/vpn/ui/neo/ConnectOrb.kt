package com.ropeah.vpn.ui.neo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ropeah.vpn.R
import com.ropeah.vpn.ui.theme.NeoAmber
import com.ropeah.vpn.ui.theme.NeoCyan
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoViolet
import com.ropeah.vpn.vpn.VpnStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Connect Orb: a glass power core, a neon progress arc and a rotating
 * tick ring. Motion is skipped when the system animator scale is off.
 */
@Composable
fun ConnectOrb(
    status: VpnStatus,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    size: Dp = 266.dp,
) {
    val reduced = rememberReducedMotion()
    val active = enabled && !locked

    val tickRotation = if (!reduced && !locked && status != VpnStatus.Disconnected) {
        val transition = rememberInfiniteTransition(label = "orbTicks")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (status == VpnStatus.Connecting) 5200 else 24000,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbTicksValue",
        )
        value
    } else {
        0f
    }

    val arcRotation = if (!reduced && !locked && status == VpnStatus.Connecting) {
        val transition = rememberInfiniteTransition(label = "orbArc")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "orbArcValue",
        )
        value
    } else {
        0f
    }

    val pulse = if (!reduced && !locked && status != VpnStatus.Disconnected) {
        val transition = rememberInfiniteTransition(label = "orbPulse")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orbPulseValue",
        )
        value
    } else {
        1f
    }

    val accent = when {
        locked -> NeoRed
        status == VpnStatus.Connected -> NeoTeal
        status == VpnStatus.Connecting -> NeoCyan
        else -> NeoTextPrimary
    }
    val actionLabel = stringResource(
        if (status == VpnStatus.Connected || status == VpnStatus.Connecting) {
            R.string.action_disconnect
        } else {
            R.string.action_connect
        },
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = actionLabel }
            .clickable(
                enabled = active,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outer = this.size.minDimension / 2f
            val dim = when {
                locked -> 0.35f
                !enabled -> 0.45f
                status == VpnStatus.Disconnected -> 0.72f
                else -> 1f
            }

            if (status == VpnStatus.Connected && !locked) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeoTeal.copy(alpha = 0.17f * pulse),
                            NeoCyan.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = outer * 1.08f,
                    ),
                    radius = outer * 1.08f,
                    center = center,
                )
            }

            // Tick ring.
            val tickCount = 72
            for (i in 0 until tickCount) {
                val major = i % 6 == 0
                val angle =
                    (i.toFloat() / tickCount.toFloat()) * 2f * PI.toFloat() +
                        Math.toRadians(tickRotation.toDouble()).toFloat()
                val outerRadius = outer * 0.955f
                val innerRadius = outer * if (major) 0.895f else 0.925f
                val start = Offset(
                    x = center.x + cos(angle) * innerRadius,
                    y = center.y + sin(angle) * innerRadius,
                )
                val end = Offset(
                    x = center.x + cos(angle) * outerRadius,
                    y = center.y + sin(angle) * outerRadius,
                )
                val tickColor = when {
                    locked -> NeoRed.copy(alpha = 0.20f * dim)
                    status == VpnStatus.Connected -> when (i % 3) {
                        0 -> NeoTeal
                        1 -> NeoCyan
                        else -> NeoViolet
                    }.copy(alpha = if (major) 0.55f else 0.24f)
                    status == VpnStatus.Connecting -> NeoCyan.copy(alpha = 0.42f)
                    else -> Color.White.copy(alpha = if (major) 0.20f else 0.10f)
                }
                drawLine(
                    color = tickColor,
                    start = start,
                    end = end,
                    strokeWidth = if (major) 1.7.dp.toPx() else 1f,
                    cap = StrokeCap.Round,
                )
            }

            // Static base ring.
            val ringRadius = outer * 0.80f
            drawCircle(
                color = Color.White.copy(alpha = 0.075f * dim),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Neon progress arc.
            val sweep = when {
                locked -> 300f
                status == VpnStatus.Connected -> 300f
                status == VpnStatus.Connecting -> 96f
                else -> 216f
            }
            val startAngle = when (status) {
                VpnStatus.Connecting -> -90f + arcRotation
                else -> -90f
            }
            drawArc(
                brush = if (locked) {
                    Brush.linearGradient(listOf(NeoRed.copy(alpha = 0.65f), NeoAmber.copy(alpha = 0.50f)))
                } else if (status == VpnStatus.Disconnected) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.10f),
                        ),
                    )
                } else {
                    NeonSweep
                },
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(
                    width = if (status == VpnStatus.Connected && !locked) 3.2.dp.toPx() else 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
                alpha = dim,
            )

            // Inner glass core.
            val coreRadius = outer * 0.565f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.035f),
                    ),
                    center = Offset(center.x, center.y - coreRadius * 0.18f),
                    radius = coreRadius * 1.35f,
                ),
                radius = coreRadius,
                center = center,
            )
            if (!locked && status != VpnStatus.Disconnected) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.13f * pulse), Color.Transparent),
                        center = center,
                        radius = coreRadius,
                    ),
                    radius = coreRadius,
                    center = center,
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.13f * dim),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = accent.copy(alpha = 0.06f * dim),
                radius = coreRadius * 0.80f,
                center = center,
                style = Stroke(width = 1f),
            )

            // Power glyph.
            val glyphRadius = outer * 0.155f
            val glyphBrush: Brush = when {
                locked -> SolidColor(NeoRed.copy(alpha = 0.78f))
                status == VpnStatus.Connected -> NeonGradient
                status == VpnStatus.Connecting -> SolidColor(NeoCyan.copy(alpha = 0.90f))
                else -> SolidColor(Color.White.copy(alpha = 0.52f))
            }
            drawArc(
                brush = glyphBrush,
                startAngle = -48f,
                sweepAngle = 276f,
                useCenter = false,
                topLeft = Offset(center.x - glyphRadius, center.y - glyphRadius),
                size = Size(glyphRadius * 2f, glyphRadius * 2f),
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            drawLine(
                brush = glyphBrush,
                start = Offset(center.x, center.y - glyphRadius * 1.48f),
                end = Offset(center.x, center.y - glyphRadius * 0.08f),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
