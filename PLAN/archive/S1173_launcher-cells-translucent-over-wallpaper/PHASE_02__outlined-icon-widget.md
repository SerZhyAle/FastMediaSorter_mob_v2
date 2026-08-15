# Phase 02 - Outlined-icon widget

**Strategic spec:** [`../S1173_launcher-cells-translucent-over-wallpaper.md`](../S1173_launcher-cells-translucent-over-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Add a shared `ImageView` subclass that draws a contrast contour around whatever drawable it holds, so both a monochrome project glyph and a colour app icon stay legible over an arbitrary wallpaper.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/OutlinedImageView.kt` | New | ≤ 130 |
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ 150 |

> No layout files in this phase - the widget is introduced and unit-visible only; Phase 03 puts it on screen.
>
> Landscape parity: no layout touched, so nothing to mirror.

---

## Steps

### Step 02.1 - Declare the widget attributes

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `declare-styleable` named `OutlinedImageView` with `oiv_outlineColor` (format `color|reference`) and `oiv_outlineWidth` (format `dimension`), following the per-widget attribute prefix every styleable in this file uses. Reuse the Phase 01 resource defaults - `@color/outline_text_stroke` and `@dimen/outline_text_stroke_width` - rather than adding a second pair of colour and dimension resources for the same visual role.

**Verification:**

- `Grep` - `declare-styleable name="OutlinedImageView"` matches once in `attrs.xml`.
- `Grep` - `name="oiv_outlineColor"` and `name="oiv_outlineWidth"` both present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 2/2 PASS. Attributes prefixed `oiv_` per the file's convention; defaults reuse the Phase 01 colour and dimension rather than duplicating them.

---

### Step 02.2 - Implement the contour pass

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/OutlinedImageView.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `OutlinedImageView` extending `AppCompatImageView` in `ui/common/widget/`. Before the normal draw, render the drawable's silhouette in the outline colour, offset in eight directions by the outline width, then let `super.onDraw` paint the real drawable on top. Derive the silhouette from the drawable's own alpha - a `SRC_IN` colour filter - so the technique works for a colour app icon as well as a monochrome vector. Set that filter, run the offset passes, then restore whatever filter was there before, all inside one synchronous `onDraw`; swallow the redraw request the filter swap raises, or every frame schedules another. Translate the canvas per pass and let `super.onDraw` position the drawable, so `ImageView`'s own scale and padding matrix stays authoritative. Resolve colour and width once in the constructor. Guard the draw path: no drawable, zero view size, or a zero outline width all fall straight through to the plain `super.onDraw`, which also lets a consumer switch the contour off through the attribute alone. Add a KDoc line explaining that eight offsets approximate a dilation, that a plain shadow is not enough over a bright wallpaper, and that the contour draws inside the view bounds so a consumer must leave padding of at least the outline width.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/OutlinedImageView.kt` exists.
- `Grep` - `class OutlinedImageView` matches exactly once in that file.
- `Grep` - `AppCompatImageView` present.
- `Grep` - `obtainStyledAttributes` present.
- `Grep` - `override fun onDraw` present.
- `Grep` - an early fall-through to `super.onDraw` guarding null drawable, zero size and zero width (`icon == null || !canDrawContour()`).
- `Grep -n "Log\.d\("` - zero hits in that file.
- `Grep -n "catch"` - zero hits in that file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 8/8 PASS. `.\a.ps1 fk` exit 0, `BUILD SUCCESSFUL in 42s`. Longest line 105 chars. Silhouette via `PorterDuff.Mode.SRC_IN` on the drawable itself, previous filter restored inside the same synchronous draw - a mutated copy was rejected because `mutate()` does not guarantee a distinct instance and would cost an allocation per icon change. Offset passes go through `canvas.translate` + `super.onDraw`, leaving `ImageView`'s scale/padding matrix authoritative. Files: `ui/common/widget/OutlinedImageView.kt` (new, 90 LOC).

> **Plan amendment 2026-07-30:** the original Step 02.3 ("guard the empty and oversized cases") was merged into this step. The guards are the first lines of the same draw path - a version of 02.2 without them would dereference a null drawable, so 02.3 could not have been an independently committable step. Phase step count 3 -> 2.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `BUILD SUCCESSFUL in 45s`, re-run after the detekt fixes.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See Audit Notes below.

---

## Audit Notes (phase boundary, 2026-07-30)

Layer 1 plus Layer 3 - the phase is one drawing widget, so allocation and borrowed-state ownership are the risk surface. Layers 2 and 4 skipped: no lifecycle, coroutine, listener or Room surface.

- The draw path allocates nothing: colour, width and the `PorterDuffColorFilter` resolve once in `init`, and `OFFSETS` lives in the companion. Eight extra `super.onDraw` passes are the cost, paid only when a drawable is present and the contour is enabled. No P0/P1.
- Borrowed-drawable mutation is the one real hazard here and it is bounded: the filter is read, replaced and restored inside a single synchronous `onDraw` on the UI thread, so no other view can observe the tinted state. Worth re-checking if this widget is ever drawn off the UI thread (a `Canvas` from a `RenderNode` or a hardware-bitmap capture path), which nothing in this repo does today.
- Both `invalidate()` and `invalidateDrawable(..)` are suppressed during the passes. `invalidateDrawable` is the one that actually fires from the filter swap; `invalidate()` is suppressed for the same reason the text widget does it. Without the pair, each frame schedules the next and the view spins.
- P2 recorded, not fixed: the contour is clipped at the view bounds, so the widget depends on its consumer leaving padding. This is documented in the KDoc and honoured by the only consumer (Phase 03). A self-sizing variant would have to fight `ImageView`'s matrix, which is a worse trade.

---

## Handoff Notes to Next Phase

Both contrast primitives now exist as shared widgets and neither is wired into a screen yet. Phase 03 is the first and only consumer of the icon one.

---

## Rollback Plan

Revert the phase commit. The widget has no consumers until Phase 03, so reverting cannot affect a live screen.
