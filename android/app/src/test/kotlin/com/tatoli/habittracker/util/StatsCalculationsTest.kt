package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitDoneEntity
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitWithDoneEntities
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculationsTest {

    private fun entry(
        freq: String,
        createdAt: Long,
        doneKeys: List<String>,
        group: String = ""
    ) = HabitWithDoneEntities(
        habit = HabitEntity(id = 1, name = "H", color = "#F2B450", freq = freq, group = group, createdAt = createdAt),
        doneEntries = doneKeys.map { HabitDoneEntity(habitId = 1, dateKey = it) }
    )

    @Test
    fun successRate_daily_countsDoneOverDaysSinceCreation() {
        val created = LocalDate.of(2026, 8, 1)
        val today = LocalDate.of(2026, 8, 10) // 10 Tage inklusive
        val e = entry("daily", epochMillisOf(created), listOf("2026-08-01", "2026-08-05", "2026-08-10"))
        assertEquals(30, successRate(e, today)) // 3/10 = 30%
    }

    @Test
    fun successRate_daily_cappedAt100() {
        val created = LocalDate.of(2026, 8, 9)
        val today = LocalDate.of(2026, 8, 9)
        val e = entry("daily", epochMillisOf(created), listOf("2026-08-09", "2026-08-09"))
        assertEquals(100, successRate(e, today))
    }

    @Test
    fun successRate_weekly_countsDoneOverWeeksSinceCreation() {
        val created = LocalDate.of(2026, 8, 3) // Montag KW32
        val today = LocalDate.of(2026, 8, 17)  // Montag KW34 -> 3 Wochen (32,33,34)
        val e = entry("weekly", epochMillisOf(created), listOf("2026-W32", "2026-W34"))
        assertEquals(67, successRate(e, today)) // round(2/3*100) = 67
    }

    @Test
    fun maxStreakEver_daily_findsLongestConsecutiveRun() {
        val e = entry(
            "daily", epochMillisOf(LocalDate.of(2026, 8, 1)),
            listOf("2026-08-01", "2026-08-02", "2026-08-03", "2026-08-05", "2026-08-06")
        )
        assertEquals(3, maxStreakEver(e))
    }

    @Test
    fun maxStreakEver_weekly_findsLongestConsecutiveRunAcrossYearBoundary() {
        // KW52 2025, KW1 2026, KW2 2026 = 3 aufeinanderfolgende Wochen
        val e = entry(
            "weekly", epochMillisOf(LocalDate.of(2025, 12, 1)),
            listOf("2025-W52", "2026-W01", "2026-W02", "2026-W10")
        )
        assertEquals(3, maxStreakEver(e))
    }

    @Test
    fun maxStreakEver_empty_returnsZero() {
        val e = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), emptyList())
        assertEquals(0, maxStreakEver(e))
    }

    @Test
    fun weekdayPatternData_aggregatesAcrossDailyHabitsOnly() {
        val today = LocalDate.of(2026, 8, 10) // Montag
        val daily = entry(
            "daily", epochMillisOf(LocalDate.of(2026, 8, 3)), // Montag der Vorwoche
            listOf("2026-08-03", "2026-08-10")
        )
        val weekly = entry("weekly", epochMillisOf(LocalDate.of(2026, 8, 3)), listOf("2026-W32"))
        val result = weekdayPatternData(listOf(daily, weekly), today)
        assertEquals(7, result.size)
        assertEquals("Mo", result[0].label)
        assertEquals(100, result[0].pct) // beide Montage im Zeitraum erledigt
    }

    @Test
    fun weekdayPatternData_excludesUnfinishedToday() {
        val today = LocalDate.of(2026, 8, 10) // Montag, noch nicht erledigt
        val daily = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 10)), emptyList())
        val result = weekdayPatternData(listOf(daily), today)
        // Grenze ist "gestern" statt heute, solange heute nicht erledigt -> totals[Mo] bleibt 0
        assertEquals(0, result[0].pct)
    }

    @Test
    fun groupComparisonData_averagesPerGroupAndSortsDescending() {
        val today = LocalDate.of(2026, 8, 10)
        val a = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), (1..10).map { "2026-08-%02d".format(it) }, group = "Fitness")
        val b = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), emptyList(), group = "Lesen")
        val result = groupComparisonData(listOf(a, b), today)
        assertEquals(listOf("Fitness", "Lesen"), result.map { it.label })
        assertEquals(100, result[0].pct)
        assertEquals(0, result[1].pct)
    }

    @Test
    fun groupComparisonData_ungroupedHabitsUseFallbackLabel() {
        val today = LocalDate.of(2026, 8, 10)
        val a = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), emptyList(), group = "")
        val result = groupComparisonData(listOf(a), today)
        assertEquals("Ohne Gruppe", result[0].label)
    }

    @Test
    fun trendData_returnsSixMonthsEndingAtCurrentMonth() {
        val today = LocalDate.of(2026, 8, 10)
        val e = entry("daily", epochMillisOf(LocalDate.of(2026, 1, 1)), listOf("2026-08-01", "2026-08-10"))
        val result = trendData(listOf(e), today)
        assertEquals(6, result.size)
        assertEquals("Aug", result.last().label)
    }

    private fun epochMillisOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
