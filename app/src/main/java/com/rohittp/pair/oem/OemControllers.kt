package com.rohittp.pair.oem

import android.content.Context
import android.os.Build
import java.util.Locale

object OemControllerRegistry {
    fun resolve(context: Context): OemDualOutputController {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        return when {
            manufacturer.contains("oneplus") || manufacturer.contains("oppo") -> OnePlusDualOutputController
            manufacturer.contains("samsung") -> SamsungDualAudioController
            else -> GenericDualOutputController
        }
    }
}

object OnePlusDualOutputController : OemDualOutputController {
    override val name: String = "oneplus_dual_output"
    private const val AUDIO_DEVICE_INVENTORY_KEY = "audio_device_inventory"

    private val globalKeys = listOf(
        "dual_bluetooth_enable",
        "dual_audio_enable",
        "bluetooth_dual_audio",
        "oplus_dual_audio_enabled",
        "oplus_bluetooth_dual_audio",
        "oplus_bluetooth_dual_mode",
        "dual_bluetooth_audio",
        "multi_bluetooth_output"
    )

    private val secureKeys = listOf(
        "oplus_dual_audio_enabled",
        "dual_audio_enable",
        "bluetooth_dual_audio"
    )

    override fun isSupported(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        return manufacturer.contains("oneplus") || manufacturer.contains("oppo")
    }

    override fun probeState(context: Context): OemDualOutputState {
        val backend = PrivilegeBackendResolver.resolve(context)
        val signalMap = mutableMapOf<String, String>()

        globalKeys.forEach { key ->
            backend.getGlobal(context, key)?.let { signalMap["global:$key"] = it }
        }
        secureKeys.forEach { key ->
            backend.getSecure(context, key)?.let { signalMap["secure:$key"] = it }
        }

        val isEnabled = signalMap.values.any { value ->
            val normalized = value.trim().lowercase(Locale.US)
            normalized == "1" || normalized == "true" || normalized == "on"
        }

        return OemDualOutputState(
            isDualOutputEnabled = isEnabled,
            activeDeviceAddresses = emptySet(),
            rawSignals = signalMap
        )
    }

    override fun enableDualOutput(
        context: Context,
        leftAddress: String,
        rightAddress: String
    ): OemControlResult {
        val backend = PrivilegeBackendResolver.resolve(context)
        if (!backend.canWriteSecureSettings(context)) {
            return OemControlResult(
                success = false,
                actionTaken = OemAction.ACCESSIBILITY_REQUIRED,
                detail = "No privileged write backend. Enable Shizuku/WRITE_SECURE_SETTINGS for OEM toggles.",
                requiresManualStep = true
            )
        }

        val touched = mutableListOf<String>()
        globalKeys.forEach { key ->
            if (backend.putGlobal(context, key, "1")) {
                touched.add("global:$key")
            }
        }
        secureKeys.forEach { key ->
            if (backend.putSecure(context, key, "1")) {
                touched.add("secure:$key")
            }
        }

        val requestedAddresses = setOf(
            leftAddress.trim().uppercase(Locale.US),
            rightAddress.trim().uppercase(Locale.US)
        )
        val inventoryRaw = backend.getSecure(context, AUDIO_DEVICE_INVENTORY_KEY)
        val patchedInventory = inventoryRaw?.let {
            patchAudioDeviceInventory(it, requestedAddresses)
        }
        if (!patchedInventory.isNullOrBlank() && patchedInventory != inventoryRaw) {
            if (backend.putSecure(context, AUDIO_DEVICE_INVENTORY_KEY, patchedInventory)) {
                touched.add("secure:$AUDIO_DEVICE_INVENTORY_KEY")
            }
        }

        if (touched.isNotEmpty()) {
            return OemControlResult(
                success = true,
                actionTaken = OemAction.SETTINGS_WRITE,
                detail = "OEM toggle write attempted via ${backend.name}. Keys=${touched.joinToString()}",
                requiresManualStep = false
            )
        }

        return OemControlResult(
            success = false,
            actionTaken = OemAction.ACCESSIBILITY_REQUIRED,
            detail = "No OnePlus dual-audio key write succeeded via ${backend.name}.",
            requiresManualStep = true
        )
    }

    override fun disableDualOutput(context: Context): OemControlResult {
        val backend = PrivilegeBackendResolver.resolve(context)
        if (!backend.canWriteSecureSettings(context)) {
            return OemControlResult(
                success = false,
                actionTaken = OemAction.NONE,
                detail = "No privileged backend to disable OEM dual audio.",
                requiresManualStep = false
            )
        }

        globalKeys.forEach { key -> backend.putGlobal(context, key, "0") }
        secureKeys.forEach { key -> backend.putSecure(context, key, "0") }
        return OemControlResult(
            success = true,
            actionTaken = OemAction.SETTINGS_WRITE,
            detail = "OEM dual-audio disable attempted via ${backend.name}.",
            requiresManualStep = false
        )
    }

    private fun patchAudioDeviceInventory(
        raw: String,
        targetAddresses: Set<String>
    ): String? {
        if (raw.isBlank() || targetAddresses.isEmpty()) return null
        var changed = false
        val patchedEntries = raw.split('|').map { entry ->
            val parts = entry.split(',').toMutableList()
            if (parts.size < 3) return@map entry
            val type = parts[0].trim()
            val address = parts[1].trim().uppercase(Locale.US)
            if (type == "8" && address in targetAddresses && parts[2] != "1") {
                parts[2] = "1"
                changed = true
            }
            parts.joinToString(",")
        }
        return if (changed) patchedEntries.joinToString("|") else null
    }
}

object SamsungDualAudioController : OemDualOutputController {
    override val name: String = "samsung_dual_audio"

    private val candidateKeys = listOf("dual_audio", "bluetooth_dual_audio")

    override fun isSupported(context: Context): Boolean {
        return Build.MANUFACTURER.lowercase(Locale.US).contains("samsung")
    }

    override fun probeState(context: Context): OemDualOutputState {
        val backend = PrivilegeBackendResolver.resolve(context)
        val signals = candidateKeys.associate { key ->
            "global:$key" to backend.getGlobal(context, key).orEmpty()
        }
        val enabled = signals.values.any { it == "1" || it.equals("true", ignoreCase = true) }
        return OemDualOutputState(
            isDualOutputEnabled = enabled,
            activeDeviceAddresses = emptySet(),
            rawSignals = signals
        )
    }

    override fun enableDualOutput(
        context: Context,
        leftAddress: String,
        rightAddress: String
    ): OemControlResult {
        return OemControlResult(
            success = false,
            actionTaken = OemAction.ACCESSIBILITY_REQUIRED,
            detail = "Samsung path requires OEM Settings automation in this build.",
            requiresManualStep = true
        )
    }

    override fun disableDualOutput(context: Context): OemControlResult {
        return OemControlResult(
            success = false,
            actionTaken = OemAction.NONE,
            detail = "No Samsung disable action implemented.",
            requiresManualStep = false
        )
    }
}

object GenericDualOutputController : OemDualOutputController {
    override val name: String = "generic"

    override fun isSupported(context: Context): Boolean = true

    override fun probeState(context: Context): OemDualOutputState {
        return OemDualOutputState(
            isDualOutputEnabled = false,
            activeDeviceAddresses = emptySet(),
            rawSignals = emptyMap()
        )
    }

    override fun enableDualOutput(
        context: Context,
        leftAddress: String,
        rightAddress: String
    ): OemControlResult {
        return OemControlResult(
            success = false,
            actionTaken = OemAction.UNSUPPORTED,
            detail = "No OEM dual-output controller available for this manufacturer.",
            requiresManualStep = true
        )
    }

    override fun disableDualOutput(context: Context): OemControlResult {
        return OemControlResult(
            success = false,
            actionTaken = OemAction.NONE,
            detail = "No OEM dual-output controller available for this manufacturer.",
            requiresManualStep = false
        )
    }
}
