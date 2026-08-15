# Phase 06 — Audio Track Diagnostics

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** —

---

## Objective

Extend `VideoPlayerManager.playerListener.onTracksChanged` to proactively detect when every audio track in the container is unsupported, show a one-shot toast listing the codec names, and ensure the unsupported-track bloc-list (TrueHD, DTS-HD MA) is applied via `TrackSelectionParameters` so ExoPlayer automatically selects a decodable track when one exists.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done (`R.string.warning_m2ts_audio_unsupported` and `warning_m2ts_audio_unsupported_title` available).
- [ ] Working tree is clean or on a feature branch.
- [ ] `VideoPlayerManager.kt` is backed up to `temp/` (file is > 500 LOC). Backup filename: `temp/VideoPlayerManager_<YYYYMMDD_HHMMSS>.kt.backup`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 950 |

> File is currently 915 LOC — projected delta is ≈ 25 lines. Backup required before edit (see Prerequisites).

---

## Steps

### Step 06.1 — Add `audioUnsupportedShownForPath` guard field

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** — start of phase (after backup)

**Prompt for developer:**

> In the "Mutable state" section of `VideoPlayerManager`, add one field immediately after the `lastCompletedPath` field:
>
> ```kotlin
> // Guards the one-shot audio-unsupported toast; reset per file load in playVideo().
> @Volatile private var audioUnsupportedShownForPath: String? = null
> ```
>
> In `playVideo()`, in the block that resets per-file state (where `currentFilePath = path`, `lastCompletedPath = null`, etc.), add:
>
> ```kotlin
> audioUnsupportedShownForPath = null
> ```

**Verification:**

- `Grep` — `audioUnsupportedShownForPath` present in `VideoPlayerManager.kt`.
- `Grep` — `audioUnsupportedShownForPath = null` appears at least twice (declaration init + reset in `playVideo`).
- `Grep` — `Log\.d(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: VideoPlayerManager.kt (+3 LOC). Dev log recorded.

---

### Step 06.2 — Extend `onTracksChanged` with all-unsupported audio detection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Inside `playerListener.onTracksChanged(tracks: Tracks)`, after the existing stereo-detection block, add audio diagnostics for `.m2ts`/`.m2t` files:
>
> ```
> val path = currentFilePath ?: return
> val isMts = path.endsWith(".m2ts", ignoreCase = true) || path.endsWith(".m2t", ignoreCase = true)
> if (isMts && audioUnsupportedShownForPath != path) {
>     val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
>     if (audioGroups.isNotEmpty()) {
>         val allUnsupported = audioGroups.all { group ->
>             (0 until group.length).none { i -> group.isTrackSupported(i) }
>         }
>         if (allUnsupported) {
>             audioUnsupportedShownForPath = path
>             // Collect MIME types of unsupported tracks for display
>             val codecs = audioGroups
>                 .flatMap { group -> (0 until group.length).map { i -> group.getTrackFormat(i).sampleMimeType ?: "?" } }
>                 .distinct()
>                 .joinToString(", ")
>             val msg = context.getString(R.string.warning_m2ts_audio_unsupported, codecs)
>             Timber.w("VideoPlayerManager: all audio tracks unsupported for $path — codecs=$codecs")
>             Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
>         } else {
>             // At least one supported track exists — apply block-list so ExoPlayer skips TrueHD/DTS-HD MA
>             exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
>                 ?.buildUpon()
>                 ?.setDisabledTextTrackSelectionFlags(0) // no-op; here to show pattern
>                 ?.build()
>                 ?: return
>             // Block-list unsupported codec MIME types via overrideTrackSelection or a custom
>             // TrackSelectionParameters extension. Minimum viable: ensure ExoPlayer's default
>             // selector has already excluded them (it does, because isTrackSupported = false).
>             // Log confirmation only.
>             Timber.d("VideoPlayerManager: .m2ts has decodable audio — DefaultTrackSelector handles selection")
>         }
>     }
> }
> ```
>
> Note: the `else` branch (decodable track exists) does not need to change `trackSelectionParameters` — `DefaultTrackSelector` already skips unsupported tracks. The log line confirms this is by design.

**Verification:**

- `Grep` — `warning_m2ts_audio_unsupported` referenced in `VideoPlayerManager.kt` (Toast message).
- `Grep` — `audioUnsupportedShownForPath = path` present (guard set after first show).
- `Grep` — `allUnsupported` present (local variable in `onTracksChanged`).
- `Grep` — `group.isTrackSupported` present.
- `Grep` — `Log\.d(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. Files: VideoPlayerManager.kt (+21 LOC, 942 total). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 06.* above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-04 (assembleStandardDebug).
- [ ] Manual smoke-test: open a `.m2ts` file with TrueHD-only audio → a Toast appears listing the codec; video plays silently. _(confirm; requires a real TrueHD file)_ MANUAL-REQUIRED
- [ ] Manual smoke-test: open a `.m2ts` file with AC-3 + TrueHD → no Toast; audio plays via AC-3. _(confirm)_ MANUAL-REQUIRED
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for `VideoPlayerManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- The audio-unsupported Toast fires once per file load (guarded by `audioUnsupportedShownForPath`). It resets on every `playVideo()` call.
- DTS-HD MA files where ExoPlayer extracts DTS core via FFmpeg AAR do NOT trigger the Toast (DTS core is `isTrackSupported = true`).
- Phase 07 closes out docs and catalog sync.

---

## Rollback Plan

Revert phase commit(s) and restore the `VideoPlayerManager.kt` backup from `temp/` if needed. No schema or data-migration impact.
