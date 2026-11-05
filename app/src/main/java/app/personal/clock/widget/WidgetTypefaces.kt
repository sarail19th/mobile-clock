package app.personal.clock.widget

import android.content.Context
import android.graphics.Typeface
import app.personal.clock.R

/** The widget fonts. Index 0 is the phone's own font; the rest are bundled static TTFs used when drawing the widget as a picture. */
object WidgetTypefaces {
    val names = listOf("System", "Clean", "Rounded", "Cute", "Soft", "Mono", "Serif", "Condensed", "Thin")

    private val res = intArrayOf(
        0, R.font.dm_sans, R.font.nunito, R.font.fredoka, R.font.quicksand,
        R.font.jetbrains_mono, R.font.playfair_display, R.font.barlow_condensed, R.font.poppins_thin,
    )

    fun typeface(ctx: Context, index: Int): Typeface {
        val i = index.coerceIn(0, res.size - 1)
        if (i == 0) return Typeface.DEFAULT
        return runCatching { ctx.resources.getFont(res[i]) }.getOrDefault(Typeface.DEFAULT)
    }
}
