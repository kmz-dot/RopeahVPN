package com.ropeah.vpn.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ropeah.vpn.R
import com.ropeah.vpn.data.IpScanner
import com.ropeah.vpn.data.ServerConfig
import com.ropeah.vpn.ui.VpnViewModel.IpScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IpScanDialog(
    server: ServerConfig,
    viewModel: VpnViewModel,
    onDismiss: () -> Unit,
) {
    val scanState by viewModel.ipScanState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // File picker for loading IPs
    var ipList by remember { mutableStateOf<List<String>?>(null) }
    var ipFileName by remember { mutableStateOf<String?>(null) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (list, name) = withContext(Dispatchers.IO) {
                    val name = resolveFileName(context, uri) ?: "ip_list.txt"
                    val lines = readLinesFromUri(context, uri)
                    lines to name
                }
                ipList = list
                ipFileName = name
            }
        }
    }

    // File saver for results
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val state = scanState
                if (state is IpScanState.Completed) {
                    withContext(Dispatchers.IO) {
                        writeResultsToUri(context, uri, state.results, server)
                    }
                }
            }
        }
    }

    val idle = scanState is IpScanState.Idle && ipList == null
    val readyToScan = scanState is IpScanState.Idle && ipList != null
    val running = scanState is IpScanState.Running
    val completed = scanState is IpScanState.Completed
    val error = scanState is IpScanState.Error

    AlertDialog(
        onDismissRequest = {
            if (running) viewModel.cancelIpScan()
            else {
                viewModel.resetIpScan()
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Scan IPs: ${server.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                // Server info
                Text(
                    text = "${server.protocol}  ·  ${server.address}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                when {
                    idle -> {
                        // ── Idle: pick a file ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.FileOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Select a .txt file with one IP per line",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "The app will swap the server address with each IP, connect, and test the tunnel.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = {
                                    openFile.launch(arrayOf("text/plain", "*/*"))
                                }) {
                                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pick IP list file")
                                }
                            }
                        }
                    }

                    readyToScan -> {
                        // ── File loaded, ready to scan ──
                        val count = ipList!!.size
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(16.dp),
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = ipFileName ?: "IP list",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "$count IPs loaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Port: ${server.port} · Protocol: ${server.protocol}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                ipList?.let { viewModel.startIpScan(server, it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Start Scan")
                        }
                    }

                    running -> {
                        // ── Scanning in progress ──
                        val progress = (scanState as IpScanState.Running).progress
                        val pct = if (progress.total > 0)
                            progress.current.toFloat() / progress.total.toFloat()
                        else 0f

                        Spacer(Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${progress.current} / ${progress.total}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "✓ ${progress.successCount}  ✗ ${progress.failCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Current IP being tested
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Testing ${progress.currentIp}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Live results
                        if (progress.results.isNotEmpty()) {
                            Text(
                                text = "Results so far:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(progress.results.reversed(), key = { it.ip + it.latencyMs }) { result ->
                                    ScanResultRow(result)
                                }
                            }
                        }
                    }

                    completed -> {
                        // ── Scan complete ──
                        val state = scanState as IpScanState.Completed
                        val validCount = state.results.count { it.success }
                        val failCount = state.results.count { !it.success }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatBox(
                                label = "Tested",
                                value = "${state.totalTested}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            StatBox(
                                label = "Valid",
                                value = "$validCount",
                                color = Color(0xFF4ADE80),
                            )
                            StatBox(
                                label = "Failed",
                                value = "$failCount",
                                color = Color(0xFFF87171),
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = if (validCount > 0) "Working IPs:" else "No working IPs found.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))

                        if (state.results.any { it.success }) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(state.results.filter { it.success }, key = { it.ip }) { result ->
                                    ScanResultRow(result)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Save results button
                        if (state.results.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val suggestedName = "valid_ips_${ipFileName ?: "scan"}"
                                    saveFile.launch(suggestedName)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.SaveAlt, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save Results")
                            }
                        }
                    }

                    error -> {
                        // ── Error ──
                        val msg = (scanState as IpScanState.Error).message
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF87171).copy(alpha = 0.12f))
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFFF87171),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                // Warning when VPN is connected
                if (viewModel.status.value == com.ropeah.vpn.vpn.VpnStatus.Connected) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "VPN is connected. Disconnect before scanning for best results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (completed) {
                Button(onClick = {
                    viewModel.resetIpScan()
                    onDismiss()
                }) {
                    Text("Done")
                }
            } else if (error) {
                Button(onClick = {
                    viewModel.resetIpScan()
                }) {
                    Text("Dismiss")
                }
            } else if (running) {
                TextButton(onClick = { viewModel.cancelIpScan() }) {
                    Text("Cancel Scan")
                }
            } else if (readyToScan) {
                TextButton(onClick = {
                    ipList = null
                    ipFileName = null
                }) {
                    Text("Choose different file")
                }
            } else {
                TextButton(onClick = {
                    viewModel.resetIpScan()
                    onDismiss()
                }) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ScanResultRow(result: IpScanner.ScanIpResult) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (result.success)
            Color(0xFF4ADE80).copy(alpha = 0.1f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Close,
                contentDescription = null,
                tint = if (result.success) Color(0xFF4ADE80) else Color(0xFFF87171),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = result.ip,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (result.success) {
                Text(
                    text = "${result.latencyMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── File I/O helpers ────────────────────────────────────────────────────

private suspend fun readLinesFromUri(context: Context, uri: Uri): List<String> =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it.isNotEmpty() }
                .filterNot { it.startsWith("#") }
                .toList()
        } ?: emptyList()
    }

private suspend fun writeResultsToUri(
    context: Context,
    uri: Uri,
    results: List<IpScanner.ScanIpResult>,
    server: ServerConfig,
) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
        writer.write("# Valid IPs for ${server.protocol} config (port ${server.port})\n")
        writer.write("# Scanned on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\n")
        writer.write("# Format: ip latency_ms\n\n")
        results.filter { it.success }.forEach { result ->
            writer.write("${result.ip} ${result.latencyMs}\n")
        }
    }
}

private fun resolveFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
    }
}
