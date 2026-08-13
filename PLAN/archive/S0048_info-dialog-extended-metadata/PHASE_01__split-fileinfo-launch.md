# Phase 01 — Split FileInfoDialog Launch Logic

**Strategic spec:** [`../S0048_info-dialog-extended-metadata.md`](../S0048_info-dialog-extended-metadata.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Extract the external-player and download-and-open logic out of `FileInfoDialog` into a new `FileInfoLaunchManager` so subsequent phases stay below the 1000-line ceiling on `FileInfoDialog.kt`. No behavior change.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done — N/A (foundation phase).
- [ ] Strategic §6 research items blocking this phase are Resolved — yes (no blockers).
- [ ] Working tree is clean or on a feature branch.
- [ ] `FileInfoDialog.kt` baseline line count recorded (current 946) for backup decision.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoLaunchManager.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` | Modified | ≤ 750 |

> `FileInfoDialog.kt` currently 946 lines → backup step required (timestamped copy in `temp/`) before the extraction edit.

---

## Steps

### Step 01.1 — Backup `FileInfoDialog.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `FileInfoDialog.kt` at `temp/FileInfoDialog.kt.<YYYYMMDD-HHMMSS>.bak` before any extraction edits, since the file is over the 500-line backup threshold.

**Verification:**

- `Glob` — at least one file matching `temp/FileInfoDialog.kt.*.bak` exists.
- `Bash` — `wc -l temp/FileInfoDialog.kt.*.bak` reports between 940 and 960 lines.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: `temp/FileInfoDialog.kt.20260502-040215.bak` (946 LOC). Dev log recorded.

---

### Step 01.2 — Create `FileInfoLaunchManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoLaunchManager.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new helper class `FileInfoLaunchManager` in package `com.sza.fastmediasorter.ui.dialog.helpers`. Move the bodies of `openInExternalPlayer()`, `downloadAndOpenFile()`, and `openDownloadedFile()` from `FileInfoDialog` into this manager unchanged. The manager takes `Context`, `MediaFile`, `DownloadNetworkFileUseCase?`, and a `CoroutineScope` in its constructor. Public methods: `openInExternalPlayer()`, `downloadAndOpenFile(onProgressDialogReady: (MaterialProgressDialog) -> Unit, onFinished: () -> Unit)`. Keep all existing Timber logging. Do not introduce `Log.d` calls. Do not introduce a Hilt module — this is a UI-layer helper, manually constructed.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoLaunchManager.kt` exists.
- `Grep` — `class FileInfoLaunchManager` matches exactly once in that file.
- `Grep` — `fun openInExternalPlayer\(` present.
- `Grep` — `fun downloadAndOpenFile\(` present.
- `Grep -n "Log\.d\("` in `FileInfoLaunchManager.kt` returns zero hits.
- `Bash` — `wc -l` of new file ≤ 280.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 6/6 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoLaunchManager.kt` (238 LOC). One constructor parameter beyond the prompt's literal four (`onDismissRequested: () -> Unit`) added to preserve the `dismiss()` semantics of the moved `openInExternalPlayer` per the prompt's Verification note. Dev log recorded.

---

### Step 01.3 — Replace `FileInfoDialog` calls with `FileInfoLaunchManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `FileInfoDialog`, instantiate a private `FileInfoLaunchManager` field in `onCreate` (after `binding` is set). Replace the click listeners in `setupDialog()` so that `btnOpenExternal` and `btnDownloadAndOpen` delegate to the manager. Delete the original `openInExternalPlayer`, `downloadAndOpenFile`, and `openDownloadedFile` methods. Keep `isLocalFile()` and `isCloudFile()` in `FileInfoDialog` (they are also used by other code paths). Verify the `dismiss()` calls inside the moved logic still happen via a callback from the manager.

**Verification:**

- `Grep` — `private val launchManager: FileInfoLaunchManager` (or equivalent property declaration) present in `FileInfoDialog.kt`.
- `Grep` — `fun openInExternalPlayer\(` returns zero hits in `FileInfoDialog.kt`.
- `Grep` — `fun downloadAndOpenFile\(` returns zero hits in `FileInfoDialog.kt`.
- `Grep` — `fun openDownloadedFile\(` returns zero hits in `FileInfoDialog.kt`.
- `Grep -n "Log\.d\("` in `FileInfoDialog.kt` returns zero hits.
- `Bash` — `wc -l app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` ≤ 750.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 6/6 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` (946 → 709 LOC, −237). `isLocalFile()` and `isCloudFile()` retained per prompt. Manager constructed inside `onCreate` with the dialog's existing `scope` and a `dismiss()` lambda for `onDismissRequested`. Dev log recorded.

---

### Step 01.4 — Smoke-build the extraction

**Files:** —
**Depends on:** Step 01.3

**Prompt for developer:**

> Run `/build` to confirm the project compiles. Open the info-dialog manually for one local audio, one network audio (SFTP/SMB), and one local image — confirm the "Open in External Player" and "Download and Open" buttons still behave as before extraction.

**Verification:**

- `/build` exits with success.
- `Grep` — `TODO\(phase-01\)` returns zero hits in `app_v2/src/main/java/`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Static check `TODO(phase-01)` PASS (0 hits). Build not yet executed — `/build` is a reference skill in this repo, not an executor; spec-dev rules forbid calling gradle directly. Awaiting user-initiated build run (e.g. `.\build-debug.PS1` or `.\dev\build-with-version.ps1`) before this step can flip to `[x] done`.
- 2026-05-02 — Verification 2/2 PASS. Build confirmed OK by user. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API changed → `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`FileInfoDialog.kt` is now ≤ 750 lines, leaving ~250 lines of headroom for additional UI fields in Phases 03 and 04. `FileInfoLaunchManager` is a self-contained helper that can absorb future launch-related logic (e.g. share-sheet, copy-link) without further `FileInfoDialog` growth.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface change. The `temp/FileInfoDialog.kt.*.bak` snapshot from Step 01.1 is the canonical pre-extraction reference.
