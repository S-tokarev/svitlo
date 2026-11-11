package ua.`in`.vyshgorod.svitlo.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ua.`in`.vyshgorod.svitlo.MainActivity
import ua.`in`.vyshgorod.svitlo.R
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.data.SvitloStore

object Notifier {
    private const val CHANNEL_ID = "power"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            L10n.channelName(),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = L10n.channelDescription()
        }
        manager.createNotificationChannel(channel)
    }

    fun warn(context: Context, group: String, place: String?, offAt: Long) {
        val now = System.currentTimeMillis()
        val minutes = ((offAt - now) / 60_000L).toInt().coerceAtLeast(0)
        val whenText = ScheduleMath.formatDayTime(offAt, now)
        show(context, 100, L10n.warnTitle(minutes), details(group, place, L10n.offDetail(whenText)))
    }

    fun scheduleChanged(context: Context, group: String, place: String?) {
        show(context, 102, L10n.scheduleChanged(), details(group, place, L10n.scheduleChangedTail()))
    }

    fun appeared(context: Context, group: String, place: String?, confirmed: Boolean) {
        val title = if (confirmed) L10n.powerReturned() else L10n.powerShouldReturn()
        val tail = if (confirmed) L10n.powerOnTail() else L10n.waitForPower()
        show(context, 101, title, details(group, place, tail))
    }

    private fun details(group: String, place: String?, tail: String): String = L10n.notice(group, place, tail)

    private fun show(context: Context, id: Int, title: String, text: String) {
        if (SvitloStore(context).quietNight() && ScheduleMath.isQuietHour(System.currentTimeMillis())) return
        ensure(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }
}
