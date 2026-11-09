package ua.`in`.vyshgorod.svitlo

import android.app.Application
import ua.`in`.vyshgorod.svitlo.alert.AlarmScheduler
import ua.`in`.vyshgorod.svitlo.alert.Notifier
import ua.`in`.vyshgorod.svitlo.widget.SvitloWidget

class SvitloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensure(this)
        AlarmScheduler.scheduleRefresh(this)
        SvitloWidget.updateAll(this)
    }
}
