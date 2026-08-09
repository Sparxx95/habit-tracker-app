# Native Android-App – Plan A: Grundgerüst Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der bisherige Capacitor-`android/`-Ordner wird durch ein echtes natives Android-Studio-Projekt (Kotlin, Jetpack Compose, Room) ersetzt, mit einem lauffähigen ersten vertikalen Slice: Habit-Liste mit Heute-Toggle, Anlegen/Bearbeiten/Löschen von Habits — alles rein lokal in einer Room-Datenbank, kein Login/Cloud-Sync.

**Architecture:** Standard-Android-Schichtung: Room-Entities/DAO (`data/`) → Repository (`data/HabitRepository.kt`) → ViewModel (`ui/*/`) → Compose-Screen (`ui/*/`). Ein `AppDatabase`-Singleton liefert die Room-Instanz, in `MainActivity` manuell verdrahtet (kein DI-Framework für Phase 1 — YAGNI, nur zwei ViewModels).

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room 2.6.1 (KSP), Gradle 8.9 (Kotlin-DSL), Robolectric 4.13 für JVM-Unit-Tests ohne Emulator.

## Global Constraints

- `applicationId`/Kotlin-`namespace`: `com.tatoli.habittracker` (Konsistenz mit der bisherigen Capacitor-App).
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- Farben (exakt aus `index.html`s `:root`-CSS-Variablen der Web-App):
  `bg = #0E2527`, `card = #16393B`, `ink = #EDF4F0`, `muted = #7FA39B`,
  `amber = #F2B450`, `amberDim = #8F6A2E`.
- Habit-Farbpalette (exakt aus der Web-App, `PALETTE`-Konstante):
  `#F2B450, #4FC98A, #5FB4E5, #E5766B, #C08BE0, #E0C34F`.
- **Bewusst NICHT Teil dieses Plans** (kommt in späteren Plänen):
  Gruppen-Feld beim Anlegen/Bearbeiten (Plan C), Monats-/KW-Raster und
  Streak-/Monatsbilanz-Anzeige (Plan B), Statistik (Plan D), XML-Backup
  (Plan E), Login/Firebase/Firestore (dauerhaft außerhalb des Scopes).
- Kein Play-Store-Release, kein signierter Build — nur Debug-APKs.
- **Lokale Verifikation:** In dieser Entwicklungsumgebung ist ein Android-SDK
  unter `~/.android-sdk` und ein JDK 17 unter `~/.jdk17` installiert (kein
  Gerät/Emulator vorhanden, aber echte `./gradlew`-Kompilierung und
  JVM-Unit-Tests via Robolectric funktionieren). Jeder Verifikations-Schritt
  in diesem Plan muss vorher setzen:
  ```bash
  export JAVA_HOME="$HOME/.jdk17"
  export PATH="$JAVA_HOME/bin:$PATH"
  export ANDROID_HOME="$HOME/.android-sdk"
  export ANDROID_SDK_ROOT="$HOME/.android-sdk"
  ```
  Volle Compose-UI-Rendering-Tests (`createComposeRule` o. Ä.) sind in dieser
  Umgebung NICHT verifiziert/nicht Teil dieses Plans — Verifikation für
  UI-Code ist „kompiliert erfolgreich" + ViewModel-Unit-Tests. Die einzige
  vollständige visuelle Prüfung bleibt Android Studio + Emulator unter
  Windows (siehe `CLAUDE.md`).

---

## Datei-Übersicht

```
android/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, gradle/wrapper/gradle-wrapper.jar   # generiert, nicht handgeschrieben
  local.properties        # gitignored, lokal je Maschine
  .gitignore
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/res/values/strings.xml
    src/main/kotlin/com/tatoli/habittracker/
      MainActivity.kt
      ui/theme/Color.kt
      ui/theme/Theme.kt
      util/Palette.kt
      util/DateUtils.kt
      data/HabitEntity.kt
      data/HabitDoneEntity.kt
      data/HabitDao.kt
      data/AppDatabase.kt
      data/HabitRepository.kt
      ui/habitlist/HabitListViewModel.kt
      ui/habitlist/HabitListScreen.kt
      ui/habitedit/HabitEditViewModel.kt
      ui/habitedit/HabitEditSheet.kt
    src/test/kotlin/com/tatoli/habittracker/
      data/HabitDaoTest.kt
      ui/habitlist/HabitListViewModelTest.kt
.github/workflows/android-build.yml   # komplett neu geschrieben
CLAUDE.md                              # Abschnitt "Native Android-App" ersetzt
```

---

### Task 1: Natives Android-Projekt-Grundgerüst + CI + Dokumentation

**Files:**
- Delete: kompletter bisheriger Inhalt von `android/` (Capacitor-generiert)
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/.gitignore`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt` (Platzhalter-Version, wird in Task 4 final verdrahtet)
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/theme/Color.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/theme/Theme.kt`
- Create (generiert, nicht per Hand): `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.properties`, `android/gradle/wrapper/gradle-wrapper.jar`
- Modify: `.github/workflows/android-build.yml`
- Modify: `CLAUDE.md`

**Interfaces:**
- Produces: lauffähiges, leeres Compose-Grundgerüst (`MainActivity` zeigt nur einen Platzhalter-Text), `Theme.kt` exportiert `HabitTrackerTheme(content: @Composable () -> Unit)` — wird von allen späteren Tasks als äußerste Theme-Hülle verwendet. `./gradlew assembleDebug` im `android/`-Ordner ist ab hier der Standard-Build-Befehl für alle folgenden Tasks.

- [ ] **Step 1: Alten Capacitor-Android-Inhalt entfernen**

```bash
cd /home/tatoli/claude/git_repos/habit-tracker-app
rm -rf android
mkdir -p android/app/src/main/kotlin/com/tatoli/habittracker/ui/theme
mkdir -p android/app/src/main/res/values
```

- [ ] **Step 2: `android/settings.gradle.kts` anlegen**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "habit-tracker-app"
include(":app")
```

- [ ] **Step 3: `android/build.gradle.kts` anlegen**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
```

- [ ] **Step 4: `android/gradle.properties` anlegen**

```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g
```

- [ ] **Step 5: `android/.gitignore` anlegen**

```
*.iml
.gradle/
local.properties
/.idea/
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
/app/build
```

- [ ] **Step 6: `android/app/build.gradle.kts` anlegen**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tatoli.habittracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tatoli.habittracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
```

- [ ] **Step 7: `android/app/src/main/AndroidManifest.xml` anlegen**

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
    </application>
</manifest>
```

- [ ] **Step 8: `android/app/src/main/res/values/strings.xml` anlegen**

```xml
<resources>
    <string name="app_name">Habits</string>
</resources>
```

- [ ] **Step 9: `ui/theme/Color.kt` anlegen**

```kotlin
package com.tatoli.habittracker.ui.theme

import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0E2527)
val Card = Color(0xFF16393B)
val CardEdge = Color(0xFF1F4A4C)
val Ink = Color(0xFFEDF4F0)
val Muted = Color(0xFF7FA39B)
val Amber = Color(0xFFF2B450)
val AmberDim = Color(0xFF8F6A2E)

val HabitPalette = listOf(
    "#F2B450", "#4FC98A", "#5FB4E5", "#E5766B", "#C08BE0", "#E0C34F"
)
```

- [ ] **Step 10: `ui/theme/Theme.kt` anlegen**

```kotlin
package com.tatoli.habittracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// App ist immer dunkel (analog zur Web-App, die keinen hellen Modus hat) —
// keine isSystemInDarkTheme()-Verzweigung nötig.
private val HabitTrackerColorScheme = darkColorScheme(
    background = Bg,
    surface = Card,
    outline = CardEdge,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    primary = Amber,
    secondary = AmberDim,
    onPrimary = Bg
)

@Composable
fun HabitTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HabitTrackerColorScheme,
        content = content
    )
}
```

- [ ] **Step 11: Platzhalter-`MainActivity.kt` anlegen**

```kotlin
package com.tatoli.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tatoli.habittracker.ui.theme.HabitTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HabitTrackerTheme {
                Surface {
                    Text("Habits")
                }
            }
        }
    }
}
```

- [ ] **Step 12: Gradle-Wrapper generieren**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
"$HOME/.gradle-8.9/bin/gradle" wrapper --gradle-version 8.9
chmod +x gradlew
```
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` existieren.

- [ ] **Step 13: Ersten Build verifizieren**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: `.github/workflows/android-build.yml` komplett neu schreiben**

```yaml
name: Android-Build

on:
  repository_dispatch:
    types: [habit-tracker-index-updated]
  workflow_dispatch: {}

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Unit-Tests
        working-directory: android
        run: ./gradlew testDebugUnitTest

      - name: Debug-APK bauen
        working-directory: android
        run: ./gradlew assembleDebug

      - name: Build-Artefakt hochladen
        uses: actions/upload-artifact@v4
        with:
          name: habit-tracker-android-debug
          path: android/app/build/outputs/apk/debug/*.apk
          retention-days: 14
```

- [ ] **Step 15: YAML validieren**

```bash
python3 -c "
import yaml
with open('.github/workflows/android-build.yml') as f:
    doc = yaml.safe_load(f)
print([s.get('name', s.get('uses')) for s in doc['jobs']['build']['steps']])
"
```
Expected: `['actions/checkout@v4', 'actions/setup-java@v4', 'Unit-Tests', 'Debug-APK bauen', 'Build-Artefakt hochladen']` — kein `npm`/`cap:sync` mehr enthalten.

- [ ] **Step 16: `CLAUDE.md` anpassen**

Titel (Zeile 1) ändern von
`# habit-tracker-app – Native iOS/Android-Wrapper (Capacitor)`
zu
`# habit-tracker-app – Native iOS-App (Capacitor) + native Android-App (Kotlin/Compose)`

Den Abschnitt `## Native Android-App (Capacitor)` (kompletter Abschnitt bis
vor `## Entwicklungs-Workflow`) komplett ersetzen durch:

```markdown
## Native Android-App (Kotlin, Jetpack Compose, Room)

- Android ist seit [Datum] eine **echte native App** (kein WebView, kein
  Capacitor mehr) — Kotlin, Jetpack Compose (UI), Room (lokale
  SQLite-Datenbank). Siehe
  `docs/superpowers/specs/2026-08-09-native-android-app-design.md` für die
  vollständige Architektur-Entscheidung.
- **Bewusst rein lokal:** kein Login, kein Firebase, kein Cloud-Sync in der
  Android-App — alle Daten liegen ausschließlich in einer lokalen
  Room-Datenbank auf dem Gerät. Einziger Datenaustauschweg zur Web-App ist
  der manuelle XML-Export/-Import (gleiches Format wie die Web-App).
- `android-build.yml` baut bei jedem `repository_dispatch`-Event (und
  manuell per `workflow_dispatch`) auf einem `ubuntu-latest`-Runner Unit-Tests
  + ein **Debug-APK** (`./gradlew assembleDebug`) und lädt es als
  Actions-Artifact hoch. Kein `npm`/Capacitor-Sync mehr nötig, da die App
  keinen Web-Inhalt mehr lädt.
- **Lokaler Test-Loop (primärer Weg, da kein Android-Gerät vorhanden ist):**
  Android Studio unter Windows installieren (nicht in WSL2 — bessere
  GPU-Beschleunigung für den Emulator), im SDK-Manager ein Android Virtual
  Device (AVD) anlegen. Projekt `android/` unter Windows in Android Studio
  öffnen (Gradle-Sync läuft automatisch, kein Sync-Skript mehr nötig) und
  "Run" → App startet im AVD-Emulator.
- Alternative ohne Android Studio: fertige APK aus dem letzten
  `Android-Build`-Actions-Lauf herunterladen
  (`gh run download <id> -n habit-tracker-android-debug`) und per Drag &
  Drop auf ein laufendes Emulator-Fenster installieren.
```

Im Abschnitt `## Entwicklungs-Workflow` den Satz „Änderungen hier betreffen
nur natives Verhalten: App-Icon, Splash-Screen, native Permissions,
Capacitor-Plugins, Build-Konfiguration." ergänzen um einen Hinweis, dass
das nur noch für iOS gilt:

Suche:
```
- Änderungen hier betreffen nur natives Verhalten: App-Icon,
  Splash-Screen, native Permissions, Capacitor-Plugins,
  Build-Konfiguration.
```
Ersetze durch:
```
- Für iOS (Capacitor) gilt weiterhin: Änderungen hier betreffen nur
  natives Verhalten (App-Icon, Splash-Screen, native Permissions,
  Capacitor-Plugins, Build-Konfiguration) — App-Code bleibt in
  `habit-tracker`.
- Für Android (echte native App, kein Capacitor mehr) ist `android/` die
  alleinige Quelle der Wahrheit für UI und Funktionalität — es gibt keinen
  Sync mehr von `index.html`.
```

- [ ] **Step 17: Alles committen**

```bash
cd /home/tatoli/claude/git_repos/habit-tracker-app
git add android .github/workflows/android-build.yml CLAUDE.md
git status --short   # sicherstellen, dass local.properties NICHT dabei ist
git commit -m "feat: natives Android-Grundgerüst (Compose/Room) ersetzt Capacitor-Wrapper"
```

---

### Task 2: Room-Datenschicht (Entities, DAO, Database, Repository)

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitEntity.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDoneEntity.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitDao.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/data/AppDatabase.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/data/HabitRepository.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/data/HabitDaoTest.kt`

**Interfaces:**
- Consumes: nichts aus Task 1 außer dem Projekt-Grundgerüst selbst.
- Produces: `HabitEntity(id: Long, name: String, color: String, freq: String)`,
  `HabitWithDoneFlag(id: Long, name: String, color: String, freq: String, doneToday: Boolean)`,
  `HabitDao` mit `observeHabitsWithDoneFlag(dateKey: String): Flow<List<HabitWithDoneFlag>>`,
  `insertHabit`, `updateHabit`, `deleteHabit`, `getHabitById`, `insertDone`, `deleteDone`.
  `AppDatabase.getInstance(context: Context): AppDatabase` (Singleton).
  `HabitRepository(dao: HabitDao)` mit `observeHabitsWithDoneFlag(dateKey)`,
  `addHabit(name, color, freq): Long`, `updateHabit(habit)`, `deleteHabit(habit)`,
  `getHabitById(id): HabitEntity?`, `toggleDone(habitId, dateKey, currentlyDone)`.
  Diese Signaturen werden von Task 3 und Task 4 direkt verwendet.

- [ ] **Step 1: Fehlschlagenden Test schreiben (`HabitDaoTest.kt`)**

```kotlin
package com.tatoli.habittracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitDaoTest {

    private fun buildDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun observeHabitsWithDoneFlag_reflectsInsertAndToggle() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val id1 = dao.insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        dao.insertHabit(HabitEntity(name = "Sport", color = "#4FC98A", freq = "weekly"))

        var result = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertEquals(2, result.size)
        assertTrue(result.all { !it.doneToday })

        dao.insertDone(HabitDoneEntity(habitId = id1, dateKey = "2026-08-09"))
        result = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertTrue(result.first { it.id == id1 }.doneToday)
        assertFalse(result.first { it.name == "Sport" }.doneToday)

        dao.deleteDone(id1, "2026-08-09")
        result = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertFalse(result.first { it.id == id1 }.doneToday)

        db.close()
    }

    @Test
    fun deleteHabit_cascadesToHabitDone() = runBlocking {
        val db = buildDb()
        val dao = db.habitDao()

        val id = dao.insertHabit(HabitEntity(name = "Meditation", color = "#5FB4E5", freq = "daily"))
        dao.insertDone(HabitDoneEntity(habitId = id, dateKey = "2026-08-09"))

        val habit = dao.getHabitById(id)
        assertEquals("Meditation", habit?.name)

        dao.deleteHabit(habit!!)
        val afterDelete = dao.observeHabitsWithDoneFlag("2026-08-09").first()
        assertTrue(afterDelete.isEmpty())

        db.close()
    }
}
```

- [ ] **Step 2: Test ausführen, sicherstellen dass er fehlschlägt**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest --console=plain
```
Expected: FAIL — Compile-Fehler, da `HabitEntity`/`HabitDoneEntity`/`AppDatabase` noch nicht existieren (`unresolved reference`).

- [ ] **Step 3: `data/HabitEntity.kt` anlegen**

```kotlin
package com.tatoli.habittracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val freq: String   // "daily" | "weekly"
)
```

- [ ] **Step 4: `data/HabitDoneEntity.kt` anlegen**

```kotlin
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
```

- [ ] **Step 5: `data/HabitDao.kt` anlegen**

```kotlin
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
```

- [ ] **Step 6: `data/AppDatabase.kt` anlegen**

```kotlin
package com.tatoli.habittracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HabitEntity::class, HabitDoneEntity::class], version = 1, exportSchema = false)
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
                ).build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 7: Test erneut ausführen, sicherstellen dass er besteht**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, beide Tests in `HabitDaoTest` grün (siehe
`android/app/build/test-results/testDebugUnitTest/TEST-com.tatoli.habittracker.data.HabitDaoTest.xml`
— `failures="0" errors="0"`).

- [ ] **Step 8: `data/HabitRepository.kt` anlegen**

```kotlin
package com.tatoli.habittracker.data

import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao) {

    fun observeHabitsWithDoneFlag(dateKey: String): Flow<List<HabitWithDoneFlag>> =
        dao.observeHabitsWithDoneFlag(dateKey)

    suspend fun addHabit(name: String, color: String, freq: String): Long =
        dao.insertHabit(HabitEntity(name = name, color = color, freq = freq))

    suspend fun updateHabit(habit: HabitEntity) = dao.updateHabit(habit)

    suspend fun deleteHabit(habit: HabitEntity) = dao.deleteHabit(habit)

    suspend fun getHabitById(id: Long): HabitEntity? = dao.getHabitById(id)

    suspend fun toggleDone(habitId: Long, dateKey: String, currentlyDone: Boolean) {
        if (currentlyDone) dao.deleteDone(habitId, dateKey)
        else dao.insertDone(HabitDoneEntity(habitId = habitId, dateKey = dateKey))
    }
}
```

`HabitRepository` hat keinen eigenen Test (reine Delegation ohne eigene
Logik) — die Verhaltenskorrektheit ist bereits durch `HabitDaoTest`
abgedeckt.

- [ ] **Step 9: Vollen Build + Tests verifizieren**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
cd /home/tatoli/claude/git_repos/habit-tracker-app
git add android/app/src/main/kotlin/com/tatoli/habittracker/data android/app/src/test/kotlin/com/tatoli/habittracker/data
git commit -m "feat: Room-Datenschicht (Habit-Entities, DAO, Repository)"
```

---

### Task 3: Habit-Liste-Screen + Heute-Toggle

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/util/DateUtils.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModel.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListScreen.kt`
- Test: `android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist/HabitListViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitRepository`, `HabitWithDoneFlag` aus Task 2; `HabitTrackerTheme`, `Amber`, `Muted` aus Task 1.
- Produces: `todayKey(): String` (util, wird auch von Task 4 nicht gebraucht,
  aber von Plan B wiederverwendet werden). `HabitListViewModel(repository: HabitRepository)`
  mit `habits: StateFlow<List<HabitWithDoneFlag>>` und `fun toggleDone(habit: HabitWithDoneFlag)`.
  `HabitListScreen(viewModel: HabitListViewModel, onAddHabit: () -> Unit, onEditHabit: (Long) -> Unit)` —
  wird in Task 4 von `MainActivity` aufgerufen.

- [ ] **Step 1: `util/DateUtils.kt` anlegen**

```kotlin
package com.tatoli.habittracker.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayKey(): String = LocalDate.now().format(DATE_FORMAT)
```

- [ ] **Step 2: Fehlschlagenden Test schreiben (`HabitListViewModelTest.kt`)**

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitEntity
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.util.todayKey
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
    fun toggleDone_updatesHabitsFlow() = runBlocking {
        val id = db.habitDao().insertHabit(HabitEntity(name = "Lesen", color = "#F2B450", freq = "daily"))
        val viewModel = HabitListViewModel(repository)

        var current = repository.observeHabitsWithDoneFlag(todayKey()).first()
        assertEquals(1, current.size)
        assertTrue(!current.first().doneToday)

        viewModel.toggleDone(current.first())
        current = repository.observeHabitsWithDoneFlag(todayKey()).first()
        assertTrue(current.first { it.id == id }.doneToday)
    }
}
```

- [ ] **Step 3: Test ausführen, sicherstellen dass er fehlschlägt**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest --console=plain
```
Expected: FAIL — `unresolved reference: HabitListViewModel`.

- [ ] **Step 4: `ui/habitlist/HabitListViewModel.kt` anlegen**

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.data.HabitWithDoneFlag
import com.tatoli.habittracker.util.todayKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitListViewModel(private val repository: HabitRepository) : ViewModel() {

    val habits: StateFlow<List<HabitWithDoneFlag>> = repository
        .observeHabitsWithDoneFlag(todayKey())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleDone(habit: HabitWithDoneFlag) {
        viewModelScope.launch {
            repository.toggleDone(habit.id, todayKey(), habit.doneToday)
        }
    }
}
```

- [ ] **Step 5: Test erneut ausführen, sicherstellen dass er besteht**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, `HabitListViewModelTest` grün.

- [ ] **Step 6: `ui/habitlist/HabitListScreen.kt` anlegen**

```kotlin
package com.tatoli.habittracker.ui.habitlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tatoli.habittracker.data.HabitWithDoneFlag

@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit
) {
    val habits by viewModel.habits.collectAsState()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(habits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    onToggle = { viewModel.toggleDone(habit) },
                    onClick = { onEditHabit(habit.id) }
                )
            }
        }
    }
}

@Composable
fun HabitCard(
    habit: HabitWithDoneFlag,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
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
            IconToggleButton(checked = habit.doneToday, onCheckedChange = { onToggle() }) {
                Icon(
                    imageVector = if (habit.doneToday) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (habit.doneToday) "Heute erledigt" else "Heute nicht erledigt",
                    tint = if (habit.doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

private fun parseHexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
```

- [ ] **Step 7: Vollen Build verifizieren**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL` (Compose-Compiler kompiliert `HabitListScreen`/`HabitCard` fehlerfrei).

- [ ] **Step 8: Commit**

```bash
cd /home/tatoli/claude/git_repos/habit-tracker-app
git add android/app/src/main/kotlin/com/tatoli/habittracker/util android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitlist android/app/src/test/kotlin/com/tatoli/habittracker/ui/habitlist
git commit -m "feat: Habit-Liste-Screen mit Heute-Toggle"
```

---

### Task 4: Anlegen/Bearbeiten-Sheet + App-Verdrahtung

**Files:**
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditViewModel.kt`
- Create: `android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit/HabitEditSheet.kt`
- Modify: `android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt`

**Interfaces:**
- Consumes: `HabitRepository`, `HabitEntity` aus Task 2; `HabitListScreen`,
  `HabitListViewModel` aus Task 3; `HabitPalette` aus Task 1
  (`ui/theme/Color.kt`).
- Produces: `HabitEditViewModel(repository: HabitRepository, habitId: Long?)`,
  `HabitEditSheet(viewModel: HabitEditViewModel, onDismiss: () -> Unit, onSaved: () -> Unit)`.
  `MainActivity` verdrahtet ab hier `AppDatabase` → `HabitRepository` →
  beide Screens über einen lokalen `EditSheetState`.

Kein eigener Unit-Test für `HabitEditViewModel` mit UI-Zustand (`name`,
`color`, `freq` als `mutableStateOf`) — dieser State ist reiner
Compose-UI-State ohne eigene Verzweigungslogik jenseits dessen, was
`HabitDaoTest`/`HabitListViewModelTest` bereits für die zugrundeliegenden
Repository-Aufrufe abdecken. Verifikation hier: `assembleDebug` kompiliert
(Compose-API-Korrektheit) — konsistent mit der in den Global Constraints
festgelegten Grenze für UI-Code in dieser Umgebung.

- [ ] **Step 1: `ui/habitedit/HabitEditViewModel.kt` anlegen**

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
                }
                loaded = true
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onColorChange(value: String) { color = value }
    fun onFreqChange(value: String) { freq = value }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            if (habitId == null) {
                repository.addHabit(name, color, freq)
            } else {
                repository.updateHabit(HabitEntity(id = habitId, name = name, color = color, freq = freq))
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.deleteHabit(HabitEntity(id = id, name = name, color = color, freq = freq))
            onDone()
        }
    }
}
```

- [ ] **Step 2: `ui/habitedit/HabitEditSheet.kt` anlegen**

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tatoli.habittracker.ui.theme.HabitPalette

@Composable
fun HabitEditSheet(
    viewModel: HabitEditViewModel,
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
                            .size(32.dp)
                            .padding(4.dp)
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
```

- [ ] **Step 3: `MainActivity.kt` final verdrahten (ersetzt den Platzhalter aus Task 1)**

```kotlin
package com.tatoli.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tatoli.habittracker.data.AppDatabase
import com.tatoli.habittracker.data.HabitRepository
import com.tatoli.habittracker.ui.habitedit.HabitEditSheet
import com.tatoli.habittracker.ui.habitedit.HabitEditViewModel
import com.tatoli.habittracker.ui.habitlist.HabitListScreen
import com.tatoli.habittracker.ui.habitlist.HabitListViewModel
import com.tatoli.habittracker.ui.theme.HabitTrackerTheme

private sealed interface EditSheetState {
    data object Hidden : EditSheetState
    data object AddNew : EditSheetState
    data class EditExisting(val habitId: Long) : EditSheetState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HabitRepository(AppDatabase.getInstance(applicationContext).habitDao())

        setContent {
            HabitTrackerTheme {
                Surface {
                    HabitTrackerApp(repository)
                }
            }
        }
    }
}

@Composable
private fun HabitTrackerApp(repository: HabitRepository) {
    var sheetState by remember { mutableStateOf<EditSheetState>(EditSheetState.Hidden) }

    val listViewModel: HabitListViewModel = viewModel(factory = viewModelFactory {
        initializer { HabitListViewModel(repository) }
    })

    HabitListScreen(
        viewModel = listViewModel,
        onAddHabit = { sheetState = EditSheetState.AddNew },
        onEditHabit = { id -> sheetState = EditSheetState.EditExisting(id) }
    )

    when (val state = sheetState) {
        is EditSheetState.Hidden -> Unit
        is EditSheetState.AddNew -> {
            val editViewModel: HabitEditViewModel = viewModel(
                key = "add-new",
                factory = viewModelFactory {
                    initializer { HabitEditViewModel(repository, habitId = null) }
                }
            )
            HabitEditSheet(
                viewModel = editViewModel,
                onDismiss = { sheetState = EditSheetState.Hidden },
                onSaved = { sheetState = EditSheetState.Hidden }
            )
        }
        is EditSheetState.EditExisting -> {
            val editViewModel: HabitEditViewModel = viewModel(
                key = "edit-${state.habitId}",
                factory = viewModelFactory {
                    initializer { HabitEditViewModel(repository, habitId = state.habitId) }
                }
            )
            HabitEditSheet(
                viewModel = editViewModel,
                onDismiss = { sheetState = EditSheetState.Hidden },
                onSaved = { sheetState = EditSheetState.Hidden }
            )
        }
    }
}
```

- [ ] **Step 4: Vollen Build + alle Tests verifizieren**

```bash
export JAVA_HOME="$HOME/.jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.android-sdk"
export ANDROID_SDK_ROOT="$HOME/.android-sdk"
cd /home/tatoli/claude/git_repos/habit-tracker-app/android
./gradlew testDebugUnitTest assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`, alle bisherigen Tests weiterhin grün
(`HabitDaoTest`, `HabitListViewModelTest`), APK unter
`android/app/build/outputs/apk/debug/app-debug.apk` vorhanden.

- [ ] **Step 5: Commit**

```bash
cd /home/tatoli/claude/git_repos/habit-tracker-app
git add android/app/src/main/kotlin/com/tatoli/habittracker/ui/habitedit android/app/src/main/kotlin/com/tatoli/habittracker/MainActivity.kt
git commit -m "feat: Anlegen/Bearbeiten-Sheet, App-Verdrahtung in MainActivity"
```

---

## Reihenfolge / Abhängigkeiten

Task 1 → Task 2 → Task 3 → Task 4 (strikt sequenziell — jede Task baut auf
den Kotlin-Typen/Funktionssignaturen der vorigen auf).

## Verifikation nach Abschluss (kein separater Task, manuell nach Task 4)

Da `habit-tracker-app` keinen Branch/PR-Workflow hat (Commits gehen direkt
auf `main`, wie bei den vorigen Plänen in diesem Repo), nach Abschluss aller
4 Tasks:

1. `git push` (Commits liegen bereits auf `main`, sofern jeder Task-Commit
   dort direkt erfolgte).
2. `gh workflow run android-build.yml --repo Sparxx95/habit-tracker-app`
   manuell auslösen und beobachten (`gh run watch <id> --repo
   Sparxx95/habit-tracker-app --exit-status`) — bestätigt, dass der Build
   auch auf einem echten `ubuntu-latest`-Runner (nicht nur in dieser
   Sandbox) funktioniert, inkl. Unit-Tests.
3. APK-Artifact herunterladen und wie gewohnt per Drag & Drop im Emulator
   installieren (siehe `CLAUDE.md`) — visuelle/funktionale Prüfung von Hand:
   App öffnen, Habit anlegen, Heute-Toggle antippen, Habit bearbeiten,
   Habit löschen.
