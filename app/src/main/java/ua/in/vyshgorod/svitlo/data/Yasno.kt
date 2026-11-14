package ua.`in`.vyshgorod.svitlo.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Public YASNO schedule for Kyiv city and Dnipro.
 * An empty slot list is not a plan. NotPlanned means the power stays on.
 */
object Yasno {
    const val SOURCE = "yasno"

    private val queueId = Regex("""^\d{1,3}(\.\d{1,2})?$""")
    private val dayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val stampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    private data class Feed(val regionId: Int, val dsoId: String, val name: String)

    private data class Side(val queue: String, val plan: List<Outage>, val date: LocalDate?)

    fun covers(region: String): Boolean = feeds(region).isNotEmpty()

    fun page(region: String): String = when (Regions.normalize(region)) {
        Regions.KYIV -> "https://static.yasno.ua/kyiv/outages"
        else -> "https://static.yasno.ua/dnipro/outages"
    }

    /** Null when this region is not published by YASNO. */
    fun load(region: String, operatorId: String = ""): ParsedPage? {
        val available = feeds(region)
        if (available.isEmpty()) return null
        val chosen = if (operatorId.isBlank()) {
            available.first()
        } else {
            available.firstOrNull { it.dsoId == operatorId } ?: return emptyPage()
        }
        val body = SvitloClient.fetchPublic(url(chosen))
        return parse(body, chosen.dsoId, available.map { GridOperator(it.dsoId, it.name) })
    }

    fun parse(json: String, operatorId: String, operators: List<GridOperator>): ParsedPage {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyPage()
        val todays = mutableListOf<Side>()
        val tomorrows = mutableListOf<Side>()
        val stamps = linkedMapOf<String, String>()
        val names = root.keys()
        while (names.hasNext()) {
            val queue = names.next()
            if (!queueId.matches(queue)) continue
            val group = root.optJSONObject(queue) ?: continue
            val stamp = stamp(group.optString("updatedOn"))
            if (stamp.isNotEmpty()) stamps.putIfAbsent(queue, stamp)
            val today = group.optJSONObject("today")
            val tomorrow = group.optJSONObject("tomorrow")
            today?.let { plan(it) }?.let { todays += Side(queue, it, civil(today.optString("date"))) }
            tomorrow?.let { plan(it) }?.let { tomorrows += Side(queue, it, civil(tomorrow.optString("date"))) }
        }
        val todayDate = todays.mapNotNull { it.date }.minOrNull()
        val todayGroups = sides(todays, todayDate, allowUndated = todayDate == null)
        val tomorrowDate = tomorrows.mapNotNull { it.date }
            .filter { todayDate == null || it.isAfter(todayDate) }
            .minOrNull()
        val tomorrowGroups = sides(tomorrows, tomorrowDate, allowUndated = false).filterValues { it.isNotEmpty() }
        val days = listOfNotNull(
            todayDate?.takeIf { todayGroups.isNotEmpty() }?.let { day("today", it, todayGroups, stamps) },
            tomorrowDate?.takeIf { tomorrowGroups.isNotEmpty() }?.let { day("tomorrow", it, tomorrowGroups, stamps) },
        )
        if (days.isEmpty()) return emptyPage()
        return ParsedPage(
            days = days,
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = days.none { it.day == "tomorrow" },
            source = SOURCE,
            operatorId = operatorId,
            operators = operators,
        )
    }

    private fun feeds(region: String): List<Feed> = when (Regions.normalize(region)) {
        Regions.KYIV -> listOf(Feed(25, "902", "ТОВ «ДТЕК КИЇВСЬКІ ЕЛЕКТРОМЕРЕЖІ»"))
        "dnipropetrovska-oblast" -> listOf(Feed(3, "301", "ДнЕМ"), Feed(3, "303", "ЦЕК"))
        else -> emptyList()
    }

    /** Keeps groups of one civil date. A same-date tomorrow is not a second day. */
    private fun sides(items: List<Side>, date: LocalDate?, allowUndated: Boolean): Map<String, List<Outage>> {
        val groups = linkedMapOf<String, List<Outage>>()
        for (item in items) {
            val matches = when {
                date == null -> allowUndated && item.date == null
                item.date == null -> false
                else -> item.date == date
            }
            if (matches) groups.putIfAbsent(item.queue, item.plan)
        }
        return groups
    }

    private fun url(feed: Feed): String {
        return "https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/${feed.regionId}/dsos/${feed.dsoId}/planned-outages"
    }

    /** Null means this side has no confirmed plan. An empty list means the power stays on. */
    private fun plan(day: JSONObject): List<Outage>? {
        val status = day.optString("status")
        if (status.equals("WaitingForSchedule", ignoreCase = true)) return null
        val slots = slots(day)
        if (status.equals("EmergencyShutdowns", ignoreCase = true) && slots.isEmpty()) return null
        if (!day.has("slots") && slots.isEmpty()) return null
        return slots
    }

    private fun slots(day: JSONObject): List<Outage> {
        val array = day.optJSONArray("slots") ?: return emptyList()
        val pieces = buildList {
            for (index in 0 until array.length()) {
                val slot = array.optJSONObject(index) ?: continue
                val type = slot.optString("type")
                if (!type.equals("Definite", true) && !type.equals("Possible", true)) continue
                val start = slot.optInt("start", -1)
                val end = slot.optInt("end", -1)
                if (start < 0 || end <= start) continue
                add(start.coerceAtMost(1440) to end.coerceAtMost(1440))
            }
        }
        return merge(pieces)
    }

    private fun merge(pieces: List<Pair<Int, Int>>): List<Outage> {
        val merged = mutableListOf<Outage>()
        for ((start, end) in pieces.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && last.endMin >= start) {
                merged[merged.lastIndex] = Outage(last.startMin, maxOf(last.endMin, end))
            } else {
                merged += Outage(start, end)
            }
        }
        return merged
    }

    private fun day(
        name: String,
        date: LocalDate,
        groups: Map<String, List<Outage>>,
        stamps: Map<String, String>,
    ): DaySchedule {
        val ordered = groups.keys.sortedWith(compareBy({ ScheduleMath.groupRank(it).first }, { ScheduleMath.groupRank(it).second }))
        return DaySchedule(
            day = name,
            label = ScheduleMath.dayLabel(date, AppLanguage.Uk),
            updatedDate = date.format(dayDate),
            updatedTime = "",
            groups = ordered.associateWith { groups.getValue(it) },
            groupUpdated = ordered.associateWith { stamps[it].orEmpty() },
        )
    }

    private fun civil(raw: String): LocalDate? {
        if (raw.length >= 10) {
            val plain = runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
            if (plain != null) return plain
        }
        return runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
    }

    private fun stamp(raw: String): String {
        val instant = runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: return ""
        return instant.atZone(ScheduleMath.zone).format(stampFormat)
    }

    private fun emptyPage() = ParsedPage(
        days = emptyList(),
        live = emptyList(),
        houses = emptyList(),
        tomorrowMissing = true,
    )
}
