package com.tatoli.habittracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN `group` TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [HabitEntity::class, HabitDoneEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habit-tracker.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
