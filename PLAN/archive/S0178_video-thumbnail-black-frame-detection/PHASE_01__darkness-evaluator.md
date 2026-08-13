# Phase 01 — Darkness Evaluator

**Strategic spec:** [`../S0178_video-thumbnail-black-frame-detection.md`](../S0178_video-thumbnail-black-frame-detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Introduce `VideoFrameDarknessEvaluator` — a standalone utility object that computes mean luma of a `Bitmap` and classifies it as dark using a configurable threshold — and `VideoFrameExtractionPolicy` object that holds seek offsets and retry limits. No changes to extraction paths yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none — first phase)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameDarknessEvaluator.kt` | **New** | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt` | **New** | ≤ 40 |

---

## Steps

### Step 1.1 — Create VideoFrameExtractionPolicy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `object VideoFrameExtractionPolicy` in package `com.sza.fastmediasorter.utils`.
>
> Constants:
> - `SEEK_OFFSETS_US: LongArray = longArrayOf(5_000_000L, 15_000_000L, 30_000_000L)` — candidate seek positions in microseconds (5 s, 15 s, 30 s).
> - `MAX_RETRIES_NETWORK: Int = 2` — maximum retry attempts after first extraction on the network path (total 3 attempts including first).
> - `MAX_RETRIES_LOCAL: Int = 3` — maximum retry attempts on the background/local path (total 4 attempts).
> - `DARKNESS_LUMA_THRESHOLD: Float = 15f / 255f` — mean luma below this value classifies a frame as dark (≈ 5.9% of full range; see strategic ADR-1).
> - `DOWNSCALE_SIZE: Int = 64` — width/height to downscale `Bitmap` before luma scan.
>
> No methods. No Hilt annotations. Plain `object`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt` exists.
- `Grep` — `object VideoFrameExtractionPolicy` matches exactly once in that file.
- `Grep` — `SEEK_OFFSETS_US` present in that file.
- `Grep` — `DARKNESS_LUMA_THRESHOLD` present in that file.
- `Grep` — `Log\.d(` returns zero hits in that file (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 5/5 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt (new, 31 LOC). Dev log recorded.

---

### Step 1.2 — Create VideoFrameDarknessEvaluator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameDarknessEvaluator.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `object VideoFrameDarknessEvaluator` in package `com.sza.fastmediasorter.utils`.
>
> Single public function:
> ```kotlin
> fun isDark(bitmap: Bitmap): Boolean
> ```
>
> Implementation:
> 1. Downscale `bitmap` to `VideoFrameExtractionPolicy.DOWNSCALE_SIZE × VideoFrameExtractionPolicy.DOWNSCALE_SIZE` using `Bitmap.createScaledBitmap(bitmap, size, size, false)`. Store in a local val; recycle it in a `finally` block unless it is the same reference as the input (i.e., `scaled !== bitmap`).
> 2. Allocate an `IntArray` of size `scaled.width * scaled.height`. Call `scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)`.
> 3. Compute mean luma using the BT.601 formula (compatible with API 23):
>    ```
>    luma = (0.299 * R + 0.587 * G + 0.114 * B) / 255
>    ```
>    where `R`, `G`, `B` are extracted from each `Int` pixel via `Color.red()`, `Color.green()`, `Color.blue()` (all available API 1+). Sum the per-pixel luma values; divide by pixel count to get mean luma.
> 4. Return `meanLuma < VideoFrameExtractionPolicy.DARKNESS_LUMA_THRESHOLD`.
>
> No Hilt annotations. No `Log.d()` calls. Use `Timber.v()` if logging is desired.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameDarknessEvaluator.kt` exists.
- `Grep` — `object VideoFrameDarknessEvaluator` matches exactly once.
- `Grep` — `fun isDark(bitmap: Bitmap): Boolean` present.
- `Grep` — `DARKNESS_LUMA_THRESHOLD` referenced in the file (uses policy constant).
- `Grep` — `Log\.d(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 5/5 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameDarknessEvaluator.kt (new, 45 LOC). Dev log recorded.

---

### Step 1.3 — Verify compilation

**Files:** (no source change — build verification only)
**Depends on:** Steps 1.1, 1.2

**Prompt for developer:**

> Run `/build` (debug, any flavor) and confirm the two new files compile without errors. Do not invoke Gradle directly.

**Verification:**

- Build exits with code 0 (no compile errors).
- `Grep` — `import com.sza.fastmediasorter.utils.VideoFrameDarknessEvaluator` can be resolved (no red underlines in IDE) — verified via successful build.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS: build exit code 0. Both new files compile without errors.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entries added for both new files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new public classes added).

---

## Handoff Notes to Next Phase

- `VideoFrameExtractionPolicy` and `VideoFrameDarknessEvaluator` are ready for use in Phases 02 and 03.
- Both are plain `object`s — no Hilt wiring required.
- Phases 02 and 03 may proceed in parallel after this phase is complete.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no Room schema change, no user-facing surface changed.
