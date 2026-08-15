# Phase 04 - Atlas region-decode helper

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (fixture atlas), Phase 03 (sidecar store)
**Blocks:** Phase 05
**Steps done:** 0 / 3

---

## Objective

Provide a helper that turns `(atlasFile, faviconIndex)` into a 32 px tile `Bitmap` using the fixed grid math, with a decode-once + cached-crop strategy that does not jank list scrolling. The cropping math is unit-tested against the Phase 01 fixture atlas.

---

## Prerequisites

- [ ] Phase 03 merged: `FaviconAtlasStore.atlasFile()` + `coords()` available.
- [ ] Phase 01 fixture atlas (`favicon-atlas.png`, 2x2 grid of 32 px tiles) available for the test.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/.../ui/streams/FaviconAtlasSlicer.kt` (new) | New | ≤ 160 |
| `app_v2/src/test/java/.../FaviconAtlasSlicerTest.kt` | New | ≤ 120 |

---

## Steps

### Step 04.1 - Fixed-grid index->rect math (the single source of truth)

**Files:** `app_v2/.../ui/streams/FaviconAtlasSlicer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `FaviconAtlasSlicer`. Define `const val TILE = 32` and `const val COLS = 16` here as the app-side half of the Phase 01/Phase 02 contract (comment them back to PHASE_01). Add a pure function `rectFor(index: Int): Rect` = `Rect(col*TILE, row*TILE, col*TILE+TILE, row*TILE+TILE)` where `col = index % COLS`, `row = index / COLS`. Add a guard `isInBounds(index, atlasWidth, atlasHeight)`: false when `index < 0` or the rect exceeds the atlas bounds (a stale/oversized index must not crash - it yields no thumbnail). Keep this file free of view code so the math is unit-testable.

**Verification:**

- `Grep` - `TILE = 32` and `COLS = 16` both appear in `FaviconAtlasSlicer.kt` with a comment referencing PHASE_01.
- `Grep` - `fun rectFor(` and an in-bounds guard (`isInBounds` or equivalent) exist.
- `.\a.ps1 fk` - compiles (exit 0).

**Status:** `[ ]`

---

### Step 04.2 - Decode-once + cached crop

**Files:** `app_v2/.../ui/streams/FaviconAtlasSlicer.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `suspend fun tileFor(index: Int): Bitmap?`. Strategy = decode-once: lazily decode the whole atlas PNG ONCE (`BitmapFactory.decodeFile(atlasFile.path)`) into a cached `Bitmap` held by the slicer (guarded by a mutex); subsequent calls reuse the cached atlas. Per call, validate via `isInBounds` against the cached atlas dimensions, then `Bitmap.createBitmap(atlas, rect.left, rect.top, TILE, TILE)`. Return null when the atlas file is absent or the index is out of bounds. Run decode + crop off the main thread (`Dispatchers.IO` / `Default`). Provide `fun invalidate()` to drop the cached atlas when a new catalog import replaced the file. Comment WHY decode-once over `BitmapRegionDecoder`: the 32 px / 16-col atlas is small enough to cache whole; escalate to `BitmapRegionDecoder` only if profiling shows the full-atlas bitmap is a memory problem.

**Verification:**

- `Grep` - `BitmapFactory.decodeFile` is called once behind a cached/lazy field (not per `tileFor` call).
- `Grep` - `Bitmap.createBitmap(` slices using the rect from `rectFor`.
- `Grep` - an `invalidate(` method exists.
- `.\a.ps1 fk` - compiles (exit 0).

**Status:** `[ ]`

---

### Step 04.3 - Slicer unit test against the fixture atlas

**Files:** `app_v2/src/test/java/.../FaviconAtlasSlicerTest.kt`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Test `rectFor` math directly (pure): `rectFor(0)` = (0,0,32,32); `rectFor(16)` = (0,32,32,64); `rectFor(17)` = (32,32,64,64). Test `isInBounds`: a negative index and an index whose rect exceeds a 64x64 atlas are out of bounds. For `tileFor`, load the Phase 01 fixture `favicon-atlas.png` (2x2 = 64x64, distinct solid colours) via a temp file and assert: index 0 returns a 32x32 bitmap whose centre pixel matches tile-0's colour; index 3 matches tile-3's colour; an out-of-bounds index returns null. Robolectric or a JVM `BitmapFactory` shadow as the project does elsewhere.

**Verification:**

- `Glob` - `FaviconAtlasSlicerTest.kt` exists.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*FaviconAtlasSlicerTest*"` - the class passes (read per-class XML).

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `rectFor`/`isInBounds` are pure and unit-tested; the index->rect math matches the 32 px / 16-col contract.
- [ ] `tileFor` decodes the atlas once (cached) and crops off the main thread; out-of-bounds/missing -> null, never a crash.
- [ ] The slicer test passes against the committed fixture atlas.
- [ ] Dev log entry added; `catalog_sync.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

Phase 05 calls `coords()[source.url]` -> `tileFor(index)` to bind the leading thumbnail, and `invalidate()` when the list reloads after an import. A null tile = empty slot (the owner's no-placeholder fallback).

---

## Rollback Plan

Revert the phase commit: delete `FaviconAtlasSlicer` + test. Phases 01-03 stay; the persisted sidecar is simply unread.
