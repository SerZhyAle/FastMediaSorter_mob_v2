# Спецификация (compact bugfix): S0936 - Трансляция замерзает: у stream-пути нет stall-watchdog (тихий столл без ошибки неисправим)

> Примечание: слаг файла (`native-heap-snapshot`) - от исходной гипотезы, опровергнутой при расследовании (см. §2). Переименование префикса запрещено (Rule 12); авторитетно - содержание §2/§3.

**Ticket:** S0936
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)
**Tactical plan:** `PLAN/S0936_bugfix-stream-freeze-native-heap-snapshot/INDEX.md`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

**Текст:**

> подвисла трансляция.. прям сильно - просто перестала идти

Найдено при анализе лога `logs/fastmediasorter_20260704_055031.log` (сборка 2.60.7040.458-NoLegal-DEBUG, SM-S731B, Android 16 / API 36, RAM 7204 MB, heapMax 512 MB).

Стрим: живой IPTV HLS `https://api-tv.ipnet.ua/api/v1/manifest/2118742539.m3u8`. Запуск 14:07:23, `ExoPlayer released` 14:09:06 после того, как пользователь сам тапнул выход. Плеер НЕ крашнулся - завис, юзер вышел.

**Нативная куча исчерпана ещё до старта плеера:**

```
14:07:24 MEM_PROBE PRE_PLAY   heap=37MB/512MB  native=136MB/167MB (free=26MB)
14:07:24 W VideoPlayerManager: native heap low before playback - free=26MB, running Glide eviction + GC
14:07:24 I VideoPlayerManager: native heap after GC - free=26MB   <- GC не освободил НИЧЕГО
```

Тренд нативной кучи по сессии: 7MB (старт) -> 25MB (main drawn, 12:17) -> **136MB** (перед плеером, 14:07).

**Источник скачка 25 -> 136 MB.** За ~40 с до плеера, в 14:06:43, при входе в список стримов пайплайн превью-снапшотов декодировал ~28 живых потоков разом и не отдал нативную память декодеров - `StreamFrameSnapshotManager` (теги в логе):

```
S0700: snapshot request enqueued ..   (~28 стримов)
S0933: TextureView capture start ..   (живой декод каждого)
S0712: persist captured frame ..
S0900: capture skipped, grid left     (часть отменена при выходе из грида)
```

**Условия воспроизведения.** Плеер стартовал с free=26MB нативной кучи на нестабильной мобильной сети: весь сеанс `rmnet0`, `NetworkStateMonitor: Link properties changed - rmnet0` в 14:07:58 / 14:08:07 / 14:08:08 / 14:08:17 - прямо в окне воспроизведения. Живой HLS + исчерпанная нативная куча + дёрганая мобильная сеть -> декодер/загрузка сегментов встают, а живой поток сам не восстанавливается (край live-окна уходит).

**Дефект в guard-е.** `VideoPlayerManager` проверяет нативную кучу перед playback и пытается `Glide eviction + GC`, но GC нативную память декодеров/битмапов не освобождает (`after GC - free=26MB`). Guard обнаружил проблему и всё равно пошёл играть.

Ограничение эвиденса: это Timber-экспорт, в нём нет системных MediaCodec/Choreographer/ANR и нет состояний ExoPlayer (см. смежный S0937). Точный момент зависания подтверждается только косвенно; для прямого доказательства нужен полный logcat при repro.

---

## 1. Проблема / симптом

Живой IPTV-стрим (HLS) замерзает во время воспроизведения и не восстанавливается сам - пользователь вынужден выйти вручную («просто перестала идти»). Воспроизведение шло на нестабильной мобильной сети. Ключевое (см. §2): stream-путь восстанавливается только по `PlaybackException`; тихий столл без ошибки не ловится, watchdog-а прогресса у стримов нет. Native-heap версия из §0 при расследовании опровергнута.

---

## 2. Корневая причина

Уточнено чтением кода (исходная гипотеза §0 про снапшот-пайплайн **опровергнута**):

1. **Снапшот-пайплайн не виноват.** `StreamFrameSnapshotManager`: `MAX_CONCURRENT_CAPTURES = 1` (Semaphore), каждый захват релизит плеер+сурфейс в `finally` (`setVideoTextureView(null)` + `release()` + `removeView`). ~28 строк `S0700` в логе - лишь *enqueue*; реально сработали 2-3 (`S0933`/`S0712`), остальное - `S0900: capture skipped, grid left`. Balloon native 25->136MB был за ~2 часа обычного юзажа (25MB - это 12:17, стрим - 14:07), не от пайплайна. Native heap - вероятно red herring.
2. **Стримы идут мимо зрелого listener-а.** Обычное видео - `VideoPlayerManager.playerListener` (health-check, retry, decoder-tracking, логирует Buffering/Ready). Стримы - `playStreamVideo` + `streamPlaybackListener` (`StreamPlaybackHelper`). В логе стрима нет ни одного лога главного listener-а -> путь другой.
3. **У stream-listener-а есть error-driven recovery, но нет stall-watchdog.** `streamPlaybackListener.onPlayerError` лечит `ERROR_CODE_BEHIND_LIVE_WINDOW` (seek-to-live + prepare, ×3) и transient/timeout/429/5xx (`isRecoverableStreamError`, backoff-retry ×4). Но всё это триггерится **только** `onPlayerError(PlaybackException)`.
4. **Тихий столл неисправим.** Если стрим застревает (позиция не растёт / вечный BUFFERING) **без** `PlaybackException`, ни одна ветка recovery не срабатывает - и watchdog-а прогресса у stream-пути нет.
5. **Существующий watchdog структурно неприменим к стримам.** `PlaybackHealthHelper.startPlaybackHealthCheck` (поллинг дельты `currentPosition`): (a) гейтед на локальные аудио `.flac/.ac3/.eac3/.wv`; (b) фоллбек - только `MediaPlayer` для локальных файлов (не http-live); (c) вызывается лишь из главного `playerListener`, не из stream-listener.
6. **Эвиденс согласуется (но не прямой).** После `prepared` (14:07:24) до релиза (14:09:06) - ни одного error-recovery warning (`Timber.w` behind-live/transient). Значит `PlaybackException` не было -> столл был тихий -> неисправимый. Прямого подтверждения нет: stream-listener не логирует состояния (ровно пробел S0937), поэтому «тихий столл» - сильный инференс, не факт.

---

## 3. Исправление

Дизайн (не «минимальный» фикс - новый компонент/жизненный цикл; при разблокировке провести через `/spec-tech`, Full):

- Добавить **stall-watchdog в stream-путь**: после `STATE_READY` поллить прогресс `currentPosition` (паттерн из `PlaybackHealthHelper`); если позиция не растёт N поллов при `playWhenReady`, **или** `STATE_BUFFERING` держится дольше порога без `READY` -> вызвать существующее восстановление (`seekToDefaultPosition()` + `prepare()`) в рамках отдельного bounded-budget.
- **Переиспользовать** recovery-машинерию `streamPlaybackListener` (re-anchor + re-prepare), не изобретать новую.
- Отменять watchdog на `release()`/`onDestroy()` (listener symmetry); не ложно-срабатывать на легитимном долгом буфере (учитывать `isLoading`/рост буфера, а не только позицию).
- **Вне объёма:** native-heap guard в `VideoPlayerManager` (GC не освобождает native alloc) - трогать только если инструментал S0937 покажет OOM-природу столла, а не сетевую.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0937 (**блокер** - stream-path инструментал; подтверждает тихий столл и даёт способ верифицировать watchdog), S0935 (соседний дефект из того же лога)
- **Flavor:** все (stream-путь в `src/main`, общий для флейворов со streams-капабилити)
- **UX-решение (owner input при реализации):** пороги столла и агрессивность авто-восстановления (как быстро дёргать re-prepare) vs риск прерывания здорового стрима; показывать ли «reconnecting» на watchdog-recovery так же, как на error-recovery.
  - **Ратифицировано владельцем 2026-07-04:** оставить дефолты Phase 01 - 3 подряд стоящих полла по 3с (~9с до детекта «позиция замёрзла»), 15с таймаут BUFFERING-без-READY, отдельный бюджет 3 попытки re-anchor перед сдачей в error-путь. Watchdog-recovery показывает тот же лейбл «Reconnecting..» (`StreamWaitPhase.RECONNECTING`), что и error-driven recovery - без отдельного лейбла. Не снимает блокер device-repro (§4, п.2) - подтверждение формы столла на устройстве остаётся обязательным перед Phase 02.

---

## 4. Проверка

1. **Предпосылка:** S0937 (логирование состояний stream-плеера) реализован - тогда repro фриза на устройстве покажет тихий столл в логе (BUFFERING без READY / стоящая позиция). **PASS** - S0937-инструментал в коде, логи `Stream state=..`/`Stream isPlaying=..` работают.
2. **Device repro:** живой HLS на дёрганой мобильной сети -> спровоцировать столл -> watchdog ловит застой, re-anchor + re-prepare, воспроизведение возобновляется **без** ручного выхода. **PASS** (2026-07-11, emulator-5556, live HLS «1+1 International», throttle: сотовая=GSM + wifi off):
   - Repro тихого столла (до Phase 02): BUFFERING 19:10:30 -> `Stream stall detected (buffering timeout)` 19:10:45 (+15 c), ноль `PlaybackException` - столл тихий, error-recovery не сработал бы.
   - Recovery (Phase 02): столл 19:25:43 -> re-anchor attempt 1 (19:25:58) и attempt 2 (19:26:13) при мёртвой сети -> сеть вернулась 19:26:26 -> READY+isPlaying 19:26:32 - стрим ожил сам.
   - Исчерпание бюджета (сеть мертва > 3 попыток): 3 attempt-а по +15 c -> `watchdog budget exhausted` -> существующий диалог «Трансляция недоступна» с «Повторить» (19:23:28) - не вечный спин и не тихая заморозка.
3. **Регресс:** здоровый стрим и легитимная долгая буферизация watchdog-ом **не** прерываются. **PASS** - многоминутный здоровый плейбек (2 сессии) без единого срабатывания; буферизация с прогрессом (isLoading + рост буфера) re-arm-ится без recovery.

---

## Last Audit

- **Date:** 2026-07-11
- **Verdict:** Verified
- **Scope:** stream-путь: тихий столл (без PlaybackException) детектится и лечится bounded re-prepare.
- **Changed (Phase 02):** `StreamStallWatchdog.kt` (обе детекции -> `recoverFromStreamStall`: budget-guard, RECONNECTING, `stop()`+`prepare()` - в отличие от onPlayerError-пути плеер НЕ в IDLE, голый `prepare()` был бы no-op; non-live restore позиции; исчерпание -> `onPlaybackError` c синтетическим `ERROR_CODE_TIMEOUT`), `VideoPlayerManager.kt` (+`streamWatchdogRecoveries`/`streamWatchdogReconnecting`), `StreamPlaybackHelper.kt` (сброс бюджета на session-start и на confirmed READY; RECONNECTING-лейбл при watchdog-recovery).
- **Evidence:** fk/db PASS; on-device (emulator-5556) полный цикл - см. §4. Watchdog-бюджет отдельный от error-driven бюджетов; сброс только на confirmed READY (инвариант против вечного спина).
- **Residual:** пороги (3x3 c poll, 15 c buffering, budget 3) - ратифицированные дефолты; тонкая подстройка возможна по полевой телеметрии. Repro на эмуляторе (санкционировано status note); органический real-device фриз добавит уверенности, но форма столла и механика восстановления подтверждены.
