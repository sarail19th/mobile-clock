package app.personal.clock.core

import java.time.DayOfWeek

/**
 * One alarm. [days] is a bitmask: bit 0 = Monday ... bit 6 = Sunday. A mask of 0 means
 * "one time": ring at the next occurrence of the time, then switch off.
 *
 * [ringtoneUri]: null = default alarm tone, "" = silent (vibrate only), otherwise a content URI.
 */
data class AlarmItem(
    val id: Int,
    val label: String,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val skipHolidays: Boolean = true,
) {
    val isOneShot: Boolean get() = days == 0

    fun runsOn(day: DayOfWeek): Boolean = (days and (1 shl (day.value - 1))) != 0

    companion object {
        const val WEEKDAYS = 0b0011111

        fun mask(vararg d: DayOfWeek): Int = d.fold(0) { acc, x -> acc or (1 shl (x.value - 1)) }
    }
}
