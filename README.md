# Nope-Mode

Selected apps go silent and un-openable — on a schedule, or on demand.

A cleanroom equivalent of Google's Focus Mode, for GrapheneOS, where Digital
Wellbeing does not exist. No accounts, no network, no analytics.

**Status:** specification only. See [design.md](design.md) for the full spec
and [todo.md](todo.md) for the build plan.

## What it does

- Pick the apps that are allowed to bother you, and the ones that aren't.
- When Nope-Mode is on, blocked apps produce **no notifications, no sound, no
  vibration**, and **cannot be opened**.
- Turn it on by hand, from a quick-settings tile, or let the schedule do it.
- **Default schedule: 20:00 → 08:00, daily.**

## How it works

Nope-Mode runs as a **device owner** and uses the platform's
`setPackagesSuspended` — the same mechanism behind Google's Focus Mode. A
suspended app cannot show notifications, play audio, raise dialogs, or launch.
It survives reboots, which a Shizuku-based approach does not.

Where device owner isn't provisioned, Nope-Mode falls back to a
notification listener plus an accessibility service. That tier works
everywhere but is leakier — a notification may make a sound before it is
suppressed.

## Setup

Device owner can **only** be provisioned on a device with **no accounts added**.
Install the APK on a freshly reset device, then:

```sh
adb shell dpm set-device-owner com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver
```

Adding a Google account first closes this window, and reopening it requires a
factory reset.

Device owner is a full MDM role and carries real tradeoffs — some banking and
DRM apps refuse to run on managed devices. Nope-Mode always exposes a
**Relinquish device owner** action so this is reversible without a wipe. Read
§2.2 of [design.md](design.md) before provisioning.

## Requirements

- Android 7.0+ (minSdk 24), built and tested against GrapheneOS on a Pixel 9 Pro
- Device owner for full enforcement; otherwise the fallback tier

## License

Copyright (c) 2026 PiercingXX. All rights reserved.

No code is derived from Hail, NotiFilter, DetoxDroid, or any other GPL project.
Nope-Mode is a cleanroom implementation written against public Android API
documentation.
