# Task 2: Suppress Countdown Timer and Ignore Interval for Audio/Video Resources

## Objective
Для ресурсов типа Audio Library и Video Library: скрыть визуальный обратный отсчёт `3..2..1..` и отменить ожидание slideshow-интервала — переход к следующему файлу происходит только после естественного завершения воспроизведения.

---

## Context & Current Behavior

`SlideshowController.kt` управляет таймером и обратным отсчётом. При активном Slideshow он:
1. Запускает `countdownRunnable` за 3 секунды до конца интервала, обновляя `tvCountdown` (TextView, расположен в `player_tv_countdown_content.xml`, id `@+id/tvCountdown`).
2. По истечении интервала (`slideShowInterval`, дефолт `DEFAULT_SLIDESHOW_INTERVAL_MS = 3000L`) вызывает переход к следующему файлу.

Для аудио и видео это поведение должно быть полностью заменено: переход происходит по окончанию воспроизведения файла, а не по таймеру.

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt` | Таймер, countdown, переход к следующему файлу |
| `app_v2/src/main/res/layout/player_tv_countdown_content.xml` | View `@+id/tvCountdown` — скрывать для данных ресурсов |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | `isSlideShowActive`, `resourceProfile` |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Callback на завершение воспроизведения (`onPlaybackEnded`) |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/constants/AppConstants.kt` | `DEFAULT_SLIDESHOW_INTERVAL_MS`, `SLIDESHOW_COUNTDOWN_TICK_MS` |

---

## Requirements

### R1 — Скрыть `tvCountdown` для AUDIO_LIBRARY / VIDEO_LIBRARY

- **Condition:** `resourceProfile == AUDIO_LIBRARY || resourceProfile == VIDEO_LIBRARY`
- **Action:** `tvCountdown.visibility = View.GONE` и **не вызывать** `startCountdown()` / `countdownRunnable` ни при каких обстоятельствах.
- `tvCountdown` должен быть скрыт сразу после инициализации Player для таких ресурсов и не появляться в течение всей сессии.

### R2 — Игнорировать slideshow-интервал

- Для `AUDIO_LIBRARY` / `VIDEO_LIBRARY`: **не запускать** interval-таймер в `SlideshowController`. Поле `slideShowInterval` не используется.
- Переход к следующему файлу должен произойти **только** в callback `onPlaybackEnded` (когда ExoPlayer сообщает `STATE_ENDED` или `onMediaItemTransition`).

### R3 — Непрерывное воспроизведение

- После окончания текущего аудио/видео файла (natural completion) — вызвать `viewModel.nextFile()` (или аналогичный метод перехода, используемый slideshow).
- Эта логика срабатывает **только** если `isSlideShowActive == true`. Если slideshow выключен пользователем — воспроизведение останавливается на текущем файле как обычно.

---

## Implementation Plan

1. **`SlideshowController`**: Добавить параметр или флаг `isMediaLibraryMode: Boolean`.
   - Если `true` → не запускать `countdownRunnable` и interval-таймер.
   - `startCountdown()` / `scheduleNextFile()` должны быть no-op при `isMediaLibraryMode = true`.

2. **`VideoPlayerManager` / `AudioPlaybackService`**: В callback завершения воспроизведения (`Player.Listener.onPlaybackStateChanged(STATE_ENDED)`) при `isSlideShowActive && isMediaLibraryMode` → вызвать `slideshowController.triggerNextFile()` или напрямую `viewModel.nextFile()`.

3. **`tvCountdown` visibility**: В `PlayerActivity` / `PlayerDialogAndUiStateManager` добавить проверку: при инициализации для `AUDIO_LIBRARY`/`VIDEO_LIBRARY` устанавливать `tvCountdown.visibility = View.GONE` однократно.

4. **Unit test:** Verify `SlideshowController` не запускает countdown при `isMediaLibraryMode = true`. Verify callback `onPlaybackEnded` → `nextFile()` при slideshow active + media library mode.

---

## Edge Cases & Risks

- **ExoPlayer playlist mode:** если Player использует встроенный ExoPlayer `repeat_mode` или `MediaController` для перехода между файлами — убедиться, что callback `onPlaybackEnded` не дублирует вызов `nextFile()`.
- **Пользователь включил Slideshow вручную** на обычном ресурсе (не AUDIO/VIDEO_LIBRARY): стандартная логика countdown+interval должна работать без изменений.
- **Переключение ресурса в рамках одной сессии Player:** если Player поддерживает переключение resource profile «на лету», `isMediaLibraryMode` должен пересчитываться корректно.
- **Короткие файлы (< 1 сек):** убедиться, что `STATE_ENDED` отрабатывается корректно и не вызывает бесконечную петлю.

