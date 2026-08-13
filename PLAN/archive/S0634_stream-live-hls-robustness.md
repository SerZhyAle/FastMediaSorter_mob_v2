# Стратегическая спецификация: S0634 - Устойчивость live-HLS (восстановление вместо «удалить канал»)

**Ticket:** S0634
**Status:** Archived
**Priority:** 70
**Date:** 2026-06-22
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-22
**Tactical spec:** `PLAN/S0634_stream-live-hls-robustness/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Draft-инбокс. Доработать через `/spec` / `/spec-update`.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-22

**Текст владельца (вербатим):**

> надо как то улучшить прием вилддеоканалов - программа всю дорогу предлагает их удалить, но это
> неправильно - нужно больше буферезировать ии делать адаптивный битрейт - изучи

**Источник:** `logs/fastmediasorter_20260622_224653.log` - две разные live-HLS трансляции упали с
`BehindLiveWindowException` (`HlsChunkSource.getNextChunk`):
- `https://api-tv.ipnet.ua/api/v1/manifest/2118742539.m3u8`
- `https://dash2.antik.sk/live/.../playlist.m3u8`

**Research (workflow wr7n4x01l, 6 агентов, сверено по media3 1.2.1 sources):**

Корень: на live-HLS плейхед отстаёт от скользящего окна манифеста (нужный сегмент уже выпал) ->
`ExoPlaybackException` с `errorCode == ERROR_CODE_BEHIND_LIVE_WINDOW` (= 1002, проверено по
`media3-common-1.2.1-sources.jar/PlaybackException.java:106`; символа `ERROR_CODE_IO_BEHIND_LIVE_WINDOW`
в 1.2.1 НЕТ, 2002 - это network-timeout). В `StreamPlaybackHelper.streamPlaybackListener.onPlayerError`
(`StreamPlaybackHelper.kt:128-143`) ретраятся только `IO_NETWORK_CONNECTION_FAILED/TIMEOUT`; 1002
проваливается в `playerCallback.onPlaybackError` -> `PlayerPlaybackCallbackImpl` -> `onStreamPlaybackFailed`
(red-статус S0593 + диалог S0581 «повторить / удалить»). Рутинный live-edge desync помечается как мёртвый
канал.

Почему «просто больше буфера» - не главное и местами вредно: длину live-окна задаёт сервер (IPTV-релеи
~18-60s). Текущий `maxBuffer = 60s` уже сопоставим с целым окном; больший буфер держит хвост ближе к
истекающему сегменту -> 1002 ЧАЩЕ. LoadControl шарится с radio/VOD (латентность переключения + RAM).
На low-RAM API 26 60s высокобитрейтного варианта без байт-капа -> OOM-давление.

Рекомендации (P0 - главное):
- **P0 BehindLiveWindow recovery:** в `onPlayerError`, ПЕРЕД transient-веткой - при
  `errorCode == ERROR_CODE_BEHIND_LIVE_WINDOW` (1002) и `exoPlayer?.isCurrentMediaItemLive == true`:
  `seekToDefaultPosition()` ЗАТЕМ `prepare()`, затем `return` (до `onPlaybackError`, иначе ложный RED).
  Один `prepare()` НЕ лечит (переподготовка на устаревшей позиции). Кап `behindLiveRecoveries = 0..3`,
  сброс ТОЛЬКО в `STATE_READY` (не в BUFFERING - иначе кап обнуляется). Линейный backoff
  `min(n*1000, 5000)ms`.
- **P1 bounded retry + классификация:** заменить булев `transientRetryDone` на счётчик (MAX 4,
  backoff 2/4/8/16s, сброс в READY). Recoverable: 1002/1003/2000/2001/2002 + 2004 при 5xx/429.
  Hard-fail (диалог сразу): 2003/2005/2006/2007/2008/3001-3004 + 2004 при 4xx (через
  `HttpDataSource.InvalidResponseCodeException.responseCode`). Для live transient 2001/2002 тоже
  `seekToDefaultPosition()` перед `prepare()`. Для non-live (радио) поведение не менять.
- **P2 LiveConfiguration + буфер:** только на non-RTSP `MediaItem.fromUri` ветке (`:77`) -
  `setLiveConfiguration(targetOffset 10s, min 4s, max 20s, maxPlaybackSpeed 1.02)` (НЕ пинить speed в 1.0).
  Буфер УМЕНЬШИТЬ: `setBufferDurationsMs(15_000, 30_000, 2_500, 5_000)`. Оговорка: LoadControl общий с
  VOD/radio - при регрессе VOD выделить live-only LoadControl.
- **P3 adaptive bitrate:** уже работает из коробки (`DefaultTrackSelector` + `AdaptiveTrackSelection.Factory`).
  Для single-variant `.m3u8` (вероятный случай ipnet/antik) ABR = no-op. НЕ заявлять владельцу «добавили
  adaptive bitrate как фикс этих 1002». Опц. tuning только для multi-variant.

Точки вставки: `StreamPlaybackHelper.kt` - `onPlayerError` (`:128-143`), `STATE_READY` сброс (`:99-103`),
MediaItem-билд (`:77`), LoadControl (`:51-54`). НЕ трогать `VideoPlayerErrorHandler.kt` (только file-playback).

Не сломать: VOD seek (гейт `isCurrentMediaItemLive`), progressive radio (single-variant, ветка не
срабатывает, ICY не трогать), RTSP (`setMediaSource`, ни LiveConfiguration ни seek), S0593 OK/FAIL
(recovery делает `return` до outcome-recorder).

Scope-gaps (отдельные драфты, не чинить здесь): reconnect для progressive-audio (radio) при обрыве;
аналогичное восстановление для RTSP.

**Вложения:** полный синтез + adversarial-критика workflow -
`temp/claude/.../tasks/wr7n4x01l.output` (на машине разработчика).

---

## 1. Проблема

Live-HLS видео-трансляции при рутинной рассинхронизации с «живым краём» (`BehindLiveWindowException`,
errorCode 1002) трактуются как мёртвые: приложение пишет красный статус и предлагает удалить канал,
вместо тихого восстановления к live-краю. Владелец: «удалять - неправильно».

---

## 2. Цели

1. При `BehindLiveWindow` (1002) на live-потоке плеер тихо ре-анкорится к live-краю, без диалога «удалить».
2. Диалог «повторить / удалить» (S0581) показывается только при действительно невосстановимых ошибках
   (hard-fail коды) или после исчерпания ограниченного числа попыток восстановления.
3. Восстановленный поток не помечается красным (S0593 OK).

**Non-goals:**

- UI-профиль контролов трансляции (это S0631).
- Reconnect для progressive-audio (radio) и RTSP - отдельные драфты.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. «Больше буферизировать и адаптивный битрейт» - выполнить по сути (устойчивость), но с честной
   оговоркой: буфер для live скорее уменьшить, ABR уже включён и для single-variant бесполезен;
   главный рычаг - recovery.

### 3.2 Жёсткие ограничения

- **Flavor:** где включены трансляции (`SUPPORT_STREAMS`).
- **API level:** `isCurrentMediaItemLive`, `seekToDefaultPosition`, `LiveConfiguration` - доступны в media3 1.2.1.
- **Производительность:** не раздувать буфер (OOM на low-RAM API 26); LoadControl общий с VOD/radio.
- **Локализация:** EN/RU/UK для любых новых строк.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0581 (диалог недоступного стрима), S0593 (статус OK/FAIL), S0631 (UI-профиль), S0635 (cast).

---

## 6. Открытые вопросы / Research items

1. Число вариантов в мастер-плейлистах падающих URL (multi- vs single-variant) - определяет реальную
   пользу P3 ABR-tuning. Проверить перед обещанием владельцу.
   - **Статус:** Deferred. P3 кодом не трогали: ABR работает из коробки (`DefaultTrackSelector` +
     `AdaptiveTrackSelection.Factory`), для single-variant `.m3u8` бесполезен. Владельцу ABR как фикс
     1002 не заявляем. Реальный рычаг - P0 recovery, он реализован.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Бесконечный re-prepare на мёртвом окне | Высокая | Спиннер навсегда, CPU/сеть-спин | Кап N=3, сброс только в READY, backoff |
| Неверная константа (2002 вместо 1002) | Средняя | Хайджек network-timeout ветки | Реализовать строго против `ERROR_CODE_BEHIND_LIVE_WINDOW` (1002) |
| Ложный RED на восстановленном потоке | Средняя | S0593 мигает красным | recovery `return` до `onPlaybackError` |
| Регресс VOD seek | Средняя | Прыжок VOD на старт | Гейт `isCurrentMediaItemLive` |

---

## 8. Реализация

Один файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`.

- P0 - в `onPlayerError`, до transient-ветки: при `ERROR_CODE_BEHIND_LIVE_WINDOW` и не-RTSP -
  `seekToDefaultPosition()` затем `prepare()` с `return` до `onPlaybackError` (нет ложного RED). Кап
  `behindLiveRecoveries = 3`, линейный backoff `min(n*1000, 5000)ms`, сброс только в `STATE_READY`.
- P1 - булев `transientRetryDone` заменён на счётчик `transientRetries` (кап 4, экспонента 2/4/8/16s,
  сброс в READY). Классификатор `isRecoverableStreamError`: recoverable 1003/2000/2001/2002 + 2004 при
  429/5xx (`HttpDataSource.InvalidResponseCodeException.responseCode`); остальное hard-fail -> диалог.
  Для live (не-RTSP) перед `prepare()` тоже `seekToDefaultPosition()`.
- P2 - на ветке `MediaItem.Builder().setLiveConfiguration(...)`: targetOffset 10s, min 4s, max 20s,
  maxPlaybackSpeed 1.02. Буфер уменьшен до `15_000, 30_000, 2_500, 5_000` (общий LoadControl, оговорка
  про live-only split при регрессе VOD).
- P3 - не реализован (см. §6.1).

Не тронуты: `VideoPlayerErrorHandler.kt`, RTSP `setMediaSource` (ни LiveConfiguration ни seek), ICY-радио.

Debug-теги (`Timber.d("S0634:`): два - behind-live recovery и transient recovery. Снять при выходе из
`BlockNeedUserTest`.

Валидация: `.\a.ps1 fk` -> BUILD SUCCESSFUL; neuroslop/deprecated-pm/ticket-log гейты - дельта 0.

---

## 10. Связи с другими спеками

S0581, S0593, S0631 (UI-профиль, параллельно), S0635 (cast). От них не блокируется.
