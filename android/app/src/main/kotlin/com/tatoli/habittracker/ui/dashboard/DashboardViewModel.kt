package com.tatoli.habittracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.groupComparisonData
import com.tatoli.habittracker.util.successRate
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

    private val _dashMode = MutableStateFlow("all")
    val dashMode: StateFlow<String> = _dashMode.asStateFlow()

    fun selectDashMode(value: String) { _dashMode.value = value }

    private val habitsWithDone = repository.observeHabitsWithDone()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selected = combine(habitsWithDone, _dashMode) { list, mode ->
        when (mode) {
            "daily" -> list.filter { it.habit.freq == "daily" }
            "weekly" -> list.filter { it.habit.freq == "weekly" }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overview: StateFlow<List<BarEntry>> = selected.map { list ->
        val today = LocalDate.now()
        list.map { entry -> BarEntry(entry.habit.name, successRate(entry, today), entry.habit.color) }
            .sortedByDescending { it.pct }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekdayPattern: StateFlow<List<BarEntry>> = selected.map { list ->
        if (list.none { it.habit.freq == "daily" }) emptyList()
        else weekdayPatternData(list, LocalDate.now()).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupComparison: StateFlow<List<BarEntry>> = selected.map { list ->
        groupComparisonData(list, LocalDate.now()).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trend: StateFlow<List<BarEntry>> = selected.map { list ->
        trendData(list, LocalDate.now()).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
