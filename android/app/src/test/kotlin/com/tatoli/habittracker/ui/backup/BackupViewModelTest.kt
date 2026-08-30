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
}
