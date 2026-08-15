# Phase 01 - Tile-pack reader

**Strategic spec:** [`../S1445_atlas-tile-random-access.md`](../S1445_atlas-tile-random-access.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Introduce the random-access tile reader with its in-memory LRU cache; no caller is switched to it yet.

---

## Prerequisites

- [ ] Working tree state acknowledged (dirty tree is normal here).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamTilePackReader.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamTilePackReaderTest.kt` | New | ≤ 140 |

---

## Steps

### Step 01.1 - Write the reader

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamTilePackReader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `class StreamTilePackReader(packFileProvider: () -> File?, cacheBudgetBytes: Int)`. Expose `fun hasPack(): Boolean`, `suspend fun tile(index: Int): Bitmap?` and `suspend fun invalidate()`. `tile` runs on `Dispatchers.IO`, returns a cached bitmap when present, otherwise reads the ZIP entry named `index.toString()` through a lazily opened `java.util.zip.ZipFile`, decodes it with `BitmapFactory.decodeStream`, stores it in the cache and returns it. A missing pack file, a missing entry, or a decode failure returns null; `IOException` is caught and logged at info, never error. Guard the lazily opened `ZipFile` with a `Mutex` exactly as `ChannelPreviewAtlasSlicer` guards its decoder, and have `invalidate()` close the ZIP, drop the reference and evict the cache. Back the cache with `android.util.LruCache` whose `sizeOf` returns `Bitmap.byteCount`, and add a companion `fun budgetBytes(context: Context): Int` returning an eighth of `ActivityManager.memoryClass` coerced into 4..24 MB.

**Why:**

Strategic §1 measured that a per-tile `decodeRegion` against the sprite sheet costs a share of a full 61,7 Mpx decode because WebP has no random access, and strategic ADR-1/ADR-3 answer that with a container whose entries are addressable individually plus a cache, so a tile costs one small decode and a re-shown tile costs nothing.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamTilePackReader.kt` exists.
- `Grep` - `class StreamTilePackReader` matches exactly once.
- `Grep` - `suspend fun tile(`, `suspend fun invalidate(`, `fun hasPack(`, `fun budgetBytes(` each present.
- `Grep` - `BitmapRegionDecoder` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 01.2 - Unit-test the reader

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamTilePackReaderTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a Robolectric test that builds a real ZIP in a temp directory with stored entries named `0`, `1`, `2`, then asserts: `hasPack()` is false when the provider returns null and true for the built file; `tile(index)` returns null for an index with no entry and for a negative index; `tile` returns null rather than throwing when the entry bytes are not an image; `invalidate()` is safe to call twice and before any read. Mirror the existing test style of `ChannelPreviewAtlasSlicerTest`.

**Why:**

Strategic §11 criterion 6 requires the pack round trip to be covered by tests, and the entry-name contract is the one thing that silently drifts between the offline packer and the app - risk table row "расхождение упаковщика и приложения по имени записи".

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `class StreamTilePackReaderTest` matches exactly once.
- Run `pwsh -NoProfile -File ./a.ps1 fu` filtered to this class, or the full unit suite; expected: this class passes.

**Status:** `[x]` done

---

### Step 01.3 - Compile

**Files:** -
**Depends on:** Step 01.2

**Prompt for developer:**

> Run the fast Kotlin compile check for the standard flavor and read its verdict in the same turn.

**Why:**

Strategic §3.2 pins the reader to API 23 as its floor, and a compile check is the cheapest proof that nothing in the new file reaches past it.

**Verification:**

- `pwsh -NoProfile -File ./a.ps1 fk` - expected exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exit 0.
- [ ] Dev log entry added for the files in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The reader is the only place that knows the container layout: entry name = index as a decimal string, no extension, image format irrelevant. Phase 02 wires callers, Phase 03 must emit exactly that naming.

---

## Rollback Plan

Revert the phase commit - the new class has no callers yet.
