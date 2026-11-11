package ua.`in`.vyshgorod.svitlo.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads https://vyshgorod.in.ua/svitlo.
 * Each published day is drawn twice in the light theme: the "changes" chart
 * and a plain copy of the same date. A later calendar date is tomorrow.
 * Group chips carry the live countdown. House rows carry sensor status.
 */
object SvitloParser {
    private val queueId = Regex("""^[1-6]\.[12]$""")
    private val textTag = Regex("""<text\b([^>]*)>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
    private val rectTag = Regex("""<rect\b([^>]*)/?>""")
    private val attr = Regex("""([\w:-]+)="([^"]*)"""")
    private val liveChip = Regex(
        """<span class="group ([^"]*)" data-g="([^"]*)">\s*<b>[^<]*</b>\s*<small>([^<]*)</small>\s*<span class="mono[^"]*" data-sec="(\d+)"""",
    )
    private val houseBlock = Regex(
        """<div class="house([^"]*)"\s+data-g="([^"]*)"\s+data-st="([^"]*)"\s+data-q="[^"]*">(.*?)</div>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val dayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val months = mapOf(
        "січня" to 1, "лютого" to 2, "березня" to 3, "квітня" to 4, "травня" to 5, "червня" to 6,
        "липня" to 7, "серпня" to 8, "вересня" to 9, "жовтня" to 10, "листопада" to 11, "грудня" to 12,
    )

    fun parse(html: String): ParsedPage {
        val chosen = linkedMapOf<LocalDate, ChartHit>()
        for ((svg, mode) in lightCharts(html)) {
            val schedule = parseChart(svg, "today") ?: continue
            val date = runCatching { LocalDate.parse(schedule.updatedDate, dayDate) }.getOrNull() ?: continue
            val current = chosen[date]
            if (current == null || (current.mode != "changes" && mode == "changes")) {
                chosen[date] = ChartHit(mode, schedule)
            }
        }
        val ordered = chosen.entries.sortedBy { it.key }.map { it.value.schedule }
        val today = ordered.firstOrNull() ?: return emptyPage()
        val tomorrow = ordered.getOrNull(1)
        return ParsedPage(
            days = listOfNotNull(today.copy(day = "today"), tomorrow?.copy(day = "tomorrow")),
            live = parseLive(html),
            houses = parseHouses(html),
            tomorrowMissing = tomorrow == null,
        )
    }

    private data class ChartHit(val mode: String, val schedule: DaySchedule)

    /** Light charts only. The site draws up to two days, and each day twice. */
    private fun lightCharts(html: String): List<Pair<String, String>> {
        val marker = "<div class=\"sg-svg sg-svg-light\">"
        val result = mutableListOf<Pair<String, String>>()
        var from = 0
        while (result.size < 8) {
            val start = html.indexOf(marker, from)
            if (start < 0) break
            val svgStart = html.indexOf("<svg", start)
            val svgEnd = html.indexOf("</svg></div>", if (svgStart < 0) start else svgStart)
            if (svgStart < 0 || svgEnd < 0) break
            result += html.substring(svgStart, svgEnd + "</svg>".length) to modeBefore(html, start)
            from = svgEnd + "</svg></div>".length
        }
        return result
    }

    private fun modeBefore(html: String, start: Int): String {
        val window = html.substring((start - 500).coerceAtLeast(0), start)
        return Regex("""data-mode="([^"]+)"""").findAll(window).lastOrNull()?.groupValues?.get(1).orEmpty()
    }

    private fun parseChart(svg: String, day: String): DaySchedule? {
        val texts = texts(svg)
        val track = track(svg) ?: return null
        val stampDate = texts.firstNotNullOfOrNull { updatedDate(it.body) } ?: return null
        val stampClock = texts.filter { it.y < 90 && it.body.matches(Regex("""\d{2}:\d{2}""")) }
            .maxByOrNull { it.x }?.body
        val date = chartDate(texts, stampDate) ?: return null
        val stamp = listOf(stampDate, stampClock).filterNotNull().joinToString(" ")
        val groups = linkedMapOf<String, List<Outage>>()
        for (label in texts) {
            if (label.x >= 180 || !queueId.matches(label.body)) continue
            val bars = offRects(svg).filter { abs(it.centerY - label.y) <= 24 }
            val merged = merge(bars.map { it.x to it.x + it.width }.sortedBy { it.first })
            groups[label.body] = merged.mapNotNull { (start, end) ->
                val from = minuteAt(start, track)
                val to = minuteAt(end, track)
                if (to > from) Outage(from, to) else null
            }
        }
        if (groups.isEmpty()) return null
        return DaySchedule(
            day = day,
            label = ScheduleMath.dayLabel(date, AppLanguage.Uk),
            updatedDate = date.format(dayDate),
            updatedTime = "",
            groups = groups,
            groupUpdated = groups.keys.associateWith { stamp },
        )
    }

    private fun parseLive(html: String): List<LiveGroup> {
        return liveChip.findAll(html).mapNotNull { match ->
            val classes = match.groupValues[1].split(Regex("\\s+"))
            val group = match.groupValues[2]
            if (!queueId.matches(group)) return@mapNotNull null
            val toward = when {
                match.groupValues[3].contains("OFF") -> "OFF"
                match.groupValues[3].contains("ON") -> "ON"
                else -> return@mapNotNull null
            }
            val seconds = match.groupValues[4].toIntOrNull() ?: return@mapNotNull null
            LiveGroup(
                id = group,
                powerOn = "on" in classes && "off" !in classes,
                toward = toward,
                seconds = seconds,
            )
        }.distinctBy { it.id }.toList()
    }

    private fun parseHouses(html: String): List<HouseStatus> {
        return houseBlock.findAll(html).mapNotNull { match ->
            val group = match.groupValues[2]
            if (!queueId.matches(group)) return@mapNotNull null
            val body = match.groupValues[4]
            val address = unescape(Regex("""<span class="addr"><b>(.*?)</b>""").find(body)?.groupValues?.get(1).orEmpty())
            if (address.isBlank()) return@mapNotNull null
            val complex = Regex("""<small class="complex-tag">(.*?)</small>""")
                .find(body)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.isNotBlank() }
            val since = Regex("""title="([^"]*з [^"]+)"""").find(body)?.groupValues?.get(1)?.let(::unescape)
            HouseStatus(
                group = group,
                powerOn = match.groupValues[3] == "on",
                address = address,
                complex = complex,
                since = since,
            )
        }.toList()
    }

    private data class TextRun(val x: Double, val y: Double, val body: String)
    private data class Bar(val x: Double, val width: Double, val centerY: Double)
    private data class Track(val origin: Double, val width: Double)

    private fun texts(svg: String): List<TextRun> {
        return textTag.findAll(svg).mapNotNull { match ->
            val values = attributes(match.groupValues[1])
            val x = values["x"]?.toDoubleOrNull() ?: return@mapNotNull null
            val y = values["y"]?.toDoubleOrNull() ?: return@mapNotNull null
            val body = unescape(match.groupValues[2].replace(Regex("<[^>]+>"), "")).trim()
            if (body.isEmpty()) null else TextRun(x, y, body)
        }.toList()
    }

    private fun offRects(svg: String): List<Bar> {
        return rectTag.findAll(svg).mapNotNull { match ->
            val values = attributes(match.groupValues[1])
            val fill = values["fill"].orEmpty()
            if (!fill.contains("CBCAC5", ignoreCase = true)) return@mapNotNull null
            val x = values["x"]?.toDoubleOrNull() ?: return@mapNotNull null
            val y = values["y"]?.toDoubleOrNull() ?: return@mapNotNull null
            val width = values["width"]?.toDoubleOrNull() ?: return@mapNotNull null
            val height = values["height"]?.toDoubleOrNull() ?: return@mapNotNull null
            if (height !in 20.0..60.0) return@mapNotNull null
            Bar(x, width, y + height / 2)
        }.toList()
    }

    private fun track(svg: String): Track? {
        return rectTag.findAll(svg).firstNotNullOfOrNull { match ->
            val values = attributes(match.groupValues[1])
            val x = values["x"]?.toDoubleOrNull() ?: return@firstNotNullOfOrNull null
            val width = values["width"]?.toDoubleOrNull() ?: return@firstNotNullOfOrNull null
            if (x in 180.0..280.0 && width in 1000.0..1300.0) Track(x, width) else null
        }
    }

    private fun minuteAt(x: Double, track: Track): Int {
        val raw = (x - track.origin) / track.width * 1440.0
        return (raw / 30.0).roundToInt().times(30).coerceIn(0, 1440)
    }

    private fun merge(segments: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val merged = mutableListOf<Pair<Double, Double>>()
        for ((start, end) in segments) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.second + 3) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, end)
            } else {
                merged += start to end
            }
        }
        return merged
    }

    private fun updatedDate(body: String): String? {
        return Regex("""Оновлено\s+(\d{2}\.\d{2}\.\d{4})""").find(body)?.groupValues?.get(1)
    }

    private fun chartDate(texts: List<TextRun>, stamp: String): LocalDate? {
        val phrase = texts.firstOrNull { run -> months.keys.any { run.body.contains(it, ignoreCase = true) } }?.body
            ?: return null
        val match = Regex("""(\d{1,2})\s+([а-яіїєґА-ЯІЇЄҐ]+)""").find(phrase) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = months[match.groupValues[2].lowercase(Locale.forLanguageTag("uk"))] ?: return null
        val stampDate = runCatching { LocalDate.parse(stamp, dayDate) }.getOrNull() ?: return null
        val candidate = runCatching { LocalDate.of(stampDate.year, month, day) }.getOrNull() ?: return null
        return if (candidate.isBefore(stampDate.minusDays(1))) candidate.plusYears(1) else candidate
    }

    private fun attributes(source: String): Map<String, String> {
        return attr.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun unescape(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun abs(value: Double) = if (value < 0) -value else value

    private fun emptyPage() = ParsedPage(
        days = emptyList(),
        live = emptyList(),
        houses = emptyList(),
        tomorrowMissing = true,
    )
}
