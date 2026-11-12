package ua.`in`.vyshgorod.svitlo.data

import java.net.HttpURLConnection
import java.net.URL

/** The site asked us to wait. [waitMs] is already clamped. */
class RateLimited(val waitMs: Long) : RuntimeException(L10n.tooMany())

/** The JSON API refused the key. The public page can still be tried. */
class ApiDenied : RuntimeException()

object SvitloClient {
    private const val SCHEDULE = "https://vyshgorod.in.ua/svitlo"
    private const val USER_AGENT = "Svitlo/1.0 (power schedule)"

    fun pageFor(group: String, source: String = "", region: String = Regions.KYIV_OBLAST): String {
        val slug = Regions.normalize(region)
        return when (source) {
            Yasno.SOURCE -> Yasno.page(slug)
            DtekSchedule.SOURCE -> DtekSchedule.PAGE
            AlertsEnergy.SOURCE -> AlertsEnergy.page(slug)
            else -> when {
                Yasno.covers(slug) -> Yasno.page(slug)
                slug == Regions.KYIV_OBLAST -> SCHEDULE
                else -> AlertsEnergy.page(slug)
            }
        }
    }

    /** Public JSON that does not need a key. */
    fun fetchPublic(url: String): String = request(url, json = true) { connection ->
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
    }

    fun fetchPage(): String = readHtml(SCHEDULE, "https://vyshgorod.in.ua/")

    fun fetchAlerts(region: String): String = readHtml(AlertsEnergy.page(region), "https://alerts.energy/")

    /** Documented call. The key is the user's own subscription key, sent only as a header. */
    fun fetchJson(url: String, apiKey: String): String {
        val key = normalizeApiKey(apiKey)
        return request(url, json = true) { connection ->
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
        }
    }

    fun normalizeApiKey(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 7 && trimmed.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)) {
            return trimmed.substring(7).trim()
        }
        return trimmed
    }

    /** A real schedule page embeds ng-state. A short refusal page does not. */
    fun looksLimited(code: Int, body: String): Boolean {
        if (code == 429) return true
        if (body.contains("ng-state")) return false
        return body.contains("Забагато запитів") || body.contains("Too many requests", ignoreCase = true)
    }

    fun retryWaitMs(retryAfter: String?): Long {
        val seconds = retryAfter?.trim()?.toLongOrNull() ?: 600L
        return (seconds * 1000L).coerceIn(60_000L, 30 * 60_000L)
    }

    private fun readHtml(url: String, referer: String): String {
        return request(url, json = false) { connection ->
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Accept-Language", "uk,ru;q=0.9")
        }
    }

    private fun request(url: String, json: Boolean, headers: (HttpURLConnection) -> Unit): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            headers(this)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (looksLimited(code, body)) throw RateLimited(retryWaitMs(connection.getHeaderField("Retry-After")))
            if (json && (code == 401 || code == 403)) throw ApiDenied()
            if (code !in 200..299) error(L10n.siteAnswered(code))
            return body
        } finally {
            connection.disconnect()
        }
    }
}
