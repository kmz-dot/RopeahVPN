package com.ropeah.vpn.ui.neo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ropeah.vpn.R
import com.ropeah.vpn.ui.theme.NeoCyan
import com.ropeah.vpn.ui.theme.NeoGlassBorderSoft
import com.ropeah.vpn.ui.theme.NeoGlassFill
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import com.ropeah.vpn.ui.theme.NeoTextTertiary
import com.ropeah.vpn.ui.theme.NeoViolet
import com.ropeah.vpn.vpn.VpnStatus
import java.util.Locale

/** Translucent glass panel with a luminous 1dp border. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderBrush = if (highlighted) neonBorderGradient(0.62f) else GlassBorderGradient
    val fill = if (highlighted) {
        Brush.verticalGradient(
            listOf(
                NeoTeal.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.045f),
                NeoViolet.copy(alpha = 0.055f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.070f), Color.White.copy(alpha = 0.032f)),
        )
    }

    val base = modifier
        .then(
            if (highlighted) {
                Modifier.shadow(
                    elevation = 22.dp,
                    shape = shape,
                    ambientColor = NeoTeal.copy(alpha = 0.32f),
                    spotColor = NeoCyan.copy(alpha = 0.24f),
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(fill, shape)
        .border(if (highlighted) 1.2.dp else 1.dp, borderBrush, shape)

    val interactive = when {
        onClick != null && onLongClick != null -> base.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
        onClick != null -> base.clickable(onClick = onClick)
        else -> base
    }

    Column(
        modifier = interactive.padding(contentPadding),
        content = content,
    )
}

@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NeoTextSecondary,
) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun NeoSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NeonGradientVertical),
        )
        Spacer(Modifier.width(9.dp))
        MicroLabel(text = text, color = NeoTextSecondary)
    }
}

@Composable
fun HudChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NeoTextPrimary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NeoGlassFill)
            .border(1.dp, GlassBorderGradient, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = HudLabelStyle,
            color = NeoTextSecondary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = HudValueStyle,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NeoBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = NeoTeal,
    filled: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (filled) NeonGradient else SolidColor(accent.copy(alpha = 0.12f)),
            )
            .border(1.dp, accent.copy(alpha = 0.38f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(Locale.US),
            style = HudLabelStyle,
            color = if (filled) Color(0xFF04121A) else accent,
        )
    }
}

@Composable
fun NeoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val active = enabled && !loading
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = 20.dp,
                        shape = shape,
                        ambientColor = NeoTeal.copy(alpha = 0.42f),
                        spotColor = NeoCyan.copy(alpha = 0.30f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                if (active) NeonGradient else SolidColor(Color.White.copy(alpha = 0.055f)),
            )
            .border(
                width = 1.dp,
                brush = if (active) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.16f),
                        ),
                    )
                } else {
                    GlassBorderGradient
                },
                shape = shape,
            )
            .clickable(enabled = active, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF04121A),
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) Color(0xFF04121A) else NeoTextTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text.uppercase(Locale.US),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    letterSpacing = 1.2.sp,
                ),
                color = if (active) Color(0xFF04121A) else NeoTextTertiary,
            )
        }
    }
}

@Composable
fun NeoGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
) {
    val accent = if (danger) com.ropeah.vpn.ui.theme.NeoRed else NeoTextSecondary
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(
                if (danger) {
                    com.ropeah.vpn.ui.theme.NeoRed.copy(alpha = 0.08f)
                } else {
                    Color.White.copy(alpha = 0.045f)
                },
            )
            .border(
                1.dp,
                if (danger) {
                    SolidColor(com.ropeah.vpn.ui.theme.NeoRed.copy(alpha = 0.28f))
                } else {
                    GlassBorderGradient
                },
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.1.sp),
            color = accent,
        )
    }
}

@Composable
fun NeoIconPill(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(NeoGlassFill)
            .border(1.dp, GlassBorderGradient, CircleShape)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = NeoTeal,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) NeoTeal else NeoTextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun NeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val border = when {
        isError -> SolidColor(com.ropeah.vpn.ui.theme.NeoRed.copy(alpha = 0.65f))
        focused -> neonBorderGradient(0.62f)
        else -> GlassBorderGradient
    }

    Column(modifier = modifier.fillMaxWidth()) {
        MicroLabel(text = label, color = if (isError) com.ropeah.vpn.ui.theme.NeoRed else NeoTextSecondary)
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = NeoTextPrimary),
            cursorBrush = NeonGradient,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.065f),
                                    Color.White.copy(alpha = 0.035f),
                                ),
                            ),
                        )
                        .border(1.dp, border, shape)
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = if (focused) NeoTeal else NeoTextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = NeoTextTertiary,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
fun NeoStatusPill(
    status: VpnStatus,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val pulse = if (reduced || status != VpnStatus.Connecting) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "statusPulse")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 820),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "statusPulseValue",
        )
        value
    }

    val accent = when (status) {
        VpnStatus.Connected -> NeoTeal
        VpnStatus.Connecting -> NeoCyan
        VpnStatus.Disconnected -> NeoTextSecondary
    }
    val label = when (status) {
        VpnStatus.Connected -> stringResource(R.string.neo_secure_link)
        VpnStatus.Connecting -> stringResource(R.string.status_connecting)
        VpnStatus.Disconnected -> stringResource(R.string.status_disconnected)
    }
    val shape = CircleShape

    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (status == VpnStatus.Connected) {
                    Brush.linearGradient(
                        listOf(
                            NeoTeal.copy(alpha = 0.12f),
                            NeoCyan.copy(alpha = 0.08f),
                            NeoViolet.copy(alpha = 0.08f),
                        ),
                    )
                } else {
                    SolidColor(Color.White.copy(alpha = 0.045f))
                },
            )
            .border(
                1.dp,
                if (status == VpnStatus.Connected) {
                    neonBorderGradient(0.38f)
                } else {
                    GlassBorderGradient
                },
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (status == VpnStatus.Connected) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape),
                )
            }
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(pulse)
                    .background(accent, CircleShape),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = if (status == VpnStatus.Disconnected) NeoTextSecondary else NeoTextPrimary,
        )
    }
}

@Composable
fun NeoLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = s * 0.48f,
            center = center,
            style = Stroke(width = 1f),
        )
        drawCircle(
            brush = NeonSweep,
            radius = s * 0.37f,
            center = center,
            style = Stroke(width = s * 0.075f, cap = StrokeCap.Round),
        )
        drawArc(
            brush = NeonGradient,
            startAngle = 200f,
            sweepAngle = 92f,
            useCenter = false,
            topLeft = Offset(s * 0.17f, s * 0.17f),
            size = Size(s * 0.66f, s * 0.66f),
            style = Stroke(width = s * 0.035f, cap = StrokeCap.Round),
        )
        drawCircle(
            color = NeoTextPrimary.copy(alpha = 0.94f),
            radius = s * 0.085f,
            center = center,
        )
    }
}
