# Phase 05 — Regression Entry Points and Tests

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Cover the corrected atomic-copy contract with focused unit tests and verify that the two user-cancel entrypoints do not manage `*.temp_copy` ownership themselves.

---

## Prerequisites

- [ ] Phase 04 ✅ Done.
- [ ] The SMB cancel path now reaches `AtomicFileOperationStrategy` as `CancellationException`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Audit only unless defect found | ≤ 830 if modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Audit only unless defect found | ≤ 480 if modified |

---

## Steps

### Step 05.1 — Create a focused test scaffold

**Files:** `AtomicFileOperationStrategyTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new unit test file for `AtomicFileOperationStrategy`. Use the existing `runTest` + `mockk` style from `UnifiedFileOperationHandlerDirectoryTest.kt`. Provide a fake delegate or mock strategy that can:
>
> - create a temp file and report success,
> - throw `CancellationException` after partial temp-file write,
> - report success without leaving the temp file behind.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt` returns one match.
- `Grep -n "class AtomicFileOperationStrategyTest|runTest|mockk" "app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt"` returns hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 3/3 PASS. Files: app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt (+103 LOC). Focused test scaffold created with `runTest`, `mockk`, and a configurable fake delegate for success, cancellation, and missing-temp scenarios.

---

### Step 05.2 — Add success-path test coverage

**Files:** `AtomicFileOperationStrategyTest.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a test that verifies: delegate writes the temp file, `copyFile` returns success, final destination exists, and the temp file is gone after rename. Keep the test local-file based so it runs as a plain JVM unit test.

**Verification:**

- `Grep -n "success.*rename|rename.*success|destination exists" "app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt"` returns at least one success-path test name or assertion block.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt (+17 LOC). Success-path test now verifies final destination exists, temp file is gone, and rename preserves content.

---

### Step 05.3 — Add cancellation and invariant-failure coverage

**Files:** `AtomicFileOperationStrategyTest.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add at least two negative tests:
>
> - `CancellationException` after partial temp write: temp file is cleaned, exception is rethrown.
> - success result with missing temp file: `copyFile` returns failure tied to the invariant path (`temp-missing-invariant` or equivalent).
>
> These tests close strategic criterion §11.5.

**Verification:**

- `Grep -n "CancellationException|cancelled|partial write" "app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt"` returns at least one test hit.
- `Grep -n "temp-missing-invariant|missing temp|postcondition" "app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt"` returns at least one test hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 3/3 PASS. Files: app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt (+27 LOC). Negative coverage added for partial-write cancellation cleanup and missing-temp postcondition failure.

---

### Step 05.4 — Audit UI cancel entrypoints and run the narrow test gate

**Files:** `BrowseFileOperationsManager.kt`, `FileOperationsHandler.kt`, `AtomicFileOperationStrategyTest.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Audit both user-cancel entrypoints:
>
> - `BrowseFileOperationsManager` share path
> - `FileOperationsHandler` share path
>
> Confirm they cancel the deferred copy and clean only their own share-cache temp file, not `*.temp_copy` ownership. Modify them only if the new atomic contract exposes duplicate cancellation error handling.
>
> Then run:
>
> ```powershell
> ./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategyTest"
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```

**Verification:**

- `Grep -n "User cancelled network share copy" "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt"` returns one hit.
- `Grep -n "User cancelled network share copy" "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt"` returns one hit.
- `Grep -n "temp_copy" "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt"` returns no hits.
- `Grep -n "temp_copy" "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt"` returns no hits.
- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategyTest"` exits with code 0.
- `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 6/6 PASS. Audit PASS: both `User cancelled network share copy` entrypoints only cancel the deferred copy and contain no `temp_copy` ownership. `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategyTest"` PASS after fixing unrelated fake repository stubs in `SendResourcesToWatchUseCaseTest.kt`. `./gradlew.bat :app_v2:compileStandardDebugKotlin` PASS.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `AtomicFileOperationStrategyTest` exists and covers success + cancellation + invariant failure.
- [ ] Both UI cancel entrypoints remain outside `*.temp_copy` ownership.
- [ ] Narrow test gate passes.

---

## Handoff Notes to Next Phase

Final phase is documentation, catalog, journal sync, and `/spec-check`. No feature-doc bullet is expected for S0069 because the fix is internal reliability work only.

---

## Rollback Plan

Delete the new test file or revert it, then re-run the narrow test gate. If UI entrypoints changed, revert those specific edits only.
