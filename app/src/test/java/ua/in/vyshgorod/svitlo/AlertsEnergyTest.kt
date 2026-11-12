package ua.`in`.vyshgorod.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.`in`.vyshgorod.svitlo.data.AlertsEnergy
import ua.`in`.vyshgorod.svitlo.data.DaySchedule
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.Regions
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.data.Snapshot

class AlertsEnergyTest {
    @Test
    fun mapsDtekHoursAndIgnoresRailway() {
        val page = AlertsEnergy.parse(page(
            row(
                initiator = "kyivska_oblast_uz",
                queue = "1.1",
                today = List(24) { 1 },
                todayStatus = "ScheduleApplies",
            ),
            row(
                initiator = "kyivska_oblast_oblenergo",
                queue = "1.1",
                today = listOf(1, 2, 3) + List(21) { 0 },
                todayPossible = listOf(0, 0, 2) + List(21) { 0 },
                tomorrow = List(24) { 0 },
                tomorrowStatus = "ScheduleApplies",
                message = "За наказом НЕК Укренерго діють стабілізаційні відключення.",
            ),
            row(
                queue = "1.2",
                today = emptyList(),
                todayStatus = "NoShutdowns",
                todayPossible = listOf(1) + List(23) { 0 },
                tomorrow = emptyList(),
                tomorrowStatus = "WaitingForSchedule",
                message = "За наказом НЕК Укренерго діють стабілізаційні відключення.",
            ),
            row(
                queue = "2.1",
                today = emptyList(),
                todayStatus = "ScheduleApplies",
                tomorrow = emptyList(),
                tomorrowStatus = "WaitingForSchedule",
            ),
        ))
        val today = page.days.single { it.day == "today" }
        assertEquals("12.11.2026", today.updatedDate)
        assertEquals("12 листопада", today.label)
        assertEquals(listOf(Outage(0, 90), Outage(120, 180)), today.groups["1.1"])
        assertEquals(listOf(Outage(0, 60)), today.groups["1.2"])
        assertFalse(today.groups.containsKey("2.1"))
        assertEquals("11.11.2026 20:30", today.groupUpdated["1.1"])
        val tomorrow = page.days.single { it.day == "tomorrow" }
        assertEquals("13.11.2026", tomorrow.updatedDate)
        assertEquals(emptyList<Outage>(), tomorrow.groups["1.1"])
        assertFalse(tomorrow.groups.containsKey("1.2"))
        assertFalse(page.tomorrowMissing)
        assertEquals(AlertsEnergy.SOURCE, page.source)
        assertEquals("За наказом НЕК Укренерго діють стабілізаційні відключення.", page.note)
    }

    @Test
    fun sameCalendarDateIsNotTomorrow() {
        val page = AlertsEnergy.parse(page(
            row(
                queue = "3.1",
                today = List(24) { 0 },
                tomorrow = listOf(1) + List(23) { 0 },
                tomorrowStatus = "ScheduleApplies",
                tomorrowDate = "2026-11-12",
            ),
        ))
        assertTrue(page.tomorrowMissing)
        assertEquals(listOf("today"), page.days.map { it.day })
        assertEquals(emptyList<Outage>(), page.days.single().groups["3.1"])
    }

    @Test
    fun missingPlanIsNotPowerOn() {
        val page = AlertsEnergy.parse(page(
            row(queue = "4.2", today = emptyList(), todayStatus = "WaitingForSchedule"),
        ))
        assertTrue(page.days.isEmpty())
        assertEquals("", page.source)
    }

    @Test
    fun explicitOperatorKeepsCityQueues() {
        val html = """
            <script id="ng-state" type="application/json">{
              "suppliers":{"data":[
                {"supplierKey":"city_uz","supplierName":"Укрзалізниця","supplierType":"uz","priority":10},
                {"supplierKey":"city_dtek","supplierName":"ДТЕК Київські електромережі","supplierType":"oblenergo","priority":20},
                {"supplierKey":"city_other","supplierName":"Інша мережа","supplierType":"other","priority":5}
              ]},
              "shutdowns":{"data":[
                ${row(initiator = "city_dtek", queue = "1.1", today = List(24) { 1 })},
                ${row(initiator = "city_other", queue = "60.1", today = listOf(1) + List(23) { 0 })},
                ${row(initiator = "city_other", queue = "10.1", today = listOf(2) + List(23) { 0 })},
                ${row(initiator = "city_uz", queue = "60.1", today = List(24) { 1 })}
              ]}
            }</script>
        """.trimIndent()
        val chosen = AlertsEnergy.parse(html, "city_other")
        val today = chosen.days.single { it.day == "today" }
        assertEquals(listOf("10.1", "60.1"), today.groups.keys.toList())
        assertEquals(listOf(Outage(0, 30)), today.groups["10.1"])
        assertEquals(listOf(Outage(0, 60)), today.groups["60.1"])
        assertEquals("city_other", chosen.operatorId)
        assertEquals(listOf("city_dtek", "city_other"), chosen.operators.map { it.id })
        val preferred = AlertsEnergy.parse(html)
        assertEquals("city_dtek", preferred.operatorId)
        assertEquals(listOf("1.1"), preferred.days.single { it.day == "today" }.groups.keys.toList())
        assertTrue(AlertsEnergy.parse(html, "missing").days.isEmpty())

        val city = Snapshot(
            fetchedAt = 0,
            days = listOf(
                DaySchedule(
                    "today",
                    "",
                    "",
                    "",
                    mapOf("60.1" to emptyList(), "10.1" to emptyList(), "2.1" to emptyList()),
                ),
            ),
            live = emptyList(),
            houses = emptyList(),
            tomorrowMissing = true,
            region = Regions.KYIV,
        )
        assertEquals(listOf("2.1", "10.1", "60.1"), ScheduleMath.knownGroups(city, Regions.KYIV))
        assertTrue(ScheduleMath.knownGroups(city, Regions.KYIV_OBLAST).isEmpty())
        assertEquals(12, ScheduleMath.knownGroups(null, Regions.KYIV_OBLAST).size)
        assertTrue(ScheduleMath.knownGroups(null, Regions.KYIV).isEmpty())
    }

    @Test
    fun pageWithoutStateIsEmpty() {
        val page = AlertsEnergy.parse("<html>vyshgorod</html>")
        assertTrue(page.days.isEmpty())
        assertEquals("", page.note)
    }

    private fun page(vararg rows: String): String {
        return """<script id="ng-state" type="application/json">{"shutdowns":{"data":[${rows.joinToString(",")}]}}</script>"""
    }

    private fun row(
        initiator: String = "kyivska_oblast_oblenergo",
        queue: String,
        today: List<Int> = emptyList(),
        todayStatus: String = "ScheduleApplies",
        todayPossible: List<Int> = emptyList(),
        tomorrow: List<Int> = emptyList(),
        tomorrowStatus: String = "WaitingForSchedule",
        todayDate: String = "2026-11-12",
        tomorrowDate: String = "2026-11-13",
        updated: String = "2026-11-11T18:30:18.845Z",
        message: String = "",
    ): String {
        val announcements = if (message.isBlank()) "[]" else """[{"message":${org.json.JSONObject.quote(message)}}]"""
        return """
            {
              "initiator":"$initiator",
              "queue":"$queue",
              "updated":"$updated",
              "today":${today.joinToString(prefix = "[", postfix = "]")},
              "tomorrow":${tomorrow.joinToString(prefix = "[", postfix = "]")},
              "todayStatus":"$todayStatus",
              "tomorrowStatus":"$tomorrowStatus",
              "todayDate":"$todayDate",
              "tomorrowDate":"$tomorrowDate",
              "todayPossible":${todayPossible.joinToString(prefix = "[", postfix = "]")},
              "todayAnnouncements":$announcements
            }
        """.trimIndent()
    }
}
