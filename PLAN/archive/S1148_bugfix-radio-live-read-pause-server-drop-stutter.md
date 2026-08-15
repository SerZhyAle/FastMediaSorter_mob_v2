# Спецификация (compact bugfix): S1148 - Рывки живого радио без ре-буферизации: пауза чтения сети (min<max буфер) провоцирует server-side drop

**Ticket:** S1148
**Status:** Archived
(MP3 48 kHz) must be compared with `/jazz_opus`; logcat must contain
`S1148: ICY metadata disabled`. Current-track ICY text is intentionally absent in this diagnostic build.
**Priority:** 90
**Date:** 2026-07-22
**Tier:** 3 - Moderate (ad-hoc)

---

## 1. Проблема / симптом

Владелец: рывки и микрофризы при проигрывании живого радио (DFM `.aacp`, NRK Jazz mp3) сохраняются после фикса S1146. Логи `logs/fastmediasorter_20260722_032819.log` и `logs/fastmediasorter_20260722_100403.log` (реальный SM-S731B, noLegal, API 36):

- Ни одного `seekToNext` и ни одной пары `playbackState=2 -> 3` во время воспроизведения - фикс S1146 сработал, ре-буферизации нет.
- `playbackState` держится `3` на всём окне воспроизведения, ошибок нет - рывки происходят НИЖЕ уровня стейтов ExoPlayer и невидимы текущему логированию.

## 2. Первичная гипотеза (опровергнута полевыми тестами)

S1118 ввёл `RadioStreamBufferConfig`: `MIN_BUFFER_MS=12_000`, `MAX_BUFFER_MS=24_000`. До S1118 оба радио-плеера (сервисный и in-app) работали на дефолтном `DefaultLoadControl` ExoPlayer, где `min == max == 50_000` - чтение сокета непрерывное. Владелец подтверждает: на дефолтах рывков не было.

Механика дефекта при `min < max`:
1. `DefaultLoadControl.shouldContinueLoading` - гистерезис: набрал `max` (24с) -> loader перестаёт читать сокет; чтение возобновляется только когда буфер стёк до `min` (12с). Итог - циклы по ~12 секунд паузы чтения.
2. Живое радио отдаётся со скоростью 1x. Пока клиент не читает, per-client очередь Icecast/SHOUTcast-релея переполняется; типовое поведение релея для отстающего клиента - выбросить отставшие данные / пересадить на live-edge (а не разорвать соединение).
3. Клиент возобновляет чтение и получает поток с выброшенным куском. ADTS/MP3-кадры самодостаточны - экстрактор молча продолжает: ни ошибки, ни `STATE_BUFFERING`, только слышимый рывок. Периодичность рывков ~= периоду цикла (max-min).

Итерации 2-7 показали, что эффект сохраняется на заводском `DefaultLoadControl`, при стабильном
буфере и при принудительном AOSP-декодере. Следовательно, эта гипотеза не является корневой причиной.

Дополнительная слепая зона: в аудио-цепочке нет `AnalyticsListener.onAudioUnderrun` - underrun'ы AudioTrack не видны в логах вообще.

## 3. Исправление

1. `RadioStreamBufferConfig`: `MIN_BUFFER_MS = MAX_BUFFER_MS = 50_000` - непрерывное чтение сокета, как на дефолтах ExoPlayer при запуске функционала. Быстрый старт сохранён: `BUFFER_FOR_PLAYBACK_MS=4_000`, `AFTER_REBUFFER=6_000` (ценность S1118 - толерантность и retry-слой - не трогается).
2. Постоянная диагностика: `AnalyticsListener.onAudioUnderrun` в обоих владельцах плеера (`AudioPlaybackService`, `StreamInlineAudioManager` OFF-mode) - `Timber.w` c размером буфера и elapsed (без ticket-id, permanent-log gate).
3. Probe (BNUT): `Timber.d("S1148: ...")` в `createLoadControl` - подтверждает, что новые значения реально применяются.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1118 (ввёл 12/24с буфер - корень), S1146 (seekToNext rebuffer - исправлен, подтверждено логом), S1137 (renderer), S1131 (stall recovery)
- **Flavor:** src/main - все флейворы со стрим-аудио; поведение не гейтится флейвором
- **UI/поведение:** без новых строк и UI; только тайминги буферизации и новые диагностические логи

## 3.5 Итерация 2 (2026-07-22, после полевого теста)

Лог `logs/fastmediasorter_20260722_102842.log` (v2.60.7221.023, фикс min=max активен - probe сработал): рывки сохраняются, при этом НЕТ underrun'ов, НЕТ ошибок, `playbackState` держится 3 во всех окнах. Вывод: ExoPlayer подаёт звук непрерывно со своей точки зрения; слышимые прыжки живут в слоях без логирования. Добавлена постоянная диагностика в `AudioPlaybackService` (`Audio diag:`): decoder init (какой декодер реально выбран после S1137), mid-stream format change (SBR/sample-rate), position discontinuity, playWhenReady+reason (фокус), playback suppression (transient focus pause при READY), volume (duck), media load started/error (тихие переоткрытия сокета - для ICY второй `load started` той же станции = upstream drop), sink/codec error. Плюс probe-телеметрия `S1148: telemetry` каждые 5с: pos, bufAhead, loading, playing, suppression - покажет, стекает ли буфер к нулю в момент рывка.

Главные кандидаты для следующего лога: (1) suppression/playWhenReady - рывок = чужой аудиофокус; (2) повторные `media load started` - сервер рвёт соединение; (3) bufAhead->0 без BUFFERING - сетевое голодание; (4) discontinuity - тихие прыжки позиции.

## 3.6 Итерация 3 (2026-07-22, лог 105908 с телеметрией)

Лог `logs/fastmediasorter_20260722_105908.log` дал развязку:
- DFM: 10:59-11:20 TCP connect к `hostingradio.ru:443` НЕ устанавливался вообще (`SocketTimeoutException: failed to connect .. after 15000ms`, source-IP `192.168.107.176` - подсеть Samsung-хотспота). Ни одного `playbackState=3` - станция не играла; слой S1118 корректно ретраил. Это сетевой путь, не плеер.
- iHeart/KissFM/VestiFM (11:21+): телеметрия идеальна - pos растёт ровно, bufAhead стабилен (21с/8.5с/6с), 0 underrun, 0 suppression, 0 discontinuity, декодеры `c2.android.aac`/`c2.sec.mp3`. Движок отдаёт звук без разрывов, а владелец слышит проскоки ~1/с на этих же станциях -> слой проскоков НИЖЕ приложения (аудио-вывод: BT/route flapping) или физическая сеть хотспота.

Сделано по запросу владельца («заводские настройки», умный режим только при сбое):
1. `RadioStreamBufferConfig` = байт-в-байт заводской `DefaultLoadControl` (50000/50000/2500/5000). Кастомных буферных значений в чистом воспроизведении не осталось; retry/tolerance-слой S1118 включается только на ошибках (лог подтверждает - при чистой игре молчит), т.е. требуемая архитектура «default-first, resilience on failure» уже соблюдена без отдельной настройки.
2. Диагностика аудио-вывода: `route=bt|wired|speaker` в каждой строке телеметрии + `Audio diag: output devices added/removed` (AudioDeviceCallback) - BT-флаппинг станет виден.

Ожидание от следующего полевого лога: проскоки при `route=bt` и стабильной телеметрии = интерференция BT/Wi-Fi или флап BT-устройства (события added/removed); проскоки при `route=speaker` с чистой телеметрией = эскалация в слой AudioTrack (потребуется дамп audio_flinger).

## 3.7 Итерация 4 (2026-07-22): опция «Сверхинтеллектуальная буферизация трансляций»

Запрос владельца: общее решение для стабильной/нестабильной/нагруженной сети; по умолчанию - заводское поведение; умный режим - опционально, чтобы пробовать A/B. Рекомендации индустрии (Akamai ExoPlayer buffering guide, ExoPlayer issues #3164/#7297, Medium LoadErrorHandlingPolicy): радио не требует экзотики - стартовая подушка и loader-level reconnect вместо фатального рестарта.

Реализация:
- Настройка `streamsSmartBuffering` (default OFF) в секции Трансляций: `AppSettings` + `StreamsSettingsStore` (`streams_smart_buffering`) + `SettingsToggleRow rowSmartBuffering` (portrait+land) + строки EN/RU/UK + аннотации.
- OFF = заводской ExoPlayer: стоковый `DefaultLoadControl.Builder().build()` и стоковая политика ошибок.
- ON = smart: старт 5с / после сбоя 10с, `prioritizeTimeOverSizeThresholds(true)`, resilient `LoadErrorHandlingPolicy` - connectivity-ошибки ретраятся с backoff 2/4/8с практически бесконечно, пока играет буфер (без фатала и рестарта плеера); data/format-ошибки сохраняют стоковый порог отказа (3), чтобы не зациклиться.
- Флаг зеркалится в SharedPreferences (`stream_playback/smart_buffering`) по паттерну cache_size_mb: писатели - `AppStartupInitializer` (старт) и `SettingsRepositoryImpl.updateSettings` (изменение); читатели - `RadioStreamBufferConfig.createLoadControl/createLoadErrorHandlingPolicy` (синхронно при сборке плеера; политика читает флаг в момент ошибки, так что переключение действует без пересоздания плеера).
- LoadControl фиксируется при создании сервисного плеера - смена тумблера для него применяется со следующего запуска сервиса (после остановки радио).

## 3.8 Итерация 5 (2026-07-22, лог 124606): ВИНОВНИК НАЙДЕН - Dolby-декодер Samsung

Решающий A/B владельца: одна станция (Adroit Jazz Underground) в двух кодеках - `/jazz` рипит ~2/с, `/jazz_opus` чистый. Лог: `/jazz` = `audio/eac3-joc` (Dolby Digital Plus Atmos), 6 каналов, 768 kbps, декодер `c2.dolby.eac3.decoder.eac3-joc`. Телеметрия чистая (байты идут непрерывно) - рипы = грязный PCM из фирменного Dolby-декодера Samsung при JOC-декоде/downmix 6ch->динамик. Подтверждающие факты: локальный mp3 чистый (`c2.sec.mp3` невиновен), Opus чистый, перезагрузка не помогает, route=speaker (BT исключён ещё в итерации 3-4).

Фикс: `createPlaybackRenderersFactory` - `MediaCodecSelector`, который для Dolby-аудио mime (`ac3`, `eac3`, `eac3-joc`, `ac4`) поднимает software-декодеры (`c2.android.eac3` - честный E-AC3 downmix без JOC/virtualizer) выше платформенных `c2.dolby.*`. Только переупорядочивание, без фильтрации + `setEnableDecoderFallback(true)` - устройства без software-кандидата остаются на платформенном. Общий для всей семьи плееров (аудио-сервис, видео, network-хелперы) - прецедент селектора: S1125 grabber.

Открытый вопрос (некритичный): «рипы на любой станции» из ранних сессий - вероятно, смесь: недоступные RU-хосты (тишина+ретраи, отдельная сетевая проблема подсети 192.168.107.x) + Dolby-станции в ротации теста. После фикса перепроверить обычные mp3/aac станции отдельно.

## 3.9 Итерация 6 (2026-07-22, лог 130923): виновник - класс вендорских аудио-декодеров Samsung

Лог с walmradio (реальная пара владельца): `/jazz` = MP3 48 кГц -> `c2.sec.mp3.decoder.mpeg` -> рипы; `/jazz_opus` = Opus 48 кГц -> `c2.android.opus` -> чисто. Плюс итерация 5: `c2.dolby.eac3` рипал E-AC3-JOC. Ретроспектива: VestiFM (рипал утром) - MP3 48 кГц; «чистые» mp3-станции - 44.1 кГц. Итог: глючит не один декодер, а класс - фирменные вендорские аудио-декодеры Samsung (`c2.sec.*`, `c2.dolby.*`); AOSP-декодеры чисты на тех же станциях. Точечный Dolby-фикс итерации 5 был недостаточен.

Фикс: `MediaCodecSelector` в `createPlaybackRenderersFactory` предпочитает software-декодеры для ВСЕХ `audio/*` mime (`MimeTypes.isAudio`); видео не затронуто (hardware-first). Reorder-only + decoder fallback сохранён.

## 3.10 Итерация 7 (2026-07-22, лог 142224): softwareOnly не заменил Samsung MP3-декодер

Лог новой сборки `2.60.7221.417` показал, что `/jazz` всё ещё выбрал `c2.sec.mp3.decoder.mpeg`. Значит сортировка только по `softwareOnly` фактически не изменила приоритет на SM-S731B. Телеметрия всё так же чиста: 11 с буфера, `STATE_READY`, без underrun/error/suppression/discontinuity во время проскоков.

Фикс: для `audio/*` селектор явно ставит `c2.android.*` первым, `c2.sec.*` и `c2.dolby.*` - после остальных; видео не затронуто. Временный `S1148` probe логирует итоговый список кандидатов. Сборка для полевой проверки: `2.60.7221.429-NoLegal-DEBUG`.

## 3.11 Итерация 8 (2026-07-22, лог 143339): декодерная гипотеза опровергнута, A/B без ICY

Лог `logs/fastmediasorter_20260722_143339.log` подтвердил фактическое применение селектора:
`/jazz` декодируется `c2.android.mp3.decoder`, `/jazz_opus` - `c2.android.opus.decoder`. Оба потока
48 кГц stereo, буфер стабилен (MP3 около 12 с, Opus около 14 с), `STATE_READY`, underrun/error/
suppression/discontinuity нет, маршрут один (`bt`). Владелец по-прежнему слышит рывки только на MP3.
Значит Samsung-декодеры не являются причиной.

Внешняя проверка точного endpoint:
- FFmpeg 30 с с `Icy-MetaData: 1` и `0` декодирует MP3 без ошибок; сервер уважает `0` и не отдаёт
  `StreamTitle`.
- 45-секундная запись содержит 1875 последовательных MP3-фреймов по 24 мс. Live-VBR использует
  кадры 32/224/256/320 кбит/с, но временных дыр на входе нет.
- Одновременные 60-секундные записи `/jazz` и `/jazz_opus` сравнены по десяти 6-секундным окнам:
  корреляция 0,937-0,984; относительное смещение во всех окнах ровно -1,120 с. Серверный MP3 не
  содержит слышимых пропусков или повторов относительно чистого Opus.

Следующий изолированный кандидат - in-band ICY path Media3 1.2.1. Для progressive stream сама
`ProgressiveMediaPeriod` добавляет `Icy-MetaData: 1`, а `IcyDataSource` вырезает блок метаданных из
аудиобайт по `icy-metaint`; Opus использует контейнерные метаданные и этот путь не проходит. A/B
сборка переопределяет заголовок на `Icy-MetaData: 0` с высшим приоритетом `DataSpec`. Цена теста -
отсутствие ICY-названия текущего трека; авторизация userinfo и redirect-поведение сохранены.

## 4. Проверка

On-device (реальный SM-S731B, DFM `.aacp`): слушать радио >=5 минут в foreground и в фоне - рывков/микрофризов нет. В логе: probe `S1148:` при старте, НЕТ строк `audio underrun`, `playbackState` держится 3. Регресс: локальные треки и очередь играют как раньше; старт радио по-прежнему быстрый (~4с буфера).

## Last Audit

**Date:** 2026-07-22
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [x] Owner confirmed that all tested radio stations play correctly in build
  `2.60.7221.450-NoLegal-DEBUG` with the ICY override active.
- [x] `/jazz` and `/jazz_opus` control pair no longer differs audibly.
- [x] Temporary `S1148:` probes removed after verification; permanent regression comments retained.
