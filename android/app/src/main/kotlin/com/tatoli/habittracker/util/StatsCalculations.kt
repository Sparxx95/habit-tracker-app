package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitWithDoneEntities
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

fun successRate(entry: HabitWithDoneEntities, today: LocalDate): Int {
    val created = createdDate(entry.habit.createdAt)
    val doneCount = entry.doneEntries.size
    if (entry.habit.freq == "weekly") {
        val totalWeeks = ChronoUnit.WEEKS.between(mondayOf(created), mondayOf(today)).toInt() + 1
        return minOf(100, Math.round(doneCount * 100.0 / maxOf(1, totalWeeks)).toInt())
    }
    val totalDays = maxOf(1, ChronoUnit.DAYS.between(created, today).toInt() + 1)
    return minOf(100, Math.round(doneCount * 100.0 / totalDays).toInt())
}

private fun isoWeeksInYear(year: Int): Int =
    LocalDate.of(year, 12, 28).get(WeekFields.ISO.weekOfWeekBasedYear())

private fun nextIsoWeek(year: Int, week: Int): Pair<Int, Int> =
    if (week < isoWeeksInYear(year)) year to (week + 1) else (year + 1) to 1

private fun parseWeekKey(key: String): Pair<Int, Int> {
    val parts = key.split("-W")
    return parts[0].toInt() to parts[1].toInt()
}

fun maxStreakEver(entry: HabitWithDoneEntities): Int {
    val keys = entry.doneEntries.map { it.dateKey }
    if (keys.isEmpty()) return 0
    if (entry.habit.freq == "weekly") {
        val parsed = keys.map(::parseWeekKey).sortedWith(compareBy({ it.first }, { it.second }))
        var best = 1
        var cur = 1
        for (i in 1 until parsed.size) {
            val next = nextIsoWeek(parsed[i - 1].first, parsed[i - 1].second)
            cur = if (next == parsed[i]) cur + 1 else 1
            if (cur > best) best = cur
        }
        return best
    }
    val days = keys.map { LocalDate.parse(it) }.sorted()
    var best = 1
    var cur = 1
    for (i in 1 until days.size) {
        cur = if (days[i - 1].plusDays(1) == days[i]) cur + 1 else 1
        if (cur > best) best = cur
    }
    return best
}

data class WeekdayStat(val label: String, val pct: Int)
data class GroupStat(val label: String, val pct: Int)
data class MonthStat(val label: String, val pct: Int)

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val MONTH_SHORT_NAMES = listOf(
    "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"
)

fun weekdayPatternData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<WeekdayStat> {
    val totals = IntArray(7)
    val dones = IntArray(7)
    entries.filter { it.habit.freq == "daily" }.forEach { entry ->
        val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
        val created = createdDate(entry.habit.createdAt)
        val boundary = if (doneKeys.contains(dateKeyOf(today))) today else today.minusDays(1)
        if (boundary.isBefore(created)) return@forEach
        var cur = created
        while (!cur.isAfter(boundary)) {
            val dow = cur.dayOfWeek.value - 1 // Montag=0 .. Sonntag=6
            totals[dow]++
            if (doneKeys.contains(dateKeyOf(cur))) dones[dow]++
            cur = cur.plusDays(1)
        }
    }
    return WEEKDAY_LABELS.indices.map { i ->
        WeekdayStat(WEEKDAY_LABELS[i], if (totals[i] > 0) Math.round(dones[i] * 100.0 / totals[i]).toInt() else 0)
    }
}

fun groupComparisonData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<GroupStat> {
    val sums = LinkedHashMap<String, Int>()
    val counts = LinkedHashMap<String, Int>()
    entries.forEach { entry ->
        val label = entry.habit.group.ifEmpty { "Ohne Gruppe" }
        sums[label] = (sums[label] ?: 0) + successRate(entry, today)
        counts[label] = (counts[label] ?: 0) + 1
    }
    return sums.keys.map { label ->
        GroupStat(label, Math.round(sums.getValue(label) * 1.0 / counts.getValue(label)).toInt())
    }.sortedByDescending { it.pct }
}

fun trendData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<MonthStat> {
    val nowWeekKey = weekKey(today)
    val currentMonth = YearMonth.from(today)
    val months = (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    return months.map { month ->
        var possible = 0
        var done = 0
        entries.forEach { entry ->
            val created = createdDate(entry.habit.createdAt)
            val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
            if (entry.habit.freq == "daily") {
                for (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    if (date.isBefore(created) || date.isAfter(today)) continue
                    possible++
                    if (doneKeys.contains(dateKeyOf(date))) done++
                }
            } else {
                var cur = mondayOf(month.atDay(1))
                val monthLastDay = month.atEndOfMonth()
                while (!cur.isAfter(monthLastDay)) {
                    if (YearMonth.from(cur) == month) {
                        val wk = weekKey(cur)
                        if (!cur.isBefore(created) && wk <= nowWeekKey) {
                            possible++
                            if (doneKeys.contains(wk)) done++
                        }
                    }
                    cur = cur.plusWeeks(1)
                }
            }
        }
        MonthStat(MONTH_SHORT_NAMES[month.monthValue - 1], if (possible > 0) Math.round(done * 100.0 / possible).toInt() else 0)
    }
}
