package com.tatoli.habittracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun observeHabitsWithDone_includesFullDoneHistoryPerHabitAndReflectsToggle() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val id1 = dao.insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val id2 = dao.insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly"))
        dao.insertDone(HabitDoneEntity(habitId = id1, dateKey = "2026-08-08"))
        dao.insertDone(HabitDoneEntity(habitId = id1, dateKey = "2026-08-09"))
        dao.insertDone(HabitDoneEntity(habitId = id2, dateKey = "2026-W31"))

        var result = dao.observeHabitsWithDone().first()
        assertEquals(2, result.size)

        val lesen = result.first { it.habit.id == id1 }
        assertEquals(2, lesen.doneEntries.size)
        assertTrue(lesen.doneEntries.any { it.dateKey == "2026-08-08" })
        assertTrue(lesen.doneEntries.any { it.dateKey == "2026-08-09" })

        val sport = result.first { it.habit.id == id2 }
        assertEquals(1, sport.doneEntries.size)
        assertEquals("2026-W31", sport.doneEntries.first().dateKey)

        dao.deleteDone(id1, "2026-08-08")
        result = dao.observeHabitsWithDone().first()
        assertEquals(1, result.first { it.habit.id == id1 }.doneEntries.size)

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
        val afterDelete = dao.observeHabitsWithDone().first()
        assertTrue(afterDelete.isEmpty())

        db.close()
    }

    @Test
    fun replaceAllHabits_deletesExistingDataAndInsertsImportedHabitsWithDoneEntries() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val oldId = dao.insertHabit(HabitEntity(name = "Alt", color = "#F2B450", freq = "daily"))
        dao.insertDone(HabitDoneEntity(habitId = oldId, dateKey = "2026-08-01"))

        val imports = listOf(
            HabitImport(
                entity = HabitEntity(name = "Neu1", color = "#4FC98A", freq = "daily", group = "G", createdAt = 111L),
                doneKeys = listOf("2026-08-05", "2026-08-06")
            ),
            HabitImport(
                entity = HabitEntity(name = "Neu2", color = "#5FB4E5", freq = "weekly", createdAt = 222L),
                doneKeys = listOf("2026-W31")
            )
        )
        dao.replaceAllHabits(imports)

        val result = dao.observeHabitsWithDone().first()
        assertEquals(2, result.size)
        assertTrue(result.none { it.habit.name == "Alt" })

        val neu1 = result.first { it.habit.name == "Neu1" }
        assertEquals("G", neu1.habit.group)
        assertEquals(111L, neu1.habit.createdAt)
        assertEquals(2, neu1.doneEntries.size)

        val neu2 = result.first { it.habit.name == "Neu2" }
        assertEquals(1, neu2.doneEntries.size)
        assertEquals("2026-W31", neu2.doneEntries.first().dateKey)

        db.close()
    }

    @Test
    fun replaceAllHabits_withEmptyList_deletesEverythingAndInsertsNothing() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()
        dao.insertHabit(HabitEntity(name = "Alt", color = "#F2B450", freq = "daily"))

        dao.replaceAllHabits(emptyList())

        assertTrue(dao.observeHabitsWithDone().first().isEmpty())
        db.close()
    }
}
