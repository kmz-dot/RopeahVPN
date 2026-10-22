package com.ropeah.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.R
import com.ropeah.vpn.data.SessionState
import com.ropeah.vpn.ui.neo.ExpiryRing
import com.ropeah.vpn.ui.neo.GlassCard
import com.ropeah.vpn.ui.neo.HudTinyStyle
import com.ropeah.vpn.ui.neo.HudValueStyle
import com.ropeah.vpn.ui.neo.MicroLabel
import com.ropeah.vpn.ui.neo.NeoBadge
import com.ropeah.vpn.ui.neo.NeoGhostButton
import com.ropeah.vpn.ui.neo.NeoPrimaryButton
import com.ropeah.vpn.ui.neo.formatBytes
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L
private const val REFERENCE_WINDOW_MS = 30L * DAY_MS

@Composable
fun AccountScreen(
    session: SessionState,
    onLogout: () -> Unit,
    syncing: Boolean = false,
    syncMessage: String? = null,
    onSync: () -> Unit = {},
    viewModel: VpnViewModel = viewModel(),
) {
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val remaining = session.remainingMs(nowMs)
    val expiresLabel = remember(session.expiresAtMs) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.expiresAtMs))
    }
    val validatedLabel = remember(session.lastValidatedMs) {
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(session.lastValidatedMs))
    }
    val days = remaining / DAY_MS
    val hours = ((remaining + HOUR_MS - 1) / HOUR_MS).coerceAtLeast(0L)
    val progress = when {
        remaining <= 0L -> 0f
        remaining >= REFERENCE_WINDOW_MS -> 1f
        else -> remaining.toFloat() / REFERENCE_WINDOW_MS.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            highlighted = true,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MicroLabel(
                    text = stringResource(R.string.neo_account),
                    color = NeoTextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                ExpiryRing(
                    progress = progress,
                    primaryText = if (days > 0L) days.toString() else hours.toString(),
                    caption = stringResource(
                        if (days > 0L) R.string.neo_days_left else R.string.neo_hours_left,
                    ),
                    size = 176.dp,
                    danger = remaining <= 0L,
                    warning = remaining in 1..(3L * DAY_MS),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = session.username,
                    style = HudValueStyle.copy(
                        fontSize = 18.sp,
                        color = NeoTextPrimary,
                    ),
                )
                Spacer(Modifier.height(7.dp))
                NeoBadge(
                    text = if (remaining > 0L) {
                        stringResource(R.string.neo_active)
                    } else {
                        stringResource(R.string.home_expired)
                    },
                    filled = remaining > 0L,
                    accent = if (remaining > 0L) {
                        com.ropeah.vpn.ui.theme.NeoTeal
                    } else {
                        com.ropeah.vpn.ui.theme.NeoRed
                    },
                )
            }
        }

        InfoCard(
            rows = listOf(
                stringResource(R.string.account_username) to session.username,
                stringResource(R.string.account_expires) to expiresLabel,
                stringResource(R.string.account_config_version) to "v${session.configVersion}",
                stringResource(R.string.account_offline_note) to validatedLabel,
                stringResource(R.string.account_traffic) to buildString {
                    append("↑ ${formatBytes(traffic?.totalUp ?: 0L)}")
                    append("   ")
                    append("↓ ${formatBytes(traffic?.totalDown ?: 0L)}")
                },
            ),
        )

        NeoPrimaryButton(
            text = stringResource(R.string.account_sync_now),
            onClick = onSync,
            enabled = !syncing,
            loading = syncing,
            icon = Icons.Filled.Sync,
            modifier = Modifier.fillMaxWidth(),
        )

        if (syncMessage != null) {
            Text(
                text = syncMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NeoTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }

        NeoGhostButton(
            text = stringResource(R.string.account_logout),
            onClick = onLogout,
            danger = true,
            icon = Icons.AutoMirrored.Filled.Logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoTextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = HudTinyStyle,
                    color = NeoTextPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.35f),
                )
            }
            if (index != rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.06f)),
                )
            }
        }
    }
}
