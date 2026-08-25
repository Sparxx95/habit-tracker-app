package com.tatoli.habittracker.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import java.time.LocalDate
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
class DashboardViewModelTest {

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

    private fun createdAtOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun overview_sortedDescendingBySuccessRate() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Niedrig", color = "#F2B450", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        db.habitDao().insertHabit(HabitEntity(name = "Hoch", color = "#4FC98A", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        val highId = db.habitDao().observeHabitsWithDone().first { it.size == 2 }.first { it.habit.name == "Hoch" }.habit.id
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(highId, LocalDate.now().toString()))

        val viewModel = DashboardViewModel(repository)
        val overview = viewModel.overview.first { it.size == 2 }
        assertEquals("Hoch", overview[0].label)
    }

    @Test
    fun selectDashMode_weekly_filtersOutDailyHabitsFromOverview() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Täglich", color = "#F2B450", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        db.habitDao().insertHabit(HabitEntity(name = "Wöchentlich", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        viewModel.overview.first { it.size == 2 }

        viewModel.selectDashMode("weekly")
        val overview = viewModel.overview.first { it.size == 1 }
        assertEquals("Wöchentlich", overview[0].label)
    }

    @Test
    fun weekdayPattern_emptyWhenNoDailyHabitsSelected() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Wöchentlich", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        viewModel.overview.first { it.isNotEmpty() }
        val pattern = viewModel.weekdayPattern.first()
        assertTrue(pattern.isEmpty())
    }

    @Test
    fun groupComparison_reflectsGroupAverages() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", group = "Fitness", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        val groups = viewModel.groupComparison.first { it.isNotEmpty() }
        assertEquals("Fitness", groups[0].label)
    }

    @Test
    fun trend_returnsSixEntries() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        val trend = viewModel.trend.first { it.isNotEmpty() }
        assertEquals(6, trend.size)
    }
}
