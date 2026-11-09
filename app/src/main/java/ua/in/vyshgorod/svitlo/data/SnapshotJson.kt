package ua.`in`.vyshgorod.svitlo.data

import org.json.JSONArray
import org.json.JSONObject

object SnapshotJson {
    fun toJson(snapshot: Snapshot): String {
        val root = JSONObject()
        root.put("fetchedAt", snapshot.fetchedAt)
        root.put("tomorrowMissing", snapshot.tomorrowMissing)
        root.put("note", snapshot.note)
        root.put("days", JSONArray().apply {
            snapshot.days.forEach { day ->
                put(JSONObject().apply {
                    put("day", day.day)
                    put("label", day.label)
                    put("updatedDate", day.updatedDate)
                    put("updatedTime", day.updatedTime)
                    put("groups", JSONObject().apply {
                        day.groups.forEach { (id, outages) ->
                            put(id, JSONArray().apply {
                                outages.forEach { put(JSONArray().put(it.startMin).put(it.endMin)) }
                            })
                        }
                    })
                    put("groupUpdated", JSONObject().apply {
                        day.groupUpdated.forEach { (id, stamp) -> put(id, stamp) }
                    })
                })
            }
        })
        root.put("live", JSONArray().apply {
            snapshot.live.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("powerOn", it.powerOn)
                    put("toward", it.toward)
                    put("seconds", it.seconds)
                })
            }
        })
        root.put("houses", JSONArray().apply {
            snapshot.houses.forEach {
                put(JSONObject().apply {
                    put("group", it.group)
                    put("powerOn", it.powerOn)
                    put("address", it.address)
                    put("complex", it.complex ?: JSONObject.NULL)
                    put("since", it.since ?: JSONObject.NULL)
                })
            }
        })
        return root.toString()
    }

    fun fromJson(raw: String?): Snapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val days = root.optJSONArray("days").toList { item ->
                val groupsJson = item.optJSONObject("groups") ?: JSONObject()
                val groups = linkedMapOf<String, List<Outage>>()
                groupsJson.keys().forEach { key ->
                    groups[key] = outagesOf(groupsJson.optJSONArray(key))
                }
                val updatedJson = item.optJSONObject("groupUpdated") ?: JSONObject()
                val groupUpdated = linkedMapOf<String, String>()
                updatedJson.keys().forEach { key ->
                    groupUpdated[key] = updatedJson.optString(key)
                }
                DaySchedule(
                    day = item.optString("day"),
                    label = item.optString("label"),
                    updatedDate = item.optString("updatedDate"),
                    updatedTime = item.optString("updatedTime"),
                    groups = groups,
                    groupUpdated = groupUpdated,
                )
            }
            val live = root.optJSONArray("live").toList {
                LiveGroup(
                    id = it.optString("id"),
                    powerOn = it.optBoolean("powerOn"),
                    toward = it.optString("toward"),
                    seconds = it.optInt("seconds"),
                )
            }
            val houses = root.optJSONArray("houses").toList {
                HouseStatus(
                    group = it.optString("group"),
                    powerOn = it.optBoolean("powerOn"),
                    address = it.optString("address"),
                    complex = it.optString("complex").ifEmpty { null },
                    since = it.optString("since").ifEmpty { null },
                )
            }
            Snapshot(
                fetchedAt = root.optLong("fetchedAt"),
                days = days,
                live = live,
                houses = houses,
                tomorrowMissing = root.optBoolean("tomorrowMissing"),
                note = root.optString("note"),
            )
        }.getOrNull()
    }

    private fun outagesOf(array: JSONArray?): List<Outage> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val pair = array.optJSONArray(index) ?: continue
                add(Outage(pair.optInt(0), pair.optInt(1)))
            }
        }
    }

    private fun <T> JSONArray?.toList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(map(item))
            }
        }
    }
}
