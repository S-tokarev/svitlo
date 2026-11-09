package ua.`in`.vyshgorod.svitlo.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads the schedule embedded in the public alerts.energy page.
 * The page state contains the source-registry shutdowns payload for every queue.
 * Hour codes: 0 none, 1 the whole hour, 2 the first 30 minutes, 3 the second 30 minutes.
 * Code 9 and status EmergencyShutdowns are emergency data, not a stabilization plan.
 */
object SvitloParser {
    private const val STATE_KEY = "api:/source-registry/areas/kyivska-oblast/shutdowns"
    private const val INITIATOR = "kyivska_oblast_oblenergo"
    private val queueId = Regex("""^[1-6]\.[12]$""")
    private val zone = ScheduleMath.zone
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val dayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val labelFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

    fun parse(html: String): ParsedPage {
        return try {
            val rows = extractRows(html) ?: return emptyPage()
            parseRows(rows)
        } catch (_: JSONException) {
            emptyPage()
        }
    }

    internal fun parseRows(rows: JSONArray): ParsedPage {
        val todayGroups = linkedMapOf<String, List<Outage>>()
        val todayUpdated = linkedMapOf<String, String>()
        val tomorrowGroups = linkedMapOf<String, List<Outage>>()
        val tomorrowUpdated = linkedMapOf<String, String>()
        var todayDate = ""
        var tomorrowDate = ""
        var sawOperator = false
        val messages = mutableListOf<String>()

        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("initiator") != INITIATOR) continue
            val queue = row.optString("queue")
            if (!queueId.matches(queue)) continue
            sawOperator = true
            val stamp = formatStamp(row.optString("updated"))
            val rowToday = row.optString("todayDate")
            val rowTomorrow = row.optString("tomorrowDate")
            if (todayDate.isEmpty()) todayDate = rowToday
            if (tomorrowDate.isEmpty()) tomorrowDate = rowTomorrow
            if (publish(row, "todayStatus", "today", "todayPossible", todayDate, "todayDate", todayGroups, queue)) {
                if (stamp != null) todayUpdated[queue] = stamp
            }
            if (publish(row, "tomorrowStatus", "tomorrow", "tomorrowPossible", tomorrowDate, "tomorrowDate", tomorrowGroups, queue)) {
                if (stamp != null) tomorrowUpdated[queue] = stamp
            }
            collectMessages(row.optJSONArray("todayAnnouncements"), messages)
            collectMessages(row.optJSONArray("tomorrowAnnouncements"), messages)
        }

        if (!sawOperator) return emptyPage()
        val today = daySchedule("today", todayDate, todayGroups, todayUpdated)
        val tomorrow = if (tomorrowGroups.isEmpty()) null else daySchedule("tomorrow", tomorrowDate, tomorrowGroups, tomorrowUpdated)
        return ParsedPage(
            days = listOfNotNull(today, tomorrow),
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = tomorrow == null,
            note = dedupe(messages),
        )
    }

    private fun publish(
        row: JSONObject,
        statusKey: String,
        arrayKey: String,
        possibleKey: String,
        expectedDate: String,
        dateKey: String,
        groups: MutableMap<String, List<Outage>>,
        queue: String,
    ): Boolean {
        val date = row.optString(dateKey)
        if (expectedDate.isEmpty() || date != expectedDate) return false
        val slots = when (row.optString(statusKey)) {
            "ScheduleApplies" -> combine(readCodes(row, arrayKey, required = true), readCodes(row, possibleKey, required = false), zerosOnly = false)
            "NoShutdowns" -> combine(readCodes(row, arrayKey, required = true), readCodes(row, possibleKey, required = false), zerosOnly = true)
            else -> null
        } ?: return false
        groups[queue] = toOutages(slots)
        return true
    }

    private fun combine(planned: Hours, possible: Hours, zerosOnly: Boolean): BooleanArray? {
        val plan = planned as? Hours.Ok ?: return null
        if (zerosOnly && plan.slots.any { it }) return null
        return when (possible) {
            Hours.Absent -> plan.slots
            is Hours.Ok -> BooleanArray(plan.slots.size) { plan.slots[it] || possible.slots[it] }
            Hours.Emergency, Hours.Unusable -> null
        }
    }

    private fun readCodes(row: JSONObject, key: String, required: Boolean): Hours {
        if (!row.has(key)) return if (required) Hours.Unusable else Hours.Absent
        if (row.isNull(key)) return Hours.Unusable
        val value = row.get(key)
        if (value !is JSONArray) return Hours.Unusable
        if (value.length() == 0) return if (required) Hours.Unusable else Hours.Absent
        if (value.length() != 24) return Hours.Unusable
        val slots = BooleanArray(48)
        var emergency = false
        for (hour in 0 until 24) {
            val code = codeAt(value, hour) ?: return Hours.Unusable
            when (code) {
                0 -> Unit
                1 -> {
                    slots[hour * 2] = true
                    slots[hour * 2 + 1] = true
                }
                2 -> slots[hour * 2] = true
                3 -> slots[hour * 2 + 1] = true
                9 -> emergency = true
                else -> return Hours.Unusable
            }
        }
        if (emergency) return Hours.Emergency
        return Hours.Ok(slots)
    }

    private fun codeAt(array: JSONArray, index: Int): Int? {
        val raw = array.opt(index)
        val number = raw as? Number ?: return null
        val code = number.toInt()
        if (number.toDouble() != code.toDouble()) return null
        return code
    }

    internal fun toOutages(slots: BooleanArray): List<Outage> {
        val result = mutableListOf<Outage>()
        var start = -1
        for (index in 0..slots.size) {
            val on = index < slots.size && slots[index]
            if (on && start < 0) start = index
            if (!on && start >= 0) {
                result.add(Outage(start * 30, index * 30))
                start = -1
            }
        }
        return result
    }

    private fun collectMessages(array: JSONArray?, into: MutableList<String>) {
        if (array == null) return
        val now = Instant.now()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optString("state") != "active") continue
            val expires = item.optString("expiresAt")
            if (expires.isNotEmpty()) {
                val until = runCatching { Instant.parse(expires) }.getOrNull() ?: continue
                if (!until.isAfter(now)) continue
            }
            val message = item.optString("message").trim()
            if (message.isNotEmpty()) into.add(message)
        }
    }

    private fun dedupe(messages: List<String>): String {
        val unique = messages.distinct()
        return unique
            .filter { message -> unique.none { other -> other != message && other.contains(message) } }
            .joinToString("\n\n")
    }

    private fun daySchedule(
        day: String,
        iso: String,
        groups: Map<String, List<Outage>>,
        updated: Map<String, String>,
    ): DaySchedule? {
        val date = runCatching { LocalDate.parse(iso, isoDate) }.getOrNull() ?: return null
        return DaySchedule(
            day = day,
            label = date.format(labelFormat),
            updatedDate = date.format(dayDate),
            updatedTime = "",
            groups = groups,
            groupUpdated = updated,
        )
    }

    private fun formatStamp(iso: String): String? {
        val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
        val zoned = instant.atZone(zone)
        return zoned.format(dayDate) + " " + zoned.format(clock)
    }

    private fun extractRows(html: String): JSONArray? {
        val idAt = html.indexOf("id=\"ng-state\"")
        if (idAt < 0) return null
        val start = html.lastIndexOf("<script", idAt)
        if (start < 0) return null
        val jsonStart = html.indexOf('>', idAt)
        val jsonEnd = html.indexOf("</script>", jsonStart + 1)
        if (jsonStart < 0 || jsonEnd < 0) return null
        var body = html.substring(jsonStart + 1, jsonEnd).trim()
        if (!body.startsWith("{")) {
            body = body
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
        val state = JSONObject(body)
        return state.optJSONObject(STATE_KEY)?.optJSONArray("data")
    }

    private fun emptyPage(): ParsedPage {
        return ParsedPage(
            days = emptyList(),
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = true,
            note = "",
        )
    }

    private sealed interface Hours {
        data object Absent : Hours
        data object Unusable : Hours
        data object Emergency : Hours
        class Ok(val slots: BooleanArray) : Hours
    }
}
