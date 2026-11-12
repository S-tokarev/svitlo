package ua.`in`.vyshgorod.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.`in`.vyshgorod.svitlo.data.DtekSchedule
import ua.`in`.vyshgorod.svitlo.data.GridOperator
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.Regions
import ua.`in`.vyshgorod.svitlo.data.SvitloClient
import ua.`in`.vyshgorod.svitlo.data.Yasno

class YasnoTest {
    @Test
    fun mapsSlotsAndDropsASameDateTomorrow() {
        val page = Yasno.parse(sample(), "902", listOf(GridOperator("902", "ТОВ «ДТЕК КИЇВСЬКІ ЕЛЕКТРОМЕРЕЖІ»")))
        val today = page.days.single { it.day == "today" }
        assertEquals("12.11.2026", today.updatedDate)
        assertEquals("12 листопада", today.label)
        assertEquals("", today.updatedTime)
        assertEquals(listOf("1.1", "2.1", "4.1", "5.1", "10.1"), today.groups.keys.toList())
        assertEquals(listOf(Outage(120, 240)), today.groups["1.1"])
        assertEquals(emptyList<Outage>(), today.groups["2.1"])
        assertEquals(emptyList<Outage>(), today.groups["5.1"])
        assertEquals(listOf(Outage(100, 130)), today.groups["4.1"])
        assertEquals(listOf(Outage(0, 30)), today.groups["10.1"])
        assertFalse(today.groups.containsKey("3.1"))
        assertEquals("11.11.2026 21:00", today.groupUpdated["1.1"])
        assertEquals("", today.groupUpdated["4.1"])
        val tomorrow = page.days.single { it.day == "tomorrow" }
        assertEquals("13.11.2026", tomorrow.updatedDate)
        assertEquals("13 листопада", tomorrow.label)
        assertEquals(listOf("3.1"), tomorrow.groups.keys.toList())
        assertEquals(listOf(Outage(60, 120)), tomorrow.groups["3.1"])
        assertFalse(tomorrow.groups.containsKey("2.1"))
        assertFalse(page.tomorrowMissing)
        assertEquals(Yasno.SOURCE, page.source)
        assertEquals("902", page.operatorId)
    }

    @Test
    fun waitingForScheduleIsNotADay() {
        val page = Yasno.parse(
            """{"1.1":{"today":{"slots":[],"date":"2026-11-12T00:00:00+02:00","status":"WaitingForSchedule"},"tomorrow":{"slots":[],"date":"2026-11-13T00:00:00+02:00","status":"WaitingForSchedule"}}}""",
            "902",
            emptyList(),
        )
        assertTrue(page.days.isEmpty())
        assertEquals("", page.source)
    }

    @Test
    fun pagesFollowTheSourceThatPublishedTheChart() {
        assertTrue(Yasno.covers(Regions.KYIV))
        assertTrue(Yasno.covers("dnipropetrovska-oblast"))
        assertFalse(Yasno.covers(Regions.KYIV_OBLAST))
        assertEquals("https://static.yasno.ua/kyiv/outages", Yasno.page(Regions.KYIV))
        assertEquals("https://static.yasno.ua/dnipro/outages", Yasno.page("dnipropetrovska-oblast"))
        assertEquals("https://static.yasno.ua/kyiv/outages", SvitloClient.pageFor("", Yasno.SOURCE, Regions.KYIV))
        assertEquals("https://static.yasno.ua/dnipro/outages", SvitloClient.pageFor("", "", "dnipropetrovska-oblast"))
        assertEquals(DtekSchedule.PAGE, SvitloClient.pageFor("", DtekSchedule.SOURCE, Regions.KYIV_OBLAST))
        assertEquals("https://vyshgorod.in.ua/svitlo", SvitloClient.pageFor("", "", Regions.KYIV_OBLAST))
        assertEquals("https://alerts.energy/odeska-oblast", SvitloClient.pageFor("", "", "odeska-oblast"))
        assertNull(Yasno.load("odeska-oblast"))
    }

    private fun sample(): String = """
        {
          "nope": {"today": {"slots": [{"start": 0, "end": 60, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "ScheduleApplies"}},
          "2.1": {
            "today": {"slots": [], "date": "2026-11-12T00:00:00+02:00", "status": "NoShutdowns"},
            "tomorrow": {"slots": [{"start": 0, "end": 60, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "ScheduleApplies"},
            "updatedOn": "2026-11-11T19:00:01+00:00"
          },
          "10.1": {
            "today": {"slots": [{"start": 0, "end": 30, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "ScheduleApplies"},
            "updatedOn": "2026-11-11T19:00:01+00:00"
          },
          "1.1": {
            "today": {
              "slots": [
                {"start": 0, "end": 120, "type": "NotPlanned"},
                {"start": 120, "end": 180, "type": "Definite"},
                {"start": 180, "end": 240, "type": "Possible"}
              ],
              "date": "2026-11-12T00:00:00+02:00",
              "status": "ScheduleApplies"
            },
            "tomorrow": {"slots": [], "date": "2026-11-13T00:00:00+02:00", "status": "WaitingForSchedule"},
            "updatedOn": "2026-11-11T19:00:01+00:00"
          },
          "3.1": {
            "today": {"slots": [], "date": "2026-11-12T00:00:00+02:00", "status": "EmergencyShutdowns"},
            "tomorrow": {
              "slots": [
                {"start": 60, "end": 90, "type": "Definite"},
                {"start": 90, "end": 120, "type": "Possible"}
              ],
              "date": "2026-11-13T00:00:00+02:00",
              "status": "ScheduleApplies"
            }
          },
          "4.1": {
            "today": {"slots": [{"start": 100, "end": 130, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "EmergencyShutdowns"}
          },
          "5.1": {
            "today": {"slots": [], "date": "2026-11-12T00:00:00+02:00", "status": "NoOutages"}
          }
        }
    """.trimIndent()
}
