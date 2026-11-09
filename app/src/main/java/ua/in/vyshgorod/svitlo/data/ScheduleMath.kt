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

    fun picture(snapshot: Snapshot, selection: Selection, now: Long): PowerPicture {
        val todaySchedule = snapshot.days.firstOrNull { it.day == "today" }
        val tomorrowSchedule = snapshot.days.firstOrNull { it.day == "tomorrow" }
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
        ranges.sortBy { it.start }

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
                if (!live.powerOn && live.toward == "ON") nextOn = changeAt
            }
        }

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
            dueWarnOffAt = nextOff?.takeIf { it > now && it - now <= WARN_BEFORE_MS },
            warnAts = offMoments.map { it - WARN_BEFORE_MS }.filter { it > now + 5_000L }.sorted(),
            appearAts = onMoments.sorted(),
            outagesToday = shownToday,
            outagesTomorrow = shownTomorrow,
            scheduleDate = scheduleDate,
            scheduleLabel = todaySchedule?.label.orEmpty(),
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
            nowZ.toLocalDate().plusDays(1) -> "завтра $clockText"
            else -> "${whenZ.dayOfMonth}.${whenZ.monthValue.toString().padStart(2, '0')} $clockText"
        }
    }

    fun formatDuration(ms: Long): String {
        val totalMin = (ms / 60_000L).toInt().coerceAtLeast(0)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
            hours > 0 -> "$hours ч"
            else -> "$minutes мин"
        }
    }

    fun formatClock(minutes: Int): String {
        val capped = minutes.coerceIn(0, 1440)
        return "%02d:%02d".format(capped / 60, capped % 60)
    }

    fun knownGroups(snapshot: Snapshot?): List<String> {
        val keys = linkedSetOf(
            "1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2",
        )
        snapshot?.days?.forEach { keys += it.groups.keys }
        snapshot?.live?.forEach { keys += it.id }
        snapshot?.houses?.forEach { keys += it.group }
        return keys.sortedWith(compareBy({ it.substringBefore('.').toIntOrNull() ?: 9 }, { it }))
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
