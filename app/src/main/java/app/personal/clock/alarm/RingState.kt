package app.personal.clock.alarm

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

data class RingInfo(
    val kind: Int,
    val id: Int,
    val label: String,
    val startedAt: Long,
    val canSnooze: Boolean,
    val snoozeMinutes: Int,
)

/** Process-wide "what is ringing right now", observed by RingActivity. Dependency-free on purpose. */
object RingState {
    @Volatile
    var current: RingInfo? = null
        private set

    private val listeners = CopyOnWriteArraySet<(RingInfo?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun set(info: RingInfo?) {
        current = info
        main.post { listeners.forEach { it(info) } }
    }

    fun addListener(l: (RingInfo?) -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: (RingInfo?) -> Unit) {
        listeners.remove(l)
    }
}
