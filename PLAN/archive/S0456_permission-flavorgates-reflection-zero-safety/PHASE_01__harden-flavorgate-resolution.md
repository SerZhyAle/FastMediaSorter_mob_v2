# Phase 01 - Harden Flavor-Gate Resolution

**Strategic spec:** [`../S0456_permission-flavorgates-reflection-zero-safety.md`](../S0456_permission-flavorgates-reflection-zero-safety.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Make a missing/typo'd flavor-gate field name fail an automated unit test instead of silently dropping a permission, while preserving the safe release default (ADR-1). Expose the declared gate-field set for testing and narrow the reflection catch so a missing field is logged at an actionable level.

---

## Prerequisites

- [ ] Strategic §6 research item is Resolved (it is - see INDEX Research inputs).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt` | Modified | ≤ 110 |

---

## Steps

### Step 01.1 - Expose declared gate fields and narrow the reflection catch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Two changes in `PermissionRegistryRepositoryImpl`:
> 1. Add a `@VisibleForTesting` (androidx.annotation) read-only accessor returning the union of every entry's `flavorGates` strings - the set of all declared gate field names - computed from `allEntries`. Name it `declaredFlavorGateFields`.
> 2. In `evaluateFlavorGates`, split the existing broad `catch (e: Exception) { false }`: catch `NoSuchFieldException` separately and log it at `Timber.e` (a misspelled/removed gate field is a developer error that must be fixed, not a normal runtime condition) before returning `false`; keep a general `catch (e: Exception) { false }` for any other reflection failure. The release default stays `false` (safe) in both cases - do not throw. Do not embed a ticket id in the log message; describe the subject in plain English (e.g. "permission flavor-gate references unknown BuildConfig field: <name>").

**Verification:**

- `Grep` - `declaredFlavorGateFields` present in the file.
- `Grep` - `catch (e: NoSuchFieldException)` present in `evaluateFlavorGates`.
- `Grep` - `Timber.e(` present in `evaluateFlavorGates` scope; `Grep -n "Log\.d\("` returns zero hits in the file.
- `Grep` - no `Timber.` line in the file contains `S0456` (permanent logs carry no ticket id).

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS (`declaredFlavorGateFields` accessor added; `catch (e: NoSuchFieldException)` → `Timber.e` then safe `false`; general `catch` preserved; no `Log.d`, no ticket id in logs). Files: PermissionRegistryRepositoryImpl.kt (+11 lines).

---

### Step 01.2 - Add a unit test asserting every declared gate field resolves to a real BuildConfig boolean

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `@Test` to the existing `PermissionRegistryRepositoryImplTest`. For every name in `repo.declaredFlavorGateFields`, reflect `BuildConfig::class.java.getField(name)` and assert it resolves to a field of boolean type; the test must fail (e.g. via `fail()` / assertion) if `getField` throws `NoSuchFieldException` for any declared gate, naming the offending field in the failure message. This runs against the `standardDebug` BuildConfig (`testStandardDebugUnitTest`) - sufficient per research artifact 01 because the gate-field set is uniform across flavors. Do not wrap the reflection in a swallowing try/catch that hides the failure - a missing field must surface as a test failure.

**Verification:**

- `Grep` - a new `@Test` referencing `declaredFlavorGateFields` and `BuildConfig` exists in the test file.
- `Grep` - the test asserts boolean field type (e.g. `Boolean::class` / `type` check) and fails on `NoSuchFieldException`.
- Run `./gradlew :app_v2:testStandardDebugUnitTest --tests "*PermissionRegistryRepositoryImplTest*"` - the targeted class passes (validated at Phase Done).

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 grep PASS (new `@Test` reflects each `declaredFlavorGateFields` name against `BuildConfig`, asserts non-null + `Boolean.TYPE`). Test execution validated at Phase Done. Files: PermissionRegistryRepositoryImplTest.kt (+13 lines).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - test source set + main compile (targeted run BUILD SUCCESSFUL).
- [x] Targeted test `PermissionRegistryRepositoryImplTest` passes (targeted run exit 0, green).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` (registry gained the `declaredFlavorGateFields` accessor).

> **UNBLOCKED 2026-06-16:** S0457 restored the `app_v2` test source set compilation. Targeted `testStandardDebugUnitTest --tests *PermissionRegistryRepositoryImplTest*` BUILD SUCCESSFUL (exit 0) - acceptance test green. Phase 01 Done.

---

## Handoff Notes to Next Phase

A typo in any `flavorGates` string now fails `PermissionRegistryRepositoryImplTest`; valid gates pass unchanged and the release runtime default stays safe. Phase 02 only reconciles catalog/changelog (no FEATURES change per strategic §8).

---

## Rollback Plan

Revert the phase commit(s) - test-and-logging hardening only; no persisted state, no user-facing surface, no runtime behaviour change for valid gates.
