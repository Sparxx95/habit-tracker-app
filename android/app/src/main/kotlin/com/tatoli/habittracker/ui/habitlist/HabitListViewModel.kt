package com.tatoli.habittracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.util.dateKeyOf
import com.tatoli.habittracker.util.isoWeekNumber
import com.tatoli.habittracker.util.mondayOf
import com.tatoli.habittracker.util.monthDayCount
import com.tatoli.habittracker.util.monthWeeks
import com.tatoli.habittracker.util.streak
import com.tatoli.habittracker.util.todayKey
import com.tatoli.habittracker.util.weekKey
import com.tatoli.habittracker.util.weekStreak
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
import kotlinx.coroutines.launch

data class DayCell(
    val date: LocalDate,
    val done: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean
)

data class WeekCell(
    val monday: LocalDate,
    val isoWeekNumber: Int,
    val done: Boolean,
    val isCurrentWeek: Boolean,
    val isFuture: Boolean
)

data class HabitDisplayState(
    val id: Long,
    val name: String,
    val color: String,
    val freq: String,
    val group: String,
    val doneToday: Boolean,
    val streakCount: Int,
    val monthTotal: Int,
    val dayCells: List<DayCell>,
    val weekCells: List<WeekCell>
)

data class HabitGroupSection(
    val label: String,
    val habits: List<HabitDisplayState>
)

data class HabitListDisplay(
    val sections: List<HabitGroupSection>,
    val showGroupHeaders: Boolean
)

class HabitListViewModel(private val repository: HabitRepository) : ViewModel() {

    private val dayKey = MutableStateFlow(todayKey())
    private val _viewMonth = MutableStateFlow(YearMonth.now())
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    private val _filterGroup = MutableStateFlow<String?>(null)
    val filterGroup: StateFlow<String?> = _filterGroup.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val habits: StateFlow<List<HabitDisplayState>> = combine(
        repository.observeHabitsWithDone(),
        _viewMonth,
        dayKey
    ) { habitsWithDone, month, key ->
        val today = LocalDate.parse(key)
        habitsWithDone.map { toDisplayState(it, month, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGroups: StateFlow<List<String>> = habits.map { list ->
        val seen = LinkedHashSet<String>()
        list.forEach { h ->
            val g = h.group.trim()
            if (g.isNotEmpty()) seen.add(g)
        }
        seen.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            availableGroups.collect { groups ->
                _filterGroup.value?.let { current ->
                    if (current !in groups) _filterGroup.value = null
                }
            }
        }
    }

    val listDisplay: StateFlow<HabitListDisplay> = combine(habits, _filterGroup) { list, filter ->
        if (filter != null) {
            val filtered = list.filter { it.group.trim() == filter.trim() }
            return@combine HabitListDisplay(sections = listOf(HabitGroupSection("", filtered)), showGroupHeaders = false)
        }
        val order = LinkedHashSet<String>()
        list.forEach { order.add(it.group.trim()) }
        val orderedLabels = order.sortedBy { if (it == "") 0 else 1 }
        val sections = orderedLabels.map { label ->
            HabitGroupSection(label, list.filter { it.group.trim() == label })
        }
        val showHeaders = list.any { it.group.trim().isNotEmpty() }
        HabitListDisplay(sections = sections, showGroupHeaders = showHeaders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitListDisplay(emptyList(), false))

    fun selectGroupFilter(group: String?) {
        _filterGroup.value = group
    }

    fun toggleToday(habit: HabitDisplayState) {
        val today = LocalDate.parse(dayKey.value)
        val key = if (habit.freq == "weekly") weekKey(today) else dayKey.value
        toggleCell(habit.id, key, habit.doneToday)
    }

    fun toggleCell(habitId: Long, dateKey: String, currentlyDone: Boolean) {
        viewModelScope.launch {
            repository.toggleDone(habitId, dateKey, currentlyDone)
        }
    }

    fun refreshDay() {
        val oldMonth = YearMonth.from(LocalDate.parse(dayKey.value))
        val newToday = LocalDate.now()
        dayKey.value = dateKeyOf(newToday)
        if (_viewMonth.value == oldMonth) {
            _viewMonth.value = YearMonth.from(newToday)
        }
    }

    fun prevMonth() {
        _viewMonth.value = _viewMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = _viewMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _viewMonth.value = next
        }
    }

    private fun toDisplayState(entry: HabitWithDoneEntities, month: YearMonth, today: LocalDate): HabitDisplayState {
        val habit = entry.habit
        val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()

        if (habit.freq == "weekly") {
            val currentWeekMonday = mondayOf(today)
            val cells = monthWeeks(month).map { monday ->
                WeekCell(
                    monday = monday,
                    isoWeekNumber = isoWeekNumber(monday),
                    done = doneKeys.contains(weekKey(monday)),
                    isCurrentWeek = monday == currentWeekMonday,
                    isFuture = monday.isAfter(currentWeekMonday)
                )
            }
            return HabitDisplayState(
                id = habit.id,
                name = habit.name,
                color = habit.color,
                freq = habit.freq,
                group = habit.group,
                doneToday = doneKeys.contains(weekKey(today)),
                streakCount = weekStreak(doneKeys, today),
                monthTotal = cells.count { it.done },
                dayCells = emptyList(),
                weekCells = cells
            )
        }

        val cells = (1..monthDayCount(month)).map { day ->
            val date = month.atDay(day)
            DayCell(
                date = date,
                done = doneKeys.contains(dateKeyOf(date)),
                isToday = date == today,
                isFuture = date.isAfter(today)
            )
        }
        return HabitDisplayState(
            id = habit.id,
            name = habit.name,
            color = habit.color,
            freq = habit.freq,
            group = habit.group,
            doneToday = doneKeys.contains(dateKeyOf(today)),
            streakCount = streak(doneKeys, today),
            monthTotal = cells.count { it.done },
            dayCells = cells,
            weekCells = emptyList()
        )
    }
}
