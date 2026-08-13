# Phase 03 - Cross-resource-type tree transfer

**Strategic spec:** [`../S1325_folder-selection-copy-move.md`](../S1325_folder-selection-copy-move.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Copy and move a directory tree between two different protocols by walking the source, re-creating the structure at the destination and transferring each entry through the existing per-file path.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the guards run before anything in this phase.
- [ ] Read `research/02__local-write-path-parity.md` and `research/03__current-state-directory-ops.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManager.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 760 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManagerTest.kt` | New | ≤ 260 |

`LocalOperationStrategy.kt`, `SmbOperationStrategy.kt` and `UnifiedFileOperationHandler.kt` exceed 500 LOC - back them up into `temp/S1325/` before editing.

---

## Steps

### Step 03.1 - Add a typed directory listing to the strategy contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `data class DirectoryEntry(val path: String, val name: String, val isDirectory: Boolean, val size: Long)` beside the existing `DirectoryInfo`, and a `suspend fun listEntries(path: String): Result<List<DirectoryEntry>>` on the interface with a default implementation built from the existing `listFiles` plus a per-entry `isDirectory` call. The default keeps every protocol working without an override; overrides exist to avoid the per-entry round trip.

**Verification:**

- `Grep` - `data class DirectoryEntry` matches exactly once in the repository.
- `Grep` - `suspend fun listEntries(` matches exactly once in `FileOperationStrategy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `data class DirectoryEntry` 1 hit, `suspend fun listEntries(` 1 hit with a default built on `listFiles` + per-entry `isDirectory`.

---

### Step 03.2 - Override the listing where it is cheap

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Override `listEntries` in the local strategy from a single `File.listFiles()` pass, and in the SMB strategy from the listing the SMB client already returns with type and size. Leave SFTP, FTP and cloud on the interface default and state that choice in a KDoc line on each override - the default is correct there, only chattier, and narrowing it is a later optimisation.

**Verification:**

- `Grep` - `override suspend fun listEntries(` matches exactly twice in `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/`.
- `Grep` - `Log.d(` returns zero hits in both edited files.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Overrides in `LocalOperationStrategy` (single `File.listFiles()` pass) and `SmbOperationStrategy` (the SMB listing already reports type and size); SFTP, FTP and cloud stay on the interface default, stated in each override's KDoc.

---

### Step 03.3 - Add the tree transfer manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `DirectoryTreeTransferManager` with `@Inject constructor` taking the strategy map and the temp-file manager already used by `UnifiedFileOperationHandler`. Expose `suspend fun copyTree(sourcePath: String, destinationPath: String, progressCallback: ((Int, Int, String) -> Unit)?): Result<Int>` and `suspend fun moveTree(..)` with the same shape. `copyTree` walks the source breadth-first through `listEntries`, creates each destination directory through the destination strategy's `createDirectory`, and transfers each file through the same download-to-temp then upload path the single-file cross-protocol copy uses, one entry at a time so no whole-tree list is held. `moveTree` calls `copyTree` and then deletes each source entry only after its own copy returned success, deepest path first. Report progress per entry. Do not swallow `CancellationException` - rethrow it so the caller sees a cancelled job.

**Verification:**

- `Glob` - `DirectoryTreeTransferManager.kt` exists.
- `Grep` - `class DirectoryTreeTransferManager` matches exactly once.
- `Grep` - `suspend fun copyTree(` and `suspend fun moveTree(` each match exactly once.
- `Grep` - `catch (e: CancellationException)` either absent or immediately followed by a `throw`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS. `DirectoryTreeTransferManager` 1 declaration, `copyTree`/`moveTree` 1 each, zero `catch (e: CancellationException)` - the walk calls `ensureActive()` per queued directory and per entry and never wraps the loop in a broad catch, so cancellation propagates. Per-entry transfer reuses each strategy's own `copyFile`; only remote-to-different-remote takes the temp-file hop.

---

### Step 03.4 - Route cross-protocol directory operations to the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Inject `DirectoryTreeTransferManager`. In `executeCopyDirectory` and `executeMoveDirectory`, replace the protocol-mismatch `UnsupportedOperationException` branch with a call into `copyTree` / `moveTree`, keeping the same-protocol path on the existing per-protocol strategy implementations. The guards from Phase 02 stay ahead of both branches. Keep the returned `Result<Int>` contract - number of entries transferred.

**Verification:**

- `Grep` - `Cross-protocol directory copy is not supported` returns zero hits in `app_v2/src/main`.
- `Grep` - `directoryTreeTransferManager` matches at least twice in `UnifiedFileOperationHandler.kt`.
- `Grep` - `sourceProtocol != destProtocol` still present, now selecting the manager branch.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `Cross-protocol directory copy is not supported` 0 hits in `src/main`, `directoryTreeTransferManager` 3 hits, protocol branch 2 hits. `requireSourceEnabled(sourcePath)` kept on the cross-protocol branch so a disabled remote source is still refused (S0391).
- 2026-07-31 - Constructor gained a parameter, so the three handler tests were updated in the same step (CLAUDE.md: a constructor change compiles the tests too).

---

### Step 03.5 - Unit-test the tree manager

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManagerTest.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> With mocked strategies, assert: a two-level tree produces one `createDirectory` per source directory and one file transfer per source file, in an order where a directory is created before its children; `moveTree` deletes a source entry only after that entry's copy succeeded; a failed entry copy leaves the corresponding source entry untouched and is reported in the returned count; cancellation propagates instead of being converted into a failure `Result`.

**Verification:**

- `Glob` - test file exists.
- Targeted run `--tests *DirectoryTreeTransferManagerTest*`; record `expected: BUILD SUCCESSFUL | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - First run FAILED: 4 pre-existing tests asserted the removed behaviour (`copyDirectory/moveDirectory cross-protocol returns UnsupportedOperationException`). Rewritten to assert delegation to the tree manager - that assertion was the old contract, not a regression.
- 2026-07-31 - Second run: expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL, exit 0. Fresh result XML at 01:54:28 - DirectoryTreeTransferManagerTest 6/0/0, UnifiedFileOperationHandlerDirectoryGuardTest 6/0/0, UnifiedFileOperationHandlerDirectoryTest 8/0/0, UnifiedFileOperationHandlerTest 12/0/0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - unit run executed `hiltJavaCompileStandardDebug` and `compileStandardDebugUnitTestKotlin`, BUILD SUCCESSFUL, exit 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added via `post-change.ps1` closure.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same closure.
- [x] Phase-boundary audit run. Detekt caught two real Layer-1 findings on first pass (`ReturnCount` in the per-entry copy, `LoopWithTooManyJumpStatements` in the walk) - both fixed by extracting the temp-file hop into its own function and removing both `continue` branches; tests and gate re-run green. Hilt graph validated by the executed `hiltJavaCompileStandardDebug` task: the manager consumes the same `Map<String, FileOperationStrategy>` multibinding the handler already had, with `@JvmSuppressWildcards`. Layer 2: cancellation is checked per queued directory and per entry, no broad catch swallows it. No P0/P1 left.

---

## Handoff Notes to Next Phase

Directory copy and move now succeed for every source/destination protocol combination and accept a per-entry progress callback. Phase 04 consumes that callback; nothing else may call the strategies' directory methods directly.

---

## Rollback Plan

Revert phase commit(s). Same-protocol behaviour is untouched by design, so a rollback restores the previous cross-protocol refusal without affecting working paths.
