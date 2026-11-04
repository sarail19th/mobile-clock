package app.personal.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.personal.clock.holiday.HolidayChecker

/** Re-arms alarms after anything that makes AlarmManager forget or re-evaluate them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                Notifications.ensureChannels(ctx)
                HolidayChecker.clearCache()
                val restarted = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
                if (restarted) TimerController.rebaseAfterRestart(ctx)
                AlarmScheduler.rescheduleAll(ctx)
            }
        }
    }
}
