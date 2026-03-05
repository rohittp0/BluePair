package com.rohittp.pair.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.rohittp.pair.core.BluePairActions
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.routing.BluePairController
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
    val rightAddress: String? = null
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

        _uiState.update {
            BluePairUiState(
                isBluetoothModeEnabled = BluePairPrefs.isBluetoothModeEnabled(appContext),
                hasBluetoothPermission = hasPermission,
                bondedDevices = bondedDevices,
                selectedAddresses = selectedAddresses,
                selectedDevices = selectedDevices,
                deviceNameByAddress = nameMap,
                leftAddress = normalizedChannels.first,
                rightAddress = normalizedChannels.second
            )
        }
    }

    private fun hasBluetoothPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun readBondedDevices(): List<SpeakerDevice> {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
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
