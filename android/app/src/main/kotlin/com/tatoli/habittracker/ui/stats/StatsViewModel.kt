package com.tatoli.habittracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.util.createdDate
import com.tatoli.habittracker.util.dateKeyOf
import com.tatoli.habittracker.util.isoWeekNumber
import com.tatoli.habittracker.util.maxStreakEver
import com.tatoli.habittracker.util.mondayOf
import com.tatoli.habittracker.util.monthWeeks
import com.tatoli.habittracker.util.successRate
import com.tatoli.habittracker.util.weekKey
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class DayState { DONE, MISS, OFF }
data class StatsTableColumn(val name: String, val color: String)
data class StatsTableRow(val day: Int, val isToday: Boolean, val states: List<DayState>)
data class StatsTable(val columns: List<StatsTableColumn>, val rows: List<StatsTableRow>)
data class WeeklyStripCell(val isoWeekNumber: Int, val done: Boolean, val active: Boolean)
data class WeeklyStripRow(val name: String, val color: String, val cells: List<WeeklyStripCell>)
data class StatsLegendEntry(val name: String, val color: String, val streak: Int, val successRatePct: Int)

class StatsViewModel(private val repository: HabitRepository) : ViewModel() {

    private val _viewMonth = MutableStateFlow(YearMonth.now())
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    private val _freq = MutableStateFlow("daily")
    val freq: StateFlow<String> = _freq.asStateFlow()

    private val _mode = MutableStateFlow("table")
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun prevMonth() { _viewMonth.value = _viewMonth.value.minusMonths(1) }
    fun nextMonth() {
        val next = _viewMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) _viewMonth.value = next
    }
    fun selectFreq(value: String) { _freq.value = value }
    fun selectMode(value: String) { _mode.value = value }

    private val habitsWithDone = repository.observeHabitsWithDone()

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasDailyHabits: StateFlow<Boolean> = habitsWithDone
        .map { list -> list.any { it.habit.freq == "daily" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasWeeklyHabits: StateFlow<Boolean> = habitsWithDone
        .map { list -> list.any { it.habit.freq == "weekly" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val table: StateFlow<StatsTable> = combine(habitsWithDone, _viewMonth) { list, month ->
        val daily = list.filter { it.habit.freq == "daily" }
        if (daily.isEmpty()) return@combine StatsTable(emptyList(), emptyList())
        val today = LocalDate.now()
        val columns = daily.map { StatsTableColumn(it.habit.name, it.habit.color) }
        val rows = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val states = daily.map { entry -> dayState(entry, date, today) }
            StatsTableRow(day, date == today, states)
        }
        StatsTable(columns, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsTable(emptyList(), emptyList()))

    val weeklyStrips: StateFlow<List<WeeklyStripRow>> = combine(habitsWithDone, _viewMonth) { list, month ->
        val weekly = list.filter { it.habit.freq == "weekly" }
        val today = LocalDate.now()
        val nowWeekKey = weekKey(today)
        weekly.map { entry ->
            val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
            val startWeekKey = weekKey(mondayOf(createdDate(entry.habit.createdAt)))
            val cells = monthWeeks(month).map { monday ->
                val wk = weekKey(monday)
                WeeklyStripCell(
                    isoWeekNumber = isoWeekNumber(monday),
                    done = doneKeys.contains(wk),
                    active = wk >= startWeekKey && wk <= nowWeekKey
                )
            }
            WeeklyStripRow(entry.habit.name, entry.habit.color, cells)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val legend: StateFlow<List<StatsLegendEntry>> = combine(habitsWithDone, _freq) { list, freqValue ->
        val today = LocalDate.now()
        list.filter { it.habit.freq == freqValue }.map { entry ->
            StatsLegendEntry(
                name = entry.habit.name,
                color = entry.habit.color,
                streak = maxStreakEver(entry),
                successRatePct = successRate(entry, today)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun dayState(entry: HabitWithDoneEntities, date: LocalDate, today: LocalDate): DayState {
        val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
        val ds = dateKeyOf(date)
        if (doneKeys.contains(ds)) return DayState.DONE
        val created = createdDate(entry.habit.createdAt)
        if (date.isAfter(today) || date.isBefore(created)) return DayState.OFF
        return DayState.MISS
    }
}
