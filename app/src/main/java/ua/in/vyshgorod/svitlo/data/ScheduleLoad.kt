package ua.`in`.vyshgorod.svitlo.data

/**
 * Kyiv oblast tries vyshgorod.in.ua/svitlo first.
 * Every other region, and a failed oblast page, comes from alerts.energy.
 */
object ScheduleLoad {
    fun load(region: String, operatorId: String = ""): ParsedPage {
        val slug = Regions.normalize(region)
        val household = operatorId.isBlank() || operatorId == AlertsEnergy.KYIV_OBLAST_DTEK
        if (slug == Regions.KYIV_OBLAST && household) {
            val primary = runCatching { SvitloParser.parse(SvitloClient.fetchPage()) }.getOrNull()
            if (primary != null && primary.days.isNotEmpty()) {
                return primary.copy(region = slug, operatorId = AlertsEnergy.KYIV_OBLAST_DTEK)
            }
        }
        val alternate = runCatching { AlertsEnergy.parse(SvitloClient.fetchAlerts(slug), operatorId) }.getOrNull()
        if (alternate != null && alternate.days.isNotEmpty()) return alternate.copy(region = slug)
        error(if (slug == Regions.KYIV_OBLAST && household) L10n.bothSitesFailed() else L10n.alertsFailed())
    }
}
