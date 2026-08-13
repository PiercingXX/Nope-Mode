# Nope-Mode — Estate Build 2 Contract (the full remaining build)

Skippy's scoping decision for the **entire** remaining build, authored 2026-08-13.
Spec: [design.md](../design.md). Operator build plan: [todo.md](../todo.md).
This supersedes the deferred-only scope of [estate-build.md](estate-build.md):
**every open workstream WS0–WS11 gets its code written here and unit-proven where
a JVM test can prove it.** "Deferred" now means *deferred verification only* —
the code lands, and the on-device checks are the operator's, off the Deferred
list at the end.

## Header — what this contract covers and what it does not

This is a **marathon contract**: it scopes the whole remaining build into tasks,
each with its own behavior-running verify. It is the plan the operator drops into
the backlog and Skippy executes.

The one proven gate (operator-verified in a prior session; **not re-run in this
scoping session** — see the toolchain note below) is:

```
env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon assembleDebug testDebugUnitTest
```

Every `- verify:` line below is a **narrowed** invocation of that same gate —
`testDebugUnitTest --tests '<a named unit-test class>'` with the identical
`JAVA_HOME`/`ANDROID_HOME` env prefix. **No task's verify equals the final gate**;
the full `assembleDebug testDebugUnitTest` is the marathon's final gate and
nothing else may claim it.

### Toolchain note (verified this session)

The sandbox grants read/write on `/home/piercingxx/.local/android-toolchain`
(containing `jdk17`, `sdk`, `gradle-8.7`). Verified present this session:
`jdk17/bin`, `sdk/platforms/android-34`, `sdk/build-tools/34.0.0`, `sdk/licenses`,
`sdk/cmdline-tools`, `sdk/platform-tools`. **The `local.properties` in the repo
still points at the stale `/home/piercingxx/.local/toolchains/android-sdk`** —
that path is NOT accessible and the build will fail on it unless the
`ANDROID_HOME` env var is set (which overrides it) or `local.properties` is
corrected to `/home/piercingxx/.local/android-toolchain/sdk`. The builder must
fix `local.properties` (or always pass `ANDROID_HOME`) before the first task.
`local.properties` is gitignored, so this does not dirty the tree.

The full gradle gate **could not be re-run in this scoping session**: the
unattended shell allowlist does not permit `./gradlew`. The gate is cited as
operator-proven from the prior session; the builder's first action is to run it
once and confirm it exits 0 before starting task T1.

### State of the tree as of scoping (verified against `app/src` this session)

- **WS1 done** — skeleton, manifest, `HomeActivity`, adaptive icon.
- **WS4 done** — `NopeDeviceAdminReceiver`, `DeviceOwnerManager` (incl. relinquish).
- **WS3 done** — `ScheduleEvaluator`, `Override`, `NopeController`, `BreakPolicy`,
  `NextBoundary` all present with 49 passing tests (verified present on disk).
  The two WS3 loose ends (configurable `BreakPolicy` constants; injected zone in
  `NopeController.derive`) are carried into the WS7 Settings task below.
- **WS2 partial** — the five entities exist (`BlockedApp`, `AppState`, `Schedule`,
  `SuspendRecord`, `BreakLog`) plus `SeedSchedule`. **No DAOs, no `NopeDatabase`,
  no seed migration, no schema export.** The Room DB wiring is device-gated
  (instrumented) and is **deferred verification** here — the code is written, the
  instrumented proof is operator's.
- **WS5, WS6, WS7, WS8, WS9, WS10, WS11** — not started. No `enforce/`,
  `schedule/`, `service/` packages exist. `HomeActivity` is the WS1/WS4 thin
  screen (three TextViews). `BackupJson` exists and is round-trip tested but is
  not wired to any file picker.
- **WS11** — commit `8bb932d` landed only the design §18 spec, the todo WS11
  section, and the five data entities. **No `RingerPolicy`, no `service/`
  package, no `ACCESS_NOTIFICATION_POLICY`** in the manifest or source.

## Rules for this marathon

Honored verbatim from todo.md's cross-cutting rules, plus the toolchain law:

- **Cleanroom only.** Hail, NotiFilter, DetoxDroid, and Curbox are GPL-3.0 or
  otherwise foreign-licensed; this repo is all-rights-reserved. **Read their
  docs, never their source** (design §1).
- **No `INTERNET` permission**, ever — a verifiable claim, checkable with
  `aapt2 dump permissions`.
- Tests gate each workstream. Failing tests means not done.
- **Never report success for a package the platform refused to suspend** (R8).
- Friction settings are editable only while Nope-Mode is inactive (§9).
- Nothing calls an `Enforcer` directly. Everything goes through `reconcile()`,
  or state stops being derived and D8 dies.

Toolchain law (this machine):

- A verify **runs behavior** — a named gradle unit-test class via
  `testDebugUnitTest --tests '…'`. It never greps, never reads a file to
  "prove" behavior.
- **No two tasks share a verify command.** Each `- verify:` is a distinct
  `--tests` target.
- **No task's verify equals the final gate.** The full
  `assembleDebug testDebugUnitTest` is reserved for the marathon's final gate.
- Every added code path gets wiring proven by test where a JVM test can prove
  it. Code whose behavior only a device can prove is **deferred verification**,
  not left unwritten and not faked with a weaker check.
- There is **no emulator and no device** on this machine. Instrumented tests
  (`androidTest`) and every on-device gate are operator work. Never substitute a
  weaker check and claim it proves device behavior.
- The tested layer must carry **zero `android.*` imports** (design §3/§5/§15) —
  that is what makes the scheduling logic JVM-provable. Android-dependent code
  (Room DAOs, AlarmManager, TileService, NotificationListener, Accessibility,
  AutomaticZenRule) is written but its *behavior* is deferred-verified by the
  operator; the pure slices of each are JVM-tested here.

---

## Buildable tasks (code written + JVM-proven here)

### T1 — WS0 build-correctness fixes

The three WS0 build-level items (the fourth WS0 item — the BackupJson warning
fix — is T10's):

- Enable **core library desugaring** in `app/build.gradle`
  (`coreLibraryDesugaringEnabled true` + `coreLibraryDesugaring
  'com.android.tools:desugar_jdk_libs:2.0.4'`) so the `java.time` usage in
  `core/` is valid on minSdk 24 (design §16, WS0).
- Add **`POST_NOTIFICATIONS`** to the manifest (design §12; WS7's break
  countdown needs it).
- Move the inline `xmlns:tools` on `QUERY_ALL_PACKAGES` up to the `<manifest>`
  tag (WS0, cosmetic).
- The BackupJson always-false-warning fix (nullable DTO + map) is **T10's**,
  not this task's — T10 owns the DTO refactor and its validation.

The desugaring and manifest changes are build-level: desugaring is a DEX-time
transform that never runs on host-JVM unit tests, and the manifest entry is not
a unit-test concern, so **no host-JVM test can prove those two directly** — only
the full `assembleDebug` gate (the marathon's final gate) proves them. What a
JVM test *can* prove is the **`java.time` path that desugaring makes valid on
minSdk 24**: the `core/` schedule-boundary code uses `java.time` types
(`LocalTime`, `ZoneId`), and a test that drives those through a real boundary
computation (including a DST transition) would fail if that code were broken.
This task's verify is therefore a **NEW** test class — `DesugaredTimeTest`,
asserting `NopeController.derive`/`NextBoundary` compute correct boundaries
across a DST transition using `java.time` — not the pre-existing
`ScheduleEvaluatorTest`, which is already green and cannot fail for anything T1
changes. The contract states plainly: this test proves the `java.time` path is
correct on the JVM; the desugaring *config* itself is proven only by the final
`assembleDebug` gate.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.core.DesugaredTimeTest'`
- files: `app/build.gradle`, `app/src/main/AndroidManifest.xml`,
  `app/src/test/java/com/piercingxx/nopemode/core/DesugaredTimeTest.kt`

### T2 — WS2 data layer: DAOs, `NopeDatabase`, seed migration, mappers

Write the full WS2 data layer. The five entities and `SeedSchedule` already
exist. Add:

- `BlockedAppDao` — list (Flow), insert, delete, `deleteByPackage`.
- `ScheduleDao` — list (Flow), upsert, delete.
- `AppStateDao` — get the single `id = 1` row, upsert; a `null` row reads as
  `Override.None` rather than crashing a fresh install.
- `SuspendRecordDao` — list, insert-all, delete-by-packages, clear.
- `BreakLogDao` — insert, `breaksSince(millis)`, `mostRecent()`.
- `NopeDatabase : RoomDatabase` — version 1, all five entities, singleton.
- Seed the default schedule via `SeedSchedule.defaultSchedule()` in a
  `RoomDatabase.Callback.onCreate` (1200 → 480, all days, enabled).
- **Mappers** between `AppState`'s string/long columns and the `Override`
  sealed interface, both directions — the one place the pure core meets storage.
- Export the schema (`room.schemaLocation`) and commit it.

The Room DAO/NopeDatabase classes are Android-dependent (they cannot run on the
JVM without Robolectric). The **mappers** are pure and JVM-provable. This task's
verify is a NEW test class covering the `AppState` ↔ `Override` round-trip
including `ForceOn(null)` and a null row reading as `Override.None`. The DAO and
`NopeDatabase` code is written here and its *behavior* is deferred to the
operator's on-device check (fresh install has the 20:00→08:00 seed row).

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.data.OverrideMapperTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/data/BlockedAppDao.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/ScheduleDao.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/AppStateDao.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/SuspendRecordDao.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/BreakLogDao.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/NopeDatabase.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/OverrideMapper.kt`,
  `app/src/test/java/com/piercingxx/nopemode/data/OverrideMapperTest.kt`,
  `app/build.gradle` (schema location)

### T3 — WS5 `SuspendEnforcer`: interface + pure selection/exclusion logic

The `Enforcer` interface and the pure parts of WS5:

- `Enforcer` interface — `apply(desired: Set<String>): Result` where the result
  carries the packages that **failed**, not just a boolean (R8).
- `SuspendablePackages` — the pure exclusion/selection logic per design §4.1:
  given the full installed-package set and the blocked list, compute what is
  suspendable with an **inline reason** per excluded package (device admins
  including self, active launcher, package installer/uninstaller/verifier,
  default dialer, permission controller). Hard-block the active IME; require
  explicit confirmation for the default SMS handler (never reachable via
  bulk-select).

`SuspendEnforcer` itself (the `dpm.setPackagesSuspended` call, `suspend_record`
crash-safety ordering, failure-array surfacing) is Android-dependent — written
here, behavior deferred. The `SuspendablePackages` pure slice is JVM-provable:
a NEW test asserts that self, a device admin, the active launcher, the IME, and
the SMS handler are excluded with reasons, and that a normal third-party app is
included.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.enforce.SuspendablePackagesTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/enforce/Enforcer.kt`,
  `app/src/main/java/com/piercingxx/nopemode/enforce/SuspendablePackages.kt`,
  `app/src/main/java/com/piercingxx/nopemode/enforce/SuspendEnforcer.kt`,
  `app/src/test/java/com/piercingxx/nopemode/enforce/SuspendablePackagesTest.kt`

### T4 — WS6 `Reconciler`: the pure derive-and-diff core

The heart of WS6 — `reconcile()` is the seam the whole app hangs off. The
Android parts (`AlarmScheduler`, `BootReceiver`) are written in T5; this task
builds the **pure reconciliation decision**:

- `Reconciler` — given (now, schedules, override, blocked list, current
  suspend_record, current tier), derive active state via `NopeController.derive`,
  compute the **desired** suspended set, diff against the current record to find
  what to suspend and what to release, and return that plan. Pure, no
  `android.*` — the `Enforcer` is an interface injected in, so the decision is
  testable without a device.
- The plan also computes the **next boundary to arm** (via `NextBoundary.next`)
  and the **override-expiry boundary** (a pending `Break`/`ForceOn` expiry is a
  state flip `NextBoundary` does not model, since it only knows schedules).

A NEW test asserts: idempotence (reconcile twice yields the same plan), the
diff correctly suspends newly-blocked and releases newly-unblocked packages, the
override-expiry boundary is armed alongside the schedule boundary, and a
`ForceOn(null)` (indefinite) arms no schedule boundary but does arm the
override-expiry path.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.schedule.ReconcilerTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/schedule/Reconciler.kt`,
  `app/src/test/java/com/piercingxx/nopemode/schedule/ReconcilerTest.kt`

### T5 — WS6 `AlarmScheduler` + `BootReceiver` (Android wiring, written)

The Android-dependent scheduling parts of WS6, written (behavior deferred):

- `AlarmScheduler` — `setExactAndAllowWhileIdle(RTC_WAKEUP)` for the **next
  boundary only**, from `NextBoundary.next(...)`; re-arm after each fire, never
  pre-schedule a chain. Also arm the pending override-expiry boundary from T4.
  `canScheduleExactAlarms()` false → degrade to `setAndAllowWhileIdle` and raise
  a **persistent** warning (design §7.2, §14).
- `BootReceiver` — `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` → re-arm and
  reconcile.
- Serialize reconcile on a single-threaded dispatcher or mutex (a boot broadcast
  and a foregrounded activity can race).

This task has **no JVM verify of its own** — it is pure Android wiring whose
behavior only a device can prove. Per the marathon rule, a task must carry a
behavior-running verify. Therefore this task's verify is the **state-derivation
test already built in T4** is NOT reused (no two tasks share a verify). Instead,
this task gets a NEW pure test on a small helper: the **degradation decision**
— a pure `AlarmMode` class that, given `canScheduleExactAlarms()`, returns which
AlarmManager method to use and whether to raise the persistent warning. That is
the one JVM-provable slice of the scheduler.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.schedule.AlarmModeTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/schedule/AlarmScheduler.kt`,
  `app/src/main/java/com/piercingxx/nopemode/schedule/BootReceiver.kt`,
  `app/src/main/java/com/piercingxx/nopemode/schedule/AlarmMode.kt`,
  `app/src/test/java/com/piercingxx/nopemode/schedule/AlarmModeTest.kt`,
  `app/src/main/AndroidManifest.xml` (receiver + permissions)

### T6 — WS7 UI: five screens + `BlockedActivity` + break countdown

The full WS7 UI, written. `HomeActivity` grows from the three-TextView thin
screen into the real Home (state, reason "scheduled until 08:00" / "break, 12
min left", master toggle, **Take a break**, blocked count, **enforcement-tier
badge**, and the loud warnings: exact-alarm degraded, DND access not granted,
and any package the platform refused to suspend). Add `BlockedAppsActivity`
(searchable, icons, checkboxes; non-suspendable shown disabled **with the
reason inline**), `SchedulesActivity` (list/add/edit/delete; wrap shown
explicitly "20:00 tonight → 08:00 tomorrow"; which day a `daysMask` bit refers
to), `SettingsActivity` (break durations 5/15/30, min interval, budget, Quiet
Ringer toggles — **editable only while inactive**, enforced by
`BreakPolicy.canEditFrictionSettings`), `SetupActivity` (tier, provisioning
command, fallback grants, relinquish + warning), and `BlockedActivity` (the
one-word *Nope.* interstitial). Break countdown notification (needs
`POST_NOTIFICATIONS` from T1). AMOLED-black monochrome matching the launcher.

This also threads the two WS3 loose ends: `BreakPolicy`'s hardcoded
`DEFAULT_MIN_INTERVAL_MINUTES` / `DEFAULT_BUDGET` become user-configurable
parameters (defaults kept), and `NopeController.derive`'s inline
`ZoneId.systemDefault()` becomes an injected parameter.

The UI is Android-dependent (Views, Activities). The JVM-provable slice is the
**reason string builder** — a pure `HomeStateText` that turns (isActive,
override, next boundary, tier, warning flags) into the exact reason strings the
Home screen renders ("scheduled until 08:00", "break, 12 min left", "exact-alarm
degraded", "DND access not granted", "package X refused"). A NEW test drives it.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.ui.HomeStateTextTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/ui/HomeActivity.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/BlockedAppsActivity.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/SchedulesActivity.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/SettingsActivity.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/SetupActivity.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/BlockedActivity.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/HomeStateText.kt`,
  `app/src/main/java/com/piercingxx/nopemode/core/BreakPolicy.kt`,
  `app/src/main/java/com/piercingxx/nopemode/core/NopeController.kt`,
  `app/src/test/java/com/piercingxx/nopemode/ui/HomeStateTextTest.kt`,
  `app/src/main/res/layout/*`, `app/src/main/res/values/*`

### T7 — WS8 Quick Settings tile

`NopeTileService : TileService` — toggles `ForceOn` / clears it. Label + state
track derived state via `requestListeningState`. Tile click routes through
`reconcile()`, not a direct enforcer call. Written; behavior deferred (tile
reflects state changed from the app and vice versa).

The JVM-provable slice is the **tile state derivation** — a pure function that,
given derived `isActive` and the current override, returns the label and whether
the tile reads as "on". A NEW test covers active/inactive/ForceOn.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.service.TileStateTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/service/NopeTileService.kt`,
  `app/src/main/java/com/piercingxx/nopemode/service/TileState.kt`,
  `app/src/test/java/com/piercingxx/nopemode/service/TileStateTest.kt`,
  `app/src/main/AndroidManifest.xml` (service + `BIND_QUICK_SETTINGS_TILE`)

### T8 — WS11 Quiet Ringer: `RingerPolicy` + `ZenPolicy`

The full WS11 code, written (behavior deferred to on-device):

- `RingerPolicy` — create the `AutomaticZenRule` once, persist its id.
- `ZenPolicy` — calls from **starred contacts only**; repeat callers allowed per
  user toggle (built-in DND category, default **on**).
- Rule state driven by the **same derived `isActive`** inside `reconcile()` — no
  second scheduler.
- `ACCESS_NOTIFICATION_POLICY` grant flow; **loud warning on Home when not
  granted** (R8) — never show "active" while the ringer is unrestricted.
- `removeAutomaticZenRule` on teardown so an uninstall can't strand the phone
  permanently quiet. Never write global DND state; own only this rule.
- Warn once at setup if the user has **no starred contacts**.

The JVM-provable slice is the **`ZenPolicy` builder** — a pure function that,
given (quietRingerEnabled, allowRepeatCallers), returns the `ZenPolicy` config
with calls-from-starred-only and repeat-callers set, and nothing else touched
(§18.1: the rule must not become a general notification filter). A NEW test
asserts the starred-calls-only policy, the repeat-caller toggle on/off, and that
no notification-category fields are set.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.service.ZenPolicyBuilderTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/service/RingerPolicy.kt`,
  `app/src/main/java/com/piercingxx/nopemode/service/ZenPolicyBuilder.kt`,
  `app/src/test/java/com/piercingxx/nopemode/service/ZenPolicyBuilderTest.kt`,
  `app/src/main/AndroidManifest.xml` (`ACCESS_NOTIFICATION_POLICY`)

### T9 — WS9 FallbackEnforcer: `NopeNotificationListener` + `NopeAccessibilityService`

The full WS9 code, written (behavior deferred):

- `NopeNotificationListener` — cancel/snooze blocked packages while active.
- `NopeAccessibilityService` — foreground detection → `BlockedActivity`; debounce
  to prevent launch loops; do **not** use `performGlobalAction(GLOBAL_ACTION_BACK)`
  (design §8.2).
- Tier selection re-evaluated whenever admin state changes (design §5).
- UI states plainly that this tier **can let a sound through** before
  suppressing, and therefore does not satisfy R2.

The JVM-provable slice is the **notification decision** — a pure function that,
given (packageName, active, blocked set, isSuspendable), decides whether to
cancel/snooze and with what duration. A NEW test covers cancel-on-blocked-active,
no-op when inactive or not blocked, and the debounce window.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.service.NotificationDecisionTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/service/NopeNotificationListener.kt`,
  `app/src/main/java/com/piercingxx/nopemode/service/NopeAccessibilityService.kt`,
  `app/src/main/java/com/piercingxx/nopemode/service/NotificationDecision.kt`,
  `app/src/test/java/com/piercingxx/nopemode/service/NotificationDecisionTest.kt`,
  `app/src/main/AndroidManifest.xml` (services + permissions + `res/xml/` configs)

### T10 — WS10 backup/restore wiring + validation

Finish WS10. `BackupJson` is built and round-trip tested. Add:

- **Range validation on import**: minute-of-day in 0..1439, `daysMask` in 0..127.
  A malformed-but-parseable schedule is the dangerous case (WS10). This is the
  pure, JVM-provable slice — a NEW test asserts out-of-range values are rejected
  and a `version` newer than `BackupJson.VERSION` is rejected rather than
  imported partially.
- Export → `ACTION_CREATE_DOCUMENT`; import → `ACTION_OPEN_DOCUMENT` (Android
  wiring, written, behavior deferred).
- Import writes to the database in a single transaction, then reconciles
  (Android wiring, written, behavior deferred).
- Also fix the WS0 always-false warnings in `BackupJson.import` here (nullable
  DTO + map) — **T10 owns the BackupJson DTO refactor and its validation
  outright**; T1's BackupJson item was removed so the two tasks do not overlap.
  T10's verify is the new validation test.

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.data.BackupValidationTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/data/BackupJson.kt`,
  `app/src/main/java/com/piercingxx/nopemode/data/BackupValidator.kt`,
  `app/src/main/java/com/piercingxx/nopemode/ui/BackupActivity.kt` (or wired into
  Settings),
  `app/src/test/java/com/piercingxx/nopemode/data/BackupValidationTest.kt`

---

## Final gate

The marathon is complete when **all ten task verifies pass** and the full proven
gate passes on a clean checkout:

```
env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon assembleDebug testDebugUnitTest
```

exits 0 and `app/build/outputs/apk/debug/app-debug.apk` exists. This full command
is the final gate only — no task verify may equal it. The final gate also proves
the whole tree compiles together (all the WS5/6/7/8/9/10/11 Android code links),
which is the strongest JVM proof available without a device.

---

## Deferred — operator on-device verification (code written here, behavior proven on-device)

These are the on-device checks the operator runs after the marathon. The code
for each is WRITTEN in the tasks above; only the *proof* is deferred. Each item
states WHAT the operator must verify on-device.

### WS2 — Room database wiring (DAOs, `NopeDatabase`, seed migration execution)
Code written in T2. **Verify on-device:** a fresh install has the 20:00→08:00
schedule with no user action; Room migration runs and the default row is present.

### WS5 — SuspendEnforcer
Code written in T3. **Verify on-device:** a blocked app goes silent, greys out,
and shows the system paused dialog; releasing restores it fully. **Verify on SDK
37** (design §16). Never report success for a package the platform refused to
suspend.

### WS6 — Android scheduling parts (`AlarmScheduler`, `BootReceiver`)
Code written in T4/T5. **Verify on-device:** reboot at 02:00 inside the window →
apps still suspended on boot. Set the clock forward across 20:00 and confirm the
boundary fires once and only once.

### WS7 — UI (five screens, `BlockedActivity`, break countdown notification)
Code written in T6. **Verify on-device:** every state in design §6 is reachable
and legible from the UI; break friction settings lock while active; the break
countdown notification posts.

### WS8 — Quick Settings tile
Code written in T7. **Verify on-device:** the tile reflects state changed from
the app, and vice versa. Tile *placement* in the shade stays a manual user step
(`sysui_qs_tiles` is vestigial on Android 17).

### WS9 — FallbackEnforcer
Code written in T9. **Verify on-device:** with device owner relinquished, blocks
still work at the weaker tier; the UI states plainly that this tier can let a
sound through and does not satisfy R2.

### WS10 — Backup / restore
Code written in T10. **Verify on-device:** export → wipe data → import restores
blocked apps and schedules; a newer-version payload is rejected.

### WS11 — Quiet Ringer (R9)
Code written in T8. **Verify on-device:** with Nope-Mode active, a call from a
non-starred number does not ring; a starred contact does; toggling repeat
callers changes second-call behaviour; revoking DND access surfaces a visible
warning. The zen rule governs **the ringer only** — never app notifications
generally (design §18.1).

---

## What this contract does NOT include (explicitly out of scope)

- **No emulator, no device, no instrumented tests run here.** `androidTest`
  files may be written where they help the operator, but they are never the
  verify of a task.
- **No `INTERNET` permission**, ever. The final gate's `aapt2 dump permissions`
  check stays a hard requirement.
- **No tile-placement automation** (WS8) — root-only on Android 17, a manual
  user step.
- **No `CallScreeningService` / `ROLE_CALL_SCREENING`** — rejected in design
  §18.3; starred contacts is the mechanism.
- **No global DND writes** — Nope-Mode owns only its own `AutomaticZenRule`.

---

Signed — Skippy, 2026-08-13.