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
import kotlin.math.cos
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

/**
 * Amber droplets trickling down inside the server card — v2 (web mockup v2,
 * user-approved 2026-11-04): droplets fall with gravity (accelerating and
 * stretching as they speed up), sway slightly, splash into sparkles where
 * they land, and pool into a glowing amber charge level that rises through
 * the card over the 1300 ms trickle, under a soft pulsing card hum.
 *
 * NOTE (2026-11-03, still binding): the amber sweep band that used to
 * travel down the card is REMOVED. On the user's Infinix it rendered as a
 * solid pure-black rect: v3 (transparent gradient stops, sliding in from
 * above the card) showed it in the strip above the card for ~100 ms; v4
 * (near-transparent amber stops, clamped inside the card) pinned it at the
 * card top for the full trickle, i.e. worse. Changing the gradient stops
 * did not change the colour, so a gradient-filled rect is the trigger on
 * this device. The v2 charge fill below is a SOLID low-alpha colour (no
 * gradient Brush) and the hum is a stroke, both different draw paths —
 * but if the black bar ever returns on-device, remove the charge fill
 * first. Do not re-add a sweep band here without an on-device check.
 */
@Composable
fun BoxScope.TrickleOverlay(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    // One-shot charge clock: the trickle phase lasts 1300 ms, so the
    // charge level rises exactly once per trickle.
    val chargeAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        chargeAnim.animateTo(1f, animationSpec = tween(durationMillis = 1300, easing = LinearEasing))
    }
    val charge = chargeAnim.value.coerceIn(0f, 1f)
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
    // Soft card hum: a pulsing amber border while trickling.
    val hum by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trickleHum",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        // Charge pool: solid low-alpha amber rising from the card bottom.
        if (charge > 0f) {
            val fillH = size.height * charge
            drawRect(
                color = NeoAmber.copy(alpha = 0.12f),
                topLeft = Offset(0f, size.height - fillH),
                size = Size(size.width, fillH),
            )
            // Bright top edge of the rising charge.
            drawLine(
                color = NeoAmber.copy(alpha = 0.45f * charge),
                start = Offset(0f, size.height - fillH),
                end = Offset(size.width, size.height - fillH),
                strokeWidth = 2.dp.toPx(),
            )
        }
        // Card hum border.
        drawRoundRect(
            color = NeoAmber.copy(alpha = hum),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
        val dropletCount = if (reduced) 6 else 12
        for (i in 0 until dropletCount) {
            val phase = i.toFloat() / dropletCount
            val lp = (progress + phase) % 1f
            // Gravity: accelerating fall, stretching as it speeds up.
            val y = size.height * lp * lp
            val sway = sin(lp * 2f * PI.toFloat() + phase * 2f * PI.toFloat()) * 4.dp.toPx()
            val x = size.width * (0.08f + 0.84f * ((i * 37 % dropletCount).toFloat() / dropletCount)) + sway
            val alpha = sin(PI.toFloat() * lp.coerceAtMost(1f)).coerceIn(0f, 1f)
            drawCircle(
                color = NeoAmber.copy(alpha = alpha),
                radius = 4.dp.toPx() + 2.dp.toPx() * lp,
                center = Offset(x, y),
            )
            drawLine(
                color = NeoAmber.copy(alpha = alpha * 0.7f),
                start = Offset(x, y - (8.dp.toPx() + 16.dp.toPx() * lp)),
                end = Offset(x, y),
                strokeWidth = 2.dp.toPx(),
            )
            // Splash sparkles where the droplet lands.
            if (lp > 0.88f) {
                val splash = (1f - (lp - 0.88f) / 0.12f).coerceIn(0f, 1f)
                for (k in 0 until 3) {
                    drawCircle(
                        color = NeoAmber.copy(alpha = splash * 0.8f),
                        radius = 2.dp.toPx(),
                        center = Offset(
                            x + (k - 1) * 6.dp.toPx(),
                            size.height - 4.dp.toPx() - splash * 6.dp.toPx(),
                        ),
                    )
                }
            }
        }
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
    // Sputter smoke trails behind the rocket (above it when diving
    // nose-down, i.e. opposite the nose) — drawn first so the body covers
    // their roots. Five puffs, alternating sides, for a dying-engine look.
    if (smoking) {
        for (i in 0 until 5) {
            drawCircle(
                color = Color.Gray.copy(alpha = 0.32f - i * 0.05f),
                radius = (5f + i * 2.5f).dp.toPx(),
                center = Offset(
                    cx + (if (i % 2 == 0) 3.dp.toPx() else -3.dp.toPx()),
                    h * 0.12f - i * 7.dp.toPx(),
                ),
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
    // Looping clocks. Their values are only read in the flight phases, so
    // they cost no recompositions while the overlay is idle.
    val bobTransition = rememberInfiniteTransition(label = "rocketBob")
    val bobValue by bobTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rocketBobValue",
    )
    val bob = if (phase == RocketPhase.Hovering) {
        sin(bobValue * 2f * PI.toFloat()) * with(density) { 6.dp.toPx() }
    } else {
        0f
    }
    // Hover sway: sideways drift on a slower cycle than the vertical bob.
    val sway = if (phase == RocketPhase.Hovering) {
        sin(bobValue * 2f * PI.toFloat() * 0.7f) * with(density) { 4.dp.toPx() }
    } else {
        0f
    }
    val flickerTransition = rememberInfiniteTransition(label = "rocketFlame")
    val flickerValue by flickerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 170, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rocketFlameValue",
    )
    val flicker = if (phase == RocketPhase.Flying || phase == RocketPhase.Hovering ||
        phase == RocketPhase.Landing
    ) {
        0.8f + 0.2f * sin(flickerValue * 2f * PI.toFloat())
    } else {
        1f
    }
    // Banking cap from the mockup: the nose follows the curve, but never
    // banks past ±34°. The dive keeps its true nose-down angle.
    val flightAngle = if (phase == RocketPhase.Diving) rocketAngle else rocketAngle.coerceIn(-34f, 34f)
    // Fading flame trail along the flown arc + liftoff smoke puff.
    if ((phase == RocketPhase.Flying || phase == RocketPhase.Landing) && trail.size > 1) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (phase == RocketPhase.Flying) {
                // Ignition puff at the launch point, fading as the flight progresses.
                val puffAlpha = (1f - trail.size / 8f).coerceIn(0f, 1f) * 0.35f
                val origin = trail.first()
                for (i in 0 until 3) {
                    drawCircle(
                        color = Color.Gray.copy(alpha = puffAlpha * (1f - i * 0.25f)),
                        radius = 6.dp.toPx() + trail.size * 2.dp.toPx() + i * 3.dp.toPx(),
                        center = Offset(origin.x + (i - 1) * 8.dp.toPx(), origin.y),
                    )
                }
            }
            trail.forEachIndexed { index, point ->
                val alpha = (index + 1).toFloat() / trail.size * 0.55f
                val drift = sin(index * 1.3f) * 3.dp.toPx()
                drawCircle(
                    color = NeoAmber.copy(alpha = alpha),
                    radius = (2.dp.toPx() + 3.dp.toPx() * alpha),
                    center = Offset(point.x + drift, point.y),
                )
            }
        }
    }
    when (phase) {
        RocketPhase.Flying, RocketPhase.Hovering, RocketPhase.Landing -> {
            // Landing anticipation: brief pull-back against the approach
            // direction before the dart, like the mockup.
            val pullAnim = remember { Animatable(0f) }
            if (phase == RocketPhase.Landing) {
                LaunchedEffect(Unit) {
                    pullAnim.animateTo(1f, animationSpec = tween(durationMillis = 120, easing = LinearEasing))
                }
            }
            val pull = if (phase == RocketPhase.Landing) {
                (1f - pullAnim.value.coerceIn(0f, 1f)) * with(density) { 10.dp.toPx() }
            } else {
                0f
            }
            // Nose direction from the flight angle (nose-up sprite rotated).
            val noseRad = Math.toRadians(flightAngle.toDouble())
            val nose = Offset(sin(noseRad).toFloat(), -cos(noseRad).toFloat())
            RocketSprite(
                rotationDeg = flightAngle,
                flame = if (phase == RocketPhase.Hovering) 0.7f * flicker else 1f * flicker,
                smoking = false,
                modifier = modifier.offset {
                    IntOffset(
                        (rocketPos.x - spritePx / 2f + sway + (-nose.x * pull)).roundToInt(),
                        (rocketPos.y - spritePx / 2f + bob + (-nose.y * pull)).roundToInt(),
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
                rotationDeg = rocketAngle, // true nose-down angle, unclamped
                flame = 0f, // nozzle dead: sputter smoke only, like the mockup
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
