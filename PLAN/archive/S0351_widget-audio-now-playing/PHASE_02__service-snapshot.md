# Phase 02 - Service Snapshot

**Strategic spec:** [`../S0351_widget-audio-now-playing.md`](../S0351_widget-audio-now-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Make `AudioPlaybackService` publish a lightweight now-playing snapshot and accept widget playback commands.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/AudioNowPlayingSnapshotStore.kt` | New | <= 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | <= 650 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt` | Modified | <= 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt` | Modified | <= 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt` | Modified | <= 220 |

---

## Steps

### Step 02.1 - Add snapshot store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/AudioNowPlayingSnapshotStore.kt`

**Prompt for developer:**

> Add a small store that persists widget-safe now-playing state in `SharedPreferences`, renders defaults for inactive state, and updates all `AudioNowPlayingWidgetProvider` instances through `AppWidgetManager`.

**Verification:**

- `Glob` - snapshot store file exists.
- `Grep` - store has `read`, `write`, `clear`, and `updateWidgets` functions.
- `Grep` - no provider-side network or bitmap decoding APIs are referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Snapshot store has `read`, `write`, `clear`, and `updateWidgets`; no provider-side network or bitmap decoding APIs.

### Step 02.2 - Publish service snapshot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`

**Prompt for developer:**

> Update the snapshot from service-owned Media3 player events: playback state, metadata changes, item transitions, and service destroy. Clear active state when playback is ended/stopped/destroyed.

**Verification:**

- `Grep` - `AudioNowPlayingSnapshotStore.write` appears in `AudioPlaybackService.kt`.
- `Grep` - `AudioNowPlayingSnapshotStore.clear` appears in `AudioPlaybackService.kt`.
- `Grep` - `onMediaMetadataChanged` or `onMediaItemTransition` is handled.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. `AudioPlaybackService` writes/clears widget snapshots and handles metadata/item transition events; playback metadata carries widget identity extras.

### Step 02.3 - Accept widget commands

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`

**Prompt for developer:**

> Add service intent commands for widget previous, play/pause, next, and favorite-safe refresh. Route playback commands through existing `dispatchCommand` values.

**Verification:**

- `Grep` - `ACTION_WIDGET_COMMAND` exists.
- `Grep` - command values map to `playback.pause_play`, `navigation.next_file`, and `navigation.previous_file`.
- `Grep` - no persistent log line contains `S0351`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Service widget commands map to existing playback dispatch ids; no `S0351` persistent log text.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt app_v2/src/main/java/com/sza/fastmediasorter/widget/AudioNowPlayingSnapshotStore.kt` returns zero hits.

---

## Rollback Plan

Remove the snapshot store and service snapshot/command additions.
