# Plan E: Backup für die native Android-App — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Habits können als XML exportiert (Share-Intent) und wieder
importiert (Dateiauswahl, mit Ersetzen-Bestätigung) werden — identisches
Format zur Web-App, letzter Baustein für volle funktionale Parität.

**Architecture:** Eine reine Kotlin-Datei (`util/HabitXml.kt`, javax.xml/
org.w3c.dom statt Android-Framework-XML-Klassen, damit sie ohne
Robolectric testbar bleibt) baut/parst das XML. `HabitRepository` bekommt
eine transaktionale `replaceAllHabits(...)`-Methode. Ein neues
`BackupViewModel` bündelt Export-/Import-/Zuletzt-Backup-Logik (Zeitstempel
in `SharedPreferences`). Ein neues `BackupSheet`-Composable (ModalBottomSheet,
viertes FAB) übernimmt die Android-spezifische Interaktion (Share-Intent,
System-Dateiauswahl) und ruft nur fertige Strings ins/vom ViewModel.

**Tech Stack:** Kotlin, `javax.xml.parsers`/`org.w3c.dom` (JDK, kein
Robolectric nötig), Android `FileProvider` + `ActivityResultContracts`,
Jetpack Compose (`ModalBottomSheet`, `AlertDialog`), Room (`@Transaction`),
`SharedPreferences`, Robolectric (für DAO/Repository/ViewModel-Tests).

**Spec:** `docs/superpowers/specs/2026-08-30-native-android-plan-e-design.md`

## Global Constraints

- `applicationId`/Kotlin-`namespace`: `com.tatoli.habittracker`.
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- XML-Format **identisch** zur Web-App: `<habits exported="…" version="2">`
  mit `<habit id="…" name="…" color="…" group="…" freq="daily|weekly">`
  und `<day date="…"/>`-Kindern. `id`-Attribut = `createdAt` (Epoch-Millis)
  des Habits, nicht die Room-`id`.
- Import mappt das `id`-Attribut auf `createdAt` des neu angelegten Habits
  (Fallback: Importzeitpunkt bei fehlendem/ungültigem Wert) — Room vergibt
  trotzdem eine eigene neue `id`. Mit dem Nutzer abgestimmt.
- Import läuft in einer Datenbank-Transaktion — alte Daten bleiben bei
  einem Fehler mitten im Import vollständig erhalten (kein Halb-Import).
- Kein "Abmelden"-Button im Backup-Sheet (kein Login/Cloud-Sync in der
  nativen App — dauerhaft außerhalb des Scopes).
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
  vorheriger Aufruf per Timeout abgebrochen wurde. Build-/Testläufe immer
  im Hintergrund starten (`run_in_background`/`&`), nie im Vordergrund mit
  kurzem Timeout — sonst bleibt ein Gradle-Daemon verwaist zurück und
  blockiert Ressourcen für den nächsten Versuch. Mehrere Minuten ganz ohne
  neue Konsolenausgabe sind in dieser Sandbox normal (z. B. bei
  `kspDebugKotlin` mit kaltem Cache), kein Hänger — mit `top -bn1` prüfen
  (aktiver `java`-Prozess mit hoher CPU-Last = läuft wirklich).
- `android.util.Xml`/`XmlPullParser` (Android-Framework) werden bewusst
  **nicht** verwendet — stattdessen `javax.xml.parsers.DocumentBuilderFactory`
  + `org.w3c.dom` (Teil des JDK, auf Android verfügbar seit API 1), damit
  `HabitXmlTest` ohne Robolectric läuft.

---

### Task 1: `HabitXml.kt` — XML-Serialisierung (reine Funktionen)

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/util/HabitXml.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/util/HabitXmlTest.kt`

**Interfaces:**
- Consumes: `com.tatoli.habittracker.data.HabitWithDoneEntities` (bereits
  vorhanden), `com.tatoli.habittracker.ui.theme.HabitPalette` (bereits
  vorhanden, `List<String>` mit Hex-Farben).
- Produces (für Task 2/3):
  ```kotlin
  data class ParsedHabit(
      val name: String,
      val color: String,
      val group: String,
      val freq: String,
      val createdAt: Long,
      val doneKeys: List<String>
  )
  fun buildHabitsXml(habits: List<HabitWithDoneEntities>): String
  fun parseHabitsXml(xml: String, importedAt: Long): List<ParsedHabit>
  ```

- [ ] **Step 1: Fehlschlagende Tests für `buildHabitsXml` schreiben**

Neue Datei `HabitXmlTest.kt`:

```kotlin
package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitDoneEntity
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitWithDoneEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitXmlTest {

    private fun entry(
        name: String = "Lesen",
        color: String = "#F2B450",
        group: String = "",
        freq: String = "daily",
        createdAt: Long = 1_723_000_000_000L,
        doneKeys: List<String> = emptyList()
    ) = HabitWithDoneEntities(
        habit = HabitEntity(id = 1, name = name, color = color, freq = freq, group = group, createdAt = createdAt),
        doneEntries = doneKeys.map { HabitDoneEntity(habitId = 1, dateKey = it) }
    )

    @Test
    fun buildHabitsXml_containsRootElementWithVersionAndExportedAttribute() {
        val xml = buildHabitsXml(emptyList())
        assertTrue(xml.contains("<habits exported=\""))
        assertTrue(xml.contains("version=\"2\">"))
        assertTrue(xml.contains("</habits>"))
    }

    @Test
    fun buildHabitsXml_usesCreatedAtAsIdAttribute() {
        val xml = buildHabitsXml(listOf(entry(createdAt = 1_723_000_000_000L)))
        assertTrue(xml.contains("id=\"1723000000000\""))
    }

    @Test
    fun buildHabitsXml_escapesSpecialCharactersInNameAndGroup() {
        val xml = buildHabitsXml(listOf(entry(name = "A & B <C>", group = "\"Q\"")))
        assertTrue(xml.contains("name=\"A &amp; B &lt;C&gt;\""))
        assertTrue(xml.contains("group=\"&quot;Q&quot;\""))
    }

    @Test
    fun buildHabitsXml_sortsDayElementsByDateKey() {
        val xml = buildHabitsXml(listOf(entry(doneKeys = listOf("2026-08-10", "2026-08-01", "2026-08-05"))))
        val firstIndex = xml.indexOf("2026-08-01")
        val secondIndex = xml.indexOf("2026-08-05")
        val thirdIndex = xml.indexOf("2026-08-10")
        assertTrue(firstIndex in 0 until secondIndex)
        assertTrue(secondIndex in 0 until thirdIndex)
    }

    @Test
    fun buildHabitsXml_marksWeeklyFreqCorrectly() {
        val xml = buildHabitsXml(listOf(entry(freq = "weekly")))
        assertTrue(xml.contains("freq=\"weekly\""))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
cd android
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.HabitXmlTest" --offline
```
Erwartet: FAIL — `buildHabitsXml`/`ParsedHabit` existieren noch nicht
(Compile-Fehler).

- [ ] **Step 3: `buildHabitsXml` implementieren**

Neue Datei `HabitXml.kt`:

```kotlin
package com.tatoli.habittracker.util

import com.tatoli.habittracker.data.HabitWithDoneEntities
import com.tatoli.habittracker.ui.theme.HabitPalette
import java.io.StringReader
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class ParsedHabit(
    val name: String,
    val color: String,
    val group: String,
    val freq: String,
    val createdAt: Long,
    val doneKeys: List<String>
)

private fun escXml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

fun buildHabitsXml(habits: List<HabitWithDoneEntities>): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<habits exported=\"").append(escXml(Instant.now().toString())).append("\" version=\"2\">\n")
    habits.forEach { entry ->
        val h = entry.habit
        sb.append("  <habit id=\"").append(h.createdAt)
            .append("\" name=\"").append(escXml(h.name))
            .append("\" color=\"").append(escXml(h.color))
            .append("\" group=\"").append(escXml(h.group))
            .append("\" freq=\"").append(if (h.freq == "weekly") "weekly" else "daily")
            .append("\">\n")
        entry.doneEntries.map { it.dateKey }.sorted().forEach { key ->
            sb.append("    <day date=\"").append(escXml(key)).append("\"/>\n")
        }
        sb.append("  </habit>\n")
    }
    sb.append("</habits>\n")
    return sb.toString()
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.HabitXmlTest" --offline
```
Erwartet: PASS (alle 5 Tests).

- [ ] **Step 5: Fehlschlagende Tests für `parseHabitsXml` ergänzen**

An `HabitXmlTest.kt` anfügen (vor der letzten schließenden `}`):

```kotlin
    @Test
    fun parseHabitsXml_roundTripsBuildHabitsXmlOutput() {
        val original = listOf(
            entry(name = "Lesen", color = "#F2B450", group = "Bildung", freq = "daily", createdAt = 111L, doneKeys = listOf("2026-08-01", "2026-08-02")),
            entry(name = "Sport", color = "#4FC98A", group = "", freq = "weekly", createdAt = 222L, doneKeys = listOf("2026-W31"))
        )
        val xml = buildHabitsXml(original)
        val parsed = parseHabitsXml(xml, importedAt = 999L)

        assertEquals(2, parsed.size)
        assertEquals("Lesen", parsed[0].name)
        assertEquals("#F2B450", parsed[0].color)
        assertEquals("Bildung", parsed[0].group)
        assertEquals("daily", parsed[0].freq)
        assertEquals(111L, parsed[0].createdAt)
        assertEquals(listOf("2026-08-01", "2026-08-02"), parsed[0].doneKeys)
        assertEquals("weekly", parsed[1].freq)
        assertEquals(listOf("2026-W31"), parsed[1].doneKeys)
    }

    @Test
    fun parseHabitsXml_missingIdAttributeFallsBackToImportedAt() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit name="Ohne ID" color="#F2B450" group="" freq="daily"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 555L)
        assertEquals(555L, parsed[0].createdAt)
    }

    @Test
    fun parseHabitsXml_missingNameFallsBackToUnbenannt() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" color="#F2B450" group="" freq="daily"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals("Unbenannt", parsed[0].name)
    }

    @Test
    fun parseHabitsXml_missingColorFallsBackToFirstPaletteColor() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" name="X" group="" freq="daily"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals(HabitPalette.first(), parsed[0].color)
    }

    @Test
    fun parseHabitsXml_nonWeeklyFreqValueBecomesDaily() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" name="X" color="#F2B450" group="" freq="monthly"></habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals("daily", parsed[0].freq)
    }

    @Test
    fun parseHabitsXml_dropsDayElementsWithInvalidDateFormat() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="1" name="X" color="#F2B450" group="" freq="daily">
                <day date="2026-08-01"/>
                <day date="not-a-date"/>
                <day date="2026-W05"/>
              </habit>
            </habits>
        """.trimIndent()
        val parsed = parseHabitsXml(xml, importedAt = 1L)
        assertEquals(listOf("2026-08-01", "2026-W05"), parsed[0].doneKeys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseHabitsXml_invalidXmlThrows() {
        parseHabitsXml("not xml at all <<<", importedAt = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseHabitsXml_noHabitElementsThrows() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><habits exported="2026-08-30" version="2"></habits>"""
        parseHabitsXml(xml, importedAt = 1L)
    }
```

- [ ] **Step 6: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.util.HabitXmlTest" --offline
```
Erwartet: FAIL — `parseHabitsXml` existiert noch nicht.

- [ ] **Step 7: `parseHabitsXml` implementieren**

An `HabitXml.kt` anfügen:

```kotlin
private val DAY_KEY_PATTERN = Regex("^\\d{4}(-\\d{2}-\\d{2}|-W\\d{2})$")

fun parseHabitsXml(xml: String, importedAt: Long): List<ParsedHabit> {
    val document = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    } catch (e: Exception) {
        throw IllegalArgumentException("XML ungültig", e)
    }
    val habitNodes = document.getElementsByTagName("habit")
    if (habitNodes.length == 0) throw IllegalArgumentException("Keine Gewohnheiten in der Datei")

    return (0 until habitNodes.length).map { i ->
        val habitElement = habitNodes.item(i) as Element
        val name = habitElement.getAttribute("name").ifEmpty { "Unbenannt" }
        val color = habitElement.getAttribute("color").ifEmpty { HabitPalette.first() }
        val group = habitElement.getAttribute("group").trim()
        val freq = if (habitElement.getAttribute("freq") == "weekly") "weekly" else "daily"
        val createdAt = habitElement.getAttribute("id").toLongOrNull() ?: importedAt
        val dayNodes = habitElement.getElementsByTagName("day")
        val doneKeys = (0 until dayNodes.length).mapNotNull { j ->
            val date = (dayNodes.item(j) as Element).getAttribute("date")
            if (DAY_KEY_PATTERN.matches(date)) date else null
        }
        ParsedHabit(name = name, color = color, group = group, freq = freq, createdAt = createdAt, doneKeys = doneKeys)
    }
}
```

- [ ] **Step 8: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --offline
```
Erwartet: PASS, alle Testklassen (inkl. der 13 neuen `HabitXmlTest`-Fälle).

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/util/HabitXml.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/util/HabitXmlTest.kt
git commit -m "feat: XML-Serialisierung für Habit-Backup (HabitXml)"
```

---

### Task 2: `HabitRepository.replaceAllHabits` — transaktionaler Import

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt`

**Interfaces:**
- Consumes: `com.tatoli.habittracker.util.ParsedHabit` aus Task 1.
- Produces (für Task 3): `HabitRepository.replaceAllHabits(imported: List<ParsedHabit>)`
  (`suspend fun`, ersetzt alle Habits + deren `habit_done`-Einträge in
  einer Transaktion).

- [ ] **Step 1: Fehlschlagenden Test schreiben**

An `HabitDaoTest.kt` anfügen (vor der letzten schließenden `}`):

```kotlin
    @Test
    fun replaceAllHabits_deletesExistingDataAndInsertsImportedHabitsWithDoneEntries() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val oldId = dao.insertHabit(HabitEntity(name = "Alt", color = "#F2B450", freq = "daily"))
        dao.insertDone(HabitDoneEntity(habitId = oldId, dateKey = "2026-08-01"))

        val imports = listOf(
            HabitImport(
                entity = HabitEntity(name = "Neu1", color = "#4FC98A", freq = "daily", group = "G", createdAt = 111L),
                doneKeys = listOf("2026-08-05", "2026-08-06")
            ),
            HabitImport(
                entity = HabitEntity(name = "Neu2", color = "#5FB4E5", freq = "weekly", createdAt = 222L),
                doneKeys = listOf("2026-W31")
            )
        )
        dao.replaceAllHabits(imports)

        val result = dao.observeHabitsWithDone().first()
        assertEquals(2, result.size)
        assertTrue(result.none { it.habit.name == "Alt" })

        val neu1 = result.first { it.habit.name == "Neu1" }
        assertEquals("G", neu1.habit.group)
        assertEquals(111L, neu1.habit.createdAt)
        assertEquals(2, neu1.doneEntries.size)

        val neu2 = result.first { it.habit.name == "Neu2" }
        assertEquals(1, neu2.doneEntries.size)
        assertEquals("2026-W31", neu2.doneEntries.first().dateKey)

        db.close()
    }

    @Test
    fun replaceAllHabits_withEmptyList_deletesEverythingAndInsertsNothing() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()
        dao.insertHabit(HabitEntity(name = "Alt", color = "#F2B450", freq = "daily"))

        dao.replaceAllHabits(emptyList())

        assertTrue(dao.observeHabitsWithDone().first().isEmpty())
        db.close()
    }
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.HabitDaoTest" --offline
```
Erwartet: FAIL — `HabitImport`/`replaceAllHabits` existieren noch nicht.

- [ ] **Step 3: `HabitImport` + `replaceAllHabits` in `HabitDao.kt` ergänzen**

In `HabitDao.kt`, nach der bestehenden `data class HabitWithDoneEntities`:

```kotlin
data class HabitImport(
    val entity: HabitEntity,
    val doneKeys: List<String>
)
```

Im `HabitDao`-Interface ergänzen (nach `deleteDone`):

```kotlin
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
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.data.HabitDaoTest" --offline
```
Erwartet: PASS (alle 4 Tests, 2 bestehende + 2 neue).

- [ ] **Step 5: `HabitRepository.replaceAllHabits` ergänzen**

In `HabitRepository.kt`:

```kotlin
import com.tatoli.habittracker.util.ParsedHabit

class HabitRepository(private val dao: HabitDao) {

    // ... bestehende Methoden unverändert ...

    suspend fun replaceAllHabits(imported: List<ParsedHabit>) {
        val imports = imported.map { p ->
            HabitImport(
                entity = HabitEntity(name = p.name, color = p.color, freq = p.freq, group = p.group, createdAt = p.createdAt),
                doneKeys = p.doneKeys
            )
        }
        dao.replaceAllHabits(imports)
    }
}
```

- [ ] **Step 6: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --offline
```
Erwartet: PASS, alle Testklassen.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt
git commit -m "feat: transaktionaler Habit-Import (HabitDao/HabitRepository.replaceAllHabits)"
```

---

### Task 3: `BackupViewModel`

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/backup/BackupViewModel.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/ui/backup/BackupViewModelTest.kt`

**Interfaces:**
- Consumes: `buildHabitsXml`/`parseHabitsXml`/`ParsedHabit` aus Task 1,
  `HabitRepository.replaceAllHabits`/`observeHabitsWithDone` aus Task 2.
- Produces (für Task 4): `BackupViewModel(repository: HabitRepository,
  prefs: SharedPreferences)`, `lastBackupText: StateFlow<String>`,
  `suspend fun buildExportXml(): String`, `suspend fun hasAnyHabits(): Boolean`,
  `suspend fun importXml(xml: String): Int` (gibt Anzahl importierter
  Habits zurück, wirft `IllegalArgumentException` bei ungültigem XML),
  `fun markBackupDone()`.

- [ ] **Step 1: Fehlschlagende Tests schreiben**

Neue Datei `BackupViewModelTest.kt`:

```kotlin
package com.tatoli.habittracker.ui.backup

import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HabitRepository
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(db.habitDao())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("backup_meta_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        viewModel = BackupViewModel(repository, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun lastBackupText_noBackupYet_showsPlaceholder() {
        assertEquals("Datensicherung · noch kein Backup", viewModel.lastBackupText.value)
    }

    @Test
    fun markBackupDone_updatesLastBackupTextToToday() {
        viewModel.markBackupDone()
        assertEquals("Datensicherung · letztes Backup: heute", viewModel.lastBackupText.value)
    }

    @Test
    fun hasAnyHabits_reflectsRepositoryState() = runBlocking {
        assertFalse(viewModel.hasAnyHabits())
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        assertTrue(viewModel.hasAnyHabits())
    }

    @Test
    fun buildExportXml_containsInsertedHabit() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily", createdAt = 42L))
        val xml = viewModel.buildExportXml()
        assertTrue(xml.contains("name=\"Lesen\""))
        assertTrue(xml.contains("id=\"42\""))
    }

    @Test
    fun importXml_replacesExistingDataAndReturnsImportedCount() = runBlocking {
        db.habitDao().insertHabit(HabitEntity(name = "Alt", color = "#F2B450", freq = "daily"))
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <habits exported="2026-08-30" version="2">
              <habit id="99" name="Neu" color="#4FC98A" group="" freq="daily">
                <day date="2026-08-01"/>
              </habit>
            </habits>
        """.trimIndent()

        val count = viewModel.importXml(xml)

        assertEquals(1, count)
        val result = repository.observeHabitsWithDone().first()
        assertEquals(1, result.size)
        assertEquals("Neu", result.first().habit.name)
        assertEquals(99L, result.first().habit.createdAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importXml_invalidXmlThrowsAndDoesNotThrowSilently() = runBlocking {
        viewModel.importXml("not valid xml")
        Unit
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew testDebugUnitTest --tests "com.tatoli.habittracker.ui.backup.BackupViewModelTest" --offline
```
Erwartet: FAIL — `BackupViewModel` existiert noch nicht (Compile-Fehler).

- [ ] **Step 3: `BackupViewModel` implementieren**

Neue Datei `BackupViewModel.kt`:

```kotlin
package com.tatoli.habittracker.ui.backup

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.buildHabitsXml
import com.tatoli.habittracker.util.parseHabitsXml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private const val KEY_LAST_BACKUP_AT = "lastBackupAt"

class BackupViewModel(
    private val repository: HabitRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _lastBackupText = MutableStateFlow(computeLastBackupText())
    val lastBackupText: StateFlow<String> = _lastBackupText.asStateFlow()

    suspend fun buildExportXml(): String =
        buildHabitsXml(repository.observeHabitsWithDone().first())

    suspend fun hasAnyHabits(): Boolean =
        repository.observeHabitsWithDone().first().isNotEmpty()

    suspend fun importXml(xml: String): Int {
        val parsed = parseHabitsXml(xml, System.currentTimeMillis())
        repository.replaceAllHabits(parsed)
        return parsed.size
    }

    fun markBackupDone() {
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis()).apply()
        _lastBackupText.value = computeLastBackupText()
    }

    private fun computeLastBackupText(): String {
        val lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, -1L)
        if (lastBackupAt < 0) return "Datensicherung · noch kein Backup"
        val days = (System.currentTimeMillis() - lastBackupAt) / 86_400_000L
        val text = when (days) {
            0L -> "heute"
            1L -> "gestern"
            else -> "vor $days Tagen"
        }
        return "Datensicherung · letztes Backup: $text"
    }
}
```

- [ ] **Step 4: Tests laufen lassen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --offline
```
Erwartet: PASS, alle Testklassen (inkl. der 6 neuen `BackupViewModelTest`-Fälle).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/backup/BackupViewModel.kt \
        android/app/src/test/kotlin/com/tatoli/habittracker/ui/backup/BackupViewModelTest.kt
git commit -m "feat: BackupViewModel (Export/Import/letztes Backup)"
```

---

### Task 4: `BackupSheet` — UI, Share-Intent, Dateiauswahl, FileProvider

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/backup/BackupSheet.kt`
- Create: `android/app/src/main/res/xml/file_paths.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `BackupViewModel` aus Task 3.
- Produces (für Task 5): `@Composable fun BackupSheet(viewModel: BackupViewModel, onDismiss: () -> Unit)`.

Kein separater Unit-Test (Share-Intent/Dateiauswahl-Launcher sind in
dieser Sandbox nicht automatisiert testbar — wie bei allen bisherigen
Sheets/Screens). Verifikation über `assembleDebug` (kompiliert
erfolgreich) plus manuellen Gerätetest am Ende von Plan E.

- [ ] **Step 1: `FileProvider` im Manifest deklarieren**

In `AndroidManifest.xml`, innerhalb von `<application>` nach der
bestehenden `<activity>`:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.tatoli.habittracker.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

Die vollständige Datei sieht danach so aus:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:allowBackup="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.tatoli.habittracker.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

**Wichtig:** `android:authorities` muss exakt
`com.tatoli.habittracker.fileprovider` sein (`applicationId` +
`.fileprovider`) — ein Tippfehler hier fällt nicht beim Kompilieren auf,
sondern erst als Absturz (`FileUriExposedException`) beim ersten
manuellen Export-Test.

- [ ] **Step 2: `file_paths.xml` anlegen**

Neues Verzeichnis `android/app/src/main/res/xml/`, neue Datei
`file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="backup_exports" path="." />
</paths>
```

- [ ] **Step 3: `BackupSheet.kt` implementieren**

```kotlin
package com.tatoli.habittracker.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(viewModel: BackupViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastBackupText by viewModel.lastBackupText.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingImportXml by remember { mutableStateOf<String?>(null) }
    var pendingImportCount by remember { mutableStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val xml = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("Datei konnte nicht gelesen werden")
                val parsedCount = countHabitsForConfirmation(xml)
                if (viewModel.hasAnyHabits()) {
                    pendingImportXml = xml
                    pendingImportCount = parsedCount
                } else {
                    val imported = viewModel.importXml(xml)
                    errorMessage = "Backup importiert: $imported Gewohnheit(en)."
                }
            } catch (e: Exception) {
                errorMessage = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(text = "Backup", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(text = lastBackupText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        val xml = viewModel.buildExportXml()
                        val fileName = "habits-backup-${System.currentTimeMillis()}.xml"
                        val file = File(context.cacheDir, fileName)
                        file.writeText(xml)
                        val uri = FileProvider.getUriForFile(context, "com.tatoli.habittracker.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/xml"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, fileName))
                        viewModel.markBackupDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exportieren")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { importLauncher.launch(arrayOf("application/xml", "text/xml", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Importieren")
            }
            errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingImportXml?.let { xml ->
        AlertDialog(
            onDismissRequest = { pendingImportXml = null },
            title = { Text("Backup importieren?") },
            text = { Text("Backup enthält $pendingImportCount Gewohnheit(en).\n\nOK = aktuelle Daten ERSETZEN\nAbbrechen = nichts tun") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            val imported = viewModel.importXml(xml)
                            errorMessage = "Backup importiert: $imported Gewohnheit(en)."
                        } catch (e: Exception) {
                            errorMessage = "Import fehlgeschlagen: ${e.message}"
                        }
                        pendingImportXml = null
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                Button(onClick = { pendingImportXml = null }) { Text("Abbrechen") }
            }
        )
    }
}

private fun countHabitsForConfirmation(xml: String): Int =
    com.tatoli.habittracker.util.parseHabitsXml(xml, System.currentTimeMillis()).size
```

**Hinweis zu `countHabitsForConfirmation`:** ruft `parseHabitsXml` einmal
vorab auf, nur um die Anzahl für den Bestätigungsdialog zu kennen (die
Web-App zeigt die Anzahl ebenfalls vor der Bestätigung an). Wird das XML
ungültig sein, wirft das hier bereits die passende Exception — der
`catch`-Block im `importLauncher` fängt sie und zeigt die Fehlermeldung,
bevor überhaupt ein Dialog erscheint. Das ist kein doppeltes Parsen im
fehlerhaften Fall (nur einmal, hier), und im Erfolgsfall wird zweimal
geparst (einmal für die Anzahl, einmal in `viewModel.importXml`) — das ist
für eine XML-Datei in der Größenordnung eines Habit-Backups (typisch
einige KB) vernachlässigbar und hält `BackupViewModel.importXml` als
einzige Stelle, die tatsächlich in die Datenbank schreibt.

- [ ] **Step 4: Kompilierung prüfen**

```bash
./gradlew compileDebugKotlin --offline
```
Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 5: Volle Testsuite + Assemble prüfen**

```bash
./gradlew testDebugUnitTest assembleDebug --offline
```
Erwartet: BUILD SUCCESSFUL, alle Tests grün, `app-debug.apk` erzeugt.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/backup/BackupSheet.kt \
        android/app/src/main/res/xml/file_paths.xml \
        android/app/src/main/AndroidManifest.xml
git commit -m "feat: BackupSheet (Export via Share-Intent, Import via Dateiauswahl)"
```

---

### Task 5: Navigation — viertes FAB & MainActivity-Wiring

**Files:**
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt`

**Interfaces:**
- Consumes: `BackupSheet`/`BackupViewModel` aus Task 3/4.
- Produces: nichts für spätere Tasks (letzter Task des Plans).

- [ ] **Step 1: Viertes FAB in `HabitListScreen.kt`**

`HabitListScreen`-Signatur um einen Parameter erweitern, FAB-Stapel um
ein viertes Element ergänzen, Bottom-Padding der Liste an den jetzt
4 FABs hohen Stapel anpassen (4×56dp + 3×12dp Abstand ≈ 260dp, plus
denselben ~24dp Sicherheitsabstand wie beim vorherigen 3-FAB-Wert
216dp = 260 + 24):

```kotlin
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val viewMonth by viewModel.viewMonth.collectAsState()
    val availableGroups by viewModel.availableGroups.collectAsState()
    val filterGroup by viewModel.filterGroup.collectAsState()
    val listDisplay by viewModel.listDisplay.collectAsState()

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onOpenBackup) {
                    Icon(Icons.Default.Save, contentDescription = "Backup")
                }
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

(Rest der Funktion unverändert, außer der `contentPadding`-Zeile:)

```kotlin
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 284.dp)
            ) {
```

Neuen Import ergänzen: `androidx.compose.material.icons.filled.Save`.

- [ ] **Step 2: Kompilierung prüfen (schlägt fehl — `MainActivity.kt` ruft
  `HabitListScreen` noch mit der alten Signatur auf)**

```bash
./gradlew compileDebugKotlin --offline
```
Erwartet: FAIL — fehlendes Argument `onOpenBackup` im bestehenden
`HabitListScreen(...)`-Aufruf in `MainActivity.kt`.

- [ ] **Step 3: Backup-Sheet-State in `MainActivity.kt` ergänzen**

Das Backup-Sheet ist ein Overlay wie das bestehende Anlegen/Bearbeiten-
Sheet (`EditSheetState`), kein `AppScreen` (kein Vollbild-Screen). Neuer,
unabhängiger Sichtbarkeits-State:

```kotlin
    var showBackupSheet by remember { mutableStateOf(false) }
```

(direkt nach der bestehenden `var sheetState by remember { ... }`-Zeile
in `HabitTrackerApp`.)

Den bestehenden `HabitListScreen(...)`-Aufruf im `AppScreen.List`-Zweig
erweitern:

```kotlin
        is AppScreen.List -> {
            HabitListScreen(
                viewModel = listViewModel,
                onAddHabit = { sheetState = EditSheetState.AddNew },
                onEditHabit = { id -> sheetState = EditSheetState.EditExisting(id) },
                onOpenStats = { currentScreen = AppScreen.Stats },
                onOpenDashboard = { currentScreen = AppScreen.Dashboard },
                onOpenBackup = { showBackupSheet = true }
            )
        }
```

Nach dem bestehenden `when (val state = sheetState) { ... }`-Block (ganz
am Ende von `HabitTrackerApp`, vor der schließenden `}` der Funktion)
ergänzen:

```kotlin
    if (showBackupSheet) {
        // Frische Instanz pro Öffnen, gleicher Grund wie bei den Edit-Sheet-ViewModels:
        // kein Zwischenspeichern von z. B. einer Fehlermeldung aus einem vorherigen Öffnen.
        val backupViewModel = remember(showBackupSheet) {
            val prefs = context.getSharedPreferences("backup_meta", android.content.Context.MODE_PRIVATE)
            com.tatoli.habittracker.ui.backup.BackupViewModel(repository, prefs)
        }
        com.tatoli.habittracker.ui.backup.BackupSheet(
            viewModel = backupViewModel,
            onDismiss = { showBackupSheet = false }
        )
    }
```

**Wichtig:** `context` ist in `HabitTrackerApp` aktuell nicht verfügbar
(nur `repository` wird als Parameter durchgereicht). Am Anfang von
`HabitTrackerApp` ergänzen:

```kotlin
    val context = androidx.compose.ui.platform.LocalContext.current
```

- [ ] **Step 4: Kompilierung und volle Testsuite prüfen**

```bash
./gradlew testDebugUnitTest assembleDebug --offline
```
Erwartet: BUILD SUCCESSFUL, alle Tests grün, `app-debug.apk` erzeugt.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt \
        android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt
git commit -m "feat: Navigation zum Backup-Sheet über viertes FAB"
```

---

## Verifikation nach Abschluss

1. APK aus dem letzten `assembleDebug`-Lauf im Windows-Emulator
   installieren (`adb install -r app-debug.apk`, ohne vorheriges
   Deinstallieren — kein Schema-Wechsel in diesem Plan, aber bestehende
   Testdaten sollen für den Export-Test erhalten bleiben).
2. **Export:** Backup-FAB antippen, "Exportieren" — System-Teilen-Menü
   muss erscheinen, XML-Datei per "In Dateien speichern" (oder
   vergleichbar) ablegen und den Inhalt stichprobenartig prüfen (Namen,
   Farben, Gruppen, `day`-Einträge, `id`-Attribut = Erstellungszeitpunkt
   in Millisekunden). "Letztes Backup"-Text muss danach auf "heute"
   springen.
3. **FileProvider-Absturzcheck:** Falls der Export-Klick sofort abstürzt
   (`FileUriExposedException` in Logcat) — als Erstes die
   `android:authorities` im Manifest gegen `applicationId` prüfen (siehe
   Risiko-Hinweis in der Design-Spec).
4. **Import (ersetzend):** Mit vorhandenen Habits in der App eine zuvor
   exportierte Datei importieren — Bestätigungsdialog mit korrekter
   Anzahl muss erscheinen, nach "OK" müssen die alten Habits komplett
   durch die importierten ersetzt sein (Namen, Farben, Gruppen, Historie,
   Erfolgsquote in der Statistik sofort korrekt basierend auf dem
   importierten Erstellungsdatum).
5. **Import (leere App):** App-Daten löschen (oder auf einem frischen
   Emulator-Profil), Import ausführen — **kein** Bestätigungsdialog,
   direkter Import.
6. **Ungültige Datei:** Eine beliebige Nicht-XML-Datei (z. B. ein Foto)
   zum Import auswählen — Fehlermeldung im Sheet, bestehende Habits
   bleiben unverändert.
7. **Web-App-Interop (falls möglich):** Ein Backup aus der Web-App
   (`habit-tracker`) in die native App importieren und umgekehrt — beide
   Richtungen müssen Namen, Farben, Gruppen, Rhythmus und Tages-/Wochen-
   Historie korrekt übernehmen.
8. FAB-Stapel (Backup/Dashboard/Statistik/Hinzufügen, jetzt 4 Stück) auf
   keinem der Screens überlappend oder abgeschnitten; letzte Habit-Karte
   beim Herunterscrollen nicht vom FAB-Stapel verdeckt.
