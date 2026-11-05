package app.personal.clock.holiday

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.personal.clock.core.HolidayRegion
import app.personal.clock.data.Store
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * Public-holiday lookup for any country, fully offline. For a given calendar year it uses, in this order:
 *
 *  1. Holiday calendars already synced on the phone (the ones the user ticked in Settings; or, if none are ticked,
 *     calendars that look like the holiday calendar of the current region, e.g. "Holidays in Japan").
 *  2. The built-in list for the region, if the app ships one (Thailand today: assets/th_holidays.json; add a country
 *     by dropping assets/holidays_xx.json in the same format).
 *
 * The region is the country of the alarm's time zone unless the user picked one. Results are cached per region+year
 * for a few minutes; pass fresh = true at alarm fire time.
 */
object HolidayChecker {

    enum class Source(val label: String) {
        CALENDAR("Calendar"),
        BUILTIN("Built-in list"),
        NONE("No data"),
    }

    data class Result(val name: String?, val source: Source, val region: String?) {
        val isHoliday: Boolean get() = name != null
    }

    /** One calendar on the phone. [suggested] = it looks like the holiday calendar of the current region. */
    class PhoneCalendar(val id: Long, val name: String, val account: String, val suggested: Boolean)

    private class YearData(val holidays: Map<LocalDate, String>, val source: Source, val loadedAt: Long)

    private const val TTL_MS = 5 * 60_000L
    private const val JULIAN_EPOCH_OFFSET = 2440588L // Julian day number of 1970-01-01 as used by CalendarContract
    private const val GOOGLE_HOLIDAY_MARKER = "#holiday@"

    private val cache = HashMap<String, YearData>()
    private val builtin = HashMap<String, Map<LocalDate, String>>()

    fun hasCalendarPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ region

    /** Country of a time-zone id from the phone's own ICU data ("Asia/Tokyo" -> "JP"); null/"001" when it has none. */
    fun regionOfZone(zoneId: String): String? = runCatching { android.icu.util.TimeZone.getRegion(zoneId) }.getOrNull()

    /** The region that applies right now: the user's manual choice, else the country of the alarm time zone. */
    fun region(ctx: Context): HolidayRegion.Resolved {
        val s = Store.get(ctx).settings()
        return HolidayRegion.resolve(s.holidayRegion, s.zone().id, ::regionOfZone)
    }

    fun countryName(code: String?, locale: Locale = Locale.ENGLISH): String =
        if (code == null) "no country" else Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifBlank { code }

    // ------------------------------------------------------------------ lookup

    /** Holiday name for [date] in the current region, or null. [date] must be the local date in the alarm's zone. */
    fun check(ctx: Context, date: LocalDate, fresh: Boolean = false): Result {
        val region = region(ctx).code
        val data = yearData(ctx, region, date.year, fresh)
        return Result(data.holidays[date], data.source, region)
    }

    /** Which source is currently deciding for [year] (for the Settings screen). */
    fun sourceFor(ctx: Context, year: Int, fresh: Boolean = true): Source = yearData(ctx, region(ctx).code, year, fresh).source

    fun clearCache() = synchronized(this) { cache.clear() }

    @Synchronized
    private fun yearData(ctx: Context, region: String?, year: Int, fresh: Boolean): YearData {
        val now = System.currentTimeMillis()
        val key = "${region.orEmpty()}|$year"
        cache[key]?.let { if (!fresh && now - it.loadedAt < TTL_MS) return it }

        val app = ctx.applicationContext
        val settings = Store.get(app).settings()
        val fromCalendar = if (hasCalendarPermission(app)) loadFromCalendar(app, year, region) else emptyMap()
        val data = when {
            fromCalendar.isNotEmpty() -> YearData(fromCalendar, Source.CALENDAR, now)
            region != null && settings.useBuiltinHolidays -> {
                val fallback = builtinHolidays(app, region).filterKeys { it.year == year }
                if (fallback.isNotEmpty()) YearData(fallback, Source.BUILTIN, now) else YearData(emptyMap(), Source.NONE, now)
            }
            else -> YearData(emptyMap(), Source.NONE, now)
        }
        cache[key] = data
        runCatching { Store.get(app).saveLastHolidaySource(data.source.label) }
        return data
    }

    // ------------------------------------------------------------------ layer 1: calendar provider

    private class CalRow(val id: Long, val display: String, val name: String, val account: String)

    private fun queryCalendars(ctx: Context): List<CalRow> {
        val out = ArrayList<CalRow>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) out += CalRow(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty())
        }
        return out
    }

    /** Words that identify the holiday calendar of [region] ("Japan", the name in the phone's language, ...). */
    private fun terms(region: String?): List<String> {
        if (region == null) return emptyList()
        val list = ArrayList<String>()
        list += countryName(region, Locale.ENGLISH)
        list += countryName(region, Locale.getDefault())
        if (region == "TH") list += listOf("วันหยุดในประเทศไทย", "ประเทศไทย")
        return list.filter { it.isNotBlank() }.distinct()
    }

    private fun mentionsRegion(row: CalRow, region: String?): Boolean {
        val hay = (row.display + "\u0000" + row.name).lowercase()
        return terms(region).any { hay.contains(it.lowercase()) }
    }

    /** "Looks like a holiday calendar of the region": mentions the country AND is clearly about holidays. */
    private fun looksLikeHolidayCalendar(row: CalRow, region: String?): Boolean {
        if (!mentionsRegion(row, region)) return false
        val hay = (row.display + "\u0000" + row.name).lowercase()
        val thai = region == "TH" && (hay.contains("วันหยุด"))
        return hay.contains("holiday") || hay.contains(GOOGLE_HOLIDAY_MARKER) || thai
    }

    /** Every calendar on the phone, with the ones that look like the current region's holiday calendar flagged. */
    fun listCalendars(ctx: Context): List<PhoneCalendar> {
        if (!hasCalendarPermission(ctx)) return emptyList()
        val region = region(ctx).code
        return runCatching {
            queryCalendars(ctx.applicationContext)
                .map { PhoneCalendar(it.id, it.display.ifBlank { it.name }, it.account, looksLikeHolidayCalendar(it, region)) }
                .sortedWith(compareByDescending<PhoneCalendar> { it.suggested }.thenBy { it.name.lowercase() })
        }.getOrDefault(emptyList())
    }

    private fun loadFromCalendar(ctx: Context, year: Int, region: String?): Map<LocalDate, String> = try {
        val settings = Store.get(ctx).settings()
        val rows = queryCalendars(ctx)
        val patterns = settings.calendarPatterns().map { it.lowercase() }
        val ids: Set<Long> = if (settings.calendarIds.isNotEmpty()) {
            rows.filter { it.id in settings.calendarIds }.map { it.id }.toSet()
        } else {
            rows.filter { r ->
                looksLikeHolidayCalendar(r, region) ||
                    patterns.any { (r.display + "\u0000" + r.name + "\u0000" + r.account).lowercase().contains(it) }
            }.map { it.id }.toSet()
        }
        if (ids.isEmpty()) emptyMap() else queryInstances(ctx, year, ids)
    } catch (e: Exception) { // SecurityException, provider missing, bad column, ...
        emptyMap()
    }

    private fun queryInstances(ctx: Context, year: Int, ids: Set<Long>): Map<LocalDate, String> {
        // All-day instances for the whole year, by local day number (all-day events are timezone-free).
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
        ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
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

    // ------------------------------------------------------------------ layer 2: bundled lists

    private fun assetNameFor(region: String): String =
        if (region == "TH") "th_holidays.json" else "holidays_${region.lowercase()}.json"

    /** Does the app ship a built-in list for [region]? */
    fun builtinAvailable(ctx: Context, region: String?): Boolean =
        region != null && builtinHolidays(ctx.applicationContext, region).isNotEmpty()

    @Synchronized
    private fun builtinHolidays(ctx: Context, region: String): Map<LocalDate, String> {
        builtin[region]?.let { return it }
        val map = HashMap<LocalDate, String>()
        try {
            val text = ctx.assets.open(assetNameFor(region)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).getJSONArray("holidays")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                runCatching { map[LocalDate.parse(o.getString("date"))] = o.getString("name") }
            }
        } catch (e: Exception) {
            // No list for this country (or a corrupt asset): behave as "no data" rather than crash an alarm.
        }
        builtin[region] = map
        return map
    }

    /** Years covered by the built-in list of [region] (for the Settings screen reminder). */
    fun builtinYears(ctx: Context, region: String?): List<Int> =
        if (region == null) emptyList() else builtinHolidays(ctx.applicationContext, region).keys.map { it.year }.distinct().sorted()
}
