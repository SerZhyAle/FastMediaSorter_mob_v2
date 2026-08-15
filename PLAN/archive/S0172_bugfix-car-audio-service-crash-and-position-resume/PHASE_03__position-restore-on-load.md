# PHASE 03 — Position Restore on SFTP Audio Load

**Ticket:** S0172  
**Phase:** 03 of 04  
**Pillar:** C — SFTP audio position restore

---

## Goal

When the user opens an SFTP (or SMB/FTP) audio file that was previously listened to, seek to the saved position immediately after `prepare()` completes, and show a "Resumed from X:XX" toast — matching the UX of local video (`VideoPlayerManager`, line 727).

---

## Context

`PlayerMediaLoaderManager.playAudioViaService()` (network branch) downloads the file to cache, then calls:

```kotlin
controller.playAudioWithMetadata(uri, netTitle) { player ->
    activity.runOnUiThread { bindServicePlayerToView(player) }
}
```

Inside `AudioServiceController.playAudioWithMetadata()`:

```kotlin
player.setMediaItem(mediaItem)
player.repeatMode = Player.REPEAT_MODE_OFF
player.prepare()
player.play()
onPlayerReady(player)
```

`player` here is a `MediaController` (not `ExoPlayer`). Seeking after `prepare()` via `MediaController` is valid — the controller forwards to the service player.

The position key is the **original SFTP path** (the `path` parameter in `playAudioViaService()`), not the cache URI. Phase 02 established this key as `AudioPlaybackService.currentOriginalPath`.

Existing restore toast string: `R.string.playback_resumed_from` — verify it exists and its format matches `VideoPlayerManager`'s usage pattern.

---

## Steps

### Step 3.1 — Verify `R.string.playback_resumed_from` exists

- [ ] Run: `grep -n "playback_resumed_from" app_v2/src/main/res/values/strings.xml`
- [ ] Check the format arg type (expected: `%s` or `%1$s` with a pre-formatted time string).
- [ ] **Verification:** key exists in EN strings. If absent, add in Step 3.1a.

#### Step 3.1a (conditional) — Add string if missing

If the key is absent, add it to all three locales using the set-android-string script:

```powershell
pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "playback_resumed_from" -Value "Resumed from %s" -CreateIfMissing
pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale ru -Key "playback_resumed_from" -Value "Продолжено с %s" -CreateIfMissing
pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale uk -Key "playback_resumed_from" -Value "Продовжено з %s" -CreateIfMissing
```

### Step 3.2 — Inject `PlaybackPositionRepository` into `PlayerMediaLoaderManager`

`PlayerMediaLoaderManager` currently has no access to `PlaybackPositionRepository`. The constructor is called from `PlayerActivity`.

- [ ] Add a nullable constructor parameter (to avoid breaking existing call sites):

```kotlin
private val playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository? = null,
```

Add it at the end of the constructor parameter list, before the closing `)`.

- [ ] Open `PlayerActivity.kt`.
- [ ] Find the instantiation of `PlayerMediaLoaderManager(...)`.
- [ ] Pass `playbackPositionRepository = playbackPositionRepository` (the Activity's `@Inject` field).
- [ ] **Verification:** `grep -n "playbackPositionRepository" PlayerMediaLoaderManager.kt` — constructor parameter present; `grep -n "PlayerMediaLoaderManager(" PlayerActivity.kt` — new arg is wired.

### Step 3.3 — Read saved position before playback

In `PlayerMediaLoaderManager`, in the SFTP/SMB/FTP branch of `playAudioViaService()`, replace:

```kotlin
val cachedFile = preCacheNetworkAudio(path, currentFileSize, resolvedCredentialsId)
if (cachedFile != null) {
    val uri = Uri.fromFile(cachedFile)
    val netTitle = viewModel.state.value.currentFile?.name?.substringBeforeLast('.') ?: cachedFile.nameWithoutExtension
    controller.playAudioWithMetadata(uri, netTitle) { player ->
        activity.runOnUiThread { bindServicePlayerToView(player) }
    }
```

with:

```kotlin
val cachedFile = preCacheNetworkAudio(path, currentFileSize, resolvedCredentialsId)
if (cachedFile != null) {
    val uri = Uri.fromFile(cachedFile)
    val netTitle = viewModel.state.value.currentFile?.name?.substringBeforeLast('.') ?: cachedFile.nameWithoutExtension

    // S0172: read saved position before handing off to the controller so we can
    // seek after prepare(). Key = original network path (not cache path) per ADR-2.
    val savedPositionMs = withContext(kotlinx.coroutines.Dispatchers.IO) {
        playbackPositionRepository?.getPosition(path)
    } ?: 0L

    controller.playAudioWithMetadata(uri, netTitle) { player ->
        // S0172: seek to saved position after prepare(); show resume toast if > 0
        if (savedPositionMs > 0L) {
            player.seekTo(savedPositionMs)
            activity.runOnUiThread {
                val timeStr = formatTime(savedPositionMs)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.playback_resumed_from, timeStr),
                    Toast.LENGTH_SHORT
                ).show()
            }
            Timber.d("S0172: SFTP audio resume seekTo $savedPositionMs ms for $path")
        }
        activity.runOnUiThread { bindServicePlayerToView(player) }
    }
```

- [ ] The `withContext` call is already inside a `lifecycleScope.launch {}` block — confirm the enclosing lambda is a suspend context. If `playAudioViaService` uses a regular lambda, wrap the repository call in `runBlocking(Dispatchers.IO) { ... }` as a fallback (less preferred).
- [ ] Add import `android.widget.Toast` if not already present.
- [ ] Add import `kotlinx.coroutines.withContext` if not present.
- [ ] **Verification:** `grep -n "savedPositionMs\|getPosition\|seekTo" PlayerMediaLoaderManager.kt` — all three appear in the SFTP branch.

### Step 3.4 — Add `formatTime` helper if not accessible

`formatTime(ms: Long): String` is used in `VideoPlayerManager`. Check if it's accessible from `PlayerMediaLoaderManager`.

- [ ] Run: `grep -rn "fun formatTime" app_v2/src/main/java/com/sza/fastmediasorter/`
- [ ] If it's `internal` inside `VideoPlayerManager` or its helpers, either:
  - Extract it to `utils/TimeFormatUtils.kt` (or equivalent) and make it `internal` / `fun formatTime(...)`.
  - Or inline the format logic directly:
    ```kotlin
    val seconds = (savedPositionMs / 1000) % 60
    val minutes = (savedPositionMs / 60_000) % 60
    val hours = savedPositionMs / 3_600_000
    val timeStr = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
                  else "%d:%02d".format(minutes, seconds)
    ```
- [ ] **Verification:** code compiles; `timeStr` is produced correctly.

### Step 3.5 — Add `Timber.d` debug tag

- [ ] The tag `Timber.d("S0172: SFTP audio resume seekTo ...")` was included in Step 3.3. Confirm it is present.
- [ ] **Verification:** `grep -rn "S0172:" app_v2/src/main/java/` shows entries in both `AudioPlaybackService.kt` and `PlayerMediaLoaderManager.kt`.

### Step 3.6 — Manual test

- [ ] Build and install: `.\scripts\builders\build-standard-device.ps1`
- [ ] Play an SFTP audio file for > 30 s, then exit the app.
- [ ] Reopen the same file.
- [ ] **Verification:**
  - App skips to ~the position it was at (± 15 s due to save interval).
  - Toast "Resumed from X:XX" appears.
  - `.\scripts\utils\search-log.ps1 -Pattern "S0172" -Tag "PlayerMediaLoaderManager"` shows `seekTo` log entry.

### Step 3.7 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt" "PlayerMediaLoaderManager" "S0172 Phase 03: read saved position and seekTo after prepare for SFTP audio"
```

---

## Verification summary

| Check | Command / signal |
|-------|-----------------|
| `playback_resumed_from` string key exists | `grep -n "playback_resumed_from" app_v2/src/main/res/values/strings.xml` |
| `playbackPositionRepository` in `PlayerMediaLoaderManager` constructor | `grep -n "playbackPositionRepository" PlayerMediaLoaderManager.kt` |
| `getPosition(path)` called before `playAudioWithMetadata` | `grep -n "getPosition" PlayerMediaLoaderManager.kt` |
| `seekTo` called in the controller callback | `grep -n "seekTo" PlayerMediaLoaderManager.kt` |
| Toast fires on resume | manual device test — toast visible |
| `Timber.d("S0172:...")` log entry in logcat | `search-log.ps1 -Pattern "S0172"` |
