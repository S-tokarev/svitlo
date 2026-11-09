package ua.`in`.vyshgorod.svitlo.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ua.`in`.vyshgorod.svitlo.data.SvitloRepository
import kotlin.concurrent.thread

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "svitlo-refresh") {
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
