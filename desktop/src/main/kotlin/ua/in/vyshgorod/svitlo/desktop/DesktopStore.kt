package ua.`in`.vyshgorod.svitlo.desktop

import org.json.JSONArray
import org.json.JSONObject
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.SnapshotJson
import java.io.File

enum class ThemeMode(val stored: String, val title: String) {
    System("system", "Система"),
    Light("light", "Светлая"),
    Dark("dark", "Тёмная");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.stored == value } ?: System
    }
}

object DesktopStore {
    private val root: File = File(System.getenv("APPDATA") ?: userData(), "Svitlo").apply { mkdirs() }
    private val file = File(root, "state.json")
    val cacheFile = File(root, "dtek-streets.json")

    private fun userData(): String {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return if (os.contains("mac")) "$home/Library/Application Support" else "$home/.local/share"
    }

    private fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    }

    private fun write(json: JSONObject) {
        file.writeText(json.toString())
    }

    fun loadSelection(): Selection {
        val json = read()
        return Selection(
            group = json.optString("group"),
            street = json.optString("street").ifBlank { null },
            house = json.optString("house").ifBlank { null },
            city = json.optString("city").ifBlank { null },
        )
    }

    fun saveSelection(selection: Selection) {
        val json = read()
        json.put("group", selection.group)
        json.put("street", selection.street ?: "")
        json.put("house", selection.house ?: "")
        json.put("city", selection.city ?: "")
        json.remove("lastOn")
        json.put("notified", JSONArray())
        write(json)
    }

    fun loadSnapshot(): Snapshot? = SnapshotJson.fromJson(read().optString("snapshot").ifBlank { null })

    fun saveSnapshot(snapshot: Snapshot) {
        val json = read()
        json.put("snapshot", SnapshotJson.toJson(snapshot))
        write(json)
    }

    fun warnEnabled() = read().optBoolean("warn", true)
    fun notifyWhenOn() = read().optBoolean("notifyOn", true)
    fun quietNight() = read().optBoolean("quiet", false)
    fun theme() = ThemeMode.from(read().optString("theme", "system"))

    fun setWarn(value: Boolean) = put("warn", value)
    fun setNotify(value: Boolean) = put("notifyOn", value)
    fun setQuiet(value: Boolean) = put("quiet", value)
    fun setTheme(mode: ThemeMode) = put("theme", mode.stored)

    fun lastDisplayOn(): Boolean? {
        val json = read()
        if (!json.has("lastOn") || json.isNull("lastOn")) return null
        return json.optBoolean("lastOn")
    }

    fun setLastDisplayOn(on: Boolean?) {
        val json = read()
        if (on == null) json.remove("lastOn") else json.put("lastOn", on)
        write(json)
    }

    fun markNotified(key: String): Boolean {
        val json = read()
        val list = json.optJSONArray("notified") ?: JSONArray()
        val keys = (0 until list.length()).map { list.getString(it) }.toMutableList()
        if (key in keys) return false
        keys.add(key)
        while (keys.size > 80) keys.removeAt(0)
        json.put("notified", JSONArray(keys))
        write(json)
        return true
    }

    private fun put(name: String, value: Any) {
        val json = read()
        json.put(name, value)
        write(json)
    }
}
