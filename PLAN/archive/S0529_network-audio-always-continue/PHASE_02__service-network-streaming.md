# Phase 02 - Service-side network streaming

**Strategic spec:** [`../S0529_network-audio-always-continue.md`](../S0529_network-audio-always-continue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Give `AudioPlaybackService` a scheme-aware media-source factory so it streams SFTP/SMB/FTP/cloud audio directly (resolving credentials from the media item), and route network/cloud audio through the service instead of mandatory full pre-cache.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkAwareMediaSourceFactory.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 720 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 780 |

> `AudioPlaybackService.kt` and `PlayerMediaLoaderManager.kt` exceed 500 LOC - timestamped backups in `temp/` before editing.

---

## Steps

### Step 02.1 - Introduce a scheme-aware media-source factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkAwareMediaSourceFactory.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Add `NetworkAwareMediaSourceFactory` implementing Media3 `MediaSource.Factory`. It inspects each `MediaItem`'s URI scheme and delegates to the matching protocol `DataSource.Factory` (`SftpDataSourceFactory` / `SmbDataSourceFactory` / `FtpDataSourceFactory` / `CloudDataSourceFactory`) already used by the in-app player, falling back to the default factory for `file://`/`http(s)`. Resolve credentials per item: read a credentials id from the `MediaItem` metadata extras if present, otherwise resolve by server/host+share parsed from the URI, using an injected credentials repository (mirror the in-app resolution in `SftpPlaybackHelper`/`SmbPlaybackHelper`). Take the protocol clients and credentials repository as constructor dependencies (Hilt `@Inject constructor`). No new Hilt scope or qualifier - constructor injection of existing singletons only.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkAwareMediaSourceFactory.kt` exists.
- `Grep` - `class NetworkAwareMediaSourceFactory` matches once (declaration).
- `Grep` - `: MediaSource.Factory` present in that file.
- `Grep` - `@Inject` constructor present in that file.

**Status:** `[ ]` not done

---

### Step 02.2 - Wire the factory into the service ExoPlayer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `NetworkAwareMediaSourceFactory` into `AudioPlaybackService` (field injection - service is already `@AndroidEntryPoint`). Pass it to the `ExoPlayer.Builder` via `setMediaSourceFactory(..)` so the service player can resolve network/cloud URIs. Keep local/HTTP playback unchanged (the factory delegates to the default for those schemes).

**Verification:**

- `Grep` - `setMediaSourceFactory` present in `AudioPlaybackService.kt`.
- `Grep` - `NetworkAwareMediaSourceFactory` referenced in `AudioPlaybackService.kt`.
- `Grep -n "Log\.d\("` - zero hits in `AudioPlaybackService.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 - Carry credentials id to the service media item

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add an `EXTRA_CREDENTIALS_ID` key and let `playAudioWithMetadata` / `playAudioPlaylistWithMetadata` accept an optional credentials id, storing it in the `MediaItem` metadata extras so `NetworkAwareMediaSourceFactory` can resolve credentials inside the service process. Keep existing call sites compiling (default the new parameter to null).

**Verification:**

- `Grep` - `EXTRA_CREDENTIALS_ID` present in `AudioServiceController.kt`.
- `Grep` - the extras `Bundle` write for credentials id present.

**Status:** `[ ]` not done

---

### Step 02.4 - Route network/cloud audio through the streaming service path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> In `playAudioViaService`, for network (SMB/SFTP/FTP) and cloud audio, hand the original network/cloud URI plus its resolved credentials id directly to the service via the controller (streaming), instead of mandatory pre-cache-to-file then `file://`. Keep `AudioPlaybackService.currentOriginalPath` as the stable position key (the original network path, not a cache URI). Retain pre-cache only as a degradation path for unstable connections; on streaming start failure, keep the existing in-app fallback. Preserve readiness feedback and saved-position restore.

**Verification:**

- `Grep` - the streaming branch passes the original `sftp`/`smb`/`ftp`/`cloud` path (not only `Uri.fromFile`) to the controller in the network/cloud branches.
- `Grep` - `AudioPlaybackService.currentOriginalPath` still set to the original path.
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaLoaderManager.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class) - deferred to Phase 05 catalog sync.

---

## Handoff Notes to Next Phase

Network/cloud audio now plays through the service via streaming, so the continuability signal can key off "playing through service" regardless of local cache. Phase 03 consumes this to make exit honour ALWAYS_CONTINUE for streamed audio.

---

## Rollback Plan

Revert phase commit(s). The new factory is additive; reverting `setMediaSourceFactory` returns the service to local-only playback and the in-app fallback path resumes. No data migration.
