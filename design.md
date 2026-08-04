# Nope-Mode — Design Specification

Android app for GrapheneOS. Selected apps go silent and un-openable, on a
schedule or on demand. A cleanroom equivalent of Google's Focus Mode, which
does not exist on GrapheneOS because Digital Wellbeing is not shipped.

**Status:** specification. No code written yet.
**Target device:** Pixel 9 Pro (`caiman`), GrapheneOS, Android 17 / SDK 37.

---

## 1. What it does

- Maintain a user-selected list of **blocked apps**.
- When Nope-Mode is **active**, every blocked app is: no notifications, no
  sound, no vibration, and **cannot be opened**.
- Activation is either **manual** (toggle / quick-settings tile) or
  **scheduled**.
- **Default schedule: 20:00 → 08:00, every day.** Ships enabled.
- Deactivation restores every blocked app to exactly its prior state.

### Non-goals

- No usage analytics, screen-time charts, or streaks.
- No per-app time budgets ("30 min of Discord/day"). Nope-Mode is binary.
- No parental controls, no remote administration, no accounts, no network.
- No blocking of websites or notification *content* filtering by keyword.
- Nothing leaves the device. The app requires no network permission at all.

---

## 2. Decisions

Recorded because each has consequences a reader will otherwise re-litigate.

| # | Decision | Rationale |
|---|---|---|
| D1 | **Device owner is the primary enforcement mechanism** | `setPackagesSuspended` is the only no-root API that makes an app truly silent *and* un-openable, and it survives reboots. Shizuku was rejected: it must be re-armed after every reboot, which silently breaks an unattended overnight schedule. |
| D2 | **NotificationListener + Accessibility is the fallback tier** | Works with zero provisioning on any device. Strictly weaker (see §4.2) but means the app is never useless. |
| D3 | **Blocked apps are un-openable, not just silent** | Full Focus Mode parity, as specified. Note this is a superset of "no noise/no notifications" — suspension delivers both in one call. |
| D4 | **Kotlin + Views, no Compose** | Matches PiercingXX-Launcher exactly (AGP 8.5.0, Kotlin 1.9.24, viewBinding, Room + kapt, Gson, coroutines, compileSdk 34 / minSdk 24 / targetSdk 34). |
| D5 | **Public repo, all rights reserved** | Matches PiercingXX-Launcher. Consequence: **no code may be copied from Hail, NotiFilter, or DetoxDroid — all are GPL-3.0.** Cleanroom only; read their docs, not their source. |
| D6 | **Room for persistence, Gson for backup** | Same stack as the launcher, so backup/restore JSON conventions carry over. |

### 2.1 Provisioning window — time-critical

Device owner can **only** be set on a device with **no accounts added**. As of
this writing the Pixel 9 Pro was freshly factory-reset and reports:

```
accounts        = 0
Device Owner Type = -1   (none)
```

The window is open. It closes the moment a Google account (or any account) is
added, and reopening it costs a **full factory reset**.

Provisioning command, run once over ADB after the APK is installed:

```sh
adb shell dpm set-device-owner com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver
```

**Build and provision Nope-Mode before adding any accounts to the phone.**

### 2.2 Device owner — what it costs

Not free. Document these in the README so the tradeoff is a choice, not a
surprise:

- Device owner is a full MDM role. Settings will show the device as managed.
- **Some apps refuse to run on managed devices** — banking, some DRM video.
  If a needed app breaks, the only fix is relinquishing device owner.
- It cannot be removed by the user through Settings. Nope-Mode **must** expose
  a "Relinquish device owner" action calling
  `DevicePolicyManager.clearDeviceOwnerApp(packageName)`, or the only exit is a
  factory reset. This is a hard requirement, not a nice-to-have.
- Only one device owner can exist. Nope-Mode claims the slot for the life of
  the install.

---

## 3. Architecture

```
              ┌──────────────────────────────┐
              │        NopeController        │  single source of truth:
              │  (active? why? which apps?)  │  decides desired state
              └───────────────┬──────────────┘
                              │ applies via
              ┌───────────────▼──────────────┐
              │      Enforcer (interface)    │
              └───────┬──────────────┬───────┘
                      │              │
        ┌─────────────▼───┐   ┌──────▼──────────────────┐
        │ SuspendEnforcer │   │  FallbackEnforcer       │
        │ (device owner)  │   │  listener + a11y        │
        └─────────────────┘   └─────────────────────────┘
```

`NopeController` never talks to Android APIs directly. It computes *desired
state* — "these packages should be blocked right now" — and hands it to
whichever `Enforcer` is available. This keeps the scheduling logic unit-testable
with no device.

### 3.1 Enforcer selection

At startup and whenever admin state changes:

```
if (devicePolicyManager.isDeviceOwnerApp(packageName)) SuspendEnforcer
else FallbackEnforcer
```

Surface the active tier prominently in the UI. A user running the fallback tier
must know their blocks are leakier.

---

## 4. Enforcement tiers

### 4.1 SuspendEnforcer (device owner) — primary

```kotlin
dpm.setPackagesSuspended(adminComponent, packages, true)   // engage
dpm.setPackagesSuspended(adminComponent, packages, false)  // release
```

Suspension gives all of the following in one call: notifications hidden, no
sound or vibration, activities stopped, no toasts or dialogs, no audio
playback, and a system "app is paused" dialog on launch attempt.

**Returns the packages it could *not* suspend.** Never ignore this array —
surface failures in the UI.

Packages that cannot be suspended, and must be filtered from the selectable
list at the point of selection:

- The current **default launcher / home app** (would brick the home screen).
- **Nope-Mode itself.**
- The active **input method** (would leave the user unable to type).
- The **dialer / default SMS** handler — permitted by the API but a footgun;
  require an explicit confirm, and never allow via bulk-select.
- System-critical packages the platform refuses.

### 4.2 FallbackEnforcer — no device owner

Two cooperating pieces, both requiring a user-granted special access:

**a) `NopeNotificationListener : NotificationListenerService`**
`onNotificationPosted` → if package is blocked and mode is active, call
`cancelNotification(sbn.key)`. Prefer `snoozeNotification(key, duration)` where
it suits, as it suppresses re-post without a user-visible dismissal.

> **Known limitation, must be stated in the UI:** the notification has already
> been posted by the time the listener sees it, so the alert sound may play
> before suppression. This tier delivers "notification disappears", not
> "notification never made a sound". It is strictly inferior to suspension.

**b) `NopeAccessibilityService : AccessibilityService`**
On `TYPE_WINDOW_STATE_CHANGED`, read the foreground package; if blocked and
active, launch `BlockedActivity` (a full-screen "Nope." interstitial) with
`FLAG_ACTIVITY_NEW_TASK`. Debounce to avoid launch loops.

Do **not** attempt `performGlobalAction(GLOBAL_ACTION_BACK)` as the blocking
mechanism — it fights the user's navigation and behaves erratically across
launchers.

---

## 5. Scheduling

### 5.1 Model

A schedule is a recurring daily window:

```kotlin
data class Schedule(
    val id: Long,
    val startMinuteOfDay: Int,   // 20:00 -> 1200
    val endMinuteOfDay: Int,     // 08:00 -> 480
    val daysMask: Int,           // bit 0 = Monday .. bit 6 = Sunday
    val enabled: Boolean,
)
```

**Windows may cross midnight.** `end <= start` means the window wraps to the
next day. The seeded default (1200 → 480) does exactly this, so the wrap case
is the *common* path, not an edge case — test it first.

`daysMask` refers to the day the window **starts**. A Friday-enabled 20:00→08:00
window runs Friday 20:00 through Saturday 08:00.

### 5.2 Reconciliation, not event-chasing

The controller must expose:

```kotlin
fun shouldBeActiveAt(now: LocalDateTime, schedules: List<Schedule>): Boolean
```

— a pure function over the schedule set. **State is always derived by asking
this function, never by accumulating alarm events.** Alarms only prompt a
re-evaluation. This makes the system self-healing: a missed, duplicated, or
late alarm cannot corrupt state.

Call `reconcile()` on: boot completed, alarm fired, app foregrounded, tile
clicked, schedule edited, blocked-app list edited, time/timezone changed.

### 5.3 Alarms

- `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, …)` for the next boundary
  only — recompute and re-arm after each fire. Do not pre-schedule a chain.
- Permission: `USE_EXACT_ALARM` (API 33+). Declare `SCHEDULE_EXACT_ALARM` as
  well for the minSdk 24 range, and handle `canScheduleExactAlarms() == false`
  by degrading to `setAndAllowWhileIdle` plus a visible warning.
- `RECEIVE_BOOT_COMPLETED` → re-arm and reconcile. Without this the schedule
  dies at the first reboot, which is precisely the Shizuku failure mode D1
  rejected.
- Also register `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED`.

### 5.4 Manual override

Manual and scheduled activation are separate inputs to one derived state:

| `manualOverride` | Schedule says | Result |
|---|---|---|
| `None` | inactive | inactive |
| `None` | active | **active** |
| `ForceOn(until?)` | either | **active** |
| `ForceOff(until)` | active | inactive until `until`, then reverts |

`ForceOff` must be time-bounded — it expires at the end of the current window,
so an early exit tonight cannot silently disable the schedule forever. This is
the single most important behavioural guard in the app.

---

## 6. Data model (Room)

```
blocked_app      packageName TEXT PK, addedAt INTEGER
schedule         id INTEGER PK, startMinuteOfDay INT, endMinuteOfDay INT,
                 daysMask INT, enabled INT
app_state        id INTEGER PK (always 1), manualOverride TEXT,
                 overrideUntil INTEGER NULL, lastReconcileAt INTEGER
suspend_record   packageName TEXT PK, suspendedAt INTEGER
```

`suspend_record` is the crash-safety net: it records what Nope-Mode actually
suspended. On boot, any package present there but no longer scheduled to be
blocked gets un-suspended. Without this table, a crash mid-activation can leave
apps permanently suspended with no UI to recover them.

Seed migration inserts the default schedule (1200, 480, all days, enabled).

---

## 7. UI

Four screens, Views + viewBinding, Material 3, matching the launcher's
AMOLED-black monochrome aesthetic.

1. **Home** — big active/inactive state, current reason ("scheduled until
   08:00"), master toggle, count of blocked apps, enforcement-tier badge
   (`Device owner` / `Limited`).
2. **Blocked apps** — searchable list of launchable packages, checkboxes, app
   icons. Non-suspendable packages (§4.1) shown disabled with a reason.
3. **Schedules** — list, edit, add, delete. Default row pre-seeded. Time
   pickers plus a day-of-week selector.
4. **Setup / status** — enforcement tier, provisioning instructions with the
   `dpm` command, permission grants for the fallback tier, and the
   **Relinquish device owner** action (§2.2).

### 7.1 Quick Settings tile

`NopeTileService : TileService` — toggles `ForceOn` / clears override. This
replaces the dead Digital Wellbeing Focus Mode tile in the user's shade layout.
Keep the tile label in sync with derived state, including when changed
elsewhere (`requestListeningState`).

---

## 8. Manifest essentials

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
<!-- deliberately NO android.permission.INTERNET -->
```

- `NopeDeviceAdminReceiver : DeviceAdminReceiver` with
  `<meta-data android:name="android.app.device_admin">` → `res/xml/device_admin.xml`.
- `NopeNotificationListener` with `BIND_NOTIFICATION_LISTENER_SERVICE`.
- `NopeAccessibilityService` with `BIND_ACCESSIBILITY_SERVICE`.
- `NopeTileService` with `BIND_QUICK_SETTINGS_TILE`.
- `BootReceiver` for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`.

Omitting `INTERNET` is a deliberate, verifiable privacy claim — keep it true.

---

## 9. Package layout

```
com.piercingxx.nopemode
├── admin/          NopeDeviceAdminReceiver, DeviceOwnerManager
├── core/           NopeController, ScheduleEvaluator, ManualOverride
├── enforce/        Enforcer, SuspendEnforcer, FallbackEnforcer
├── data/           Room entities, DAOs, NopeDatabase, BackupJson
├── schedule/       AlarmScheduler, BootReceiver
├── service/        NopeNotificationListener, NopeAccessibilityService,
│                   NopeTileService
└── ui/             HomeActivity, BlockedAppsActivity, SchedulesActivity,
                    SetupActivity, BlockedActivity
```

---

## 10. Failure modes

| Scenario | Required behaviour |
|---|---|
| App crashes while apps are suspended | `suspend_record` + boot reconcile restores them |
| Reboot mid-window | `BootReceiver` reconciles; apps re-suspend |
| Exact alarm permission denied | Degrade to inexact, warn visibly, keep reconciling on foreground |
| User selects their own launcher | Blocked at selection time, not at suspend time |
| `setPackagesSuspended` returns failures | Show which apps failed and why; never claim success |
| Device owner relinquished while active | Un-suspend everything first, then drop to fallback tier |
| Blocked app uninstalled | Prune from `blocked_app` and `suspend_record` on reconcile |
| Schedule edited mid-window | Reconcile immediately; may deactivate instantly |
| DST shift inside a window | Minute-of-day comparison on local time; test the 02:00 jump |

---

## 11. Testing

Pure-JVM unit tests carry the weight. `ScheduleEvaluator` has no Android
dependency by design.

- `shouldBeActiveAt` across: normal window, **midnight-crossing window**
  (the default), day-mask boundaries, DST spring-forward and fall-back, exact
  boundary minutes (20:00:00 and 08:00:00).
- `ManualOverride` truth table (§5.4), including `ForceOff` expiry.
- Backup JSON round-trip.
- Instrumented: Room migrations, `suspend_record` recovery after simulated
  crash.

Gate: no workstream is complete while its tests fail.

---

## 12. Build

Mirror PiercingXX-Launcher exactly:

```
AGP 8.5.0 · Kotlin 1.9.24 · compileSdk 34 · minSdk 24 · targetSdk 34
Java/jvmTarget 1.8 · viewBinding · buildConfig
appcompat 1.6.1 · constraintlayout 2.1.4 · material 1.11.0 · preference 1.2.1
room 2.6.1 (+ kapt compiler, room-ktx) · gson 2.10.1 · coroutines 1.7.3
junit 4.13.2 · org.json 20231013 (test) · espresso 3.5.1
```

`applicationId` / `namespace`: `com.piercingxx.nopemode`.
Debug keystore checked in, matching the launcher, so sideloads keep one
signing identity.

> `compileSdk 34` against a device on SDK 37 is fine — but
> `setPackagesSuspended` behaviour should be verified on-device early, since
> the target device is three API levels ahead of the compile target.

---

## 13. Build order

1. **Skeleton** — gradle, manifest, package layout, empty activities. Builds and installs.
2. **Data** — Room entities, DAOs, seed migration with the default 20:00→08:00 schedule. Unit tests.
3. **Core** — `ScheduleEvaluator` + `ManualOverride`. Pure JVM, fully tested. *No Android APIs.*
4. **Device owner** — `NopeDeviceAdminReceiver`, provisioning detection, relinquish action. **Provision on the phone here, before any account is added.**
5. **SuspendEnforcer** — `setPackagesSuspended`, `suspend_record`, failure surfacing.
6. **Scheduling** — `AlarmScheduler`, `BootReceiver`, reconcile-on-everything.
7. **UI** — four screens.
8. **QS tile** — then place it in the shade where the Focus Mode tile used to sit.
9. **FallbackEnforcer** — listener + accessibility. Last, because it is the degraded path.
10. **Backup/restore** — Gson JSON, matching launcher conventions.

Workstreams 1–6 produce a headless but fully functional app. UI is deliberately
late; the scheduling logic is the part that must be right.
