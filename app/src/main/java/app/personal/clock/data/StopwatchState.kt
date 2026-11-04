package app.personal.clock.data

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stopwatch persisted in [Store] so it survives the app being closed. Time comes from
 * SystemClock.elapsedRealtime(), which keeps counting with the screen off.
 * (It resets on reboot, which also clears a running stopwatch - see [load].)
 */
data class StopwatchState(
    val running: Boolean = false,
    /** elapsedRealtime when the current run segment started. */
    val startedAt: Long = 0L,
    /** time accumulated before the current segment. */
    val accumulatedMs: Long = 0L,
    /** cumulative elapsed time at each lap, oldest first. */
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long =
        accumulatedMs + if (running) (now - startedAt).coerceAtLeast(0L) else 0L

    fun start(now: Long = SystemClock.elapsedRealtime()) = if (running) this else copy(running = true, startedAt = now)

    fun stop(now: Long = SystemClock.elapsedRealtime()) =
        if (!running) this else copy(running = false, accumulatedMs = elapsed(now), startedAt = 0L)

    fun lap(now: Long = SystemClock.elapsedRealtime()) =
        if (!running) this else copy(laps = laps + elapsed(now))

    fun reset() = StopwatchState()

    fun toJson(): String = JSONObject()
        .put("running", running).put("startedAt", startedAt).put("accumulatedMs", accumulatedMs)
        .put("laps", JSONArray(laps)).toString()

    companion object {
        fun load(store: Store): StopwatchState {
            val raw = store.stopwatchRaw() ?: return StopwatchState()
            return runCatching {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("laps") ?: JSONArray()
                val s = StopwatchState(
                    running = o.optBoolean("running"),
                    startedAt = o.optLong("startedAt"),
                    accumulatedMs = o.optLong("accumulatedMs"),
                    laps = (0 until arr.length()).map { arr.getLong(it) },
                )
                // elapsedRealtime restarts at boot: a "running" state from before a reboot is stale.
                if (s.running && s.startedAt > SystemClock.elapsedRealtime()) s.copy(running = false, startedAt = 0L) else s
            }.getOrDefault(StopwatchState())
        }

        fun save(store: Store, s: StopwatchState) = store.saveStopwatchRaw(s.toJson())
    }
}
