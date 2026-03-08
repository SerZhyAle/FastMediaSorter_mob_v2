# Research: Audio Empty-State Animation (no cover art)

Date: 2026-03-07
Scope: research only, no code changes

## 1) Текущее состояние в проекте (AS-IS)

- Корневой фон экрана плеера черный (`activity_player_unified.xml`, `android:background="@color/black"`).
- В `PlayerView` уже включено `app:use_artwork="true"` и `app:default_artwork="@drawable/ic_music_note"`.
- Поверх `PlayerView` есть отдельный `audioCoverArtView` (тоже на черном фоне).
- В `ImageLoadingManager.loadAudioCoverArt()` при отсутствии embedded/online cover ставится fallback `R.drawable.ic_music_note`.
- Значит проблема не в отсутствии fallback, а в визуальной бедности состояния и/или в конфигурации слоев/моментах переключения.

Ключевые точки:
- `app_v2/src/main/res/layout/activity_player_unified.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`

## 2) Что есть "из коробки" в Media3/PlayerView

- `PlayerView` умеет показывать artwork/default artwork для аудио.
- Готового встроенного "музыкального визуализатора" (спектр, FFT-bars) в `PlayerView` нет.
- Для нестандартной анимации нужен отдельный overlay/view поверх `PlayerView`/`audioCoverArtView`.

## 3) Варианты анимации

### Option A: AnimatedVectorDrawable (AVD) + мягкая пульсация / ViewPropertyAnimator

Идея:
- Статичная иконка ноты + 2-3 тонких кольца/ореола, медленно пульсирующих (AVD).
- Альтернатива: простой `ViewPropertyAnimator` (`animate().scaleX().scaleY()`), который немного масштабирует сам `audioCoverArtView`.

Плюсы:
- Нулевая новая зависимость.
- Низкая нагрузка (vector + property animation).
- Простой контроль темпа, прозрачности, цикличности.

Минусы:
- Не реагирует на музыку (чисто декоративно).

Нагрузка/риск:
- CPU/GPU: низкие.
- Риск регрессий: низкий.

### Option B: Легкий кастомный `View` (Canvas), "дышащие" столбики

Идея:
- 5-9 столбиков внизу/по центру, анимация синусом (без реального FFT).

Плюсы:
- Очень легкая и контролируемая анимация.
- Можно сделать ненавязчиво (10-20 FPS, низкая амплитуда).
- Без разрешений и без новых библиотек.

Минусы:
- Псевдо-реакция, не реальный аудио-анализ.

Нагрузка/риск:
- CPU/GPU: низкие при throttling.
- Риск: низкий-средний (нужно аккуратно в lifecycle).

### Option C: Реальный визуализатор через `android.media.audiofx.Visualizer`

Идея:
- Получать waveform/FFT из audio session и рисовать спектр.

Плюсы:
- Реальная реакция на музыку.

Минусы:
- API сложнее, больше edge-cases на разных устройствах.
- По Android docs для использования Visualizer требуется `RECORD_AUDIO`; это UX/политический риск для пользователя.
- Выше вероятность девайс-специфичных проблем.

Нагрузка/риск:
- CPU: средняя (зависит от capture rate и draw pipeline).
- Риск: средний-высокий (permissions + совместимость).

### Option D: GIF/WebP loop в фоне

Идея:
- Зациклить заранее подготовленный мягкий анимированный фон.

Плюсы:
- Быстро по реализации.

Минусы:
- Декодирование кадров может быть дороже на слабых девайсах.
- Сложнее подстроить под разные разрешения/ориентации без артефактов.
- Риск "утомляющей" анимации выше.

Нагрузка/риск:
- CPU/RAM: средние.
- Риск: средний.

### Option E: Lottie

Идея:
- Lottie JSON-анимация как overlay.

Плюсы:
- Качественный визуал, гибкость motion.

Минусы:
- Новая зависимость.
- Не всегда бесплатно по CPU на слабых устройствах.
- Для задачи может быть overkill.

Нагрузка/риск:
- CPU/GPU: низкие-средние (зависит от сложности JSON).
- Риск: средний.

### Option F: Кастомный `AudioProcessor` в Media3 (ExoPlayer)

Идея:
- Создать свой `AudioProcessor` и внедрить его через кастомный `RenderersFactory` при создании `ExoPlayer`. 
- Перехватывать сырые PCM-байты до отправки в AudioTrack. Считать RMS (уровень громкости) или делать простой FFT.

Плюсы:
- **Не требует `RECORD_AUDIO` permission** (мы читаем свой же буфер внутри приложения).
- Реальная честная реакция на воспроизводимую музыку.
- Полный контроль над данными.

Минусы:
- Высокая сложность реализации (необходима логика вычислений RMS/FFT на Kotlin/Java).
- Выше нагрузка на CPU из-за программных расчетов PCM данных.
- Сложность синхронизации callback'ов на UI поток с нужной частотой (rate limiting).

Нагрузка/риск:
- CPU: средняя-высокая.
- Риск: средний (усложняет инициализацию плеера).

## 4) Рекомендация

Рекомендуемый путь (минимум риска и нагрузки):
1. Phase 1 (MVP): Option A (AVD pulse) как дефолт empty-state.
2. Phase 2 (опционально): добавить Option B (Canvas bars) под feature flag `settings`.
3. Option C (Visualizer) не брать в первую итерацию из-за permission/risk-профиля.

Почему:
- Требование "симпатично, но не грузит и не утомляет" лучше всего покрывается A/B.
- Не нужен новый permission, нет network/decode overhead, минимальные регрессии.

## 5) UI-гайд для "неутомляющей" анимации

- Частота: 0.2-0.4 Hz (медленная пульсация).
- Амплитуда: 4-8% масштаба.
- Альфа: 0.15-0.35, без резких вспышек.
- Цвет: 1 акцент + нейтральный серый, без кислотных контрастов.
- FPS cap для custom draw: 20 FPS достаточно.

## 6) Куда интегрировать (когда будет реализация)

- Основной слой: в `mediaContentArea` рядом с `audioCoverArtView` (или за ним).
- Управление видимостью: в `ImageLoadingManager.loadAudioCoverArt()` в ветке fallback.
- Lifecycle: старт/стоп жестко привязан к `Player.Listener.onIsPlayingChanged(isPlaying)`. Анимация должна работать только когда `isPlaying == true` (т.е. `playbackState == STATE_READY` и `playWhenReady == true`).

---

Итог: для текущего проекта оптимален AVD/Canvas-подход без Visualizer на первом шаге. Это дает заметный UX-эффект при минимальной нагрузке и без permission-рисков.
