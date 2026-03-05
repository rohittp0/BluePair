package com.rohittp.pair.quicksettings

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.routing.BluePairController

class BluePairTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        BluePairController.toggleBluetoothMode(this)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isEnabled = BluePairPrefs.isBluetoothModeEnabled(this)
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(
            if (isEnabled) R.string.widget_status_on else R.string.widget_status_off
        )
        tile.updateTile()
    }

    companion object {
        fun requestTileRefresh(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, BluePairTileService::class.java)
            )
        }
    }
}
