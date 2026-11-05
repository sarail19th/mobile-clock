package app.personal.clock.data

import app.personal.clock.core.AlarmItem
import app.personal.clock.core.CustomDay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {

    /** Settings JSON as written by versions before holiday regions existed. */
    private val oldSettings = """
        {"followPhoneZone":false,"homeZoneId":"Asia/Bangkok","defaultSnoozeMinutes":7,"autoStopMinutes":12,
         "calendarNames":"Holidays in Thailand|วันหยุดในประเทศไทย",
         "notifyOnSkip":false,"showSecondsInApp":true}
    """.trimIndent()

    @Test fun oldSettingsKeepTheirValuesAndGetUniversalDefaults() {
        val s = AppSettings.fromJson(JSONObject(oldSettings))
        assertEquals("Asia/Bangkok", s.homeZoneId)
        assertEquals(7, s.defaultSnoozeMinutes)
        assertEquals(12, s.autoStopMinutes)
        assertFalse(s.notifyOnSkip)
        assertTrue(s.showSecondsInApp)
        // new fields
        assertEquals("", s.holidayRegion)          // automatic: country of the alarm zone
        assertTrue(s.useBuiltinHolidays)
        assertTrue(s.calendarIds.isEmpty())
    }

    @Test fun theOldThaiOnlyCalendarPatternIsMigratedAway() {
        // It would otherwise keep matching Thai calendars after the user moves to another country.
        assertEquals("", AppSettings.fromJson(JSONObject(oldSettings)).calendarNames)
    }

    @Test fun aPatternTheUserTypedThemselvesIsKept() {
        val s = AppSettings.fromJson(JSONObject("""{"calendarNames":"Work holidays|Bank days"}"""))
        assertEquals("Work holidays|Bank days", s.calendarNames)
        assertEquals(listOf("Work holidays", "Bank days"), s.calendarPatterns())
    }

    @Test fun emptyOrMissingSettingsAreTheDefaults() {
        assertEquals(AppSettings(), AppSettings.fromJson(null))
        assertEquals(AppSettings(), AppSettings.fromJson(JSONObject("{}")))
    }

    @Test fun newSettingsRoundTrip() {
        val s = AppSettings(
            followPhoneZone = true, homeZoneId = "Asia/Tokyo", holidayRegion = "JP",
            useBuiltinHolidays = false, calendarIds = listOf(3L, 41L), calendarNames = "Extra",
        )
        assertEquals(s, AppSettings.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test fun zoneFollowsThePhoneOrTheHomeSetting() {
        assertEquals("Asia/Tokyo", AppSettings(homeZoneId = "Asia/Tokyo").zone().id)
        assertEquals(java.time.ZoneId.systemDefault(), AppSettings(followPhoneZone = true, homeZoneId = "Asia/Tokyo").zone())
        assertEquals("Asia/Bangkok", AppSettings(homeZoneId = "Not/AZone").zone().id) // bad id falls back safely
    }

    // ---- alarms
    @Test fun alarmsFromOlderVersionsDefaultToNoCustomDaySkipping() {
        val old = JSONObject("""{"id":5,"label":"Morning","hour":7,"minute":30,"days":31,"enabled":true,"skipHolidays":true}""")
        val a = Store.alarmFromJson(old)
        assertTrue(a.skipHolidays)
        assertFalse(a.skipCustomDays)
        assertEquals("", a.group)
    }

    @Test fun alarmSkipFlagsRoundTrip() {
        val a = AlarmItem(9, "Gym", 6, 0, AlarmItem.WEEKDAYS, skipHolidays = false, skipCustomDays = true, group = "Home")
        assertEquals(a, Store.alarmFromJson(JSONObject(Store.alarmToJson(a).toString())))
    }

    // ---- custom days
    @Test fun customDaysRoundTripIncludingRangesAndYearly() {
        val one = CustomDay(1, "Dentist", LocalDate.of(2026, 12, 9))
        val range = CustomDay(2, "Leave", LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 24))
        val yearly = CustomDay(3, "Anniversary", LocalDate.of(2026, 2, 14), null, yearly = true)
        for (c in listOf(one, range, yearly)) {
            assertEquals(c, Store.customDayFromJson(JSONObject(Store.customDayToJson(c).toString())))
        }
        assertNull(Store.customDayFromJson(JSONObject(Store.customDayToJson(one).toString())).to)
    }
}
