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
import ua.`in`.vyshgorod.svitlo.data.AlertsEnergy
import ua.`in`.vyshgorod.svitlo.data.DtekSchedule
import ua.`in`.vyshgorod.svitlo.data.Yasno
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.Regions
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
            L10n.applyCode(store.languageCode())
            val selection = store.loadSelection()
            val snapshot = store.loadSnapshot()?.takeIf { Regions.same(it.region, store.region()) }
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
                views.setTextViewText(R.id.widget_kicker, L10n.appTitle())
                views.setTextViewText(R.id.widget_status, L10n.chooseGroup())
                views.setTextViewText(R.id.widget_next, L10n.openApp())
                views.setTextViewText(R.id.widget_updated, "vyshgorod.in.ua/svitlo")
            } else {
                val now = System.currentTimeMillis()
                val picture = ScheduleMath.picture(snapshot, selection, now)
                var scaleInk = 0xFF3D6B52.toInt()
                val place = selection.placeLabel
                val kicker = if (place.isNullOrBlank()) L10n.groupTitle(selection.group) else place
                views.setTextViewText(R.id.widget_kicker, kicker)
                when (picture.displayOn) {
                    true -> {
                        scaleInk = 0xFF143024.toInt()
                        paint(views, R.drawable.widget_bg_on, 0xFF0F6B38.toInt(), 0xFF143024.toInt(), 0xFF3D6B52.toInt())
                        views.setTextViewText(R.id.widget_status, L10n.powerOn())
                        val off = picture.nextOffAt
                        views.setTextViewText(
                            R.id.widget_next,
                            if (off == null) L10n.noUpcomingOutage()
                            else L10n.offAtIn(ScheduleMath.formatDayTime(off, now), ScheduleMath.formatDuration(off - now)),
                        )
                    }
                    false -> {
                        scaleInk = 0xFF3C120C.toInt()
                        paint(views, R.drawable.widget_bg_off, 0xFFA33E2C.toInt(), 0xFF3C120C.toInt(), 0xFF7A463C.toInt())
                        views.setTextViewText(R.id.widget_status, L10n.powerOff())
                        val on = picture.nextOnAt
                        val until = picture.offUntil
                        views.setTextViewText(
                            R.id.widget_next,
                            when {
                                on != null -> L10n.onAtIn(ScheduleMath.formatDayTime(on, now), ScheduleMath.formatDuration(on - now))
                                until != null -> L10n.offUntil(ScheduleMath.formatDuration(until - now))
                                else -> L10n.unknownOnTime()
                            },
                        )
                    }
                    null -> {
                        paint(views, R.drawable.widget_bg, 0xFFF6F1E7.toInt(), 0xFFF6F1E7.toInt(), 0xFFB6AB9C.toInt())
                        views.setTextViewText(R.id.widget_status, L10n.noData())
                        views.setTextViewText(R.id.widget_next, L10n.pullToRefresh())
                    }
                }
                val groupLine = L10n.widgetFooter(selection.group, ScheduleMath.formatDayTime(snapshot.fetchedAt, now))
                val footer = when (snapshot.source) {
                    AlertsEnergy.SOURCE -> "$groupLine · alerts.energy"
                    Yasno.SOURCE -> "$groupLine · YASNO"
                    DtekSchedule.SOURCE -> "$groupLine · ДТЕК"
                    else -> groupLine
                }
                views.setTextViewText(R.id.widget_updated, footer)
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
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 22f
            paint.isFakeBoldText = true
            val labels = listOf("00" to 0) + bands(outages).mapNotNull { (start, _, _) ->
                if (start <= 0 || start >= 1440) null
                else {
                    val text = if (start % 60 == 0) "%02d".format(start / 60) else "%02d:%02d".format(start / 60, start % 60)
                    text to start
                }
            } + ("24" to 1440)
            val widths = labels.map { paint.measureText(it.first) }
            val lefts = ScheduleMath.placeHourLabels(labels.map { it.second }, widths, width.toFloat(), 6f)
            labels.forEachIndexed { index, (text, _) ->
                canvas.drawText(text, lefts[index], height - 8f, paint)
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
