# Nope-Mode — Remaining work

Spec: [design.md](design.md). Punch list from the 2026-08-26 review.

**Verified:** `./gradlew testDebugUnitTest assembleDebug` green. 235 unit tests,
0 failures.

---

## Local setup

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.piercingxx.nopemode/.ui.HomeActivity
```

Provisioning survives reinstall. Only Relinquish or a wipe drops device owner;
regaining it costs a factory reset.

---

## T1 — Relinquish  ✅

- [x] Setup: Relinquish control, visible only while device owner
- [x] Plain-language warning (managed device, banking/DRM, factory reset to
      re-provision)
- [x] Confirm → `enforcer.apply(emptySet())` → `RingerPolicy.tearDown()` →
      `clearDeviceOwnerApp()` → reconcile onto fallback
- [x] Abort if packages are still recorded as suspended
- [x] After release, Setup shows fallback grant buttons

## T2 — Quiet Ringer is not ringer-only  ✅

- [x] Explicitly allow alarms, media, system, reminders, events, messages,
      conversations, all visual effects
- [x] Restrict calls to starred contacts; repeat callers follow the user toggle
- [x] Tests assert `allowOtherInterruptions`, not an untouched-field flag
- [x] Settings/Home copy: this uses Do Not Disturb
- [x] Home probes with `RingerPolicy.isUsable()` — no `ensureRule` rewrite of
      repeat callers

## T3 — Silent under-enforcement (R8)  ✅

- [x] Persist `Enforcer.Result.failed` and thrown reconcile errors
- [x] Name those packages on Home; badge them in the picker
- [x] `reconcileAndApply` does not throw; apply and arm are isolated
- [x] `canScheduleExactAlarms()` false → `setAndAllowWhileIdle` + Home warning
- [x] `SecurityException` caught inside `arm()`
- [x] Setup: deep link to Alarms & reminders
- [x] Prune uninstalled packages from `blocked_app` and `suspend_record`; skip
      prune if the installed-set read is empty/failed

## T4 — Take a break  ✅

- [x] Home: Take a break when derived-active (schedule or ForceOn)
- [x] 5 / 15 / 30 from `FrictionSettings.BREAK_CHOICES`
- [x] End break while a break is running
- [x] Policy driven from derived `isActive`; ForceOn budget from local midnight
- [x] Countdown notification + `POST_NOTIFICATIONS` request on API 33+
- [x] Refused breaks show the policy reason

## T5 — Settings lock and fallback grants  ✅

- [x] Settings: `canEditFrictionSettings(isEnabled && derive(...))`
- [x] Detect notification-listener and accessibility grants
- [x] Fallback Home warning when either is off
- [x] Setup: deep links to listener, accessibility, DND, exact alarms

## T6 — Backup  ✅

- [x] `ACTION_CREATE_DOCUMENT` export
- [x] `ACTION_OPEN_DOCUMENT` import
- [x] Validate, then commit in one Room transaction, then reconcile
- [x] Reject `version` newer than `BackupJson.VERSION`
- [x] Honest copy; show errors if the file is rejected

## T7 — Stale spec  ✅

- [x] `design.md` status line and §18.2 match the DND mapping
- [x] README: Relinquish is real; backup is real

---

## Cross-cutting

- Cleanroom only. Read prior-art docs, never their source.
- No `INTERNET` permission. Check with `aapt2 dump permissions`.
- Never report success for a package the platform refused to suspend (R8).
- Nothing calls an `Enforcer` except `reconcileAndApply` (and Relinquish, which
  must release before dropping owner).
- Friction settings editable only while the product is actually inactive
  (master off **or** derived inactive).
