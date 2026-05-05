# Phase 03 — Local Playback Fix

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 07
**Steps done:** 1 / 1
**Started:** 2026-05-03
**Completed:** —

---

## Objective

Enable BD-TS `.m2ts` playback for local files by inserting a format-detection + player-recreation step into `playLocalVideoInternal` before ExoPlayer receives the media item.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`TsPacketFormatDetector` available).
- [ ] Phase 02 is ✅ Done (`wrapForBdTs(TsPacketFormat)` available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt` | Modified | ≤ 270 |

---

## Steps

### Step 03.1 — Detect BD-TS format and recreate player for local `.m2ts` files

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `playLocalVideoInternal`, between the file-existence check and the `exoPlayer?.apply { setMediaItem... }` block, insert BD-TS detection and conditional player recreation for `.m2ts`/`.m2t` files on non-`content://` paths:
>
> ```
> if (normalizedPath ends with ".m2ts" or ".m2t" (ignoreCase) AND does not start with "content://") {
>     val format = withContext(Dispatchers.IO) {
>         try {
>             FileInputStream(normalizedPath).use { fis ->
>                 val probe = ByteArray(TsPacketFormatDetector.PROBE_BYTES)
>                 val read = fis.read(probe)
>                 TsPacketFormatDetector.detect(if (read > 0) probe.copyOf(read) else probe)
>             }
>         } catch (e: Exception) {
>             Timber.w(e, "VideoPlayerManager: BD-TS probe failed for $normalizedPath")
>             TsPacketFormat.UNKNOWN
>         }
>     }
>     if (format != TsPacketFormat.STANDARD_188) {
>         // Release existing player (created without BD-TS factory) and recreate with it
>         releasePlayer()
>         val localFactory: DataSource.Factory = DefaultDataSourceFactory(context)
>         val audioAttr = AudioAttributes.Builder()
>             .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
>             .setUsage(C.USAGE_MEDIA)
>             .build()
>         exoPlayer = ExoPlayer.Builder(context)
>             .setMediaSourceFactory(
>                 DefaultMediaSourceFactory(localFactory.wrapForBdTs(TsPacketFormat.BD_192))
>             )
>             .setAudioAttributes(audioAttr, true)
>             .build()
>         exoPlayer?.addListener(playerListener)
>         currentPlayerView?.player = exoPlayer
>     }
> }
> ```
>
> For `STANDARD_188`: fall through to the existing `if (exoPlayer == null && currentPlayerView != null) createPlayer(currentPlayerView!!)` guard — no player recreation needed.
>
> Remove the old `if (exoPlayer == null ...) createPlayer(...)` call from within the `if (isMts)` branch; keep it only for the non-`.m2ts` / `STANDARD_188` code paths.
>
> Imports to add: `DefaultDataSourceFactory` (`androidx.media3.datasource.DefaultDataSourceFactory`), `TsPacketFormat`, `TsPacketFormatDetector`, `wrapForBdTs`.

**Verification:**

- `Grep` — `TsPacketFormatDetector.detect` present in `LocalPlaybackHelper.kt`.
- `Grep` — `releasePlayer()` present (called in the BD-TS recreation branch).
- `Grep` — `DefaultDataSourceFactory` present in `LocalPlaybackHelper.kt`.
- `Grep` — `wrapForBdTs` present in `LocalPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in `LocalPlaybackHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: LocalPlaybackHelper.kt (+35 LOC, 9 new imports). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 03.* above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-04 (assembleStandardDebug).
- [ ] Manual smoke-test: open a local BD-TS `.m2ts` file → video plays without error. _(confirm before marking done)_ MANUAL-REQUIRED
- [ ] Manual smoke-test: open a local 188-byte `.m2ts` file → video plays without error and no BD-TS factory applied. _(confirm before marking done)_ MANUAL-REQUIRED
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Local BD-TS `.m2ts` playback is fixed. Player recreation uses the same pattern as SMB/SFTP/FTP.
- `content://` URIs (MediaStore) are excluded from local file detection — they are not accessible via `FileInputStream`. For `content://` `.m2ts` paths, the existing fallback (no BD-TS layer) remains; a future spec may address this via a `ContentResolver` open + stream peek.
- Phase 04 applies the same detection pattern for cloud sources using `detectTsFormatSuspend`.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
