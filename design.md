# Nope-Mode — Design Specification

Android app for GrapheneOS. Selected apps go silent and un-openable, on a
schedule or on demand.

A cleanroom equivalent of Google's Focus Mode, which does not exist on
GrapheneOS because Digital Wellbeing is not shipped and cannot be installed
from Play.

**Status:** Shipped — device-owner suspend, fallback listener + accessibility, schedules, picker, tile, Take a break, Relinquish, Quiet Ringer (DND; see §18.2), JSON backup, R8 warnings. compileSdk 35 / targetSdk 35 / minSdk 24.
**Target:** Pixel 6 / Pixel 9 Pro, GrapheneOS.
**Provisioned:** device owner is live on the target device as of 2026-08-03.

---

## 1. Cleanroom provenance

This repository is **all rights reserved**. Every close prior art is
**GPL-3.0**. Copying from them would force this project to GPL.

**What was studied:** F-Droid listings, README files, published feature
descriptions, user-facing behavior, and official Android API documentation.

**What was never opened:** the source code of Hail, NotiFilter, DetoxDroid,
Curbox Detox, or Open TimeLimit.

Behavioral *ideas* are not copyrightable; expression is. What follows is
derived from documented behavior and the public Android SDK. Anyone extending
this project must hold the same line: **read their docs, never their source.**

### Prior art and what each contributed

| Project | License | What was taken (behavior only) |
|---|---|---|
| Google Focus Mode | proprietary | The target behavior itself: pick distracting apps, pause them, schedule it, and offer a **bounded** "take a break" (5/15/30 min). Grayed-out icons as the paused signal. |
| [Hail](https://github.com/aistra0528/Hail) | GPL-3.0 | Confirmation that suspend-based freezing is the right mechanism, and that disable/hide/suspend are meaningfully different. |
| [NotiFilter](https://github.com/BURG3R5/NotiFilter) | GPL-3.0 | That notification-listener filtering is viable as a fallback, and that scheduling windows belong on the filter itself. |
| [DetoxDroid](https://f-droid.org/packages/com.flx_apps.digitaldetox/) | GPL-3.0 | **"Protection always on"** — you pause deliberately, it auto-resumes, and there is a *minimum interval between pauses*. This is the single most important behavioral idea in the whole design (§9). |
| [Curbox Detox](https://f-droid.org/packages/neth.iecal.curbox/) | FOSS | Graduated bypass resistance: strict / timed / per-attempt / challenge / capped attempt counts. |

---

## 2. Requirements

**R1.** Maintain a user-selected list of blocked apps.
**R2.** When active, blocked apps emit no notification, no sound, no vibration.
**R3.** When active, blocked apps cannot be opened.
**R4.** Activate manually, from a quick-settings tile, or on a schedule.
**R5.** Ship with a default schedule of **20:00 → 08:00, every day**, enabled.
**R6.** Deactivating restores every app to exactly its prior state.
**R7.** Survive reboots, including a reboot in the middle of a window.
**R8.** Never silently under-enforce. If a block fails, say so.
**R9.** While active, the phone must not ring except for **starred contacts**,
plus **repeat callers** when that toggle is on. See §18.

### Non-goals

- Usage analytics, screen-time charts, streaks, leaderboards.
- Per-app time budgets. Nope-Mode is binary: blocked or not.
- Website or URL blocking, keyword filtering of notification content.
- Grayscale, doomscroll detection, Extra Dim. (DetoxDroid does these; they are
  a different product.)
- Parental controls, remote administration, accounts, telemetry.
- **Any network access whatsoever.** The app declares no `INTERNET` permission
  and must never gain one.

---

## 3. Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **Device owner is the primary enforcement mechanism** | `setPackagesSuspended` is the only no-root API delivering R2 *and* R3, and it survives reboots (R7). Shizuku was rejected: it must be re-armed after every reboot, silently breaking an unattended overnight schedule — precisely the failure the app exists to prevent. |
| D2 | **Notification listener + accessibility is the fallback tier** | Zero provisioning, works anywhere. Strictly weaker (§8.2) but the app is never useless. |
| D3 | **Blocked apps are un-openable, not merely silent** | Full Focus Mode parity. Suspension delivers R2 and R3 in one call, so this costs nothing extra. |
| D4 | **Kotlin + Views, no Compose** | Matches PiercingXX-Launcher exactly. |
| D5 | **Public repo, all rights reserved** | Matches the launcher. Consequence: cleanroom discipline per §1, permanently. |
| D6 | **Room + Gson** | Same stack as the launcher, so backup JSON conventions carry over. |
| D7 | **Breaks are always time-bounded** | An unbounded "off" turns a schedule into a suggestion. See §9. |
| D8 | **State is derived, never accumulated** | A pure function of (now, schedules, override). Makes missed or duplicated alarms harmless. See §6. |

### 3.1 Device owner — the standing cost

Device owner is a full MDM role, and this is a real tradeoff, not a formality:

- Settings will report the device as managed.
- **Some apps refuse to run on managed devices** — banking, certain DRM video.
- The user cannot remove it through Settings. Nope-Mode **must** expose
  *Relinquish device owner* → `clearDeviceOwnerApp()`. Without it the only exit
  is a factory reset. Implemented in WS4; do not remove it.
- Only one device owner may exist. Nope-Mode holds that slot for the life of
  the install.

### 3.2 Provisioning window (historical, now satisfied)

Device owner can only be set on a device with **zero accounts**:

```sh
adb shell dpm set-device-owner \
    com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver
```

This was provisioned on 2026-08-03 after a factory reset. **It cost one wipe to
learn that opening the Google apps first closes the window.** If device owner is
ever relinquished, regaining it costs another full reset — treat it as
non-renewable.

---

## 4. Platform mechanics

Authoritative behavior of `DevicePolicyManager.setPackagesSuspended`, from
Android documentation. The whole design rests on this, so it is quoted rather
than paraphrased loosely.

A suspended package:

- cannot start activities;
- has its **notifications hidden**;
- does **not** appear in the recents / overview screen;
- cannot show toasts, dialogs, or snackbars;
- **cannot play audio or vibrate the device**;
- shows a system "app is paused" dialog when tapped;
- is **grayed out in the launcher**.

That single call satisfies R2 and R3 completely. No notification listener, no
accessibility service, no DND manipulation is required at this tier.

### 4.1 Packages that cannot be suspended

The platform refuses these outright. The picker must exclude them at
**selection time**, not fail at enforcement time:

- Device admins — **this includes Nope-Mode itself**
- The **active launcher** (suspending it bricks the home screen)
- The required **package installer**, **uninstaller**, and **verifier**
- The **default dialer**
- The **permission controller**

Two more that the platform *permits* but which must be guarded anyway:

- **The active input method.** Suspending the keyboard leaves the user unable
  to type — including unable to type their way out. Hard-block it.
- **The default SMS handler.** Permitted, and a legitimate choice for some, but
  it silences 2FA codes. Require an explicit confirmation; never reachable via
  bulk-select.

`setPackagesSuspended` **returns the array of packages it could not suspend.**
Never discard it (R8).

---

## 5. Architecture

```
        ┌────────────────────────────────────┐
        │           NopeController           │  owns desired state
        │  derive(now, schedules, override)  │  talks to no Android API
        └──────────────────┬─────────────────┘
                           │ desired: Set<packageName>
        ┌──────────────────▼─────────────────┐
        │         Enforcer (interface)       │
        └────────┬──────────────────┬────────┘
                 │                  │
      ┌──────────▼──────┐   ┌───────▼─────────────────┐
      │ SuspendEnforcer │   │    FallbackEnforcer     │
      │  device owner   │   │  listener + a11y        │
      └─────────────────┘   └─────────────────────────┘
```

`NopeController` computes *what should be true* and hands it to an `Enforcer`.
It imports nothing from `android.*`. That is what makes the scheduling logic —
the part that must be correct — testable on the JVM with no device.

Tier selection, re-evaluated whenever admin state changes:

```
if (dpm.isDeviceOwnerApp(packageName)) SuspendEnforcer else FallbackEnforcer
```

The active tier must be **visible in the UI at all times**. A user on the
fallback tier is getting weaker protection and has to know it (R8).

---

## 6. State model

The heart of the app. Everything else is plumbing.

```kotlin
sealed interface Override {
    data object None : Override
    data class ForceOn(val until: Instant?) : Override   // null = indefinite
    data class Break(val until: Instant) : Override      // ALWAYS bounded
}

fun isActive(now, schedules, override): Boolean = when (override) {
    is Override.ForceOn -> override.until == null || now < override.until
    is Override.Break   -> if (now < override.until) false
                           else scheduleSaysActive(now, schedules)
    Override.None       -> scheduleSaysActive(now, schedules)
}
```

| Override | Schedule | Result |
|---|---|---|
| `None` | inactive | inactive |
| `None` | active | **active** |
| `ForceOn(null)` | either | **active**, indefinitely |
| `ForceOn(t)`, now < t | either | **active** |
| `ForceOn(t)`, now ≥ t | — | expires → re-derive from schedule |
| `Break(t)`, now < t | active | inactive (break running) |
| `Break(t)`, now ≥ t | active | **active** — break expired, auto-resume |

**`Break` has no unbounded variant, by construction.** There is no way to
express "off until I say so". This is D7, and it is the difference between a
schedule and a suggestion.

---

## 7. Scheduling

```kotlin
data class Schedule(
    val id: Long,
    val startMinuteOfDay: Int,   // 20:00 -> 1200
    val endMinuteOfDay: Int,     // 08:00 -> 480
    val daysMask: Int,           // bit 0 = Monday .. bit 6 = Sunday
    val enabled: Boolean,
)
```

**Windows cross midnight.** `end <= start` means the window wraps into the next
day. The shipped default (1200 → 480) does exactly this, so **the wrap is the
common path, not an edge case.** Test it first, before the non-wrapping case.

`daysMask` refers to the day the window **starts**. A Friday-enabled 20:00→08:00
window runs Friday 20:00 through Saturday 08:00. A user unchecking Saturday
expects Saturday *night* to be unblocked, not Saturday *morning* — say so in the
UI, because both readings are defensible.

### 7.1 Reconciliation, not event-chasing

```kotlin
fun shouldBeActiveAt(now: LocalDateTime, schedules: List<Schedule>): Boolean
```

Pure. State is **always** obtained by calling this, never by accumulating alarm
events (D8). Alarms merely prompt re-evaluation. A missed, late, or duplicated
alarm therefore cannot corrupt state — the system is self-healing.

`reconcile()` runs on: boot completed, alarm fired, app foregrounded, tile
clicked, schedule edited, blocked-list edited, break started or expired,
`TIME_SET`, `TIMEZONE_CHANGED`.

### 7.2 Alarms

- `setExactAndAllowWhileIdle(RTC_WAKEUP, …)` for **the next boundary only**;
  recompute and re-arm after each fire. Never pre-schedule a chain.
- `USE_EXACT_ALARM` (API 33+), plus `SCHEDULE_EXACT_ALARM` for the minSdk 24
  range. If `canScheduleExactAlarms()` is false, degrade to
  `setAndAllowWhileIdle` **and show a persistent warning** — an inexact 20:00
  boundary can drift by minutes, which is visible and wrong.
- `RECEIVE_BOOT_COMPLETED` → re-arm and reconcile. Without this the schedule
  dies at the first reboot, which is exactly the Shizuku failure D1 rejected.

---

## 8. Enforcement tiers

### 8.1 SuspendEnforcer — primary

```kotlin
dpm.setPackagesSuspended(admin, packages, true)   // engage
dpm.setPackagesSuspended(admin, packages, false)  // release
```

Order of operations matters for crash safety:

1. Write intended packages to `suspend_record`.
2. Call `setPackagesSuspended(…, true)`.
3. Inspect the returned failure array; record and surface any failures.

Releasing reverses it: suspend-false first, then clear `suspend_record` only
for packages that actually released.

If the process dies between (1) and (2), reconcile-on-boot sees a record with
nothing suspended and re-derives — harmless. If it dies between (2) and (3),
the record is authoritative and the apps get released correctly. **Writing the
record first is what makes a crash recoverable rather than stranding apps.**

### 8.2 FallbackEnforcer — no device owner

**a) `NopeNotificationListener : NotificationListenerService`**
On `onNotificationPosted`, if the package is blocked and the mode is active,
`cancelNotification(sbn.key)`; prefer `snoozeNotification(key, duration)` where
it fits, since it suppresses re-post without a visible dismissal.

> **Stated limitation, which must appear in the UI verbatim in spirit:** the
> notification has already been posted when the listener sees it, so **the alert
> sound may play before suppression**. This tier delivers "the notification goes
> away", not "the notification never made a sound". It does not satisfy R2.

**b) `NopeAccessibilityService : AccessibilityService`**
On `TYPE_WINDOW_STATE_CHANGED`, read the foreground package; if blocked and
active, launch `BlockedActivity` with `FLAG_ACTIVITY_NEW_TASK`. Debounce to
avoid launch loops.

Do **not** use `performGlobalAction(GLOBAL_ACTION_BACK)` as the block — it
fights the user's navigation and behaves inconsistently across launchers.

---

## 9. Breaks and anti-bypass

Adopted from DetoxDroid's "protection always on" and Curbox's graduated
resistance. Without this, a schedule is decoration — at 2am the user simply
turns it off.

**Baseline (ship in WS7):**

- **Take a break** offers **5 / 15 / 30 minutes**, matching Focus Mode. No
  custom-duration field, no "until I turn it back on".
- Breaks **auto-resume**. There is no user action required to re-engage.
- A break shows a persistent countdown notification from Nope-Mode itself, so
  it is never ambiguous whether protection is on.

**Friction (WS7, configurable, default on):**

- **Minimum interval between breaks** — default 30 minutes. Prevents chaining
  5-minute breaks into an unbounded evening.
- **Break budget** — default 3 per active window. Exhausted means exhausted.

**Deferred, explicitly out of scope for v1:**

- Typing a sentence, scanning a QR code, or a commitment password. These are
  proven patterns (Curbox, DetoxDroid) but they are anti-features until the
  basics are solid. Revisit only if the baseline proves too easy to defeat.

Every friction setting must be **editable only while Nope-Mode is inactive.**
Otherwise the 2am workaround is simply to raise the break budget.

---

## 10. Data model (Room)

```
blocked_app      packageName TEXT PK, addedAt INTEGER
schedule         id INTEGER PK, startMinuteOfDay INT, endMinuteOfDay INT,
                 daysMask INT, enabled INT
app_state        id INTEGER PK (=1), overrideKind TEXT, overrideUntil INTEGER NULL,
                 lastReconcileAt INTEGER
suspend_record   packageName TEXT PK, suspendedAt INTEGER
break_log        id INTEGER PK, startedAt INTEGER, durationMinutes INT
```

- `suspend_record` — crash safety net (§8.1). On boot, anything present here
  but no longer scheduled to be blocked is released. Without this table a crash
  mid-activation can leave apps permanently suspended with no UI to recover them.
- `break_log` — enforces minimum interval and break budget (§9).

Seed migration inserts the default schedule `(1200, 480, all days, enabled)`.

---

## 11. UI

Five screens. Views + viewBinding, AMOLED-black monochrome, matching the
launcher.

1. **Home** — large active/inactive state; the *reason* ("scheduled until
   08:00", "break, 12 min left"); master toggle; **Take a break** button when
   active; blocked-app count; **enforcement-tier badge**.
2. **Blocked apps** — searchable list of launchable packages with icons and
   checkboxes. Packages from §4.1 shown disabled **with the reason inline** —
   not silently absent, or the user assumes it's blocked when it isn't.
3. **Schedules** — list, add, edit, delete. Time pickers plus day-of-week
   selector. Default row pre-seeded. Show the wrap explicitly: *"20:00 tonight
   → 08:00 tomorrow"*.
4. **Settings** — break duration options, minimum interval, break budget.
   **Locked while active** (§9).
5. **Setup / status** — tier, provisioning command, fallback permission grants,
   and **Relinquish device owner** with a plain-language warning.

`BlockedActivity` — the fallback tier's full-screen interstitial. One word,
*Nope.*, the app name, and when the block lifts.

### 11.1 Quick Settings tile

`NopeTileService : TileService` — toggles `ForceOn` / clears it. Label and state
must track derived state even when changed elsewhere
(`requestListeningState`).

On the target device this tile replaces the dead Digital Wellbeing Focus Mode
tile, which cannot work on GrapheneOS. Note the shade layout **cannot be set
programmatically on Android 17** — `sysui_qs_tiles` is vestigial and the real
storage is root-only — so tile placement is always a manual user step. Do not
waste effort automating it.

---

## 12. Manifest

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<!-- deliberately NO android.permission.INTERNET -->
```

Components: `NopeDeviceAdminReceiver` (+ `res/xml/device_admin.xml`),
`NopeNotificationListener`, `NopeAccessibilityService`, `NopeTileService`,
`BootReceiver` (`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`).

`POST_NOTIFICATIONS` is for Nope-Mode's own break countdown only.

The absent `INTERNET` permission is a verifiable privacy claim. Keep it true;
it is checkable with `aapt2 dump permissions`.

---

## 13. Package layout

```
com.piercingxx.nopemode
├── admin/      NopeDeviceAdminReceiver, DeviceOwnerManager          [WS4 done]
├── core/       NopeController, ScheduleEvaluator, Override, BreakPolicy
├── enforce/    Enforcer, SuspendEnforcer, FallbackEnforcer
├── data/       entities, DAOs, NopeDatabase, BackupJson
├── schedule/   AlarmScheduler, BootReceiver
├── service/    NopeNotificationListener, NopeAccessibilityService,
│               NopeTileService
└── ui/         HomeActivity, BlockedAppsActivity, SchedulesActivity,
                SettingsActivity, SetupActivity, BlockedActivity
```

---

## 14. Failure modes

| Scenario | Required behaviour |
|---|---|
| Crash while apps suspended | `suspend_record` + boot reconcile releases them |
| Reboot mid-window | `BootReceiver` reconciles; apps re-suspend |
| Exact alarm permission denied | Degrade to inexact, warn persistently, keep reconciling |
| User selects own launcher / IME | Blocked at selection time with an inline reason |
| `setPackagesSuspended` returns failures | Name the apps that failed; never report success (R8) |
| Device owner relinquished while active | Release everything **first**, then drop to fallback |
| Blocked app uninstalled | Prune from `blocked_app` and `suspend_record` on reconcile |
| Schedule edited mid-window | Reconcile immediately; may deactivate instantly |
| DST shift inside a window | Compare minute-of-day on local time; test the 02:00 jump |
| Break spans the window end | Break expires, schedule already over → stays inactive |
| Clock moved backwards | `Break.until` in the future by more than max duration → cancel the break |

---

## 15. Testing

`ScheduleEvaluator` and the override model are pure JVM by design. They carry
the weight.

- `shouldBeActiveAt`: normal window; **midnight-crossing window (the default)**;
  exact boundary minutes 20:00:00 and 08:00:00; day-mask boundaries across the
  wrap; DST spring-forward and fall-back inside a window.
- Full override truth table (§6), including `ForceOn` and `Break` expiry, and
  the clock-moved-backwards guard.
- `BreakPolicy`: minimum interval, budget exhaustion, budget reset at window end.
- Backup JSON round-trip.
- Instrumented: Room migrations; `suspend_record` recovery after a simulated
  crash; a real suspend/release cycle on-device.

**A workstream with failing tests is not done.**

---

## 16. Build

```
AGP 8.5.0 · Kotlin 1.9.24 · compileSdk 34 · minSdk 24 · targetSdk 34
Java/jvmTarget 1.8 · viewBinding · buildConfig
appcompat 1.6.1 · constraintlayout 2.1.4 · material 1.11.0 · preference 1.2.1
room 2.6.1 (+kapt, room-ktx) · gson 2.10.1 · coroutines 1.7.3
junit 4.13.2 · org.json 20231013 (test) · espresso 3.5.1
```

`applicationId` / `namespace`: `com.piercingxx.nopemode`. Debug keystore shared
with the launcher so sideloads keep one signing identity.

> `compileSdk 34` against a device on **SDK 37** is intentional (it matches the
> launcher) but means three API levels of untested drift. Verify
> `setPackagesSuspended` behavior on-device in WS5 before building UI on top
> of it.

---

## 17. Build order

| WS | Scope | State |
|---|---|---|
| 1 | Skeleton — gradle, manifest, packages, icon | **done** |
| 4 | Device admin receiver, `DeviceOwnerManager`, relinquish | **done, provisioned** |
| 2 | Room entities, DAOs, seed migration | next |
| 3 | `ScheduleEvaluator`, `Override`, `BreakPolicy` — pure JVM, fully tested | next |
| 5 | `SuspendEnforcer`, `suspend_record`, failure surfacing | |
| 6 | `AlarmScheduler`, `BootReceiver`, reconcile-on-everything | |
| 7 | UI, five screens, break friction | |
| 8 | QS tile | |
| 9 | `FallbackEnforcer` — listener + accessibility | |
| 10 | Gson backup/restore | |

WS1–6 produce a fully functional headless app. UI is deliberately late: the
scheduling logic is the part that has to be right, and it is the part that can
be proven correct without a device.

WS3 is the highest-risk workstream and has no Android dependencies — build it
against tests first, in isolation.

---

## 18. Ringer policy — "Quiet Ringer" (R9)

App suspension cannot silence the phone. The dialer is one of the packages the
platform refuses to suspend (§4.1), and correctly so. Calls therefore need a
second, independent mechanism.

### 18.1 Scope — why this is calls-only

DND cannot be scoped to a set of apps. `ZenPolicy` is a **global** interruption
filter with category-level exceptions; there is no API to apply it to five
chosen packages.

That is fine, because **the blocked-app list already silences the apps it
covers** — a suspended app cannot post a notification, play audio, or vibrate
(§4). Nothing about the ringer is needed to achieve that.

So the division of labour is:

| Concern | Mechanism | Scope |
|---|---|---|
| Selected apps go silent and un-openable | `setPackagesSuspended` | the checkbox list |
| Phone must not ring | `AutomaticZenRule` | the ringer, which is global by nature |

The zen rule **must not** be used to silence app notifications generally. Doing
so would quiet apps the user never selected, breaking the app's central promise
that it only touches what you picked.

### 18.2 Mechanism

`AutomaticZenRule` (API 24+), owned by Nope-Mode, with a `ZenPolicy` that
overrides global policy only while the rule is active.

```
permission : android.permission.ACCESS_NOTIFICATION_POLICY
             (user grant: Settings → Do Not Disturb access)

create once : NotificationManager.addAutomaticZenRule(rule) -> id, persisted
activate    : setAutomaticZenRuleState(id, Condition.STATE_TRUE)
deactivate  : setAutomaticZenRuleState(id, Condition.STATE_FALSE)
```

`ZenPolicy`:

- **Calls:** allowed from **starred contacts only**.
- **Repeat callers:** allowed iff the user toggle is on. This is a built-in
  DND category, not something Nope-Mode implements — a second call from the
  same number inside the platform's window rings through.
- **Every other interruption category is explicitly allowed** (alarms, media,
  system, reminders, events, messages from anyone, conversations, all visual
  effects). Leaving those fields UNSET inherits the user's DND defaults and
  silences apps the user never selected. Android cannot restrict the ringer
  alone; the UI must say this uses Do Not Disturb.

Rule state is driven by the **same derived `isActive`** as everything else
(§6). It is set inside `reconcile()`; there is no separate scheduler.

### 18.3 Why starred contacts rather than an in-app list

`CallScreeningService` + `ROLE_CALL_SCREENING` would allow an arbitrary
per-number whitelist, and was rejected:

- It is bound **before the device rings** and must respond within **5 seconds**
  or the platform times out — a slow or crashed lookup delays or drops a real
  call.
- Only one app can hold the role, so it would collide with any spam-blocking
  app.
- Repeat-caller logic would become ours to write and test, instead of being a
  platform guarantee.

Starred contacts is a whitelist that already exists, is edited in Contacts, is
a single source of truth across the whole system, and cannot make the phone
fail to ring because of a bug in this app. Revisit only if starring proves too
coarse in real use.

### 18.4 Failure modes

| Scenario | Required behaviour |
|---|---|
| `ACCESS_NOTIFICATION_POLICY` not granted | Ringer policy is inert. **Say so loudly on Home** — a silently non-functional Quiet Ringer is the worst outcome here (R8). Never show "active" while the ringer is unrestricted. |
| App crashes while rule is active | Rule state is re-derived on boot and on foreground; reconcile sets it false when inactive. |
| App uninstalled while rule is active | `removeAutomaticZenRule` on teardown. A stranded rule would leave the phone permanently quiet with the owning app gone. |
| Device owner relinquished | Unrelated — the zen rule does not depend on device owner and keeps working. |
| User has manual DND on independently | Nope-Mode owns *its* rule only; it must never write global DND state or fight the user's own setting. |
| No starred contacts exist | Valid, and means nothing rings. Warn once at setup — it is a plausible mistake, not necessarily an intent. |

### 18.5 Settings

- **Quiet Ringer** — master on/off for the whole feature. Default **on**.
- **Allow repeat callers** — default **on**. A second call from the same number
  is the closest thing to an emergency signal the platform offers, and missing
  a genuine emergency is worse than one unwanted ring. The user can turn it off.

Both live in the Settings screen and, like the break-friction settings, are
**editable only while Nope-Mode is inactive** (§9).
