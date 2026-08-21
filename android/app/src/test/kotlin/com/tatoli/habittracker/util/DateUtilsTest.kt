package com.tatoli.habittracker.util

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun weekKey_returnsIsoWeekFormat() {
        // 2026-08-09 ist ein Sonntag, ISO-Kalenderwoche 32
        assertEquals("2026-W32", weekKey(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun isoWeekNumber_matchesWeekKey() {
        assertEquals(32, isoWeekNumber(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun mondayOf_returnsPrecedingOrSameMonday() {
        assertEquals(LocalDate.of(2026, 8, 3), mondayOf(LocalDate.of(2026, 8, 9)))
        assertEquals(LocalDate.of(2026, 8, 3), mondayOf(LocalDate.of(2026, 8, 3)))
    }

    @Test
    fun monthWeeks_returnsMondaysTouchingTheMonth() {
        // August 2026 beginnt Samstag (2026-08-01), endet Montag (2026-08-31)
        val weeks = monthWeeks(YearMonth.of(2026, 8))
        assertEquals(LocalDate.of(2026, 7, 27), weeks.first())
        assertEquals(LocalDate.of(2026, 8, 31), weeks.last())
        assertEquals(6, weeks.size)
    }

    @Test
    fun monthDayCount_returnsDaysInMonth() {
        assertEquals(31, monthDayCount(YearMonth.of(2026, 8)))
        assertEquals(28, monthDayCount(YearMonth.of(2026, 2)))
    }

    @Test
    fun firstDayOfWeekOffset_mondayIsZero() {
        // 2026-08-01 ist ein Samstag -> Offset 5 (Mo=0..So=6)
        assertEquals(5, firstDayOfWeekOffset(YearMonth.of(2026, 8)))
    }

    @Test
    fun streak_countsConsecutiveDaysEndingToday_todayMayBeOpen() {
        val today = LocalDate.of(2026, 8, 9)
        val done = setOf("2026-08-08", "2026-08-07", "2026-08-06")
        assertEquals(3, streak(done, today))
    }

    @Test
    fun streak_breaksOnGap() {
        val today = LocalDate.of(2026, 8, 9)
        val done = setOf("2026-08-09", "2026-08-08", "2026-08-06")
        assertEquals(2, streak(done, today))
    }

    @Test
    fun weekStreak_currentWeekOpen_countsPastConsecutiveWeeks() {
        val today = LocalDate.of(2026, 8, 9) // KW 32, noch nicht abgehakt
        val done = setOf(weekKey(LocalDate.of(2026, 8, 1)), weekKey(LocalDate.of(2026, 7, 25)))
        assertEquals(2, weekStreak(done, today))
    }

    @Test
    fun weekStreak_currentWeekDone_includesItInCount() {
        val today = LocalDate.of(2026, 8, 9) // KW 32
        val done = setOf(
            weekKey(LocalDate.of(2026, 8, 9)),
            weekKey(LocalDate.of(2026, 8, 1))
        )
        assertEquals(2, weekStreak(done, today))
    }
}
