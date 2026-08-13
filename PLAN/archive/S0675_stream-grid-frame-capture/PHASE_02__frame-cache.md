# Phase 02 - Frame Cache

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 1 / 1
**Started:** -
**Completed:** -

---

## Objective

Provide an in-memory, TTL-bounded cache keyed by stream URL that holds the last captured frame bitmap, so scrolling/rebinding does not re-capture and stale frames are refreshed.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/StreamFrameCache.kt` | New | ≤ 120 |

---

## Steps

### Step 02.1 - Create StreamFrameCache (TTL, in-memory, capacity-bounded)

**Files:** `data/repository/streams/StreamFrameCache.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class StreamFrameCache @Inject constructor()` in the streams data package (mirrors `FaviconAtlasStore`'s location). Hold an LRU-bounded map of `url -> (bitmap, capturedAtElapsedRealtime)`; key by the stream URL string. Expose:
> - `fun get(url: String): Bitmap?` - returns the cached bitmap only if it is younger than the TTL, else null.
> - `fun isFresh(url: String): Boolean` - true if a non-expired entry exists (drives whether the snapshot engine should re-capture).
> - `fun put(url: String, bitmap: Bitmap)` - stores/refreshes the entry, evicting the oldest beyond capacity (recycle nothing held by a view).
> - `fun invalidate(url: String)` and `fun clear()` - drop one / all entries (manual refresh).
>
> Use `SystemClock.elapsedRealtime()` for timestamps (monotonic, not wall-clock). TTL and capacity are private consts: `FRAME_TTL_MS = 60_000L`, `MAX_ENTRIES = 64`. Guard concurrent access (the engine writes off the main thread, the adapter reads on it) with a synchronized map or a lock. Do not recycle bitmaps inside the cache - a recycled bitmap may still be set on a live ImageView; rely on GC.

**Verification:**

- `Glob` - `StreamFrameCache.kt` exists.
- `Grep` - `class StreamFrameCache` matches exactly once (declaration).
- `Grep` - `fun get(`, `fun isFresh(`, `fun put(`, `fun invalidate(`, `fun clear(` all present.
- `Grep` - `elapsedRealtime` present (monotonic clock used).
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Created `StreamFrameCache` (@Singleton): LRU LinkedHashMap (access-order, MAX_ENTRIES=64), FRAME_TTL_MS=60_000, `SystemClock.elapsedRealtime` timestamps, synchronized get/isFresh/put/invalidate/clear, no bitmap recycle. All grep predicates matched; `.\a.ps1 fk` exit 0.

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exit 0.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 06.

---

## Handoff Notes to Next Phase

`StreamFrameCache` is the hand-off surface between the snapshot engine (writer, Phase 03) and the grid adapter (reader, Phase 04). `isFresh()` gates re-capture.

---

## Rollback Plan

Revert phase commit - new file, no callers yet.
