package com.focusforceplus.app.study.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class StudyCalendarTest {

    @Test
    fun `month grid has fixed 42 cells starting on monday`() {
        // 2026-11-01 is Sunday -> 6 leading blanks, 30 days, 6 trailing blanks = 42 cells.
        val nov = calendarMonthGrid(YearMonth.of(2026, 11))
        assertEquals(42, nov.size)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 11, 1).dayOfWeek)
        assertTrue(nov.take(6).all { it == null })
        assertEquals(LocalDate.of(2026, 11, 1), nov[6])
        assertEquals(LocalDate.of(2026, 11, 30), nov[35])
        assertTrue(nov.drop(36).all { it == null })
    }

    @Test
    fun `month grid handles leap year february and monday start`() {
        val leapFeb = calendarMonthGrid(YearMonth.of(2024, 2))
        assertEquals(42, leapFeb.size)
        val nonNullDays = leapFeb.filterNotNull()
        assertEquals(29, nonNullDays.size)
        assertEquals(LocalDate.of(2024, 2, 29), nonNullDays.last())

        // 2027-02-01 is Monday -> 0 leading blanks, 28 days, 14 trailing blanks.
        val mondayFeb = calendarMonthGrid(YearMonth.of(2027, 2))
        assertEquals(42, mondayFeb.size)
        assertEquals(LocalDate.of(2027, 2, 1), mondayFeb[0])
        assertEquals(LocalDate.of(2027, 2, 28), mondayFeb[27])
        assertTrue(mondayFeb.drop(28).all { it == null })
    }

    @Test
    fun `selectability enforces inclusive min and max dates`() {
        val min = LocalDate.of(2026, 11, 14)
        val max = LocalDate.of(2026, 12, 31)
        assertFalse(isDateSelectable(LocalDate.of(2026, 11, 13), minDate = min))
        assertTrue(isDateSelectable(min, minDate = min))
        assertTrue(isDateSelectable(max, minDate = min, maxDate = max))
        assertFalse(isDateSelectable(LocalDate.of(2027, 1, 1), minDate = min, maxDate = max))
    }

    @Test
    fun `month clamping and month selectability respect min date`() {
        val min = LocalDate.of(2026, 11, 14)
        val minMonth = YearMonth.of(2026, 11)
        val maxMonth = YearMonth.of(2030, 12)
        assertEquals(minMonth, clampMonth(YearMonth.of(2026, 3), minMonth, maxMonth))
        assertFalse(canSelectMonth(YearMonth.of(2026, 10), minDate = min))
        assertTrue(canSelectMonth(YearMonth.of(2026, 11), minDate = min))
        assertTrue(canSelectMonth(YearMonth.of(2027, 1), minDate = min))
    }

    @Test
    fun `display formatting labels today tomorrow yesterday and weekday`() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals("", formatStudyDateDisplay("", today))
        assertEquals("2026-11-14 · 今天", formatStudyDateDisplay("2026-11-14", today))
        assertEquals("2026-11-15 · 明天", formatStudyDateDisplay("2026-11-15", today))
        assertEquals("2026-11-13 · 昨天", formatStudyDateDisplay("2026-11-13", today))
        assertEquals("2026-11-18 · 周三", formatStudyDateDisplay("2026-11-18", today))
    }

    @Test
    fun `parseStudyDate trims valid dates and rejects invalid strings`() {
        assertEquals(LocalDate.of(2026, 11, 14), parseStudyDate(" 2026-11-14 "))
        assertNull(parseStudyDate(""))
        assertNull(parseStudyDate("2026-02-30"))
        assertNull(parseStudyDate("not-a-date"))
    }
}
