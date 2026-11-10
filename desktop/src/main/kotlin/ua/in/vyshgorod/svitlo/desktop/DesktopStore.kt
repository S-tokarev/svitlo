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
    val directory: File = File(System.getenv("APPDATA") ?: userData(), "Svitlo").apply { mkdirs() }
    private val file = File(directory, "state.json")
    val cacheFile = File(directory, "dtek-streets.json")

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

    fun loadPlaces(): List<Selection> {
        val json = read()
        val array = json.optJSONArray("places")
        if (array != null) {
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Selection(
                    group = item.optString("group"),
                    street = item.optString("street").ifBlank { null },
                    house = item.optString("house").ifBlank { null },
                    city = item.optString("city").ifBlank { null },
                ).takeIf { it.group.isNotBlank() }
            }
        }
        val one = loadSelection()
        return if (one.group.isBlank()) emptyList() else listOf(one)
    }

    fun savePlaces(places: List<Selection>, focus: Selection) {
        val json = read()
        val array = JSONArray()
        places.filter { it.group.isNotBlank() }.forEach { place ->
            array.put(
                JSONObject()
                    .put("group", place.group)
                    .put("street", place.street ?: "")
                    .put("house", place.house ?: "")
                    .put("city", place.city ?: ""),
            )
        }
        json.put("places", array)
        json.put("group", focus.group)
        json.put("street", focus.street ?: "")
        json.put("house", focus.house ?: "")
        json.put("city", focus.city ?: "")
        write(json)
    }

    fun saveSelection(selection: Selection) {
        val places = loadPlaces().filterNot { it.placeKey == selection.placeKey }.toMutableList()
        if (selection.group.isNotBlank()) places += selection
        savePlaces(places.takeLast(8), selection)
    }

    fun widgetOpen() = read().optBoolean("widget", false)

    fun setWidgetOpen(open: Boolean) {
        val json = read()
        json.put("widget", open)
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

    fun lastDisplayOn(placeKey: String): Boolean? {
        val json = read()
        val map = json.optJSONObject("lastOns")
        if (map != null) {
            if (!map.has(placeKey) || map.isNull(placeKey)) return null
            return map.optBoolean(placeKey)
        }
        if (!json.has("lastOn") || json.isNull("lastOn")) return null
        val only = loadPlaces().singleOrNull()
        if (only?.placeKey != placeKey) return null
        return json.optBoolean("lastOn")
    }

    fun setLastDisplayOn(placeKey: String, on: Boolean?) {
        val json = read()
        val map = json.optJSONObject("lastOns") ?: JSONObject()
        if (on == null) map.remove(placeKey) else map.put(placeKey, on)
        json.put("lastOns", map)
        json.remove("lastOn")
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
