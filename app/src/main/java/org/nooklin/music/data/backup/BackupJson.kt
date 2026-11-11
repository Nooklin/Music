package org.nooklin.music.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONObject

/**
 * Turns the allowed settings of one DataStore into JSON and back. Only the names in `allowed` are read or written, in
 * both directions, so a backup file can never set anything else. Each value is stored as {"t": type, "v": value}.
 */
object BackupJson {
    fun dump(prefs: Preferences, allowed: Set<String>): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.asMap()) {
            if (key.name !in allowed) continue
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                else -> continue
            }
            out.put(key.name, entry)
        }
        return out
    }

    fun apply(target: MutablePreferences, json: JSONObject, allowed: Set<String>) {
        for (name in allowed) {
            val entry = json.optJSONObject(name) ?: continue
            when (entry.optString("t")) {
                "s" -> target[stringPreferencesKey(name)] = entry.optString("v")
                "b" -> target[booleanPreferencesKey(name)] = entry.optBoolean("v")
                "i" -> target[intPreferencesKey(name)] = entry.optInt("v")
                "l" -> target[longPreferencesKey(name)] = entry.optLong("v")
                "f" -> target[floatPreferencesKey(name)] = entry.optDouble("v").toFloat()
            }
        }
    }
}
