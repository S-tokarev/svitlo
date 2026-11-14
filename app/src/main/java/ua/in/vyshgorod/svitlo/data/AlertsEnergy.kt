package ua.`in`.vyshgorod.svitlo.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reads a public alerts.energy region page.
 * Household operators are kept. Ukrzaliznytsia shares group numbers and is ignored.
 * An empty hour array is not a plan, and it does not mean the power is on.
 */
object AlertsEnergy {
    const val SOURCE = "alerts.energy"
    const val KYIV_OBLAST_DTEK = "kyivska_oblast_oblenergo"

    private val queueId = Regex("""^\d{1,3}(\.\d{1,2})?$""")

    fun page(region: String): String = "https://alerts.energy/${Regions.normalize(region)}"
    private val dayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val stampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun parse(html: String, operatorId: String = ""): ParsedPage {
        val json = stateJson(html) ?: return emptyPage()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyPage()
        return parseRoot(root, operatorId)
    }

    /** Shutdown and supplier arrays from the documented JSON API. No network. */
    fun parseApi(shutdownsJson: String, suppliersJson: String, operatorId: String = ""): ParsedPage {
        val shutdowns = runCatching { JSONArray(shutdownsJson) }.getOrNull() ?: return emptyPage()
        val suppliers = runCatching { JSONArray(suppliersJson) }.getOrNull() ?: JSONArray()
        val root = JSONObject()
            .put("shutdowns", JSONObject().put("data", shutdowns))
            .put("suppliers", JSONObject().put("data", suppliers))
        return parseRoot(root, operatorId)
    }

    private fun parseRoot(root: JSONObject, operatorId: String): ParsedPage {
        val rows = findRows(root) ?: return emptyPage()
        val suppliers = suppliers(root)
        val initiators = linkedSetOf<String>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val queue = text(row, "queue")
            if (queueId.matches(queue)) initiators += text(row, "initiator")
        }
        initiators.remove("")
        val operator = choose(suppliers, initiators, operatorId.trim())
        if (operator.isBlank()) return emptyPage()
        var todayDate: LocalDate? = null
        var tomorrowDate: LocalDate? = null
        val todayGroups = linkedMapOf<String, Piece>()
        val tomorrowGroups = linkedMapOf<String, Piece>()
        val notes = linkedSetOf<String>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (text(row, "initiator") != operator) continue
            val queue = text(row, "queue")
            if (!queueId.matches(queue)) continue
            if (todayDate == null) todayDate = civil(text(row, "todayDate", "today_date"))
            if (tomorrowDate == null) tomorrowDate = civil(text(row, "tomorrowDate", "tomorrow_date"))
            val stamp = stamp(text(row, "updated"))
            val messages = messages(row, "todayAnnouncements", "today_announcements") +
                messages(row, "tomorrowAnnouncements", "tomorrow_announcements")
            val todayPlan = plan(row, "today")
            val tomorrowPlan = plan(row, "tomorrow")
            val today = todayDate
            val tomorrow = tomorrowDate
            if (todayPlan != null && todayGroups.putIfAbsent(queue, Piece(todayPlan, stamp)) == null) {
                notes += messages
            }
            val tomorrowFollows = tomorrow != null && (today == null || tomorrow.isAfter(today))
            if (tomorrowFollows && tomorrowPlan != null && tomorrowPlan.isNotEmpty() &&
                tomorrowGroups.putIfAbsent(queue, Piece(tomorrowPlan, stamp)) == null
            ) {
                notes += messages
            }
        }
        val today = todayDate
        val tomorrow = tomorrowDate
        val days = listOfNotNull(
            today?.takeIf { todayGroups.isNotEmpty() }?.let { day("today", it, todayGroups) },
            tomorrow?.takeIf { tomorrowGroups.isNotEmpty() && (today == null || it.isAfter(today)) }
                ?.let { day("tomorrow", it, tomorrowGroups) },
        )
        if (days.isEmpty()) return emptyPage()
        return ParsedPage(
            days = days,
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = days.none { it.day == "tomorrow" },
            note = notes.joinToString("\n").take(800),
            source = SOURCE,
            operatorId = operator,
            operators = options(suppliers, initiators),
        )
    }

    private data class Supplier(val id: String, val name: String, val type: String, val priority: Int)

    private fun choose(suppliers: List<Supplier>, initiators: Set<String>, preferred: String): String {
        if (preferred.isNotBlank()) return if (preferred in initiators) preferred else ""
        suppliers.filter { it.type != "uz" }
            .sortedByDescending { it.priority }
            .firstOrNull { it.id in initiators }
            ?.let { return it.id }
        val household = initiators.filterNot { it.endsWith("_uz") }.sorted()
        return household.firstOrNull { "oblenergo" in it } ?: household.firstOrNull().orEmpty()
    }

    private fun options(suppliers: List<Supplier>, initiators: Set<String>): List<GridOperator> {
        val listed = suppliers.filter { it.type != "uz" }.sortedByDescending { it.priority }
        if (listed.isNotEmpty()) return listed.map { GridOperator(it.id, it.name) }
        return initiators.filterNot { it.endsWith("_uz") }.sorted().map { GridOperator(it, it) }
    }

    private fun suppliers(root: JSONObject): List<Supplier> {
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.contains("suppliers")) continue
            val data = root.optJSONObject(key)?.optJSONArray("data") ?: continue
            return buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val id = text(item, "supplierKey", "supplier_key", "slug")
                    if (id.isBlank()) continue
                    add(
                        Supplier(
                            id,
                            text(item, "supplierName", "supplier_name").ifBlank { id },
                            text(item, "supplierType", "supplier_type"),
                            item.optInt("priority"),
                        ),
                    )
                }
            }
        }
        return emptyList()
    }

    private data class Piece(
        val outages: List<Outage>,
        val stamp: String,
    )

    private fun civil(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

    /** Null means this side has no confirmed plan. An empty list means a published plan with no outage. */
    private fun plan(row: JSONObject, which: String): List<Outage>? {
        val status = text(row, "${which}Status", "${which}_status")
        val planned = ints(row.optJSONArray(which))
        val possible = possible(row, which)
        val hours = when (status) {
            "NoShutdowns" -> if (planned.size == 24) planned else List(24) { 0 }
            "ScheduleApplies", "EmergencyShutdowns" -> planned.takeIf { it.size == 24 }
            "" -> planned.takeIf { it.size == 24 }
            else -> null
        } ?: return null
        return outages(hours, possible)
    }

    private fun possible(row: JSONObject, which: String): List<Int> {
        val direct = row.optJSONArray("${which}Possible") ?: row.optJSONArray("${which}_possible")
        if (direct != null) return ints(direct)
        val published = row.optJSONObject("${which}PublishedSchedule")
            ?: row.optJSONObject("${which}_published_schedule")
        return ints(published?.optJSONArray("possible"))
    }

    /** 0 is clear, 1 is the whole hour, 2 is the first half, 3 is the second. Possible hours count too. */
    private fun outages(planned: List<Int>, possible: List<Int>): List<Outage> {
        val slots = BooleanArray(48)
        for (hour in 0 until 24) {
            mark(slots, hour, planned.getOrElse(hour) { 0 })
            mark(slots, hour, possible.getOrElse(hour) { 0 })
        }
        val result = mutableListOf<Outage>()
        var start: Int? = null
        for (index in 0 until 48) {
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

    private fun mark(slots: BooleanArray, hour: Int, code: Int) {
        when (code) {
            1 -> {
                slots[hour * 2] = true
                slots[hour * 2 + 1] = true
            }
            2 -> slots[hour * 2] = true
            3 -> slots[hour * 2 + 1] = true
        }
    }

    private fun day(name: String, date: LocalDate, groups: Map<String, Piece>): DaySchedule {
        val ordered = groups.entries.sortedWith(
            compareBy({ ScheduleMath.groupRank(it.key).first }, { ScheduleMath.groupRank(it.key).second }),
        )
        return DaySchedule(
            day = name,
            label = ScheduleMath.dayLabel(date, AppLanguage.Uk),
            updatedDate = date.format(dayDate),
            updatedTime = "",
            groups = ordered.associateTo(linkedMapOf()) { it.key to it.value.outages },
            groupUpdated = ordered.associate { it.key to it.value.stamp },
        )
    }

    private fun findRows(root: JSONObject): JSONArray? {
        val keys = root.keys()
        while (keys.hasNext()) {
            val data = root.optJSONObject(keys.next())?.optJSONArray("data") ?: continue
            for (index in 0 until data.length()) {
                val row = data.optJSONObject(index) ?: continue
                if (row.has("initiator") && row.has("queue") && row.has("today")) return data
            }
        }
        return null
    }

    private fun stateJson(html: String): String? {
        val marker = "<script id=\"ng-state\" type=\"application/json\">"
        val start = html.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = html.indexOf("</script>", from)
        if (end < 0) return null
        return html.substring(from, end)
    }

    private fun messages(row: JSONObject, vararg keys: String): List<String> {
        val array = keys.firstNotNullOfOrNull { row.optJSONArray(it) } ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val message = array.optJSONObject(index)?.let { text(it, "message") }.orEmpty()
                if (message.isNotEmpty()) add(message)
            }
        }
    }

    private fun ints(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return List(array.length()) { index -> array.optInt(index) }
    }

    private fun text(row: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!row.has(key) || row.isNull(key)) continue
            val value = row.optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun stamp(updated: String): String {
        val instant = runCatching { Instant.parse(updated) }.getOrNull() ?: return ""
        return instant.atZone(ScheduleMath.zone).format(stampFormat)
    }

    private fun emptyPage() = ParsedPage(
        days = emptyList(),
        live = emptyList(),
        houses = emptyList(),
        tomorrowMissing = true,
    )
}
