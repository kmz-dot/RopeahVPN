package com.ropeah.vpn.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ropeah.vpn.data.CloudflareIpFetcher
import com.ropeah.vpn.data.CloudflareRange
import com.ropeah.vpn.data.IpScanner
import com.ropeah.vpn.data.IpScanner.ScanIpResult
import com.ropeah.vpn.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Scan State (top-level sealed class) ─────────────────────────────────

sealed class CfScanState {
    data object Idle : CfScanState()
    data class Fetching(val progress: String) : CfScanState()
    data class Loaded(
        val ranges: List<CloudflareRange>,
        val samplesPerRange: Int,
        val port: Int,
    ) : CfScanState()
    data class Scanning(
        val ranges: List<CloudflareRange>,
        val current: Int,
        val total: Int,
        val currentIp: String,
        val successCount: Int,
        val failCount: Int,
        val results: List<ScanIpResult>,
    ) : CfScanState()
    data class Completed(
        val ranges: List<CloudflareRange>,
        val results: List<ScanIpResult>,
        val totalTested: Int,
    ) : CfScanState()
    data class Error(val message: String) : CfScanState()
}

// ── ViewModel ───────────────────────────────────────────────────────────

class CloudflareScanViewModel : ViewModel() {

    private val _state = MutableStateFlow<CfScanState>(CfScanState.Idle)
    val state: StateFlow<CfScanState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var lastSamplesPerRange = 15
    private var lastPort = 80

    fun fetchRanges(samplesPerRange: Int = 15, port: Int = 80) {
        if (_state.value is CfScanState.Fetching) return
        lastSamplesPerRange = samplesPerRange
        lastPort = port
        _state.value = CfScanState.Fetching("Fetching Cloudflare IP ranges...")

        viewModelScope.launch {
            try {
                val ranges = CloudflareIpFetcher.fetchAndSample(samplesPerRange) { msg ->
                    _state.value = CfScanState.Fetching(msg)
                }
                _state.value = CfScanState.Loaded(
                    ranges = ranges,
                    samplesPerRange = samplesPerRange,
                    port = port,
                )
            } catch (e: Exception) {
                _state.value = CfScanState.Error(e.message ?: "Failed to fetch ranges")
            }
        }
    }

    fun startScan(context: Context, baseConfig: ServerConfig) {
        val current = _state.value
        if (current !is CfScanState.Loaded) return
        if (scanJob?.isActive == true) return

        val allIps = current.ranges.flatMap { it.sampledIps }
        if (allIps.isEmpty()) {
            _state.value = CfScanState.Error("No IPs to scan. Try increasing samples per range.")
            return
        }

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val ipScanner = IpScanner(context)
            _state.value = CfScanState.Scanning(
                ranges = current.ranges,
                current = 0,
                total = allIps.size,
                currentIp = "",
                successCount = 0,
                failCount = 0,
                results = emptyList(),
            )

            try {
                ipScanner.scan(
                    baseConfig = baseConfig.copy(port = current.port),
                    ips = allIps,
                    probeUrl = "https://www.gstatic.com/generate_204",
                    onProgress = { progress ->
                        _state.value = CfScanState.Scanning(
                            ranges = current.ranges,
                            current = progress.current,
                            total = progress.total,
                            currentIp = progress.currentIp,
                            successCount = progress.successCount,
                            failCount = progress.failCount,
                            results = progress.results,
                        )
                    },
                )

                val final = _state.value
                if (final is CfScanState.Scanning) {
                    _state.value = CfScanState.Completed(
                        ranges = current.ranges,
                        results = final.results,
                        totalTested = final.total,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = CfScanState.Loaded(
                    ranges = current.ranges,
                    samplesPerRange = lastSamplesPerRange,
                    port = lastPort,
                )
            } catch (e: Exception) {
                _state.value = CfScanState.Error(e.message ?: "Scan failed")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        val current = _state.value
        if (current is CfScanState.Scanning) {
            _state.value = CfScanState.Loaded(
                ranges = current.ranges,
                samplesPerRange = lastSamplesPerRange,
                port = lastPort,
            )
        }
    }

    fun reset() {
        scanJob?.cancel()
        scanJob = null
        _state.value = CfScanState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

// ── Composable Page ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflareScanPage(
    server: ServerConfig?,
    onBack: () -> Unit,
    viewModel: CloudflareScanViewModel = viewModel(),
) {
    val curState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // File saver for results
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val s = curState
            if (s is CfScanState.Completed) {
                scope.launch {
                    writeResultsToUri(context, uri, s.results)
                }
            }
        }
    }

    // Auto-fetch on first composition
    LaunchedEffect(Unit) {
        if (curState is CfScanState.Idle) {
            viewModel.fetchRanges()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cloudflare Scanner", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (curState is CfScanState.Scanning) viewModel.cancelScan()
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Server info bar
            if (server != null && curState !is CfScanState.Idle) {
                ServerInfoBar(server)
                Spacer(Modifier.height(12.dp))
            }

            when (val s = curState) {
                is CfScanState.Idle -> IdleContent(onStart = { viewModel.fetchRanges() })
                is CfScanState.Fetching -> FetchingContent(s.progress)
                is CfScanState.Loaded -> LoadedContent(
                    ranges = s.ranges,
                    samplesPerRange = s.samplesPerRange,
                    port = s.port,
                    serverName = server?.let { "${it.protocol} · ${it.address}" } ?: "",
                    hasServer = server != null,
                    onUpdateSamples = { newSamples ->
                        viewModel.fetchRanges(newSamples, s.port)
                    },
                    onUpdatePort = { newPort ->
                        viewModel.fetchRanges(s.samplesPerRange, newPort)
                    },
                    onStartScan = {
                        if (server != null) viewModel.startScan(context, server)
                    },
                )
                is CfScanState.Scanning -> ScanningContent(s, onCancel = { viewModel.cancelScan() })
                is CfScanState.Completed -> CompletedContent(
                    s,
                    onSave = { saveFile.launch("cloudflare_alive_ips.txt") },
                    onRetry = { viewModel.fetchRanges() },
                )
                is CfScanState.Error -> ErrorContent(
                    s.message,
                    onRetry = { viewModel.fetchRanges() },
                    onBack = onBack,
                )
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────

@Composable
private fun ServerInfoBar(server: ServerConfig) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${server.protocol} · ${server.address}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Cloudflare IP Scanner",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Fetch Cloudflare IP ranges and test which ones\nwork best as a proxy endpoint for your config.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Icon(Icons.Filled.Cloud, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Fetch Ranges")
            }
        }
    }
}

@Composable
private fun FetchingContent(progress: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = progress,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadedContent(
    ranges: List<CloudflareRange>,
    samplesPerRange: Int,
    port: Int,
    serverName: String,
    hasServer: Boolean,
    onUpdateSamples: (Int) -> Unit,
    onUpdatePort: (Int) -> Unit,
    onStartScan: () -> Unit,
) {
    val totalIps = ranges.sumOf { it.sampledIps.size }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${ranges.size} IPv4 Ranges Loaded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$totalIps sampled IPs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (serverName.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Using: $serverName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Config row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SamplesDropdown(
                        value = samplesPerRange,
                        label = "Samples/range",
                        modifier = Modifier.weight(1f),
                        onValueChange = onUpdateSamples,
                    )
                    PortInput(
                        port = port,
                        label = "Port",
                        modifier = Modifier.weight(1f),
                        onPortChange = onUpdatePort,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (!hasServer) {
                    // Warning when no server is configured
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF97316).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "No server selected. Go to Servers and pick an active server first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF97316),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = onStartScan,
                    enabled = hasServer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan $totalIps IPs")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "IP Ranges",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(ranges, key = { it.cidr }) { range ->
                RangeCard(range = range)
            }
            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamplesDropdown(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(5, 10, 15, 20, 30, 50)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = "$value",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text("$opt IPs") },
                    onClick = {
                        onValueChange(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PortInput(
    port: Int,
    label: String,
    modifier: Modifier = Modifier,
    onPortChange: (Int) -> Unit,
) {
    var text by remember(port) { mutableStateOf(if (port > 0) "$port" else "") }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toIntOrNull()?.let { p ->
                if (p in 1..65535) onPortChange(p)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
    )
}

@Composable
private fun RangeCard(range: CloudflareRange) {
    var expanded by remember { mutableStateOf(false) }

    val sizeLabel = when {
        range.totalAddresses >= 1_000_000 -> "${"%.1f".format(range.totalAddresses / 1_000_000.0)}M"
        range.totalAddresses >= 1_000 -> "${range.totalAddresses / 1_000}K"
        else -> "${range.totalAddresses}"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = range.cidr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "$sizeLabel total · ${range.sampledIps.size} sampled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                range.sampledIps.forEach { ip ->
                    Text(
                        text = ip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningContent(
    state: CfScanState.Scanning,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }

                Spacer(Modifier.height(8.dp))

                val progress = if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${state.current} / ${state.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row {
                        Text(
                            text = "✓ ${state.successCount}",
                            color = Color(0xFF4ADE80),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "✗ ${state.failCount}",
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.currentIp.ifEmpty { "starting..." },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "testing proxy...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Results so far",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val displayResults = state.results.takeLast(200)
            items(displayResults, key = { "${it.ip}-${it.latencyMs}-${it.success}" }) { result ->
                ScanResultMiniRow(result)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ScanResultMiniRow(result: IpScanner.ScanIpResult) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (result.success)
            Color(0xFF4ADE80).copy(alpha = 0.08f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Close,
                contentDescription = null,
                tint = if (result.success) Color(0xFF4ADE80) else Color(0xFFF87171),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = result.ip,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (result.success) {
                Text(
                    text = "${result.latencyMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = latencyColor(result.latencyMs),
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = "timeout",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun CompletedContent(
    state: CfScanState.Completed,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    val validResults = state.results.filter { it.success }
    val failCount = state.results.count { !it.success }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatChip("Tested", "${state.totalTested}", MaterialTheme.colorScheme.onSurfaceVariant)
                    StatChip("Alive", "${validResults.size}", Color(0xFF4ADE80))
                    StatChip("Failed", "$failCount", Color(0xFFF87171))
                }

                if (validResults.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))

                    val best = validResults.minByOrNull { it.latencyMs }
                    if (best != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF4ADE80).copy(alpha = 0.1f))
                                .padding(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.Speed,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fastest IP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${best.ip}  ·  ${best.latencyMs}ms",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save IPs")
                        }
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Again")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Alive IPs (${validResults.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        if (validResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No responsive IPs found on this port.\nTry a different port or increase samples per range.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(validResults.sortedBy { it.latencyMs }, key = { it.ip }) { result ->
                    ScanResultFullRow(result)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
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

@Composable
private fun ScanResultFullRow(result: IpScanner.ScanIpResult) {
    val barColor = latencyColor(result.latencyMs)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = result.ip,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            val barWidthFraction = (result.latencyMs.toFloat().coerceIn(0f, 500f) / 500f).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barWidthFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${result.latencyMs}ms",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = barColor,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
                TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
    }
}

// ── File I/O & Helpers ───────────────────────────────────────────────────

private suspend fun writeResultsToUri(
    context: Context,
    uri: Uri,
    results: List<IpScanner.ScanIpResult>,
) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
        val validResults = results.filter { it.success }
        writer.write("# Cloudflare Alive IPs\n")
        writer.write("# Generated on ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        writer.write("# Total tested: ${results.size} | Alive: ${validResults.size}\n")
        writer.write("# IP addresses only\n\n")
        validResults.sortedBy { it.latencyMs }.forEach { result ->
            writer.write("${result.ip}\n")
        }
    }
}

@Composable
private fun latencyColor(ms: Long): Color = when {
    ms < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    ms < 80 -> Color(0xFF4ADE80)
    ms < 200 -> Color(0xFFFACC15)
    ms < 400 -> Color(0xFFF97316)
    else -> Color(0xFFF87171)
}
