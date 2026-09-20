# Nope-Mode-todo

Operator mill contract materialized from dispatch. Do not invent a different exam.

## Goal

Fix Nope-Mode per root todo.md 'Nope-Mode' section (HEAD 04edddc, package com.piercingxx.nopemode). Permission declare is already correct (uses-only) — keep it. (1) P0: Honor launcher Custom via BACKGROUND extra — presetForLabel('Custom') currently returns null so Custom is a no-op. (2) P1: Live-repaint Home (not only onResume) — BrandActivity.applyTheme() is resume-only; README says it repaints without being opened but foreground Home does not. (3) P0: Protect com.piercingxx.xxclock (and keep dialer/launcher/IME/self); the picker must not offer Clock as a suspend target, or must warn hard — suspended clock = missed alarms. (4) P1: QS tile runBlocking on the main thread (NopeTileService onClick/refresh) — Room under the shade can ANR. (5) P1: QUERY_ALL_PACKAGES denied on GrapheneOS → empty blocked-app picker; surface that honestly. Device-QA items (device-owner only, relinquish works) are NOT part of this contract — code-side work only.

### T1 — Fix Nope-Mode per root todo.md Nope-Mode section (HEAD 04edddc, package com.piercingxx.nopemode)....

- files: app/src/main/java
- verify: ./gradlew :app:testDebugUnitTest --offline

## Final gate

- verify: ./gradlew testDebugUnitTest --offline
