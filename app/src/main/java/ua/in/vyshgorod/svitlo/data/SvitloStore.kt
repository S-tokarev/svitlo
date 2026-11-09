package ua.`in`.vyshgorod.svitlo.data

import android.content.Context
import org.json.JSONArray

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

    fun saveSelection(selection: Selection) {
        prefs.edit()
            .putString(KEY_GROUP, selection.group)
            .putString(KEY_STREET, selection.street)
            .putString(KEY_HOUSE, selection.house)
            .putString(KEY_CITY, selection.city)
            .remove(KEY_LAST_ON)
            .remove(KEY_NOTIFIED)
            .commit()
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

    /** null when this group has no baseline yet, so the first load does not look like a switch-on. */
    fun lastDisplayOn(): Boolean? {
        return if (prefs.contains(KEY_LAST_ON)) prefs.getBoolean(KEY_LAST_ON, false) else null
    }

    fun setLastDisplayOn(on: Boolean?) {
        val editor = prefs.edit()
        if (on == null) editor.remove(KEY_LAST_ON) else editor.putBoolean(KEY_LAST_ON, on)
        editor.commit()
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
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_WARN = "warn"
        private const val KEY_ON = "notify_on"
        private const val KEY_QUIET = "quiet_night"
        private const val KEY_THEME = "theme"
        private const val KEY_LAST_ON = "last_on"
        private const val KEY_NOTIFIED = "notified"
    }
}

enum class ThemeMode(val stored: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}
