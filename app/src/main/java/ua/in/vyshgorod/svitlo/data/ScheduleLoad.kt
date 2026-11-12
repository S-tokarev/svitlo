package ua.`in`.vyshgorod.svitlo.data

import java.io.File

/**
 * Kyiv city and Dnipro use the public YASNO API. Kyiv oblast uses the DTEK ajax schedule.
 * If that call fails, oblast still tries vyshgorod.in.ua/svitlo and then alerts.energy.
 * A personal alerts.energy key is the later JSON fallback.
 * After «too many requests» that path waits, so the next refresh does not hit the same site again.
 */
object ScheduleLoad {
    @Volatile private var htmlQuietUntilMs = 0L
    @Volatile private var apiQuietUntilMs = 0L
    @Volatile private var dtekQuietUntilMs = 0L
    @Volatile private var yasnoQuietUntilMs = 0L

    fun load(
        region: String,
        operatorId: String = "",
        apiKey: String = "",
        streetCache: File? = null,
    ): ParsedPage {
        val now = System.currentTimeMillis()
        val slug = Regions.normalize(region)
        val household = operatorId.isBlank() || operatorId == AlertsEnergy.KYIV_OBLAST_DTEK
        val key = SvitloClient.normalizeApiKey(apiKey)
        val direct = direct(slug, operatorId, household, streetCache, now)
        if (direct != null) return direct
        if (key.isNotEmpty() && now >= apiQuietUntilMs) {
            try {
                val page = loadApi(slug, operatorId, key)
                if (page.days.isNotEmpty()) return page.copy(region = slug)
            } catch (rate: RateLimited) {
                val until = now + rate.waitMs
                apiQuietUntilMs = until
                htmlQuietUntilMs = until
                error(L10n.tooMany())
            } catch (_: Exception) {
                // A rejected key can still use the public page.
            }
        }
        if (System.currentTimeMillis() < htmlQuietUntilMs) error(L10n.tooMany())
        try {
            if (slug == Regions.KYIV_OBLAST && household) {
                val primary = tolerate { SvitloParser.parse(SvitloClient.fetchPage()) }
                if (primary != null && primary.days.isNotEmpty()) {
                    return primary.copy(region = slug, operatorId = AlertsEnergy.KYIV_OBLAST_DTEK)
                }
            }
            val alternate = tolerate { AlertsEnergy.parse(SvitloClient.fetchAlerts(slug), operatorId) }
            if (alternate != null && alternate.days.isNotEmpty()) return alternate.copy(region = slug)
        } catch (rate: RateLimited) {
            htmlQuietUntilMs = System.currentTimeMillis() + rate.waitMs
            error(L10n.tooMany())
        }
        error(if (slug == Regions.KYIV_OBLAST && household) L10n.bothSitesFailed() else L10n.alertsFailed())
    }

    /** A published direct chart, or null when the older pages should be tried. A quiet clock does not fall through. */
    private fun direct(
        slug: String,
        operatorId: String,
        household: Boolean,
        streetCache: File?,
        now: Long,
    ): ParsedPage? {
        val fromDtek = slug == Regions.KYIV_OBLAST && household
        val fromYasno = Yasno.covers(slug)
        if (!fromDtek && !fromYasno) return null
        if (fromDtek && now < dtekQuietUntilMs) error(L10n.tooMany())
        if (fromYasno && now < yasnoQuietUntilMs) error(L10n.tooMany())
        try {
            val page = if (fromDtek) streetCache?.let { DtekSchedule.load(it) } else Yasno.load(slug, operatorId)
            if (page != null && page.days.isNotEmpty()) return page.copy(region = slug)
        } catch (rate: RateLimited) {
            val until = now + rate.waitMs
            if (fromDtek) dtekQuietUntilMs = until else yasnoQuietUntilMs = until
            error(L10n.tooMany())
        } catch (_: Exception) {
        }
        return null
    }

    private fun loadApi(slug: String, operatorId: String, key: String): ParsedPage {
        val base = "https://alerts.energy/api/v1/source-registry/areas/$slug"
        val shutdowns = SvitloClient.fetchJson("$base/shutdowns", key)
        val suppliers = try {
            SvitloClient.fetchJson("$base/suppliers", key)
        } catch (rate: RateLimited) {
            throw rate
        } catch (_: Exception) {
            "[]"
        }
        return AlertsEnergy.parseApi(shutdowns, suppliers, operatorId)
    }

    private inline fun <T> tolerate(block: () -> T): T? {
        try {
            return block()
        } catch (rate: RateLimited) {
            throw rate
        } catch (_: Exception) {
            return null
        }
    }
}
