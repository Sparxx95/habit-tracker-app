package com.tatoli.habittracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitDaoTest {

    private fun buildDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun observeHabitsWithDoneFlag_reflectsInsertAndToggle() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val id1 = dao.insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        dao.insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly"))

        var result = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertEquals(2, result.size)
        assertTrue(result.all { !it.doneToday })

        dao.insertDone(HabitDoneEntity(habitId = id1, dateKey = "2026-08-09"))
        result = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertTrue(result.first { it.id == id1 }.doneToday)
        assertFalse(result.first { it.name == "Sport" }.doneToday)

        dao.deleteDone(id1, "2026-08-09")
        result = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertFalse(result.first { it.id == id1 }.doneToday)

        db.close()
    }

    @Test
    fun deleteHabit_cascadesToHabitDone() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val id = dao.insertHabit(HabitEntity(name = "Meditation", color = "#5FB4E5", freq = "daily"))
        dao.insertDone(HabitDoneEntity(habitId = id, dateKey = "2026-08-09"))

        val habit = dao.getHabitById(id)
        assertEquals("Meditation", habit?.name)

        dao.deleteHabit(habit!!)
        val afterDelete = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertTrue(afterDelete.isEmpty())

        db.close()
    }
}
