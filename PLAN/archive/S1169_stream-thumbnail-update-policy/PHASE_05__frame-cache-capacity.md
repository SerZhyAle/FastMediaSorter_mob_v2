# Phase 05 - Frame-cache capacity for large catalogs

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (independent; ordered late to keep risk isolated)
**Blocks:** Phase 06
**Steps done:** 1 / 1
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Step 05.1: PASS (MAX_CACHE_BYTES present, MAX_ENTRIES=64 removed, allocationByteCount used, StreamFrameCacheTest tests=6 failures=0). detekt scoped PASS both files.

---

## Objective

Stop the fixed `MAX_ENTRIES = 64` LRU from evicting a still-visible tile in catalogs larger than 64 rows (which forces a redundant re-capture on the next rebind). Raise/scale the in-memory frame cap so the visible window plus a scroll margin always fits, bounded by a byte budget rather than a magic row count.

---

## Prerequisites

- [ ] `StreamFrameCache` unchanged from main.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/StreamFrameCache.kt` | Modified | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/streams/StreamFrameCacheTest.kt` | New | ≤ 160 |

---

## Steps

### Step 05.1 - Byte-budget LRU + eviction test

**Files:** `data/repository/streams/StreamFrameCache.kt`, `StreamFrameCacheTest.kt` (new)
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the fixed `MAX_ENTRIES = 64` row cap with a byte-budget LRU: evict the eldest while the summed `bitmap.allocationByteCount` exceeds `MAX_CACHE_BYTES` (a captured 640x360 RGB_565 frame is ~460 KB; budget the cache at `12 * 1024 * 1024` = ~26 frames of headroom over a typical visible window, but expressed in bytes so it self-scales with tile size). Keep a small hard row ceiling as a backstop (e.g. 256) to bound map size. Preserve `live`/`restored` semantics, `isFresh`, `hasEntry`, `putRestored` no-clobber, and the `lock`. Add a new unit test `StreamFrameCacheTest` covering: `get` returns last frame; `isFresh` false for restored and for expired live; byte-budget eviction drops the eldest when the budget is exceeded; `putRestored` does not clobber a live entry.

**Verification:**

- `Grep` - `MAX_CACHE_BYTES` present; `MAX_ENTRIES = 64` no longer present.
- `Glob` - `StreamFrameCacheTest.kt` exists.
- `Grep` - `allocationByteCount` referenced in `StreamFrameCache.kt`.
- `--tests *StreamFrameCacheTest` green.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] `Step 05.1` is `[x] done`.
- [ ] `/build` passes.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `--tests *StreamFrameCacheTest` green.
- [ ] Dev log entry for both files.
- [ ] Phase-boundary audit: all cache access still under `lock`; no bitmap recycled (a frame may be live on an ImageView).

---

## Handoff Notes to Next Phase

Visible tiles no longer evict under normal catalog sizes, closing the last redundant-recapture path. Phase 06 finalizes catalog/dev-log and records the capability.

---

## Rollback Plan

Revert the phase commit(s) - cache reverts to the fixed 64-row cap; no data or schema change.
