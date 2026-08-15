# Phase 03 — Background Extractor Retry

**Strategic spec:** [`../S0178_video-thumbnail-black-frame-detection.md`](../S0178_video-thumbnail-black-frame-detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Wire darkness detection into `ThumbnailExtractorHelper.extractVideoThumbnail`: replace the fixed t=0 offset with the retry loop using `VideoFrameExtractionPolicy` seek positions (5 s, 15 s, 30 s), up to `MAX_RETRIES_LOCAL` retries. Return the best non-dark frame found, or the first non-null frame if all are dark, or `null` if no frame could be extracted.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`VideoFrameDarknessEvaluator` and `VideoFrameExtractionPolicy` exist and compile).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt` | Modified | ≤ 180 |

> File is currently 138 lines — will not exceed 500 after edit; no backup required.

---

## Steps

### Step 3.1 — Replace fixed-offset extraction with retry loop in extractVideoThumbnail

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt`
**Depends on:** — start of phase (Phase 01 done)

**Prompt for developer:**

> Modify `extractVideoThumbnail(localFile: File, outputFile: File): Boolean` in `ThumbnailExtractorHelper`.
>
> Current behaviour: single call to `getFrameAtTime(0, OPTION_CLOSEST_SYNC)`.
>
> New behaviour:
> 1. Read video duration: call `retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L` (in milliseconds).
> 2. If `durationMs < 5000L` (< 5 s), use `[0L]` as the only candidate seek position (in microseconds).
>    Otherwise use `VideoFrameExtractionPolicy.SEEK_OFFSETS_US` as candidate list.
> 3. Iterate candidates. For each:
>    a. Call `retriever.getFrameAtTime(offsetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)`.
>    b. If null — break (unreadable stream).
>    c. If `VideoFrameDarknessEvaluator.isDark(frame)`:
>       - Log `Timber.d("[S0178] bg-extractor: dark frame at ${offsetUs/1_000_000}s, retrying")`.
>       - If `bestBitmap == null`, store as fallback in `bestBitmap`.
>       - Continue to next candidate (up to `VideoFrameExtractionPolicy.MAX_RETRIES_LOCAL` retries after first attempt, i.e., total ≤ 4 calls).
>    d. Otherwise: store as `bestBitmap`. Break.
> 4. If `bestBitmap == null` — return `false` (no frame extracted, same as current null-path).
> 5. Compress `bestBitmap` to `outputFile` and `recycle()` it. Return `true`.
>
> The `Timber.d("[S0178] bg-extractor:` log tag is a debug verification tag and must remain in code until the spec leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` — `VideoFrameDarknessEvaluator.isDark` present in `ThumbnailExtractorHelper.kt`.
- `Grep` — `VideoFrameExtractionPolicy.SEEK_OFFSETS_US` present.
- `Grep` — `VideoFrameExtractionPolicy.MAX_RETRIES_LOCAL` present.
- `Grep` — `METADATA_KEY_DURATION` present.
- `Grep` — `Timber.d("[S0178] bg-extractor:` present.
- `Grep` — `getFrameAtTime(0,` returns zero hits in the file (old hardcoded t=0 removed).
- `Grep` — `Log\.d(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 7/7 PASS. Files: ThumbnailExtractorHelper.kt (modified). Dev log recorded.

---

### Step 3.2 — Verify compilation and run build

**Files:** (no source change — build verification only)
**Depends on:** Step 3.1

**Prompt for developer:**

> Run `/build` (debug, any flavor). Confirm compilation succeeds.

**Verification:**

- Build exits with code 0.
- `Grep` — `Log\.d(` zero hits in `ThumbnailExtractorHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS: build exit code 0, Log.d zero hits in ThumbnailExtractorHelper.kt.

---

### Step 3.3 — Dev log entry for ThumbnailExtractorHelper

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Step 3.2

**Prompt for developer:**

> Run:
> ```
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt" "S0178" "Add dark-frame retry loop to background video thumbnail extractor"
> ```

**Verification:**

- `Grep` — `ThumbnailExtractorHelper` present in `dev/CHANGELOG.md` with a line dated today.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS: ThumbnailExtractorHelper present in dev/CHANGELOG.md (dated today).

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry present (Step 3.3).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Background preload path now uses the same retry logic and darkness threshold as the network path.
- Both extraction paths share a single policy object — adjusting constants affects both uniformly.
- Phase 04 (docs + catalog cleanup) may proceed once both Phase 02 and Phase 03 are done.

---

## Rollback Plan

Revert phase commit(s) — no schema change, no data migration, no user-facing surface changed.
