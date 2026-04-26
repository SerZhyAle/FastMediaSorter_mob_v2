# Phase 02 — Honest Diagnostics

**Strategic spec:** [`../spec_browse-thumbnail-reliability.md`](../spec_browse-thumbnail-reliability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Extend `GlideCacheStats` with a `ThumbnailCacheRepository` hits counter and fix the misleading "Zero disk cache hits" warning that fires when thumbnails are served from the persistent thumbnail repository (which Glide classifies as `LOCAL`, not `DISK_CACHE`). Wire the new counter into `NetworkVideoFrameDecoder`.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt` | Modified | ≤ 365 |

---

## Steps

### Step 2.1 — Add thumbnailRepoCacheHits counter and update logStats

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `GlideCacheStats`:
>
> 1. Add a new `AtomicInteger` field: `private val thumbnailRepoCacheHits = AtomicInteger(0)`
> 2. Add a new public method: `fun recordThumbnailRepoHit() { thumbnailRepoCacheHits.incrementAndGet() }`
> 3. Update `reset()` to also reset this counter: `thumbnailRepoCacheHits.set(0)`
> 4. In `logStats()`: include this counter in `total` calculation; add a log line `"📂 Thumbnail repo hits: $repo (%.1f%%)".format(repoPercent)` alongside the other counters.
> 5. Fix the misleading warning at line 81: change condition from `if (disk == 0 && total > 10)` to `if (disk == 0 && repo == 0 && total > 10)` so the warning does not fire when thumbnails are legitimately served from the thumbnail repository.
> 6. Update `getSummary()` to include repo hits: e.g., `"Cache: D=$disk M=$memory R=$repo O=$other"`

**Verification:**

- `Grep` — `thumbnailRepoCacheHits` in `GlideCacheStats.kt` returns exactly **3** hits (field declaration, `recordThumbnailRepoHit()`, `reset()`).
- `Grep` — `recordThumbnailRepoHit` in `GlideCacheStats.kt` returns exactly **1** match (method declaration; call-site is in NetworkVideoFrameDecoder).
- `Grep` — `disk == 0 && repo == 0` in `GlideCacheStats.kt` returns exactly **1** match (updated warning condition).

**Status:** `[x]` done

---

### Step 2.2 — Wire recordThumbnailRepoHit into NetworkVideoFrameDecoder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `NetworkVideoFrameDecoder.loadFromThumbnailCache()`, when returning a non-null `Resource<Drawable>` (i.e., when the cached thumbnail is successfully decoded), call `GlideCacheStats.recordThumbnailRepoHit()` just before the return statement.
> This makes ThumbnailCacheRepository hits visible in diagnostics.
> Import `com.sza.fastmediasorter.utils.GlideCacheStats` if not already imported.

**Verification:**

- `Grep` — `GlideCacheStats.recordThumbnailRepoHit\(\)` in `NetworkVideoFrameDecoder.kt` returns exactly **1** match.
- `Grep` — `import com.sza.fastmediasorter.utils.GlideCacheStats` in `NetworkVideoFrameDecoder.kt` returns exactly **1** match.

**Status:** `[x]` done

---

### Step 2.3 — Dev log for Phase 02 files

**Files:** —
**Depends on:** Step 2.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt" "spec-dev" "Phase 02: add thumbnailRepoCacheHits counter; fix misleading zero-disk-cache warning"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt" "spec-dev" "Phase 02: wire GlideCacheStats.recordThumbnailRepoHit() on ThumbnailCache hit"
> ```

**Verification:**

- `Grep` — `GlideCacheStats.kt` in `dev/CHANGELOG.md` matches at least **1** line added after today's date.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added for both modified files.

---

## Handoff Notes to Next Phase

- Phase 03 (persistent failure cache) is independent from Phase 02 and can proceed in parallel; both depend only on Phase 01.
- After Phase 02, `GlideCacheStats.logStats()` will no longer falsely warn about zero disk cache hits when thumbnails come from the repository.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
