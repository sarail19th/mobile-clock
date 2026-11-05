package app.personal.clock.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which country's public holidays apply. One setting (the alarm time zone) normally decides it; the user can override. */
object HolidayRegion {
    /** [code] = ISO 3166 country code ("TH"), or null when no country applies (e.g. UTC, Etc/GMT+7). */
    data class Resolved(val code: String?, val fromZone: Boolean)

    private fun valid(code: String?): String? {
        val c = code?.trim()?.uppercase() ?: return null
        return if (c.length == 2 && c.all { it in 'A'..'Z' } && c != "ZZ") c else null
    }

    /**
     * [manual] wins when it is a valid country code. Otherwise the country of [zoneId] ([regionOfZone] is the
     * platform lookup, android.icu TimeZone.getRegion; injected so this stays pure and testable).
     */
    fun resolve(manual: String, zoneId: String, regionOfZone: (String) -> String?): Resolved {
        valid(manual)?.let { return Resolved(it, false) }
        return Resolved(valid(runCatching { regionOfZone(zoneId) }.getOrNull()), true)
    }
}

object SkipDates {
    /**
     * The calendar date of [epochMs] in [zone]. Holiday checks must use the date in the SAME zone the alarm rings in:
     * 05:30 in Bangkok on 14 April is still 13 April in UTC, and mixing the two would skip the wrong day.
     */
    fun localDate(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
