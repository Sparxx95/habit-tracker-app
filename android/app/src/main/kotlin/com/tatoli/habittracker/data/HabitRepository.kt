package com.tatoli.habittracker.data

import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao) {

    fun observeHabitsWithDoneFlag(dateKey: String): Flow<List<HabitWithDoneFlag>> =
        dao.observeHabitsWithDoneFlag(dateKey)

    suspend fun addHabit(name: String, color: String, freq: String): Long =
        dao.insertHabit(HabitEntity(name = name, color = color, freq = freq))

    suspend fun updateHabit(habit: HabitEntity) = dao.updateHabit(habit)

    suspend fun deleteHabit(habit: HabitEntity) = dao.deleteHabit(habit)

    suspend fun getHabitById(id: Long): HabitEntity? = dao.getHabitById(id)

    suspend fun toggleDone(habitId: Long, dateKey: String, currentlyDone: Boolean) {
        if (currentlyDone) dao.deleteDone(habitId, dateKey)
        else dao.insertDone(HabitDoneEntity(habitId = habitId, dateKey = dateKey))
    }
}
