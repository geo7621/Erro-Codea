package com.obdstudio.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdstudio.app.core.DiagnosticController
import com.obdstudio.app.core.DiagnosticState
import com.obdstudio.app.core.ModuleCatalog
import com.obdstudio.app.core.PairedAdapter
import com.obdstudio.app.core.LiveParameter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF122A3B)
private val Teal = Color(0xFF007F83)
private val Mint = Color(0xFF0DC6B1)
private val CanvasColor = Color(0xFFF4F7FA)
private val Ink = Color(0xFF183245)
private val Muted = Color(0xFF748697)
private val Line = Color(0xFFE2EAF0)
private val Amber = Color(0xFFBF741F)
private val VeryLightTeal = Color(0xFFE8F7F5)

private val studioScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    background = CanvasColor,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF4F7),
    outline = Line
)

@Composable
fun StudioApp(controller: DiagnosticController) {
    val state by controller.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) controller.refreshPaired()
    }
    val requirePermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            grant.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else controller.refreshPaired()
    }
    LaunchedEffect(tab) { if (tab == 3) requirePermission() }

    MaterialTheme(colorScheme = studioScheme, typography = Typography()) {
        Scaffold(
            containerColor = CanvasColor,
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    val labels = listOf("Overview", "Systems", "Faults", "Adapter")
                    val glyphs = listOf("▣", "◈", "⚑", "⌁")
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(glyphs[index], fontSize = 23.sp, fontWeight = FontWeight.Medium) },
                            label = { Text(label, fontSize = 11.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Teal, selectedTextColor = Teal, indicatorColor = VeryLightTeal)
                        )
                    }
                }
            }
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                BrandHeader(state)
                when (tab) {
                    0 -> OverviewTab(state, onConnect = { tab = 3 }, onScan = { controller.scanEngineCodes(); tab = 2 })
                    1 -> SystemsTab(state, onDiscover = controller::discoverResponders)
                    2 -> FaultsTab(state, onScan = controller::scanEngineCodes, onClear = { confirmClear = true })
                    else -> AdapterTab(state, onRefresh = requirePermission, onConnect = controller::connect, onDisconnect = controller::disconnect)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear engine fault codes?") },
                text = {
                    Text("This sends OBD service 04 to responding emissions-related ECUs. It can erase fault codes, freeze-frame data and reset emissions readiness monitors. It does not repair faults. Save your results first. Continue only when parked safely.")
                },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; controller.clearEngineCodes() }) {
                        Text("Clear engine codes", color = Color(0xFFB93C3C))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun BrandHeader(state: DiagnosticState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(
                Brush.linearGradient(listOf(Navy, Teal))
            ), contentAlignment = Alignment.Center
        ) { Text("O", fontSize = 27.sp, color = Color.White, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("OBD STUDIO", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp, color = Navy)
            Text("Vehicle intelligence · Offline", fontSize = 11.sp, color = Muted)
        }
        Text(
            if (state.ecuDetected) "● LIVE" else if (state.connected) "● ADAPTER" else "○ OFFLINE",
            color = if (state.ecuDetected) Teal else Muted,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (state.ecuDetected) VeryLightTeal else Line.copy(alpha = .65f)).padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun OverviewTab(state: DiagnosticState, onConnect: () -> Unit, onScan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Navy, Color(0xFF194C58))))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DIAGNOSTIC WORKSPACE", color = Color(0xFF8FDFD7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text(if (state.ecuDetected) "Vehicle connected" else "Know your vehicle.\nBeyond the dashboard.", color = Color.White, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Text(if (state.ecuDetected) "Live engine data is available. Explore sensors or run a diagnostic scan." else "Professional-style diagnostics, your data stays on your phone. Pair an ELM327 Bluetooth Classic adapter to start.", color = Color(0xFFDAE8EC), fontSize = 13.sp, lineHeight = 19.sp)
                Button(
                    onClick = if (state.ecuDetected) onScan else onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (state.ecuDetected) "Scan engine faults" else "Connect Bluetooth adapter", fontWeight = FontWeight.Bold) }
            }
        }
        SectionTitle("Live telemetry", "Updates when the ECU responds")
        val mil = state.milStatus
        if (mil != null) {
            StudioCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check engine indicator", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Reported by generic OBD-II monitor (PID 01)", fontSize = 11.sp, color = Muted)
                    }
                    Text(if (mil.illuminated) "MIL ON" else "MIL OFF",
                        color = if (mil.illuminated) Amber else Teal,
                        fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Text("Confirmed emissions DTC count reported by ECU: ${mil.confirmedCodeCount}",
                    fontSize = 12.sp, color = Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("ENGINE RPM", state.live.reading(0x0C), "rpm", Modifier.weight(1f), Teal)
            MetricCard("VEHICLE SPEED", state.live.reading(0x0D), "km/h", Modifier.weight(1f), Color(0xFF4D6CBB))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("COOLANT", state.live.reading(0x05), "°C", Modifier.weight(1f), Amber)
            MetricCard("ECU VOLTAGE", state.live.reading(0x42) ?: state.voltage?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "V", Modifier.weight(1f), Teal)
        }
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Engine speed", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Recent live RPM samples", fontSize = 12.sp, color = Muted)
                }
                Text(if (state.rpmPoints.isEmpty()) "NO DATA" else "LIVE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (state.rpmPoints.isEmpty()) Muted else Teal)
            }
            Spacer(Modifier.height(10.dp))
            if (state.rpmPoints.size > 1) RpmGraph(state.rpmPoints)
            else Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).background(CanvasColor), contentAlignment = Alignment.Center) {
                Text("Chart appears after receiving RPM data", color = Muted, fontSize = 12.sp)
            }
        }
        SectionTitle("Additional live sensors", "Only PIDs advertised by your vehicle")
        val extra = state.live.filter { it.pid in state.activePids && it.pid !in setOf(0x0C, 0x0D, 0x05, 0x42) }
        if (extra.isEmpty()) {
            StatusNote("Connect to show additional live data supported by your ECU.")
        } else {
            StudioCard(padding = 4.dp) {
                extra.forEachIndexed { index, sensor ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(sensor.name, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Ink)
                        Text(sensor.value?.let {
                            val rounded = if (sensor.unit in setOf("g/s", "L/h"))
                                String.format(Locale.US, "%.1f", it) else String.format(Locale.US, "%.0f", it)
                            "$rounded ${sensor.unit}"
                        } ?: "--", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
                    }
                    if (index != extra.lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◈", fontSize = 28.sp, color = Teal)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("All-system architecture", fontWeight = FontWeight.Bold)
                    Text("${ModuleCatalog.all.size} modules catalogued · Enhanced profiles are future work", fontSize = 12.sp, color = Muted)
                }
            }
        }
        StatusNote(state.message)
    }
}

private fun List<LiveParameter>.reading(pid: Int): String? = firstOrNull { it.pid == pid }?.value?.let { value ->
    if (pid == 0x42) String.format(Locale.US, "%.1f", value) else String.format(Locale.US, "%.0f", value)
}

@Composable
private fun RpmGraph(readings: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).background(CanvasColor).padding(13.dp)) {
        val lowest = 0f
        val highest = maxOf(1000f, readings.maxOrNull() ?: 1000f) * 1.18f
        val step = size.width / (readings.size - 1).coerceAtLeast(1)
        val path = Path()
        readings.forEachIndexed { i, reading ->
            val y = size.height - (reading - lowest) / (highest - lowest) * size.height
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
        }
        drawLine(color = Line, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 2f)
        drawPath(path, color = Teal, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun SystemsTab(state: DiagnosticState, onDiscover: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Vehicle systems", "Complete module catalog")
        StatusNote("Only modules with verified responses are marked accessible. An advanced adapter alone does not provide proprietary ECU addresses or protocols.")
        StudioCard {
            Text("Discover responding ECUs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Read-only CAN responder survey using generic Mode 01. This does not identify transmission, ABS or airbag modules.",
                color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDiscover, enabled = state.ecuDetected && !state.scanning,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (state.scanning) "Communicating…" else "Detect OBD responding ECU IDs")
            }
            if (state.responderMessage.isNotBlank()) {
                Text(state.responderMessage, color = Muted, fontSize = 12.sp)
            }
            if (state.responderIds.isNotEmpty()) {
                state.responderIds.forEach { id ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CAN ID $id", fontSize = 14.sp, color = Navy, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text("OBD RESPONDER", fontSize = 10.sp, color = Teal)
                    }
                }
            }
            Text("Unknown or non-responding systems remain unverified — not fault-free.",
                color = Muted, fontSize = 11.sp)
        }
        ModuleCatalog.all.groupBy { it.group }.forEach { (group, modules) ->
            Text(group.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Muted, letterSpacing = 1.sp, modifier = Modifier.padding(top = 5.dp))
            StudioCard(padding = 4.dp) {
                modules.forEachIndexed { index, module ->
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VeryLightTeal), contentAlignment = Alignment.Center) {
                            Text(module.id.take(3), fontWeight = FontWeight.Black, fontSize = 12.sp, color = Teal)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(module.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(module.description, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(4.dp))
                        val ready = module.id == "ECM" && state.ecuDetected
                        Text(if (ready) "ACCESSIBLE" else if (module.id == "ECM") "NOT TESTED" else "PROFILE NEEDED",
                            fontSize = 9.sp, color = if (ready) Teal else Muted, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (ready) VeryLightTeal else CanvasColor).padding(horizontal = 7.dp, vertical = 6.dp))
                    }
                    if (index != modules.lastIndex) HorizontalDivider(color = Line.copy(alpha = .8f))
                }
            }
        }
    }
}

@Composable
private fun FaultsTab(state: DiagnosticState, onScan: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        SectionTitle("Fault diagnostics", "Generic OBD-II · Engine and emissions")
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Engine fault codes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Stored · Pending · Permanent", color = Muted, fontSize = 12.sp)
                }
                Text(if (state.lastScanComplete) "${state.codes.size}" else "--", fontSize = 30.sp, color = if (state.codes.isEmpty()) Teal else Amber, fontWeight = FontWeight.Black)
            }
            if (state.lastScanComplete) {
                Spacer(Modifier.height(6.dp))
                Text("Pending codes: ${if (state.pendingSupported == true) "ECU responded" else "No response / unsupported"}",
                    fontSize = 12.sp, color = Muted)
                Text("Permanent codes: ${if (state.permanentSupported == true) "ECU responded" else "No response / unsupported"}",
                    fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onScan, enabled = state.ecuDetected && !state.scanning, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (state.scanning) "Working..." else "Read engine fault codes", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onClear, enabled = state.ecuDetected && !state.scanning && state.lastScanComplete && state.codes.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Clear engine codes (confirmation required)")
            }
        }
        if (state.lastScanComplete && state.codes.isEmpty()) {
            StatusNote("The generic engine scan returned no stored, pending or permanent codes. Other modules have NOT been checked.")
        } else if (state.codes.isNotEmpty()) {
            state.codes.forEach { fault ->
                StudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fault.code, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Navy, modifier = Modifier.weight(1f))
                        Text(fault.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Amber)
                    }
                    Text(fault.description, fontSize = 13.sp, color = Muted)
                }
            }
        } else if (!state.scanning) {
            StatusNote("No current scan results. Connect an adapter and press Read engine fault codes.")
        }
        StatusNote(state.message)
        SectionTitle("Offline history", "Saved automatically after each successful scan")
        if (state.history.isEmpty()) {
            StudioCard { Text("Your scan history will appear here.", color = Muted, fontSize = 13.sp) }
        } else {
            StudioCard(padding = 4.dp) {
                state.history.take(20).forEachIndexed { index, scan ->
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scan.timestamp)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(scan.adapter, color = Muted, fontSize = 11.sp)
                            if (scan.totalCodes > 0) Text(listOf(scan.stored, scan.pending, scan.permanent).filter { it.isNotEmpty() }.joinToString(", "), fontSize = 11.sp, color = Muted, maxLines = 2)
                        }
                        Text("${scan.totalCodes} codes", fontSize = 12.sp, color = Teal, fontWeight = FontWeight.Bold)
                    }
                    if (index < state.history.size - 1 && index < 19) HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
private fun AdapterTab(state: DiagnosticState, onRefresh: () -> Unit, onConnect: (PairedAdapter) -> Unit, onDisconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        SectionTitle("Bluetooth connection", "ELM327 · Bluetooth Classic SPP")
        StudioCard {
            Text(if (state.connected) "Adapter connected" else if (state.connecting) "Connecting…" else "Not connected", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(state.message, color = Muted, fontSize = 12.sp)
            if (state.connected) {
                Spacer(Modifier.height(10.dp))
                AdapterInfo("Device", state.adapterName)
                AdapterInfo("Firmware", state.adapterVersion)
                AdapterInfo("Protocol", state.protocol)
                AdapterInfo("Engine ECU", if (state.ecuDetected) "Responding" else "No OBD-II response")
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
            }
        }
        SectionTitle("Paired devices", "Select the adapter already paired in Android settings")
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh paired Bluetooth devices") }
        if (state.paired.isEmpty()) {
            StatusNote("No paired devices listed. Open Android Settings → Bluetooth → Pair new device; select your ELM327 adapter, then come back and refresh. Common PINs are 1234 or 0000, depending on the adapter.")
        } else {
            state.paired.forEach { device ->
                StudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(VeryLightTeal), contentAlignment = Alignment.Center) { Text("⌁", color = Teal, fontSize = 27.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(device.address, fontSize = 11.sp, color = Muted)
                        }
                        Button(onClick = { onConnect(device) }, enabled = !state.connecting, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp)) { Text("Connect", fontSize = 12.sp) }
                    }
                }
            }
        }
        StatusNote("Works with paired ELM327 Bluetooth Classic / SPP adapters. BLE-only adapters and Wi-Fi connections will be added through separate transport implementations. No internet access permission is requested.")
    }
}

@Composable
private fun AdapterInfo(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(title, fontSize = 12.sp, color = Muted, modifier = Modifier.width(98.dp))
        Text(value.ifBlank { "--" }, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun MetricCard(label: String, reading: String?, unit: String, modifier: Modifier = Modifier, accent: Color = Teal) {
    Column(modifier.clip(RoundedCornerShape(19.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(19.dp)).padding(16.dp)) {
        Box(Modifier.width(29.dp).height(4.dp).clip(RoundedCornerShape(9.dp)).background(accent))
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = .5.sp)
        Spacer(Modifier.height(8.dp))
        Text(reading ?: "--", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Navy)
        Text(unit, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun StudioCard(padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(19.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(5.dp), content = content
    )
}

@Composable
private fun StatusNote(message: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF1F5)).padding(14.dp)) {
        Text("ⓘ", color = Teal, fontSize = 17.sp)
        Spacer(Modifier.width(9.dp))
        Text(message, color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
