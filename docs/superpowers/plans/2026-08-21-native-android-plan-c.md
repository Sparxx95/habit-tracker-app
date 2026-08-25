# Plan C: Gruppen für die native Android-App — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Habits können einer Gruppe zugewiesen werden (Dropdown im
Anlegen/Bearbeiten-Sheet); die Liste zeigt Gruppen-Überschriften und
Filter-Chips, funktional an die Web-App angelehnt.

**Architecture:** `HabitEntity` bekommt ein `group`-Feld über eine echte
Room-Migration (v1→v2, additive `ALTER TABLE`). Gruppierungs-/Filterlogik
läuft komplett in Kotlin im `HabitListViewModel` (keine neuen DB-Queries),
analog zu Streak-/Zellen-Berechnung aus Plan B.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `ExposedDropdownMenuBox`),
Room (`Migration`, `SupportSQLiteOpenHelper` für den Migrationstest),
Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-21-native-android-plan-c-design.md`

## Global Constraints

- `applicationId`/Kotlin-`namespace`: `com.tatoli.habittracker`.
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- Gruppen-Zuweisung als **Dropdown** (nicht Freitext-Autocomplete): "Keine
  Gruppe" + bestehende Gruppennamen + "+ Neue Gruppe…", Auswahl von
  Letzterem blendet ein Textfeld ein (max. 40 Zeichen, getrimmt beim
  Speichern).
- Migration ist **additiv** (`ALTER TABLE ... ADD COLUMN`), kein
  `fallbackToDestructiveMigration()` — bestehende Habits/Done-Historie
  bleiben vollständig erhalten. `group` ist ein reserviertes
  SQLite-Schlüsselwort — in roher Migrations-SQL immer in Backticks
  (`` `group` ``) schreiben.
- **Bewusst NICHT Teil dieses Plans:** Statistik-Screen (Plan D),
  XML-Backup (Plan E), Login/Firebase (dauerhaft außerhalb des Scopes),
  Änderungen an Zeitraster-/Streak-Logik (Plan B bleibt unverändert).
- Kein Play-Store-Release, kein signierter Build — nur Debug-APKs.
- **Lokale Verifikation:** Vor jedem Gradle-Aufruf:
  ```bash
  export JAVA_HOME="$HOME/.jdk17"
  export PATH="$JAVA_HOME/bin:$PATH"
  export ANDROID_HOME="$HOME/.android-sdk"
  export ANDROID_SDK_ROOT="$HOME/.android-sdk"
  ```
  Volle Compose-UI-Rendering-Tests sind in dieser Umgebung NICHT Teil des
  Scopes (kein Gerät/Emulator) — Verifikation für UI-Code ist "kompiliert
  erfolgreich" (`./gradlew assembleDebug`) + ViewModel-/DAO-/Migrations-Unit-Tests
  (`./gradlew testDebugUnitTest`).

---

### Task 1: Room-Migration v1→v2 (`group`-Feld)

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitEntity.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/AppDatabase.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/data/AppDatabaseMigrationTest.kt` (neu)

**Interfaces:**
- Produces (für Task 2): `HabitEntity.group: String` (Default `""`),
  `MIGRATION_1_2: Migration` (top-level `val` in `AppDatabase.kt`,
  gleiches Package, kein Import nötig).

- [ ] **Step 1: Write the failing test**

Erstelle `android/app/src/test/kotlin/com/tatoli/habittracker/data/AppDatabaseMigrationTest.kt`
mit:

```kotlin
package com.tatoli.habittracker.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
}
```

This is a dependency-free approach (no new Gradle dependency needed —
`SupportSQLiteOpenHelper`/`FrameworkSQLiteOpenHelperFactory` are already
transitively available via `androidx.room:room-runtime`, already a
dependency): it hand-builds a v1-shaped SQLite database, inserts data,
calls `MIGRATION_1_2.migrate(db)` directly, and asserts on the raw result —
no `androidx.room:room-testing`/`MigrationTestHelper`/`androidx.test`
instrumentation setup needed.

- [ ] **Step 2: Run test to verify it fails**

```bash
export JAVA_HOME="$HOME/.jdk17"; export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"; export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.AppDatabaseMigrationTest"
```
Expected: FAIL — Compile-Fehler, `MIGRATION_1_2` existiert noch nicht.

- [ ] **Step 3: Add the `group` field and the migration**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitEntity.kt`:

```kotlin
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
```

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/data/AppDatabase.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.AppDatabaseMigrationTest"
```
Expected: PASS.

- [ ] **Step 5: Full build check**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL` — bestehende `HabitDaoTest`/`HabitListViewModelTest`
Aufrufe von `HabitEntity(...)` ohne `group`-Argument bleiben gültig (Default
`""` greift).

Da `exportSchema = true` gesetzt ist, wird beim Build automatisch
`android/app/schemas/com.tatoli.habittracker.data.AppDatabase/2.json`
generiert — prüfe nach dem Build, dass diese Datei existiert
(`ls android/app/schemas/com.tatoli.habittracker.data.AppDatabase/`), und
committe sie mit.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitEntity.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/AppDatabase.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/data/AppDatabaseMigrationTest.kt \
        android/app/schemas/com.tatoli.habittracker.data.AppDatabase/2.json
git commit -m "feat: Room-Migration v1->v2 für Habit-Gruppen-Feld"
```

---

### Task 2: HabitRepository + HabitListViewModel Gruppen-/Filterlogik

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModel.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitEntity.group` (Task 1).
- Produces (für Task 3 und Task 4): `HabitDisplayState.group: String`,
  `HabitRepository.addHabit(name, color, freq, group): Long`,
  `HabitGroupSection(label: String, habits: List<HabitDisplayState>)`,
  `HabitListDisplay(sections: List<HabitGroupSection>, showGroupHeaders: Boolean)`,
  `HabitListViewModel.availableGroups: StateFlow<List<String>>`,
  `HabitListViewModel.filterGroup: StateFlow<String?>`,
  `HabitListViewModel.listDisplay: StateFlow<HabitListDisplay>`,
  `HabitListViewModel.selectGroupFilter(group: String?)`.

- [ ] **Step 1: Write the failing tests**

Füge in `android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt`
diese vier Testmethoden am Ende der Klasse (vor der letzten schließenden
`}`) ein:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.habitlist.HabitListViewModelTest"
```
Expected: FAIL — Compile-Fehler (`availableGroups`, `listDisplay`,
`selectGroupFilter`, `HabitEntity(..., group = ...)` als benannter
Parameter für ein noch fehlendes Feld — falls Task 1 bereits gemerged ist,
ist nur `availableGroups`/`listDisplay`/`selectGroupFilter` unbekannt).

- [ ] **Step 3: Add `group` to `HabitRepository.addHabit`**

Ersetze in `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`
die Zeile:
```kotlin
    suspend fun addHabit(name: String, color: String, freq: String): Long =
        dao.insertHabit(HabitEntity(name = name, color = color, freq = freq))
```
durch:
```kotlin
    suspend fun addHabit(name: String, color: String, freq: String, group: String): Long =
        dao.insertHabit(HabitEntity(name = name, color = color, freq = freq, group = group))
```

- [ ] **Step 4: Add `group` to `HabitDisplayState` and the grouping/filter logic to `HabitListViewModel`**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModel.kt`
mit:

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.util.dateKeyOf
import com.tatoli.habittracker.util.isoWeekNumber
import com.tatoli.habittracker.util.mondayOf
import com.tatoli.habittracker.util.monthDayCount
import com.tatoli.habittracker.util.monthWeeks
import com.tatoli.habittracker.util.streak
import com.tatoli.habittracker.util.todayKey
import com.tatoli.habittracker.util.weekKey
import com.tatoli.habittracker.util.weekStreak
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayCell(
    val date: LocalDate,
    val done: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean
)

data class WeekCell(
    val monday: LocalDate,
    val isoWeekNumber: Int,
    val done: Boolean,
    val isCurrentWeek: Boolean,
    val isFuture: Boolean
)

data class HabitDisplayState(
    val id: Long,
    val name: String,
    val color: String,
    val freq: String,
    val group: String,
    val doneToday: Boolean,
    val streakCount: Int,
    val monthTotal: Int,
    val dayCells: List<DayCell>,
    val weekCells: List<WeekCell>
)

data class HabitGroupSection(
    val label: String,
    val habits: List<HabitDisplayState>
)

data class HabitListDisplay(
    val sections: List<HabitGroupSection>,
    val showGroupHeaders: Boolean
)

class HabitListViewModel(private val repository: HabitRepository) : ViewModel() {

    private val dayKey = MutableStateFlow(todayKey())
    private val _viewMonth = MutableStateFlow(YearMonth.now())
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    private val _filterGroup = MutableStateFlow<String?>(null)
    val filterGroup: StateFlow<String?> = _filterGroup.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val habits: StateFlow<List<HabitDisplayState>> = combine(
        repository.observeHabitsWithDone(),
        _viewMonth,
        dayKey
    ) { habitsWithDone, month, key ->
        val today = LocalDate.parse(key)
        habitsWithDone.map { toDisplayState(it, month, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGroups: StateFlow<List<String>> = habits.map { list ->
        val seen = LinkedHashSet<String>()
        list.forEach { h ->
            val g = h.group.trim()
            if (g.isNotEmpty()) seen.add(g)
        }
        seen.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listDisplay: StateFlow<HabitListDisplay> = combine(habits, _filterGroup) { list, filter ->
        if (filter != null) {
            val filtered = list.filter { it.group.trim() == filter }
            return@combine HabitListDisplay(sections = listOf(HabitGroupSection("", filtered)), showGroupHeaders = false)
        }
        val order = LinkedHashSet<String>()
        list.forEach { order.add(it.group.trim()) }
        val orderedLabels = order.sortedBy { if (it == "") 0 else 1 }
        val sections = orderedLabels.map { label ->
            HabitGroupSection(label, list.filter { it.group.trim() == label })
        }
        val showHeaders = list.any { it.group.trim().isNotEmpty() }
        HabitListDisplay(sections = sections, showGroupHeaders = showHeaders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitListDisplay(emptyList(), false))

    fun selectGroupFilter(group: String?) {
        _filterGroup.value = group
    }

    fun toggleToday(habit: HabitDisplayState) {
        val today = LocalDate.parse(dayKey.value)
        val key = if (habit.freq == "weekly") weekKey(today) else dayKey.value
        toggleCell(habit.id, key, habit.doneToday)
    }

    fun toggleCell(habitId: Long, dateKey: String, currentlyDone: Boolean) {
        viewModelScope.launch {
            repository.toggleDone(habitId, dateKey, currentlyDone)
        }
    }

    fun refreshDay() {
        val oldMonth = YearMonth.from(LocalDate.parse(dayKey.value))
        val newToday = LocalDate.now()
        dayKey.value = dateKeyOf(newToday)
        if (_viewMonth.value == oldMonth) {
            _viewMonth.value = YearMonth.from(newToday)
        }
    }

    fun prevMonth() {
        _viewMonth.value = _viewMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = _viewMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _viewMonth.value = next
        }
    }

    private fun toDisplayState(entry: HabitWithDoneEntities, month: YearMonth, today: LocalDate): HabitDisplayState {
        val habit = entry.habit
        val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()

        if (habit.freq == "weekly") {
            val currentWeekMonday = mondayOf(today)
            val cells = monthWeeks(month).map { monday ->
                WeekCell(
                    monday = monday,
                    isoWeekNumber = isoWeekNumber(monday),
                    done = doneKeys.contains(weekKey(monday)),
                    isCurrentWeek = monday == currentWeekMonday,
                    isFuture = monday.isAfter(currentWeekMonday)
                )
            }
            return HabitDisplayState(
                id = habit.id,
                name = habit.name,
                color = habit.color,
                freq = habit.freq,
                group = habit.group,
                doneToday = doneKeys.contains(weekKey(today)),
                streakCount = weekStreak(doneKeys, today),
                monthTotal = cells.count { it.done },
                dayCells = emptyList(),
                weekCells = cells
            )
        }

        val cells = (1..monthDayCount(month)).map { day ->
            val date = month.atDay(day)
            DayCell(
                date = date,
                done = doneKeys.contains(dateKeyOf(date)),
                isToday = date == today,
                isFuture = date.isAfter(today)
            )
        }
        return HabitDisplayState(
            id = habit.id,
            name = habit.name,
            color = habit.color,
            freq = habit.freq,
            group = habit.group,
            doneToday = doneKeys.contains(dateKeyOf(today)),
            streakCount = streak(doneKeys, today),
            monthTotal = cells.count { it.done },
            dayCells = cells,
            weekCells = emptyList()
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.habitlist.HabitListViewModelTest"
```
Expected: PASS (10 Tests: 6 bestehende + 4 neue, 0 Failures).

- [ ] **Step 6: Full build check**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL` — Achtung: `MainActivity.kt`,
`HabitEditViewModel.kt` und `HabitListScreen.kt` rufen bislang noch
`repository.addHabit(name, color, freq)` (3 Argumente) bzw. verwenden
`HabitCard`/`HabitDisplayState` ohne `group` nirgends direkt auf, sodass
dieser Task für sich allein **nicht** kompilieren wird, solange
`HabitEditViewModel.save()` noch die alte 3-Parameter-Signatur aufruft.
Das wird in Task 3 behoben — bis dahin ist ein Compile-Fehler in
`HabitEditViewModel.kt` (falscher Argumentanzahl bei `addHabit`) das
einzige erwartete Resultat dieses Zwischenzustands. **Um diesen Task
trotzdem einzeln grün abzuschließen**, ändere zusätzlich in
`android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt`
den einen Aufruf
```kotlin
                repository.addHabit(name, color, freq)
```
zu
```kotlin
                repository.addHabit(name, color, freq, group = "")
```
(ein reines Kompilier-Notbehelf mit hartkodiertem leerem Gruppenwert — Task 3
ersetzt das durch die echte, aus dem Dropdown stammende Gruppe). Danach
`./gradlew testDebugUnitTest assembleDebug` erneut ausführen, jetzt
`BUILD SUCCESSFUL` erwartet.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModel.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt
git commit -m "feat: Gruppen-/Filterlogik in HabitRepository und HabitListViewModel"
```

---

### Task 3: HabitEditViewModel + HabitEditSheet Gruppen-Dropdown + MainActivity-Verdrahtung

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt`

**Interfaces:**
- Consumes: `HabitListViewModel.availableGroups: StateFlow<List<String>>`
  (Task 2), `HabitRepository.addHabit(name, color, freq, group)` (Task 2).
- Produces: `HabitEditViewModel.group: String`,
  `HabitEditViewModel.onGroupChange(value: String)`,
  `HabitEditSheet(viewModel, availableGroups: List<String>, onDismiss, onSaved)`
  (neuer Parameter `availableGroups`).

Reine UI-Ergänzung + Verdrahtung, keine neue Testdatei (folgt der
etablierten Konvention aus Plan A: `HabitEditViewModel` hat keinen eigenen
dedizierten Unit-Test, da es reiner Compose-`mutableStateOf`-Formularzustand
ist). Verifikation ist "kompiliert erfolgreich" + bestehende Tests bleiben
grün.

- [ ] **Step 1: `HabitEditViewModel.kt` um `group` erweitern**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt`
mit:

```kotlin
package com.tatoli.habittracker.ui.habitedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.ui.theme.HabitPalette
import kotlinx.coroutines.launch

// Bewusst nicht über viewModel()/ViewModelStore bezogen (siehe MainActivity.remember(state)) —
// jedes Sheet-Öffnen braucht eine frische Instanz ohne Altzustand aus vorherigen Sitzungen.
// onCleared()/viewModelScope-Cancellation durch das Framework finden dadurch nicht statt;
// die Coroutine-Nutzung bleibt kurzlebig (save/delete), das ist hier unkritisch.
class HabitEditViewModel(
    private val repository: HabitRepository,
    private val habitId: Long?
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var color by mutableStateOf(HabitPalette.first())
        private set
    var freq by mutableStateOf("daily")
        private set
    var group by mutableStateOf("")
        private set
    var loaded by mutableStateOf(habitId == null)
        private set

    val isEditing: Boolean get() = habitId != null

    init {
        if (habitId != null) {
            viewModelScope.launch {
                repository.getHabitById(habitId)?.let { habit ->
                    name = habit.name
                    color = habit.color
                    freq = habit.freq
                    group = habit.group
                }
                loaded = true
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onColorChange(value: String) { color = value }
    fun onFreqChange(value: String) { freq = value }
    fun onGroupChange(value: String) { group = value }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            if (habitId == null) {
                repository.addHabit(name, color, freq, group)
            } else {
                repository.updateHabit(HabitEntity(id = habitId, name = name, color = color, freq = freq, group = group))
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.deleteHabit(HabitEntity(id = id, name = name, color = color, freq = freq, group = group))
            onDone()
        }
    }
}
```

- [ ] **Step 2: `HabitEditSheet.kt` um Gruppen-Dropdown erweitern**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt`
mit:

```kotlin
package com.tatoli.habittracker.ui.habitedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tatoli.habittracker.ui.theme.HabitPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditSheet(
    viewModel: HabitEditViewModel,
    availableGroups: List<String>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = if (viewModel.isEditing) "Habit bearbeiten" else "Habit anlegen",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text("Farbe", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                HabitPalette.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(8.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (viewModel.color == hex) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { viewModel.onColorChange(hex) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Nur "Täglich" verfügbar: "Wöchentlich" hätte noch keine Wochen-Key-bewusste
            // Abfrage/Verarbeitung (ViewModel/DAO) hinter sich und würde still wie "daily"
            // behandelt. Kehrt zurück, sobald ein späterer Plan echte Wochensemantik liefert.
            Text("Rhythmus", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = viewModel.freq == "daily",
                    onClick = { viewModel.onFreqChange("daily") },
                    label = { Text("Täglich") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = viewModel.freq == "weekly",
                    onClick = { viewModel.onFreqChange("weekly") },
                    label = { Text("Wöchentlich") }
                )
            }
            Spacer(Modifier.height(16.dp))

            GroupDropdown(
                availableGroups = availableGroups,
                selectedGroup = viewModel.group,
                onGroupSelected = viewModel::onGroupChange
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.save(onDone = onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.name.isNotBlank()
            ) {
                Text("Speichern")
            }

            if (viewModel.isEditing) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.delete(onDone = onSaved) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Löschen")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdown(
    availableGroups: List<String>,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var addingNew by remember { mutableStateOf(false) }
    var newGroupText by remember { mutableStateOf("") }

    Text("Gruppe", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))

    val displayValue = when {
        addingNew -> "+ Neue Gruppe…"
        selectedGroup.isEmpty() -> "Keine Gruppe"
        else -> selectedGroup
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Gruppe wählen") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Keine Gruppe") },
                onClick = {
                    addingNew = false
                    onGroupSelected("")
                    expanded = false
                }
            )
            availableGroups.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g) },
                    onClick = {
                        addingNew = false
                        onGroupSelected(g)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("+ Neue Gruppe…") },
                onClick = {
                    addingNew = true
                    newGroupText = ""
                    onGroupSelected("")
                    expanded = false
                }
            )
        }
    }

    if (addingNew) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newGroupText,
            onValueChange = { value ->
                newGroupText = value.take(40)
                onGroupSelected(newGroupText.trim())
            },
            label = { Text("Name der neuen Gruppe") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

Hinweis: `Modifier.menuAnchor()` (parameterlos) ist die in diesem Projekt
gültige API — die App ist auf `compose-bom:2024.06.00` (Material3 1.2.1)
gepinnt, wo nur die parameterlose Variante existiert; die spätere typisierte
Variante mit `MenuAnchorType` gibt es erst ab Material3 1.3.0 und würde
hier nicht kompilieren.

- [ ] **Step 3: `MainActivity.kt` verdrahten**

In `android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt`:
füge den Import
```kotlin
import androidx.compose.runtime.collectAsState
```
hinzu (alphabetisch einordnen). In `HabitTrackerApp(repository: HabitRepository)`,
direkt nach der Zeile, die `listViewModel` erzeugt, ergänze:
```kotlin
    val availableGroups by listViewModel.availableGroups.collectAsState()
```
Und ergänze in **beiden** `HabitEditSheet(...)`-Aufrufen (im
`EditSheetState.AddNew`- und im `EditSheetState.EditExisting`-Zweig) den
neuen Parameter:
```kotlin
            HabitEditSheet(
                viewModel = editViewModel,
                availableGroups = availableGroups,
                onDismiss = { sheetState = EditSheetState.Hidden },
                onSaved = { sheetState = EditSheetState.Hidden }
            )
```

- [ ] **Step 4: Compile check**

```bash
export JAVA_HOME="$HOME/.jdk17"; export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"; export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd android && ./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL`, alle bisherigen Tests (Task 1 + Task 2 +
Plan-B-Tests) weiterhin grün. `HabitListScreen.kt` ruft `HabitEditSheet`
noch nicht auf (das tut nur `MainActivity.kt`), daher keine weiteren
Anpassungen an `HabitListScreen.kt` in diesem Task nötig — die
Filter-Chips/Gruppen-Überschriften kommen in Task 4.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt
git commit -m "feat: Gruppen-Dropdown im Anlegen/Bearbeiten-Sheet"
```

---

### Task 4: HabitListScreen UI (Filter-Chips + Gruppen-Überschriften)

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`

**Interfaces:**
- Consumes: `HabitListViewModel.availableGroups`, `.filterGroup`,
  `.listDisplay`, `.selectGroupFilter(group)` (Task 2), `HabitGroupSection`,
  `HabitListDisplay` (Task 2).

Reine UI-Ergänzung auf Basis der in Task 2 bereits korrekt berechneten
Daten — keine Logikänderung. Verifikation ist "kompiliert erfolgreich".

- [ ] **Step 1: `HabitListScreen.kt` um Filter-Chips und Gruppen-Überschriften erweitern**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`
mit:

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tatoli.habittracker.util.dateKeyOf
import com.tatoli.habittracker.util.firstDayOfWeekOffset
import com.tatoli.habittracker.util.weekKey
import java.time.YearMonth

private val MONTH_NAMES = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
)
private val DAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit
) {
    val viewMonth by viewModel.viewMonth.collectAsState()
    val availableGroups by viewModel.availableGroups.collectAsState()
    val filterGroup by viewModel.filterGroup.collectAsState()
    val listDisplay by viewModel.listDisplay.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            MonthHeader(
                viewMonth = viewMonth,
                onPrev = viewModel::prevMonth,
                onNext = viewModel::nextMonth
            )
            if (availableGroups.isNotEmpty()) {
                FilterChipsRow(
                    availableGroups = availableGroups,
                    filterGroup = filterGroup,
                    onSelect = viewModel::selectGroupFilter
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                listDisplay.sections.forEach { section ->
                    if (listDisplay.showGroupHeaders) {
                        item(key = "header-${section.label}") {
                            Text(
                                text = section.label.ifEmpty { "Allgemein" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(section.habits, key = { it.id }) { habit ->
                        HabitCard(
                            habit = habit,
                            onToggleToday = { viewModel.toggleToday(habit) },
                            onToggleCell = { dateKey, currentlyDone ->
                                viewModel.toggleCell(habit.id, dateKey, currentlyDone)
                            },
                            onEdit = { onEditHabit(habit.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    availableGroups: List<String>,
    filterGroup: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filterGroup == null,
            onClick = { onSelect(null) },
            label = { Text("Alle") }
        )
        availableGroups.forEach { g ->
            FilterChip(
                selected = filterGroup == g,
                onClick = { onSelect(g) },
                label = { Text(g) }
            )
        }
    }
}

@Composable
private fun MonthHeader(viewMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val isCurrentMonth = viewMonth == YearMonth.now()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Vorheriger Monat")
        }
        Text(
            text = "${MONTH_NAMES[viewMonth.monthValue - 1]} ${viewMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = !isCurrentMonth) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Nächster Monat")
        }
    }
}

@Composable
fun HabitCard(
    habit: HabitDisplayState,
    onToggleToday: () -> Unit,
    onToggleCell: (dateKey: String, currentlyDone: Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val habitColor = parseHexColor(habit.color)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(habitColor, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconToggleButton(checked = habit.doneToday, onCheckedChange = { onToggleToday() }) {
                    Icon(
                        imageVector = if (habit.doneToday) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (habit.doneToday) "Heute erledigt" else "Heute nicht erledigt",
                        tint = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Bearbeiten")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (habit.freq == "weekly") {
                    "🔥 ${habit.streakCount} Wochen Serie · ${habit.monthTotal}/${habit.weekCells.size} diesen Monat"
                } else {
                    "🔥 ${habit.streakCount} Tage Serie · ${habit.monthTotal}/${habit.dayCells.size} diesen Monat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (habit.freq == "weekly") {
                WeeklyStrip(habit.weekCells, habitColor, onToggleCell)
            } else {
                DailyGrid(habit.dayCells, habitColor, onToggleCell)
            }
        }
    }
}

@Composable
private fun DailyGrid(cells: List<DayCell>, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val firstOffset = cells.firstOrNull()?.let { firstDayOfWeekOffset(YearMonth.from(it.date)) } ?: 0
        val paddedCells: List<DayCell?> = List(firstOffset) { null } + cells
        paddedCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(modifier = Modifier.weight(1f).size(36.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (cell != null) {
                            DayCellButton(cell, habitColor, onToggleCell)
                        }
                    }
                }
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).size(36.dp))
                }
            }
        }
    }
}

@Composable
private fun DayCellButton(cell: DayCell, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    val description = "${cell.date.dayOfMonth}. ${MONTH_NAMES[cell.date.monthValue - 1]}" +
        if (cell.done) " erledigt" else ""
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (cell.done) habitColor else Color.Transparent)
            .then(
                if (cell.isToday) Modifier.border(1.dp, habitColor, RoundedCornerShape(9.dp)) else Modifier
            )
            .clickable(enabled = !cell.isFuture) { onToggleCell(dateKeyOf(cell.date), cell.done) }
            .alpha(if (cell.isFuture) 0.28f else 1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WeeklyStrip(cells: List<WeekCell>, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Box(modifier = Modifier.weight(1f).size(36.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                WeekCellButton(cell, habitColor, onToggleCell)
            }
        }
    }
}

@Composable
private fun WeekCellButton(cell: WeekCell, habitColor: Color, onToggleCell: (String, Boolean) -> Unit) {
    val description = "Woche ${cell.isoWeekNumber}" + if (cell.done) " erledigt" else ""
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(if (cell.done) habitColor else Color.Transparent)
            .then(
                if (cell.isCurrentWeek) Modifier.border(1.dp, habitColor, RoundedCornerShape(9.dp)) else Modifier
            )
            .clickable(enabled = !cell.isFuture) { onToggleCell(weekKey(cell.monday), cell.done) }
            .alpha(if (cell.isFuture) 0.28f else 1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "KW${cell.isoWeekNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun parseHexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
```

Änderungen gegenüber der vorherigen Version: `HabitListScreen` liest jetzt
zusätzlich `availableGroups`/`filterGroup`/`listDisplay` und rendert die
neue `FilterChipsRow` (nur wenn `availableGroups` nicht leer ist) sowie
Gruppen-Überschriften über `item(key = "header-...")`-Einträge, wenn
`listDisplay.showGroupHeaders` wahr ist. `MonthHeader`, `HabitCard`,
`DailyGrid`, `DayCellButton`, `WeeklyStrip`, `WeekCellButton`,
`parseHexColor` sind unverändert aus Plan B übernommen.

- [ ] **Step 2: Compile check**

```bash
export JAVA_HOME="$HOME/.jdk17"; export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"; export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd android && ./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL`, alle bisherigen Tests weiterhin grün (kein
neuer Test in diesem Task — reine UI).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt
git commit -m "feat: Filter-Chips und Gruppen-Überschriften in der Habit-Liste"
```

---

## Verifikation nach Abschluss

- Push auf `main` in `Sparxx95/habit-tracker-app`.
- `android-build.yml` läuft automatisch (push-Trigger auf `android/**`) —
  grünen Lauf auf einem echten `ubuntu-latest`-Runner abwarten.
- APK aus dem Actions-Artifact herunterladen und im Windows-Emulator
  **über die bestehende Installation drüberinstallieren, ohne vorher zu
  deinstallieren** (z. B. `adb install -r app-debug.apk`, oder per
  Drag-and-Drop ohne vorheriges Entfernen der App). Das unterscheidet sich
  bewusst vom Deinstallieren-und-neu-installieren-Vorgehen aus den Plan
  A/B-Tests: dort war das nötig, um einen Debug-Signing-Key-Mismatch
  aufzulösen, hier geht es aber um den Test einer echten Schema-Migration
  — ein vorheriges Deinstallieren würde die Datenbankdatei löschen, sodass
  beim nächsten Start `onCreate` eine leere v2-Datenbank anlegt und
  `MIGRATION_1_2` nie tatsächlich läuft. Danach manuell testen: Habit
  einer neuen Gruppe zuweisen ("+ Neue Gruppe…"), Habit einer bestehenden
  Gruppe zuweisen, Gruppen-Überschriften in der Liste prüfen, Filter-Chip
  antippen und prüfen, dass nur Habits dieser Gruppe angezeigt werden,
  "Alle" wieder antippen. **Wichtig:** da dies die erste echte
  Schema-Migration ist, sollten die im Emulator bereits vorhandenen
  Habits (aus Plan A/B-Tests) nach dem Update weiterhin vollständig
  vorhanden sein (Name, Farbe, Rhythmus, komplette Done-Historie/Streak)
  — kein Datenverlust durch die Migration.
