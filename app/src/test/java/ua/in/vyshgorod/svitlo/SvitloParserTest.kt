package ua.`in`.vyshgorod.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.`in`.vyshgorod.svitlo.data.DaySchedule
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.SvitloParser
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import java.time.LocalDate
import java.time.LocalTime

class SvitloParserTest {
    @Test
    fun readsChartBarsLiveChipAndHouseSensor() {
        val page = SvitloParser.parse(page(today = true, tomorrow = true, live = true, house = true))
        val today = page.days.single { it.day == "today" }
        assertEquals("09.11.2026", today.updatedDate)
        assertEquals(
            listOf(Outage(0, 360), Outage(600, 990), Outage(1170, 1440)),
            today.groups["1.1"],
        )
        assertEquals(
            listOf(Outage(0, 210), Outage(570, 1050), Outage(1230, 1440)),
            today.groups["2.1"],
        )
        assertEquals("08.11.2026 23:45", today.groupUpdated["1.1"])
        val tomorrow = page.days.single { it.day == "tomorrow" }
        assertEquals(listOf(Outage(0, 60)), tomorrow.groups["1.1"])
        assertFalse(page.tomorrowMissing)
        val live = page.live.single()
        assertEquals("1.1", live.id)
        assertFalse(live.powerOn)
        assertEquals("ON", live.toward)
        assertEquals(100, live.seconds)
        val house = page.houses.single()
        assertEquals("1.1", house.group)
        assertEquals("Кургузова, 11В", house.address)
        assertFalse(house.powerOn)
        assertEquals("Зірковий", house.complex)
    }

    @Test
    fun emptyPageDoesNotInventASchedule() {
        val page = SvitloParser.parse("<html>challenge</html>")
        assertTrue(page.days.isEmpty())
        assertEquals("", page.note)
    }

    @Test
    fun tenMinuteWarningAndNextSwitchUseKyivSchedule() {
        val zone = ScheduleMath.zone
        val now = LocalDate.of(2026, 11, 9).atTime(16, 40).atZone(zone).toInstant().toEpochMilli()
        val snapshot = Snapshot(
            fetchedAt = now,
            days = SvitloParser.parse(page(today = true, tomorrow = false, live = false, house = false)).days,
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = true,
        )
        val picture = ScheduleMath.picture(snapshot, Selection(group = "1.1"), now)
        assertEquals(true, picture.displayOn)
        val off = picture.nextOffAt ?: error("missing off")
        val offTime = java.time.Instant.ofEpochMilli(off).atZone(zone)
        assertEquals(19, offTime.hour)
        assertEquals(30, offTime.minute)
        assertEquals(1, picture.warnAts.size)
        assertEquals(off - ScheduleMath.WARN_BEFORE_MS, picture.warnAts.single())
        assertNull(picture.dueWarnOffAt)
        assertEquals("08.11.2026 23:45", picture.updatedLabel)
    }

    @Test
    fun lightAfterMidnightUsesTheNextDayInsteadOfZero() {
        val zone = ScheduleMath.zone
        val now = LocalDate.of(2026, 11, 9).atTime(23, 10).atZone(zone).toInstant().toEpochMilli()
        val continued = snapshot(
            now,
            DaySchedule("today", "9 ноября", "09.11.2026", "", mapOf("2.1" to listOf(Outage(1230, 1440)))),
            DaySchedule("tomorrow", "10 ноября", "10.11.2026", "", mapOf("2.1" to listOf(Outage(0, 180)))),
        )
        val on = ScheduleMath.picture(continued, Selection(group = "2.1"), now).nextOnAt ?: error("missing on")
        val time = java.time.Instant.ofEpochMilli(on).atZone(zone)
        assertEquals(10, time.dayOfMonth)
        assertEquals(3, time.hour)
        assertEquals(0, time.minute)

        val cutoff = snapshot(
            now,
            DaySchedule("today", "9 ноября", "09.11.2026", "", mapOf("2.1" to listOf(Outage(1230, 1440)))),
        )
        assertNull(ScheduleMath.picture(cutoff, Selection(group = "2.1"), now).nextOnAt)
    }

    @Test
    fun scheduleChangeIgnoresAFinishedDay() {
        val now = LocalDate.of(2026, 11, 10).atTime(9, 0).atZone(ScheduleMath.zone).toInstant().toEpochMilli()
        val before = snapshot(
            now,
            DaySchedule("today", "9 ноября", "09.11.2026", "", mapOf("2.1" to listOf(Outage(0, 60)))),
            DaySchedule("tomorrow", "10 ноября", "10.11.2026", "", mapOf("2.1" to listOf(Outage(0, 180)))),
        )
        val rolled = snapshot(
            now,
            DaySchedule("today", "10 ноября", "10.11.2026", "", mapOf("2.1" to listOf(Outage(0, 180)))),
        )
        assertFalse(ScheduleMath.scheduleChanged(before, rolled, "2.1", now))
        val edited = snapshot(
            now,
            DaySchedule("today", "10 ноября", "10.11.2026", "", mapOf("2.1" to listOf(Outage(0, 240)))),
        )
        assertTrue(ScheduleMath.scheduleChanged(rolled, edited, "2.1", now))
    }

    @Test
    fun hourLabelsKeepEverySwitchIncludingTheTightEnd() {
        val minutes = listOf(0, 180, 360, 810, 990, 1410, 1440)
        val widths = listOf(18f, 18f, 18f, 42f, 42f, 48f, 18f)
        val lefts = ScheduleMath.placeHourLabels(minutes, widths, track = 340f, gap = 4f)
        assertEquals(minutes.size, lefts.size)
        lefts.forEachIndexed { index, left ->
            assertTrue(left >= -0.5f)
            assertTrue(left + widths[index] <= 340.5f)
            if (index > 0) assertTrue(left + 0.5f >= lefts[index - 1] + widths[index - 1])
        }
        assertTrue(lefts[4] > lefts[3] + widths[3])
        assertTrue(lefts[4] < lefts[5])
    }

    @Test
    fun quietHoursCoverNightButNotEvening() {
        val zone = ScheduleMath.zone
        fun at(hour: Int, minute: Int): Long {
            return LocalDate.of(2026, 11, 9).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        }
        assertTrue(ScheduleMath.isQuietHour(at(23, 0)))
        assertTrue(ScheduleMath.isQuietHour(at(2, 15)))
        assertTrue(ScheduleMath.isQuietHour(at(6, 59)))
        assertFalse(ScheduleMath.isQuietHour(at(7, 0)))
        assertFalse(ScheduleMath.isQuietHour(at(22, 59)))
        assertFalse(ScheduleMath.isQuietHour(at(18, 11)))
    }

    private fun snapshot(now: Long, vararg days: DaySchedule) = Snapshot(
        fetchedAt = now,
        days = days.toList(),
        live = emptyList(),
        houses = emptyList(),
        tomorrowMissing = days.none { it.day == "tomorrow" },
    )

    private fun page(today: Boolean, tomorrow: Boolean, live: Boolean, house: Boolean): String {
        val charts = buildString {
            if (today) {
                append(chart("понеділок, 9 листопада", "1.1" to TODAY_11, "2.1" to TODAY_21))
            }
            if (tomorrow) append(chart("вівторок, 10 листопада", "1.1" to listOf(Outage(0, 60))))
        }
        val chip = if (live) {
            """<span class="group off" data-g="1.1"><b>1.1</b><small>до ON</small><span class="mono" data-sec="100">--:--:--</span></span>"""
        } else {
            ""
        }
        val sensor = if (house) {
            """<div class="house off" data-g="1.1" data-st="off" data-q="кургузова"><span class="addr"><b>Кургузова, 11В</b><small class="complex-tag">Зірковий</small></span><span title="Світла немає з 09.11.2026 17:07"></span></div>"""
        } else {
            ""
        }
        return charts + chip + sensor
    }

    private fun chart(phrase: String, vararg rows: Pair<String, List<Outage>>): String {
        val texts = mutableListOf(
            """<text x="1200" y="63">Оновлено 08.11.2026</text>""",
            """<text x="1360" y="64">23:45</text>""",
            """<text x="277" y="73">$phrase</text>""",
        )
        val rects = mutableListOf("""<rect x="230" y="120" width="1118" height="40" fill="#FFFFFF"/>""")
        rows.forEachIndexed { index, (group, outages) ->
            val labelY = 170.0 + index * 48.0
            texts += """<text x="92" y="$labelY">$group</text>"""
            for (outage in outages) {
                val x = 230.0 + outage.startMin / 1440.0 * 1118.0
                val width = (outage.endMin - outage.startMin) / 1440.0 * 1118.0
                rects += """<rect x="$x" y="${labelY - 28}" width="$width" height="45" fill="#CBCAC5"/>"""
            }
        }
        return """<div class="sg-svg sg-svg-light"><svg>${rects.joinToString("")}${texts.joinToString("")}</svg></div>"""
    }

    private companion object {
        val TODAY_11 = listOf(Outage(0, 360), Outage(600, 990), Outage(1170, 1440))
        val TODAY_21 = listOf(Outage(0, 210), Outage(570, 1050), Outage(1230, 1440))
    }
}
