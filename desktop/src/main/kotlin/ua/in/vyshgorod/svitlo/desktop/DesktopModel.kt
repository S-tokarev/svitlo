package ua.`in`.vyshgorod.svitlo.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.`in`.vyshgorod.svitlo.data.AddressHit
import ua.`in`.vyshgorod.svitlo.data.AddressSearch
import ua.`in`.vyshgorod.svitlo.data.AppLanguage
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.PowerPicture
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.SvitloClient
import ua.`in`.vyshgorod.svitlo.data.SvitloParser

class DesktopModel {
    var selection by mutableStateOf(DesktopStore.loadSelection())
    var places by mutableStateOf(DesktopStore.loadPlaces())
    var snapshot by mutableStateOf(DesktopStore.loadSnapshot())
    var theme by mutableStateOf(DesktopStore.theme())
    var warn by mutableStateOf(DesktopStore.warnEnabled())
    var notifyOn by mutableStateOf(DesktopStore.notifyWhenOn())
    var quiet by mutableStateOf(DesktopStore.quietNight())
    var language by mutableStateOf(AppLanguage.Uk)
    var now by mutableStateOf(System.currentTimeMillis())

    init {
        L10n.applyCode(DesktopStore.languageCode())
        language = L10n.language
    }
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var hits by mutableStateOf<List<AddressHit>>(emptyList())
    var hint by mutableStateOf<String?>(null)

    var onNotify: (String, String) -> Unit = { _, _ -> }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshMs = 15 * 60 * 1000L

    val picture: PowerPicture?
        get() = snapshot?.let { ScheduleMath.picture(it, selection, now) }

    fun start() {
        scope.launch {
            while (isActive) {
                now = System.currentTimeMillis()
                checkAlerts()
                delay(20_000)
            }
        }
        scope.launch {
            val saved = snapshot
            if (saved == null || System.currentTimeMillis() - saved.fetchedAt >= refreshMs) refresh()
            while (isActive) {
                delay(refreshMs)
                refresh()
            }
        }
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { download() } }
            loading = false
            result.onSuccess {
                snapshot = it
                now = System.currentTimeMillis()
                checkAlerts()
            }.onFailure {
                error = it.message ?: L10n.updateFailed()
            }
        }
    }

    fun selectGroup(group: String) {
        selection = Selection(group)
        DesktopStore.savePlaces(places, selection)
        hits = emptyList()
        hint = null
    }

    fun focusPlace(place: Selection) {
        selection = place
        DesktopStore.savePlaces(places, place)
    }

    fun removePlace(place: Selection) {
        places = places.filterNot { it.placeKey == place.placeKey }
        if (selection.placeKey == place.placeKey) selection = places.lastOrNull() ?: Selection()
        DesktopStore.savePlaces(places, selection)
    }

    fun search() {
        val text = query.trim()
        if (text.length < 2) {
            hint = L10n.typeTwoLettersDesktop()
            hits = emptyList()
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AddressSearch.search(text, DesktopStore.cacheFile) }
            }
            result.onSuccess {
                hits = it.hits
                hint = it.hint
            }.onFailure {
                hint = it.message
                hits = emptyList()
            }
        }
    }

    fun pick(hit: AddressHit) {
        if (hit.group.isBlank()) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        AddressSearch.houses(
                            hit.city,
                            hit.street,
                            AddressSearch.houseFilter(query),
                            DesktopStore.cacheFile,
                        )
                    }
                }
                result.onSuccess {
                    hits = it.hits
                    hint = it.hint
                }.onFailure { hint = it.message }
            }
            return
        }
        selection = Selection(hit.group, hit.street, hit.house, hit.city)
        places = (places.filterNot { it.placeKey == selection.placeKey } + selection).takeLast(8)
        DesktopStore.savePlaces(places, selection)
        hits = emptyList()
        query = ""
    }

    fun updateWarn(value: Boolean) {
        warn = value
        DesktopStore.setWarn(value)
    }

    fun updateNotify(value: Boolean) {
        notifyOn = value
        DesktopStore.setNotify(value)
    }

    fun updateQuiet(value: Boolean) {
        quiet = value
        DesktopStore.setQuiet(value)
    }

    fun updateTheme(mode: ThemeMode) {
        theme = mode
        DesktopStore.setTheme(mode)
    }

    fun updateLanguage(value: AppLanguage) {
        language = value
        L10n.language = value
        DesktopStore.setLanguage(value)
    }

    private fun download(): Snapshot {
        val parsed = SvitloParser.parse(SvitloClient.fetchPage())
        if (parsed.days.isEmpty()) error(L10n.emptySchedule())
        val next = Snapshot(
            fetchedAt = System.currentTimeMillis(),
            days = parsed.days,
            live = parsed.live,
            houses = parsed.houses,
            tomorrowMissing = parsed.tomorrowMissing,
            note = parsed.note,
        )
        val previous = DesktopStore.loadSnapshot()
        if (previous != null) {
            watched().forEach { place ->
                if (!ScheduleMath.scheduleChanged(previous, next, place.group, next.fetchedAt)) return@forEach
                val key = "plan:${place.placeKey}:" + ScheduleMath.planStamp(next, place.group)
                if (DesktopStore.markNotified(key)) {
                    onNotify(L10n.scheduleChanged(), details(place, L10n.scheduleChangedTail()))
                }
            }
        }
        DesktopStore.saveSnapshot(next)
        return next
    }

    fun watched(): List<Selection> {
        if (places.isNotEmpty()) return places
        return if (selection.group.isBlank()) emptyList() else listOf(selection)
    }

    fun tooltip(): String {
        val items = watched()
        if (items.isEmpty() || snapshot == null) return L10n.appTitle()
        return items.joinToString(" · ") { place ->
            val on = ScheduleMath.picture(snapshot!!, place, now).displayOn
            val name = place.placeLabel ?: place.group
            val state = when (on) {
                true -> L10n.lightWord()
                false -> L10n.offWord()
                null -> "?"
            }
            "$name $state"
        }
    }

    private fun checkAlerts() {
        val currentSnapshot = snapshot ?: return
        val silent = quiet && ScheduleMath.isQuietHour(now)
        watched().forEach { place ->
            val current = ScheduleMath.picture(currentSnapshot, place, now)
            val due = current.dueWarnOffAt
            if (!silent && warn && due != null && DesktopStore.markNotified("warn:${place.placeKey}:$due")) {
                val minutes = ((due - now) / 60_000L).toInt().coerceAtLeast(0)
                onNotify(L10n.warnTitle(minutes), details(place, L10n.offDetail(ScheduleMath.formatDayTime(due, now))))
            }
            val display = current.displayOn
            val previous = DesktopStore.lastDisplayOn(place.placeKey)
            if (!silent && notifyOn && previous == false && display == true) {
                val near = current.appearAts.firstOrNull { kotlin.math.abs(it - now) < 30 * 60 * 1000L }
                val key = if (near != null) "appear:${place.placeKey}:$near" else "appear-live:${place.placeKey}:${now / 600_000L}"
                if (DesktopStore.markNotified(key)) {
                    onNotify(L10n.powerReturned(), details(place, L10n.powerOnTail()))
                }
            }
            if (display != null) DesktopStore.setLastDisplayOn(place.placeKey, display)
        }
    }

    private fun details(place: Selection, tail: String): String {
        return L10n.notice(place.group, place.placeLabel, tail)
    }
}
