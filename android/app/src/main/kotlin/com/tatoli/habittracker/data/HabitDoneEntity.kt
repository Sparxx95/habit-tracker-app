package com.tatoli.habittracker.data

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "habit_done",
    primaryKeys = ["habitId", "dateKey"],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class HabitDoneEntity(
    val habitId: Long,
    val dateKey: String   // "YYYY-MM-DD" (daily) oder "YYYY-Www" (weekly)
)
