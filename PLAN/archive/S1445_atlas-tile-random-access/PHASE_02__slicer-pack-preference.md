# Phase 02 - Slicer pack preference

**Strategic spec:** [`../S1445_atlas-tile-random-access.md`](../S1445_atlas-tile-random-access.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Both artwork slicers read the tile pack when it is installed and keep the sprite-sheet path as the fallback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/ChannelPreviewAtlasStore.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/StreamLogoAtlasStore.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/ChannelPreviewAtlasSlicer.kt` | Modified | ≤ 170 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicer.kt` | Modified | ≤ 175 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 02.1 - Resolve the pack file in both stores

**Files:** `ChannelPreviewAtlasStore.kt`, `StreamLogoAtlasStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun tilePackFile(): File?` to both stores next to their existing `atlasFile()`, returning `channel-preview-tiles.zip` and `stream-logo-tiles.zip` respectively from the same payload directory, or null when the file is absent.

**Why:**

Strategic §5.2 keeps payload resolution in the store and rendering in the slicer, so the slicer must receive the pack the same way it receives the sheet today - through a provider owned by the store.

**Verification:**

- `Grep` - `fun tilePackFile()` matches once in each store.
- `Grep` - `channel-preview-tiles.zip` matches once, `stream-logo-tiles.zip` matches once.

**Status:** `[x]` done

---

### Step 02.2 - Prefer the pack in both slicers

**Files:** `ChannelPreviewAtlasSlicer.kt`, `StreamLogoAtlasSlicer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Give each slicer a second constructor parameter holding a `StreamTilePackReader`. In `tileFor`, return the pack reader's tile when `hasPack()` is true, and only otherwise fall through to the existing `BitmapRegionDecoder` path. Make `invalidate()` invalidate the pack reader as well as the decoder. Do not change the geometry helpers, the bounds check, or the existing catch blocks.

**Why:**

Strategic ADR-4 keeps the sheet path alive because updating the payload is the user's action, so a user who has not accepted the update must keep seeing pictures rather than empty cells.

**Verification:**

- `Grep` - `StreamTilePackReader` referenced in both slicer files.
- `Grep` - `decodeRegion` still present in both slicer files (the fallback survives).
- `pwsh -NoProfile -File ./a.ps1 fk` - expected exit 0.

**Status:** `[x]` done

---

### Step 02.3 - Wire the readers in the activity

**Files:** `StreamsActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Construct one `StreamTilePackReader` per payload from the matching store's `tilePackFile()` and `StreamTilePackReader.budgetBytes(this)`, and pass each into its slicer. Leave the existing post-install and post-import invalidation calls untouched - they already invalidate the slicers.

**Why:**

Strategic §4 records that the activity owns slicer construction and the post-install invalidation, so wiring the reader anywhere else would split one lifecycle across two owners.

**Verification:**

- `Grep` - `StreamTilePackReader(` matches twice in `StreamsActivity.kt`.
- `Grep` - `atlasSlicer.invalidate()` and `logoSlicer.invalidate()` still present.

**Status:** `[x]` done

---

### Step 02.4 - Compile and check the touched files

**Files:** -
**Depends on:** Step 02.3

**Prompt for developer:**

> Run the fast Kotlin compile check and the scoped closure gates over the files this phase touched.

**Why:**

Strategic §3.2 forbids any path that materialises the whole sheet, and the scoped gate run is where an accidental regression in the touched files surfaces before the device test.

**Verification:**

- `pwsh -NoProfile -File ./a.ps1 fk` - expected exit 0.
- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<touched>" -ScopeToFile -Target "S1445" -Description "Tile pack preference in stream artwork slicers" -ChangeType Kotlin` - expected `post-change: PASS`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exit 0.
- [ ] Dev log entry added for the files in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The app now reads `channel-preview-tiles.zip` / `stream-logo-tiles.zip` from the payload directory when present. Phase 03 must publish assets that land under exactly those file names.

---

## Rollback Plan

Revert the phase commit - the sheet path is untouched, so reverting restores the current behaviour exactly.
