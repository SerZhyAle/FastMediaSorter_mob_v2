# Спецификация: S1113 - VR: 7K HEVC видео не рендерится в immersive (застывший первый кадр)

**Ticket:** S1113
**Status:** Archived
**Priority:** 72
**Date:** 2026-07-19
**Tier:** 3 - bugfix (ad-hoc)
**Roadmap entry:** Ad-hoc - парковка находки при device-тесте VR на Quest 3 (2026-07-19)

> **Scope:** Draft. Симптом и объективные доказательства с устройства. Тесно связан с S0772 (тот же 7K-файл), но симптом другой: не OOM-краш, а не-старт воспроизведения. Root cause - гипотеза.

---

## 0. Захваченный материал (verbatim)

Найдено при device-тесте VR на Quest 3 (noLegal debug), сессия 2026-07-19. Вход: обычный плеер -> VR-badge (`StartVrPlaybackUseCaseImpl source=PLAYER_BADGE mode=FILE_URI mediaType=VIDEO`).

Репорты владельца (verbatim):

> запустил дважды - просто черный кран в иммерсив

> 18vr.. просто белые половна мира. можт это первый кадр видео и оно просто не старптует

> video_180_stereo_sbs.mp4 играет иммрсив из плеера норм

Симптом: файл `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` (~20 GB, 7K HEVC, SBS-180) в immersive показывает застывшие пустые «белые половины мира» - воспроизведение не стартует. Обычное видео нормального разрешения (`video_180_stereo_sbs.mp4`, 42 MB) в тот же immersive-путь играет нормально. Значит дефект 7K-специфичный, а не общий для immersive-видео.

---

## 1. Проблема

7K HEVC видео в immersive-плеере не начинает воспроизведение: XR-сцена и геометрия рисуются, но видео-текстура остаётся пустой (застывший/первый кадр). Нет OOM, нет краша, нет `onPlayerError`. Видео нормального разрешения на том же пути играет.

## 2. Доказательства (logcat -b all, Quest 3, 2026-07-19)

Полный лог: `temp/scratch/vr_session_20260719/logcat_full.log`. Immersive-сессия 7K: ~00:46:38 -> 00:47:01 (вышли в vrshell).

- Вход: `StartVrPlaybackUseCaseImpl: VR launch dispatched source=PLAYER_BADGE mode=FILE_URI mediaType=VIDEO`.
- Стерео разрешено верно: `S0771: immersive stereo resolved layout=SIDE_BY_SIDE file=18VR_..._180x180_3dh.mp4`.
- XR поднялся: `native video surface created`; `frame loop entered`; `set_render_config projection=1 layout=2`; `OpenXR ... IDLE -> READY -> SYNCHRONIZED`; HUD залит (`hud upload 1024x640`).
- Видео-текстура: единственный `texture uploaded: 4096x2048` (00:46:38.595), дальше за 23 сек НЕТ ни одного повторного `texture uploaded` / `updateTexImage` / `onFrameAvailable` -> кадры не текут.
- Декодер: новый `c2.qti.hevc.decoder` component[88], `video/hevc`, surface generation set - но нет `onVideoSizeChanged` / output-format с размерами / признаков output-кадров.
- При передаче плоский->immersive: серия `W/MessageQueue: Handler (MediaCodec$EventHandler) sending message to a Handler on a dead thread` + `IllegalStateException at MediaCodec.postEventFromNative` (декодер плоского плеера отпускается с колбэками в полёте).
- НЕТ `S0772:` маркера буфер-капа в immersive-сессии -> immersive-плеер, вероятно, НЕ проходит через `PrefetchLoadControlFactory` (capped LoadControl плоского плеера).
- Контроль: `video_180_stereo_sbs.mp4` (42 MB) на том же пути играет (владелец подтвердил) -> immersive-видео путь исправен, ломается именно 7K.

## 3. Root cause (гипотезы, требуют проверки)

1. **LoadControl не применён к immersive-плееру (ведущая гипотеза).** S0772-кап (`PrefetchLoadControlFactory`, heap-bounded) не всплывает в immersive-логе. Immersive ExoPlayer, вероятно, создаётся с дефолтным LoadControl; на 7K-потоке буферизация не достигает старта (застревает в BUFFERING, не доходит до READY/playWhenReady) -> застывший первый кадр. Обычное видео стартует быстро, поэтому играет. Фикс-направление: подать тот же heap-bounded LoadControl (S0772) и в VR/immersive-плеер.
2. **Предел декодера/поверхности на 7680-широкий HEVC в immersive-конфиге.** Quest 3 по спеке тянет ~8K HEVC, но per-eye SBS-180 7K + external-texture surface могут не отдавать output-кадры. Проверить фактический max и путь SurfaceTexture->XR video quad.
3. **Гонка при передаче плоский->immersive** (`MediaCodec dead thread`): декодер плоского плеера не полностью дренируется до release; возможна порча состояния surface/декодера перед immersive-стартом.

## 4. Связь с S0772

- S0772 (`bugfix-vr-7k-video-playback-oom-crash`, BlockNeedUserTest) - ТОТ ЖЕ 7K-файл. За сессию 2026-07-19 OOM/краша НЕ было (OOM-фикс держит). Но 7K не рендерится в immersive - отдельный симптом (не-старт, не OOM), вне буквального критерия S0772 («no OutOfMemoryError»).
- Следствие: S0772 нельзя чисто Verified как «7K играет» - рендер-проблема трекается здесь. S0772 остаётся на своём критерии (OOM), device-часть по нему всё ещё pending.

## 5. Влияние

- Флагманский 7K-сценарий (immersive 3D-фильм в высоком разрешении) не работает: пользователь видит пустые белые половины вместо видео.
- Только `noLegal` (VR-плеер). Обычное разрешение не затронуто.

## 6. Не покрыто существующими тикетами

- **S0772** - OOM-краш на 7K; здесь не-старт рендера (другой симптом), кросс-линк выше.
- **S1112** - TB->SBS мис-детект (мешанина на картинке); здесь картинки нет вовсе (пустая текстура). Другой дефект.
- Дедуп по каталогу (`7k`,`immersive`,`video`,`render`) - иных совпадений нет.

## 7. Расследование на устройстве (2026-07-19) и следующий шаг

Подтверждено статикой + логом этой сессии:

- Immersive-плеер создаётся ДЕФОЛТНЫМ `ExoPlayer.Builder(ctx).build()` - `DiagnosticXrActivity` (стр. ~1037) и `ImmersiveBrowsePlaybackController` (стр. ~59). НЕТ `PrefetchLoadControlFactory` (S0772-кап есть только у плоского плеера).
- `onPlayerError`-листенер подключён (стр. ~1041), но в 7K-сессии НЕ сработал - декодер не падал (не source/decoder/render error).
- Нативная `SurfaceTexture` создаётся без `setDefaultBufferSize` (`xr_session.cpp createVideoSurfaceObjects`) - размер диктует MediaCodec, зашитого кэпа размера НЕТ. Значит дело не в клампе текстуры.
- Контроль: обычное видео (`video_180_stereo_sbs.mp4`) на том же пути играет; 7K застывает -> дефект 7K-специфичный. Другие плееры декодируют этот 7K на этом же Quest -> регрессия именно нашего пайплайна.

Ведущая гипотеза: дефолтный LoadControl не добирает старт-буфер на 7K high-bitrate -> плеер не доходит до `STATE_READY` -> кадры не идут (OOM при этом нет, декод не наполняет буфер). Обычное видео стартует быстро.

Следующий шаг (нужна device-итерация, очки сняты):
1. Добавить пробу playbackState/isLoading/первого кадра в immersive-плеер, снять logcat на 7K.
2. Кандидат-фикс: подать immersive-плееру тот же heap-bounded `PrefetchLoadControlFactory`, что и плоскому (S0772), + гарантировать `playWhenReady=true`; проверить, что 7K доходит до READY и рендерит.
3. Если не помогает - проверить фактический вывод декодера (MediaCodec output-format / rendered frames) на 7680-широкий HEVC в external-texture-пути.
