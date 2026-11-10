package ua.`in`.vyshgorod.svitlo.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ua.`in`.vyshgorod.svitlo.data.SvitloRepository
import kotlin.concurrent.thread

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val offAt = intent.getLongExtra(AlarmScheduler.EXTRA_OFF_AT, 0L)
        val onAt = intent.getLongExtra(AlarmScheduler.EXTRA_ON_AT, 0L)
        val placeKey = intent.getStringExtra(AlarmScheduler.EXTRA_PLACE).orEmpty()
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "svitlo-alert") {
            try {
                when (action) {
                    AlarmScheduler.ACTION_WARN -> SvitloRepository.onWarnAlarm(app, offAt, placeKey)
                    AlarmScheduler.ACTION_APPEAR -> SvitloRepository.onAppearAlarm(app, onAt, placeKey)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
