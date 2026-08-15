# Phase 02 - Atlas store and slicer

**Strategic spec:** [`../S1201_radio-logo-atlas.md`](../S1201_radio-logo-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Add the app-side read path for the logo atlas - a store that resolves the downloaded payload and a slicer that region-decodes one 136x136 tile - without touching any UI yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (sheet geometry fixed in the packer).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/StreamLogoAtlasStore.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicer.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicerTest.kt` | New | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/streams/StreamLogoAtlasStoreTest.kt` | New | ≤ 90 |

> All four live in `src/main` / `src/test` - the atlas is inert where no payload is installed, so no flavor source set and no `BuildConfig` guard. No layout edited, so no landscape counterpart is due.

---

## Steps

### Step 02.1 - Add StreamLogoAtlasStore

**Files:** `data/repository/streams/StreamLogoAtlasStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamLogoAtlasStore` as a `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`, mirroring `ChannelPreviewAtlasStore`: `dir` = `filesDir/delivery/STREAM_LOGO_ATLAS`, sheet = `stream-logo-atlas.webp`, sidecar = `stream-logo-coords.json`. Expose `atlasFile(): File?` (null unless the file exists) and `suspend fun coords(): Map<String, Int>` decoding the flat `url -> index` JSON on `Dispatchers.IO`. An absent or malformed sidecar yields an empty map logged at `Timber.i` - not-yet-downloaded is the expected state, not an error. Skip non-integer values defensively so one bad entry cannot poison the map. No `write(..)`: the download path owns the bytes.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class StreamLogoAtlasStore` matches exactly once.
- `Grep` - `STREAM_LOGO_ATLAS`, `stream-logo-atlas.webp`, `stream-logo-coords.json` all present.
- `Grep -n "Log\.d\("` - zero hits in the file.
- `Grep` - no `fun write(` in the file.

**Status:** `[x]` done

---

### Step 02.2 - Add StreamLogoAtlasSlicer

**Files:** `ui/streams/StreamLogoAtlasSlicer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `StreamLogoAtlasSlicer(private val atlasFileProvider: () -> File?)` mirroring `ChannelPreviewAtlasSlicer`: a `Mutex`-guarded cached `BitmapRegionDecoder`, pure `rectFor(index)` using `col = index % COLS`, `row = index / COLS`, `isInBounds(index, atlasWidth, atlasHeight)`, `suspend fun tileFor(index): Bitmap?` on `Dispatchers.IO` that region-decodes only the requested tile, and `suspend fun invalidate()` recycling the decoder so a fresh download is picked up. Companion constants `TILE_W = 136`, `TILE_H = 136`, `COLS = 59` with a comment naming the packer function `Build-StreamLogoAtlas` as the other half of the contract. Decode with `BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }` - the sheet carries alpha and must not be flattened. Guard the `BitmapRegionDecoder.newInstance` API 31 fork exactly as the preview slicer does. Catch only `IllegalArgumentException` / `IllegalStateException` / `IOException`, each returning null with a `Timber.i` line.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class StreamLogoAtlasSlicer` matches exactly once.
- `Grep` - `TILE_W = 136`, `TILE_H = 136`, `COLS = 59` all present.
- `Grep` - `Build-StreamLogoAtlas` referenced in a comment.
- `Grep` - `ARGB_8888` present.
- `Grep` - `Build.VERSION_CODES.S` present (the region-decoder API fork).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 02.3 - Unit-test the pure halves

**Files:** `src/test/.../StreamLogoAtlasSlicerTest.kt`, `src/test/.../StreamLogoAtlasStoreTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Port the existing `ChannelPreviewAtlasSlicerTest` / `ChannelPreviewAtlasStoreTest` cases to the logo pair. Cover: `rectFor` for index 0, a mid-row index and a wrap to the next row; `isInBounds` false for a negative index and for an index past the sheet bottom; store returns an empty map for an absent sidecar, for blank text, and for malformed JSON; store parses a well-formed `url -> index` object including a string-encoded integer value.

**Verification:**

- `Glob` - both test files exist.
- `.\a.ps1 fu` - the four new test classes' cases pass; record `expected: 0 failures | actual: <n>` for the new classes only (the suite carries pre-existing failures unrelated to this ticket).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (two new classes).
- [ ] Phase-boundary audit run - focus: decoder ownership and the invalidate race (no tile decode against a recycled decoder), no main-thread decode, no full-sheet materialisation.

---

## Handoff Notes to Next Phase

- `StreamLogoAtlasStore` is injectable but reads a directory nothing writes yet; Phase 03 registers the deliverable that fills it.

---

## Rollback Plan

Delete the four new files - nothing else references them until Phase 04.
