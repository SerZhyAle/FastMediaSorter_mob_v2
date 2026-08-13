# Phase 02 — Network Decoder Retry

**Strategic spec:** [`../S0178_video-thumbnail-black-frame-detection.md`](../S0178_video-thumbnail-black-frame-detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Wire darkness detection into `NetworkVideoFrameDecoder`: retry frame extraction at offsets 5 s → 15 s → 30 s (max 2 retries after first attempt); skip caching if all candidates are dark; add lazy eviction of already-cached dark thumbnails on cache-hit path in `ThumbnailCacheRepositoryImpl`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`VideoFrameDarknessEvaluator` and `VideoFrameExtractionPolicy` exist and compile).
- [ ] Working tree is clean or on a feature branch.
- [ ] `NetworkVideoFrameDecoder.kt` backed up to `temp/` (file is 424 lines; > 500 after edit — backup step 2.1 is mandatory).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ThumbnailCacheRepositoryImpl.kt` | Modified | ≤ 260 |

> `NetworkVideoFrameDecoder.kt` is currently 424 lines. After edits it will exceed 500 lines — create a timestamped backup in `temp/` before editing (Step 2.1).

---

## Steps

### Step 2.1 — Backup NetworkVideoFrameDecoder before editing

**Files:** `temp/NetworkVideoFrameDecoder_<timestamp>.kt` (backup)
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt` to `temp/NetworkVideoFrameDecoder_<YYYYMMDD_HHMM>.kt`. Replace `<YYYYMMDD_HHMM>` with the current date-time. This satisfies the repo backup rule for files > 500 LOC after edit.

**Verification:**

- `Glob` — `temp/NetworkVideoFrameDecoder_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS: temp/NetworkVideoFrameDecoder_20260512_2234.kt created.

---

### Step 2.2 — Add dark-frame retry loop inside extractVideoFrame

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Modify `extractVideoFrame(mediaDataSource, path)` in `NetworkVideoFrameDecoder`.
>
> Current behaviour: one call to `getFrameAtTime(1_000_000L, OPTION_CLOSEST_SYNC)`.
>
> New behaviour:
> 1. Build a list of candidate seek positions: prepend `VideoFrameExtractionPolicy.SEEK_OFFSETS_US` (5 s, 15 s, 30 s) to the existing `1_000_000L` start — i.e., the effective order is `[5_000_000L, 15_000_000L, 30_000_000L]`. The old hardcoded `1_000_000L` offset is replaced by starting at 5 s.
> 2. Track `bestBitmap: Bitmap?` (initially `null`) — the first non-null, non-dark frame found; or if all frames are dark, the first non-null frame.
> 3. Iterate over the candidate list. For each position:
>    a. Call `retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)`.
>    b. If result is `null` — `Timber.w(...)` and `break` (same as current null-path behaviour, to avoid second system warning).
>    c. If `VideoFrameDarknessEvaluator.isDark(frame)` is `true`: log `Timber.d("[S0178] dark frame at ${positionUs/1_000_000}s, trying next offset")`. If `bestBitmap == null`, store frame as `bestBitmap` (best-so-far fallback). Continue to next offset.
>    d. If not dark: store as `bestBitmap`. Break — first good frame wins.
>    e. Stop iterating when `VideoFrameExtractionPolicy.MAX_RETRIES_NETWORK` retries have been used (i.e., at most 3 total calls: offsets index 0, 1, 2).
> 4. Return `ExtractionOutcome(bitmap = bestBitmap)`.
>
> Check video duration before starting: read `METADATA_KEY_DURATION` from the retriever (in milliseconds). If duration < 5000 ms (< 5 s) or duration is unreadable, use `[0L]` as the only candidate instead of `SEEK_OFFSETS_US` to avoid seeking past the end.
>
> The `Timber.d("[S0178] dark frame …")` log tag is a debug verification tag — it must remain in code until the spec leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` — `VideoFrameDarknessEvaluator.isDark` present in `NetworkVideoFrameDecoder.kt`.
- `Grep` — `VideoFrameExtractionPolicy.SEEK_OFFSETS_US` present.
- `Grep` — `VideoFrameExtractionPolicy.MAX_RETRIES_NETWORK` present.
- `Grep` — `METADATA_KEY_DURATION` present.
- `Grep` — `Timber.d("[S0178]` present (debug verification tag).
- `Grep` — `Log\.d(` returns zero hits in the file.
- File line count ≤ 500: `wc -l NetworkVideoFrameDecoder.kt` (or IDE).

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 7/7 PASS. Files: NetworkVideoFrameDecoder.kt (modified, 445 LOC ≤ 500). Dev log recorded.

---

### Step 2.3 — Skip caching dark frames in decode() and add lazy cache eviction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> In `decode()`, at the point where `outcome.bitmap != null` is true and the code calls `saveThumbnailToCache`:
>
> Add a guard: if `VideoFrameDarknessEvaluator.isDark(outcome.bitmap)` is true, **do not** call `saveThumbnailToCache` or `thumbnailCacheRepository.saveThumbnail`. Log `Timber.d("[S0178] all candidates dark — not caching, returning best-effort frame: $fileName")`. Still return the `BitmapDrawableResource` wrapping `outcome.bitmap` (best-effort display), but do not persist it to cache.
>
> Also in `loadFromThumbnailCache()`, after successfully decoding the cached JPEG into `bitmap`:
>
> Add a lazy eviction check: if `VideoFrameDarknessEvaluator.isDark(bitmap)` is true:
> 1. Log `Timber.d("[S0178] cached thumbnail is dark — evicting and re-extracting: $fileName")`.
> 2. Call `runBlocking { thumbnailCacheRepository.deleteThumbnail(path) }`.
> 3. Return `null` — this forces the caller to fall through to fresh extraction.
>
> This handles already-cached dark thumbnails without a Room schema change.

**Verification:**

- `Grep` — two occurrences of `VideoFrameDarknessEvaluator.isDark` in `NetworkVideoFrameDecoder.kt` (one in extraction path, one in cache-load path).
- `Grep` — `deleteThumbnail(path)` present in `loadFromThumbnailCache` context.
- `Grep` — `not caching` or `all candidates dark` log message present.
- `Grep` — `Log\.d(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS (4/4 predicates). Note: isDark occurrences = 3 (loop + decode guard + cache eviction) vs spec predicate "2" — spec count was underspecified; all three calls required by prompts 2.2+2.3. deleteThumbnail(path) present; "all candidates dark" log present; Log.d zero hits.

---

### Step 2.4 — Verify compilation and run build

**Files:** (no source change — build verification only)
**Depends on:** Steps 2.2, 2.3

**Prompt for developer:**

> Run `/build` (debug, any flavor). Confirm compilation succeeds. Check that `Grep Log\.d(` returns zero hits in both touched files.

**Verification:**

- Build exits with code 0.
- `Grep` — `Log\.d(` zero hits in `NetworkVideoFrameDecoder.kt`.
- `Grep` — `Log\.d(` zero hits in `ThumbnailCacheRepositoryImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS: build exit code 0, Log.d zero hits in both touched files.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Network extraction path now attempts up to 3 frames before returning best-effort or null.
- Dark cached frames are lazily evicted on next access — no schema migration required.
- Phase 03 (background extractor) is independent; can be merged before or after Phase 02.

---

## Rollback Plan

Revert phase commit(s). The backup in `temp/NetworkVideoFrameDecoder_<timestamp>.kt` is available for reference. No Room schema change, no DB migration to undo.
