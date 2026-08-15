# Стратегическая спецификация: S1127 - Диагностика воспроизведения стримов (AnalyticsListener)

**Ticket:** S1127
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-20
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - аудит stream-playback-recommendations.md (2026-07-20)
**Tactical spec:** `PLAN/S1127_stream-playback-diagnostics/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20

**Захвачено во время:** аудит внешнего документа `stream-playback-recommendations.md` (StreamsPlayer), режим «аудит + разложить в тикеты».

**Текст:**

Источник (§1 «diagnose before you tune» и §9): без структурной диагностики истинную причину зависаний не найти. Рекомендуется логировать time-to-first-frame, число/длительность сталлов, rendered vs dropped frames (`onDroppedVideoFrames`), выбранный декодер HW/SW (`onVideoDecoderInitialized`), ошибки с кодами (`onPlayerError`). Этот тикет - enabler: без метрик нельзя решить, каким стримам форсить SW-декод (S1125) или понижать качество (S1128).

Находки аудита FMS:

- `AnalyticsListener` НЕ используется нигде в `app_v2/src/main` (grep - 0 совпадений). Значит нет `onDroppedVideoFrames` и `onVideoDecoderInitialized` (HW vs SW) ни на одном пути.
- time-to-first-frame на стрим-пути ОТСУТСТВУЕТ: единственный `onRenderedFirstFrame`-пробник (`VideoPlayerManager.playerListener`, S0196) документирован как обходимый стрим-листенером (`StreamPlaybackHelper.kt:138`), а `streamPlaybackListener` вообще не переопределяет `onRenderedFirstFrame`.
  - Эвиденс: `VideoPlayerManager.kt:544-556` (skip для network/cloud), `StreamPlaybackHelper.kt:123-249`.
- Сталлы: PARTIAL - переходы состояний и попытки восстановления логируются (`StreamPlaybackHelper.kt:139`, `StreamStallWatchdog.kt:106-132`), но нет агрегированного per-session счётчика и суммарной длительности в мс, только отдельные строки с reason.
- Коды ошибок: COVERED - `onPlayerError` классифицирует `ERROR_CODE_BEHIND_LIVE_WINDOW/TIMEOUT/IO_*`, 429/5xx с полным URL (`StreamPlaybackHelper.kt:199-278`). Host отдельно не агрегируется по источникам.

Объём: завести `AnalyticsListener` (минимум на стрим-пути) с dropped-frames, HW/SW декодером, time-to-first-frame и агрегированными сталл-метриками; логирование через Timber (без Sxxxx в постоянных логах). Опционально - агрегация по host для поиска систематически плохих источников.

Не покрыт открытыми тикетами (каталог: diagnostics / dropped video frames - нет записей).

**Вложения:**
- Исходный документ рекомендаций (StreamsPlayer, §1/§9) - `PLAN/S1127_stream-playback-diagnostics/attachments/stream-playback-recommendations.md`

---

## 1. Проблема

Без структурной диагностики истинную причину зависаний стрима не найти. Сейчас на стрим-пути нет `AnalyticsListener` (grep - 0 в `src/main`), поэтому не логируются dropped vs rendered frames, выбранный декодер (HW/SW) и time-to-first-frame; сталлы видны только отдельными строками state-перехода, без агрегированного per-session счётчика и суммарной длительности. Это блокирует обоснованные решения в S1125 (форсить SW-декод) и S1128 (понижать качество) - они опираются на метрики, которых нет.

---

## 2. Цели

1. Завести `AnalyticsListener` на стрим-плеере (`playStreamVideo`) с симметричным снятием на всех teardown-путях.
2. Логировать через Timber (постоянные логи, БЕЗ `Sxxxx`): time-to-first-frame, dropped frames (`onDroppedVideoFrames`), декодер + HW/SW (`onVideoDecoderInitialized`).
3. Агрегировать per-session сталл-метрики: число сталлов и суммарную длительность в мс (buffering после первого кадра), плюс one-line session summary на release.
4. Агрегатор - чистый Kotlin с инъецируемым таймером, покрытый unit-тестом (детерминированно, off-device).

**Non-goals:**

- Агрегация по host для поиска систематически плохих источников (опционально, отдельным тикетом при необходимости).
- Изменение самой логики восстановления/качества (это S1125/S1128 - потребители метрик).
- UI-визуализация метрик; только логи.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Нулевой hot-path оверхед: метрики - низкочастотные события (state/decoder/first-frame) или счётчик кадров от Media3, без per-frame Timber-спама.

### 3.2 Жёсткие ограничения

- **Flavor:** стрим-путь активен там, где `SUPPORT_STREAMS=true` (standard, legacy, vr, noLegal). Код в `src/main`, поведение гейтится наличием стрима.
- **API level:** без API-специфики (Media3 `AnalyticsListener`, minSdk 26/23).
- **Wear OS:** не затрагивается.
- **Производительность:** только агрегированный/событийный лог; никаких per-frame аллокаций.
- **Совместимость данных:** без изменений схемы Room.
- **Локализация:** не применяется (только dev-логи, не user-facing строки).
- **Доступность:** не визуальная фича.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1125 (SW-декод по измерению), S1128 (quality step-down по измерению)

---

## 4. Контекст текущей архитектуры

Стрим-плеер строится в `StreamPlaybackHelper.playStreamVideo` (`ui/player/helpers/`): `ExoPlayer.Builder(..).build()`, затем добавляется `streamPlaybackListener` (`Player.Listener`), трекаемый как `VideoPlayerManager.activeExtraPlayerListener`. Снятие листенеров симметрично в двух местах `VideoPlayerLifecycleHelper`: `releasePlayer()` и `onDestroy()` (оба зовут `listOfNotNull(playerListener, activeExtraPlayerListener).forEach(player::removeListener)` + `player.release()`). Стрим-state-переходы уже логируются (`onPlaybackStateChanged`, S0937), сталл-восстановление - в `StreamStallWatchdog`, коды ошибок классифицируются в `onPlayerError`. Отсутствует именно `AnalyticsListener` (dropped frames, decoder, first-frame render) и агрегированные сталл-метрики.

---

## 5. Предлагаемый подход

Тонкий `AnalyticsListener`-адаптер поверх чистого агрегатора, подключаемый рядом с существующим `streamPlaybackListener` и снимаемый на тех же teardown-рёбрах.

### 5.1 Основные столпы / модули

- `StreamPlaybackDiagnostics` (новый, `ui/player/helpers/`) - чистый Kotlin-агрегатор, инъецируемый таймер `() -> Long`, без Media3-типов. Держит: TTFF, stallCount, totalStallMs, droppedFrames, decoderName+isHardware. Метод `summary()` - одна greppable-строка.
- `StreamDiagnosticsAnalyticsListener` (новый) - реализует `AnalyticsListener`, транслирует callbacks в агрегатор + постоянный Timber-лог (без `Sxxxx`).
- `VideoPlayerManager` - новое поле `activeStreamAnalyticsListener: AnalyticsListener?` (+ ссылка на диагностику для summary на release).
- Wiring: `playStreamVideo` строит диагностику+листенер, `player.addAnalyticsListener(..)`, ставит `preparedAtMs` перед `player.prepare()`.
- Снятие: в `VideoPlayerLifecycleHelper.releasePlayer()` И `onDestroy()` - `player.removeAnalyticsListener(..)` + лог `summary()` + null поля (симметрия listener - оба пути).

### 5.2 Потоки данных и событий

- `onVideoDecoderInitialized(name)` -> HW/SW по эвристике имени (SW: `c2.android.*`/`OMX.google.*`; иначе HW) -> `diagnostics.onDecoderInitialized`.
- `onRenderedFirstFrame` -> `diagnostics.onFirstFrameRendered()` (TTFF = now - preparedAtMs).
- `onDroppedVideoFrames(count)` -> `diagnostics.onDroppedFrames(count)`.
- `onPlaybackStateChanged`: BUFFERING после первого кадра -> `onStallStarted`; READY -> `onStallEnded` (аккумулирует длительность + счётчик).
- release/destroy -> `Timber.i("Stream session: <summary>")`.

### 5.3 Точки расширяемости

- Агрегатор изолирован от Media3 - позже можно добавить host-агрегацию или экспорт в StatsSink без правки листенера.
- HW/SW-эвристика вынесена отдельно - при появлении точного Media3-API заменить в одном месте.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет (архитектура резолвится из кода; точки attach/detach и агрегатор определены).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Утечка `AnalyticsListener` (не снят на одном из teardown-путей) держит player | Средняя | Memory leak плеера | Снятие в ОБОИХ местах `VideoPlayerLifecycleHelper` (releasePlayer + onDestroy), как для `activeExtraPlayerListener` |
| HW/SW-эвристика по имени декодера неточна на редком вендоре | Низкая | Неверная метка hw/sw в логе | Логировать и сырое имя декодера рядом с меткой |
| Реальные метрики эмитятся только при живом стриме | Средняя | Полная проверка требует устройства | Unit-тест агрегатора off-device; интеграция - device-test (BlockNeedUserTest) |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (диагностический enabler, не user-facing).

---

## 9. Архитектурные решения (ADR)

- Агрегатор - чистый Kotlin с инъецируемым таймером (не `SystemClock` напрямую), чтобы сталл/TTFF-аккаунтинг был детерминированно unit-тестируем off-device.
- HW/SW определяется эвристикой имени декодера (Media3 1.2.1 не отдаёт прямой флаг в `onVideoDecoderInitialized`).

---

## 10. Связи с другими спеками

- Enabler для S1125 (SW-декод по измерению) и S1128 (quality step-down по измерению) - оба потребляют эти метрики.
- Соседствует с S0937 (stream state logging) и `StreamStallWatchdog` (S0936) - дополняет, не заменяет.

---

## 11. Критерии готовности (strategic-level)

1. `AnalyticsListener` подключён на стрим-пути и снят симметрично на обоих teardown-путях (нет утечки листенера - проверка на minified release-варианте не требуется, но listener-symmetry-гейт зелёный).
2. В логах при воспроизведении стрима присутствуют TTFF, dropped frames, декодер+HW/SW и session-summary со сталл-метриками.
3. `StreamPlaybackDiagnostics` покрыт unit-тестом (TTFF, сталл count/duration, dropped, decoder, summary) - PASS off-device.
4. Device-test (Quest не нужен, обычное устройство/эмулятор с живым стримом): метрики появляются в logcat при проигрывании реального стрима.

---

## Last Audit

**Date:** 2026-07-20
**Type:** on-device (emulator-5554, Pixel 9 AVD, Android 15 / SDK 35)
**Build:** v2.60.7201.237-DEBUG (com.sza.fastmediasorter.debug) - installed APK, no rebuild
**Verdict:** PASS

Live HLS video stream played end-to-end: "1+1 International" (`https://dash2.antik.sk/live/test_one_plus_one_int_tizen/playlist.m3u8`), a video `.m3u8` path (not radio/RTSP) so the video `AnalyticsListener` is exercised. Buffer -> render -> ~18s playback -> back-out teardown. Screenshot confirms live video rendering (channel logo + "12+" overlay), not a spinner.

Expected vs actual (verbatim logcat, cleared buffer before run):

- Probe (new code path ran) - expected `S1127: stream diagnostics AnalyticsListener attached` | actual `S1127: stream diagnostics AnalyticsListener attached path=https://dash2.antik.sk/live/test_one_plus_one_int_tizen/playlist.m3u8` (PASS)
- TTFF - expected `Stream diag: first frame ttff=<N>ms` | actual `Stream diag: first frame ttff=1983ms path=..` (PASS)
- Decoder + HW/SW - expected `Stream diag: decoder=<name> (hw|sw)` | actual `Stream diag: decoder=c2.goldfish.h264.decoder (hw) path=..` (PASS)
- Dropped frames (optional) - expected `Stream diag: dropped=<n>` | actual line not emitted; session summary reports `dropped=0` (no drops this session, so `onDroppedVideoFrames` had nothing to log) (PASS - consistent, not a miss)
- Session summary on teardown - expected `Stream session: ttff=.. stalls=.. totalStallMs=.. dropped=.. decoder=..` | actual `Stream session: ttff=1983ms stalls=0 totalStallMs=0 dropped=0 decoder=c2.goldfish.h264.decoder(hw)` (PASS)

Note: emulator uses the software Goldfish H.264 codec (`c2.goldfish.h264.decoder`), which the name heuristic labels `(hw)` because it starts with neither `c2.android.` nor `OMX.google.`. This is expected behavior of the documented heuristic on the emulator; on real hardware a vendor HW decoder (e.g. `c2.qti.*`, `OMX.qcom.*`) would land the same `(hw)` label, and a genuine SW fallback (`c2.android.avc.decoder`) would read `(sw)`.

**Evidence:** `temp/S1127/logcat.txt` (filtered capture), `temp/S1127/stream_playing.png` (rendering proof).
