# Nope-Mode-verify1

Operator mill contract materialized from dispatch. Do not invent a different exam.

## Goal

Verify one open box: "Resync tokens from `piercingxx-branding` if Nope-Mode colors are" The previous mill (Nope-Mode-todo4) delivered nothing for it — every file it named was already on main, so building it again lands nothing. The list states no evidence line for it, so state in the change exactly what you ran and what it printed. If it fails, implement exactly what fails and land that. If it passes, check the box off in the todo list and record the evidence in the same change. Do not re-mill files that are already present, and do not widen this past the one box.

### T1 — Resync tokens from `piercingxx-branding` if Nope-Mode colors are

- files: app/src/main/java/com/piercingxx/nopemode/ResyncTokens.kt, app/src/test/java/com/piercingxx/nopemode/ResyncTokensTest.kt
- note: verify was a test this box writes, which closes the box without wiring anything into the app (xx-camera, xx-apps, 2026-09-14). Appended an app-level acceptance the mill cannot satisfy by writing a test: production code outside ResyncTokens must reference it.
- verify: ./gradlew :app:testDebugUnitTest --offline --tests ResyncTokensTest && grep -rn 'ResyncTokens' app/src/main --exclude=ResyncTokens.kt

## Final gate

- verify: ./gradlew testDebugUnitTest --offline
