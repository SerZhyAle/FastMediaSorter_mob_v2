# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0302_file-manager-mode.md`](../S0302_file-manager-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 - Browse UX
**Blocks:** none - final completion phase
**Steps done:** 2 / 2
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Update the public-facing feature lists, quick start documentation, and FAQ to document "File Manager Mode". Run the mandatory code catalog synchronization and register development logs.

---

## Prerequisites

- [ ] Phase 03 completed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 50 |
| `docs/FEATURES_RU.md` | Modified | ≤ 50 |
| `docs/FEATURES_UK.md` | Modified | ≤ 50 |
| `docs/FAQ.md` | Modified | ≤ 50 |
| `docs/FAQ_RU.md` | Modified | ≤ 50 |
| `docs/FAQ_UK.md` | Modified | ≤ 50 |
| `docs/README.md` | Modified | ≤ 50 |
| `docs/README_RU.md` | Modified | ≤ 50 |
| `docs/README_UK.md` | Modified | ≤ 50 |
| `README.md` | Modified | ≤ 50 |
| `README_RU.md` | Modified | ≤ 50 |
| `README_UK.md` | Modified | ≤ 50 |

---

## Steps

### Step 04.1 - Update Trilingual Documentation

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`, `README.md`, `README_RU.md`, `README_UK.md`
**Depends on:** start of phase

**Prompt for developer:**

> Review and edit public documentation to rename "All Files" capability to "File Manager Mode" (or "Режим файлового менеджера" in RU/UK mirrors).
> Frame this mode as a productive scenario for directory browsing, copying, moving, deleting, and renaming files on local storage, network backends (SMB, SFTP, FTP), and cloud resources (Google Drive, Dropbox, OneDrive). Clarify that unsupported binary formats can be fully managed (copied, renamed, deleted) even if they cannot be played internally.

**Verification:**

- `Grep` - `"File Manager Mode"` exists in `docs/FEATURES.md` and `README.md`.
- `Grep` - `"Режим файлового менеджера"` exists in `docs/FEATURES_RU.md` and `README_RU.md`.

**Status:** `[x] done`

---

### Step 04.2 - Catalog Synchronization & Dev Log Registration

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the PowerShell synchronization script to update the public API class catalog and register the change log.
> Command: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`
>
> In addition, update the `dev/CHANGELOG.md` or invoke `add_to_dev_log.ps1` for every modified file.

**Verification:**

- Catalog sync finishes successfully.
- Dev logs registered for all touched files.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Catalog sync script completes without errors.
- [x] Dev log entries registered successfully.

---

## Handoff Notes to Next Phase

Final phase. S0302 implementation fully finalized. Proceed to Spec Check validation.

---

## Rollback Plan

Revert git changes for documentation files.
