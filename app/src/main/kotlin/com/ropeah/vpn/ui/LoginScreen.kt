package com.ropeah.vpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.R
import com.ropeah.vpn.ui.neo.GlassCard
import com.ropeah.vpn.ui.neo.MicroLabel
import com.ropeah.vpn.ui.neo.NeoPrimaryButton
import com.ropeah.vpn.ui.neo.NeoTextField
import com.ropeah.vpn.ui.theme.NeoRed
import com.ropeah.vpn.ui.theme.NeoTeal
import com.ropeah.vpn.ui.theme.NeoTextPrimary
import com.ropeah.vpn.ui.theme.NeoTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoginScreen(viewModel: SessionViewModel = viewModel()) {
    val state by viewModel.loginState.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        keyboard?.hide()
        viewModel.login(username, pin)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp)
            .padding(top = 52.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.v2eon_full_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = NeoTeal.copy(alpha = 0.35f),
                    spotColor = NeoTeal.copy(alpha = 0.25f),
                )
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Fit,
        )

        Spacer(Modifier.height(22.dp))
        MicroLabel(
            text = stringResource(R.string.neo_access_terminal),
            color = NeoTextSecondary,
        )

        Spacer(Modifier.height(42.dp))

        NeoTextField(
            value = username,
            onValueChange = {
                username = it
                viewModel.clearLoginError()
            },
            label = stringResource(R.string.neo_identity),
            placeholder = stringResource(R.string.login_username),
            leadingIcon = Icons.Filled.Person,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))

        NeoTextField(
            value = pin,
            onValueChange = {
                pin = it
                viewModel.clearLoginError()
            },
            label = stringResource(R.string.neo_access_key),
            placeholder = stringResource(R.string.login_pin),
            leadingIcon = Icons.Filled.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.login_pin_hint),
            style = MaterialTheme.typography.bodySmall,
            color = NeoTextSecondary.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        )

        if (state.error != null) {
            Spacer(Modifier.height(18.dp))
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                borderBrush = androidx.compose.ui.graphics.SolidColor(NeoRed.copy(alpha = 0.42f)),
                fillBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        NeoRed.copy(alpha = 0.14f),
                        NeoRed.copy(alpha = 0.055f),
                    ),
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 11.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(NeoRed, CircleShape),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = formatLoginError(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoRed,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        NeoPrimaryButton(
            text = stringResource(R.string.login_activate),
            onClick = { submit() },
            enabled = !state.loading && username.isNotBlank(),
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            MicroLabel(
                text = stringResource(R.string.neo_no_account_micro),
                color = NeoTextSecondary.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun formatLoginError(state: LoginUiState): String {
    val base = state.error ?: return ""
    val expiredAt = state.expiredAtMs ?: return base
    val date = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(expiredAt))
    return "$base ($date)"
}
