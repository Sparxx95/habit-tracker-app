# Design-Spec: Echte native Android-App (Kotlin/Compose/Room)

**Datum:** 2026-08-09
**Status:** Genehmigt, bereit für Implementierungsplanung

## Ausgangslage

`habit-tracker-app` enthält bisher nur einen dünnen Capacitor-WebView-Wrapper
um dieselbe `index.html`, die auch als Web-App auf GitHub Pages läuft (siehe
`docs/superpowers/specs/2026-08-07-android-app-ci-design.md` im
`habit-tracker`-Repo für die Historie dieses Wrappers). Die App hat keinen
eigenen nativen Code und keine eigene lokale Datenbank — `localStorage`
innerhalb der WebView übernimmt die Persistenz.

**Ziel dieser Änderung:** Der Android-Teil von `habit-tracker-app` wird durch
eine **echte native Android-App** ersetzt (Kotlin, Jetpack Compose, Room als
lokale Datenbank) — als Grundlage für spätere echte native Funktionen
(Erinnerungen, Widgets, o. Ä.), die mit einer reinen WebView nicht sauber
umsetzbar wären. Die iOS-Seite (Capacitor) bleibt vorerst unverändert
bestehen und unabhängig wartbar.

**Rahmenbedingungen (mit Nutzer abgestimmt):**
- Nur Android, iOS bleibt vorerst beim bestehenden Capacitor-Wrapper.
- Die neue native App lebt im bestehenden Repo `Sparxx95/habit-tracker-app`,
  im bisherigen `android/`-Ordner (kompletter Ersatz des bisherigen
  Capacitor-generierten Inhalts).
- **Volle funktionale Parität** zur Web-App bei den Habit-Tracking-Features
  (Anlegen/Bearbeiten/Löschen, Heute-Toggle, Monats-/KW-Raster, Streaks,
  Gruppen, Statistik, XML-Backup/Import) — **außer** Login/Cloud-Sync
  (Firebase Auth + Firestore): Phase 1 ist bewusst rein lokal, kein Login,
  kein Cloud-Sync.
- Design: Look & Feel der Web-App übernehmen (Farbpalette, grundlegende
  Bedienlogik), aber **idiomatisch in Compose/Material 3** umgesetzt, nicht
  pixelgenau nachgebaut (Ausnahme: das Ringdiagramm in der Statistik braucht
  zwangsläufig eigene Canvas-Zeichnung, da kein Material-Standard-Widget
  dafür existiert).
- `index.html`/`www/`/`scripts/sync-www.js`/`package.json`/
  `capacitor.config.json` bleiben unverändert bestehen (werden weiterhin für
  die iOS-Capacitor-Seite gebraucht).

## Nicht-Ziele (Phase 1)

- Kein Login, kein Firebase Auth, kein Firestore-Cloud-Sync in der nativen
  App.
- Keine automatische Migration/Übernahme eventuell vorhandener
  Capacitor-WebView-`localStorage`-Testdaten (die App wurde bisher nur kurz
  manuell getestet, keine echten Nutzdaten vorhanden).
- Kein Play-Store-Release, kein signierter Build — weiterhin nur
  Debug-APKs wie bisher.
- Keine Änderung an der iOS-Capacitor-Pipeline.
- Keine Änderung an `habit-tracker` (Web-App-Repo).
- Kein Live-Sync zwischen Web-App und nativer App — der XML-Export/Import
  ist der einzige (manuelle) Datenaustauschweg zwischen beiden.

## Architektur

### Tech-Stack

- **Kotlin**, **Jetpack Compose** (UI, deklarativ statt XML-Layouts)
- **Room** (offizielle Android-Persistenzbibliothek über SQLite) als lokale
  Datenbank
- **Material 3** mit eigenem `ColorScheme` (dunkles Petrol + Bernstein-Akzent,
  analog zu den CSS-Variablen der Web-App)
- Architektur-Pattern: `ViewModel` + `StateFlow` pro Screen, Repository-
  Schicht über Room-DAOs (Standard-Android-Empfehlung, gut isoliert
  testbar ohne UI)
- `minSdk 26` (Android 8.0 — deckt praktisch alle realistischen Geräte ab,
  vereinfacht spätere native Funktionen), `targetSdk` aktuelle stabile
  Version zum Zeitpunkt der Umsetzung
- `applicationId` bleibt `com.tatoli.habittracker` (Konsistenz mit der
  bisherigen Capacitor-App)

### Datenmodell (Room)

Ersetzt das bisherige JSON-Blob-Format der Web-App durch echte relationale
Tabellen — ermöglicht echte SQL-Abfragen (z. B. für Streak-Berechnung) statt
Array-Scans über die gesamte Historie:

```kotlin
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,     // Hex, z.B. "#F2B450"
    val group: String,     // "" = keine Gruppe
    val freq: String       // "daily" | "weekly"
)

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
    val dateKey: String     // "YYYY-MM-DD" (daily) oder "YYYY-Www" (weekly)
)
```

`onDelete = CASCADE` sorgt dafür, dass beim Löschen eines Habits automatisch
alle zugehörigen `habit_done`-Einträge mitgelöscht werden (SQL-seitig, kein
manueller Zusatzcode nötig).

### Datenaustausch mit der Web-App (nur XML, manuell)

Das XML-Backup-Format bleibt **identisch** zum bestehenden Format der
Web-App (siehe `CLAUDE.md` in `habit-tracker`):

```xml
<habits exported="ISO-Datum" version="2">
  <habit id="…" name="…" color="#…" group="…" freq="daily|weekly">
    <day date="2026-07-19"/>
  </habit>
</habits>
```

Export nutzt Androids Share-Intent (Datei teilen), Import liest eine vom
Nutzer ausgewählte XML-Datei über den System-Dateiauswahldialog. Damit kann
der Nutzer bei Bedarf manuell Daten zwischen Web-App und nativer App
austauschen — kein automatischer Sync, rein dateibasiert.

### Screens / Feature-Inventar

1. **Habit-Liste (Hauptscreen):** Heute-Toggle pro Habit, Streak-Anzeige,
   Gruppen-Überschriften, Filter-Chips ("Alle" + Gruppen), Tagesfortschritt
   im Header.
2. **Anlegen/Bearbeiten-Sheet:** Name, Farbauswahl (aus fester Palette wie
   Web-App), Gruppe (Freitext mit Vorschlägen aus vorhandenen Gruppen),
   Rhythmus (täglich/wöchentlich), Löschen.
3. **Monats-/KW-Raster:** Je nach Rhythmus des Habits — Monatsraster
   (täglich) bzw. KW-Streifen (wöchentlich), Nachtragen vergangener
   Tage/Wochen durch Antippen, Monatsnavigation (nicht in die Zukunft).
4. **Statistik-Screen:** Monatsübersicht aller Habits, Umschalter
   Tabelle/Ringdiagramm (Ring als eigene Compose-`Canvas`-Zeichnung),
   wöchentliche Habits separat als KW-Streifen, Legende mit Erfolgsquote
   und längster Serie je Habit.
5. **Backup-Screen:** XML-Export (Share-Intent) und -Import (Dateiauswahl,
   mit Ersetzen-Bestätigung), Anzeige "letztes Backup vor n Tagen".

### CI-Integration

`.github/workflows/android-build.yml` wird umgeschrieben: Der bisherige
Capacitor-Sync-Schritt (`npm run cap:sync:android`, abhängig von
`www/index.html`) entfällt komplett — das neue native Projekt hat keine
WebView-Inhalte zu synchronisieren. Der Workflow reduziert sich auf
Checkout → JDK/Gradle-Setup → `./gradlew assembleDebug` im `android/`-Ordner
→ APK-Artifact hochladen. Trigger (`repository_dispatch` +
`workflow_dispatch`) bleiben unverändert, auch wenn der
`repository_dispatch`-Trigger (ausgelöst bei Änderungen an `index.html` in
`habit-tracker`) für die native App nicht mehr direkt relevant ist, da sie
`index.html` nicht mehr konsumiert — er bleibt aus Konsistenzgründen bestehen
und kann später entfernt werden, falls er sich als unnötig erweist.

## Umsetzung in mehreren Plänen

Wegen des Umfangs (volle Feature-Parität) wird die Umsetzung in mehrere
aufeinander aufbauende Implementierungspläne aufgeteilt, jeder für sich
lauffähig und testbar:

- **Plan A — Grundgerüst:** Natives Android-Studio-Projekt (Compose, Room,
  Material-3-Theme), CI-Workflow-Umschreibung, Habit-Liste + Heute-Toggle +
  Anlegen/Bearbeiten (kleinster durchgängiger Vertical Slice).
- **Plan B — Zeitraster:** Monats-/KW-Raster mit Navigation,
  Streak-/Monatsbilanz-Berechnung.
- **Plan C — Gruppen:** Gruppen-Zuweisung, -Überschriften, Filter-Chips.
- **Plan D — Statistik:** Statistik-Screen (Tabelle + Ringdiagramm).
- **Plan E — Backup:** XML-Export/Import.

Jeder Plan wird einzeln brainstormed/geplant, sobald der vorherige
abgeschlossen ist (nicht alle auf einmal im Voraus durchgeplant, um auf
Erkenntnisse aus der Umsetzung reagieren zu können).

## Risiken / offene Punkte

- **Ringdiagramm-Statistik** ist der aufwendigste UI-Teil (eigene
  Canvas-Zeichnung ohne Material-Vorlage) — wird bewusst in einen eigenen
  Plan (D) verschoben, nicht Teil des Grundgerüsts.
- **Kein Gerät vorhanden** — Testing bleibt wie bisher Emulator-only
  (Android Studio unter Windows, siehe `CLAUDE.md`).
- **Zwei komplett unterschiedliche Tech-Stacks im selben Repo** (natives
  Kotlin/Compose unter `android/`, Capacitor/WebView unter `ios/` +
  `www/`/`scripts/`) — bewusster Zwischenzustand, da iOS vorerst unverändert
  bleibt. Muss in `CLAUDE.md` klar dokumentiert werden, damit das nicht als
  Inkonsistenz missverstanden wird.
- **Keine Cloud-Backup-Sicherheit in Phase 1** — Datenverlust bei
  App-Deinstallation/Gerätewechsel ist nur über den manuellen XML-Export
  vermeidbar. Nutzer muss das aktiv wissen (Hinweis in der App/Doku).
