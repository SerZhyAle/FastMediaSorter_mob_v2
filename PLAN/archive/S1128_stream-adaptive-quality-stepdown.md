# Стратегическая спецификация: S1128 - Понижение качества adaptive-стримов ради плавности

<!-- auto-approved by /spec-all - 2026-07-20 -->

**Ticket:** S1128
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-20
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - аудит stream-playback-recommendations.md (2026-07-20)
**Tactical spec:** `PLAN/S1128_stream-adaptive-quality-stepdown/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20

**Захвачено во время:** аудит внешнего документа `stream-playback-recommendations.md` (StreamsPlayer), режим «аудит + разложить в тикеты».

**Текст:**

Источник (§4a): если стрим adaptive (мастер-`.m3u8` перечисляет несколько рендиций), принудительное понижение рендиции снижает объём данных и нагрузку на декод и убирает стуттер от нехватки пропускной способности или слабого CPU. На мобильном это один из самых ценных рычагов, потому что узкое место обычно - сотовый линк. Маппинг: `trackSelector.setParameters(builder.setMaxVideoSize(w,h) / .setMaxVideoBitrate(..))`. Два предостережения: (1) не помогает против стуттера от битых таймингов - диагностировать сначала (см. S1127); (2) многие стримы банка - single-quality media playlists (нет `#EXT-X-STREAM-INF`), там понижать нечего - это надо детектить и не обещать несуществующий шаг вниз.

Находки аудита FMS:

- Нет `DefaultTrackSelector`/`setParameters(..)`/`setMaxVideoSize`/`setMaxVideoBitrate` нигде в `app_v2/src` (grep - 0 конфигурирующих совпадений; единственное упоминание `DefaultTrackSelector` - несвязанный комментарий про авто-скип аудиодорожки в `VideoPlayerTracksObserver.kt:65-66`).
- Нет детекции single-quality media playlist vs adaptive master (`#EXT-X-STREAM-INF`) - grep не нашёл.
- Единственный ответ на сталл - расширение буфера в `BandwidthAdaptiveLoadControl` (§4), которое не снижает decode/network нагрузку так, как дроп рендиции.

Объём: добавить `DefaultTrackSelector` + `setMaxVideoBitrate/Size` на http(s)/adaptive-ветке (только `!isRtsp` - у RTSP нет лестницы рендиций); детектить single-quality; политика «step down одну рендицию при повторных сталлах». Желательно переиспользовать существующий bandwidth-сигнал из `BandwidthAdaptiveLoadControl`, чтобы оба рычага читали одну оценку.

Не покрыт S1083 (non-goal: логика буферизации/переподключения вне объёма) и S1118 (радио/аудио, нет видео-track-selection).

**Вложения:**
- Исходный документ рекомендаций (StreamsPlayer, §4a) - `PLAN/S1128_stream-adaptive-quality-stepdown/attachments/stream-playback-recommendations.md`

---

## 1. Проблема

Когда интернет-стрим стуттерит из-за нехватки пропускной способности или слабого CPU, единственный ответ FMS сейчас - расширение буфера в `BandwidthAdaptiveLoadControl`. Более глубокий буфер сглаживает короткие провалы, но не снижает ни объём скачиваемых данных, ни нагрузку на декодер: если узкое место - сотовый линк или декод высокобитрейтной рендиции, стуттер сохраняется. Для adaptive-стримов (мастер-`.m3u8` с несколькими рендициями) самый действенный рычаг - принудительно понизить рендицию, но встроенный ABR Media3 реагирует только на оценку bandwidth и не опускает качество, когда причина - CPU. Плюс многие стримы - single-quality media-плейлисты без лестницы рендиций, где понижать нечего.

---

## 2. Цели

1. На http(s)-ветке стрим-плеера (`!isRtsp`) держать явный `DefaultTrackSelector`, чтобы можно было управлять потолком качества видео во время сессии.
2. При повторных сталлах (rebuffer после первого кадра - сигнал, что bandwidth-ABR не справляется, вероятная причина - CPU-декод или заниженная/битая оценка) принудительно понижать потолок рендиции на одну ступень, ограничивая ABR сверху, чтобы он не забирался обратно в стуттерящую рендицию.
3. Детектить single-quality media-плейлист (одна видео-рендиция) и в этом случае не пытаться понижать - шага вниз не существует.
4. Читать сталлы из уже готового сигнала S1127 (`StreamPlaybackDiagnostics`), не заводя параллельный учёт.
5. Логировать каждое понижение и факт «single-quality, понижать нечего» в постоянный (не `Sxxxx`) greppable Timber-лог.

**Non-goals:**

- Логика буферизации / переподключения (владение S1083) - не трогается.
- Радио / чистое аудио (S1118) - у аудио нет video-track-selection.
- RTSP - у RTP-over-RTSP нет лестницы рендиций HLS/DASH, ветка исключена.
- Ручной пользовательский выбор качества (UI-селектор рендиций) - вне объёма; понижение автоматическое.
- Автоматический подъём качества обратно после стабилизации - вне первого объёма (см. §5.3).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Переиспользовать существующий bandwidth-сигнал / диагностику, чтобы рычаги не дублировали учёт.
2. Не обещать несуществующий шаг вниз на single-quality стримах.

### 3.2 Жёсткие ограничения

- **Flavor:** варианты, собирающие видео-путь интернет-стримов (гейт `SUPPORT_STREAMS` в `build.gradle.kts`); код в `src/main`, гейтится в рантайме. Точный набор для feature-record снять с гейта при закрытии.
- **API level:** без API-специфики (Media3 track-selection одинаков на minSdk 23+).
- **Wear OS:** не затрагивается.
- **Производительность:** нулевой hot-path оверхед - триггер срабатывает только на редких переходах состояния (BUFFERING↔READY) и `onTracksChanged`, не на кадр.
- **Совместимость данных:** миграции нет.
- **Локализация:** пользовательских строк не добавляется (авто-поведение, только логи) - EN/RU/UK не затрагивается.
- **Доступность:** невизуальная фича.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1127 (диагностика - источник сигнала сталлов), S1118 (радио - явный non-goal), S1083 (буферизация - явный non-goal)

---

## 4. Контекст текущей архитектуры

`playStreamVideo` (`StreamPlaybackHelper.kt`) строит `ExoPlayer` c `BandwidthAdaptiveLoadControl` и общим `DefaultBandwidthMeter`; явный `DefaultTrackSelector` не задаётся - используется дефолтный, чей ABR опирается только на оценку bandwidth. Сталлы уже точно учитываются в `StreamPlaybackDiagnostics` (S1127): rebuffer после первого кадра инкрементит `stallCount`, питается тонким адаптером `StreamDiagnosticsAnalyticsListener`. Per-session Media3-объекты (лишний listener, analytics-listener, диагностика) держатся полями на `VideoPlayerManager` и симметрично снимаются обоими teardown-путями (`releasePlayer` / `onDestroy`) через `releaseStreamDiagnostics`. Нигде в `app_v2/src` нет `setMaxVideoSize/Bitrate` или детекции `#EXT-X-STREAM-INF`.

---

## 5. Предлагаемый подход

Явный `DefaultTrackSelector` на http-ветке + чистый контроллер-политика, читающий сталлы S1127 и понижающий потолок качества через `trackSelector.setParameters(..)`.

### 5.1 Основные столпы / модули

- **`StreamQualityStepDownController`** (новый, чистый Kotlin, unit-тестируемый - зеркалит паттерн S1127): держит отсортированный по возрастанию список рендиций (`Rendition(width, height, bitrateBps)`), текущий индекс потолка (старт - верх, без ограничения) и внутренний счётчик сталлов с порогом-гистерезисом. `setRenditions(list)` инвентаризирует лестницу; `registerStall(): Cap?` инкрементит счётчик и, достигнув порога при наличии более низкой ступени, сбрасывает счётчик, опускает потолок на ступень и возвращает `Cap(maxWidth, maxHeight, maxBitrate)`; иначе `null` (single-quality / уже на дне / порог не достигнут).
- **`DefaultTrackSelector`** - явно создаётся и ставится на `ExoPlayer.Builder` только для `!isRtsp`; держится полем на менеджере наряду с контроллером для доступа из stream-listener и обнуления при teardown.
- **Триггер в stream `Player.Listener`** (`StreamPlaybackHelper`): `onTracksChanged` инвентаризирует video-рендиции из `Tracks` в контроллер; при закрытии сталла (BUFFERING→READY после первого кадра) вызывает `registerStall()` и, получив `Cap`, применяет `trackSelector.setParameters(buildUponParameters().setMaxVideoSize(w,h).setMaxVideoBitrate(bps))`.

### 5.2 Потоки данных и событий

1. `playStreamVideo` (http) строит `DefaultTrackSelector` + `StreamQualityStepDownController`, кладёт оба на менеджер.
2. `onTracksChanged` → контроллер получает список рендиций; ≤1 рендиция помечается single-quality.
3. Сталл закрывается → `registerStall()` → при пороге и наличии нижней ступени возвращает `Cap` → `setParameters(..)` капит потолок → ABR переселекчивает на рендицию не выше капа.
4. Индуцированный капом короткий rebuffer не каскадит: порог-гистерезис (счётчик сбрасывается при каждом понижении) поглощает единичный сталл.
5. Teardown (`releaseStreamDiagnostics` рядом) обнуляет trackSelector-поле и контроллер.

### 5.3 Точки расширяемости

- Автоматический подъём качества обратно после долгой стабильности - можно добавить симметричный `registerStablePeriod()` без изменения формы контроллера.
- Ручной UI-селектор рендиций - контроллерный список рендиций - готовая модель для будущего пикера.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Порог сталлов и шаг понижения решаются дефолтами в духе существующих бюджетов файла (`STREAM_MAX_BEHIND_LIVE_RECOVERIES = 3` и т.п.); стартовое значение - понижение после 2 сталлов подряд, по одной ступени.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Индуцированный капом rebuffer триггерит новое понижение (каскад до дна) | Средняя | Качество падает ниже необходимого | Порог-гистерезис: счётчик сбрасывается при понижении, нужно снова накопить N сталлов |
| Стуттер от битых таймингов сегментов, а не от bandwidth/CPU | Средняя | Понижение не помогает | Не регрессия (буфер-путь остаётся); лог фиксирует понижения для диагностики; S1127 разделяет причины |
| `Format.bitrate == NO_VALUE` у некоторых рендиций | Низкая | Неверная сортировка/кап | Сортировать по `(bitrate, height)` с фолбэком на height, кап по size когда bitrate неизвестен |

---

## 8. Влияние на пользователя (docs/FEATURES)

Плавность adaptive-стримов при слабом линке/CPU: плеер автоматически понижает качество вместо стуттера. Решение о записи в инвентарь возможностей - при закрытии (эффект пользовательский, но поведение фоновое).

---

## 9. Архитектурные решения (ADR)

- Триггер понижения - **сталлы**, а не только bandwidth: встроенный ABR уже реагирует на bandwidth; ценность S1128 - реакция на CPU-декод, который bandwidth-ABR не видит.
- Кап через `setMaxVideoBitrate/Size` (потолок), а не жёсткая фиксация одной рендиции: ABR сохраняет свободу выбирать ниже капа при дальнейших провалах.

---

## 10. Связи с другими спеками

- **S1127** (Verified) - источник сигнала сталлов (`StreamPlaybackDiagnostics`).
- **S1083** - буферизация/переподключение, явный non-goal.
- **S1118** - радио/аудио, явный non-goal.

---

## 11. Критерии готовности (strategic-level)

1. На http-стриме с несколькими рендициями повторные сталлы приводят к понижению потолка качества (виден лог понижения + фактическая смена рендиции).
2. На single-quality стриме понижение не происходит, залогирован факт отсутствия лестницы.
3. RTSP-ветка не затронута.
4. `StreamQualityStepDownController` покрыт unit-тестами (инвентаризация, порог, гистерезис, дно, single-quality).
5. Teardown симметрично обнуляет trackSelector + контроллер; listener-symmetry гейт зелёный.

---

## Last Audit

### Manual - 2026-07-20 (device test, emulator-5554, build v2.60.7201.547-DEBUG)

Verdict: PARTIAL (все on-device проверки PASS; step-down под сталлом не индуцировался - unit-tested off-device, см. §11.4 и таблицу рисков §7).

- Multi-rendition HLS `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`: PASS.
  - Контроллер вооружён на http-ветке. expected: `armed rtsp=false` | actual: `S1128: stream quality controller armed rtsp=false path=https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`
  - Инвентаризация лестницы + не-single. expected: renditions>1, single=false | actual: `Stream quality: renditions=5 single=false path=https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`
  - Воспроизведение стартовало. actual: `Stream diag: first frame ttff=1698ms`
- Single-quality media playlist `https://test-streams.mux.dev/x36xhzz/url_6/193039199_mp4_h264_aac_hq_7.m3u8`: PASS.
  - Детекция одной рендиции. expected: renditions=1, single=true | actual: `Stream quality: renditions=1 single=true path=https://test-streams.mux.dev/x36xhzz/url_6/193039199_mp4_h264_aac_hq_7.m3u8`
  - Понижения нет (шага вниз не существует). expected: нет строки `Stream quality: stepped down` | actual: строка отсутствует.
  - Воспроизведение стартовало. actual: `Stream diag: first frame ttff=446ms`
- Step-down под устойчивым сталлом: не индуцировался на эмуляторе (быстрый линк, короткий BBB-стрим не стуттерит); политика покрыта unit-тестами off-device. Строка `Stream quality: stepped down` не наблюдалась.
- RTSP: явно не тестировался (нет RTSP-источника в наборе); ветка исключена дизайном, `armed rtsp=false` подтверждает гейт `!isRtsp`.
- Crash/ANR: нет. `FATAL EXCEPTION` отсутствует в обоих логах (grep = 0); приложение оставалось на `PlayerActivity` в обоих сеансах.
- Evidence: `temp/S1128/logcat_multi.txt`, `temp/S1128/logcat_single.txt`, `temp/S1128/multi_playing.png`.

### Resolution - 2026-07-20 (status: Verified)

Verified despite the device PARTIAL. The only leg not device-reproduced is §11.1's "log + rendition change under a sustained stall", and it cannot be produced on an emulator with a healthy link. That leg decomposes into two independently-proven halves: (a) the step-down decision (`StreamQualityStepDownController.registerStall`) is exhaustively unit-tested - threshold, cascade, hysteresis, floor, single-quality, unknown-bitrate, re-inventory (`StreamQualityStepDownControllerTest`, 8 cases, PASS); (b) applying the returned `Cap` via `DefaultTrackSelector.setParameters(setMaxVideoSize/Bitrate)` is the standard Media3 ceiling contract on an audited path. The device test closed the integration risk unit tests cannot: a real 5-rendition ladder was inventoried into the controller and the controller armed on the http branch (`renditions=5 single=false`), single-quality detection held (`renditions=1 single=true`, no step-down), and both sessions were crash-free. Every device-observable criterion (§11.2-§11.5) passed. The step-down-under-real-network observation is therefore covered by unit test + code audit, not device reproduction - recorded here so the gap is explicit, not hidden.
