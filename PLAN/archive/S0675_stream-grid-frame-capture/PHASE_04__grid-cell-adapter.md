# Phase 04 - Grid Cell + Adapter

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Render the stream catalog as grid tiles: each cell shows the cached current frame (or favicon/placeholder fallback), the channel title, and hosts a `TextureView` the snapshot engine can render into.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`StreamFrameCache` readable).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_stream_grid_cell.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | New | ≤ 280 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |

> Item cell layout is orientation-agnostic (column count is owned by the `GridLayoutManager` span, set in Phase 05) - no `layout-land/item_stream_grid_cell.xml` needed.

---

## Steps

### Step 04.1 - Add trilingual grid-cell strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add via one lockstep call `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` per key (parity-enforced EN/RU/UK):
> - `streams_grid_cell_cd` - cell content description, format arg = channel title. EN: `"%1$s - current frame"`. RU: `"%1$s - текущий кадр"`. UK: `"%1$s - поточний кадр"`.
> - `streams_grid_no_frame_cd` - fallback content description when no frame. EN: `"%1$s - no preview"`. RU: `"%1$s - без превью"`. UK: `"%1$s - без прев'ю"`.
>
> Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (label/affordance) and §6 tone checklist (terse, no trailing period beyond what is shown, plain hyphen, ё where grammatical - none here).

**Verification:**

- `Grep` - `streams_grid_cell_cd` present in all three `strings.xml`.
- `Grep` - `streams_grid_no_frame_cd` present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_grid_"` exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Added `streams_grid_cell_cd` (`%1$s - current frame` / `текущий кадр` / `поточний кадр`) and `streams_grid_no_frame_cd` (`%1$s - no preview` / `без превью` / `без прев'ю`) via set-android-string.ps1 add. `check_strings_localized.ps1 -KeyPrefix streams_grid_` -> all OK (exit 0). UK apostrophe escaped, `%1$s` intact, no mojibake.

---

### Step 04.2 - Create item_stream_grid_cell.xml

**Files:** `res/layout/item_stream_grid_cell.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create the grid tile layout: a fixed-aspect (16:9) container holding, stacked:
> - a `TextureView` (`@+id/textureCapture`) filling the tile - the surface the snapshot engine renders into during capture; kept `INVISIBLE`/`alpha 0` except while capturing.
> - an `ImageView` (`@+id/ivFrame`, `scaleType="centerCrop"`) showing the captured frame bitmap or the favicon/placeholder fallback.
> - a bottom title `TextView` (`@+id/tvTitle`, single line, ellipsize end) over a subtle scrim so the channel name is legible on any frame.
>
> Use `?attr/` / `@color/` references only - no hardcoded `="#hex"`. The root tile is `focusable="true"`, `clickable="true"`, with a `?attr/selectableItemBackground` foreground so D-pad/mouse focus is visible. No fixed width (the span owns column width); fixed height derives from the 16:9 ratio.

**Verification:**

- `Glob` - `res/layout/item_stream_grid_cell.xml` exists.
- `Grep` - `@+id/textureCapture`, `@+id/ivFrame`, `@+id/tvTitle` all present.
- `Grep` - `TextureView` element present.
- `Grep -n "=\"#"` returns zero hits in the file (no hardcoded hex).
- `Grep` - `focusable` present.
- `.\a.ps1 fr` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Created `item_stream_grid_cell.xml`: ConstraintLayout tile (16:9 ratio via `layout_constraintDimensionRatio`), `@+id/textureCapture` (invisible/alpha 0), `@+id/ivFrame` (centerCrop), bottom `@+id/tvTitle` over a new `bg_stream_grid_title_scrim` gradient drawable. Root focusable/clickable with `?attr/selectableItemBackground` foreground. No hardcoded hex (`?attr/` + `@color/` + `@android:color/`). `.\a.ps1 fr` exit 0.

---

### Step 04.3 - Create StreamGridAdapter

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `class StreamGridAdapter` as a `ListAdapter<StreamSourceEntity, …>` using `ItemStreamGridCellBinding`. Reuse the `DiffUtil` contract from `StreamSourceAdapter` (id identity, full-content equality). Constructor collaborators (plain lambdas, test-friendly, mirroring `StreamSourceAdapter`):
> - `onPlay: (StreamSourceEntity) -> Unit` - tile tap (Phase 05 passes the existing `onPlay`).
> - `frameProvider: (url: String) -> Bitmap?` - reads `StreamFrameCache.get(url)`.
> - `requestCapture: (url: String, textureViewProvider: () -> TextureView?) -> Unit` - enqueues a snapshot (Phase 05 passes `StreamFrameSnapshotManager.request`); only for http(s) VIDEO sources.
> - `faviconResolver` / `faviconTileLoader` / `faviconScope` - same fallback favicon plumbing as `StreamSourceAdapter`.
>
> In `bind`: set `tvTitle`; if `frameProvider(url)` returns a bitmap, show it in `ivFrame` and set `streams_grid_cell_cd`; else show the favicon/placeholder fallback, set `streams_grid_no_frame_cd`, and for a VIDEO http(s) source call `requestCapture(url) { binding.textureCapture }`. On `onViewRecycled`, cancel the per-cell favicon job (mirror `StreamSourceAdapter.cancelFaviconLoad`). Expose `fun repaintUrl(url: String)` that finds the bound position for `url` and `notifyItemChanged` so the engine's `onCaptured` callback refreshes just that tile. Tile tap -> `onPlay`. No business logic in the adapter beyond binding. Timber only; no trivial comments.

**Verification:**

- `Glob` - `StreamGridAdapter.kt` exists.
- `Grep` - `class StreamGridAdapter` matches exactly once.
- `Grep` - `frameProvider`, `requestCapture`, `fun repaintUrl` all present.
- `Grep` - `ItemStreamGridCellBinding` referenced.
- `Grep -n "Log\.d\("` returns zero hits.
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Created `StreamGridAdapter` (ListAdapter, id+content DiffUtil, `ItemStreamGridCellBinding`). Collaborators `onPlay`/`frameProvider`/`requestCapture` + favicon plumbing (resolver/loader/scope, boundUrl guard, cancel on recycle). `bind` shows cached frame + `streams_grid_cell_cd` or favicon fallback + `streams_grid_no_frame_cd`, enqueueing capture only for http(s) VIDEO. `repaintUrl` notifies the bound position. All grep predicates matched; `.\a.ps1 fc` exit 0 (binding generated).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build` -> `standard debug` (resource + binding generation needed for `ItemStreamGridCellBinding`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`StreamGridAdapter` is fully decoupled from the engine via three lambdas; Phase 05 wires `frameProvider`->`StreamFrameCache.get`, `requestCapture`->`StreamFrameSnapshotManager.request`, and routes `onCaptured`->`repaintUrl`.

---

## Rollback Plan

Revert phase commit - new files; the existing list adapter is untouched.
