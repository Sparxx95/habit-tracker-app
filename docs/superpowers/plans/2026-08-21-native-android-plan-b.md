# Plan B: Zeitraster & Streaks für die native Android-App — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jede Habit-Karte bekommt ihr vollständiges Zeitraster (Monatsraster
für tägliche, KW-Streifen für wöchentliche Habits) mit Nachtragen
vergangener Tage/Wochen, gemeinsamer Monatsnavigation und
🔥-Streak-/Monatsbilanz-Anzeige — funktional an die Web-App angelehnt.

**Architecture:** Room bekommt eine `@Relation`-Query, die pro Habit die
komplette Done-Historie liefert (ersetzt die bisherige Tages-Flag-Query).
Streak-/Grid-Berechnung passiert als reine, testbare Kotlin-Funktionen
(`DateUtils.kt`), analog zur Web-App-Logik. `HabitListViewModel` kombiniert
Done-Historie + aktuell angezeigten Monat zu einem vollständigen
`HabitDisplayState` pro Habit (inkl. Tages-/Wochen-Zellen); die
Compose-UI rendert das nur noch.

**Tech Stack:** Kotlin, Jetpack Compose, Room, `java.time` (kein externes
Datums-Library), Robolectric (DAO-Test), reines JUnit4 (Datums-/Streak-Logik).

**Spec:** `docs/superpowers/specs/2026-08-21-native-android-plan-b-design.md`

## Global Constraints

- `applicationId`/Kotlin-`namespace`: `com.tatoli.habittracker`.
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- Farben (exakt, aus Plan A übernommen, hier nicht neu zu setzen):
  `bg = #0E2527`, `card = #16393B`, `ink = #EDF4F0`, `muted = #7FA39B`,
  `amber = #F2B450`, `amberDim = #8F6A2E`. Habit-Palette:
  `#F2B450, #4FC98A, #5FB4E5, #E5766B, #C08BE0, #E0C34F`.
- Zukünftige Tage/Wochen sind in der nativen App **nicht antippbar**
  (deaktiviert) — bewusste Abweichung von der Web-App, mit dem Nutzer
  abgestimmt.
- Layout: 1:1-Parität zur Web-App — eine Liste, Raster direkt in jeder
  Karte eingebettet, eine gemeinsame Monatsnavigation oben.
- **Bewusst NICHT Teil dieses Plans:** Gruppen-Feld/-Filter (Plan C),
  Statistik-Screen (Plan D), XML-Backup (Plan E), Login/Firebase (dauerhaft
  außerhalb des Scopes).
- Kein Play-Store-Release, kein signierter Build — nur Debug-APKs.
- **Lokale Verifikation:** Vor jedem Gradle-Aufruf:
  ```bash
  export JAVA_HOME="$HOME/.jdk17"
  export PATH="$JAVA_HOME/bin:$PATH"
  export ANDROID_HOME="$HOME/.android-sdk"
  export ANDROID_SDK_ROOT="$HOME/.android-sdk"
  ```
  Volle Compose-UI-Rendering-Tests (`createComposeRule` o. Ä.) sind in
  dieser Umgebung NICHT Teil des Scopes (kein Gerät/Emulator) — Verifikation
  für UI-Code ist "kompiliert erfolgreich" (`./gradlew assembleDebug`) +
  ViewModel-/DAO-/reine-Funktions-Unit-Tests (`./gradlew testDebugUnitTest`).

---

### Task 1: Zeitraster-/Streak-Helfer in DateUtils.kt

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/util/DateUtils.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/util/DateUtilsTest.kt` (neu)

**Interfaces:**
- Produces (für Task 3): `dateKeyOf(date: LocalDate): String`,
  `weekKey(date: LocalDate): String`, `isoWeekNumber(date: LocalDate): Int`,
  `mondayOf(date: LocalDate): LocalDate`,
  `monthWeeks(yearMonth: YearMonth): List<LocalDate>`,
  `monthDayCount(yearMonth: YearMonth): Int`,
  `firstDayOfWeekOffset(yearMonth: YearMonth): Int`,
  `streak(doneDateKeys: Set<String>, today: LocalDate = LocalDate.now()): Int`,
  `weekStreak(doneWeekKeys: Set<String>, today: LocalDate = LocalDate.now()): Int`.
  `todayKey()` bleibt bestehen (wird intern auf `dateKeyOf` umgestellt).

Diese Funktionen sind reine Kotlin-Logik ohne Android-Abhängigkeiten
(`java.time` ist ab API 26 nativ verfügbar — passt zu `minSdk = 26` — und
in JVM-Unit-Tests ohnehin über das Host-JDK verfügbar, unabhängig vom
Android-`minSdk`). Der Test braucht daher **kein** Robolectric.

- [ ] **Step 1: Write the failing test**

Ersetze den Inhalt von
`android/app/src/test/kotlin/com/tatoli/habittracker/util/DateUtilsTest.kt`
mit:

```kotlin
package com.tatoli.habittracker.util

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun weekKey_returnsIsoWeekFormat() {
        // 2026-08-09 ist ein Sonntag, ISO-Kalenderwoche 32
        assertEquals("2026-W32", weekKey(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun isoWeekNumber_matchesWeekKey() {
        assertEquals(32, isoWeekNumber(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun mondayOf_returnsPrecedingOrSameMonday() {
        assertEquals(LocalDate.of(2026, 8, 3), mondayOf(LocalDate.of(2026, 8, 9)))
        assertEquals(LocalDate.of(2026, 8, 3), mondayOf(LocalDate.of(2026, 8, 3)))
    }

    @Test
    fun monthWeeks_returnsMondaysTouchingTheMonth() {
        // August 2026 beginnt Samstag (2026-08-01), endet Montag (2026-08-31)
        val weeks = monthWeeks(YearMonth.of(2026, 8))
        assertEquals(LocalDate.of(2026, 7, 27), weeks.first())
        assertEquals(LocalDate.of(2026, 8, 31), weeks.last())
        assertEquals(6, weeks.size)
    }

    @Test
    fun monthDayCount_returnsDaysInMonth() {
        assertEquals(31, monthDayCount(YearMonth.of(2026, 8)))
        assertEquals(28, monthDayCount(YearMonth.of(2026, 2)))
    }

    @Test
    fun firstDayOfWeekOffset_mondayIsZero() {
        // 2026-08-01 ist ein Samstag -> Offset 5 (Mo=0..So=6)
        assertEquals(5, firstDayOfWeekOffset(YearMonth.of(2026, 8)))
    }

    @Test
    fun streak_countsConsecutiveDaysEndingToday_todayMayBeOpen() {
        val today = LocalDate.of(2026, 8, 9)
        val done = setOf("2026-08-08", "2026-08-07", "2026-08-06")
        assertEquals(3, streak(done, today))
    }

    @Test
    fun streak_breaksOnGap() {
        val today = LocalDate.of(2026, 8, 9)
        val done = setOf("2026-08-09", "2026-08-08", "2026-08-06")
        assertEquals(2, streak(done, today))
    }

    @Test
    fun weekStreak_currentWeekOpen_countsPastConsecutiveWeeks() {
        val today = LocalDate.of(2026, 8, 9) // KW 32, noch nicht abgehakt
        val done = setOf(weekKey(LocalDate.of(2026, 8, 1)), weekKey(LocalDate.of(2026, 7, 25)))
        assertEquals(2, weekStreak(done, today))
    }

    @Test
    fun weekStreak_currentWeekDone_includesItInCount() {
        val today = LocalDate.of(2026, 8, 9) // KW 32
        val done = setOf(
            weekKey(LocalDate.of(2026, 8, 9)),
            weekKey(LocalDate.of(2026, 8, 1))
        )
        assertEquals(2, weekStreak(done, today))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
export JAVA_HOME="$HOME/.jdk17"; export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"; export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.DateUtilsTest"
```
Expected: FAIL — `weekKey`, `isoWeekNumber`, `mondayOf`, `monthWeeks`,
`monthDayCount`, `firstDayOfWeekOffset`, `streak`, `weekStreak` unresolved.

- [ ] **Step 3: Implement the helpers**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/util/DateUtils.kt` mit:

```kotlin
package com.tatoli.habittracker.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun dateKeyOf(date: LocalDate): String = date.format(DATE_FORMAT)

fun todayKey(): String = dateKeyOf(LocalDate.now())

fun isoWeekNumber(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

fun weekKey(date: LocalDate): String {
    val year = date.get(WeekFields.ISO.weekBasedYear())
    return "%d-W%02d".format(year, isoWeekNumber(date))
}

fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

fun monthWeeks(yearMonth: YearMonth): List<LocalDate> {
    val lastOfMonth = yearMonth.atEndOfMonth()
    val weeks = mutableListOf<LocalDate>()
    var cur = mondayOf(yearMonth.atDay(1))
    while (!cur.isAfter(lastOfMonth)) {
        weeks.add(cur)
        cur = cur.plusWeeks(1)
    }
    return weeks
}

fun monthDayCount(yearMonth: YearMonth): Int = yearMonth.lengthOfMonth()

fun firstDayOfWeekOffset(yearMonth: YearMonth): Int = yearMonth.atDay(1).dayOfWeek.value - 1

fun streak(doneDateKeys: Set<String>, today: LocalDate = LocalDate.now()): Int {
    var count = 0
    var cur = today
    if (!doneDateKeys.contains(dateKeyOf(cur))) cur = cur.minusDays(1)
    while (doneDateKeys.contains(dateKeyOf(cur))) {
        count++
        cur = cur.minusDays(1)
    }
    return count
}

fun weekStreak(doneWeekKeys: Set<String>, today: LocalDate = LocalDate.now()): Int {
    var count = 0
    var cur = mondayOf(today)
    if (!doneWeekKeys.contains(weekKey(cur))) cur = cur.minusWeeks(1)
    while (doneWeekKeys.contains(weekKey(cur))) {
        count++
        cur = cur.minusWeeks(1)
    }
    return count
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.DateUtilsTest"
```
Expected: PASS (10 Tests, 0 Failures).

- [ ] **Step 5: Verify the rest of the project still compiles**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL` — `todayKey()` wird an denselben Stellen wie
bisher verwendet (`HabitListViewModel.kt`), Signatur unverändert.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/util/DateUtils.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/util/DateUtilsTest.kt
git commit -m "feat: Zeitraster-/Streak-Helfer in DateUtils"
```

---

### Task 2: Room-Relation-Query für die volle Done-Historie

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt`

**Interfaces:**
- Consumes: `HabitEntity`, `HabitDoneEntity` (aus Plan A, unverändert).
- Produces (für Task 3): `HabitWithDoneEntities(habit: HabitEntity,
  doneEntries: List<HabitDoneEntity>)`,
  `HabitDao.observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>>`,
  `HabitRepository.observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>>`.

Diese Query wird **additiv** neben der bestehenden
`observeHabitsWithDoneFlag`/`HabitWithDoneFlag` eingeführt — die alten
bleiben in diesem Task unangetastet (werden erst in Task 3 entfernt, sobald
`HabitListViewModel` umgestellt ist). So bleibt das Projekt nach diesem Task
vollständig kompilierbar und grün, ohne Task 3 vorwegzunehmen.

- [ ] **Step 1: Write the failing test**

Füge in `android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt`
eine neue Testmethode direkt nach `observeHabitsWithDoneFlag_reflectsInsertAndToggle`
ein:

```kotlin
    @Test
    fun observeHabitsWithDone_includesFullDoneHistoryPerHabitAndReflectsToggle() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val id1 = dao.insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val id2 = dao.insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly"))
        dao.insertDone(HabitDoneEntity(habitId = id1, dateKey = "2026-08-08"))
        dao.insertDone(HabitDoneEntity(habitId = id1, dateKey = "2026-08-09"))
        dao.insertDone(HabitDoneEntity(habitId = id2, dateKey = "2026-W31"))

        var result = dao.observeHabitsWithDone().first()
        assertEquals(2, result.size)

        val lesen = result.first { it.habit.id == id1 }
        assertEquals(2, lesen.doneEntries.size)
        assertTrue(lesen.doneEntries.any { it.dateKey == "2026-08-08" })
        assertTrue(lesen.doneEntries.any { it.dateKey == "2026-08-09" })

        val sport = result.first { it.habit.id == id2 }
        assertEquals(1, sport.doneEntries.size)
        assertEquals("2026-W31", sport.doneEntries.first().dateKey)

        dao.deleteDone(id1, "2026-08-08")
        result = dao.observeHabitsWithDone().first()
        assertEquals(1, result.first { it.habit.id == id1 }.doneEntries.size)

        db.close()
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.HabitDaoTest"
```
Expected: FAIL — Compile-Fehler, `HabitWithDoneEntities`/`observeHabitsWithDone`
existieren noch nicht.

- [ ] **Step 3: Add the relation query**

In `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt`,
Imports ergänzen und die neue Datenklasse + Query direkt nach der
bestehenden `HabitWithDoneFlag`-Datenklasse (Zeile 17) einfügen, **ohne**
etwas Bestehendes zu entfernen:

```kotlin
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Transaction
```

```kotlin
data class HabitWithDoneEntities(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val doneEntries: List<HabitDoneEntity>
)
```

Und im `@Dao interface HabitDao` (nach `observeHabitsWithDoneFlag`):

```kotlin
    @Transaction
    @Query("SELECT * FROM habits ORDER BY id")
    fun observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>>
```

In `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`,
nach der bestehenden `observeHabitsWithDoneFlag`-Delegation ergänzen:

```kotlin
    fun observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>> = dao.observeHabitsWithDone()
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.HabitDaoTest"
```
Expected: PASS (3 Tests in dieser Klasse, 0 Failures).

- [ ] **Step 5: Full build check**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL` — alle bisherigen Tests bleiben grün, da nichts
Bestehendes entfernt wurde.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt
git commit -m "feat: Room-Relation-Query für volle Habit-Done-Historie"
```

---

### Task 3: HabitListViewModel-Umbau auf Monatsansicht + Streak/Zellen

Dies ist der Kern des Plans: die ViewModel-Schicht wird auf die neue
Relation-Query umgestellt, berechnet Streak/Monatsbilanz/Tages-/Wochen-Zellen
und ersetzt dabei die alte, jetzt überflüssige Tages-Flag-Query vollständig.
Da diese Umstellung zwangsläufig `HabitListScreen.kt` und den bestehenden
ViewModel-Test mitbetrifft (der Typ von `habits` ändert sich), gehören alle
drei Änderungen zu einem gemeinsamen, nur zusammen sinnvoll überprüfbaren
Task.

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt` (nur minimal: Typ-/Methodennamen-Anpassung, **kein** Grid-UI — das kommt in Task 4)
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt` (alte Flag-Query entfernen)
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt` (alte Delegation entfernen)
- Modify: `android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt` (obsoleten Test entfernen)
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt` (neu geschrieben)

**Interfaces:**
- Consumes: `HabitRepository.observeHabitsWithDone()`, `HabitWithDoneEntities`
  (Task 2), `dateKeyOf/weekKey/isoWeekNumber/mondayOf/monthWeeks/
  monthDayCount/firstDayOfWeekOffset/streak/weekStreak/todayKey` (Task 1).
- Produces (für Task 4 und Task 5): `DayCell(date: LocalDate, done: Boolean,
  isToday: Boolean, isFuture: Boolean)`, `WeekCell(monday: LocalDate,
  isoWeekNumber: Int, done: Boolean, isCurrentWeek: Boolean, isFuture:
  Boolean)`, `HabitDisplayState(id: Long, name: String, color: String, freq:
  String, doneToday: Boolean, streakCount: Int, monthTotal: Int, dayCells:
  List<DayCell>, weekCells: List<WeekCell>)`,
  `HabitListViewModel.habits: StateFlow<List<HabitDisplayState>>`,
  `HabitListViewModel.viewMonth: StateFlow<YearMonth>`,
  `HabitListViewModel.toggleToday(habit: HabitDisplayState)`,
  `HabitListViewModel.toggleCell(habitId: Long, dateKey: String, currentlyDone: Boolean)`,
  `HabitListViewModel.prevMonth()`, `HabitListViewModel.nextMonth()`,
  `HabitListViewModel.refreshDay()` (unverändert aus Plan A).

- [ ] **Step 1: Write the failing test**

Ersetze den kompletten Inhalt von
`android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt`
mit:

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.dateKeyOf
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
class HabitListViewModelTest {

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

    @Test
    fun toggleToday_marksTodayDoneAndUpdatesStreak() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)

        var current = viewModel.habits.first { it.isNotEmpty() }
        assertFalse(current.first().doneToday)
        assertEquals(0, current.first().streakCount)

        viewModel.toggleToday(current.first())
        current = viewModel.habits.first { it.isNotEmpty() && it.first().doneToday }
        assertTrue(current.first().doneToday)
        assertEquals(1, current.first().streakCount)
    }

    @Test
    fun toggleCell_togglesArbitraryPastDayAndUpdatesMonthTotal() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)
        // Erster Tag des aktuell angezeigten Monats: immer <= heute, unabhängig
        // davon, welcher Tag "heute" gerade ist (im Gegensatz zu "gestern", das am
        // 1. eines Monats in den Vormonat fiele und außerhalb der dayCells läge).
        val targetDate = YearMonth.now().atDay(1)
        val targetKey = dateKeyOf(targetDate)

        var current = viewModel.habits.first { it.isNotEmpty() }
        val habitId = current.first().id
        val totalBefore = current.first().monthTotal

        viewModel.toggleCell(habitId, targetKey, currentlyDone = false)
        current = viewModel.habits.first { it.isNotEmpty() && it.first().monthTotal == totalBefore + 1 }
        assertEquals(totalBefore + 1, current.first().monthTotal)
        assertTrue(current.first().dayCells.first { it.date == targetDate }.done)
    }

    @Test
    fun dailyHabit_dayCellsCoverCurrentMonthWithCorrectTodayFlag() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)
        val today = LocalDate.now()
        val expectedDays = YearMonth.now().lengthOfMonth()

        val current = viewModel.habits.first { it.isNotEmpty() }
        val cells = current.first().dayCells
        assertEquals(expectedDays, cells.size)
        assertTrue(cells.none { it.isFuture && it.date <= today })
        assertTrue(cells.first { it.date == today }.isToday)
    }

    @Test
    fun weeklyHabit_hasWeekCellsAndEmptyDayCells() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly"))
        val viewModel = HabitListViewModel(repository)

        val current = viewModel.habits.first { it.isNotEmpty() }
        val habit = current.first()
        assertTrue(habit.dayCells.isEmpty())
        assertTrue(habit.weekCells.isNotEmpty())
    }

    @Test
    fun prevMonth_thenNextMonth_returnsToCurrentMonth_nextMonthNeverGoesPastNow() = runBlocking {
        val viewModel = HabitListViewModel(repository)
        val startMonth = viewModel.viewMonth.first()

        viewModel.prevMonth()
        assertEquals(startMonth.minusMonths(1), viewModel.viewMonth.first())

        viewModel.nextMonth()
        assertEquals(startMonth, viewModel.viewMonth.first())

        viewModel.nextMonth()
        assertEquals(startMonth, viewModel.viewMonth.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.habitlist.HabitListViewModelTest"
```
Expected: FAIL — Compile-Fehler (`toggleToday`, `toggleCell`, `viewMonth`,
`prevMonth`, `nextMonth`, `HabitDisplayState`-Felder existieren noch nicht;
`habits` ist noch vom alten Typ).

- [ ] **Step 3: Rewrite HabitListViewModel.kt**

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
    val doneToday: Boolean,
    val streakCount: Int,
    val monthTotal: Int,
    val dayCells: List<DayCell>,
    val weekCells: List<WeekCell>
)

class HabitListViewModel(private val repository: HabitRepository) : ViewModel() {

    private val dayKey = MutableStateFlow(todayKey())
    private val _viewMonth = MutableStateFlow(YearMonth.now())
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val habits: StateFlow<List<HabitDisplayState>> = combine(
        repository.observeHabitsWithDone(),
        _viewMonth
    ) { habitsWithDone, month ->
        val today = LocalDate.now()
        habitsWithDone.map { toDisplayState(it, month, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleToday(habit: HabitDisplayState) {
        val key = if (habit.freq == "weekly") weekKey(LocalDate.now()) else dayKey.value
        toggleCell(habit.id, key, habit.doneToday)
    }

    fun toggleCell(habitId: Long, dateKey: String, currentlyDone: Boolean) {
        viewModelScope.launch {
            repository.toggleDone(habitId, dateKey, currentlyDone)
        }
    }

    fun refreshDay() {
        dayKey.value = todayKey()
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
            doneToday = doneKeys.contains(dateKeyOf(today)),
            streakCount = streak(doneKeys, today),
            monthTotal = cells.count { it.done },
            dayCells = cells,
            weekCells = emptyList()
        )
    }
}
```

`firstDayOfWeekOffset` (Task 1) wird hier bewusst nicht importiert — sie
wird erst in Task 4 für das UI-Rendering der Leerzellen vor Monatsbeginn
gebraucht, nicht für die Datenberechnung in diesem ViewModel.

- [ ] **Step 4: Remove the now-obsolete flag-based query**

In `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt`:
entferne die `HabitWithDoneFlag`-Datenklasse (Zeilen 11-17 im
Ausgangszustand) und die `observeHabitsWithDoneFlag`-Methode komplett.

In `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`:
entferne die `observeHabitsWithDoneFlag`-Delegationsmethode.

In `android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt`:
entferne die Testmethode `observeHabitsWithDoneFlag_reflectsInsertAndToggle`
komplett (ihre Abdeckung ist durch
`observeHabitsWithDone_includesFullDoneHistoryPerHabitAndReflectsToggle` aus
Task 2 bereits gegeben). Die zweite Testmethode
(`deleteHabit_cascadesToHabitDone`) referenziert `observeHabitsWithDoneFlag`
in ihrer letzten Zeile (`dao.observeHabitsWithDoneFlag("2026-08-09").first()`)
— ersetze diesen einen Aufruf durch `dao.observeHabitsWithDone().first()`
(die Assertion `assertTrue(afterDelete.isEmpty())` bleibt unverändert
gültig, da eine leere Habit-Liste in beiden Query-Formen leer bleibt).

- [ ] **Step 5: Minimal adjustment to HabitListScreen.kt (compile only, no grid yet)**

In `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`:
ersetze den Import `com.tatoli.habittracker.data.HabitWithDoneFlag` durch
keinen Import (der Typ kommt jetzt aus dem gleichen Package,
`HabitDisplayState` aus `HabitListViewModel.kt`). Ändere die
`HabitCard`-Funktionssignatur von `habit: HabitWithDoneFlag` zu
`habit: HabitDisplayState`, und den Aufruf `onToggle = { viewModel.toggleDone(habit) }`
in `HabitListScreen` zu `onToggle = { viewModel.toggleToday(habit) }`. Alles
andere in der Datei (Card-Layout, Toggle-Icon) bleibt unverändert — das
Grid kommt in Task 4.

- [ ] **Step 6: Run test to verify it passes**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.habitlist.HabitListViewModelTest"
```
Expected: PASS (5 Tests, 0 Failures).

- [ ] **Step 7: Full build check**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL` — inklusive `DateUtilsTest` (Task 1) und
`HabitDaoTest` (Task 2, jetzt ohne die entfernte Testmethode).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModel.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt
git commit -m "feat: HabitListViewModel auf Monatsansicht mit Streak/Zellen umgestellt"
```

---

### Task 4: Monatsraster-UI (Monatsnavigation, Tages-/KW-Grid)

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`

**Interfaces:**
- Consumes: `HabitListViewModel.viewMonth`, `.prevMonth()`, `.nextMonth()`,
  `.toggleCell(habitId, dateKey, currentlyDone)` (Task 3), `HabitDisplayState`,
  `DayCell`, `WeekCell` (Task 3), `dateKeyOf`, `firstDayOfWeekOffset` (Task 1).
- Produces: nichts weiter (letzter UI-Baustein dieses Plans, abgesehen von
  Task 5).

Reine UI-Ergänzung auf Basis der in Task 3 bereits korrekt berechneten
Daten — keine Logikänderung. Verifikation ist "kompiliert erfolgreich"
(kein Compose-Rendering-Test in dieser Umgebung, siehe Global Constraints).

- [ ] **Step 1: Replace HabitListScreen.kt with the full grid UI**

Ersetze den kompletten Inhalt von
`android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`
mit:

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val habits by viewModel.habits.collectAsState()
    val viewMonth by viewModel.viewMonth.collectAsState()

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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        onToggleToday = { viewModel.toggleToday(habit) },
                        onToggleCell = { dateKey, currentlyDone ->
                            viewModel.toggleCell(habit.id, dateKey, currentlyDone)
                        },
                        onClick = { onEditHabit(habit.id) }
                    )
                }
            }
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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
                        .background(parseHexColor(habit.color), CircleShape)
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
                        contentDescription = if (habit.doneToday) "Erledigt" else "Nicht erledigt",
                        tint = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (habit.freq == "weekly") {
                    "🔥 ${habit.streakCount} Wochen Serie · ${habit.monthTotal}/${habit.weekCells.size} diesen Monat"
                } else {
                    "🔥 ${habit.streakCount} Tage Serie · ${habit.monthTotal}/${habit.dayCells.size} diesen Monat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            if (habit.freq == "weekly") {
                WeeklyStrip(habit.weekCells, onToggleCell)
            } else {
                DailyGrid(habit.dayCells, onToggleCell)
            }
        }
    }
}

@Composable
private fun DailyGrid(cells: List<DayCell>, onToggleCell: (String, Boolean) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val firstOffset = cells.firstOrNull()?.let { firstDayOfWeekOffset(YearMonth.from(it.date)) } ?: 0
        val paddedCells: List<DayCell?> = List(firstOffset) { null } + cells
        paddedCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(modifier = Modifier.weight(1f).size(36.dp), contentAlignment = Alignment.Center) {
                        if (cell != null) {
                            DayCellButton(cell, onToggleCell)
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
private fun DayCellButton(cell: DayCell, onToggleCell: (String, Boolean) -> Unit) {
    TextButton(
        onClick = { onToggleCell(dateKeyOf(cell.date), cell.done) },
        enabled = !cell.isFuture,
        modifier = if (cell.isToday) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
        } else {
            Modifier
        },
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (cell.done) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text = cell.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WeeklyStrip(cells: List<WeekCell>, onToggleCell: (String, Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            TextButton(
                onClick = { onToggleCell(weekKey(cell.monday), cell.done) },
                enabled = !cell.isFuture,
                modifier = Modifier.weight(1f).let {
                    if (cell.isCurrentWeek) it.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else it
                },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (cell.done) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (cell.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(text = "KW ${cell.isoWeekNumber}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun parseHexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
```

- [ ] **Step 2: Compile check**

```bash
export JAVA_HOME="$HOME/.jdk17"; export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"; export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd android && ./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL`, alle bisherigen Tests weiterhin grün (kein
neuer Test in diesem Task — reine UI, siehe Global Constraints zum
UI-Test-Scope).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt
git commit -m "feat: Monatsnavigation + Tages-/KW-Raster pro Habit-Karte"
```

---

### Task 5: "Wöchentlich"-Rhythmus im Anlegen/Bearbeiten-Sheet reaktivieren

Jetzt, wo Wochen-Habits im Monatsraster vollständig unterstützt werden
(Task 3+4), kann die Rhythmus-Wahl im Sheet wieder beide Optionen anbieten —
in Plan A war "Wöchentlich" bewusst entfernt worden, weil damals nichts
danach fragte.

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt`

**Interfaces:**
- Consumes: `HabitEditViewModel.freq`, `.onFreqChange(value: String)`
  (unverändert aus Plan A, bereits generisch für beliebige Werte).

- [ ] **Step 1: Re-add the "Wöchentlich" chip**

In `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt`,
ersetze den Rhythmus-Abschnitt (aktuell nur "Täglich", Zeilen 72-82) mit:

```kotlin
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
```

Der `Modifier.width`-Import (aktuell entfernt, siehe Plan A) muss wieder
ergänzt werden: `import androidx.compose.foundation.layout.width` zu den
bestehenden Imports hinzufügen.

- [ ] **Step 2: Compile check**

```bash
export JAVA_HOME="$HOME/.jdk17"; export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"; export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd android && ./gradlew testDebugUnitTest assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt
git commit -m "feat: Wöchentlich-Rhythmus im Anlegen/Bearbeiten-Sheet reaktivieren"
```

---

## Verifikation nach Abschluss

- Push auf `main` in `Sparxx95/habit-tracker-app`.
- `android-build.yml` läuft automatisch (push-Trigger auf `android/**`, seit
  Plan A) — grünen Lauf auf einem echten `ubuntu-latest`-Runner abwarten.
- APK aus dem Actions-Artifact herunterladen, im Windows-Emulator
  installieren (alte Version vorher deinstallieren, siehe bekannter
  Signatur-Konflikt aus Plan A) und manuell testen: Monat wechseln
  (vor/zurück, nicht in die Zukunft), Tag/Woche in der Vergangenheit
  nachtragen, Streak-Anzeige nach mehreren Tagen prüfen, wöchentlichen
  Habit anlegen und KW-Streifen prüfen.
