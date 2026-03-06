package com.rohittp.pair.oem

import android.content.Context
import org.json.JSONObject

data class OemSettingsSnapshot(
    val global: Map<String, String>,
    val secure: Map<String, String>,
    val system: Map<String, String>
)

object OemSnapshotCollector {
    fun captureSnapshot(context: Context): OemSettingsSnapshot {
        val backend = PrivilegeBackendResolver.resolve(context)
        return OemSettingsSnapshot(
            global = backend.listNamespace("global"),
            secure = backend.listNamespace("secure"),
            system = backend.listNamespace("system")
        )
    }

    fun toJson(snapshot: OemSettingsSnapshot): String {
        return JSONObject()
            .put("global", JSONObject(snapshot.global))
            .put("secure", JSONObject(snapshot.secure))
            .put("system", JSONObject(snapshot.system))
            .toString()
    }

    fun fromJson(json: String): OemSettingsSnapshot? {
        return runCatching {
            val root = JSONObject(json)
            OemSettingsSnapshot(
                global = root.optJSONObject("global").toStringMap(),
                secure = root.optJSONObject("secure").toStringMap(),
                system = root.optJSONObject("system").toStringMap()
            )
        }.getOrNull()
    }

    fun diff(before: OemSettingsSnapshot, after: OemSettingsSnapshot): List<String> {
        val changes = mutableListOf<String>()
        changes += diffNamespace("global", before.global, after.global)
        changes += diffNamespace("secure", before.secure, after.secure)
        changes += diffNamespace("system", before.system, after.system)
        return changes
    }

    private fun diffNamespace(
        namespace: String,
        before: Map<String, String>,
        after: Map<String, String>
    ): List<String> {
        val allKeys = (before.keys + after.keys).toSortedSet()
        return allKeys.mapNotNull { key ->
            val beforeValue = before[key]
            val afterValue = after[key]
            if (beforeValue == afterValue) {
                null
            } else {
                "$namespace:$key => $beforeValue -> $afterValue"
            }
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = optString(key)
        }
        return map
    }
}
