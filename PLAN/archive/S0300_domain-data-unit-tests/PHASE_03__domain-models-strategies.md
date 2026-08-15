# Phase 03 - Domain Models, Strategies, Identity, Input, OCR, Transfer

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

Add JVM unit tests for the remaining in-scope domain packages: models with logic, strategies, identity, input use cases, OCR, and domain-level transfer rules.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `COVERAGE_INVENTORY.md` Phase 03 rows present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/**/*Test.kt` | New | ≤ 300 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/strategy/*Test.kt` | New | ≤ 300 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/{identity,ocr,transfer}/*Test.kt` | New | ≤ 300 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/input/**/*Test.kt` | New | ≤ 300 each |

> Test-only source set. Only models carrying logic (validation, derivation, comparison) are in scope; plain data holders are excluded per the cutoff.

---

## Steps

### Step 03.1 - Cover `domain/model` (logic-bearing only)

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/**/*Test.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> For each `domain/model` (and `domain/model/link`) class the inventory marks in-scope (has validation, derivation, comparison, or factory logic - not a plain data holder), add a `*Test.kt` covering that logic and its edge values. Update inventory rows.

**Verification:**

- `Grep` - each in-scope `domain/model` class has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 03.2 - Cover `domain/strategy`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/strategy/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for each in-scope `domain/strategy` class, covering strategy selection/branching and boundary inputs. Update inventory rows.

**Verification:**

- `Grep` - each in-scope `domain/strategy` class has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 03.3 - Cover `domain/identity`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/identity/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope `domain/identity` classes (identity derivation/equality logic). Update inventory rows.

**Verification:**

- `Grep` - each in-scope `domain/identity` class has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 03.4 - Cover `domain/input` and `domain/input/usecase`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/input/**/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `domain/input` and `domain/input/usecase`, covering input-mapping and binding-resolution logic. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 03.5 - Cover `domain/ocr` and `domain/transfer`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/{ocr,transfer}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `domain/ocr` and `domain/transfer`, covering OCR result handling and domain-level transfer rules (no real I/O). Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 03.6 - Green-run Phase 03 tests

**Files:** - (validation only)
**Depends on:** Steps 03.1–03.5

**Prompt for developer:**

> Run the Phase 03 test classes; confirm each passes via per-class XML reports. Do not fix unrelated pre-existing red tests; do not add new red.

**Verification:**

- Each new Phase 03 test class XML shows `failures="0" errors="0"` (`expected: 0/0 | actual: per report`).
- `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`. 26 new test classes (154 methods); inventory Phase 03: 0 in-scope rows remain untested. `expected: 0 | actual: 0`.
- [x] Project compiles - `:app_v2:compileStandardDebugUnitTestKotlin` exit 0.
- [x] All new Phase 03 test classes green per per-class XML (`failures="0" errors="0"`); no new red.
- [x] `Grep` for `TODO(phase-03)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` zero hits across new files.
- [x] Dev log entry added for the phase.

**Step Log:**

- 2026-05-29 - Covered by one `android-kotlin-developer` batch: models with logic, strategies (Local/Cloud/Ftp/Sftp/Smb), identity, input, ocr, transfer. `PaddleOcrEngine*` deferred to Phase 07 (live in `src/noLegal/`, off standard classpath). Adjacent debt noted: dead `FileNotFoundException` branch in `FileOperationErrorHandler`.

---

## Handoff Notes to Next Phase

All in-scope domain rows in the inventory are covered. Data-layer phases (04–06) follow.

---

## Rollback Plan

Delete the new `domain/**/*Test.kt` files added in this phase. No production code changed.
