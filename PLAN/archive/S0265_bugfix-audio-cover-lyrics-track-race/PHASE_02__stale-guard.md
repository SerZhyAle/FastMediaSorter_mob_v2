# Phase 02 - Stale-Result Guard at Application Points

**Strategic spec:** [`../S0265_bugfix-audio-cover-lyrics-track-race.md`](../S0265_bugfix-audio-cover-lyrics-track-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Insert identity-based guards at every point where an async result (audio metadata, cover bitmap) is applied to UI state or persisted to cache. A guard compares the originating `MediaFile.path` carried by the result against the path of the track currently playing. Mismatch → silent drop, no UI change, no cache write, no error-level log. This implements strategic §5.1 pillars B (silent drop) and E (cache cleanliness).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done. Callback signature carries `originatingPath` end-to-end.
- [ ] Build is green on `standard` flavor.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Modified | ≤ 1285 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1725 (pre-existing >1500 LOC tech debt - minimal touch, +5 LOC) |

> No new files. The current-path supplier reuses the existing `callback.getCurrentFile()` already declared on `ImageLoadingManager.ImageLoadingCallback`.

---

## Steps

### Step 02.1 - Add current-path supplier to `AudioCoverArtLoader` constructor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a parameter `currentFilePathProvider: () -> String?` to the `AudioCoverArtLoader` primary constructor (right before `callback: Callback`). Update the construction site inside `ImageLoadingManager.kt` (around line 156-170) to pass `{ callback.getCurrentFile()?.path }` as the new argument. Do not add a getter for the supplier - it is a private property of `AudioCoverArtLoader`.

**Verification:**

- `Grep` - `private val currentFilePathProvider: \(\) -> String\?` returns 1 match inside `AudioCoverArtLoader.kt`.
- `Grep` - `currentFilePathProvider = \{ callback\.getCurrentFile\(\)\?\.path \}` returns 1 match inside `ImageLoadingManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: AudioCoverArtLoader.kt (constructor +1 param), ImageLoadingManager.kt (construction site +1 arg).

---

### Step 02.2 - Guard Glide `onResourceReady` against stale results

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Both Glide `RequestListener.onResourceReady` callbacks (around lines 289-293 and 392-397) currently set `coverArtDisplayedForPath = file.path` and push the bitmap to the system notification unconditionally. Add an identity check at the very start of each `onResourceReady` body:
>
> ```kotlin
> override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
>     if (currentFilePathProvider() != file.path) {
>         Timber.d("AudioCoverArtLoader: stale cover bitmap for ${file.name} (current=${currentFilePathProvider()}) - dropped")
>         return false
>     }
>     coverArtDisplayedForPath = file.path
>     (resource as? BitmapDrawable)?.bitmap?.let { pushArtworkToNotification(it) }
>     return false
> }
> ```
>
> Apply the same change to **both** listeners (online-search branch and cache-revalidation branch). The drop is `return false` so Glide still places the loaded drawable into the target, but the side-effects (`coverArtDisplayedForPath` write and notification push) are skipped. Phase 03 covers the case where the bitmap itself must not appear in the ImageView.

**Verification:**

- `Grep` - `currentFilePathProvider\(\) != file\.path` returns at least 2 matches inside `AudioCoverArtLoader.kt`.
- `Grep` - `stale cover bitmap` returns at least 2 matches inside `AudioCoverArtLoader.kt`.
- `Grep` - `coverArtDisplayedForPath = file\.path` returns 8 matches inside `AudioCoverArtLoader.kt` (unchanged set: 2 inside guard-protected `onResourceReady` blocks + 6 in cache-hit / embedded / cache-revalidation branches that run synchronously on Main thread; counted pre-edit and post-edit, must be equal).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: AudioCoverArtLoader.kt (2 guards added). Predicate-3 count corrected from planned 4 to actual 8 (planner under-counted; pre-edit count was also 8, so the literal predicate "count unchanged" is honoured).

---

### Step 02.3 - Guard `PlayerActivity.onAudioMetadataLoaded` and silently drop stale results

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the Phase 01 stub of `onAudioMetadataLoaded` with the guarded form:
>
> ```kotlin
> /** Called by ImageLoadingManager when audio metadata is loaded from the online source. */
> fun onAudioMetadataLoaded(
>     metadata: com.sza.fastmediasorter.domain.model.AudioMetadata,
>     originatingPath: String
> ) {
>     val current = viewModel.state.value.currentFile
>     if (current == null || current.path != originatingPath) {
>         Timber.d("PlayerActivity: stale audio metadata (originating=$originatingPath, current=${current?.path}) - dropped")
>         return
>     }
>     audioMetadataManager.onMetadataLoaded(metadata, current)
> }
> ```
>
> The cache write inside `PlayerAudioMetadataManager.onMetadataLoaded` now only happens for matching paths, which closes the lyrics-poisoning leak (strategic §5.1 pillar E). Do not change `PlayerAudioMetadataManager.kt` itself - the fix is at the call site.

**Verification:**

- `Grep` - `current\.path != originatingPath` returns 1 match inside `PlayerActivity.kt`.
- `Grep` - `stale audio metadata` returns 1 match inside `PlayerActivity.kt`.
- `Grep` - `audioMetadataManager\.onMetadataLoaded\(metadata, current\)` returns 1 match inside `PlayerActivity.kt`.
- `Grep` - `audioMetadataManager\.onMetadataLoaded\(metadata, viewModel\.state\.value\.currentFile\)` returns 0 matches (previous unguarded form is removed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: PlayerActivity.kt. Stale metadata now silently dropped at Timber.d level; cache write only on identity match.

---

### Step 02.4 - Verify lyrics path no longer reads poisoned cache

**Files:** none (verification only)
**Depends on:** Step 02.3

**Prompt for developer:**

> No code change. Re-read `PlayerActivity.searchAndShowLyrics()` (around line 687-691) to confirm that the cache read `audioMetadataManager.getCachedMetadataFor(currentFile?.path)` now relies on a cache that can only contain entries for paths whose identity matched at write time (guaranteed by Step 02.3). If `currentFile.path` has no cache entry (because the corresponding metadata search was dropped as stale), `getCachedMetadataFor` returns `null` and `lyricsManager.searchAndShowLyrics` falls back to `currentFile.artist` / `currentFile.title` from the actual audio file. This is the desired behaviour - no change needed.

**Verification:**

- `Grep` - `audioMetadataManager\.getCachedMetadataFor\(currentFile\?\.path\)` returns 1 match inside `PlayerActivity.kt` (unchanged).
- `Grep` - `lyricsManager\.searchAndShowLyrics\(currentFile, metadata\?\.trackName, metadata\?\.artistName\)` returns 1 match inside `PlayerActivity.kt` (unchanged).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Lyrics call site unchanged; relies on now-protected cache (Step 02.3 guard). `getCachedMetadataFor` returning null falls back to `currentFile.artist`/`currentFile.title` from the actual file ID3 tags.

---

### Step 02.5 - Verify project builds and run unit tests for player package

**Files:** none (build only)
**Depends on:** Step 02.4

**Prompt for developer:**

> Trigger a debug build of the `standard` flavor via `/build`. After the build is green, also run `assembleStandardDebug` to catch any kapt cache staleness from the construction-site change. Do not run the full `testStandardDebugUnitTest` - per project memory, that target carries pre-existing failures. Spot-check any `*Test.kt` files directly under `app_v2/src/test/java/.../player/` via per-class `--tests` filtering, but only if such tests exist.

**Verification:**

- Build exits with code 0.
- `Grep` - `Timber\.d\("S0265:` returns 0 hits across `app_v2/src/main/java/` (BlockNeedUserTest probes belong to the final transition).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Build SUCCESSFUL (assembleStandardDebug, 36s, exit 0). APK v2.60.5201.207.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Application-point guards are in place: late metadata cannot reach `PlayerAudioMetadataManager`; late bitmaps cannot rewrite `coverArtDisplayedForPath` or push notification artwork.
- The ImageView can still **briefly show** the late bitmap before the next-track load reaches `Glide.into()` - Phase 03 closes that visual leak by clearing Glide on every new `loadAudioCoverArt`.
- Media-session notification guard hardening is Phase 04 (orthogonal: identity-by-index → identity-by-mediaId).

---

## Rollback Plan

Revert the phase commit. The Phase 01 contract remains intact and the guards simply disappear - behaviour falls back to the pre-fix state. No data migration, no public API removal.
