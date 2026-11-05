package app.personal.clock.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure scheduling maths, kept free of Android types so it can be unit-tested on the JVM. */
object AlarmMath {

    data class Skipped(val date: LocalDate, val at: ZonedDateTime, val name: String)

    /** [skipped] lists holiday occurrences that were passed over before [at]. */
    data class Next(val at: ZonedDateTime, val skipped: List<Skipped>)

    /**
     * Next time [alarm] should ring strictly after [now], in [zone].
     * [holiday] returns the reason to skip a date (holiday or custom-day name) or null; it is only consulted when the
     * alarm skips holidays and/or custom days and is not one-shot. The date it receives is the local date in [zone].
     */
    fun next(
        alarm: AlarmItem,
        now: Instant,
        zone: ZoneId,
        holiday: (LocalDate) -> String?,
    ): Next? {
        val today = now.atZone(zone).toLocalDate()
        val time = LocalTime.of(alarm.hour, alarm.minute)
        val skipped = ArrayList<Skipped>()
        val horizonDays = if (alarm.isOneShot) 1 else 800
        for (i in 0..horizonDays) {
            val date = today.plusDays(i.toLong())
            if (!alarm.isOneShot && !alarm.runsOn(date.dayOfWeek)) continue
            val at = ZonedDateTime.of(date, time, zone)
            if (!at.toInstant().isAfter(now)) continue
            if (!alarm.isOneShot && (alarm.skipHolidays || alarm.skipCustomDays)) {
                val name = holiday(date)
                if (name != null) {
                    skipped += Skipped(date, at, name)
                    continue
                }
            }
            return Next(at, skipped)
        }
        return null
    }
}
