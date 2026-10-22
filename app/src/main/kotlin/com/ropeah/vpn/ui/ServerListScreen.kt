package com.ropeah.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ropeah.vpn.R
import com.ropeah.vpn.data.Protocol
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.util.QrEncoder
import com.ropeah.vpn.vpn.VpnStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    session: com.ropeah.vpn.data.SessionState? = null,
    viewModel: VpnViewModel = viewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val active by viewModel.activeServer.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val latencyMap by viewModel.latencyMap.collectAsStateWithLifecycle()
    val pinging by viewModel.pinging.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    var contextTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var qrTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var scanTarget by remember { mutableStateOf<ServerConfig?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun showSnackbar(message: String) {
        coroutineScope.launch { snackbar.showSnackbar(message) }
    }

    fun copyUri(server: ServerConfig) {
        val clip = context.getSystemService<ClipboardManager>() ?: return
        clip.setPrimaryClip(ClipData.newPlainText("server-uri", server.rawUri))
        showSnackbar(context.getString(R.string.server_uri_copied))
    }

    fun shareUri(server: ServerConfig) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, server.rawUri)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.server_share_chooser))
        )
    }

    fun deleteWithUndo(server: ServerConfig, index: Int) {
        viewModel.deleteServer(server)
        coroutineScope.launch {
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.server_deleted, server.name),
                actionLabel = context.getString(R.string.server_deleted_undo),
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreServer(server, index)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (servers.isNotEmpty()) {
                FloatingActionButton(onClick = { addOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.servers_fab_add))
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (servers.isEmpty()) {
            EmptyServers(
                onAdd = { addOpen = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(servers, key = { it.id }) { server ->
                    val isActive = server.id == active?.id
                    val latencyMs = latencyMap[server.id]
                    val isPinging = pinging.contains(server.id)
                    ServerCard(
                        server = server,
                        isActive = isActive,
                        isConnected = isActive && status == VpnStatus.Connected,
                        isConnecting = isActive && status == VpnStatus.Connecting,
                        latencyMs = latencyMs,
                        isPinging = isPinging,
                        onTap = { viewModel.selectServer(server) },
                        onLongPress = { contextTarget = server },
                    )
                }
            }
        }
    }

    if (addOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ImportSheet(
            sheetState = sheetState,
            onDismiss = { addOpen = false },
            onImport = { uri ->
                val name = viewModel.importFromUri(uri)
                if (name != null) {
                    addOpen = false
                    showSnackbar(context.getString(R.string.servers_add_imported, name))
                    true
                } else {
                    false
                }
            },
            onError = { showSnackbar(context.getString(it)) },
        )
    }

    contextTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isPinging = pinging.contains(target.id)
        ServerContextSheet(
            server = target,
            isActive = target.id == active?.id,
            isPinging = isPinging,
            sheetState = sheetState,
            onDismiss = { contextTarget = null },
            onSetActive = {
                viewModel.selectServer(target)
                contextTarget = null
            },
            onShowQr = {
                qrTarget = target
                contextTarget = null
            },
            onCopy = {
                copyUri(target)
                contextTarget = null
            },
            onShare = {
                shareUri(target)
                contextTarget = null
            },
            onTestLatency = {
                viewModel.measureLatency(target)
                contextTarget = null
            },
            onScanIps = {
                scanTarget = target
                contextTarget = null
            },
            onDelete = {
                val index = servers.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
                contextTarget = null
                deleteWithUndo(target, index)
            },
        )
    }

    qrTarget?.let { target ->
        ServerQrDialog(server = target, onDismiss = { qrTarget = null })
    }

    scanTarget?.let { target ->
        IpScanDialog(
            server = target,
            viewModel = viewModel,
            onDismiss = { scanTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerCard(
    server: ServerConfig,
    isActive: Boolean,
    isConnected: Boolean,
    isConnecting: Boolean,
    latencyMs: Long?,
    isPinging: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val borderAlpha = if (isActive) 1f else 0f
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProtocolAvatar(
                protocol = server.protocol,
                accentAlpha = if (isActive) 1f else borderAlpha + 0.6f,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.name,
                        fontSize = 16.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        ActiveDot(
                            isConnected = isConnected,
                            isConnecting = isConnecting,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${protocolLabel(server.protocol)}  ·  ${server.address}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (latencyMs != null) {
                        Spacer(Modifier.width(8.dp))
                        LatencyBadge(latencyMs = latencyMs)
                    } else if (isPinging) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LatencyBadge(latencyMs: Long) {
    val (color, label) = when {
        latencyMs < 0L -> MaterialTheme.colorScheme.onSurfaceVariant to "timeout"
        latencyMs < 100 -> Color(0xFF4ADE80) to "${latencyMs}ms"
        latencyMs < 300 -> Color(0xFFFACC15) to "${latencyMs}ms"
        else -> Color(0xFFFF6B6B) to "${latencyMs}ms"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ProtocolAvatar(protocol: Protocol, accentAlpha: Float) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(accent.copy(alpha = 0.18f * accentAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = protocolInitial(protocol),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

@Composable
private fun ActiveDot(isConnected: Boolean, isConnecting: Boolean) {
    val transition = rememberInfiniteTransition(label = "serverActiveDot")
    val pulse by transition.animateFloat(
        initialValue = if (isConnecting) 0.3f else 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 700 else 1400,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "serverActiveDotPulse",
    )
    val color = when {
        isConnected -> Color(0xFF4ADE80)
        isConnecting -> Color(0xFFFACC15)
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            if (isConnected || isConnecting) {
                Box(
                    Modifier
                        .size(16.dp)
                        .alpha(0.30f * pulse)
                        .background(color, CircleShape),
                )
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.server_status_active),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun protocolInitial(protocol: Protocol): String = when (protocol) {
    Protocol.VMess -> "V"
    Protocol.VLESS -> "L"
    Protocol.Trojan -> "T"
    Protocol.Shadowsocks -> "S"
}

private fun protocolLabel(protocol: Protocol): String = when (protocol) {
    Protocol.VMess -> "VMess"
    Protocol.VLESS -> "VLESS"
    Protocol.Trojan -> "Trojan"
    Protocol.Shadowsocks -> "Shadowsocks"
}

@Composable
private fun EmptyServers(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.servers_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.servers_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.servers_fab_add))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerContextSheet(
    server: ServerConfig,
    isActive: Boolean,
    isPinging: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSetActive: () -> Unit,
    onShowQr: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onTestLatency: () -> Unit,
    onScanIps: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (!isActive) {
                ContextRow(Icons.Filled.Wifi, stringResource(R.string.server_action_set_active), onSetActive)
            }
            ContextRow(Icons.Filled.QrCode2, stringResource(R.string.server_action_show_qr), onShowQr)
            ContextRow(Icons.Filled.ContentCopy, stringResource(R.string.server_action_copy_uri), onCopy)
            ContextRow(Icons.Filled.Share, stringResource(R.string.server_action_share_uri), onShare)
            ContextRow(
                icon = Icons.Filled.Sync,
                label = stringResource(R.string.server_action_test_latency),
                onClick = onTestLatency,
                tint = if (isPinging) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                       else MaterialTheme.colorScheme.onSurface,
            )
            ContextRow(
                icon = Icons.Filled.Search,
                label = stringResource(R.string.server_action_scan_ips),
                onClick = onScanIps,
            )
            ContextRow(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.server_action_delete),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ContextRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun ServerQrDialog(server: ServerConfig, onDismiss: () -> Unit) {
    var bitmap by remember(server.id) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(server.id) {
        bitmap = QrEncoder.encode(server.rawUri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_qr_title, server.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${server.protocol}  ·  ${server.address}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.server_qr_dismiss))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onImport: (String) -> Boolean,
    onError: (Int) -> Unit,
) {
    val context = LocalContext.current
    var manualOpen by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        if (!onImport(text.trim())) onError(R.string.servers_add_invalid)
    }

    val cameraPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchScanner(scanLauncher, context)
        else onError(R.string.servers_add_camera_denied)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.servers_add_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            ImportOption(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(R.string.servers_add_scan_qr),
                subtitle = stringResource(R.string.servers_add_scan_qr_desc),
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchScanner(scanLauncher, context)
                    else cameraPerm.launch(android.Manifest.permission.CAMERA)
                },
            )

            Spacer(Modifier.height(8.dp))

            ImportOption(
                icon = Icons.Filled.ContentPaste,
                title = stringResource(R.string.servers_add_paste_clip),
                subtitle = stringResource(R.string.servers_add_paste_clip_desc),
                onClick = {
                    val text = readClipboard(context)
                    when {
                        text.isNullOrBlank() -> onError(R.string.servers_add_clipboard_empty)
                        !onImport(text.trim()) -> onError(R.string.servers_add_invalid)
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            ImportOption(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.servers_add_manual),
                subtitle = stringResource(R.string.servers_add_hint),
                onClick = { manualOpen = true },
            )

            Spacer(Modifier.height(20.dp))
        }
    }

    if (manualOpen) {
        ManualImportDialog(
            onAdd = { onImport(it) },
            onDismiss = { manualOpen = false },
        )
    }
}

@Composable
private fun ImportOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualImportDialog(
    onAdd: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.servers_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = uri,
                    onValueChange = {
                        uri = it
                        error = false
                    },
                    label = { Text(stringResource(R.string.servers_add_hint)) },
                    isError = error,
                )
                if (error) {
                    Text(
                        text = stringResource(R.string.servers_add_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onAdd(uri.trim())) onDismiss() else error = true
            }) {
                Text(stringResource(R.string.servers_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.servers_add_cancel))
            }
        },
    )
}

private fun launchScanner(
    launcher: androidx.activity.result.ActivityResultLauncher<ScanOptions>,
    context: Context,
) {
    val options = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt(context.getString(R.string.servers_add_scan_prompt))
        setBeepEnabled(false)
        setOrientationLocked(false)
        setBarcodeImageEnabled(false)
    }
    launcher.launch(options)
}

private fun readClipboard(context: Context): String? {
    val clip = context.getSystemService<ClipboardManager>() ?: return null
    val item = clip.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    return item.coerceToText(context)?.toString()
}
