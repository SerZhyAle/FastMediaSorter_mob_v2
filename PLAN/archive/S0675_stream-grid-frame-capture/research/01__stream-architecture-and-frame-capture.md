# Research 01 - Stream browser architecture & live-frame capture (S0675)

**Дата:** 2026-06-25
**Источник:** android-solution-researcher (read-only sweep)

## Слой-карта текущего экрана «Трансляции»

| Класс | Путь | Роль |
|---|---|---|
| `StreamsActivity` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` (~534 LOC) | Entry Activity. `LinearLayoutManager` захардкожен (line 119). |
| `StreamsViewModel` | `ui/streams/StreamsViewModel.kt` (~394) | StateFlow<StreamsUiState>, фильтр/сортировка/импорт/сессия |
| `StreamSourceAdapter` | `ui/streams/StreamSourceAdapter.kt` (~247) | ListAdapter; асинхронная загрузка фавикона с `boundUrl`-guard (lines 157-178) - паттерн отмены при rebind |
| `StreamSourceEntity` | `data/local/db/StreamSourceEntity.kt` (41) | url, title, mediaKind (AUDIO/VIDEO/RTSP), category, topic, language |
| `StreamSourceRepository` / `StreamSourceDao` | `data/repository/...`, `data/local/db/...` | Room |
| `StreamsSettingsStore` / `StreamsSessionStore` | `data/repository/settings/` | существующее хранилище настроек/сессии трансляций - сюда писать флаг режима |
| `StreamInlineAudioManager` | `ui/streams/helpers/StreamInlineAudioManager.kt` (183) | один ExoPlayer для инлайн-аудио; lifecycle build->setMediaItem->prepare->playWhenReady |
| `StreamPlaybackHelper` | `ui/player/helpers/StreamPlaybackHelper.kt` (248) | построение ExoPlayer для live/RTSP; буфер 15_000/30_000/2_500/5_000 (lines 55-61) |
| `VideoPlayerManager` | `ui/player/VideoPlayerManager.kt` (782, близко к лимиту) | единственный полноэкранный движок; НЕ расширять/не вызывать для ячеек |

## Захват кадра - что переиспользуемо

- `SaveVideoFrameManager.captureFrame()` (`ui/player/helpers/SaveVideoFrameManager.kt:161-176`) - канонический способ: найти `TextureView` в иерархии `PlayerView`, вызвать `.getBitmap()` на main thread. **Единственный надёжный способ снять живой кадр.** Требует TextureView-рендер (SurfaceView не отдаёт bitmap).
- `VideoPosterExtractor` / `NetworkVideoFrameDecoder` / `ThumbnailExtractorHelper` - на `MediaMetadataRetriever.getFrameAtTime`. **Неприменимо к живому потоку** (нет фиксированной длины/перемотки). Порог памяти `NATIVE_HEAP_LOW_THRESHOLD_BYTES = 50MB` в `VideoPosterExtractor.kt:169` - переиспользовать как guard.

## Сетка - паттерн

- `BrowseRecyclerViewManager` (`ui/browse/managers/BrowseRecyclerViewManager.kt`) - `GridLayoutManager` со span по ширине, `GridRowSpacingItemDecoration`, переключение list/grid. Формула span и decoration переиспользуемы.

## Flavor / гейт

- `SUPPORT_STREAMS`: standard=true, lite=false, photos=false, legacy=true, noLegal=true.
- Гейт: `CapabilityAvailability.isStreamsAvailable()` читает `BuildConfig.SUPPORT_STREAMS` (`core/capability/CapabilityAvailability.kt:47`); `MainActivity.kt:667`. `MediaCapabilities` НЕ содержит `supportsStreams` - гейт прямой по BuildConfig в src/main (существующий прецедент).
- Новый build-флаг не нужен: режим - рантайм-подрежим уже гейтированного экрана.

## Производительность (реальность)

- Аппаратные видеодекодеры: 2-4 одновременных сессии на типовом ARM SoC. >2-4 одновременных захватов -> `DECODER_INIT_FAILED`/чёрные кадры. API запроса лимита нет -> последовательный/ограниченно-параллельный захват.
- Snapshot-and-release: открыть -> дождаться `STATE_READY`/первого кадра -> getBitmap -> release немедленно. Минимальный буфер на ячейку. Downscale битмапа под размер ячейки.
- RTSP TCP-interleaved тяжелее HLS - тем более последовательно.

## Риски (сводно)

- MediaCodec concurrent limit (High), TextureView обязателен (High), память на legacy (High), сетевой трафик (High), зависший поток блокирует очередь (нужен тайм-аут+watchdog), раздувание StreamsActivity (вынос в менеджеры), новый view type в адаптере + DiffUtil (Med).

## /spec-draft кандидаты (вне объёма S0675)

1. `runBlocking` в Glide decode thread - `data/network/glide/NetworkVideoFrameDecoder.kt:184,220,258`.
2. `MediaCapabilities` без `supportsStreams` - гейт по BuildConfig в src/main нарушает паттерн интерфейса (CLAUDE.md Rule 14). `CapabilityAvailability.kt:47`, `MainActivity.kt:667`.
