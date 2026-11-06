package app.personal.clock.alarm

import app.personal.clock.sys
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.personal.clock.R
import app.personal.clock.Tabs
import app.personal.clock.core.AlarmItem
import app.personal.clock.data.TimerItem
import app.personal.clock.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notifications {
    const val CH_RING = "ring"
    const val CH_INFO = "info"
    const val CH_TIMER = "timers"

    const val ID_RING = 1001
    private const val ID_MISSED = 2000
    private const val ID_SKIP = 2500
    private const val ID_TIMER = 3000
    const val ID_SNOOZED = 3900

    fun ensureChannels(ctx: Context) {
        val nm = ctx.sys<NotificationManager>()
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, "Ringing alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm or timer is ringing"
                setSound(null, null) // the sound is played by the ring service on the alarm stream
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Skipped and missed alarms", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet notices, e.g. \"Skipped: Songkran\". Can be turned off in Settings."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown for timers that are running"
            },
        )
    }

    fun openApp(ctx: Context, tab: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(Tabs.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, 500 + tab, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nm(ctx: Context) = ctx.sys<NotificationManager>()

    private fun smallIcon(ctx: Context): Icon = Icon.createWithResource(ctx, R.drawable.ic_stat_clock)

    // ------------------------------------------------------------------ alarms

    fun postSkipped(ctx: Context, alarm: AlarmItem, date: LocalDate, holidayName: String) {
        val label = alarm.label.ifBlank { "Alarm" }
        val day = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(smallIcon(ctx))
            .setContentTitle("Skipped: $holidayName")
            .setContentText("$label on $day")
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, Tabs.ALARM))
            .build()
        nm(ctx).notify(ID_SKIP + (alarm.id % 400), n)
    }

    fun postMissed(ctx: Context, title: String, text: String, id: Int) {
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(smallIcon(ctx))
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, Tabs.ALARM))
            .build()
        nm(ctx).notify(ID_MISSED + (id % 400), n)
    }

    /** Stays until the alarm rings again or the user cancels the snooze (one notification per alarm). */
    fun postSnoozed(ctx: Context, alarmId: Int, label: String, untilText: String) {
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(smallIcon(ctx))
            .setContentTitle("Snoozed")
            .setContentText("${label.ifBlank { "Alarm" }} rings again at $untilText")
            .setOngoing(true)
            .setContentIntent(openApp(ctx, Tabs.ALARM))
            .addAction(Notification.Action.Builder(null, "Cancel snooze", AlarmScheduler.cancelSnoozePendingIntent(ctx, alarmId)).build())
            .build()
        nm(ctx).notify(ID_SNOOZED + (alarmId % 90), n)
    }

    fun cancelSnoozed(ctx: Context, alarmId: Int) = nm(ctx).cancel(ID_SNOOZED + (alarmId % 90))

    // ------------------------------------------------------------------ timers

    /** Ongoing notification with a system-rendered countdown (no per-second work from this app). */
    fun postTimerRunning(ctx: Context, t: TimerItem) {
        val n = Notification.Builder(ctx, CH_TIMER)
            .setSmallIcon(smallIcon(ctx))
            .setContentTitle(t.label.ifBlank { "Timer" })
            .setContentText("Time remaining")
            .setWhen(t.endWall)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openApp(ctx, Tabs.TIMER))
            .build()
        nm(ctx).notify(ID_TIMER + (t.id % 800), n)
    }

    fun cancelTimer(ctx: Context, id: Int) = nm(ctx).cancel(ID_TIMER + (id % 800))
}
