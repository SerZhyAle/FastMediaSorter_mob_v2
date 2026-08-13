# Спецификация: S0933 - Crash-free движок захвата кадров сетки видеотрансляций

**Ticket:** S0933
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

**Текст:**

Что не работает (а работало) - в режиме сетки для видеотрансляций должны один за другим обновляться картинки-миниатюры у видимых сейчас на экране каналов. Помимо этого, эта «последняя» миниатюра должна закрепляться для этого канала для сетки. При повторном открытии сетки - её и показывать. Хранить в нашем удаляемом кэше в низком разрешении по одной (последней) на канал.

**Контекст (2026-07-04):** поведение регрессировало не из-за логики персиста, а из-за движка захвата. Реальный кадр-движок (S0700, offscreen ExoPlayer -> ImageReader) на части железа падает нативным process kill (Samsung Exynos, Android 16 / API36: kill в нативной настройке декодера/Surface, до любого Java-колбэка; Java try/catch его не ловит). Поэтому захват отключён (`CAPTURE_ENABLED=false`, S0700 2026-07-04), и сетка показывает только favicon-атлас.

---

## 1. Проблема

Движок захвата кадра видео-канала для сетки (S0700: muted offscreen ExoPlayer, рендер в `ImageReader`-Surface) несовместим с частью аппаратных декодеров: на Samsung Exynos / API36 он роняет процесс нативно на этапе настройки декодера/Surface, а на эмуляторе декодер отдаёт `YUV_420_888` (0x23) в сконфигуренный под `RGBA_8888` (0x1) reader и бросает `UnsupportedOperationException`. Kill нативный, до Java-колбэка, перехватом не лечится. Движок отключён kill-switch-ем, из-за чего готовое поведение S0712 (disk-персист последнего кадра) и S0784 (кадры по одному, «держать последний», восстановление при повторном открытии) не имеет источника кадров - сетка деградировала на favicon.

## 2. Цели

1. Захват кадра видео-канала работает без нативного краша на реальных устройствах, включая Samsung Exynos / Android 16 / API36.
2. Механизм получения кадра не зависит от offscreen `ImageReader`-Surface, несовместимого с частью HW-декодеров.
3. После реворка `CAPTURE_ENABLED` снова `true`, и восстанавливается уже реализованное поведение: у видимых видео-каналов кадры-миниатюры появляются по одному, последний кадр закрепляется за каналом, персистится low-res на диск и показывается сразу при повторном открытии сетки.

**Non-goals:**

- Логика персиста, анти-мигания и восстановления - уже реализованы в S0712 (disk-persist + prewarm) и S0784 (держать последний кадр, подмена «на месте», restore on reopen). Эта спека их НЕ переписывает - только заменяет механизм получения кадра, который их питает.
- AUDIO / RTSP-каналы - вне области, только http(s) VIDEO (как в S0784).
- Favicon-fallback (S0785) остаётся заглушкой, пока у канала нет кадра.
- Формат кэша миниатюр (low-res JPEG, один последний на канал, удаляемый кэш) уже задан S0712 - не меняется.

## 3. Ограничения

- **Flavor:** streams (standard + noLegal); правки в `src/main`.
- **Совместимость декодеров:** механизм обязан пережить и software-декодер (эмулятор, YUV_420_888), и аппаратные (Exynos и др.) без нативного process kill. Это жёсткий критерий, а не оптимизация.
- **Производительность:** один кадр на канал, низкое разрешение; захваты дедуплицируются (`pending`) и ограничены `MAX_CONCURRENT_CAPTURES`; захват прерывается при выходе из сетки / скролле (cancel-семантика S0900).
- **Release-контракт:** строгий teardown (CODE_AUDIT / S0700): detach surface до `release()`, снять все listeners, освободить декодер - чтобы перезахваты не текли декодерами.
- **Локализация:** без новых user-visible строк (внутренний механизм); при появлении - EN/RU/UK.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0700 (движок захвата + kill-switch `CAPTURE_ENABLED`; это его re-enable gate), S0712 (disk-persist последнего кадра + prewarm), S0784 (кадры по одному, держать последний, restore on reopen), S0785 (favicon-fallback как заглушка), S0900 (cancel-семантика захвата).
- **UI:** grid-миниатюра видео-канала; кадр подменяет favicon-заглушку «на месте», без мигания (владелец поведения - S0784). Новых экранов/строк нет.
- **Flavor:** streams (standard + noLegal), правки в `src/main`; инертно без streams-UI.
- **Data:** переиспользуется удаляемый low-res кэш кадров (память `StreamFrameCache` + диск `StreamFramePersistentStore`, S0712); новой схемы/миграции нет.

## 4. Направление решения (research, не финал)

- Кандидат: рендер muted ExoPlayer в `TextureView` (или `SurfaceTexture`) и снятие кадра через `PixelCopy` / `getBitmap()`, вместо offscreen `ImageReader`-Surface. Причина: device-тест S21 (2026-06-27, S0700) показал, что `TextureView`-путь НЕ падал нативно - он лишь отдавал чёрный кадр из-за слишком раннего `release()`. Значит нужен ещё ограниченный ожидание первого декодированного кадра (или короткий seek) перед снятием и teardown.
- Отклонено/слабо: `ImageReader` с фиксированным форматом (нельзя угадать формат вывода декодера заранее); `MediaMetadataRetriever.getFrameAtTime` (плохо ложится на live HLS без seekable-кадров).
- Открытый вопрос: нужен ли GL-контекст для `PixelCopy` с `SurfaceTexture`, или достаточно `TextureView.getBitmap()` на attached-view вне иерархии RecyclerView-ячейки (чтобы recycle/scroll не мешал).

**Артефакт:** `research/01__crash-free-frame-capture.md` (решение: `TextureView.getBitmap()` в window-attached невидимом offscreen-хосте; MMR/ImageReader отклонены). Тактический план: `S0933_streams-grid-capture-engine-rework/INDEX.md`.

## 5. Критерии готовности

1. Вход в сетку видео-каналов на реальном устройстве (Samsung Exynos / API36): процесс НЕ падает нативно; кадры появляются по одному у видимых каналов.
2. Последний кадр канала персистится low-res на диск (S0712) и показывается сразу при повторном открытии сетки (без ожидания нового захвата).
3. Механизм не использует offscreen `ImageReader`-Surface (или использует путь/формат, совместимый с HW-декодером); проверено на эмуляторе (software codec) И на реальном устройстве.
4. `CAPTURE_ENABLED=true`; проект компилируется; в logcat нет FATAL / native-kill при сессии в сетке >60s с периодическим и pull-to-refresh.

## Last Audit

### Implemented + emulator PASS - 2026-07-04 (via /spec-dev; device gate pending)

Реворк выполнен по тактическому плану (TextureView-путь). Затронуто: `StreamFrameSnapshotManager` (тело `capture()` переписано на off-screen `TextureView` + `setVideoTextureView` + await `onRenderedFirstFrame` + `getBitmap`; удалены `ImageReader`/`readFrame`/`readerHandler`/`POST_LAYOUT_SETTLE_DELAY_MS`; `CAPTURE_ENABLED=true`; параметр `hostProvider`), `StreamsActivity` (провайдер `binding.streamCaptureHost`), `activity_streams.xml` + `-land` + `-w600dp` (off-screen `streamCaptureHost`), `StreamGridAdapter` (устаревший ImageReader-комментарий).

**Эмулятор-смоук (standard debug 2.60.7040.321, Pixel-6 / API33 - тот самый software-кодек, что ронял ImageReader):**
- `S0933: TextureView capture start` - 8 захватов запущено; `S0712: persist captured frame` - **7 кадров реально захвачены и сохранены**.
- Сетка визуально показывает живые кадры видео (черепаха/1+1 International, флаг/1+1 Marafon, Times Square/10 TV, новости/100% NEWS, ювелирка/1001 Noites, 111 TV, 12 TV Parma, 13 Festival, 13 Ulica), зелёные галочки = online.
- **0 FATAL, 0 `ImageReader_JNI`, 0 `UnsupportedOperationException`** - старый крашащий путь удалён; процесс жив в `StreamsActivity`.
- Компиляция BUILD SUCCESSFUL; detekt scoped PASS (3 .kt, 0 новых findings); ticket-log PASS.

**Device gate (BlockNeedUserTest):** нативный краш воспроизводится только на Samsung Exynos / API36 (noLegal) - эмулятор его не показывает, поэтому эмулятор-PASS сильное, но не финальное доказательство. Владелец проверяет на своём устройстве: открыть видео-сетку с пиннами -> нет нативного process kill; кадры по одному; последний держится и восстанавливается при повторном открытии. Основание уверенности: (1) эмулятор-кодек, ломавший ImageReader, теперь работает; (2) device-тест S21 (2026-06-27) показал, что TextureView-путь не крашил нативно.
