package app.personal.clock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.personal.clock.core.WidgetFitter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CoreTest {
    private val bkk = ZoneId.of("Asia/Bangkok")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = bkk): Instant =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    private val morningMon = AlarmItem(1, "Morning Mon", 5, 30, AlarmItem.mask(DayOfWeek.MONDAY))
    private val morningTueFri = AlarmItem(
        2, "Morning Tue-Fri", 7, 30,
        AlarmItem.mask(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    )
    private val afternoon = AlarmItem(3, "Afternoon", 16, 30, AlarmItem.WEEKDAYS)

    @Test fun mondayAlarmFromSundayFindsNextMonday() {
        // 2026-11-01 is a Sunday
        val n = AlarmMath.next(morningMon, at(2026, 11, 1, 12, 0), bkk) { null }
        assertNotNull(n)
        assertEquals(LocalDate.of(2026, 11, 2), n!!.at.toLocalDate())
        assertEquals(5, n.at.hour); assertEquals(30, n.at.minute)
    }

    @Test fun alreadyPassedTodayMovesToNextMatchingDay() {
        // Wed 2026-11-04 08:00 -> Tue-Fri 07:30 alarm already passed -> Thursday
        val n = AlarmMath.next(morningTueFri, at(2026, 11, 4, 8, 0), bkk) { null }!!
        assertEquals(LocalDate.of(2026, 11, 5), n.at.toLocalDate())
    }

    @Test fun exactlyAtTriggerTimeGoesToNextOccurrence() {
        val n = AlarmMath.next(afternoon, at(2026, 11, 4, 16, 30), bkk) { null }!!
        assertEquals(LocalDate.of(2026, 11, 5), n.at.toLocalDate())
    }

    @Test fun fridayAfternoonRollsToMonday() {
        // Fri 2026-11-06 17:00
        val n = AlarmMath.next(afternoon, at(2026, 11, 6, 17, 0), bkk) { null }!!
        assertEquals(DayOfWeek.MONDAY, n.at.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 9), n.at.toLocalDate())
    }

    @Test fun holidayIsSkippedAndReported() {
        // Songkran-like: Tue 2026-04-14 is a holiday; asking on Mon 2026-04-13 evening
        val holidays = mapOf(LocalDate.of(2026, 4, 14) to "Songkran", LocalDate.of(2026, 4, 15) to "Songkran")
        val n = AlarmMath.next(morningTueFri, at(2026, 4, 13, 20, 0), bkk) { holidays[it] }!!
        assertEquals(LocalDate.of(2026, 4, 16), n.at.toLocalDate())
        assertEquals(2, n.skipped.size)
        assertEquals("Songkran", n.skipped.first().name)
        assertEquals(LocalDate.of(2026, 4, 14), n.skipped.first().date)
    }

    @Test fun skipFlagOffIgnoresHolidays() {
        val a = morningTueFri.copy(skipHolidays = false)
        val n = AlarmMath.next(a, at(2026, 4, 13, 20, 0), bkk) { "Anything" }!!
        assertEquals(LocalDate.of(2026, 4, 14), n.at.toLocalDate())
        assertTrue(n.skipped.isEmpty())
    }

    @Test fun oneShotRingsTodayIfStillAheadElseTomorrowAndIgnoresHolidays() {
        val a = AlarmItem(9, "once", 9, 0, 0)
        val today = AlarmMath.next(a, at(2026, 11, 4, 8, 0), bkk) { "Holiday" }!!
        assertEquals(LocalDate.of(2026, 11, 4), today.at.toLocalDate())
        val tomorrow = AlarmMath.next(a, at(2026, 11, 4, 10, 0), bkk) { "Holiday" }!!
        assertEquals(LocalDate.of(2026, 11, 5), tomorrow.at.toLocalDate())
    }

    @Test fun alarmFiresInConfiguredZoneRegardlessOfPhoneZone() {
        // Same instant, evaluated in Bangkok: 07:30 Bangkok == 08:30 Tokyo. The result must be 07:30 in Bangkok.
        val now = at(2026, 11, 4, 0, 0)
        val n = AlarmMath.next(afternoon.copy(hour = 7, minute = 30), now, bkk) { null }!!
        assertEquals(7, n.at.hour)
        assertEquals(ZoneId.of("Asia/Bangkok"), n.at.zone)
        assertEquals(at(2026, 11, 4, 7, 30), n.at.toInstant())
        assertEquals(at(2026, 11, 4, 9, 30, ZoneId.of("Asia/Tokyo")), n.at.toInstant())
    }

    @Test fun noDaysAndNothingToSchedule() {
        // Every day is a holiday -> bounded loop returns null instead of spinning forever.
        assertNull(AlarmMath.next(afternoon, at(2026, 11, 4, 8, 0), bkk) { "Always" })
    }

    @Test fun utcOffsetLabels() {
        assertEquals("UTC+7", Fmt.utcOffset(7 * 3600))
        assertEquals("UTC", Fmt.utcOffset(0))
        assertEquals("UTC+5:30", Fmt.utcOffset(5 * 3600 + 1800))
        assertEquals("UTC-3:30", Fmt.utcOffset(-(3 * 3600 + 1800)))
        assertEquals("UTC-5", Fmt.utcOffset(-5 * 3600))
        assertEquals("UTC+5:45", Fmt.utcOffset(5 * 3600 + 45 * 60))
    }

    @Test fun dstZonesGiveDifferentOffsetsInSummerAndWinter() {
        val ny = ZoneId.of("America/New_York")
        val winter = ny.rules.getOffset(at(2026, 1, 15, 12, 0, ny)).totalSeconds
        val summer = ny.rules.getOffset(at(2026, 7, 15, 12, 0, ny)).totalSeconds
        assertEquals("UTC-5", Fmt.utcOffset(winter))
        assertEquals("UTC-4", Fmt.utcOffset(summer))
    }

    @Test fun everyBuiltInCityHasARealTimeZone() {
        val bad = Cities.all.filter { runCatching { ZoneId.of(it.zoneId) }.isFailure }.map { it.name + "=" + it.zoneId }
        assertTrue("invalid zones: $bad", bad.isEmpty())
        assertTrue(Cities.all.size > 200)
        assertEquals("Europe/Paris", Cities.all.first { it.name == "Marseille" }.zoneId)
        assertEquals("Asia/Shanghai", Cities.all.first { it.name == "Beijing" }.zoneId)
    }

    @Test fun worldEntriesKeepTheChosenName() {
        assertEquals("Europe/Paris", WorldEntry.zone("Europe/Paris|Marseille"))
        assertEquals("Marseille", WorldEntry.label("Europe/Paris|Marseille"))
        assertEquals("Paris", WorldEntry.label("Europe/Paris"))
        assertEquals("Europe/Paris", WorldEntry.make("Europe/Paris", "Paris"))
        assertEquals("Asia/Shanghai|Beijing", WorldEntry.make("Asia/Shanghai", "Beijing"))
    }

    @Test fun alarmsDefaultToNoGroup() {
        assertEquals("", AlarmItem(1, "x", 7, 0, 0).group)
        assertEquals("Work", AlarmItem(1, "x", 7, 0, 0, group = "Work").group)
    }

    @Test fun hoursDifference() {
        assertEquals("+2 h", Fmt.hoursDiff(2 * 3600))
        assertEquals("\u22127 h", Fmt.hoursDiff(-7 * 3600))
        assertEquals("+5:30 h", Fmt.hoursDiff(5 * 3600 + 1800))
        assertEquals("same time", Fmt.hoursDiff(0))
    }

    @Test fun untilFormatting() {
        assertEquals("< 1 m", Fmt.until(0))
        assertEquals("1 m", Fmt.until(1))
        assertEquals("1 m", Fmt.until(60_000))
        assertEquals("2 m", Fmt.until(60_001))
        assertEquals("3 h 20 m", Fmt.until((3 * 60 + 20) * 60_000L))
        assertEquals("2 d 4 h", Fmt.until(((2 * 24 + 4) * 60 + 10) * 60_000L))
    }

    @Test fun timerAndStopwatchFormatting() {
        assertEquals("05:00", Fmt.clockHms(300_000))
        assertEquals("1:00:00", Fmt.clockHms(3_600_000))
        assertEquals("00:01", Fmt.clockHms(1))
        assertEquals("01:23.45", Fmt.stopwatch(83_459))
        assertEquals("1:01:01.00", Fmt.stopwatch(3_661_000))
    }
}

class WidgetFitTest {
    private fun fit(w: Int, h: Int, size: Int = 64, alarm: Boolean = true, world: Boolean = false, ampm: Boolean = false) =
        WidgetFitter.fit(size, 18, 15, 14, 12, 0, ampm, true, alarm, world, w, h)

    @Test fun roomyWidgetKeepsEverythingAtRequestedSize() {
        val f = fit(360, 200)
        assertEquals(1f, f.scale)
        assertTrue(f.showDate && f.showAlarm)
    }

    @Test fun smallWidgetShrinksOrDropsLinesBeforeCrushingTheTime() {
        val f = fit(250, 110)
        assertTrue(f.scale >= 0.69f)
    }

    @Test fun veryShortWidgetStillShowsTheTime() {
        val f = fit(250, 56)
        assertTrue(f.scale * 64 >= 19.9f)
        assertTrue(!f.showDate && !f.showAlarm && !f.showWorld)
    }

    @Test fun narrowWidgetShrinksTimeToWidth() {
        val f = fit(120, 300, size = 120)
        assertTrue(f.scale * 120 * 2.9f <= 120 - 24 + 0.5f)
    }

    @Test fun lineSizesAreScaledTogether() {
        val f = fit(250, 110)
        // every line shares one scale, so the user's size ratios are preserved
        assertTrue(f.scale in 0.3f..1f)
    }
}
