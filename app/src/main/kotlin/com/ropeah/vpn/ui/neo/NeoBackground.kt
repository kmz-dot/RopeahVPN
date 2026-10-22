package com.ropeah.vpn.ui.neo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ropeah.vpn.ui.theme.NeoCyan
import com.ropeah.vpn.ui.theme.NeoInkBottom
import com.ropeah.vpn.ui.theme.NeoInkMid
import com.ropeah.vpn.ui.theme.NeoInkTop
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoViolet
import kotlin.random.Random

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
)

/**
 * Deep-space canvas: ink gradient, aurora mesh, faint grid and starfield.
 * It is deliberately static so it costs one draw pass and no continuous frames.
 */
@Composable
fun NeoBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val stars = remember {
        val random = Random(0x5EED)
        List(84) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.5f + random.nextFloat() * 1.25f,
                alpha = 0.08f + random.nextFloat() * 0.22f,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(NeoInkTop, NeoInkMid, NeoInkBottom),
                    ),
                )

                // Aurora mesh — low alpha, layered radial fields.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(NeoTeal.copy(alpha = 0.17f), Color.Transparent),
                        center = Offset(size.width * 0.06f, size.height * 0.02f),
                        radius = size.width * 0.92f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(NeoCyan.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 1.02f, size.height * 0.30f),
                        radius = size.width * 0.72f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(NeoViolet.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 1.02f),
                        radius = size.width * 0.84f,
                    ),
                )

                // Faint engineering grid.
                val step = 56.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.022f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.018f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += step
                }

                // Starfield.
                stars.forEach { star ->
                    drawCircle(
                        color = Color.White.copy(alpha = star.alpha),
                        radius = star.radius.dp.toPx(),
                        center = Offset(
                            x = star.x * size.width,
                            y = star.y * size.height,
                        ),
                    )
                }

                // Edge vignette keeps text contrast high at the extremes.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                        center = Offset(size.width * 0.5f, size.height * 0.43f),
                        radius = size.maxDimension * 0.76f,
                    ),
                )
            },
        content = content,
    )
}
