# Nope-Mode — Estate Build Contract

Skippy's scoping decision for the remaining build, authored 2026-08-11. Spec:
[design.md](../design.md). Operator build plan: [todo.md](../todo.md).

## Header — scoping decisions

This contract covers **only what is buildable and provable on this machine** —
a user-local Android toolchain, **no device, no emulator**. The one proven gate
(verified today, exits 0, produces `app/build/outputs/apk/debug/app-debug.apk`):

```
env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon assembleDebug testDebugUnitTest
```

Every `- verify:` line below is a **narrowed** invocation of that same gate —
`testDebugUnitTest --tests '<a named unit-test class>'` with the identical
`JAVA_HOME`/`ANDROID_HOME` env prefix. **No task's verify equals the final gate**
(the full `assembleDebug testDebugUnitTest`); that full command is the marathon's
final gate and nothing else may claim it.

State of the tree as of scoping (verified against `app/src`):

- **WS1 done** — skeleton, manifest, `HomeActivity`, adaptive icon.
- **WS4 done** — `NopeDeviceAdminReceiver`, `DeviceOwnerManager` (incl. relinquish).
- **WS2 partial** — the five Room entities exist (`BlockedApp`, `AppState`,
  `Schedule`, `SuspendRecord`, `BreakLog`). **No DAOs, no `NopeDatabase`, no seed
  migration.** The Room DB wiring is device-gated (instrumented) and is deferred.
  Only the *pure seed-schedule factory* is buildable here.
- **WS3, WS5, WS6, WS7, WS8, WS9, WS10** — not started. No `core/`, `enforce/`,
  `schedule/`, or `service/` packages exist.
- **WS11** — the commit `8bb932d` titled "Add R9 Quiet Ringer" landed only the
  design §18 spec, the todo.md WS11 section, and the five data entities. **No
  `RingerPolicy` code, no `service/` package, no `ACCESS_NOTIFICATION_POLICY`**
  are in the manifest or source. WS11 code is **not** landed; it is deferred.

**Buildable here (JVM unit tests can prove the behavior, zero `android.*` at the
tested layer):** WS3 core logic (the heart of the app), WS10 backup/restore
round-trip, the pure *next-boundary* computation from WS6, and the pure
*seed-schedule factory* from WS2. Everything else — every on-device gate in
todo.md — is OPERATOR work and lives in the Deferred section, never as tasks.

## Rules for this marathon

Honored verbatim from todo.md's cross-cutting rules, plus the toolchain law:

- **Cleanroom only.** Hail, NotiFilter, DetoxDroid, and Curbox are GPL-3.0 or
  otherwise foreign-licensed; this repo is all-rights-reserved. **Read their
  docs, never their source** (design §1).
- **No `INTERNET` permission**, ever — a verifiable claim, checkable with
  `aapt2 dump permissions`.
- Tests gate each workstream. Failing tests means not done.
- **Never report success for a package the platform refused to suspend** (R8).
- Friction settings are editable only while Nope-Mode is inactive, or the 2am
  workaround is simply to raise the break budget.

Toolchain law (this machine):

- A verify **runs behavior** — a named gradle unit-test class via
  `testDebugUnitTest --tests '…'`. It never greps, never reads a file to
  "prove" behavior.
- **No two tasks share a verify command.** Each `- verify:` is a distinct
  `--tests` target.
- **No task's verify equals the final gate.** The full
  `assembleDebug testDebugUnitTest` is reserved for the marathon's final gate.
- Every added code path gets wiring proven by test where a JVM test can prove
  it. Code whose behavior only a device can prove is **deferred**, not faked
  with a weaker check.
- There is **no emulator and no device** on this machine. Instrumented tests
  (`androidTest`) and every on-device gate are operator work. Never substitute a
  weaker check and claim it proves device behavior.
- The tested layer must carry **zero `android.*` imports** (design §3/§5/§15) —
  that is what makes the scheduling logic JVM-provable.

---

## Buildable tasks

### T1 — ScheduleEvaluator: `shouldBeActiveAt` with midnight-crossing windows

`ScheduleEvaluator.shouldBeActiveAt(now: LocalDateTime, schedules: List<Schedule>): Boolean`
— pure, no `android.*`. Windows where `end <= start` wrap into the next day; the
shipped default (1200 → 480) is the common path, not an edge case. `daysMask`
refers to the day the window *starts* (bit 0 = Monday … bit 6 = Sunday). Tests
cover: normal window, midnight-crossing window, exact boundary minutes 20:00:00
and 08:00:00, day-mask boundaries across the wrap, DST spring-forward and
fall-back inside a window, and the clock-moved-backwards guard (design §14).

- verify: `env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.core.ScheduleEvaluatorTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/core/ScheduleEvaluator.kt`, `app/src/test/java/com/piercingxx/nopemode/core/ScheduleEvaluatorTest.kt`

### T2 — Override sealed interface + `NopeController.derive` truth table

`Override` sealed interface: `None`, `ForceOn(until: Instant?)`, `Break(until:
Instant)` — **`Break` has no unbounded variant, by construction** (design §6, D7).
`NopeController.derive(now, schedules, override)` implements the full §6 truth
table: `ForceOn` indefinite vs. expiring, `Break` auto-resume on expiry, and the
clock-moved-backwards guard (a `Break.until` in the future by more than max
duration cancels the break). Pure, no `android.*`.

- verify: `env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.core.NopeControllerTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/core/Override.kt`, `app/src/main/java/com/piercingxx/nopemode/core/NopeController.kt`, `app/src/test/java/com/piercingxx/nopemode/core/NopeControllerTest.kt`

### T3 — BreakPolicy: minimum interval, budget, reset at window end

`BreakPolicy` enforces the anti-bypass rules (design §9): minimum interval
between breaks (default 30 min), break budget (default 3 per active window),
budget **reset at window end**, and all friction settings editable only while
inactive. Pure, no `android.*`. Tests cover interval enforcement, budget
exhaustion, and budget reset.

- verify: `env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.core.BreakPolicyTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/core/BreakPolicy.kt`, `app/src/test/java/com/piercingxx/nopemode/core/BreakPolicyTest.kt`

### T4 — Next-boundary computation (WS6 pure slice)

`NextBoundary.next(now: LocalDateTime, schedules: List<Schedule>): LocalDateTime`
— the pure computation behind `AlarmScheduler`'s "next boundary only; recompute
and re-arm after each fire" rule (design §7.2). Given derived active state, the
next instant at which the active/inactive state flips. Pure, no `android.*`.
Tests cover a boundary inside the current window, the next-day wrap, and a
schedule that is currently inactive.

- verify: `env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.core.NextBoundaryTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/core/NextBoundary.kt`, `app/src/test/java/com/piercingxx/nopemode/core/NextBoundaryTest.kt`

### T5 — Seed-schedule factory (WS2 pure slice)

`SeedSchedule.defaultSchedule()` returns the `Schedule(1200, 480, all-days-mask,
enabled)` that the seed migration must insert (design §10, §7). Pure — a Room
entity is a plain annotated data class and instantiates fine on the JVM. Tests
assert the exact default values: start 20:00, end 08:00, all seven days set,
enabled. The *migration execution* itself is device-gated and deferred.

- verify: `env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.data.SeedScheduleTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/data/SeedSchedule.kt`, `app/src/test/java/com/piercingxx/nopemode/data/SeedScheduleTest.kt`

### T6 — BackupJson: Gson round-trip of blocked apps, schedules, settings

`BackupJson` serializes and restores blocked apps, schedules, and settings as
Gson JSON (design §10, WS10). Pure — operates on the plain data classes, no
`android.*` at the tested layer. Tests cover a full round-trip (export → import
yields identical state) and rejection of malformed input **without corrupting
existing state** (design §14, WS10).

- verify: `env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests 'com.piercingxx.nopemode.data.BackupJsonTest'`
- files: `app/src/main/java/com/piercingxx/nopemode/data/BackupJson.kt`, `app/src/test/java/com/piercingxx/nopemode/data/BackupJsonTest.kt`

---

## Final gate

The marathon is complete when **all six task verifies pass** and the full proven
gate passes on a clean checkout:

```
env JAVA_HOME=$HOME/.local/toolchains/jdk17 ANDROID_HOME=$HOME/.local/toolchains/android-sdk ./gradlew --no-daemon assembleDebug testDebugUnitTest
```

exits 0 and `app/build/outputs/apk/debug/app-debug.apk` exists. This full command
is the final gate only — no task verify may equal it.

---

## Deferred — operator on-device verification (no device/emulator here)

These are todo.md workstreams whose behavior **only a device can prove**. They
are NOT tasks in this marathon. Do not build them here; do not fake them with a
weaker check. Each item states WHAT the operator must verify on-device.

### WS2 — Room database wiring (DAOs, `NopeDatabase`, seed migration execution)
The five entities exist; the DAOs, `NopeDatabase`, and the seed `Migration`
object are missing. Room migrations and DAO behavior need an Android runtime and
are instrumented tests. **Verify on-device:** a fresh install has the 20:00→08:00
schedule with no user action; Room migration runs and the default row is present.

### WS5 — SuspendEnforcer
`setPackagesSuspended`, `suspend_record` crash safety (write record before
suspend), failure-array surfacing (R8), non-suspendable package exclusion at
selection time, active-IME hard-block + default-SMS explicit confirm, and boot
reconcile of orphans in `suspend_record`. **Verify on-device:** a blocked app
goes silent, greys out, and shows the system paused dialog; releasing restores
it fully. **Verify on SDK 37** (design §16). Never report success for a package
the platform refused to suspend.

### WS6 — Android scheduling parts (`AlarmScheduler`, `BootReceiver`)
`setExactAndAllowWhileIdle(RTC_WAKEUP)` next-boundary-only, re-arm after each
fire, exact-alarm degrade + persistent warning, `BOOT_COMPLETED`/`TIME_SET`/
`TIMEZONE_CHANGED`, and `reconcile()` on every trigger. The pure next-boundary
computation is T4 (buildable); the AlarmManager/BroadcastReceiver wiring is not.
**Verify on-device:** reboot at 02:00 inside the window → apps still suspended on
boot.

### WS7 — UI (five screens, `BlockedActivity`, break countdown notification)
Home (state, reason, master toggle, Take a break, blocked count, tier badge),
Blocked apps (searchable, non-suspendable shown disabled with inline reason),
Schedules (list/add/edit/delete, wrap shown explicitly), Settings (break
durations 5/15/30, min interval, budget — locked while active), Setup (tier,
provisioning command, fallback grants, relinquish + warning), `BlockedActivity`
interstitial, break countdown notification, AMOLED-black monochrome. **Verify
on-device:** every state in design §6 is reachable and legible from the UI.

### WS8 — Quick Settings tile
`NopeTileService : TileService` toggling `ForceOn`/clearing it, label + state
tracking derived state via `requestListeningState`. **Verify on-device:** the
tile reflects state changed from the app, and vice versa. Tile *placement* in
the shade stays a manual user step (`sysui_qs_tiles` is vestigial on Android 17).

### WS9 — FallbackEnforcer
`NopeNotificationListener` (cancel/snooze blocked packages while active),
`NopeAccessibilityService` (foreground detection → `BlockedActivity`), debounce
against launch loops, and UI stating plainly that this tier can let a sound
through and therefore does not satisfy R2. **Verify on-device:** with device
owner relinquished, blocks still work at the weaker tier.

### WS11 — Quiet Ringer (R9)
`RingerPolicy` creating the `AutomaticZenRule` once and persisting its id,
`ZenPolicy` with calls from **starred contacts only**, repeat-callers toggle
(built-in DND category), rule state driven by the same derived `isActive` inside
`reconcile()` (no second scheduler), `ACCESS_NOTIFICATION_POLICY` grant flow with
a loud Home warning when not granted, `removeAutomaticZenRule` on teardown, never
writing global DND state, and a one-time warning when the user has no starred
contacts. **Verify on-device:** with Nope-Mode active, a call from a non-starred
number does not ring; a starred contact does; toggling repeat callers changes
second-call behaviour; revoking DND access surfaces a visible warning. The zen
rule governs **the ringer only** — never app notifications generally (design
§18.1).

---

Signed — Skippy, 2026-