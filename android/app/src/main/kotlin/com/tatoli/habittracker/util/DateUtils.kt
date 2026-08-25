package com.tatoli.habittracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun dateKeyOf(date: LocalDate): String = date.format(DATE_FORMAT)

fun todayKey(): String = dateKeyOf(LocalDate.now())

fun isoWeekNumber(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

fun weekKey(date: LocalDate): String {
    val year = date.get(WeekFields.ISO.weekBasedYear())
    return String.format(java.util.Locale.ROOT, "%d-W%02d", year, isoWeekNumber(date))
}

fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

fun monthWeeks(yearMonth: YearMonth): List<LocalDate> {
    val lastOfMonth = yearMonth.atEndOfMonth()
    val weeks = mutableListOf<LocalDate>()
    var cur = mondayOf(yearMonth.atDay(1))
    while (!cur.isAfter(lastOfMonth)) {
        weeks.add(cur)
        cur = cur.plusWeeks(1)
    }
    return weeks
}

fun monthDayCount(yearMonth: YearMonth): Int = yearMonth.lengthOfMonth()

fun firstDayOfWeekOffset(yearMonth: YearMonth): Int = yearMonth.atDay(1).dayOfWeek.value - 1

fun streak(doneDateKeys: Set<String>, today: LocalDate = LocalDate.now()): Int {
    var count = 0
    var cur = today
    if (!doneDateKeys.contains(dateKeyOf(cur))) cur = cur.minusDays(1)
    while (doneDateKeys.contains(dateKeyOf(cur))) {
        count++
        cur = cur.minusDays(1)
    }
    return count
}

fun weekStreak(doneWeekKeys: Set<String>, today: LocalDate = LocalDate.now()): Int {
    var count = 0
    var cur = mondayOf(today)
    if (!doneWeekKeys.contains(weekKey(cur))) cur = cur.minusWeeks(1)
    while (doneWeekKeys.contains(weekKey(cur))) {
        count++
        cur = cur.minusWeeks(1)
    }
    return count
}

fun createdDate(createdAt: Long): LocalDate =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
