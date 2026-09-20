# nope-mode-todo-contract

Operator mill contract materialized from dispatch. Do not invent a different exam.

## Goal

Write /media/Working-Storage/GitHub/Phone-Projects/android/Nope-Mode/contracts/Nope-Mode-todo.md per this exact spec (base master @ 04edddc, package com.piercingxx.nopemode). Permission declare is already correct (uses-only) — do NOT touch it. Device-QA items (device-owner only, relinquish works) are NOT part of the contract — code-side work only. The contract must open with a 'State of the tree (measured this session)' section citing file:line for each of the 5 deliverables…

### T1 — Implement the scoped goal

- files: app/src/main/AndroidManifest.xml
- verify: ./gradlew :app:testDebugUnitTest --offline

## Final gate

- verify: ./gradlew testDebugUnitTest --offline
