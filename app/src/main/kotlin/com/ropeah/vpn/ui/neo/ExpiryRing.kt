package com.ropeah.vpn.ui.neo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ropeah.vpn.ui.theme.NeoAmber
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ExpiryRing(
    progress: Float,
    primaryText: String,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 178.dp,
    danger: Boolean = false,
    warning: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850),
        label = "expiryProgress",
    )
    val value = if (reduced) progress.coerceIn(0f, 1f) else animated
    val accentBrush = when {
        danger -> Brush.sweepGradient(listOf(NeoRed, NeoAmber, NeoRed))
        warning -> Brush.sweepGradient(listOf(NeoAmber, com.ropeah.vpn.ui.theme.NeoCyan, NeoAmber))
        else -> NeonSweep
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f - 7.dp.toPx()

            drawCircle(
                color = Color.White.copy(alpha = 0.065f),
                radius = radius,
                center = center,
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.035f),
                radius = radius - 12.dp.toPx(),
                center = center,
                style = Stroke(width = 1f),
            )

            if (value > 0f) {
                drawArc(
                    brush = accentBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * value,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
                )
                val angle = (-90f + 360f * value) * (PI.toFloat() / 180f)
                val end = Offset(
                    x = center.x + cos(angle) * radius,
                    y = center.y + sin(angle) * radius,
                )
                drawCircle(
                    color = if (danger) NeoRed.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.22f),
                    radius = 8.dp.toPx(),
                    center = end,
                )
                drawCircle(
                    color = if (danger) NeoRed else Color.White.copy(alpha = 0.92f),
                    radius = 3.dp.toPx(),
                    center = end,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = if (danger) {
                        Brush.linearGradient(listOf(NeoRed, NeoAmber))
                    } else {
                        NeonGradient
                    },
                ),
            )
            Spacer(Modifier.height(4.dp))
            MicroLabel(text = caption, color = NeoTextSecondary)
        }
    }
}
