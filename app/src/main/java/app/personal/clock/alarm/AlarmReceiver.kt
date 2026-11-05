package app.personal.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.personal.clock.data.Store
import app.personal.clock.core.SkipDates
import app.personal.clock.holiday.SkipRules
import app.personal.clock.widget.ClockWidgetProvider
import java.time.Instant

/** Entry point for everything AlarmManager delivers. Kept short: it runs on the main thread. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> onFire(ctx, intent)
            AlarmScheduler.ACTION_SNOOZE_FIRE -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                if (Store.get(ctx).alarm(id) != null) RingService.start(ctx, RingService.KIND_ALARM, id)
            }
            AlarmScheduler.ACTION_SKIP_NOTICE -> onSkipNotice(ctx, intent)
            AlarmScheduler.ACTION_WIDGET_TICK -> ClockWidgetProvider.updateAll(ctx) // also re-arms the next minute
            AlarmScheduler.ACTION_TIMER_FIRE -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                TimerController.onFired(ctx, id)
            }
        }
    }

    private fun onFire(ctx: Context, intent: Intent) {
        val store = Store.get(ctx)
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = store.alarm(id) ?: return
        if (!alarm.enabled) return

        val settings = store.settings()
        val triggerAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        // The date is taken in the zone the alarm rings in, never the phone's or UTC's.
        val date = SkipDates.localDate(triggerAt, settings.zone())

        // Fire-time check: catches holidays that appeared (e.g. calendar sync) after this alarm was scheduled.
        val skipName = if (!alarm.isOneShot) SkipRules.reason(ctx, alarm, date, fresh = true) else null

        try {
            if (skipName != null) {
                if (settings.notifyOnSkip) Notifications.postSkipped(ctx, alarm, date, skipName)
            } else {
                RingService.start(ctx, RingService.KIND_ALARM, id)
            }
        } finally {
            // Chain: always register the following occurrence, even if starting the ring failed.
            var current = alarm
            if (alarm.isOneShot) {
                current = alarm.copy(enabled = false)
                store.upsertAlarm(current)
            }
            AlarmScheduler.schedule(ctx, current)
            ClockWidgetProvider.updateAll(ctx)
        }
    }

    private fun onSkipNotice(ctx: Context, intent: Intent) {
        val store = Store.get(ctx)
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = store.alarm(id) ?: return
        val name = intent.getStringExtra(AlarmScheduler.EXTRA_HOLIDAY) ?: return
        val at = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        val date = SkipDates.localDate(at, store.settings().zone())
        if (alarm.enabled && store.settings().notifyOnSkip) Notifications.postSkipped(ctx, alarm, date, name)
        // Re-run the chain so a following skipped day (e.g. Songkran day 2 and 3) also gets its notice.
        AlarmScheduler.schedule(ctx, alarm)
        ClockWidgetProvider.updateAll(ctx)
    }
}
