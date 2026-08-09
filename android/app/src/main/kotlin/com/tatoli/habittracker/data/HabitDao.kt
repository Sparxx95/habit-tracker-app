package com.tatoli.habittracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class HabitWithDoneFlag(
    val id: Long,
    val name: String,
    val color: String,
    val freq: String,
    val doneToday: Boolean
)

@Dao
interface HabitDao {

    @Query("""
        SELECT h.id as id, h.name as name, h.color as color, h.freq as freq,
               EXISTS(SELECT 1 FROM habit_done d WHERE d.habitId = h.id AND d.dateKey = :dateKey) as doneToday
        FROM habits h
        ORDER BY h.id
    """)
    fun observeHabitsWithDoneFlag(dateKey: String): Flow<List<HabitWithDoneFlag>>

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
}
