# Phase 01 - Request source ownership

**Strategic spec:** [`../S1370_share-receive-copy-dies-with-activity.md`](../S1370_share-receive-copy-dies-with-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** -
**Completed:** 2026-08-03

---

## Objective

Teach the persisted background-transfer request to declare that its sources are staged storage owned by the operation, and make the worker delete them on every terminal outcome.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - all four are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModels.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | Modified | ≤ 700 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModelsSerializationTest.kt` | Modified | ≤ 300 |

---

## Steps

### Step 01.1 - Add the staged-sources flag to the persisted request

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModels.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `sourcesOwnedByOperation: Boolean = false` property to `BrowseFileTransferRequest`, annotated `@SerializedName("sourcesOwnedByOperation")` like every other persisted field. Add `stagingDirectoryPath: String? = null` with its own `@SerializedName`, holding the directory whose contents the operation may remove once every source has been consumed. Keep both at the end of the constructor so existing positional construction sites keep compiling, and keep the defaults so a request written by a previous app version deserializes without them.

**Why:**

Strategic §3.2 requires the persisted request to survive an app update between write and read, and §5.1 makes the request the single place that declares who owns the sources; without an explicit default-safe field the worker cannot tell a staged share copy from a normal Browse transfer.

**Verification:**

- `Grep` - `sourcesOwnedByOperation` matches in `BrowseFileTransferModels.kt`.
- `Grep` - `@SerializedName("sourcesOwnedByOperation")` matches exactly once.
- `Grep` - `@SerializedName("stagingDirectoryPath")` matches exactly once.
- `Grep` - `val sourcesOwnedByOperation: Boolean = false` present (default retained).

**Status:** `[x]` done

---

### Step 01.2 - Purge staged sources in the worker's terminal path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `doWork()`, extend the existing `finally` block: when the request read at entry has `sourcesOwnedByOperation == true`, delete every source file it names and then the directory named by `stagingDirectoryPath` if it is empty. Run the deletion inside `withContext(NonCancellable)` on the IO dispatcher so a cancelled work still cleans up, and log the outcome with `Timber.i` naming the file count. Do nothing when the flag is absent or false.

**Why:**

Strategic §2 goal 3 and ADR-2 make the executor the owner of staged sources on every terminal outcome; running the purge only on success would leave the share cache growing after a failure or a cancel, which is the regression §7 lists.

**Verification:**

- `Grep` - `sourcesOwnedByOperation` matches in `BrowseFileTransferWorker.kt`.
- `Grep` - `NonCancellable` matches at least twice in that file (existing cancellation handler plus the new purge).
- `Grep` - `stagingDirectoryPath` matches in that file.
- `Grep -n "Log\.d\("` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.3 - Keep the purge out of the non-staged path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Guard the purge so it can never touch a Browse transfer: refuse to delete when `sourcesOwnedByOperation` is false, and refuse when `stagingDirectoryPath` is null or is not a parent of every source path. Log a `Timber.w` naming the mismatch when the guard refuses.

**Why:**

Strategic §5.1 scopes the new ownership to callers that stage their data, and a Browse copy names live user files as sources - deleting those would turn a copy into a destructive operation.

**Verification:**

- `Grep` - `Timber.w` matches in the purge block of `BrowseFileTransferWorker.kt`.
- `Grep` - `startsWith` or `canonicalPath` used in the guard (parent containment check present).

**Status:** `[x]` done

---

### Step 01.4 - Cover backward compatibility with a test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModelsSerializationTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a test that deserializes a JSON payload written without the two new keys and asserts `sourcesOwnedByOperation` is `false` and `stagingDirectoryPath` is `null`. Add a second test that round-trips a request carrying both new values and asserts the wire keys are exactly `sourcesOwnedByOperation` and `stagingDirectoryPath`.

**Why:**

Strategic §3.2 states the request is read back by a worker that can outlive an app update, so a silently renamed or missing key would desync the format - the failure mode the file's existing `@SerializedName` comment already records for S0957.

**Verification:**

- `Grep` - `sourcesOwnedByOperation` matches in the test file.
- `Grep` - `stagingDirectoryPath` matches in the test file.
- `.\a.ps1 fu` - the `BrowseFileTransferModelsSerializationTest` class passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The persisted request can now declare staged ownership, and the worker honours it on success, failure and cancel. Phase 02 may hand sources over without arranging its own cleanup.

---

## Rollback Plan

Revert phase commit(s) - the new fields default to the previous behaviour, so a stored request written by this phase stays readable by the reverted code.
