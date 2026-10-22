package com.ropeah.vpn.ui.neo

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ropeah.vpn.ui.theme.JetBrainsMono
import com.ropeah.vpn.ui.theme.NeoAmber
import com.ropeah.vpn.ui.theme.NeoCyan
import com.ropeah.vpn.ui.theme.NeoGlassBorderSoft
import com.ropeah.vpn.ui.theme.NeoGreen
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import com.ropeah.vpn.ui.theme.NeoViolet
import java.util.Locale

/** The signature teal → cyan → violet gradient. */
val NeonGradient = Brush.linearGradient(
    colors = listOf(NeoTeal, NeoCyan, NeoViolet),
)

val NeonGradientVertical = Brush.verticalGradient(
    colors = listOf(NeoTeal, NeoCyan, NeoViolet),
)

/** Sweep version used by rings and arcs. */
val NeonSweep = Brush.sweepGradient(
    colors = listOf(NeoTeal, NeoCyan, NeoViolet, NeoTeal),
)

val GlassBorderGradient = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.18f),
        Color.White.copy(alpha = 0.07f),
        NeoGlassBorderSoft,
    ),
)

fun neonBorderGradient(alpha: Float = 0.55f): Brush = Brush.linearGradient(
    colors = listOf(
        NeoTeal.copy(alpha = alpha),
        NeoCyan.copy(alpha = alpha),
        NeoViolet.copy(alpha = alpha),
    ),
)

val HudValueStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp,
)

val HudLabelStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.1.sp,
)

val HudTinyStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    letterSpacing = 0.2.sp,
)

/**
 * Reads the system animator duration scale and keeps it current. When animations
 * are disabled the orb/rings still render, just without motion.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember {
        mutableStateOf(readAnimatorScale(context) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readAnimatorScale(context) == 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readAnimatorScale(context: android.content.Context): Float =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )

/** Decimal megabits per second, the way the design's HUD reads. */
fun formatRate(bytesPerSecond: Long): String {
    val mbps = bytesPerSecond.coerceAtLeast(0L) * 8.0 / 1_000_000.0
    return if (mbps >= 10.0) {
        String.format(Locale.US, "%.0f", mbps)
    } else {
        String.format(Locale.US, "%.1f", mbps)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

fun formatSession(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

fun latencyColor(latencyMs: Long?): Color = when {
    latencyMs == null || latencyMs < 0L -> NeoTextSecondary
    latencyMs < 100L -> NeoGreen
    latencyMs < 250L -> NeoAmber
    else -> NeoRed
}

val textPrimary = NeoTextPrimary
val textSecondary = NeoTextSecondary
