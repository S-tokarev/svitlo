package ua.`in`.vyshgorod.svitlo.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Group schedule from the DTEK Kyiv regional networks ajax answer.
 * Hour 1 is 00:00–01:00. "yes" is power on, "no" is off, "first" and "second" are halves.
 */
object DtekSchedule {
    const val SOURCE = "dtek"
    const val PAGE = "https://www.dtek-krem.com.ua/ua/shutdowns"

    private val queueName = Regex("""^(?:GPV)?(\d{1,3}(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)
    private val dayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun load(cacheFile: java.io.File): ParsedPage = parse(AddressSearch.oblastSchedule(cacheFile))

    fun parse(json: String): ParsedPage {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyPage()
        if (!root.optBoolean("result")) return emptyPage()
        val fact = root.optJSONObject("fact") ?: return emptyPage()
        val data = fact.optJSONObject("data") ?: return emptyPage()
        val todayMarker = fact.optLong("today", 0L).takeIf { it > 0L }?.let { localDate(it) }
        val stamp = fact.optString("update").trim()
        val byDate = linkedMapOf<LocalDate, MutableMap<String, List<Outage>>>()
        val dayKeys = data.keys()
        while (dayKeys.hasNext()) {
            val key = dayKeys.next()
            val epoch = key.toLongOrNull() ?: continue
            val groups = data.optJSONObject(key) ?: continue
            val date = localDate(epoch)
            val parsed = byDate.getOrPut(date) { linkedMapOf() }
            val names = groups.keys()
            while (names.hasNext()) {
                val raw = names.next()
                val queue = queue(raw) ?: continue
                val hours = groups.optJSONObject(raw) ?: continue
                parsed.putIfAbsent(queue, outages(hours))
            }
        }
        val today = byDate.keys.firstOrNull { it == todayMarker } ?: byDate.keys.minOrNull()
        val tomorrow = byDate.keys.filter { today == null || it.isAfter(today) }.minOrNull()
        val tomorrowGroups = tomorrow?.let { byDate.getValue(it).filterValues { outages -> outages.isNotEmpty() } }.orEmpty()
        val days = listOfNotNull(
            today?.takeIf { byDate[it].orEmpty().isNotEmpty() }?.let { day("today", it, byDate.getValue(it), stamp) },
            tomorrow?.takeIf { tomorrowGroups.isNotEmpty() }?.let { day("tomorrow", it, tomorrowGroups, stamp) },
        )
        if (days.isEmpty()) return emptyPage()
        return ParsedPage(
            days = days,
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = days.none { it.day == "tomorrow" },
            source = SOURCE,
            region = Regions.KYIV_OBLAST,
            operatorId = AlertsEnergy.KYIV_OBLAST_DTEK,
            operators = listOf(GridOperator(AlertsEnergy.KYIV_OBLAST_DTEK, "ДТЕК Київські регіональні електромережі")),
        )
    }

    private fun queue(raw: String): String? = queueName.matchEntire(raw.trim())?.groupValues?.get(1)

    /** Keys are hours 1..24. A missing hour is not an outage. */
    private fun outages(hours: JSONObject): List<Outage> {
        val slots = BooleanArray(48)
        val names = hours.keys()
        while (names.hasNext()) {
            val key = names.next()
            val hour = key.toIntOrNull() ?: continue
            if (hour !in 1..24) continue
            mark(slots, hour - 1, hours.optString(key))
        }
        val result = mutableListOf<Outage>()
        var start: Int? = null
        for (index in slots.indices) {
            if (slots[index]) {
                if (start == null) start = index * 30
            } else if (start != null) {
                result += Outage(start, index * 30)
                start = null
            }
        }
        if (start != null) result += Outage(start, 1440)
        return result
    }

    private fun mark(slots: BooleanArray, hour: Int, value: String) {
        when (value.lowercase()) {
            "no", "maybe" -> {
                slots[hour * 2] = true
                slots[hour * 2 + 1] = true
            }
            "first", "mfirst" -> slots[hour * 2] = true
            "second", "msecond" -> slots[hour * 2 + 1] = true
        }
    }

    private fun day(name: String, date: LocalDate, groups: Map<String, List<Outage>>, stamp: String): DaySchedule {
        val ordered = groups.keys.sortedWith(compareBy({ ScheduleMath.groupRank(it).first }, { ScheduleMath.groupRank(it).second }))
        val time = stamp.substringAfter(" ", "").takeIf { stamp.contains(" ") }.orEmpty()
        return DaySchedule(
            day = name,
            label = ScheduleMath.dayLabel(date, AppLanguage.Uk),
            updatedDate = date.format(dayDate),
            updatedTime = time,
            groups = ordered.associateWith { groups.getValue(it) },
            groupUpdated = ordered.associateWith { stamp },
        )
    }

    private fun localDate(epochSeconds: Long): LocalDate {
        return Instant.ofEpochSecond(epochSeconds).atZone(ScheduleMath.zone).toLocalDate()
    }

    private fun emptyPage() = ParsedPage(
        days = emptyList(),
        live = emptyList(),
        houses = emptyList(),
        tomorrowMissing = true,
    )
}
