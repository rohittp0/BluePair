package com.rohittp.pair.routing

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairActions
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.quicksettings.BluePairTileService
import com.rohittp.pair.widget.BluePairWidgetProvider

object BluePairController {
    fun toggleBluetoothMode(context: android.content.Context) {
        setBluetoothModeEnabled(context, !BluePairPrefs.isBluetoothModeEnabled(context))
    }

    fun setBluetoothModeEnabled(context: android.content.Context, enabled: Boolean) {
        val appContext = context.applicationContext
        if (enabled) {
            BluePairPrefs.setBluetoothModeEnabled(appContext, true)
            BluePairPrefs.setRoutingState(appContext, RoutingState.ENABLING)
            BluePairPrefs.setRoutingDetail(
                appContext,
                appContext.getString(R.string.routing_detail_enabling)
            )
            broadcastStateChanged(appContext)
            try {
                val intent = Intent(appContext, BluetoothRoutingService::class.java)
                    .setAction(BluePairActions.ACTION_ENABLE_ROUTING)
                ContextCompat.startForegroundService(appContext, intent)
            } catch (error: Exception) {
                Log.e("BLUEPAIR_ROUTING", "Failed to start routing service", error)
                BluePairPrefs.setBluetoothModeEnabled(appContext, false)
                BluePairPrefs.setRoutingState(appContext, RoutingState.OFF)
                BluePairPrefs.setRoutingDetail(
                    appContext,
                    appContext.getString(R.string.routing_detail_off)
                )
                broadcastStateChanged(appContext)
            }
            return
        }

        appContext.stopService(Intent(appContext, BluetoothRoutingService::class.java))
        BluePairPrefs.setBluetoothModeEnabled(appContext, false)
        BluePairPrefs.setRoutingState(appContext, RoutingState.OFF)
        BluePairPrefs.setRoutingDetail(appContext, appContext.getString(R.string.routing_detail_off))
        broadcastStateChanged(appContext)
    }

    fun broadcastStateChanged(context: android.content.Context) {
        val appContext = context.applicationContext
        appContext.sendBroadcast(
            Intent(BluePairActions.ACTION_STATE_CHANGED).setPackage(appContext.packageName)
        )
        BluePairWidgetProvider.updateAllWidgets(appContext)
        BluePairTileService.requestTileRefresh(appContext)
    }
}
