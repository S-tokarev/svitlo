package ua.`in`.vyshgorod.svitlo.data

import java.net.HttpURLConnection
import java.net.URL

object SvitloClient {
    /**
     * One schedule page embeds every queue. The paid JSON API (Bearer) answers 401,
     * so the app reads the same payload from this public page.
     */
    private const val SCHEDULE =
        "https://alerts.energy/kyivska-oblast/schedule/kyivska-oblast-oblenergo/2.1"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    fun pageFor(group: String): String {
        val queue = if (group.matches(Regex("""^[1-6]\.[12]$"""))) group else "2.1"
        return "https://alerts.energy/kyivska-oblast/schedule/kyivska-oblast-oblenergo/$queue"
    }

    fun fetchPage(): String = get(SCHEDULE, referer = "https://alerts.energy/")

    private fun get(url: String, referer: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Language", "uk,ru;q=0.9")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Сайт ответил $code")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
