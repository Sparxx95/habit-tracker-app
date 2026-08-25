package com.tatoli.habittracker.ui.habitedit

import androidx.compose.runtime.snapshotFlow
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HabitEditViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(db.habitDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // repository.observeHabitsWithDone() statt eines direkten Einmal-Reads nach save():
    // Rooms Suspend-DAO-Funktionen laufen über Rooms eigenen Hintergrund-Executor, nicht
    // zwingend synchron auf dem Unconfined-Dispatcher des Tests. .first{predicate} wartet
    // robust auf die tatsächliche Flow-Emission statt auf eine angenommene Ausführungsreihenfolge
    // zu vertrauen — gleiches, bereits bewährtes Muster wie in HabitListViewModelTest.

    @Test
    fun save_newHabit_setsCreatedAtToNow() = runBlocking {
        val before = System.currentTimeMillis()
        val viewModel = HabitEditViewModel(repository, habitId = null)
        viewModel.onNameChange("Lesen")
        viewModel.save {}

        val saved = repository.observeHabitsWithDone().first { it.isNotEmpty() }.first().habit
        val after = System.currentTimeMillis()
        assertEquals("Lesen", saved.name)
        assertTrue(saved.createdAt in before..after)
    }

    @Test
    fun save_existingHabit_preservesOriginalCreatedAt() = runBlocking {
        val originalCreatedAt = 1_000_000L
        val habitId = db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = originalCreatedAt)
        )
        val viewModel = HabitEditViewModel(repository, habitId = habitId)
        // Wartet, bis der init-Ladevorgang wirklich abgeschlossen ist, bevor gespeichert wird —
        // ohne das würde save() ggf. vor dem Laden laufen und createdAt stünde noch auf dem
        // Default 0L. Die reale UI (HabitEditSheet.kt) gated aktuell nicht auf `loaded` (siehe
        // Plan C, bereits als bekannte, geringfügige Vorbedingung dokumentiert) — das hier
        // testet den beabsichtigten Steady-State (Bearbeiten nach vollständigem Laden), nicht
        // dieses vorbestehende, ungetestete Rennen.
        snapshotFlow { viewModel.loaded }.first { it }
        viewModel.onNameChange("Lesen (bearbeitet)")
        viewModel.save {}

        val saved = repository.observeHabitsWithDone()
            .first { it.isNotEmpty() && it.first().habit.name == "Lesen (bearbeitet)" }
            .first().habit
        assertEquals(originalCreatedAt, saved.createdAt)
    }
}
