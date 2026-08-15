# Research: Воспроизведение интернет-потоков (audio/video по URL) - S0565

Сводный исследовательский артефакт. Объединяет четыре находки: интеграционный seam в codebase, каталог типов потоков с тестовыми URL, паттерны реализации Media3 1.2.1, матрицу сложностей и митигаций. Целевой модуль `app_v2`, pin Media3 1.2.1.

## 1. Источники интернет-потоков

Типы потоков, релевантные для плеера на Media3:

- **HLS VOD** (`.m3u8`) - сегментированный поток с фиксированной длительностью. Контейнеры внутри: MPEG-TS, fMP4/CMAF, ADTS (AAC), MP3. Субтитры CEA-608/708, WebVTT. Модуль `media3-exoplayer-hls`.
- **HLS LIVE** (`.m3u8`) - сегментированный живой эфир, `duration = C.TIME_UNSET`, есть live-window. Поддержка Apple LL-HLS. Community LL-HLS и SCTE-35 не поддерживаются.
- **DASH** (`.mpd`) - VOD и live, ultra-low-latency CMAF live. Контейнеры fMP4, WebM, Matroska (только демультиплексированные, отдельный AdaptationSet на тип). MPEG-TS внутри DASH не поддерживается. Модуль `media3-exoplayer-dash`.
- **Progressive HTTP(S)** - прямой медиафайл по URL. Контейнеры: MP4, M4A, fMP4, WebM, MKV, MP3, Ogg (Vorbis/Opus/FLAC), WAV, MPEG-TS, MPEG-PS, FLV (без seek), ADTS/AAC, FLAC, AMR. MP3/ADTS/AMR seek только через constant-bitrate. Играется ядром `DefaultExtractorsFactory` без доп. модулей - доступно даже lite/photos.
- **Icecast / Shoutcast radio (ICY)** - протокол in-band метаданных поверх HTTP; payload - обычный MP3 или AAC/ADTS, играется как progressive. ICY-метаданные (`IcyHeaders`, `IcyInfo`) читаются ядром ExoPlayer нативно с версии 2.10, без расширений. Большая доля публичного радио раздаётся по plain `http://` - требует cleartext-политики (см. §4, §6).
- **Playlists m3u/pls/xspf** - не медиа, а файлы-указатели на реальные URL. ExoPlayer НЕ парсит `.pls`/`.xspf`/plain `.m3u` - приложение должно скачать, распарсить и передать вложенный URL плееру. Исключение - HLS `.m3u8`, который парсит HLS-модуль. `.m3u8` неоднозначен: это либо Extended-M3U плейлист, либо Apple HLS-манифест - различать по содержимому (`#EXT-X-` теги), а не по расширению.
- **RTSP** (`rtsp://`) - live и on-demand, форматы H.264, AAC (ADTS), AC-3, транспорт RTP/UDP unicast и interleaved RTP/RTSP/TCP. Модуль `media3-exoplayer-rtsp`. Своя схема, не https.
- **RTMP** (`rtmp://`) - через add-on `media3-datasource-rtmp` (обёртка LibRtmp), `DefaultDataSource` подхватывает рефлексией.
- **WebRTC** - НЕ поддерживается Media3, нужен отдельный стек, вне области плеера.
- **SmoothStreaming** - поддерживается Media3, но явно ИСКЛЮЧЁН из области проекта, не подключать.

### Таблица тестовых URL

| type | example url | live? | https? |
|---|---|---|---|
| HLS VOD (HEVC) | https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8 | VOD | yes |
| HLS VOD (fMP4) | https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8 | VOD | yes |
| HLS VOD (TS) | https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8 | VOD | yes |
| HLS VOD | https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8 | VOD | yes |
| HLS VOD | https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8 | VOD | yes |
| HLS VOD | https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.mp4/.m3u8 | VOD | yes |
| HLS LIVE | https://demo.unified-streaming.com/k8s/live/stable/live.isml/.m3u8 | LIVE | yes |
| HLS LIVE | https://cph-msl.akamaized.net/hls/live/2000341/test/master.m3u8 | LIVE | yes |
| DASH LIVE | https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd | LIVE | yes |
| DASH LIVE | https://livesim2.dashif.org/livesim2/segtimeline_1/patch_60/testpic_2s/Manifest.mpd | LIVE | yes |
| DASH VOD | https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd | VOD | yes |
| Progressive MP4 | https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4 | VOD | yes |
| Radio (ICY/AAC) | https://ice5.somafm.com/groovesalad-128-aac | LIVE | yes |
| Radio (ICY/MP3) | https://ice5.somafm.com/groovesalad-128-mp3 | LIVE | yes |
| Playlist .pls | https://somafm.com/groovesalad130.pls | LIVE | yes |
| Playlist .m3u | https://somafm.com/m3u/groovesalad130.m3u | LIVE | yes |
| Radio directory API | https://de1.api.radio-browser.info/json/stations | n/a | yes (записи часто http) |
| RTSP VOD | rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4 | VOD | n/a (схема rtsp) |

Оговорки: сторонние тестовые URL (Mux, Akamai, Wowza demo) ротируются/уходят в офлайн; Apple-потоки могут rate-limit. Самые устойчивые all-https endpoints - DASH-IF livesim и SomaFM. Многие записи radio-browser.info - `http://`, что и нужно для проверки cleartext-обработки.

## 2. Текущая архитектура плеера и точка интеграции

Ключевые классы (все пути от `app_v2/src/main/java/com/sza/fastmediasorter/`):

- `ui/player/VideoPlayerManager.kt` (666 строк) - оркестрирует жизненный цикл ExoPlayer; владеет `playVideo()`, диспетчеризует по `ResourceType` в protocol-хелперы.
- `ui/player/PlayerActivity.kt` (999) - host Activity, вызывает `mediaLoaderManager.playVideo(path)`.
- `ui/player/helpers/PlayerMediaLoaderManager.kt` (1009) - фасад между Activity и VideoPlayerManager, маршрутизация по типу медиа и ресурса.
- `ui/player/AudioPlaybackService.kt` (591) - `MediaSessionService`, владеет ExoPlayer для фонового аудио.
- `ui/player/helpers/AudioServiceController.kt` (~200) - клиентский `MediaController`-коннектор к сервису; уже несёт путь `playAudioWithMetadata()` со `streamCredentials`.
- `ui/player/helpers/NetworkAwareMediaSourceFactory.kt` (131) - `MediaSource.Factory`, диспетчеризует SMB/SFTP/FTP/cloud в protocol-DataSource-factories; для прочих схем (включая `http/https`) `dataSourceFactoryFor()` возвращает `null` (строка 113: `else -> null`), делегируя в `DefaultMediaSourceFactory(context)`.
- `ui/player/helpers/LocalPlaybackHelper.kt` (257) - `playLocalVideoInternal()`: строит `MediaItem`, делает `File(normalizedPath).exists()`, затем `setMediaItem().prepare()`.
- `ui/player/helpers/SmbPlaybackHelper.kt`, `FtpPlaybackHelper.kt`, `CloudPlaybackHelper.kt` - паттерн protocol-хелпера: каждый - `internal suspend fun VideoPlayerManager.play*Video(...)`, создаёт свежий ExoPlayer с protocol-specific factory.
- `ui/player/helpers/BdTsPlaybackHelper.kt` (64) - `buildBdTsMediaSourceFactory()`: оборачивает любую `DataSource.Factory` в `DefaultMediaSourceFactory` с BD-TS extractor config.
- `ui/player/helpers/PlayerMediaViewVisibilityHelper.kt` (65) - `determineResourceType()`: детект маршрута по префиксу схемы URI.
- `domain/model/Models.kt:6-15` - enum `ResourceType { LOCAL, SMB, SFTP, FTP, CLOUD }` - **нет значения `HTTP`/`STREAM`**.

### Архитектурный разрыв и точный seam

Главный разрыв: `ResourceType` не имеет случая `HTTP`/`STREAM`. `determineResourceType()` (`PlayerMediaViewVisibilityHelper.kt:57-63`) для любого `http(s)://` пути проваливается в `defaultType ?: ResourceType.LOCAL`. В результате HTTP-URL уходит в `playLocalVideoInternal()`, который делает `File(normalizedPath).exists()` на не-файловом пути и падает на guard'е `showFileNotFound()` (если путь не начинается с `content://`). Намеренного маршрута `http(s)://` в плеер сегодня нет.

Точный seam (минимальное вклинивание), три шва в одной плоскости:

1. **Enum**: добавить `HTTP_STREAM` в `ResourceType` (`Models.kt`).
2. **Route detection**: в `determineResourceType()` (`PlayerMediaViewVisibilityHelper.kt:57-63`) распознать `path.startsWith("http://") || path.startsWith("https://")` → `ResourceType.HTTP_STREAM` ДО fallback'а в LOCAL.
3. **Dispatch + helper**: добавить ветку в `VideoPlayerManager.playVideo()` switch (`CLOUD/SMB/SFTP/FTP/LOCAL`) → новый `internal suspend fun VideoPlayerManager.playHttpStreamVideo(url, playWhenReady)` в `ui/player/helpers/HttpStreamPlaybackHelper.kt`, по образцу `CloudPlaybackHelper` (минимальный build ExoPlayer + `setMediaItem(MediaItem.fromUri(url))` + `prepare()` + `playWhenReady`), но с HTTP-aware `DataSource.Factory` (user-agent, cross-protocol redirects - см. §3).

Альтернатива (меньше изменений, грязнее): short-circuit `path.startsWith("http")` перед `File.exists()` внутри `playLocalVideoInternal()`. Решение между двумя подходами - за владельцем (см. §6, открытый вопрос про enum).

Фоновое аудио (радио): путь `AudioPlaybackService` уже работоспособен для `http(s)://` аудио без изменений кода, т.к. `NetworkAwareMediaSourceFactory` для http проваливается в `DefaultMediaSourceFactory(context)`. Расширение `AudioServiceController.playAudioWithMetadata()` для http-URL - добавление одного параметра (`streamCredentials = null`). Минус текущего пути: нет user-agent (часть станций отклоняет запросы без `User-Agent`), нет retry/ICY-обработки.

Буферы: protocol-хелперы переиспользуют `PrefetchLoadControlFactory.build()`; константы тюнятся в companion `VideoPlayerManager.kt:154-178` (`MIN_BUFFER_MS`, `CLOUD_MIN_BUFFER_MS`, `AUDIO_MIN_BUFFER_MS`). VOD-константы (15-30 с) не годятся для live/radio - нужны меньшие (2-5 с).

Покрытие тестами seam'а сегодня нулевое: нет тестов на `LocalPlaybackHelper`, `NetworkAwareMediaSourceFactory` (включая `else -> null` для http), `determineResourceType()`/`playVideoWithResourceType()`, `AudioServiceController`.

## 3. Реализация (Media3 паттерны)

Проверено против Media3 1.2.1 (pin проекта), namespace `androidx.media3.*`. Логирование - только Timber (`Timber.d`/`Timber.w`), не `android.util.Log`.

Импорты:
```kotlin
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Metadata
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
```

### 3.1 MediaItem + DefaultMediaSourceFactory: auto-detect / MIME hint / явные factories

Порядок детекции `DefaultMediaSourceFactory`: явный `setMimeType` на MediaItem → расширение в URI → серверный `Content-Type`. Progressive MIME не требует.

Auto-detect по расширению/Content-Type:
```kotlin
val item = MediaItem.fromUri("https://example.com/live/playlist.m3u8")
player.setMediaItem(item)
player.prepare()
```

MIME hint (когда нет расширения / сервер врёт - типично для radio/relay):
```kotlin
val hls = MediaItem.Builder().setUri(hlsUri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
val dash = MediaItem.Builder().setUri(dashUri).setMimeType(MimeTypes.APPLICATION_MPD).build()
val rtsp = MediaItem.Builder().setUri(rtspUri).setMimeType(MimeTypes.APPLICATION_RTSP).build()
```

Явные per-protocol factories (максимум контроля; место для кастомного `LoadErrorHandlingPolicy`):
```kotlin
val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
    .createMediaSource(MediaItem.fromUri(hlsUri))
val dashSource = DashMediaSource.Factory(dataSourceFactory)
    .createMediaSource(MediaItem.fromUri(dashUri))
// Progressive = raw MP3/AAC/OGG icecast - типичный кейс интернет-радио.
val progressiveSource = ProgressiveMediaSource.Factory(dataSourceFactory)
    .createMediaSource(MediaItem.fromUri(radioMp3Uri))
player.setMediaSource(progressiveSource)
player.prepare()
```

Замечание: `DefaultMediaSourceFactory` строит `HlsMediaSource`/`DashMediaSource` только если их Gradle-артефакты на classpath (есть в standard/legacy/noLegal/vr; нет в lite/photos - см. §5).

### 3.2 DefaultHttpDataSource: user-agent, cross-protocol redirects

`setAllowCrossProtocolRedirects(true)` обязателен для радио: relay-URL рутинно делают 301/302 между http и https.

```kotlin
val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    .setUserAgent("FastMediaSorter/2.60 (Android)")
    .setAllowCrossProtocolRedirects(true)
    .setConnectTimeoutMs(15_000)
    .setReadTimeoutMs(15_000)
    .setKeepPostFor302Redirects(false)
    .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))  // запрос ICY-метаданных

val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory)

val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    )
    .build()
```

Per-request заголовки (auth-стримы): `ResolvingDataSource.Factory(httpDataSourceFactory) { spec -> spec.withRequestHeaders(...) }`.

### 3.3 ICY-метаданные радио (IcyHeaders / IcyInfo)

`IcyHeaders` (при connect) → station name/genre/url/bitrate. `IcyInfo` (периодически) → текущий title. Оба - через `Player.Listener.onMetadata`. Требует `Icy-MetaData: 1` из §3.2.

```kotlin
player.addListener(object : Player.Listener {
    override fun onMetadata(metadata: Metadata) {
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is IcyHeaders -> Timber.d("ICY station=${entry.name} genre=${entry.genre} bitrate=${entry.bitrate}")
                is IcyInfo -> Timber.d("ICY now-playing=${entry.title} url=${entry.url}")
            }
        }
    }
})
```

Проброс now-playing в системную нотификацию (split "Artist - Track"):
```kotlin
val (artist, title) = entry.title?.split(" - ", limit = 2)
    ?.let { it.getOrNull(0) to it.getOrNull(1) } ?: (null to entry.title)
player.currentMediaItem?.let { current ->
    player.replaceMediaItem(
        player.currentMediaItemIndex,
        current.buildUpon().setMediaMetadata(
            MediaMetadata.Builder().setArtist(artist).setTitle(title).build()
        ).build()
    )
}
```

### 3.4 Live: LiveConfiguration, seekToDefaultPosition, BehindLiveWindow

Только для сегментированного live (HLS/DASH). Чистое Icecast/Shoutcast progressive-радио "живое" по духу, но не имеет live-window семантики.

```kotlin
val liveItem = MediaItem.Builder()
    .setUri(liveUri)
    .setMimeType(MimeTypes.APPLICATION_M3U8)
    .setLiveConfiguration(
        MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(5_000)
            .setMinOffsetMs(2_000)
            .setMaxOffsetMs(8_000)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.03f)
            .build()
    )
    .build()
```

Глобальный default offset: `DefaultMediaSourceFactory(context).setLiveTargetOffsetMs(5_000)`.

Прыжок к live-edge + обработка BehindLiveWindow:
```kotlin
player.seekToDefaultPosition()  // к live-краю

override fun onPlayerError(error: PlaybackException) {
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        player.seekToDefaultPosition()
        player.prepare()
    }
}
```
Пробы live-состояния: `player.isCurrentMediaItemLive`, `player.isCurrentMediaItemDynamic`, `player.currentLiveOffset`.

### 3.5 LoadControl: streaming vs local

`setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)`.

```kotlin
// Интернет-поток: большой буфер, приоритет времени над размером, быстрый старт.
val streamingLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(30_000, 60_000, 2_500, 5_000)
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()

// Локальный progressive: тугие буферы, низкая задержка старта.
val localLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(5_000, 15_000, 1_000, 2_000)
    .build()
```

### 3.6 Error / retry

Сетевые коды: `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`, `ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT`, `ERROR_CODE_IO_BAD_HTTP_STATUS`, `ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE`, плюс `ERROR_CODE_BEHIND_LIVE_WINDOW`.

App-level retry:
```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition(); player.prepare()
            }
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                scope.launch { delay(3_000); player.prepare() }
            }
            else -> Timber.w(error, "Unrecoverable playback error code=${error.errorCode}")
        }
    }
})
```

Network-layer retry/backoff (`LoadErrorHandlingPolicy` - управляет внутренними retry загрузки сегментов ДО фатального onPlayerError):
```kotlin
val retryPolicy = object : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        minOf(1000L * (1 shl (info.errorCount - 1)), 8_000L)  // exp backoff cap 8s
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 6
}
val source = HlsMediaSource.Factory(dataSourceFactory)
    .setLoadErrorHandlingPolicy(retryPolicy)
    .createMediaSource(liveItem)
```
`DefaultLoadControl` (сколько буферизовать, против столлов) и `LoadErrorHandlingPolicy` (retry/backoff при ошибке загрузки) - разные рычаги; оба нужны для устойчивого мобильного стриминга.

### 3.7 Фоновое аудио (MediaSessionService, WAKE_MODE_NETWORK, audio focus)

```kotlin
class RadioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)   // пауза при отключении наушников
            .setWakeMode(C.WAKE_MODE_NETWORK)    // держит CPU + WiFi для стриминга
            .setLoadControl(streamingLoadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
            )
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }
    override fun onGetSession(c: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() {
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        super.onDestroy()
    }
}
```
`setWakeMode(C.WAKE_MODE_NETWORK)` требует `android.permission.WAKE_LOCK`. Media3-сессия авто-промоутит сервис в foreground с `MediaNotification` во время игры - ручной билдер нотификации не нужен.

Manifest:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<service android:name=".RadioPlaybackService"
    android:foregroundServiceType="mediaPlayback" android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

Note по проекту: `INTERNET` уже объявлен безусловно (`app_v2/src/main/AndroidManifest.xml:22`); `FOREGROUND_SERVICE_MEDIA_PLAYBACK` уже присутствует (существующий background-audio путь). `AudioPlaybackService.onCreate()` корректно гейтит `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` через `Build.VERSION.SDK_INT >= Q` (строка 172) - радио через сервис работает с API 23+ (legacy). `FOREGROUND_SERVICE_USE_WHILE_DEVICE_LOCKED` не объявлен.

UI-сторона: `MediaController.Builder(context, SessionToken(...)).buildAsync()`, на connect - `setMediaItem` → `prepare` → `play`.

Доп. интеграция: добавить Gradle-deps `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-session` (если нет) рядом с `media3-exoplayer`. ICY-поддержка - в ядре, без отдельной либы.

## 4. Сложности и митигации

| # | Сложность | Причина | Влияние | Митигация (Media3 API) | Решение владельца? |
|---|-----------|---------|---------|------------------------|--------------------|
| P0 | Cleartext HTTP заблокирован | Base-config `cleartextTrafficPermitted=false`; большинство публичного радио - plain `http://` | Поток молча не грузится для большой доли реальных radio-URL | Per-domain allowlist в `network_security_config.xml`, ИЛИ relax base policy (регрессия безопасности) | **ДА** - tradeoff безопасности |
| P0 | Разрыв flavor coverage (lite/photos) | media3-hls/dash есть только в standard/noLegal/legacy/vr | HLS/DASH дают `UnrecognizedInputFormatException` (нет extractor) - фича сломана в этих flavor | Gate фичи через flavor-интерфейс (без `BuildConfig.IS_*` в `src/main`, rule 14), ИЛИ добавить deps (рост APK) | **ДА** - scope vs размер APK |
| P0 | `.m3u`/`.pls` не парсятся нативно | ExoPlayer трактует их как медиа → `UnrecognizedInputFormatException` | Вставка типичной radio-directory ссылки (часто `.pls`/`.m3u`) полностью падает | Скачать плейлист, распарсить (`FileN=` для PLS; non-`#` строки для M3U), извлечь реальный URL, затем MediaItem | Нет (инженерия) |
| P1 | Сбой auto-detect формата | Неверный/отсутствующий `Content-Type`, нестандартное/отсутствующее расширение | `UnrecognizedInputFormatException`; HLS/DASH не стартует | `setMimeType(APPLICATION_M3U8/APPLICATION_MPD)` или явная `HlsMediaSource.Factory`/`DashMediaSource.Factory` | Нет |
| P1 | http↔https редиректы | Radio-серверы 301/302; ExoPlayer по умолчанию блокирует cross-protocol | Поток падает после редиректа даже при доступном target | `setAllowCrossProtocolRedirects(true)` | Нет |
| P1 | Обрывы на нестабильной мобильной сети | Прерывания; default surface `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` после 3 retry | Воспроизведение стопается вместо восстановления при возврате сигнала | Кастомный `LoadErrorHandlingPolicy` (exp backoff, выше/бесконечный retry) + re-`prepare()` по connectivity-restored + UX переподключения | Частично (UX) |
| P1 | Lifecycle фонового воспроизведения | Потеря audio-focus, doze, нет FGS, нет wifi/wake lock | Аудио стопается при выключенном экране / в фоне; убивается системой | `MediaSessionService` (FGS+focus+нотификация); `setHandleAudioBecomingNoisy(true)`; `setWakeMode(C.WAKE_MODE_NETWORK)` | Частично (scope) |
| P2 | Rebuffering/столлы на медленной сети | Default buffer + адаптивный выбор стартуют выше пропускной способности | Дропауты, долгий старт, повторные столлы на cellular | Тюнинг `DefaultLoadControl`; для HLS/DASH ограничить старт-битрейт через `DefaultTrackSelector` (`setMaxVideoBitrate`/`setInitialBitrateEstimate`) | Нет |
| P2 | Дрейф live-edge / BehindLiveWindow | Долгая пауза/буфер уводит позицию за live-window (только HLS/DASH live) | Падение с `ERROR_CODE_BEHIND_LIVE_WINDOW` после resume | `if errorCode == ERROR_CODE_BEHIND_LIVE_WINDOW → seekToDefaultPosition(); prepare()` | Нет |
| P2 | ICY / нестандартный ответ сервера | Shoutcast/Icecast отвечают `ICY 200 OK`; `DefaultHttpDataSource` может отклонить ("Unexpected status line: ICY 200 OK") | Часть shoutcast-потоков не открывается | Использовать Media3 OkHttp datasource (`datasource_okhttp`), толерантный к ICY status line | Нет |
| P3 | Нет метаданных / обложки | Радио часто без ID3/ICY или только периодический ICY title | Пустой now-playing, нет обложки в нотификации | Читать ICY через `onMetadata`; placeholder-обложка; косметика | Нет |
| P3 | DRM / Widevine | Защищённые потоки требуют license flow | Неактуально для радио | **Вне области** - радио/подкасты не DRM; задокументировать как non-goal | Note only |

Доп. риски из codebase (severity Med/High):
- `determineResourceType()` не распознаёт http(s) - сегодня URL молча → LOCAL → `showFileNotFound()` (High).
- `playLocalVideoInternal()` делает `File(path).exists()` на HTTP-URL - падение guard'а без `content://` (High).
- Live duration = `C.TIME_UNSET` - `PlaybackPositionRepository.getPosition()`/`startPositionSaving()` (`VideoPlayerManager.kt:603,644`) попытаются сохранять/восстанавливать TIME_UNSET (Med). Митигация: подавлять save/restore для HTTP_STREAM или по `player.isCurrentMediaItemDynamic`.
- Текущий `AudioPlaybackService` http-путь без user-agent - часть станций отклоняет (Med).
- `PlayerMediaLoaderManager` на 1009 строках - добавление routing рискует приблизить к лимиту 1500 (Med).

## 5. Рекомендация по объёму 1-й итерации

Делать первым:
- **Progressive HTTP(S) audio** (mp3/aac/ogg/flac/wav) - ядро `DefaultExtractorsFactory`, работает во всех flavor включая lite/photos.
- **HLS VOD** (`.m3u8`) - авто-детект `DefaultMediaSourceFactory`, deps уже есть в standard/legacy/noLegal/vr.
- **Базовое радио** (Icecast/Shoutcast progressive) - тот же progressive-путь + ICY-метаданные через `onMetadata`, user-agent + `allowCrossProtocolRedirects` + `Icy-MetaData:1`.
- Обязательная инфраструктура потока: `DefaultHttpDataSource` с user-agent/redirects, streaming `LoadControl` (2-5 с для live), базовый error-retry, подавление position save/restore для динамических item.

Отложить:
- DASH live и HLS live (LiveConfiguration, BehindLiveWindow recovery, live-offset tuning) - отдельная итерация после стабильного VOD/radio.
- RTSP / RTMP - отдельные модули и схемы, узкий спрос.
- Парсинг плейлистов `.m3u`/`.pls`/`.xspf` (скачать-распарсить-извлечь) - отдельная инженерная задача; пока пользователь вводит прямой stream-URL.
- DRM / Widevine - явный non-goal.
- ICY OkHttp datasource (для `ICY 200 OK`-станций) - после базового радио, если всплывут несовместимые станции.

Привязка к flavor coverage:
- **standard, legacy, noLegal, vr**: полный набор 1-й итерации (progressive + HLS VOD + радио) - deps hls/dash присутствуют.
- **lite, photos**: media3-hls/dash отсутствуют (`app_v2/build.gradle.kts:1134-1141`). HLS/DASH URL дадут `UnrecognizedInputFormatException`. Вариант (a): скрыть entry-point интернет-потоков целиком; вариант (b): ограничить progressive http(s) audio (mp3/aac), который ядро тянет без hls/dash. Гейтить через flavor-интерфейс/source-set (rule 14), НЕ через `BuildConfig.IS_*` в `src/main`. Проверять на реальном build варианта lite/photos (rule 20). Учесть: photos имеет `SUPPORT_AUDIO=false`/`SUPPORT_VIDEO=false` - вероятно entry-point отсутствует вовсе.

Релевантные BuildConfig-флаги по flavor: `SUPPORT_AUDIO` (false только в photos), `SUPPORT_VIDEO` (false только в photos), `ENABLE_PERSISTENT_AUDIO_PLAYBACK` (false в lite/photos), `SUPPORTS_DEFAULT_PLAYER` (false в lite). Радио-через-сервис зависит от `ENABLE_PERSISTENT_AUDIO_PLAYBACK`.

## 6. Решения, требующие владельца

1. **Cleartext-политика для http-радио** (P0, безопасность). Опции:
   - Per-domain allowlist в `res/xml/network_security_config.xml` (`<domain-config cleartextTrafficPermitted="true">`). Сохраняет base HTTPS-only. Проблема: radio-URL пользовательские и неограниченные - статический allowlist покроет только известные/bundled-пресеты, не произвольные станции.
   - Relax base policy (`cleartextTrafficPermitted="true"` в base-config). Разблокирует всё http-радио, но откатывает HTTPS-only-харднинг app-wide - влияет на все сетевые вызовы, не только плеер.
   - Middle path: base HTTPS-only, cleartext включается только для потоков, явно подтверждённых пользователем. Не релаксить base молча.

2. **Поддержка lite/photos** (P0, scope vs APK size). Скрыть entry-point целиком ИЛИ ограничить progressive audio без hls/dash ИЛИ добавить deps в эти flavor (рост APK). Для photos с `SUPPORT_*=false` - вероятно entry-point не нужен.

3. **UI-вход для stream-URL** (вне этого ресерча). Типизированный URL (новый диалог) / плейлист-файл / intent от внешнего приложения - определяет, где entry-point (`PlayerActivity.createIntent()`, `StandalonePlayerActivity` или новый). Сопутствующие вопросы за владельцем: video-стримы vs audio-only радио (через `AudioPlaybackService` foreground-путь или in-activity `VideoPlayerManager`, либо оба по MIME); новый `ResourceType.HTTP_STREAM` vs short-circuit в `playLocalVideoInternal()`; авто-recovery `BEHIND_LIVE_WINDOW`; подавление position save/restore для стримов; auth-protected стримы (Basic Auth / токены) - в области или нет; surfacing ICY-метаданных в нотификации/"Now Playing" (эта или follow-up спека).

## 7. Источники / ссылки

Android официальные:
- Supported formats: https://developer.android.com/media/media3/exoplayer/supported-formats
- HLS: https://developer.android.com/media/media3/exoplayer/hls
- DASH: https://developer.android.com/media/media3/exoplayer/dash
- Media items / MIME hints: https://developer.android.com/media/media3/exoplayer/media-items
- Media sources: https://developer.android.com/media/media3/exoplayer/media-sources
- Customization (DataSource/MediaSource/LoadControl/error policy): https://developer.android.com/media/media3/exoplayer/customization
- Live streaming: https://developer.android.com/media/media3/exoplayer/live-streaming
- Background playback (MediaSessionService): https://developer.android.com/media/media3/session/background-playback
- Background audio hardening: https://developer.android.com/about/versions/17/changes/bg-audio
- Troubleshooting: https://developer.android.com/media/media3/exoplayer/troubleshooting
- Network Security Configuration: https://developer.android.com/training/articles/security-config
- Media3 releases / modules: https://developer.android.com/jetpack/androidx/releases/media3
- API refs: DefaultMediaSourceFactory, DefaultLoadControl.Builder, DefaultLoadErrorHandlingPolicy, IcyHeaders, PlaybackException (developer.android.com/reference/androidx/media3/...)

Issues / практика:
- ICY в Media3 (IcyHeaders/IcyInfo): https://github.com/androidx/media/issues/153 ; ext: https://github.com/saschpe/android-exoplayer2-ext-icy
- Network retry/recovery: https://github.com/androidx/media/issues/1140
- ExoPlayer .pls/.m3u: https://github.com/google/ExoPlayer/issues/2051 , https://github.com/google/ExoPlayer/issues/4066
- ExoPlayer Shoutcast ICY: https://github.com/google/ExoPlayer/issues/473 , https://github.com/google/ExoPlayer/issues/3735
- BehindLiveWindow resume: https://github.com/google/ExoPlayer/issues/8675
- PlaybackException.java source: https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/PlaybackException.java
- RTMP datasource: https://github.com/google/ExoPlayer/blob/release-v2/extensions/rtmp/README.md
- Load error handling (Medium): https://medium.com/google-exoplayer/load-error-handling-in-exoplayer-488ab6908137
- Akamai buffering strategy: https://www.akamai.com/blog/performance/enhancing-video-streaming-quality-for-exoplayer-part-2-exoplayers-buffering-strategy-how-to-lower

Тестовые потоки / каталоги:
- Sample HLS URL lists: https://developerinsider.co/sample-hls-m3u8-streams-test-urls-vod-and-live/ , https://ottverse.com/free-hls-m3u8-test-urls/
- DASH-IF livesim: https://livesim.dashif.org/ , https://livesim2.dashif.org/
- DASH-IF test vectors: https://github.com/Dash-Industry-Forum/dash-live-source-simulator/wiki/Test-URLs , https://testassets.dashif.org/#testvector/list
- DASH-IF sources.json: https://github.com/Dash-Industry-Forum/dash.js/blob/development/samples/dash-if-reference-player/app/sources.json
- Free MPEG-DASH MPD: https://ottverse.com/free-mpeg-dash-mpd-manifest-example-test-urls/
- SomaFM direct links: https://somafm.com/groovesalad/directstreamlinks.html
- radio-browser.info API: https://api.radio-browser.info/ , https://github.com/AnowHosting/radio-browser-api-documentation
- Unified Streaming player URLs: https://docs.unified-streaming.com/documentation/vod/player-urls.html
- Akamai MSL HLS/CMAF: https://techdocs.akamai.com/msl/docs/hls
- Playlist форматы (m3u/m3u8/pls/xspf): https://www.web3.lu/playlists-m3u-and-pls/ , https://blog.fileformat.com/en/audio/m3u-vs-m3u8-understanding-the-difference-and-when-to-use-each-format/ , https://helpful.knobs-dials.com/index.php/Playlist_file_notes