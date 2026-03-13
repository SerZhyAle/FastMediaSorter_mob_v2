# Task 3: Handle "Play audio/video in slideshow to end" Setting in Mixed Resources

## Objective
Для ресурсов со смешанным содержимым (mixed resources): если включена настройка "Play audio/video in slideshow to end", скрывать countdown-оверлей при воспроизведении аудио/видео и переходить к следующему файлу по окончании воспроизведения (не по таймеру, даже если файл короче интервала).

---

## Context & Current Behavior

Настройка **"Play audio/video in slideshow to end"** — `AppSettings.playToEndInSlideshow` (`DataStore key: KEY_PLAY_TO_END = "play_to_end_in_slideshow"`, файл `SettingsRepositoryImpl.kt`, строка ~94). Управляется через `switchPlayToEnd` в `PlaybackSettingsFragment.kt`.

Эта задача отличается от Task 2. Task 2 — про `AUDIO_LIBRARY`/`VIDEO_LIBRARY` (профиль ресурса однородный). Эта задача — про **смешанный ресурс** (например, `ResourceProfile.ALL_FILES` или `DOCUMENTS`), где среди файлов есть и изображения, и документы, и аудио/видео.

Сейчас в смешанном режиме countdown показывается даже когда аудио/видео ещё воспроизводится — что вводит пользователя в заблуждение.

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt` | Countdown + interval timer logic |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | `playToEndInSlideshow: Boolean` |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | `isSlideShowActive`, `currentFile` (MediaType), settings state |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Callback `onPlaybackEnded` / `STATE_ENDED` |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | `MediaType.AUDIO`, `MediaType.VIDEO` |

---

## Requirements

### R1 — Скрыть countdown при воспроизведении audio/video в mixed slideshow

**Условие активации:**
```
isSlideShowActive == true
AND playToEndInSlideshow == true
AND currentFile.type IN (MediaType.AUDIO, MediaType.VIDEO)
AND resourceProfile NOT IN (AUDIO_LIBRARY, VIDEO_LIBRARY)  // обрабатывается в Task 2
```

**Action:** `tvCountdown.visibility = View.GONE`. Countdown не запускать (пауза `countdownRunnable`).

### R2 — Игнорировать интервал если файл короче интервала

- При выполнении вышеуказанного условия:
  - Если `fileDuration < slideshowInterval`: перейти к следующему файлу **сразу** после `onPlaybackEnded`, не ждать остаток времени до `slideshowInterval`.
  - Если `fileDuration >= slideshowInterval`: режим `playToEndInSlideshow` требует дождаться полного окончания, переход по `onPlaybackEnded`.

### R3 — Когда `playToEndInSlideshow == false`

- Стандартное поведение Slideshow: countdown показывается, переход по `slideshowInterval` для всех типов файлов. Изменений не требуется.

---

## Implementation Plan

1. **`SlideshowController`**: Перед запуском countdown-цикла добавить проверку:
   ```kotlin
   fun shouldSuppressCountdown(
       currentMediaType: MediaType?,
       playToEnd: Boolean,
       isMediaLibrary: Boolean
   ): Boolean {
       if (isMediaLibrary) return true  // handled by Task 2
       return playToEnd && currentMediaType in listOf(MediaType.AUDIO, MediaType.VIDEO)
   }
   ```

2. **`VideoPlayerManager.onPlaybackEnded`**: При `playToEnd && currentfile.type in (AUDIO, VIDEO) && isSlideShowActive && !isMediaLibrary`:
   ```kotlin
   slideshowController.cancelIntervalTimer()
   viewModel.nextFile()
   ```

3. **UI Update в `PlayerDialogAndUiStateManager`**: при смене текущего `MediaType` во время Slideshow — пересчитывать `tvCountdown` visibility.

4. **Unit tests:**
   - `playToEnd=true, type=VIDEO, short file`: сразу после `STATE_ENDED` вызывается `nextFile()`.
   - `playToEnd=true, type=IMAGE`: countdown показывается, interval работает.
   - `playToEnd=false, type=VIDEO`: countdown показывается, interval работает.

---

## Edge Cases & Risks

- **Смена файла во время Slideshow (next/prev вручную):** Slideshow-таймер должен сбрасываться и начинать свежей логикой для нового файла.
- **Аудио файл, застрявший за interval:** не допускать ситуацию, где одновременно ожидаются два триггера: окончание файла + таймер интервала. Обязательная отмена interval-таймера после `onPlaybackEnded`.
- **Типы файлов не AUDIO/VIDEO (документы, PDF):** логика не трогается, поведение остаётся стандартным.

