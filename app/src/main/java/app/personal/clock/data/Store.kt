package app.personal.clock.data

import android.content.Context
import android.content.SharedPreferences
import app.personal.clock.core.AlarmItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * All persistent state lives here as small JSON blobs in one SharedPreferences file.
 * Chosen over Room/DataStore to keep the dependency list minimal and to allow synchronous reads
 * from broadcast receivers (an alarm must never wait on a database to start ringing).
 */
class Store private constructor(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- alarms
    fun alarms(): List<AlarmItem> = synchronized(LOCK) { readAlarms() }

    private fun readAlarms(): List<AlarmItem> {
        val raw = sp.getString(K_ALARMS, null) ?: return emptyList()
        return runCatching { JSONArray(raw).mapObjects { alarmFromJson(it) } }.getOrDefault(emptyList())
    }

    private fun writeAlarms(list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(alarmToJson(it)) }
        sp.edit().putString(K_ALARMS, arr.toString()).apply()
    }

    fun alarm(id: Int): AlarmItem? = alarms().firstOrNull { it.id == id }

    /** Inserts or replaces by id. Pass id = 0 to allocate a new one. Returns the stored item. */
    fun upsertAlarm(a: AlarmItem): AlarmItem = synchronized(LOCK) {
        val list = readAlarms().toMutableList()
        val item = if (a.id == 0) a.copy(id = allocId()) else a
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list += item
        writeAlarms(list)
        item
    }

    fun deleteAlarm(id: Int) = synchronized(LOCK) { writeAlarms(readAlarms().filterNot { it.id == id }) }

    fun replaceAlarms(list: List<AlarmItem>) = synchronized(LOCK) {
        writeAlarms(list)
        val maxId = list.maxOfOrNull { it.id } ?: 0
        if (sp.getInt(K_NEXT_ID, 1) <= maxId) sp.edit().putInt(K_NEXT_ID, maxId + 1).apply()
    }

    private fun allocId(): Int {
        val id = sp.getInt(K_NEXT_ID, 1)
        sp.edit().putInt(K_NEXT_ID, id + 1).apply()
        return id
    }

    /** Next free id shared by alarms and timers (they use different PendingIntent request-code ranges). */
    fun newId(): Int = synchronized(LOCK) { allocId() }

    /** Creates the three default alarms and the world list exactly once. */
    fun ensureSeeded() = synchronized(LOCK) {
        if (sp.getBoolean(K_SEEDED, false)) return@synchronized
        val tueFri = AlarmItem.mask(
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        writeAlarms(
            listOf(
                AlarmItem(allocId(), "Morning Mon", 5, 30, AlarmItem.mask(DayOfWeek.MONDAY)),
                AlarmItem(allocId(), "Morning Tue-Fri", 7, 30, tueFri),
                AlarmItem(allocId(), "Afternoon", 16, 30, AlarmItem.WEEKDAYS),
            ),
        )
        if (!sp.contains(K_WORLD)) writeWorld(listOf(AppSettings.DEFAULT_ZONE))
        sp.edit().putBoolean(K_SEEDED, true).apply()
    }

    // ---------------------------------------------------------------- settings
    fun settings(): AppSettings = AppSettings.fromJson(
        sp.getString(K_SETTINGS, null)?.let { runCatching { JSONObject(it) }.getOrNull() },
    )

    fun saveSettings(s: AppSettings) = sp.edit().putString(K_SETTINGS, s.toJson().toString()).apply()

    // ---------------------------------------------------------------- world clock
    fun world(): List<String> {
        val raw = sp.getString(K_WORLD, null) ?: return listOf(AppSettings.DEFAULT_ZONE)
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(listOf(AppSettings.DEFAULT_ZONE))
    }

    fun saveWorld(list: List<String>) = writeWorld(list)

    private fun writeWorld(list: List<String>) {
        val arr = JSONArray()
        list.distinct().forEach { arr.put(it) }
        sp.edit().putString(K_WORLD, arr.toString()).apply()
    }

    // ---------------------------------------------------------------- timers
    fun timers(): List<TimerItem> = synchronized(LOCK) { readTimers() }

    private fun readTimers(): List<TimerItem> {
        val raw = sp.getString(K_TIMERS, null) ?: return emptyList()
        return runCatching { JSONArray(raw).mapObjects { TimerItem.fromJson(it) } }.getOrDefault(emptyList())
    }

    fun timer(id: Int): TimerItem? = timers().firstOrNull { it.id == id }

    fun upsertTimer(t: TimerItem): TimerItem = synchronized(LOCK) {
        val list = readTimers().toMutableList()
        val item = if (t.id == 0) t.copy(id = allocId()) else t
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list += item
        writeTimers(list)
        item
    }

    fun deleteTimer(id: Int) = synchronized(LOCK) { writeTimers(readTimers().filterNot { it.id == id }) }

    private fun writeTimers(list: List<TimerItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString(K_TIMERS, arr.toString()).apply()
    }

    // ---------------------------------------------------------------- widgets
    fun widgetDefault(): WidgetConfig =
        WidgetConfig.fromJson(sp.getString(K_WIDGET_DEFAULT, null)?.let { runCatching { JSONObject(it) }.getOrNull() })

    fun hasWidgetDefaultSaved(): Boolean = sp.contains(K_WIDGET_DEFAULT)

    fun saveWidgetDefault(c: WidgetConfig) = sp.edit().putString(K_WIDGET_DEFAULT, c.toJson().toString()).apply()

    fun hasWidgetConfig(id: Int): Boolean = sp.contains(K_WIDGET_PREFIX + id)

    /** Config of one placed widget; falls back to the saved default for widgets that have none yet. */
    fun widgetConfig(id: Int): WidgetConfig {
        val raw = sp.getString(K_WIDGET_PREFIX + id, null)
            ?: return widgetDefault()
        return WidgetConfig.fromJson(runCatching { JSONObject(raw) }.getOrNull(), widgetDefault())
    }

    fun saveWidgetConfig(id: Int, c: WidgetConfig) =
        sp.edit().putString(K_WIDGET_PREFIX + id, c.toJson().toString()).apply()

    fun deleteWidgetConfig(id: Int) = sp.edit().remove(K_WIDGET_PREFIX + id).apply()

    fun allWidgetConfigs(): Map<Int, WidgetConfig> =
        sp.all.keys.filter { it.startsWith(K_WIDGET_PREFIX) }.mapNotNull { key ->
            val id = key.removePrefix(K_WIDGET_PREFIX).toIntOrNull() ?: return@mapNotNull null
            id to widgetConfig(id)
        }.toMap()

    // ---------------------------------------------------------------- stopwatch
    fun stopwatchRaw(): String? = sp.getString(K_STOPWATCH, null)
    fun saveStopwatchRaw(s: String) = sp.edit().putString(K_STOPWATCH, s).apply()

    // ---------------------------------------------------------------- misc flags
    fun lastHolidaySource(): String = sp.getString(K_LAST_SOURCE, "") ?: ""
    fun saveLastHolidaySource(s: String) = sp.edit().putString(K_LAST_SOURCE, s).apply()

    companion object {
        private const val FILE = "clock_store"
        private val LOCK = Any()
        private const val K_ALARMS = "alarms"
        private const val K_NEXT_ID = "next_id"
        private const val K_SEEDED = "seeded"
        private const val K_SETTINGS = "settings"
        private const val K_WORLD = "world"
        private const val K_TIMERS = "timers"
        private const val K_WIDGET_DEFAULT = "widget_default"
        private const val K_WIDGET_PREFIX = "widget_"
        private const val K_STOPWATCH = "stopwatch"
        private const val K_LAST_SOURCE = "last_holiday_source"

        fun get(ctx: Context): Store = Store(ctx.applicationContext)

        fun alarmToJson(a: AlarmItem): JSONObject = JSONObject()
            .put("id", a.id).put("label", a.label).put("hour", a.hour).put("minute", a.minute)
            .put("days", a.days).put("enabled", a.enabled)
            .put("ringtoneUri", a.ringtoneUri ?: JSONObject.NULL)
            .put("vibrate", a.vibrate).put("snoozeMinutes", a.snoozeMinutes)
            .put("skipHolidays", a.skipHolidays)

        fun alarmFromJson(o: JSONObject): AlarmItem = AlarmItem(
            id = o.getInt("id"),
            label = o.optString("label", ""),
            hour = o.getInt("hour").coerceIn(0, 23),
            minute = o.getInt("minute").coerceIn(0, 59),
            days = o.optInt("days", 0) and 0x7F,
            enabled = o.optBoolean("enabled", true),
            ringtoneUri = if (o.isNull("ringtoneUri")) null else o.optString("ringtoneUri"),
            vibrate = o.optBoolean("vibrate", true),
            snoozeMinutes = o.optInt("snoozeMinutes", 5).coerceIn(1, 60),
            skipHolidays = o.optBoolean("skipHolidays", true),
        )
    }
}
