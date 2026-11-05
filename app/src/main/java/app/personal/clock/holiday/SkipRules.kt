package app.personal.clock.holiday

import android.content.Context
import app.personal.clock.core.AlarmItem
import app.personal.clock.core.CustomDay
import app.personal.clock.data.Store
import java.time.LocalDate

/**
 * Decides whether one alarm skips one date, applying THAT alarm's own switches: public holidays and/or the user's
 * custom days. [date] must be the local date in the zone the alarm rings in (see core.SkipDates).
 */
object SkipRules {

    /** The reason to skip ([date]'s holiday name or custom-day label), or null to ring normally. */
    fun reason(
        ctx: Context,
        alarm: AlarmItem,
        date: LocalDate,
        fresh: Boolean = false,
        customDays: List<CustomDay> = Store.get(ctx).customDays(),
    ): String? {
        if (alarm.skipHolidays) HolidayChecker.check(ctx, date, fresh).name?.let { return it }
        if (alarm.skipCustomDays) customDays.firstOrNull { it.matches(date) }?.let { return it.label.ifBlank { "Custom day" } }
        return null
    }

    /** Function form for AlarmMath. Reads the custom days once. */
    fun lookupFor(ctx: Context, alarm: AlarmItem): (LocalDate) -> String? {
        val custom = Store.get(ctx).customDays()
        return { date -> reason(ctx, alarm, date, false, custom) }
    }
}
