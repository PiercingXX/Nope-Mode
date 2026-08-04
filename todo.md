# Nope-Mode — Build Plan

Eleven workstreams in dependency order. Each lands with its tests passing.
Spec: [design.md](design.md).

WS1 and WS4 are done and live on the device. **WS3 is the highest-risk
remaining workstream** — pure JVM, no Android dependencies, build it against
tests first and in isolation.

WS11 (Quiet Ringer) is independent of WS5/WS9 — it uses a different platform
mechanism and can be built in parallel with the enforcement work.

---

## WS1 — Project skeleton ✅ DONE

- [x] Gradle wrapper, root + app `build.gradle` mirroring PiercingXX-Launcher
      (AGP 8.5.0, Kotlin 1.9.24, compileSdk 34, minSdk 24, targetSdk 34)
- [x] `applicationId` / `namespace` = `com.piercingxx.nopemode`
- [x] viewBinding + buildConfig; Room/kapt, Gson, coroutines wired
- [x] Debug keystore shared with the launcher (one signing identity)
- [x] Package dirs per design §13
- [x] `HomeActivity` — builds, installs, launches
- [x] `.gitignore`, `LICENSE`, adaptive icon

**Gate:** `./gradlew assembleDebug` succeeds; APK installs. ✅

## WS4 — Device owner ✅ DONE (provisioned 2026-08-03)

- [x] `NopeDeviceAdminReceiver` + `res/xml/device_admin.xml`
- [x] `DeviceOwnerManager`: `isDeviceOwnerApp`, tier reporting
- [x] **Relinquish** → `clearDeviceOwnerApp` (hard requirement, design §3.1)
- [x] Provisioning command surfaced in-app

**Gate:** `dumpsys device_policy` shows Nope-Mode as device owner. ✅
Verified: `admin=ComponentInfo{com.piercingxx.nopemode/…NopeDeviceAdminReceiver}`,
`testOnlyAdmin=false`.

> Device owner is **non-renewable** — regaining it after relinquishing costs a
> full factory reset. It already cost one wipe to learn that opening the Google
> apps closes the provisioning window.

---

## WS2 — Data layer

- [ ] Room entities: `blocked_app`, `schedule`, `app_state`, `suspend_record`,
      `break_log` (design §10)
- [ ] DAOs + `NopeDatabase`
- [ ] Seed migration inserting the default schedule **1200 → 480, all days, enabled**
- [ ] Instrumented test: migration runs, default row present

**Gate:** a fresh install has the 20:00→08:00 schedule with no user action.

## WS3 — Core logic (pure JVM, no Android) ← highest risk

- [ ] `ScheduleEvaluator.shouldBeActiveAt(now, schedules): Boolean`
- [ ] Midnight-crossing windows (`end <= start`) — **the default, not an edge case**
- [ ] `daysMask` keyed to the day the window *starts*
- [ ] `Override` sealed interface: `None` / `ForceOn(until?)` / `Break(until)`
- [ ] `Break` has **no unbounded variant** — enforced by the type (design §6, D7)
- [ ] `BreakPolicy`: minimum interval, break budget, budget reset at window end
- [ ] `NopeController.derive(...)` per the §6 truth table

**Tests — this is the workstream that must be right:**
- [ ] Normal window; **midnight-crossing window**; exact boundary minutes
      (20:00:00 and 08:00:00)
- [ ] Day-mask boundaries across the wrap
- [ ] DST spring-forward and fall-back inside a window
- [ ] Full override truth table incl. `ForceOn` and `Break` expiry
- [ ] Clock-moved-backwards guard (design §14)
- [ ] `BreakPolicy`: interval enforcement, budget exhaustion, budget reset

**Gate:** every evaluator branch covered, all green, **zero `android.*` imports**.

## WS5 — SuspendEnforcer

- [ ] `Enforcer` interface; `SuspendEnforcer` via `setPackagesSuspended`
- [ ] Write `suspend_record` **before** suspending (crash safety, design §8.1)
- [ ] Handle the returned failure array — surface it, never swallow (R8)
- [ ] Exclude non-suspendable packages at **selection** time, with inline
      reasons: device admins incl. self, active launcher, package
      installer/uninstaller/verifier, default dialer, permission controller
- [ ] Hard-block the **active IME**; explicit confirm for **default SMS**
- [ ] Boot reconcile releases orphans in `suspend_record`

**Gate:** on-device — a blocked app goes silent, greys out, and shows the system
paused dialog; releasing restores it fully. **Verify on SDK 37** (design §16).

## WS6 — Scheduling

- [ ] `AlarmScheduler` — `setExactAndAllowWhileIdle(RTC_WAKEUP)`, next boundary only
- [ ] Re-arm after each fire; never pre-schedule a chain
- [ ] `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM`; degrade + warn if denied
- [ ] `BootReceiver`: `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`
- [ ] `reconcile()` on every trigger in design §7.1
- [ ] State always derived, never accumulated (D8)

**Gate:** reboot at 02:00 inside the window → apps still suspended on boot.

*After WS6 the app is fully functional headless.*

## WS7 — UI

- [ ] Home: state, reason, master toggle, **Take a break**, blocked count,
      **enforcement-tier badge**
- [ ] Blocked apps: searchable, icons, checkboxes; non-suspendable shown
      disabled **with the reason inline**
- [ ] Schedules: list / add / edit / delete; show the wrap explicitly
      ("20:00 tonight → 08:00 tomorrow")
- [ ] Settings: break durations 5/15/30, minimum interval, break budget —
      **editable only while inactive** (design §9)
- [ ] Setup: tier, provisioning command, fallback grants, relinquish + warning
- [ ] `BlockedActivity` interstitial
- [ ] Break countdown notification
- [ ] AMOLED-black monochrome, matching the launcher

**Gate:** every state in §6 is reachable and legible from the UI.

## WS8 — Quick Settings tile

- [ ] `NopeTileService : TileService` — toggle `ForceOn` / clear
- [ ] Label + state track derived state (`requestListeningState`)

**Gate:** tile reflects state changed from the app, and vice versa.

> Tile *placement* in the shade is a manual user step. `sysui_qs_tiles` is
> vestigial on Android 17 and the real storage is root-only — verified, twice.
> Do not spend effort automating it.

## WS9 — FallbackEnforcer

- [ ] `NopeNotificationListener` — cancel/snooze blocked packages while active
- [ ] `NopeAccessibilityService` — foreground detection → `BlockedActivity`
- [ ] Debounce to prevent launch loops
- [ ] UI states plainly that this tier **can let a sound through** before
      suppressing, and therefore does not satisfy R2

**Gate:** with device owner relinquished, blocks still work at the weaker tier.

## WS10 — Backup / restore

- [ ] Gson JSON export/import of blocked apps, schedules, and settings
- [ ] Round-trip unit test
- [ ] Import validates and rejects malformed input without corrupting state

**Gate:** export → wipe data → import restores blocked apps and schedules.

## WS11 — Quiet Ringer (R9)

- [ ] `RingerPolicy`: create the `AutomaticZenRule` once, persist its id
- [ ] `ZenPolicy` — calls from **starred contacts only**
- [ ] **Repeat callers** allowed per user toggle (built-in DND category, not
      hand-rolled). Default **on**
- [ ] Rule state driven by the same derived `isActive` inside `reconcile()` —
      no second scheduler
- [ ] `ACCESS_NOTIFICATION_POLICY` grant flow; **loud warning on Home when not
      granted** — a silently inert Quiet Ringer is the worst outcome (R8)
- [ ] `removeAutomaticZenRule` on teardown so an uninstall can't strand the
      phone permanently quiet
- [ ] Never write global DND state; own only this rule
- [ ] Warn once at setup if the user has **no starred contacts** (means nothing
      rings — plausible mistake, not necessarily intent)
- [ ] Settings: Quiet Ringer master toggle, repeat-callers toggle — editable
      only while inactive

**Gate:** with Nope-Mode active, a call from a non-starred number does not ring;
a starred contact does; toggling repeat callers changes the second-call
behaviour. Revoking DND access surfaces a visible warning.

> The zen rule governs **the ringer only**. It must never be used to silence app
> notifications generally — the blocked-app list already does that via
> suspension, and widening the rule would quiet apps the user never selected
> (design §18.1).

---

## Cross-cutting rules

- **Cleanroom only.** Hail, NotiFilter, DetoxDroid, and Curbox are GPL-3.0 or
  otherwise foreign-licensed; this repo is all-rights-reserved. **Read their
  docs, never their source** (design §1).
- **No `INTERNET` permission**, ever — a verifiable claim, checkable with
  `aapt2 dump permissions`.
- Tests gate each workstream. Failing tests means not done.
- **Never report success for a package the platform refused to suspend** (R8).
- Friction settings are editable only while Nope-Mode is inactive, or the 2am
  workaround is simply to raise the break budget.
