# nope-mode-p0-contract

Operator mill contract materialized from dispatch. Do not invent a different exam.

## Goal

Write the verify-sectioned contract at contracts/Nope-Mode.md (base branch master @ 04edddc, package com.piercingxx.nopemode). This is a WRITE-THE-FILE-ONLY job: produce the contract file, do not implement the P0s and do not enqueue a mill. The contract must open with a '## State of the tree (measured this session)' section citing file:line for each deliverable, then declare three tasks as '### T1/T2/T3 — ...' sections, each with a '- verify:' bullet that runs behavior and a '- files:' bullet naming the paths it changes, then end with a '## Final gate' section naming ONE command.

Measured tree (verified this session, gate green: ./gradlew --no-daemon testDebugUnitTest --rerun-tasks = BUILD SUCCESSFUL, 235 tests 0 failures 0 errors 0 skipped across 24 suites):
- P0 #1 Custom is a no-op: ThemeSyncReceiver.kt:20-25 documents Custom as 'deliberately a no-op' and EXTRA_BACKGROUND as 'Documented but unread'; onReceive returns early when presetForLabel(name) is null (ThemeSyncReceiver.kt:62-65). ThemeSyncReceiverTest.kt has a test asserting Custom persists nothing (encodes wrong behaviour).
- P0 #2 Home repaints only on resume: BrandActivity.applyTheme() runs only from onCreate/onResume (BrandActivity.kt:26-32); no live broadcast registration.
- P0 #3 xxclock not protected: ProtectedPackages.discover() hard-blocks self, active admins, home launcher, active IME, package installer, default dialer, permission controller (ProtectedPackages.kt:40-80); com.piercingxx.xxclock appears nowhere in the workspace.

Contract task specs:
- T1 Honor launcher Custom via BACKGROUND extra: ThemeSyncReceiver reads xx.launcher.extra.BACKGROUND ARGB when the theme name is unresolvable/Custom; add SettingsStore setCustomBackground/customBackground(): Int? pair; BrandActivity.applyTheme/BackgroundTheme honour a stored custom colour over the named preset; update the ThemeSyncReceiverTest test that asserts Custom persists nothing. verify: ./gradlew --no-daemon testDebugUnitTest --tests com.piercingxx.nopemode.ui.ThemeSyncReceiverTest. files: ThemeSyncReceiver.kt, SettingsStore.kt, BackgroundTheme.kt, BrandActivity.kt, ThemeSyncReceiverTest.kt.
- T2 Live-repaint Home not only onResume: BrandActivity registers a dynamic BroadcastReceiver for xx.launcher.THEME_CHANGED in onResume, unregisters in onPause, calls applyTheme() on receipt. Name the registration method registerLiveThemeRelay() and handler onThemeChanged() so the change is greppable. HomeActivity inherits via BrandActivity. verify: grep -n registerLiveThemeRelay app/src/main/java/com/piercingxx/nopemode/ui/BrandActivity.kt. files: BrandActivity.kt.
- T3 Protect com.piercingxx.xxclock: ProtectedPackages.discover() hard-blocks com.piercingxx.xxclock with a reason (family clock that must stay reachable); keep dialer/launcher/IME/self protections intact. Add JVM test ProtectedPackagesTest driving discover with a mocked Context (MockK already a dependency, app/build.gradle:82) asserting Protections.hardBlocked contains com.piercingxx.xxclock. verify: ./gradlew --no-daemon testDebugUnitTest --tests com.piercingxx.nopemode.enforce.ProtectedPackagesTest. files: ProtectedPackages.kt, ProtectedPackagesTest.kt.

Contract formatting rules (enqueue lint enforces these): every task MUST carry a '- files:' bullet; every '- verify:' is the BARE command and nothing else — no quotes of any kind, no backticks, no prose after it, not wrapped in code fences (a trailing backtick makes the shell die); test selectors written bare, never --tests "quoted"; NO TWO TASKS MAY SHARE A VERIFY (each task's verify must be distinct); no individual task may claim the Final gate command as its verify; the '## Final gate' section names ONE command as a '- verify:' bullet — the repo's own test command ./gradlew --no-daemon testDebugUnitTest. If a task promises a named symbol, its verify must be able to detect that symbol (T2's grep detects registerLiveThemeRelay; T1's test exercises EXTRA_BACKGROUND; T3's test calls discover and asserts xxclock). Do not enqueue a mill; write the contract file only.

### T1 — Write the verify-sectioned contract at contracts/Nope-Mode.md (base branch master @ 04edddc, pack...

- files: app/src/main/java
- verify: ./gradlew :app:testDebugUnitTest --offline

## Final gate

- verify: ./gradlew testDebugUnitTest --offline
