# Phase 03 - Batch Coordinator

**Strategic spec:** [../S0117_url-media-downloader-nolegal-flavor.md](../S0117_url-media-downloader-nolegal-flavor.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Extend the coordinator, progress reporting, and post-download UX so an album result runs as sequential per-item downloads with continue-on-error semantics.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] The site resolver can already return single-item and album outcomes.
- [x] Focused tests for `LinkExtractionRegistry` are green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | <= 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt` | Modified | <= 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | Modified | <= 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenterTest.kt` | Modified | <= 250 |

---

## Steps

### Step 03.1 - Extend coordinator result and progress contracts for batch execution

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add batch-aware `ProgressState` and `Result` models that can represent the current item index, total item count, and an end-of-run summary without breaking the existing single-item flows.

**Verification:**

- `Grep` - `data class BatchDownloading` present in `LinkAutoDownloadCoordinator.kt`.
- `Grep` - `data class BatchCompleted` present in `LinkAutoDownloadCoordinator.kt`.
- `Grep` - `data class BatchSummary` present in `LinkAutoDownloadCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Added batch-aware progress and result models without regressing existing single-item outcomes.

---

### Step 03.2 - Execute album items sequentially with continue-on-error policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Teach `LinkAutoDownloadCoordinator.handle` to run the structured album outcome as sequential download operations, reuse the existing writer and streaming pipeline for each item, continue after local failures, and surface a final summary result. Cover the main success/partial-failure path with focused JVM tests.

**Verification:**

- `Grep` - `continue_on_error` absent from production code comments/strings.
- `Grep` - `BatchCompleted` referenced from `handleUrl(` in `LinkAutoDownloadCoordinator.kt`.
- `Grep` - `runBatch(` present in `LinkAutoDownloadCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Sequential per-item execution and continue-on-error summary landed in `LinkAutoDownloadCoordinator`.

---

### Step 03.3 - Project batch state into progress and result UX

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenterTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Update the progress dialog and result presenter so batch downloads show `item i of N` while running and report a final partial-success summary after completion. Preserve the existing single-item UX messages.

**Verification:**

- `Grep` - `item %1$d of %2$d` or equivalent new batch string usage present in `LinkAutoDownloadProgressDialog.kt`.
- `Grep` - `BatchCompleted` handled in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` - `BatchCompleted` referenced in `LinkAutoDownloadResultPresenterTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Progress dialog and result presenter now handle batch states; new `s0117_` strings added in EN/RU/UK.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles for the touched slice via `:app_v2:compileNoLegalDebugKotlin`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 can assume the `noLegal` downloader already surfaces final batch summaries and only needs to add the license/compliance UI.