# Nope-Mode

> Selected apps go silent and un-openable — on a schedule, or because you said so.

Focus Mode for GrapheneOS, where Digital Wellbeing does not exist. When it is
on, the rest produce **no notifications, no sound, no vibration**, and **cannot
be opened**. Hand, tile, or schedule (default 20:00 → 08:00). No accounts, no
network, no analytics. No `INTERNET` in the manifest.

<img src="docs/images/screenshot.png" width="270" alt="Nope-Mode home screen on a Pixel 6 — device-owner enforcement active">

**Provisioned and enforcing on GrapheneOS.** Quiet Ringer uses Do Not Disturb:
starred contacts still ring. Relinquish is on Setup and releases suspended apps
before dropping device owner.

Runs as **device owner** via `setPackagesSuspended`. Survives reboot. Without
device owner it falls back to a notification listener plus accessibility —
leakier. A notification may make a sound before it is suppressed.

Device owner can be granted **only** with zero accounts and zero secondary
profiles. First app on a fresh device, or not at all. Add a Google account
first and the window closes. Reopening it costs a factory reset. Some banking
and DRM apps refuse a managed device. Relinquish is always there.

```
package: com.piercingxx.nopemode    minSdk 24
```

```sh
adb shell dpm set-device-owner com.piercingxx.nopemode/.admin.NopeDeviceAdminReceiver
```

Read [design.md](design.md) before provisioning.

## Build

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## License

All rights reserved. See [LICENSE](LICENSE). Cleanroom — not derived from Hail,
NotiFilter, DetoxDroid, or any other GPL project.
