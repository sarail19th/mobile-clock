package app.personal.clock.alarm

import app.personal.clock.sys
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.personal.clock.Tabs
import app.personal.clock.core.AlarmItem
import app.personal.clock.core.AlarmMath
import app.personal.clock.data.Store
import app.personal.clock.holiday.HolidayChecker
import app.personal.clock.widget.ClockWidgetProvider
import java.time.Instant

/**
 * Chain scheduling: only the NEXT occurrence of each alarm is registered with AlarmManager.
 * When it fires (or is skipped) the receiver computes and registers the one after.
 *
 * The registered time already skips holidays known right now, so the system's own "next alarm"
 * indicator is truthful; the receiver re-checks at fire time to catch holidays that were added later.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "app.personal.clock.action.ALARM_FIRE"
    const val ACTION_SNOOZE_FIRE = "app.personal.clock.action.ALARM_SNOOZE_FIRE"
    const val ACTION_SKIP_NOTICE = "app.personal.clock.action.ALARM_SKIP_NOTICE"
    const val ACTION_TIMER_FIRE = "app.personal.clock.action.TIMER_FIRE"

    const val EXTRA_ID = "id"
    const val EXTRA_TRIGGER_AT = "trigger_at"
    const val EXTRA_HOLIDAY = "holiday"

    // PendingIntent request-code ranges (alarm ids and timer ids share one counter, so ranges can't collide)
    private const val RC_FIRE = 0
    private const val RC_SNOOZE = 1_000_000
    private const val RC_SKIP = 2_000_000
    private const val RC_TIMER = 3_000_000

    data class NextInfo(val alarm: AlarmItem, val at: java.time.ZonedDateTime, val skipped: List<AlarmMath.Skipped>)

    private fun am(ctx: Context): AlarmManager = ctx.sys<AlarmManager>()

    private fun pi(ctx: Context, action: String, requestCode: Int, configure: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action).apply(configure)
        return PendingIntent.getBroadcast(
            ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(ctx: Context): Boolean = am(ctx).canScheduleExactAlarms()

    /** Registers (or clears) the next occurrence of [alarm]. Safe to call repeatedly. */
    fun schedule(ctx: Context, alarm: AlarmItem) {
        val alarmManager = am(ctx)
        alarmManager.cancel(pi(ctx, ACTION_SKIP_NOTICE, RC_SKIP + alarm.id))
        val firePi = pi(ctx, ACTION_FIRE, RC_FIRE + alarm.id) { putExtra(EXTRA_ID, alarm.id) }
        if (!alarm.enabled) {
            alarmManager.cancel(firePi)
            return
        }
        val settings = Store.get(ctx).settings()
        val next = AlarmMath.next(alarm, Instant.now(), settings.zone(), HolidayChecker.lookup(ctx))
        if (next == null) {
            alarmManager.cancel(firePi)
            return
        }
        val triggerAt = next.at.toInstant().toEpochMilli()
        val fire = pi(ctx, ACTION_FIRE, RC_FIRE + alarm.id) {
            putExtra(EXTRA_ID, alarm.id)
            putExtra(EXTRA_TRIGGER_AT, triggerAt)
        }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, Notifications.openApp(ctx, Tabs.ALARM)),
            fire,
        )

        // A quiet "Skipped: <holiday>" notice at the moment the skipped alarm would have rung.
        next.skipped.firstOrNull()?.let { s ->
            val notice = pi(ctx, ACTION_SKIP_NOTICE, RC_SKIP + alarm.id) {
                putExtra(EXTRA_ID, alarm.id)
                putExtra(EXTRA_TRIGGER_AT, s.at.toInstant().toEpochMilli())
                putExtra(EXTRA_HOLIDAY, s.name)
            }
            setExactCompat(ctx, AlarmManager.RTC_WAKEUP, s.at.toInstant().toEpochMilli(), notice)
        }
    }

    fun cancel(ctx: Context, alarmId: Int) {
        val alarmManager = am(ctx)
        alarmManager.cancel(pi(ctx, ACTION_FIRE, RC_FIRE + alarmId))
        alarmManager.cancel(pi(ctx, ACTION_SNOOZE_FIRE, RC_SNOOZE + alarmId))
        alarmManager.cancel(pi(ctx, ACTION_SKIP_NOTICE, RC_SKIP + alarmId))
    }

    fun cancelSnooze(ctx: Context, alarmId: Int) {
        am(ctx).cancel(pi(ctx, ACTION_SNOOZE_FIRE, RC_SNOOZE + alarmId))
    }

    /** One-shot re-ring [minutes] from now. Returns the trigger time in epoch millis. */
    fun scheduleSnooze(ctx: Context, alarmId: Int, minutes: Int): Long {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val p = pi(ctx, ACTION_SNOOZE_FIRE, RC_SNOOZE + alarmId) {
            putExtra(EXTRA_ID, alarmId)
            putExtra(EXTRA_TRIGGER_AT, at)
        }
        am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifications.openApp(ctx, Tabs.ALARM)), p)
        return at
    }

    fun timerPendingIntent(ctx: Context, timerId: Int): PendingIntent =
        pi(ctx, ACTION_TIMER_FIRE, RC_TIMER + timerId) { putExtra(EXTRA_ID, timerId) }

    /** Exact, Doze-proof one-shot that falls back to the inexact variant if exact alarms are denied. */
    fun setExactCompat(ctx: Context, type: Int, triggerAt: Long, pi: PendingIntent) {
        val alarmManager = am(ctx)
        try {
            if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(type, triggerAt, pi)
            else alarmManager.setAndAllowWhileIdle(type, triggerAt, pi)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(type, triggerAt, pi)
        }
    }

    /** Re-registers everything. Called on app start, boot, package update, time/zone change and settings edits. */
    fun rescheduleAll(ctx: Context) {
        val store = Store.get(ctx)
        store.alarms().forEach { schedule(ctx, it) }
        ClockWidgetProvider.updateAll(ctx)
    }

    /** The alarm that will actually ring next (holiday-aware), for the Alarm tab header and the widget. */
    fun nextOverall(ctx: Context): NextInfo? {
        val store = Store.get(ctx)
        val zone = store.settings().zone()
        val lookup = HolidayChecker.lookup(ctx)
        val now = Instant.now()
        return store.alarms()
            .filter { it.enabled }
            .mapNotNull { a -> AlarmMath.next(a, now, zone, lookup)?.let { NextInfo(a, it.at, it.skipped) } }
            .minByOrNull { it.at.toInstant() }
    }
}
