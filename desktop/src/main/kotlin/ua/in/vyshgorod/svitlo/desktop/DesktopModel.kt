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
import ua.`in`.vyshgorod.svitlo.data.PowerPicture
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.SvitloClient
import ua.`in`.vyshgorod.svitlo.data.SvitloParser

class DesktopModel {
    var selection by mutableStateOf(DesktopStore.loadSelection())
    var snapshot by mutableStateOf(DesktopStore.loadSnapshot())
    var theme by mutableStateOf(DesktopStore.theme())
    var warn by mutableStateOf(DesktopStore.warnEnabled())
    var notifyOn by mutableStateOf(DesktopStore.notifyWhenOn())
    var quiet by mutableStateOf(DesktopStore.quietNight())
    var now by mutableStateOf(System.currentTimeMillis())
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
                error = it.message ?: "Не удалось обновить график"
            }
        }
    }

    fun selectGroup(group: String) {
        selection = Selection(group)
        DesktopStore.saveSelection(selection)
        hits = emptyList()
        hint = null
    }

    fun search() {
        val text = query.trim()
        if (text.length < 2) {
            hint = "Введите хотя бы две буквы"
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
        DesktopStore.saveSelection(selection)
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

    private fun download(): Snapshot {
        val parsed = SvitloParser.parse(SvitloClient.fetchPage())
        if (parsed.days.isEmpty()) error("Сайт отдал страницу без графика")
        val next = Snapshot(
            fetchedAt = System.currentTimeMillis(),
            days = parsed.days,
            live = parsed.live,
            houses = parsed.houses,
            tomorrowMissing = parsed.tomorrowMissing,
            note = parsed.note,
        )
        val previous = DesktopStore.loadSnapshot()
        val group = DesktopStore.loadSelection().group
        if (previous != null && group.isNotBlank() &&
            ScheduleMath.scheduleChanged(previous, next, group, next.fetchedAt)
        ) {
            val key = "plan:" + ScheduleMath.planStamp(next, group)
            if (DesktopStore.markNotified(key)) {
                onNotify(
                    "График изменился",
                    details(group, "расписание на сайте обновили"),
                )
            }
        }
        DesktopStore.saveSnapshot(next)
        return next
    }

    private fun checkAlerts() {
        val current = picture ?: return
        val group = selection.group
        if (group.isBlank()) return
        val silent = quiet && ScheduleMath.isQuietHour(now)
        val due = current.dueWarnOffAt
        if (!silent && warn && due != null && DesktopStore.markNotified("warn:$due")) {
            val minutes = ((due - now) / 60_000L).toInt().coerceAtLeast(0)
            val title = if (minutes <= 1) "Свет выключат с минуты на минуту" else "Свет выключат через $minutes мин"
            onNotify(title, details(group, "по графику отключение в ${ScheduleMath.formatDayTime(due, now)}"))
        }
        val display = current.displayOn
        val previous = DesktopStore.lastDisplayOn()
        if (!silent && notifyOn && previous == false && display == true) {
            val near = current.appearAts.firstOrNull { kotlin.math.abs(it - now) < 30 * 60 * 1000L }
            val key = if (near != null) "appear:$near" else "appear-live:$group:${now / 600_000L}"
            if (DesktopStore.markNotified(key)) {
                onNotify("По графику свет включили", details(group, "по графику свет есть"))
            }
        }
        if (display != null) DesktopStore.setLastDisplayOn(display)
    }

    private fun details(group: String, tail: String): String {
        val place = selection.placeLabel
        return buildString {
            append("Группа ")
            append(group)
            if (!place.isNullOrBlank()) {
                append(" · ")
                append(place)
            }
            append(" · ")
            append(tail)
        }
    }
}
