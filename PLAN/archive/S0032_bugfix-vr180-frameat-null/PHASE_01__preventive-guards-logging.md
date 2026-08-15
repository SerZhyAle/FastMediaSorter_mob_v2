# Phase 01 — Preventive Guards & Enriched Logging

**Strategic spec:** [`../S0032_bugfix-vr180-frameat-null.md`](../S0032_bugfix-vr180-frameat-null.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Extract the inline `MediaMetadataRetriever.getFrameAtTime` block from `VideoPlayerManager.onRenderedFirstFrame` into a dedicated `VideoPosterExtractor` class with preventive skip guards (native-heap-low, decoder-busy) and enriched Timber log markers carrying explicit `reason=` codes. No fallback hierarchy yet — Phase 02 wires that.

---

## Prerequisites

- [ ] Strategic spec at `Status: Approved` or later (already done — flipped during `/spec-tech`).
- [ ] No work in progress in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` (currently 908 LOC — backup before edit, see Step 01.4).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 900 (current 908; net change −15..−25) |
| `temp/VideoPlayerManager_<YYYYMMDD-HHmm>.kt.backup` | New (timestamped backup) | — |

> `VideoPlayerManager.kt` is >500 LOC → backup step (01.4) is mandatory before edit.

---

## Steps

### Step 01.1 — Create `VideoPosterExtractor` skeleton with `Result` model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new Kotlin class `VideoPosterExtractor` in package `com.sza.fastmediasorter.ui.player`. Define a public sealed enum `SkipReason { OOM, DECODER_BUSY, UNSUPPORTED, UNKNOWN }` and a public data class `Result(val bitmap: android.graphics.Bitmap?, val source: Source, val reason: SkipReason?)` with enum `Source { FRAME_AT_TIME, GLIDE_MEMORY, EXOPLAYER_LAST, PLACEHOLDER, NONE }`. Add a single public suspend function `suspend fun extract(context: android.content.Context, path: String, isPlayerBusy: Boolean): Result` that for now returns `Result(null, Source.NONE, null)` — implementation lands in Steps 01.2 and 01.3. Constructor takes no parameters. Add a `private const val NATIVE_HEAP_LOW_THRESHOLD_BYTES = 50L * 1024 * 1024` companion constant. File must use `Timber` for any logging — never `android.util.Log`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt` exists.
- `Grep` — `class VideoPosterExtractor` matches exactly once.
- `Grep` — `enum class SkipReason` matches once; values `OOM`, `DECODER_BUSY`, `UNSUPPORTED`, `UNKNOWN` all present.
- `Grep` — `enum class Source` matches once; values `FRAME_AT_TIME`, `GLIDE_MEMORY`, `EXOPLAYER_LAST`, `PLACEHOLDER`, `NONE` all present.
- `Grep` — `data class Result` matches once.
- `Grep` — `suspend fun extract(` matches once.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 7/7 PASS. Files: ui/player/VideoPosterExtractor.kt (+33 LOC). Dev log recorded.

---

### Step 01.2 — Implement preventive skip in `extract`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `VideoPosterExtractor.extract`, before any retriever call: read `android.os.Debug.getNativeHeapFreeSize()` once into a local `nativeFreeBytes`. Order of preventive checks: **(1)** if `isPlayerBusy == true` → return `Result(null, Source.NONE, SkipReason.DECODER_BUSY)`; **(2)** if `nativeFreeBytes < NATIVE_HEAP_LOW_THRESHOLD_BYTES` → return `Result(null, Source.NONE, SkipReason.OOM)`. Emit one `Timber.d` line on entry: `Timber.d("VideoPlayerManager: getFrameAtTime attempt path=$path nativeFreeMb=${nativeFreeBytes / (1024 * 1024)} playerBusy=$isPlayerBusy")`. Do not yet attempt `getFrameAtTime` — that lands in Step 01.3.

**Verification:**

- `Grep` — `Debug\.getNativeHeapFreeSize` matches once in the file.
- `Grep` — `SkipReason\.DECODER_BUSY` referenced inside `extract`.
- `Grep` — `SkipReason\.OOM` referenced inside `extract`.
- `Grep` — `getFrameAtTime attempt` appears in exactly one Timber call.
- `Grep -n` — the order of checks: `DECODER_BUSY` precedes `OOM` in the source.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 5/5 PASS. Files: ui/player/VideoPosterExtractor.kt (+13 LOC). Dev log recorded.

---

### Step 01.3 — Implement actual `getFrameAtTime` with reason classification

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> If preventive checks pass, allocate a `MediaMetadataRetriever`, set the data source to `path`, and call `getFrameAtTime(0L, OPTION_CLOSEST_SYNC)` inside a `try`/`finally` that always calls `retriever.release()`. On non-null bitmap → return `Result(bitmap, Source.FRAME_AT_TIME, null)`. On null bitmap → classify reason:
>
> 1. Re-read `Debug.getNativeHeapFreeSize()`; if now `< NATIVE_HEAP_LOW_THRESHOLD_BYTES` → reason = `OOM`.
> 2. Else if `retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)` returns null → reason = `UNSUPPORTED` (the container MIME type is not exposed for unsupported formats).
> 3. Else → reason = `UNKNOWN`.
>
> Catch `kotlinx.coroutines.CancellationException` and rethrow. Catch any other `Exception` → log `Timber.w(e, "VideoPlayerManager: getFrameAtTime threw exception path=$path")` and return `Result(null, Source.NONE, SkipReason.UNKNOWN)`. On the success path emit `Timber.d("VideoPlayerManager: getFrameAtTime succeeded path=$path size=${bitmap.width}x${bitmap.height}")`. On the null path emit `Timber.w("VideoPlayerManager: getFrameAtTime returned null path=$path reason=$reason")` — no `fallback=` field yet (added in Phase 02).

**Verification:**

- `Grep` — `getFrameAtTime\(0L` matches once.
- `Grep` — `retriever\.release\(\)` inside a `finally` block (use `Grep -B 3 "retriever.release"` to confirm).
- `Grep` — `METADATA_KEY_MIMETYPE` referenced once.
- `Grep` — `getFrameAtTime returned null` appears in exactly one `Timber.w` call carrying `reason=`.
- `Grep` — `CancellationException` is rethrown (not swallowed).
- `Grep` — `Log\.d\(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 6/6 PASS. Files: ui/player/VideoPosterExtractor.kt (+38 LOC). Dev log recorded.

---

### Step 01.4 — Wire `VideoPosterExtractor` into `VideoPlayerManager.onRenderedFirstFrame`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> **Backup first:** before editing, copy the current file to `temp/VideoPlayerManager_<YYYYMMDD-HHmm>.kt.backup` (use the project's timestamp scheme — see other backups in `temp/` for the format). Replace the body of `onRenderedFirstFrame` (currently spanning roughly lines 453–483 — the `managerScope.launch(Dispatchers.IO) { ... }` block that calls `getFrameAtTime` directly) with a call to a fresh `VideoPosterExtractor` instance held as a `private val posterExtractor = VideoPosterExtractor()` field on `VideoPlayerManager`. Inside `onRenderedFirstFrame`, keep the existing network/cloud short-circuit (smb / sftp / ftp / cloud `path.startsWith`). Then `managerScope.launch(Dispatchers.IO) { val isBusy = withContext(Dispatchers.Main) { exoPlayer?.let { it.isPlaying || it.isLoading } == true }; val result = posterExtractor.extract(context, path, isBusy); if (result.bitmap != null) { withContext(Dispatchers.Main) { callback(result.bitmap) } } }`. Do not invoke `callback` for null bitmaps in this phase — Phase 02 adds the fallback chain that always delivers a bitmap.
>
> **Important:** Do not preserve the legacy `Timber.w("VideoPlayerManager: getFrameAtTime returned null for $path")` line — its job is now done by `VideoPosterExtractor` with richer reason data. Remove it.

**Verification:**

- `Glob` — `temp/VideoPlayerManager_*.kt.backup` exists.
- `Grep` in `VideoPlayerManager.kt` — `private val posterExtractor` matches once.
- `Grep` in `VideoPlayerManager.kt` — `posterExtractor.extract` matches once inside `onRenderedFirstFrame`.
- `Grep` in `VideoPlayerManager.kt` — `getFrameAtTime` returns zero hits (responsibility moved out).
- `Grep` in `VideoPlayerManager.kt` — `MediaMetadataRetriever` returns zero hits.
- `Grep` — `getFrameAtTime returned null for` (legacy phrase) returns zero hits in `VideoPlayerManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 7/7 PASS. Files: ui/player/VideoPlayerManager.kt (908 → 901 LOC, −7); backup at temp/VideoPlayerManager_20260429-1320.kt.backup. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` BUILD SUCCESSFUL (2026-04-29).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] No regression in poster extraction for an ordinary local 1080p file: with native heap healthy and ExoPlayer idle on `onRenderedFirstFrame`, the existing dynamic-background flow still receives a bitmap (manual probe — log shows `getFrameAtTime succeeded`). MANUAL-REQUIRED — verified at end of Phase 02.

---

## Handoff Notes to Next Phase

- After Phase 01, `VideoPosterExtractor` returns `Result.bitmap = null` on every skip / failure, and `VideoPlayerManager` stops invoking the dynamic-background callback in those cases. Phase 02 wires fallbacks (Glide memory cache → last ExoPlayer frame → static placeholder) so the callback always fires with a non-null bitmap.
- The `Source` and `reason` fields are populated correctly already — Phase 02 only adds new `Source` values and the `fallback=` field on the existing `Timber.w` line.

---

## Rollback Plan

Revert the phase commit. The backup at `temp/VideoPlayerManager_<timestamp>.kt.backup` restores the prior inline implementation if a hotfix is needed. No data, schema, or user-facing surface changed.
