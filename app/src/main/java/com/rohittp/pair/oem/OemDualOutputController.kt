package com.rohittp.pair.oem

import android.content.Context

enum class OemAction {
    NONE,
    SETTINGS_WRITE,
    ACCESSIBILITY_REQUIRED,
    UNSUPPORTED
}

data class OemControlResult(
    val success: Boolean,
    val actionTaken: OemAction,
    val detail: String,
    val requiresManualStep: Boolean
)

data class OemDualOutputState(
    val isDualOutputEnabled: Boolean,
    val activeDeviceAddresses: Set<String>,
    val rawSignals: Map<String, String>
)

interface OemDualOutputController {
    val name: String

    fun isSupported(context: Context): Boolean

    fun probeState(context: Context): OemDualOutputState

    fun enableDualOutput(
        context: Context,
        leftAddress: String,
        rightAddress: String
    ): OemControlResult

    fun disableDualOutput(context: Context): OemControlResult
}
