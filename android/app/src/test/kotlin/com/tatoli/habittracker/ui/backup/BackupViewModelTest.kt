package com.tatoli.habittracker.ui.backup

import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HabitRepository
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(db.habitDao())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("backup_meta_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        viewModel = BackupViewModel(repository, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun lastBackupText_noBackupYet_showsPlaceholder() {
        assertEquals("Datensicherung · noch kein Backup", viewModel.lastBackupText.value)
    }

    @Test
    fun markBackupDone_updatesLastBackupTextToToday() {
        viewModel.markBackupDone()
        assertEquals("Datensicherung · letztes Backup: heute", viewModel.lastBackupText.value)
    }

    @Test
    fun hasAnyHabits_reflectsRepositoryState() = runBlocking {
        assertFalse(viewModel.hasAnyHabits())
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        assertTrue(viewModel.hasAnyHabits())
    }

    @Test
    fun buildExportXml_containsInsertedHabit() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = 42L))
        val xml = viewModel.buildExportXml()
        assertTrue(xml.contains("name=\"Lesen\""))
        assertTrue(xml.contains("id=\"42\""))
    }

    @Test
    fun importXml_replacesExistingDataAndReturnsImportedCount() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Alt", color = "#F2B450", freq = "daily"))
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="99" name="Neu" color="#4FC98A" group="" freq="daily">
                <day date="2026-08-01"/>
              </habit>
            </habits>
        """.trimIndent()

        val count = viewModel.importXml(xml)

        assertEquals(1, count)
        val result = repository.observeHabitsWithDone().first()
        assertEquals(1, result.size)
        assertEquals("Neu", result.first().habit.name)
        assertEquals(99L, result.first().habit.createdAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importXml_invalidXmlThrowsAndDoesNotThrowSilently() = runBlocking {
        viewModel.importXml("not valid xml")
        Unit
    }

    @Test
    fun lastBackupText_oneDayAgo_showsGestern() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("backup_meta_test", Context.MODE_PRIVATE)
        prefs.edit().putLong("lastBackupAt", System.currentTimeMillis() - 86_400_000L).commit()
        val vm = BackupViewModel(repository, prefs)
        assertEquals("Datensicherung · letztes Backup: gestern", vm.lastBackupText.value)
    }

    @Test
    fun lastBackupText_severalDaysAgo_showsDayCount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("backup_meta_test", Context.MODE_PRIVATE)
        prefs.edit().putLong("lastBackupAt", System.currentTimeMillis() - 5 * 86_400_000L).commit()
        val vm = BackupViewModel(repository, prefs)
        assertEquals("Datensicherung · letztes Backup: vor 5 Tagen", vm.lastBackupText.value)
    }

    @Test
    fun exportThenImport_roundTripsAllDataUnchanged() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", group = "Bildung", createdAt = 1000L)
        )
        db.habitDao().insertHabit(
            HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = 2000L)
        )
        val lesenId = repository.observeHabitsWithDone().first { it.size == 2 }.first { it.habit.name == "Lesen" }.habit.id
        val sportId = repository.observeHabitsWithDone().first().first { it.habit.name == "Sport" }.habit.id
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(lesenId, "2026-08-01"))
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(lesenId, "2026-08-02"))
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(sportId, "2026-W31"))

        val xml = viewModel.buildExportXml()
        viewModel.importXml(xml)

        val result = repository.observeHabitsWithDone().first()
        assertEquals(2, result.size)
        val lesen = result.first { it.habit.name == "Lesen" }
        assertEquals("#F2B450", lesen.habit.color)
        assertEquals("Bildung", lesen.habit.group)
        assertEquals(1000L, lesen.habit.createdAt)
        assertEquals(setOf("2026-08-01", "2026-08-02"), lesen.doneEntries.map { it.dateKey }.toSet())
        val sport = result.first { it.habit.name == "Sport" }
        assertEquals("weekly", sport.habit.freq)
        assertEquals(2000L, sport.habit.createdAt)
        assertEquals(setOf("2026-W31"), sport.doneEntries.map { it.dateKey }.toSet())
    }

    @Test
    fun importXml_invalidXml_leavesExistingDataCompletelyIntact() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Bestehend", color = "#F2B450", freq = "daily", createdAt = 42L))

        try {
            viewModel.importXml("nicht valides xml <<<")
        } catch (e: IllegalArgumentException) {
            // erwartet
        }

        val result = repository.observeHabitsWithDone().first()
        assertEquals(1, result.size)
        assertEquals("Bestehend", result.first().habit.name)
    }
}
