# Nope-Mode — P0 Contract

Base branch `master` @ `04edddc` ("Hold the launcher's THEME_SYNC permission so
theme changes arrive"), package `com.piercingxx.nopemode`.

This is a WRITE-THE-FILE-ONLY contract: it declares the P0 deliverables and
their gates. It does not implement the P0s and does not enqueue a mill — the
tasks below are the work to be done against the measured tree, each with its
own `- verify:` and `- files:` bullets.

## State of the tree (measured this session)

Measured 2026-09-01. Gate green: `./gradlew --no-daemon testDebugUnitTest
--rerun-tasks` = BUILD SUCCESSFUL, 235 tests, 0 failures, 0 errors, 0 skipped
across 24 suites. The three P0s below are the remaining work.

- **P0 #1 — Custom is a no-op.** `ThemeSyncReceiver.kt:20-25` documents
  "Custom" (and any other unrecognised name) as "deliberately a no-op" and
  `EXTRA_BACKGROUND` as "Documented but unread"; `onReceive` returns early when
  `presetForLabel(name)` is null (`ThemeSyncReceiver.kt:66-68`), so a launcher
  Custom theme persists nothing. `ThemeSyncReceiverTest.kt:131-138` has a test
  (`a Custom theme persists nothing`) asserting exactly that — it encodes the
  wrong behaviour and must be updated.
- **P0 #2 — Home repaints only on resume.** `BrandActivity.applyTheme()` runs
  only from `onCreate`/`onResume` (`BrandActivity.kt:28-35`); there is no live
  broadcast registration, so a theme change while Home is foregrounded is not
  repainted until the next resume.
- **P0 #3 — xxclock not protected.** `ProtectedPackages.discover()`
  (`ProtectedPackages.kt:48-95`) hard-blocks self, active admins, home
  launcher, active IME, package installer, default dialer, and permission
  controller (`ProtectedPackages.kt:52-82`); `com.piercingxx.xxclock` appears
  nowhere in the workspace, so the family clock is not protected from
  suspension.

## T1 — Honor launcher Custom via BACKGROUND extra

`ThemeSyncReceiver` reads `xx.launcher.extra.BACKGROUND` ARGB when the theme
name is unresolvable/Custom; add `SettingsStore.setCustomBackground` /
`customBackground(): Int?` pair; `BrandActivity.applyTheme`/`BackgroundTheme`
honour a stored custom colour over the named preset; update the
`ThemeSyncReceiverTest` test that asserts Custom persists nothing.

- verify: ./gradlew --no-daemon testDebugUnitTest --tests com.piercingxx.nopemode.ui.ThemeSyncReceiverTest
- files: app/src/main/java/com/piercingxx/nopemode/ui/ThemeSyncReceiver.kt, app/src/main/java/com/piercingxx/nopemode/data/SettingsStore.kt, app/src/main/java/com/piercingxx/nopemode/ui/BackgroundTheme.kt, app/src/main/java/com/piercingxx/nopemode/ui/BrandActivity.kt, app/src/test/java/com/piercingxx/nopemode/ui/ThemeSyncReceiverTest.kt

## T2 — Live-repaint Home not only onResume

`BrandActivity` registers a dynamic BroadcastReceiver for
`xx.launcher.THEME_CHANGED` in `onResume`, unregisters in `onPause`, and calls
`applyTheme()` on receipt. Name the registration method `registerLiveThemeRelay()`
and the handler `onThemeChanged()` so the change is greppable. `HomeActivity`
inherits via `BrandActivity`.

- verify: grep -n registerLiveThemeRelay app/src/main/java/com/piercingxx/nopemode/ui/BrandActivity.kt
- files: app/src/main/java/com/piercingxx/nopemode/ui/BrandActivity.kt

## T3 — Protect com.piercingxx.xxclock

`ProtectedPackages.discover()` hard-blocks `com.piercingxx.xxclock` with a
reason (family clock that must stay reachable); keep dialer/launcher/IME/self
protections intact. Add a JVM test `ProtectedPackagesTest` driving `discover`
with a mocked Context (MockK already a dependency, `app/build.gradle:82`)
asserting `Protections.hardBlocked` contains `com.piercingxx.xxclock`.

- verify: ./gradlew --no-daemon testDebugUnitTest --tests com.piercingxx.nopemode.enforce.ProtectedPackagesTest
- files: app/src/main/java/com/piercingxx/nopemode/enforce/ProtectedPackages.kt, app/src/test/java/com/piercingxx/nopemode/enforce/ProtectedPackagesTest.kt

## Final gate

- verify: ./gradlew testDebugUnitTest --offline