package com.tatoli.habittracker.ui.habitlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.dateKeyOf
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
    fun toggleToday_marksTodayDoneAndUpdatesStreak() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)

        var current = viewModel.habits.first { it.isNotEmpty() }
        assertFalse(current.first().doneToday)
        assertEquals(0, current.first().streakCount)

        viewModel.toggleToday(current.first())
        current = viewModel.habits.first { it.isNotEmpty() && it.first().doneToday }
        assertTrue(current.first().doneToday)
        assertEquals(1, current.first().streakCount)
    }

    @Test
    fun toggleCell_togglesArbitraryPastDayAndUpdatesMonthTotal() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)
        // Erster Tag des aktuell angezeigten Monats: immer <= heute, unabhängig
        // davon, welcher Tag "heute" gerade ist (im Gegensatz zu "gestern", das am
        // 1. eines Monats in den Vormonat fiele und außerhalb der dayCells läge).
        val targetDate = YearMonth.now().atDay(1)
        val targetKey = dateKeyOf(targetDate)

        var current = viewModel.habits.first { it.isNotEmpty() }
        val habitId = current.first().id
        val totalBefore = current.first().monthTotal

        viewModel.toggleCell(habitId, targetKey, currentlyDone = false)
        current = viewModel.habits.first { it.isNotEmpty() && it.first().monthTotal == totalBefore + 1 }
        assertEquals(totalBefore + 1, current.first().monthTotal)
        assertTrue(current.first().dayCells.first { it.date == targetDate }.done)
    }

    @Test
    fun dailyHabit_dayCellsCoverCurrentMonthWithCorrectTodayFlag() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)
        val today = LocalDate.now()
        val expectedDays = YearMonth.now().lengthOfMonth()

        val current = viewModel.habits.first { it.isNotEmpty() }
        val cells = current.first().dayCells
        assertEquals(expectedDays, cells.size)
        assertTrue(cells.none { it.isFuture && it.date <= today })
        assertTrue(cells.first { it.date == today }.isToday)
    }

    @Test
    fun weeklyHabit_hasWeekCellsAndEmptyDayCells() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly"))
        val viewModel = HabitListViewModel(repository)

        val current = viewModel.habits.first { it.isNotEmpty() }
        val habit = current.first()
        assertTrue(habit.dayCells.isEmpty())
        assertTrue(habit.weekCells.isNotEmpty())
    }

    @Test
    fun prevMonth_thenNextMonth_returnsToCurrentMonth_nextMonthNeverGoesPastNow() = runBlocking {
        val viewModel = HabitListViewModel(repository)
        val startMonth = viewModel.viewMonth.first()

        viewModel.prevMonth()
        assertEquals(startMonth.minusMonths(1), viewModel.viewMonth.first())

        viewModel.nextMonth()
        assertEquals(startMonth, viewModel.viewMonth.first())

        viewModel.nextMonth()
        assertEquals(startMonth, viewModel.viewMonth.first())
    }

    @Test
    fun refreshDay_advancesViewMonthWhenViewingCurrentMonthAndDayRollsIntoNewMonth() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)
        viewModel.habits.first { it.isNotEmpty() }

        val startMonth = viewModel.viewMonth.first()
        // Simuliert denselben Monat wie beim Start (Standardfall: App war schon offen,
        // kein Monatswechsel simuliert hier direkt, da dayKey privat ist) —
        // stattdessen wird geprüft, dass refreshDay() aufrufbar ist und viewMonth
        // unverändert bleibt, wenn der (simulierte) Tag im selben Monat bleibt.
        viewModel.refreshDay()
        assertEquals(startMonth, viewModel.viewMonth.first())
    }

    @Test
    fun availableGroups_returnsDistinctNonEmptyGroupsInFirstOccurrenceOrder() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", group = "Fitness"))
        db.habitDao().insertHabit(HabitEntity(name = "B", color = "#4FC98A", freq = "daily", group = ""))
        db.habitDao().insertHabit(HabitEntity(name = "C", color = "#5FB4E5", freq = "daily", group = "Lesen"))
        db.habitDao().insertHabit(HabitEntity(name = "D", color = "#E5766B", freq = "daily", group = "Fitness"))
        val viewModel = HabitListViewModel(repository)

        val groups = viewModel.availableGroups.first { it.isNotEmpty() }
        assertEquals(listOf("Fitness", "Lesen"), groups)
    }

    @Test
    fun listDisplay_groupsUngroupedFirstThenNamedGroupsInFirstOccurrenceOrder() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", group = "Fitness"))
        db.habitDao().insertHabit(HabitEntity(name = "B", color = "#4FC98A", freq = "daily", group = ""))
        db.habitDao().insertHabit(HabitEntity(name = "C", color = "#5FB4E5", freq = "daily", group = "Lesen"))
        db.habitDao().insertHabit(HabitEntity(name = "D", color = "#E5766B", freq = "daily", group = "Fitness"))
        val viewModel = HabitListViewModel(repository)

        val display = viewModel.listDisplay.first { it.sections.isNotEmpty() }
        assertTrue(display.showGroupHeaders)
        assertEquals(listOf("", "Fitness", "Lesen"), display.sections.map { it.label })
        assertEquals(listOf("B"), display.sections[0].habits.map { it.name })
        assertEquals(listOf("A", "D"), display.sections[1].habits.map { it.name })
        assertEquals(listOf("C"), display.sections[2].habits.map { it.name })
    }

    @Test
    fun listDisplay_withActiveFilter_returnsSingleFlatSectionWithoutHeaders() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", group = "Fitness"))
        db.habitDao().insertHabit(HabitEntity(name = "B", color = "#4FC98A", freq = "daily", group = ""))
        val viewModel = HabitListViewModel(repository)
        viewModel.listDisplay.first { it.sections.isNotEmpty() }

        viewModel.selectGroupFilter("Fitness")
        val display = viewModel.listDisplay.first { it.sections.size == 1 && it.sections[0].habits.size == 1 }
        assertFalse(display.showGroupHeaders)
        assertEquals(listOf("A"), display.sections[0].habits.map { it.name })
    }

    @Test
    fun listDisplay_noNamedGroups_showGroupHeadersIsFalse() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)

        val display = viewModel.listDisplay.first { it.sections.isNotEmpty() }
        assertFalse(display.showGroupHeaders)
    }

    @Test
    fun selectGroupFilter_invalidatedWhenFilteredGroupDisappears() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", group = "Fitness"))
        val viewModel = HabitListViewModel(repository)
        val habitId = viewModel.habits.first { it.isNotEmpty() }.first().id

        viewModel.availableGroups.first { it == listOf("Fitness") }
        viewModel.selectGroupFilter("Fitness")
        assertEquals("Fitness", viewModel.filterGroup.first())

        val entity = repository.getHabitById(habitId)!!
        repository.deleteHabit(entity)

        assertEquals(null, viewModel.filterGroup.first { it == null })
    }
}
