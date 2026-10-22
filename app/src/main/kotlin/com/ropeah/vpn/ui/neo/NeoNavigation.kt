package com.ropeah.vpn.ui.neo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import java.util.Locale

data class NeoNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Transparent command bar: logo mark, wordmark and the current section. */
@Composable
fun NeoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(62.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoLogoMark(size = 25.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "V2EON",
            style = MaterialTheme.typography.titleLarge.copy(brush = NeonGradient),
            fontSize = 21.sp,
            letterSpacing = 1.1.sp,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.14f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = NeoTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Floating glass pill navigation. Four destinations, active item gets the neon pill. */
@Composable
fun NeoBottomBar(
    items: List<NeoNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xD90A1420),
                            Color(0xC7070D16),
                        ),
                    ),
                )
                .border(1.dp, GlassBorderGradient, RoundedCornerShape(30.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(item.route) },
                            role = Role.Tab,
                        )
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(30.dp)
                            .then(
                                if (selected) {
                                    Modifier
                                        .shadow(
                                            elevation = 10.dp,
                                            shape = RoundedCornerShape(15.dp),
                                            ambientColor = NeoTeal.copy(alpha = 0.55f),
                                            spotColor = NeoTeal.copy(alpha = 0.40f),
                                        )
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(NeonGradient)
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) Color(0xFF04121A) else NeoTextSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 0.8.sp,
                        ),
                        color = if (selected) NeoTextPrimary else NeoTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
