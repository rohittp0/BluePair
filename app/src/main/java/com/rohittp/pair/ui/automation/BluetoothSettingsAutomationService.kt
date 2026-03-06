package com.rohittp.pair.ui.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairPrefs

class BluetoothSettingsAutomationService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!BluePairPrefs.isOemAutomationRequested(this)) return

        val root = rootInActiveWindow ?: return
        val dualAudioClicked = clickByText(
            root,
            listOf(
                "Dual audio",
                "Dual Audio",
                "Dual connection",
                "Audio sharing",
                "Share audio"
            )
        )

        if (dualAudioClicked) {
            BluePairPrefs.setOemAutomationRequested(this, false)
            BluePairPrefs.setRoutingDetail(
                this,
                getString(R.string.automation_complete_detail)
            )
            return
        }

        clickByText(
            root,
            listOf(
                "Connected devices",
                "Bluetooth",
                "Device settings"
            )
        )
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    private fun clickByText(root: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        candidates.forEach { candidate ->
            val nodes = root.findAccessibilityNodeInfosByText(candidate)
            nodes?.forEach { node ->
                if (clickNodeOrParent(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    companion object {
        fun requestDualAudioAutomation(context: Context) {
            BluePairPrefs.setOemAutomationRequested(context, true)
            BluePairPrefs.setRoutingDetail(
                context,
                context.getString(R.string.automation_running_detail)
            )
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
