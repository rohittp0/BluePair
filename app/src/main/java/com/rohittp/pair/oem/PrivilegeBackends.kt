package com.rohittp.pair.oem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

interface PrivilegeBackend {
    val name: String
    fun canWriteSecureSettings(context: Context): Boolean
    fun putGlobal(context: Context, key: String, value: String): Boolean
    fun putSecure(context: Context, key: String, value: String): Boolean
    fun getGlobal(context: Context, key: String): String?
    fun getSecure(context: Context, key: String): String?
    fun listNamespace(namespace: String): Map<String, String>
}

object PrivilegeBackendResolver {
    fun resolve(context: Context): PrivilegeBackend {
        if (ShizukuBackend.isAvailable(context)) {
            return ShizukuBackend
        }
        if (WriteSecureSettingsBackend.canWriteSecureSettings(context)) {
            return WriteSecureSettingsBackend
        }
        return NoPrivilegeBackend
    }
}

object ShizukuBackend : PrivilegeBackend {
    override val name: String = "shizuku"

    fun isAvailable(context: Context): Boolean {
        return isPackageInstalled(context, "moe.shizuku.privileged.api") ||
            isPackageInstalled(context, "moe.shizuku.manager") ||
            ShizukuBridge.getStatus().binderAlive
    }

    override fun canWriteSecureSettings(context: Context): Boolean {
        return ShizukuBridge.getStatus().permissionGranted
    }

    override fun putGlobal(context: Context, key: String, value: String): Boolean {
        return ShizukuBridge.runCommand("settings", "put", "global", key, value).isSuccess
    }

    override fun putSecure(context: Context, key: String, value: String): Boolean {
        return ShizukuBridge.runCommand("settings", "put", "secure", key, value).isSuccess
    }

    override fun getGlobal(context: Context, key: String): String? {
        val result = ShizukuBridge.runCommand("settings", "get", "global", key)
        return if (result.isSuccess) result.stdout.trim() else null
    }

    override fun getSecure(context: Context, key: String): String? {
        val result = ShizukuBridge.runCommand("settings", "get", "secure", key)
        return if (result.isSuccess) result.stdout.trim() else null
    }

    override fun listNamespace(namespace: String): Map<String, String> {
        val result = ShizukuBridge.runCommand("settings", "list", namespace)
        if (!result.isSuccess) return emptyMap()
        return parseSettingsList(result.stdout)
    }
}

object WriteSecureSettingsBackend : PrivilegeBackend {
    override val name: String = "write_secure_settings"

    override fun canWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun putGlobal(context: Context, key: String, value: String): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Global.putString(context.contentResolver, key, value)
        }.getOrDefault(false)
    }

    override fun putSecure(context: Context, key: String, value: String): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Secure.putString(context.contentResolver, key, value)
        }.getOrDefault(false)
    }

    override fun getGlobal(context: Context, key: String): String? {
        return runCatching {
            Settings.Global.getString(context.contentResolver, key)
        }.getOrNull()
    }

    override fun getSecure(context: Context, key: String): String? {
        return runCatching {
            Settings.Secure.getString(context.contentResolver, key)
        }.getOrNull()
    }

    override fun listNamespace(namespace: String): Map<String, String> {
        return LocalShellRunner.listSettings(namespace)
    }
}

object NoPrivilegeBackend : PrivilegeBackend {
    override val name: String = "none"

    override fun canWriteSecureSettings(context: Context): Boolean = false

    override fun putGlobal(context: Context, key: String, value: String): Boolean = false

    override fun putSecure(context: Context, key: String, value: String): Boolean = false

    override fun getGlobal(context: Context, key: String): String? =
        runCatching { Settings.Global.getString(context.contentResolver, key) }.getOrNull()

    override fun getSecure(context: Context, key: String): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()

    override fun listNamespace(namespace: String): Map<String, String> {
        return LocalShellRunner.listSettings(namespace)
    }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess
}

private object LocalShellRunner {
    fun listSettings(namespace: String): Map<String, String> {
        val output = runCommand("settings", "list", namespace) ?: return emptyMap()
        return parseSettingsList(output)
    }

    private fun runCommand(vararg command: String): String? {
        return runCatching {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

private fun parseSettingsList(output: String): Map<String, String> {
    return output.lineSequence()
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            key to value
        }
        .toMap()
}
