# Phase 01 — Adapter Thumbnail Hardening

**Strategic spec:** [`../spec_browse-thumbnail-reliability.md`](../spec_browse-thumbnail-reliability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Fix two independent bugs in `AdapterThumbnailLoader`: (1) unify `DiskCacheStrategy` for local video thumbnails so list-view and grid-view share the same Glide disk cache entry; (2) add a failed-cache pre-check for network videos before starting a Glide load (same guard already present for EPUB and PDF).

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done. (none required)
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 625 |

> File is ~622 lines — backup required before edit (rule: >500 LOC).

---

## Steps

### Step 1.1 — Backup AdapterThumbnailLoader before edit

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `AdapterThumbnailLoader.kt` in `temp/` before any changes.
> Command: `cp app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt temp/AdapterThumbnailLoader_$(date +%Y%m%d_%H%M%S).kt.backup`
> (On Windows PowerShell: `Copy-Item <src> temp\AdapterThumbnailLoader_<timestamp>.kt.backup`)

**Verification:**

- `Glob` — `temp/AdapterThumbnailLoader_*.kt.backup` matches at least one file.

**Status:** `[x]` done

---

### Step 1.2 — Unify DiskCacheStrategy for local video thumbnails

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `AdapterThumbnailLoader.loadVideo()`, the local-video Glide request (the `else` branch, ~line 583) currently uses `.diskCacheStrategy(if (isListMode) DiskCacheStrategy.RESOURCE else DiskCacheStrategy.DATA)`.
> Change this to always use `DiskCacheStrategy.RESOURCE` — remove the conditional entirely.
> Remove the inline comment `// List: RESOURCE (decoded bitmap), Grid: DATA — preserve original behaviour` as it will be stale.

**Verification:**

- `Grep` — pattern `DiskCacheStrategy.DATA` in `AdapterThumbnailLoader.kt` returns **zero** hits.
- `Grep` — pattern `diskCacheStrategy\(DiskCacheStrategy.RESOURCE\)` in `AdapterThumbnailLoader.kt` matches at least 5 times (unchanged RESOURCE usages plus the fixed one).

**Status:** `[x]` done

---

### Step 1.3 — Add network video failed-cache pre-check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> In `AdapterThumbnailLoader.loadVideo()`, the `isNetworkPath` branch (~line 527) starts a Glide request without checking the failed cache first — unlike EPUB and PDF branches which have an early-return guard.
> Add a pre-check at the top of the `isNetworkPath` block, before the Glide call:
>
> ```kotlin
> if (NetworkFileDataFetcher.isVideoFailed(file.path)) {
>     Timber.v("Skipping video thumbnail load for ${file.name} (cached as failed)")
>     showGeneratedPlaceholder(imageView, file)
>     return
> }
> ```
>
> Insert after the existing `if (isListMode && !getShowVideoThumbnails())` guard (keep that guard first).

**Verification:**

- `Grep` — `isVideoFailed\(file\.path\)` in `AdapterThumbnailLoader.kt` returns exactly **1** match (in the `loadVideo` network branch).
- `Grep` — `Log\.d\(` in `AdapterThumbnailLoader.kt` returns **zero** hits (Timber-only rule).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `AdapterThumbnailLoader.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Phase 02 can now assume that `GlideCacheStats.recordLoad()` receives accurate `dataSource` values — no more DATA/RESOURCE split for local videos.
- Phase 03 builds on the `isVideoFailed()` path — the pre-check in Step 1.3 will immediately benefit once persistent failure cache is wired in Phase 03.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no DB change, no user-facing surface added. Restore from `temp/AdapterThumbnailLoader_*.kt.backup` if needed.
