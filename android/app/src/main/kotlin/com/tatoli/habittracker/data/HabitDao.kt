package com.tatoli.habittracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class HabitWithDoneEntities(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val doneEntries: List<HabitDoneEntity>
)

data class HabitImport(
    val entity: HabitEntity,
    val doneKeys: List<String>
)

@Dao
interface HabitDao {

    @Transaction
    @Query("SELECT * FROM habits ORDER BY id")
    fun observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Long): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDone(done: HabitDoneEntity)

    @Query("DELETE FROM habit_done WHERE habitId = :habitId AND dateKey = :dateKey")
    suspend fun deleteDone(habitId: Long, dateKey: String)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Insert
    suspend fun insertHabits(habits: List<HabitEntity>): List<Long>

    @Insert
    suspend fun insertDoneEntries(entries: List<HabitDoneEntity>)

    @Transaction
    suspend fun replaceAllHabits(imports: List<HabitImport>) {
        deleteAllHabits()
        if (imports.isEmpty()) return
        val ids = insertHabits(imports.map { it.entity })
        val doneEntries = imports.indices.flatMap { i ->
            imports[i].doneKeys.map { key -> HabitDoneEntity(habitId = ids[i], dateKey = key) }
        }
        if (doneEntries.isNotEmpty()) insertDoneEntries(doneEntries)
    }
}
