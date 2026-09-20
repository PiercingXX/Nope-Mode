# nope-mode-corrective

Permission declare is already uses-only. Do not touch AndroidManifest.xml.
Do not touch SchedulesActivity.kt. Device-owner QA is out of scope.

## Goal

(1) Honor launcher Custom via BACKGROUND extra — presetForLabel('Custom') currently returns null so Custom is a no-op. (2) Live-repaint Home, not only onResume. (3) Protect com.piercingxx.xxclock (keep dialer/launcher/IME/self). (4) QS tile runBlocking on the main thread in NopeTileService. (5) QUERY_ALL_PACKAGES denied on GrapheneOS shows an empty blocked-app picker — surface that honestly.

### T1 — Honor launcher Custom via BACKGROUND extra

ThemeSyncReceiver reads xx.launcher.extra.BACKGROUND ARGB when presetForLabel returns null/Custom; SettingsStore gains setCustomBackground / customBackground(); BackgroundTheme and BrandActivity honour a stored custom colour. Update ThemeSyncReceiverTest `a Custom theme persists nothing`. New test ThemeSyncCustomTest drives onReceive with EXTRA_BACKGROUND and a Custom name and asserts the ARGB is persisted.

- files: app/src/main/java/com/piercingxx/nopemode/ui/ThemeSyncReceiver.kt, app/src/main/java/com/piercingxx/nopemode/data/SettingsStore.kt, app/src/main/java/com/piercingxx/nopemode/ui/BackgroundTheme.kt, app/src/main/java/com/piercingxx/nopemode/ui/BrandActivity.kt, app/src/test/java/com/piercingxx/nopemode/ui/ThemeSyncReceiverTest.kt, app/src/test/java/com/piercingxx/nopemode/ui/ThemeSyncCustomTest.kt
- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.nopemode.ui.ThemeSyncCustomTest

### T2 — Live-repaint Home not only onResume

BrandActivity registers a dynamic BroadcastReceiver for xx.launcher.THEME_CHANGED in onResume, unregisters in onPause, and calls applyTheme() on receipt. Name the registration method registerLiveThemeRelay() so the change is greppable. HomeActivity inherits via BrandActivity. New test ThemeRelayTest calls registerLiveThemeRelay by name.

- files: app/src/main/java/com/piercingxx/nopemode/ui/BrandActivity.kt, app/src/test/java/com/piercingxx/nopemode/ui/ThemeRelayTest.kt
- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.nopemode.ui.ThemeRelayTest

### T3 — Protect com.piercingxx.xxclock

ProtectedPackages.discover() hard-blocks com.piercingxx.xxclock with a missed-alarm reason; keep dialer/launcher/IME/self protections. JVM test ProtectedPackagesTest asserts Protections.hardBlocked contains com.piercingxx.xxclock.

- files: app/src/main/java/com/piercingxx/nopemode/enforce/ProtectedPackages.kt, app/src/test/java/com/piercingxx/nopemode/enforce/ProtectedPackagesTest.kt
- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.nopemode.enforce.ProtectedPackagesTest

### T4 — QS tile Room off the main thread

NopeTileService onClick/refresh call runBlocking on the main thread. Extract a named suspendable TileLoader whose suspend fun load(...) reads the app-state and schedule DAOs and returns TileSnapshot; NopeTileService calls it from a coroutine scope off the main thread. Add kotlinx-coroutines-test if missing. New test TileLoaderTest drives TileLoader.load via runTest.

- files: app/src/main/java/com/piercingxx/nopemode/service/NopeTileService.kt, app/src/main/java/com/piercingxx/nopemode/service/TileLoader.kt, app/src/test/java/com/piercingxx/nopemode/service/TileLoaderTest.kt, app/build.gradle
- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.nopemode.service.TileLoaderTest

### T5 — Honest empty blocked-app picker

QUERY_ALL_PACKAGES denied on GrapheneOS returns empty from queryIntentActivities. Extract named AppPickerEmptyState that, given the launchable-app count, returns whether the picker is empty and the explanatory message; BlockedAppsActivity renders it. New test AppPickerEmptyStateTest drives an empty list.

- files: app/src/main/java/com/piercingxx/nopemode/ui/AppPickerEmptyState.kt, app/src/main/java/com/piercingxx/nopemode/ui/BlockedAppsActivity.kt, app/src/test/java/com/piercingxx/nopemode/ui/AppPickerEmptyStateTest.kt
- verify: ./gradlew :app:testDebugUnitTest --offline --tests com.piercingxx.nopemode.ui.AppPickerEmptyStateTest

## Final gate

- verify: ./gradlew testDebugUnitTest --offline
