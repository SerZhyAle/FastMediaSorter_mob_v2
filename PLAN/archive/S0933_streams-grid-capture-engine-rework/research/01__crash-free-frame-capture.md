# Research 01 - Crash-free single-frame capture for stream grid thumbnails

**Ticket:** S0933
**Дата:** 2026-07-04

## Вопрос

Как снять один кадр из muted ExoPlayer (live HLS) в `Bitmap` без нативного process kill, который даёт текущий offscreen-`ImageReader` путь на аппаратных декодерах (Samsung Exynos / API36).

## Почему падает текущий путь (S0700)

`ExoPlayer.setVideoSurface(ImageReader.surface)` с `ImageReader(RGBA_8888)`:

- Эмулятор (software codec): декодер отдаёт `YUV_420_888` (0x23) в reader, сконфигуренный `RGBA_8888` (0x1) -> `acquireLatestImage()` бросает `UnsupportedOperationException` (ловимо, S0700 catch).
- Samsung Exynos / API36 (HW codec): нативный kill в настройке декодера/BufferQueue ДО любого Java-колбэка (лог обрывается на enqueue, ни исключения, ни crash-файла). Java try/catch бесполезен.

Корень: формат буфера декодера нельзя навязать/угадать заранее, а HW-путь `MediaCodec -> ImageReader-Surface` для произвольного формата нестабилен на части SoC.

## Кандидаты

### A. TextureView + getBitmap() - ВЫБРАН

- `ExoPlayer.setVideoTextureView(tv)`; `TextureView` рендерит декодированный кадр в свою `SurfaceTexture` через GPU; `SurfaceTextureListener.onSurfaceTextureUpdated` фиксирует появление кадра; `tv.getBitmap(w, h)` копирует текущий кадр в `Bitmap` (community-стандарт: ExoPlayer issues #418/#2451/#8975).
- Device-доказательство: тест S21 (2026-06-27, S0700) на `TextureView`-пути НЕ давал нативного kill - только чёрный кадр из-за слишком раннего `release()`. То есть путь совместим с HW-декодером; недостающее - дождаться первого кадра перед снятием.
- Цена: `TextureView` рендерит **только будучи attached к окну** и hardware-accelerated. `getBitmap()` на detached view возвращает null/чёрное. Значит нужен window-attached offscreen-хост (невидимый контейнер в иерархии Activity), а не «pure offscreen» как `ImageReader`.

### B. MediaMetadataRetriever.getFrameAtTime - ОТКЛОНЁН

- Самодостаточен (свой extractor+decoder, без Surface -> без format-mismatch и без Surface-kill).
- Но для live HLS ненадёжен: «нет гарантии, что источник имеет кадр в заданной позиции» (Android docs); рассчитан на seekable-контент с длительностью. Для live m3u8 часто блокирует/возвращает null. Не подходит как основной путь.

### C. ImageReader с другим форматом - ОТКЛОНЁН

- Нельзя статически выбрать формат, совместимый со всеми декодерами (YUV vs RGBA). `ImageFormat.PRIVATE` не читается CPU напрямую. Тупик без GPU-конвертации.

## Решение

**TextureView + getBitmap()**, рендер в window-attached невидимый offscreen-хост, с ограниченным ожиданием первого кадра (`onRenderedFirstFrame` от `Player.Listener` ИЛИ первый `onSurfaceTextureUpdated`) перед `getBitmap()` и teardown.

## Открытые вопросы -> решения

- **Где хост-контейнер?** Прокинуть в `StreamFrameSnapshotManager` `ViewGroup`-провайдер от `StreamsActivity` (content root). Контейнер: `FrameLayout` размера `CAPTURE_WIDTH x CAPTURE_HEIGHT`, добавлен в root с `alpha=0` / за экраном (`translationX = -10000`), `importantForAccessibility=NO`. Один общий хост, TextureView добавляется/убирается на каждый захват (или переиспользуется под `MAX_CONCURRENT_CAPTURES=1`).
- **Нить:** ExoPlayer + View - главный поток; `getBitmap()` - главный поток (TextureView требует), затем компрессия/scale и `cache.put` - можно off-main. `getBitmap(w,h)` уже отдаёт low-res.
- **Первый кадр:** ждать `onRenderedFirstFrame` (надёжнее `onSurfaceTextureUpdated`); таймаут `CAPTURE_TIMEOUT_MS` сохраняется; по таймауту -> null -> favicon-заглушка.
- **Teardown-контракт (обяз.):** `setVideoTextureView(null)` -> `player.release()` -> снять `SurfaceTextureListener` -> убрать TextureView из хоста. На всех путях (успех/таймаут/отмена/ошибка), как в текущем `finally`.
- **Cancel/лимиты:** `pending`-дедуп, `MAX_CONCURRENT_CAPTURES`, S0900 cancel-семантика - переиспользуются без изменений (меняется только тело `capture()`).

## Влияние на дизайн

- Меняется **только** источник кадра внутри `StreamFrameSnapshotManager.capture(url): Bitmap?`; сигнатура и downstream (`StreamFrameCache`, `StreamFramePersistentStore`, grid-repaint, S0712/S0784) неизменны.
- Новая зависимость: window-attached `ViewGroup`-хост -> `StreamFrameSnapshotManager` получает `hostProvider: () -> ViewGroup?` (null -> захват пропускается, favicon). `StreamsActivity` отдаёт content root.
- `CAPTURE_ENABLED` снова `true` после реворка.

## Источники

- ExoPlayer issue #418 "Grab Current Frame" - https://github.com/google/ExoPlayer/issues/418
- ExoPlayer issue #2451 "Grab a frame of the video" - https://github.com/google/ExoPlayer/issues/2451
- ExoPlayer issue #8975 "Retrieving Current frame from Exoplayer" - https://github.com/google/ExoPlayer/issues/8975
- Android `MediaMetadataRetriever.getFrameAtTime` (frame-availability caveat) - developer.android.com
