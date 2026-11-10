package ua.`in`.vyshgorod.svitlo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import ua.`in`.vyshgorod.svitlo.MainActivity
import ua.`in`.vyshgorod.svitlo.R
import ua.`in`.vyshgorod.svitlo.alert.AlarmScheduler
import ua.`in`.vyshgorod.svitlo.data.Outage
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
            views.setViewVisibility(R.id.widget_scale, View.GONE)

            if (selection.group.isBlank() || snapshot == null) {
                paint(views, R.drawable.widget_bg, 0xFFF6F1E7.toInt(), 0xFFF6F1E7.toInt(), 0xFFB6AB9C.toInt())
                views.setTextViewText(R.id.widget_kicker, "Светло")
                views.setTextViewText(R.id.widget_status, "Выберите группу")
                views.setTextViewText(R.id.widget_next, "Откройте приложение")
                views.setTextViewText(R.id.widget_updated, "vyshgorod.in.ua/svitlo")
            } else {
                val now = System.currentTimeMillis()
                val picture = ScheduleMath.picture(snapshot, selection, now)
                var scaleInk = 0xFF3D6B52.toInt()
                val place = selection.placeLabel
                val kicker = if (place.isNullOrBlank()) "Группа ${selection.group}" else place
                views.setTextViewText(R.id.widget_kicker, kicker)
                when (picture.displayOn) {
                    true -> {
                        scaleInk = 0xFF143024.toInt()
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
                        scaleInk = 0xFF3C120C.toInt()
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
                if (picture.displayOn != null) {
                    views.setViewVisibility(R.id.widget_scale, View.VISIBLE)
                    views.setImageViewBitmap(
                        R.id.widget_scale,
                        dayScale(picture.outagesToday, ScheduleMath.minuteOfDay(now), scaleInk),
                    )
                }
            }
            manager.updateAppWidget(id, views)
        }

        private fun dayScale(outages: List<Outage>, nowMin: Int, labelColor: Int): Bitmap {
            val width = 600
            val height = 96
            val barHeight = 34f
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val clip = Path()
            clip.addRoundRect(RectF(0f, 0f, width.toFloat(), barHeight), 10f, 10f, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            for ((start, end, powerOn) in bands(outages)) {
                paint.color = if (powerOn) 0xFFF4F0E6.toInt() else 0xFF3C120C.toInt()
                canvas.drawRect(width * start / 1440f, 0f, width * end / 1440f, barHeight, paint)
            }
            paint.color = 0xFF1C6E8A.toInt()
            val mark = width * nowMin.coerceIn(0, 1440) / 1440f
            canvas.drawRect(mark - 2f, 0f, mark + 2f, barHeight, paint)
            canvas.restore()
            paint.color = labelColor
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 30f
            paint.isFakeBoldText = true
            var lastRight = 0f
            for ((start, _, _) in bands(outages)) {
                if (start <= 0 || start >= 1440) continue
                val text = if (start % 60 == 0) "%02d".format(start / 60) else "%02d:%02d".format(start / 60, start % 60)
                val textWidth = paint.measureText(text)
                val x = width * start / 1440f
                val left = x - textWidth / 2f
                if (left < lastRight + 8f || left + textWidth > width) continue
                canvas.drawText(text, x, height - 8f, paint)
                lastRight = left + textWidth
            }
            return bitmap
        }

        private fun bands(outages: List<Outage>): List<Triple<Int, Int, Boolean>> {
            val clipped = outages.mapNotNull { outage ->
                val start = outage.startMin.coerceIn(0, 1440)
                val end = outage.endMin.coerceIn(0, 1440)
                if (end > start) start to end else null
            }.sortedBy { it.first }
            val slices = mutableListOf<Triple<Int, Int, Boolean>>()
            var cursor = 0
            for ((start, end) in clipped) {
                if (start > cursor) slices += Triple(cursor, start, true)
                val from = maxOf(start, cursor)
                if (end > from) slices += Triple(from, end, false)
                cursor = maxOf(cursor, end)
            }
            if (cursor < 1440) slices += Triple(cursor, 1440, true)
            return slices
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
