package com.rohittp.pair.routing

enum class RoutingMode(val rawValue: String) {
    CLASSIC_BEST_EFFORT("classic_best_effort"),
    LE_AUDIO_PREFERRED("le_audio_preferred");

    companion object {
        fun fromRaw(value: String?): RoutingMode {
            return entries.firstOrNull { it.rawValue == value } ?: LE_AUDIO_PREFERRED
        }
    }
}

enum class RoutingState(val rawValue: String) {
    OFF("off"),
    ENABLING("enabling"),
    ACTIVE_DUAL("active_dual"),
    ACTIVE_SINGLE("active_single"),
    WAITING("waiting"),
    BLOCKED_CONFIG("blocked_config"),
    BLOCKED_PERMISSION("blocked_permission"),
    PLATFORM_LIMITED("platform_limited");

    companion object {
        fun fromRaw(value: String?): RoutingState {
            return entries.firstOrNull { it.rawValue == value } ?: OFF
        }
    }
}

data class RoutingDiagnosticsRecord(
    val timestampMs: Long,
    val state: RoutingState,
    val detail: String,
    val requestedAddresses: Set<String>,
    val a2dpConnectedAddresses: Set<String>,
    val headsetConnectedAddresses: Set<String>,
    val leConnectedAddresses: Set<String>,
    val activeOutputAddresses: Set<String>,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int
)
