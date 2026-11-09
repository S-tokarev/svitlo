package ua.`in`.vyshgorod.svitlo.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ua.`in`.vyshgorod.svitlo.data.SvitloRepository
import kotlin.concurrent.thread

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        SvitloRepository.rescheduleFromCache(app)
        AlarmScheduler.scheduleRefresh(app, delayMs = 15_000L)
        val pending = goAsync()
        thread(name = "svitlo-boot") {
            try {
                SvitloRepository.refreshBlocking(app)
            } catch (_: Exception) {
                AlarmScheduler.scheduleRefresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
