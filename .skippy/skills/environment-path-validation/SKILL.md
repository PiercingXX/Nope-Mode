---
name: environment-path-validation
description: Detect and correct non-existent or blocked toolchain paths in verification commands before execution.
origin: teacher
status: unreviewed
---

1. Inspect the verify command's environment variables (e.g., JAVA_HOME, ANDROID_HOME) and executable paths. 2. Cross-reference these paths against the actual filesystem (e.g., using `ls` or `test -d`) to confirm existence. 3. If a path is missing or points to a sandbox-blocked location, search the repository or system for the correct installed location (e.g., `/home/piercingxx/.local/android-toolchain/`). 4. Rewrite the verify command with the corrected paths and executable before attempting execution.
