package ua.`in`.vyshgorod.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.SvitloParser
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SvitloParserTest {
    @Test
    fun mapsOblenergoHourCodesAndIgnoresTheOtherOperator() {
        val page = SvitloParser.parse(page(ROWS))
        val today = page.days.single { it.day == "today" }
        assertEquals("09.11.2026", today.updatedDate)
        assertEquals(
            listOf(Outage(0, 210), Outage(570, 1050), Outage(1230, 1440)),
            today.groups["2.1"],
        )
        assertEquals(
            listOf(Outage(0, 360), Outage(600, 990), Outage(1170, 1440)),
            today.groups["1.1"],
        )
        assertEquals("09.11.2026 08:05", today.groupUpdated["2.1"])
        assertFalse(today.groups.containsKey("9.9"))
        assertTrue(page.live.isEmpty())
        assertTrue(page.houses.isEmpty())
        assertTrue(page.tomorrowMissing)
        assertEquals("В інших районах діють стабілізаційні відключення.", page.note)
    }

    @Test
    fun unionsPossibleSlotsAndDropsUnusableDays() {
        val page = SvitloParser.parse(page(EDGE_ROWS))
        val today = page.days.single { it.day == "today" }
        assertEquals(listOf(Outage(600, 660)), today.groups["1.1"])
        assertEquals(emptyList<Outage>(), today.groups["1.2"])
        assertFalse(today.groups.containsKey("2.1"))
        assertFalse(today.groups.containsKey("2.2"))
        assertFalse(today.groups.containsKey("3.1"))
        assertFalse(today.groups.containsKey("3.2"))
        val tomorrow = page.days.single { it.day == "tomorrow" }
        assertEquals(listOf(Outage(0, 60)), tomorrow.groups["1.1"])
        assertFalse(page.tomorrowMissing)
    }

    @Test
    fun emptyPageDoesNotInventASchedule() {
        val page = SvitloParser.parse("<html>challenge</html>")
        assertTrue(page.days.isEmpty())
        assertEquals("", page.note)
    }

    @Test
    fun tenMinuteWarningAndNextSwitchUseKyivSchedule() {
        val zone = ZoneId.of("Europe/Kyiv")
        val now = LocalDate.of(2026, 11, 9).atTime(16, 40).atZone(zone).toInstant().toEpochMilli()
        val snapshot = Snapshot(
            fetchedAt = now,
            days = SvitloParser.parse(page(ROWS)).days,
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

    private fun page(rows: String): String {
        return """<script id="ng-state" type="application/json">{"api:/source-registry/areas/kyivska-oblast/shutdowns":{"data":$rows,"ts":1}}</script>"""
    }

    private companion object {
        private const val ZEROS = "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0"
        private val NOTE = """
            {"state":"active","kind":"Emergency","expiresAt":"2099-01-01T00:00:00Z","message":"В інших районах діють стабілізаційні відключення."}
        """.trimIndent()
        private val SHORT_NOTE = """
            {"state":"active","kind":"GPV","expiresAt":"2099-01-01T00:00:00Z","message":"стабілізаційні"}
        """.trimIndent()
        private val EXPIRED = """
            {"state":"active","kind":"Emergency","expiresAt":"2000-01-01T00:00:00Z","message":"прострочене"}
        """.trimIndent()

        val ROWS = """
            [
              {"initiator":"kyivska_oblast_uz","queue":"2.1","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[$ZEROS],"todayAnnouncements":[]},
              {"initiator":"kyivska_oblast_oblenergo","queue":"2.1","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1,2,0,0,0,0,0,3,1,1,1,1,1,1,1,2,0,0,3,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[$ZEROS],"todayAnnouncements":[$NOTE,$SHORT_NOTE,$EXPIRED]},
              {"initiator":"kyivska_oblast_oblenergo","queue":"1.1","updated":"2026-11-08T21:45:10.007Z","today":[1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,2,0,0,3,1,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[$ZEROS],"todayAnnouncements":[$NOTE]},
              {"initiator":"kyivska_oblast_oblenergo","queue":"9.9","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"}
            ]
        """.trimIndent()

        val EDGE_ROWS = """
            [
              {"initiator":"kyivska_oblast_oblenergo","queue":"1.1","updated":"2026-11-09T06:05:00.201Z","today":[$ZEROS],"tomorrow":[1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"todayStatus":"ScheduleApplies","tomorrowStatus":"ScheduleApplies","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0]},
              {"initiator":"kyivska_oblast_oblenergo","queue":"1.2","updated":"2026-11-09T06:05:00.201Z","today":[$ZEROS],"tomorrow":[],"todayStatus":"NoShutdowns","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[$ZEROS]},
              {"initiator":"kyivska_oblast_oblenergo","queue":"2.1","updated":"2026-11-09T06:05:00.201Z","today":[9,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"},
              {"initiator":"kyivska_oblast_oblenergo","queue":"2.2","updated":"2026-11-09T06:05:00.201Z","today":[$ZEROS],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":null},
              {"initiator":"kyivska_oblast_oblenergo","queue":"3.1","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"},
              {"initiator":"kyivska_oblast_oblenergo","queue":"3.2","updated":"2026-11-09T06:05:00.201Z","today":[$ZEROS],"tomorrow":[],"todayStatus":"EmergencyShutdowns","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"}
            ]
        """.trimIndent()
    }
}
