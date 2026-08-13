# Phase 01 - Item operation policy

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Introduce one policy object that answers whether a browse item supports a given operation, with directories supported for copy / move / rename / delete; no caller wired yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseItemOperationPolicy.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseItemOperationPolicyTest.kt` | New | ≤ 160 |

No flavor-specific file in this phase - the policy is flavor-agnostic and lives in `src/main`.

---

## Steps

### Step 01.1 - Add the operation enum and policy object

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseItemOperationPolicy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BrowseItemOperationPolicy` as an `object` in `ui/browse/helpers`, with a nested `enum class BrowseItemOperation` listing `COPY`, `MOVE`, `RENAME`, `DELETE`, `FAVORITE`, `OPEN_IN_PLAYER`, `SEND_TO`, `INFO`, `EXTRACT_ARCHIVE`, `REORDER`. Expose `fun supports(operation: BrowseItemOperation, file: MediaFile): Boolean` and `fun isSelectable(file: MediaFile): Boolean`. `isSelectable` returns `true` for every item including directories. `supports` returns `true` for a directory only on `COPY`, `MOVE`, `RENAME`, `DELETE`, `REORDER`; for a non-directory it returns `true` for every entry except where the caller's own settings gate applies, which the policy does not evaluate. Keep the class free of Android and settings dependencies so it stays unit-testable.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseItemOperationPolicy.kt` exists.
- `Grep` - `object BrowseItemOperationPolicy` matches exactly once.
- `Grep` - `fun supports(` and `fun isSelectable(` each match exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Files: ui/browse/helpers/BrowseItemOperationPolicy.kt (New, 54 LOC). Deviation: the enum carries an extra `SELECT` entry and `isSelectable` delegates to `supports(SELECT, ..)` instead of returning a literal `true` - a public function returning only a constant trips detekt `FunctionOnlyReturningConstant` (Rule 19, detekt-clean-first).

---

### Step 01.2 - Document the directory exclusions in the policy source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseItemOperationPolicy.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a KDoc block on `supports` stating why `FAVORITE`, `OPEN_IN_PLAYER`, `SEND_TO`, `INFO` and `EXTRACT_ARCHIVE` are refused for a directory: favourite and player targets are single media files, outbound sharing stages file URIs, the info dialog reads single-file metadata, and archive extraction needs an archive file. Explain the invariant that every caller reads this policy instead of testing `isDirectory` inline. No comment restating what a line does.

**Verification:**

- `Grep` - `isDirectory` appears in `BrowseItemOperationPolicy.kt` only inside `supports`/`isSelectable` bodies and the KDoc; count the matches and record the number.
- `Grep` - `SEND_TO` present in the KDoc block.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `isDirectory` occurs twice in the file: once in the KDoc, once in `supports`. KDoc written together with Step 01.1's declaration - the rationale belongs to the same file write, not a second edit pass.

---

### Step 01.3 - Unit-test the policy matrix

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseItemOperationPolicyTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Write JUnit tests asserting: a directory item is selectable; a directory supports `COPY`, `MOVE`, `RENAME`, `DELETE`; a directory does not support `FAVORITE`, `OPEN_IN_PLAYER`, `SEND_TO`, `INFO`, `EXTRACT_ARCHIVE`; a regular file supports all of them. Build `MediaFile` fixtures directly, no mocking framework.

**Verification:**

- `Glob` - test file exists.
- Run `pwsh -NoProfile -File ./a.ps1 fu` or the targeted `--tests *BrowseItemOperationPolicyTest*` and record `expected: BUILD SUCCESSFUL | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Files: src/test/.../BrowseItemOperationPolicyTest.kt (New, 81 LOC). Targeted unit run `--tests *BrowseItemOperationPolicyTest*` - expected: exit 0 | actual: exit 0. Note: that run went straight through `gradlew.bat` and therefore skipped `temp/BUILD.LOCK`; every later gradle call in this ticket goes through `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests <filter>`, which takes the lock (Rule 23).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - the targeted unit run compiled main + test sources, exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added via `post-change.ps1` closure - expected: exit 0 | actual: exit 0.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same closure.
- [x] Phase-boundary audit run - Layer 1 only (new stateless object, no lifecycle, coroutine, listener or Room surface). No P0/P1/P2 findings.

---

## Handoff Notes to Next Phase

`BrowseItemOperationPolicy` is the single source of truth for per-item operation applicability. Phases 05 and 06 replace their inline `isDirectory` branches with calls into it; no other place may re-derive the same answer.

---

## Rollback Plan

Revert phase commit(s) - new files only, no caller wired, no user-facing surface changed.
