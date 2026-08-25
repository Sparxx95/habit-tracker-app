# Plan D: Statistik & Dashboard für die native Android-App — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zwei neue Vollbild-Screens (Statistik, Dashboard), erreichbar über
zwei neue FABs auf dem Haupt-Screen, mit voller funktionaler Parität zu den
gleichnamigen Sheets der Web-App (Tabellen-/Ring-Ansicht, KW-Streifen,
Legende, Gesamtübersicht/Wochentags-Muster/Gruppen-Vergleich/6-Monats-Trend).

**Architecture:** `HabitEntity` bekommt ein `createdAt`-Feld über eine
weitere additive Room-Migration (v2→v3) — die Statistik-Formeln brauchen
das Erstellungsdatum, das bisher nirgends gespeichert wird. Alle
Berechnungen (Erfolgsquote, längste Serie, Wochentags-Muster,
Gruppen-Vergleich, Trend) leben als reine, unabhängig testbare
Kotlin-Funktionen in `util/StatsCalculations.kt`. Zwei getrennte
ViewModels (`StatsViewModel`, `DashboardViewModel`) speisen zwei getrennte
Vollbild-Screens, gleiches Muster wie `HabitListViewModel`/
`HabitEditViewModel`. Das Ringdiagramm ist ein eigener `Canvas`-Composable
(`RingStatsChart`), der die SVG-Ring-Geometrie der Web-App 1:1 in
Kotlin-Trigonometrie überträgt und für gekrümmte Text-Labels
`nativeCanvas.drawTextOnPath(...)` nutzt.

**Tech Stack:** Kotlin, Jetpack Compose (`Canvas`, `nativeCanvas`), Room
(`Migration`), Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-25-native-android-plan-d-design.md`

## Global Constraints

- `applicationId`/Kotlin-`namespace`: `com.tatoli.habittracker`.
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- Migration ist **additiv** (`ALTER TABLE ... ADD COLUMN`), kein
  `fallbackToDestructiveMigration()` — bestehende Habits/Done-Historie
  bleiben vollständig erhalten. Bestehende Zeilen bekommen
  `System.currentTimeMillis()` zum Migrationszeitpunkt als `createdAt`
  (nicht Epoch 0 — würde Erfolgsquoten für Alt-Habits massiv verzerren).
- Statistik und Dashboard sind **zwei getrennte Vollbild-Screens** mit
  **zwei getrennten ViewModels** (kein gemeinsames ViewModel) — Statistik
  hat Monatsnavigation, Dashboard nicht; beide liefern unterschiedliche
  Aggregate.
- Ringdiagramm wird **nah am Original** umgesetzt (290°-Bogen mit
  70°-Öffnung, gekrümmte Ring-Labels, seitliche Tail-Legende) — bewusst
  mit dem Nutzer abgestimmte Abweichung vom sonstigen
  "idiomatisch, nicht pixelgenau"-Grundsatz.
- `drawTextOnPath` läuft über `drawContext.canvas.nativeCanvas` (reguläre
  `android.graphics.Canvas`-API) — das ist eine bewusste, dokumentierte
  Abweichung von reiner Compose-`DrawScope`-API, kein Fehler.
- **Bewusst NICHT Teil dieses Plans:** XML-Backup (Plan E),
  Login/Firebase (dauerhaft außerhalb des Scopes), Änderungen an
  Zeitraster-/Streak-/Gruppen-Logik aus Plan B/C.
- Kein Play-Store-Release, kein signierter Build — nur Debug-APKs.
- **Lokale Verifikation:** Vor jedem Gradle-Aufruf:
  ```bash
  export JAVA_HOME="$HOME/.jdk17"
  export ANDROID_HOME="$HOME/.android-sdk"
  export ANDROID_SDK_ROOT="$HOME/.android-sdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
  Immer `--offline` an `./gradlew`-Aufrufe anhängen (ohne diese Flag kann
  der Build in dieser Sandbox an einem Netzwerk-Check ohne jede Ausgabe
  hängen bleiben). Vor jedem Build-Versuch `./gradlew --stop`, falls ein
  vorheriger Aufruf per Timeout abgebrochen wurde — der Daemon läuft sonst
  unsichtbar weiter und blockiert Ressourcen für den nächsten Versuch. Ein
  laufender Build kann mehrere Minuten ganz ohne neue Konsolenausgabe
  zeigen (z. B. bei `kspDebugKotlin` mit kaltem Cache) — das ist in dieser
  Sandbox normal, kein Hänger; mit `top -bn1` prüfen (aktiver `java`-Prozess
  mit hoher CPU-Last = läuft wirklich).
- Compose Canvas/`nativeCanvas`-APIs sind in diesem Projekt bisher
  **ungenutzt** (erster Einsatz in Task 5) — API-Signaturen (Parameter-
  Namen/Reihenfolge von `drawArc`, exakte `nativeCanvas.drawTextOnPath`-
  Signatur) vor Gebrauch gegen die tatsächlich gepinnte Compose-BOM-Version
  verifizieren (Decompilierung wie in Plan B/C bei Zweifeln), nicht blind
  aus diesem Plan übernehmen, falls der Compiler widerspricht.

---

### Task 1: `createdAt`-Migration & Repository-/EditViewModel-Wiring

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitEntity.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/AppDatabase.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/data/AppDatabaseMigrationTest.kt`
- Test (neu): `android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModelTest.kt`

**Interfaces:**
- Produces: `HabitEntity.createdAt: Long` (Epoch-Millis, Default `0`),
  `MIGRATION_2_3: Migration`, `HabitRepository.addHabit(name: String,
  color: String, freq: String, group: String, createdAt: Long): Long`.
- Consumes: nichts aus späteren Tasks.

- [ ] **Step 1: Migrationstest schreiben (schlägt fehl, da `MIGRATION_2_3`
  noch nicht existiert)**

In `AppDatabaseMigrationTest.kt` folgenden Test ergänzen (Datei existiert
bereits mit `migrate1To2_...`, diesen Test danach einfügen):

```kotlin
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
```

Am Dateikopf `import org.junit.Assert.assertTrue` ergänzen (neben dem
bereits vorhandenen `assertEquals`-Import).

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
cd android
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.AppDatabaseMigrationTest" --offline
```
Erwartet: FAIL — `MIGRATION_2_3` ist nicht deklariert (Compile-Fehler).

- [ ] **Step 3: `HabitEntity.createdAt` ergänzen**

In `HabitEntity.kt`, `createdAt` als letztes Feld anfügen:

```kotlin
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val freq: String,      // "daily" | "weekly"
    val group: String = "", // "" = keine Gruppe
    val createdAt: Long = 0 // Epoch-Millis, System.currentTimeMillis() bei Anlage
)
```

- [ ] **Step 4: `MIGRATION_2_3` + `version = 3` in `AppDatabase.kt`**

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE habits SET createdAt = ? WHERE createdAt = 0", arrayOf(System.currentTimeMillis()))
    }
}
```

`@Database(..., version = 3, exportSchema = true)` setzen, und
`.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` im `Room.databaseBuilder(...)`-
Aufruf.

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.AppDatabaseMigrationTest" --offline
```
Erwartet: PASS (beide Migrationstests, `migrate1To2_...` und
`migrate2To3_...`).

- [ ] **Step 6: `HabitRepository.addHabit` um `createdAt` erweitern**

```kotlin
    suspend fun addHabit(name: String, color: String, freq: String, group: String, createdAt: Long): Long =
        dao.insertHabit(HabitEntity(name = name, color = color, freq = freq, group = group, createdAt = createdAt))
```

- [ ] **Step 7: `HabitEditViewModelTest.kt` anlegen (schlägt fehl, da
  `createdAt`-State noch nicht existiert)**

Neue Datei, gleiches Robolectric+In-Memory-Room-Muster wie
`HabitListViewModelTest.kt`:

```kotlin
package com.tatoli.habittracker.ui.habitedit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HabitEditViewModelTest {

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

    // repository.observeHabitsWithDone() statt eines direkten Einmal-Reads nach save():
    // Rooms Suspend-DAO-Funktionen laufen über Rooms eigenen Hintergrund-Executor, nicht
    // zwingend synchron auf dem Unconfined-Dispatcher des Tests. .first{predicate} wartet
    // robust auf die tatsächliche Flow-Emission statt auf eine angenommene Ausführungsreihenfolge
    // zu vertrauen — gleiches, bereits bewährtes Muster wie in HabitListViewModelTest.

    @Test
    fun save_newHabit_setsCreatedAtToNow() = runBlocking {
        val before = System.currentTimeMillis()
        val viewModel = HabitEditViewModel(repository, habitId = null)
        viewModel.onNameChange("Lesen")
        viewModel.save {}

        val saved = repository.observeHabitsWithDone().first { it.isNotEmpty() }.first().habit
        val after = System.currentTimeMillis()
        assertEquals("Lesen", saved.name)
        assertTrue(saved.createdAt in before..after)
    }

    @Test
    fun save_existingHabit_preservesOriginalCreatedAt() = runBlocking {
        val originalCreatedAt = 1_000_000L
        val habitId = db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = originalCreatedAt)
        )
        val viewModel = HabitEditViewModel(repository, habitId = habitId)
        viewModel.onNameChange("Lesen (bearbeitet)")
        viewModel.save {}

        val saved = repository.observeHabitsWithDone()
            .first { it.isNotEmpty() && it.first().habit.name == "Lesen (bearbeitet)" }
            .first().habit
        assertEquals(originalCreatedAt, saved.createdAt)
    }
}
```

- [ ] **Step 8: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.habitedit.HabitEditViewModelTest" --offline
```
Erwartet: FAIL — `repository.addHabit(...)` im bestehenden `save()`-Aufruf
hat noch die alte 4-Parameter-Signatur, `createdAt` bleibt beim
`updateHabit`-Zweig auf `0` statt dem Original-Wert.

- [ ] **Step 9: `HabitEditViewModel` um `createdAt`-State erweitern**

In `HabitEditViewModel.kt`:

```kotlin
    var group by mutableStateOf("")
        private set
    private var createdAt by mutableStateOf(0L)
    var loaded by mutableStateOf(habitId == null)
        private set
```

`init`-Block ergänzen (`createdAt = habit.createdAt` beim Laden):

```kotlin
    init {
        if (habitId != null) {
            viewModelScope.launch {
                repository.getHabitById(habitId)?.let { habit ->
                    name = habit.name
                    color = habit.color
                    freq = habit.freq
                    group = habit.group
                    createdAt = habit.createdAt
                }
                loaded = true
            }
        }
    }
```

`save()` und `delete()` anpassen:

```kotlin
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            if (habitId == null) {
                repository.addHabit(name, color, freq, group, System.currentTimeMillis())
            } else {
                repository.updateHabit(
                    HabitEntity(id = habitId, name = name, color = color, freq = freq, group = group, createdAt = createdAt)
                )
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.deleteHabit(
                HabitEntity(id = id, name = name, color = color, freq = freq, group = group, createdAt = createdAt)
            )
            onDone()
        }
    }
```

- [ ] **Step 10: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --offline
```
Erwartet: PASS, alle Testklassen (inkl. der beiden neuen Tests).

- [ ] **Step 11: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitEntity.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/AppDatabase.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/data/AppDatabaseMigrationTest.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModelTest.kt
git commit -m "feat: Room-Migration v2->v3 für Habit-Erstellungsdatum (createdAt)"
```

---

### Task 2: `StatsCalculations.kt` — reine Berechnungsfunktionen

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/util/StatsCalculations.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/util/DateUtils.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/util/StatsCalculationsTest.kt`

**Interfaces:**
- Consumes: `com.tatoli.habittracker.data.HabitWithDoneEntities`
  (`habit: HabitEntity`, `doneEntries: List<HabitDoneEntity>`, bereits aus
  Task 1/vorherigen Plänen vorhanden), `mondayOf`/`weekKey`/`dateKeyOf`
  aus `DateUtils.kt`.
- Produces (für Task 3/6):
  `data class WeekdayStat(val label: String, val pct: Int)`,
  `data class GroupStat(val label: String, val pct: Int)`,
  `data class MonthStat(val label: String, val pct: Int)`,
  `fun successRate(entry: HabitWithDoneEntities, today: LocalDate): Int`,
  `fun maxStreakEver(entry: HabitWithDoneEntities): Int`,
  `fun weekdayPatternData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<WeekdayStat>`,
  `fun groupComparisonData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<GroupStat>`,
  `fun trendData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<MonthStat>`,
  `fun createdDate(createdAt: Long): LocalDate` (in `DateUtils.kt`).

Diese Funktionen sind 1:1-Portierungen der gleichnamigen Funktionen in
`habit-tracker/index.html` (`successRate`, `maxStreakEver`,
`weekdayPatternData`, `groupComparisonData`, `trendData`,
`isoWeeksInYear`, `nextIsoWeek`) — bei Unklarheiten dort nachschlagen.

- [ ] **Step 1: `createdDate`-Helfer in `DateUtils.kt` ergänzen**

Am Ende der Datei anfügen (Imports `java.time.Instant`, `java.time.ZoneId`
am Dateikopf ergänzen):

```kotlin
fun createdDate(createdAt: Long): LocalDate =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
```

- [ ] **Step 2: Fehlschlagende Tests für `successRate` und
  `maxStreakEver` schreiben**

Neue Datei `StatsCalculationsTest.kt`:

```kotlin
package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitDoneEntity
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitWithDoneEntities
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculationsTest {

    private fun entry(
        freq: String,
        createdAt: Long,
        doneKeys: List<String>,
        group: String = ""
    ) = HabitWithDoneEntities(
        habit = HabitEntity(id = 1, name = "H", color = "#F2B450", freq = freq, group = group, createdAt = createdAt),
        doneEntries = doneKeys.map { HabitDoneEntity(habitId = 1, dateKey = it) }
    )

    @Test
    fun successRate_daily_countsDoneOverDaysSinceCreation() {
        val created = LocalDate.of(2026, 8, 1)
        val today = LocalDate.of(2026, 8, 10) // 10 Tage inklusive
        val e = entry("daily", epochMillisOf(created), listOf("2026-08-01", "2026-08-05", "2026-08-10"))
        assertEquals(30, successRate(e, today)) // 3/10 = 30%
    }

    @Test
    fun successRate_daily_cappedAt100() {
        val created = LocalDate.of(2026, 8, 9)
        val today = LocalDate.of(2026, 8, 9)
        val e = entry("daily", epochMillisOf(created), listOf("2026-08-09", "2026-08-09"))
        assertEquals(100, successRate(e, today))
    }

    @Test
    fun successRate_weekly_countsDoneOverWeeksSinceCreation() {
        val created = LocalDate.of(2026, 8, 3) // Montag KW32
        val today = LocalDate.of(2026, 8, 17)  // Montag KW34 -> 3 Wochen (32,33,34)
        val e = entry("weekly", epochMillisOf(created), listOf("2026-W32", "2026-W34"))
        assertEquals(67, successRate(e, today)) // round(2/3*100) = 67
    }

    @Test
    fun maxStreakEver_daily_findsLongestConsecutiveRun() {
        val e = entry(
            "daily", epochMillisOf(LocalDate.of(2026, 8, 1)),
            listOf("2026-08-01", "2026-08-02", "2026-08-03", "2026-08-05", "2026-08-06")
        )
        assertEquals(3, maxStreakEver(e))
    }

    @Test
    fun maxStreakEver_weekly_findsLongestConsecutiveRunAcrossYearBoundary() {
        // KW52 2025, KW1 2026, KW2 2026 = 3 aufeinanderfolgende Wochen
        val e = entry(
            "weekly", epochMillisOf(LocalDate.of(2025, 12, 1)),
            listOf("2025-W52", "2026-W01", "2026-W02", "2026-W10")
        )
        assertEquals(3, maxStreakEver(e))
    }

    @Test
    fun maxStreakEver_empty_returnsZero() {
        val e = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), emptyList())
        assertEquals(0, maxStreakEver(e))
    }

    private fun epochMillisOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
```

- [ ] **Step 3: Tests laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.StatsCalculationsTest" --offline
```
Erwartet: FAIL — `StatsCalculations.kt` existiert noch nicht (Compile-Fehler).

- [ ] **Step 4: `successRate` und `maxStreakEver` implementieren**

Neue Datei `StatsCalculations.kt`:

```kotlin
package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitWithDoneEntities
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

fun successRate(entry: HabitWithDoneEntities, today: LocalDate): Int {
    val created = createdDate(entry.habit.createdAt)
    val doneCount = entry.doneEntries.size
    if (entry.habit.freq == "weekly") {
        val totalWeeks = ChronoUnit.WEEKS.between(mondayOf(created), mondayOf(today)).toInt() + 1
        return minOf(100, Math.round(doneCount * 100.0 / maxOf(1, totalWeeks)).toInt())
    }
    val totalDays = maxOf(1, ChronoUnit.DAYS.between(created, today).toInt() + 1)
    return minOf(100, Math.round(doneCount * 100.0 / totalDays).toInt())
}

private fun isoWeeksInYear(year: Int): Int =
    LocalDate.of(year, 12, 28).get(WeekFields.ISO.weekOfWeekBasedYear())

private fun nextIsoWeek(year: Int, week: Int): Pair<Int, Int> =
    if (week < isoWeeksInYear(year)) year to (week + 1) else (year + 1) to 1

private fun parseWeekKey(key: String): Pair<Int, Int> {
    val parts = key.split("-W")
    return parts[0].toInt() to parts[1].toInt()
}

fun maxStreakEver(entry: HabitWithDoneEntities): Int {
    val keys = entry.doneEntries.map { it.dateKey }
    if (keys.isEmpty()) return 0
    if (entry.habit.freq == "weekly") {
        val parsed = keys.map(::parseWeekKey).sortedWith(compareBy({ it.first }, { it.second }))
        var best = 1
        var cur = 1
        for (i in 1 until parsed.size) {
            val next = nextIsoWeek(parsed[i - 1].first, parsed[i - 1].second)
            cur = if (next == parsed[i]) cur + 1 else 1
            if (cur > best) best = cur
        }
        return best
    }
    val days = keys.map { LocalDate.parse(it) }.sorted()
    var best = 1
    var cur = 1
    for (i in 1 until days.size) {
        cur = if (days[i - 1].plusDays(1) == days[i]) cur + 1 else 1
        if (cur > best) best = cur
    }
    return best
}
```

- [ ] **Step 5: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.StatsCalculationsTest" --offline
```
Erwartet: PASS (alle 6 bisherigen Tests).

- [ ] **Step 6: Fehlschlagende Tests für `weekdayPatternData`,
  `groupComparisonData`, `trendData` ergänzen**

An `StatsCalculationsTest.kt` anfügen (vor der letzten schließenden `}`):

```kotlin
    @Test
    fun weekdayPatternData_aggregatesAcrossDailyHabitsOnly() {
        val today = LocalDate.of(2026, 8, 10) // Montag
        val daily = entry(
            "daily", epochMillisOf(LocalDate.of(2026, 8, 3)), // Montag der Vorwoche
            listOf("2026-08-03", "2026-08-10")
        )
        val weekly = entry("weekly", epochMillisOf(LocalDate.of(2026, 8, 3)), listOf("2026-W32"))
        val result = weekdayPatternData(listOf(daily, weekly), today)
        assertEquals(7, result.size)
        assertEquals("Mo", result[0].label)
        assertEquals(100, result[0].pct) // beide Montage im Zeitraum erledigt
    }

    @Test
    fun weekdayPatternData_excludesUnfinishedToday() {
        val today = LocalDate.of(2026, 8, 10) // Montag, noch nicht erledigt
        val daily = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 10)), emptyList())
        val result = weekdayPatternData(listOf(daily), today)
        // Grenze ist "gestern" statt heute, solange heute nicht erledigt -> totals[Mo] bleibt 0
        assertEquals(0, result[0].pct)
    }

    @Test
    fun groupComparisonData_averagesPerGroupAndSortsDescending() {
        val today = LocalDate.of(2026, 8, 10)
        val a = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), (1..10).map { "2026-08-%02d".format(it) }, group = "Fitness")
        val b = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), emptyList(), group = "Lesen")
        val result = groupComparisonData(listOf(a, b), today)
        assertEquals(listOf("Fitness", "Lesen"), result.map { it.label })
        assertEquals(100, result[0].pct)
        assertEquals(0, result[1].pct)
    }

    @Test
    fun groupComparisonData_ungroupedHabitsUseFallbackLabel() {
        val today = LocalDate.of(2026, 8, 10)
        val a = entry("daily", epochMillisOf(LocalDate.of(2026, 8, 1)), emptyList(), group = "")
        val result = groupComparisonData(listOf(a), today)
        assertEquals("Ohne Gruppe", result[0].label)
    }

    @Test
    fun trendData_returnsSixMonthsEndingAtCurrentMonth() {
        val today = LocalDate.of(2026, 8, 10)
        val e = entry("daily", epochMillisOf(LocalDate.of(2026, 1, 1)), listOf("2026-08-01", "2026-08-10"))
        val result = trendData(listOf(e), today)
        assertEquals(6, result.size)
        assertEquals("Aug", result.last().label)
    }
```

Am Dateikopf `import com.tatoli.habittracker.data.HabitEntity` ist
bereits vorhanden; keine weiteren Imports nötig.

- [ ] **Step 7: Tests laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.StatsCalculationsTest" --offline
```
Erwartet: FAIL — die drei Funktionen fehlen noch.

- [ ] **Step 8: `weekdayPatternData`, `groupComparisonData`, `trendData`
  implementieren**

An `StatsCalculations.kt` anfügen (Imports `java.time.YearMonth` ergänzen):

```kotlin
data class WeekdayStat(val label: String, val pct: Int)
data class GroupStat(val label: String, val pct: Int)
data class MonthStat(val label: String, val pct: Int)

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val MONTH_SHORT_NAMES = listOf(
    "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"
)

fun weekdayPatternData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<WeekdayStat> {
    val totals = IntArray(7)
    val dones = IntArray(7)
    entries.filter { it.habit.freq == "daily" }.forEach { entry ->
        val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
        val created = createdDate(entry.habit.createdAt)
        val boundary = if (doneKeys.contains(dateKeyOf(today))) today else today.minusDays(1)
        if (boundary.isBefore(created)) return@forEach
        var cur = created
        while (!cur.isAfter(boundary)) {
            val dow = cur.dayOfWeek.value - 1 // Montag=0 .. Sonntag=6
            totals[dow]++
            if (doneKeys.contains(dateKeyOf(cur))) dones[dow]++
            cur = cur.plusDays(1)
        }
    }
    return WEEKDAY_LABELS.indices.map { i ->
        WeekdayStat(WEEKDAY_LABELS[i], if (totals[i] > 0) Math.round(dones[i] * 100.0 / totals[i]).toInt() else 0)
    }
}

fun groupComparisonData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<GroupStat> {
    val sums = LinkedHashMap<String, Int>()
    val counts = LinkedHashMap<String, Int>()
    entries.forEach { entry ->
        val label = entry.habit.group.ifEmpty { "Ohne Gruppe" }
        sums[label] = (sums[label] ?: 0) + successRate(entry, today)
        counts[label] = (counts[label] ?: 0) + 1
    }
    return sums.keys.map { label ->
        GroupStat(label, Math.round(sums.getValue(label) * 1.0 / counts.getValue(label)).toInt())
    }.sortedByDescending { it.pct }
}

fun trendData(entries: List<HabitWithDoneEntities>, today: LocalDate): List<MonthStat> {
    val nowWeekKey = weekKey(today)
    val currentMonth = YearMonth.from(today)
    val months = (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    return months.map { month ->
        var possible = 0
        var done = 0
        entries.forEach { entry ->
            val created = createdDate(entry.habit.createdAt)
            val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
            if (entry.habit.freq == "daily") {
                for (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    if (date.isBefore(created) || date.isAfter(today)) continue
                    possible++
                    if (doneKeys.contains(dateKeyOf(date))) done++
                }
            } else {
                var cur = mondayOf(month.atDay(1))
                val monthLastDay = month.atEndOfMonth()
                while (!cur.isAfter(monthLastDay)) {
                    if (YearMonth.from(cur) == month) {
                        val wk = weekKey(cur)
                        if (!cur.isBefore(created) && wk <= nowWeekKey) {
                            possible++
                            if (doneKeys.contains(wk)) done++
                        }
                    }
                    cur = cur.plusWeeks(1)
                }
            }
        }
        MonthStat(MONTH_SHORT_NAMES[month.monthValue - 1], if (possible > 0) Math.round(done * 100.0 / possible).toInt() else 0)
    }
}
```

- [ ] **Step 9: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --offline
```
Erwartet: PASS, alle Testklassen.

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/util/StatsCalculations.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/util/DateUtils.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/util/StatsCalculationsTest.kt
git commit -m "feat: reine Berechnungsfunktionen für Statistik/Dashboard (StatsCalculations)"
```

---

### Task 3: `StatsViewModel` (Tabelle, KW-Streifen, Legende)

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModel.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitRepository.observeHabitsWithDone()`, `successRate`,
  `maxStreakEver` aus Task 2, `monthWeeks`/`weekKey`/`dateKeyOf`/
  `isoWeekNumber`/`mondayOf`/`monthDayCount` aus `DateUtils.kt`.
- Produces (für Task 4 und Task 5):
  ```kotlin
  enum class DayState { DONE, MISS, OFF }
  data class StatsTableColumn(val name: String, val color: String)
  data class StatsTableRow(val day: Int, val isToday: Boolean, val states: List<DayState>)
  data class StatsTable(val columns: List<StatsTableColumn>, val rows: List<StatsTableRow>)
  data class WeeklyStripCell(val isoWeekNumber: Int, val done: Boolean, val active: Boolean)
  data class WeeklyStripRow(val name: String, val color: String, val cells: List<WeeklyStripCell>)
  data class StatsLegendEntry(val name: String, val color: String, val streak: Int, val successRatePct: Int)
  ```
  `StatsViewModel`: `viewMonth: StateFlow<YearMonth>`, `prevMonth()`,
  `nextMonth()`, `freq: StateFlow<String>` ("daily"|"weekly"),
  `selectFreq(freq: String)`, `mode: StateFlow<String>`
  ("table"|"circle"), `selectMode(mode: String)`, `table:
  StateFlow<StatsTable>`, `weeklyStrips: StateFlow<List<WeeklyStripRow>>`,
  `legend: StateFlow<List<StatsLegendEntry>>`, `hasDailyHabits:
  StateFlow<Boolean>`, `hasWeeklyHabits: StateFlow<Boolean>`.
  Kreis-Ansicht-Daten (`RingChartData`) sind **nicht** Teil dieses Tasks —
  folgen in Task 5.

- [ ] **Step 1: Fehlschlagenden Test für `table` schreiben**

Neue Datei `StatsViewModelTest.kt`:

```kotlin
package com.tatoli.habittracker.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
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
class StatsViewModelTest {

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

    private fun createdAtOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun table_hasOneColumnPerDailyHabitAndOneRowPerDayOfMonth() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(YearMonth.now().atDay(1)))
        )
        val viewModel = StatsViewModel(repository)
        val table = viewModel.table.first { it.columns.isNotEmpty() }
        assertEquals(listOf("Lesen"), table.columns.map { it.name })
        assertEquals(YearMonth.now().lengthOfMonth(), table.rows.size)
    }

    @Test
    fun table_excludesWeeklyHabits() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(YearMonth.now().atDay(1)))
        )
        val viewModel = StatsViewModel(repository)
        viewModel.hasWeeklyHabits.first { it }
        val table = viewModel.table.first()
        assertTrue(table.columns.isEmpty())
    }

    @Test
    fun legend_reflectsSuccessRateAndStreakForActiveFreq() = runBlocking {
        val today = LocalDate.now()
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(today))
        )
        val habitId = db.habitDao().observeHabitsWithDone().first { it.isNotEmpty() }.first().habit.id
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(habitId, today.toString()))
        val viewModel = StatsViewModel(repository)
        val legend = viewModel.legend.first { it.isNotEmpty() }
        assertEquals("Lesen", legend[0].name)
        assertEquals(100, legend[0].successRatePct)
        assertEquals(1, legend[0].streak)
    }

    @Test
    fun selectFreq_switchesLegendToWeeklyHabits() = runBlocking {
        val today = LocalDate.now()
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(today)))
        db.habitDao().insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(today)))
        val viewModel = StatsViewModel(repository)
        viewModel.legend.first { it.isNotEmpty() }

        viewModel.selectFreq("weekly")
        val legend = viewModel.legend.first { it.isNotEmpty() && it[0].name == "Sport" }
        assertEquals("Sport", legend[0].name)
    }

    @Test
    fun nextMonth_neverGoesPastCurrentMonth() = runBlocking {
        val viewModel = StatsViewModel(repository)
        val start = viewModel.viewMonth.first()
        viewModel.nextMonth()
        assertEquals(start, viewModel.viewMonth.first())
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.stats.StatsViewModelTest" --offline
```
Erwartet: FAIL — `StatsViewModel` existiert noch nicht (Compile-Fehler).

- [ ] **Step 3: `StatsViewModel` implementieren**

Neue Datei `StatsViewModel.kt`:

```kotlin
package com.tatoli.habittracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.util.dateKeyOf
import com.tatoli.habittracker.util.isoWeekNumber
import com.tatoli.habittracker.util.maxStreakEver
import com.tatoli.habittracker.util.mondayOf
import com.tatoli.habittracker.util.monthWeeks
import com.tatoli.habittracker.util.successRate
import com.tatoli.habittracker.util.weekKey
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

enum class DayState { DONE, MISS, OFF }
data class StatsTableColumn(val name: String, val color: String)
data class StatsTableRow(val day: Int, val isToday: Boolean, val states: List<DayState>)
data class StatsTable(val columns: List<StatsTableColumn>, val rows: List<StatsTableRow>)
data class WeeklyStripCell(val isoWeekNumber: Int, val done: Boolean, val active: Boolean)
data class WeeklyStripRow(val name: String, val color: String, val cells: List<WeeklyStripCell>)
data class StatsLegendEntry(val name: String, val color: String, val streak: Int, val successRatePct: Int)

class StatsViewModel(private val repository: HabitRepository) : ViewModel() {

    private val _viewMonth = MutableStateFlow(YearMonth.now())
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    private val _freq = MutableStateFlow("daily")
    val freq: StateFlow<String> = _freq.asStateFlow()

    private val _mode = MutableStateFlow("table")
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun prevMonth() { _viewMonth.value = _viewMonth.value.minusMonths(1) }
    fun nextMonth() {
        val next = _viewMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) _viewMonth.value = next
    }
    fun selectFreq(value: String) { _freq.value = value }
    fun selectMode(value: String) { _mode.value = value }

    private val habitsWithDone = repository.observeHabitsWithDone()

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasDailyHabits: StateFlow<Boolean> = habitsWithDone
        .map { list -> list.any { it.habit.freq == "daily" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasWeeklyHabits: StateFlow<Boolean> = habitsWithDone
        .map { list -> list.any { it.habit.freq == "weekly" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val table: StateFlow<StatsTable> = combine(habitsWithDone, _viewMonth) { list, month ->
        val daily = list.filter { it.habit.freq == "daily" }
        if (daily.isEmpty()) return@combine StatsTable(emptyList(), emptyList())
        val today = LocalDate.now()
        val columns = daily.map { StatsTableColumn(it.habit.name, it.habit.color) }
        val rows = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val states = daily.map { entry -> dayState(entry, date, today) }
            StatsTableRow(day, date == today, states)
        }
        StatsTable(columns, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsTable(emptyList(), emptyList()))

    val weeklyStrips: StateFlow<List<WeeklyStripRow>> = combine(habitsWithDone, _viewMonth) { list, month ->
        val weekly = list.filter { it.habit.freq == "weekly" }
        val today = LocalDate.now()
        val nowWeekKey = weekKey(today)
        weekly.map { entry ->
            val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
            val startWeekKey = weekKey(mondayOf(com.tatoli.habittracker.util.createdDate(entry.habit.createdAt)))
            val cells = monthWeeks(month).map { monday ->
                val wk = weekKey(monday)
                WeeklyStripCell(
                    isoWeekNumber = isoWeekNumber(monday),
                    done = doneKeys.contains(wk),
                    active = wk >= startWeekKey && wk <= nowWeekKey
                )
            }
            WeeklyStripRow(entry.habit.name, entry.habit.color, cells)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val legend: StateFlow<List<StatsLegendEntry>> = combine(habitsWithDone, _freq) { list, freqValue ->
        val today = LocalDate.now()
        list.filter { it.habit.freq == freqValue }.map { entry ->
            StatsLegendEntry(
                name = entry.habit.name,
                color = entry.habit.color,
                streak = maxStreakEver(entry),
                successRatePct = successRate(entry, today)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun dayState(entry: HabitWithDoneEntities, date: LocalDate, today: LocalDate): DayState {
        val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
        val ds = dateKeyOf(date)
        if (doneKeys.contains(ds)) return DayState.DONE
        val created = com.tatoli.habittracker.util.createdDate(entry.habit.createdAt)
        if (date.isAfter(today) || date.isBefore(created)) return DayState.OFF
        return DayState.MISS
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.stats.StatsViewModelTest" --offline
```
Erwartet: PASS (alle 5 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModel.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModelTest.kt
git commit -m "feat: StatsViewModel (Tabelle, KW-Streifen, Legende)"
```

---

### Task 4: `StatsScreen` — Tabellen-Ansicht, KW-Streifen, Legende, Navigation

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsScreen.kt`

**Interfaces:**
- Consumes: alles aus Task 3 (`StatsViewModel` + seine `StateFlow`s/Datentypen).
- Produces: `@Composable fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit)`.
  Die Kreis-Ansicht wird in Task 5 ergänzt (Platzhalter-Verzweigung hier
  reicht noch nicht aus — Task 5 fügt den echten Zweig ein, siehe dort).

Kein separater Unit-Test für dieses Composable (UI-Rendering ist in dieser
Sandbox nicht automatisiert prüfbar — wie bei `HabitListScreen`/
`HabitEditSheet` in Plan A-C). Verifikation über `assembleDebug` (kompiliert
erfolgreich) plus manuellen Gerätetest am Ende von Plan D.

- [ ] **Step 1: `StatsScreen.kt` implementieren**

```kotlin
package com.tatoli.habittracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.YearMonth

private val MONTH_NAMES = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
)

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val viewMonth by viewModel.viewMonth.collectAsState()
    val freq by viewModel.freq.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val table by viewModel.table.collectAsState()
    val weeklyStrips by viewModel.weeklyStrips.collectAsState()
    val legend by viewModel.legend.collectAsState()
    val hasDailyHabits by viewModel.hasDailyHabits.collectAsState()
    val hasWeeklyHabits by viewModel.hasWeeklyHabits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ToggleRow(
                options = listOf("daily" to "Täglich", "weekly" to "Wöchentlich"),
                selected = freq,
                onSelect = viewModel::selectFreq
            )
            Spacer(Modifier.size(8.dp))
            ToggleRow(
                options = listOf("table" to "Tabelle", "circle" to "Kreis"),
                selected = mode,
                onSelect = viewModel::selectMode
            )
            MonthNavRow(viewMonth, onPrev = viewModel::prevMonth, onNext = viewModel::nextMonth)

            when {
                freq == "daily" && !hasDailyHabits ->
                    EmptyMessage("Keine täglichen Gewohnheiten angelegt.")
                freq == "daily" && mode == "table" -> {
                    StatsTableView(table)
                    SectionTitle("Erfolgsquote (gesamt)")
                    LegendView(legend)
                }
                freq == "daily" && mode == "circle" -> {
                    // Kreis-Ansicht: siehe Task 5 (RingStatsChart)
                }
                freq == "weekly" && !hasWeeklyHabits ->
                    EmptyMessage("Keine wöchentlichen Gewohnheiten angelegt.")
                freq == "weekly" && mode == "table" -> {
                    weeklyStrips.forEach { row -> WeeklyStripView(row) }
                    SectionTitle("Erfolgsquote (gesamt)")
                    LegendView(legend)
                }
                freq == "weekly" && mode == "circle" -> {
                    // Kreis-Ansicht (KW-Sektoren): siehe Task 5
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun MonthNavRow(viewMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
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
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StatsTableView(table: StatsTable) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Column {
            Text("Tag", style = MaterialTheme.typography.labelSmall, modifier = Modifier.size(38.dp, 24.dp))
            table.rows.forEach { row ->
                Text(
                    text = row.day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.size(38.dp, 28.dp).padding(top = 4.dp)
                )
            }
        }
        table.columns.forEachIndexed { colIndex, column ->
            Column {
                Text(
                    text = column.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = parseHexColor(column.color),
                    modifier = Modifier.width(60.dp).padding(horizontal = 4.dp)
                )
                table.rows.forEach { row ->
                    val state = row.states[colIndex]
                    Box(
                        modifier = Modifier.size(60.dp, 28.dp).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            DayState.DONE -> Box(
                                Modifier.size(14.dp).background(parseHexColor(column.color), CircleShape)
                            )
                            DayState.MISS -> Box(
                                Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                            )
                            DayState.OFF -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyStripView(row: WeeklyStripRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text = row.name, style = MaterialTheme.typography.titleSmall, color = parseHexColor(row.color))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            row.cells.forEach { cell ->
                Box(
                    modifier = Modifier.weight(1f).size(32.dp).padding(2.dp)
                        .background(
                            if (cell.done) parseHexColor(row.color) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "KW${cell.isoWeekNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendView(entries: List<StatsLegendEntry>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).background(parseHexColor(entry.color), CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(text = entry.name, modifier = Modifier.weight(1f))
                Text(text = "🔥 ${entry.streak}", modifier = Modifier.padding(end = 8.dp))
                Text(text = "${entry.successRatePct}%")
            }
        }
    }
}

private fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
```

**Hinweis für Task 5:** Die beiden Kommentar-Zweige
`// Kreis-Ansicht: ...` in `when { ... }` werden in Task 5 durch echte
`RingStatsChart(...)`-Aufrufe ersetzt — Task 5 modifiziert diese Datei.

- [ ] **Step 2: Kompilierung prüfen**

```bash
./gradlew compileDebugKotlin --offline
```
Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsScreen.kt
git commit -m "feat: StatsScreen (Tabelle, KW-Streifen, Legende, Navigation)"
```

---

### Task 5: Ringdiagramm (`RingStatsChart`) — Tages- und Wochen-Kreis-Ansicht

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/RingStatsChart.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsScreen.kt`

**Interfaces:**
- Consumes: `StatsTable`-artige Grunddaten aus Task 3 (wird um eigene
  `RingChartData`-Ableitung im `StatsViewModel` erweitert).
- Produces: `data class RingChartData(val ringNames: List<String>,
  val ringColors: List<String>, val sectorLabels: List<String>,
  val states: List<List<DayState>>, val highlightSectorIndex: Int)`
  (`states[ringIndex][sectorIndex]`), `StatsViewModel.dailyRingChart:
  StateFlow<RingChartData>`, `StatsViewModel.weeklyRingChart:
  StateFlow<RingChartData>`, `@Composable fun RingStatsChart(data:
  RingChartData, modifier: Modifier = Modifier)`.

Dieser Task ist der aufwendigste UI-Teil des gesamten nativen Projekts
(siehe Design-Spec, Abschnitt „Risiken“). Die Geometrie ist 1:1 aus
`habit-tracker/index.html` (`circleSVG`, `polar`, `statsCircleHTML`,
`statsWeekCircleHTML`) übertragen — bei Unklarheiten dort nachschlagen.
Vor Gebrauch von `drawArc`/`nativeCanvas.drawTextOnPath` die tatsächliche
API-Signatur der gepinnten Compose-Version verifizieren (siehe Global
Constraints) — dieser Plan-Code ist die Zielrichtung, keine garantiert
kompilierbare Vorlage für exakt diese Version.

- [ ] **Step 1: `RingChartData`-Ableitung im `StatsViewModel` ergänzen**

An `StatsViewModel.kt` anfügen (`data class RingChartData` neben den
anderen `data class`-Deklarationen am Dateikopf):

```kotlin
data class RingChartData(
    val ringNames: List<String>,
    val ringColors: List<String>,
    val sectorLabels: List<String>,
    val states: List<List<DayState>>, // states[ringIndex][sectorIndex]
    val highlightSectorIndex: Int
)
```

Im `StatsViewModel`-Klassenkörper ergänzen (nach `weeklyStrips`):

```kotlin
    val dailyRingChart: StateFlow<RingChartData> = combine(habitsWithDone, _viewMonth) { list, month ->
        val daily = list.filter { it.habit.freq == "daily" }
        val today = LocalDate.now()
        val highlight = if (YearMonth.from(today) == month) today.dayOfMonth - 1 else -1
        RingChartData(
            ringNames = daily.map { it.habit.name },
            ringColors = daily.map { it.habit.color },
            sectorLabels = (1..month.lengthOfMonth()).map { it.toString() },
            states = daily.map { entry ->
                (1..month.lengthOfMonth()).map { day -> dayState(entry, month.atDay(day), today) }
            },
            highlightSectorIndex = highlight
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RingChartData(emptyList(), emptyList(), emptyList(), emptyList(), -1))

    val weeklyRingChart: StateFlow<RingChartData> = combine(habitsWithDone, _viewMonth) { list, month ->
        val weekly = list.filter { it.habit.freq == "weekly" }
        val today = LocalDate.now()
        val nowWeekKey = weekKey(today)
        val mondays = monthWeeks(month)
        var highlight = -1
        mondays.forEachIndexed { i, monday -> if (weekKey(monday) == nowWeekKey) highlight = i }
        RingChartData(
            ringNames = weekly.map { it.habit.name },
            ringColors = weekly.map { it.habit.color },
            sectorLabels = mondays.map { isoWeekNumber(it).toString() },
            states = weekly.map { entry ->
                val doneKeys = entry.doneEntries.map { it.dateKey }.toSet()
                val startWeekKey = weekKey(mondayOf(com.tatoli.habittracker.util.createdDate(entry.habit.createdAt)))
                mondays.map { monday ->
                    val wk = weekKey(monday)
                    when {
                        doneKeys.contains(wk) -> DayState.DONE
                        wk < startWeekKey || wk > nowWeekKey -> DayState.OFF
                        else -> DayState.MISS
                    }
                }
            },
            highlightSectorIndex = highlight
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RingChartData(emptyList(), emptyList(), emptyList(), emptyList(), -1))
```

- [ ] **Step 2: Fehlschlagenden Test für die Ring-Daten schreiben**

An `StatsViewModelTest.kt` anfügen:

```kotlin
    @Test
    fun dailyRingChart_hasOneRingPerDailyHabitAndOneSectorPerDay() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = createdAtOf(YearMonth.now().atDay(1)))
        )
        val viewModel = StatsViewModel(repository)
        val chart = viewModel.dailyRingChart.first { it.ringNames.isNotEmpty() }
        assertEquals(listOf("Lesen"), chart.ringNames)
        assertEquals(YearMonth.now().lengthOfMonth(), chart.sectorLabels.size)
        assertEquals(chart.sectorLabels.size, chart.states[0].size)
    }

    @Test
    fun weeklyRingChart_highlightsCurrentWeekWhenViewingCurrentMonth() = runBlocking {
        db.habitDao().insertHabit(
            HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(LocalDate.now()))
        )
        val viewModel = StatsViewModel(repository)
        val chart = viewModel.weeklyRingChart.first { it.ringNames.isNotEmpty() }
        assertTrue(chart.highlightSectorIndex >= 0)
    }
```

- [ ] **Step 3: Test laufen lassen, Erfolg bestätigen (Implementierung
  bereits in Step 1 erfolgt)**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.stats.StatsViewModelTest" --offline
```
Erwartet: PASS (alle Tests inkl. der 2 neuen).

- [ ] **Step 4: `RingStatsChart`-Composable implementieren**

Neue Datei `RingStatsChart.kt`:

```kotlin
package com.tatoli.habittracker.ui.stats

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

private const val VIEWPORT_W = 360f
private const val VIEWPORT_H = 336f
private const val CX = 206f
private const val CY = 186f
private const val OUTER = 122f
private const val INNER = 46f
private const val SWEEP_DEG = 290f
private const val TAIL_LEFT = CX - 150f
private const val MISS_ALPHA = 0.35f

@Composable
fun RingStatsChart(data: RingChartData, modifier: Modifier = Modifier) {
    val ringCount = data.ringNames.size
    val sectorCount = data.sectorLabels.size
    if (ringCount == 0 || sectorCount == 0) return
    val ringWidth = (OUTER - INNER) / ringCount
    val perSector = SWEEP_DEG / sectorCount

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(VIEWPORT_W / VIEWPORT_H)) {
        val scale = size.width / VIEWPORT_W
        val cx = CX * scale
        val cy = CY * scale

        for (ringIndex in 0 until ringCount) {
            val midRadius = (INNER + (ringIndex + 0.5f) * ringWidth) * scale
            val strokeWidth = (ringWidth - 1.6f) * scale
            val ringColor = parseHexColor(data.ringColors[ringIndex])
            for (sectorIndex in 0 until sectorCount) {
                val a0 = sectorIndex * perSector
                val a1 = (sectorIndex + 1) * perSector
                val startDeg = if (sectorIndex == 0) a0 else a0 + 0.6f
                val sweepDeg = (a1 - 0.6f) - startDeg
                val state = data.states[ringIndex][sectorIndex]
                val fillColor = if (state == DayState.DONE) ringColor else Color.White.copy(alpha = 0.06f)
                val alpha = if (state == DayState.OFF) MISS_ALPHA else 1f
                drawArc(
                    color = fillColor,
                    startAngle = startDeg - 90f,
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = Offset(cx - midRadius, cy - midRadius),
                    size = Size(midRadius * 2, midRadius * 2),
                    style = Stroke(width = strokeWidth),
                    alpha = alpha
                )
            }
        }

        val midAngle = SWEEP_DEG / 2
        val lowerHalf = midAngle > 90 && midAngle < 270
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val namePaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            setShadowLayer(2.6f * scale, 0f, 0f, android.graphics.Color.argb(200, 10, 28, 30))
        }
        val tailNamePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#EDF4F0")
            textSize = 11f * scale
            isFakeBoldText = true
        }
        val outerLabelPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.parseColor("#EDF4F0")
            textSize = 10f * scale
        }

        for (ringIndex in 0 until ringCount) {
            val yTop = cy - (INNER + (ringIndex + 1) * ringWidth - 0.8f) * scale
            val yBot = cy - (INNER + ringIndex * ringWidth + 0.8f) * scale
            val midY = (yTop + yBot) / 2
            val height = yBot - yTop
            val ringColor = parseHexColor(data.ringColors[ringIndex])

            drawRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset(TAIL_LEFT * scale, yTop),
                size = Size((cx - TAIL_LEFT * scale) - TAIL_LEFT * scale, height)
            )
            drawRect(
                color = ringColor,
                topLeft = Offset(TAIL_LEFT * scale, yTop),
                size = Size(5f * scale, height)
            )
            nativeCanvas.drawText(
                truncateLabel(data.ringNames[ringIndex], 20),
                (TAIL_LEFT + 12f) * scale,
                midY + 3.6f * scale,
                tailNamePaint
            )

            val mid = (INNER + (ringIndex + 0.5f) * ringWidth) * scale
            val span = 70f
            val aA = midAngle - span / 2
            val aB = midAngle + span / 2
            val startA = if (lowerHalf) aB else aA
            val endA = if (lowerHalf) aA else aB
            val path = Path()
            val sweepDirection = if (lowerHalf) -1f else 1f
            path.addArc(
                cx - mid, cy - mid, cx + mid, cy + mid,
                startA - 90f, (endA - startA) * sweepDirection.let { if (lowerHalf) -1f else 1f }
            )
            namePaint.textSize = (ringWidth * 0.6f).coerceIn(9f, 12f) * scale
            nativeCanvas.drawTextOnPath(truncateLabel(data.ringNames[ringIndex], 20), path, 0f, 0f, namePaint)
        }

        data.sectorLabels.forEachIndexed { sectorIndex, label ->
            val a0 = sectorIndex * perSector
            val a1 = (sectorIndex + 1) * perSector
            val mid = (a0 + a1) / 2
            val point = polar(cx, cy, (OUTER + 9f) * scale, mid)
            outerLabelPaint.color = if (sectorIndex == data.highlightSectorIndex) {
                android.graphics.Color.parseColor("#F2B450")
            } else {
                android.graphics.Color.parseColor("#EDF4F0")
            }
            nativeCanvas.drawText(label, point.x, point.y + 3f * scale, outerLabelPaint)
        }
    }
}

private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
    val angleRad = Math.toRadians((deg - 90).toDouble())
    return Offset(cx + r * cos(angleRad).toFloat(), cy + r * sin(angleRad).toFloat())
}

private fun truncateLabel(s: String, n: Int): String = if (s.length > n) s.take(n - 1) + "…" else s

private fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
```

**Bekannte Unsicherheit (bewusst, siehe Global Constraints):** Der
`Path.addArc(left, top, right, bottom, startAngle, sweepAngle)`-Aufruf für
das gekrümmte Namens-Label nutzt `android.graphics.Path` (nicht
`androidx.compose.ui.graphics.Path`) — falls die Bogenrichtung
(`lowerHalf`) beim ersten manuellen Test falsch aussieht (Text kopfüber im
unteren Halbkreis), das Vorzeichen von `sweepDirection` bzw. der
`startA`/`endA`-Tausch anpassen; das ist ein reiner Geometrie-
Feinschliff, kein struktureller Fehler.

- [ ] **Step 5: Kreis-Zweige in `StatsScreen.kt` einsetzen**

In `StatsScreen.kt`: `import com.tatoli.habittracker.ui.stats.RingStatsChart`
ist nicht nötig (gleiches Package). Die beiden Platzhalter-Kommentare aus
Task 4 ersetzen:

```kotlin
                freq == "daily" && mode == "circle" -> {
                    val dailyRingChart by viewModel.dailyRingChart.collectAsState()
                    RingStatsChart(dailyRingChart, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
```
und
```kotlin
                freq == "weekly" && mode == "circle" -> {
                    val weeklyRingChart by viewModel.weeklyRingChart.collectAsState()
                    RingStatsChart(weeklyRingChart, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
```

(`collectAsState`-Aufrufe innerhalb der `when`-Zweige sind in Compose
zulässig, solange sie unconditional pro Rekomposition desselben Zweigs
aufgerufen werden — hier unproblematisch, da jeder Zweig bei jedem Aufruf
von `StatsScreen` entweder ausgeführt oder komplett übersprungen wird,
nicht bedingt innerhalb eines gemeinsamen Zweigs.)

- [ ] **Step 6: Kompilierung prüfen**

```bash
./gradlew compileDebugKotlin --offline
```
Erwartet: BUILD SUCCESSFUL. Bei `Unresolved reference`- oder
Typ-Fehlern zu `nativeCanvas`/`drawArc`/`Path.addArc`: tatsächliche
Signatur der gepinnten Compose-Version prüfen (siehe Global Constraints)
und anpassen — als dokumentierte Abweichung im Report festhalten, nicht
stillschweigend umgehen.

- [ ] **Step 7: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest assembleDebug --offline
```
Erwartet: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/RingStatsChart.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModel.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/stats/StatsScreen.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/stats/StatsViewModelTest.kt
git commit -m "feat: Ringdiagramm (RingStatsChart) für Tages- und Wochen-Statistik"
```

---

### Task 6: `DashboardViewModel`

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/dashboard/DashboardViewModel.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitRepository.observeHabitsWithDone()`, `successRate`,
  `weekdayPatternData`, `groupComparisonData`, `trendData` aus Task 2.
- Produces (für Task 7):
  `data class BarEntry(val label: String, val pct: Int, val color: String?)`,
  `DashboardViewModel`: `dashMode: StateFlow<String>` ("daily"|"weekly"|"all"),
  `selectDashMode(mode: String)`, `overview: StateFlow<List<BarEntry>>`,
  `weekdayPattern: StateFlow<List<BarEntry>>`, `groupComparison:
  StateFlow<List<BarEntry>>`, `trend: StateFlow<List<BarEntry>>`.

- [ ] **Step 1: Fehlschlagende Tests schreiben**

Neue Datei `DashboardViewModelTest.kt`:

```kotlin
package com.tatoli.habittracker.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

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

    private fun createdAtOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun overview_sortedDescendingBySuccessRate() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Niedrig", color = "#F2B450", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        db.habitDao().insertHabit(HabitEntity(name = "Hoch", color = "#4FC98A", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        val highId = db.habitDao().observeHabitsWithDone().first { it.size == 2 }.first { it.habit.name == "Hoch" }.habit.id
        db.habitDao().insertDone(com.tatoli.habittracker.data.HabitDoneEntity(highId, LocalDate.now().toString()))

        val viewModel = DashboardViewModel(repository)
        val overview = viewModel.overview.first { it.size == 2 }
        assertEquals("Hoch", overview[0].label)
    }

    @Test
    fun selectDashMode_weekly_filtersOutDailyHabitsFromOverview() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Täglich", color = "#F2B450", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        db.habitDao().insertHabit(HabitEntity(name = "Wöchentlich", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        viewModel.overview.first { it.size == 2 }

        viewModel.selectDashMode("weekly")
        val overview = viewModel.overview.first { it.size == 1 }
        assertEquals("Wöchentlich", overview[0].label)
    }

    @Test
    fun weekdayPattern_emptyWhenNoDailyHabitsSelected() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Wöchentlich", color = "#4FC98A", freq = "weekly", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        viewModel.overview.first { it.isNotEmpty() }
        val pattern = viewModel.weekdayPattern.first()
        assertTrue(pattern.isEmpty())
    }

    @Test
    fun groupComparison_reflectsGroupAverages() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", group = "Fitness", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        val groups = viewModel.groupComparison.first { it.isNotEmpty() }
        assertEquals("Fitness", groups[0].label)
    }

    @Test
    fun trend_returnsSixEntries() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "A", color = "#F2B450", freq = "daily", createdAt = createdAtOf(LocalDate.now())))
        val viewModel = DashboardViewModel(repository)
        val trend = viewModel.trend.first { it.isNotEmpty() }
        assertEquals(6, trend.size)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.dashboard.DashboardViewModelTest" --offline
```
Erwartet: FAIL — `DashboardViewModel` existiert noch nicht.

- [ ] **Step 3: `DashboardViewModel` implementieren**

Neue Datei `DashboardViewModel.kt`:

```kotlin
package com.tatoli.habittracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.groupComparisonData
import com.tatoli.habittracker.util.successRate
import com.tatoli.habittracker.util.trendData
import com.tatoli.habittracker.util.weekdayPatternData
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BarEntry(val label: String, val pct: Int, val color: String?)

class DashboardViewModel(private val repository: HabitRepository) : ViewModel() {

    private val _dashMode = MutableStateFlow("all")
    val dashMode: StateFlow<String> = _dashMode.asStateFlow()

    fun selectDashMode(value: String) { _dashMode.value = value }

    private val habitsWithDone = repository.observeHabitsWithDone()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selected = combine(habitsWithDone, _dashMode) { list, mode ->
        when (mode) {
            "daily" -> list.filter { it.habit.freq == "daily" }
            "weekly" -> list.filter { it.habit.freq == "weekly" }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overview: StateFlow<List<BarEntry>> = selected.map { list ->
        val today = LocalDate.now()
        list.map { entry -> BarEntry(entry.habit.name, successRate(entry, today), entry.habit.color) }
            .sortedByDescending { it.pct }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekdayPattern: StateFlow<List<BarEntry>> = selected.map { list ->
        if (list.none { it.habit.freq == "daily" }) emptyList()
        else weekdayPatternData(list, LocalDate.now()).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupComparison: StateFlow<List<BarEntry>> = selected.map { list ->
        groupComparisonData(list, LocalDate.now()).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trend: StateFlow<List<BarEntry>> = selected.map { list ->
        trendData(list, LocalDate.now()).map { BarEntry(it.label, it.pct, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

(`import kotlinx.coroutines.flow.map` am Dateikopf ergänzen.)

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.dashboard.DashboardViewModelTest" --offline
```
Erwartet: PASS (alle 5 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/dashboard/DashboardViewModel.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: DashboardViewModel (Gesamtübersicht, Wochentags-Muster, Gruppen-Vergleich, Trend)"
```

---

### Task 7: `DashboardScreen`

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: alles aus Task 6 (`DashboardViewModel` + `BarEntry`).
- Produces: `@Composable fun DashboardScreen(viewModel: DashboardViewModel, onBack: () -> Unit)`.

Kein separater Unit-Test (gleiche Begründung wie Task 4).

- [ ] **Step 1: `DashboardScreen.kt` implementieren**

```kotlin
package com.tatoli.habittracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val dashMode by viewModel.dashMode.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val weekdayPattern by viewModel.weekdayPattern.collectAsState()
    val groupComparison by viewModel.groupComparison.collectAsState()
    val trend by viewModel.trend.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("daily" to "Täglich", "weekly" to "Wöchentlich", "all" to "Alle").forEach { (value, label) ->
                    FilterChip(selected = dashMode == value, onClick = { viewModel.selectDashMode(value) }, label = { Text(label) })
                }
            }

            DashboardSection(title = "Gesamtübersicht", entries = overview, emptyText = "Keine Gewohnheiten angelegt.")
            DashboardSection(title = "Wochentags-Muster", entries = weekdayPattern, emptyText = "Nur bei täglichen Gewohnheiten verfügbar.")
            DashboardSection(
                title = "Gruppen-Vergleich",
                entries = if (groupComparison.size >= 2) groupComparison else emptyList(),
                emptyText = "Lege Gruppen an, um sie hier zu vergleichen."
            )
            TrendSection(trend)
        }
    }
}

@Composable
private fun DashboardSection(title: String, entries: List<BarEntry>, emptyText: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) {
            Text(text = emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.forEach { entry -> BarRow(entry) }
        }
    }
}

@Composable
private fun BarRow(entry: BarEntry) {
    val barColor = entry.color?.let { parseHexColor(it) } ?: MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = entry.label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier.weight(1f).height(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction = (entry.pct / 100f).coerceIn(0.02f, 1f))
                    .height(16.dp)
                    .background(barColor, RoundedCornerShape(8.dp))
            )
        }
        Text(text = "${entry.pct}%", modifier = Modifier.width(44.dp).padding(start = 8.dp))
    }
}

@Composable
private fun TrendSection(entries: List<BarEntry>) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "Verlaufs-Trend (6 Monate)", style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) return
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)
        ) {
            entries.forEach { entry ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "${entry.pct}%", style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height((entry.pct.coerceIn(2, 100) / 100f * 90).dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    Text(text = entry.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
```

- [ ] **Step 2: Kompilierung prüfen**

```bash
./gradlew compileDebugKotlin --offline
```
Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat: DashboardScreen (Balken-Ansichten + Trend)"
```

---

### Task 8: Navigation — FABs & Routing

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt`

**Interfaces:**
- Consumes: `StatsScreen`/`StatsViewModel` (Task 3-5),
  `DashboardScreen`/`DashboardViewModel` (Task 6-7).
- Produces: nichts für spätere Tasks (letzter Task des Plans).

- [ ] **Step 1: Zwei neue FABs in `HabitListScreen.kt`**

`HabitListScreen`-Signatur um zwei Parameter erweitern und den
`floatingActionButton`-Slot auf einen gestapelten `Column` umstellen:

```kotlin
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    val viewMonth by viewModel.viewMonth.collectAsState()
    val availableGroups by viewModel.availableGroups.collectAsState()
    val filterGroup by viewModel.filterGroup.collectAsState()
    val listDisplay by viewModel.listDisplay.collectAsState()

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onOpenDashboard) {
                    Icon(Icons.Default.Insights, contentDescription = "Dashboard")
                }
                FloatingActionButton(onClick = onOpenStats) {
                    Icon(Icons.Default.BarChart, contentDescription = "Statistik")
                }
                FloatingActionButton(onClick = onAddHabit) {
                    Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
                }
            }
        }
    ) { padding ->
```

(Rest der Funktion unverändert.) Neue Imports am Dateikopf ergänzen:
`androidx.compose.material.icons.filled.BarChart`,
`androidx.compose.material.icons.filled.Insights`.

- [ ] **Step 2: Kompilierung prüfen (schlägt fehl — `MainActivity.kt`
  ruft `HabitListScreen` noch mit der alten Signatur auf)**

```bash
./gradlew compileDebugKotlin --offline
```
Erwartet: FAIL — fehlende Argumente `onOpenStats`/`onOpenDashboard` im
bestehenden `HabitListScreen(...)`-Aufruf in `MainActivity.kt`.

- [ ] **Step 3: Screen-Routing in `MainActivity.kt` ergänzen**

`EditSheetState` bleibt unverändert (regelt weiterhin nur das
Anlegen/Bearbeiten-Sheet über der Liste). Neu: ein zweiter, unabhängiger
Sealed-State für den aktuell sichtbaren Vollbild-Screen:

```kotlin
private sealed interface AppScreen {
    data object List : AppScreen
    data object Stats : AppScreen
    data object Dashboard : AppScreen
}
```

In `HabitTrackerApp(...)` ergänzen (nach `var sheetState by remember { ... }`):

```kotlin
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.List) }
```

Den bestehenden Aufruf

```kotlin
    HabitListScreen(
        viewModel = listViewModel,
        onAddHabit = { sheetState = EditSheetState.AddNew },
        onEditHabit = { id -> sheetState = EditSheetState.EditExisting(id) }
    )
```

ersetzen durch eine `when`-Verzweigung über `currentScreen`:

```kotlin
    when (currentScreen) {
        is AppScreen.List -> {
            HabitListScreen(
                viewModel = listViewModel,
                onAddHabit = { sheetState = EditSheetState.AddNew },
                onEditHabit = { id -> sheetState = EditSheetState.EditExisting(id) },
                onOpenStats = { currentScreen = AppScreen.Stats },
                onOpenDashboard = { currentScreen = AppScreen.Dashboard }
            )
        }
        is AppScreen.Stats -> {
            val statsViewModel = remember(currentScreen) {
                com.tatoli.habittracker.ui.stats.StatsViewModel(repository)
            }
            com.tatoli.habittracker.ui.stats.StatsScreen(
                viewModel = statsViewModel,
                onBack = { currentScreen = AppScreen.List }
            )
        }
        is AppScreen.Dashboard -> {
            val dashboardViewModel = remember(currentScreen) {
                com.tatoli.habittracker.ui.dashboard.DashboardViewModel(repository)
            }
            com.tatoli.habittracker.ui.dashboard.DashboardScreen(
                viewModel = dashboardViewModel,
                onBack = { currentScreen = AppScreen.List }
            )
        }
    }
```

Das bestehende `when (val state = sheetState) { ... }`-Bearbeiten-Sheet-
Block bleibt unverändert direkt darunter stehen (das Sheet legt sich als
Overlay über den jeweils aktiven `currentScreen`, exakt wie bisher über
die Liste).

- [ ] **Step 4: Kompilierung und volle Testsuite prüfen**

```bash
./gradlew testDebugUnitTest assembleDebug --offline
```
Erwartet: BUILD SUCCESSFUL, alle Tests grün, `app-debug.apk` erzeugt.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt
git commit -m "feat: Navigation zu Statistik/Dashboard über neue FABs"
```

---

## Verifikation nach Abschluss

1. APK aus dem letzten `assembleDebug`-Lauf im Windows-Emulator
   installieren (`adb install -r app-debug.apk`, ohne vorheriges
   Deinstallieren — Migration v2→v3 soll dabei real durchlaufen und
   bestehende Testdaten sollen erhalten bleiben, gleiche Begründung wie
   in Plan C).
2. Mehrere Habits mit unterschiedlichen Rhythmen/Gruppen/Farben anlegen,
   einige Tage/Wochen zurückliegend abhaken (über das bestehende
   Nachtragen im Hauptscreen).
3. **Statistik-FAB antippen:**
   - Tabelle (täglich): Häkchen/Punkte stimmen mit den abgehakten Tagen
     überein, Legende zeigt plausible Erfolgsquoten/Serien.
   - Kreis (täglich): **erste echte Gerätedarstellung des
     Ringdiagramms** — als Erstes prüfen (bekanntes Risiko aus der
     Design-Spec). Lesbarkeit der gekrümmten Labels, keine
     abgeschnittenen/kopfüber stehenden Texte, Sektor-Färbung stimmt.
   - Wöchentlich umschalten: KW-Streifen bzw. KW-Kreis analog prüfen.
   - Monatsnavigation: vor/zurück, "vorwärts" im aktuellen Monat
     deaktiviert.
4. **Dashboard-FAB antippen:** Balken in allen vier Abschnitten korrekt
   sortiert/gefüllt, Umschalter Täglich/Wöchentlich/Alle filtert
   sichtbar, Platzhaltertexte erscheinen bei <2 Gruppen bzw. keinen
   täglichen Habits.
5. FAB-Stapel (Dashboard/Statistik/Hinzufügen) auf keinem der drei
   Screens überlappend oder abgeschnitten.
6. Zurück-Pfeile auf beiden neuen Screens führen zur Liste zurück, ohne
   Datenverlust im Hauptscreen (Filter/Monat der Liste bleiben erhalten).
