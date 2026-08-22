package com.tatoli.habittracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val freq: String,      // "daily" | "weekly"
    val group: String = "" // "" = keine Gruppe
)
