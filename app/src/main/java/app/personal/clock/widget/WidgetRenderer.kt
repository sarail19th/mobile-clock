package app.personal.clock.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import app.personal.clock.R
import app.personal.clock.Tabs
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.alarm.Notifications
import app.personal.clock.core.Fmt
import app.personal.clock.core.WidgetFit
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
 * Two modes:
 *  - font "System" (index 0): the time is a TextClock, ticked by the system once a minute with no work from
 *    this app. No seconds format exists anywhere, so seconds can never appear.
 *  - any other font: launchers ignore app-bundled fonts in widgets, so the text is drawn into a bitmap in
 *    the chosen typeface and refreshed once a minute by [WidgetTicker].
 */
object WidgetRenderer {

    /** [shadow] -> layout (the system-font TextClock layout; it also holds the bitmap view). */
    private val LAYOUTS = intArrayOf(R.layout.widget_f0_s0, R.layout.widget_f0_s1)

    /** Typeface per font index; 0 = the phone's own font. */
    private val FONT_RES = intArrayOf(
        0, R.font.dm_sans, R.font.nunito, R.font.fredoka, R.font.quicksand,
        R.font.jetbrains_mono, R.font.playfair_display, R.font.barlow_condensed, R.font.poppins_thin,
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

    fun initialLayout(): Int = LAYOUTS[0]

    /** True when this config is drawn as a bitmap (needs the per-minute refresh). */
    fun usesBitmap(cfg: WidgetConfig): Boolean = cfg.font != 0

    /** The widget's time zone id as TextClock wants it. */
    fun clockZone(cfg: WidgetConfig, settings: AppSettings): String =
        if (cfg.zoneMode == 1 || settings.followPhoneZone) TimeZone.getDefault().id else settings.zone().id

    @Suppress("DEPRECATION")
    private fun sizesFor(ctx: Context, appWidgetId: Int): List<SizeF> {
        val opts = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(appWidgetId)
        val listed = opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.filter { it.width > 0f && it.height > 0f }?.distinct()
        if (!listed.isNullOrEmpty()) return listed
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        return listOf(SizeF(w.toFloat(), h.toFloat()))
    }

    /** Smallest size the launcher may show this widget at (used by the editor preview and the live-clock fit). */
    fun primarySizeDp(ctx: Context, appWidgetId: Int): Pair<Int, Int> {
        val s = sizesFor(ctx, appWidgetId).minByOrNull { it.width * it.height } ?: SizeF(DEFAULT_WIDTH_DP.toFloat(), DEFAULT_HEIGHT_DP.toFloat())
        return s.width.toInt() to s.height.toInt()
    }

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
        val cfg = override ?: Store.get(ctx).widgetConfig(appWidgetId)
        val sizes = sizesFor(ctx, appWidgetId)
        if (!usesBitmap(cfg)) {
            val (w, h) = primarySizeDp(ctx, appWidgetId)
            return buildOne(ctx, cfg, w, h)
        }
        // Bitmap mode: one bitmap per size the launcher can show (portrait / landscape) so nothing is stretched.
        return if (sizes.size == 1) {
            buildOne(ctx, cfg, sizes[0].width.toInt(), sizes[0].height.toInt())
        } else {
            RemoteViews(sizes.associateWith { buildOne(ctx, cfg, it.width.toInt(), it.height.toInt()) })
        }
    }

    private fun buildOne(ctx: Context, cfg: WidgetConfig, wDp: Int, hDp: Int): RemoteViews {
        val store = Store.get(ctx)
        val settings = store.settings()
        val density = ctx.resources.displayMetrics.density
        val rv = RemoteViews(ctx.packageName, LAYOUTS[if (cfg.shadow) 1 else 0])

        val alarmText = if (cfg.showNextAlarm) nextAlarmText(ctx, cfg) else null
        val worldText = if (cfg.showWorld && cfg.worldZone.isNotBlank()) worldLabel(cfg.worldZone) else null
        val showAmPm = !cfg.use24h && cfg.showAmPm
        val fit = WidgetFitter.fit(
            wantedSp = cfg.sizeSp, paddingDp = cfg.paddingDp, font = cfg.font, showAmPm = showAmPm,
            wantDate = cfg.showDate, wantAlarm = alarmText != null, wantWorld = worldText != null,
            widthDp = wDp, heightDp = hDp,
        )
        val tz = clockZone(cfg, settings)

        if (usesBitmap(cfg)) {
            bindBitmap(ctx, rv, cfg, fit, tz, alarmText, worldText, showAmPm, wDp, hDp)
        } else {
            bindTextClocks(rv, cfg, fit, tz, alarmText, worldText, showAmPm)
            val pad = (cfg.paddingDp * density).toInt()
            rv.setViewPadding(R.id.w_content, pad, pad, pad, pad)
        }
        bindBackground(rv, cfg)

        when (cfg.tapAction) {
            WidgetConfig.TAP_APP -> rv.setOnClickPendingIntent(R.id.w_root, Notifications.openApp(ctx, Tabs.CLOCK))
            WidgetConfig.TAP_ALARMS -> rv.setOnClickPendingIntent(R.id.w_root, Notifications.openApp(ctx, Tabs.ALARM))
            else -> Unit
        }
        return rv
    }

    // ------------------------------------------------------------------ bitmap mode (custom fonts)

    private fun bindBitmap(
        ctx: Context, rv: RemoteViews, cfg: WidgetConfig, fit: WidgetFit, tz: String,
        alarmText: String?, worldText: String?, showAmPm: Boolean, wDp: Int, hDp: Int,
    ) {
        val zone = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.systemDefault())
        val now = Instant.now().atZone(zone)
        val loc = Locale.getDefault()
        val timeFmt = if (cfg.use24h) (if (cfg.leadingZero) "HH:mm" else "H:mm") else (if (cfg.leadingZero) "hh:mm" else "h:mm")
        val time = now.format(DateTimeFormatter.ofPattern(timeFmt, loc))
        val ampm = now.format(DateTimeFormatter.ofPattern("a", loc))
        val dateText = now.format(
            DateTimeFormatter.ofPattern(WidgetConfig.DATE_FORMATS[cfg.dateFormat.coerceIn(0, WidgetConfig.DATE_FORMATS.size - 1)], loc),
        )

        val lines = ArrayList<WidgetBitmap.Line>()
        val dateLine = WidgetBitmap.Line(listOf(WidgetBitmap.Part(dateText, fit.subSp, cfg.dateColor)))
        if (fit.showDate && cfg.dateAbove) lines += dateLine
        lines += WidgetBitmap.Line(
            listOfNotNull(
                WidgetBitmap.Part(time, fit.sizeSp, cfg.textColor),
                if (showAmPm) WidgetBitmap.Part(ampm, fit.ampmSp, cfg.textColor, gapDp = 4f) else null,
            ),
        )
        if (fit.showDate && !cfg.dateAbove) lines += dateLine
        if (fit.showAlarm && alarmText != null) {
            lines += WidgetBitmap.Line(listOf(WidgetBitmap.Part(alarmText, fit.subSp * 0.9f, cfg.dateColor)))
        }
        if (fit.showWorld && worldText != null) {
            val wz = runCatching { ZoneId.of(cfg.worldZone) }.getOrNull()
            val wt = wz?.let { Instant.now().atZone(it).format(DateTimeFormatter.ofPattern(if (cfg.use24h) "HH:mm" else "h:mm a", loc)) }.orEmpty()
            lines += WidgetBitmap.Line(
                listOf(
                    WidgetBitmap.Part(worldText, fit.subSp * 0.9f, cfg.dateColor),
                    WidgetBitmap.Part(wt, fit.subSp * 0.9f, cfg.textColor, gapDp = 6f),
                ),
            )
        }

        val typeface = runCatching { ctx.resources.getFont(FONT_RES[cfg.font.coerceIn(0, FONT_RES.size - 1)]) }
            .getOrDefault(Typeface.DEFAULT)
        val bmp = WidgetBitmap.render(ctx, cfg, typeface, wDp, hDp, lines)
        rv.setViewVisibility(R.id.w_content, View.GONE)
        rv.setViewVisibility(R.id.w_bitmap, View.VISIBLE)
        rv.setImageViewBitmap(R.id.w_bitmap, bmp)
    }

    // ------------------------------------------------------------------ live clock mode (System font)

    private fun bindTextClocks(
        rv: RemoteViews, cfg: WidgetConfig, fit: WidgetFit, tz: String,
        alarmText: String?, worldText: String?, showAmPm: Boolean,
    ) {
        val size = fit.sizeSp
        val subSize = fit.subSp
        rv.setViewVisibility(R.id.w_bitmap, View.GONE)

        val timeFmt = if (cfg.use24h) {
            if (cfg.leadingZero) "HH:mm" else "H:mm"
        } else {
            if (cfg.leadingZero) "hh:mm" else "h:mm"
        }
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

        // date: one of two identical views, above or below the time
        val dateId = if (cfg.dateAbove) R.id.w_date_top else R.id.w_date
        val otherDateId = if (cfg.dateAbove) R.id.w_date else R.id.w_date_top
        rv.setViewVisibility(otherDateId, View.GONE)
        rv.setViewVisibility(dateId, if (fit.showDate) View.VISIBLE else View.GONE)
        if (fit.showDate) {
            val fmt = WidgetConfig.DATE_FORMATS[cfg.dateFormat.coerceIn(0, WidgetConfig.DATE_FORMATS.size - 1)]
            rv.setCharSequence(dateId, "setFormat12Hour", fmt)
            rv.setCharSequence(dateId, "setFormat24Hour", fmt)
            rv.setString(dateId, "setTimeZone", tz)
            rv.setTextViewTextSize(dateId, TypedValue.COMPLEX_UNIT_SP, subSize)
            rv.setTextColor(dateId, cfg.dateColor)
        }

        rv.setViewVisibility(R.id.w_alarm, if (fit.showAlarm) View.VISIBLE else View.GONE)
        if (fit.showAlarm) {
            rv.setTextViewText(R.id.w_alarm, alarmText)
            rv.setTextViewTextSize(R.id.w_alarm, TypedValue.COMPLEX_UNIT_SP, subSize * 0.9f)
            rv.setTextColor(R.id.w_alarm, cfg.dateColor)
        }

        rv.setViewVisibility(R.id.w_world_row, if (fit.showWorld) View.VISIBLE else View.GONE)
        if (fit.showWorld) {
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

        // alignment (LinearLayout.setGravity / TextView.setGravity are both @RemotableViewMethod)
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
    }

    // ------------------------------------------------------------------ background (both modes)

    private fun bindBackground(rv: RemoteViews, cfg: WidgetConfig) {
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
    }
}
