# Спецификация (compact bugfix): S1137 - FfmpegAudioRenderer падает на AAC радио-потоках (noLegal)

**Ticket:** S1137
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-21
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-21

**Текст:**

Автозахват при анализе логов (/log-reader). AudioPlaybackService фатально
падает при воспроизведении сетевых аудио/радио-потоков: FfmpegAudioRenderer не
может декодировать AAC (mp4a.40.2 и AAC+/aacp), сервис останавливается.
Повторяется 4 раза за сессию. Флейвор noLegal (FFmpeg .so - on-demand),
устройство SM-S731B, Android 16 / API 36, arm64. Похоже связано с ранее
закрытыми S0386/S0923 (native-attach FFmpeg на API36), но там статус Archived -
дефект воспроизводится на свежих сборках 2.60.7200.

**Эвиденс (лог `logs/fastmediasorter_20260720_053446.log`):**

```
[2669] 21:25:49 I/App: StreamInlineAudioManager: inline audio start - https://dfm-disc90.hostingradio.ru/disc9096.aacp (bg=true)
[2670] 21:25:49 D/App: AudioServiceController: playAudioWithMetadata uri=https://dfm-disc90.hostingradio.ru/disc9096.aacp title=DFM ... artist=null
[2671] 21:25:49 D/App: AudioPlaybackService: playbackState=2
[2672] 21:25:55 D/App: AudioPlaybackService: playbackState=3
[2675] 21:25:58 E/App: AudioPlaybackService: fatal playback error - stopping service
[2676] androidx.media3.exoplayer.ExoPlaybackException: FfmpegAudioRenderer error, index=2,
       format=Format(0, null, null, audio/mp4a-latm, mp4a.40.2, -1, null, [-1, -1, -1.0, null], [2, 44100]), format_supported=YES
       at androidx.media3.exoplayer.ExoPlayerImplInternal.handleMessage(ExoPlayerImplInternal.java:608)
       Caused by: androidx.media3.decoder.ffmpeg.FfmpegDecoderException: Error decoding (see logcat).
```

Повторы: 21:05:12, 21:06:39, 21:25:58, 21:26:50 (тот же стек). Также 15
FfmpegDecoderException в `logs/fastmediasorter_20260717_234437.log`.

Замечание: `format_supported=YES` и рендерер выбран (index=2) - .so загружен, но
декод падает в рантайме. Это отличается от «native-attach broken» (там рендерер
вообще недоступен). Возможная причина - HE-AAC/SBR (AAC+) в потоке `.aacp` либо
специфика сборки FFmpeg-декодера на API36.

---

## 1. Проблема / симптом

AudioPlaybackService получает фатальную `ExoPlaybackException` от
FfmpegAudioRenderer при декодировании AAC-потоков (mp4a.40.2, AAC+/aacp) и
останавливает сервис - радио-воспроизведение обрывается. Флейвор noLegal,
Android 16 / API 36, arm64 (SM-S731B). Воспроизводится стабильно на нескольких
радио-URL.

---

## 2. Корневая причина

Общая фабрика рендереров `createPlaybackRenderersFactory`
(`ui/player/helpers/PlaybackRenderersFactory.kt`) строит `DefaultRenderersFactory`
с `EXTENSION_RENDERER_MODE_PREFER`. Это ставит FFmpeg-аудиорендерер **впереди**
платформенного MediaCodec для всех форматов, которые FFmpeg заявляет
поддерживаемыми (`format_supported=YES`), включая AAC-LC (mp4a.40.2) и AAC+
(aacp/HE-AAC). Для этих радио-потоков рантайм-декод в FFmpeg падает
(`FfmpegDecoderException: Error decoding`), а рантайм-ошибка декодера - фатальна
и **не** покрывается `setEnableDecoderFallback(true)` (fallback срабатывает
только на этапе инициализации декодера, не в середине потока). Итог -
`ExoPlaybackException`, `AudioPlaybackService` останавливает сервис.

Ключевой факт: FFmpeg-расширение доставляется/бандлится **ради DTS** (Set D /
`FFMPEG_DTS`, см. комментарий S0386 Phase 07 в файле), а не ради AAC. AAC/MP3/
HE-AAC/FLAC/Opus платформенный MediaCodec на minSdk 26+ декодирует надёжно.
`_PREFER` заставляет FFmpeg перехватывать общие форматы без всякой выгоды - это
и есть дефект. FFmpeg-расширение Media3 - audio-only, поэтому режим влияет
только на выбор аудиодекодера; видео всегда идёт через MediaCodec.

---

## 3. Исправление

Заменить `EXTENSION_RENDERER_MODE_PREFER` на `EXTENSION_RENDERER_MODE_ON` в
`createPlaybackRenderersFactory`. При `_ON` платформенные MediaCodec-рендереры
добавляются **первыми**, а FFmpeg-расширение - после и используется только для
форматов, которые платформа не тянет (DTS и пр.). Итог:

- AAC/AAC+/MP3/распространённое аудио -> MediaCodec (надёжно, аппаратно).
- DTS и экзотика -> FFmpeg (возможность Set D сохраняется полностью).
- `setEnableDecoderFallback(true)` остаётся - init-fallback между декодерами не
  теряется.

Правка - одна строка в общей фабрике; охват - все хосты воспроизведения
(audio-service, cloud, ftp, smb, sftp, player, stream), т.к. все зовут
`createPlaybackRenderersFactory`. Видео не затрагивается.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0386 (Archived), S0923 (Archived) - native-attach FFmpeg API36
- **UI/поведение:** без изменений UI; меняется только выбор аудиодекодера под капотом. Пользователь-видимый эффект - радио/аудио перестаёт падать.
- **Flavor:** src/main - все флейворы (standard бандлит FFmpeg, noLegal доставляет on-demand). Дефект воспроизводился на noLegal, но код общий.

---

## 4. Проверка

- Compile: `a.ps1 dq` (standard debug) - PASS.
- On-device (device-gated, требует устройство/эмулятор): воспроизвести
  `https://dfm-disc90.hostingradio.ru/disc9096.aacp` и AAC-поток на noLegal/API36 -
  аудио играет, `AudioPlaybackService: fatal playback error` в логе нет.
- Регресс DTS: воспроизвести DTS-видео при установленном Set D (FFMPEG_DTS) -
  звук по-прежнему декодируется через FFmpeg.

---

## Last Audit

### Manual / on-device

- [ ] AAC/HE-AAC radio stream plays with no `AudioPlaybackService: fatal playback error` - **INCONCLUSIVE 2026-07-23**: blocked, see below.
- [ ] S1137 probe (`extension mode=ON`) fires at renderer-factory creation - **INCONCLUSIVE 2026-07-23**: probe never fired (no playback reached).
- [ ] DTS video via FFmpeg (Set D) still decodes - SKIPPED (out-of-scope: Set D not installed on emulator; needs DTS fixture).

Run: `/spec-test-device` on emulator-5554 (standard-debug, x86_64, Android 15/API35), APK 2.60.7220.314-DEBUG.
Expected: open Streams -> play an AAC `.aacp` radio channel -> audio plays, no FFmpeg fatal, S1137 probe in log.
Actual: **StreamsActivity crashes on open** before any channel can play - `UninitializedPropertyAccessException: lateinit property inlineAudio has not been initialized` (StreamsActivity.kt:326 <- StreamInlineAudioManager init, StreamInlineAudioManager.kt:146/150). The `onNowPlayingChanged` lambda dereferences `inlineAudio` before its assignment at line 309 completes; StreamInlineAudioManager's init collects a StateFlow that emits synchronously and fires the lambda during construction.
Blocker is **concurrent S1141/S1142 WIP** (now-playing on grid tiles) in the uncommitted working tree the APK was built from - NOT the S1137 decode path. S1137's own fix is present and correct in source (`EXTENSION_RENDERER_MODE_ON` + probe at PlaybackRenderersFactory.kt:33), but on-device AAC-radio verification is unreachable while StreamsActivity crashes. Re-run `/spec-test-device S1137` on a tree without the S1141/S1142 init-ordering crash.
Log: no `S1137:` probe, no `FfmpegDecoderException`/`AudioPlaybackService fatal` (nothing played). Evidence: temp/S1137/mobile_test_scenario_20260723_1822.md, temp/S1137/run_20260723_1822.log, temp/S1137/screens/step_03_streams_crash.png.

## Revision History

- **2026-07-23** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 15/API35)
  - Scenario: temp/S1137/mobile_test_scenario_20260723_1822.md · PASS/FAIL/INCONCLUSIVE 0/0/2, SKIPPED 1 · Errors in log: 1 (StreamsActivity open crash, S1141/S1142 WIP - not S1137)
  - Verdict INCONCLUSIVE: S1137 fix present in source; AAC-radio path blocked by concurrent StreamsActivity init crash.

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 32 times: `playback renderers factory - extension mode=ON (MediaCodec preferred for AAC)`.
- The exact stream named in the status note was played: `dfm-disc90.hostingradio.ru/disc9096.aacp`, on noLegal / API 36. No AudioPlaybackService fatal, no FFmpeg crash - the original defect did not reproduce.
- Weak point: that session logged repeated `Audio diag: load error .. SocketTimeoutException` on the tester's mobile link, so sustained successful playback is not proven, only the absence of the crash.
