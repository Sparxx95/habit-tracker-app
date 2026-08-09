# habit-tracker-app – Native iOS/Android-Wrapper (Capacitor)

## Projektüberblick

Dieses Repo enthält ausschließlich die native iOS- und Android-App für den
[Habit Tracker](https://github.com/Sparxx95/habit-tracker) – gebaut mit
Capacitor als dünner nativer Wrapper um dieselbe `index.html`, die auch als
Web-App via GitHub Pages läuft.

**Wichtig:** `index.html` selbst liegt **nicht** in diesem Repo. Einzige
Quelle der Wahrheit ist `index.html` im Root von
[`Sparxx95/habit-tracker`](https://github.com/Sparxx95/habit-tracker).
`scripts/sync-www.js` lädt sie bei jedem Sync per HTTPS von
`https://raw.githubusercontent.com/Sparxx95/habit-tracker/main/index.html`.

## CI-Kopplung

- Ein grüner `test.yml`-Lauf im `habit-tracker`-Repo löst automatisch per
  `repository_dispatch` (Event-Typ `habit-tracker-index-updated`) einen
  Build hier aus – `ios-build.yml` und `android-build.yml` reagieren beide
  darauf. Zusätzlich manuell auslösbar per `workflow_dispatch`.
- Es gibt keinen `workflow_run`-Trigger mehr (der würde nur innerhalb
  desselben Repos funktionieren) – die Kopplung läuft über das
  `repository_dispatch`-Event, das `habit-tracker`s `test.yml` sendet
  (Secret `HABIT_TRACKER_APP_DISPATCH_TOKEN` dort hinterlegt).

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

## Native Android-App (Capacitor)

- `android-build.yml` baut bei jedem `repository_dispatch`-Event (und
  manuell per `workflow_dispatch`) auf einem `ubuntu-latest`-Runner ein
  **Debug-APK** (`./gradlew assembleDebug`, automatisch mit dem
  Debug-Keystore signiert und direkt installierbar) und lädt es als
  Actions-Artifact hoch. Kein Play-Store-Konto nötig für diesen Schritt.
- **Lokaler Test-Loop (primärer Weg, da kein Android-Gerät vorhanden ist):**
  Voraussetzung: vorher `npm ci` und `npm run cap:sync:android` ausführen
  — ohne `node_modules/` und ohne Sync schlägt der Gradle-Sync fehl
  (generierte Cordova-Plugin-Ordner sind gitignored und werden erst beim
  Sync erzeugt). Android Studio unter Windows installieren (nicht in
  WSL2 — bessere GPU-Beschleunigung für den Emulator), im SDK-Manager ein
  Android Virtual Device (AVD) anlegen. Nach jedem `npm run
  cap:sync:android` das Projekt `android/` unter Windows in Android
  Studio öffnen (Gradle-Sync läuft automatisch) und "Run" → App startet
  im AVD-Emulator.
- Alternative ohne Android Studio: fertige APK aus dem letzten
  `Android-Build`-Actions-Lauf herunterladen
  (`gh run download <id> -n habit-tracker-android-debug`) und per Drag &
  Drop auf ein laufendes Emulator-Fenster installieren.

## Entwicklungs-Workflow

- App-Code-Änderungen (`index.html`) passieren ausschließlich im
  `habit-tracker`-Repo, nicht hier.
- Änderungen hier betreffen nur natives Verhalten: App-Icon,
  Splash-Screen, native Permissions, Capacitor-Plugins,
  Build-Konfiguration.
- `npm run sync` / `npm run cap:sync:ios` / `npm run cap:sync:android`
  holen sich immer die aktuelle `index.html` von `main` in
  `habit-tracker` — ein lokaler Checkout dieses Repos hat sonst keinen
  Zugriff auf den Web-App-Code.
