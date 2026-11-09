package ua.`in`.vyshgorod.svitlo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ua.`in`.vyshgorod.svitlo.MainActivity
import ua.`in`.vyshgorod.svitlo.R
import ua.`in`.vyshgorod.svitlo.alert.AlarmScheduler
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.data.SvitloStore

class SvitloWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmScheduler.ACTION_WIDGET) {
            updateAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) {
            AlarmScheduler.cancelWidgetTick(context)
            return
        }
        ids.forEach { id -> updateOne(context, manager, id) }
        AlarmScheduler.scheduleWidgetTick(context)
    }

    override fun onDisabled(context: Context) {
        AlarmScheduler.cancelWidgetTick(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SvitloWidget::class.java))
            if (ids.isEmpty()) {
                AlarmScheduler.cancelWidgetTick(context)
                return
            }
            ids.forEach { id -> updateOne(context, manager, id) }
            AlarmScheduler.scheduleWidgetTick(context)
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_svitlo)
            val store = SvitloStore(context)
            val selection = store.loadSelection()
            val snapshot = store.loadSnapshot()
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (selection.group.isBlank() || snapshot == null) {
                paint(views, R.drawable.widget_bg, 0xFFF6F1E7.toInt(), 0xFFF6F1E7.toInt(), 0xFFB6AB9C.toInt())
                views.setTextViewText(R.id.widget_kicker, "Светло")
                views.setTextViewText(R.id.widget_status, "Выберите группу")
                views.setTextViewText(R.id.widget_next, "Откройте приложение")
                views.setTextViewText(R.id.widget_updated, "График ДТЕК · alerts.energy")
            } else {
                val now = System.currentTimeMillis()
                val picture = ScheduleMath.picture(snapshot, selection, now)
                val place = selection.placeLabel
                val kicker = if (place.isNullOrBlank()) "Группа ${selection.group}" else place
                views.setTextViewText(R.id.widget_kicker, kicker)
                when (picture.displayOn) {
                    true -> {
                        paint(views, R.drawable.widget_bg_on, 0xFF0F6B38.toInt(), 0xFF143024.toInt(), 0xFF3D6B52.toInt())
                        views.setTextViewText(R.id.widget_status, "Свет есть")
                        val off = picture.nextOffAt
                        views.setTextViewText(
                            R.id.widget_next,
                            if (off == null) "Отключений по графику нет"
                            else "По графику выключат в ${ScheduleMath.formatDayTime(off, now)} · через ${ScheduleMath.formatDuration(off - now)}",
                        )
                    }
                    false -> {
                        paint(views, R.drawable.widget_bg_off, 0xFFA33E2C.toInt(), 0xFF3C120C.toInt(), 0xFF7A463C.toInt())
                        views.setTextViewText(R.id.widget_status, "Света нет")
                        val on = picture.nextOnAt
                        views.setTextViewText(
                            R.id.widget_next,
                            if (on == null) "Время включения неизвестно"
                            else "По графику включат в ${ScheduleMath.formatDayTime(on, now)} · через ${ScheduleMath.formatDuration(on - now)}",
                        )
                    }
                    null -> {
                        paint(views, R.drawable.widget_bg, 0xFFF6F1E7.toInt(), 0xFFF6F1E7.toInt(), 0xFFB6AB9C.toInt())
                        views.setTextViewText(R.id.widget_status, "Нет данных")
                        views.setTextViewText(R.id.widget_next, "Потяните обновление в приложении")
                    }
                }
                val groupLine = "Группа ${selection.group} · обновлено ${ScheduleMath.formatDayTime(snapshot.fetchedAt, now)}"
                views.setTextViewText(R.id.widget_updated, groupLine)
            }
            manager.updateAppWidget(id, views)
        }

        private fun paint(views: RemoteViews, background: Int, status: Int, body: Int, muted: Int) {
            views.setInt(R.id.widget_root, "setBackgroundResource", background)
            views.setTextColor(R.id.widget_kicker, muted)
            views.setTextColor(R.id.widget_status, status)
            views.setTextColor(R.id.widget_next, body)
            views.setTextColor(R.id.widget_updated, muted)
        }
    }
}
