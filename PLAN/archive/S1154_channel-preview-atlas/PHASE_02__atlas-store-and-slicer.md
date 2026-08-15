# Phase 02 - Atlas store and per-tile slicer

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Introduce the on-device atlas access layer: a store that resolves the downloaded sheet + parses the `url->index` sidecar from `filesDir/delivery/CHANNEL_PREVIEW_ATLAS/`, and a per-tile `BitmapRegionDecoder` slicer that never decodes the full 8192x8192 sheet (ADR-2). Pure parts (sidecar parser, index math, bounds) are unit-tested. No UI wiring yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`DeliverableSet.CHANNEL_PREVIEW_ATLAS` and its payload dir exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/ChannelPreviewAtlasStore.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/ChannelPreviewAtlasSlicer.kt` | New | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/streams/ChannelPreviewAtlasStoreTest.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/ChannelPreviewAtlasSlicerTest.kt` | New | ≤ 160 |

> No `res/layout/*.xml` edits - no landscape-parity obligation.
>
> **Flavor placement.** Both classes are flavor-agnostic (`src/main`); they are inert on `lite`/`photos` because no atlas is ever downloaded there. No `BuildConfig.*` guard.

---

## Steps

### Step 02.1 - Atlas store (payload resolver + sidecar parser)

**Files:** `data/repository/streams/ChannelPreviewAtlasStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class ChannelPreviewAtlasStore @Inject constructor(@ApplicationContext context)`. Expose `fun atlasFile(): File?` resolving `filesDir/delivery/CHANNEL_PREVIEW_ATLAS/channel-preview-atlas.webp` (null when absent), and `suspend fun coords(): Map<String, Int>` reading `channel-preview-coords.json` off `Dispatchers.IO`. Reuse the defensive JSON decode shape from `FaviconAtlasStore.decodeCoords` (skip non-integer values; an absent/corrupt sidecar yields an empty map logged at `Timber.i`, not error). This store is READ-ONLY - the `DeliverableSetDownloader` writes the payload; there is no `write(..)`.

**Verification:**

- `Glob` - `ChannelPreviewAtlasStore.kt` exists.
- `Grep` - `class ChannelPreviewAtlasStore` matches exactly once.
- `Grep` - `fun atlasFile()` and `suspend fun coords()` both present.
- `Grep` - `CHANNEL_PREVIEW_ATLAS` (delivery dir segment) present.
- `Grep -n "Log\.d\("` - zero hits in this file.

**Status:** `[x]` done

---

### Step 02.2 - Per-tile region-decode slicer

**Files:** `ui/streams/ChannelPreviewAtlasSlicer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class ChannelPreviewAtlasSlicer(private val atlasFileProvider: () -> File?)`. Define the tile-geometry invariant as companion consts `TILE_W = 240`, `TILE_H = 135`, `COLS = 34` (the shared contract with the offline packer, Phase 06). Add pure `fun rectFor(index: Int): Rect` (`col = index % COLS`, `row = index / COLS`, `left = col * TILE_W`, `top = row * TILE_H`) and `fun isInBounds(index, atlasWidth, atlasHeight): Boolean`. Add `suspend fun tileFor(index: Int): Bitmap?` on `Dispatchers.IO` that opens a cached `BitmapRegionDecoder` for the sheet and calls `decodeRegion(rectFor(index), options)` - NEVER `BitmapFactory.decodeFile` on the whole sheet (ADR-2 / risk §7). Add `suspend fun invalidate()` (guarded by a `Mutex`) that recycles the decoder so a re-download is picked up. Guard against out-of-bounds/`null` decoder returning null, never crashing.

**Verification:**

- `Glob` - `ChannelPreviewAtlasSlicer.kt` exists.
- `Grep` - `class ChannelPreviewAtlasSlicer` matches exactly once.
- `Grep` - `BitmapRegionDecoder` present; `decodeFile` NOT present (zero hits) in this file.
- `Grep` - `TILE_W = 240`, `TILE_H = 135`, `COLS = 34` all present.
- `Grep` - `fun rectFor(`, `fun isInBounds(`, `suspend fun tileFor(`, `suspend fun invalidate(` all present.

**Status:** `[x]` done

---

### Step 02.3 - Store parser unit test

**Files:** `src/test/.../ChannelPreviewAtlasStoreTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Unit-test the sidecar parser: a well-formed `url->index` JSON yields the expected map; a corrupt / non-integer-valued entry is skipped without throwing; an absent file yields an empty map. Use a temp dir for `filesDir` (Robolectric or a context stub as the sibling tests do).

**Verification:**

- `Glob` - `ChannelPreviewAtlasStoreTest.kt` exists.
- Run `.\gradlew.bat testStandardDebugUnitTest --tests "*ChannelPreviewAtlasStoreTest"` - passes.

**Status:** `[x]` done

---

### Step 02.4 - Slicer index-math + bounds unit test

**Files:** `src/test/.../ChannelPreviewAtlasSlicerTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Unit-test the pure geometry: `rectFor(0)` = `(0,0,240,135)`; `rectFor(COLS)` starts the second row (`top = 135`); `rectFor(index)` column wraps at `COLS`. `isInBounds` rejects a negative index and an index whose rect exceeds an 8192x8192 sheet, and accepts an in-range one. No bitmap decode in this test (geometry only).

**Verification:**

- `Glob` - `ChannelPreviewAtlasSlicerTest.kt` exists.
- Run `.\gradlew.bat testStandardDebugUnitTest --tests "*ChannelPreviewAtlasSlicerTest"` - passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (two new classes); set `role`+`status` for both via `set.ps1`.
- [ ] Phase-boundary audit - no unresolved P0/P1. Focus: image-loading/memory path (region decoder lifecycle, `Mutex`-guarded invalidate, no full-sheet decode), Bitmap ownership.

---

## Handoff Notes to Next Phase

- `ChannelPreviewAtlasStore.coords()` gives the `url->index` map; `ChannelPreviewAtlasSlicer.tileFor(index)` gives a 240x135 tile bitmap. Phase 03 wires these into the grid as one `atlasPreviewLoader` lambda (VIDEO-only).
- The `TILE_W/TILE_H/COLS` consts are the geometry invariant the Phase 06 packer must mirror exactly.

---

## Rollback Plan

Revert the phase commit(s). Two new isolated classes, no callers yet - safe to revert.
