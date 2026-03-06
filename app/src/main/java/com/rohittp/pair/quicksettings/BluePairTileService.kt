package com.rohittp.pair.quicksettings

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.routing.BluePairController
import com.rohittp.pair.routing.RoutingState

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
        val routingState = BluePairPrefs.getRoutingState(this)
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = when (routingState) {
            RoutingState.ACTIVE_DUAL -> getString(R.string.routing_state_active_dual)
            RoutingState.ACTIVE_SINGLE -> getString(R.string.routing_state_active_single)
            RoutingState.WAITING -> getString(R.string.routing_state_waiting)
            RoutingState.ENABLING -> getString(R.string.routing_state_enabling)
            RoutingState.PLATFORM_LIMITED -> getString(R.string.routing_state_platform_limited)
            RoutingState.BLOCKED_CONFIG -> getString(R.string.routing_state_blocked_config)
            RoutingState.BLOCKED_PERMISSION -> getString(R.string.routing_state_blocked_permission)
            RoutingState.OFF -> getString(R.string.routing_state_off)
        }
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
