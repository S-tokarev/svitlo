package ua.`in`.vyshgorod.svitlo.data

import android.content.Context
import ua.`in`.vyshgorod.svitlo.alert.AlarmScheduler
import ua.`in`.vyshgorod.svitlo.alert.Notifier
import ua.`in`.vyshgorod.svitlo.widget.SvitloWidget
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

object SvitloRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val gate = Any()

    fun refreshAsync(context: Context, onDone: (Result<Snapshot>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            onDone(runCatching { refreshBlocking(app) })
        }
    }

    fun searchAsync(context: Context, query: String, onDone: (Result<AddressSearch.Lookup>) -> Unit) {
        val cache = addressCache(context)
        executor.execute {
            onDone(runCatching { AddressSearch.search(query, cache) })
        }
    }

    fun housesAsync(
        context: Context,
        city: String,
        street: String,
        house: String?,
        onDone: (Result<AddressSearch.Lookup>) -> Unit,
    ) {
        val cache = addressCache(context)
        executor.execute {
            onDone(runCatching { AddressSearch.houses(city, street, house, cache) })
        }
    }

    private fun addressCache(context: Context): File = File(context.applicationContext.cacheDir, "dtek-streets.json")

    fun refreshBlocking(context: Context): Snapshot {
        val parsed = SvitloParser.parse(SvitloClient.fetchPage())
        if (parsed.days.isEmpty()) {
            error("Сайт отдал страницу без графика")
        }
        val snapshot = Snapshot(
            fetchedAt = System.currentTimeMillis(),
            days = parsed.days,
            live = parsed.live,
            houses = parsed.houses,
            tomorrowMissing = parsed.tomorrowMissing,
            note = parsed.note,
        )
        val app = context.applicationContext
        val store = SvitloStore(app)
        store.saveSnapshot(snapshot)
        synchronized(gate) {
            evaluate(app, store, snapshot, announceTransitions = true)
        }
        AlarmScheduler.scheduleRefresh(app)
        SvitloWidget.updateAll(app)
        return snapshot
    }

    fun rescheduleFromCache(context: Context) {
        val app = context.applicationContext
        val store = SvitloStore(app)
        val snapshot = store.loadSnapshot() ?: return
        synchronized(gate) {
            evaluate(app, store, snapshot, announceTransitions = true)
        }
        SvitloWidget.updateAll(app)
    }

    fun onWarnAlarm(context: Context, offAt: Long) {
        if (offAt <= 0L) return
        val app = context.applicationContext
        val store = SvitloStore(app)
        if (!store.warnEnabled()) return
        val selection = store.loadSelection()
        if (selection.group.isBlank()) return
        synchronized(gate) {
            if (!store.markNotified("warn:$offAt")) return
            Notifier.warn(app, selection.group, selection.placeLabel, offAt)
        }
    }

    fun onAppearAlarm(context: Context, onAt: Long) {
        if (onAt <= 0L) return
        val app = context.applicationContext
        val store = SvitloStore(app)
        if (!store.notifyWhenOn()) return
        val selection = store.loadSelection()
        if (selection.group.isBlank()) return
        synchronized(gate) {
            if (store.lastDisplayOn() == true) return
            if (!store.markNotified("appear:$onAt")) return
            val snapshot = store.loadSnapshot()
            val confirmed = snapshot?.let {
                ScheduleMath.picture(it, selection, System.currentTimeMillis()).displayOn == true
            } ?: false
            Notifier.appeared(app, selection.group, selection.placeLabel, confirmed)
            if (confirmed) store.setLastDisplayOn(true)
        }
    }

    private fun evaluate(context: Context, store: SvitloStore, snapshot: Snapshot, announceTransitions: Boolean) {
        val selection = store.loadSelection()
        if (selection.group.isBlank()) {
            AlarmScheduler.rescheduleAlerts(context, emptyPicture(), warn = false, appear = false)
            return
        }
        val now = System.currentTimeMillis()
        val picture = ScheduleMath.picture(snapshot, selection, now)
        AlarmScheduler.rescheduleAlerts(
            context,
            picture,
            warn = store.warnEnabled(),
            appear = store.notifyWhenOn(),
        )
        if (!announceTransitions) return

        val dueOff = picture.dueWarnOffAt
        if (store.warnEnabled() && dueOff != null && store.markNotified("warn:$dueOff")) {
            Notifier.warn(context, selection.group, selection.placeLabel, dueOff)
        }

        val current = picture.displayOn
        val previous = store.lastDisplayOn()
        if (store.notifyWhenOn() && previous == false && current == true) {
            val near = picture.appearAts.firstOrNull { abs(it - now) < 30 * 60 * 1000L }
            val key = if (near != null) "appear:$near" else "appear-live:${selection.group}:${now / 600_000L}"
            if (store.markNotified(key)) {
                Notifier.appeared(context, selection.group, selection.placeLabel, confirmed = true)
            }
        }
        if (current != null) store.setLastDisplayOn(current)
    }

    private fun emptyPicture(): PowerPicture {
        return PowerPicture(
            displayOn = null,
            plannedOn = null,
            houseOn = null,
            nextOffAt = null,
            nextOnAt = null,
            dueWarnOffAt = null,
            warnAts = emptyList(),
            appearAts = emptyList(),
            outagesToday = emptyList(),
            outagesTomorrow = emptyList(),
            scheduleDate = null,
            scheduleLabel = "",
            updatedLabel = "",
            usingHouse = false,
        )
    }
}
