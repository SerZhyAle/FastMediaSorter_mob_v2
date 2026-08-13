# Phase 06 - Data Transfer, Link/Auth, Cloud

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 6 / 6
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Add JVM unit tests for the remaining in-scope data packages: transfer engine and strategies, link resolution/cookie/auth, cloud access logic, and the remaining small logic-bearing packages.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `COVERAGE_INVENTORY.md` Phase 06 rows present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/**/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/**/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/cloud/**/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/{browser,input,hash,verifier,permissions}/*Test.kt` | New | ≤ 300 each |

> Test-only source set. Flavor-only cloud/link variants are deferred to Phase 07; this phase covers the shared (`standard`/`main`) implementations only.

---

## Steps

### Step 06.1 - Cover `data/transfer` (engine, strategies, local, access, trash)

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/**/*Test.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add tests for in-scope classes in `data/transfer` and its subpackages (`strategies`, `strategy`, `local`, `access`, `trash`): strategy selection, copy/move decision logic, collision/overwrite rules, and access checks. Use `TemporaryFolder` for filesystem-shaped logic; no real network. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in `data/transfer**` has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 06.2 - Cover `data/link`, `data/link/cookie`, `data/link/auth`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/**/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `data/link`, `data/link/cookie`, and `data/link/auth` (shared implementations only): URL/link parsing, cookie persistence rules, and auth-token handling branches. Fake any network/storage dependency. Update inventory rows.

**Verification:**

- `Grep` - each in-scope shared class in `data/link**` has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 06.3 - Cover `data/cloud` and `data/cloud/helpers`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/cloud/**/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope `data/cloud` and `data/cloud/helpers` classes (shared implementations): auth state machine transitions, token-refresh decision logic, and response mapping. Fake the cloud SDK clients. `data/cloud/glide` integration glue is out-of-scope per the cutoff. Update inventory rows.

**Verification:**

- `Grep` - each in-scope shared class in `data/cloud**` has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 06.4 - Cover `data/browser` and `data/input`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/{browser,input}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `data/browser` and `data/input`: browser-intent/url decision logic and input-data mapping. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 06.5 - Cover `data/hash`, `data/verifier`, `data/permissions`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/{hash,verifier,permissions}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `data/hash`, `data/verifier`, and `data/permissions`: hashing logic, integrity verification branches, and permission-state evaluation. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 06.6 - Green-run Phase 06 tests

**Files:** - (validation only)
**Depends on:** Steps 06.1–06.5

**Prompt for developer:**

> Run Phase 06 test classes; confirm each passes via per-class XML reports. Do not fix unrelated red tests; do not add new red.

**Verification:**

- Each new Phase 06 test class XML shows `failures="0" errors="0"` (`expected: 0/0 | actual: per report`).
- `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`. 27 new test classes (214 methods); ~40 rows reclassified `out`; inventory Phase 06: 0 in-scope shared rows remain (the 9 `link/nolegal` rows belong to Phase 07). `expected: 0 | actual: 0`.
- [x] Project compiles - `:app_v2:compileStandardDebugUnitTestKotlin` exit 0.
- [x] All new Phase 06 test classes green per per-class XML (`failures="0" errors="0"`); no new red; cloud/network SDK clients mocked, no real auth/network.
- [x] `Grep` for `TODO(phase-06)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` zero hits across new files.
- [x] Dev log entry added for the phase.

**Step Log:**

- 2026-05-29 - Covered by one `android-kotlin-developer` batch. Transfer strategies/operations/FileAccess, cloud path+REST mapping utils, hashers, quick verifiers, link/cookie/streaming, input bindings, permissions repos, browser launcher. Pure mapping extracted into tested `*Utils`; Uri/MediaStore/cloud-SDK/Media3/WebView-bound classes reclassified `out`.

---

## Handoff Notes to Next Phase

All shared (`standard`/`main`) in-scope data rows covered. Phase 07 covers flavor-only (`noLegal`/`vr`) data logic in their own test source sets.

---

## Rollback Plan

Delete the new `data/{transfer,link,cloud,browser,input,hash,verifier,permissions}/**/*Test.kt` files. No production code changed.
