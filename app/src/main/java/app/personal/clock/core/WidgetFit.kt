package app.personal.clock.core

/**
 * Decides how much the text of a stacked widget must be scaled down, and which optional lines to drop,
 * so everything fits a widget of a given size. Used by the real renderer and the editor preview alike.
 *
 * [scale] multiplies EVERY line's size (time, date, next alarm, second clock) so the proportions the user
 * chose are kept; lines are dropped (world, then alarm, then date) before the text shrinks below 70%.
 */
data class WidgetFit(
    val scale: Float,
    val showDate: Boolean,
    val showAlarm: Boolean,
    val showWorld: Boolean,
)

object WidgetFitter {
    const val MONO_FONT = 5
    const val MIN_TIME_SP = 20f

    fun fit(
        timeSp: Int,
        dateSp: Int,
        alarmSp: Int,
        worldSp: Int,
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
        val minK = (MIN_TIME_SP / timeSp.coerceAtLeast(1)).coerceAtMost(1f)

        fun needH(k: Float): Float {
            var h = timeSp * 1.2f
            if (date) h += dateSp * 1.3f
            if (alarm) h += alarmSp * 1.3f
            if (world) h += worldSp * 1.3f
            return h * k
        }
        fun needW(k: Float): Float = k * timeSp * ((if (font == MONO_FONT) 3.2f else 2.9f) + if (showAmPm) 0.5f else 0f)
        fun best(): Float {
            var k = 1f
            while (k > minK && (needH(k) > availH || needW(k) > availW)) k -= 0.02f
            return k.coerceAtLeast(minK)
        }

        var k = best()
        val drops = listOf({ world = false }, { alarm = false }, { date = false })
        var i = 0
        while (k < 0.7f && i < drops.size) {
            drops[i++]()
            k = best()
        }
        return WidgetFit(k, date, alarm, world)
    }
}
