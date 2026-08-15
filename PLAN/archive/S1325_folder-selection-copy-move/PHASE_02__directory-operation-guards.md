# Phase 02 - Directory operation guards

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Refuse unsafe directory copy / move before any bytes move - destination inside source, destination equal to source, document-tree destination - and stop the local recursive walk from following a symlink loop.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Read `research/01__saf-tree-destination.md` and `research/03__current-state-directory-ops.md`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt` | Modified | ≤ 680 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandlerDirectoryGuardTest.kt` | New | ≤ 220 |

Both modified Kotlin files exceed 500 LOC - Step 02.0 takes backups first.

---

## Steps

### Step 02.0 - Back up the two large files

**Files:** `temp/S1325/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `UnifiedFileOperationHandler.kt` and `LocalOperationStrategy.kt` into `temp/S1325/` with a timestamped name before editing (CLAUDE.md Rule 5).

**Verification:**

- `Glob` - two files matching `temp/S1325/*UnifiedFileOperationHandler*.kt` and `temp/S1325/*LocalOperationStrategy*.kt` exist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 1/1 PASS. `temp/S1325/UnifiedFileOperationHandler_20260731_013954.kt`, `temp/S1325/LocalOperationStrategy_20260731_013954.kt`.

---

### Step 02.1 - Add the guard predicates to the directory dispatch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`
**Depends on:** Step 02.0

**Prompt for developer:**

> In `executeCopyDirectory` and `executeMoveDirectory`, before resolving the strategy, reject three cases and return `Result.failure` carrying a distinguishable exception type each: destination parent path equals the source path or is nested inside it (compare normalized, separator-terminated paths, protocol prefix included); destination parent equals the source's own parent for a MOVE, which would be a no-op; destination parent starts with `content:` - a document-tree target that the local strategy cannot address. Introduce one private helper per check rather than inlining the string work twice, and a small `DirectoryOperationRefusal` exception type in the same file so the caller can map a refusal to its message.

**Verification:**

- `Grep` - `DirectoryOperationRefusal` matches in `UnifiedFileOperationHandler.kt` at least three times (declaration plus both call sites).
- `Grep` - `startsWith("content:` present in `UnifiedFileOperationHandler.kt`.
- `Grep` - `executeCopyDirectory` body contains the nested-destination helper call; record the helper name.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `DirectoryOperationRefusal` 8 hits, `startsWith("content:` 1 hit, helper `refuseUnsafeDirectoryOperation` called from both `executeCopyDirectory` (line 503) and `executeMoveDirectory` (line 541).
- 2026-07-31 - Deviation from the prompt, self-caught while writing the test: the same-parent case is refused for **copy** as well, not only for move. The tree lands at `destination/<name>`, which for the current parent is the source path itself, so the per-entry copy would overwrite its own input instead of producing a second copy. Refusing only the move would have shipped a data-losing copy.

---

### Step 02.2 - Guard the local recursive walk against loops

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> `collectAllFiles` recurses into every child directory with no protection. Track visited canonical paths in a set passed down the recursion and skip a directory whose canonical path was already visited, so a symlink cycle terminates. Also cap depth with a named constant in the companion object and log a single `Timber.w` when the cap is hit - no silent truncation.

**Verification:**

- `Grep` - `canonicalPath` present in `LocalOperationStrategy.kt`.
- `Grep` - a companion-object depth constant matches exactly once; record its name and value.
- `Grep` - `Log.d(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `canonicalPath` 2 hits, companion constant `MAX_DIRECTORY_DEPTH = 64` 1 hit, `Log.d(` 0 hits. Visited-set and depth cap both log a `Timber.w` before returning, so a truncated walk is never silent.

---

### Step 02.3 - Add the refusal strings in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add three keys in one lockstep call each via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>`: `error_folder_into_itself`, `error_folder_same_location`, `error_folder_destination_not_supported`. Each message names what the user did and what to do instead, per `docs/COMMUNICATION_POLICY.md` §2 message formula; run the §6 tone checklist before writing them. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "error_folder"`.

**Verification:**

- `Grep` - each of the three keys matches exactly once in each of the three `strings.xml` files.
- `check_strings_localized.ps1` exits 0 - record `expected: 0 | actual: <observed>`.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Keys added to `strings_file_operations.xml` in EN/RU/UK via one `set-android-string.ps1 -Action add` call each. `check_strings_localized.ps1 -KeyPrefix error_folder` - expected: 0 | actual: 0 (10 keys, all locales). Each message names the obstacle and the next action; no raw exception text.

---

### Step 02.4 - Unit-test the guards

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandlerDirectoryGuardTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Mirror the existing `UnifiedFileOperationHandlerDirectoryTest` fixture style with mocked strategies. Assert that copy into a nested destination, move into the same parent, and any `content://` destination all fail with `DirectoryOperationRefusal` and that the strategy is never invoked in those cases. Assert a legitimate copy still delegates to the strategy.

**Verification:**

- `Glob` - test file exists.
- Targeted run `--tests *UnifiedFileOperationHandlerDirectoryGuardTest*`; record `expected: BUILD SUCCESSFUL | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Run through `check-standard-fast.ps1 -Mode Unit -Tests "*UnifiedFileOperationHandlerDirectory*"` (takes `BUILD.LOCK`) - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL, exit 0. Result XML read directly: `UnifiedFileOperationHandlerDirectoryGuardTest` tests=6 failures=0 errors=0, mtime 01:44:28 - fresh, not a stale report.
- 2026-07-31 - Six cases: copy into own subdirectory, copy into the source itself, sibling with a longer name still allowed, move into current parent, copy into current parent (self-copy), document-tree destination for both operations. Each refusal also asserts the strategy was never called.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - unit-test run compiled main + test sources, BUILD SUCCESSFUL, exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added - `post-change.ps1` (Mixed) PASS in 44958 ms, exit 0; every gate PASS or SKIP.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same closure.
- [x] Phase-boundary audit run - Layers 1-3. Findings: none at P0/P1. P2 noted and accepted: the visited-set walk now protects `deleteDirectory` and `getDirectoryInfo` as well, and the whole-tree list is still materialised in memory by the local strategy - that stays until the tree manager in Phase 03 streams entries. `assert-detekt -ChangedFiles` over all three touched Kotlin files - PASS, exit 0.

---

## Handoff Notes to Next Phase

Every directory copy / move now passes one guarded entry point, so Phase 03 can enable cross-resource-type transfer without re-deriving the safety checks. The refusal exception type is the mapping point for user-facing messages in Phase 06.

---

## Rollback Plan

Revert phase commit(s). No data migration; the guards only refuse operations that previously failed later or unsafely.
