---
name: sandbox-toolchain-path-override
description: Corrects verification commands when they reference invalid toolchain paths or blocked binaries by using the pre-granted absolute paths and allowed binaries in the sandbox.
origin: teacher
status: unreviewed
---

1. Inspect the `verify` command in the task spec for `$HOME` variables or relative binaries (e.g., `./gradlew`). 2. Cross-reference with the sandbox's granted environment (e.g., `/home/piercingxx/.local/android-toolchain/`) and allowed command list. 3. Rewrite the command to use the absolute path and the allowed binary name (e.g., `gradle` instead of `./gradlew`). 4. Ensure no source code changes are made if the implementation is already correct; only the verification command needs fixing.
