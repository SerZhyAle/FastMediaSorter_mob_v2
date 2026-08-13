# S0156 Research: Internet Media Extraction — yt-dlp class tools + noLegal extension surface

**Direction:** Интернет-контент (Столп E, §5.1)
**First iteration:** 2026-05-12
**Scope:** URL media extraction для YouTube, YouTube Music, Instagram, TikTok, DeviantArt, X, Reddit, Pinterest, Flickr, ArtStation, Bandcamp, SoundCloud, Vimeo, Dailymotion и других публично доступных хостингов.

---

## Ограничения и контекст noLegal flavor

- **Дистрибуция:** noLegal не публикуется в Google Play и не распространяется публично. Исключительно для личного использования автора.
- **APK size:** бюджет отсутствует — Chaquopy (+40-60 MB), `libytdlp.so` (+35-50 MB) и любые другие тяжёлые зависимости допустимы.
- **Лицензии:** допустимы любые опубликованные библиотеки и код — GPL-2.0, GPL-3.0, AGPL-3.0, Chaquopy Community (non-commercial), проприетарные SDK. Ограничения по redistribution не применяются.

---

## Итерация 2026-05-12 — реальная картина failures и пакетные кандидаты

### Что реально работает в standard прямо сейчас

| Платформа / сценарий | Результат | Причина |
|---|---|---|
| Прямая ссылка на медиафайл (mp4, jpg, mp3, pdf ..) | ✅ Работает | `DirectFileExtractionStrategy` — HEAD + GET |
| Threads (одиночное видео) | ✅ Работает | CDN `t15.5256-10` без Referer-gate; `InvisibleWebView` перехватывает через `shouldInterceptRequest` |
| YouTube (через NewPipeExtractor) | ✅ noLegal only | `NewPipeSiteExtractionStrategy`, ALLOWED_SERVICE_IDS |
| YouTube Music | ✅ noLegal only | Тот же YouTube service в NewPipe |
| SoundCloud, Bandcamp, PeerTube, MediaCCC | ✅ noLegal only | ALLOWED_SERVICE_IDS |
| TikTok single video | ✅ Работает (S0171) | Desktop UA + `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON в DOM_DISCOVERY_SCRIPT |
| Instagram single public Reel/video | ⚠️ Нестабильно | `InvisibleWebView` иногда перехватывает CDN URL, но Referer-gate (S0171) ещё не прошёл on-device verify |

### Что системно НЕ работает и почему — platform-by-platform

**Instagram — 3 отдельных failure-режима:**

1. **`SocialPreviewOnly`** (наиболее частый) — страница рендерится, но все найденные кандидаты оказываются изображениями (thumbnail-превью поста). `isImageCandidate()` фильтрует их, ничего не остаётся, `KnownAuthResources.isPreviewSensitiveHost()` возвращает `true` → результат `SocialPreviewOnly`. Это правильное поведение для неавторизованного доступа к закрытому контенту, но UI предлагает логин даже на публичных постах, где auth не нужна.

2. **`DownloadCorrupted`** (подтверждён в логах S0170/S0171) — CDN `scontent.cdninstagram.com` (bucket `t50.2886-16`) отдаёт 200 OK с ~200-байтовым stub, тип `video/mp4`. `DirectFileExtractionStrategy.open()` не блокирует (тип допустимый), но S0170-sniffer отклоняет `kind=too-small`. Причина: отсутствует Referer при CDN re-fetch (S0171 исправляет это, статус In Progress).

3. **Карусели/галереи** — даже если одиночное видео вдруг извлекается, carousel-пост содержит N медиаэлементов. `InvisibleWebView` видит только DOM первого элемента. `shouldInterceptRequest` перехватывает CDN URL только первого загруженного медиа. Остальные элементы карусели не скролируются, не загружаются.

**TikTok — после S0171 стало значительно лучше, но:**

- `vm.tiktok.com` short URL: 301 → `www.tiktok.com/@user/video/<id>`. После S0171 desktop UA применяется корректно, JSON парсится. Нерешённый BUG-3: `sessionApplied=false` при canonical-host mismatch (cookie для `tiktok.com`, lookup по `vm.tiktok.com`). Обходится eTLD+1 wildcard в `LinkDownloadCookieJar` (S0171 шаг 8).
- A/B schema drift: TikTok иногда меняет JSON ключ `itemInfo` на `videoData`. DOM_DISCOVERY_SCRIPT не имеет fallback для этого.
- Нет поддержки TikTok плейлистов/профилей — только single video URL.

**Facebook — полностью не работает:**

- Facebook редиректит мобильный WebView на `m.facebook.com` + агрессивный deep-link в приложение (`fb://`). `shouldOverrideUrlLoading` блокирует non-http схемы, но страница уже показывает "открыть в приложении".
- С desktop UA: Facebook рендерит базовую страницу, но видео CDN URL (`video.xx.fbcdn.net`) не появляется в `shouldInterceptRequest` — видео грузится через MSE/blob: внутри WebView, а не как сетевой ресурс.
- `blob:` URL не перехватываются через `shouldInterceptRequest` — это фундаментальное ограничение Android WebView API.
- Без специализированного extractor с Facebook Graph API или Mobile Basic site парсингом — работающего решения нет.

**Threads карусели:**

- Одиночное видео работает (CDN без Referer). Но Threads-пост с несколькими медиа: та же проблема что у Instagram — только первый элемент.

**Авторизация — системная проблема:**

`applySessionContext()` в `LinkAutoDownloadCoordinator` делает lookup по `host` из URL. При редиректах или CDN субдоменах lookup может вернуть пустой список. Например: пользователь сохранил сессию для `instagram.com`, URL ссылки `https://www.instagram.com/p/...` — host = `www.instagram.com`, `loadFor("www.instagram.com")` → пусто (сохранено под `instagram.com`). eTLD+1 wildcard в `LinkDownloadCookieJar` частично решает это для OkHttp-слоя, но `applySessionContext` ищет точное совпадение host. Это latent bug для любого нового extractor.

**Fix направление:** `applySessionContext()` живёт в main sourceSet (`LinkAutoDownloadCoordinator`) — исправление eTLD+1 lookup нужно вносить туда, а не в noLegal-специфичный код. На устройстве установлен ровно один APK (standard или noLegal), поэтому fix в shared-коде автоматически покрывает оба flavor без override и без дублирования. Никакого `applySessionContextNoLegal()` не требуется — это был бы антипаттерн.

---

## Что найдено

### Текущее состояние в кодовой базе

**Существующий extraction pipeline (все market flavors):**

```
URL → LinkExtractionRegistry → [site → direct → html → dynamic]
                                     ↓               ↓
                           NewPipeSiteExtractionStrategy  InvisibleWebViewExtractionStrategy
                           (noLegal only)                  (all flavors)
```

**Ключевые классы:**

| Класс | Путь | Роль |
|---|---|---|
| `UrlExtractionStrategy` | `domain/usecase/link/` | Интерфейс — `probe()` + `open()` |
| `LinkExtractionRegistry` | `domain/usecase/link/` | Упорядоченный реестр; `site → direct → html → dynamic` |
| `LinkAutoDownloadCoordinator` | `domain/usecase/link/` | Оркестратор: cookies → strategy chain → writer |
| `DirectFileExtractionStrategy` | `data/link/` | HEAD-probe + GET; принимает `extraHeaders` (S0171) |
| `InvisibleWebViewExtractionStrategy` | `data/link/` | JS-рендеринг + DOM_DISCOVERY_SCRIPT |
| `NewPipeSiteExtractionStrategy` | `data/link/nolegal/` | GPL extractor via NewPipeExtractor v0.24.0 |
| `NewPipeOkHttpDownloader` | `data/link/nolegal/` | OkHttp bridge для NewPipe |
| `NoLegalLinkDownloadModule` | `di/` (noLegal sourceSet) | Hilt multibinding для `UrlExtractionStrategy` |
| `EncryptedCookieStore` | `data/link/cookie/` | AES256-GCM cookie store, multi-account |
| `LinkDownloadCookieJar` | `data/link/cookie/` | OkHttp CookieJar с eTLD+1 wildcard (S0171) |
| `LinkDownloadSessionContext` | `data/link/cookie/` | Per-run account cookie injection |
| `KnownAuthResources` | `data/link/auth/` | Реестр известных соцсетей с loginUrl |
| `WebViewAuthDialogFragment` | `ui/share/auth/` | In-app WebView auth harvest |
| `AuthSessionRepository` | `domain/repository/` | Multi-account facade над EncryptedCookieStore |

**Текущее покрытие extraction по NewPipeExtractor v0.24.0:**

- YouTube (`youtube.com`, `youtu.be`) — video/audio streams + playlists + MAX_BATCH_ITEMS=30
- YouTube Music (`music.youtube.com`) — входит в YouTube service
- SoundCloud — audio streams + playlists
- MediaCCC — conference talks
- PeerTube — federated video instances
- Bandcamp — audio streams
- Odysee/LBRY — через NewPipe service (если в ALLOWED_SERVICE_IDS)

`ALLOWED_SERVICE_IDS` сейчас: `{YouTube, SoundCloud, MediaCCC, PeerTube, Bandcamp}`.

**Текущее покрытие InvisibleWebViewExtractionStrategy (standard + noLegal):**

S0171 добавил:
- TikTok: desktop UA override → `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON parsing
  - `downloadAddr` (watermark-free, приоритет)
  - `bitrateInfo[0].PlayAddr.UrlList[0]` (high-quality fallback)
  - `playAddr` (fallback)
  - CDN headers: `Referer: https://www.tiktok.com/`, desktop UA, `Range: bytes=0-`
- Instagram: CDN replay headers — `Referer: https://www.instagram.com/` + desktop UA
  - CDN bucket `t50.2886-16` (scontent.cdninstagram.com) требует Referer
  - Token `oh=` в URL уже является auth-credential; Cookie не нужна для публичного контента
  - Подтверждено yt-dlp `InstagramIE._HEADERS` и youtube-dl issue #29736

**`KnownAuthResources` уже включает:** Instagram, Threads, Pinterest, TikTok, X (Twitter), DeviantArt, Reddit, Tumblr, Flickr, ArtStation — все с loginUrl для WebViewAuthDialog.

### Реальные ограничения текущего состояния

**Что НЕ работает надёжно в standard (InvisibleWebView limitations):**

1. **Instagram galleries/carousels** — несколько медиа в одном посте. InvisibleWebView видит preview-изображение, но не все элементы галереи. Нужен API endpoint:
   `https://i.instagram.com/api/v1/media/<shortcode_to_media_id>/info/` с `sessionid` cookie.

2. **Instagram Stories/Reels** — требуют cookie `sessionid` для авторизованного пользователя. Без cookie работает только публичный контент.

3. **X (Twitter) videos** — Twitter API v2 заблокирован для бесплатного доступа. Текущий InvisibleWebView может перехватить CDN URL из `shouldInterceptRequest`, но Twitter CDN (`video.twimg.com`) выдаёт signed URL без Referer. Нестабильно.

4. **DeviantArt high-res** — `__NEXT_DATA__` JSON injection (аналог TikTok rehydration), но для изображений: `content.imageSrc` / `fullview` / `download.src`. InvisibleWebView не умеет парсить этот путь и возвращает thumbnail.

5. **Reddit video** — `v.redd.it` hosted videos имеют DASH-манифест, где audio и video разделены. Нужен remux. Стандартный pipeline не делает remux.

6. **Pinterest** — высокое разрешение изображений в `closeup_images[].url_list` в JSON-LD, но InvisibleWebView видит только thumbnail из `<img>`.

7. **Vimeo** — CDN URLs требуют `Referer: https://vimeo.com/` и JWT token, который новый для каждого просмотра. Иногда работает через InvisibleWebView, но не стабильно.

### yt-dlp как reference для extraction patterns

yt-dlp (Unlicense, fork youtube-dl) покрывает **1800+ сайтов**. Для ключевых платформ:

**Instagram (`instagram.com`):**
```python
_HEADERS = {
    'Referer': 'https://www.instagram.com/',
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)...',
    'Accept': 'video/mp4,video/*;q=0.9,*/*;q=0.8',
}
```
- GraphQL endpoint: `https://www.instagram.com/graphql/query/?query_hash=...&variables={"shortcode":"<code>"}`
- Media ID из URL: `https://www.instagram.com/p/<shortcode>/` → media_id через shortcode алгоритм
- Gallery: `edge_sidecar_to_children.edges[].node.video_url` / `.display_url`
- CDN: `scontent.cdninstagram.com` — Referer-gated (instagram.com) + desktop UA. Bucket `t50.2886-16` vs `t15.5256-10` (Threads, без Referer)
- Story: требует `sessionid` cookie; endpoint `/api/v1/feed/reels_media/?reel_ids=<user_id>`

**TikTok (`tiktok.com`):**
- Desktop UA → полная SSR страница → `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON (уже реализовано в DOM_DISCOVERY_SCRIPT)
- JSON path:
  ```
  .__DEFAULT_SCOPE__["webapp.video-detail"].itemInfo.itemStruct.video
    .downloadAddr    ← watermark-free (уже в DOM_DISCOVERY_SCRIPT, приоритет)
    .bitrateInfo[0].PlayAddr.UrlList[0]  ← high bitrate (уже в DOM_DISCOVERY_SCRIPT)
    .playAddr        ← fallback (уже в DOM_DISCOVERY_SCRIPT)
  ```
- CDN hosts: `v19.tiktokcdn.com`, `v16-webapp-prime.tiktok.com`, `v19-webapp.tiktok.com`, `byteoversea.com`
- CDN требует: `Referer: https://www.tiktok.com/` + desktop UA + `Range: bytes=0-`
- eTLD+1 wildcard в `LinkDownloadCookieJar` (S0171) форвардит `sessionid` cookie с `tiktok.com` на CDN субдомены

**DeviantArt (`deviantart.com`):**
- `__NEXT_DATA__` JSON injection (Next.js SSR): `<script id="__NEXT_DATA__" type="application/json">`
- JSON path: `props.pageProps.deviationInfo` → `fullview.src` (full resolution) / `download.src`
- Для mature content нужна авторизация: cookie `auth` + `userinfo` + `auth_secure`
- OAuth2 API: `https://www.deviantart.com/api/v1/oauth2/deviation/download/<deviationid>?mature_content=true`
- gallery-dl DeviantArt extractor: OAuth2-based, поддерживает galleries, collections, watchlist
- Текущий InvisibleWebView возвращает thumbnail (`<img>` из DOM), а не `fullview`

**Scrapfly findings (2026, TikTok):**
- Тот же `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON path подтверждён
- Дополнительный путь для поиска видео: `.__DEFAULT_SCOPE__["seo.abtest"]` как fallback при изменении schema
- Headless browser (puppeteer/playwright) нужен только когда SSR не отдаёт нужный JSON — в нашем случае WebView с desktop UA достаточен
- TikTok иногда вращает JSON ключи при A/B тестах: `itemInfo` → `videoData` → `item`. Нужен defensive parsing с fallback

**X (Twitter) `x.com`:**
- CDN: `video.twimg.com` — signed URLs, Referer НЕ требуется
- Но API для получения медиа URL: `https://api.twitter.com/1.1/statuses/show.json?id=<tweet_id>` — требует Bearer token
- Guest token flow: `POST https://api.twitter.com/1.1/guest/activate.json` с application Bearer → гостевой токен
- yt-dlp использует этот flow без auth (публичные твиты)
- InvisibleWebView перехватывает CDN URL через `shouldInterceptRequest` — работает, но нестабильно при lazy-load

**Reddit (`reddit.com`):**
- Video: `v.redd.it/<id>/DASH_1080.mp4` (video only) + `v.redd.it/<id>/DASH_audio.mp4`
- Нужен remux для объединения потоков
- Без remux: либо скачивать только video дорожку (без звука), либо только audio
- gallery-dl Reddit extractor: поддерживает `i.redd.it` (images), `v.redd.it` (video — video-only без remux)
- JSON API: `https://www.reddit.com/comments/<id>.json` → `secure_media.reddit_video.fallback_url`

**Flickr (`flickr.com`):**
- API: `https://api.flickr.com/services/rest/?method=flickr.photos.getSizes&photo_id=<id>&api_key=...`
- Public API key достаточен для публичных фото
- `Original` size URL из `sizes.size[]` array
- gallery-dl Flickr extractor: требует API key, поддерживает albums/photosets, favorites

**ArtStation (`artstation.com`):**
- API: `https://www.artstation.com/projects/<hash>.json` — полный JSON с `assets[].image_url` (full resolution)
- Никакой auth не нужен для публичных проектов
- `assets[].has_image=true` → `assets[].image_url` — прямой CDN URL
- Прямо map'ится на `DirectFileExtractionStrategy` после извлечения URL

**Vimeo (`vimeo.com`):**
- Публичное видео: `https://player.vimeo.com/video/<id>` → config JSON в script tag
- JSON path: `playerConfig.request.files.progressive[].url` — прямые MP4 ссылки
- или HLS: `playerConfig.request.files.hls.cdns.<cdn>.url`
- Требует `Referer: https://vimeo.com/` + desktop UA (аналогично Instagram)

**Dailymotion (`dailymotion.com`):**
- API: `https://api.dailymotion.com/video/<id>?fields=stream_h264_url,stream_h264_hd_url,...`
- Public API без auth для публичных видео
- Stream URLs c CDN напрямую

### gallery-dl как альтернативный reference для image-focused sites

gallery-dl (GPLv2, ~550 поддерживаемых сайтов) специализируется на image galleries:
- DeviantArt: OAuth2, full API, bulk download by username/gallery/collection
- Pixiv: требует login (R-18 контент), поддерживает ugoira (ZIP→GIF/APNG)
- ArtStation: без API key, полные проекты
- Flickr: API key, albums/photosets
- Tumblr: API + web scraping hybrid
- Pinterest: boards, sections, pins
- Instagram: images/carousels (с sessionid cookie для private)
- Twitter/X: images из твитов

**Лицензия gallery-dl**: GPLv2 — та же redistribution-проблема, что и NewPipeExtractor. Допустимо только в `noLegal` sourceSet.

---

## Пакетные кандидаты для noLegal — сравнительный анализ

*Ключевой вопрос: какой пакет/механизм даёт реально рабочую загрузку для YouTube + Instagram + Threads + Facebook + TikTok и при этом интегрируется в существующую инфраструктуру (UrlExtractionStrategy + EncryptedCookieStore + AuthSessionRepository)?*

### Candidate A — NewPipeExtractor (уже используется, v0.24.0 → апгрейд)

**Покрывает:**
- YouTube / YouTube Music ✅ (stream, playlist, channel feed, search)
- SoundCloud ✅ (audio streams + playlists)
- Bandcamp ✅, PeerTube ✅, MediaCCC ✅

**НЕ покрывает:**
- Instagram ✗ — NewPipe намеренно НЕ добавил Instagram extractor (позиция авторов — слишком частые поломки API)
- TikTok ✗ — аналогично, нет в NewPipeExtractor
- Facebook ✗
- Threads ✗

**Апгрейд v0.24.0 → latest (`v0.24.5` или выше):**
- NewPipeExtractor активно развивается; `v0.24.0` от 2023, последние теги — `v0.24.5+`
- Апгрейд даёт: исправления YouTube Innertube API breaks, обновление SoundCloud client ID rotation, Bandcamp fix
- Интеграция: просто изменить версию в `build.gradle.kts`; API совместимость сохраняется
- Лицензия: GPL-3.0 — уже решено (noLegal только)

**Вывод:** надёжный для YouTube/YTMusic, не решает Instagram/TikTok/Facebook. Апгрейд версии — первый шаг.

---

### Candidate B — yt-dlp как JSON-only CLI (без Python, через pre-compiled binary)

**Что это:** yt-dlp поддерживает `--dump-json` режим — вместо скачивания выводит JSON с URLs, форматами, метаданными. Kotlin wrapper делает только JSON-парсинг.

**Покрывает 1800+ сайтов**, включая:
- YouTube ✅, YouTube Music ✅ (плейлисты, каналы, поиск `ytsearch:`)
- Instagram ✅ (публичные + sessionid cookie → `instagram_sessionid=<value>` в cookies file)
- TikTok ✅ (watermark-free через `downloadAddr`)
- Facebook ✅ (публичные видео, Basic HTML fallback)
- Threads ✅ (через Instagram extractor)
- Twitter/X ✅ (guest token flow)
- Vimeo ✅, Dailymotion ✅, Flickr ✅, Reddit ✅ (с remux через ffmpeg или video-only)
- Bandcamp ✅, SoundCloud ✅, PeerTube ✅, Odysee ✅

**Проблема: Android W^X policy.** Нельзя скачать и запустить бинарник. Но:

**Путь A — yt-dlp bundled как `libytdlp.so` в APK `jniLibs/arm64-v8a/`:**
- PyInstaller компилирует yt-dlp + Python runtime в один ELF binary
- Переименовать в `libytdlp.so`, упаковать в `src/noLegal/jniLibs/arm64-v8a/`
- `System.loadLibrary("ytdlp")` загружает как native lib — выполняется из APK без `W^X` нарушения
- `ProcessBuilder` запускает распакованный binary через `context.applicationInfo.nativeLibraryDir`
- IPC: pipe-based JSON stdin/stdout (`yt-dlp --dump-json <url>`)
- Ограничение: только arm64-v8a (достаточно для 99%+ Android-устройств 2024+); x86/x86_64 не поддерживается в этой схеме
- APK size overhead: ~35-50 MB для Python + yt-dlp + зависимости (compressed)

**Путь Б — Chaquopy (JVM Python runtime):**
- `pip install yt-dlp` в gradle build time → встраивается в APK
- `com.chaquo.python.Python.getInstance().getModule("yt_dlp")` → вызов Python функций напрямую
- Startup: ~1-3 сек первый запуск (распаковка в cache dir), повторные — мгновенно
- APK size overhead: ~40-60 MB
- Chaquopy Community edition: Apache 2.0; коммерческий для production использования требует лицензии
- Преимущество: yt-dlp обновляется через gradle без перекомпиляции binary

**Интеграция с существующей системой auth:**
- yt-dlp принимает `--cookies-from-browser` или `--cookies <file>` (Netscape cookie file format)
- `EncryptedCookieStore.loadForAccount()` → serialize в Netscape format → temp file → `--cookies <path>` → удалить после запуска
- `AccountId` / sessionid из `EncryptedCookieStore` прокидывается в yt-dlp как cookie для instagram.com, tiktok.com и т.д.
- Это **прямое подключение к существующей auth инфраструктуре** — пользователь логинится через `WebViewAuthDialogFragment`, cookies сохраняются в `EncryptedCookieStore`, yt-dlp их читает

**Kotlin integration surface:**
```kotlin
// Примерный wrapper (src/noLegal/java/.../nolegal/YtDlpExtractionStrategy.kt)
class YtDlpExtractionStrategy @Inject constructor(
    private val cookieStore: EncryptedCookieStore,
    private val direct: DirectFileExtractionStrategy,
    @ApplicationContext private val context: Context,
) : UrlExtractionStrategy {
    override val id = "ytdlp"

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        // Быстрый probe: только --simulate --print webpage_url, таймаут 5 сек
        val result = runYtDlp(url, listOf("--simulate", "--print", "webpage_url"), timeout = 5_000)
        if (result.exitCode == 0) ProbeResult.Applicable(null, null) else ProbeResult.NotApplicable
    }

    override suspend fun open(url: String, onProgress: (Long, Long?) -> Unit): OpenResult = withContext(Dispatchers.IO) {
        val cookieFile = writeCookiesForUrl(url)  // EncryptedCookieStore → Netscape format
        try {
            val json = runYtDlp(url, buildArgs(url, cookieFile), timeout = 30_000)
            parseYtDlpJson(json.stdout, url, onProgress)
        } finally {
            cookieFile?.delete()
        }
    }
}
```

**Лицензия yt-dlp:** Unlicense (public domain) — redistributable без ограничений.  
**Лицензия зависимостей yt-dlp:** `mutagen` (GPL-2.0), `pycryptodomex` (BSD), `brotli` (MIT) — при bundled binary всё включено в binary, redistribution лицензии зависят от интерпретации "static linking GPL". При sideload-only sборке — acceptably риск.

---

### Candidate C — instagram4j (Java/Android-native Instagram API client)

**Репо:** `https://github.com/instagram4j/instagram4j`  
**Лицензия:** Apache 2.0 — ✅ redistributable, нет GPL проблем  
**Что даёт:**
- Полный Instagram Private API (Mobile API v1): `GET /api/v1/media/<media_id>/info/`
- Carousels: `carousel_media[]` в response
- Stories: `GET /api/v1/feed/user/<user_id>/story/`
- Reels: `GET /api/v1/clips/user/` + `GET /api/v1/media/<id>/info/`
- Прямые CDN URL (`image_versions2.candidates[].url`, `video_versions[].url`) — уже Referer-free при правильном User-Agent (mobile app UA)

**Как интегрируется:**
- Auth: Instagram API login через `instagram4j` → access_token / session cookie → сохранить в `EncryptedCookieStore` под host `instagram.com`
- Проблема: instagram4j требует логин через username/password API — это **не WebView-based flow**, который сейчас используется
- Альтернатива: извлечь `sessionid` cookie из `EncryptedCookieStore` (сохранённый через WebViewAuthDialog) и передать в instagram4j `IGClient.withCookies(sessionid)` конструктор

**Главный risk:** Instagram Mobile API нестабильна — Instagram регулярно блокирует неофициальные клиенты, меняет подписи запросов (X-IG-App-ID, device fingerprint). instagram4j последний стабильный commit — 2022. Maintenance-risk очень высокий.

**Вывод:** Apache 2.0 привлекательно, но слишком высокий maintenance-risk + несовместимый auth flow.

---

### Candidate D — cobalt (self-hosted REST API)

**Репо:** `https://github.com/imputnet/cobalt`  
**Лицензия:** AGPL-3.0  
**Что даёт:** REST API `POST /api/json` → `{"url": "..."}` → ответ с download URL  
**Покрывает:** YouTube, TikTok, Instagram, Twitter/X, Facebook, Reddit, Vimeo, SoundCloud + ещё ~20 сайтов  

**Интеграция:**
- Новый `CobaltExtractionStrategy` делает HTTP POST к configurable endpoint
- `CobaltSettings.serverUrl` в `SettingsRepository` — пользователь настраивает свой cobalt instance
- Не требует cookies — cobalt сам управляет сессиями на своей стороне

**Блокер:** AGPL-3.0 — API call к серверу не является redistribution, можно использовать клиент в коммерческом приложении. Но if cobalt сервер недоступен — стратегия бесполезна. Внешняя зависимость нарушает принцип автономности.

**Вывод:** хороший fallback для power-user с self-hosted cobalt. НЕ решение для out-of-box скачивания.

---

### Candidate E — gallery-dl (Python, via Chaquopy) для image-heavy platforms

**Лицензия:** GPL-2.0 → только noLegal  
**Что даёт в дополнение к yt-dlp:**
- DeviantArt galleries (bulk по username/collection) — yt-dlp умеет только single deviation
- Pixiv artist feed (bulk) — yt-dlp не поддерживает Pixiv
- Pinterest boards/sections (bulk)
- Tumblr blogs (bulk)
- ArtStation (bulk по username) — yt-dlp поддерживает только single project

**Интеграция:** аналогична yt-dlp via Chaquopy — `gallery-dl --dump-json <url>` → JSON → batch URLs  
**APK overhead:** ~5 MB (gallery-dl) + Python runtime (shared с yt-dlp если оба через Chaquopy — один runtime)

**Вывод:** комплементарен yt-dlp для image galleries. Если yt-dlp через Chaquopy реализован — gallery-dl добавляется минимальным overhead.

---

### Итоговая приоритетная матрица пакетных кандидатов

| Кандидат | YouTube/YTMusic | Instagram | Threads | TikTok | Facebook | Универсальность | Интеграция с auth | APK overhead | Maintenance | Приоритет |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| NewPipeExtractor (апгрейд) | ✅ | ✗ | ✗ | ✗ | ✗ | 6 сайтов | ✗ (не нужна) | +0 MB | Высокое | 🔴 Срочно |
| **yt-dlp via Chaquopy** | ✅ | ✅ | ✅ | ✅ | ✅ | **1800+** | **✅ cookies passthrough** | +40-60 MB | Высокое | 🟢 Приоритет 1 |
| yt-dlp as `libytdlp.so` | ✅ | ✅ | ✅ | ✅ | ✅ | 1800+ | ✅ cookies passthrough | +35-50 MB | Среднее | 🟡 Приоритет 2 |
| Kotlin-native extractors | ✅ (NP) | Частично | Частично | ✅ (S0171) | ✗ | 5-10 сайтов | ✅ native | +0 MB | Низкое | 🟡 Дополнение |
| instagram4j | ✗ | ✅ | ✗ | ✗ | ✗ | 1 сайт | ⚠️ Несовместимо | +2 MB | ❌ Мертвый | 🔴 Rejected |
| cobalt API | ✅ | ✅ | ✅ | ✅ | ✅ | ~25 | ✗ (server-side) | +0 MB | Зависит от сервера | 🟡 Fallback |
| gallery-dl via Chaquopy | ✗ | ✅ | ✗ | ✗ | ✗ | 550 (images) | ✅ cookies | +5 MB (shared) | Высокое | 🟡 Дополнение |

---

## Просто и быстро

*Кандидаты с минимальным integration cost, расположены по убыванию ожидаемой ценности.*

**1. Расширение `ALLOWED_SERVICE_IDS` в `NewPipeSiteExtractionStrategy`**
- Добавить Odysee (`ServiceList.Odysee`) в allowlist
- Одна строка кода, нулевые зависимости — Odysee service уже в NewPipeExtractor
- Лицензия: MIT (Odysee service в NewPipe под MIT)
- Объём: < 5 строк

**2. `ArtStationSiteExtractionStrategy` в `noLegal`**
- JSON API `https://www.artstation.com/projects/<hash>.json` — без API key, без auth, публичные проекты
- `assets[].image_url[]` → batch of `DirectFileExtractionStrategy.open()` вызовов
- Pattern: аналог `NewPipeSiteExtractionStrategy.openPlaylist()` — создать `OpenResult.Batch`
- `KnownAuthResources` уже регистрирует `artstation.com` с loginUrl
- Лицензия: pure Kotlin, нет сторонних зависимостей
- Объём: ~100 строк

**3. `VimeoSiteExtractionStrategy` в `noLegal`**
- `player.vimeo.com/video/<id>` config JSON → `progressive[].url` (прямые MP4) или HLS manifest
- Referer: `https://vimeo.com/` + desktop UA (уже константа `DESKTOP_CHROME_UA` в InvisibleWebView)
- Лицензия: pure Kotlin
- Объём: ~120 строк

**4. `DeviantArtSiteExtractionStrategy` в `noLegal` — через `__NEXT_DATA__` scraping**
- `GET https://www.deviantart.com/author/art-title` → parse `<script id="__NEXT_DATA__">` → `props.pageProps.deviationInfo.fullview.src`
- Referer: `https://www.deviantart.com/`
- Для публичных изображений auth не нужна; для mature content — `sessionid` cookie из KnownAuthResources flow
- Лицензия: pure Kotlin
- Объём: ~150 строк

**5. `DailymotionSiteExtractionStrategy` в `noLegal`**
- Public API: `https://api.dailymotion.com/video/<id>?fields=stream_h264_hd_url,...`
- Без API key для публичных видео
- Лицензия: pure Kotlin
- Объём: ~80 строк

---

## Сложно но возможно

*Кандидаты с известным путём, но значительным объёмом или licensing workaround.*

**1. Instagram gallery/carousel extractor в `noLegal`**
- Type blocker: `redistribution-license` — нет GPL компонентов, но нужна точная реализация Instagram GraphQL
- Path:
  1. Shortcode → media_id: алгоритм из yt-dlp `_decode_media_id(shortcode)` (публичный алгоритм)
  2. GraphQL query: `https://www.instagram.com/graphql/query/?query_hash=<hash>&variables={"shortcode":"<code>"}`
  3. `edge_sidecar_to_children.edges[].node.video_url` / `.display_url`
  4. CDN headers: Referer instagram.com + desktop UA (уже реализовано в S0171 InvisibleWebView)
- Проблема: Instagram периодически меняет `query_hash`. Нужен механизм обновления или fallback на InvisibleWebView
- Для Stories/Reels private content: `sessionid` cookie (уже есть в KnownAuthResources flow)
- Объём: ~300 строк + тест

**2. X (Twitter) extractor в `noLegal`**
- Type blocker: `maintenance-risk` — Twitter меняет guest token flow несколько раз в год
- Path:
  1. Guest token: `POST https://api.twitter.com/1.1/guest/activate.json` с hardcoded app Bearer token
  2. Tweet API: `GET https://api.twitter.com/1.1/statuses/show.json?id=<id>` с `Authorization: Bearer <app_token>` и `x-guest-token: <guest>`
  3. `extended_entities.media[].video_info.variants[]` → highest bitrate MP4
- App Bearer token: публично известен из yt-dlp исходников, но Twitter может его отозвать
- Объём: ~200 строк

**3. Reddit video+audio remux в `noLegal`**
- Type blocker: `heavy-runtime` — remux требует нативного ffmpeg или медленного Java AVMux
- DASH video: `v.redd.it/<id>/DASH_1080.mp4`
- DASH audio: `v.redd.it/<id>/DASH_audio.mp4`
- Оба URL без авторизации для публичных постов
- JSON: `https://www.reddit.com/comments/<id>.json?feature=linked_listing` → `secure_media.reddit_video`
- Без remux: скачать video-only (приемлемо для silent clips) или только audio
- С remux: нужен noLegal-only ffmpeg AAR — тяжёлый (15–30 MB)
- Объём (без remux): ~150 строк; с remux: зависит от выбранного ffmpeg binding

**4. Pixiv extractor в `noLegal`**
- Type blocker: `redistribution-license` + `privacy-risk`
- API v1: `https://app-api.pixiv.net/v1/illust/detail?illust_id=<id>` с OAuth Bearer
- OAuth flow Pixiv: PKCE-based, требует регистрацию app (бесплатно, но нужен аккаунт Pixiv)
- Альтернатива: использовать cookie-based auth через KnownAuthResources flow
- Ugoira (ZIP анимации) → нужен decode в APNG или GIF = дополнительный runtime
- R-18 контент требует строгой UI-маркировки; сама по себе лицензия изображений — всё права художника
- Объём: ~400 строк (без ugoira decode)

**5. Bundled yt-dlp Python через Chaquopy (JVM Python bridge)**
- Type blocker: `heavy-runtime` + `redistribution-license`
- Chaquopy: Python 3 runtime для Android, ~30-50 MB overhead
- Даёт доступ к полным 1800+ сайтам yt-dlp немедленно
- APK size: +50-80 MB (Python runtime + yt-dlp wheels + зависимости)
- Startup: первый запуск ~2-5 сек на unpacking
- yt-dlp лицензия: Unlicense (public domain) — redistribution допустим
- Зависимости yt-dlp: частично GPL (mutagen для аудио тегов и др.) — нужна audit
- Chaquopy лицензия: Apache 2.0 (коммерческая версия), MIT lite version
- Path: Chaquopy gradle plugin → `python { pip { install("yt-dlp") } }` → `Python.start()` → `PyObject` bridge
- Объём конфигурации: ~50 строк Kotlin bridge + gradle setup

---

## Фантастика, но хочется

*Технически достижимо, но требует героических усилий или неразрешённых dependency.*

**1. yt-dlp sidecar binary (ARM64 ELF + PyInstaller)**
- Bundled pre-built yt-dlp executable для Android ARM64
- Запуск через `ProcessBuilder`, IPC через stdin/stdout JSON
- Ограничения: Android `W^X` policy запрещает исполнение скачанных бинарников;
  только APK-bundled native libs (`lib/*.so`) выполняются без `MANAGE_EXTERNAL_STORAGE`
- Workaround: .so extension + `System.load()` как трамплин — хак, не надёжный путь
- Реальный путь: Termux companion app с `com.termux.RUN_COMMAND` intent — но это внешняя зависимость

**2. Full gallery-dl Python через Chaquopy для image galleries**
- gallery-dl даёт bulk DeviantArt gallery, Pixiv artist feed, Flickr album, Pinterest board
- ~20 MB Python overhead + ~5 MB gallery-dl
- В отличие от yt-dlp, gallery-dl сильнее ориентирован на images/galleries — комплементарен, не дублирует
- Лицензия gallery-dl: GPLv2 — обязательно noLegal

**3. Cobalt API bridge (`cobalt.tools`)**
- cobalt.tools — открытый API для загрузки медиа с YouTube, TikTok, Twitter, Instagram и ещё ~20 сайтов
- Лицензия: AGPL-3.0 — redistribution как часть коммерческого APK запрещён, но API call к внешнему серверу — нет
- Блокер: внешняя зависимость от публичного сервиса (cobalt.tools может упасть / изменить политику)
- Self-hosted вариант: cobalt сервер можно запустить локально, но это выходит за рамки мобильного приложения
- Потенциально: использовать как fallback для сайтов, не покрытых native extractors

---

## Блокеры

| Блокер | Тип | Конкретная причина |
|---|---|---|
| NewPipeExtractor GPL-2.0 | `redistribution-license` | Изолирован в noLegal — уже решено архитектурно |
| yt-dlp Unlicense | — | НЕ блокер для redistribution; отдельные зависимости требуют audit |
| gallery-dl GPLv2 | `redistribution-license` | Допустимо только в noLegal sourceSet |
| Instagram query_hash volatility | `maintenance-risk` | Instagram меняет GraphQL hash; нужен fallback или dynamic fetch |
| X/Twitter Bearer token rotation | `maintenance-risk` | Hardcoded Bearer ротируется Twitter периодически |
| yt-dlp via Chaquopy | `heavy-runtime` | +50-80 MB APK, 2-5 сек init; неприемлемо для market, допустимо sideload |
| Android W^X policy | `security-risk` | Нельзя запускать скачанные бинарники; только APK-bundled .so |
| Reddit DASH remux | `heavy-runtime` | Нативный ffmpeg = +15-30 MB; без него — video-only (нет звука) |
| Pixiv OAuth регистрация | `external-binary` | Нужен зарегистрированный app client_id; публично известен из приложений |
| DeviantArt mature content | `privacy-risk` | Контент только для зарегистрированных пользователей; требует явной user opt-in UI |
| Cobalt API external service | `maintenance-risk` | Внешний сервис, не под контролем; uptime SLA отсутствует |

---

## Как новые noLegal extractors присоединяются к существующей инфраструктуре

Это центральный архитектурный факт для всех follow-up спеков.

### Принцип «overlay without collision»

noLegal flavor не заменяет и не модифицирует стандартный код — он добавляет свои стратегии **поверх** него через Hilt multibinding. Механизм:

- `LinkExtractionRegistry` обходит все зарегистрированные `UrlExtractionStrategy` в порядке приоритета: `site → direct → html → dynamic`
- Standard flavor регистрирует только `direct`, `html`, `dynamic` стратегии
- noLegal flavor добавляет `site` стратегии (NewPipe + будущие yt-dlp и platform-native) через отдельный `NoLegalLinkDownloadModule` в `src/noLegal/di/`
- `site` стратегии имеют более высокий приоритет и вызываются первыми; при `ProbeResult.NotApplicable` цепочка передаётся стандартным стратегиям

Это означает: **любые улучшения в стандартных стратегиях (`InvisibleWebViewExtractionStrategy`, `DirectFileExtractionStrategy`) автоматически доступны и в noLegal** — как fallback после site-стратегий или как переиспользуемые компоненты внутри них. Например, `NewPipeSiteExtractionStrategy` напрямую вызывает `DirectFileExtractionStrategy.open()` для CDN re-fetch — тот же код что использует standard.

**Shared infrastructure — один код, оба flavor:**

| Компонент | Где живёт | Используют оба? |
|---|---|---|
| `EncryptedCookieStore` | `data/link/auth/` (main sourceSet) | ✅ — standard WebViewAuth + noLegal site extractors |
| `AuthSessionRepository` | `data/link/auth/` | ✅ |
| `WebViewAuthDialogFragment` | `ui/link/auth/` | ✅ — один диалог для всех platform-specific логинов |
| `KnownAuthResources` | `data/link/auth/` | ✅ — loginUrl registry расширяется, не дублируется |
| `DirectFileExtractionStrategy` | `data/link/` | ✅ — финальный CDN download для всех extractors |
| `LinkAutoDownloadCoordinator` | `domain/usecase/link/` | ✅ — оркестратор общий |
| `LinkDownloadCookieJar` | `data/link/cookie/` | ✅ — eTLD+1 wildcard работает для обоих |

Следствие: при добавлении нового сайта в `KnownAuthResources.loginUrl` или расширении `EncryptedCookieStore` — auth-flow автоматически работает и в standard (WebView) и в noLegal (site extractor с cookie passthrough). Дублирование auth кода не требуется.

---

**Точка расширения:** `UrlExtractionStrategy` multibinding в `NoLegalLinkDownloadModule`.

Каждый новый site extractor:
1. Реализует `UrlExtractionStrategy` в `src/noLegal/java/...data/link/nolegal/`
2. Регистрируется через `@Binds @IntoSet` в `NoLegalLinkDownloadModule`
3. Автоматически встаёт в `LinkExtractionRegistry` с id `"site_<name>"` (или расширяет существующий `NewPipeSiteExtractionStrategy`)
4. Получает доступ через Hilt inject:
   - `EncryptedCookieStore` — read cookies для auth-bound сайтов
   - `DirectFileExtractionStrategy` — финальный download после URL extraction
   - `LinkDownloadSessionContext` — per-run account cookies (если пользователь выбрал аккаунт)

**CDN headers pattern (уже реализован в S0171):**
```kotlin
// Уже есть в InvisibleWebViewExtractionStrategy.cdnReplayHeaders()
val headers = buildMap {
    put("User-Agent", DESKTOP_CHROME_UA)
    put("Referer", "$pageOrigin/")    // instagram.com, tiktok.com, vimeo.com ...
    put("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
    if (isTikTokCdn(host)) put("Range", "bytes=0-")
}
return direct.open(cdnUrl, onProgress, headers)  // DirectFileExtractionStrategy.open(extraHeaders)
```

Новые extractors должны переиспользовать этот же паттерн, не дублировать его.

**Auth flow pattern (уже реализован):**
```
KnownAuthResources.matchHost(host)        // есть loginUrl?
    → WebViewAuthDialogFragment           // пользователь логинится
    → WebViewAuthViewModel.saveSession()  // cookies в EncryptedCookieStore
    → LinkAutoDownloadCoordinator.applySessionContext()  // cookies в LinkDownloadCookieJar
    → EncryptedCookieStore.loadForAccount(host, accountId) → OkHttp headers
```
Для Instagram, TikTok, DeviantArt, X, Flickr, Pinterest, Reddit — loginUrl уже задан в `KnownAuthResources`.

**Расширение `KnownAuthResources`** для новых сайтов: Vimeo, Dailymotion, ArtStation (уже есть!) — добавить `loginUrl`.

---

## Потенциальные follow-up спеки

**1. `S0xxx` — NewPipeExtractor version bump + Odysee allowlist (Quick win)**
- Апгрейд `v0.24.0` → `v0.24.5+` в `build.gradle.kts`
- Добавить `ServiceList.Odysee.serviceId` в `ALLOWED_SERVICE_IDS`
- Проверить breaking changes в API (обычно нет — NewPipe API стабильна)
- Effort: 1 день, нулевой risk

**2. `S0xxx` — yt-dlp via Chaquopy: feasibility + UrlExtractionStrategy wrapper (приоритет)**
- Цель: покрыть YouTube/Instagram/TikTok/Facebook/Threads/X как единый «universal extractor» в noLegal
- Шаги:
  1. Chaquopy gradle setup в `noLegal` sourceSet only
  2. `yt-dlp --dump-json <url>` → parse `formats[]` → select best → `direct.open(url, extraHeaders)`
  3. Cookie bridge: `EncryptedCookieStore.loadForAccount()` → Netscape cookie file format → temp file → `--cookies` arg → delete after use
  4. Auth passthrough тест: сохранить Instagram sessionid через WebViewAuthDialog → загрузить private post через yt-dlp
  5. TikTok A/B schema drift: yt-dlp уже обрабатывает это внутри; снимает maintenance burden с DOM_DISCOVERY_SCRIPT
  6. APK size audit: измерить compressed size, startup time, RAM при первом вызове
- Go/No-Go: > 80 MB compressed overhead или > 4 сек startup → fallback на Candidate libytdlp.so подход
- Лицензия yt-dlp: Unlicense — redistribution OK; зависимости audit как часть шага

**3. `S0xxx` — Kotlin-native extractors batch (ArtStation + Vimeo + DeviantArt + Dailymotion)**
- Четыре сайта из «просто и быстро» — чистый Kotlin, нет GPL зависимостей
- ArtStation и DeviantArt приоритетнее (image platforms); Vimeo и Dailymotion — video
- Единая спека, 4 стратегии, ~600 строк суммарно

**4. `S0xxx` — Instagram gallery + Stories extractor in noLegal**
- Реализация только если yt-dlp Chaquopy decision = Rejected (yt-dlp покрывает Instagram лучше)
- Иначе — дублирует yt-dlp coverage; нецелесообразно

---

*Документ будет дополнен при следующей итерации ресёрча.*
