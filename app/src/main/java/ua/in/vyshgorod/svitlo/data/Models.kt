package ua.`in`.vyshgorod.svitlo.data

/** Outage interval in minutes from local midnight. End is exclusive, 1440 means 24:00. */
data class Outage(
    val startMin: Int,
    val endMin: Int,
)

data class DaySchedule(
    val day: String,
    val label: String,
    val updatedDate: String,
    val updatedTime: String,
    val groups: Map<String, List<Outage>>,
    /** Per queue, "dd.MM.yyyy HH:mm" in Europe/Kyiv. This is when the operator published the plan. */
    val groupUpdated: Map<String, String> = emptyMap(),
)

/** Countdown chip from the site: current planned state and seconds until it flips. */
data class LiveGroup(
    val id: String,
    val powerOn: Boolean,
    val toward: String,
    val seconds: Int,
)

data class HouseStatus(
    val group: String,
    val powerOn: Boolean,
    val address: String,
    val complex: String?,
    val since: String?,
)

data class AddressHit(
    val city: String,
    val street: String,
    val house: String,
    val group: String,
) {
    val label: String
        get() = listOf(city, street, house).filter { it.isNotBlank() }.joinToString(", ")
}

data class Selection(
    val group: String = "",
    val street: String? = null,
    val house: String? = null,
    val city: String? = null,
) {
    val placeLabel: String?
        get() {
            if (street.isNullOrBlank() || house.isNullOrBlank()) return null
            val place = listOfNotNull(city?.takeIf { it.isNotBlank() }, street, house)
            return place.joinToString(", ")
        }

    val placeKey: String
        get() = listOf(group, city.orEmpty(), street.orEmpty(), house.orEmpty()).joinToString("\u001f")
}

data class Snapshot(
    val fetchedAt: Long,
    val days: List<DaySchedule>,
    val live: List<LiveGroup>,
    val houses: List<HouseStatus>,
    val tomorrowMissing: Boolean,
    /** Operator announcement. A local notice does not cancel a published plan. */
    val note: String = "",
)

data class ParsedPage(
    val days: List<DaySchedule>,
    val live: List<LiveGroup>,
    val houses: List<HouseStatus>,
    val tomorrowMissing: Boolean,
    val note: String = "",
)
