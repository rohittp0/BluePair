package com.rohittp.pair.routing

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rohittp.pair.MainActivity
import com.rohittp.pair.R
import com.rohittp.pair.core.BluePairActions
import com.rohittp.pair.core.BluePairPrefs
import com.rohittp.pair.oem.OemControllerRegistry

class BluetoothRoutingService : Service() {
    private val serviceTag = "BLUEPAIR_ROUTING"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var probeGeneration = 0

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
        when (val validation = validateConfiguration()) {
            is ConfigurationValidation.BlockedConfig -> {
                Log.w(serviceTag, "Routing start blocked by configuration.")
                handleBlockedState(
                    state = RoutingState.BLOCKED_CONFIG,
                    detail = getString(R.string.routing_detail_blocked_config)
                )
            }

            is ConfigurationValidation.BlockedPermission -> {
                Log.w(serviceTag, "Routing start blocked by Bluetooth permission.")
                handleBlockedState(
                    state = RoutingState.BLOCKED_PERMISSION,
                    detail = getString(R.string.routing_detail_blocked_permission)
                )
            }

            is ConfigurationValidation.Valid -> {
                beginRouting(validation.configuration)
            }
        }
    }

    private fun beginRouting(configuration: RoutingConfiguration) {
        val oemController = OemControllerRegistry.resolve(this)
        BluePairPrefs.setOemControllerName(this, oemController.name)
        val oemResult = oemController.enableDualOutput(
            context = this,
            leftAddress = configuration.leftAddress,
            rightAddress = configuration.rightAddress
        )

        BluePairPrefs.setBluetoothModeEnabled(this, true)
        BluePairPrefs.setRoutingMode(this, RoutingMode.LE_AUDIO_PREFERRED)
        val enablingDetail = buildString {
            append(getString(R.string.routing_detail_enabling))
            if (oemResult.detail.isNotBlank()) {
                append(' ').append(oemResult.detail)
            }
            if (oemResult.requiresManualStep) {
                append(' ').append(getString(R.string.routing_detail_oem_manual_step))
            }
        }
        updateRoutingState(
            state = RoutingState.ENABLING,
            detail = enablingDetail,
            appendDiagnostics = true
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                contentText = BluePairPrefs.getRoutingDetail(this),
                isActive = true
            )
        )
        BluePairController.broadcastStateChanged(this)

        val nextGeneration = ++probeGeneration
        scheduleRoutingProbe(
            configuration = configuration,
            generation = nextGeneration,
            attempt = 0,
            shouldNudge = true
        )
    }

    private fun scheduleRoutingProbe(
        configuration: RoutingConfiguration,
        generation: Int,
        attempt: Int,
        shouldNudge: Boolean
    ) {
        if (generation != probeGeneration) return

        val probe = BluetoothRoutingEngine.probeRouting(
            context = this,
            leftAddress = configuration.leftAddress,
            rightAddress = configuration.rightAddress,
            shouldNudge = shouldNudge
        )
        val timedOut = attempt >= MAX_PROBE_ATTEMPTS
        val decision = decideState(probe, timedOut)

        updateRoutingState(
            state = decision.state,
            detail = decision.detail,
            appendDiagnostics = decision.appendDiagnostics,
            probe = probe
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                contentText = decision.detail,
                isActive = true
            )
        )
        BluePairController.broadcastStateChanged(this)

        if (!decision.shouldContinuePolling) return

        mainHandler.postDelayed(
            {
                scheduleRoutingProbe(
                    configuration = configuration,
                    generation = generation,
                    attempt = attempt + 1,
                    shouldNudge = false
                )
            },
            PROBE_INTERVAL_MS
        )
    }

    private fun decideState(
        probe: BluetoothRoutingProbeResult,
        timedOut: Boolean
    ): RoutingDecision {
        if (probe.isDualOutputActive) {
            return RoutingDecision(
                state = RoutingState.ACTIVE_DUAL,
                detail = getString(R.string.routing_detail_active_dual),
                shouldContinuePolling = false,
                appendDiagnostics = true
            )
        }

        if (!timedOut) {
            return RoutingDecision(
                state = RoutingState.WAITING,
                detail = getString(R.string.routing_detail_waiting),
                shouldContinuePolling = true,
                appendDiagnostics = false
            )
        }

        if (probe.isSingleOutputActive) {
            val detail = if (probe.isLikelyDualClassicConnected) {
                getString(R.string.routing_detail_dual_connected_single_active_classic)
            } else if (probe.hasLeCandidate) {
                getString(R.string.routing_detail_active_single_le)
            } else {
                getString(R.string.routing_detail_active_single_classic)
            }
            return RoutingDecision(
                state = RoutingState.ACTIVE_SINGLE,
                detail = detail,
                shouldContinuePolling = false,
                appendDiagnostics = true
            )
        }

        val limitedDetail = if (probe.isLikelyDualClassicConnected) {
            getString(R.string.routing_detail_dual_connected_single_active_classic)
        } else if (probe.hasLeCandidate) {
            getString(R.string.routing_detail_platform_limited_le_hint)
        } else {
            getString(R.string.routing_detail_platform_limited_classic)
        }
        return RoutingDecision(
            state = RoutingState.PLATFORM_LIMITED,
            detail = limitedDetail,
            shouldContinuePolling = false,
            appendDiagnostics = true
        )
    }

    private fun handleBlockedState(state: RoutingState, detail: String) {
        BluePairPrefs.setBluetoothModeEnabled(this, false)
        updateRoutingState(
            state = state,
            detail = detail,
            appendDiagnostics = true
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                contentText = detail,
                isActive = false
            )
        )
        BluePairController.broadcastStateChanged(this)
        stopSelf()
    }

    private fun stopRouting() {
        probeGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)

        OemControllerRegistry.resolve(this).disableDualOutput(this)
        BluePairPrefs.setBluetoothModeEnabled(this, false)
        updateRoutingState(
            state = RoutingState.OFF,
            detail = getString(R.string.routing_detail_off),
            appendDiagnostics = false
        )
        BluePairController.broadcastStateChanged(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        probeGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)
        val wasEnabled = BluePairPrefs.isBluetoothModeEnabled(this)
        if (wasEnabled) {
            BluePairPrefs.setBluetoothModeEnabled(this, false)
            updateRoutingState(
                state = RoutingState.OFF,
                detail = getString(R.string.routing_detail_off),
                appendDiagnostics = false
            )
            BluePairController.broadcastStateChanged(this)
        }
        super.onDestroy()
    }

    private fun updateRoutingState(
        state: RoutingState,
        detail: String,
        appendDiagnostics: Boolean,
        probe: BluetoothRoutingProbeResult? = null
    ) {
        BluePairPrefs.setRoutingState(this, state)
        BluePairPrefs.setRoutingDetail(this, detail)

        if (!appendDiagnostics) return

        BluePairPrefs.appendRoutingDiagnostics(
            context = this,
            record = RoutingDiagnosticsRecord(
                timestampMs = System.currentTimeMillis(),
                state = state,
                detail = detail,
                requestedAddresses = probe?.requestedAddresses.orEmpty(),
                a2dpConnectedAddresses = probe?.a2dpConnectedAddresses.orEmpty(),
                headsetConnectedAddresses = probe?.headsetConnectedAddresses.orEmpty(),
                leConnectedAddresses = probe?.leConnectedAddresses.orEmpty(),
                activeOutputAddresses = probe?.activeOutputAddresses.orEmpty(),
                manufacturer = BluetoothRoutingEngine.environmentManufacturer(),
                model = BluetoothRoutingEngine.environmentModel(),
                sdkInt = BluetoothRoutingEngine.environmentSdkInt()
            )
        )
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
    private fun validateConfiguration(): ConfigurationValidation {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return ConfigurationValidation.BlockedPermission
        }

        val selectedDevices = BluePairPrefs.getSelectedDeviceAddresses(this)
        val left = BluePairPrefs.getLeftDeviceAddress(this)
        val right = BluePairPrefs.getRightDeviceAddress(this)
        if (selectedDevices.size < 2 || left == null || right == null || left == right) {
            return ConfigurationValidation.BlockedConfig
        }
        if (!selectedDevices.contains(left) || !selectedDevices.contains(right)) {
            return ConfigurationValidation.BlockedConfig
        }

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter ?: return ConfigurationValidation.BlockedConfig
        val pairedAddresses = bluetoothAdapter.bondedDevices.map { it.address }.toSet()
        if (!pairedAddresses.contains(left) || !pairedAddresses.contains(right)) {
            return ConfigurationValidation.BlockedConfig
        }

        return ConfigurationValidation.Valid(
            configuration = RoutingConfiguration(leftAddress = left, rightAddress = right)
        )
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "blue_pair_routing_channel"
        const val NOTIFICATION_ID = 1501
        const val REQUEST_CODE_LAUNCH = 2201
        const val REQUEST_CODE_STOP = 2202
        const val PROBE_INTERVAL_MS = 500L
        const val MAX_PROBE_ATTEMPTS = 12
    }

    private sealed class ConfigurationValidation {
        data class Valid(val configuration: RoutingConfiguration) : ConfigurationValidation()
        data object BlockedConfig : ConfigurationValidation()
        data object BlockedPermission : ConfigurationValidation()
    }

    private data class RoutingConfiguration(
        val leftAddress: String,
        val rightAddress: String
    )

    private data class RoutingDecision(
        val state: RoutingState,
        val detail: String,
        val shouldContinuePolling: Boolean,
        val appendDiagnostics: Boolean
    )
}
