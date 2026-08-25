package com.tatoli.habittracker.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrate1To2_preservesExistingDataAndAddsGroupColumnWithEmptyDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test.db"
        context.deleteDatabase(dbName)

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, color TEXT NOT NULL, freq TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE habit_done (habitId INTEGER NOT NULL, dateKey TEXT NOT NULL, " +
                            "PRIMARY KEY(habitId, dateKey), " +
                            "FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO habits (id, name, color, freq) VALUES (1, 'Lesen', '#F2B450', 'daily')")
        db.execSQL("INSERT INTO habit_done (habitId, dateKey) VALUES (1, '2026-08-09')")

        MIGRATION_1_2.migrate(db)

        val habitCursor = db.query("SELECT name, `group` FROM habits WHERE id = 1")
        habitCursor.moveToFirst()
        assertEquals("Lesen", habitCursor.getString(0))
        assertEquals("", habitCursor.getString(1))
        habitCursor.close()

        val doneCursor = db.query("SELECT dateKey FROM habit_done WHERE habitId = 1")
        doneCursor.moveToFirst()
        assertEquals("2026-08-09", doneCursor.getString(0))
        doneCursor.close()

        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2To3_preservesExistingDataAndBackfillsCreatedAt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-2-3.db"
        context.deleteDatabase(dbName)

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, color TEXT NOT NULL, freq TEXT NOT NULL, " +
                            "`group` TEXT NOT NULL DEFAULT '')"
                    )
                    db.execSQL(
                        "CREATE TABLE habit_done (habitId INTEGER NOT NULL, dateKey TEXT NOT NULL, " +
                            "PRIMARY KEY(habitId, dateKey), " +
                            "FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO habits (id, name, color, freq, `group`) VALUES (1, 'Lesen', '#F2B450', 'daily', '')")
        db.execSQL("INSERT INTO habit_done (habitId, dateKey) VALUES (1, '2026-08-09')")

        val beforeMigration = System.currentTimeMillis()
        MIGRATION_2_3.migrate(db)
        val afterMigration = System.currentTimeMillis()

        val habitCursor = db.query("SELECT name, createdAt FROM habits WHERE id = 1")
        habitCursor.moveToFirst()
        assertEquals("Lesen", habitCursor.getString(0))
        val createdAt = habitCursor.getLong(1)
        assertTrue(createdAt in beforeMigration..afterMigration)
        habitCursor.close()

        val doneCursor = db.query("SELECT dateKey FROM habit_done WHERE habitId = 1")
        doneCursor.moveToFirst()
        assertEquals("2026-08-09", doneCursor.getString(0))
        doneCursor.close()

        db.close()
        context.deleteDatabase(dbName)
    }
}
