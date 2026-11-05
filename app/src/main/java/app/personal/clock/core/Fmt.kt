package app.personal.clock.core

import kotlin.math.abs

/** Small, pure formatting helpers (unit-tested). */
object Fmt {

    /** "America/New_York" -> "New York", "Asia/Bangkok" -> "Bangkok". */
    fun cityName(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

    /** "UTC+7", "UTC+5:30", "UTC-3:30", "UTC". */
    fun utcOffset(totalSeconds: Int): String {
        if (totalSeconds == 0) return "UTC"
        val sign = if (totalSeconds < 0) "-" else "+"
        val a = abs(totalSeconds)
        val h = a / 3600
        val m = (a % 3600) / 60
        return "UTC$sign$h" + if (m != 0) ":" + m.toString().padStart(2, '0') else ""
    }

    /** Difference between two UTC offsets as "+2 h", "\u22127 h", "+5:30 h", "same time". */
    fun hoursDiff(diffSeconds: Int): String {
        if (diffSeconds == 0) return "same time"
        val sign = if (diffSeconds < 0) "\u2212" else "+"
        val a = abs(diffSeconds)
        val h = a / 3600
        val m = (a % 3600) / 60
        return sign + h + (if (m != 0) ":" + m.toString().padStart(2, '0') else "") + " h"
    }

    /** "3 h 20 m", "2 d 4 h", "45 m", "< 1 m". Rounds up to the next whole minute. */
    fun until(ms: Long): String {
        val totalMin = ((ms.coerceAtLeast(0) + 59_999L) / 60_000L).toInt()
        if (totalMin <= 0) return "< 1 m"
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return when {
            d > 0 -> "$d d $h h"
            h > 0 -> "$h h $m m"
            else -> "$m m"
        }
    }

    /** "05:30" / "1:05:30" for timers. */
    fun clockHms(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999L) / 1000L
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val mm = m.toString().padStart(2, '0')
        val ss = sec.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }

    /** Stopwatch: "01:23.45" / "1:01:23.45" (truncates, never rounds up). */
    fun stopwatch(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val cs = (t % 1000) / 10
        val s = t / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val mm = m.toString().padStart(2, '0')
        val ss = sec.toString().padStart(2, '0')
        val cc = cs.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss.$cc" else "$mm:$ss.$cc"
    }
}
