# Phase 03 — Persistent Failure Cache

**Strategic spec:** [`../spec_browse-thumbnail-reliability.md`](../spec_browse-thumbnail-reliability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Create `VideoExtractionFailurePersistence` (SharedPreferences-backed, no Room schema change) so that `markVideoAsFailed()` survives app restarts. Wire it into `NetworkFileDataFetcher`'s companion lazy-init. TTL = 7 days, max 500 entries (FIFO eviction). Also hook `clearFailedVideoCache()` to clear the persistent store.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt` | **New** | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt` | Modified | ≤ 760 |

> `NetworkFileModelLoader.kt` is ~720 lines — backup required before edit.

---

## Steps

### Step 3.1 — Create VideoExtractionFailurePersistence

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new Kotlin object `VideoExtractionFailurePersistence` in `data/network/glide/`.
> Use `SharedPreferences` (`MODE_PRIVATE`, name `"video_extraction_failures"`) backed by `FastMediaSorterApp.appContext`.
> Storage format: `Set<String>` where each entry is `"<path>|<timestampMs>"`.
>
> Implement the following methods:
>
> - `fun loadAll(): Map<String, Long>` — reads prefs, parses each `"path|ts"` entry, drops expired entries (older than 7 days), returns `path → timestampMs`.
> - `fun persistFailure(path: String)` — loads current set, adds `"path|${currentTimeMs}"`, enforces max 500 entries by dropping oldest by timestamp, writes back.
> - `fun clearAll()` — clears the SharedPreferences entirely.
>
> Constants:
> - `private const val TTL_MS = 7L * 24 * 60 * 60 * 1000`
> - `private const val MAX_ENTRIES = 500`
> - `private const val PREFS_NAME = "video_extraction_failures"`
> - `private const val KEY_FAILURES = "failures"`
>
> Use `Timber` for all logging. No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt` exists.
- `Grep` — `object VideoExtractionFailurePersistence` in that file returns exactly **1** match.
- `Grep` — `fun loadAll` in that file returns exactly **1** match.
- `Grep` — `fun persistFailure` in that file returns exactly **1** match.
- `Grep` — `fun clearAll` in that file returns exactly **1** match.
- `Grep` — `Log\.d\(` in that file returns **zero** hits.

**Status:** `[x]` done

---

### Step 3.2 — Backup NetworkFileModelLoader before edit

**Files:** `temp/`
**Depends on:** Step 3.1

**Prompt for developer:**

> Create a timestamped backup: `Copy-Item app_v2\src\main\java\com\sza\fastmediasorter\data\network\glide\NetworkFileModelLoader.kt temp\NetworkFileModelLoader_<timestamp>.kt.backup`

**Verification:**

- `Glob` — `temp/NetworkFileModelLoader_*.kt.backup` matches at least one file.

**Status:** `[x]` done

---

### Step 3.3 — Wire VideoExtractionFailurePersistence into NetworkFileDataFetcher

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> In `NetworkFileDataFetcher` companion object:
>
> 1. Add a `@Volatile private var persistenceInitialized = false` field (companion-level).
> 2. Add a private `fun ensurePersistenceLoaded()` helper: uses double-checked locking. On first call, loads `VideoExtractionFailurePersistence.loadAll()` and merges results into `failedVideos` (only adds entries not already present). Sets `persistenceInitialized = true`.
> 3. In `isVideoFailed(path)`: call `ensurePersistenceLoaded()` before checking `failedVideos`.
> 4. In `markVideoAsFailed(path)`: after adding to `failedVideos`, also call `VideoExtractionFailurePersistence.persistFailure(path)`.
> 5. In `clearFailedVideoCache()`: also call `VideoExtractionFailurePersistence.clearAll()`.
>
> `ensurePersistenceLoaded()` should be safe to call from multiple threads — use `synchronized(failedVideos)` for the check-and-init block.

**Verification:**

- `Grep` — `ensurePersistenceLoaded` in `NetworkFileModelLoader.kt` returns at least **3** matches (declaration + calls in `isVideoFailed` and `markVideoAsFailed`).
- `Grep` — `VideoExtractionFailurePersistence.persistFailure` in `NetworkFileModelLoader.kt` returns exactly **1** match.
- `Grep` — `VideoExtractionFailurePersistence.clearAll` in `NetworkFileModelLoader.kt` returns exactly **1** match.
- `Grep` — `Log\.d\(` in `NetworkFileModelLoader.kt` returns **zero** hits.

**Status:** `[x]` done

---

### Step 3.4 — Dev log for Phase 03 files

**Files:** —
**Depends on:** Step 3.3

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt" "spec-dev" "Phase 03: new persistent failure cache for video extraction (SharedPrefs, TTL 7d, max 500)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" "spec-dev" "Phase 03: wire VideoExtractionFailurePersistence into markVideoAsFailed / isVideoFailed"
> ```

**Verification:**

- `Grep` — `VideoExtractionFailurePersistence.kt` in `dev/CHANGELOG.md` matches at least **1** line added after today's date.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entries added for new and modified files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public class `VideoExtractionFailurePersistence`): `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Phase 03 establishes that failed-video state survives app restarts.
- The Phase 01 pre-check in `AdapterThumbnailLoader` already calls `isVideoFailed()` — after Phase 03, this pre-check also covers cross-session failures.
- Final cleanup in Phase 04.

---

## Rollback Plan

Revert phase commit(s). `SharedPreferences` `video_extraction_failures` will remain on device but is self-TTL'd — no data migration needed.
