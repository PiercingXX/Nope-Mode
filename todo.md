# Nope-Mode — Build Plan

Ten workstreams, in dependency order. Each lands with its tests passing.
Spec: [design.md](design.md).

Nothing here is checked. No code exists yet.

---

## WS1 — Project skeleton

- [ ] Gradle wrapper, root + app `build.gradle` mirroring PiercingXX-Launcher
      (AGP 8.5.0, Kotlin 1.9.24, compileSdk 34, minSdk 24, targetSdk 34)
- [ ] `applicationId` / `namespace` = `com.piercingxx.nopemode`
- [ ] viewBinding + buildConfig on; Room/kapt, Gson, coroutines wired
- [ ] Debug keystore checked in (match launcher, one signing identity)
- [ ] Package dirs per design §9
- [ ] Empty `HomeActivity` — builds, installs, launches
- [ ] `.gitignore`, `LICENSE` (all rights reserved)

**Gate:** `./gradlew assembleDebug` succeeds; APK installs on the 9 Pro.

## WS2 — Data layer

- [ ] Room entities: `blocked_app`, `schedule`, `app_state`, `suspend_record`
- [ ] DAOs + `NopeDatabase`
- [ ] Seed migration inserting default schedule **1200 → 480, all days, enabled**
- [ ] Instrumented test: migration runs, default row present

**Gate:** fresh install has the 20:00→08:00 schedule with no user action.

## WS3 — Core logic (pure JVM, no Android)

- [ ] `ScheduleEvaluator.shouldBeActiveAt(now, schedules): Boolean`
- [ ] Midnight-crossing windows (`end <= start`) — **the default case**
- [ ] `daysMask` keyed to the day the window *starts*
- [ ] `ManualOverride` sealed type: `None` / `ForceOn(until?)` / `ForceOff(until)`
- [ ] Derived-state truth table per design §5.4
- [ ] `ForceOff` expires at end of current window — never disables the schedule permanently

**Tests (this is the workstream that must be right):**
- [ ] Normal window, midnight-crossing window, exact boundary minutes
- [ ] Day-mask boundaries across the wrap
- [ ] DST spring-forward and fall-back inside a window
- [ ] Full `ManualOverride` truth table incl. expiry

**Gate:** 100% of evaluator branches covered, all green, zero Android imports.

## WS4 — Device owner

- [ ] `NopeDeviceAdminReceiver : DeviceAdminReceiver` + `res/xml/device_admin.xml`
- [ ] `DeviceOwnerManager`: detect `isDeviceOwnerApp`, expose tier
- [ ] **Relinquish** action → `clearDeviceOwnerApp` (hard requirement, design §2.2)
- [ ] Setup screen shows the exact `dpm set-device-owner` command

> **Provision the phone during this workstream, before any account is added.**
> The window closes permanently once an account exists.

**Gate:** `dumpsys device_policy` shows Nope-Mode as device owner; relinquish works.

## WS5 — SuspendEnforcer

- [ ] `Enforcer` interface; `SuspendEnforcer` via `setPackagesSuspended`
- [ ] Write `suspend_record` **before** suspending (crash safety)
- [ ] Handle the returned "could not suspend" array — surface, never swallow
- [ ] Filter non-suspendable packages at selection time: home app, self, active
      IME, dialer/SMS (explicit confirm only)
- [ ] Boot reconcile un-suspends orphans in `suspend_record`

**Gate:** on-device — a blocked app goes silent and shows the system paused
dialog; releasing restores it fully. Verify on SDK 37 (design §12).

## WS6 — Scheduling

- [ ] `AlarmScheduler` — `setExactAndAllowWhileIdle(RTC_WAKEUP)`, next boundary only
- [ ] Re-arm after each fire; never pre-schedule a chain
- [ ] `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM`; degrade gracefully if denied
- [ ] `BootReceiver`: `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`
- [ ] `reconcile()` called on every trigger in design §5.2
- [ ] State always derived from `shouldBeActiveAt`, never accumulated

**Gate:** reboot at 02:00 inside the window → apps still suspended on boot.

*After WS6 the app is fully functional headless. UI is deliberately later.*

## WS7 — UI

- [ ] Home: state, reason, master toggle, blocked count, **enforcement-tier badge**
- [ ] Blocked apps: searchable, icons, checkboxes; non-suspendable shown
      disabled with reason
- [ ] Schedules: list / add / edit / delete, time pickers + day selector
- [ ] Setup: tier, provisioning command, fallback permission grants, relinquish
- [ ] AMOLED-black monochrome, matching the launcher

**Gate:** every state in §5.4 is reachable and legible from the UI.

## WS8 — Quick Settings tile

- [ ] `NopeTileService : TileService` — toggle `ForceOn` / clear override
- [ ] Label + state stay in sync when changed elsewhere (`requestListeningState`)
- [ ] Place in the shade where the dead Focus Mode tile sat

**Gate:** tile reflects state changed from the app, and vice versa.

## WS9 — FallbackEnforcer

- [ ] `NopeNotificationListener` — cancel/snooze blocked packages while active
- [ ] `NopeAccessibilityService` — foreground detection → `BlockedActivity`
- [ ] Debounce to prevent launch loops
- [ ] UI states plainly that this tier can let a sound through before suppressing

**Gate:** with device owner relinquished, blocks still work at the weaker tier.

## WS10 — Backup / restore

- [ ] Gson JSON export/import of blocked apps + schedules (launcher conventions)
- [ ] Round-trip unit test
- [ ] Import validates and rejects malformed input without corrupting state

**Gate:** export → wipe data → import restores blocked apps and schedules.

---

## Cross-cutting rules

- **Cleanroom only.** Hail, NotiFilter, and DetoxDroid are GPL-3.0; this repo is
  all-rights-reserved. Read their docs, never their source.
- **No `INTERNET` permission**, ever. It is a verifiable privacy claim.
- Tests gate each workstream. A workstream with failing tests is not done.
- Failure surfaces to the user. Never report success for a package the platform
  refused to suspend.
