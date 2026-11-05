package app.personal.clock.widget

import app.personal.clock.data.WidgetConfig

/** A starting point for a widget: sets the layout and the sizes/visibility that suit it. Everything stays editable afterwards. */
class WidgetTemplate(
    val id: Int,
    val name: String,
    val sizeLabel: String,
    /** The size (dp) a widget of this kind is normally placed at; the picker thumbnails are drawn at it. */
    val nominalW: Int,
    val nominalH: Int,
    val blurb: String,
)

object WidgetTemplates {
    val all = listOf(
        WidgetTemplate(0, "Classic", "4×2", 330, 150, "Date, big time and the next alarm"),
        WidgetTemplate(1, "Compact", "2×1", 160, 74, "Time with a two-line date beside it"),
        WidgetTemplate(2, "Dual clock", "4×2", 330, 150, "Home city and one more side by side"),
        WidgetTemplate(3, "Analog", "2×2", 150, 150, "Round face with the date"),
        WidgetTemplate(4, "Analog + digital", "4×2", 330, 150, "Round face, numbers and lines"),
        WidgetTemplate(5, "Big minimal", "4×2", 330, 150, "One huge thin time, no panel"),
        WidgetTemplate(6, "Date first", "4×1", 330, 74, "Date on the left, time on the right"),
        WidgetTemplate(7, "Alarm card", "2×2", 150, 150, "Your next alarm is the star"),
    )

    /** [worldFallback] is used for templates that need a second city when none is chosen yet. */
    fun apply(base: WidgetConfig, id: Int, worldFallback: String): WidgetConfig = when (id) {
        1 -> base.copy(
            layout = WidgetConfig.LAYOUT_ROW, sizeSp = 38, dateSizeSp = 13, showDate = true, showNextAlarm = false,
            showWorld = false, paddingDp = 10, hAlign = WidgetConfig.ALIGN_CENTER, vAlign = WidgetConfig.ALIGN_CENTER,
        )
        2 -> base.copy(
            layout = WidgetConfig.LAYOUT_DUAL, sizeSp = 46, dateSizeSp = 13, showDate = true, showNextAlarm = false,
            showWorld = true, worldZone = base.worldZone.ifBlank { worldFallback }, paddingDp = 12,
        )
        3 -> base.copy(
            layout = WidgetConfig.LAYOUT_ANALOG, sizeSp = 40, dateSizeSp = 13, showDate = true, showNextAlarm = false,
            showWorld = false, cornerStep = WidgetConfig.MAX_CORNER_STEP, paddingDp = 10,
        )
        4 -> base.copy(
            layout = WidgetConfig.LAYOUT_ANALOG_DIGITAL, order = WidgetConfig.DEFAULT_ORDER, sizeSp = 46, dateSizeSp = 15, alarmSizeSp = 13, showDate = true,
            showNextAlarm = true, showWorld = false, paddingDp = 12,
        )
        5 -> base.copy(
            layout = WidgetConfig.LAYOUT_STACK, sizeSp = 104, showDate = false, showNextAlarm = false, showWorld = false,
            bgMode = WidgetConfig.BG_TRANSPARENT, shadow = true, hAlign = WidgetConfig.ALIGN_CENTER, vAlign = WidgetConfig.ALIGN_CENTER,
        )
        6 -> base.copy(
            layout = WidgetConfig.LAYOUT_DATE_FIRST, sizeSp = 38, dateSizeSp = 26, showDate = true, showNextAlarm = false,
            showWorld = false, paddingDp = 14,
        )
        7 -> base.copy(
            layout = WidgetConfig.LAYOUT_ALARM_CARD, sizeSp = 48, dateSizeSp = 13, alarmSizeSp = 13, showDate = true,
            showNextAlarm = true, showWorld = false, paddingDp = 12, cornerStep = 6,
        )
        else -> base.copy(
            layout = WidgetConfig.LAYOUT_STACK, sizeSp = 64, dateSizeSp = 18, alarmSizeSp = 15, showDate = true, order = WidgetConfig.DEFAULT_ORDER,
            showNextAlarm = true, showWorld = false, paddingDp = 12,
        )
    }

    /** Which template a saved config most resembles (for highlighting in the picker). */
    fun guess(cfg: WidgetConfig): Int = when (cfg.layout) {
        WidgetConfig.LAYOUT_ROW -> 1
        WidgetConfig.LAYOUT_DUAL -> 2
        WidgetConfig.LAYOUT_ANALOG -> 3
        WidgetConfig.LAYOUT_ANALOG_DIGITAL -> 4
        WidgetConfig.LAYOUT_DATE_FIRST -> 6
        WidgetConfig.LAYOUT_ALARM_CARD -> 7
        else -> if (cfg.sizeSp >= 100 && !cfg.showDate) 5 else 0
    }
}
