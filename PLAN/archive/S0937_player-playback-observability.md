# Стратегическая спецификация: S0937 - Обсервабилити воспроизведения: состояния/ошибки/stall плеера

**Ticket:** S0937
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-04 (из анализа лога)
**Tactical spec:** `PLAN/S0937_player-playback-observability/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

**Текст:**

Найдено при анализе лога `logs/fastmediasorter_20260704_055031.log` во время расследования зависания трансляции (S0936).

За все 102 с воспроизведения живого HLS-стрима в логе **ноль** событий состояния плеера:
- нет `STATE_BUFFERING` / `STATE_READY` / `STATE_ENDED`,
- нет `onPlayerError`,
- нет счётчиков rebuffer / dropped frames / stall.

Поиск по логу `Choreographer|Skipped frames|ANR|onPlayerError|STATE_|onPlaybackState|stall|rebuffer|dropped` дал 1 совпадение - и то это `media3 OOM-safe logger installed` на старте, не событие воспроизведения.

Следствие: зависание живого стрима не оставляет диагностического следа. По логу постфактум невозможно сказать, встал ли декодер, отвалился ли сегмент, ушёл ли live-edge, или это UI-фриз. Пришлось выводить причину S0936 косвенно (тренд нативной кучи + сетевые события вокруг окна воспроизведения).

Это Timber-экспорт (теги App/UserAction/SLog) - системных MediaCodec/Choreographer в нём нет по определению. Но события самого ExoPlayer приложение могло бы логировать своим Timber-деревом и не делает.

**Захвачено во время:** S0936 (расследование зависания трансляции)

---

## 1. Проблема

Плеер (ExoPlayer/Media3) не инструментирован: переходы состояний, ошибки, события буферизации/stall не попадают в Timber-лог приложения. Из-за этого баги воспроизведения - заморозки, рассинхрон, тихие отказы - невозможно диагностировать постфактум по харвесту лога; каждый раждый разбор требует живого repro с полным системным logcat. Область: слой плеера (video/stream playback).

---

## 2. Цели

1. В логе приложения видны переходы состояний плеера (buffering/ready/ended/idle).
2. В логе видны ошибки плеера (`onPlayerError`) с кодом/причиной.
3. В логе видны события деградации: stall/rebuffer, потенциально dropped frames, уход от live-edge для live-потоков.
4. Разбор зависания стрима возможен по одному Timber-харвесту, без обязательного системного logcat.

**Non-goals:**

- Полноценная телеметрия/аналитика воспроизведения в облако.
- Метрики производительности рендера на каждый кадр (дорого, шумно).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Объём узкий: только логирование переходов состояний stream-listener-а; без нового компонента и без изменения логики воспроизведения.

### 3.2 Жёсткие ограничения

- **Flavor:** все (плеер общий для всех вариантов).
- **API level:** без API-специфики.
- **Wear OS:** уточнить (есть ли отдельный плеер на wear).
- **Производительность:** лог не должен спамить на hot-path воспроизведения; уровни debug/gated, не info по каждому кадру.
- **Совместимость данных:** нет.
- **Локализация:** не применимо (логи EN-only, не user-visible).
- **Доступность:** не применимо.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0936 (зависание трансляции - главный потребитель этой обсервабилити)

---

## 4. Контекст текущей архитектуры

Уточнено чтением кода (объём уже, чем казалось при драфте):

1. **Обычное видео уже инструментировано.** `VideoPlayerManager.playerListener` логирует переходы (`Buffering..`/`Playback ready`/`Player idle`/`Playback ended`), `onRenderedFirstFrame`, ошибки идут в `errorHandler`.
2. **Снапшоты стримов уже инструментированы.** `StreamFrameSnapshotManager` логирует `S0933`/`S0712`/`S0900` + `onPlayerError` (`Timber.w`).
3. **Пробел - только stream-плеер.** Стримы играются через `streamPlaybackListener` (`StreamPlaybackHelper.kt`), который **не логирует переходы состояний** (`onPlaybackStateChanged`/`onIsPlayingChanged` молчат). Ошибки там логируются лишь когда срабатывает recovery (`Timber.w` behind-live/transient/hard-fail). Поэтому **тихий столл** (стрим встал без `PlaybackException`) не оставляет в логе ни следа - что и заблокировало диагностику в S0936.

---

## 5. Предлагаемый подход

Добавить постоянное (не gated) `Timber.d`-логирование переходов состояний в существующий `streamPlaybackListener` - без нового компонента, без изменения логики воспроизведения.

### 5.1 Основные столпы / модули

- Правка одного файла - `StreamPlaybackHelper.kt`, внутри `streamPlaybackListener`. Никаких новых классов/слоёв/DI.

### 5.2 Потоки данных и событий

- `onPlaybackStateChanged` -> `Timber.d` с меткой состояния (`IDLE/BUFFERING/READY/ENDED`) + path.
- `onIsPlayingChanged` -> `Timber.d(isPlaying, path)`.
- Итог: тихий столл виден как «вошёл в BUFFERING / isPlaying=false и не вернулся в READY» - без системного logcat, в обычном Timber-харвесте.

### 5.3 Точки расширяемости

- Метка состояния через маленький private-хелпер (int -> имя); при желании позже туда же добавить rebuffer-счётчик, но это вне текущего объёма.

---

## 6. Открытые вопросы / Research items

Разрешено при расследовании (в рамках S0936):

- Уровень логирования: **постоянный `Timber.d`** (как у главного listener-а). Переходы состояний редки (не per-frame), спама нет - гейт-флаг не нужен.
- Достаточно ли `Player.Listener`: **да** - `onPlaybackStateChanged` + `onIsPlayingChanged` делают тихий столл видимым; `AnalyticsListener`/dropped-frames вне объёма.
- Единый слушатель на все хосты: **не нужен** - главный video-путь и снапшоты уже логируют; правка касается только stream-listener-а.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Лог-спам на hot-path воспроизведения | Средняя | Раздутые харвесты, потеря сигнала | Gated-уровень, дедуп повторяющихся состояний |
| Дубль слушателей на каждый хост | Средняя | Утечки listener-ов (нарушение listener symmetry) | Единый слушатель с симметричным release |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (внутренняя диагностика, не user-visible).

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0936 - зависание трансляции; эта обсервабилити нужна для прямого доказательства момента зависания.

---

## 11. Критерии готовности (strategic-level)

1. Повтор зависания стрима оставляет в Timber-логе явный след (состояние/ошибка/stall).
2. Разбор бага воспроизведения возможен по одному харвесту лога приложения.
3. Логирование не спамит при нормальном воспроизведении.

---

## Last Audit

**Date:** 2026-07-04
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Verified against `StreamPlaybackHelper.kt`:

- §2.1 state transitions: `onPlaybackStateChanged` -> `Timber.d("Stream state=%s reconnecting=%b path=%s", streamStateLabel(..), ..)` (line 135). PASS.
- §2.2 errors: `onPlayerError` logs behind-live / transient / hard-fail via `Timber.w(error, .., path)` (lines 199/216/225). PASS.
- §2.3/§2.4 silent stall diagnosable from one Timber harvest: `state=BUFFERING` + `isPlaying=false` never returning to READY (lines 135/162). PASS.
- §11.3 no spam: permanent `Timber.d` on state transitions only (low-frequency, not per-frame). PASS.
- `streamStateLabel(state)` helper declared (line 263). PASS.
- Debug-tag invariant: zero `Timber.d("S0937:` probe strings (logs are permanent, non-prefixed diagnostics; `// S0937:` are WHY-comments). PASS.
- §8 FEATURES: "Без изменений" (internal diagnostic, not user-visible). EXEMPT.

### Manual / on-device

- [ ] Runtime harvest of the new `Stream state=..` / `Stream isPlaying=..` lines on the next live-stream session - folded into S0936 device-repro, no separate check needed.
