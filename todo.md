# Nope-Mode — Build Plan

Spec: [design.md](design.md). Target: Pixel 9 Pro (`caiman`), GrapheneOS,
Android 17 / SDK 37.

**State verified on-device 2026-08-12.** `./gradlew test assembleDebug` is
green: 58 unit tests, 0 failures. APK installs; `HomeActivity` launches without
crashing; `com.piercingxx.nopemode` is still device owner
(`isOrganizationOwnedDevice=true`). The absent `INTERNET` permission is
confirmed against the built APK via `aapt2 dump permissions`.

**What the app currently does: nothing but report its enforcement tier.** Every
pure-logic layer is built and proven. Every layer that touches the platform is
absent — no database, no enforcer, no alarms, no reconcile. Nothing is ever
suspended.

---

## Local setup

`local.properties` is gitignored and must exist before Gradle will run:

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

Build, test, install, launch:

```sh
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.piercingxx.nopemode/.ui.HomeActivity
```

> The installed APK is signed with the launcher's shared debug keystore, so
> `install -r` is an upgrade in place. It does **not** touch device owner —
> provisioning survives reinstalls. Only `clearDeviceOwnerApp()` or a wipe
> drops it, and regaining it costs a factory reset (design §3.2).

---

## Status at a glance

| WS | Scope | State |
|---|---|---|
| 1 | Skeleton — gradle, manifest, packages, icon | **done** |
| 4 | Device admin receiver, `DeviceOwnerManager`, relinquish | **done, provisioned** |
| 3 | Core logic — evaluator, override, break policy, next boundary | **done, 49 tests** |
| 0 | Build correctness fixes | **← start here, small** |
| 2 | Entities **done**; DAOs, `NopeDatabase`, seed migration | **partial** |
| 5 | `SuspendEnforcer`, `suspend_record`, failure surfacing | unbuilt |
| 6 | `AlarmScheduler`, `BootReceiver`, **`reconcile()`** | unbuilt |
| 7 | UI — five screens, break friction | unbuilt |
| 8 | QS tile | unbuilt |
| 11 | Quiet Ringer | unbuilt |
| 9 | `FallbackEnforcer` — listener + accessibility | unbuilt |
| 10 | `BackupJson` **done, 4 tests**; file I/O wiring | **partial** |

**Shortest path to an app that actually blocks something: WS0 → WS2 → WS5 →
WS6.** That sequence is fully headless and is where the product starts
existing. WS7 makes it usable; WS8/9/10/11 make it complete.

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
Re-verified 2026-08-12: `admin=ComponentInfo{com.piercingxx.nopemode/…NopeDeviceAdminReceiver}`,
`testOnlyAdmin=false`.

> Device owner is **non-renewable** — regaining it after relinquishing costs a
> full factory reset. It already cost one wipe to learn that opening the Google
> apps closes the provisioning window.

## WS3 — Core logic ✅ DONE (pure JVM, 49 tests)

All four pure classes are built with zero `android.*` imports, and every gate
listed in the original plan is met by a named test.

- [x] `ScheduleEvaluator.shouldBeActiveAt(now, schedules): Boolean`
- [x] Midnight-crossing windows (`end <= start`), `daysMask` keyed to start day
- [x] `Override` sealed interface with **no unbounded `Break`** (D7)
- [x] `BreakPolicy`: minimum interval, budget, budget reset at window end
- [x] `NopeController.derive(...)` per the §6 truth table
- [x] `NextBoundary.next(...)` — the pure half of WS6's alarm arming
- [x] Tests: normal + wrap windows, boundary minutes 20:00:00 / 08:00:00,
      day-mask across the wrap and across Sunday→Monday, **DST spring-forward
      and fall-back**, full override table, clock-moved-backwards guard,
      interval / budget exhaustion / budget reset

**Gate:** every evaluator branch covered, all green, zero `android.*`. ✅

Two loose ends carried into later workstreams, neither blocking:

- [ ] `BreakPolicy` hardcodes `DEFAULT_MIN_INTERVAL_MINUTES` and
      `DEFAULT_BUDGET` internally. Design §9 requires both to be
      user-configurable — thread them in as parameters when WS7 builds the
      Settings screen, keeping the constants as defaults.
- [ ] `NopeController.derive` converts `now` to an `Instant` via
      `ZoneId.systemDefault()` inline. Harmless today; make the zone an
      injected parameter if a test ever needs to pin it.

---

## WS0 — Build correctness ← start here

Two defects found in review. Both are small and both are worth clearing before
new code lands on top of them.

- [ ] **`java.time` on minSdk 24 with no desugaring.** All of `core/` uses
      `LocalDateTime` / `Instant` / `Duration`, which are API 26+. `minSdk` is
      24 and `app/build.gradle` enables neither core library desugaring nor the
      `desugar_jdk_libs` dependency, so on API 24–25 every one of those classes
      throws `NoClassDefFoundError` at runtime. Harmless on the SDK 37 target
      but it makes the README's "Android 7.0+ (minSdk 24)" claim false. Fix by
      adding to `android { }`:

      ```groovy
      compileOptions {
          coreLibraryDesugaringEnabled true
      }
      ```

      plus `coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'`
      in `dependencies`. Alternatively raise `minSdk` to 26 and correct the
      README — but desugaring is cheap and keeps the stated range honest.
- [ ] **`POST_NOTIFICATIONS` missing from the manifest.** Design §12 lists it
      and WS7's break countdown needs it. Add the `<uses-permission>` now, and
      the API 33+ runtime request alongside the countdown in WS7.
- [ ] Silence the three always-false warnings in `BackupJson.import`. The null
      checks are correct — Gson genuinely can leave a non-null Kotlin field
      null — but Kotlin can't see it. Keep the guards and quiet the compiler:
      annotate the fields nullable in a private DTO and map to `BackupData`
      after validation, so the check is expressible rather than suppressed.
- [ ] Optional: pull `compileSdk` / `targetSdk` to 36 (installed locally).
      Design §16 accepts 34 to match the launcher, so this is a judgement call
      — but three API levels of drift on the one API the product depends on is
      the reason WS5's gate exists. If it stays at 34, run WS5's on-device
      verification before writing any UI on top.
- [ ] Move the inline `xmlns:tools` on the `QUERY_ALL_PACKAGES` element up to
      the `<manifest>` tag. Cosmetic.

**Gate:** `./gradlew test assembleDebug` still green; `aapt2 dump permissions`
shows `POST_NOTIFICATIONS` added and **still no `INTERNET`**.

## WS2 — Data layer (finish it)

The five `@Entity` classes exist and match design §10 exactly. Nothing
references them — there is no `@Database` class, so the app persists nothing.

- [x] Room entities: `blocked_app`, `schedule`, `app_state`, `suspend_record`,
      `break_log` (design §10)
- [ ] `BlockedAppDao` — list (Flow), insert, delete, `deleteByPackage` for the
      uninstall prune in design §14
- [ ] `ScheduleDao` — list (Flow), upsert, delete
- [ ] `AppStateDao` — get the single `id = 1` row, upsert; a `null` row must
      read as `Override.None` rather than crashing a fresh install
- [ ] `SuspendRecordDao` — list, insert-all, delete-by-packages, clear
- [ ] `BreakLogDao` — insert, `breaksSince(millis)` for the budget count,
      `mostRecent()` for the interval check
- [ ] `NopeDatabase : RoomDatabase` — version 1, all five entities, singleton
- [ ] Seed the default schedule via `SeedSchedule.defaultSchedule()` in a
      `RoomDatabase.Callback.onCreate` — **1200 → 480, all days, enabled**
- [ ] Mappers between `AppState`'s string/long columns and the `Override`
      sealed interface, both directions. This is the one place the pure core
      meets storage; unit-test the round-trip including `ForceOn(null)`.
- [ ] Instrumented test: fresh database contains exactly the seed row
- [ ] Export the schema (`room.schemaLocation`) and commit it, so the first
      real migration has a baseline to diff against

**Gate:** a fresh install has the 20:00→08:00 schedule with no user action.

> `AppState.lastReconcileAt` defaults to `System.currentTimeMillis()` at
> construction. Set it explicitly from `reconcile()` in WS6; do not rely on the
> default, or the column records object-creation time rather than reconcile time.

## WS5 — SuspendEnforcer

Where the product starts working. Nothing here exists yet — the `enforce/`
package has not been created.

- [ ] `Enforcer` interface — `apply(desired: Set<String>): Result` where the
      result carries the packages that **failed**, not just a boolean
- [ ] `SuspendEnforcer` via `dpm.setPackagesSuspended(admin, packages, true/false)`
- [ ] Write `suspend_record` **before** suspending (crash safety, design §8.1);
      on release, suspend-false first and clear records only for packages that
      actually released
- [ ] Handle the returned failure array — surface it, never swallow (R8). A
      package the platform refused must never be reported as blocked.
- [ ] `SuspendablePackages` — exclude at **selection** time with an inline
      reason per design §4.1: device admins **including self**, active launcher,
      package installer / uninstaller / verifier, default dialer, permission
      controller
- [ ] Hard-block the **active IME** (a suspended keyboard leaves no way to type
      out of it); require explicit confirmation for the **default SMS handler**,
      never reachable via bulk-select
- [ ] Boot reconcile releases orphans in `suspend_record`
- [ ] Prune uninstalled packages from `blocked_app` and `suspend_record`

**Gate — on-device, and do this before building UI on top:** a blocked app goes
silent, greys out in the launcher, and shows the system paused dialog; releasing
restores it fully. **Verify on SDK 37** with `compileSdk 34` (design §16) —
this is the untested-drift check.

> Verify by hand first, before any UI exists:
> ```sh
> adb shell dumpsys package <pkg> | grep -i suspend
> ```
> A one-off debug entry point that suspends a single hardcoded package is worth
> writing and throwing away here.

## WS6 — Scheduling and `reconcile()`

`reconcile()` is the seam the entire app hangs off and it does not exist in any
form. It is listed inside design §7.1 rather than as its own workstream, which
undersells it: it is the single function that reads state, calls
`NopeController.derive`, drives the `Enforcer`, arms the next alarm, and (from
WS11) sets the zen rule. Build it as its own named unit.

- [ ] `Reconciler.reconcile()` — load schedules + override + blocked list,
      derive, apply via `Enforcer`, persist `lastReconcileAt`, arm next boundary
- [ ] Make it idempotent and safe to call concurrently — a boot broadcast and a
      foregrounded activity can race. Serialize on a single-threaded dispatcher
      or a mutex.
- [ ] Call it from every trigger in design §7.1: boot completed, alarm fired,
      app foregrounded, tile clicked, schedule edited, blocked-list edited,
      break started or expired, `TIME_SET`, `TIMEZONE_CHANGED`
- [ ] `AlarmScheduler` — `setExactAndAllowWhileIdle(RTC_WAKEUP)` for the
      **next boundary only**, from `NextBoundary.next(...)` (already built and
      tested); re-arm after each fire, never pre-schedule a chain
- [ ] Also arm a boundary for a pending `Break`/`ForceOn` expiry — the override
      expiring is a state flip that `NextBoundary` does not model, since it only
      knows about schedules
- [ ] `canScheduleExactAlarms()` false → degrade to `setAndAllowWhileIdle` and
      raise a **persistent** warning (design §7.2, §14)
- [ ] `BootReceiver`: `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` → re-arm
      and reconcile
- [ ] State always derived, never accumulated (D8)

**Gate:** reboot at 02:00 inside the window → apps still suspended on boot.
Set the clock forward across 20:00 with `adb shell su 0 date` or by hand and
confirm the boundary fires once and only once.

*After WS6 the app is fully functional headless.* This is the milestone worth
stopping at to live with for a few days before building UI.

## WS7 — UI

Currently `HomeActivity` renders three TextViews: the app name and the tier
string. The other four screens and `BlockedActivity` do not exist.

- [ ] Home: state, reason ("scheduled until 08:00", "break, 12 min left"),
      master toggle, **Take a break**, blocked count, **enforcement-tier badge**
- [ ] Home must also carry the loud warnings: exact-alarm degraded (WS6),
      DND access not granted (WS11), and any package the platform refused to
      suspend (WS5/R8)
- [ ] Blocked apps: searchable, icons, checkboxes; non-suspendable shown
      disabled **with the reason inline** — never silently absent
- [ ] Schedules: list / add / edit / delete; show the wrap explicitly
      ("20:00 tonight → 08:00 tomorrow") and state which day a `daysMask` bit
      refers to, since both readings are defensible (design §7)
- [ ] Settings: break durations 5/15/30, minimum interval, break budget, Quiet
      Ringer toggles — **editable only while inactive** (design §9), enforced by
      `BreakPolicy.canEditFrictionSettings` which already exists
- [ ] Setup: tier, provisioning command, fallback grants, relinquish + warning
- [ ] `BlockedActivity` interstitial — one word, *Nope.*, the app name, when the
      block lifts
- [ ] Break countdown notification (needs `POST_NOTIFICATIONS` from WS0)
- [ ] AMOLED-black monochrome, matching the launcher

**Gate:** every state in §6 is reachable and legible from the UI.

## WS8 — Quick Settings tile

- [ ] `NopeTileService : TileService` — toggle `ForceOn` / clear
- [ ] Label + state track derived state (`requestListeningState`)
- [ ] Tile click routes through `reconcile()`, not a direct enforcer call

**Gate:** tile reflects state changed from the app, and vice versa.

> Tile *placement* in the shade is a manual user step. `sysui_qs_tiles` is
> vestigial on Android 17 and the real storage is root-only — verified, twice.
> Do not spend effort automating it.

## WS11 — Quiet Ringer (R9)

Independent of WS5/WS9 — different platform mechanism, can be built in
parallel with the enforcement work. Depends on WS6 only for `reconcile()`.

- [ ] `RingerPolicy`: create the `AutomaticZenRule` once, persist its id
- [ ] `ZenPolicy` — calls from **starred contacts only**
- [ ] **Repeat callers** allowed per user toggle (built-in DND category, not
      hand-rolled). Default **on**
- [ ] Rule state driven by the same derived `isActive` inside `reconcile()` —
      no second scheduler
- [ ] `ACCESS_NOTIFICATION_POLICY` grant flow; **loud warning on Home when not
      granted** — a silently inert Quiet Ringer is the worst outcome (R8).
      Never show "active" while the ringer is unrestricted.
- [ ] `removeAutomaticZenRule` on teardown so an uninstall can't strand the
      phone permanently quiet
- [ ] Never write global DND state; own only this rule
- [ ] Warn once at setup if the user has **no starred contacts** (means nothing
      rings — plausible mistake, not necessarily intent)

**Gate:** with Nope-Mode active, a call from a non-starred number does not ring;
a starred contact does; toggling repeat callers changes the second-call
behaviour. Revoking DND access surfaces a visible warning.

> The zen rule governs **the ringer only**. It must never be used to silence app
> notifications generally — the blocked-app list already does that via
> suspension, and widening the rule would quiet apps the user never selected
> (design §18.1).

## WS9 — FallbackEnforcer

Only reachable by relinquishing device owner, which on this device costs a
factory reset to undo. Build it late, and test it on a second device or an
emulator rather than by relinquishing the target.

- [ ] `NopeNotificationListener` — cancel/snooze blocked packages while active
- [ ] `NopeAccessibilityService` — foreground detection → `BlockedActivity`
- [ ] Debounce to prevent launch loops; do **not** use
      `performGlobalAction(GLOBAL_ACTION_BACK)` (design §8.2)
- [ ] Tier selection re-evaluated whenever admin state changes (design §5)
- [ ] UI states plainly that this tier **can let a sound through** before
      suppressing, and therefore does not satisfy R2

**Gate:** with device owner relinquished, blocks still work at the weaker tier.

## WS10 — Backup / restore (finish it)

`BackupJson` is built and round-trip tested. It is not wired to anything — no
file picker, no callers.

- [x] Gson JSON export/import of blocked apps, schedules, and settings
- [x] Round-trip unit test; malformed input rejected without corrupting state
- [ ] Export → `ACTION_CREATE_DOCUMENT`; import → `ACTION_OPEN_DOCUMENT`
- [ ] Import writes to the database in a single transaction, then reconciles
- [ ] Reject a payload whose `version` is newer than `BackupJson.VERSION`
      rather than importing it partially
- [ ] Validate ranges on import: minute-of-day in 0..1439, `daysMask` in
      0..127. A malformed-but-parseable schedule is the dangerous case, and
      `import` currently only guards against missing fields.

**Gate:** export → wipe data → import restores blocked apps and schedules.

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
- Nothing calls an `Enforcer` directly. Everything goes through `reconcile()`,
  or state stops being derived and D8 quietly dies.
