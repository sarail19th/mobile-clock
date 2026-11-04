package app.personal.clock.holiday

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.personal.clock.data.Store
import org.json.JSONObject
import java.time.LocalDate

/**
 * Thai public-holiday lookup, two layers (checked in this order, per calendar year):
 *
 *  1. The phone's own calendar provider (already synced, so it works offline): all-day events in any
 *     calendar whose display name / name / account matches the configured patterns.
 *  2. The bundled assets/th_holidays.json list, used when the calendar permission is denied, no matching
 *     calendar exists, or the calendar has no events at all for that year.
 *
 * Results are cached per year for a few minutes; pass fresh = true at alarm fire time.
 */
object HolidayChecker {

    enum class Source(val label: String) {
        CALENDAR("Calendar"),
        BUILTIN("Built-in list"),
        NONE("No data"),
    }

    data class Result(val name: String?, val source: Source) {
        val isHoliday: Boolean get() = name != null
    }

    private class YearData(val holidays: Map<LocalDate, String>, val source: Source, val loadedAt: Long)

    private const val TTL_MS = 5 * 60_000L
    private const val JULIAN_EPOCH_OFFSET = 2440588L // Julian day number of 1970-01-01 as used by CalendarContract

    private val cache = HashMap<Int, YearData>()
    private var builtin: Map<LocalDate, String>? = null

    fun hasCalendarPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Holiday name for [date], or null. */
    fun check(ctx: Context, date: LocalDate, fresh: Boolean = false): Result {
        val data = yearData(ctx, date.year, fresh)
        return Result(data.holidays[date], data.source)
    }

    /** Function form for AlarmMath. */
    fun lookup(ctx: Context): (LocalDate) -> String? = { check(ctx, it).name }

    /** Which source is currently deciding for [year] (for the Settings screen). */
    fun sourceFor(ctx: Context, year: Int, fresh: Boolean = true): Source = yearData(ctx, year, fresh).source

    fun clearCache() = synchronized(this) { cache.clear() }

    @Synchronized
    private fun yearData(ctx: Context, year: Int, fresh: Boolean): YearData {
        val now = System.currentTimeMillis()
        cache[year]?.let { if (!fresh && now - it.loadedAt < TTL_MS) return it }

        val app = ctx.applicationContext
        val fromCalendar = if (hasCalendarPermission(app)) loadFromCalendar(app, year) else emptyMap()
        val data = when {
            fromCalendar.isNotEmpty() -> YearData(fromCalendar, Source.CALENDAR, now)
            else -> {
                val fallback = builtinHolidays(app).filterKeys { it.year == year }
                if (fallback.isNotEmpty()) YearData(fallback, Source.BUILTIN, now)
                else YearData(emptyMap(), Source.NONE, now)
            }
        }
        cache[year] = data
        runCatching { Store.get(app).saveLastHolidaySource(data.source.label) }
        return data
    }

    // ------------------------------------------------------------------ layer 1: calendar provider

    private fun loadFromCalendar(ctx: Context, year: Int): Map<LocalDate, String> = try {
        val patterns = Store.get(ctx).settings().calendarPatterns().map { it.lowercase() }
        if (patterns.isEmpty()) emptyMap() else queryCalendar(ctx, year, patterns)
    } catch (e: Exception) { // SecurityException, provider missing, bad column, ...
        emptyMap()
    }

    private fun queryCalendar(ctx: Context, year: Int, patterns: List<String>): Map<LocalDate, String> {
        val cr = ctx.contentResolver

        // 1. Which calendars count as the Thai holiday calendar?
        val ids = HashSet<Long>()
        val calProjection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        cr.query(CalendarContract.Calendars.CONTENT_URI, calProjection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val haystack = (1..3).joinToString("\u0000") { c.getString(it).orEmpty() }.lowercase()
                if (patterns.any { haystack.contains(it) }) ids += c.getLong(0)
            }
        }
        if (ids.isEmpty()) return emptyMap()

        // 2. All-day instances for the whole year, by local day number (all-day events are timezone-free).
        val startDay = LocalDate.of(year, 1, 1).toEpochDay() + JULIAN_EPOCH_OFFSET
        val endDay = LocalDate.of(year, 12, 31).toEpochDay() + JULIAN_EPOCH_OFFSET
        val uri = CalendarContract.Instances.CONTENT_BY_DAY_URI.buildUpon()
            .appendPath(startDay.toString()).appendPath(endDay.toString()).build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.END_DAY,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val out = HashMap<LocalDate, String>()
        cr.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(1) != 1) continue
                if (c.getLong(4) !in ids) continue
                val title = c.getString(0)?.trim().orEmpty().ifEmpty { "Holiday" }
                val from = c.getLong(2)
                val to = maxOf(from, c.getLong(3))
                for (day in from..minOf(to, from + 31)) {
                    val date = LocalDate.ofEpochDay(day - JULIAN_EPOCH_OFFSET)
                    if (date.year != year) continue
                    val prev = out[date]
                    out[date] = if (prev == null || prev == title) title else "$prev / $title"
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------------ layer 2: bundled list

    private fun builtinHolidays(ctx: Context): Map<LocalDate, String> {
        builtin?.let { return it }
        val map = HashMap<LocalDate, String>()
        try {
            val text = ctx.assets.open("th_holidays.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).getJSONArray("holidays")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                runCatching { map[LocalDate.parse(o.getString("date"))] = o.getString("name") }
            }
        } catch (e: Exception) {
            // Missing/corrupt asset: behave as "no data" rather than crash an alarm.
        }
        builtin = map
        return map
    }

    /** Years covered by the bundled list (for the Settings screen reminder). */
    fun builtinYears(ctx: Context): List<Int> = builtinHolidays(ctx.applicationContext).keys.map { it.year }.distinct().sorted()
}
