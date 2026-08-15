# Phase 02 - Gesture wiring

**Strategic spec:** [`../S1273_pdf-reader-page-seek-zone-collides-with-text.md`](../S1273_pdf-reader-page-seek-zone-collides-with-text.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Feed the PhotoView touch stream through `PdfPageSwipeDetector` on every surface, and retire the vertical branch of `handlePdfFling` that strategic section 2.1 proved unreachable. This is the phase that changes what the user feels.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `PdfPageSwipeDetector`, `PdfViewerManager.isPageSwipeEnabled()` and `PdfViewerManager.turnPage(..)` all exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified (added Step 02.5) | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified (added Step 02.5) | ≤ 1100 |

> `PlayerGestureSetupManager.kt` is 532 LOC - over the 500-line backup threshold. Back it up under `temp/S1273/` before the first edit. `PdfViewerManager.kt` was already backed up in Step 01.1.

No layout files are touched, so the landscape-parity rule does not apply. The arrow buttons and their handlers are not touched at all - strategic section 3.3 keeps them as the unchanged non-gesture path.

---

## Steps

### Step 02.1 - Back up the oversized file before editing

**Files:** `temp/S1273/PlayerGestureSetupManager.kt.<timestamp>.bak`
**Depends on:** - start of phase

**Prompt for developer:**

> `PlayerGestureSetupManager.kt` is over the 500-line backup threshold in CLAUDE.md Rule 5. Copy it to `temp/S1273/` with a timestamped name before making any edit in this phase.

**Verification:**

- `Glob` - `temp/S1273/PlayerGestureSetupManager.kt.*.bak` matches at least one file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 1/1 PASS. Backup `temp/S1273/PlayerGestureSetupManager.kt.20260731-021211.bak`.

---

### Step 02.2 - Construct a detector per PhotoView surface and route the touch stream through it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `configurePhotoViewGestures`, build one `PdfPageSwipeDetector` for the surface being configured, before the existing `setOnTouchListener` block. Its `Host` reports `isPageSwipeEnabled()` from `activity._pdfViewerManager?.isPageSwipeEnabled() ?: false`, reports `currentScale()` from the local `photoView.scale` so surface B is judged by its own zoom rather than surface A's, and forwards `turnPage(next)` to `activity._pdfViewerManager?.turnPage(next)`. One detector per surface, because the per-gesture state is per surface.
>
> Inside the existing touch listener, keep the current PDF down-point capture as it is, then offer the event to the detector only while the current file is a PDF. When the detector claims the gesture, send the attacher a single `ACTION_CANCEL` - obtain a copy of the event, set the cancel action, pass it to `attacher.onTouch`, recycle it - the first time only, tracked by a flag reset on `ACTION_DOWN`, and consume the event by returning true. Otherwise forward to `attacher.onTouch(v, ev)` exactly as today.
>
> The cancel is what stops a zoomed page from continuing to pan under the two fingers that just turned the page, and what stops the trailing fling from turning a second page. Do not swallow the event when the detector has not claimed it - pinch-zoom, one-finger pan on a zoomed page, horizontal zoom-step flings and image gestures all depend on the untouched path.

**Verification:**

- `Grep` - `PdfPageSwipeDetector(` matches in `PlayerGestureSetupManager.kt`.
- `Grep` - `ACTION_CANCEL` present in `PlayerGestureSetupManager.kt`.
- `Grep` - `attacher.onTouch(v, ev)` still present - the untouched path survives.
- `Grep` - `Log\.d\(` returns zero hits in `PlayerGestureSetupManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS. Files: PlayerGestureSetupManager.kt 532 -> 564 LOC (budget 620).

---

### Step 02.3 - Retire the unreachable vertical branch in `handlePdfFling`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> `handlePdfFling` keeps only the horizontal zoom-step branch from S0949. Delete the vertical page-navigation branch, and delete the `photoView.scale > PDF_NAV_ZOOM_THRESHOLD` term from its guard: strategic section 2.1 shows `PhotoViewAttacher` never calls this listener above scale 1.0 or with more than one pointer, so the term can never be true here. Keep the `isScrollMode` and `pdfRenderer == null` guards. Remove `PDF_NAV_ZOOM_THRESHOLD` and `PDF_SWIPE_VELOCITY_THRESHOLD` from the companion object if nothing else references them after this edit, per CLAUDE.md Rule 20 - grep before deleting, and leave any constant the horizontal branch still needs. Rewrite the KDoc so it describes what the method now does rather than what it used to.

**Verification:**

- `Grep` - `showPreviousPage()` and `showNextPage()` return zero hits inside `handlePdfFling`.
- `Grep` - `stepPdfZoom(zoomIn = diffX > 0)` still present in `PdfViewerManager.kt`.
- `Grep` - every companion constant still named in `PdfViewerManager.kt` is referenced at least once in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Vertical branch removed; `PDF_NAV_ZOOM_THRESHOLD` dropped to one reference (declaration only) and deleted per Rule 20. `PDF_SWIPE_VELOCITY_THRESHOLD` still serves the horizontal branch and stays.

---

### Step 02.4 - Insert the device-verification probe tags

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageSwipeDetector.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> This ticket goes to `BlockNeedUserTest`, so per CLAUDE.md "Debug Verification Tags" add one `Timber.d("S1273: ..")` at each changed flow entry, and no more. There are exactly two: the two-finger claim and the one-finger unzoomed claim. Put each on the claim itself, not on every move event, and include the direction so the logcat shows which way the page went. Keep each line at 120 characters or under so the detekt gate passes on the first build.

**Verification:**

- `Grep` - `Timber.d("S1273:` matches exactly twice across `app_v2/src`.
- `Grep` - no `Timber.d("S1273:` line exceeds 120 characters.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Two tags, both in `PdfPageSwipeDetector.kt`; longest line in that file is 100 characters.

---

### Step 02.5 - Mirror the gesture onto the standalone document hosts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Added 2026-07-31, mid-phase. The plan assumed one PDF gesture host; grepping `handlePdfFling` while fixing a detekt finding turned up three. `DocumentStandaloneActivity` and `StandaloneViewManager` drive the same reader from the standalone document screen, so wiring only the unified player would have left the owner's complaint alive on whichever screen he actually opened the file from. This step exists so the widened file list is recorded rather than smuggled in.
>
> Give both hosts the same detector the player got. `DocumentStandaloneActivity` already forwards a touch listener to the attacher - add the detector ahead of it and keep the down-point capture. `StandaloneViewManager` has no touch listener at all, so add one that forwards to the attacher, and gate the host's `isPageSwipeEnabled()` on `currentMediaType == MediaType.PDF` because that PhotoView is shared with images and GIFs. Update both `handlePdfFling` call sites for the dropped `velocityY` parameter, and correct the stale comments that still claim the fling handler owns the zoom guard.

**Verification:**

- `Grep` - `PdfPageSwipeDetector(` matches in both files.
- `Grep` - `handlePdfFling(e1, e2, velocityX, velocityY)` returns zero hits across `app_v2/src`.
- `Grep` - `currentMediaType == MediaType.PDF` present in `StandaloneViewManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Both standalone hosts now share the player's gesture map.
- 2026-07-31 - AUDIT-FIX: wiring the third host tripped detekt `LargeClass 605/600` on `StandaloneViewManager`. The size was the symptom; the cause was the same ~25 lines of install-and-cancel glue copied into three hosts. Extracted `PdfPageSwipeDetector.install(photoView, host, onDown)` and reduced all three call sites to it, which removes the duplication and drops the class back under the threshold. A copy that drifts is how one screen ends up paging and another not.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly). This is the build that validates code and probe tags together; no separate build after.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gesture map is complete and the probe tags are in. Phase 03 regenerates the catalog for the new class, records the capability, and hands the ticket to the device test. The tags stay in the tree until the ticket leaves `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit. Phase 01's class is left unwired and the tree returns to the shipped gesture behaviour, with no data migration and no persisted state to undo.
