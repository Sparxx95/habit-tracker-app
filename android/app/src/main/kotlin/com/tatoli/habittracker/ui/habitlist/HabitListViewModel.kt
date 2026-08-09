package com.tatoli.habittracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneFlag
import com.tatoli.habittracker.util.todayKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitListViewModel(private val repository: HabitRepository) : ViewModel() {

    val habits: StateFlow<List<HabitWithDoneFlag>> = repository
        .observeHabitsWithDoneFlag(todayKey())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleDone(habit: HabitWithDoneFlag) {
        viewModelScope.launch {
            repository.toggleDone(habit.id, todayKey(), habit.doneToday)
        }
    }
}
