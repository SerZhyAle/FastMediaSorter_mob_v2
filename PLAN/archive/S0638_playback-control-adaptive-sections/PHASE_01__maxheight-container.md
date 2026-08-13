# Phase 01 - Max-height container

**Strategic spec:** [`../S0638_playback-control-adaptive-sections.md`](../S0638_playback-control-adaptive-sections.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Introduce a reusable `MaxHeightLinearLayout` view that caps its measured height at a code-set pixel limit, so the dialog can keep the selector and content scrollable without ever exceeding the screen. No layout or fragment wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/MaxHeightLinearLayout.kt` | New | ≤ 60 |

---

## Steps

### Step 01.1 - Add MaxHeightLinearLayout

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/MaxHeightLinearLayout.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MaxHeightLinearLayout`, a `LinearLayout` subclass in package `com.sza.fastmediasorter.ui.common.widget` with the standard `@JvmOverloads constructor(context, attrs, defStyleAttr)`. Expose a single mutable property `var maxHeightPx: Int = 0` whose setter calls `requestLayout()` only when the value actually changes. Override `onMeasure(widthMeasureSpec, heightMeasureSpec)`: when `maxHeightPx > 0`, derive a capped height spec - take the incoming height mode/size, compute `cap = if (mode == UNSPECIFIED) maxHeightPx else minOf(size, maxHeightPx)`, and rebuild the height spec as `MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)`; otherwise pass the original spec through. Call `super.onMeasure` with the (possibly capped) height spec. This lets the container shrink-to-content below the cap (no empty gap) yet bound and let children scroll above it. No business logic, no logging, no comments restating the code - a single short WHY comment on the AT_MOST choice is allowed.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/MaxHeightLinearLayout.kt` exists.
- `Grep` - `class MaxHeightLinearLayout` matches exactly once.
- `Grep` - `var maxHeightPx` present.
- `Grep` - `override fun onMeasure` present.
- `Grep` - `AT_MOST` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 5/5 PASS. Files: ui/common/widget/MaxHeightLinearLayout.kt (+44 LOC, new). `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `Log\.d\(` in the new file returns zero hits.
- [ ] Dev log entry added for the new file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`MaxHeightLinearLayout` is available for both dialog layouts as the resizable region. Phase 02 wraps the scrollable area in it under id `playbackResizableArea`; Phase 03 sets `maxHeightPx` from the fragment.

---

## Rollback Plan

Revert phase commit - new isolated view class, no callers yet, no data or user-facing surface changed.
