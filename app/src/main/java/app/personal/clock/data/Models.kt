package app.personal.clock.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Global app settings (one instance, stored as JSON). */
data class AppSettings(
    /** false = alarms/clock follow [homeZoneId]; true = follow the phone's current zone. */
    val followPhoneZone: Boolean = false,
    val homeZoneId: String = DEFAULT_ZONE,
    val defaultSnoozeMinutes: Int = 5,
    val autoStopMinutes: Int = 10,
    /** Advanced: also treat calendars whose name contains one of these (separated by '|') as holiday calendars. */
    val calendarNames: String = "",
    val notifyOnSkip: Boolean = true,
    val showSecondsInApp: Boolean = false,
    /** Manual holiday country (ISO code, e.g. "FR"). Empty = automatic: the country of the alarm's time zone. */
    val holidayRegion: String = "",
    /** Use the app's built-in public-holiday list when one exists for the region (Thailand today). */
    val useBuiltinHolidays: Boolean = true,
    /** Phone calendars (ids) the user ticked as holiday calendars. Empty = pick automatically from the region. */
    val calendarIds: List<Long> = emptyList(),
) {
    fun zone(): ZoneId =
        if (followPhoneZone) ZoneId.systemDefault()
        else runCatching { ZoneId.of(homeZoneId) }.getOrDefault(ZoneId.of(DEFAULT_ZONE))

    fun calendarPatterns(): List<String> =
        calendarNames.split('|', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun toJson(): JSONObject = JSONObject()
        .put("followPhoneZone", followPhoneZone)
        .put("homeZoneId", homeZoneId)
        .put("defaultSnoozeMinutes", defaultSnoozeMinutes)
        .put("autoStopMinutes", autoStopMinutes)
        .put("calendarNames", calendarNames)
        .put("notifyOnSkip", notifyOnSkip)
        .put("showSecondsInApp", showSecondsInApp)
        .put("holidayRegion", holidayRegion)
        .put("useBuiltinHolidays", useBuiltinHolidays)
        .put("calendarIds", JSONArray().also { arr -> calendarIds.forEach { arr.put(it) } })

    companion object {
        const val DEFAULT_ZONE = "Asia/Bangkok"

        /** The pre-universal default. Settings that still hold it are migrated to "" (the region now picks the calendar). */
        private const val LEGACY_CALENDAR_NAMES = "Holidays in Thailand|\u0E27\u0E31\u0E19\u0E2B\u0E22\u0E38\u0E14\u0E43\u0E19\u0E1B\u0E23\u0E30\u0E40\u0E17\u0E28\u0E44\u0E17\u0E22"

        fun fromJson(o: JSONObject?): AppSettings {
            val d = AppSettings()
            if (o == null) return d
            val storedNames = o.optString("calendarNames", d.calendarNames)
            val ids = o.optJSONArray("calendarIds")
            return AppSettings(
                followPhoneZone = o.optBoolean("followPhoneZone", d.followPhoneZone),
                homeZoneId = o.optString("homeZoneId", d.homeZoneId).ifBlank { d.homeZoneId },
                defaultSnoozeMinutes = o.optInt("defaultSnoozeMinutes", d.defaultSnoozeMinutes).coerceIn(1, 60),
                autoStopMinutes = o.optInt("autoStopMinutes", d.autoStopMinutes).coerceIn(1, 60),
                calendarNames = if (storedNames == LEGACY_CALENDAR_NAMES) "" else storedNames,
                notifyOnSkip = o.optBoolean("notifyOnSkip", d.notifyOnSkip),
                showSecondsInApp = o.optBoolean("showSecondsInApp", d.showSecondsInApp),
                holidayRegion = o.optString("holidayRegion", d.holidayRegion),
                useBuiltinHolidays = o.optBoolean("useBuiltinHolidays", d.useBuiltinHolidays),
                calendarIds = if (ids == null) emptyList() else (0 until ids.length()).mapNotNull { runCatching { ids.getLong(it) }.getOrNull() },
            )
        }
    }
}

/**
 * A countdown timer. [state]: IDLE (remaining = duration), RUNNING (ends at [endElapsed] on the
 * elapsedRealtime clock, mirrored in [endWall] for reboot recovery), PAUSED (remaining kept in [remainingMs]).
 */
data class TimerItem(
    val id: Int,
    val label: String,
    val durationMs: Long,
    val state: Int = IDLE,
    val remainingMs: Long = durationMs,
    val endElapsed: Long = 0L,
    val endWall: Long = 0L,
) {
    fun remaining(nowElapsed: Long): Long = when (state) {
        RUNNING -> (endElapsed - nowElapsed).coerceAtLeast(0L)
        else -> remainingMs
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("durationMs", durationMs).put("state", state)
        .put("remainingMs", remainingMs).put("endElapsed", endElapsed).put("endWall", endWall)

    companion object {
        const val IDLE = 0
        const val RUNNING = 1
        const val PAUSED = 2

        fun fromJson(o: JSONObject): TimerItem = TimerItem(
            id = o.getInt("id"),
            label = o.optString("label", ""),
            durationMs = o.getLong("durationMs"),
            state = o.optInt("state", IDLE),
            remainingMs = o.optLong("remainingMs", o.getLong("durationMs")),
            endElapsed = o.optLong("endElapsed", 0L),
            endWall = o.optLong("endWall", 0L),
        )
    }
}

/** Per-widget appearance. Colours are opaque RGB stored as ARGB ints (0xFFRRGGBB). */
data class WidgetConfig(
    val font: Int = 0,
    val sizeSp: Int = 64,
    val hAlign: Int = ALIGN_CENTER,
    val vAlign: Int = ALIGN_CENTER,
    val bgMode: Int = BG_FROSTED,
    val bgColor: Int = 0xFF1B1F2A.toInt(),
    val bgOpacity: Int = 55,
    val cornerStep: Int = 5,
    val paddingDp: Int = 12,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val dateColor: Int = 0xFFD0D4DC.toInt(),
    /** Size of the date line (sp). */
    val dateSizeSp: Int = 18,
    /** Next-alarm line: its own size and colour. */
    val alarmSizeSp: Int = 15,
    val alarmColor: Int = 0xFFD0D4DC.toInt(),
    /** Second (world) clock line: its own size and colour for the label. */
    val worldSizeSp: Int = 14,
    val worldColor: Int = 0xFFD0D4DC.toInt(),
    /** Second accent: minute hand on analog faces. */
    val accentColor: Int = 0xFF9FC2FF.toInt(),
    /** One of the LAYOUT_* constants: how the pieces are arranged. */
    val layout: Int = LAYOUT_STACK,
    val use24h: Boolean = true,
    val showAmPm: Boolean = true,
    val leadingZero: Boolean = true,
    val showDate: Boolean = true,
    val dateFormat: Int = 0,
    /**
     * Top-to-bottom order of the Classic layout's lines: a permutation of T (time), D (date), A (next alarm),
     * W (second clock). Other layouts have a fixed arrangement and ignore it.
     */
    val order: String = DEFAULT_ORDER,
    val showNextAlarm: Boolean = true,
    val showWorld: Boolean = false,
    val worldZone: String = "",
    /** 0 = app home zone setting (Bangkok unless "follow phone" is on), 1 = always the phone's zone. */
    val zoneMode: Int = 0,
    val shadow: Boolean = false,
    /** Shadow darkness 0..100 (%) and softness 0..20 (dp of blur). Defaults reproduce the original tight shadow. */
    val shadowStrength: Int = 70,
    val shadowBlur: Int = 4,
    val tapAction: Int = TAP_APP,
) {
    /** True when the date sits above the time (the live-clock widget can only do this one reordering). */
    /** [order] if it is a valid permutation, otherwise the default. */
    val safeOrder: String get() = if (validOrder(order)) order else DEFAULT_ORDER

    val dateAbove: Boolean get() = order.indexOf('D') < order.indexOf('T')

    fun toJson(): JSONObject = JSONObject()
        .put("font", font).put("sizeSp", sizeSp).put("hAlign", hAlign).put("vAlign", vAlign)
        .put("bgMode", bgMode).put("bgColor", bgColor).put("bgOpacity", bgOpacity)
        .put("cornerStep", cornerStep).put("paddingDp", paddingDp)
        .put("textColor", textColor).put("dateColor", dateColor)
        .put("dateSizeSp", dateSizeSp).put("alarmSizeSp", alarmSizeSp).put("alarmColor", alarmColor)
        .put("worldSizeSp", worldSizeSp).put("worldColor", worldColor).put("accentColor", accentColor).put("layout", layout)
        .put("use24h", use24h).put("showAmPm", showAmPm).put("leadingZero", leadingZero)
        .put("showDate", showDate).put("dateFormat", dateFormat).put("order", order)
        .put("showNextAlarm", showNextAlarm).put("showWorld", showWorld).put("worldZone", worldZone)
        .put("zoneMode", zoneMode).put("shadow", shadow).put("shadowStrength", shadowStrength).put("shadowBlur", shadowBlur).put("tapAction", tapAction)

    companion object {
        const val ALIGN_START = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_END = 2

        const val BG_SOLID = 0
        const val BG_TRANSPARENT = 1
        const val BG_FROSTED = 2

        const val LAYOUT_STACK = 0
        const val LAYOUT_ROW = 1
        const val LAYOUT_DUAL = 2
        const val LAYOUT_ANALOG = 3
        const val LAYOUT_ANALOG_DIGITAL = 4
        const val LAYOUT_ALARM_CARD = 5
        const val LAYOUT_DATE_FIRST = 6

        const val TAP_APP = 0
        const val TAP_ALARMS = 1
        const val TAP_NONE = 2

        const val DEFAULT_ORDER = "TDAW"

        /** A usable order is exactly the four letters T, D, A, W once each, in any sequence. */
        fun validOrder(s: String): Boolean = s.length == 4 && s.toSet() == setOf('T', 'D', 'A', 'W')

        const val FONT_COUNT = 9
        const val DEFAULT_SHADOW_STRENGTH = 70
        const val DEFAULT_SHADOW_BLUR = 4
        const val MAX_CORNER_STEP = 12
        val DATE_FORMATS = listOf("EEE d MMM", "EEEE, d MMMM", "d MMM yyyy", "EEE, MMM d", "dd/MM/yyyy")

        /** [is24h] seeds the 12/24h default from the phone's own setting for brand-new widgets. */
        fun default(is24h: Boolean = true) = WidgetConfig(use24h = is24h, leadingZero = is24h)

        fun fromJson(o: JSONObject?, fallback: WidgetConfig = WidgetConfig()): WidgetConfig {
            if (o == null) return fallback
            val d = fallback
            return WidgetConfig(
                font = o.optInt("font", d.font).coerceIn(0, FONT_COUNT - 1),
                sizeSp = o.optInt("sizeSp", d.sizeSp).coerceIn(24, 120),
                hAlign = o.optInt("hAlign", d.hAlign).coerceIn(0, 2),
                vAlign = o.optInt("vAlign", d.vAlign).coerceIn(0, 2),
                bgMode = o.optInt("bgMode", d.bgMode).coerceIn(0, 2),
                bgColor = o.optInt("bgColor", d.bgColor),
                bgOpacity = o.optInt("bgOpacity", d.bgOpacity).coerceIn(0, 100),
                cornerStep = o.optInt("cornerStep", d.cornerStep).coerceIn(0, MAX_CORNER_STEP),
                paddingDp = o.optInt("paddingDp", d.paddingDp).coerceIn(0, 40),
                textColor = o.optInt("textColor", d.textColor),
                dateColor = o.optInt("dateColor", d.dateColor),
                dateSizeSp = o.optInt("dateSizeSp", d.dateSizeSp).coerceIn(8, 60),
                alarmSizeSp = o.optInt("alarmSizeSp", d.alarmSizeSp).coerceIn(8, 60),
                alarmColor = o.optInt("alarmColor", o.optInt("dateColor", d.alarmColor)),
                worldSizeSp = o.optInt("worldSizeSp", d.worldSizeSp).coerceIn(8, 60),
                worldColor = o.optInt("worldColor", o.optInt("dateColor", d.worldColor)),
                accentColor = o.optInt("accentColor", d.accentColor),
                layout = o.optInt("layout", d.layout).coerceIn(0, LAYOUT_DATE_FIRST),
                use24h = o.optBoolean("use24h", d.use24h),
                showAmPm = o.optBoolean("showAmPm", d.showAmPm),
                leadingZero = o.optBoolean("leadingZero", d.leadingZero),
                showDate = o.optBoolean("showDate", d.showDate),
                dateFormat = o.optInt("dateFormat", d.dateFormat).coerceIn(0, DATE_FORMATS.size - 1),
                // Older saves only knew "date above/below the time".
                order = o.optString("order", "").takeIf { validOrder(it) }
                    ?: if (o.has("dateAbove")) (if (o.optBoolean("dateAbove")) "DTAW" else DEFAULT_ORDER) else d.order,
                showNextAlarm = o.optBoolean("showNextAlarm", d.showNextAlarm),
                showWorld = o.optBoolean("showWorld", d.showWorld),
                worldZone = o.optString("worldZone", d.worldZone),
                zoneMode = o.optInt("zoneMode", d.zoneMode).coerceIn(0, 1),
                shadow = o.optBoolean("shadow", d.shadow),
                shadowStrength = o.optInt("shadowStrength", d.shadowStrength).coerceIn(0, 100),
                shadowBlur = o.optInt("shadowBlur", d.shadowBlur).coerceIn(0, 20),
                tapAction = o.optInt("tapAction", d.tapAction).coerceIn(0, 2),
            )
        }
    }
}

internal fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        runCatching { f(o) }.onSuccess { out += it }
    }
    return out
}
