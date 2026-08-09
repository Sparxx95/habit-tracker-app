# habit-tracker-app – Native iOS-App (Capacitor) + native Android-App (Kotlin/Compose)

## Projektüberblick

Dieses Repo enthält die native iOS- und Android-App für den
[Habit Tracker](https://github.com/Sparxx95/habit-tracker). Die beiden
Plattformen sind architektonisch unterschiedlich: **iOS** ist weiterhin ein
dünner Capacitor-Wrapper um dieselbe `index.html`, die auch als Web-App via
GitHub Pages läuft. **Android** ist seit 2026-08-09 eine echte native App
(Kotlin, Jetpack Compose, Room) ohne WebView und ohne Capacitor – siehe
Abschnitt "Native Android-App" unten für Details.

**Wichtig (iOS):** `index.html` selbst liegt **nicht** in diesem Repo. Einzige
Quelle der Wahrheit ist `index.html` im Root von
[`Sparxx95/habit-tracker`](https://github.com/Sparxx95/habit-tracker).
`scripts/sync-www.js` lädt sie bei jedem Sync per HTTPS von
`https://raw.githubusercontent.com/Sparxx95/habit-tracker/main/index.html`.
Für Android ist `index.html` irrelevant – dort ist `android/` die alleinige
Quelle der Wahrheit (siehe unten).

## CI-Kopplung

- **iOS:** Ein grüner `test.yml`-Lauf im `habit-tracker`-Repo löst
  automatisch per `repository_dispatch` (Event-Typ
  `habit-tracker-index-updated`) einen Build hier aus – `ios-build.yml`
  reagiert darauf. Zusätzlich manuell auslösbar per `workflow_dispatch`.
  Es gibt keinen `workflow_run`-Trigger mehr (der würde nur innerhalb
  desselben Repos funktionieren) – die Kopplung läuft über das
  `repository_dispatch`-Event, das `habit-tracker`s `test.yml` sendet
  (Secret `HABIT_TRACKER_APP_DISPATCH_TOKEN` dort hinterlegt).
- **Android:** reagiert **nicht** mehr auf `repository_dispatch` (das war ein
  Überbleibsel aus der Capacitor-Zeit, als Android noch `index.html`
  konsumiert hat). `android-build.yml` läuft stattdessen bei `push`/
  `pull_request` mit Änderungen unter `android/**` sowie manuell per
  `workflow_dispatch`.

## Native iOS-App (Capacitor)

- `ios-build.yml` baut bei jedem `repository_dispatch`-Event (und manuell
  per `workflow_dispatch`) einen **unsigned Debug-Build für den
  iOS-Simulator** auf einem macOS-Runner und lädt ihn als Actions-Artifact
  hoch. Kein Apple Developer Account nötig für diesen Schritt.
- `ios-bootstrap.yml` ist ein **einmaliger** manueller Workflow, der das
  native Xcode-Projekt ursprünglich generiert hat. Nach dem Umzug in
  dieses Repo i. d. R. nicht mehr nötig, außer bei komplettem Reset von
  `ios/`.
- **Auf dem echten iPhone testen (aktuell, ohne Dev Account):**
  `ios/App/App.xcworkspace` lokal in Xcode öffnen, mit kostenloser
  Apple-ID als "Personal Team" signieren, per Kabel/WLAN aufs iPhone
  installieren. Signatur ist 7 Tage gültig, danach neu signieren.
- **Sobald ein Apple Developer Account existiert (99$/Jahr, noch nicht
  umgesetzt):** Fastlane + App Store Connect API Key als GitHub Secrets
  ergänzen, `ios-build.yml` um signierten Build + automatischen
  TestFlight-Upload erweitern.
- Repo ist bewusst öffentlich → macOS-Runner-Minuten sind unbegrenzt und
  kostenlos.

## Native Android-App (Kotlin, Jetpack Compose, Room)

- Android ist seit 2026-08-09 eine **echte native App** (kein WebView, kein
  Capacitor mehr) — Kotlin, Jetpack Compose (UI), Room (lokale
  SQLite-Datenbank). Siehe
  `docs/superpowers/specs/2026-08-09-native-android-app-design.md` für die
  vollständige Architektur-Entscheidung.
- **Bewusst rein lokal:** kein Login, kein Firebase, kein Cloud-Sync in der
  Android-App — alle Daten liegen ausschließlich in einer lokalen
  Room-Datenbank auf dem Gerät. Einziger Datenaustauschweg zur Web-App ist
  der manuelle XML-Export/-Import (gleiches Format wie die Web-App).
- `android-build.yml` baut bei jedem `push`/`pull_request` mit Änderungen
  unter `android/**` (und manuell per `workflow_dispatch`) auf einem
  `ubuntu-latest`-Runner Unit-Tests + ein **Debug-APK**
  (`./gradlew assembleDebug`) und lädt es als Actions-Artifact hoch. Kein
  `repository_dispatch`/`npm`/Capacitor-Sync mehr nötig, da die App keinen
  Web-Inhalt mehr lädt.
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

## Entwicklungs-Workflow

- App-Code-Änderungen (`index.html`) passieren ausschließlich im
  `habit-tracker`-Repo, nicht hier.
- Für iOS (Capacitor) gilt weiterhin: Änderungen hier betreffen nur
  natives Verhalten (App-Icon, Splash-Screen, native Permissions,
  Capacitor-Plugins, Build-Konfiguration) — App-Code bleibt in
  `habit-tracker`.
- Für Android (echte native App, kein Capacitor mehr) ist `android/` die
  alleinige Quelle der Wahrheit für UI und Funktionalität — es gibt keinen
  Sync mehr von `index.html`.
- `npm run sync` / `npm run cap:sync:ios` holen sich immer die aktuelle
  `index.html` von `main` in `habit-tracker` — ein lokaler Checkout dieses
  Repos hat sonst keinen Zugriff auf den Web-App-Code (nur für iOS relevant).
