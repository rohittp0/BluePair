package com.rohittp.pair.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairActions
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.oem.OemSnapshotCollector
import com.rohittp.pair.oem.ShizukuBridge
import com.rohittp.pair.oem.ShizukuBackend
import com.rohittp.pair.routing.BluePairController
import com.rohittp.pair.routing.RoutingDiagnosticsRecord
import com.rohittp.pair.routing.RoutingMode
import com.rohittp.pair.routing.RoutingState
import com.rohittp.pair.ui.automation.BluetoothSettingsAutomationService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpeakerDevice(
    val name: String,
    val address: String
)

data class BluePairUiState(
    val isBluetoothModeEnabled: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val bondedDevices: List<SpeakerDevice> = emptyList(),
    val selectedAddresses: Set<String> = emptySet(),
    val selectedDevices: List<SpeakerDevice> = emptyList(),
    val deviceNameByAddress: Map<String, String> = emptyMap(),
    val leftAddress: String? = null,
    val rightAddress: String? = null,
    val actionMessage: String? = null,
    val routingState: RoutingState = RoutingState.OFF,
    val routingDetail: String = "",
    val routingMode: RoutingMode = RoutingMode.LE_AUDIO_PREFERRED,
    val diagnostics: List<RoutingDiagnosticsRecord> = emptyList(),
    val oemControllerName: String = "",
    val oemSnapshotDiff: String = "",
    val shizukuBinderAlive: Boolean = false,
    val shizukuPermissionGranted: Boolean = false
)

class BluePairViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(BluePairUiState())
    val uiState = _uiState.asStateFlow()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluePairActions.ACTION_STATE_CHANGED) {
                refreshState()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            stateReceiver,
            IntentFilter(BluePairActions.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshState()
    }

    override fun onCleared() {
        appContext.unregisterReceiver(stateReceiver)
        super.onCleared()
    }

    fun onBluetoothPermissionChanged() {
        refreshState()
    }

    fun toggleOutputMode() {
        val currentlyEnabled = BluePairPrefs.isBluetoothModeEnabled(appContext)
        if (!currentlyEnabled) {
            val blockedReason = validateEnablePrerequisites()
            if (blockedReason != null) {
                _uiState.update { it.copy(actionMessage = blockedReason) }
                return
            }
        }

        _uiState.update { it.copy(actionMessage = null) }
        BluePairController.toggleBluetoothMode(appContext)
        refreshState()
    }

    fun toggleDeviceSelection(address: String) {
        val currentSelection = BluePairPrefs.getSelectedDeviceAddresses(appContext).toMutableSet()
        if (currentSelection.contains(address)) {
            currentSelection.remove(address)
        } else {
            if (currentSelection.size >= 2) return
            currentSelection.add(address)
        }

        BluePairPrefs.setSelectedDeviceAddresses(appContext, currentSelection)
        val normalized = normalizeChannels(
            selectedAddresses = currentSelection,
            leftAddress = BluePairPrefs.getLeftDeviceAddress(appContext),
            rightAddress = BluePairPrefs.getRightDeviceAddress(appContext)
        )
        BluePairPrefs.setLeftDeviceAddress(appContext, normalized.first)
        BluePairPrefs.setRightDeviceAddress(appContext, normalized.second)
        refreshState()
    }

    fun assignLeft(address: String) {
        val selected = BluePairPrefs.getSelectedDeviceAddresses(appContext)
        if (!selected.contains(address)) return

        val currentRight = BluePairPrefs.getRightDeviceAddress(appContext)
        val nextRight = if (currentRight == address) {
            selected.firstOrNull { it != address }
        } else {
            currentRight
        }

        BluePairPrefs.setLeftDeviceAddress(appContext, address)
        BluePairPrefs.setRightDeviceAddress(appContext, nextRight)
        refreshState()
    }

    fun assignRight(address: String) {
        val selected = BluePairPrefs.getSelectedDeviceAddresses(appContext)
        if (!selected.contains(address)) return

        val currentLeft = BluePairPrefs.getLeftDeviceAddress(appContext)
        val nextLeft = if (currentLeft == address) {
            selected.firstOrNull { it != address }
        } else {
            currentLeft
        }

        BluePairPrefs.setLeftDeviceAddress(appContext, nextLeft)
        BluePairPrefs.setRightDeviceAddress(appContext, address)
        refreshState()
    }

    fun refreshState() {
        val hasPermission = hasBluetoothPermission(appContext)
        val bondedDevices = if (hasPermission) readBondedDevices() else emptyList()
        val selectedAddresses = BluePairPrefs.getSelectedDeviceAddresses(appContext)
        val diagnostics = BluePairPrefs.getRoutingDiagnostics(appContext)
        val shizukuStatus = ShizukuBridge.getStatus()
        val nameMap = bondedDevices.associate { it.address to it.name }.toMutableMap()
        selectedAddresses.forEach { address ->
            if (!nameMap.containsKey(address)) {
                nameMap[address] = address
            }
        }

        val selectedDevices = selectedAddresses
            .map { address -> SpeakerDevice(nameMap[address].orEmpty(), address) }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }

        val normalizedChannels = normalizeChannels(
            selectedAddresses = selectedAddresses,
            leftAddress = BluePairPrefs.getLeftDeviceAddress(appContext),
            rightAddress = BluePairPrefs.getRightDeviceAddress(appContext)
        )
        BluePairPrefs.setLeftDeviceAddress(appContext, normalizedChannels.first)
        BluePairPrefs.setRightDeviceAddress(appContext, normalizedChannels.second)

        _uiState.update { current ->
            current.copy(
                isBluetoothModeEnabled = BluePairPrefs.isBluetoothModeEnabled(appContext),
                hasBluetoothPermission = hasPermission,
                bondedDevices = bondedDevices,
                selectedAddresses = selectedAddresses,
                selectedDevices = selectedDevices,
                deviceNameByAddress = nameMap,
                leftAddress = normalizedChannels.first,
                rightAddress = normalizedChannels.second,
                routingState = BluePairPrefs.getRoutingState(appContext),
                routingDetail = BluePairPrefs.getRoutingDetail(appContext),
                routingMode = BluePairPrefs.getRoutingMode(appContext),
                diagnostics = diagnostics,
                oemControllerName = BluePairPrefs.getOemControllerName(appContext),
                oemSnapshotDiff = BluePairPrefs.getOemSnapshotLastDiff(appContext),
                shizukuBinderAlive = shizukuStatus.binderAlive,
                shizukuPermissionGranted = shizukuStatus.permissionGranted
            )
        }
    }

    fun clearDiagnostics() {
        BluePairPrefs.clearRoutingDiagnostics(appContext)
        refreshState()
    }

    fun diagnosticsAsText(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.getDefault())
        return BluePairPrefs.getRoutingDiagnostics(appContext)
            .joinToString(separator = "\n\n") { record ->
                val timestamp = Instant.ofEpochMilli(record.timestampMs)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
                buildString {
                    append("time=").append(timestamp).append('\n')
                    append("state=").append(record.state.rawValue).append('\n')
                    append("detail=").append(record.detail).append('\n')
                    append("requested=").append(record.requestedAddresses).append('\n')
                    append("a2dpConnected=").append(record.a2dpConnectedAddresses).append('\n')
                    append("headsetConnected=").append(record.headsetConnectedAddresses).append('\n')
                    append("leConnected=").append(record.leConnectedAddresses).append('\n')
                    append("activeOutputs=").append(record.activeOutputAddresses).append('\n')
                    append("device=").append(record.manufacturer).append(" / ")
                        .append(record.model).append(" / sdk=").append(record.sdkInt)
                }
            }
    }

    fun runOemAutomation() {
        BluetoothSettingsAutomationService.requestDualAudioAutomation(appContext)
        _uiState.update {
            it.copy(actionMessage = appContext.getString(R.string.automation_running_detail))
        }
        refreshState()
    }

    fun requestShizukuPermission() {
        val status = ShizukuBridge.getStatus()
        if (!status.binderAlive) {
            if (!ShizukuBackend.isAvailable(appContext)) {
                _uiState.update {
                    it.copy(actionMessage = appContext.getString(R.string.shizuku_not_installed))
                }
                return
            }
            if (openShizukuApp()) {
                _uiState.update {
                    it.copy(actionMessage = appContext.getString(R.string.shizuku_open_and_start))
                }
                return
            }
            _uiState.update {
                it.copy(actionMessage = appContext.getString(R.string.shizuku_not_running))
            }
            return
        }
        if (status.permissionGranted) {
            _uiState.update {
                it.copy(actionMessage = appContext.getString(R.string.shizuku_already_granted))
            }
            refreshState()
            return
        }
        ShizukuBridge.requestPermissionIfNeeded()
        _uiState.update {
            it.copy(actionMessage = appContext.getString(R.string.shizuku_permission_requested))
        }
        refreshState()
    }

    fun markOemBaselineSnapshot() {
        val baseline = OemSnapshotCollector.captureSnapshot(appContext)
        BluePairPrefs.setOemSnapshotBaseline(appContext, OemSnapshotCollector.toJson(baseline))
        _uiState.update {
            it.copy(actionMessage = appContext.getString(R.string.snapshot_baseline_saved))
        }
        refreshState()
    }

    fun captureOemSnapshotDiff() {
        val baselineRaw = BluePairPrefs.getOemSnapshotBaseline(appContext)
        val baseline = OemSnapshotCollector.fromJson(baselineRaw)
        if (baseline == null) {
            _uiState.update {
                it.copy(actionMessage = appContext.getString(R.string.snapshot_diff_missing_baseline))
            }
            return
        }

        val current = OemSnapshotCollector.captureSnapshot(appContext)
        val diffLines = OemSnapshotCollector.diff(baseline, current)
        val diffText = if (diffLines.isEmpty()) {
            "No setting changes detected."
        } else {
            diffLines.joinToString(separator = "\n")
        }
        BluePairPrefs.setOemSnapshotLastDiff(appContext, diffText)
        _uiState.update {
            it.copy(
                actionMessage = appContext.getString(
                    R.string.snapshot_diff_saved,
                    diffLines.size
                )
            )
        }
        refreshState()
    }

    @SuppressLint("MissingPermission")
    private fun validateEnablePrerequisites(): String? {
        if (!hasBluetoothPermission(appContext)) {
            return appContext.getString(R.string.enable_blocked_permission)
        }

        val selected = BluePairPrefs.getSelectedDeviceAddresses(appContext)
        if (selected.size < 2) {
            return appContext.getString(R.string.enable_blocked_select_two)
        }

        val left = BluePairPrefs.getLeftDeviceAddress(appContext)
        val right = BluePairPrefs.getRightDeviceAddress(appContext)
        val hasValidChannels = left != null &&
            right != null &&
            left != right &&
            selected.contains(left) &&
            selected.contains(right)

        if (!hasValidChannels) {
            return appContext.getString(R.string.enable_blocked_assign_channels)
        }

        val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter ?: return appContext.getString(
            R.string.enable_blocked_select_two
        )
        val pairedAddresses = bluetoothAdapter.bondedDevices.map { it.address }.toSet()
        if (!pairedAddresses.contains(left) || !pairedAddresses.contains(right)) {
            return appContext.getString(R.string.enable_blocked_select_two)
        }

        return null
    }

    private fun hasBluetoothPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun openShizukuApp(): Boolean {
        val launchPackages = listOf(
            "moe.shizuku.manager",
            "moe.shizuku.privileged.api"
        )
        launchPackages.forEach { pkg ->
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launchIntent)
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun readBondedDevices(): List<SpeakerDevice> {
        val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter ?: return emptyList()
        return bluetoothAdapter.bondedDevices
            .map { device ->
                val safeName = device.name ?: device.address
                SpeakerDevice(name = safeName, address = device.address)
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun normalizeChannels(
        selectedAddresses: Set<String>,
        leftAddress: String?,
        rightAddress: String?
    ): Pair<String?, String?> {
        if (selectedAddresses.isEmpty()) return null to null
        if (selectedAddresses.size == 1) return selectedAddresses.first() to null

        val ordered = selectedAddresses.sorted()
        val left = if (leftAddress in selectedAddresses) leftAddress else null
        val right = if (rightAddress in selectedAddresses) rightAddress else null

        if (left != null && right != null && left != right) {
            return left to right
        }
        if (left != null) {
            return left to ordered.first { it != left }
        }
        if (right != null) {
            return ordered.first { it != right } to right
        }
        return ordered[0] to ordered[1]
    }
}
