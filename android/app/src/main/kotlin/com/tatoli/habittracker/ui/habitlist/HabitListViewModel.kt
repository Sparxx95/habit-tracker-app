package com.tatoli.habittracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneFlag
import com.tatoli.habittracker.util.todayKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitListViewModel(private val repository: HabitRepository) : ViewModel() {

    private val dayKey = MutableStateFlow(todayKey())

    @OptIn(ExperimentalCoroutinesApi::class)
    val habits: StateFlow<List<HabitWithDoneFlag>> = dayKey
        .flatMapLatest { key -> repository.observeHabitsWithDoneFlag(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleDone(habit: HabitWithDoneFlag) {
        viewModelScope.launch {
            repository.toggleDone(habit.id, dayKey.value, habit.doneToday)
        }
    }

    fun refreshDay() {
        dayKey.value = todayKey()
    }
}
