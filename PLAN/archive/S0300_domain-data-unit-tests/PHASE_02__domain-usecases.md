# Phase 02 - Domain Use Cases & Core Logic

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Add JVM unit tests for every in-scope class in the domain use-case and core-logic packages, using the Phase 01 harness. No production code changes beyond minimal visibility adjustments needed for isolation.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (harness + inventory available).
- [ ] `COVERAGE_INVENTORY.md` rows for Phase 02 are present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/**/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/*Test.kt` | New | ≤ 300 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/{mutation,verifier,hash,path,files}/*Test.kt` | New | ≤ 300 each |

> Test-only source set. Each step covers one inventory batch. Reuse `testing/` helpers; do not re-declare dispatcher rules or fakes.

---

## Steps

### Step 02.1 - Cover `domain/usecase` (root)

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/*Test.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> For each in-scope class in `domain/usecase` (root, excluding `link`/`scan` subpackages handled in 02.2), add a `*Test.kt` covering its branches, transformations, and error paths. Drive coroutines with `MainDispatcherRule`; supply dependencies via `testing/fakes` or MockK. Assert observable results and recorded interactions, not implementation steps. Flip each class's status column in `COVERAGE_INVENTORY.md`.

**Verification:**

- `Grep` - every in-scope `domain/usecase` (root) inventory row maps to an existing `*Test.kt` (spot-check: `expected: N in-scope classes | actual: N test files`).
- `Grep -n "Log\.d\("` - zero hits across new files.
- `Grep` - `runTest` present where the class under test is suspendable.

**Status:** `[x]` done

---

### Step 02.2 - Cover `domain/usecase/link`, `/scan`, `/link/streaming`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/{link,scan}/**/*Test.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add tests for in-scope classes in `domain/usecase/link`, `domain/usecase/scan`, and `domain/usecase/link/streaming`. Cover link resolution branching, scan filtering/ordering rules, and streaming-path edge cases. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these subpackages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 02.3 - Cover `domain/playback`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope `domain/playback` classes (playback decision/ordering logic). Update inventory rows.

**Verification:**

- `Grep` - each in-scope `domain/playback` class has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 02.4 - Cover `domain/mutation` and `domain/verifier`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/{mutation,verifier}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `domain/mutation` and `domain/verifier`, covering state-mutation rules and verification predicates including failure cases. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 02.5 - Cover `domain/hash`, `domain/path`, `domain/files`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/{hash,path,files}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `domain/hash`, `domain/path`, and `domain/files` (hashing logic, path normalization/edge cases, file-rule helpers). Use `TemporaryFolder` for any filesystem-shaped logic. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 02.6 - Green-run Phase 02 tests

**Files:** - (validation only)
**Depends on:** Steps 02.1–02.5

**Prompt for developer:**

> Run the Phase 02 test classes and confirm each passes. Use per-class XML reports under `app_v2/build/test-results/testStandardDebugUnitTest/` to verify own work (the full suite carries pre-existing red tests outside this spec - do not fix them, do not add new red).

**Verification:**

- For each new Phase 02 test class: its XML report shows `failures="0" errors="0"` (`expected: 0/0 | actual: per report`).
- `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`. Covered via 3 sequential agent batches (21 + 18 + 17 = 56 new test classes, ~338 methods); ~46 rows reclassified `out` (Android/Robolectric-bound or trivial). Inventory Phase 02: 0 in-scope rows remain untested. `expected: 0 | actual: 0`.
- [x] Project compiles - `:app_v2:compileStandardDebugUnitTestKotlin` exit 0.
- [x] All new Phase 02 test classes are green per per-class XML (`failures="0" errors="0"`); no new red; ~26 pre-existing unrelated red not touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` zero hits across new domain test files. `expected: 0 | actual: 0`.
- [x] Dev log entry added for the phase.

**Step Log:**

- 2026-05-29 - Phase covered by 3 orchestrated `android-kotlin-developer` batches. Steps 02.1-02.5 (usecase/scan/link/playback/mutation/verifier/hash/path/files in-scope classes) all written + green; step 02.6 green-run satisfied via per-class XML sweep (exit 0). Honest partial coverage noted in inventory for classes with Android-only branches. Adjacent production bug flagged: `ConnectionThrottleManager.setLastSpeedMbps` format-string crash (out of S0300 scope).

---

## Handoff Notes to Next Phase

Domain use-case and core-logic rows in `COVERAGE_INVENTORY.md` are flipped to covered. Phase 03 covers remaining domain (models, strategies, identity, input, ocr, transfer).

---

## Rollback Plan

Delete the new `domain/**/*Test.kt` files. No production code or user-facing surface changed.
