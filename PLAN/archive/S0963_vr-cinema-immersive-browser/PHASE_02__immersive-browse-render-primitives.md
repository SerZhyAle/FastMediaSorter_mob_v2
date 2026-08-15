# Phase 02 - Immersive BROWSE render primitives

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Add the `src/vr` BROWSE render primitives - a Canvas thumbnail-grid renderer, a UV->pixel hit-test dispatcher, and a heap-bounded on-demand thumbnail decoder - as siblings to the existing `HudCanvasRenderer` / `HudInteractionDispatcher`. No Activity yet; these are pure, unit-testable helpers that draw onto one Canvas quad pushed through the existing `queueHud`/`queueFrame` channel.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `ui/xr/helpers/HudCanvasRenderer.kt` and `HudInteractionDispatcher.kt` read as captured in `research/01` §1.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowseCell.kt` | New | ≤ 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowseGridRenderer.kt` | New | ≤ 280 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowseInteractionDispatcher.kt` | New | ≤ 140 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveThumbnailDecoder.kt` | New | ≤ 160 |

> All four are flavor-only (`src/vr`), consumed only inside the immersive Activity. Catalog `-NoFlavors "standard,lite,photos,legacy"` recorded in Phase 06.

---

## Steps

### Step 02.1 - Grid cell model

**Files:** `ui/xr/browse/ImmersiveBrowseCell.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `data class ImmersiveBrowseCell` describing one grid entry: `index: Int`, `label: String`, `isFolder: Boolean`, `mediaType: VrMediaType`, `stereoBadge: String?` (short SBS/OU/360 tag or null), `bounds: RectF` (assigned by the renderer during layout), `thumbnail: Bitmap?` (null until decoded). Keep it a plain holder - no logic.

**Verification:**

- `Glob` - `ui/xr/browse/ImmersiveBrowseCell.kt` exists.
- `Grep` - `data class ImmersiveBrowseCell` matches exactly once.

**Status:** `[x]` done

---

### Step 02.2 - Grid renderer

**Files:** `ui/xr/browse/ImmersiveBrowseGridRenderer.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class ImmersiveBrowseGridRenderer` that draws an N-column grid of `ImmersiveBrowseCell` onto a supplied `Canvas` of fixed panel size (mirror `HudCanvasRenderer`'s 1024x512-style fixed buffer; pick a BROWSE panel size constant, e.g. 1024x1024). Responsibilities: compute each cell's `bounds: RectF` from a `columns` count + page offset (`layout(cells, columns, scrollOffset)`), draw thumbnail bitmap (or a placeholder rect + label when `thumbnail == null`), draw the stereo badge and folder chevron, draw a highlight ring on the hovered index. Expose `fun draw(canvas, cells, hoveredIndex)`. Use theme colors via passed-in ints - no hardcoded hex beyond neutral debug fills. No Glide, no decode here (decoder owns that).

**Verification:**

- `Glob` - `ui/xr/browse/ImmersiveBrowseGridRenderer.kt` exists.
- `Grep` - `class ImmersiveBrowseGridRenderer` matches exactly once.
- `Grep` - `fun draw(` and `fun layout(` present.

**Status:** `[x]` done

---

### Step 02.3 - Interaction dispatcher

**Files:** `ui/xr/browse/ImmersiveBrowseInteractionDispatcher.kt` (New)
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `class ImmersiveBrowseInteractionDispatcher` mirroring `HudInteractionDispatcher`: `dispatch(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean)` maps normalized UV [0..1] to panel pixels, finds the cell whose `bounds` contains the point, updates a `hoveredIndex`, and on click invokes injected callbacks `onCellSelected: (ImmersiveBrowseCell) -> Unit` and `onScroll: (Int) -> Unit` for reserved scroll zones (top/bottom edge bands). Return the resolved `hoveredIndex` so the caller can re-draw. No haptics call here - the Activity fires haptics on click via the existing `HudHapticBridge`.

**Verification:**

- `Glob` - `ui/xr/browse/ImmersiveBrowseInteractionDispatcher.kt` exists.
- `Grep` - `class ImmersiveBrowseInteractionDispatcher` matches exactly once.
- `Grep` - `fun dispatch(` present.

**Status:** `[x]` done

---

### Step 02.4 - Heap-bounded thumbnail decoder

**Files:** `ui/xr/browse/ImmersiveThumbnailDecoder.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class ImmersiveThumbnailDecoder` that decodes a cell thumbnail on demand via `Glide.with(context).asBitmap().load(model).override(cellW, cellH).centerCrop().submit()` sized to grid-cell pixels, off the render thread (suspend or callback on `Dispatchers.IO`). Enforce a heap budget: cap concurrent decoded bitmaps and skip decode when the running decoded-bytes total would exceed a cap derived from `Runtime.getRuntime().maxMemory()` (mirror the `videoBufferCapBytesForHeap` idiom; visible cells only). Expose `suspend fun decode(cell, model): Bitmap?` and `fun release()` clearing Glide targets and the byte accounting. WHY-comment the heap cap (S0772 7K OOM context).

**Verification:**

- `Glob` - `ui/xr/browse/ImmersiveThumbnailDecoder.kt` exists.
- `Grep` - `class ImmersiveThumbnailDecoder` matches exactly once.
- `Grep` - `maxMemory()` referenced (heap-derived cap).
- `Grep` - `.override(` present (decode-at-cell-size).

**Status:** `[x]` done

---

## Step Log

- 2026-07-11 - Steps 02.1-02.4 Verification all PASS (Glob/Grep/line-length). Files: ImmersiveBrowseCell.kt, ImmersiveBrowseGridRenderer.kt (layout/draw, 4x2 page), ImmersiveBrowseInteractionDispatcher.kt (UV hit-test + edge page-flip bands), ImmersiveThumbnailDecoder.kt (Glide override + maxMemory/8 heap cap). Authored via android-kotlin-developer subagent, disjoint new src/vr files. Build gate deferred (BUILD.LOCK).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug` (No-Op, classes not on classpath) and `.\a.ps1 fkn` (noLegal - real classes compiled).
- [ ] `.\a.ps1 vr debug` compiles if `src/vr` touched (mandatory VR-source build).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`ImmersiveBrowseGridRenderer.draw`, `ImmersiveBrowseInteractionDispatcher.dispatch`, and `ImmersiveThumbnailDecoder.decode` are ready for the Activity (Phase 03) to compose: Activity owns the Canvas + native quad push + haptics; these three own layout/hit-test/decode. All four are `src/vr`-only.

---

## Rollback Plan

Revert the phase commit - four new isolated `src/vr` files with no call sites yet; no other surface affected.
