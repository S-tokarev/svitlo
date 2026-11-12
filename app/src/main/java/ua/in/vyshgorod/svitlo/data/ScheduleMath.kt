package ua.`in`.vyshgorod.svitlo.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneRulesException
import kotlin.math.abs

data class PowerPicture(
    val displayOn: Boolean?,
    val plannedOn: Boolean?,
    val houseOn: Boolean?,
    val nextOffAt: Long?,
    val nextOnAt: Long?,
    /** Outage reaches midnight and tomorrow's chart is not published yet. Not a confirmed switch-on. */
    val offUntil: Long? = null,
    /** Off moment we are already inside the 10-minute warning window for. */
    val dueWarnOffAt: Long?,
    val warnAts: List<Long>,
    val appearAts: List<Long>,
    val outagesToday: List<Outage>,
    val outagesTomorrow: List<Outage>,
    val scheduleDate: LocalDate?,
    val scheduleLabel: String,
    val updatedLabel: String,
    val usingHouse: Boolean,
)

object ScheduleMath {
    private val oblastGroups = listOf(
        "1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2",
    )

    /**
     * Android 9 still has the pre-2022 id Europe/Kiev. Europe/Kyiv is the same zone, UTC+2.
     * A missing id used to kill the first frame, because this object loads with the screen.
     */
    val zone: ZoneId = kyivZone()
    const val WARN_BEFORE_MS = 10 * 60 * 1000L

    private fun kyivZone(): ZoneId {
        for (id in arrayOf("Europe/Kyiv", "Europe/Kiev")) {
            try {
                return ZoneId.of(id)
            } catch (_: ZoneRulesException) {
                // This image does not know that id.
            }
        }
        return ZoneOffset.ofHours(2)
    }

    private const val HORIZON_MS = 36 * 60 * 60 * 1000L
    private const val LIVE_FRESH_MS = 20 * 60 * 1000L
    private const val MERGE_MS = 20 * 60 * 1000L

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    /** Day title such as "10 листопада". Month names are fixed so a runtime without locale data cannot print "Nov". */
    fun dayLabel(date: LocalDate, language: AppLanguage = L10n.language): String {
        return "${date.dayOfMonth} ${L10n.month(date.monthValue, language)}"
    }

    fun dayLabel(updatedDate: String, language: AppLanguage = L10n.language): String {
        val date = parseDate(updatedDate) ?: return ""
        return dayLabel(date, language)
    }

    fun picture(snapshot: Snapshot, selection: Selection, now: Long): PowerPicture {
        val todaySchedule = snapshot.days.firstOrNull { it.day == "today" }
        val tomorrowSchedule = nextDay(snapshot)
        val scheduleDate = todaySchedule?.updatedDate?.let(::parseDate)
        val group = selection.group
        val todayOutages = todaySchedule?.groups?.get(group).orEmpty()
        val tomorrowOutages = tomorrowSchedule?.groups?.get(group).orEmpty()
        val zoneNow = Instant.ofEpochMilli(now).atZone(zone)
        val today = zoneNow.toLocalDate()

        val ranges = mutableListOf<Range>()
        val publishedToday = scheduleDate == today && todaySchedule?.groups?.containsKey(group) == true
        val publishedYesterday = scheduleDate == today.minusDays(1)
        if (publishedToday) ranges += absoluteRanges(scheduleDate!!, todayOutages)
        if (tomorrowSchedule?.groups?.containsKey(group) == true && (publishedToday || publishedYesterday)) {
            val date = if (publishedYesterday) today else scheduleDate!!.plusDays(1)
            ranges += absoluteRanges(date, tomorrowOutages)
        }
        val merged = mergeTouching(ranges.sortedBy { it.start })
        ranges.clear()
        ranges += merged

        val coversToday = publishedToday || (publishedYesterday && tomorrowSchedule?.groups?.containsKey(group) == true)
        val plannedOn = if (coversToday) ranges.none { now >= it.start && now < it.end } else null

        val live = snapshot.live.firstOrNull { it.id == group }
        val liveFresh = live != null && now - snapshot.fetchedAt in 0..LIVE_FRESH_MS
        var nextOff = ranges.filter { it.start > now }.minOfOrNull { it.start }
        var nextOn = ranges.firstOrNull { now >= it.start && now < it.end }?.end
            ?: ranges.filter { it.start > now }.minByOrNull { it.start }?.end

        if (liveFresh && live != null) {
            val changeAt = snapshot.fetchedAt + live.seconds * 1000L
            if (changeAt > now - 60_000L) {
                if (live.powerOn && live.toward == "OFF") nextOff = changeAt
                if (!live.powerOn && live.toward == "ON") {
                    val scheduled = nextOn
                    val liveStopsAtMidnight = isDayBoundary(changeAt) && scheduled != null && scheduled > changeAt + 60_000L
                    if (!liveStopsAtMidnight) nextOn = changeAt
                }
            }
        }
        val tomorrowKnown = tomorrowSchedule?.groups?.containsKey(group) == true && (publishedToday || publishedYesterday)
        val offUntil = if (nextOn != null && isDayBoundary(nextOn) && !tomorrowKnown) nextOn else null
        if (offUntil != null) nextOn = null

        val house = findHouse(snapshot.houses, selection)
        val displayOn = when {
            house != null -> house.powerOn
            liveFresh && live != null -> live.powerOn
            else -> plannedOn
        }

        val offMoments = linkedSetOf<Long>()
        ranges.map { it.start }.filter { it in (now + 1)..(now + HORIZON_MS) }.forEach(offMoments::add)
        if (nextOff != null && nextOff in (now + 1)..(now + HORIZON_MS)) {
            val keep = nextOff
            offMoments.removeAll { abs(it - keep) < MERGE_MS }
            offMoments.add(keep)
        }

        val onMoments = linkedSetOf<Long>()
        ranges.map { it.end }.filter { it in (now + 1)..(now + HORIZON_MS) }.forEach(onMoments::add)
        if (nextOn != null && nextOn in (now + 1)..(now + HORIZON_MS)) {
            val keep = nextOn
            onMoments.removeAll { abs(it - keep) < MERGE_MS }
            onMoments.add(keep)
        }
        if (!tomorrowKnown) onMoments.removeAll { isDayBoundary(it) }

        val shownToday = when {
            publishedToday -> todayOutages
            publishedYesterday && tomorrowSchedule != null -> tomorrowOutages
            else -> emptyList()
        }
        val shownTomorrow = if (publishedToday && tomorrowSchedule != null) tomorrowOutages else emptyList()

        return PowerPicture(
            displayOn = displayOn,
            plannedOn = if (liveFresh && live != null) live.powerOn else plannedOn,
            houseOn = house?.powerOn,
            nextOffAt = nextOff?.takeIf { it > now },
            nextOnAt = nextOn?.takeIf { it > now },
            offUntil = offUntil?.takeIf { it > now },
            dueWarnOffAt = nextOff?.takeIf { it > now && it - now <= WARN_BEFORE_MS },
            warnAts = offMoments.map { it - WARN_BEFORE_MS }.filter { it > now + 5_000L }.sorted(),
            appearAts = onMoments.sorted(),
            outagesToday = shownToday,
            outagesTomorrow = shownTomorrow,
            scheduleDate = scheduleDate,
            scheduleLabel = todaySchedule?.updatedDate?.let { dayLabel(it) }.orEmpty()
                .ifBlank { todaySchedule?.label.orEmpty() },
            updatedLabel = todaySchedule?.groupUpdated?.get(group)?.takeIf { it.isNotBlank() }
                ?: listOf(todaySchedule?.updatedDate, todaySchedule?.updatedTime)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" "),
            usingHouse = house != null,
        )
    }

    fun findHouse(houses: List<HouseStatus>, selection: Selection): HouseStatus? {
        val street = selection.street ?: return null
        val house = selection.house ?: return null
        val streetKey = normStreet(street)
        val houseKey = normHouse(house)
        if (streetKey.isEmpty() || houseKey.isEmpty()) return null
        return houses.firstOrNull { item ->
            if (item.group != selection.group) return@firstOrNull false
            val parts = item.address.split(",").map { it.trim() }
            if (parts.size < 2) return@firstOrNull false
            normStreet(parts[0]) == streetKey && normHouse(parts[1]) == houseKey
        }
    }

    fun formatTime(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFormat)
    }

    fun formatDayTime(epochMs: Long, now: Long): String {
        val whenZ = Instant.ofEpochMilli(epochMs).atZone(zone)
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val clockText = whenZ.format(timeFormat)
        return when (whenZ.toLocalDate()) {
            nowZ.toLocalDate() -> clockText
            nowZ.toLocalDate().plusDays(1) -> L10n.tomorrowAt(clockText)
            else -> "${whenZ.dayOfMonth}.${whenZ.monthValue.toString().padStart(2, '0')} $clockText"
        }
    }

    fun formatDuration(ms: Long): String {
        val totalMin = (ms / 60_000L).toInt().coerceAtLeast(0)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return L10n.duration(hours, minutes)
    }

    fun formatClock(minutes: Int): String {
        val capped = minutes.coerceIn(0, 1440)
        return "%02d:%02d".format(capped / 60, capped % 60)
    }

    /** A chart labeled tomorrow counts only when its date is the next calendar day. */
    fun groupHasTomorrow(snapshot: Snapshot, group: String): Boolean {
        return nextDay(snapshot)?.groups?.containsKey(group) == true
    }

    fun knownGroups(snapshot: Snapshot?, region: String = Regions.KYIV_OBLAST): List<String> {
        val selected = Regions.normalize(region)
        val stored = when {
            snapshot == null || snapshot.region.isBlank() -> Regions.KYIV_OBLAST
            else -> Regions.normalize(snapshot.region)
        }
        val found = if (stored == selected) {
            buildSet {
                snapshot?.days?.forEach { addAll(it.groups.keys) }
                snapshot?.live?.forEach { add(it.id) }
                snapshot?.houses?.forEach { add(it.group) }
            }
        } else {
            emptySet()
        }
        if (selected == Regions.KYIV_OBLAST && stored == selected) {
            return sortedGroups(oblastGroups + found)
        }
        return sortedGroups(found)
    }

    fun sortedGroups(ids: Collection<String>): List<String> {
        return ids.filter { it.isNotBlank() }.distinct().sortedWith(compareBy({ groupRank(it).first }, { groupRank(it).second }, { it }))
    }

    fun groupRank(id: String): Pair<Int, Int> {
        val major = id.substringBefore('.').toIntOrNull() ?: 999
        val minor = id.substringAfter('.', "").toIntOrNull() ?: 0
        return major to minor
    }

    /**
     * Puts every hour label inside the track without dropping one.
     * Labels stay near their minute, and a tight pair such as 23:30 and 24 sits side by side.
     */
    fun placeHourLabels(minutes: List<Int>, widths: List<Float>, track: Float, gap: Float): FloatArray {
        val count = minutes.size
        if (count == 0) return FloatArray(0)
        val safeTrack = track.coerceAtLeast(1f)
        val lefts = FloatArray(count)
        lefts[0] = (safeTrack * minutes[0] / 1440f - widths[0] / 2f)
            .coerceIn(0f, (safeTrack - widths[0]).coerceAtLeast(0f))
        for (index in 1 until count) {
            val ideal = safeTrack * minutes[index] / 1440f - widths[index] / 2f
            val minLeft = lefts[index - 1] + widths[index - 1] + gap
            lefts[index] = maxOf(ideal, minLeft)
        }
        val overflow = lefts[count - 1] + widths[count - 1] - safeTrack
        if (overflow > 0f) {
            lefts[count - 1] = (safeTrack - widths[count - 1]).coerceAtLeast(0f)
            for (index in count - 2 downTo 0) {
                val maxLeft = lefts[index + 1] - gap - widths[index]
                if (lefts[index] > maxLeft) lefts[index] = maxLeft
            }
        }
        if (lefts[0] < 0f) {
            val total = widths.sum()
            val free = (safeTrack - total).coerceAtLeast(0f)
            val step = if (count > 1) free / (count - 1) else 0f
            var cursor = 0f
            for (index in 0 until count) {
                lefts[index] = cursor
                cursor += widths[index] + step
            }
        }
        return lefts
    }

    /**
     * True when a future calendar day in [next] has different outages for [group]
     * than [previous]. A day that is already over does not count as a change.
     */
    fun scheduleChanged(previous: Snapshot, next: Snapshot, group: String, now: Long): Boolean {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val old = plans(previous, group)
        val fresh = plans(next, group)
        return (old.keys + fresh.keys).any { key ->
            val date = parseDate(key)
            if (date != null && date.isBefore(today)) return@any false
            normalize(old[key]) != normalize(fresh[key])
        }
    }

    fun planStamp(snapshot: Snapshot, group: String): String {
        return plans(snapshot, group).entries.joinToString("|") { (date, outages) ->
            date + ":" + normalize(outages).joinToString(",") { "${it.startMin}-${it.endMin}" }
        }
    }

    fun minuteOfDay(now: Long): Int {
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        return zoned.hour * 60 + zoned.minute
    }

    /** 23:00 inclusive until 07:00 exclusive. */
    fun isQuietHour(epochMs: Long): Boolean {
        val minute = minuteOfDay(epochMs)
        return minute >= 23 * 60 || minute < 7 * 60
    }

    private data class Range(val start: Long, val end: Long)

    private fun mergeTouching(ranges: List<Range>): List<Range> {
        val merged = mutableListOf<Range>()
        for (range in ranges) {
            val last = merged.lastOrNull()
            if (last != null && range.start <= last.end) {
                merged[merged.lastIndex] = Range(last.start, maxOf(last.end, range.end))
            } else {
                merged += range
            }
        }
        return merged
    }

    private fun isDayBoundary(epochMs: Long): Boolean {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
        return zoned.hour == 0 && zoned.minute == 0
    }

    private fun nextDay(snapshot: Snapshot): DaySchedule? {
        val anchor = snapshot.days.firstOrNull { it.day == "today" }?.updatedDate?.let(::parseDate) ?: return null
        val tomorrow = snapshot.days.firstOrNull { it.day == "tomorrow" } ?: return null
        val date = parseDate(tomorrow.updatedDate) ?: return null
        return tomorrow.takeIf { date == anchor.plusDays(1) }
    }

    private fun plans(snapshot: Snapshot, group: String): Map<String, List<Outage>> {
        val map = linkedMapOf<String, List<Outage>>()
        for (day in snapshot.days) {
            if (day.updatedDate.isBlank()) continue
            map[day.updatedDate] = day.groups[group] ?: continue
        }
        return map
    }

    private fun normalize(outages: List<Outage>?): List<Outage> {
        return outages.orEmpty().sortedWith(compareBy({ it.startMin }, { it.endMin }))
    }

    private fun absoluteRanges(date: LocalDate, outages: List<Outage>): List<Range> {
        val startOfDay = date.atStartOfDay(zone)
        return outages.map { outage ->
            Range(
                start = startOfDay.plusMinutes(outage.startMin.toLong()).toInstant().toEpochMilli(),
                end = startOfDay.plusMinutes(outage.endMin.toLong()).toInstant().toEpochMilli(),
            )
        }
    }

    private fun parseDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value, dateFormat) }.getOrNull()
    }

    private fun normStreet(value: String): String {
        return value.lowercase()
            .replace("вул.", "")
            .replace("вулиця", "")
            .replace("пров.", "")
            .replace("провулок", "")
            .replace("просп.", "")
            .replace(".", "")
            .replace(" ", "")
            .trim()
    }

    private fun normHouse(value: String): String {
        return value.lowercase().replace(" ", "").replace("-", "").replace(".", "").trim()
    }
}
