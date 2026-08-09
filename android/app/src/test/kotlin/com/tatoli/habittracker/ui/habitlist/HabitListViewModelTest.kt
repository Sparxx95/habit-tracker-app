package com.tatoli.habittracker.ui.habitlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.todayKey
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
class HabitListViewModelTest {

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

    @Test
    fun toggleDone_updatesHabitsFlow() = runBlocking {
        val id = db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)

        var current = repository.observeHabitsWithDoneFlag(todayKey()).first()
        assertEquals(1, current.size)
        assertTrue(!current.first().doneToday)

        viewModel.toggleDone(current.first())
        current = repository.observeHabitsWithDoneFlag(todayKey()).first()
        assertTrue(current.first { it.id == id }.doneToday)
    }
}
