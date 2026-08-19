# S0902 - Wear players: loop-on-end, no onStop teardown, unbounded temp cache, dead album art (P2 cluster)

**Ticket:** S0902
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-05 -->
<!-- first activated increment of umbrella S0552 (resume-wear-development), owner decision 2026-07-05 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Модуль wear (catalog_sync -Module wear).

- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt:48 - Dead album-art path: albumArtRepository and preferencesRepository injected but never used; MediaMetadataRetriever import unused; albumArtUrl UI branch can never render
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt:94 - Contract item 5 (playWhenReady reset): audio STATE_ENDED seeks to 0 without pausing - track restarts and loops indefinitely
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt:156 - First-run video never auto-starts: dismissBatteryWarning does not set playWhenReady although the load path defers autoplay to dismissal
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt:207 - SMB playback temp files are re-downloaded every open and never deleted - cache dirs grow without app-side bound on watch storage
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt:333 - Contract item 2: no onStop handling - video/audio playback keeps running while the host activity is stopped; onCleared is the only teardown edge

**Re-verified against current code 2026-07-05 (/spec-all resume):** all 5 findings still accurate. `VideoPlayerViewModel`'s own `STATE_ENDED` branch already does `pause()` + `seekTo(0)` correctly - `AudioPlayerViewModel`'s branch is the one missing the `pause()`, confirming the fix pattern already exists in-repo.

## 1. Goal (RU)

Пять независимых дефектов в Wear-плеерах (аудио и видео): бесконечный луп аудио по достижении конца трека, видео не запускается после закрытия battery-предупреждения при первом запуске, SMB-временные файлы растут без ограничения кэша, нет паузы при остановке хост-активности, и мёртвый неиспользуемый код album-art в аудио-плеере.

## 2. Constraints

- No schema/DI-scope changes; новый `SmbCacheEvictor` - stateless-объект, не Hilt-биндинг.
- Happy-path поведение не меняется, кроме пяти именованных дефектов; пауза по onStop не авто-возобновляет воспроизведение при возврате - пользователь нажимает play заново.
- Удаление `albumArtRepository`/`preferencesRepository` из `AudioPlayerViewModel` - только из конструктора этого класса; оба репозитория остаются забинжены и используются другими Wear ViewModel (SettingsViewModel, BrowseViewModel, ImageViewerViewModel) - не трогаем.
- Вытеснение кэша никогда не удаляет только что записанный temp-файл; вытеснение oldest-first до фиксированного лимита. Раздельные лимиты для аудио (100 MB) и видео (300 MB), отражающие разницу в типичном размере файлов.

## 3. Phases

### Phase 1 - `AudioPlayerViewModel` STATE_ENDED loop (finding line 94, Contract item 5)

- Step 1.1: in `playerListener.onPlaybackStateChanged`, `STATE_ENDED` branch - call `exoPlayer.pause()` before `exoPlayer.seekTo(0)`, mirroring `VideoPlayerViewModel`'s existing correct pattern, so `playWhenReady` is cleared and playback does not auto-restart from position 0.
  - Verification: grep - `STATE_ENDED` branch in `AudioPlayerViewModel.kt` calls `exoPlayer.pause()` before `seekTo(0)`.

### Phase 2 - `AudioPlayerViewModel` dead album-art cleanup (finding line 48)

- Step 2.1: remove the unused `albumArtRepository: AlbumArtRepository` and `preferencesRepository: WearPreferencesRepository` constructor params and the unused `import android.media.MediaMetadataRetriever`. Neither field is referenced anywhere else in the class.
  - Verification: grep - no `albumArtRepository`, `preferencesRepository`, or `MediaMetadataRetriever` token remains in `AudioPlayerViewModel.kt`; `AlbumArtRepository`/`WearPreferencesRepository` still referenced elsewhere in `wear/` (SettingsViewModel, BrowseViewModel, ImageViewerViewModel, WearAppModule) - untouched.

### Phase 3 - `VideoPlayerViewModel` first-run autoplay after battery-warning dismissal (finding line 156)

- Step 3.1: in `dismissBatteryWarning()`, call `exoPlayer.play()` after clearing `showBatteryWarning` in ui state, so a video whose load path deferred autoplay (because the warning was still showing at load time) starts once the user dismisses it.
  - Verification: grep - `dismissBatteryWarning()` calls `exoPlayer.play()`.

### Phase 4 - Bounded SMB temp-file cache, both players (finding line 207)

- Step 4.1: add `wear/src/main/java/com/sza/fastmediasorter/wear/util/SmbCacheEvictor.kt` - a plain object with `fun evictOldestUntilUnderCap(cacheDir: File, keep: File, capBytes: Long)` that sums the dir's file sizes and, if over `capBytes`, deletes oldest-by-`lastModified` files first (skipping `keep`) until under the cap. Uses a loop with early `break` (<=2 returns for detekt `ReturnCount`).
- Step 4.2: `AudioPlayerViewModel.loadSmbAudio` - after writing `tempFile` (already inside `withContext(Dispatchers.IO)`), call `SmbCacheEvictor.evictOldestUntilUnderCap(cacheDir, keep = tempFile, capBytes = SMB_AUDIO_CACHE_CAP_BYTES)` (new companion const, `100L * 1024 * 1024`).
- Step 4.3: `VideoPlayerViewModel.loadSmbFile` - same call with `SMB_VIDEO_CACHE_CAP_BYTES` (new companion const, `300L * 1024 * 1024`).
  - Verification: grep - `SmbCacheEvictor.evictOldestUntilUnderCap` called in both `loadSmbAudio` and `loadSmbFile`, after the temp-file write, passing the just-written file as `keep`.

### Phase 5 - onStop teardown, both players (finding line 333, Contract item 2)

- Step 5.1: `AudioPlayerViewModel` - add `fun onHostStopped() { exoPlayer.pause() }`.
- Step 5.2: `VideoPlayerViewModel` - add `fun onHostStopped() { exoPlayer.pause() }`.
- Step 5.3: `AudioPlayerScreen` - wire `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHostStopped() }` (imports: `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.compose.LifecycleEventEffect` - already on the `lifecycle-runtime-compose:2.7.0` dependency). Registration/removal is symmetric by construction (tied to the composable's own lifecycle).
- Step 5.4: `VideoPlayerScreen` - same wiring.
  - Verification: grep - both screens call `LifecycleEventEffect(Lifecycle.Event.ON_STOP)`; both ViewModels expose `onHostStopped()` calling `exoPlayer.pause()`.

### Phase 6 - Build gate

- Step 6.1: `standard debug` Kotlin compile (`a.ps1 fk`). Detekt-clean on the touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings on the touched files.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0552 (resume-wear-development - umbrella; this is its first activated increment, owner decision 2026-07-05), S0878 (audit tail container - triage source), S0896 (WearAppModule:68 audio-focus).

## Related

- S0878 (audit tail container - triage source); S0896 (WearAppModule:68 audio-focus живёт там); S0552 (resume-wear-development, umbrella - first activated increment).
