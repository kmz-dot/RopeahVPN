package com.ropeah.vpn.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkScheme = darkColorScheme(
    primary = RopeahPrimary,
    onPrimary = RopeahOnPrimary,
    primaryContainer = RopeahPrimaryContainer,
    onPrimaryContainer = RopeahOnPrimaryContainer,
    background = RopeahBackground,
    surface = RopeahSurface,
    onSurface = RopeahOnSurface,
    error = RopeahError,
)

@Composable
fun RopeahTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        else -> DarkScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = RopeahTypography,
        content = content,
    )
}
