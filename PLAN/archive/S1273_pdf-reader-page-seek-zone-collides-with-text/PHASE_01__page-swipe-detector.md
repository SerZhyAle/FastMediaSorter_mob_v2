# Phase 01 - Page-swipe detector

**Strategic spec:** [`../S1273_pdf-reader-page-seek-zone-collides-with-text.md`](../S1273_pdf-reader-page-seek-zone-collides-with-text.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Introduce `PdfPageSwipeDetector` and the `PdfViewerManager` paging predicates it drives. Nothing is wired to a view in this phase, so behaviour is unchanged when it lands.

---

## Prerequisites

- [ ] Strategic section 4.1 resolved - yes, owner 2026-07-29.
- [ ] Strategic section 2.1 read - it decides why this class exists at all.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageSwipeDetector.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` | Modified | ≤ 1100 |

> `PdfViewerManager.kt` is 1064 LOC - over the 500-line backup threshold. Back it up under `temp/S1273/` before the first edit.

No layout files are touched in this phase, so the landscape-parity rule does not apply. No flavor-specific file is introduced: the PDF reader already lives in `src/main` and this class adds no `BuildConfig` guard.

---

## Steps

### Step 01.1 - Back up the oversized file before editing

**Files:** `temp/S1273/PdfViewerManager.kt.<timestamp>.bak`
**Depends on:** - start of phase

**Prompt for developer:**

> `PdfViewerManager.kt` is over the 500-line backup threshold in CLAUDE.md Rule 5. Copy it to `temp/S1273/` with a timestamped name before making any edit in this phase.

**Verification:**

- `Glob` - `temp/S1273/PdfViewerManager.kt.*.bak` matches at least one file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 1/1 PASS. Backup `temp/S1273/PdfViewerManager.kt.20260731-020457.bak`.

---

### Step 01.2 - Add the paging predicates to `PdfViewerManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add two public members so a gesture detector can ask whether a swipe may turn a page and then turn one, without reaching into PDF state itself. `fun isPageSwipeEnabled(): Boolean` returns true when `pdfRenderer != null` and `!isScrollMode` - in scroll mode the RecyclerView owns vertical movement. `fun turnPage(next: Boolean)` calls `showNextPage()` when `next` is true and `showPreviousPage()` otherwise. Both are thin delegations; put no gesture arithmetic here.

**Verification:**

- `Grep` - `fun isPageSwipeEnabled\(\): Boolean` matches exactly once in `PdfViewerManager.kt`.
- `Grep` - `fun turnPage\(next: Boolean\)` matches exactly once in `PdfViewerManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `PdfViewerManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Files: PdfViewerManager.kt (+11 LOC).

---

### Step 01.3 - Write `PdfPageSwipeDetector`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageSwipeDetector.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `PdfPageSwipeDetector(context: Context, private val host: Host)` with a nested `interface Host { fun isPageSwipeEnabled(): Boolean; fun currentScale(): Float; fun turnPage(next: Boolean) }`. Expose one entry point, `fun onTouchEvent(event: MotionEvent): Boolean`, returning true once the detector has claimed the gesture so the caller stops forwarding to the PhotoView attacher.
>
> Read the paging distance once in the constructor from `ViewConfiguration.get(context).scaledPagingTouchSlop`; it is the platform's own paging threshold and replaces any pixel constant. Track per-gesture state: the anchor position of the first pointer, the anchors of both pointers plus their initial span once a second pointer arrives, and a `claimed` flag. Reset every field on `ACTION_DOWN`, `ACTION_UP` and `ACTION_CANCEL`.
>
> On `ACTION_MOVE`, when `host.isPageSwipeEnabled()` and the gesture is not yet claimed, decide in two branches. With two or more pointers: compare the current span between the two tracked pointers against the initial span, and abandon the page-turn candidacy for the whole gesture once the ratio leaves `1f ± PINCH_SPAN_TOLERANCE`, because that is a pinch, not a page turn (S1274 section 3.1); otherwise claim when both pointers have travelled vertically in the same direction past the paging slop and vertical travel exceeds horizontal. With exactly one pointer, claim only when `host.currentScale() <= PAGE_PAN_SCALE_THRESHOLD` and the vertical travel passes the paging slop and exceeds the horizontal travel - a zoomed page keeps its one-finger pan untouched. On a claim, set the flag and call `host.turnPage(next = <travel is upward>)` exactly once.
>
> Keep every threshold a named `companion object` constant (`PINCH_SPAN_TOLERANCE`, `PAGE_PAN_SCALE_THRESHOLD` = 1.05f, matching the value the old fling guard used). No bare numeric literals in expressions, no comment that restates the line below it, and log nothing at info level or above - this runs per touch event.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageSwipeDetector.kt` exists.
- `Grep` - `class PdfPageSwipeDetector` matches exactly once in that file.
- `Grep` - `fun onTouchEvent\(event: MotionEvent\): Boolean` present.
- `Grep` - `interface Host` present.
- `Grep` - `scaledPagingTouchSlop` present.
- `Grep` - `PINCH_SPAN_TOLERANCE` and `PAGE_PAN_SCALE_THRESHOLD` both present.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 8/8 PASS. Files: PdfPageSwipeDetector.kt (New, 152 LOC). Longest line 100.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`PdfPageSwipeDetector` exists but is instantiated nowhere, so PDF gestures still behave exactly as they ship today. Phase 02 owns every observable change: it constructs one detector per PhotoView surface, feeds it the touch stream, and only then removes the unreachable vertical branch from `handlePdfFling`. Splitting it this way keeps the tree from carrying a state where fling paging is gone and swipe paging is not yet wired.

---

## Rollback Plan

Revert the phase commit. One new file and two added methods, no data migration and no user-facing surface changed.
