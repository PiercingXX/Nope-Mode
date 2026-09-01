# Nope-Mode — P0 Contract (the shipped deliverable)

Skippy's primary contract for the Nope-Mode deliverable, authored 2026-09-01.
Spec: [design.md](../design.md). Operator build plan: [todo.md](../todo.md).
This is the **P0 contract** — the top-level scoping document for the whole
app, against which every narrower contract ([estate-build.md](estate-build.md),
[estate-build-2.md](estate-build-2.md), [r8-refusal-test.md](r8-refusal-test.md))
is a corrective or scoping refinement.

## Header — base branch and what this contract covers

**This contract builds on base branch `master` @ `04edddc`** ("Hold the
launcher's THEME_SYNC permission so theme changes arrive"). The deliverable is
the complete Nope-Mode Android app: the packages under `app/src/main/java`
that turn selected apps silent and un-openable on a schedule or on demand, for
GrapheneOS, as a cleanroom equivalent of Google's Focus Mode.

The one proven gate for the whole deliverable:

```
./gradlew :app:testDebugUnitTest --offline
```

This is the full unit-test gate the operator runs to confirm the shipped app.
It runs the entire host-JVM test suite (235 unit tests across the `core`,
`data`, `enforce`, `schedule`, `service`, `ui`, and `admin` packages) and fails
if any behavior regresses. It does not grep, does not read a file, and is not
instrumented — the on-device checks stay the operator's, per the Deferred
section.

## Scope — the shipped packages under `app/src/main/java`

The deliverable is the `com.piercingxx.nopemode` package tree. Each package
carries the behavior named here; the pure slices of each are JVM-proven by the
test classes that share their package:

- **`admin/`** — `NopeDeviceAdminReceiver`, `DeviceOwnerManager` (incl.
  relinquish), `RelinquishAction`, `BlockedMessage`. Device-owner provisioning
  and teardown; the Relinquish path releases suspended apps before dropping
  device owner.
- **`core/`** — `ScheduleEvaluator`, `Override`, `NopeController`, `BreakPolicy`,
  `FrictionSettings`, `NextBoundary`. The pure schedule/override/break logic:
  midnight-crossing windows, the §6 truth table, anti-bypass break rules,
  next-boundary computation. Zero `android.*` at this layer.
- **`data/`** — the five Room entities (`BlockedApp`, `AppState`, `Schedule`,
  `SuspendRecord`, `BreakLog`), `NopeDatabase`, five DAOs, `SeedSchedule`,
  `OverrideMapper`, `BackupJson`/`BackupValidator`/`BackupRestorer`,
  `ReconcileStatus`, `SettingsStore`. Backup export/import round-trip and the
  `Override` ↔ storage mapping are JVM-proven.
- **`enforce/`** — `Enforcer`, `SuspendEnforcer`, `SuspendablePackages`,
  `PackagePruner`, `ProtectedPackages`. `setPackagesSuspended` with the R8
  guarantee: **never report success for a package the platform refused to
  suspend.** The refusal path (failed surfacing + record cleanup) is
  JVM-proven by `SuspendEnforcerTest`.
- **`schedule/`** — `Reconciler`, `AlarmScheduler`, `AlarmMode`, `BootReceiver`,
  `BreakStarter`. The reconcile seam everything hangs off, next-boundary-only
  alarm arming, exact-alarm degradation, boot reconcile. The pure reconcile
  decision and the degradation choice are JVM-proven.
- **`service/`** — `NopeTileService`, `NopeNotificationListener`,
  `NopeAccessibilityService`, `RingerPolicy`, `ZenPolicyBuilder`,
  `FallbackGrants`, `BreakNotification`, `NotificationDecision`, `TileState`.
  The fallback tier and Quiet Ringer (DND, starred-contacts-only). Tile state
  and the ZenPolicy builder are JVM-proven.
- **`ui/`** — `HomeActivity`, `BlockedAppsActivity`, `SchedulesActivity`,
  `SettingsActivity`, `SetupActivity`, `BlockedActivity`, `BackupActivity`,
  `AppPicker`, `HomeStateText`, `ScheduleText`, `ThemeSyncReceiver`,
  `BackgroundTheme`. The five screens plus the blocked interstitial and break
  countdown. Reason-string and schedule-text builders are JVM-proven.

## Rules honored by this contract

- **Cleanroom only.** Hail, NotiFilter, DetoxDroid, and Curbox are GPL-3.0 or
  otherwise foreign-licensed; this repo is all-rights-reserved. Read their
  docs, never their source (design §1).
- **No `INTERNET` permission**, ever — a verifiable claim, checkable with
  `aapt2 dump permissions`.
- **Never report success for a package the platform refused to suspend** (R8).
- Nothing calls an `Enforcer` except `reconcileAndApply` (and Relinquish, which
  must release before dropping owner).
- Friction settings are editable only while the product is actually inactive
  (master off **or** derived inactive).

## Verify

- verify: `./gradlew :app:testDebugUnitTest --offline`
- files: `app/src/main/java`

The verify runs the full host-JVM unit-test gate and fails if any of the 235
tests regress. It is the primary gate for the P0 deliverable; the narrower
contracts refine individual behaviors with their own named `--tests` targets.

## Deferred — operator on-device verification (no device/emulator here)

The code is shipped and unit-proven; the on-device behavior checks are the
operator's, unchanged from estate-build-2.md. A fresh install seeds the
20:00→08:00 schedule; a blocked app goes silent, greys out, and shows the
system paused dialog; releasing restores it fully; a reboot at 02:00 inside the
window keeps apps suspended; the tile reflects state changed from the app and
vice versa; with device owner relinquished the fallback tier still blocks; and
with Nope-Mode active a call from a non-starred number does not ring while a
starred contact does (verify on SDK 37, design §16). These are NOT tasks in
this contract — they are the operator's on-device proof.

---

Signed — Skippy, 2026-09-01.