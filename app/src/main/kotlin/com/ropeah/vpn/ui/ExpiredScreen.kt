package com.ropeah.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ropeah.vpn.R
import com.ropeah.vpn.data.SessionState
import com.ropeah.vpn.ui.neo.ConnectOrb
import com.ropeah.vpn.ui.neo.GlassCard
import com.ropeah.vpn.ui.neo.MicroLabel
import com.ropeah.vpn.ui.neo.NeoGhostButton
import com.ropeah.vpn.ui.neo.NeoPrimaryButton
import com.ropeah.vpn.ui.neo.NeoLogoMark
import com.ropeah.vpn.ui.theme.NeoAmber
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import com.ropeah.vpn.vpn.VpnStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpiredScreen(
    session: SessionState,
    checking: Boolean,
    suspended: Boolean = false,
    message: String? = null,
    onCheckAgain: () -> Unit,
    onLogout: () -> Unit,
) {
    val expiredLabel = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(session.expiresAtMs))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeoLogoMark(size = 46.dp)
        Spacer(Modifier.height(10.dp))
        MicroLabel(
            text = "V2EON // ACCESS CONTROL",
            color = NeoTextSecondary,
        )

        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .size(214.dp)
                .clip(CircleShape)
                .background(NeoRed.copy(alpha = 0.045f))
                .border(1.dp, SolidColor(NeoRed.copy(alpha = 0.28f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ConnectOrb(
                status = VpnStatus.Disconnected,
                enabled = false,
                onClick = {},
                locked = true,
                size = 202.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        MicroLabel(
            text = stringResource(R.string.neo_access_locked),
            color = NeoRed,
        )

        Spacer(Modifier.height(22.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderBrush = SolidColor(NeoRed.copy(alpha = 0.38f)),
            fillBrush = Brush.verticalGradient(
                listOf(
                    NeoRed.copy(alpha = 0.12f),
                    NeoAmber.copy(alpha = 0.045f),
                    Color.White.copy(alpha = 0.02f),
                ),
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeoRed.copy(alpha = 0.12f))
                        .border(1.dp, NeoRed.copy(alpha = 0.32f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockClock,
                        contentDescription = null,
                        tint = NeoRed,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(
                        if (suspended) R.string.suspended_title else R.string.neo_account_expired
                    ).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeoTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (suspended) {
                        stringResource(R.string.suspended_body)
                    } else {
                        stringResource(R.string.expired_body, expiredLabel)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoTextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.06f)),
                )
                Spacer(Modifier.height(14.dp))
                MicroLabel(
                    text = stringResource(R.string.neo_renew_contact),
                    color = NeoAmber,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        NeoPrimaryButton(
            text = stringResource(R.string.neo_check_access),
            onClick = onCheckAgain,
            enabled = !checking,
            loading = checking,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        NeoGhostButton(
            text = stringResource(R.string.expired_logout),
            onClick = onLogout,
            enabled = !checking,
            danger = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
