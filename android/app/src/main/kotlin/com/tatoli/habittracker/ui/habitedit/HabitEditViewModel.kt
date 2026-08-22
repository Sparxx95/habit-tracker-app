package com.tatoli.habittracker.ui.habitedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.ui.theme.HabitPalette
import kotlinx.coroutines.launch

// Bewusst nicht über viewModel()/ViewModelStore bezogen (siehe MainActivity.remember(state)) —
// jedes Sheet-Öffnen braucht eine frische Instanz ohne Altzustand aus vorherigen Sitzungen.
// onCleared()/viewModelScope-Cancellation durch das Framework finden dadurch nicht statt;
// die Coroutine-Nutzung bleibt kurzlebig (save/delete), das ist hier unkritisch.
class HabitEditViewModel(
    private val repository: HabitRepository,
    private val habitId: Long?
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var color by mutableStateOf(HabitPalette.first())
        private set
    var freq by mutableStateOf("daily")
        private set
    var loaded by mutableStateOf(habitId == null)
        private set

    val isEditing: Boolean get() = habitId != null

    init {
        if (habitId != null) {
            viewModelScope.launch {
                repository.getHabitById(habitId)?.let { habit ->
                    name = habit.name
                    color = habit.color
                    freq = habit.freq
                }
                loaded = true
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onColorChange(value: String) { color = value }
    fun onFreqChange(value: String) { freq = value }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            if (habitId == null) {
                repository.addHabit(name, color, freq, group = "")
            } else {
                repository.updateHabit(HabitEntity(id = habitId, name = name, color = color, freq = freq))
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.deleteHabit(HabitEntity(id = id, name = name, color = color, freq = freq))
            onDone()
        }
    }
}
