package app.personal.clock.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Global app settings (one instance, stored as JSON). */
data class AppSettings(
    /** false = alarms/clock follow [homeZoneId] (Bangkok); true = follow the phone's zone. */
    val followPhoneZone: Boolean = false,
    val homeZoneId: String = DEFAULT_ZONE,
    val defaultSnoozeMinutes: Int = 5,
    val autoStopMinutes: Int = 10,
    /** Calendar names to treat as the Thai holiday calendar, separated by '|'. Case-insensitive "contains". */
    val calendarNames: String = "Holidays in Thailand|วันหยุดในประเทศไทย",
    val notifyOnSkip: Boolean = true,
    val showSecondsInApp: Boolean = false,
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

    companion object {
        const val DEFAULT_ZONE = "Asia/Bangkok"

        fun fromJson(o: JSONObject?): AppSettings {
            val d = AppSettings()
            if (o == null) return d
            return AppSettings(
                followPhoneZone = o.optBoolean("followPhoneZone", d.followPhoneZone),
                homeZoneId = o.optString("homeZoneId", d.homeZoneId).ifBlank { d.homeZoneId },
                defaultSnoozeMinutes = o.optInt("defaultSnoozeMinutes", d.defaultSnoozeMinutes).coerceIn(1, 60),
                autoStopMinutes = o.optInt("autoStopMinutes", d.autoStopMinutes).coerceIn(1, 60),
                calendarNames = o.optString("calendarNames", d.calendarNames),
                notifyOnSkip = o.optBoolean("notifyOnSkip", d.notifyOnSkip),
                showSecondsInApp = o.optBoolean("showSecondsInApp", d.showSecondsInApp),
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
    val use24h: Boolean = true,
    val showAmPm: Boolean = true,
    val leadingZero: Boolean = true,
    val showDate: Boolean = true,
    val dateFormat: Int = 0,
    val showNextAlarm: Boolean = true,
    val showWorld: Boolean = false,
    val worldZone: String = "",
    /** 0 = app home zone setting (Bangkok unless "follow phone" is on), 1 = always the phone's zone. */
    val zoneMode: Int = 0,
    val shadow: Boolean = false,
    val tapAction: Int = TAP_APP,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("font", font).put("sizeSp", sizeSp).put("hAlign", hAlign).put("vAlign", vAlign)
        .put("bgMode", bgMode).put("bgColor", bgColor).put("bgOpacity", bgOpacity)
        .put("cornerStep", cornerStep).put("paddingDp", paddingDp)
        .put("textColor", textColor).put("dateColor", dateColor)
        .put("use24h", use24h).put("showAmPm", showAmPm).put("leadingZero", leadingZero)
        .put("showDate", showDate).put("dateFormat", dateFormat)
        .put("showNextAlarm", showNextAlarm).put("showWorld", showWorld).put("worldZone", worldZone)
        .put("zoneMode", zoneMode).put("shadow", shadow).put("tapAction", tapAction)

    companion object {
        const val ALIGN_START = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_END = 2

        const val BG_SOLID = 0
        const val BG_TRANSPARENT = 1
        const val BG_FROSTED = 2

        const val TAP_APP = 0
        const val TAP_ALARMS = 1
        const val TAP_NONE = 2

        const val FONT_COUNT = 6
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
                use24h = o.optBoolean("use24h", d.use24h),
                showAmPm = o.optBoolean("showAmPm", d.showAmPm),
                leadingZero = o.optBoolean("leadingZero", d.leadingZero),
                showDate = o.optBoolean("showDate", d.showDate),
                dateFormat = o.optInt("dateFormat", d.dateFormat).coerceIn(0, DATE_FORMATS.size - 1),
                showNextAlarm = o.optBoolean("showNextAlarm", d.showNextAlarm),
                showWorld = o.optBoolean("showWorld", d.showWorld),
                worldZone = o.optString("worldZone", d.worldZone),
                zoneMode = o.optInt("zoneMode", d.zoneMode).coerceIn(0, 1),
                shadow = o.optBoolean("shadow", d.shadow),
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
