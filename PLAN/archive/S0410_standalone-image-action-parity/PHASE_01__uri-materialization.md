# Phase 01 - URI materialization foundation

**Strategic spec:** [`../S0410_standalone-image-action-parity.md`](../S0410_standalone-image-action-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Introduce a use case that copies a readable external image URI to a private cache file and returns its local path, plus a delete hook for the temp file. No UI or host wiring yet.

---

## Prerequisites

- [ ] Strategic §6.2 research item Resolved (save target & temp lifecycle confirmed).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeUriToFileUseCase.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Add MaterializeUriToFileUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeUriToFileUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MaterializeUriToFileUseCase` (constructor-injected `@ApplicationContext Context`). A suspend `invoke(uri: Uri, displayName: String?)` runs on `Dispatchers.IO`: open the URI via `contentResolver.openInputStream`, copy bytes to a new file under `context.cacheDir` (subfolder `standalone_edit`, filename derived from `displayName` extension or sniffed MIME, falling back to `.jpg`), and return the absolute path as a result type (e.g. `Materialized(absolutePath)` / `Failed`). Do not assume the URI has a filesystem path - this is the fallback for `ResolveLocalPathFromUriUseCase.NotLocal`. Read the existing `ResolveLocalPathFromUriUseCase` for the project's URI-handling conventions and mirror its structure.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeUriToFileUseCase.kt` exists.
- `Grep` - `class MaterializeUriToFileUseCase` matches exactly once.
- `Grep` - `cacheDir` present.
- `Grep` - `openInputStream` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 4/4 PASS. Files: MaterializeUriToFileUseCase.kt (New). Dev log recorded.

---

### Step 01.2 - Add temp-file cleanup hook

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MaterializeUriToFileUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `cleanup(absolutePath: String)` (or `cleanupAll()`) function that deletes the materialized cache file(s) under the `standalone_edit` subfolder, so the host can release temp storage on destroy / after a completed operation. Keep it side-effect-safe (no throw if the file is already gone).

**Verification:**

- `Grep` - `fun cleanup` present in the file.
- `Grep` - `standalone_edit` referenced for both create and cleanup paths.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. `SUBFOLDER = standalone_edit` used by invoke (create) + cleanup/cleanupAll. Files: MaterializeUriToFileUseCase.kt. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the new file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public use case).

---

## Handoff Notes to Next Phase

A use case now turns any readable image URI into a local cache path. Phases 03 and 04 consume it to enable file-based operations (crop-to-file, compress, draw save-as) on non-local images. Output files of those operations go to MediaStore Pictures - this use case only produces the editable *source*.

---

## Rollback Plan

Revert phase commit - new file only, no caller yet, no data migration.
