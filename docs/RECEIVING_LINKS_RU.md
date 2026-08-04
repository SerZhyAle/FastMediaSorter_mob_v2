# Система загрузки контента при получении ссылки

Этот документ описывает полную архитектуру системы, которая срабатывает, когда в приложение приходит текстовая ссылка через системный Share-sheet (ACTION_SEND, EXTRA_TEXT).

Охватываемые спеки: **S0003** (Verified) · **S0116** (Verified) · **S0140** (Verified) · **S0144** (Implemented) · **S0151** (Partial) · **S0155** (BlockNeedUserTest) · **S0157** (In Progress).


---

## 1. Точка входа

> **Трансляции vs загрузка по ссылке.** Если вы хотите слушать интернет-радио или добавить RTSP/HLS-поток постоянно - используйте экран **Трансляции** (Настройки → Медиа → Трансляции или пункт «Трансляции» в выпадающем меню главного экрана). Описанная здесь система Share-sheet предназначена для разового скачивания медиафайлов по ссылке из другого приложения.

`ReceiveShareActivity` принимает `Intent` с текстовым payload.  
`UrlInTextDetector` извлекает все уникальные `http(s)`-URL из текста `EXTRA_TEXT` (спека **S0140**).

- Если URL не найдены или настройка `linkAutoDownloadEnabled = false` - текст оборачивается в `.txt`-файл и обрабатывается как обычный входящий файл.
- Если найден **1 URL** и настройка включена - запускается конвейер `LinkAutoDownloadCoordinator` (одиночный режим, с возможным предложением авторизации).
- Если найдено **несколько URL** и настройка включена - запускается пакетная загрузка через `LinkAutoDownloadCoordinator.handleBatch` (без проактивного предложения авторизации).

---

## 2. Настройки, управляющие поведением

Все три настройки живут в разделе **«Поделиться/Приём → Поведение»** (`PlaybackSettingsFragment`).

| Настройка | По умолчанию | Описание |
|-----------|:---:|---|
| `linkAutoDownloadEnabled` | вкл | Мастер-тумблер всей системы |
| `linkAutoDownloadDestinationResourceId` | пусто | Ресурс-получатель; пусто = Downloads |
| `linkAutoDownloadOpenInPlayer` | вкл | После загрузки - открыть в плеере; выкл = только тост |
| `linkDownloadMaxResolution` | 1080p | Максимальное разрешение для стримов |
| `linkDownloadAudioOnly` | выкл | Скачивать только аудиодорожку из стрима |

Подчинённые настройки (ресурс, открыть в плеере, качество) визуально отключены, пока мастер-тумблер выключен.

---

## 3. Аутентификация: проактивный шаг перед скачиванием (S0144, S0157)

До запуска конвейера `ReceiveShareActivity` проверяет:

1. Есть ли уже сохранённая авторизация для этого хоста в `EncryptedCookieStore`.
2. Есть ли постоянный отказ для этого хоста (запись с `type=dismissed` в `EncryptedCookieStore`).

Если **нет ни авторизации, ни постоянного отказа** - показывается диалог-предложение «добавить авторизацию» с тремя кнопками (S0157):

- «Добавить» - открывается `WebViewAuthDialogFragment` на странице входа. Для хостов из `KnownAuthResources` открывается каноническая страница входа ресурса; для прочих хостов - сам переданный URL.
- «Пропустить» - конвейер запускается без авторизации, dismissal **не** записывается; при следующем шаринге предложение появится снова.
- «Не спрашивать» - постоянный отказ записывается в `EncryptedCookieStore` как запись с `type=dismissed`; предложение для этого хоста больше не показывается до удаления записи.

Диалог появляется для **любого http(s)-хоста** - не только для `KnownAuthResources`. Хосты из `KnownAuthResources` имеют дополнительную особенность: `WebViewAuthDialogFragment` открывается на каноническом URL входа и обрабатывает `intent://`-редиректы.

Если авторизация уже есть или постоянный отказ зафиксирован - предложение не появляется, конвейер запускается сразу.

### Выбор аккаунта (S0155, BlockNeedUserTest)

После проактивной проверки, если для хоста сохранено ≥ 2 аккаунтов, `AccountSelectionManager` показывает диалог выбора аккаунта.

- Дефолт - аккаунт с наибольшим `lastUsedAtEpochMillis` (последний успешно использованный).
- При единственном аккаунте - диалог не появляется, аккаунт выбирается автоматически.
- При нулевом количестве аккаунтов - поведение как до S0155 (предложение войти через S0144).

Выбранный аккаунт сохраняется в `LinkDownloadSessionContext` и передаётся конвейеру.

---

## 4. Конвейер извлечения контента

`LinkAutoDownloadCoordinator` запускает `LinkExtractionRegistry` - реестр стратегий (Hilt multibinding), которые обходятся в каноническом порядке.

### 4.1 Cookie injection

Перед каждым HTTP-запросом стратегий `LinkDownloadCookieJar` автоматически инжектирует сохранённые куки для домена из `EncryptedCookieStore`. Для Media3 источников - через `DefaultHttpDataSource.Factory.setDefaultRequestProperties`. Все куки хранятся в зашифрованном виде (`EncryptedSharedPreferences`), не синхронизируются с системным браузером.

### 4.2 Стратегии извлечения (в порядке применения)

#### Стратегия 1 - Direct file (`DirectFileExtractionStrategy`)

Если URL сам по себе отвечает медиа-MIME или содержит явное расширение из whitelist - скачивается напрямую. Никакого дополнительного парсинга.

#### Стратегия 2 - HTML-парсинг (`HtmlPageExtractionStrategy`)

GET страницы (≤ 2 МиБ). Сбор кандидатов:

- `og:video`, `og:image`, `twitter:player:stream` мета-теги.
- `<video>`, `<audio>`, `<source>` теги с `src`.
- `<img>` с `src`/`srcset`.
- Прямые ссылки с whitelisted-расширениями в HTML.
- `m3u8`/`mpd`-ссылки через `StreamingManifestSniffer` (в `<meta>`, `<link>`, `<source>`, JSON-LD `VideoObject.contentUrl/embedUrl`, regexp по тексту страницы).
- JSON-LD `VideoObject` через `StructuredMediaSniffer`.

HEAD-probe по кандидатам (≤ 8 параллельно, лимит 4 с) для определения размера. Политика выбора (`CandidateSelectionPolicy`): первый кандидат с известным размером ≥ 1 МБ; если таких нет - самый большой; если размер ни у кого неизвестен - первый по порядку.

**Специальное правило для известных соцсетей (S0151):** если единственный кандидат - превью-картинка из Open Graph, и хост входит в `KnownAuthResources` - исход помечается как `SocialPreviewOnly`, а не «успех». Превью само по себе не сохраняется. Конвейер переходит к следующей стратегии.

Стриминговый кандидат (`m3u8`/`mpd`) направляется в `StreamingPipeline` вместо direct-write.

#### Стратегия 3 - Динамический разбор (`InvisibleWebViewExtractionStrategy`)

Применяется для хостов из `KnownAuthResources`, когда HTML-стратегия вернула `SocialPreviewOnly`. Открывает страницу в невидимом WebView с инжектированными куки из `LinkDownloadSessionContext`, ждёт рендеринга JS, анализирует итоговый DOM.

- Если нашлось реальное медиа (видео reel'а, изображения карусели) - передаётся дальше.
- Если снова только превью - исход `SocialPreviewOnly`, переход к реактивному шагу (§5).

> **Состояние (S0151, Partial):** для Instagram reel / карусели архитектурный вопрос §6.1 (хватает ли динамического разбора или нужен разбор внутреннего JSON страницы) остаётся открытым - проверяется на устройстве с валидной сессией. Диагностический лог `S0151-diag:` показывает набор кандидатов по каждой стратегии.

#### Стратегия 4 - Стриминговый конвейер (`StreamingPipeline`, только на сборках с видео)

Когда кандидат - `m3u8` или `mpd` манифест:

1. Pre-flight парсинг: если обнаружен DRM (`EXT-X-KEY METHOD=SAMPLE-AES` / `<ContentProtection>`) - исход `Blocked(DrmProtected)`, попытки скачивания нет.
2. Скачивание сегментов через Media3 `HlsDownloader` / `DashDownloader` в `cacheDir/url-stream/<id>/`. Вариант выбирается по настройкам качества (§2).
3. Remux: `MediaExtractor` + `MediaMuxer` → стандартный MP4 (sample-copy без re-encode). Поддерживаемые кодеки: H.264/AVC, H.265/HEVC, AV1, AAC-LC. При несовместимом кодеке - исход `Error(MuxFailed(codec))`.
4. Временные сегменты удаляются после remux или при отмене.
5. Финальный MP4 → `LinkDownloadWriter`.

На `lite`/`photos` сборках Media3 HLS/DASH модули отсутствуют - стриминговые кандидаты дают `Blocked(StreamingDisabled)`.

### 4.3 Fallback на S0003 baseline

Каждая новая стратегия (S0116) при необработанной ошибке возвращает `OpenResult.NotApplicable` или `NotFound`, и реестр переходит к следующей стратегии. Базовый функционал S0003 (прямые файлы + статический HTML) никогда не регрессирует от сбоев новых компонентов.

---

## 5. Реактивная авторизация при неудаче извлечения

Если конвейер вернул `SocialPreviewOnly` (реальный контент не найден) или HTTP 401/403:

### 5.1 Предложение входа

- **Нет сохранённой авторизации** → диалог «войти для этого ресурса».
- **Авторизация есть, но контент не получен** → диалог «войти заново»; текст явно объясняет: «сохранённая сессия для этого ресурса не дала контент - возможно, устарела».
- **С S0155:** текст адресован конкретному аккаунту: «Вход для `@user` мог устареть - войти заново под этим аккаунтом?» Обновляется только запись `(хост, аккаунт)` - другие аккаунты того же хоста не затрагиваются.

### 5.2 WebView-вход (`WebViewAuthDialogFragment`)

- Изолированный WebView с уникальным `WebViewDatabase` (без наследования от системного браузера).
- Навигация: `intent://`-редиректы перехватываются (если есть запасной `http`-адрес - загружается он; иначе - остаёмся на текущей странице). Нестандартные схемы (`market://` и пр.) гасятся. `http/https` - не трогаем.
- После успешного входа куки домена извлекаются через `CookieManager.getCookie(domain)`.
- С S0155: показывается диалог «назовите этот аккаунт» с hint от `AccountNameHintExtractor` (пробует `username`/`ds_user_id` из куки). Пользователь подтверждает или редактирует имя. Внутренний ключ - UUID; `displayName` - редактируемая метка.
- Куки WebView очищаются (`removeAllCookies`, `clearCache`, `clearHistory`).
- Новая сессия сохраняется в `EncryptedCookieStore` по ключу `(хост, accountId)`.
- Автоматически запускается повторная попытка скачивания.

### 5.3 Отказ пользователя

Как и в проактивном предложении (§3), реактивный диалог при `SocialPreviewOnly` содержит три кнопки: «Войти», «Пропустить сейчас», «Не спрашивать».

- «Пропустить сейчас» - dismissal не записывается; при следующей ссылке предложение появится снова.
- «Не спрашивать» - запись `type=dismissed` добавляется в `EncryptedCookieStore` по хосту. Запись видна в списке авторизаций в Настройках - её удаление отзывает отказ.
- При последующих шарингах с заблокированного хоста показывается тост «контент недоступен» - не молчаливый выход.

---

## 6. Запись результата

`LinkDownloadWriter` пишет итоговый поток в ресурс из `linkAutoDownloadDestinationResourceId`.

- Имя файла: из `Content-Disposition` / URL-сегмента → нормализация → при коллизии - суффикс времени. Существующие файлы не перезаписываются.
- Если ресурс недоступен (удалён / оффлайн / нет прав) - fallback в системный Downloads + Toast с явным указанием причины.
- Partial carousel (S0151): если извлечена часть изображений карусели - сохраняется то, что удалось; сообщение «сохранено N из M» + предложение войти заново для попытки получить остальное.

---

## 7. UX результата и прогресс

### Прогресс

`LinkAutoDownloadProgressDialog` - недиспозитивный диалог поверх UI с `LinearProgressIndicator`:
- Индeterminate пока размер не известен.
- Determinate (`Downloading.total != null`) с отображением байт через `Formatter.formatShortFileSize`.
- Кнопка «Отмена» отменяет корутину координатора.

### Исходы (определяет `LinkAutoDownloadResultPresenter`)

Поведение зависит от `linkAutoDownloadOpenInPlayer`:

| Исход | `openInPlayer = true` | `openInPlayer = false` |
|-------|-----------------------|------------------------|
| Успех (файл сохранён) | Открывает файл в плеере | Тост `s0116_toast_saved_to_resource` |
| Fallback в Downloads | Открывает файл в плеере | Тост `s0116_toast_saved_to_downloads` |
| Пакетная загрузка | Тост `s0117_toast_batch_saved` или Диалог со сводкой ошибок | Тост `s0117_toast_batch_saved` или Диалог со сводкой ошибок |
| DRM-защита | Тост `s0116_toast_drm_blocked` | Тост `s0116_toast_drm_blocked` |
| MuxFailed | Тост `s0116_toast_mux_failed` | Тост `s0116_toast_mux_failed` |
| Streaming отсутствует | Тост `s0116_toast_streaming_disabled` | Тост `s0116_toast_streaming_disabled` |
| Требуется авторизация | Диалог WebView | Тост `s0116_toast_auth_required` |
| Только превью (соцсеть) | Диалог-предложение входа или тост `s0151_toast_content_unavailable` | Диалог-предложение входа или тост `s0151_toast_content_unavailable` |
| Медиа не найдено | Тост `link_autodownload_error_no_media` | Тост `link_autodownload_error_no_media` |
| Запрещенный MIME-тип | Тост `link_autodownload_error_mime_blocked` | Тост `link_autodownload_error_mime_blocked` |
| Сеть недоступна | Тост `link_autodownload_error_no_network` | Тост `link_autodownload_error_no_network` |
| Таймаут | Тост `link_autodownload_error_timeout` | Тост `link_autodownload_error_timeout` |

---

## 8. Управление авторизациями в Настройках

`AuthSessionsActivity` → `AuthSessionsListFragment` - раздел «Авторизации для загрузки» в Настройках.

Топ-тулбар с заголовком и кнопкой «+». Без плавающей кнопки, без перекрытия системными панелями.

Кнопка «+» → выбор ресурса из `KnownAuthResources` или «Ввести вручную» → `WebViewAuthDialogFragment`.

**С S0155 (`AuthAccountGroupAdapter`):** список группируется по хостам; каждый хост раскрывается в список аккаунтов. На каждый аккаунт - операции «войти заново», «удалить», «переименовать». На хост - операция «добавить ещё один аккаунт» (запускает WebView-вход; после сохранения - новая запись добавляется к списку, не заменяет существующую).

**С S0157:** постоянные отказы («Не спрашивать», `type=dismissed`) отображаются в том же списке с меткой «Не авторизован (вы отказались)» и только кнопкой «Удалить». Удаление записи отзывает постоянный отказ - предложение снова будет показываться. Каждая строка аккаунта отображает дату последнего использования или «ещё не использовалась».

---

## 9. Хранилище аккаунтов/сессий

`EncryptedCookieStore` (ключ `(хост, accountId)`, `EncryptedSharedPreferences`) хранит:

- `displayName` - редактируемая метка аккаунта.
- Cookie-строку - зашифрована.
- `lastUsedAtEpochMillis` - время последнего успешного использования (для сортировки по умолчанию).

**Миграция (S0155):** существующие записи формата `domain:<host>` автоматически конвертируются в первый аккаунт хоста с `displayName = "Account 1"` / `"Аккаунт 1"` / `"Акаунт 1"`. Принудительного повторного входа нет.

**Миграция (S0157):** при первом запуске версии с S0157 все существующие `acct:` и `domain:` записи стираются (`"s0157_wiped"` флаг в `"link_download_cookies_meta"`). Это однократная очистка - функция авторизации не была выпущена пользователям до S0157.

Постоянные отказы («Не спрашивать») хранятся в `EncryptedCookieStore` как записи с `type=dismissed` и фиксированным `accountId = "__dismissed__"`. Проверка отказа: `AuthSessionRepository.isDismissedForHost(host)`. Запись с `type=dismissed` не имеет куки - `cookieCount = 0` не является сигналом к её удалению. Отдельный `AuthOfferDismissalStore` упразднён (S0157).

---

## 10. Поддержка по сборкам (flavor matrix)

Значения выведены из [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) - сгенерированной сетки возможностей по сборкам.

| Возможность | standard | noLegal | legacy | vr | lite | photos |
|-------------|:---:|:---:|:---:|:---:|:---:|:---:|
| Прямой файл (MP4/MP3/JPEG/..) | ✓ | ✓ | ✓ | ✓ | ✓ | image-only |
| HTML-парсинг (og:*, video/audio/img теги) | ✓ | ✓ | ✓ | ✓ | ✓ | image-only |
| Streaming sniffer (m3u8/mpd в HTML) | ✓ | ✓ | ✓ | ✓ | - | - |
| HLS/DASH → MP4 (Media3 + MediaMuxer) | ✓ | ✓ | ✓ | ✓ | - | - |
| WebView-авторизация (любой домен) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Cookie injection | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Несколько аккаунтов на хост (S0155) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Настройки качества | ✓ | ✓ | ✓ | ✓ | - | - |
| Постобработка UX (плеер vs тост) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Дополнительные стратегии извлечения (`UrlExtractionStrategy` из flavor-source set) | - | ✓ | - | - | - | - |
| Пейсер карусели (`LinkDownloadPacer`, S0973) | - | ✓ | - | - | - | - |

**Столбец `noLegal` (S1392).** Приём ссылок не читает ни одного флага возможностей: в общем коде встречается только `BuildConfig.DEBUG`, а `IS_NO_LEGAL_FLAVOR` не читается нигде в `app_v2/src`. Флаги, которыми `noLegal` отличается от `standard` (`IS_NO_LEGAL_FLAVOR`, `SUPPORT_VR_PLAYER`, `VR_UI_COMPOSITION_LAYER_ENABLED`), в этом коде не участвуют, а все остальные (`SUPPORT_VIDEO`, `SUPPORT_AUDIO`, `SUPPORT_IMAGES`, `SUPPORT_STREAMS`, `SUPPORT_CLOUD`, `SUPPORT_LOCAL_NETWORK`, `SUPPORT_DOCUMENTS`) у обеих сборок объявлены `true`. Поэтому по девяти базовым строкам столбцы совпадают. Различие приходит не из флага, а из source set: модуль `noLegal` добавляет в мультибиндинг дополнительные стратегии извлечения и пейсер - это две последние строки.

---

## 11. Диагностика (debug-сборки)

`BuildConfig.LOG_LINK_DOWNLOAD = true` (только debug) включает verbose-трассировку:

- Каждый HTTP-запрос/ответ (URL без query-string, статус, Content-Type, Content-Length).
- Каждый сегмент HLS-скачивания (индекс, byte range, длительность).
- Каждая операция с куки (домен + количество + имена, но не значения).

`LinkDownloadTrace` - структурированные константы для grep. Все `S0116:` entry-point теги удалены (Verified). Постоянные `Timber.v` трассы под `LOG_LINK_DOWNLOAD` остаются.

Для Instagram/Threads дополнительно работает постоянный диагностический лог с префиксом `S0151-diag:` - какая стратегия отработала, какой набор кандидатов получен (real-video / carousel-image / og-preview), применялась ли сессия.

---

## 12. Ключевые классы

| Класс | Слой | Роль |
|-------|------|------|
| `ReceiveShareActivity` | ui/share | Точка входа, управление UI-флоу |
| `UrlInTextDetector` | ui/share | Извлечение списка http(s)-URL из текста |
| `AccountSelectionManager` | ui/share/helpers | Диалог выбора аккаунта (S0155) |
| `LinkAutoDownloadProgressDialog` | ui/share | Прогресс + отмена |
| `LinkAutoDownloadResultPresenter` | ui/share | Проекция исхода на UX |
| `WebViewAuthDialogFragment` | ui/share/auth | WebView-вход, перехват intent:// |
| `AuthSessionsActivity/ListFragment` | ui/settings/auth | Управление авторизациями |
| `AuthAccountGroupAdapter` | ui/settings/auth | Список хостов→аккаунтов (S0155) |
| `LinkAutoDownloadCoordinator` | domain/usecase/link | Оркестрация всего конвейера |
| `LinkExtractionRegistry` | domain/usecase/link | Реестр стратегий (Hilt multibinding) |
| `UrlExtractionStrategy` | domain/usecase/link | Контракт стратегии |
| `StreamingPipeline` | domain/usecase/link | HLS/DASH → MP4 |
| `DirectFileExtractionStrategy` | data/link | Стратегия: прямой файл |
| `HtmlPageExtractionStrategy` | data/link | Стратегия: HTML-парсинг + streaming sniffer |
| `InvisibleWebViewExtractionStrategy` | data/link | Стратегия: динамический разбор (соцсети) |
| `StreamingManifestSniffer` | data/link | Поиск m3u8/mpd в HTML |
| `StructuredMediaSniffer` | data/link | JSON-LD VideoObject |
| `CandidateSelectionPolicy` | data/link | Выбор лучшего кандидата |
| `LinkDownloadWriter` | data/link | Запись в ресурс с fallback |
| `EncryptedCookieStore` | data/link/cookie | Хранилище куки по (хост, accountId); включает записи `type=dismissed` (S0157) |
| `LinkDownloadCookieJar` | data/link/cookie | OkHttp CookieJar с инжекцией |
| `LinkDownloadSessionContext` | data/link/cookie | Контекст выбранного аккаунта |
| `KnownAuthResources` | data/link/auth | Справочник известных соцсетей |
| `AccountNameHintExtractor` | data/link/auth | Извлечение имени из куки (S0155) |
| `AuthSessionRepository` | domain/repository | Интерфейс доступа к аккаунтам |
| `LinkDownloadTrace` | core/log | Структурированные константы для лога |

---

## 13. Статус открытых вопросов

| Тикет | Открытый вопрос | Статус |
|-------|-----------------|--------|
| S0151 §6.1 | Извлечение видео reel'а / карусели Instagram через динамический разбор - достаточно ли WebView-стратегии или нужен разбор внутреннего JSON | Open - ждёт проверки на устройстве с валидной сессией |
| S0155 | Реализация завершена, ждёт проверки на устройстве | BlockNeedUserTest |
| S0151 §11.3 | Распознавание `threads.com` в логе | Ожидает on-device теста |
| S0144 | Верхний тулбар + вход без `ERR_UNKNOWN_URL_SCHEME` | Implemented, ждёт теста |
| S0157 | 3-кнопочный диалог, universal host, dismissed records в настройках | In Progress |
