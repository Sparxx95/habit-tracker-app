# Design-Spec: Plan E – Backup für die native Android-App

**Datum:** 2026-08-30
**Status:** Genehmigt, bereit für Implementierungsplanung

## Ausgangslage

Plan A (Grundgerüst), Plan B (Zeitraster & Streaks), Plan C (Gruppen) und
Plan D (Statistik & Dashboard) sind fertig, alle manuell auf dem Gerät
bestätigt und nach `main` gepusht. Laut der Roadmap-Spec vom 2026-08-09 ist
**Plan E** der letzte Baustein für volle funktionale Parität zur Web-App:
XML-Export/-Import, identisches Format zur Web-App
(`habit-tracker/index.html`, Funktionen `buildXML()`, der
`importFile`-Change-Handler, `updateBackupInfo()`/`markBackupDone()`).

**Wichtiger Befund während der Analyse:** Die Web-App nutzt das
`id`-Attribut im XML als Fallback für das Erstellungsdatum eines Habits
(dort ist `id` selbst `Date.now()` bei Anlage, siehe `createdDate()` in
`index.html`). Die native App hat seit Plan D `id` (Room-Autoincrement)
und `createdAt` (Epoch-Millis) als getrennte Felder. Mit dem Nutzer
abgestimmt: der Import interpretiert das `id`-Attribut als Zahl und setzt
sie als `createdAt` des neu angelegten Habits (Fallback: Importzeitpunkt,
falls keine gültige Zahl) — Room vergibt trotzdem eine eigene neue `id`.
Damit stimmen Erfolgsquote/Serie nach einem Import aus der Web-App sofort.

## Nicht-Ziele (Plan E)

- Kein Login/Firebase/Cloud-Sync (dauerhaft außerhalb des Scopes) — die
  native App hat keinen "Abmelden"-Button, das Backup-Sheet enthält daher
  ausschließlich Backup-Funktionen, anders als das Pendant in der Web-App.
- Keine Änderung an der iOS-Capacitor-Seite.
- Keine Änderung an Zeitraster-/Streak-/Gruppen-/Statistik-Logik aus den
  Plänen B/C/D.
- Kein automatischer/Cloud-Sync-Ersatz — Backup bleibt rein manuell und
  dateibasiert, exakt wie in der Web-App.

## Architektur

### Datenschicht: XML-Serialisierung

Neue reine Kotlin-Datei `util/HabitXml.kt` (kein Room, kein Compose, keine
Coroutines — analog zu `util/StatsCalculations.kt` aus Plan D):

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

- `buildHabitsXml` nutzt `android.util.Xml.newSerializer()` (Teil des
  Android-SDK, keine neue Abhängigkeit). Format identisch zur Web-App:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <habits exported="ISO-Datum" version="2">
    <habit id="…" name="…" color="#…" group="…" freq="daily|weekly">
      <day date="2026-07-19"/>
    </habit>
  </habits>
  ```
  `id`-Attribut = `habit.createdAt` als String. `day`-Elemente sortiert
  nach `dateKey` (String-Sortierung reicht, da `YYYY-MM-DD`/`YYYY-Www`
  beide lexikographisch chronologisch sind).
- `parseHabitsXml` nutzt `android.util.Xml.newPullParser()`. Pro
  `<habit>`-Element: `name` (Fallback `"Unbenannt"`), `color` (Fallback
  `HabitPalette.first()`), `group` (getrimmt, Fallback `""`), `freq`
  (nur exakt `"weekly"` erkannt, sonst `"daily"`), `createdAt` (aus `id`
  als `Long`, Fallback `importedAt` falls kein gültiger Long). `day`-Kinder
  nur übernommen, wenn `date` dem Muster `^\d{4}(-\d{2}-\d{2}|-W\d{2})$`
  entspricht, sonst verworfen (exakt die Web-App-Validierung, siehe
  `index.html`-Import-Handler).
- Ungültiges XML (Parser-Fehler) oder keine `<habit>`-Elemente wirft eine
  Exception mit sprechender Meldung — der Aufrufer (ViewModel) fängt das
  und zeigt sie an, kein stiller Fehlschlag.

### Datenschicht: Repository-Erweiterung

`HabitRepository` bekommt eine neue Methode (für den Export reicht das
bereits vorhandene `observeHabitsWithDone().first()`, keine zusätzliche
Repository-Methode nötig):

```kotlin
suspend fun replaceAllHabits(imported: List<ParsedHabit>)
```

`replaceAllHabits` läuft in einer `@Transaction`-DAO-Methode: alle
bestehenden Habits löschen (kaskadiert automatisch zu `habit_done` durch
das bereits vorhandene `onDelete = CASCADE`), dann alle importierten
Habits + ihre `HabitDoneEntity`-Einträge einfügen. Schlägt mitten im
Import etwas fehl, bleibt durch die Transaktion der alte Stand erhalten.

### Persistenz: letztes Backup

Einfacher Zeitstempel (Epoch-Millis) über `SharedPreferences`
(`context.getSharedPreferences("backup_meta", MODE_PRIVATE)`,
Key `"lastBackupAt"`) — für einen einzelnen Wert ist Room hier
überdimensioniert, SharedPreferences ist der idiomatische Android-Weg.

### ViewModel-Schicht

`BackupViewModel` bleibt reine Daten-/Business-Logik ohne
Android-Framework-Abhängigkeiten außer `ViewModel`/`viewModelScope`
(Share-Intent und Dateiauswahl-Launcher leben im Composable, siehe UI
unten):

```kotlin
class BackupViewModel(
    private val repository: HabitRepository,
    private val prefs: SharedPreferences
) : ViewModel() {
    val lastBackupText: StateFlow<String>
    suspend fun buildExportXml(): String
    suspend fun importXml(xml: String): Int   // gibt Anzahl importierter Habits zurück, wirft bei Fehler
    fun markBackupDone()
    suspend fun hasAnyHabits(): Boolean        // true wenn mind. 1 Habit vorhanden (steuert Ersetzen-Bestätigung)
}
```

`lastBackupText` berechnet "noch kein Backup" / "heute" / "gestern" /
"vor n Tagen" aus dem gespeicherten Zeitstempel — exakte Logik aus
`updateBackupInfo()` in der Web-App übernommen (`days = 0` → "heute",
`days = 1` → "gestern", sonst `"vor " + days + " Tagen"`).

### UI: Backup-Sheet & Navigation

Vierter FAB (💾, wie in der Web-App) im bestehenden Stapel auf
`HabitListScreen` (Dashboard/Statistik/Hinzufügen/Backup). Öffnet ein neues
`BackupSheet` als `ModalBottomSheet` — gleiches Muster wie das bestehende
Anlegen/Bearbeiten-Sheet (Overlay über der Liste, kein neuer
`AppScreen`-Navigationszustand nötig, da es kein Vollbild-Screen ist).

Inhalt: "Exportieren"-Button, "Importieren"-Button,
"Datensicherung · letztes Backup: …"-Text. Kein "Abmelden"-Button (siehe
Nicht-Ziele).

**Export:** Button-Klick baut den XML-String über `BackupViewModel`,
schreibt ihn in eine temporäre Datei im Cache-Verzeichnis
(`habits-backup-<Datum>.xml`), erzeugt eine `content://`-URI über einen
neuen `FileProvider`-Eintrag im Manifest und startet einen
`ACTION_SEND`-Share-Intent (`type = "application/xml"`). Direkt nach dem
Start des Share-Intents (nicht erst nach einem Activity-Result, das
Share-Intents oft nicht zuverlässig liefern) wird
`viewModel.markBackupDone()` aufgerufen — exakt wie die Web-App, wo schon
der Klick auf Exportieren als "Backup gemacht" zählt.

**Import:** Button startet `ActivityResultContracts.OpenDocument()` mit
MIME-Typ-Filtern (`application/xml`, `text/xml`, `*/*` als Fallback, da
XML auf Android nicht immer korrekt registriert ist). Liest die Datei über
die zurückgegebene URI, ruft `viewModel.importXml(...)` auf. Vor dem
tatsächlichen Ersetzen: Compose-`AlertDialog` ("Backup enthält N
Gewohnheit(en). Aktuelle Daten ersetzen?"), **außer** die App ist aktuell
komplett leer (`hasAnyHabits() == false`) — dann wird ohne Nachfrage
direkt importiert, exakt wie in der Web-App. Fehler (ungültiges XML, leere
Datei) werden als Fehlermeldung im Sheet angezeigt, kein Datenverlust.

### Manifest-Änderung

Einziger Manifest-Eingriff dieses Plans: `FileProvider`-Deklaration
(Pflicht seit Android 7 für `file://`-URIs an andere Apps) plus eine neue
`res/xml/file_paths.xml`, die das Cache-Verzeichnis freigibt.

## Testing

- **`HabitXmlTest`:** reine JUnit-Tests (kein Robolectric) für
  `buildHabitsXml`/`parseHabitsXml` — Rundtrip (Export → Import → gleiche
  Daten), Sonderzeichen-Escaping in Namen/Gruppen, ungültiges XML wirft
  Exception, fehlendes/ungültiges `id`-Attribut fällt auf `importedAt`
  zurück, ungültige `day`-Daten werden verworfen, `freq` außer `"weekly"`
  wird zu `"daily"`.
- **`BackupViewModelTest`:** Robolectric + In-Memory-Room, gleiches Muster
  wie die bisherigen ViewModel-Tests — `importXml` ersetzt bestehende
  Daten korrekt in einer Transaktion (alte Habits + deren `habit_done`
  vollständig weg, neue vollständig da), `createdAt`-Mapping aus dem
  `id`-Attribut, `lastBackupText`-Berechnung für alle Fälle (kein Backup /
  heute / gestern / n Tage).
- **Kein UI-Test für `BackupSheet`** (Share-Intent/Dateiauswahl-Launcher
  sind in dieser Sandbox nicht automatisiert testbar) — manueller
  Gerätetest zwingend erforderlich, wie bei den bisherigen Sheets/Screens.

## Risiken / offene Punkte

- **Share-Intent-Zuverlässigkeit:** Es gibt keine verlässliche Rückmeldung,
  ob der Nutzer den Share-Dialog abgeschlossen oder abgebrochen hat (wie
  bei der Web-App auch) — `markBackupDone()` wird beim Start des Intents
  aufgerufen, nicht bei dessen Abschluss. Bewusste, mit der Web-App
  konsistente Vereinfachung, kein Implementierungsfehler.
- **`FileProvider`-Konfiguration** ist die einzige Manifest-/Ressourcen-
  Änderung im gesamten nativen Projekt bisher — sorgfältig gegen die
  tatsächliche `applicationId` (`com.tatoli.habittracker`) verifizieren,
  da die Provider-`authority` typischerweise `<applicationId>.fileprovider`
  lautet und bei Tippfehlern der Export beim ersten manuellen Test mit
  einer `FileUriExposedException` abstürzt statt beim Kompilieren
  aufzufallen.
- **Kein Gerät vorhanden** — wie bei allen bisherigen Plänen bleibt der
  eigentliche Test von Share-Intent und System-Dateiauswahl
  Emulator-/Gerätetest, nicht in dieser Sandbox verifizierbar.
