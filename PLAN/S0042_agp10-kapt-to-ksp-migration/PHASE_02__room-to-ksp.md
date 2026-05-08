# Phase 02 — room-to-ksp

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Switch the Room annotation processor from `kapt` to `ksp`; verify DAO compilation and Room-generated code correctness via the existing Room instrumented test suite.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch that includes Phase 01 changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 800 |

> `AppDatabase.kt` uses `exportSchema = false` — no schema export directory argument is needed in kapt or ksp configuration. No `room { schemaDirectory(...) }` block is required.

---

## Steps

### Step 2.1 — Replace Room compiler kapt with ksp

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, replace the line:
> ```
> kapt("androidx.room:room-compiler:2.7.0")
> ```
> with:
> ```
> ksp("androidx.room:room-compiler:2.7.0")
> ```
> Do not touch the `room-runtime` or `room-ktx` implementation lines. Do not add a `room { }` DSL block — `AppDatabase` uses `exportSchema = false`, so no schema directory configuration is needed.

**Verification:**

- `Grep` — `kapt.*room-compiler` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `ksp\("androidx.room:room-compiler:2.7.0"\)` returns exactly 1 hit.

**Status:** `[ ]` not done

---

### Step 2.2 — Build and confirm DAO compilation

**Files:** none
**Depends on:** Step 2.1

**Prompt for developer:**

> Run `./gradlew assembleStandardDebug`. Confirm that Room's KSP processor generates DAO implementations without error. The build output must not contain any `[ROOM]` warnings about missing type arguments or unresolved references.

**Verification:**

- Build output contains `BUILD SUCCESSFUL`.
- Build output does not contain `error:`.
- `Grep` build output for `\[ROOM\]` returns 0 hits (no Room processor warnings).

**Status:** `[ ]` not done

---

### Step 2.3 — Run Room instrumented tests

**Files:** none
**Depends on:** Step 2.2

**Prompt for developer:**

> Run the Room instrumented test suite: `./gradlew :app_v2:connectedStandardDebugAndroidTest --tests "*.room.*"` (or the equivalent test filter for Room DAO tests in this project). If no Room-specific instrumented tests exist, run `./gradlew :app_v2:testStandardDebugUnitTest` and confirm unit tests pass. Document which test command was run.

**Verification:**

- Test run exits with code 0 (all tests pass).
- No `RoomDatabase` or DAO-related test failures in output.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `kapt.*room-compiler` in `app_v2/build.gradle.kts` returns 0 hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "S0042 Phase 02" "Switch Room compiler kapt → ksp"`.

---

## Handoff Notes to Next Phase

- Room's generated DAO and `_Impl` classes are now produced by KSP.
- Two kapt entries remain: `hilt-android-compiler` and `hilt-compiler`.
- These two Hilt processors share generated-code visibility and must be migrated together in Phase 03.

---

## Rollback Plan

Revert phase commit. Restore `kapt("androidx.room:room-compiler:2.7.0")`. Clean build required after revert.
