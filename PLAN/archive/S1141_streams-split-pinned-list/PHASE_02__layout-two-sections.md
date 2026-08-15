# Phase 02 - Two-section layout (all 3 orientation variants)

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

**Step Log:**

- 2026-07-23 - Step 02.1 PASS: portrait restructured (pinned+main sections, all 9 new ids, 0 hex, main ids preserved).
- 2026-07-23 - Step 02.2 PASS: streamCaptureHostPinned added (translationX matches twice).
- 2026-07-23 - Step 02.3 PASS: land + w600dp mirrored (pinned rv gets 16dp side padding); `.\a.ps1 fr` BUILD SUCCESSFUL exit 0, all 3 variants compile.

---

## Objective

Restructure the streams content area into two vertically-stacked, weighted sections - a pinned section (collapsible header + own SwipeRefreshLayout + `rvStreamsPinned`) above the existing main section (collapsible header + existing `swipeStreams`/`rvStreams`) - and add a second off-screen capture host, applied identically across `layout/`, `layout-land/`, and `layout-w600dp/` (Rule 11). No Kotlin change; new view ids are consumed in Phase 03.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (strings, chevrons, dimens exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_streams.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout-land/activity_streams.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout-w600dp/activity_streams.xml` | Modified | ≤ 420 |

> Landscape + w600dp variants are mandatory counterparts (Rule 11); all three change in lockstep.

---

## Steps

### Step 02.1 - Insert the pinned section above the main content area (portrait)

**Files:** `res/layout/activity_streams.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the vertical `LinearLayout` that holds the content (below `streamControls`, above `streamMiniControl`), replace the single weight-1 `FrameLayout` (main list container) with two sibling section blocks inside that same vertical LinearLayout:
> 1. `streamsPinnedSection` - a vertical `LinearLayout`, `layout_height=0dp`, `layout_weight=1`, `visibility=gone` (shown by the manager only when pinned channels exist). Children: (a) a horizontal header `streamsPinnedHeader` - `layout_height=@dimen/streams_section_header_height`, `clickable=true`, `focusable=true`, `background=?attr/selectableItemBackground`, `nextFocus*` wired for D-pad/TV, containing `tvPinnedHeader` (label `@string/streams_section_pinned`, weight 1) and `ivPinnedChevron` (`@drawable/ic_expand_less`, `app:tint="?attr/colorControlNormal"`, `contentDescription=@string/streams_section_collapse`); (b) a weight-1 `FrameLayout` holding `swipeStreamsPinned` (SwipeRefreshLayout) wrapping `rvStreamsPinned` (RecyclerView, vertical scrollbars, `clipToPadding=false`).
> 2. `streamsMainSection` - a vertical `LinearLayout`, `layout_height=0dp`, `layout_weight=2`. Children: (a) a header `streamsMainHeader` mirroring the pinned header but with label `@string/streams_section_main`, `ivMainChevron`, `contentDescription=@string/streams_section_collapse`; (b) the existing weight-1 `FrameLayout` moved intact here - it keeps `swipeStreams`/`rvStreams`, `emptyStateView`, and the two scroll-button groups.
>
> Use `?attr/`/`@color/`/`@dimen/` only - no hardcoded hex (Rule 19). Keep every existing id (`swipeStreams`, `rvStreams`, `emptyStateView`, `fabStreams*`, `streamScrollButtons*`) so Phase-03/05 Kotlin still binds. Default weights: pinned 1, main 2 (main larger by default).

**Verification:**

- `Grep` - `streamsPinnedSection`, `streamsPinnedHeader`, `tvPinnedHeader`, `ivPinnedChevron`, `swipeStreamsPinned`, `rvStreamsPinned`, `streamsMainSection`, `streamsMainHeader`, `ivMainChevron` all present in `res/layout/activity_streams.xml`.
- `Grep` - `rvStreams`, `swipeStreams`, `emptyStateView`, `fabStreamsScrollToTop` still present (main section preserved).
- `Grep -n "=\"#"` in the file returns zero layout hex literals (Rule 19).

**Status:** `[ ]` not done

---

### Step 02.2 - Add the second off-screen capture host (portrait)

**Files:** `res/layout/activity_streams.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `streamCaptureHostPinned` - a second off-screen `FrameLayout` mirroring `streamCaptureHost` exactly (`translationX="-10000dp"`, `clickable=false`, `focusable=false`, `importantForAccessibility="noHideDescendants"`) so the pinned section's grid snapshot engine has its own window-attached capture surface (two engines must not share one host). Place it as a sibling of `streamCaptureHost` under the root CoordinatorLayout.

**Verification:**

- `Grep` - `streamCaptureHostPinned` present once in `res/layout/activity_streams.xml`.
- `Grep` - `translationX="-10000dp"` matches twice in the file (both hosts).

**Status:** `[ ]` not done

---

### Step 02.3 - Mirror the two-section structure into landscape and w600dp

**Files:** `res/layout-land/activity_streams.xml`, `res/layout-w600dp/activity_streams.xml`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Apply the identical section restructure (Step 02.1) and second capture host (Step 02.2) to both `layout-land/activity_streams.xml` and `layout-w600dp/activity_streams.xml`, preserving each variant's existing per-orientation attributes (the land/w600 `rvStreams` keeps its `paddingStart/paddingEnd=16dp`; the mini-control keeps its `minHeight=48dp`). Same ids, same default weights (pinned 1, main 2).

**Verification:**

- `Grep` - `rvStreamsPinned` and `streamCaptureHostPinned` present in BOTH `layout-land/activity_streams.xml` and `layout-w600dp/activity_streams.xml`.
- `Grep -n "=\"#"` in both files returns zero layout hex literals.
- `/build` - `.\a.ps1 fr` (resources/manifest) exits 0 with all three variants compiling.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fr`, then `.\a.ps1 dq` once Phase 03 wires Kotlin; a resource-only compile is sufficient here).
- [ ] All three `activity_streams.xml` variants carry the same new ids (Rule 11).
- [ ] Dev log entry added for all three layout files.

---

## Handoff Notes to Next Phase

Phase 03 binds `rvStreamsPinned`, `swipeStreamsPinned`, `streamsPinnedSection`, `streamsPinnedHeader`, `streamsMainSection`, `streamsMainHeader`, `streamCaptureHostPinned` via the generated `ActivityStreamsBinding`.

---

## Rollback Plan

Revert the phase commit - layout-only, no data or behavior surface changed; the previous single-list layout is restored intact.
