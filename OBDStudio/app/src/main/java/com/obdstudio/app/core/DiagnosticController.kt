package com.obdstudio.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.obdstudio.app.bluetooth.BluetoothSppTransport
import com.obdstudio.app.bluetooth.DiagnosticTransport
import com.obdstudio.app.storage.ScanHistoryStore
import com.obdstudio.app.storage.ScanRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

data class PairedAdapter(val name: String, val address: String)
data class FaultCode(val code: String, val status: String, val description: String)
data class LiveParameter(val pid: Int, val name: String, val unit: String, val value: Double? = null)

data class DiagnosticState(
    val paired: List<PairedAdapter> = emptyList(),
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val ecuDetected: Boolean = false,
    val adapterName: String = "",
    val adapterVersion: String = "",
    val protocol: String = "",
    val voltage: Double? = null,
    val milStatus: MilStatus? = null,
    val responderIds: List<String> = emptyList(),
    val respondersChecked: Boolean = false,
    val responderMessage: String = "",
    val pendingSupported: Boolean? = null,
    val permanentSupported: Boolean? = null,
    val activePids: Set<Int> = emptySet(),
    val live: List<LiveParameter> = defaultParameters,
    val rpmPoints: List<Float> = emptyList(),
    val codes: List<FaultCode> = emptyList(),
    val history: List<ScanRecord> = emptyList(),
    val scanning: Boolean = false,
    val lastScanComplete: Boolean = false,
    val message: String = "Pair the adapter in Android Bluetooth settings, then select it here."
)

val defaultParameters = listOf(
    LiveParameter(0x0C,"Engine RPM","rpm"),
    LiveParameter(0x0D,"Road speed","km/h"),
    LiveParameter(0x05,"Coolant","°C"),
    LiveParameter(0x42,"ECU voltage","V"),
    LiveParameter(0x11,"Throttle","%"),
    LiveParameter(0x04,"Engine load","%"),
    LiveParameter(0x0F,"Intake air","°C"),
    LiveParameter(0x0B,"Intake pressure","kPa"),
    LiveParameter(0x06,"Short fuel trim","%"),
    LiveParameter(0x07,"Long fuel trim","%"),
    LiveParameter(0x2F,"Fuel level","%"),
    LiveParameter(0x10,"Airflow (MAF)","g/s"),
    LiveParameter(0x33,"Barometric pressure","kPa"),
    LiveParameter(0x0A,"Fuel pressure","kPa"),
    LiveParameter(0x1F,"Engine runtime","s"),
    LiveParameter(0x31,"Distance since codes cleared","km"),
    LiveParameter(0x46,"Ambient air temperature","°C"),
    LiveParameter(0x5C,"Engine oil temperature","°C"),
    LiveParameter(0x5E,"Engine fuel rate","L/h"),
    LiveParameter(0x2C,"Commanded EGR","%"),
    LiveParameter(0x2D,"EGR error","%"),
    LiveParameter(0x52,"Ethanol content","%")
)

class DiagnosticController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport: DiagnosticTransport = BluetoothSppTransport(context)
    private val db = ScanHistoryStore(context)
    private var pollJob: Job? = null
    private var connectJob: Job? = null
    private var workJob: Job? = null
    private val mutableState = MutableStateFlow(DiagnosticState())
    val state = mutableState.asStateFlow()

    init { refreshHistory() }

    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun refreshPaired() {
        if (!permitted()) {
            mutableState.update { it.copy(message = "Allow Nearby devices permission to list paired adapters.") }
            return
        }
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val known = adapter?.bondedDevices.orEmpty().map { PairedAdapter(it.name ?: "Unknown device", it.address) }
                .sortedBy { it.name.lowercase(Locale.US) }
            mutableState.update { it.copy(paired = known, message = if (known.isEmpty()) "No paired Bluetooth devices. Pair your ELM327 in Android settings first." else it.message) }
        } catch (e: Exception) {
            mutableState.update { it.copy(message = e.message ?: "Bluetooth permission required") }
        }
    }

    fun connect(device: PairedAdapter) {
        if (!permitted()) { refreshPaired(); return }
        if (mutableState.value.connecting) return
        connectJob?.cancel()
        pollJob?.cancel()
        transport.close()
        mutableState.update { it.copy(connecting = true, connected = false, ecuDetected = false,
            codes = emptyList(), responderIds = emptyList(), respondersChecked = false, responderMessage = "",
            milStatus = null, pendingSupported = null, permanentSupported = null,
            message = "Connecting to ${device.name}...") }
        connectJob = scope.launch {
            try {
                transport.open(device.address)
                transport.request("ATZ", 6000)
                transport.request("ATE0")
                transport.request("ATL0")
                transport.request("ATS0")
                transport.request("ATH0")
                transport.request("ATSP0")
                val version = transport.request("ATI").replace(">", "").trim()
                val voltage = runCatching { ObdCodec.adapterVoltage(transport.request("ATRV")) }.getOrNull()
                val probe = runCatching { transport.request("0100", 12500) }.getOrDefault("NO DATA")
                val detected = ObdCodec.payload(probe, 0x41, 0x00)?.size?.let { it >= 4 } == true
                val firstBlock = if (detected) ObdCodec.supportedPids(probe) else emptySet()
                val secondBlock = if (0x20 in firstBlock) runCatching {
                    ObdCodec.supportedPids(transport.request("0120"), 0x20)
                }.getOrDefault(emptySet()) else emptySet()
                val thirdBlock = if (0x40 in secondBlock) runCatching {
                    ObdCodec.supportedPids(transport.request("0140"), 0x40)
                }.getOrDefault(emptySet()) else emptySet()
                val available = firstBlock + secondBlock + thirdBlock
                val protocol = runCatching { transport.request("ATDP").replace(">", "").trim() }.getOrDefault("Unknown")
                val mil = if (0x01 in available) runCatching {
                    ObdCodec.milStatus(transport.request("0101", 5000))
                }.getOrNull() else null
                mutableState.update {
                    it.copy(
                        connecting = false, connected = true, ecuDetected = detected,
                        adapterName = device.name, adapterVersion = version, protocol = protocol,
                        voltage = voltage, milStatus = mil, activePids = available,
                        message = if (detected) "Engine OBD-II responding — ready to scan" else "Adapter connected, but no engine ECU response. Check ignition and compatibility."
                    )
                }
                if (detected) startPolling()
            } catch (e: Exception) {
                transport.close()
                mutableState.update { it.copy(connecting = false, connected = false, ecuDetected = false, message = "Connection failed: ${e.message ?: "Unknown error"}") }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            val supported = mutableState.value.activePids
            val watched = defaultParameters.filter { it.pid in supported }
            val priorityIds = setOf(0x0C, 0x0D, 0x05, 0x42)
            val priority = watched.filter { it.pid in priorityIds }
            val secondary = watched.filter { it.pid !in priorityIds }
            var secondaryIndex = 0
            while (isActive && mutableState.value.connected && mutableState.value.ecuDetected) {
                val extras = if (secondary.isEmpty()) emptyList() else {
                    List(minOf(2, secondary.size)) { offset ->
                        secondary[(secondaryIndex + offset) % secondary.size]
                    }.also { secondaryIndex = (secondaryIndex + it.size) % secondary.size }
                }
                for (item in priority + extras) {
                    if (!isActive) break
                    val result = runCatching { transport.request("01%02X".format(Locale.US, item.pid), 2800) }.getOrNull()
                    val number = result?.let { ObdCodec.pidValue(it, item.pid) }
                    if (number != null) {
                        mutableState.update { old ->
                            old.copy(
                                live = old.live.map { if (it.pid == item.pid) it.copy(value = number) else it },
                                rpmPoints = if (item.pid == 0x0C) (old.rpmPoints + number.toFloat()).takeLast(28) else old.rpmPoints
                            )
                        }
                    }
                }
                delay(250)
            }
        }
    }

    /** Stored, pending and permanent *generic engine/emissions* DTCs only. */
    fun scanEngineCodes() {
        if (!mutableState.value.ecuDetected || mutableState.value.scanning) return
        workJob?.cancel()
        workJob = scope.launch {
            mutableState.update { it.copy(scanning = true, lastScanComplete = false, message = "Reading generic OBD-II faults...") }
            pollJob?.cancelAndJoin()
            try {
                val rawStored = transport.request("03", 9000)
                if (!ObdCodec.hasModeResponse(rawStored, 0x43)) error("No valid Mode 03 response; cannot confirm stored-code status")
                val stored = ObdCodec.troubleCodes(rawStored, 0x43)
                val rawPending = runCatching { transport.request("07", 9000) }.getOrDefault("NO DATA")
                val pendingAvailable = ObdCodec.hasModeResponse(rawPending, 0x47)
                val pending = if (pendingAvailable) ObdCodec.troubleCodes(rawPending, 0x47) else emptyList()
                val rawPermanent = runCatching { transport.request("0A", 9000) }.getOrDefault("NO DATA")
                val permanentAvailable = ObdCodec.hasModeResponse(rawPermanent, 0x4A)
                val permanent = if (permanentAvailable) ObdCodec.troubleCodes(rawPermanent, 0x4A) else emptyList()
                val mil = if (0x01 in mutableState.value.activePids) runCatching {
                    ObdCodec.milStatus(transport.request("0101", 5000))
                }.getOrNull() else null
                val codes = buildList {
                    stored.forEach { add(FaultCode(it, "Stored", TroubleCodeDescriptions.explain(it))) }
                    pending.forEach { add(FaultCode(it, "Pending", TroubleCodeDescriptions.explain(it))) }
                    permanent.forEach { add(FaultCode(it, "Permanent", TroubleCodeDescriptions.explain(it))) }
                }
                withContext(Dispatchers.IO) { db.insert(mutableState.value.adapterName, stored, pending, permanent) }
                refreshHistory()
                mutableState.update { it.copy(scanning = false, codes = codes, lastScanComplete = true,
                    pendingSupported = pendingAvailable, permanentSupported = permanentAvailable,
                    milStatus = mil ?: it.milStatus,
                    message = "Generic emissions scan: ${codes.size} code entries. Pending: ${if (pendingAvailable) "read" else "no response"}; permanent: ${if (permanentAvailable) "read" else "no response"}. Other systems NOT scanned.") }
            } catch (e: Exception) {
                mutableState.update { it.copy(scanning = false, lastScanComplete = false, message = "Scan failed: ${e.message}") }
            } finally {
                if (mutableState.value.connected && mutableState.value.ecuDetected) startPolling()
            }
        }
    }

    /** A generic CAN responder survey; does not identify proprietary modules. */
    fun discoverResponders() {
        if (!mutableState.value.ecuDetected || mutableState.value.scanning) return
        workJob?.cancel()
        workJob = scope.launch {
            mutableState.update { it.copy(scanning = true, respondersChecked = false,
                responderMessage = "Discovering generic OBD responders…", message = "Discovering generic OBD responders…") }
            pollJob?.cancelAndJoin()
            var headersEnabled = false
            try {
                val enabled = transport.request("ATH1", 3000)
                if (enabled.contains("?")) error("Adapter does not support CAN header display")
                headersEnabled = true
                val reply = transport.request("0100", 10000)
                val ids = CanResponderCodec.responseIds(reply)
                val summary = if (ids.isEmpty())
                    "No CAN responder IDs recognized. This does not prove that other modules are absent."
                    else "${ids.size} generic OBD responding CAN IDs detected; ECU identity is not verified."
                mutableState.update { it.copy(responderIds = ids, respondersChecked = true,
                    responderMessage = summary, message = summary) }
            } catch (e: Exception) {
                mutableState.update { it.copy(respondersChecked = false,
                    responderMessage = "Discovery unavailable: ${e.message}",
                    message = "Discovery unavailable: ${e.message}") }
            } finally {
                if (headersEnabled) {
                    // Restore the header-free format required by the existing live PID reader.
                    val restored = runCatching { transport.request("ATH0", 3000) }.isSuccess
                    if (!restored) mutableState.update { it.copy(
                        responderMessage = "Could not restore adapter headers; disconnect and reconnect.",
                        message = "Adapter configuration uncertain. Reconnect before continuing.") }
                    else if (mutableState.value.connected && mutableState.value.ecuDetected) startPolling()
                } else if (mutableState.value.connected && mutableState.value.ecuDetected) startPolling()
                mutableState.update { it.copy(scanning = false) }
            }
        }
    }

    /** Destructive action only after the UI explicitly confirms. */
    fun clearEngineCodes() {
        if (!mutableState.value.ecuDetected || mutableState.value.scanning) return
        workJob = scope.launch {
            mutableState.update { it.copy(scanning = true, message = "Sending clear request to engine ECU...") }
            pollJob?.cancelAndJoin()
            try {
                val response = transport.request("04", 9500)
                val acknowledged = response.uppercase(Locale.US).contains("44")
                mutableState.update { it.copy(scanning = false, lastScanComplete = false, codes = if (acknowledged) emptyList() else it.codes,
                    message = if (acknowledged) "ECU acknowledged reset. Rescan to confirm. Readiness monitors may be reset." else "Clear not confirmed: ${response.take(100)}") }
            } catch (e: Exception) {
                mutableState.update { it.copy(scanning = false, message = "Clear failed: ${e.message}") }
            } finally {
                if (mutableState.value.connected && mutableState.value.ecuDetected) startPolling()
            }
        }
    }

    private fun refreshHistory() {
        scope.launch {
            val history = runCatching { withContext(Dispatchers.IO) { db.recent() } }.getOrDefault(emptyList())
            mutableState.update { it.copy(history = history) }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        pollJob?.cancel()
        workJob?.cancel()
        transport.close()
        mutableState.update { old ->
            old.copy(connecting = false, connected = false, ecuDetected = false, adapterName = "", protocol = "", activePids = emptySet(),
                voltage = null, milStatus = null, responderIds = emptyList(), respondersChecked = false,
                responderMessage = "", pendingSupported = null, permanentSupported = null,
                live = defaultParameters, rpmPoints = emptyList(), codes = emptyList(), lastScanComplete = false, scanning = false,
                message = "Disconnected")
        }
    }

    fun dispose() {
        disconnect()
        db.close()
        scope.cancel()
    }
}
