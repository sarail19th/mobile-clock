package app.personal.clock.widget

import app.personal.clock.data.WidgetConfig

/**
 * Which editor settings each widget layout actually uses. Layouts other than Classic have a fixed arrangement,
 * so e.g. alignment or "date above/below" would silently do nothing there; the editor hides or disables those
 * controls using this table. Keep it in sync with [WidgetScene].
 */
class LayoutCaps(
    val name: String,
    val hAlign: Boolean,
    val vAlign: Boolean,
    val datePosition: Boolean,
    val timeSize: Boolean,
    val timeFormat: Boolean,
    val dateLine: Boolean,
    val dateSwitch: Boolean,
    val dateFormat: Boolean,
    val alarmLine: Boolean,
    val alarmSwitch: Boolean,
    val worldLine: Boolean,
    val worldSwitch: Boolean,
    val worldStyle: Boolean,
    val accent: Boolean,
    /** What the date line is used for, when it isn't the date itself. */
    val dateNote: String = "",
) {
    companion object {
        fun of(layout: Int): LayoutCaps = when (layout) {
            WidgetConfig.LAYOUT_ROW -> LayoutCaps(
                "Compact", hAlign = false, vAlign = true, datePosition = false, timeSize = true, timeFormat = true,
                dateLine = true, dateSwitch = true, dateFormat = false, alarmLine = false, alarmSwitch = false,
                worldLine = false, worldSwitch = false, worldStyle = false, accent = false,
                dateNote = "Shown as two short lines beside the time.",
            )
            WidgetConfig.LAYOUT_DUAL -> LayoutCaps(
                "Dual clock", hAlign = false, vAlign = true, datePosition = false, timeSize = true, timeFormat = true,
                dateLine = true, dateSwitch = false, dateFormat = false, alarmLine = false, alarmSwitch = false,
                worldLine = true, worldSwitch = false, worldStyle = false, accent = false,
                dateNote = "Size and colour are used for the two city names.",
            )
            WidgetConfig.LAYOUT_ANALOG -> LayoutCaps(
                "Analog", hAlign = false, vAlign = false, datePosition = false, timeSize = false, timeFormat = false,
                dateLine = true, dateSwitch = true, dateFormat = false, alarmLine = false, alarmSwitch = false,
                worldLine = false, worldSwitch = false, worldStyle = false, accent = true,
                dateNote = "Shown as a short date inside the face.",
            )
            WidgetConfig.LAYOUT_ANALOG_DIGITAL -> LayoutCaps(
                "Analog + digital", hAlign = false, vAlign = false, datePosition = false, timeSize = true, timeFormat = true,
                dateLine = true, dateSwitch = true, dateFormat = true, alarmLine = true, alarmSwitch = true,
                worldLine = false, worldSwitch = false, worldStyle = false, accent = true,
            )
            WidgetConfig.LAYOUT_ALARM_CARD -> LayoutCaps(
                "Alarm card", hAlign = false, vAlign = false, datePosition = false, timeSize = true, timeFormat = true,
                dateLine = true, dateSwitch = true, dateFormat = false, alarmLine = true, alarmSwitch = false,
                worldLine = false, worldSwitch = false, worldStyle = false, accent = false,
                dateNote = "Used for the small \"Now\" line and the \"Next alarm\" caption.",
            )
            WidgetConfig.LAYOUT_DATE_FIRST -> LayoutCaps(
                "Date first", hAlign = false, vAlign = false, datePosition = false, timeSize = true, timeFormat = true,
                dateLine = true, dateSwitch = true, dateFormat = true, alarmLine = false, alarmSwitch = false,
                worldLine = false, worldSwitch = false, worldStyle = false, accent = false,
            )
            else -> LayoutCaps(
                "Classic", hAlign = true, vAlign = true, datePosition = true, timeSize = true, timeFormat = true,
                dateLine = true, dateSwitch = true, dateFormat = true, alarmLine = true, alarmSwitch = true,
                worldLine = true, worldSwitch = true, worldStyle = true, accent = false,
            )
        }
    }
}
