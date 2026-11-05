package app.personal.clock.alarm

import app.personal.clock.sys
import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import app.personal.clock.data.Store
import app.personal.clock.data.TimerItem

/**
 * Countdown timers. A running timer is one exact, Doze-proof alarm on the elapsedRealtime clock
 * (so wall-clock changes do not move it) plus a system-rendered countdown notification.
 */
object TimerController {

    fun add(ctx: Context, durationMs: Long, label: String, startNow: Boolean = true): TimerItem {
        val store = Store.get(ctx)
        val t = store.upsertTimer(TimerItem(id = 0, label = label, durationMs = durationMs))
        return if (startNow) start(ctx, t.id) ?: t else t
    }

    fun start(ctx: Context, id: Int): TimerItem? {
        val store = Store.get(ctx)
        val t = store.timer(id) ?: return null
        val remaining = if (t.state == TimerItem.PAUSED) t.remainingMs else t.durationMs
        val nowE = SystemClock.elapsedRealtime()
        val running = t.copy(
            state = TimerItem.RUNNING,
            remainingMs = remaining,
            endElapsed = nowE + remaining,
            endWall = System.currentTimeMillis() + remaining,
        )
        store.upsertTimer(running)
        arm(ctx, running)
        return running
    }

    fun pause(ctx: Context, id: Int) {
        val store = Store.get(ctx)
        val t = store.timer(id) ?: return
        if (t.state != TimerItem.RUNNING) return
        val left = t.remaining(SystemClock.elapsedRealtime())
        store.upsertTimer(t.copy(state = TimerItem.PAUSED, remainingMs = left, endElapsed = 0, endWall = 0))
        disarm(ctx, id)
    }

    fun reset(ctx: Context, id: Int) {
        val store = Store.get(ctx)
        val t = store.timer(id) ?: return
        store.upsertTimer(t.copy(state = TimerItem.IDLE, remainingMs = t.durationMs, endElapsed = 0, endWall = 0))
        disarm(ctx, id)
    }

    /** Changes a saved timer's length and label. Saving always resets it to Ready (a running countdown is stopped). */
    fun update(ctx: Context, id: Int, durationMs: Long, label: String) {
        val store = Store.get(ctx)
        val t = store.timer(id) ?: return
        store.upsertTimer(t.copy(label = label, durationMs = durationMs, state = TimerItem.IDLE, remainingMs = durationMs, endElapsed = 0, endWall = 0))
        disarm(ctx, id)
    }

    fun delete(ctx: Context, id: Int) {
        disarm(ctx, id)
        Store.get(ctx).deleteTimer(id)
    }

    /** The alarm went off. */
    fun onFired(ctx: Context, id: Int) {
        val store = Store.get(ctx)
        val t = store.timer(id) ?: return
        if (t.state != TimerItem.RUNNING) return
        store.upsertTimer(t.copy(state = TimerItem.IDLE, remainingMs = t.durationMs, endElapsed = 0, endWall = 0))
        Notifications.cancelTimer(ctx, id)
        RingService.start(ctx, RingService.KIND_TIMER, id)
    }

    /**
     * After a reboot or app update the elapsedRealtime clock restarted (reboot) or alarms were dropped (update),
     * so rebuild every running timer from its stored wall-clock end time.
     */
    fun rebaseAfterRestart(ctx: Context) {
        val store = Store.get(ctx)
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        store.timers().filter { it.state == TimerItem.RUNNING }.forEach { t ->
            val left = t.endWall - wallNow
            if (left <= 0) {
                onFired(ctx, t.id)
            } else {
                val rebased = t.copy(endElapsed = elapsedNow + left)
                store.upsertTimer(rebased)
                arm(ctx, rebased)
            }
        }
    }

    private fun arm(ctx: Context, t: TimerItem) {
        val pi = AlarmScheduler.timerPendingIntent(ctx, t.id)
        AlarmScheduler.setExactCompat(ctx, AlarmManager.ELAPSED_REALTIME_WAKEUP, t.endElapsed, pi)
        Notifications.postTimerRunning(ctx, t)
    }

    private fun disarm(ctx: Context, id: Int) {
        ctx.sys<AlarmManager>().cancel(AlarmScheduler.timerPendingIntent(ctx, id))
        Notifications.cancelTimer(ctx, id)
    }
}
