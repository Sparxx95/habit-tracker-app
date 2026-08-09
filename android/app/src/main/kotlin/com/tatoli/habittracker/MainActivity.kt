package com.tatoli.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.ui.habitedit.HabitEditSheet
import com.tatoli.habittracker.ui.habitedit.HabitEditViewModel
import com.tatoli.habittracker.ui.habitlist.HabitListScreen
import com.tatoli.habittracker.ui.habitlist.HabitListViewModel
import com.tatoli.habittracker.ui.theme.HabitTrackerTheme

private sealed interface EditSheetState {
    data object Hidden : EditSheetState
    data object AddNew : EditSheetState
    data class EditExisting(val habitId: Long) : EditSheetState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HabitRepository(AppDatabase.getInstance(applicationContext).habitDao())

        setContent {
            HabitTrackerTheme {
                Surface {
                    HabitTrackerApp(repository)
                }
            }
        }
    }
}

@Composable
private fun HabitTrackerApp(repository: HabitRepository) {
    var sheetState by remember { mutableStateOf<EditSheetState>(EditSheetState.Hidden) }

    val listViewModel: HabitListViewModel = viewModel(factory = viewModelFactory {
        initializer { HabitListViewModel(repository) }
    })

    HabitListScreen(
        viewModel = listViewModel,
        onAddHabit = { sheetState = EditSheetState.AddNew },
        onEditHabit = { id -> sheetState = EditSheetState.EditExisting(id) }
    )

    when (val state = sheetState) {
        is EditSheetState.Hidden -> Unit
        is EditSheetState.AddNew -> {
            // Bewusst nicht über viewModel(key = ...): dessen ViewModelStore ist an die
            // Activity gebunden und überlebt Hidden→AddNew-Zyklen, sodass eine per Konstante
            // geschlüsselte Instanz beim erneuten Öffnen alte, verworfene Eingaben zeigen würde.
            // remember(state) erzeugt bei jedem Wechsel nach AddNew eine frische Instanz, weil
            // dieser when-Zweig beim Wechsel nach Hidden aus der Komposition entfernt wird.
            val editViewModel = remember(state) { HabitEditViewModel(repository, habitId = null) }
            HabitEditSheet(
                viewModel = editViewModel,
                onDismiss = { sheetState = EditSheetState.Hidden },
                onSaved = { sheetState = EditSheetState.Hidden }
            )
        }
        is EditSheetState.EditExisting -> {
            // Gleicher Grund wie oben: frische Instanz pro Öffnen statt einer über habitId
            // geschlüsselten, Activity-gebundenen Instanz mit verworfenen Änderungen.
            val editViewModel = remember(state) { HabitEditViewModel(repository, habitId = state.habitId) }
            HabitEditSheet(
                viewModel = editViewModel,
                onDismiss = { sheetState = EditSheetState.Hidden },
                onSaved = { sheetState = EditSheetState.Hidden }
            )
        }
    }
}
