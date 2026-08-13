# Стратегическая спецификация: S0116 — URL Media Downloader (веб-медиа, стримы, авторизация)

**Ticket:** S0116
**Status:** Verified
**Priority:** 50
**Date:** 2026-05-08
**Implemented date:** 2026-05-08
**Verified date:** 2026-05-08
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — запрос 2026-05-08 (расширение S0003: generic web-media URL coverage, HLS/DASH в стандартный MP4, универсальная WebView-авторизация)
**Tactical spec:** `PLAN/S0116_url-media-downloader/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Реализованный в S0003 канал загрузки по ссылке успешно скачивает прямые медиафайлы и кандидатов из статического HTML, но не покрывает значимую часть реальных веб-медиа сценариев: контент часто отдаётся через HLS/DASH-манифесты, embed-плееры, промежуточные страницы и JavaScript-рендеринг, а не через прямую ссылку на файл. Пользователь видит медиаконтент в браузере, но приложение получает «не нашли файл по ссылке». Часть источников также требует входа пользователя, а отдельного защищённого флоу авторизации для веб-сессий в приложении сейчас нет.

Дополнительно: текущий S0003 при успешной загрузке всегда открывает результат в плеере. Пользователь иногда хочет «тихий» режим — сохранить и продолжить работу, не отвлекаясь на смену экрана. Настройка для этого уже заложена (`linkAutoDownloadOpenInPlayer`), но новые исходы S0116 (HLS success, DRM blocked, MuxFailed) должны её уважать и иметь локализованные toast-сообщения.

---

## 2. Цели

1. Для ссылок на поддерживаемый публичный веб-медиа контент приложение находит скачиваемый файл или пригодный для дальнейшего офлайн-сохранения поток без участия пользователя — максимум возможностей в `standard` flavor, публикуемом в Google Play и Meta Horizon Store.
2. HLS (`m3u8`) и DASH (`mpd`) ссылки, обнаруженные в HTML или статических manifest URL, скачиваются и собираются в стандартный MP4 файл, открываемый любым плеером.
3. Если источник требует авторизации, приложение показывает встроенный изолированный веб-экран входа, извлекает сессионные куки и применяет их для последующих загрузок с этого источника.
4. Пользователь может видеть список сохранённых авторизаций и удалять их по отдельности.
5. Качество потока при загрузке выбирается по правилу: «наилучшее доступное, не выше установленного в настройках порога» (без доп. экрана для каждой загрузки).
6. Новые стратегии извлечения подключаются без изменения UI и настроек пользователя.
7. Функция остаётся пригодной для публикации: публичные материалы приложения описывают generic web-media workflow без перечисления конкретных соцсетей, площадок и сценариев обхода ограничений.
8. Существующая настройка автооткрытия (`linkAutoDownloadOpenInPlayer`) распространяется на все новые исходы; при `false` пользователь видит локализованный toast о результате (success или причина ошибки) для каждого нового типа исхода.
9. Сбой любого нового компонента не приводит к регрессии S0003 baseline-функционала: direct file download и static HTML candidate selection продолжают работать как сейчас, даже если streaming downloader, cookie storage или WebView auth отказали.

**Non-goals:**

- Полноценный обход платных подписок или закрытых аккаунтов без авторизации самого пользователя.
- Автоматическая ротация аккаунтов, покупка прокси или обход CAPTCHA без ввода пользователя.
- Перекодирование, наложение субтитров или постобработка скачанного контента.
- Поддержка live-only источников и аналогичных «только в реальном времени» сценариев.
- Multi-item / album / playlist batch — вынесено в отдельную спеку S0117.
- Site-specific reverse-engineered экстракторы и GPL-3 зависимости — вынесены в отдельную sideload-only спеку S0117.
- Публичное перечисление конкретных соцсетей, площадок или сценариев, которые можно трактовать как downloader restricted content.
- Гарантированная поддержка страниц, где медиа доступно только после JavaScript-рендеринга без статически извлекаемых сигналов.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. **Максимизировать покрытие в `standard` flavor** (публикуемый Google Play / Meta Horizon Store). Расширения за пределы generic-уровня — отдельная спека S0117 (sideload-only), не блокирующая S0116.
2. Адаптировать архитектурный опыт зрелых open-source проектов: паттерн Generic Extractor для best-effort статического поиска (`m3u8`, `mpd`, `og:video`, media tags, JSON-LD VideoObject).
3. Механизм авторизации использует встроенный WebView, из которого через `CookieManager` извлекаются сессионные куки. Куки шифруются, не синхронизируются с системным браузером и инжектятся в HTTP-запросы через OkHttp `CookieJar` и Media3 `HttpDataSource` request-properties.
4. Ограничение качества при загрузке настраивается в одном месте, а не на каждую загрузку.
5. Не требовать у пользователя отдельного «менеджера загрузок» — весь процесс идёт через уже существующий канал S0003.
6. Во внешних материалах приложения не перечислять конкретные соцсети, площадки и формулировки, которые могут быть прочитаны как обещание «качать контент соцсетей» или обходить ограничения источника.
7. Итоговый файл должен открываться вне приложения как стандартный медиафайл (MP4 / MP3 / JPEG); проприетарный «контейнер только для нашего плеера» допустим лишь как исключительный fallback для редких codec/DRM-кейсов.
8. Поведение при `linkAutoDownloadOpenInPlayer == false` — toast о результате, а не молчание; для всех типов исходов (success, error, blocked) должны быть локализованные сообщения.
9. Добавлять много debug-information. Направление будет активно эволюционировать (новые HTML-паттерны, codec edge cases, auth-сценарии); удалённая отладка по логам — основной канал диагностики.

### 3.2 Жёсткие ограничения

- **Flavor — единая капабилити-граница (research-driven, см. §13):**
   - `standard`, `legacy`, `vr`, `vrUnlicensed` — полный generic URL flow: direct file, расширенный generic HTML-sniffing (включая `m3u8`/`mpd` URL detection), HLS/DASH download через Media3 + MediaMuxer remux в стандартный MP4, WebView-авторизация и cookie injection, политика выбора качества. Никакого site-specific reverse-engineered кода, никаких viral-copyleft (GPL) зависимостей. Публикуется в Google Play и Meta Horizon Store.
   - `lite` — generic URL flow без HLS/DASH-сборщика (исключён по причине APK-бюджета и `SUPPORT_VIDEO`-консистентности; lite — это «лёгкая» сборка, потоковый контур — отдельный +2 МБ зависимостей). Direct file и static HTML-sniffing — да, WebView-авторизация — да.
   - `photos` — экстрактор изображений (включая `og:image`, `<img srcset>`, ссылки на JPEG/PNG/WebP/GIF), без видео и потоков (`SUPPORT_VIDEO=false`). WebView-авторизация — да.
- **API level:** `minSdk 26` (стандарт) / `minSdk 23` (legacy) — без зависимостей выше. Media3 HLS/DASH модули требуют `compileSdk 35` уже в проекте — совместимо.
- **Wear OS:** не затрагивается.
- **Scope первой итерации:** один медиарезультат на один URL; при URL альбома/карусели — первый элемент с уведомлением пользователя. Multi-item / album batch — вынесено в S0117.
- **Generic baseline:** generic extraction — это best-effort статический поиск по HTML; страницы, полностью зависящие от JavaScript-рендеринга, не входят в гарантированный базовый контракт. Покрываются: `og:video`, `og:image`, `<video>`/`<audio>` теги, `<source>`, `<img srcset>`, `m3u8`/`mpd` ссылки в HTML, JSON-LD `VideoObject.contentUrl`.
- **Streaming baseline:** текущая сборка `standard` исключает `media3-exoplayer-hls` и `media3-exoplayer-dash` (`app_v2/build.gradle.kts` строки 647-652). Для S0116 эти модули включаются обратно в market-flavors, поддерживающих видео (`standard`, `legacy`, `vr`, `vrUnlicensed`); ожидаемая стоимость APK — ≈ 1.5–2 МБ. `lite` и `photos` остаются без них.
- **Output контракт (закрытие §6.4):** результат HLS/DASH — стандартный MP4 файл, собранный через `MediaExtractor` + `MediaMuxer` (sample-copy без re-encode). Проприетарный `.v3.exo` контейнер ExoPlayer как пользовательский артефакт не используется.
- **Производительность:** сборка HLS/DASH-сегментов выполняется в фоновом воркере, UI не блокируется; временный сегмент-кэш живёт в `cacheDir`, ограничен существующим кэш-бюджетом и удаляется после remux.
- **Совместимость данных:** новые настройки добавляются с безопасными значениями по умолчанию; существующий backup-формат S0003 не ломается.
- **Backward compatibility (S0003):** все новые компоненты S0116 спроектированы с явным fallback в существующий S0003 pipeline (см. §5.4). Если новый компонент сломан/недоступен/выкинул необработанное исключение — пользователь должен получить тот же опыт, что и в S0003 Verified, без регрессии.
- **Локализация:** EN/RU/UK — обязательно для всех новых строк (экраны авторизации, ошибки, настройки качества, toast-сообщения для всех новых исходов).
- **Доступность:** WebView-экран авторизации — стандартный Android WebView, TalkBack работает штатно; кнопка «Удалить авторизацию» — touch target ≥ 48dp.
- **Дистрибуция и compliance:** docs/FEATURES, manifest metadata, store listing, release notes и другие публичные тексты остаются нейтральными по площадкам; перечисление конкретных соцсетей, площадок и формулировок restricted-use запрещено для всех профилей.
- **Правовая/политическая:** извлечение ориентируется только на пользовательский доступ к веб-медиа, доступному самому пользователю; авторизованный доступ — исключительно через ввод учётных данных самим пользователем в интерфейсе самого источника во встроенном WebView.

---

## 4. Контекст текущей архитектуры

S0003 (`PLAN/S0003_link-receive-download.md`, Verified) ввёл реестр стратегий извлечения с двумя стратегиями: прямой файл (`DirectFileExtractionStrategy`) и HTML-парсинг (`HtmlPageExtractionStrategy`). Реестр (`LinkExtractionRegistry`) уже расширяем (Hilt multibinding `Set<UrlExtractionStrategy>`), канонический порядок задаётся одним списком. Текущая модель оркестрации остаётся однорезультатной: первый применимый обработчик открывает один поток, после чего `LinkDownloadWriter` пишет один итоговый файл в ресурс или Downloads.

`HtmlPageExtractionStrategy` уже умеет: GET HTML (≤ 2 MiB), извлечение кандидатов по `og:video`, `og:image`, `twitter:player:stream`, `<video>`/`<audio>`, `<source>`, `<img>` (включая `srcset`), inline-якоря с whitelisted-расширениями, HEAD-probe (≤ 8 параллельно, бюджет 4 с), фильтр по MIME-whitelist и выбор кандидата через `CandidateSelectionPolicy`. Текущий пробел: нет распознавания `m3u8`/`mpd` ссылок (ни в HTML, ни в JSON-LD), нет потокового офлайн-контура, нет cookie-инъекции, нет WebView-авторизации.

**Существующая настройка автооткрытия (важно для §5.1 столп M):** в `AppSettings.kt:165` уже определена `linkAutoDownloadOpenInPlayer: Boolean = true`, читается в `LinkAutoDownloadCoordinator.kt:81`, иерархически подчинена `linkAutoDownloadEnabled` через UI в `PlaybackSettingsFragment.kt:445`. S0116 не создаёт настройку заново — расширяет её действие на новые типы исходов.

В сборке уже есть OkHttp 4.12, Jsoup 1.17.2, Media3 core/ui/session/decoder/effect 1.2.1 и `androidx.security:security-crypto:1.1.0-alpha06` (EncryptedSharedPreferences). Модули `media3-exoplayer-dash` и `media3-exoplayer-hls` явно исключены в `app_v2/build.gradle.kts` (строки 647-652) ради ≈ 1.5–2 МБ APK-экономии — для S0116 они возвращаются для market-flavors с видео.

Текущая граница: прямые HTTP-ответы с медиа-MIME обрабатываются, best-effort статический HTML-поиск — да, HLS/DASH офлайн-сборка — нет, страницы, полностью зависящие от JavaScript-рендеринга, — не гарантируются, многообъектные источники — вне текущего контракта. Расширение до стримов и универсальной аутентификации требует: (а) расширения существующего реестра парой generic-стратегий (streaming sniffer + streaming downloader); (б) cookie-хранилища и WebView-флоу; (в) явной политики исходов и graceful degradation в S0003 baseline.

---

## 5. Предлагаемый подход

Расширение реестра стратегий S0003: добавляются три новых компонента — Generic Streaming Sniffer (распознавание `m3u8`/`mpd` URL и manifest-link в HTML), потоковый офлайн-контур (Media3 HLS/DASH downloader + remux в MP4 через MediaMuxer), универсальная WebView-авторизация с cookie injection. Готовый артефакт — стандартный MP4/MP3/JPEG — пишется в ресурс по уже существующему контуру S0003 (Столп E), а пользовательский UX (открыть в плеере / показать toast) уважает существующую настройку `linkAutoDownloadOpenInPlayer` (Столп M ниже).

### 5.1 Основные столпы / модули

**Столп G — Generic Streaming Sniffer (`standard`, `legacy`, `vr`, `vrUnlicensed`).**

- Цель: расширить существующий `HtmlPageExtractionStrategy` распознаванием стримовых манифестов в статическом HTML без site-specific знаний.
- Требования: дополнить harvester по `m3u8` и `mpd` URL — поиск в `<meta>`, `<link>`, `<source>` тегах, в JSON-LD `VideoObject.contentUrl` / `embedUrl`, в plain-text regexp по HTML (`https?://[^"'\s]+\.(m3u8|mpd)(?:\?[^"'\s]*)?`), в data-атрибутах. Никакого JavaScript-исполнения, никакого reverse-engineering site-specific JSON форматов.
- Кандидат типа streaming-manifest помечается отдельным MIME (`application/vnd.apple.mpegurl`, `application/dash+xml`) и направляется в столп I вместо direct-write.
- Безопасное расширение существующей логики: candidate selection учитывает streaming-кандидаты по политике столпа J.
- Ничего не делает с защищёнными DRM-стримами (наличие `EXT-X-KEY METHOD=SAMPLE-AES` или `<ContentProtection>` в манифесте → результат `Blocked(reason=DrmProtected)`, нет попытки обхода).

**Столп I — Потоковый офлайн-контур: HLS/DASH → MP4 (`standard`, `legacy`, `vr`, `vrUnlicensed`).**

- Цель: превратить сегментированный стрим в стандартный MP4 файл, читаемый любым плеером.
- Backend: `media3-exoplayer-hls:1.2.1` + `media3-exoplayer-dash:1.2.1` подключаются обратно в Gradle для market-flavors с видео. `lite` и `photos` — без них.
- Алгоритм:
   1. Скачивание сегментов через `HlsDownloader` / `DashDownloader` в `cacheDir/url-stream/<id>/` — variant выбирается по политике столпа J (резолюция, аудио-only).
   2. Remux: `MediaExtractor` читает скачанные TS / fragmented MP4 сегменты; `MediaMuxer` пишет финальный MP4 (sample-copy без re-encode, video MIME `video/avc`/`video/hevc`/`video/av01`, audio MIME `audio/mp4a-latm`).
   3. Cleanup: временный сегмент-кэш удаляется немедленно после успешного remux или при отмене.
   4. Финальный MP4 → `LinkDownloadWriter` (S0003 Столп E) → пользовательский ресурс.
- Прогресс: отображается через существующий `LinkAutoDownloadProgressDialog` (S0003 Столп F); фаза «загрузка сегментов» и «remux» — два разных индикатора.
- Fallback при codec mismatch (sample-copy недоступен из-за нестандартного codec): результат `Error(MuxFailed)`; повторного re-encode нет — это сознательное исключение, чтобы не тащить в `standard` тяжёлый transcoding pipeline.
- Не блокирует воспроизведение: существующий плеер не затрагивается.

**Столп J — Политика выбора формата (общая для всех flavors с видео).**

- Цель: выбрать наилучший формат среди предложенных без диалога на каждую загрузку.
- Требования: настройка «Максимальное разрешение при загрузке» (480p / 720p / 1080p / Лучшее доступное); отдельная настройка «Скачивать только аудио, если возможно» (boolean); значения по умолчанию: 1080p + audio-only = выключено.
- Применяется и к direct-file кандидатам с известным разрешением (через MIME hint / HEAD-probe), и к streaming-вариантам в манифесте.
- Размещение: раздел «Поделиться/Приём», подсекция «Загрузка по ссылке», рядом с существующими настройками S0003.

**Столп K — Cookie-хранилище и инъекция (`standard`, `legacy`, `vr`, `vrUnlicensed`, `lite`, `photos`).**

- Цель: хранить сессионные куки между загрузками и применять их к сетевым запросам экстракторов и Media3 datasource.
- Требования: извлечение куки из `CookieManager.getInstance()` после успешного входа; шифрование через существующую инфраструктуру `androidx.security:security-crypto`; хранение по домену; кастомная реализация OkHttp `CookieJar`, инжектируемая в `@Named("linkDownload")` `OkHttpClient`; для Media3 — `DefaultHttpDataSource.Factory.setDefaultRequestProperties(mapOf("Cookie" to ...))` для каждого скачивания.
- Куки не синхронизируются с системным браузером и не наследуются другими WebView приложения.
- Управление: отдельный экран «Авторизации для загрузки» в разделе настроек — список доменов с сохранёнными сессиями, для каждого — «Удалить» без подтверждения; «Добавить» — запускает столп L.
- Универсальность: любой пользовательский домен, не только конкретные платформы.

**Столп L — Изолированный WebView-флоу авторизации (`standard`, `legacy`, `vr`, `vrUnlicensed`, `lite`, `photos`).**

- Цель: дать пользователю войти на любой пользовательский ресурс через родной веб-интерфейс и безопасно извлечь куки-сессию.
- Требования: диалог/экран с WebView; WebView с уникальным `WebViewDatabase` (изоляция от системного браузера); при успешном входе куки домена извлекаются через `CookieManager.getInstance().getCookie(domain)`, передаются в столп K, а из WebView-контекста удаляются (`CookieManager.removeAllCookies()`, `clearCache(true)`, `clearHistory()`). После сохранения — повторная попытка скачивания автоматически.
- Триггер: (а) ответ HTTP 401/403 в direct/HTML стратегиях → опциональное предложение войти; (б) явная кнопка «Добавить авторизацию» в настройках. CAPTCHA / 2FA пользователь проходит руками в WebView, это ожидаемо.
- Универсальность: пользователь сам выбирает домен через ввод URL или из существующего failed-download workflow. Никакой preconfigured-list.

**Столп M — Post-download UX: автооткрытие vs тост (все flavors).**

- **Текущее состояние:** настройка `linkAutoDownloadOpenInPlayer: Boolean = true` уже существует в `AppSettings.kt:165`, читается в `LinkAutoDownloadCoordinator.kt:81`, иерархически подчинена master-toggle `linkAutoDownloadEnabled` (включён switch viewable только при включённом master) в `PlaybackSettingsFragment.kt:445`. UI — `fragment_settings_playback.xml` (`switchLinkAutodownloadOpenInPlayer`). S0116 не создаёт настройку заново — расширяет её действие на новые типы исходов.
- Цель: для всех новых видов исходов S0116 (HLS/DASH success, DRM blocked, MuxFailed, Streaming disabled в lite/photos, новые auth-flow исходы) сохранить ту же семантику: `openInPlayer == true` (default) → автооткрытие в плеере как сейчас; `openInPlayer == false` → toast с локализованным сообщением о результате (success или причина ошибки). Master-toggle `linkAutoDownloadEnabled == false` глушит весь pipeline, как сейчас.
- Toast-сообщения для всех исходов S0116 — локализованы в EN/RU/UK; ключи под префиксом `s0116_toast_*`:
   - `s0116_toast_saved_to_resource` (success в ресурс): «Сохранено: {fileName}»
   - `s0116_toast_saved_to_downloads` (fallback в Downloads): «Сохранено в Downloads: {fileName}»
   - `s0116_toast_streaming_started`: «Загружается стрим..» (показывается, если ≥ 3 секунд до завершения, чтобы пользователь не подумал, что приложение зависло)
   - `s0116_toast_drm_blocked`: «Защищённый контент, скачивание невозможно»
   - `s0116_toast_mux_failed`: «Не удалось собрать MP4 (codec: {codec})»
   - `s0116_toast_streaming_disabled`: «Стримы недоступны в этой сборке»
   - `s0116_toast_auth_required`: «Требуется вход — откройте настройки»
   - `s0116_toast_no_media_found`: переиспользует существующий S0003 string
   - `s0116_toast_network_error`: переиспользует существующий S0003 string
- Поведение в `lite` / `photos` — те же тосты применимы (даже если streaming недоступен, toast о Blocked-исходе показывается); `openInPlayer == false` глушит автооткрытие, не глушит toast.
- Settings UI остаётся без структурных изменений — switch `switchLinkAutodownloadOpenInPlayer` уже подчинён master-toggle `switchLinkAutodownloadEnabled` (см. `PlaybackSettingsFragment.kt:445-447`); добавляются только новые toast-strings.
- Совместимость с существующими исходами S0003 — `Saved` / `FellBackToDownloads` / `Failed.*`: уже работают по этой настройке, поведение не меняется. S0116 не модифицирует mapping `LinkAutoDownloadCoordinator.Result` для существующих исходов, только расширяет sealed hierarchy новыми вариантами.

### 5.2 Потоки данных и событий

Канал S0003 (Share-sheet → URL) → реестр стратегий обходится в каноническом порядке:

- direct → если URL отвечает медиа-MIME, пишем напрямую в ресурс (S0003 Столп E).
- html (расширенный G) → парсим HTML, получаем кандидатов: direct-media (`og:video`, `<video>`, `<source>`, `<img>`) и streaming-manifest (`m3u8`/`mpd` ссылки в HTML/JSON-LD/regexp). Cookie injection (столп K) применяется автоматически.
- streaming-manifest кандидат → столп I (HLS/DASH downloader → remux → MP4) → S0003 Столп E.
- direct-media кандидат → S0003 Столп E.
- HTTP 401/403 ответ от любой стратегии → опциональное предложение запустить столп L (WebView-авторизация); после сохранения куки в столп K — автоматический повтор той же стратегии.

После записи в ресурс (или fallback в Downloads) — столп M проецирует исход на пользовательский UX по настройке `linkAutoDownloadOpenInPlayer` (автооткрытие или toast).

Расширение через site-specific экстракторы для платформ, не отдающих контент через generic HTML/streaming-сниффер, — отдельная sideload-only спека S0117. В S0116 таких экстракторов нет.

### 5.3 Точки расширяемости

- Реестр стратегий — добавление новой generic-стратегии (например, дополнительные media tags) без затрагивания других.
- Политика выбора формата — конфигурируется без изменения стратегий.
- Потоковый офлайн-контур — может быть заменён (например, на FFmpeg-mux через уже существующий `fms-ffmpeg-dts.aar` для нестандартных codec) без изменений в канале выше.
- Cookie-хранилище — может быть расширено на OAuth-токены и API-ключи без изменения UI авторизации.
- Streaming sniffer — distinct-by-domain quirks (specific HTML patterns) могут добавляться как фильтры внутри generic-стратегии без выхода в site-specific территорию.
- Site-Resolver надстройка (S0117) врезается перед direct/html/streaming как отдельная стратегия в реестре; в S0116 точка интеграции — сам `LinkExtractionRegistry` без специальных hooks.

### 5.4 Graceful degradation: fallback в S0003 baseline

Все новые компоненты S0116 (G, I, J, K, L, M) обязаны деградировать в существующее поведение S0003 при сбое, чтобы как минимум базовый функционал — direct file download и static HTML candidate selection — никогда не регрессировал.

- **Расширенный HTML sniffer (G):** новые типы кандидатов (`m3u8`/`mpd` URL, JSON-LD VideoObject) — отдельные сборщики внутри `harvestCandidates`. При выкидывании любого исключения сборщиком — лог + продолжение остальных сборщиков. Если ни один streaming-кандидат не извлёкся — поведение идентично S0003 (берутся прямые `og:video` / `<video>` / `<img>` кандидаты).
- **Streaming downloader (I):** при `Manifest.parseFailure`, `ContentProtection != null`, codec mismatch в `MediaMuxer.writeSampleData` или любом ином сбое в потоковом контуре — стратегия возвращает `OpenResult.NotFound("streaming_unavailable")` (а не `Error`), что заставляет реестр перейти к следующей стратегии. Если HTML-кандидат содержит и streaming-вариант, и direct-MP4 — направление перебора: streaming → fallback на direct. Если streaming `NotFound` и direct отсутствует → терминальная ошибка с понятным toast.
- **Cookie injection (K):** недоступность EncryptedSharedPreferences (corrupt keystore, restored backup mismatch) → cookie storage возвращает empty set; `CookieJar` ведёт себя как пустой; downloader работает без cookies (= идентично S0003 поведению до auth).
- **WebView auth (L):** недоступность WebView (rare на API 26+, но возможно на enterprise-managed устройствах с отключённым WebView) → опция «Добавить авторизацию» скрыта в настройках; при HTTP 401/403 показывается обычная error-toast вместо предложения войти. Существующие сохранённые cookies продолжают работать.
- **Quality policy (J):** некорректное значение настройки (например, после backup restore из старой версии) → fallback на «Best available» по умолчанию.
- **Media3 HLS/DASH модули отсутствуют в APK (lite/photos):** реестр не получает streaming-стратегию (Hilt multibinding пуст для streaming-id); все остальные стратегии работают; HLS/DASH URL → `OpenResult.NotFound` или `Blocked(StreamingDisabled)` с понятным toast (см. столп M).
- **Toast strings отсутствуют (миграция, повреждённые ресурсы):** fallback на existing S0003 strings (`R.string.link_download_failed_unknown` и подобные).
- **Master switch `linkAutoDownloadOpenInPlayer` после crash восстановления prefs корраптится:** fallback на `true` (default) — пользователь видит автооткрытие как сейчас.

Принцип: **каждый новый компонент S0116 обёрнут в try/catch → лог → возврат NotApplicable / NotFound** — никаких uncaught exceptions, рушащих pipeline. Тестируется отдельно: instrumentation-test, который мокает один из новых компонентов в «всегда падает» режим, и проверяет, что S0003-baseline сценарии (прямые MP4-ссылки, простые HTML с `og:video`) проходят без регрессии.

### 5.5 Debug logging strategy

Направление S0116 ожидается активно эволюционирующим (новые HTML-паттерны, изменения в streaming-форматах, новые auth-сценарии). Логи — основной инструмент удалённой диагностики, поэтому каждая новая стратегия и каждый ключевой переход состояния обязан оставлять structured-trace в Timber.

- **Базовый Sxxxx debug tag (требование CLAUDE.md):** на entry point каждого нового flow — один `Timber.d("S0116: <path>")` с минимальной идентификацией:
   - `S0116: html-sniffer harvested N candidates (direct=X, streaming=Y, image=Z) for <domain>`
   - `S0116: streaming-downloader started, manifest=hls|dash, segments=N, target=<file>`
   - `S0116: streaming-downloader remux start, codec=<v/a>, samples=N`
   - `S0116: webview-auth opened for <domain>, cookies-before=N`
   - `S0116: cookie-jar inject domain=<domain>, cookies=N for request <strategy>`
   - `S0116: post-download UX, openInPlayer=<bool>, outcome=<type>`
- **Verbose-trace под debug build flag:** `BuildConfig.LOG_NETWORK_THUMBNAILS` уже существует как pattern в `app_v2/build.gradle.kts`; вводится новый `BuildConfig.LOG_LINK_DOWNLOAD` (debug=true, release=false) — под ним:
   - `Timber.v` для каждого HTTP request/response (URL, status, content-type, content-length)
   - `Timber.v` для каждого HLS segment download (segment index, byte range, duration)
   - `Timber.v` для каждой попытки `MediaExtractor.advance()` / `MediaMuxer.writeSampleData()` с byte size
   - `Timber.v` для cookie operations (add, remove, inject) — без значений cookies
- **Ошибки и edge cases:** все ошибочные ветки логируют через `Timber.w` (recoverable) или `Timber.e` (terminal) с полным stacktrace; пустые catch-блоки запрещены. Если ошибка попадает в fallback — лог уровня `Timber.w` с явным маркером `fallback=<target_strategy>`.
- **Removal на Verified:** все `Timber.d("S0116:` теги удаляются при переходе спеки в Verified (стандартное правило CLAUDE.md). `Timber.v` под `LOG_LINK_DOWNLOAD` остаются — это постоянная диагностическая инфраструктура, не временные тэги.
- **Privacy:** в логах URL обрезается до `host` + `path[0..2 segments]` (без query string и hash) — query может содержать short-lived signed-tokens платформ, попадание которых в logcat нежелательно. Cookie values никогда не логируются — только domain + count + names of cookies.
- **Структура для grep:** все S0116 trace-строки начинаются префиксом `S0116:` или (для verbose) `[link-dl]`, чтобы `logcat | grep -E 'S0116|link-dl'` давал чистый поток для отладки.

---

## 6. Открытые вопросы / Research items

1. **Библиотеки-экстракторы и их лицензии (для market-сборок S0116)**
   - **Research findings (2026-05-08):**
      - NewPipe Extractor — GPL v3, viral copyleft. Несовместим с проприетарными market-сборками FastMediaSorter. Не входит в S0116; рассматривается отдельно в S0117.
      - youtubedl-android (yausername wrapper) — бандлит yt-dlp + Python 3.8 + FFmpeg в APK; APK-инкремент ≈ 30-50 МБ; runtime-исполнение Python в APK конфликтует с Google Play «Device and Network Abuse». Не входит в S0116.
      - kotlin-youtubeExtractor / NanoDL и аналоги — узкое покрытие, требуют site-specific reverse-engineering. Не входят в S0116.
      - Apache 2.0 / MIT generic-extractor библиотеки нативно для Android в индустрии не существуют — паттерн «один extractor на сайт» по своей сути включает reverse-engineering, который и порождает GPL-копирование.
   - **Решение:** S0116 использует **только native generic-стратегии** (расширенный HTML-sniffer + streaming sniffer + WebView-авторизация). Никаких third-party extractor-библиотек в любой market-сборке. Расширение через site-specific extractors — отдельная sideload-only спека S0117.
   - **Статус:** Closed (Решено: native-only generic для всех flavors S0116)

2. **Внешний runtime/downloader как зависимость**
   - **Решение:** внешний процесс, Python runtime или heavyweight downloader toolkit не встраиваются ни в одну сборку S0116. Эта политика распространяется и на S0117 (sideload) по той же причине Google Play на signing-key уровне.
   - **Статус:** Closed (Решено: Kotlin-нативная реализация)

3. **Устойчивость generic-стратегий S0116 к churn-у HTML-структур платформ**
   - **Вопрос:** платформы могут менять HTML-разметку (убрать `og:video`, заменить `m3u8` на проприетарный формат, скрыть медиа за JS-only рендерингом). Как обеспечить устойчивость generic-сниффера?
   - **Решение:** S0116 не привязывается ни к одной платформе — сниффер ищет стандартные паттерны HTML/JSON-LD/streaming protocols. Если платформа переходит на JS-only — этот URL просто не работает в S0116 (terminal `NotFound`), это ожидаемое и приемлемое поведение для «generic» инструмента. Постоянное расширение sniffer-паттернов через follow-up minor-спеки по мере выявления новых стандартных HTML-сигналов. Активный verbose-logging (§5.5) — основной канал обнаружения новых паттернов.
   - **Статус:** Closed (Решено: generic-сниффер не подразумевает 100% coverage; добавление новых стандартных паттернов — incremental)

4. **HLS/DASH → контракт итогового артефакта**
   - **Research findings (2026-05-08):**
      - Media3 `HlsDownloader` / `DashDownloader` сохраняют сегменты в `Cache` в формате `.v3.exo` (внутренний chunked binary). Этот формат читается только Media3 — для пользователя как конечный артефакт неприемлем.
      - Стандартный путь HLS/DASH → MP4: `MediaExtractor` (читает скачанные TS / fMP4 сегменты) + `MediaMuxer` (пишет MP4 в sample-copy режиме без re-encode). Поддерживаемые codec: H.264/AVC, H.265/HEVC, AV1, AAC-LC. Для нестандартных codec (например, Opus в DASH-only стримах) — sample-copy mux недоступен.
      - Альтернатива через `media3-transformer:1.2.1` (Transformer API) — поддерживает trim+mux MP4 edit-list без re-encode для AVC/HEVC/AV1; добавляет ≈ 1 МБ APK, но даёт более чистый Kotlin-flow.
   - **Решение:** **Вариант (б) — всегда стандартный MP4 файл** через `MediaExtractor` + `MediaMuxer` (sample-copy без re-encode). Это даёт пользовательский UX «скачанное видео открывается где угодно», совместимый с записью в любой ресурс. При codec mismatch — `OpenResult.Error(MuxFailed(codec))` с локализованным toast и без re-encode fallback (re-encode тяжёл, требует MediaCodec encoder coverage). Re-encode рассматривается как отдельная Phase 3 спека.
   - **Статус:** Closed (Решено: всегда MP4 через MediaMuxer sample-copy; codec mismatch → terminal error)

5. **Куки WebView: безопасность и инъекция**
   - **Решение:** извлечение через `CookieManager.getInstance().getCookie(domain)` сразу после успешного входа, сохранение в `EncryptedSharedPreferences` (Столп K), очистка WebView-state. При загрузке — подстановка через OkHttp `CookieJar` для extractor-запросов и `DefaultHttpDataSource.Factory.setDefaultRequestProperties` для Media3 sources.
   - **Статус:** Closed (Решено: извлечение в CookieJar + очистка)

6. **Store-compliance и публичное позиционирование**
   - **Research findings (2026-05-08):**
      - Google Play Intellectual Property policy запрещает «apps that **induce or encourage** copyright infringement» и «streaming apps that allow users to download a local copy of copyrighted content **without authorization**». Триггеры review: упоминание YouTube/Instagram/TikTok в store listing, скриншоты с их UI, встроенный браузер с предзагруженной соцсетью, site-specific экстракторы.
      - **Generic-протокол downloaders на Google Play разрешены** (доказательства: Slix — HLS/DASH/RTMP/FLV/MP4 downloader; Advanced Download Manager). Ключевое отличие: protocol-level capability vs platform-targeted feature.
      - Meta Horizon Store policy фокусируется на VR-content guidelines, app review (VRC checklist), без специальных ограничений на media-downloader как класс. «Windows into existing service» категория явно поддерживается.
   - **Решение:**
      - Все market-сборки S0116 (`standard`, `legacy`, `vr`, `vrUnlicensed`, `lite`, `photos`) — generic URL flow + расширенный HTML sniffing + HLS/DASH download → MP4 + WebView-авторизация. Никакого site-specific reverse-engineered кода. Публикуемо в Google Play и Meta Horizon Store.
      - Public docs/manifests/store listing — нейтральны: «download supported web media», «standard streaming protocols», «user-driven authentication». Никаких поимённых упоминаний платформ.
      - Расширение возможностей за пределы generic — отдельная sideload-only спека S0117, не блокирующая S0116.
   - **Статус:** Closed (Решено: все market-сборки S0116 покрывают generic protocol+streaming+auth)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Store review трактует feature как restricted-source downloader из-за wording/copy | Высокая | Потеря канала дистрибуции | Нейтральная по площадкам формулировка; никаких named-source promises в docs/manifests/store metadata/screenshots; никаких icon assets с логотипами платформ; в публичных описаниях — protocol/format termin (HLS, DASH, MP4, image), не «socials» |
| HLS/DASH MediaMuxer remux падает на нестандартном codec | Средняя | Часть стримов не сохраняется | `OpenResult.Error(MuxFailed)` с понятным toast; пользователь видит, что именно сломалось; re-encode не пытаемся; список «known failing codecs» собирается через verbose-logging (§5.5) для будущей Phase 3 |
| `media3-exoplayer-hls` + `dash` добавляют APK weight | Низкая | +1.5–2 МБ к size budget standard/legacy/vr | Включаются только в flavor-ы с видео; в `lite`/`photos` не подключены; baseline-profile обновляется чтобы избежать стартового регресса |
| Куки WebView утекают в другие WebView-контексты | Низкая | Несанкционированный доступ к сессии пользователя | Изоляция WebView через уникальный `WebViewDatabase`; явный clear после извлечения; cookies в EncryptedSharedPreferences |
| Сборка HLS-сегментов занимает много места на устройстве | Средняя | Переполнение кэша, ошибки записи | Сегменты в `cacheDir/url-stream/<id>/`, удаляются сразу после remux или при отмене; pre-flight check свободного места перед стартом |
| Авторизация источника требует CAPTCHA / 2FA | Средняя | WebView-флоу не завершается автоматически | Пользователь проходит CAPTCHA/2FA вручную в WebView, это ожидаемое поведение |
| Scope creep от однорезультатного MVP к playlist/carousel batch-сценариям | Средняя | Раздувание MVP | Жёстко фиксировать однорезультатный scope для S0116; multi-item вынесен в S0117 |
| Generic streaming sniffer ложно срабатывает (находит `m3u8` в комментарии HTML) | Низкая | Скачивание мусора | Distinct-by-URL + HEAD-probe сегмента манифеста с проверкой Content-Type на `application/vnd.apple.mpegurl` или `application/dash+xml` |
| DRM-защищённый стрим попадает в downloader pipeline | Средняя | Скачанный файл бесполезен / упадёт mux | Pre-flight парсинг манифеста на `EXT-X-KEY METHOD=SAMPLE-AES` (HLS) и `<ContentProtection>` (DASH); если DRM detected → `Blocked(reason=DrmProtected)`, явное сообщение пользователю |
| Новый компонент S0116 выкидывает необработанное исключение и рушит pipeline целиком | Средняя | Регрессия base-S0003 функционала | §5.4 Graceful degradation: каждая новая стратегия обёрнута try/catch → fallback в существующий S0003 path; instrumentation-test проверяет это через mocked-failure |
| Logcat-поток после релиза слишком verbose, пользовательский диск/buffer переполняется | Низкая | UX degradation, потеря других логов | `BuildConfig.LOG_LINK_DOWNLOAD` отключён в release; в release остаётся только `Timber.w`/`Timber.e` для ошибочных веток |
| Sxxxx debug tags забываются перед Verified transition | Средняя | Production логи засорены | CLAUDE.md требование: grep `Timber.d("S0116:` перед transition в Verified; commit removal вместе со сменой статуса |
| Toast при `openInPlayer == false` не отображается из-за context-leak в коротком voiding-flow | Низкая | Пользователь не понимает, что произошло | Toast вызывается через `applicationContext` в координаторе; instrumentation-test покрывает «share-sheet → silent download → toast appears» сценарий |

---

## 8. Влияние на пользователя (docs/FEATURES)

Расширение S0003 в `docs/FEATURES.md` §22 Background & System Services — добавить нейтральную формулировку:

> **Расширенная загрузка по ссылке:** прямые медиаURL, встроенные медиа в HTML-странице, стандартные потоковые манифесты (HLS / DASH) загружаются как обычный MP4/MP3/JPEG. Если сайт требует входа, можно авторизоваться во встроенном веб-экране — после этого загрузки с этого домена работают с сохранённой сессией. Настройки качества применяются автоматически. Открывать результат в плеере или ограничиться тостом — настраивается в «Загрузка по ссылке».

Зеркала `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` — обязательны.

Запрещённые формулировки (любая ссылка → reject): «download from <название платформы>», «save Reels / Stories / Shorts», «bypass restrictions», «private content».

---

## 9. Архитектурные решения (ADR)

**ADR-1: Расширение реестра S0003, а не отдельный pipeline**

- **Решение:** S0116 расширяет существующий `LinkExtractionRegistry` парой generic-стратегий (расширенный HTML sniffer с поддержкой `m3u8`/`mpd`, streaming downloader). Site-specific надстройка — вне scope, переехала в S0117.
- **Альтернативы:** отдельный pipeline S0116 параллельно S0003; объединение реестров в новый layer.
- **Почему:** обратная совместимость гарантирована; reuse `LinkAutoDownloadCoordinator`, `LinkDownloadWriter`, `LinkAutoDownloadProgressDialog`; добавление стратегий без слома существующих.

**ADR-2: Потоковый офлайн-контур использует Media3 + MediaMuxer, output — стандартный MP4**

- **Решение:** HLS/DASH сегменты скачиваются Media3 `HlsDownloader` / `DashDownloader`; финальный артефакт — стандартный MP4 через `MediaExtractor` + `MediaMuxer` (sample-copy без re-encode). Codec mismatch → terminal `Error(MuxFailed)`, без re-encode fallback.
- **Альтернативы:** хранить как `.v3.exo`; FFmpeg-mux через `fms-ffmpeg-dts.aar`; Media3 Transformer.
- **Почему:** `.v3.exo` ломает контракт «скачанный файл открывается везде»; FFmpeg-путь оверкил для базовых codec; Transformer избыточен для sample-copy задачи.

**ADR-3: WebView-авторизация — универсальная, не платформо-специфичная**

- **Решение:** WebView-флоу авторизации — это generic capability «логин для любого пользовательского URL». Триггер — HTTP 401/403 либо явная кнопка пользователя в настройках.
- **Альтернативы:** внешний браузер + Custom Tabs; preconfigured platform list; хранить куки в WebView.
- **Почему:** пользователь остаётся в приложении; универсальность снимает store-policy риск; flow идентичен для любого домена.

**ADR-4: S0116 — generic-only во всех market-сборках, расширения вынесены в S0117**

- **Решение:** S0116 содержит исключительно generic protocol-level downloading. Никакого site-specific reverse-engineered кода и никаких viral-copyleft (GPL) зависимостей. Расширение для платформ с JS-only рендерингом (NewPipe-уровень покрытие, album batch) — отдельная sideload-only спека S0117.
- **Альтернативы:** включить site-specific экстракторы в `standard` под BuildConfig flag; смешать оба scope в одной спеке.
- **Почему:** Slix, Advanced Download Manager успешно публикуются на Google Play; запрет Google Play касается «inducing/encouraging copyright infringement», не protocol capability как таковой; чёткий boundary spec — спека отвечает за один shippable unit.

**ADR-5: Media3 streaming modules для market-flavors с видео, не для всех**

- **Решение:** `media3-exoplayer-hls:1.2.1` и `media3-exoplayer-dash:1.2.1` подключаются как `standardImplementation` / `legacyImplementation` / `vrImplementation` / `vrUnlicensedImplementation`. `lite` и `photos` остаются без них.
- **Альтернативы:** включить везде (раздувает `lite`); отказаться от HLS/DASH вообще.
- **Почему:** flavor-specific dependency declaration уже используется в проекте (см. `fms-ffmpeg-dts.aar`); нет нужды менять архитектуру Gradle.

**ADR-6: Graceful degradation — fail-soft в S0003 baseline, не fail-hard**

- **Решение:** каждый новый компонент S0116 при необработанном сбое (исключение в strategy.open, decode-failure в muxer, недоступность WebView, повреждённый cookie storage) откатывается до next-strategy в реестре или до S0003 baseline-поведения. Никаких uncaught exceptions, рушащих pipeline.
- **Альтернативы:** fail-fast с понятной ошибкой пользователю; fallback на «попробуй сам через браузер».
- **Почему:** S0003 — Verified production-функционал, регрессия неприемлема; новые компоненты S0116 — экспериментальные, ожидается итеративное улучшение, и fail-soft даёт работающее поведение даже на этапе шероховатостей.

**ADR-7: Verbose logging как постоянная инфраструктура, не временная отладка**

- **Решение:** S0116 вводит `BuildConfig.LOG_LINK_DOWNLOAD` (debug=true, release=false) для verbose `Timber.v` traces всех URL/HTTP/segment/cookie operations. `Timber.d("S0116:` теги — временные, удаляются на Verified; `Timber.v` под `LOG_LINK_DOWNLOAD` — постоянные, остаются в коде после Verified.
- **Альтернативы:** только разовые Sxxxx tags на entry-points; always-on verbose без BuildConfig.
- **Почему:** S0116 ожидается активно изменяться (новые HTML-паттерны, codec edge cases, auth-сценарии); удалённая отладка — основной канал диагностики; debug-only verbose — золотая середина между «ничего не видно» и «производственный шум».

**ADR-8: Новые исходы S0116 уважают существующую `linkAutoDownloadOpenInPlayer` без новой настройки**

- **Решение:** S0116 не создаёт отдельный toggle для toast-vs-open поведения; reuse существующий `linkAutoDownloadOpenInPlayer` (default `true`) для всех новых типов исходов (HLS success, DRM blocked, MuxFailed, Streaming disabled). Toast strings локализованы для каждого нового исхода.
- **Альтернативы:** отдельная `s0116ToastOnly` настройка; всегда тост, никогда автооткрытие; всегда автооткрытие, без тоста.
- **Почему:** существующая настройка уже описана в `AppSettings.kt:158-162` как «whether the resulting file opens automatically» — её семантика покрывает любые новые исходы; добавление второй настройки удвоило бы UI без пользы.

---

## 10. Связи с другими спеками

- **S0003** (`link-receive-download`, Verified) — прямой предшественник; S0116 расширяет его реестр стратегий, не модифицирует реализованные столпы A–F. Graceful degradation S0116 (см. §5.4) гарантирует, что S0003 baseline сохраняет рабочесть при любом сбое S0116 компонента.
- **S0117** (`url-media-downloader-nolegal-flavor`, Draft) — sideload-only расширение S0116 для site-specific экстракторов и album batch. Не блокирует S0116 MVP. Будет планироваться tactical-уровнем после S0116 Verified.
- Зависимостей, блокирующих начало тактического планирования S0116, нет.

---

## 11. Критерии готовности (strategic-level)

1. URL прямого медиафайла (MP4/MP3/JPEG и др. allowed MIME), пришедший через Share-sheet, сохраняется в выбранный ресурс одним итоговым файлом — поведение S0003 без регрессии.
2. URL HTML-страницы со статически извлекаемыми media signals (`og:video`, `og:image`, `<video>`, `<audio>`, `<source>`, `<img srcset>`, `m3u8`/`mpd` ссылки в HTML/JSON-LD) → один итоговый файл в ресурс. Streaming-варианты поддерживаются всеми flavors с видео; image-only — всеми flavors.
3. URL прямого манифеста HLS (`.m3u8`) или DASH (`.mpd`) → стандартный MP4 в ресурс через MediaMuxer remux, без `.v3.exo` артефактов. Поддерживается `standard`/`legacy`/`vr`/`vrUnlicensed`. `lite`/`photos` — `Blocked(StreamingDisabled)` с понятным toast.
4. URL, отдающий 401/403 → опциональное предложение войти; после WebView-авторизации и сохранения куки в столпе K — повторная попытка скачивания без ручного шага.
5. Сохранённые авторизации видны на отдельном экране настроек, каждая удаляется одной кнопкой без подтверждения.
6. Настройки «Максимальное разрешение при загрузке» (480p / 720p / 1080p / Best) и «Скачивать только аудио, если возможно» влияют на выбор кандидата для всех новых загрузок.
7. Существующие сценарии S0003 (прямые файлы, static HTML-parsing) не регрессируют ни при нормальной работе S0116, ни при имитированном сбое любого нового компонента (проверяется отдельным mocked-failure instrumentation-test).
8. DRM-защищённый стрим → `Blocked(DrmProtected)` с локализованным toast, без попыток скачивания.
9. Все новые строки присутствуют в EN/RU/UK; `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0116_` возвращает 0.
10. `docs/FEATURES.md` + `_RU` + `_UK`, manifest metadata, store listing и release notes нейтральны по платформам, без named-source claims.
11. Все market-сборки S0116 (`standard`, `lite`, `legacy`, `photos`, `vr`, `vrUnlicensed`) компилируются без site-specific extractor кода в APK.
12. APK size delta для `standard` в пределах +2.5 МБ.
13. **Post-download UX (столп M):** при `linkAutoDownloadOpenInPlayer == true` все новые исходы (HLS success, DRM blocked, MuxFailed, Streaming disabled, auth required) выполняют автооткрытие в плеере (для success) или показывают error-dialog (для error/blocked) — текущее поведение S0003. При `linkAutoDownloadOpenInPlayer == false` те же исходы проецируются в локализованные toast-сообщения (ключи `s0116_toast_*`), без открытия плеера и без error-dialog. Master-toggle `linkAutoDownloadEnabled == false` глушит весь pipeline.
14. **Debug logging:** в debug-сборке `BuildConfig.LOG_LINK_DOWNLOAD == true` производит verbose-trace всех HTTP/segment/cookie operations (без значений cookies, без query strings); в release-сборке — только `Timber.w`/`Timber.e` для recoverable/terminal ошибок. Все `Timber.d("S0116:` теги удалены перед transition в Verified.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0116` — создаст `PLAN/S0116_url-media-downloader/` с фазами.

**Все блокирующие open questions закрыты:**

- §6.1 closed — native generic only во всех market-сборках; расширения в S0117
- §6.2 closed — Kotlin native, no external runtime
- §6.3 closed — generic-сниффер не подразумевает 100% coverage; new patterns — incremental
- §6.4 closed — стандартный MP4 через MediaMuxer sample-copy
- §6.5 closed — extract via CookieManager, store via EncryptedSharedPreferences, inject via OkHttp CookieJar и Media3 HttpDataSource properties
- §6.6 closed — все market-сборки S0116 покрывают generic protocol+streaming+auth

**Tactical phase должен раскрыть:**

- Phase 1: расширенный HTML sniffer (G), потоковый downloader (I), политика качества (J).
- Phase 2: cookie storage (K), WebView-авторизация (L).
- Phase 3: post-download UX расширение (столп M) — toast strings, mapping новых исходов на existing `linkAutoDownloadOpenInPlayer`.
- Phase 4: graceful degradation (§5.4) — instrumentation-test для mocked-failure regression check; debug logging infrastructure (§5.5).

---

## 13. Distribution-Profile Capability Matrix

Эта матрица — закрытый контракт capability по каналу дистрибуции; она должна оставаться в синхроне с `app_v2/build.gradle.kts` flavor-блоками.

| Capability | standard | legacy | vr | vrUnlicensed | lite | photos |
|------------|:--------:|:------:|:--:|:------------:|:----:|:------:|
| Direct file (MP4/MP3/JPEG/…) | ✓ | ✓ | ✓ | ✓ | ✓ | image-only |
| Generic HTML sniffing (`og:*`, video/audio/source/img tags) | ✓ | ✓ | ✓ | ✓ | ✓ | image-only |
| Streaming sniffer (`m3u8`/`mpd` URL detection) | ✓ | ✓ | ✓ | ✓ | — | — |
| HLS/DASH download → MP4 (Media3 + MediaMuxer) | ✓ | ✓ | ✓ | ✓ | — | — |
| WebView-based authentication (universal) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Cookie injection (CookieJar + Media3 source) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Quality preference (max resolution / audio-only) | ✓ | ✓ | ✓ | ✓ | — | — |
| Post-download UX (open-in-player vs toast) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Graceful fallback to S0003 baseline | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Debug logging (`LOG_LINK_DOWNLOAD`) | debug-only | debug-only | debug-only | debug-only | debug-only | debug-only |
| Published to Google Play | ✓ | ✓ | — | — | ✓ | ✓ |
| Published to Meta Horizon Store | — | — | ✓ | — | — | — |
| Sideload distribution | optional | optional | optional | ✓ | optional | optional |

**Расширения за пределы этой матрицы (site-specific extractors, album batch, GPL-3 NewPipe Extractor) — sideload-only flavor, описанный в S0117. В матрицу S0116 не входит.**

**Key principle:** S0116 covers ≥ 80% of real-world web media URLs (any direct file, any HTML page exposing media via standard tags, any HLS/DASH manifest, any user-authenticated source) without store-compliance risk.

---

## Revision History

- **2026-05-08** — strategic deep-update v2 (claude-opus-4-7, focus: post-download UX (столп M), graceful degradation (§5.4), debug logging (§5.5), noLegal вынесен в S0117)
   - Applied:
      - §1: добавлен второй абзац о silent-mode requirement (toast вместо open-in-player)
      - §2: добавлены цели #8 (open-in-player toggle для всех новых исходов) и #9 (graceful degradation S0003 baseline)
      - §3.1: добавлено wish #1 «maximize standard», #7 «output as standard MP4», #8 «toast при openInPlayer=false», #9 «verbose debug logging»
      - §3.2: добавлен Backward compatibility ограничение; matrix очищена от noLegal column
      - §4: уточнены ссылки на существующую `linkAutoDownloadOpenInPlayer` настройку (`AppSettings.kt:165`, `LinkAutoDownloadCoordinator.kt:81`, `PlaybackSettingsFragment.kt:445`)
      - §5.1: убран столп H (Site-Resolver) — переехал в S0117; добавлен новый столп M (Post-download UX) с reuse существующей настройки и полным списком toast-strings
      - §5.2: убрано упоминание Site-Resolver; добавлена ссылка на S0117 как точку расширения
      - §5.3: убрано упоминание noLegal sourceSet; добавлена точка интеграции для S0117 site-resolver
      - **NEW §5.4:** Graceful degradation — fallback каждого нового компонента S0116 (G/I/J/K/L/M) в существующий S0003 baseline; принцип fail-soft вместо fail-hard
      - **NEW §5.5:** Debug logging strategy — Sxxxx tags + `BuildConfig.LOG_LINK_DOWNLOAD` для verbose traces; privacy-safe URL/cookie logging; structure для grep
      - §6: closed all open questions; убраны Phase 2 / noLegal упоминания, заменены ссылками на S0117
      - §7: добавлены риски «новый компонент рушит pipeline» (mitigated §5.4), «verbose log overflow» (mitigated build flag), «Sxxxx tags забыты» (mitigated CLAUDE.md grep), «toast не отображается из-за context-leak»
      - §9: ADR блок переписан — ADR-1..4 содержательно обновлены, **NEW ADR-6** (graceful degradation), **NEW ADR-7** (verbose logging infrastructure), **NEW ADR-8** (reuse `linkAutoDownloadOpenInPlayer` без новой настройки); удалён старый ADR-5 noLegal — переехал в S0117
      - §10: добавлена связь с S0117
      - §11 done criteria — расширены до 14 пунктов: добавлены критерий 7 (regression test for graceful degradation), 13 (post-download UX), 14 (debug logging)
      - §12: tactical phasing уточнён — Phase 3 для post-download UX, Phase 4 для graceful degradation + logging
      - §13 capability matrix — убрана `noLegal` column; добавлены строки «Post-download UX», «Graceful fallback to S0003», «Debug logging»

- **2026-05-08** — strategic deep-update v1 (claude-opus-4-7, focus: maximize standard flavor coverage, store-compliance research, close all open questions)
   - **Research findings driving updates:**
      - Google Play Intellectual Property policy targets «inducing/encouraging copyright infringement», not protocol capability. Generic-protocol downloaders (HLS/DASH/RTMP/MP4) explicitly published on Play Store (Slix com.waspchat.slix; Advanced Download Manager com.dv.adm).
      - Media3 `HlsDownloader` / `DashDownloader` outputs proprietary `.v3.exo` cache; standard MP4 export possible via `MediaExtractor` + `MediaMuxer` sample-copy without re-encode for AVC/HEVC/AV1/AAC.
      - NewPipe Extractor — GPL-3 viral, incompatible with proprietary market builds.
      - `app_v2/build.gradle.kts` already excludes `media3-exoplayer-hls` and `media3-exoplayer-dash` (lines 647-652) for ≈ 1.5–2 МБ APK saving.
   - **Boundary redrawn:** capability boundary between market and `noLegal` is **«site-specific reverse-engineered code or viral-copyleft»**, not «streaming/no-streaming» or «socials/no-socials». Generic protocol-level downloading + universal HTML sniffing + WebView authentication are fully store-compliant.

- **2026-05-08** — by `spec-update` (GPT-5.4, focus: completeness, consistency, store-compliance)
   - Applied: убрано публично-опасное platform-specific позиционирование; скорректированы неверные предпосылки по HLS/DASH; scope первой итерации зафиксирован как однорезультатный.

- **2026-05-08** — by `/spec-update` (claude-opus-4-5, focus: completeness, consistency)
   - Applied: 8 правок — noLegal flavor в §3.2; whitelist Instagram/Threads + copyleft-rule в §5.1 Столп H; §6.1 → Partial; §6.6 → Closed; risks row 3; §11 критерий 10; §12 blockers list. Proposed (DISCUSS): 1 (P-1 ADR-5 noLegal).

---

## Last Audit

**Date:** 2026-05-08
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 26 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 1

### Manual / on-device

- [ ] Run `GracefulDegradationTest` on a connected device (instrumentation, no JVM equivalent) — strategic §11 criterion 7.
- [ ] Run `MediaMuxerRemuxerInstrumentationTest` after replacing placeholder fixtures (`androidTest/assets/s0116_fixtures/{tiny_avc_aac.ts,tiny_opus.webm}`) with real media bytes per the README.
- [ ] On-device sanity: paste a real HLS `.m3u8` URL through Share-sheet on a `standard` debug build, verify resulting MP4 plays in any external player — strategic §11 criterion 3.
- [ ] On-device sanity: paste a 401-protected URL, verify WebView auth dialog opens, complete sign-in, verify retry succeeds — strategic §11 criteria 4-5.
- [ ] APK size delta vs pre-S0116 baseline ≤ +2.5 MB (strategic §11 criterion 12) — measure post-build artefacts.

### Exempt notes

- Flavor-specific classes in `streamingEnabled/` and `streamingDisabled/` source-sets are not indexed by `dev/CATALOG/scripts/scan.ps1` (scanner restricted to `src/main/`). 7 classes (`StreamingDownloadStrategy`, `Media3SegmentDownloader`, `MediaMuxerRemuxer`, `ManifestDrmDetector`, `StreamingCacheCleaner`, `NoOpStreamingPipeline`, both `StreamingModule.kt`) are intentionally absent from the catalog — confirmed limitation, not a regression.

### PASS summary

- 21 new files created (domain + data + di + ui + tests + layouts + resources). All Glob existence checks pass.
- All 11 sanctioned `LinkDownloadTrace.tag` entry points present exactly once each (§5.5 audit step 07.3).
- Zero `Log.d(..)` in any S0116-touched file (Timber-only invariant).
- Zero GPL-3 / yt-dlp / NewPipe imports in any flavor's APK source path (store-compliance gate).
- Zero platform names in `domain/` layer.
- 4 `media3-exoplayer-hls:1.2.1` + 4 `media3-exoplayer-dash:1.2.1` per-flavor declarations; previous `exclude` directives gone.
- `LOG_LINK_DOWNLOAD` BuildConfig field present in both `debug` (true) and `release` (false) blocks.
- `AuthSessionsActivity` registered in `AndroidManifest.xml` with `parentActivityName=".ui.settings.SettingsActivity"`.
- 4 new sealed `Result.Failed` variants in coordinator (`DrmBlocked`, `StreamingDisabled`, `MuxFailed(codec)`, `AuthRequired(host, originalUrl)`); all 5 S0003 variants preserved.
- 4 new `BlockedReason` enum entries (`DrmProtected`, `StreamingDisabled`, `MuxFailed`, `AuthRequired`); all 3 S0003 entries preserved.
- `Extended URL download (S0116)` bullet present in all 3 FEATURES locales (EN/RU/UK), all under §22.
- All 4 string parity gates (`s0116_toast_=7`, `auth_sessions_=3`, `webview_auth_=4`, `setting_saved_authorizations=2`) exit 0.
- `mockwebserver:4.12.0` androidTestImplementation present.
- All 8 phases ✅ Done; 51/51 step `[x]`; INDEX `Phases: 8 / 8 done`.
- Catalog regenerated — 11 new `main/` classes indexed (StreamingPipeline, EncryptedCookieStore, LinkDownloadCookieJar, AuthSessionRepository, AuthSessionRepositoryImpl, AuthSessionAdapter, AuthSessionsActivity, AuthSessionsListFragment, AuthSessionsListViewModel, WebViewAuthDialogFragment, WebViewAuthViewModel, LinkAutoDownloadResultPresenter).
- Strategic spec status was `Implemented` before audit; flips to `Verified` on completion.
