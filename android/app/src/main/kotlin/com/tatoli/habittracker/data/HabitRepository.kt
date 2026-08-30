package com.tatoli.habittracker.data

import com.tatoli.habittracker.util.ParsedHabit
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao) {

    fun observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>> = dao.observeHabitsWithDone()

    suspend fun addHabit(name: String, color: String, freq: String, group: String, createdAt: Long): Long =
        dao.insertHabit(HabitEntity(name = name, color = color, freq = freq, group = group, createdAt = createdAt))

    suspend fun updateHabit(habit: HabitEntity) = dao.updateHabit(habit)

    suspend fun deleteHabit(habit: HabitEntity) = dao.deleteHabit(habit)

    suspend fun getHabitById(id: Long): HabitEntity? = dao.getHabitById(id)

    suspend fun toggleDone(habitId: Long, dateKey: String, currentlyDone: Boolean) {
        if (currentlyDone) dao.deleteDone(habitId, dateKey)
        else dao.insertDone(HabitDoneEntity(habitId = habitId, dateKey = dateKey))
    }

    suspend fun replaceAllHabits(imported: List<ParsedHabit>) {
        val imports = imported.map { p ->
            HabitImport(
                entity = HabitEntity(name = p.name, color = p.color, freq = p.freq, group = p.group, createdAt = p.createdAt),
                doneKeys = p.doneKeys.distinct()
            )
        }
        dao.replaceAllHabits(imports)
    }
}
