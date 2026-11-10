package ua.`in`.vyshgorod.svitlo.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/**
 * Street and house lookup for the whole Kyiv oblast, from the same directory
 * the DTEK Kyiv regional networks shutdown page uses. The schedule itself
 * comes from the public vyshgorod.in.ua/svitlo page.
 */
object AddressSearch {
    private const val AJAX = "https://www.dtek-krem.com.ua/ua/ajax"
    private const val ORIGIN = "https://www.dtek-krem.com.ua"
    private const val REFERER = "https://www.dtek-krem.com.ua/ua/shutdowns"
    private const val USER_AGENT = "Svitlo/1.0 (power schedule; Kyiv oblast)"
    private const val FRESH_MS = 24L * 60L * 60L * 1000L
    private const val STREET_LIMIT = 24
    private const val HOUSE_LIMIT = 40
    private const val AUTO_HOUSE_STREETS = 3

    private val queueName = Regex("""^[1-6]\.[12]$""")
    private val csrfPattern = Regex("""i:1;s:\d+:"([^"]+)"""")
    private val placePrefix = Regex("""^(смт|місто|селище|село|с-ще|м|с)\.?\s+""", RegexOption.IGNORE_CASE)
    private val streetPrefix = Regex(
        """^(вулиця|вул|улица|провулок|пров|проспект|просп|бульвар|бул|площа|пл|набережна|наб|шосе|тупик|майдан|проїзд|проезд|ул)\.?\s+""",
        RegexOption.IGNORE_CASE,
    )
    private val houseToken = Regex("""^\d+[0-9a-zа-яіїєґ/\-]*$""", RegexOption.IGNORE_CASE)
    private val stopWords = setOf(
        "вул", "вулиця", "улица", "ул", "пров", "провулок", "просп", "проспект",
        "бул", "бульвар", "пл", "площа", "наб", "набережна", "шосе", "тупик",
        "майдан", "місто", "село", "селище", "смт", "город",
    )
    private val latinHouse = "aceiopxkmhtby".toCharArray().zip("асеіорхкмнтву".toCharArray()).toMap()

    private val gate = Any()
    private val random = SecureRandom()
    private val session = DtekSession()
    private var directory: Map<String, List<String>>? = null
    private var directoryAt = 0L

    data class Lookup(val hits: List<AddressHit>, val hint: String? = null)

    fun search(query: String, cacheFile: File): Lookup {
        val parsed = parseQuery(query)
        if (parsed.tokens.isEmpty()) error("Введите название улицы")
        val streets = loadDirectory(cacheFile)
        val found = matchStreets(streets, parsed)
        if (found.streets.isEmpty()) return Lookup(emptyList())
        val house = parsed.house
        if (house != null && found.streets.size <= AUTO_HOUSE_STREETS) {
            val hits = found.streets.flatMap { street ->
                housesOn(street.city, street.street, house).hits.filter { houseMatches(house, it.house) }
            }
            if (hits.isNotEmpty()) {
                return Lookup(hits.distinctBy { it.city + it.street + it.house + it.group }.take(HOUSE_LIMIT))
            }
        }
        val hint = if (found.truncated) "Много улиц. Добавьте населённый пункт." else null
        return Lookup(
            found.streets.map { AddressHit(city = it.city, street = it.street, house = "", group = "") },
            hint,
        )
    }

    fun houses(city: String, street: String, houseQuery: String?, cacheFile: File): Lookup {
        loadDirectory(cacheFile)
        return housesOn(city, street, houseQuery?.takeIf { it.isNotBlank() })
    }

    fun houseFilter(query: String): String? = parseQuery(query).house

    internal fun parseDirectory(json: String): Map<String, List<String>> {
        val streets = JSONObject(json).optJSONObject("streets") ?: return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        val names = streets.keys()
        while (names.hasNext()) {
            val city = names.next()
            val list = streets.optJSONArray(city) ?: continue
            val values = buildList {
                for (index in 0 until list.length()) {
                    val street = list.optString(index)
                    if (street.isNotBlank()) add(street)
                }
            }
            if (city.isNotBlank() && values.isNotEmpty()) result[city] = values
        }
        return result
    }

    internal fun parseHouses(json: String, city: String, street: String, houseQuery: String?): Lookup {
        val data = JSONObject(json).optJSONObject("data") ?: return Lookup(emptyList())
        val hits = mutableListOf<AddressHit>()
        val numbers = data.keys()
        while (numbers.hasNext()) {
            val number = numbers.next()
            if (houseQuery != null && !houseMatches(houseQuery, number)) continue
            for (group in groupsOf(data.opt(number))) {
                hits.add(AddressHit(city = city, street = street, house = number, group = group))
            }
        }
        hits.sortWith(compareBy({ houseOrder(it.house) }, { it.house }, { it.group }))
        if (hits.isEmpty() && houseQuery != null) {
            val all = parseHouses(json, city, street, null)
            if (all.hits.isEmpty()) return Lookup(emptyList())
            return Lookup(all.hits.take(HOUSE_LIMIT), "Такого дома нет. Показаны дома этой улицы.")
        }
        val hint = if (hits.size > HOUSE_LIMIT) "Введите номер дома, чтобы сузить список." else null
        return Lookup(hits.take(HOUSE_LIMIT), hint)
    }

    internal fun csrfToken(cookieValue: String): String? {
        val decoded = URLDecoder.decode(cookieValue, "UTF-8")
        return csrfPattern.find(decoded)?.groupValues?.get(1)
    }

    internal fun maskToken(token: String, mask: ByteArray = random.generateSeed(token.length)): String {
        val raw = token.toByteArray(Charsets.UTF_8)
        require(mask.size == raw.size)
        val masked = ByteArray(raw.size) { index -> (mask[index].toInt() xor raw[index].toInt()).toByte() }
        return Base64.getUrlEncoder().encodeToString(mask + masked)
    }

    internal data class ParsedQuery(val house: String?, val tokens: List<String>)

    internal fun parseQuery(query: String): ParsedQuery {
        val parts = query.trim().split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return ParsedQuery(null, emptyList())
        val house = parts.last().takeIf { houseToken.matches(it) }
        val words = if (house == null) parts else parts.dropLast(1)
        val tokens = words.map { fold(it.trim('.')) }.filter { it.length >= 2 && it !in stopWords }
        return ParsedQuery(house, tokens)
    }

    internal data class StreetHit(val city: String, val street: String)

    internal data class StreetQuery(val streets: List<StreetHit>, val truncated: Boolean)

    internal fun matchStreets(directory: Map<String, List<String>>, parsed: ParsedQuery): StreetQuery {
        if (parsed.tokens.isEmpty()) return StreetQuery(emptyList(), false)
        val scored = mutableListOf<Scored>()
        for ((city, streets) in directory) {
            val cityFold = fold(stripPlace(city))
            for (street in streets) {
                val streetFold = fold(stripStreet(street))
                val streetHits = parsed.tokens.filter { tokenHits(streetFold, it) }
                if (streetHits.isEmpty()) continue
                if (parsed.tokens.any { !tokenHits(streetFold, it) && !tokenHits(cityFold, it) }) continue
                val cityHits = parsed.tokens.filter { tokenHits(cityFold, it) }
                var score = 10
                if (streetFold == streetHits.joinToString(" ")) score += 50
                if (streetFold.startsWith(streetHits.first())) score += 20
                if (cityHits.isNotEmpty()) score += 30
                if (streetHits.size == parsed.tokens.size) score += 15
                scored.add(Scored(city, street, score))
            }
        }
        if (scored.isEmpty()) return StreetQuery(emptyList(), false)
        scored.sortWith(compareByDescending<Scored> { it.score }.thenBy { it.city }.thenBy { it.street })
        val best = scored.first().score
        val close = scored.filter { it.score >= best - 15 }
        return StreetQuery(
            close.take(STREET_LIMIT).map { StreetHit(it.city, it.street) },
            close.size > STREET_LIMIT,
        )
    }

    internal fun houseMatches(wanted: String, candidate: String): Boolean {
        val left = foldHouse(wanted)
        val right = foldHouse(candidate)
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        if (!right.startsWith(left)) return false
        val next = right.getOrNull(left.length) ?: return true
        return !next.isDigit()
    }

    private fun housesOn(city: String, street: String, houseQuery: String?): Lookup {
        val json = synchronized(gate) { session.homes(city, street) }
        val payload = JSONObject(json)
        if (payload.optBoolean("result") != true) return Lookup(emptyList())
        return parseHouses(json, city, street, houseQuery)
    }

    private fun loadDirectory(cacheFile: File): Map<String, List<String>> = synchronized(gate) {
        val now = System.currentTimeMillis()
        val memory = directory
        if (memory != null && now - directoryAt < FRESH_MS) return memory
        val cached = readCache(cacheFile, now)
        if (cached != null) {
            directory = cached
            directoryAt = cacheFile.lastModified().takeIf { it > 0L } ?: now
            return cached
        }
        val json = session.streets()
        val parsed = parseDirectory(json)
        if (parsed.isEmpty()) error("Справочник адресов пуст")
        writeCache(cacheFile, json)
        directory = parsed
        directoryAt = now
        parsed
    }

    private fun readCache(cacheFile: File, now: Long): Map<String, List<String>>? {
        if (!cacheFile.isFile) return null
        if (now - cacheFile.lastModified() > FRESH_MS) return null
        return runCatching { parseDirectory(cacheFile.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun writeCache(cacheFile: File, json: String) {
        cacheFile.parentFile?.mkdirs()
        val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        temp.writeText(json)
        if (cacheFile.exists()) cacheFile.delete()
        if (!temp.renameTo(cacheFile)) {
            cacheFile.writeText(json)
            temp.delete()
        }
    }

    private fun groupsOf(value: Any?): List<String> {
        val records = when (value) {
            is JSONObject -> listOf(value)
            is JSONArray -> buildList {
                for (index in 0 until value.length()) value.optJSONObject(index)?.let { add(it) }
            }
            else -> emptyList()
        }
        if (records.any { servedElsewhere(it) }) return emptyList()
        return records.flatMap { reasons(it) }.mapNotNull { queueOf(it) }.distinct()
    }

    private fun reasons(record: JSONObject): List<String> {
        return when (val raw = record.opt("sub_type_reason")) {
            is JSONArray -> buildList {
                for (index in 0 until raw.length()) {
                    val reason = raw.optString(index)
                    if (reason.isNotBlank()) add(reason)
                }
            }
            is String -> listOf(raw).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun servedElsewhere(record: JSONObject): Boolean {
        if (!record.has("cek") || record.isNull("cek")) return false
        return when (val value = record.opt("cek")) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.isNotBlank() && value != "0" && !value.equals("false", true)
            else -> false
        }
    }

    private fun queueOf(raw: String): String? {
        val name = raw.trim().removePrefix("GPV").removePrefix("gpv")
        return name.takeIf { queueName.matches(it) }
    }

    private fun stripPlace(city: String): String = placePrefix.replace(city.trim(), "")

    private fun stripStreet(street: String): String = streetPrefix.replace(street.trim(), "")

    private fun fold(value: String): String {
        val lower = value.lowercase(Locale.forLanguageTag("uk"))
        val folded = StringBuilder(lower.length)
        for (char in lower) {
            when (char) {
                'і', 'ы' -> folded.append('и')
                'ї', 'є', 'ё', 'э' -> folded.append('е')
                'ґ' -> folded.append('г')
                'ъ', '\'', '’', 'ʼ', '-' -> Unit
                else -> folded.append(char)
            }
        }
        return folded.toString()
    }

    private fun loose(folded: String): String = folded.filter { it !in "аеиоуьюя" }

    private fun tokenHits(haystack: String, token: String): Boolean {
        if (haystack.contains(token)) return true
        if (token.length < 4) return false
        val needle = loose(token)
        return needle.length >= 3 && loose(haystack).contains(needle)
    }

    private fun foldHouse(value: String): String {
        val compact = value.filter { !it.isWhitespace() }.lowercase(Locale.ROOT)
        val folded = StringBuilder(compact.length)
        for (char in compact) folded.append(latinHouse[char] ?: char)
        return folded.toString()
    }

    private fun houseOrder(house: String): Int = house.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

    private data class Scored(val city: String, val street: String, val score: Int)

    private class DtekSession {
        private val cookies = linkedMapOf<String, String>()
        private var token: String? = null

        fun streets(): String = call(mapOf("method" to "getStreets"))

        fun homes(city: String, street: String): String = call(
            linkedMapOf(
                "method" to "getHomeNum",
                "data[0][name]" to "city",
                "data[0][value]" to city,
                "data[1][name]" to "street",
                "data[1][value]" to street,
                "data[2][name]" to "updateFact",
                "data[2][value]" to "",
            ),
        )

        private fun call(form: Map<String, String>): String {
            ensureToken()
            var answer = post(form, withToken = true)
            if (answer.code == 400) {
                token = null
                ensureToken()
                answer = post(form, withToken = true)
            }
            if (answer.code !in 200..299) error("ДТЕК ответил ${answer.code}")
            if ("_Incapsula_Resource" in answer.body) error("ДТЕК временно закрыл справочник")
            return answer.body
        }

        private fun ensureToken() {
            if (token != null) return
            post(mapOf("method" to "getStreets"), withToken = false)
            if (token == null) error("ДТЕК не выдал ключ запроса")
        }

        private fun post(form: Map<String, String>, withToken: Boolean): Answer {
            val body = form.entries.joinToString("&") { (key, value) ->
                URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            }
            val connection = (URL(AJAX).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Origin", ORIGIN)
                setRequestProperty("Referer", REFERER)
                if (cookies.isNotEmpty()) {
                    setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
                if (withToken) setRequestProperty("X-CSRF-Token", maskToken(token ?: error("Нет ключа запроса")))
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                absorbCookies(connection)
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return Answer(code, text)
            } finally {
                connection.disconnect()
            }
        }

        private fun absorbCookies(connection: HttpURLConnection) {
            var index = 0
            while (true) {
                val key = connection.getHeaderFieldKey(index)
                if (key == null) {
                    if (index == 0) {
                        index += 1
                        continue
                    }
                    break
                }
                if (key.equals("Set-Cookie", ignoreCase = true)) {
                    val header = connection.getHeaderField(index).orEmpty()
                    val pair = header.substringBefore(';')
                    val name = pair.substringBefore('=').trim()
                    val value = pair.substringAfter('=', "")
                    if (name.isNotEmpty()) cookies[name] = value
                    if (name == "_csrf-dtek-krem") token = csrfToken(value) ?: token
                }
                index += 1
            }
        }
    }

    private data class Answer(val code: Int, val body: String)
}
