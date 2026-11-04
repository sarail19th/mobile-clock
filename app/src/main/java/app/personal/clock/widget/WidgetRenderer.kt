package app.personal.clock.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import app.personal.clock.R
import app.personal.clock.Tabs
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.alarm.Notifications
import app.personal.clock.core.Fmt
import app.personal.clock.core.WidgetFitter
import app.personal.clock.data.AppSettings
import app.personal.clock.data.Store
import app.personal.clock.data.WidgetConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the RemoteViews for one widget.
 *
 * Time is drawn by TextClock: the system ticks it once a minute, this app does no work per minute,
 * and because the format never contains seconds, seconds can never appear.
 */
object WidgetRenderer {

    /** [font][shadow] -> layout. One layout per typeface because RemoteViews cannot change a typeface at runtime. */
    private val LAYOUTS = arrayOf(
        intArrayOf(R.layout.widget_f0_s0, R.layout.widget_f0_s1),
        intArrayOf(R.layout.widget_f1_s0, R.layout.widget_f1_s1),
        intArrayOf(R.layout.widget_f2_s0, R.layout.widget_f2_s1),
        intArrayOf(R.layout.widget_f3_s0, R.layout.widget_f3_s1),
        intArrayOf(R.layout.widget_f4_s0, R.layout.widget_f4_s1),
        intArrayOf(R.layout.widget_f5_s0, R.layout.widget_f5_s1),
    )

    private val BG = intArrayOf(
        R.drawable.widget_bg_r0, R.drawable.widget_bg_r1, R.drawable.widget_bg_r2, R.drawable.widget_bg_r3,
        R.drawable.widget_bg_r4, R.drawable.widget_bg_r5, R.drawable.widget_bg_r6, R.drawable.widget_bg_r7,
        R.drawable.widget_bg_r8, R.drawable.widget_bg_r9, R.drawable.widget_bg_r10, R.drawable.widget_bg_r11,
        R.drawable.widget_bg_r12,
    )
    private val BORDER = intArrayOf(
        R.drawable.widget_bd_r0, R.drawable.widget_bd_r1, R.drawable.widget_bd_r2, R.drawable.widget_bd_r3,
        R.drawable.widget_bd_r4, R.drawable.widget_bd_r5, R.drawable.widget_bd_r6, R.drawable.widget_bd_r7,
        R.drawable.widget_bd_r8, R.drawable.widget_bd_r9, R.drawable.widget_bd_r10, R.drawable.widget_bd_r11,
        R.drawable.widget_bd_r12,
    )

    const val DEFAULT_WIDTH_DP = 250
    const val DEFAULT_HEIGHT_DP = 110

    fun initialLayout(): Int = LAYOUTS[0][0]

    /** The widget's time zone id as TextClock wants it. */
    fun clockZone(cfg: WidgetConfig, settings: AppSettings): String =
        if (cfg.zoneMode == 1 || settings.followPhoneZone) TimeZone.getDefault().id else settings.zone().id

    fun nextAlarmText(ctx: Context, cfg: WidgetConfig): String? {
        val next = AlarmScheduler.nextOverall(ctx) ?: return null
        val pattern = if (cfg.use24h) "EEE HH:mm" else "EEE h:mm a"
        return "Next alarm  " + next.at.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private fun worldLabel(zoneId: String): String? = runCatching {
        val off = ZoneId.of(zoneId).rules.getOffset(Instant.now()).totalSeconds
        "${Fmt.cityName(zoneId)} · ${Fmt.utcOffset(off)}"
    }.getOrNull()

    fun build(ctx: Context, appWidgetId: Int, override: WidgetConfig? = null): RemoteViews {
        val store = Store.get(ctx)
        val cfg = override ?: store.widgetConfig(appWidgetId)
        val settings = store.settings()
        val density = ctx.resources.displayMetrics.density

        val rv = RemoteViews(ctx.packageName, LAYOUTS[cfg.font.coerceIn(0, LAYOUTS.size - 1)][if (cfg.shadow) 1 else 0])

        // ---- how much room do we have?
        val opts = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(appWidgetId)
        val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        val alarmText = if (cfg.showNextAlarm) nextAlarmText(ctx, cfg) else null
        val worldText = if (cfg.showWorld && cfg.worldZone.isNotBlank()) worldLabel(cfg.worldZone) else null
        val showAmPm = !cfg.use24h && cfg.showAmPm
        val fit = WidgetFitter.fit(
            wantedSp = cfg.sizeSp, paddingDp = cfg.paddingDp, font = cfg.font, showAmPm = showAmPm,
            wantDate = cfg.showDate, wantAlarm = alarmText != null, wantWorld = worldText != null,
            widthDp = wDp, heightDp = hDp,
        )
        val size = fit.sizeSp
        val subSize = fit.subSp
        val showDate = fit.showDate
        val showAlarm = fit.showAlarm
        val showWorld = fit.showWorld

        // ---- time
        val timeFmt = if (cfg.use24h) {
            if (cfg.leadingZero) "HH:mm" else "H:mm"
        } else {
            if (cfg.leadingZero) "hh:mm" else "h:mm"
        }
        val tz = clockZone(cfg, settings)
        rv.setCharSequence(R.id.w_time, "setFormat12Hour", timeFmt)
        rv.setCharSequence(R.id.w_time, "setFormat24Hour", timeFmt)
        rv.setString(R.id.w_time, "setTimeZone", tz)
        rv.setTextViewTextSize(R.id.w_time, TypedValue.COMPLEX_UNIT_SP, size)
        rv.setTextColor(R.id.w_time, cfg.textColor)

        rv.setViewVisibility(R.id.w_ampm, if (showAmPm) View.VISIBLE else View.GONE)
        if (showAmPm) {
            rv.setString(R.id.w_ampm, "setTimeZone", tz)
            rv.setTextViewTextSize(R.id.w_ampm, TypedValue.COMPLEX_UNIT_SP, fit.ampmSp)
            rv.setTextColor(R.id.w_ampm, cfg.textColor)
        }

        // ---- date (one of two identical views: above or below the time)
        val dateId = if (cfg.dateAbove) R.id.w_date_top else R.id.w_date
        val otherDateId = if (cfg.dateAbove) R.id.w_date else R.id.w_date_top
        rv.setViewVisibility(otherDateId, View.GONE)
        rv.setViewVisibility(dateId, if (showDate) View.VISIBLE else View.GONE)
        if (showDate) {
            val fmt = WidgetConfig.DATE_FORMATS[cfg.dateFormat.coerceIn(0, WidgetConfig.DATE_FORMATS.size - 1)]
            rv.setCharSequence(dateId, "setFormat12Hour", fmt)
            rv.setCharSequence(dateId, "setFormat24Hour", fmt)
            rv.setString(dateId, "setTimeZone", tz)
            rv.setTextViewTextSize(dateId, TypedValue.COMPLEX_UNIT_SP, subSize)
            rv.setTextColor(dateId, cfg.dateColor)
        }

        // ---- next alarm
        rv.setViewVisibility(R.id.w_alarm, if (showAlarm) View.VISIBLE else View.GONE)
        if (showAlarm) {
            rv.setTextViewText(R.id.w_alarm, alarmText)
            rv.setTextViewTextSize(R.id.w_alarm, TypedValue.COMPLEX_UNIT_SP, subSize * 0.9f)
            rv.setTextColor(R.id.w_alarm, cfg.dateColor)
        }

        // ---- second clock
        rv.setViewVisibility(R.id.w_world_row, if (showWorld) View.VISIBLE else View.GONE)
        if (showWorld) {
            rv.setTextViewText(R.id.w_world_label, worldText)
            rv.setTextViewTextSize(R.id.w_world_label, TypedValue.COMPLEX_UNIT_SP, subSize * 0.9f)
            rv.setTextColor(R.id.w_world_label, cfg.dateColor)
            val wf = if (cfg.use24h) "HH:mm" else "h:mm a"
            rv.setCharSequence(R.id.w_world_time, "setFormat12Hour", wf)
            rv.setCharSequence(R.id.w_world_time, "setFormat24Hour", wf)
            rv.setString(R.id.w_world_time, "setTimeZone", cfg.worldZone)
            rv.setTextViewTextSize(R.id.w_world_time, TypedValue.COMPLEX_UNIT_SP, subSize * 0.9f)
            rv.setTextColor(R.id.w_world_time, cfg.textColor)
        }

        // ---- alignment (LinearLayout.setGravity / TextView.setGravity are both @RemotableViewMethod)
        val hg = when (cfg.hAlign) {
            WidgetConfig.ALIGN_START -> Gravity.START
            WidgetConfig.ALIGN_END -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        val vg = when (cfg.vAlign) {
            0 -> Gravity.TOP
            2 -> Gravity.BOTTOM
            else -> Gravity.CENTER_VERTICAL
        }
        rv.setInt(R.id.w_content, "setGravity", vg or hg)
        rv.setInt(R.id.w_time_row, "setGravity", hg)
        rv.setInt(R.id.w_date, "setGravity", hg)
        rv.setInt(R.id.w_date_top, "setGravity", hg)
        rv.setInt(R.id.w_alarm, "setGravity", hg)
        rv.setInt(R.id.w_world_row, "setGravity", hg)

        val pad = (cfg.paddingDp * density).toInt()
        rv.setViewPadding(R.id.w_content, pad, pad, pad, pad)

        // ---- background
        val step = cfg.cornerStep.coerceIn(0, WidgetConfig.MAX_CORNER_STEP)
        when (cfg.bgMode) {
            WidgetConfig.BG_TRANSPARENT -> {
                rv.setViewVisibility(R.id.w_bg, View.GONE)
                rv.setViewVisibility(R.id.w_border, View.GONE)
            }
            else -> {
                rv.setViewVisibility(R.id.w_bg, View.VISIBLE)
                rv.setImageViewResource(R.id.w_bg, BG[step])
                rv.setInt(R.id.w_bg, "setColorFilter", cfg.bgColor or (0xFF shl 24))
                rv.setInt(R.id.w_bg, "setImageAlpha", cfg.bgOpacity * 255 / 100)
                if (cfg.bgMode == WidgetConfig.BG_FROSTED) {
                    // "Frosted" = tinted translucent fill + thin light edge. Real backdrop blur is not possible in a widget.
                    rv.setViewVisibility(R.id.w_border, View.VISIBLE)
                    rv.setImageViewResource(R.id.w_border, BORDER[step])
                    rv.setInt(R.id.w_border, "setColorFilter", 0xFFFFFFFF.toInt())
                    rv.setInt(R.id.w_border, "setImageAlpha", 90)
                } else {
                    rv.setViewVisibility(R.id.w_border, View.GONE)
                }
            }
        }

        // ---- tap
        when (cfg.tapAction) {
            WidgetConfig.TAP_APP -> rv.setOnClickPendingIntent(R.id.w_root, Notifications.openApp(ctx, Tabs.CLOCK))
            WidgetConfig.TAP_ALARMS -> rv.setOnClickPendingIntent(R.id.w_root, Notifications.openApp(ctx, Tabs.ALARM))
            else -> Unit
        }
        return rv
    }
}
