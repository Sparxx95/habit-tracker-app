package com.tatoli.habittracker.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import java.time.LocalDate
import java.time.YearMonth
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
class StatsViewModelTest {

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
    fun table_hasOneColumnPerDailyHabitAndOneRowPerDayOfMonth() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(YearMonth.now().atDay(1)))
        )
        val viewModel = StatsViewModel(repository)
        val table = viewModel.table.first { it.columns.isNotEmpty() }
        assertEquals(listOf("Lesen"), table.columns.map { it.name })
        assertEquals(YearMonth.now().lengthOfMonth(), table.rows.size)
    }

    @Test
    fun table_excludesWeeklyHabits() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(YearMonth.now().atDay(1)))
        )
        val viewModel = StatsViewModel(repository)
        viewModel.hasWeeklyHabits.first { it }
        val table = viewModel.table.first()
        assertTrue(table.columns.isEmpty())
    }

    @Test
    fun legend_reflectsSuccessRateAndStreakForActiveFreq() = runBlocking {
        val today = LocalDate.now()
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(today))
        )
        val habitId = db.habitDao().observeHabitsWithDone().first { it.isNotEmpty() }.first().habit.id
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(habitId, today.toString()))
        val viewModel = StatsViewModel(repository)
        val legend = viewModel.legend.first { it.isNotEmpty() }
        assertEquals("Lesen", legend[0].name)
        assertEquals(100, legend[0].successRatePct)
        assertEquals(1, legend[0].streak)
    }

    @Test
    fun selectFreq_switchesLegendToWeeklyHabits() = runBlocking {
        val today = LocalDate.now()
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(today)))
        db.habitDao().insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(today)))
        val viewModel = StatsViewModel(repository)
        viewModel.legend.first { it.isNotEmpty() }

        viewModel.selectFreq("weekly")
        val legend = viewModel.legend.first { it.isNotEmpty() && it[0].name == "Sport" }
        assertEquals("Sport", legend[0].name)
    }

    @Test
    fun dailyRingChart_hasOneRingPerDailyHabitAndOneSectorPerDay() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(YearMonth.now().atDay(1)))
        )
        val viewModel = StatsViewModel(repository)
        val chart = viewModel.dailyRingChart.first { it.ringNames.isNotEmpty() }
        assertEquals(listOf("Lesen"), chart.ringNames)
        assertEquals(YearMonth.now().lengthOfMonth(), chart.sectorLabels.size)
        assertEquals(chart.sectorLabels.size, chart.states[0].size)
    }

    @Test
    fun weeklyRingChart_highlightsCurrentWeekWhenViewingCurrentMonth() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(LocalDate.now()))
        )
        val viewModel = StatsViewModel(repository)
        val chart = viewModel.weeklyRingChart.first { it.ringNames.isNotEmpty() }
        assertTrue(chart.highlightSectorIndex >= 0)
    }

    @Test
    fun nextMonth_neverGoesPastCurrentMonth() = runBlocking {
        val viewModel = StatsViewModel(repository)
        val start = viewModel.viewMonth.first()
        viewModel.nextMonth()
        assertEquals(start, viewModel.viewMonth.first())
    }
}
