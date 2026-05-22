package com.ropeah.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ropeah.vpn.vpn.TrafficSample
import kotlinx.coroutines.delay
import kotlin.math.max

private const val SPARKLINE_CAPACITY = 60

@Composable
fun Dashboard(
    connectedSince: Long?,
    traffic: TrafficSample?,
    modifier: Modifier = Modifier,
) {
    val downHistory = remember { ArrayDeque<Long>(SPARKLINE_CAPACITY) }
    val upHistory = remember { ArrayDeque<Long>(SPARKLINE_CAPACITY) }

    LaunchedEffect(traffic?.timestampMs) {
        traffic ?: return@LaunchedEffect
        if (downHistory.size == SPARKLINE_CAPACITY) downHistory.removeFirst()
        if (upHistory.size == SPARKLINE_CAPACITY) upHistory.removeFirst()
        downHistory.addLast(traffic.downlinkBps)
        upHistory.addLast(traffic.uplinkBps)
    }

    var elapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(connectedSince) {
        if (connectedSince == null) {
            elapsedMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            elapsedMs = System.currentTimeMillis() - connectedSince
            delay(500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "SESSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatElapsed(elapsedMs),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            LatencyBadge(latencyMs = traffic?.latencyMs ?: TrafficSample.LATENCY_UNKNOWN)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpeedCell(
                label = "↓ DOWN",
                bps = traffic?.downlinkBps ?: 0L,
                total = traffic?.totalDown ?: 0L,
                color = MaterialTheme.colorScheme.primary,
            )
            SpeedCell(
                label = "↑ UP",
                bps = traffic?.uplinkBps ?: 0L,
                total = traffic?.totalUp ?: 0L,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(Modifier.height(14.dp))

        Sparkline(
            down = downHistory,
            up = upHistory,
            downColor = MaterialTheme.colorScheme.primary,
            upColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
    }
}

@Composable
private fun SpeedCell(label: String, bps: Long, total: Long, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatBps(bps),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        Text(
            text = "total ${formatBytes(total)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LatencyBadge(latencyMs: Long) {
    val (label, dotColor) = when {
        latencyMs == TrafficSample.LATENCY_UNKNOWN -> "— ms" to MaterialTheme.colorScheme.onSurfaceVariant
        latencyMs < 0 -> "fail" to MaterialTheme.colorScheme.error
        latencyMs < 80 -> "${latencyMs} ms" to Color(0xFF4ADE80)
        latencyMs < 200 -> "${latencyMs} ms" to Color(0xFFFACC15)
        else -> "${latencyMs} ms" to Color(0xFFF87171)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Sparkline(
    down: ArrayDeque<Long>,
    up: ArrayDeque<Long>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (down.isEmpty() && up.isEmpty()) return@Canvas

        val peak = max(
            down.maxOrNull() ?: 0L,
            up.maxOrNull() ?: 0L,
        ).coerceAtLeast(1L).toFloat()

        fun plot(values: ArrayDeque<Long>, color: Color) {
            if (values.size < 2) return
            val stepX = w / (SPARKLINE_CAPACITY - 1).toFloat()
            val offset = (SPARKLINE_CAPACITY - values.size).toFloat()
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = (offset + i) * stepX
                val y = h - (v / peak) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = color, style = Stroke(width = 3f))
        }

        // baseline
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, h - 1f),
            end = Offset(w, h - 1f),
            strokeWidth = 1f,
        )

        plot(down, downColor)
        plot(up, upColor)
    }
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun formatBps(bps: Long): String {
    if (bps <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var v = bps.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (v >= 100 || i == 0) "%.0f %s".format(v, units[i])
    else "%.1f %s".format(v, units[i])
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (v >= 100 || i == 0) "%.0f %s".format(v, units[i])
    else "%.1f %s".format(v, units[i])
}
