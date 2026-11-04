package app.personal.clock

import android.app.Application
import app.personal.clock.alarm.Notifications

class ClockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Channels are cheap and idempotent; everything else (seeding, rescheduling) happens in
        // MainActivity / BootReceiver so that firing an alarm never triggers extra work here.
        Notifications.ensureChannels(this)
    }
}
