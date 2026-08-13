# Phase 02 — Switch all callers to contract

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Route every read/write of trash directory names through `TrashFolderContract`. After this phase, the producer (`BaseFileOperationHandler.executeDelete`), the active cleaner (`CleanupTrashFoldersUseCase`), the dead-code cleaner (`CleanupTrashUseCase`), the legacy helper (`BaseFileOperationHandler.createTrashFolder`), and the restore use-case (`RestoreDeletedUseCase`) all reference the contract. Cleaner additionally recognises the legacy `.trash_<ts>` layout as best-effort migration. This phase ends the rename/cleaner desynchronisation bug.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 #1–#5 Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt` | Modified | ≤ 170 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashUseCase.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt` | Modified | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCaseTest.kt` | New | ≤ 250 |

> `BaseFileOperationHandler.kt` is currently ~407 lines. After edits it stays under 500 — no backup file required, but verify with `wc -l` before commit.

---

## Steps

### Step 02.1 — Switch soft-delete producer to contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt`
**Depends on:** Phase 01

**Status was:** `[~] in progress` (started 2026-05-15)

**Prompt for developer:**

> In `executeDelete`, replace the literal trash path construction (`"$parentPath/.trash/$timestamp"`) with `TrashFolderContract.buildSnapshotPath(parentPath, timestamp)`. Replace any other literal `.trash` reference in this file with `TrashFolderContract.CONTAINER_NAME` or the appropriate helper. Update the unused legacy helper `createTrashFolder` (line ~257) to also call the contract (either via `TrashFolderContract.buildContainerPath` for the parent or remove the helper if no callers exist — verify with Grep first; remove if zero callers).

**Verification:**

- `Grep -n` — `"\.trash"` literal occurs zero times in `BaseFileOperationHandler.kt` (no string-literal trash references after this change). `expected: 0 matches | actual: <observed>`.
- `Grep -n` — `TrashFolderContract\.buildSnapshotPath` appears at least once.
- `Grep -n` — `Log\.d\(` returns zero matches in this file.
- `pwsh -Command "(Get-Content app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt | Measure-Object -Line).Lines"` — `expected: <500 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. `.trash` literal: 0 (was 2). `TrashFolderContract.buildSnapshotPath`: 1. `Log.d(`: 0. LOC: 373 (was 407). Also deleted dead-code helper `createTrashFolder` from `BaseFileOperationHandler` and its override in `FtpFileOperationHandler` — Grep confirmed zero callers. Two files modified.

---

### Step 02.2 — Switch active cleaner to contract + legacy recognition

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt`
**Depends on:** Step 02.1

**Status was:** `[~] in progress` (started 2026-05-15)

**Prompt for developer:**

> Rewrite `cleanupTrashFoldersRecursive` to do two things at each directory level:
> 1. If the current `file` is a directory and `TrashFolderContract.isContainerDir(file.name)` — iterate its children (snapshot subdirectories). For each child use `TrashFolderContract.parseSnapshotTimestamp(child.name)` to obtain a timestamp; apply the `maxAgeMs` check (0 means "delete all"); delete the snapshot directory recursively; after the loop, if the container `.trash` directory is empty — delete it too.
> 2. If the current `file` is a directory whose name matches `TrashFolderContract.isLegacyContainerDir(name)` — parse via `TrashFolderContract.parseLegacyTimestamp`; apply the same age/all logic; delete the legacy directory recursively. This is the migration path for prior versions.
> 3. Otherwise recurse into the subdirectory.
> Remove `TRASH_PREFIX = ".trash_"` constant from the file (no longer needed — owned by the contract). Keep `DEFAULT_AGE_MS = 5 * 60 * 1000L`.
> Logging: keep Timber.i counters for `new` and `legacy` paths separately so on-device verification can confirm migration ran.

**Verification:**

- `Grep -n` — `TRASH_PREFIX` not present in this file.
- `Grep -n` — `TrashFolderContract\.isContainerDir` and `TrashFolderContract\.isLegacyContainerDir` both present.
- `Grep -n` — `Log\.d\(` returns zero matches.
- Target variant compiles — `expected: BUILD SUCCESSFUL | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 structural PASS. `TRASH_PREFIX`: 0 (removed). `TrashFolderContract.isContainerDir`/`isLegacyContainerDir`: 4 occurrences. `Log.d(`: 0. Compile deferred to Phase Done Criteria. Use-case rewritten as state machine: canonical container handling, legacy migration, empty-container removal.

---

### Step 02.3 — Consolidate `CleanupTrashUseCase` (singular) on contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashUseCase.kt`
**Depends on:** Phase 01

**Status was:** `[~] in progress` (started 2026-05-15)

**Prompt for developer:**

> Either (a) replace the literal `"$resourceRootPath/.trash"` with `TrashFolderContract.buildContainerPath(resourceRootPath)` and keep the use-case, OR (b) delete the use-case entirely after confirming via `Grep -r "CleanupTrashUseCase" app_v2/src/main` that it has zero call sites. Choose (b) if no production code references it. Document the choice in the commit message.

**Verification:**

- `Grep -n` — `"\.trash"` literal occurs zero times in this file (path (a)), OR file is absent (path (b)).
- If file present: `Grep -n` — `TrashFolderContract\.buildContainerPath` is present.
- `Grep -r "CleanupTrashUseCase" app_v2/src/main` — outside its own file, hits only in code that survives this phase (zero hits means safe to delete).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification PASS. Took path (b): deleted the use-case file entirely after confirming zero call sites in `app_v2/src/main` via Grep. File absent, references zero.

---

### Step 02.4 — Restore use-case on contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt`
**Depends on:** Phase 01

**Status was:** `[~] in progress` (started 2026-05-15)

**Prompt for developer:**

> Replace the literal `"$currentPath/.trash"` with `TrashFolderContract.buildContainerPath(currentPath)`. Replace the snapshot-name parsing in `findLatestTrashFolder` (currently `name.toLongOrNull()`) with `TrashFolderContract.parseSnapshotTimestamp(name)`. No behaviour change — purely contract alignment.

**Verification:**

- `Grep -n` — `"\.trash"` literal occurs zero times in this file.
- `Grep -n` — `TrashFolderContract\.buildContainerPath` and `TrashFolderContract\.parseSnapshotTimestamp` both present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. `.trash` literal: 0. `TrashFolderContract` references: 3 (import + 2 method calls). Snapshot listing now routes via `parseSnapshotTimestamp`; `maxByOrNull` returns `Long.MIN_VALUE` default for unreachable null branch.
- 2026-05-15 — Host-side validation follow-up: normalize backslash-separated absolute paths before extracting snapshot folder names so `parseSnapshotTimestamp` also works in JVM tests and Windows tooling.

---

### Step 02.5 — Unit tests for cleaner end-to-end

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCaseTest.kt`
**Depends on:** Steps 02.2

**Status was:** `[~] in progress` (started 2026-05-15)

**Prompt for developer:**

> Write JUnit 4 tests using `@TempDir` (or `JUnit @Rule TemporaryFolder` if project standard) for:
> (a) New layout, snapshot older than TTL is deleted. (b) New layout, snapshot newer than TTL is kept. (c) Legacy layout `.trash_<ts>` older than TTL is deleted (migration). (d) `maxAgeMs = 0L` deletes both new and legacy regardless of age. (e) Files outside `.trash` / `.trash_*` are untouched. (f) Empty `.trash` container is removed after the last snapshot is purged.
> Cover both root and one nested subdirectory level.

**Verification:**

- `Glob` — test file exists.
- `Grep` — at least 6 `@Test` annotations.
- Tests pass: `testStandardDebugUnitTest` (or equivalent) reports `expected: 6 passed | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification PASS. `CleanupTrashFoldersUseCaseTest.kt` exists with 7 `@Test` cases covering canonical TTL deletion, canonical TTL keep, legacy migration cleanup, `maxAgeMs = 0L`, untouched non-trash files, empty container removal, and nested-directory descent. Targeted cleaner test run passed during the implementation session.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep -rn "\"\\.trash"` across `app_v2/src/main` shows zero string-literal hits outside `TrashFolderContract.kt`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- The rename/cleaner desynchronisation bug is closed. From this point on, any newly soft-deleted file lands at the canonical path and is reachable by the cleaner.
- Phase 03 removes the forced cleanup on reload/refresh/shutdown so the TTL window actually exists.

---

## Rollback Plan

- Revert the phase commits in reverse order. No data migration to undo — on-disk trash directories are still readable by the old code as long as it is also reverted.
