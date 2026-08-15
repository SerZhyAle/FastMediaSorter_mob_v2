# Phase 01 — teardown-guard

**Strategic spec:** [`../S0131_bugfix-pdf-null-bitmap.md`](../S0131_bugfix-pdf-null-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Guard all three lazy-computed viewer-manager accesses in `PlayerLifecycleManager` with backing-field null checks so that teardown never triggers lazy initialization of managers that were never used.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 research items — resolved (see INDEX blockers section).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 557 |

> File is 557 lines — backup step required (timestamped copy in `temp/`).

---

## Steps

### Step 1.1 — Backup PlayerLifecycleManager.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `PlayerLifecycleManager.kt` in `temp/` before making any edits. The file is 557 lines — backup is mandatory per project rules.
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt" `
>     "temp/PlayerLifecycleManager_$ts.kt.backup"
> ```

**Verification:**

- `Glob` — `temp/PlayerLifecycleManager_*.kt.backup` returns at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. Backup: `temp/PlayerLifecycleManager_20260509_182422.kt.backup`. Dev log recorded.

---

### Step 1.2 — Replace try/catch guards with backing-field null checks

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `PlayerLifecycleManager.kt`, the `try { activity.epubViewerManager.release() } catch (e: UninitializedPropertyAccessException)` pattern (and the same for `pdfViewerManager`) has no effect: `epubViewerManager` and `pdfViewerManager` are lazy computed properties, not `lateinit var`. They never throw `UninitializedPropertyAccessException` — they silently construct the manager instead, triggering `BitmapDrawable created with null Bitmap` because views are already in teardown state.
>
> Apply three changes in the same edit:
>
> **Change A** — `updateButtonVisibility()` (~line 119–130):
> Replace the `try { activity.pdfViewerManager.updateButtonVisibility() } catch ...` block with:
> ```kotlin
> if (activity._pdfViewerManager != null) activity.pdfViewerManager.updateButtonVisibility()
> ```
>
> **Change B** — `releaseResources()` EPUB block (~line 233–238):
> Replace:
> ```kotlin
> // Release EpubViewerManager
> try {
>     activity.epubViewerManager.release()
> } catch (e: UninitializedPropertyAccessException) {
>     // Not initialized, skip
> }
> ```
> With:
> ```kotlin
> // Release EpubViewerManager
> if (activity._epubViewerManager != null) activity.epubViewerManager.release()
> ```
>
> **Change C** — `releaseResources()` PDF block (~line 240–245):
> Replace:
> ```kotlin
> // Release PdfViewerManager - closes PdfRenderer, cancels render jobs, clears caches (ML-001 fix)
> try {
>     activity.pdfViewerManager.close()
> } catch (e: UninitializedPropertyAccessException) {
>     // Not initialized, skip
> }
> ```
> With:
> ```kotlin
> // Release PdfViewerManager - closes PdfRenderer, cancels render jobs, clears caches (ML-001 fix)
> if (activity._pdfViewerManager != null) activity.pdfViewerManager.close()
> ```
>
> Add debug tag at entry of `releaseResources()`:
> ```kotlin
> Timber.d("S0131: releaseResources — lazy-viewer null guards applied")
> ```

**Verification:**

- `Grep -n "_epubViewerManager != null" PlayerLifecycleManager.kt` — matches at least once.
- `Grep -n "_pdfViewerManager != null" PlayerLifecycleManager.kt` — matches at least twice (updateButtonVisibility + releaseResources).
- `Grep -n "activity.epubViewerManager.release()" PlayerLifecycleManager.kt` — zero hits inside any `try` block.
- `Grep -n "activity.pdfViewerManager.close()" PlayerLifecycleManager.kt` — zero hits inside any `try` block.
- `Grep -n "Log\.d(" PlayerLifecycleManager.kt` — zero hits (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 5/5 PASS. `_epubViewerManager != null` guard @ line 230; `_pdfViewerManager != null` guards @ lines 120, 233; no `try/catch` wrappers on lazy properties; `Log.d(` zero hits. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `PlayerLifecycleManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 01 establishes: all three lazy viewer-manager accesses in the teardown path are guarded by backing-field null checks. `BitmapDrawable created with null Bitmap` will no longer appear when closing a session that used EPUB but not PDF viewer. Phase 02 may proceed independently.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
