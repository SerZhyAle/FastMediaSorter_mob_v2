# Phase 05 — compat-flags-removal

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Remove the two AGP legacy DSL compatibility flags from `gradle.properties` and verify that all six product flavors build without deprecation warnings.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] `Grep` — `kapt` in `app_v2/build.gradle.kts` returns 0 hits (Phase 04 complete).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `gradle.properties` | Modified | ≤ 50 |

---

## Steps

### Step 5.1 — Remove legacy DSL compat flags

**Files:** `gradle.properties`
**Depends on:** — start of phase

**Prompt for developer:**

> In `gradle.properties`, remove the following lines and all associated comments:
>
> ```
> # builtInKotlin=false REQUIRED while kotlin-kapt is in use.
> # To remove this warning: migrate Hilt/Room annotation processors to KSP (kapt -> ksp).
> # See: https://developer.android.com/r/tools/built-in-kotlin
> android.builtInKotlin=false
> # newDsl=false required by AGP when builtInKotlin=false (kapt compat mode).
> android.newDsl=false
> ```
>
> These two flags are co-dependent — removing one without the other produces a build configuration error. Remove both in a single edit.

**Verification:**

- `Grep` — `builtInKotlin` in `gradle.properties` returns 0 hits.
- `Grep` — `newDsl` in `gradle.properties` returns 0 hits.

**Status:** `[ ]` not done

---

### Step 5.2 — Build all six flavors and confirm zero deprecation warnings

**Files:** none
**Depends on:** Step 5.1

**Prompt for developer:**

> Run a build for all six product flavor × debug combinations:
> ```
> ./gradlew assembleStandardDebug assembleLiteDebug assemblePhotosDebug assembleLegacyDebug assembleVrDebug assembleVrUnlicensedDebug
> ```
> Capture the full build output. Confirm:
> 1. All six APKs are produced successfully.
> 2. The output contains zero lines matching `This behavior has been deprecated` or `has been deprecated and is scheduled to be removed`.
> 3. Configuration cache is active.

**Verification:**

- `Glob` — `app_v2/build/outputs/apk/*/debug/*.apk` finds 6 files (one per flavor).
- Build output does not contain `This behavior has been deprecated`.
- Build output does not contain `has been deprecated`.
- Build output contains `Configuration cache entry`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `Grep` — `builtInKotlin` in `gradle.properties` returns 0 hits.
- [ ] `Grep` — `newDsl` in `gradle.properties` returns 0 hits.
- [ ] All six `*Debug` APKs build without deprecation warnings.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "gradle.properties" "S0042 Phase 05" "Remove legacy DSL compat flags android.builtInKotlin and android.newDsl"`.

---

## Handoff Notes to Next Phase

- The main source of deprecation warnings is eliminated.
- Two minor legacy patterns remain: the `kotlinOptions { }` block in `wear/build.gradle.kts` and the `sourceSets { getByName("vrUnlicensed") { ... } }` pattern in `app_v2/build.gradle.kts`. Phase 06 addresses these.
- If Step 5.2 reveals any **new** deprecation warnings surfaced by the flag removal (i.e., warnings that were previously masked), record them in the Blockers Log before proceeding to Phase 06.

---

## Rollback Plan

Revert phase commit. Restore both `android.builtInKotlin=false` and `android.newDsl=false` with their comments. Clean build required after revert.
