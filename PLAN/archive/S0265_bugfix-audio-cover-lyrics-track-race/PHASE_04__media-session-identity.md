# Phase 04 - Media-Session Notification Identity Hardening

**Strategic spec:** [`../S0265_bugfix-audio-cover-lyrics-track-race.md`](../S0265_bugfix-audio-cover-lyrics-track-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Replace the index-only guard inside `pushArtworkToNotification` with a compound `(currentMediaItemIndex AND currentMediaItem.mediaId)` guard. In playlist mode the index changes across tracks and the old guard worked; in single-item rotation mode the index stays at `0` and the old guard let the previous track's artwork reach the current track's `MediaItem`. The compound guard correctly distinguishes both modes. Implements strategic §5.1 pillar D and ADR-3.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Build is green on `standard` flavor.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` | Modified | ≤ 530 |

> No new files.

---

## Steps

### Step 04.1 - Compound `(index, mediaId)` guard in `pushArtworkToNotification`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Modify `pushArtworkToNotification(bitmap: Bitmap)` (around lines 421-447). Capture both the current media-item index **and** its stable `mediaId` (from `Player.currentMediaItem.mediaId`) at the start of the function, alongside the existing `currentItem` capture. At the end, change the guard from index-only to compound:
>
> ```kotlin
> private fun pushArtworkToNotification(bitmap: Bitmap) {
>     val player = binding.playerView.player ?: return
>     if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
>     val currentIndex = player.currentMediaItemIndex
>     val currentItem = player.currentMediaItem ?: return
>     val currentMediaId = currentItem.mediaId
>     lifecycleScope.launch(Dispatchers.IO) {
>         try {
>             val maxSide = 512
>             val w = bitmap.width; val h = bitmap.height
>             val scaled = if (w > maxSide || h > maxSide) {
>                 val scale = maxSide.toFloat() / maxOf(w, h)
>                 Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
>             } else bitmap
>             val bytes = java.io.ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
>             if (scaled !== bitmap) scaled.recycle()
>             val updatedItem = currentItem.buildUpon()
>                 .setMediaMetadata(currentItem.mediaMetadata.buildUpon().setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build())
>                 .build()
>             withContext(Dispatchers.Main) {
>                 val nowIndex = player.currentMediaItemIndex
>                 val nowMediaId = player.currentMediaItem?.mediaId
>                 if (nowIndex == currentIndex && nowMediaId == currentMediaId) {
>                     player.replaceMediaItem(currentIndex, updatedItem)
>                     Timber.d("pushArtworkToNotification: pushed ${bytes.size}b at index=$currentIndex mediaId=$currentMediaId")
>                 } else {
>                     Timber.d("pushArtworkToNotification: stale (capturedIndex=$currentIndex/$currentMediaId, current=$nowIndex/$nowMediaId) - dropped")
>                 }
>             }
>         } catch (e: Exception) { Timber.w(e, "pushArtworkToNotification: failed") }
>     }
> }
> ```
>
> The single-item rotation case is now correctly handled: in that mode `nowIndex == currentIndex` always holds (both are `0`), but `nowMediaId != currentMediaId` when the track has changed, so the artwork update is dropped. In playlist mode both checks tighten the guard.

**Verification:**

- `Grep` - `val currentMediaId = currentItem\.mediaId` returns 1 match inside `AudioCoverArtLoader.kt`.
- `Grep` - `nowIndex == currentIndex && nowMediaId == currentMediaId` returns 1 match.
- `Grep` - `pushArtworkToNotification: stale` returns 1 match (the dropped-update log).
- `Grep` - `if \(player\.currentMediaItemIndex == currentIndex\) \{` (the index-only guard) returns 0 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: AudioCoverArtLoader.kt. Compound guard `(index, mediaId)` now distinguishes playlist mode (index changes) from single-item rotation (mediaId changes).

---

### Step 04.2 - Verify project builds

**Files:** none (build only)
**Depends on:** Step 04.1

**Prompt for developer:**

> Trigger a debug build of the `standard` flavor via `/build`. No new imports are needed (`MediaItem.mediaId` is a property of an already-imported class).

**Verification:**

- Build exits with code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Build SUCCESSFUL (assembleStandardDebug, 48s, exit 0). APK v2.60.5201.210.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for `AudioCoverArtLoader.kt`.

---

## Handoff Notes to Next Phase

- Media-session artwork now respects track identity in both playlist and single-item rotation modes.
- All four strategic pillars (A, B, C, D, E) are implemented end-to-end.
- Phase 05 finalises catalog sync, changelog roll-up, and BlockNeedUserTest probe insertion.

---

## Rollback Plan

Revert the phase commit. The index-only guard returns; behaviour falls back to playlist-correct, single-item-incorrect.
