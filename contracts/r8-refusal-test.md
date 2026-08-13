# Nope-Mode — Corrective Contract: R8 Refusal Path Proven by JVM Test

Skippy's corrective scope, authored 2026-08-13, following Nagatha's audit of the
full build. Spec: [design.md](../design.md). Parent build contract:
[estate-build-2.md](estate-build-2.md).

## Header — base branch and why this contract exists

**This queue item must build on base `ralph/queue-nope-mode-full-build`** — the
branch that carries the full build, including `SuspendEnforcer`. Do not rebase
onto `master`; the enforcer under test only exists on that branch.

Nagatha audited the full build and found a real gap: `SuspendEnforcer.apply()`'s
refused-package handling has no test. She is right that it is JVM-provable with
a mocked `DevicePolicyManager` — `setPackagesSuspended` is a plain method call on
an interface we can stub, and the enforcer's `suspend_record` bookkeeping is a
Room DAO interface we can fake. The refusal path is the single most important
guarantee in the whole app (R8, design §5): **never report success for a package
the platform refused to suspend.** It deserves a test that can actually fail.

The parent contract deferred `SuspendEnforcer`'s behavior to the operator's
on-device check (estate-build-2.md §Deferred, WS5). This corrective contract
narrows that: the **refusal path** is pulled out of deferred and proven here on
the JVM. The rest of WS5 (a blocked app going silent, greying out, showing the
system paused dialog; SDK 37) stays operator on-device work.

## The code under test (verified against the branch this session)

`app/src/main/java/com/piercingxx/nopemode/enforce/SuspendEnforcer.kt` on
`ralph/queue-nope-mode-full-build`:

- `apply(desired)` reads the current `suspend_record`, releases anything recorded
  but no longer desired (unsuspend **before** deleting the record), then suspends
  the newly-desired set — recording **before** suspending so a crash mid-activation
  is recoverable on boot.
- `dpm.setPackagesSuspended(admin, toSuspend, true)` returns the array of packages
  it **could not** suspend (or `null` when all succeeded). The code adds those to
  `failed` and deletes their `suspend_record` — because a package the platform
  refused was never actually suspended, so its record must not survive to be
  re-released later.
- It returns `Enforcer.Result(failed)`. `Result.success` is `failed.isEmpty()`.

The `Enforcer` interface (same branch) already carries the R8 guarantee in its
contract: the result carries the packages that **failed**, not just a boolean,
and an empty `failed` means fully enforced. This test proves the implementation
honors it.

## Scope — what the refusal-path test must prove

A NEW JVM test class, `SuspendEnforcerTest`, drives `SuspendEnforcer.apply()`
with a **mocked `DevicePolicyManager`** (via MockK) and a **hand-rolled in-memory
fake `SuspendRecordDao`** (the DAO is a Room interface, so a fake is trivial and
avoids mocking `Flow`/`suspend`). It must prove, with the DPM stubbed to refuse
specific packages:

1. **Refused packages surfaced in `Result.failed`.** When the DPM returns
   `arrayOf("com.example.refused")`, `apply()` returns
   `Result(failed = setOf("com.example.refused"))`.
2. **Refused packages' `suspend_record`s cleaned.** After `apply()`, the fake DAO
   no longer contains the refused package; the successfully-suspended packages
   are still recorded.
3. **Nothing refused is ever reported as blocked.** `Result.success` is `false`
   when any package is refused, and the refused package is not present in the
   recorded/suspended set — the caller cannot treat it as enforced.

Plus the two guard rails that keep the refusal path honest:

4. **Happy path.** When the DPM returns `null` (all succeeded), `Result.failed`
   is empty, `Result.success` is `true`, and every desired package stays recorded.
5. **Release path.** A recorded-but-no-longer-desired package is unsuspended (DPM
   called with `false`) and its record is deleted — so the refusal bookkeeping
   does not corrupt the release half.

The test mocks the Android classes it must (`DevicePolicyManager`, `Context` via
`getSystemService`) and fakes the DAO; it does **not** run Android. No Robolectric,
no emulator, no device.

## Toolchain — the one new dependency and the narrowed verify

The build currently has **no mocking library** (`testImplementation` is only
`junit:junit:4.13.2` and `org.json:json`). Mocking `DevicePolicyManager` and
`Context` requires one. Add MockK — the standard Kotlin mocking library,
compatible with this project's Kotlin 1.9.24 / JVM 1.8 target:

```groovy
testImplementation 'io.mockk:mockk:1.13.10'
```

to `app/build.gradle`'s `dependencies`. (MockK 1.13.x is the line for Kotlin 1.9;
1.13.10 is a stable point release on that line. If the builder's resolution pulls
a newer 1.13.x it is fine — the API used here is stable.)

The verify is the same narrowed shape as every estate-build-2 task — a named
gradle unit-test class, **quote-free**, not the full gate:

- verify: `env JAVA_HOME=$HOME/.local/android-toolchain/jdk17 ANDROID_HOME=$HOME/.local/android-toolchain/sdk ./gradlew --no-daemon testDebugUnitTest --tests com.piercingxx.nopemode.enforce.SuspendEnforcerTest`
- files: `app/build.gradle` (add MockK),
  `app/src/test/java/com/piercingxx/nopemode/enforce/SuspendEnforcerTest.kt`

This verify runs behavior — it executes the test class and fails if the refusal
path regresses. It does not grep, does not read a file, and is not the full
`assembleDebug testDebugUnitTest` gate.

## Deferred — stays operator on-device work (unchanged from estate-build-2)

The rest of WS5 is untouched by this contract and remains deferred to the
operator's on-device check: a blocked app goes silent, greys out, and shows the
system paused dialog; releasing restores it fully; verify on SDK 37 (design §16).
This contract only pulls the **refusal bookkeeping** (failed surfacing + record
cleanup + never-report-blocked) into JVM-proven territory.

---

Signed — Skippy, 2026-08-13.