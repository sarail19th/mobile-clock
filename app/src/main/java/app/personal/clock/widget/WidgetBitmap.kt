package app.personal.clock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import app.personal.clock.data.WidgetConfig

/**
 * Draws the widget's text onto a bitmap in any typeface.
 *
 * Why: launchers (Samsung's included) ignore app-bundled fonts inside RemoteViews, so a custom font
 * can only reach the home screen as a picture. The picture is refreshed once a minute by [WidgetTicker].
 */
object WidgetBitmap {

    /** One run of text. [gapDp] is empty space drawn before it (e.g. between the time and "PM"). */
    class Part(val text: String, val sp: Float, val color: Int, val gapDp: Float = 0f)

    /** Parts share one baseline, left to right. */
    class Line(val parts: List<Part>)

    private class Laid(
        val paints: List<TextPaint>,
        val widths: List<Float>,
        val gaps: List<Float>,
        val width: Float,
        val ascent: Float,
        val height: Float,
    )

    fun render(ctx: Context, cfg: WidgetConfig, typeface: Typeface, widthDp: Int, heightDp: Int, lines: List<Line>): Bitmap {
        val m = ctx.resources.displayMetrics
        val d = m.density
        val w = (widthDp * d).toInt().coerceIn(1, 1800)
        val h = (heightDp * d).toInt().coerceIn(1, 1800)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pad = cfg.paddingDp * d

        val laid = lines.map { line ->
            val paints = line.parts.map { p ->
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    color = p.color
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, p.sp, m)
                    if (cfg.shadow) setShadowLayer(4f * d, 0f, 1.5f * d, 0xB3000000.toInt())
                }
            }
            val widths = paints.mapIndexed { i, tp -> tp.measureText(line.parts[i].text) }
            val gaps = line.parts.map { it.gapDp * d }
            val ascent = paints.minOf { it.fontMetrics.ascent } // negative: distance above the baseline
            val descent = paints.maxOf { it.fontMetrics.descent }
            Laid(paints, widths, gaps, widths.sum() + gaps.sum(), -ascent, descent - ascent)
        }

        val total = laid.fold(0f) { acc, l -> acc + l.height }
        var y = when (cfg.vAlign) {
            0 -> pad
            2 -> h - pad - total
            else -> (h - total) / 2f
        }
        for ((i, l) in laid.withIndex()) {
            var x = when (cfg.hAlign) {
                WidgetConfig.ALIGN_START -> pad
                WidgetConfig.ALIGN_END -> w - pad - l.width
                else -> (w - l.width) / 2f
            }
            val baseline = y + l.ascent
            for (j in l.paints.indices) {
                x += l.gaps[j]
                canvas.drawText(lines[i].parts[j].text, x, baseline, l.paints[j])
                x += l.widths[j]
            }
            y += l.height
        }
        return bmp
    }
}
