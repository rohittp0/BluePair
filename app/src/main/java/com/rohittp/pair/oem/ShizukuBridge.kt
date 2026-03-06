package com.rohittp.pair.oem

import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.lang.reflect.Method
import rikka.shizuku.Shizuku

data class ShizukuStatus(
    val binderAlive: Boolean,
    val permissionGranted: Boolean
)

data class ShizukuCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

object ShizukuBridge {
    private const val TAG = "BLUEPAIR_SHIZUKU"
    private const val PERMISSION_REQUEST_CODE = 4003

    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    fun getStatus(): ShizukuStatus {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return ShizukuStatus(binderAlive = false, permissionGranted = false)
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuStatus(binderAlive = true, permissionGranted = granted)
    }

    fun requestPermissionIfNeeded(): Boolean {
        val status = getStatus()
        if (!status.binderAlive) return false
        if (status.permissionGranted) return true

        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            false
        }.getOrDefault(false)
    }

    fun runCommand(vararg command: String): ShizukuCommandResult {
        val status = getStatus()
        if (!status.binderAlive) {
            return ShizukuCommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku binder is not alive"
            )
        }
        if (!status.permissionGranted) {
            return ShizukuCommandResult(
                exitCode = -2,
                stdout = "",
                stderr = "Shizuku permission not granted"
            )
        }

        return runCatching {
            val method = newProcessMethod ?: error("Shizuku newProcess method not available")
            val process = method.invoke(
                null,
                command,
                null,
                null
            ) as Process
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            ShizukuCommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
        }.getOrElse { error ->
            Log.e(TAG, "Shizuku command failed: ${command.joinToString(" ")}", error)
            ShizukuCommandResult(
                exitCode = -3,
                stdout = "",
                stderr = error.message.orEmpty()
            )
        }
    }
}
