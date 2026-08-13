# S1144 Research 01 - Streams track / subtitle / metadata architecture

**Захвачено:** 2026-07-23 (research agent, feeds strategic §4/§5/§6/§9)
**Статус:** Resolved (architecture mapped from codebase)

---

## Ключевые факты текущего состояния

- **Видеопоток играет `VideoPlayerManager`** (единственный `ExoPlayer` и для локального видео, и для видео/RTSP-потоков), путь построения плеера - `StreamPlaybackHelper.playStreamVideo` (`ui/player/helpers/StreamPlaybackHelper.kt:41-138`, "BandwidthAdaptive").
- **Выбор аудио/субтитров уже работает для потоков** через `VideoTrackSelectionManager` (`ui/player/VideoTrackSelectionManager.kt`, 211 LOC: `TrackSelectionOverride`, `C.TRACK_TYPE_AUDIO/TEXT`, `CaptionStyleCompat`) и UI `PlaybackControlDialogFragment` (`ui/player/PlaybackControlDialogFragment.kt`, вкладки AUDIO/SUBTITLES). Вкладки не заглушены для потоков - появляются, если `hasMultipleAudioTracks`/`hasSubtitles` истинны в момент открытия диалога.
- **Пробел персистентности:** `TrackSelectionOverride` - только на сессию. Пересоздание плеера (ретюн канала, перезапуск приложения) сбрасывает выбор. `StreamSourceEntity` не имеет колонок под трек-предпочтение.
- **Схема БД:** `AppDatabase` = `@Database(version = 42)` (`data/local/db/AppDatabase.kt:36`). Последняя миграция `Migration41To42.kt` (аддитивный `ALTER TABLE stream_sources ADD COLUMN access`). Свободный слот - **42→43**, шаблон копируется дословно.
- **Аналог "запомнить player-предпочтение по источнику":** `StereoFormatOverrideEntity` (ключ `filePath`, `Upsert`, чтение при загрузке; работает и для сетевых путей).
- **Ключ канала стабилен - URL:** плеер идентифицирует поток строкой `path` (URL) сквозь весь стек; `StreamSourceDao.getByUrl(url)` уже резолвит URL→сущность (используется при retry потока, `PlayerEventHandler.kt:173`). Запись/чтение по URL в момент проигрывания не требует новой проводки через `PlayerActivity.createIntent`.
- **ICY "now playing" для видеопотока захватывается** (`StreamPlaybackHelper.kt:340-353` `onMetadata`→`IcyInfo`) и пишется в `player.mediaMetadata` (`replaceMediaItem`, строки 451-467), **но не отображается** ни одним UI видеопути (`tvFileNameOverlay` - статичный лейбл, не привязан к метаданным). Radio-путь показывает это через `StreamInlineAudioManager.renderTitle()`.
- **HLS ID3/EMSG timed-metadata не обрабатывается** - только ICY. Grep по `EMSG`/`EventMessage`/`Id3Frame`/`TimedMetadata` в `src/main` - ноль совпадений. Реальные IPTV/HLS-гиды обычно приходят через ID3/EMSG, а не ICY.
- **Глобальный дефолт языка уже есть:** `PlayerSettingsDialog.PlayerSettings` (audio/subtitle language: DEFAULT/EN/RU/UK + subtitles on/off) применяется через `VideoTrackSelectionManager.applyTrackSelection(player, settings, appLanguage)` при каждом воспроизведении (локальном и потоковом).
- **Флейворы:** `SUPPORT_STREAMS` = standard/legacy/noLegal/vr (lite/photos - false). Код трек-селекции/метаданных (`StreamPlaybackHelper`, `VideoTrackSelectionManager`, `PlaybackControlDialogFragment`) целиком в `src/main`, дополнительного flavor-split нет. `StreamProtocolSupport` (HLS/DASH/RTSP vs progressive-only) ортогонален и потоковый UI на progressive-only сборках не отгружается.
- **Тестовое покрытие цепочки - нулевое:** нет тестов на `VideoTrackSelectionManager`, `StreamPlaybackHelper`, `PlaybackControlDialogFragment`, `StreamInlineAudioManager`.

## Риски (перенесены в §7 спеки)

- `PlaybackControlDialogFragment` снимает `hasMultipleAudioTracks`/`hasSubtitles` один раз при открытии - live-манифест может доложить треки позже; вкладка может ложно отсутствовать.
- `activeStreamTrackSelector` переиспользуется `StreamQualityStepDownController` (S1128) для деградации качества при столах: новая логика `setParameters` не должна затирать step-down cap, а step-down - ручной override пользователя.
- RTSP-ветка строит плеер без явного `DefaultTrackSelector`; субтитров у RTSP-каналов обычно нет - секция может быть всегда пустой.
- Индекс группы/трека нестабилен на live-контенте (регенерация групп на live-edge) - персистить индекс хрупко; язык-код стабилен.

## Подлинные владельческие решения (Approval gate, см. §3.3/§6)

1. **Q1 - где показывать имя текущей программы** (overlay в плеере / тулбар / карточка канала в сетке). Нет существующей проводки - решение о размещении (Rule 10).
2. **Q2 - объём источника метаданных**: только ICY (уже захвачен, нужно лишь привязать UI) или также HLS ID3/EMSG (значимая новая работа, полноценный IPTV-гид).
3. **Q6 - смысл "выбрать в настройках"**: глобальный дефолт языка для потоков в `StreamsSettingsFragment` (зеркало `PlayerSettingsDialog`), per-channel-правка, или оба.

## Архитектурно-решаемое (рекомендации, см. §9 ADR)

- **Q3 - место хранения:** новые nullable-колонки на `stream_sources` (миграция 42→43, зеркало `access`/S1117), ключ - стабильный `id`/`url` канала. Проще отдельной таблицы, строка уже загружена при проигрывании.
- **Q4 - что запоминать:** язык-код аудио и субтитров (стабилен через релоады манифеста), применяется через `setPreferredAudioLanguage`/`setPreferredTextLanguage`, а не сырой `TrackSelectionOverride` индекс.
- **Q5 - взаимодействие с глобальным дефолтом:** per-channel-предпочтение переопределяет глобальный дефолт только для своего канала; при отсутствии - применяется глобальный дефолт (семантика `StereoFormatOverride`).
