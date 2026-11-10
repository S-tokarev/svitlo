package ua.`in`.vyshgorod.svitlo.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.widget.SvitloWidget

object AlarmScheduler {
    const val ACTION_REFRESH = "ua.in.vyshgorod.svitlo.REFRESH"
    const val ACTION_WARN = "ua.in.vyshgorod.svitlo.WARN"
    const val ACTION_APPEAR = "ua.in.vyshgorod.svitlo.APPEAR"
    const val ACTION_WIDGET = "ua.in.vyshgorod.svitlo.WIDGET"
    const val EXTRA_OFF_AT = "offAt"
    const val EXTRA_ON_AT = "onAt"
    const val EXTRA_PLACE = "place"

    private const val REFRESH_MS = 15 * 60 * 1000L
    private const val MAX_ALERTS = 24

    data class PlannedAlert(val at: Long, val placeKey: String)

    fun scheduleRefresh(context: Context, delayMs: Long = REFRESH_MS) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        set(alarmManager, System.currentTimeMillis() + delayMs, refreshIntent(context))
    }

    /** Recomputes the countdown at the next minute. Does not download the schedule. */
    fun scheduleWidgetTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val nextMinute = (now / 60_000L + 1L) * 60_000L + 500L
        val pending = widgetIntent(context)
        try {
            set(alarmManager, nextMinute, pending)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pending)
        }
    }

    fun cancelWidgetTick(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(widgetIntent(context))
    }

    fun rescheduleAlerts(
        context: Context,
        warns: List<PlannedAlert>,
        appears: List<PlannedAlert>,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (index in 0 until MAX_ALERTS) {
            alarmManager.cancel(warnIntent(context, index, 0L, ""))
            alarmManager.cancel(appearIntent(context, index, 0L, ""))
        }
        warns.take(MAX_ALERTS).forEachIndexed { index, alert ->
            val offAt = alert.at + ScheduleMath.WARN_BEFORE_MS
            set(alarmManager, alert.at, warnIntent(context, index, offAt, alert.placeKey))
        }
        appears.take(MAX_ALERTS).forEachIndexed { index, alert ->
            set(alarmManager, alert.at, appearIntent(context, index, alert.at, alert.placeKey))
        }
    }

    private fun set(alarmManager: AlarmManager, at: Long, pending: PendingIntent) {
        val trigger = at.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshReceiver::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 1, intent, flags())
    }

    private fun widgetIntent(context: Context): PendingIntent {
        val intent = Intent(context, SvitloWidget::class.java).setAction(ACTION_WIDGET)
        return PendingIntent.getBroadcast(context, 2, intent, flags())
    }

    private fun warnIntent(context: Context, index: Int, offAt: Long, placeKey: String): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction(ACTION_WARN)
            .putExtra(EXTRA_OFF_AT, offAt)
            .putExtra(EXTRA_PLACE, placeKey)
        return PendingIntent.getBroadcast(context, 1_000 + index, intent, flags())
    }

    private fun appearIntent(context: Context, index: Int, onAt: Long, placeKey: String): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction(ACTION_APPEAR)
            .putExtra(EXTRA_ON_AT, onAt)
            .putExtra(EXTRA_PLACE, placeKey)
        return PendingIntent.getBroadcast(context, 2_000 + index, intent, flags())
    }

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
