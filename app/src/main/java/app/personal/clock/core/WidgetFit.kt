package app.personal.clock.core

/**
 * Decides how big the time can be and which optional lines still fit, for a widget of a given size.
 * Used by both the real RemoteViews renderer and the config-screen preview so they always agree.
 */
data class WidgetFit(
    val sizeSp: Float,
    val showDate: Boolean,
    val showAlarm: Boolean,
    val showWorld: Boolean,
) {
    /** Size for the secondary lines (date, next alarm, second clock). */
    val subSp: Float get() = WidgetFitter.sub(sizeSp)

    val ampmSp: Float get() = (sizeSp * 0.32f).coerceAtLeast(12f)
}

object WidgetFitter {
    const val MONO_FONT = 6

    fun sub(sz: Float): Float = (sz * 0.28f).coerceIn(12f, 28f)

    fun fit(
        wantedSp: Int,
        paddingDp: Int,
        font: Int,
        showAmPm: Boolean,
        wantDate: Boolean,
        wantAlarm: Boolean,
        wantWorld: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): WidgetFit {
        val availH = (heightDp - 2 * paddingDp).coerceAtLeast(20)
        val availW = (widthDp - 2 * paddingDp).coerceAtLeast(40)
        var date = wantDate
        var alarm = wantAlarm
        var world = wantWorld

        fun needH(sz: Float): Float {
            var h = sz * 1.2f
            if (date) h += sub(sz) * 1.3f
            if (alarm) h += sub(sz) * 0.9f * 1.3f
            if (world) h += sub(sz) * 0.9f * 1.3f
            return h
        }
        fun needW(sz: Float): Float = sz * (if (font == MONO_FONT) 3.2f else 2.9f) + if (showAmPm) sz * 0.5f else 0f
        fun fitSize(): Float {
            var s = wantedSp.toFloat()
            while (s > 20f && (needH(s) > availH || needW(s) > availW)) s -= 2f
            return s
        }

        // Prefer dropping optional lines (world, then next alarm, then date) over shrinking the time below 70%.
        var size = fitSize()
        val drops = listOf({ world = false }, { alarm = false }, { date = false })
        var i = 0
        while (size < wantedSp * 0.7f && i < drops.size) {
            drops[i++]()
            size = fitSize()
        }
        return WidgetFit(size, date, alarm, world)
    }
}
