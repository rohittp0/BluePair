package com.rohittp.pair.routing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.Locale

data class BluetoothRoutingProbeResult(
    val requestedAddresses: Set<String>,
    val activeOutputAddresses: Set<String>,
    val a2dpConnectedAddresses: Set<String>,
    val headsetConnectedAddresses: Set<String>,
    val leConnectedAddresses: Set<String>,
    val isDualOutputConnected: Boolean,
    val isDualA2dpConnected: Boolean,
    val isDualHeadsetConnected: Boolean,
    val isLikelyDualClassicConnected: Boolean,
    val isDualLeConnected: Boolean,
    val isDualOutputActive: Boolean,
    val isSingleOutputActive: Boolean,
    val hasLeCandidate: Boolean
)

object BluetoothRoutingEngine {
    private const val TAG = "BLUEPAIR_ROUTING"

    @SuppressLint("MissingPermission")
    fun probeRouting(
        context: Context,
        leftAddress: String,
        rightAddress: String,
        shouldNudge: Boolean
    ): BluetoothRoutingProbeResult {
        val appContext = context.applicationContext
        val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        val audioManager = appContext.getSystemService(AudioManager::class.java)

        val requested = setOf(leftAddress.normalizedAddress(), rightAddress.normalizedAddress())
        if (bluetoothAdapter == null) {
            Log.w(TAG, "No BluetoothAdapter on this device. Routing probe cannot continue.")
            return BluetoothRoutingProbeResult(
                requestedAddresses = requested,
                activeOutputAddresses = emptySet(),
                a2dpConnectedAddresses = emptySet(),
                headsetConnectedAddresses = emptySet(),
                leConnectedAddresses = emptySet(),
                isDualOutputConnected = false,
                isDualA2dpConnected = false,
                isDualHeadsetConnected = false,
                isLikelyDualClassicConnected = false,
                isDualLeConnected = false,
                isDualOutputActive = false,
                isSingleOutputActive = false,
                hasLeCandidate = false
            )
        }

        val bondedByAddress = bluetoothAdapter.bondedDevices.associateBy { it.address.normalizedAddress() }
        val a2dpConnected = bluetoothManager.connectedAddresses(BluetoothProfile.A2DP)
        val headsetConnected = bluetoothManager.connectedAddresses(BluetoothProfile.HEADSET)
        val leConnected = bluetoothManager.connectedAddresses(BluetoothProfile.LE_AUDIO)
        val dualA2dpConnected = requested.all(a2dpConnected::contains)
        val dualHeadsetConnected = requested.all(headsetConnected::contains)
        val likelyDualClassicConnected = dualA2dpConnected || dualHeadsetConnected
        val dualLeConnected = requested.all(leConnected::contains)
        val dualConnected = requested.all { it in a2dpConnected || it in leConnected }

        logEnvironment()
        logDeviceCapabilities(
            requestedAddresses = requested,
            bondedByAddress = bondedByAddress,
            a2dpConnected = a2dpConnected,
            headsetConnected = headsetConnected,
            leConnected = leConnected
        )

        if (shouldNudge) {
            nudgeMediaRouting(audioManager)
        }

        val activeOutputs = readBluetoothOutputAddresses(audioManager)
        val dualActive = requested.all(activeOutputs::contains)
        val singleActive = requested.any(activeOutputs::contains)
        val hasLeCandidate = requested.any(leConnected::contains)

        Log.i(
            TAG,
            "RoutingProbe dualConnected=$dualConnected dualA2dpConnected=$dualA2dpConnected dualHeadsetConnected=$dualHeadsetConnected likelyDualClassicConnected=$likelyDualClassicConnected dualLeConnected=$dualLeConnected dualActive=$dualActive singleActive=$singleActive hasLeCandidate=$hasLeCandidate a2dpConnected=$a2dpConnected headsetConnected=$headsetConnected leConnected=$leConnected activeOutputs=$activeOutputs requested=$requested"
        )

        return BluetoothRoutingProbeResult(
            requestedAddresses = requested,
            activeOutputAddresses = activeOutputs,
            a2dpConnectedAddresses = a2dpConnected,
            headsetConnectedAddresses = headsetConnected,
            leConnectedAddresses = leConnected,
            isDualOutputConnected = dualConnected,
            isDualA2dpConnected = dualA2dpConnected,
            isDualHeadsetConnected = dualHeadsetConnected,
            isLikelyDualClassicConnected = likelyDualClassicConnected,
            isDualLeConnected = dualLeConnected,
            isDualOutputActive = dualActive,
            isSingleOutputActive = singleActive,
            hasLeCandidate = hasLeCandidate
        )
    }

    fun environmentManufacturer(): String = Build.MANUFACTURER

    fun environmentModel(): String = Build.MODEL

    fun environmentSdkInt(): Int = Build.VERSION.SDK_INT

    private fun logEnvironment() {
        Log.i(
            TAG,
            "Env manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL} device=${Build.DEVICE} sdk=${Build.VERSION.SDK_INT}"
        )
    }

    @SuppressLint("MissingPermission")
    private fun logDeviceCapabilities(
        requestedAddresses: Set<String>,
        bondedByAddress: Map<String, BluetoothDevice>,
        a2dpConnected: Set<String>,
        headsetConnected: Set<String>,
        leConnected: Set<String>
    ) {
        requestedAddresses.forEach { address ->
            val device = bondedByAddress[address]
            if (device == null) {
                Log.w(TAG, "Device[$address] not found in bonded devices.")
                return@forEach
            }

            val className = describeBluetoothClass(device.bluetoothClass)
            val deviceType = describeDeviceType(device.type)
            val uuids = device.uuids.orEmpty().map(ParcelUuid::toString)
            val supportsA2dp = uuids.any { it.startsWith(A2DP_UUID_PREFIX, ignoreCase = true) }

            Log.i(
                TAG,
                "Device[$address] name=${device.name ?: "unknown"} bondState=${device.bondState} type=$deviceType class=$className supportsA2dpUuid=$supportsA2dp isA2dpConnected=${address in a2dpConnected} isHeadsetConnected=${address in headsetConnected} isLeConnected=${address in leConnected}"
            )
        }
    }

    private fun nudgeMediaRouting(audioManager: AudioManager?) {
        if (audioManager == null) return

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .build()

        val focusResult = audioManager.requestAudioFocus(focusRequest)
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, 0)
        audioManager.abandonAudioFocusRequest(focusRequest)

        Log.i(TAG, "Routing nudge issued. focusResult=$focusResult")
    }

    private fun readBluetoothOutputAddresses(audioManager: AudioManager?): Set<String> {
        if (audioManager == null) return emptySet()

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter(::isBluetoothOutput)
            .mapNotNull { device ->
                device.address
                    .takeIf { it.isMacAddress() && it != ZERO_ADDRESS }
                    ?.normalizedAddress()
            }
            .toSet()
    }

    private fun isBluetoothOutput(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            else -> false
        }
    }

    private fun String.normalizedAddress(): String = uppercase(Locale.US)

    private fun String.isMacAddress(): Boolean {
        return MAC_ADDRESS_REGEX.matches(normalizedAddress())
    }

    private fun BluetoothManager?.connectedAddresses(profile: Int): Set<String> {
        if (this == null) return emptySet()
        return runCatching {
            getConnectedDevices(profile)
                .map { it.address.normalizedAddress() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun describeBluetoothClass(btClass: BluetoothClass?): String {
        return when (btClass?.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "AUDIO_VIDEO_LOUDSPEAKER"
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "AUDIO_VIDEO_HEADPHONES"
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "AUDIO_VIDEO_WEARABLE_HEADSET"
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "AUDIO_VIDEO_HIFI_AUDIO"
            null -> "unknown"
            else -> btClass.deviceClass.toString()
        }
    }

    private fun describeDeviceType(type: Int): String {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "UNKNOWN"
            else -> type.toString()
        }
    }

    private const val A2DP_UUID_PREFIX = "0000110d"
    private const val ZERO_ADDRESS = "00:00:00:00:00:00"
    private val MAC_ADDRESS_REGEX = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
}
