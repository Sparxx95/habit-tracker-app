# Design-Spec: Plan C – Gruppen für die native Android-App

**Datum:** 2026-08-21
**Status:** Genehmigt, bereit für Implementierungsplanung

## Ausgangslage

Plan A (Grundgerüst) und Plan B (Zeitraster & Streaks) sind fertig und
manuell auf dem Emulator bestätigt. Laut der Design-Spec vom 2026-08-09 ist
**Plan C** der nächste Baustein: Gruppen-Zuweisung, Gruppen-Überschriften in
der Liste und Filter-Chips — funktional an die Web-App angelehnt (siehe
`habit-tracker/index.html`, Funktionen/Variablen `filterGroup`,
`namedGroups`, `populateGroupSelect()`, `groupSelectValue()`,
`wireGroupSelect()`, den `render()`-Gruppierungsblock und den
`chips`-Click-Handler).

**Wichtiger Befund:** Das aktuelle `HabitEntity` hat noch **kein**
`group`-Feld — Plan A hat dieses Feld bewusst nicht mitgenommen (Fokus auf
den minimalen vertikalen Slice). Plan C führt es jetzt über eine **echte
Room-Migration** ein (kein `fallbackToDestructiveMigration()`), da bereits
echte Nutzdaten auf dem Gerät existieren. Plan A hat dafür vorgesorgt:
`exportSchema = true` + committetes Schema-JSON existieren bereits genau für
diesen Fall.

**Rahmenbedingungen (mit Nutzer abgestimmt):**
- Gruppen-Zuweisung im Anlegen/Bearbeiten-Sheet als **Dropdown** (1:1 wie
  die Web-App: "Keine Gruppe" + bestehende Gruppennamen + "+ Neue
  Gruppe…", Auswahl von Letzterem blendet ein Textfeld für den neuen Namen
  ein), nicht als Freitext-Autocomplete.
- Migration ist eine echte, additive `ALTER TABLE`-Migration — bestehende
  Habits/Done-Historie bleiben vollständig erhalten, neue Spalte bekommt
  Default `''`.

## Nicht-Ziele (Plan C)

- Kein Statistik-Screen (Plan D).
- Kein XML-Backup/-Import (Plan E).
- Kein Login/Firebase/Cloud-Sync (dauerhaft außerhalb des Scopes).
- Keine Änderung an der iOS-Capacitor-Seite.
- Keine Änderung an Zeitraster-/Streak-Logik (Plan B bleibt unverändert,
  Gruppen sind orthogonal dazu).

## Architektur

### Datenschicht & Migration

`HabitEntity` bekommt ein neues Feld:
```kotlin
val group: String = ""   // "" = keine Gruppe
```

`AppDatabase` geht von `version = 1` auf `version = 2` mit einer expliziten
Migration:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN `group` TEXT NOT NULL DEFAULT ''")
    }
}
```
`group` ist ein reserviertes SQLite-Schlüsselwort (`GROUP BY`) — der
Spaltenname muss in der rohen Migrations-SQL in Backticks stehen, sonst
Syntaxfehler. Der `Room.databaseBuilder(...)`-Aufruf in
`AppDatabase.getInstance()` bekommt `.addMigrations(MIGRATION_1_2)`.

`HabitRepository.addHabit(name, color, freq)` bekommt einen zusätzlichen
`group: String`-Parameter; `updateHabit(habit: HabitEntity)` bleibt
unverändert (nimmt bereits die volle Entity inkl. `group` entgegen).

### ViewModel-Schicht

`HabitDisplayState` (in `HabitListViewModel.kt`) bekommt ein neues Feld
`group: String`. `HabitListViewModel` bekommt:
- `filterGroup: StateFlow<String?>` (`null` = alle Gruppen, analog zu
  `filterGroup` in der Web-App)
- `fun selectGroupFilter(group: String?)`
- Die von der UI benötigte Liste existierender Gruppennamen wird aus den
  bereits geladenen `HabitDisplayState`s abgeleitet (erstes Auftreten,
  ungruppierte zuerst wie in der Web-App) — kein zusätzlicher DB-Query
  nötig, reine Kotlin-Berechnung wie schon bei Streak/Zellen in Plan B.

`HabitEditViewModel` bekommt ein `group: String`-State-Feld (beim Laden
eines bestehenden Habits befüllt, Default `""` bei Neuanlage) und
`onGroupChange(value: String)`. `save()` gibt `group` an
`repository.addHabit`/die aktualisierte `HabitEntity` weiter.

### UI: Anlegen/Bearbeiten-Sheet

Neuer Abschnitt "Gruppe" mit Compose `ExposedDropdownMenuBox`:
- Optionen: "Keine Gruppe" + alle aktuell existierenden Gruppennamen
  (übergeben vom aufrufenden Screen, nicht neu geladen) + "+ Neue
  Gruppe…".
- Wählt man "+ Neue Gruppe…", erscheint ein `OutlinedTextField` für den
  neuen Namen (max. 40 Zeichen, wie die Web-App `maxlength="40"`),
  getrimmt beim Speichern.

### UI: Liste (Gruppen-Überschriften & Filter-Chips)

- Horizontal scrollbare Chip-Reihe unter der Monatsnavigation: "Alle" +
  ein Chip pro existierender Gruppe — nur sichtbar, wenn mindestens eine
  Gruppe existiert (sonst ausgeblendet).
- Tippen auf einen Chip setzt `filterGroup` im ViewModel.
- Ist `filterGroup == null` und existiert mindestens eine benannte Gruppe:
  Gruppen-Überschriften über den jeweiligen Habit-Karten ("Allgemein" für
  ungruppierte, sonst der Gruppenname), Reihenfolge nach erstem Auftreten,
  ungruppiert zuerst — exakt wie `render()` in der Web-App. Bei aktivem
  Gruppenfilter keine Überschriften, nur die gefilterten Karten.

## Testing

- **Migrations-Test:** Rooms `MigrationTestHelper` — prüft, dass v1-Daten
  (Habit + Done-Historie) nach der Migration auf v2 vollständig erhalten
  bleiben und die neue `group`-Spalte den Default-Wert `''` trägt.
- **ViewModel-Test:** Gruppierungs-/Filterlogik (Reihenfolge nach erstem
  Auftreten, ungruppiert zuerst, Filter blendet andere Gruppen aus) anhand
  präparierter `HabitDisplayState`-Listen.
- **UI bleibt** "kompiliert erfolgreich" — keine Compose-Rendering-Tests in
  dieser Sandbox-Umgebung (unverändert seit Plan A/B).

## Risiken / offene Punkte

- **`group` als reserviertes SQL-Schlüsselwort** ist der einzige nicht ganz
  triviale Punkt der Migration — muss in der rohen `ALTER TABLE`-SQL
  konsequent mit Backticks geschrieben werden; Raum-generierte Queries
  (normale `@Insert`/`@Update`/`@Query` über die Entity) quoten
  automatisch, nur die handgeschriebene Migrations-SQL braucht die
  manuelle Backtick-Schreibweise.
- **Erste echte Migration des Projekts** — bisher gab es nur `version = 1`.
  Ein Fehler hier würde beim App-Update auf dem Gerät sichtbar
  crashen (kein `fallbackToDestructiveMigration()`), was als Verhalten
  gewünscht ist (laut, nicht still) — sollte aber sorgfältig gegen die
  echten, bereits im Emulator vorhandenen Testdaten verifiziert werden.
