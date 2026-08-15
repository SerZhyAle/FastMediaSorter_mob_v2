# Phase 01 - Carry the reader's zoom across a page turn

**Strategic spec:** [`../S1327_pdf-zoom-resets-on-every-page-turn.md`](../S1327_pdf-zoom-resets-on-every-page-turn.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

Read the PhotoView's supplementary matrix before the page bitmap is swapped and write it back after, so a page turn lands the next page at the zoom the reader was already using.

---

## Prerequisites

- [x] Owner decision A in INDEX.md answered - 2026-08-02, recommended option: zoom and pan together.
- [x] Owner decision B in INDEX.md answered - 2026-08-02, recommended option: every page move carries it.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` | Modified | ≤ 1110 |

One file. `PdfViewerManager` is the single owner of the page-mode PhotoView, and all three PDF hosts - `PlayerActivity` through `PlayerViewerFactory`, `DocumentStandaloneActivity`, and `StandaloneViewManager` - drive their own PhotoView through this same class. No host needs touching.

The file stands at 1069 lines, above the 500-line backup threshold and below the 1500-line split threshold, so Step 01.1 takes a backup and no split is required.

Scroll mode is out of scope and needs no guard: `PdfPageAdapter` renders each page into a plain `ImageView`, so there is no zoom there to lose.

No layout file changes, so the landscape-parity rule does not apply. No new string, so the string audit does not apply. No settings key, so Rule 22 does not apply.

---

## Steps

### Step 01.1 - Back up the file before editing

**Files:** `temp/S1327/PdfViewerManager.kt.<timestamp>.bak`
**Depends on:** - start of phase

**Prompt for developer:**

> `PdfViewerManager.kt` is over 500 lines, so CLAUDE.md Rule 10.5 requires a timestamped copy under `temp/S1327/` before the first edit. Create the ticket directory and copy the file into it with a timestamp in the name.

**Verification:**

- `Glob` - at least one file matches `temp/S1327/PdfViewerManager.kt.*`.

**Status:** `[x]` done

---

### Step 01.2 - Capture and restore the supplementary matrix around the bitmap swap

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add one private `android.graphics.Matrix` field to `PdfViewerManager` holding the zoom and pan to carry into the next page, plus a boolean saying whether it holds anything. Inside `showPdfPage`, in the `withContext(Dispatchers.Main)` block, call `safeViews.photoView.getSuppMatrix(..)` into that field immediately before the existing `safeViews.photoView.setImageBitmap(null)` line, and set the flag only when the view already holds a page - guard on `safeViews.photoView.drawable != null`. After the block has run `safeViews.photoView.setImageBitmap(bitmap)` and assigned `currentPageBitmap = bitmap`, call `safeViews.photoView.setSuppMatrix(..)` with the captured matrix when the flag is set. Restore after the `currentPageBitmap` assignment rather than straight after the bitmap push, because `setSuppMatrix` fires the `OnMatrixChangeListener` installed in `init`, and that listener reads `currentPageBitmap`. Do not use `setScale` for the restore: it recentres on the view and would throw if the captured value ever fell outside the `setScaleLevels` range.
>
> Clear the flag in `displayPdf` and in `close()`, so a zoom belonging to one document cannot be inherited by the first page of the next one. `displayPdf` calls `closePdfRenderer()`, which does not touch the PhotoView image, so without this the next document opens wearing the previous document's zoom.
>
> Owner decision A, answered 2026-08-02: carry zoom and pan together. Write the captured matrix back unchanged - no `Matrix.getValues` / `setValues` zeroing of the vertical translation. The drop-to-top variant was rejected; do not implement it.
>
> Owner decision B, answered 2026-08-02: every page move carries it. This needs no extra code, because every route already funnels through `showPdfPage`. Do not add the `carryZoom: Boolean` parameter, and leave `showGoToPageDialog` and the `onPagePicked` argument in `showThumbnailNavigation` untouched - the fit-scale-on-distant-jump variant was rejected.
>
> The adjacent rotation reset belongs to S1355 and is out of scope here. Capture and restore only around the bitmap swap in `showPdfPage`; do not hook `setFrame`, a layout listener, or a configuration callback.
>
> Keep it clean for the gates: no comment restating what the adjacent line does, and a short `// S1327:` note only on the two non-obvious points, which are why the restore follows the `currentPageBitmap` assignment and why the carry is cleared on document change.

**Verification:**

- `Grep` - `getSuppMatrix` matches exactly once in `PdfViewerManager.kt`.
- `Grep` - `setSuppMatrix` matches exactly once in `PdfViewerManager.kt`.
- `Grep` - `setImageBitmap` matches seven times in `PdfViewerManager.kt`: the six pre-existing pushes, none moved or dropped, plus the one the phase-boundary audit added in `displayPdf` (see Step Log 2026-08-03). The predicate was written as "still six" before that fix was known to be necessary.
- `Grep` - `Log\.d\(` returns zero hits in `PdfViewerManager.kt`.
- `Grep` - the private matrix field name matches at least four times in `PdfViewerManager.kt`: declaration, capture, restore, and at least one clear site.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 5/5 PASS. `getSuppMatrix`=1, `setSuppMatrix`=1, `setImageBitmap`=6, `Log.d(`=0, `carriedPageMatrix`=5 (declaration, capture, restore, two clear sites). Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` (+16 LOC). PhotoView 2.3.0 API confirmed by `javap` on the cached AAR: `public void getSuppMatrix(Matrix)`, `public boolean setSuppMatrix(Matrix)`. Dev log deferred to the single phase-closure `post-change.ps1` run after Step 01.3, which edits the same file - one changelog row per logical change.
- 2026-08-03 - Amended by the phase-boundary audit: `displayPdf` now also drops the PhotoView image, because clearing the carry alone left the stale drawable to re-arm it on the next document's first page. `setImageBitmap` count moves 6 -> 7 and the step's third predicate was updated to match. Re-verified 6/6: `getSuppMatrix`=1, `setSuppMatrix`=1, `setImageBitmap`=7, `Log.d(`=0, `carriedPageMatrix`=5, `Timber.d("S1327:`=1.

---

### Step 01.3 - Add the device-verification probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> This ticket goes to `BlockNeedUserTest`, so per CLAUDE.md "Debug Verification Tags" it needs exactly one `Timber.d("S1327: ..")` at the entry of the changed flow. Put it beside the restore in `showPdfPage`, reporting the scale that was reapplied and the page index, so a logcat line proves the carry ran rather than that the code exists. One tag for the flow, not one per branch. Keep the line at or under 120 characters including its indentation, so the detekt gate passes on the first build.
>
> Suggested line, 83 characters before indentation:
>
> ```kotlin
> Timber.d("S1327: page turn carried zoom=${safeViews.photoView.scale} index=$index")
> ```

**Verification:**

- `Grep` - `Timber\.d\("S1327:` matches exactly once across `app_v2/src/**/*.kt`.
- The matched line is at or under 120 characters including indentation.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 2/2 PASS. One `Timber.d("S1327:` line in the tree, `PdfViewerManager.kt:840`, 108 characters including indentation. Placed inside the restore branch so the line proves the carry ran, not that the code exists.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 fk` exit 0, re-run after the audit fix (`BUILD SUCCESSFUL in 51s`).
- [x] Detekt gate passes for the touched file: `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles ..PdfViewerManager.kt` exit 0, `PASS (no new findings; baselines hold)`, re-run after the audit fix.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `PdfViewerManager.kt` - one row via `post-change.ps1` (`post-change: PASS (Kotlin)`), which also ran the scoped gates and the catalog sync.
- [x] No catalog regeneration needed from this phase alone - no class added, one private field and no public signature change. `post-change.ps1` regenerated the catalog anyway as part of closure.
- [x] Phase-boundary audit run - one P1 found and fixed inside the phase (see Step Log), no unresolved P0/P1 findings.

---

## Phase-boundary audit - 2026-08-03

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md` against the one touched file.

- **P1 - the document-change clear was dead as written, fixed in this phase.** Step 01.2 cleared the
  carry inside `displayPdf` right after `closePdfRenderer()`, but `closePdfRenderer` never touches the
  PhotoView image. The view therefore still held the previous document's bitmap when the new
  document's first `showPdfPage` ran, the `drawable != null` capture guard passed against that stale
  drawable, and the previous document's zoom was re-armed and re-applied - the exact defect the clear
  was written to prevent. Fix: drop the image alongside the carry in `displayPdf`
  (`safeViews.photoView.setImageBitmap(null)`), so the first render of a new document sees no drawable
  and captures nothing. Compile and detekt re-run green after the fix.
- **Layer 2, concurrency - clean.** Capture and restore both sit inside the existing
  `withContext(Dispatchers.Main)` block with no suspension point between them, and every writer of the
  carry field (`showPdfPage`, `displayPdf`, `close`) runs on the main thread, so the field needs no
  synchronisation. A `pageRenderJob` cancellation between capture and restore leaves the flag armed
  with a valid matrix, which the next page turn overwrites - no stale-state path.
- **Layer 3, memory and ownership - clean.** One `Matrix` instance reused for the lifetime of the
  manager, reset rather than reallocated, cleared in both `displayPdf` and `close()`. No listener
  added, so listener symmetry is unchanged; the gate confirms `new imbalance 0`.
- **Layer 1, architecture - clean.** One private field and two call sites inside the class that already
  owns the page-mode PhotoView. No host touched, no public signature changed, no flavor guard.

**UI-surface gate (S1338).** Placement decision is on record in the strategic spec §3.3, owner-confirmed
2026-08-02: "no new control, no new screen, no new settings row". Nothing is placed by this phase - the
change is invisible until a page turns. The screen proof is the device-test gate that follows this
phase in the same run; its screenshot paths are recorded in Phase 02's Step Log.

---

## Handoff Notes to Next Phase

The carried state is session-scoped and in memory only. It survives page turns inside one open document and dies when the document is closed or another is opened. Nothing is written to `PlaybackPositionRepository`, so reopening a file still restores the page index at fit scale, which is the existing behaviour and is deliberate here.

Two adjacent resets stay untouched and are not defects introduced by this phase:

- Entering PDF fullscreen still forces `setScale(1f, true)` on the separate `pdfFullscreenPhotoView`, which is a different surface with its own deliberate reset.
- Rotation still drops the zoom, and that is **S1355**, spun off by the owner on 2026-08-02. All three hosts declare `configChanges="orientation|screenSize|.."` so the activity is not recreated, but the PhotoView gets a new frame, `PhotoView.setFrame` calls `attacher.update()`, and the same `resetMatrix()` runs. S1355 is ordered after this phase and should reuse the carry field introduced here rather than declare a second one.

---

## Rollback Plan

Revert the phase commit. No data migration, no persisted state, no user-facing surface beyond the reader's own framing.
