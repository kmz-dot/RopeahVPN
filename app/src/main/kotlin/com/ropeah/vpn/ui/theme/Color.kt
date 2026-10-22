package com.ropeah.vpn.ui.theme

import androidx.compose.ui.graphics.Color

// ── Neo-HUD palette ────────────────────────────────────────────────────────
// Deep-space ink, neon teal → cyan → violet. The UI is dark-only by design.
val NeoInkTop = Color(0xFF05090F)
val NeoInkMid = Color(0xFF070D16)
val NeoInkBottom = Color(0xFF0A1220)

val NeoTextPrimary = Color(0xFFEAF6FF)
val NeoTextSecondary = Color(0xFF8FA8BF)
val NeoTextTertiary = Color(0xFF5E7488)

val NeoTeal = Color(0xFF14E0B0)
val NeoGreen = Color(0xFF2FE6A0)
val NeoCyan = Color(0xFF22C8FF)
val NeoViolet = Color(0xFF8B7CFF)
val NeoAmber = Color(0xFFFFB648)
val NeoRed = Color(0xFFFF6470)

val NeoGlassFill = Color(0x0FFFFFFF)          // white @ 6%
val NeoGlassFillStrong = Color(0x14FFFFFF)    // white @ 8%
val NeoGlassBorder = Color(0x24FFFFFF)        // white @ 14%
val NeoGlassBorderSoft = Color(0x14FFFFFF)    // white @ 8%
val NeoSurface = Color(0xFF0B1522)
val NeoSurfaceRaised = Color(0xFF101C2A)
val NeoOutline = Color(0xFF243546)

// Legacy aliases kept so any untouched screen still resolves to the new palette.
val RopeahPrimary = NeoTeal
val RopeahOnPrimary = Color(0xFF031218)
val RopeahPrimaryContainer = Color(0xFF0B3B36)
val RopeahOnPrimaryContainer = NeoTextPrimary
val RopeahBackground = NeoInkTop
val RopeahSurface = NeoSurface
val RopeahOnSurface = NeoTextPrimary
val RopeahError = NeoRed
