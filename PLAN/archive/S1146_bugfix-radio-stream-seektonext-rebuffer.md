# Спецификация (compact bugfix): S1146 - Рывки/микрофризы живого радио: seekToNext на single-file форсит ре-буферизацию

**Ticket:** S1146
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-22
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-22

**Текст:**

fix: рывки и микрофризы при проигрывании живого радио (DFM AAC) - seekToNext на single-file live-стриме форсит ре-буферизацию

Симптом (со слов владельца): рывки и микрофризы при проигрывании радиостанции DFM в разделе Трансляции.

Устройство: реальный Samsung SM-S731B, Android 16 / API 36, флейвор noLegal. Логи: logs/fastmediasorter_20260721_221710.log (и ранее logs/fastmediasorter_20260721_143538.log).

Эвиденс (лог 221710, реальное устройство):
- 23:21:40 StreamInlineAudioManager: inline audio start - https://dfm-disc90.hostingradio.ru/disc9096.aacp
- 23:23:27 AudioPlaybackService: "seekToNext on single file -> seeking to end"; playbackState=2 (23:23:27) -> =3 (23:23:31) = ~4.6с ре-буфер
- 23:24:02 seekToNext -> 2 -> 3; 23:24:03 seekToNext снова (через 0.27с) -> 2 -> 3 = микрофриз
Каждый seekToNext даёт цикл BUFFERING(2)->READY(3) = слышимый рывок. Срабатывает в фоне повторно.

Корневая причина (по коду, app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt):
- ForwardingPlayer.seekToNext() (313-323): для single-file (mediaItemCount<=1) делает seekTo(duration или Int.MAX_VALUE), чтобы сработал STATE_ENDED и Activity догрузила следующий файл (это осознанный трюк для локальных треков). Для ЖИВОГО радио duration=C.TIME_UNSET -> fallback Int.MAX_VALUE -> seekTo(MAX) на live-стриме сбрасывает буфер и ре-анкорит на live-edge = ре-буферизация (для live STATE_ENDED вообще не наступает, трюк не работает, остаётся только рывок).
- Тот же дефект в seekToPrevious() (325-341).
- skip-команды force-added даже для single-file (isCommandAvailable/getAvailableCommands 346-356), поэтому нотификация/MediaSession/виджет показывают next/prev на радио, и любой такой вызов рвёт поток.

Триггеры seekToNext (в фоне): dispatchCommand("navigation.next_file") <- handleWidgetCommand WIDGET_COMMAND_NEXT (608), нотификация/MediaSession skip, BT/гарнитура. Live-guard делает вызов безвредным независимо от источника.

Асимметрия: видео-плеер уже прячет skip/rewind для стримов (ExoPlayerControlsManager.kt:155-157 hiddenForStream), аудио-сервис - нет.

Направление фикса:
1. В seekToNext()/seekToPrevious() override: если текущий источник - live/unbounded (кандидаты-дискриминаторы: exoPlayer.isCurrentMediaItemLive, либо !exoPlayer.isCurrentMediaItemSeekable, либо явный isStream-флаг от StreamInlineAudioManager) -> no-op (у живого радио нет next/prev; skip не должен ре-буферить live-edge). Точный сигнал для ICY/.aacp прогрессив-стрима проверить на реальном устройстве - isCurrentMediaItemLive может быть false для ICY, тогда надёжнее !isCurrentMediaItemSeekable или явный флаг.
2. Не force-добавлять COMMAND_SEEK_TO_NEXT/PREVIOUS для live-стрима (чтобы нотификация не рисовала skip-кнопки на радио - зеркалить видео-плеер).
3. Регресс: обычные локальные single/multi треки - next/prev работают как раньше (seek-to-end трюк + STATE_ENDED advance).

Проверка (device, реальный SM-S731B с DFM .aacp): играть DFM AAC-радио, свернуть app, дёргать next из нотификации/виджета - без ре-буфера/рывка; локальные треки - переключение файлов работает как прежде. Probe в логе подтверждает live-ветку.

**Вложения:**
- device-лог с воспроизведением рывков (DFM .aacp, реальный SM-S731B) - `PLAN/S1146_bugfix-radio-stream-seektonext-rebuffer/attachments/01__radio-stutter-device-log.log`

---

## 1. Проблема / симптом

Живое радио (DFM, AAC `.aacp` поток) в разделе Трансляции периодически рвётся - слышимые рывки и микрофризы. В логе реального устройства (SM-S731B, noLegal, API 36) каждый рывок совпадает с `AudioPlaybackService: seekToNext on single file -> seeking to end`, за которым идёт цикл `playbackState=2 (BUFFERING) -> =3 (READY)` (первый ~4.6с, дальше короче), в т.ч. дважды за 0.27с. То есть плеер ре-буферизует живой поток без причины.

---

## 2. Корневая причина

`ForwardingPlayer.seekToNext()`/`seekToPrevious()` в `AudioPlaybackService` для одиночного элемента (`mediaItemCount<=1`) выполняют `seekTo(duration.takeIf{it>0} ?: Int.MAX_VALUE)` - трюк «seek в конец -> STATE_ENDED -> Activity грузит следующий файл». У живого потока `duration = C.TIME_UNSET`, поэтому цель = `Int.MAX_VALUE`; `seekTo(MAX)` на live-источнике сбрасывает буфер и ре-анкорится на live-edge = ре-буферизация. Для live `STATE_ENDED` не наступает, «advance» не происходит - остаётся только рывок. Усугубляется тем, что `isCommandAvailable`/`getAvailableCommands` (346-356) force-добавляют `COMMAND_SEEK_TO_NEXT/PREVIOUS` даже для single-file, так что нотификация/MediaSession/виджет показывают skip-кнопки на радио, а любой их вызов рвёт поток. Видео-плеер аналогичный skip для стримов уже прячет (`ExoPlayerControlsManager.kt:155-157` `hiddenForStream`) - в аудио-сервисе такой защиты нет.

---

## 3. Исправление

1. В `seekToNext()`/`seekToPrevious()` override: определить live/unbounded источник и сделать вызов no-op для него (у живого радио нет next/prev, skip не должен ре-буферить live-edge). Дискриминатор выбрать/проверить на реальном устройстве против ICY `.aacp`: `exoPlayer.isCurrentMediaItemLive`, либо `!exoPlayer.isCurrentMediaItemSeekable`, либо явный `isStream`-флаг, прокинутый от `StreamInlineAudioManager` (ICY-прогрессив может не помечаться live -> `!isCurrentMediaItemSeekable`/явный флаг надёжнее).
2. Не адвертайзить `COMMAND_SEEK_TO_NEXT/PREVIOUS` для live-источника, чтобы нотификация/MediaSession не рисовали skip на радио (зеркалить логику стрим-гейтинга видео-плеера).
3. Точный триггер повторных вызовов в фоне (виджет NEXT / нотификация / BT) допинить при реализации, но live-guard обезвреживает независимо от источника.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1118 (radio buffer tolerance), S1137 (AAC-радио renderer fix), S1131 (stream stall recovery), S1083 (stream playback controls)
- **Flavor:** src/main (AudioPlaybackService) - все флейворы со стрим-аудио (standard, noLegal); поведение гейтится живостью источника, не флейвором
- **UI/поведение:** без новых строк; на живом радио skip next/prev в нотификации/виджете становятся no-op и не адвертайзятся; локальные треки и очередь - без изменений

---

## 4.1 Полевые данные 2026-07-22

Логи `logs/fastmediasorter_20260722_032819.log` и `logs/fastmediasorter_20260722_100403.log` (реальный SM-S731B, v2.60.7220.230): во всех окнах радио НИ ОДНОГО `seekToNext`/`seekToPrevious` и ни одной пары `playbackState 2->3` во время игры - ре-буферизация по skip устранена. Probe `S1146:` не сработал, т.к. владелец skip не нажимал - целевой сценарий status note (skip из нотификации/виджета) остаётся к проверке. Остаточные рывки владельца имеют ДРУГОЙ механизм - вынесены в S1148 (пауза чтения сокета из-за min<max буфера -> server-side drop).

## 4. Проверка

On-device (реальный SM-S731B, DFM `.aacp`): играть радио, свернуть приложение, дёргать next/prev из нотификации/виджета - воспроизведение НЕ ре-буферизуется и не рвётся (`playbackState` держится `=3`, нет пары `2->3`); skip-кнопки на радио отсутствуют/no-op. Регресс: локальный одиночный трек и очередь - next/prev переключают файлы как раньше. Probe `S1146:` подтверждает прохождение live-ветки no-op.
