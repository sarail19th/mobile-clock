package app.personal.clock.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A day (or range of days) the user wants alarms to skip, e.g. "Annual leave" 20-24 Dec, or "Anniversary" 14 Feb every year.
 * [to] = null means a single day. When [yearly] is true only month and day matter and the range repeats every year
 * (it may cross the new year: 30 Dec - 2 Jan).
 */
data class CustomDay(
    val id: Int,
    val label: String,
    val from: LocalDate,
    val to: LocalDate? = null,
    val yearly: Boolean = false,
) {
    private val lo: LocalDate get() = if (to != null && to.isBefore(from)) to else from
    private val hi: LocalDate get() = if (to != null && to.isAfter(from)) to else from

    fun matches(date: LocalDate): Boolean {
        if (!yearly) return !date.isBefore(lo) && !date.isAfter(hi)
        val span = ChronoUnit.DAYS.between(lo, hi)
        // The range that began this year, or last year (for ranges that cross New Year).
        for (year in intArrayOf(date.year, date.year - 1)) {
            val start = anchor(year)
            val end = start.plusDays(span)
            if (!date.isBefore(start) && !date.isAfter(end)) return true
        }
        return false
    }

    private fun anchor(year: Int): LocalDate {
        val day = minOf(lo.dayOfMonth, YearMonth.of(year, lo.month).lengthOfMonth()) // 29 Feb in a non-leap year -> 28 Feb
        return LocalDate.of(year, lo.month, day)
    }
}
