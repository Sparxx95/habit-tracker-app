# Design-Spec: Plan B – Zeitraster & Streaks für die native Android-App

**Datum:** 2026-08-21
**Status:** Genehmigt, bereit für Implementierungsplanung

## Ausgangslage

Plan A hat die native Android-App (Kotlin/Compose/Room) mit einem minimalen
vertikalen Slice fertiggestellt: Habit-Liste mit Heute-Toggle sowie
Anlegen/Bearbeiten-Sheet. Was fehlt, um funktional zur Web-App aufzuschließen,
ist laut Design-Spec vom 2026-08-09 explizit als **Plan B** vorgesehen:
Monats-/KW-Raster mit Navigation, Streak-/Monatsbilanz-Berechnung.

**Ziel dieser Änderung:** Die native App bekommt pro Habit das vollständige
Zeitraster (Monatsraster für tägliche Habits, KW-Streifen für wöchentliche),
inklusive Nachtragen vergangener Tage/Wochen durch Antippen, Monatsnavigation
und 🔥-Streak- sowie Monatsbilanz-Anzeige — funktional und optisch an die
Web-App angelehnt (siehe `habit-tracker/index.html`, Funktionen `streak()`,
`weekStreak()`, `monthWeeks()`, `isoWeek()`/`weekKey()`, `mondayOf()`).

**Rahmenbedingungen (mit Nutzer abgestimmt):**
- Eine bewusste Abweichung von der Web-App: Zukünftige Tage/Wochen sind in
  der nativen App **nicht antippbar** (deaktiviert), nicht nur optisch
  gedimmt wie in der Web-App.
- Layout: volle 1:1-Parität zur Web-App — eine einzige Liste, jede
  Habit-Karte enthält das komplette Raster direkt eingebettet (kein
  separater Detail-Screen pro Habit), mit einer gemeinsamen
  Monatsnavigation oben über der Liste.
- **Weiterhin außerhalb des Scopes** (spätere Pläne bzw. dauerhaft
  außerhalb): Gruppen-Zuweisung/-Filter (Plan C), Statistik-Screen
  (Plan D), XML-Backup (Plan E), Login/Firebase/Cloud-Sync (dauerhaft
  außerhalb).
- Der "Wöchentlich"-Rhythmus, der in Plan A aus dem Anlegen/Bearbeiten-Sheet
  entfernt wurde (weil er keine Semantik hatte), wird in diesem Plan wieder
  eingeführt — jetzt mit voller KW-Streifen-Unterstützung.

## Architektur

### Datenschicht (Room)

Die bisherige `HabitDao.observeHabitsWithDoneFlag(dateKey: String)`-Query
lieferte pro Habit nur ein einzelnes `doneToday`-Flag (SQL-`EXISTS`-Check für
einen Tag). Streak-Berechnung und Monatsraster brauchen dagegen die
**vollständige Done-Historie** pro Habit — analog zum `done`-Objekt, das die
Web-App komplett im Speicher hält.

Ersetzt durch eine Room-`@Relation`-Query:

```kotlin
data class HabitWithDoneEntities(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val doneEntries: List<HabitDoneEntity>
)

@Transaction
@Query("SELECT * FROM habits ORDER BY id")
fun observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>>
```

`HabitRepository` bekommt eine entsprechend delegierende Methode
`observeHabitsWithDone(): Flow<List<HabitWithDoneEntities>>`, die die
bisherige `observeHabitsWithDoneFlag` ersetzt. `toggleDone(habitId, dateKey,
currentlyDone)` bleibt unverändert (wird sowohl vom Heute-Toggle als auch von
den Raster-Zellen aufgerufen).

### Datums-/Streak-Logik (reine Kotlin-Funktionen)

Neue Funktionen in `util/DateUtils.kt`, 1:1 an die Web-App-Logik angelehnt:

```kotlin
fun weekKey(date: LocalDate): String   // "YYYY-Www", ISO-Woche
fun mondayOf(date: LocalDate): LocalDate
fun monthWeeks(year: Int, month: Int): List<LocalDate>   // Montage, die den Monat berühren
fun monthDayCount(year: Int, month: Int): Int
fun firstDayOfWeekOffset(year: Int, month: Int): Int      // Mo=0, für Leerzellen vor Monatsbeginn

fun streak(doneDateKeys: Set<String>, today: LocalDate = LocalDate.now()): Int
fun weekStreak(doneWeekKeys: Set<String>, today: LocalDate = LocalDate.now()): Int
```

`streak()`/`weekStreak()` zählen wie in der Web-App rückwärts ab heute bzw.
der laufenden Woche, wobei der heutige Tag/die laufende Woche offen sein darf,
ohne die Serie zu brechen (siehe `index.html:628-634` und `:651-657`).

Diese Funktionen sind reine Kotlin-Logik ohne Android-Abhängigkeiten und
daher mit normalen JUnit-Tests (ohne Robolectric) testbar.

### ViewModel

`HabitListViewModel` bekommt zusätzlich zum bestehenden `dayKey`-State einen
`viewMonth`-State (Jahr/Monat des angezeigten Monats, analog zu `view.y`/
`view.m` in der Web-App), plus Navigations-Funktionen `prevMonth()`/
`nextMonth()` (letzteres no-op, wenn bereits im aktuellen Kalendermonat).

`habits: StateFlow<List<HabitDisplayState>>` wird aus der Kombination von
`repository.observeHabitsWithDone()` und `viewMonth` berechnet. Für jeden
Habit enthält `HabitDisplayState`:
- Stammdaten (id, name, color, freq)
- `doneToday: Boolean` (aus der vollständigen Done-Liste abgeleitet)
- `streakCount: Int`
- Für `freq == "daily"`: `dayCells: List<DayCell>` (Datum, done, isToday,
  isFuture, isBlank) für den angezeigten Monat, sowie `monthTotal: Int`
- Für `freq == "weekly"`: `weekCells: List<WeekCell>` (Montag-Datum,
  KW-Nummer, done, isCurrentWeek, isFuture) für die Wochen, die den
  angezeigten Monat berühren, sowie `monthTotal: Int`

## UI-Struktur & Interaktion

- **Gemeinsame Monatsnavigation** oben über der `HabitListScreen`-Liste:
  Titel ("August 2026") + ‹/›-Buttons. "›" ist deaktiviert, sobald der
  angezeigte Monat der aktuelle Kalendermonat ist (keine Zukunfts-Navigation,
  analog zu `nextM.disabled = isCurrentMonth` in der Web-App).
- **Habit-Karte (täglich):** wie bisher Name/Farbe/Heute-Toggle, darunter ein
  Untertitel "🔥 5 Tage · 12/21 diesen Monat", darunter ein 7-spaltiges
  Monatsraster (Mo–So-Labels, Leerzellen vor Monatsbeginn, ein Button pro
  Tag).
- **Habit-Karte (wöchentlich):** analog, aber KW-Streifen statt
  Tagesraster ("🔥 3 Wochen · 2/4 diesen Monat"), ein Button pro Woche
  ("KW 29" etc.).
- **Tap-Verhalten:** Zellen ≤ heute (bzw. ≤ aktuelle KW) sind tippbar und
  toggeln über `repository.toggleDone(habitId, dateKey, currentlyDone)`.
  Zellen in der Zukunft sind deaktiviert (bewusste Abweichung von der
  Web-App, siehe Rahmenbedingungen oben).
- Der große Heute-Toggle in der Karte bleibt unverändert in Funktion, wirkt
  jetzt aber auf denselben zugrundeliegenden Datensatz wie das Raster.
- **Anlegen/Bearbeiten-Sheet:** Der "Wöchentlich"-Rhythmus-Chip wird wieder
  eingeführt (in Plan A entfernt, da damals keine Semantik dahinterstand) —
  jetzt mit vollständiger KW-Unterstützung im Raster.

## Testing

- **DAO-Test** für die neue `@Relation`-Query `observeHabitsWithDone()`
  (Room/Robolectric, wie bisher in Task 2 von Plan A).
- **Reine Funktions-Tests** für die neuen `DateUtils`-Helfer (`streak()`,
  `weekStreak()`, `monthWeeks()`, `weekKey()`, `mondayOf()`,
  `firstDayOfWeekOffset()`) — normale JUnit-Tests ohne Robolectric, da reine
  Kotlin-Logik ohne Android-Abhängigkeiten.
- **ViewModel-Test:** Streak-/Monatsbilanz-Berechnung anhand eines
  präparierten Done-Sets prüfen (analog zu Task 3 aus Plan A).
- **UI bleibt "kompiliert erfolgreich"** — volle Compose-Rendering-Tests
  (`createComposeRule` o. Ä.) sind in dieser Sandbox-Umgebung weiterhin
  nicht Teil des Scopes (kein Gerät/Emulator vorhanden), wie bereits in
  Plan A festgelegt.

## Nicht-Ziele (Plan B)

- Kein Gruppen-Feld, keine Gruppen-Filter (Plan C).
- Kein Statistik-Screen (Tabelle/Ringdiagramm) (Plan D).
- Kein XML-Backup/-Import (Plan E).
- Kein Login/Firebase/Cloud-Sync (dauerhaft außerhalb des Scopes).
- Keine Änderung an der iOS-Capacitor-Seite.

## Risiken / offene Punkte

- **`@Relation`-Query lädt die komplette Done-Historie pro Habit bei jeder
  Änderung neu.** Bei den realistischen Datenmengen eines persönlichen
  Habit-Trackers (Dutzende Habits, ein paar hundert Done-Einträge über
  Jahre) ist das unkritisch — sollte sich das als Performance-Problem
  erweisen, wäre eine spätere Optimierung (z. B. Query nur für den
  sichtbaren Zeitraum) ein separates, kleines Follow-up, kein Blocker für
  diesen Plan.
- **Streak-Berechnung ist unabhängig vom angezeigten Monat** (immer
  rückwärts ab heute/aktueller Woche), während das Monatsraster und die
  Monatsbilanz sich auf den gerade *angezeigten* Monat beziehen — das
  entspricht exakt dem Web-App-Verhalten, ist aber eine Feinheit, die beim
  Implementieren nicht durcheinandergebracht werden darf.
