# Phase 01 — Fix Cache Miss Detection

**Strategic spec:** [`../S0084_bugfix-cache-subfolder-mismatch-restore.md`](../S0084_bugfix-cache-subfolder-mismatch-restore.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Fix `PlayerMediaFilesLoader` to distinguish a cold-start cache miss (normal) from a true subfolder mismatch (cache exists but contains a different folder's files), and emit the correct log level for each case.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(foundation phase — N/A)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 500 |

> Backup required: file currently exceeds 500 lines — create timestamped copy in `temp/` before editing (Step 01.1).

---

## Steps

### Step 01.1 — Backup PlayerMediaFilesLoader before edit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `PlayerMediaFilesLoader.kt` to `temp/PlayerMediaFilesLoader_backup_<YYYYMMDD_HHMMSS>.kt`. Confirm the backup exists before proceeding.

**Verification:**

- `Glob` — `temp/PlayerMediaFilesLoader_backup_*.kt` returns at least one match.

**Status:** `[ ]` not done

---

### Step 01.2 — Split cache-miss condition into cold-start vs. true subfolder mismatch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `PlayerMediaFilesLoader.kt`, locate the block starting at `val allFiles = if (cachedFiles != null && ...)`.
>
> Currently the log branch `if (!cacheMatchesInitialFile)` fires both when `cachedFiles == null` (in-memory cache is empty — normal cold start) and when `cachedFiles != null` but doesn't contain the file (true scope mismatch).
>
> Replace the single `if (!cacheMatchesInitialFile)` log line with a two-branch check:
>
> ```kotlin
> if (cachedFiles == null) {
>     Timber.d("PlayerMediaFilesLoader: cache empty (cold start), loading from $initialFileDir")
> } else if (!cacheMatchesInitialFile) {
>     Timber.w("PlayerMediaFilesLoader: cache scope mismatch — cached ${cachedFiles.size} files do not contain initialFilePath=$initialFilePath, reloading from $initialFileDir")
> } else if (cacheHasOnlyDirectories) {
>     Timber.w("Cache contains only directories (${cachedFiles.size} items), loading actual files from current path")
> } else {
>     Timber.w("Cache miss! Loading files via UseCase (slow path)")
> }
> ```
>
> Do not change any other logic — only the log statements.

**Verification:**

- `Grep` — `Timber.w("Cache does not contain initialFilePath` returns **zero** hits in `PlayerMediaFilesLoader.kt`.
- `Grep` — `Timber.d("PlayerMediaFilesLoader: cache empty (cold start)` returns exactly **one** hit in `PlayerMediaFilesLoader.kt`.
- `Grep` — `Timber.w("PlayerMediaFilesLoader: cache scope mismatch` returns exactly **one** hit in `PlayerMediaFilesLoader.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerMediaFilesLoader.kt`.

**Status:** `[ ]` not done

---

### Step 01.3 — Resolve strategic §6 open items in spec file

**Files:** `PLAN/S0084_bugfix-cache-subfolder-mismatch-restore.md`
**Depends on:** Step 01.1 *(spec update only, no code dependency)*

**Prompt for developer:**

> In `PLAN/S0084_bugfix-cache-subfolder-mismatch-restore.md` §6, change both research items from `**Статус:** Open` to `**Статус:** Resolved` and append a one-line finding after each:
>
> §6.1: "Resolved — `lastIndexOf('/')` handles any depth; `initialFileDir` is always the immediate parent of the file, regardless of nesting depth."
>
> §6.2: "Resolved — when recursive scan is on, the resource-level cache contains all files (including subfolders), so `cacheMatchesInitialFile` is true. Bug fires only when recursive scan is off and the in-memory cache is empty (cold start) or contains a different subfolder's files."

**Verification:**

- `Grep` — `**Статус:** Open` returns **zero** hits in `PLAN/S0084_bugfix-cache-subfolder-mismatch-restore.md`.
- `Grep` — `**Статус:** Resolved` returns **two** hits in `PLAN/S0084_bugfix-cache-subfolder-mismatch-restore.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `PlayerMediaFilesLoader.kt` now emits DEBUG for cold-start cache misses and WARN only for genuine scope mismatches.
- Strategic §6 items are closed.
- Phase 02 (docs-catalog-cleanup) may start.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
