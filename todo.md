# Nope-Mode — Remaining work

**2026-09-04.** Punch list T1–T7 is empty. This is a **shipped
device-owner** app. Remaining work is Android 17 regression + polish.
Do not greenfield.

Package: `com.piercingxx.nopemode`  
GrapheneOS Focus Mode: device-owner suspend + Quiet Ringer.

```
Status: provisioned, Relinquish + backup real, theme permission held.
compileSdk/targetSdk 35. design.md toolchain section is stale.
```

---

## Estate (locked 2026-09-20)

Sign-in is the skippy-tel door. xx-apps on `127.0.0.1` is the user gateway; this app does not mint accounts. Hub up: reuse that session. Hub down: stored origin + token, then one-app login.

User files leave the phone through **xx-drive** (the user's own tree). Suite backup (`SuiteBackupProvider` → xx-apps → skippy-tel) is the phone snapshot, not a second sync product. No per-app `:845x` in the UX.

## Locked / stop (read every session)

- **NEVER uninstall** this package on a provisioned phone without
  **Relinquish** first. Suspended apps strand. Device-owner slot is
  consumed. Factory reset is the only way back after Relinquish.
- **NEVER** `adb uninstall` or casual clear-data.
- Do not remove Relinquish or suspend_record crash-safety ordering.
- Do not add `INTERNET`.
- Keep **xx-clock** out of the blocked-apps list.

---

## N1 — Android 17 / caiman regression

- [ ] Schedule window: apps suspend, QS tile reflects it, Take a break
  unsuspends for the budget then re-suspends.
- [ ] Reboot mid-window: still suspended after boot.
- [ ] DST / overnight: next window still fires.
- [ ] Quiet Ringer: DND on; starred contacts still ring (dialer).
- [ ] Relinquish on a **spare** or after a written backup — do not
  practice this on the only daily driver without a plan.
- [ ] Exact-alarm + DND + notification-listener grants survive an OS
  upgrade; document the re-grant path if they do not.

**Accept:** dated notes on the Pixel 9 Pro. Then this file can stay
quiet until the next OS bump.

---

## N2 — Branding

- [x] Resync tokens from `piercingxx-branding` if Nope-Mode colors are
  still the old set. Theme receiver already holds `THEME_SYNC`.

---

## Do not start unless reopened

Typing/QR bypass, usage analytics, URL blocking, per-app time budgets,
CallScreeningService whitelist finer than starred-contacts.

---

## Stop conditions

- Uninstall / clear-data “to test” on the provisioned daily → reject.
- `INTERNET` → reject.
- Inventing anti-bypass as v1 work → reject.

## Emulator smoke (millable)

- [x] SMOKE-1 — Declare a launcher activity: give one Activity in AndroidManifest.xml an intent filter with MAIN action and LAUNCHER category
  - files: app/src/main/AndroidManifest.xml
  - verify: python3 /home/piercingxx/.skippy/app/scripts/android_smoke.py . 2>&1 | tail -1 | grep -q 'SMOKE PASS'
- [x] SMOKE — the app passes its emulator smoke run
  - verify: python3 /home/piercingxx/.skippy/app/scripts/android_smoke.py . 2>&1 | tail -1 | grep -q 'SMOKE PASS'


## WAVE-1 — xx-apps catalog (operator 2026-09-17)

Package `com.piercingxx.nopemode`. Default-on. Device-owner Focus Mode.
xx-apps may uninstall this APK when the Skippy user is disabled. Keep
`INTERNET` absent. Keep xx-clock out of the blocked-apps list.

- [x] Nm-E1 — Package id frozen for the store seed. Theme sync stays.

## BACKUP wave (operator lock 2026-09-20)

Contract: `xx-apps/docs/SUITE-BACKUP-PROVIDER.md`; server
`skippy-tel-network/docs/SUITE-BACKUP.md`. No release of this app ships
without its provider once the `suite-backup` library is on the estate
Maven.

- [x] BK-1 — Ship `SuiteBackupProvider` at `${applicationId}.suite.backup` guarded by `com.piercingxx.suite.permission.BACKUP` plus the in-code signature check. Snapshot contains schedules and app lists DB, `prefs/`. Restore applies atomically then exits the process.
  - files: app/src/main/AndroidManifest.xml, app/src/main/java/**/backup/SuiteBackupProvider.kt
  - verify: unit test round-trips snapshot → restore on an in-memory store; xx-apps Back up now lists this app with a size
