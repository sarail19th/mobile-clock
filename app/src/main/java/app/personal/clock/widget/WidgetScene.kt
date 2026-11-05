package app.personal.clock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.core.Fmt
import app.personal.clock.core.WorldEntry
import app.personal.clock.core.WidgetFitter
import app.personal.clock.data.Store
import app.personal.clock.data.WidgetConfig
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/**
 * Draws a widget's content (text, analog face) onto a transparent bitmap, for any layout and any font.
 *
 * The home-screen widget shows this bitmap (launchers ignore app-bundled fonts, and only a picture can express
 * the analog / dual / card layouts), and the editor preview draws the very same bitmap, so what you see is what you get.
 * The panel background is NOT part of the bitmap: the widget draws it with image views, the preview with Compose.
 */
object WidgetScene {

    private class Txt(val text: String, val sp: Float, val color: Int, val gapDp: Float = 0f)
    private class Row(val parts: List<Txt>)
    private class Block(val rect: RectF, val rows: List<Row>, val hAlign: Int, val vAlign: Int)

    private class Laid(
        val paints: List<TextPaint>,
        val widths: List<Float>,
        val gaps: List<Float>,
        val width: Float,
        val ascent: Float,
        val height: Float,
    )

    fun render(ctx: Context, cfg: WidgetConfig, wDp: Int, hDp: Int): Bitmap {
        val m = ctx.resources.displayMetrics
        val d = m.density
        val w = (wDp * d).toInt().coerceIn(1, 1800)
        val h = (hDp * d).toInt().coerceIn(1, 1800)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val settings = Store.get(ctx).settings()
        val zone = runCatching { ZoneId.of(WidgetRenderer.clockZone(cfg, settings)) }.getOrDefault(ZoneId.systemDefault())
        val now = Instant.now().atZone(zone)
        val face = WidgetTypefaces.typeface(ctx, cfg.font)
        val pad = cfg.paddingDp * d
        val inner = RectF(pad, pad, w - pad, h - pad)
        if (inner.width() < 4f || inner.height() < 4f) return bmp

        val text = Strings(ctx, cfg, now)
        var analog: RectF? = null
        var divider = false
        val blocks = ArrayList<Block>()
        val gap = 8f * d

        when (cfg.layout) {
            WidgetConfig.LAYOUT_ROW -> {
                val split = inner.left + inner.width() * 0.56f
                blocks += Block(RectF(inner.left, inner.top, split, inner.bottom), listOf(timeRow(cfg, text)), WidgetConfig.ALIGN_END, cfg.vAlign)
                val dateRows = if (cfg.showDate) listOf(
                    Row(listOf(Txt(text.dateTop, cfg.dateSizeSp.toFloat(), cfg.dateColor))),
                    Row(listOf(Txt(text.dateBottom, cfg.dateSizeSp.toFloat(), cfg.dateColor))),
                ) else emptyList()
                blocks += Block(RectF(split + gap, inner.top, inner.right, inner.bottom), dateRows, WidgetConfig.ALIGN_START, cfg.vAlign)
            }
            WidgetConfig.LAYOUT_DUAL -> {
                val mid = inner.centerX()
                divider = true
                blocks += Block(RectF(inner.left, inner.top, mid - gap / 2, inner.bottom), dualRows(cfg, text.homeLabel, text.time, text.ampm), WidgetConfig.ALIGN_CENTER, cfg.vAlign)
                blocks += Block(RectF(mid + gap / 2, inner.top, inner.right, inner.bottom), dualRows(cfg, text.otherLabel, text.otherTime, text.otherAmpm), WidgetConfig.ALIGN_CENTER, cfg.vAlign)
            }
            WidgetConfig.LAYOUT_ANALOG -> {
                val side = min(inner.width(), inner.height())
                val r = RectF(inner.centerX() - side / 2, inner.centerY() - side / 2, inner.centerX() + side / 2, inner.centerY() + side / 2)
                analog = r
                if (cfg.showDate) {
                    val cy = r.centerY() + side * 0.22f
                    blocks += Block(
                        RectF(r.centerX() - side * 0.34f, cy - side * 0.1f, r.centerX() + side * 0.34f, cy + side * 0.1f),
                        listOf(Row(listOf(Txt(text.dateShort, cfg.dateSizeSp.toFloat(), cfg.dateColor)))),
                        WidgetConfig.ALIGN_CENTER, WidgetConfig.ALIGN_CENTER,
                    )
                }
            }
            WidgetConfig.LAYOUT_ANALOG_DIGITAL -> {
                val side = min(inner.height(), inner.width() * 0.42f)
                val r = RectF(inner.left, inner.centerY() - side / 2, inner.left + side, inner.centerY() + side / 2)
                analog = r
                val rows = ArrayList<Row>()
                rows += timeRow(cfg, text)
                if (cfg.showDate) rows += Row(listOf(Txt(text.date, cfg.dateSizeSp.toFloat(), cfg.dateColor)))
                if (cfg.showNextAlarm && text.alarm != null) rows += Row(listOf(Txt(text.alarm, cfg.alarmSizeSp.toFloat(), cfg.alarmColor)))
                blocks += Block(RectF(r.right + gap, inner.top, inner.right, inner.bottom), rows, WidgetConfig.ALIGN_START, WidgetConfig.ALIGN_CENTER)
            }
            WidgetConfig.LAYOUT_ALARM_CARD -> {
                val rows = ArrayList<Row>()
                rows += Row(listOf(Txt("Next alarm", cfg.dateSizeSp.toFloat(), cfg.dateColor)))
                rows += Row(listOf(Txt(text.alarmClock ?: "--:--", cfg.sizeSp.toFloat(), cfg.textColor)))
                rows += Row(listOf(Txt(text.alarmWhen ?: "No alarm set", cfg.alarmSizeSp.toFloat(), cfg.alarmColor)))
                if (cfg.showDate) rows += Row(listOf(Txt("Now " + text.time + text.ampm.let { if (it.isEmpty()) "" else " $it" }, cfg.dateSizeSp * 0.9f, cfg.dateColor)))
                blocks += Block(inner, rows, WidgetConfig.ALIGN_CENTER, WidgetConfig.ALIGN_CENTER)
            }
            WidgetConfig.LAYOUT_DATE_FIRST -> {
                val split = inner.left + inner.width() * 0.5f
                val dateRows = if (cfg.showDate) listOf(Row(listOf(Txt(text.date, cfg.dateSizeSp.toFloat(), cfg.dateColor)))) else emptyList()
                blocks += Block(RectF(inner.left, inner.top, split, inner.bottom), dateRows, WidgetConfig.ALIGN_START, WidgetConfig.ALIGN_CENTER)
                blocks += Block(RectF(split, inner.top, inner.right, inner.bottom), listOf(timeRow(cfg, text)), WidgetConfig.ALIGN_END, WidgetConfig.ALIGN_CENTER)
            }
            else -> {
                // STACK: same fitting rules as the live TextClock widget (drop optional lines before crushing the time)
                val fit = WidgetFitter.fit(
                    cfg.sizeSp, cfg.dateSizeSp, cfg.alarmSizeSp, cfg.worldSizeSp, cfg.paddingDp, cfg.font, text.showAmPm,
                    cfg.showDate, cfg.showNextAlarm && text.alarm != null, cfg.showWorld && text.worldLabel != null, wDp, hDp,
                )
                val k = fit.scale
                val rows = ArrayList<Row>()
                val dateRow = Row(listOf(Txt(text.date, cfg.dateSizeSp * k, cfg.dateColor)))
                if (fit.showDate && cfg.dateAbove) rows += dateRow
                rows += timeRow(cfg, text, k)
                if (fit.showDate && !cfg.dateAbove) rows += dateRow
                if (fit.showAlarm && text.alarm != null) rows += Row(listOf(Txt(text.alarm, cfg.alarmSizeSp * k, cfg.alarmColor)))
                if (fit.showWorld && text.worldLabel != null) {
                    rows += Row(
                        listOf(
                            Txt(text.worldLabel, cfg.worldSizeSp * k, cfg.worldColor),
                            Txt(text.otherTime + if (text.otherAmpm.isEmpty()) "" else " " + text.otherAmpm, cfg.worldSizeSp * k, cfg.textColor, gapDp = 6f),
                        ),
                    )
                }
                blocks += Block(inner, rows, cfg.hAlign, cfg.vAlign)
            }
        }

        // Safety net: shrink everything uniformly until every block fits its box.
        var k = 1f
        while (k > 0.3f && !blocks.all { fits(it, k, m, face, cfg.shadow, d) }) k -= 0.05f

        analog?.let { drawAnalog(canvas, it, now, cfg, d) }
        if (divider) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (cfg.textColor and 0x00FFFFFF) or (0x66 shl 24); strokeWidth = d }
            canvas.drawLine(inner.centerX(), inner.top + inner.height() * 0.2f, inner.centerX(), inner.bottom - inner.height() * 0.2f, p)
        }
        blocks.forEach { draw(canvas, it, k, m, face, cfg.shadow, d) }
        return bmp
    }

    // ------------------------------------------------------------------ text content

    private class Strings(ctx: Context, cfg: WidgetConfig, val now: ZonedDateTime) {
        private val loc = Locale.getDefault()
        val showAmPm = !cfg.use24h && cfg.showAmPm
        private val timeFmt = if (cfg.use24h) (if (cfg.leadingZero) "HH:mm" else "H:mm") else (if (cfg.leadingZero) "hh:mm" else "h:mm")
        val time: String = now.format(DateTimeFormatter.ofPattern(timeFmt, loc))
        val ampm: String = if (showAmPm) now.format(DateTimeFormatter.ofPattern("a", loc)) else ""
        val date: String = now.format(DateTimeFormatter.ofPattern(WidgetConfig.DATE_FORMATS[cfg.dateFormat.coerceIn(0, WidgetConfig.DATE_FORMATS.size - 1)], loc))
        val dateTop: String = now.format(DateTimeFormatter.ofPattern("EEE", loc))
        val dateBottom: String = now.format(DateTimeFormatter.ofPattern("d MMM", loc))
        val dateShort: String = now.format(DateTimeFormatter.ofPattern("EEE d", loc)).uppercase(loc)
        val alarm: String? = WidgetRenderer.nextAlarmText(ctx, cfg)

        private val next = AlarmScheduler.nextOverall(ctx)
        val alarmClock: String? = next?.at?.format(DateTimeFormatter.ofPattern(if (cfg.use24h) "HH:mm" else "h:mm a", loc))
        val alarmWhen: String? = next?.let {
            it.at.format(DateTimeFormatter.ofPattern("EEE", loc)) + " · in " + Fmt.until(it.at.toInstant().toEpochMilli() - System.currentTimeMillis())
        }

        private val otherZone: ZoneId? = cfg.worldZone.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(WorldEntry.zone(it)) }.getOrNull() }
        private val otherNow: ZonedDateTime? = otherZone?.let { Instant.now().atZone(it) }
        val homeLabel: String = Fmt.cityName(now.zone.id) + " · " + Fmt.utcOffset(now.offset.totalSeconds)
        val otherLabel: String = otherNow?.let { Fmt.cityName(otherZone!!.id) + " · " + Fmt.utcOffset(it.offset.totalSeconds) } ?: "Add a city"
        private val otherPattern = timeFmt
        val otherTime: String = otherNow?.format(DateTimeFormatter.ofPattern(otherPattern, loc)) ?: "--:--"
        val otherAmpm: String = if (showAmPm) otherNow?.format(DateTimeFormatter.ofPattern("a", loc)).orEmpty() else ""
        val worldLabel: String? = if (otherNow != null) otherLabel else null
    }

    private fun timeRow(cfg: WidgetConfig, t: Strings, k: Float = 1f): Row {
        val parts = ArrayList<Txt>()
        parts += Txt(t.time, cfg.sizeSp * k, cfg.textColor)
        if (t.ampm.isNotEmpty()) parts += Txt(t.ampm, (cfg.sizeSp * k * 0.32f).coerceAtLeast(12f), cfg.textColor, gapDp = 4f)
        return Row(parts)
    }

    private fun dualRows(cfg: WidgetConfig, label: String, time: String, ampm: String): List<Row> {
        val parts = ArrayList<Txt>()
        parts += Txt(time, cfg.sizeSp.toFloat(), cfg.textColor)
        if (ampm.isNotEmpty()) parts += Txt(ampm, (cfg.sizeSp * 0.32f).coerceAtLeast(12f), cfg.textColor, gapDp = 3f)
        val rows = ArrayList<Row>()
        rows += Row(listOf(Txt(label, cfg.dateSizeSp.toFloat(), cfg.dateColor)))
        rows += Row(parts)
        return rows
    }

    // ------------------------------------------------------------------ drawing

    private fun layout(rows: List<Row>, k: Float, m: android.util.DisplayMetrics, face: Typeface, shadow: Boolean, d: Float): List<Laid> =
        rows.map { row ->
            val paints = row.parts.map { p ->
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = face
                    color = p.color
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, p.sp * k, m)
                    if (shadow) setShadowLayer(4f * d, 0f, 1.5f * d, 0xB3000000.toInt())
                }
            }
            val widths = paints.mapIndexed { i, tp -> tp.measureText(row.parts[i].text) }
            val gaps = row.parts.map { it.gapDp * d }
            val ascent = paints.minOf { it.fontMetrics.ascent }
            val descent = paints.maxOf { it.fontMetrics.descent }
            Laid(paints, widths, gaps, widths.sum() + gaps.sum(), -ascent, descent - ascent)
        }

    private fun fits(b: Block, k: Float, m: android.util.DisplayMetrics, face: Typeface, shadow: Boolean, d: Float): Boolean {
        if (b.rows.isEmpty()) return true
        val laid = layout(b.rows, k, m, face, shadow, d)
        val w = laid.maxOf { it.width }
        val h = laid.fold(0f) { acc, l -> acc + l.height }
        return w <= b.rect.width() + 0.5f && h <= b.rect.height() + 0.5f
    }

    private fun draw(canvas: Canvas, b: Block, k: Float, m: android.util.DisplayMetrics, face: Typeface, shadow: Boolean, d: Float) {
        if (b.rows.isEmpty()) return
        val laid = layout(b.rows, k, m, face, shadow, d)
        val total = laid.fold(0f) { acc, l -> acc + l.height }
        var y = when (b.vAlign) {
            0 -> b.rect.top
            2 -> b.rect.bottom - total
            else -> b.rect.centerY() - total / 2f
        }
        for ((i, l) in laid.withIndex()) {
            var x = when (b.hAlign) {
                WidgetConfig.ALIGN_START -> b.rect.left
                WidgetConfig.ALIGN_END -> b.rect.right - l.width
                else -> b.rect.centerX() - l.width / 2f
            }
            val baseline = y + l.ascent
            for (j in l.paints.indices) {
                x += l.gaps[j]
                canvas.drawText(b.rows[i].parts[j].text, x, baseline, l.paints[j])
                x += l.widths[j]
            }
            y += l.height
        }
    }

    private fun drawAnalog(canvas: Canvas, rect: RectF, now: ZonedDateTime, cfg: WidgetConfig, d: Float) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) / 2f * 0.94f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            if (cfg.shadow) setShadowLayer(4f * d, 0f, 1.5f * d, 0xB3000000.toInt())
        }
        // dial ring
        line.color = (cfg.textColor and 0x00FFFFFF) or (0x88 shl 24)
        line.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, r, line)
        // 12 ticks, longer at 12/3/6/9
        line.color = cfg.textColor
        for (i in 0 until 12) {
            val long = i % 3 == 0
            line.strokeWidth = (if (long) 3f else 1.5f) * d
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            canvas.drawLine(cx, cy - r + 4f * d, cx, cy - r + (if (long) 14f else 9f) * d, line)
            canvas.restore()
        }
        val minute = now.minute
        val hourAngle = (now.hour % 12) * 30f + minute * 0.5f
        val minuteAngle = minute * 6f
        line.color = cfg.textColor
        line.strokeWidth = (r * 0.09f).coerceAtLeast(3f * d)
        canvas.save(); canvas.rotate(hourAngle, cx, cy); canvas.drawLine(cx, cy, cx, cy - r * 0.5f, line); canvas.restore()
        line.color = cfg.accentColor
        line.strokeWidth = (r * 0.06f).coerceAtLeast(2f * d)
        canvas.save(); canvas.rotate(minuteAngle, cx, cy); canvas.drawLine(cx, cy, cx, cy - r * 0.78f, line); canvas.restore()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cfg.textColor; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, 4f * d, dot)
    }
}
