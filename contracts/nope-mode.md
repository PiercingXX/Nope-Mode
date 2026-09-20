# nope-mode

Operator mill contract materialized from dispatch. Do not invent a different exam.

## Goal

Per root todo.md 'Nope-Mode' section + family theme-sync: (1) P0 honor launcher Custom via BACKGROUND extra (presetForLabel('Custom') currently returns null — no-op); (2) P1 live-repaint Home, not only onResume; (3) P0 protect com.piercingxx.xxclock (and keep dialer/launcher/IME/self) — picker must not offer Clock as suspend target or must warn hard (suspended clock = missed alarms); (4) P1 QS tile runBlocking on main thread (NopeTileService onClick/refresh) — Room under shade can ANR; (5) P1 surface QUERY_ALL_PACKAGES-denied-on-GrapheneOS honestly (empty blocked-app picker). Device QA (device-owner) NOT part of this contract — code-side only.

### T1 — Implement the scoped goal

- files: app/src/main/java/com/piercingxx/nopemode/service/NopeTileService.kt, app/src/main/java/com/piercingxx/nopemode/ui/BackgroundTheme.kt, app/src/main/java/com/piercingxx/nopemode/ui/SchedulesActivity.kt, app/src/main/java/com/piercingxx/nopemode/ui/BlockedAppsActivity.kt, app/src/main/AndroidManifest.xml
- verify: ./gradlew :app:testDebugUnitTest --offline

## Final gate

- verify: ./gradlew testDebugUnitTest --offline
