package com.ropeah.vpn.ui.neo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ropeah.vpn.ui.theme.NeoAmber
import com.ropeah.vpn.ui.theme.NeoCyan
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoViolet
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Connect-animation phases, mirroring the web mockup
 * (v2eon-connect-animation-mockup):
 *
 * Trickling — the selected server card sheds amber droplets while the
 *   connect starts.
 * Flying — the rocket leaves the card and climbs to a hover point above
 *   the orb while the tunnel negotiates.
 * Hovering — the rocket bobs above the orb while status is Connecting.
 *   A late reply still lands: slowness is never a crash.
 * Landing — the rocket darts into the orb on Connected.
 * Diving — on failure (or the 15 s handshake timeout) the rocket
 *   plummets tip-first back into its server card.
 * Crashed — burst + scorch + FAILED banner with RETRY.
 */
enum class RocketPhase {
    Idle,
    Trickling,
    Flying,
    Hovering,
    Landing,
    Flash,
    Diving,
    Crashed,
}

/** Rotation (degrees) that points a nose-up rocket along [from] -> [to]. */
fun rocketAngle(from: Offset, to: Offset): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    if (dx == 0f && dy == 0f) return 0f
    return Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
}

/** Quadratic bezier point: the arc the rocket flies, like the web mockup. */
fun bezierPoint(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
        u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
    )
}

/** Control point that bows the card -> hover flight into a gentle arc. */
fun arcControl(start: Offset, end: Offset, bowPx: Float): Offset {
    return Offset(
        (start.x + end.x) / 2f + bowPx,
        (start.y + end.y) / 2f - bowPx * 0.5f,
    )
}

/** Amber droplets trickling down inside the server card. */
@Composable
fun BoxScope.TrickleOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "trickle")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "trickleProgress",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val dropletCount = 10
        for (i in 0 until dropletCount) {
            val offset = (progress + i.toFloat() / dropletCount) % 1f
            val x = size.width * (0.08f + 0.84f * ((i * 37 % dropletCount).toFloat() / dropletCount))
            val y = size.height * offset
            val alpha = (sin(PI.toFloat() * offset)).coerceIn(0f, 1f)
            drawCircle(
                color = NeoAmber.copy(alpha = alpha),
                radius = 5.dp.toPx(),
                center = Offset(x, y),
            )
            drawLine(
                color = NeoAmber.copy(alpha = alpha * 0.7f),
                start = Offset(x, y - 14.dp.toPx()),
                end = Offset(x, y),
                strokeWidth = 2.dp.toPx(),
            )
        }
        // NOTE (2026-11-03): the amber sweep band that used to travel down the
        // card with the droplets is REMOVED. On the user's Infinix it rendered
        // as a solid pure-black rect at the card top: v3 (Transparent gradient
        // stops, sliding in from above the card) showed it in the strip above
        // the card for ~100ms; v4 (near-transparent amber stops, clamped
        // inside the card) moved it to the card top and pinned it there for
        // the full ~500ms trickle, i.e. worse. Changing the gradient stops
        // did not change the colour, so the rect itself is the trigger on
        // this device, not its colours. Droplets alone carry the trickle.
        // Do not re-add a sweep rect here without an on-device check.
    }
}

/** Burnt tint left on the server card after a crash. */
@Composable
fun BoxScope.ScorchOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeoRed.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.28f),
                ),
            ),
            size = size,
        )
        // Charred specks along the bottom edge.
        for (i in 0 until 7) {
            val x = size.width * (0.1f + 0.8f * i / 6f)
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = (2.5f + (i % 3)).dp.toPx(),
                center = Offset(x, size.height - 6.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawRocketBody(flame: Float, smoking: Boolean) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    // Glow halo so the rocket reads at a glance, like the mockup.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeoTeal.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(cx, h * 0.45f),
            radius = w * 0.75f,
        ),
        radius = w * 0.75f,
        center = Offset(cx, h * 0.45f),
    )
    // Smoke puffs trail behind the rocket (above it when diving nose-down,
    // i.e. opposite the nose) — drawn first so the body covers their roots.
    if (smoking) {
        for (i in 0 until 3) {
            drawCircle(
                color = Color.Gray.copy(alpha = 0.30f - i * 0.08f),
                radius = (5f + i * 2.5f).dp.toPx(),
                center = Offset(cx, h * 0.12f - i * 7.dp.toPx()),
            )
        }
    }
    // Flame below the body: longer and brighter, mockup-style.
    if (flame > 0f) {
        val flamePath = Path().apply {
            moveTo(cx - w * 0.11f, h * 0.82f)
            quadraticTo(cx, h * 0.82f + 26.dp.toPx() * flame, cx + w * 0.11f, h * 0.82f)
            quadraticTo(cx, h * 0.90f, cx - w * 0.11f, h * 0.82f)
            close()
        }
        drawPath(
            path = flamePath,
            brush = Brush.verticalGradient(
                colors = listOf(NeoAmber, NeoAmber.copy(alpha = 0.55f), Color.Transparent),
                startY = h * 0.82f,
                endY = h * 0.82f + 26.dp.toPx() * flame,
            ),
        )
    }
    // Fins.
    val finPath = Path().apply {
        moveTo(cx - w * 0.16f, h * 0.62f)
        lineTo(cx - w * 0.30f, h * 0.84f)
        lineTo(cx - w * 0.16f, h * 0.84f)
        close()
        moveTo(cx + w * 0.16f, h * 0.62f)
        lineTo(cx + w * 0.30f, h * 0.84f)
        lineTo(cx + w * 0.16f, h * 0.84f)
        close()
    }
    drawPath(path = finPath, color = NeoViolet)
    // Body.
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(cx - w * 0.16f, h * 0.22f),
        size = Size(w * 0.32f, h * 0.62f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f),
    )
    // Nose cone (the tip).
    val nosePath = Path().apply {
        moveTo(cx - w * 0.16f, h * 0.24f)
        quadraticTo(cx, -h * 0.02f, cx + w * 0.16f, h * 0.24f)
        close()
    }
    drawPath(path = nosePath, color = NeoTeal)
    // Window.
    drawCircle(
        color = NeoCyan,
        radius = w * 0.08f,
        center = Offset(cx, h * 0.44f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = w * 0.08f,
        center = Offset(cx, h * 0.44f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
    )
}

/** The little rocket sprite, drawn nose-up and rotated by [rotationDeg]. */
@Composable
fun RocketSprite(
    rotationDeg: Float,
    flame: Float,
    smoking: Boolean,
    spriteSize: Dp = 68.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(spriteSize)) {
        rotate(degrees = rotationDeg, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawRocketBody(flame = flame, smoking = smoking)
        }
    }
}

/** One-shot crash burst: sparks + debris radiating from the impact point. */
@Composable
fun CrashBurst(
    modifier: Modifier = Modifier,
    burstSize: Dp = 110.dp,
) {
    // Animatable + LaunchedEffect: plays exactly once per composition, so
    // the burst fires on impact and settles instead of pulsing forever.
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progressAnim.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = LinearEasing))
    }
    val p = progressAnim.value.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(burstSize)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until 16) {
            val angle = (i.toFloat() / 16f) * 2f * PI.toFloat()
            val len = (14.dp.toPx() + 26.dp.toPx() * p) * (0.7f + (i % 3) * 0.15f)
            drawLine(
                color = if (i % 2 == 0) NeoAmber.copy(alpha = (1f - p) * 0.9f)
                else NeoRed.copy(alpha = (1f - p) * 0.9f),
                start = Offset(cx, cy),
                end = Offset(cx + kotlin.math.cos(angle) * len, cy + kotlin.math.sin(angle) * len),
                strokeWidth = 2.dp.toPx(),
            )
        }
        for (i in 0 until 5) {
            val angle = (i.toFloat() / 5f) * 2f * PI.toFloat() + 0.4f
            val len = 30.dp.toPx() * p
            drawCircle(
                color = Color.DarkGray.copy(alpha = (1f - p) * 0.8f),
                radius = 3.dp.toPx(),
                center = Offset(cx + kotlin.math.cos(angle) * len, cy + kotlin.math.sin(angle) * len),
            )
        }
    }
}

/** One-shot teal impact flash when the rocket lands in the orb. */
@Composable
fun LandingFlash(
    modifier: Modifier = Modifier,
    flashSize: Dp = 300.dp,
) {
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progressAnim.animateTo(1f, animationSpec = tween(durationMillis = 600, easing = LinearEasing))
    }
    val p = progressAnim.value.coerceIn(0f, 1f)
    // Fade in over the first 150 ms instead of popping at full brightness:
    // the old instant flash, landing in the same frame as the orb glow +
    // caption + pill flip, was the biggest single-frame change on screen,
    // and the phone's screen recorder dropped a video slice there (a
    // 2-frame pure-black band in recordings only — see the v2 video
    // analysis). Spreading the flash over several frames keeps the
    // recorder's encoder from tripping.
    val fade = (p / 0.25f).coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(flashSize)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeoTeal.copy(alpha = (1f - p) * 0.45f * fade),
                    NeoCyan.copy(alpha = (1f - p) * 0.18f * fade),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = size.width / 2f,
            ),
            radius = size.width / 2f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = NeoTeal.copy(alpha = (1f - p) * 0.8f * fade),
            radius = (20.dp.toPx() + 110.dp.toPx() * p),
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
        )
    }
}

/**
 * Overlay that positions the rocket sprite at [rocketPos] (in the root
 * Box's coordinates). Hover bobbing is drawn here so the phase machine
 * only owns the flight path itself. [trail] holds recent flight points
 * for the fading flame trail, like the mockup's arc.
 */
@Composable
fun BoxScope.RocketOverlay(
    phase: RocketPhase,
    rocketPos: Offset,
    rocketAngle: Float,
    crashKey: Int,
    trail: List<Offset>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    if (reduced) return
    val density = LocalDensity.current
    val spritePx = with(density) { 68.dp.toPx() }
    val bob = if (phase == RocketPhase.Hovering) {
        val transition = rememberInfiniteTransition(label = "rocketBob")
        val bobValue by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rocketBobValue",
        )
        (sin(bobValue * 2f * PI.toFloat()) * with(density) { 6.dp.toPx() })
    } else {
        0f
    }
    // Fading flame trail along the flown arc.
    if ((phase == RocketPhase.Flying || phase == RocketPhase.Landing) && trail.size > 1) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            trail.forEachIndexed { index, point ->
                val alpha = (index + 1).toFloat() / trail.size * 0.55f
                drawCircle(
                    color = NeoAmber.copy(alpha = alpha),
                    radius = (2.dp.toPx() + 3.dp.toPx() * alpha),
                    center = point,
                )
            }
        }
    }
    when (phase) {
        RocketPhase.Flying, RocketPhase.Hovering, RocketPhase.Landing -> {
            RocketSprite(
                rotationDeg = rocketAngle,
                flame = if (phase == RocketPhase.Hovering) 0.7f else 1f,
                smoking = false,
                modifier = modifier.offset {
                    IntOffset(
                        (rocketPos.x - spritePx / 2f).roundToInt(),
                        (rocketPos.y - spritePx / 2f + bob).roundToInt(),
                    )
                },
            )
        }
        RocketPhase.Flash -> {
            LandingFlash(
                modifier = modifier.offset {
                    IntOffset(
                        (rocketPos.x - with(density) { 150.dp.toPx() }).roundToInt(),
                        (rocketPos.y - with(density) { 150.dp.toPx() }).roundToInt(),
                    )
                },
            )
        }
        RocketPhase.Diving -> {
            RocketSprite(
                rotationDeg = rocketAngle,
                flame = 0.25f,
                smoking = true,
                modifier = modifier.offset {
                    IntOffset(
                        (rocketPos.x - spritePx / 2f).roundToInt(),
                        (rocketPos.y - spritePx / 2f).roundToInt(),
                    )
                },
            )
        }
        RocketPhase.Crashed -> {
            // Burst centred on the card, keyed so it replays per crash.
            key(crashKey) {
                CrashBurst(
                    modifier = modifier.offset {
                        IntOffset(
                            (rocketPos.x - with(density) { 55.dp.toPx() }).roundToInt(),
                            (rocketPos.y - with(density) { 55.dp.toPx() }).roundToInt(),
                        )
                    },
                )
            }
        }
        else -> Unit
    }
}
