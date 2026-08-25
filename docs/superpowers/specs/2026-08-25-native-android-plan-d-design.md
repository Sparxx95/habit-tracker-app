# Design-Spec: Plan D – Statistik & Dashboard für die native Android-App

**Datum:** 2026-08-25
**Status:** Genehmigt, bereit für Implementierungsplanung

## Ausgangslage

Plan A (Grundgerüst), Plan B (Zeitraster & Streaks) und Plan C (Gruppen)
sind fertig, alle manuell auf dem Emulator bestätigt und nach `main`
gepusht. Laut der Roadmap-Spec vom 2026-08-09 ist **Plan D** als
"Statistik-Screen (Tabelle + Ringdiagramm)" vorgesehen.

**Scope-Erweiterung gegenüber der ursprünglichen Roadmap:** Die Web-App
(`habit-tracker/index.html`) hat inzwischen zwei statistikbezogene Sheets,
nicht nur eines:
- **Statistik** (`fabStats`/`statsBody`, Funktion `renderStats()`):
  Monatsbezogen, Tabelle/Kreis-Umschalter, Tages/Wochen-Umschalter,
  Legende.
- **Dashboard** (`fabDashboard`/`dashboardBody`, Funktion
  `renderDashboard()`): zeitraumübergreifend (kein Monatsbezug),
  Gesamtübersicht-Balken, Wochentags-Muster, Gruppen-Vergleich,
  6-Monats-Trend. Im Web-App-`CLAUDE.md` nicht dokumentiert, aber
  vollständig im Code verdrahtet (eigener FAB, eigenes Sheet).

Mit dem Nutzer abgestimmt: **Plan D deckt beide Screens ab**, für volle
Parität in einem Rutsch statt eines späteren Plans F.

**Wichtiger Befund während der Analyse:** Die native `HabitEntity.id` ist
eine von Room autogenerierte laufende Nummer (`@PrimaryKey(autoGenerate =
true)`), **kein** Erstellungs-Zeitstempel. Die Web-App leitet
`createdDate(habit)` direkt aus `habit.id` ab (dort `id: Date.now()` bei
Anlage) und braucht das für praktisch alle Statistik-Formeln
(`successRate`, `maxStreakEver`, `weekdayPatternData`, `trendData`), um
Tage/Wochen vor der Habit-Erstellung korrekt aus dem Nenner
auszuschließen. Dieses Datum existiert im nativen Schema bisher nicht —
Plan D führt es über eine neue Room-Migration ein (siehe unten).

## Nicht-Ziele (Plan D)

- Kein XML-Backup/-Import (Plan E).
- Kein Login/Firebase/Cloud-Sync (dauerhaft außerhalb des Scopes).
- Keine Änderung an der iOS-Capacitor-Seite.
- Keine Änderung an Zeitraster-/Streak-/Gruppen-Logik aus Plan B/C.
- Kein Pixel-genauer Nachbau der Web-App-Optik generell (siehe
  Grundsatz-Spec vom 2026-08-09) — **Ausnahme:** das Ringdiagramm wird
  bewusst nah am Original umgesetzt (siehe unten), da mit dem Nutzer so
  abgestimmt.

## Architektur

### Navigation & Einstieg

Zwei neue FABs auf `HabitListScreen`, zusätzlich zum bestehenden
Hinzufügen-FAB, gestapelt wie in der Web-App (Backup-FAB kommt erst mit
Plan E):
- **Statistik-FAB** (📊) → `StatsScreen`
- **Dashboard-FAB** (📈) → `DashboardScreen`

Beide Screens ersetzen den Hauptscreen als Vollbild (kein
`ModalBottomSheet`, kein Navigation-Framework nötig — gleiches
manuelles Sealed-State-Muster wie das bestehende Anlegen/Bearbeiten-Sheet
in `MainActivity.kt`, nur ohne Sheet-Overlay), mit Zurück-Pfeil oben links.

### Datenschicht & Migration

`HabitEntity` bekommt ein neues Feld:
```kotlin
val createdAt: Long = 0   // Epoch-Millis, System.currentTimeMillis() bei Anlage
```

`AppDatabase` geht von `version = 2` auf `version = 3`:
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE habits SET createdAt = ? WHERE createdAt = 0", arrayOf(System.currentTimeMillis()))
    }
}
```
Bestehende Zeilen bekommen den Migrationszeitpunkt als `createdAt` (sicherer
Default — Epoch 0 würde bestehende Habits so behandeln, als existierten sie
seit 1970, was ihre Erfolgsquote massiv und falsch nach unten verzerren
würde). `HabitRepository.addHabit(...)` bekommt einen zusätzlichen
`createdAt: Long`-Parameter (Aufrufer übergibt `System.currentTimeMillis()`).

Gleiches additive `ALTER TABLE`-Muster wie `MIGRATION_1_2` aus Plan C,
`.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` in `AppDatabase.getInstance()`.

### Berechnungs-Layer

Neue Datei `util/StatsCalculations.kt` — reine, Room/Compose-unabhängige
Kotlin-Funktionen, 1:1 portiert aus den entsprechenden `index.html`-Funktionen:
- `successRate(habit, doneKeys, today): Int` (aus `successRate()`)
- `maxStreakEver(habit, doneKeys): Int` (aus `maxStreakEver()`)
- `weekdayPatternData(habits, today): List<WeekdayStat>` (aus `weekdayPatternData()`)
- `groupComparisonData(habits, today): List<GroupStat>` (aus `groupComparisonData()`)
- `trendData(habits, today): List<MonthStat>` (aus `trendData()`, rollierende
  letzte 6 Monate)

Alle Funktionen nehmen die bereits vorhandenen, vollständigen
Done-Historien entgegen (`repository.observeHabitsWithDone()` liefert
bereits die komplette Historie, nicht nur den aktuellen Monat — bestätigt
durch die bestehenden `HabitDaoTest`s) statt selbst zu laden.

### ViewModel-Schicht

Zwei getrennte ViewModels, je eines pro Screen (analog zu
`HabitListViewModel`/`HabitEditViewModel` — kein gemeinsames ViewModel, da
Statistik monatsbezogenen Navigations-State braucht, den Dashboard gar
nicht hat, und beide völlig unterschiedliche Aggregate liefern):

**`StatsViewModel`:**
- State: `viewMonth: StateFlow<YearMonth>` (mit `prevMonth()`/`nextMonth()`,
  `nextMonth()` nicht über den aktuellen Monat hinaus — exakt das Muster
  aus `HabitListViewModel`), `freq: StateFlow<"daily"|"weekly">`,
  `mode: StateFlow<"table"|"circle">`
- Abgeleitet: `dailyHabits`/`weeklyHabits` (gefiltert), Tabellen-Zelldaten,
  Kreis-Sektordaten, Wochen-Streifen-Zelldaten, Legenden-Einträge — alles
  `combine(...).stateIn(...)` über `repository.observeHabitsWithDone()` +
  lokalen UI-State, gleiches Muster wie `HabitListViewModel.listDisplay`.

**`DashboardViewModel`:**
- State: `dashMode: StateFlow<"daily"|"weekly"|"all">`
- Abgeleitet: Gesamtübersicht-Balkendaten, Wochentags-Muster-Balkendaten
  (nur bei `daily`/`all`), Gruppen-Vergleichs-Balkendaten, Trend-Balkendaten
  — via `StatsCalculations.kt`-Funktionen kombiniert mit
  `repository.observeHabitsWithDone()`.

### UI: Statistik-Screen

- Toggle-Reihe: Tages/Wöchentlich (`FilterChip`-Paar), darunter
  Tabelle/Kreis (`FilterChip`-Paar), darunter Monatsnavigation (Pfeile +
  Titel, "vorwärts" deaktiviert im aktuellen Monat) — wiederverwendet aus
  `MonthHeader`-Muster.
- **Tabellen-Ansicht** (nur tägliche Habits): Zeilen = Tage des Monats,
  Spalten = Habits, Häkchen/Punkt/leer je Tageszustand
  (erledigt/verpasst/außerhalb Zeitraum) in Habit-Farbe — scrollbares Grid,
  transponierte Variante der bestehenden `DailyGrid`.
- **Kreis-Ansicht** (nur tägliche Habits, nah am Original): neuer
  Composable `RingStatsChart` mit `Canvas`:
  - Jeder Ring-Sektor (ein Tag × ein Habit-Ring) wird per
    `drawArc(color, startAngle, sweepAngle, useCenter = false, style =
    Stroke(width = ringWidth))` gezeichnet — Compose zeichnet
    Ring-Segmente direkt als Bogen-Stroke, ohne die manuelle
    Pfad-Geometrie, die die SVG-Version braucht.
  - Gekrümmte Namens-Labels entlang der Ringe und die Tag-Zahlen außen
    nutzen `drawContext.canvas.nativeCanvas.drawTextOnPath(text, path,
    hOffset, vOffset, paint)` — reguläre `android.graphics.Canvas`-API,
    über `nativeCanvas` erreichbar, entspricht direkt dem SVG-`<textPath>`
    des Originals.
  - Seitliche "Schwanz"-Legende (Farbstreifen + Name je Ring) wird im
    selben Canvas als Rechtecke + Text gezeichnet, an den jeweiligen Ring
    anschließend.
  - Geometrie (290°-Bogen mit 70°-Öffnung, Ringbreite, Polarkoordinaten)
    wird aus `circleSVG()`/`polar()` 1:1 in Kotlin-Trigonometrie
    übernommen.
  - `RingStatsChart` wird generisch über eine Sektorenliste gebaut (Tage
    oder Kalenderwochen), damit sie auch für die Wochen-Kreis-Ansicht
    wiederverwendbar ist (siehe unten) — im Original ist `circleSVG()`
    ebenfalls generisch über `sectors` und wird für beide Fälle
    wiederverwendet (`statsCircleHTML`/`statsWeekCircleHTML`).
- **Wochen-Ansicht** (wöchentliche Habits): wie bei Tages-Habits gibt es
  Tabelle/Kreis als Umschalter:
  - Tabelle-Modus: pro Habit eine Zeile mit KW-Kästchen für die Wochen des
    Monats, Stil wiederverwendet aus `WeekCellButton`.
  - Kreis-Modus: derselbe `RingStatsChart` wie oben, nur mit
    KW-Sektoren statt Tages-Sektoren (Sektor-Label = ISO-Kalenderwoche
    statt Tageszahl) — Portierung von `statsWeekCircleHTML()`.
- **Legende** (Tabellen- und Wochen-Ansicht): Farbpunkt, Name, 🔥-Streak,
  Erfolgsquote je Habit.

### UI: Dashboard-Screen

- Umschalter Täglich/Wöchentlich/Alle oben (`FilterChip`-Reihe).
- **Gesamtübersicht**: horizontale Balken je Habit, absteigend nach
  Erfolgsquote sortiert, Balkenfarbe = Habit-Farbe, Prozent rechts —
  `Box`/`Row` mit `fillMaxWidth(fraction = pct/100f)`, kein Canvas nötig.
- **Wochentags-Muster** (nur bei Alle/Täglich): 7 Balken (Mo–So),
  aggregierte Erfolgsquote je Wochentag über alle täglichen Habits seit
  deren `createdAt` bis heute.
- **Gruppen-Vergleich**: Balken je Gruppe (Durchschnitts-Erfolgsquote),
  nur ab ≥ 2 Gruppen, sonst Platzhaltertext.
- **Verlaufs-Trend (6 Monate)**: vertikale Balken, Höhe proportional zur
  Erfolgsquote, Monatslabel + Prozent darüber — ebenfalls ohne Canvas
  (`Box`-Höhen in einem `Row`).

## Testing

- **Migrations-Test v2→v3:** gleiches dependency-freies Muster wie
  `AppDatabaseMigrationTest` aus Plan C (handgebaute v2-Tabelle,
  `MIGRATION_2_3.migrate(db)` direkt aufgerufen) — prüft Erhalt aller
  Bestandsdaten und den `System.currentTimeMillis()`-Backfill für
  `createdAt`.
- **`StatsCalculationsTest`:** reine JUnit-Tests (kein Robolectric nötig)
  für `successRate`, `maxStreakEver`, `weekdayPatternData`,
  `groupComparisonData`, `trendData` — analog zu `DateUtilsTest`.
- **`StatsViewModelTest`/`DashboardViewModelTest`:** Robolectric +
  In-Memory-Room, gleiches Muster wie `HabitListViewModelTest`.
- **Ringdiagramm (Canvas)** ist per JVM-Unit-Test nicht prüfbar —
  manueller Gerätetest zwingend erforderlich (Lesbarkeit der gekrümmten
  Labels, FAB-Stapel-Layout ohne Überlappung von drei FABs, Scroll-
  Verhalten bei vielen Habits).

## Risiken / offene Punkte

- **Ringdiagramm ist der aufwendigste UI-Teil des gesamten nativen
  Projekts** (wie schon in der Roadmap-Spec vom 2026-08-09 vermerkt) —
  eigener Task, größtes Fehlerrisiko, keine automatisierte
  Rendering-Verifikation in dieser Sandbox möglich.
- **`drawTextOnPath` läuft über `nativeCanvas`**, nicht über die reine
  Compose-`DrawScope`-API — muss vom Implementierer explizit als bewusste,
  dokumentierte Abweichung von "reinem Compose" behandelt werden (ähnlich
  den in Plan C dokumentierten, empirisch verifizierten
  Material3-API-Versions-Abweichungen), nicht als Fehler.
- **Migration v2→v3 mit `UPDATE ... WHERE createdAt = 0` nach einem
  frischen `ALTER TABLE ... DEFAULT 0`:** Reihenfolge der beiden
  `execSQL`-Aufrufe ist wichtig (erst Spalte anlegen, dann befüllen) —
  muss im Implementierungsplan mit exakter SQL vorgegeben werden, nicht
  dem Implementierer überlassen.
- **Dashboard war nicht in der ursprünglichen Plan-D-Abgrenzung der
  Roadmap-Spec vom 2026-08-09** — dieser Design-Spec erweitert den Scope
  bewusst mit dem Nutzer abgestimmt; die Roadmap-Spec selbst wird nicht
  rückwirkend geändert, dieser Spec ist die verbindliche Quelle für Plan D.
