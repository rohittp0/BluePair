package com.rohittp.pair.core

import android.content.Context

object BluePairPrefs {
    private const val PREFS_NAME = "blue_pair_prefs"
    private const val KEY_BLUETOOTH_MODE = "bluetooth_mode_enabled"
    private const val KEY_SELECTED_DEVICES = "selected_devices"
    private const val KEY_LEFT_DEVICE = "left_device"
    private const val KEY_RIGHT_DEVICE = "right_device"

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
}
