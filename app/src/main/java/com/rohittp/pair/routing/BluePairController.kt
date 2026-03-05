package com.rohittp.pair.routing

import android.content.Intent
import androidx.core.content.ContextCompat
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
            val intent = Intent(appContext, BluetoothRoutingService::class.java)
                .setAction(BluePairActions.ACTION_ENABLE_ROUTING)
            ContextCompat.startForegroundService(appContext, intent)
            return
        }

        appContext.stopService(Intent(appContext, BluetoothRoutingService::class.java))
        BluePairPrefs.setBluetoothModeEnabled(appContext, false)
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
