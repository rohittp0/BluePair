package com.rohittp.pair.routing

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rohittp.pair.MainActivity
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairActions
import com.rohittp.pair.core.BluePairPrefs

class BluetoothRoutingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BluePairActions.ACTION_DISABLE_ROUTING -> stopRouting()
            else -> startRouting()
        }
        return START_STICKY
    }

    private fun startRouting() {
        ensureNotificationChannel()
        if (!hasValidConfiguration()) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(
                    contentText = getString(R.string.notification_missing_config),
                    isActive = false
                )
            )
            BluePairPrefs.setBluetoothModeEnabled(this, false)
            BluePairController.broadcastStateChanged(this)
            stopSelf()
            return
        }

        BluePairPrefs.setBluetoothModeEnabled(this, true)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                contentText = getString(R.string.notification_text),
                isActive = true
            )
        )
        BluePairController.broadcastStateChanged(this)
    }

    private fun stopRouting() {
        BluePairPrefs.setBluetoothModeEnabled(this, false)
        BluePairController.broadcastStateChanged(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        BluePairPrefs.setBluetoothModeEnabled(this, false)
        BluePairController.broadcastStateChanged(this)
        super.onDestroy()
    }

    private fun buildNotification(contentText: String, isActive: Boolean) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(appLaunchPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(isActive)
            .addAction(
                R.mipmap.ic_launcher,
                getString(R.string.notification_action_stop),
                stopPendingIntent()
            )
            .build()

    private fun appLaunchPendingIntent(): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_LAUNCH,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, BluetoothRoutingService::class.java)
            .setAction(BluePairActions.ACTION_DISABLE_ROUTING)
        return PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun hasValidConfiguration(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val selectedDevices = BluePairPrefs.getSelectedDeviceAddresses(this)
        val left = BluePairPrefs.getLeftDeviceAddress(this)
        val right = BluePairPrefs.getRightDeviceAddress(this)

        if (selectedDevices.size < 2 || left == null || right == null || left == right) {
            return false
        }
        if (!selectedDevices.contains(left) || !selectedDevices.contains(right)) {
            return false
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val pairedAddresses = bluetoothAdapter.bondedDevices.map { it.address }.toSet()
        return pairedAddresses.contains(left) && pairedAddresses.contains(right)
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "blue_pair_routing_channel"
        const val NOTIFICATION_ID = 1501
        const val REQUEST_CODE_LAUNCH = 2201
        const val REQUEST_CODE_STOP = 2202
    }
}
