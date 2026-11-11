package ua.`in`.vyshgorod.svitlo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SvitloStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadSelection(): Selection {
        return Selection(
            group = prefs.getString(KEY_GROUP, "").orEmpty(),
            street = prefs.getString(KEY_STREET, null),
            house = prefs.getString(KEY_HOUSE, null),
            city = prefs.getString(KEY_CITY, null),
        )
    }

    fun loadPlaces(): List<Selection> {
        val raw = prefs.getString(KEY_PLACES, null)
        if (raw != null) {
            val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                selectionFrom(item).takeIf { it.group.isNotBlank() }
            }
        }
        val one = loadSelection()
        return if (one.group.isBlank()) emptyList() else listOf(one)
    }

    fun savePlaces(places: List<Selection>, focus: Selection) {
        val array = JSONArray()
        places.filter { it.group.isNotBlank() }.forEach { array.put(selectionJson(it)) }
        prefs.edit()
            .putString(KEY_PLACES, array.toString())
            .putString(KEY_GROUP, focus.group)
            .putString(KEY_STREET, focus.street)
            .putString(KEY_HOUSE, focus.house)
            .putString(KEY_CITY, focus.city)
            .commit()
    }

    fun saveSelection(selection: Selection) {
        val places = loadPlaces().filterNot { it.placeKey == selection.placeKey } + selection
        savePlaces(places.filter { it.group.isNotBlank() }.takeLast(8), selection)
    }

    fun loadSnapshot(): Snapshot? = SnapshotJson.fromJson(prefs.getString(KEY_SNAPSHOT, null))

    fun saveSnapshot(snapshot: Snapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, SnapshotJson.toJson(snapshot)).commit()
    }

    fun warnEnabled(): Boolean = prefs.getBoolean(KEY_WARN, true)

    fun notifyWhenOn(): Boolean = prefs.getBoolean(KEY_ON, true)

    fun setWarnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WARN, enabled).commit()
    }

    fun setNotifyWhenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ON, enabled).commit()
    }

    fun quietNight(): Boolean = prefs.getBoolean(KEY_QUIET, false)

    fun setQuietNight(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET, enabled).commit()
    }

    fun themeMode(): ThemeMode = when (prefs.getString(KEY_THEME, ThemeMode.System.stored)) {
        ThemeMode.Light.stored -> ThemeMode.Light
        ThemeMode.Dark.stored -> ThemeMode.Dark
        else -> ThemeMode.System
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.stored).commit()
    }

    fun languageCode(): String? = prefs.getString(KEY_LANGUAGE, null)?.ifBlank { null }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code()).commit()
    }

    /** null when this address has no baseline yet, so the first load does not look like a switch-on. */
    fun lastDisplayOn(placeKey: String): Boolean? {
        val map = lastMap()
        if (map != null) {
            if (!map.has(placeKey) || map.isNull(placeKey)) return null
            return map.optBoolean(placeKey)
        }
        if (!prefs.contains(KEY_LAST_ON)) return null
        val only = loadPlaces().singleOrNull() ?: loadSelection().takeIf { it.group.isNotBlank() }
        if (only?.placeKey != placeKey) return null
        return prefs.getBoolean(KEY_LAST_ON, false)
    }

    fun setLastDisplayOn(placeKey: String, on: Boolean?) {
        val map = lastMap() ?: JSONObject()
        if (on == null) map.remove(placeKey) else map.put(placeKey, on)
        prefs.edit().putString(KEY_LAST_MAP, map.toString()).remove(KEY_LAST_ON).commit()
    }

    fun markNotified(key: String): Boolean {
        val list = runCatching {
            val array = JSONArray(prefs.getString(KEY_NOTIFIED, "[]"))
            MutableList(array.length()) { array.getString(it) }
        }.getOrElse { mutableListOf() }
        if (key in list) return false
        list.add(key)
        while (list.size > 80) list.removeAt(0)
        prefs.edit().putString(KEY_NOTIFIED, JSONArray(list).toString()).commit()
        return true
    }

    companion object {
        private const val FILE = "svitlo"
        private const val KEY_GROUP = "group"
        private const val KEY_STREET = "street"
        private const val KEY_HOUSE = "house"
        private const val KEY_CITY = "city"
        private const val KEY_PLACES = "places"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_WARN = "warn"
        private const val KEY_ON = "notify_on"
        private const val KEY_QUIET = "quiet_night"
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LAST_ON = "last_on"
        private const val KEY_LAST_MAP = "last_ons"
        private const val KEY_NOTIFIED = "notified"
    }

    private fun lastMap(): JSONObject? {
        val raw = prefs.getString(KEY_LAST_MAP, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun selectionJson(selection: Selection): JSONObject {
        return JSONObject()
            .put("group", selection.group)
            .put("street", selection.street ?: "")
            .put("house", selection.house ?: "")
            .put("city", selection.city ?: "")
    }

    private fun selectionFrom(json: JSONObject): Selection {
        return Selection(
            group = json.optString("group"),
            street = json.optString("street").ifBlank { null },
            house = json.optString("house").ifBlank { null },
            city = json.optString("city").ifBlank { null },
        )
    }
}

enum class ThemeMode(val stored: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}
