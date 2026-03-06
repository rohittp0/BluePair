package com.rohittp.pair.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairActions
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.routing.BluePairController
import com.rohittp.pair.routing.RoutingState

class BluePairWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == BluePairActions.ACTION_TOGGLE_FROM_WIDGET) {
            BluePairController.toggleBluetoothMode(context)
            updateAllWidgets(context)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BluePairWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isEmpty()) return
            widgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context))
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val routingState = BluePairPrefs.getRoutingState(context)
            val statusText = when (routingState) {
                RoutingState.ACTIVE_DUAL -> context.getString(R.string.routing_state_active_dual)
                RoutingState.ACTIVE_SINGLE -> context.getString(R.string.routing_state_active_single)
                RoutingState.WAITING -> context.getString(R.string.routing_state_waiting)
                RoutingState.ENABLING -> context.getString(R.string.routing_state_enabling)
                RoutingState.PLATFORM_LIMITED -> context.getString(R.string.routing_state_platform_limited)
                RoutingState.BLOCKED_CONFIG -> context.getString(R.string.routing_state_blocked_config)
                RoutingState.BLOCKED_PERMISSION -> context.getString(R.string.routing_state_blocked_permission)
                RoutingState.OFF -> context.getString(R.string.routing_state_off)
            }

            val toggleIntent = Intent(context, BluePairWidgetProvider::class.java).apply {
                action = BluePairActions.ACTION_TOGGLE_FROM_WIDGET
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                3401,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_blue_pair).apply {
                setTextViewText(R.id.widget_status, statusText)
                setOnClickPendingIntent(R.id.widget_toggle, togglePendingIntent)
            }
        }
    }
}
