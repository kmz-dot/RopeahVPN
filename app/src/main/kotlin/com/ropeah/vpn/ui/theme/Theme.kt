package com.ropeah.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeoColorScheme = darkColorScheme(
    primary = NeoTeal,
    onPrimary = Color(0xFF031218),
    primaryContainer = Color(0xFF0B3B36),
    onPrimaryContainer = NeoTextPrimary,
    inversePrimary = NeoCyan,
    secondary = NeoCyan,
    onSecondary = Color(0xFF021318),
    secondaryContainer = Color(0xFF0A2C3D),
    onSecondaryContainer = NeoTextPrimary,
    tertiary = NeoViolet,
    onTertiary = Color(0xFF0B0718),
    tertiaryContainer = Color(0xFF211C3D),
    onTertiaryContainer = NeoTextPrimary,
    background = NeoInkTop,
    onBackground = NeoTextPrimary,
    surface = NeoSurface,
    onSurface = NeoTextPrimary,
    surfaceVariant = NeoSurfaceRaised,
    onSurfaceVariant = NeoTextSecondary,
    surfaceTint = NeoTeal,
    inverseSurface = NeoTextPrimary,
    inverseOnSurface = NeoInkTop,
    outline = NeoOutline,
    outlineVariant = Color(0xFF182534),
    error = NeoRed,
    onError = Color(0xFF1A0508),
    errorContainer = Color(0xFF3A1017),
    onErrorContainer = Color(0xFFFFD9DC),
    scrim = Color.Black,
)

/**
 * V2EON's dark-only Neo-HUD theme. Dynamic colour is intentionally ignored:
 * the aurora/neon identity is part of the product, not the wallpaper.
 */
@Composable
fun RopeahTheme(
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NeoColorScheme,
        typography = NeoTypography,
        content = content,
    )
}
