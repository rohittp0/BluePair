package com.rohittp.pair.core

import android.content.Context
import com.rohittp.pair.routing.RoutingDiagnosticsRecord
import com.rohittp.pair.routing.RoutingMode
import com.rohittp.pair.routing.RoutingState
import org.json.JSONArray
import org.json.JSONObject

object BluePairPrefs {
    private const val PREFS_NAME = "blue_pair_prefs"
    private const val KEY_BLUETOOTH_MODE = "bluetooth_mode_enabled"
    private const val KEY_SELECTED_DEVICES = "selected_devices"
    private const val KEY_LEFT_DEVICE = "left_device"
    private const val KEY_RIGHT_DEVICE = "right_device"
    private const val KEY_ROUTING_STATE = "routing_state"
    private const val KEY_ROUTING_DETAIL = "routing_detail"
    private const val KEY_ROUTING_MODE = "routing_mode"
    private const val KEY_ROUTING_DIAGNOSTICS = "routing_diagnostics"
    private const val KEY_OEM_AUTOMATION_REQUESTED = "oem_automation_requested"
    private const val KEY_OEM_SNAPSHOT_BASELINE = "oem_snapshot_baseline"
    private const val KEY_OEM_SNAPSHOT_LAST_DIFF = "oem_snapshot_last_diff"
    private const val KEY_OEM_CONTROLLER_NAME = "oem_controller_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBluetoothModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLUETOOTH_MODE, false)

    fun setBluetoothModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLUETOOTH_MODE, enabled).apply()
    }

    fun getSelectedDeviceAddresses(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED_DEVICES, emptySet())?.toSet().orEmpty()

    fun setSelectedDeviceAddresses(context: Context, addresses: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_SELECTED_DEVICES, addresses.toMutableSet())
            .apply()
    }

    fun getLeftDeviceAddress(context: Context): String? =
        prefs(context).getString(KEY_LEFT_DEVICE, null)

    fun setLeftDeviceAddress(context: Context, address: String?) {
        prefs(context).edit().putString(KEY_LEFT_DEVICE, address).apply()
    }

    fun getRightDeviceAddress(context: Context): String? =
        prefs(context).getString(KEY_RIGHT_DEVICE, null)

    fun setRightDeviceAddress(context: Context, address: String?) {
        prefs(context).edit().putString(KEY_RIGHT_DEVICE, address).apply()
    }

    fun getRoutingState(context: Context): RoutingState =
        RoutingState.fromRaw(prefs(context).getString(KEY_ROUTING_STATE, null))

    fun setRoutingState(context: Context, state: RoutingState) {
        prefs(context).edit().putString(KEY_ROUTING_STATE, state.rawValue).apply()
    }

    fun getRoutingDetail(context: Context): String =
        prefs(context).getString(KEY_ROUTING_DETAIL, "").orEmpty()

    fun setRoutingDetail(context: Context, detail: String) {
        prefs(context).edit().putString(KEY_ROUTING_DETAIL, detail).apply()
    }

    fun getRoutingMode(context: Context): RoutingMode =
        RoutingMode.fromRaw(prefs(context).getString(KEY_ROUTING_MODE, null))

    fun setRoutingMode(context: Context, mode: RoutingMode) {
        prefs(context).edit().putString(KEY_ROUTING_MODE, mode.rawValue).apply()
    }

    fun appendRoutingDiagnostics(
        context: Context,
        record: RoutingDiagnosticsRecord,
        maxEntries: Int = 40
    ) {
        val current = JSONArray(prefs(context).getString(KEY_ROUTING_DIAGNOSTICS, "[]"))
        val next = JSONArray()
        for (index in 0 until current.length()) {
            next.put(current.getJSONObject(index))
        }
        next.put(record.toJson())

        while (next.length() > maxEntries) {
            next.remove(0)
        }

        prefs(context).edit()
            .putString(KEY_ROUTING_DIAGNOSTICS, next.toString())
            .apply()
    }

    fun getRoutingDiagnostics(context: Context): List<RoutingDiagnosticsRecord> {
        val raw = prefs(context).getString(KEY_ROUTING_DIAGNOSTICS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val diagnostics = mutableListOf<RoutingDiagnosticsRecord>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            diagnostics.add(
                RoutingDiagnosticsRecord(
                    timestampMs = obj.optLong("timestampMs"),
                    state = RoutingState.fromRaw(obj.optString("state")),
                    detail = obj.optString("detail"),
                    requestedAddresses = obj.optStringSet("requestedAddresses"),
                    a2dpConnectedAddresses = obj.optStringSet("a2dpConnectedAddresses"),
                    headsetConnectedAddresses = obj.optStringSet("headsetConnectedAddresses"),
                    leConnectedAddresses = obj.optStringSet("leConnectedAddresses"),
                    activeOutputAddresses = obj.optStringSet("activeOutputAddresses"),
                    manufacturer = obj.optString("manufacturer"),
                    model = obj.optString("model"),
                    sdkInt = obj.optInt("sdkInt")
                )
            )
        }
        return diagnostics
    }

    fun clearRoutingDiagnostics(context: Context) {
        prefs(context).edit().putString(KEY_ROUTING_DIAGNOSTICS, "[]").apply()
    }

    fun isOemAutomationRequested(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OEM_AUTOMATION_REQUESTED, false)

    fun setOemAutomationRequested(context: Context, requested: Boolean) {
        prefs(context).edit().putBoolean(KEY_OEM_AUTOMATION_REQUESTED, requested).apply()
    }

    fun getOemSnapshotBaseline(context: Context): String =
        prefs(context).getString(KEY_OEM_SNAPSHOT_BASELINE, "").orEmpty()

    fun setOemSnapshotBaseline(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OEM_SNAPSHOT_BASELINE, value).apply()
    }

    fun getOemSnapshotLastDiff(context: Context): String =
        prefs(context).getString(KEY_OEM_SNAPSHOT_LAST_DIFF, "").orEmpty()

    fun setOemSnapshotLastDiff(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OEM_SNAPSHOT_LAST_DIFF, value).apply()
    }

    fun getOemControllerName(context: Context): String =
        prefs(context).getString(KEY_OEM_CONTROLLER_NAME, "").orEmpty()

    fun setOemControllerName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OEM_CONTROLLER_NAME, value).apply()
    }

    private fun RoutingDiagnosticsRecord.toJson(): JSONObject {
        return JSONObject()
            .put("timestampMs", timestampMs)
            .put("state", state.rawValue)
            .put("detail", detail)
            .put("requestedAddresses", JSONArray(requestedAddresses.toList()))
            .put("a2dpConnectedAddresses", JSONArray(a2dpConnectedAddresses.toList()))
            .put("headsetConnectedAddresses", JSONArray(headsetConnectedAddresses.toList()))
            .put("leConnectedAddresses", JSONArray(leConnectedAddresses.toList()))
            .put("activeOutputAddresses", JSONArray(activeOutputAddresses.toList()))
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("sdkInt", sdkInt)
    }

    private fun JSONObject.optStringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        val values = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            values.add(array.optString(index))
        }
        return values
    }
}
