package com.tatoli.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

private sealed interface AppScreen {
    data object List : AppScreen
    data object Stats : AppScreen
    data object Dashboard : AppScreen
}

// AppScreen ist eine zustandslose sealed interface (nur data objects) – zum Überleben von
// Rotation/Prozesstod genügt es, sie auf einen String zu mappen statt die Instanz selbst
// zu serialisieren.
private val AppScreenSaver: Saver<AppScreen, String> = Saver(
    save = { screen ->
        when (screen) {
            is AppScreen.List -> "list"
            is AppScreen.Stats -> "stats"
            is AppScreen.Dashboard -> "dashboard"
        }
    },
    restore = { value ->
        when (value) {
            "stats" -> AppScreen.Stats
            "dashboard" -> AppScreen.Dashboard
            else -> AppScreen.List
        }
    }
)

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
    val context = LocalContext.current
    var sheetState by remember { mutableStateOf<EditSheetState>(EditSheetState.Hidden) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var currentScreen by rememberSaveable(stateSaver = AppScreenSaver) {
        mutableStateOf<AppScreen>(AppScreen.List)
    }

    val listViewModel: HabitListViewModel = viewModel(factory = viewModelFactory {
        initializer { HabitListViewModel(repository) }
    })
    val availableGroups by listViewModel.availableGroups.collectAsState()

    // Stats-/Dashboard-ViewModel existieren nur, solange ihr Screen aktiv ist (gleiches
    // Prinzip wie bei EditSheetState unten: frische Instanz pro Öffnen). Hier einmal
    // berechnet (statt separat je Verwendungsstelle), damit der when-Zweig unten und der
    // ON_RESUME-Handler dieselbe Instanz referenzieren statt zwei unabhängig erzeugte.
    val statsViewModel = if (currentScreen is AppScreen.Stats) {
        remember(currentScreen) { com.tatoli.habittracker.ui.stats.StatsViewModel(repository) }
    } else {
        null
    }
    val dashboardViewModel = if (currentScreen is AppScreen.Dashboard) {
        remember(currentScreen) { com.tatoli.habittracker.ui.dashboard.DashboardViewModel(repository) }
    } else {
        null
    }
    // DisposableEffect unten läuft nur einmal pro lifecycleOwner und würde eine direkt
    // eingefangene statsViewModel/dashboardViewModel-Referenz sonst "einfrieren" (stale
    // closure) statt der aktuellen Instanz zu folgen, wenn der Screen wechselt.
    val latestStatsViewModel by rememberUpdatedState(statsViewModel)
    val latestDashboardViewModel by rememberUpdatedState(dashboardViewModel)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listViewModel.refreshDay()
                latestStatsViewModel?.refreshDay()
                latestDashboardViewModel?.refreshDay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = currentScreen !is AppScreen.List) {
        currentScreen = AppScreen.List
    }

    when (currentScreen) {
        is AppScreen.List -> {
            HabitListScreen(
                viewModel = listViewModel,
                onAddHabit = { sheetState = EditSheetState.AddNew },
                onEditHabit = { id -> sheetState = EditSheetState.EditExisting(id) },
                onOpenStats = { currentScreen = AppScreen.Stats },
                onOpenDashboard = { currentScreen = AppScreen.Dashboard },
                onOpenBackup = { showBackupSheet = true }
            )
        }
        is AppScreen.Stats -> {
            com.tatoli.habittracker.ui.stats.StatsScreen(
                viewModel = requireNotNull(statsViewModel),
                onBack = { currentScreen = AppScreen.List }
            )
        }
        is AppScreen.Dashboard -> {
            com.tatoli.habittracker.ui.dashboard.DashboardScreen(
                viewModel = requireNotNull(dashboardViewModel),
                onBack = { currentScreen = AppScreen.List }
            )
        }
    }

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
                availableGroups = availableGroups,
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
                availableGroups = availableGroups,
                onDismiss = { sheetState = EditSheetState.Hidden },
                onSaved = { sheetState = EditSheetState.Hidden }
            )
        }
    }

    if (showBackupSheet) {
        // Frische Instanz pro Öffnen, gleicher Grund wie bei den Edit-Sheet-ViewModels:
        // kein Zwischenspeichern von z. B. einer Fehlermeldung aus einem vorherigen Öffnen.
        val backupViewModel = remember(showBackupSheet) {
            val prefs = context.getSharedPreferences(
                com.tatoli.habittracker.ui.backup.BackupViewModel.BACKUP_PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
            com.tatoli.habittracker.ui.backup.BackupViewModel(repository, prefs)
        }
        com.tatoli.habittracker.ui.backup.BackupSheet(
            viewModel = backupViewModel,
            onDismiss = { showBackupSheet = false }
        )
    }
}
