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
            error(L10n.emptySchedule())
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
        val previous = store.loadSnapshot()
        if (previous != null) {
            watched(store).forEach { selection ->
                if (!ScheduleMath.scheduleChanged(previous, snapshot, selection.group, snapshot.fetchedAt)) return@forEach
                val key = "plan:${selection.placeKey}:" + ScheduleMath.planStamp(snapshot, selection.group)
                if (store.markNotified(key)) {
                    Notifier.scheduleChanged(app, selection.group, selection.placeLabel)
                }
            }
        }
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

    fun onWarnAlarm(context: Context, offAt: Long, placeKey: String) {
        if (offAt <= 0L) return
        val app = context.applicationContext
        val store = SvitloStore(app)
        if (!store.warnEnabled()) return
        val selection = placeByKey(store, placeKey) ?: return
        synchronized(gate) {
            if (!store.markNotified("warn:${selection.placeKey}:$offAt")) return
            Notifier.warn(app, selection.group, selection.placeLabel, offAt)
        }
    }

    fun onAppearAlarm(context: Context, onAt: Long, placeKey: String) {
        if (onAt <= 0L) return
        val app = context.applicationContext
        val store = SvitloStore(app)
        if (!store.notifyWhenOn()) return
        val selection = placeByKey(store, placeKey) ?: return
        synchronized(gate) {
            if (store.lastDisplayOn(selection.placeKey) == true) return
            if (!store.markNotified("appear:${selection.placeKey}:$onAt")) return
            val snapshot = store.loadSnapshot()
            val confirmed = snapshot?.let {
                ScheduleMath.picture(it, selection, System.currentTimeMillis()).displayOn == true
            } ?: false
            Notifier.appeared(app, selection.group, selection.placeLabel, confirmed)
            if (confirmed) store.setLastDisplayOn(selection.placeKey, true)
        }
    }

    private fun evaluate(context: Context, store: SvitloStore, snapshot: Snapshot, announceTransitions: Boolean) {
        val places = watched(store)
        if (places.isEmpty()) {
            AlarmScheduler.rescheduleAlerts(context, emptyList(), emptyList())
            return
        }
        val now = System.currentTimeMillis()
        val warns = mutableListOf<AlarmScheduler.PlannedAlert>()
        val appears = mutableListOf<AlarmScheduler.PlannedAlert>()
        places.forEach { selection ->
            val picture = ScheduleMath.picture(snapshot, selection, now)
            if (store.warnEnabled()) {
                picture.warnAts.forEach { warns += AlarmScheduler.PlannedAlert(it, selection.placeKey) }
            }
            if (store.notifyWhenOn()) {
                picture.appearAts.forEach { appears += AlarmScheduler.PlannedAlert(it, selection.placeKey) }
            }
            if (!announceTransitions) return@forEach
            val dueOff = picture.dueWarnOffAt
            if (store.warnEnabled() && dueOff != null && store.markNotified("warn:${selection.placeKey}:$dueOff")) {
                Notifier.warn(context, selection.group, selection.placeLabel, dueOff)
            }
            val current = picture.displayOn
            val previous = store.lastDisplayOn(selection.placeKey)
            if (store.notifyWhenOn() && previous == false && current == true) {
                val near = picture.appearAts.firstOrNull { abs(it - now) < 30 * 60 * 1000L }
                val key = if (near != null) "appear:${selection.placeKey}:$near" else "appear-live:${selection.placeKey}:${now / 600_000L}"
                if (store.markNotified(key)) {
                    Notifier.appeared(context, selection.group, selection.placeLabel, confirmed = true)
                }
            }
            if (current != null) store.setLastDisplayOn(selection.placeKey, current)
        }
        AlarmScheduler.rescheduleAlerts(
            context,
            warns.sortedBy { it.at },
            appears.sortedBy { it.at },
        )
    }

    private fun watched(store: SvitloStore): List<Selection> {
        val places = store.loadPlaces()
        if (places.isNotEmpty()) return places
        val focus = store.loadSelection()
        return if (focus.group.isBlank()) emptyList() else listOf(focus)
    }

    private fun placeByKey(store: SvitloStore, placeKey: String): Selection? {
        val places = watched(store)
        if (placeKey.isNotBlank()) return places.firstOrNull { it.placeKey == placeKey }
        return places.singleOrNull() ?: store.loadSelection().takeIf { it.group.isNotBlank() }
    }

}
