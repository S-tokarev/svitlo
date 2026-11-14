package ua.`in`.vyshgorod.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.`in`.vyshgorod.svitlo.data.AlertsEnergy
import ua.`in`.vyshgorod.svitlo.data.DtekSchedule
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.Regions

class DtekScheduleTest {
    @Test
    fun mapsProbedGroupAndHalfHours() {
        val page = DtekSchedule.parse(fact())
        val today = page.days.single { it.day == "today" }
        assertEquals("12.11.2026", today.updatedDate)
        assertEquals("15:00", today.updatedTime)
        assertEquals("12 листопада", today.label)
        assertEquals(listOf("1.1", "1.2", "3.1", "4.1", "6.2"), today.groups.keys.toList())
        assertEquals(listOf(Outage(0, 240), Outage(600, 870), Outage(1200, 1440)), today.groups["1.1"])
        assertEquals(listOf(Outage(0, 60)), today.groups["1.2"])
        assertEquals(emptyList<Outage>(), today.groups["3.1"])
        assertEquals(listOf(Outage(840, 870), Outage(930, 960)), today.groups["4.1"])
        assertEquals(listOf(Outage(0, 90), Outage(150, 180)), today.groups["6.2"])
        assertEquals("12.11.2026 15:00", today.groupUpdated["1.1"])
        assertFalse(today.groups.containsKey("junk"))
        val tomorrow = page.days.single { it.day == "tomorrow" }
        assertEquals("13.11.2026", tomorrow.updatedDate)
        assertEquals("13 листопада", tomorrow.label)
        assertEquals(listOf(Outage(1380, 1440)), tomorrow.groups["2.1"])
        assertFalse(page.tomorrowMissing)
        assertEquals(DtekSchedule.SOURCE, page.source)
        assertEquals(Regions.KYIV_OBLAST, page.region)
        assertEquals(AlertsEnergy.KYIV_OBLAST_DTEK, page.operatorId)
        assertEquals("ДТЕК Київські регіональні електромережі", page.operators.single().name)
    }

    @Test
    fun allClearTomorrowIsNotAFullDayOfPower() {
        val today = 1794434400L
        val tomorrow = today + 86_400L
        val page = DtekSchedule.parse(
            """
            {
              "result": true,
              "fact": {
                "update": "13.11.2026 20:47",
                "today": $today,
                "data": {
                  "$today": {"GPV2.1": {"1": "no"}},
                  "$tomorrow": {
                    "GPV2.1": {"1": "yes", "2": "yes"},
                    "GPV3.1": {"5": "no"}
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val next = page.days.single { it.day == "tomorrow" }
        assertEquals(listOf("3.1"), next.groups.keys.toList())
        assertEquals(listOf(Outage(240, 300)), next.groups["3.1"])
        assertFalse(next.groups.containsKey("2.1"))
        assertFalse(page.tomorrowMissing)
    }

    @Test
    fun missingFactIsNotASchedule() {
        val page = DtekSchedule.parse("""{"result":true,"data":{}}""")
        assertTrue(page.days.isEmpty())
        assertEquals("", page.source)
        assertTrue(DtekSchedule.parse("""{"result":false}""").days.isEmpty())
    }

    /** Hours from the 12.11.2026 probe, plus small groups that pin halves and possible slots. */
    private fun fact(): String {
        val probed = (1..24).joinToString(",") { hour ->
            val value = when (hour) {
                in 1..4, in 11..14, in 21..24 -> "no"
                15 -> "first"
                else -> "yes"
            }
            "\"$hour\":\"$value\""
        }
        val today = 1794434400L
        val tomorrow = today + 86_400L
        return """
            {
              "result": true,
              "fact": {
                "update": "12.11.2026 15:00",
                "today": $today,
                "data": {
                  "$today": {
                    "GPV1.1": {$probed},
                    "GPV1.2": {"1": "no"},
                    "GPV3.1": {"1": "yes", "2": "yes"},
                    "GPV4.1": {"15": "first", "16": "second"},
                    "GPV6.2": {"1": "maybe", "2": "mfirst", "3": "msecond", "4": "yes"},
                    "junk": {"1": "no"}
                  },
                  "$tomorrow": {
                    "GPV2.1": {"24": "no"}
                  }
                }
              }
            }
        """.trimIndent()
    }
}
