# Nope-Mode

> Selected apps go silent and un-openable — on a schedule, or because you said so.

A cleanroom equivalent of Focus Mode, for GrapheneOS, where Digital Wellbeing
does not exist. Pick the apps allowed to bother you; when Nope-Mode is on, the
rest produce **no notifications, no sound, no vibration**, and **cannot be
opened**. Turn it on by hand, from a Quick Settings tile, or let the schedule do
it — the default is 20:00 → 08:00, daily. No accounts, no network, no analytics:
the manifest declares no `INTERNET` permission, so there is nothing to leak and
nothing to audit.

<img src="docs/images/screenshot.png" width="270" alt="Nope-Mode home screen on a Pixel 6 — device-owner enforcement active">

**Status:** provisioned and enforcing on a Pixel 6 running GrapheneOS — device
owner granted, notification listener and accessibility service enabled, Do Not
Disturb policy access held. 224 unit tests, all green.

**The backup screen is a dead end.** `exportJson` and `restoreJson` have no
caller outside the test suite, there is no file picker, and `restoreJson` stops
after the validator without committing anything to the Room DAOs. It is a screen,
not a feature.

## How it works

Nope-Mode runs as a **device owner** and calls the platform's
`setPackagesSuspended` — the mechanism behind Focus Mode itself. A suspended app
cannot show notifications, play audio, raise dialogs, or launch, and it survives
reboots, which a Shizuku-based approach does not. Without device owner it falls
back to a notification listener plus an accessibility service: that tier runs
anywhere and is leakier — a notification may make a sound before it is
suppressed.

## Setup 🛠️

Device owner can be provisioned **only** while the device has zero accounts and
zero secondary profiles. That constraint is the whole reason Nope-Mode is the
first app installed on a fresh device. This one was:

```sh
adb shell dpm set-device-owner com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver
```

Add a Google account first and the window closes. Reopening it costs a factory
reset. Device owner is a full MDM role and carries real tradeoffs — some banking
and DRM apps refuse to run on a managed device. Nope-Mode always exposes
**Relinquish device owner**, so backing out costs nothing. Read
[design.md](design.md) before provisioning.

## Theme sync 🌀

XX-Launcher is the sender: it broadcasts `xx.launcher.THEME_CHANGED` with a theme
name and a background ARGB, and Nope-Mode persists the choice and repaints
without being opened.

## Build 🧪

Android 7.0+ (minSdk 24), targetSdk 35.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

[design.md](design.md) is the spec; [todo.md](todo.md) is history and lags the
code.

## License

Copyright (c) 2026 PiercingXX. All rights reserved. No code is derived from Hail,
NotiFilter, DetoxDroid, or any other GPL project — Nope-Mode is a cleanroom
implementation written against public Android API documentation.
