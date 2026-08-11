---
name: environment-path-validation
description: Detect and correct toolchain path mismatches and shell allowlist blocks before executing verification commands.
origin: teacher
status: unreviewed
---

1. Inspect the environment variables in the verify command (e.g., JAVA_HOME, ANDROID_HOME). 2. Cross-reference these paths with the actual filesystem (e.g., check if `/home/piercingxx/.local/android-toolchain` exists vs the plan's `$HOME/.local/toolchains`). 3. Check if the command (e.g., `./gradlew`) is blocked by the sandbox allowlist. 4. Rewrite the verify command with the correct absolute paths and ensure the tool is allowed, or report the path mismatch as a blocker rather than attempting to run the command.
