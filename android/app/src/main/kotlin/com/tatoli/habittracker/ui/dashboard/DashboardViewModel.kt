package com.tatoli.habittracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.groupComparisonData
import com.tatoli.habittracker.util.successRate
import com.tatoli.habittracker.util.todayKey
import com.tatoli.habittracker.util.trendData
import com.tatoli.habittracker.util.weekdayPatternData
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BarEntry(val label: String, val pct: Int, val color: String?)

class DashboardViewModel(private val repository: HabitRepository) : ViewModel() {

    private val dayKey = MutableStateFlow(todayKey())

    private val _dashMode = MutableStateFlow("all")
    val dashMode: StateFlow<String> = _dashMode.asStateFlow()

    fun selectDashMode(value: String) { _dashMode.value = value }

    fun refreshDay() {
        dayKey.value = todayKey()
    }

    private val habitsWithDone = repository.observeHabitsWithDone()

    // Unabhängig vom dashMode-Filter: ob überhaupt Habits existieren. Unterscheidet
    // "wirklich keine Habits" von "Habits vorhanden, aber keine passt zum gewählten
    // Rhythmus-Filter" – DashboardScreen zeigt dafür zwei verschiedene Leerzustände.
    @OptIn(ExperimentalCoroutinesApi::class)
    val hasAnyHabits: StateFlow<Boolean> = habitsWithDone
        .map { list -> list.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selected = combine(habitsWithDone, _dashMode) { list, mode ->
        when (mode) {
            "daily" -> list.filter { it.habit.freq == "daily" }
            "weekly" -> list.filter { it.habit.freq == "weekly" }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val overview: StateFlow<List<BarEntry>> = combine(selected, dayKey) { list, key ->
        val today = LocalDate.parse(key)
        list.map { entry -> BarEntry(entry.habit.name, successRate(entry, today), entry.habit.color) }
            .sortedByDescending { it.pct }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekdayPattern: StateFlow<List<BarEntry>> = combine(selected, dayKey) { list, key ->
        if (list.none { it.habit.freq == "daily" }) emptyList()
        else weekdayPatternData(list, LocalDate.parse(key)).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupComparison: StateFlow<List<BarEntry>> = combine(selected, dayKey) { list, key ->
        groupComparisonData(list, LocalDate.parse(key)).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val trend: StateFlow<List<BarEntry>> = combine(selected, dayKey) { list, key ->
        trendData(list, LocalDate.parse(key)).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
