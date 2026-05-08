# Phase 03 — hilt-to-ksp

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Switch all Hilt annotation processors — main DI compiler, extensions compiler (ViewModel + WorkManager), and androidTest compiler — from `kapt`/`kaptAndroidTest` to `ksp`/`kspAndroidTest` in a single commit. These three processors share generated-code visibility; splitting them across separate phases causes compilation failures.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch that includes Phase 01–02 changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 800 |

---

## Steps

### Step 3.1 — Replace hilt-android-compiler kapt with ksp

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, replace:
> ```
> kapt("com.google.dagger:hilt-android-compiler:2.57.2")
> ```
> with:
> ```
> ksp("com.google.dagger:hilt-android-compiler:2.57.2")
> ```
> Do not touch the `implementation("com.google.dagger:hilt-android:2.57.2")` line.

**Verification:**

- `Grep` — `kapt\("com.google.dagger:hilt-android-compiler` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `ksp\("com.google.dagger:hilt-android-compiler:2.57.2"\)` returns exactly 1 hit (not counting `kspAndroidTest`).

**Status:** `[ ]` not done

---

### Step 3.2 — Replace hilt-compiler (extensions) kapt with ksp

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, replace:
> ```
> kapt("androidx.hilt:hilt-compiler:1.2.0")
> ```
> with:
> ```
> ksp("androidx.hilt:hilt-compiler:1.2.0")
> ```
> This compiler handles `@HiltViewModel` and `@HiltWorker` annotations and depends on `hilt-android-compiler` output. Both must be on the same processor (KSP) for generated code to be mutually visible — which is why Steps 3.1 and 3.2 are in the same phase.

**Verification:**

- `Grep` — `kapt\("androidx.hilt:hilt-compiler` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `ksp\("androidx.hilt:hilt-compiler:1.2.0"\)` returns exactly 1 hit.

**Status:** `[ ]` not done

---

### Step 3.3 — Replace kaptAndroidTest with kspAndroidTest

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 3.2

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, replace:
> ```
> kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.57.2")
> ```
> with:
> ```
> kspAndroidTest("com.google.dagger:hilt-android-compiler:2.57.2")
> ```

**Verification:**

- `Grep` — `kaptAndroidTest` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `kspAndroidTest\("com.google.dagger:hilt-android-compiler:2.57.2"\)` returns exactly 1 hit.

**Status:** `[ ]` not done

---

### Step 3.4 — Build across all main flavors

**Files:** none
**Depends on:** Step 3.3

**Prompt for developer:**

> Verify that kapt is no longer used in `app_v2/build.gradle.kts` at this point. Run:
> ```
> ./gradlew assembleStandardDebug assembleLiteDebug assembleVrDebug
> ```
> All three must succeed. The `kaptGenerateStubs` and `kaptStandardDebugKotlin` tasks must no longer appear in the task list (the build output should not contain any `kapt` task names).

**Verification:**

- Build output contains `BUILD SUCCESSFUL` (or three successive successes).
- `Grep` build output for `> Task :app_v2:kapt` returns 0 hits.
- Build output does not contain `error:`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `Grep` — `kapt(` in `app_v2/build.gradle.kts` returns 0 hits (only the `kapt { }` config block remains, covered in Phase 04).
- [ ] `Grep` — `kaptAndroidTest` in `app_v2/build.gradle.kts` returns 0 hits.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "S0042 Phase 03" "Switch all Hilt compilers kapt/kaptAndroidTest → ksp/kspAndroidTest"`.

---

## Handoff Notes to Next Phase

- All four annotation processor dependencies (`hilt-android-compiler`, `hilt-compiler`, `room-compiler`, `glide-compiler`) are now on KSP.
- The `kapt` **dependency configurations** are gone, but the `id("kotlin-kapt")` plugin and the `kapt { }` configuration block remain. Phase 04 removes these.
- The `kapt-specific` entries in `gradle.properties` (`kapt.incremental.apt`, `kapt.use.worker.api`) also remain for Phase 04.

---

## Rollback Plan

Revert phase commit. Restore the three original `kapt`/`kaptAndroidTest` lines. All three must be restored together — partial restore leaves the Hilt processors in a split state that fails to compile. Clean build required after revert.
