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

- [ ] Resync tokens from `piercingxx-branding` if Nope-Mode colors are
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
