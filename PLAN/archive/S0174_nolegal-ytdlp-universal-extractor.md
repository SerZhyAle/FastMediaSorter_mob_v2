# Стратегическая спецификация: S0174 — noLegal: универсальный extractor на базе yt-dlp

**Ticket:** S0174
**Status:** BlockByOtherTask
**Implemented date:** 2026-05-12
**Priority:** 70
**Date:** 2026-05-12
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — ресёрч S0156, итерация 2026-05-12
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical spec:** [`PLAN/S0174_nolegal-ytdlp-universal-extractor/INDEX.md`](PLAN/S0174_nolegal-ytdlp-universal-extractor/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

noLegal flavor содержит extraction-инфраструктуру, покрывающую YouTube/YTMusic/SoundCloud через NewPipeExtractor, и WebView-fallback для single-video страниц. Instagram-карусели, Facebook, Threads-галереи, Twitter и сотни других платформ либо падают с `SocialPreviewOnly`, либо полностью не поддерживаются. Поддерживать per-platform Kotlin-экстракторы для каждого сайта нереалистично — API платформ ломаются, схемы меняются. Нужен универсальный механизм, который покрывает 1800+ сайтов и обновляется как пакет, а не как код.

---

## 2. Цели

1. Пользователь может скачать видео или медиа с любого из 1800+ сайтов, поддерживаемых yt-dlp, передав ссылку стандартным share-механизмом.
2. Instagram: работают одиночные посты, карусели (все элементы), Reels, Stories (при наличии авторизации).
3. YouTube/YTMusic: покрытие не хуже текущего NewPipeExtractor (плейлисты, каналы, форматы).
4. TikTok: одиночные видео без watermark; watermark-free поток выбирается явным фильтром формата (`bv[format_id!*=watermark]+ba/best`); при отсутствии watermark-free — fallback на лучший доступный.
5. Facebook: публичные видео при наличии cookies авторизации (анонимное извлечение ненадёжно — Facebook требует login для большинства публичного контента с 2023).
6. Threads: одиночные медиа и галереи.
7. Авторизация: cookies, сохранённые через существующий WebView-логин, передаются yt-dlp автоматически — пользователь не вводит учётные данные повторно.
8. noLegal-specific извлечение вызывается до fallback-цепочки standard flavor — standard-код не затрагивается.

**Non-goals:**

- Замена NewPipeExtractor — он остаётся в цепочке как резервный путь для своих платформ.
- Загрузка через yt-dlp в standard, lite, photos, legacy flavors.
- Поддержка аудио-only форматов (первая итерация — видео и изображения).
- Пакетная загрузка каналов/плейлистов в первой итерации (фокус — single URL → single/carousel media).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Автоматическое определение наилучшего формата (приоритет: оригинальное качество без watermark).
2. При карусели — загрузка всех элементов как batch, отображение прогресса по элементам.
3. Возможность в будущем обновлять yt-dlp независимо от основного APK (горячее обновление пакета).

### 3.2 Жёсткие ограничения

- **Flavor:** `noLegal` только. Ни один байт yt-dlp или Chaquopy не попадает в standard, lite, photos, legacy.
- **API level:** minSdk 26 (standard для noLegal flavor). Chaquopy поддерживает API 21+, ограничений нет.
- **Wear OS:** не затрагивается.
- **APK size:** без ограничений — noLegal не публикуется в Google Play, личное использование. Ожидаемый overhead: ~12–16 MB на device slice (arm64-v8a + yt-dlp wheel).
- **Лицензии:** без ограничений — личное использование; GPL, AGPL, Chaquopy Community допустимы.
- **Совместимость данных:** без изменений Room-схемы.
- **Локализация:** новые строки (прогресс, ошибки) — EN/RU/UK.
- **Доступность:** не применяется (нет новых UI-элементов).
- **Принцип overlay:** noLegal-extractor встаёт перед существующей strategy chain; standard-код не модифицируется; при `NotApplicable` управление передаётся в стандартную цепочку.

---

## 4. Контекст текущей архитектуры

### 4.1 Extraction chain

Стратегии регистрируются через Hilt `@Binds @IntoSet` в двух DI-модулях:
- `LinkDownloadModule` (main sourceSet) — регистрирует `direct`, `html`, `dynamic`.
- `NoLegalLinkDownloadModule` (noLegal sourceSet) — регистрирует `NewPipeSiteExtractionStrategy` с id `"site"`.

`LinkExtractionRegistry` сортирует стратегии по `CANONICAL_ORDER = listOf("site", "direct", "html", "dynamic")`. Стратегии с id вне списка попадают в конец (`Int.MAX_VALUE`).

**Архитектурный блокер:** `CANONICAL_ORDER` живёт в `main` sourceSet. Чтобы `YtDlpExtractionStrategy` (id `"ytdlp"`) была первой, нужно либо:
- (A) добавить `"ytdlp"` в `CANONICAL_ORDER` в main — нарушает overlay-принцип (main знает о noLegal id);
- (B) вынести механизм приоритизации в `Set<StrategyOrderEntry>` Hilt multibinding — каждый DI-модуль вносит свои приоритеты, registry сортирует по ним;
- (C) ввести noLegal-специфичный подкласс `LinkExtractionRegistry` с переопределённым порядком.

Выбор варианта — обязательное архитектурное решение `/spec-tech`.

### 4.2 Cookie store

`EncryptedCookieStore` хранит cookies по точному hostname (как при сохранении). `LinkDownloadCookieJar` реализует eTLD+1-lookup при загрузке для OkHttp. Для cookie bridge в yt-dlp нужна аналогичная eTLD+1-логика: при сериализации Netscape-файла перебрать все сохранённые записи, чей `registrableDomain()` совпадает с целевым host. Хелпер `registrableDomain()` из `LinkDownloadCookieJar` должен быть вынесен в shared utility (S0171 зависимость).

**Важно:** `WebViewAuthDialogFragment` сохраняет cookies по точному host без eTLD+1. Cookie bridge должен это учитывать — сопоставлять по `registrableDomain()`, а не по exact match. `facebook.com` в `KnownAuthResources` отсутствует — нужно добавить.

### 4.3 OpenResult.Batch

`OpenResult.Batch` уже реализован и обрабатывается в `LinkAutoDownloadCoordinator.runBatch()`. При Batch-результате координатор вызывает `handleUrl()` per item — каждый item-URL снова проходит probe-цепочку. `YtDlpStrategy.probe()` должна возвращать `NotApplicable` для CDN-URL (прямых ссылок на медиафайл), чтобы не вызывать `extract_info` повторно на уже разрешённых URL.

### 4.4 Probe timeout

`LinkDownloadModule` задаёт `callTimeout(30s)` на уровне OkHttp. Собственного таймаута на probe-вызов нет. yt-dlp `extract_info` с `process=False` может блокировать 5–30 с на медленных сетях. Нужен явный Python-side timeout (`socket_timeout` в `ydl_opts` + `concurrent.futures` wrapper с `result(timeout=8)`) — иначе весь coordinator подвисает до OkHttp callTimeout.

### 4.5 Chaquopy — ограничения, критичные для реализации

- `extractNativeLibs=true` обязателен — иначе Python `.so` не загрузится из сжатого APK.
- Холодный старт (первый вызов, runtime не распакован): 1.5–3 с до начала сетевого I/O. Прогрев при старте приложения рекомендован.
- `subprocess.Popen` и `os.fork` заблокированы Android — yt-dlp post-processors, вызывающие ffmpeg через shell, упадут с `FileNotFoundError`. ADR-2 (не использовать встроенный downloader yt-dlp) устраняет эту проблему полностью.
- `signal.signal()` частично работает на Android — timeout через `SIGALRM` недоступен. Таймаут реализуется через `concurrent.futures`.
- CPython GIL: реальной параллельности нет. Несколько одновременных вызовов `extract_info` сериализуются. Kotlin-side — dedicated single-thread executor.
- yt-dlp не thread-safe на уровне разделяемого состояния `YoutubeDL` instance — создавать новый экземпляр на каждый вызов.

---

## 5. Предлагаемый подход

Встроить Python-runtime (Chaquopy) с yt-dlp в noLegal sourceSet. Новый extraction-компонент работает как высокоприоритетная site-стратегия: `probe(url)` быстро проверяет поддержку сайта (`extract_info` с `process=False`), `open(url)` запускает `extract_info` с `download=False` для получения URL и метаданных, затем финальная загрузка идёт через существующий CDN-download компонент с нужными заголовками.

### 5.1 Основные столпы

**A — Chaquopy runtime в noLegal sourceSet**
- Python 3 runtime + yt-dlp wheel, встроенные в APK через noLegal-специфичный `pip` DSL-блок в Gradle.
- Chaquopy plugin применяется ко всем вариантам на уровне Gradle (не flavor-scopable), но `pip`-пакеты скоупятся в `noLegal` flavor — другие flavors wheel не получают.
- Версия yt-dlp фиксируется в Gradle; обновление — смена одной строки зависимости.
- Инициализация Python runtime: lazy singleton с double-checked locking; прогрев при старте приложения в background.
- `extractNativeLibs=true` в manifest noLegal или через `packagingOptions`.

**B — Cookie bridge**
- При каждом вызове: сериализовать актуальные cookies для целевого хоста из `EncryptedCookieStore` в Netscape cookie file во временный файл в `context.filesDir` (не в `/sdcard/` — Python file I/O туда не ходит).
- eTLD+1 matching при перечислении записей cookie store — переиспользовать `registrableDomain()` helper (вынести из `LinkDownloadCookieJar` в shared utility).
- Передать путь к файлу в yt-dlp как `cookiefile` в `ydl_opts`.
- Удалить файл немедленно после завершения вызова (success или failure) через `try/finally`; `deleteOnExit()` как страховка.

**C — UrlExtractionStrategy wrapper**
- Реализует тот же контракт что и существующие стратегии.
- `probe()`: `extract_info(url, download=False, process=False)` в `ydl_opts` с `socket_timeout=8` + `concurrent.futures` wrapper с `result(timeout=10)` → `Applicable` / `NotApplicable`. CDN-URL (прямые ссылки) → немедленно `NotApplicable` (regex-check перед Python-вызовом).
- `open()`: `extract_info(url, download=False)` → Python `dict` как Kotlin `Map` (Chaquopy конвертирует нативно, JSON-парсинг не нужен) → выбрать лучший формат → CDN download через `DirectFileExtractionStrategy.open(url, onProgress, extraHeaders)`.
- Карусели: `info['_type'] in ('playlist', 'multi_video')` → batch из N URL → `OpenResult.Batch`.
- При инициализации Python runtime fail (OOM, SELinux) → `NotApplicable` (не crash), логировать через Timber.
- Priority: выше NewPipeExtractor (позиция 0 в цепочке); при `NotApplicable` — NewPipe и остальная цепочка продолжают работу.

**D — Регистрация в noLegal DI**
- Добавить `"ytdlp"` id в порядок приоритизации (архитектурное решение из §4.1 — выбрать вариант в `/spec-tech`).
- Standard flavor не видит ни строчки Chaquopy или yt-dlp кода.
- `facebook.com` добавить в `KnownAuthResources`.

### 5.2 Потоки данных и событий

```
Пользователь share → LinkAutoDownloadCoordinator
    → applySessionContext(host)
    → LinkExtractionRegistry.probe(url) — перебирает стратегии по приоритету
        → YtDlpExtractionStrategy.probe(url)          [noLegal, NEW]
            → CDN-URL check → skip if direct link
            → Chaquopy: extract_info(url, process=False, socket_timeout=8)
            → Applicable / NotApplicable
        → YtDlpExtractionStrategy.open(url)
            → write cookie file (EncryptedCookieStore → Netscape, eTLD+1 match)
            → Chaquopy: extract_info(url, download=False) → Python dict → Kotlin Map
            → delete cookie file (try/finally)
            → single video: CDN download via DirectFileExtractionStrategy
            → carousel (_type=playlist/multi_video): OpenResult.Batch(item URLs)
    [если NotApplicable → NewPipeSiteExtractionStrategy → direct → html → dynamic]
```

### 5.3 Точки расширяемости

- Cookie bridge можно расширить на передачу browser-profile cookies в будущем.
- `YtDlpStrategy` может принять флаги формата через settings без изменения архитектуры.
- `gallery-dl` (~2 MB wheel, ~300 сайтов, фокус на галереи/изображения) может быть добавлен как второй Python-модуль в тот же Chaquopy runtime для дополнительного покрытия. Не замена yt-dlp — complementary для image-only платформ.

---

## 6. Открытые вопросы / Research items

1. **`CANONICAL_ORDER` и overlay-принцип** — [BLOCKER для /spec-tech]
   - **Вопрос:** Какой из трёх вариантов (§4.1 A/B/C) принять для размещения `"ytdlp"` первым в цепочке, не нарушая overlay-принцип?
   - **Статус:** Open — обязательное решение в `/spec-tech`.

2. **Facebook в `KnownAuthResources`**
   - **Вопрос:** Нужно ли добавить `facebook.com` в `KnownAuthResources` чтобы при yt-dlp `NotApplicable` или probe-fail на Facebook-URL — coordinator показывал SocialPreviewOnly + auth-prompt, а не `NoMediaFound`?
   - **Статус:** Open — решить в `/spec-tech`.

3. **APK size overhead**
   - **Вопрос:** Фактический размер Chaquopy + yt-dlp в noLegal APK/AAB slice.
   - **Нужно выяснить:** Собрать noLegal debug APK с Chaquopy, замерить. Ожидание: 12–16 MB per arm64-v8a slice; бюджет отсутствует (не Play).
   - **Статус:** Open (выясняется на этапе /spec-tech feasibility).

4. **Startup latency**
   - **Вопрос:** Фактическое время cold-start на целевом устройстве.
   - **Нужно выяснить:** Замерить на API 26/34 устройстве. Ожидание из community-benchmarks: 1.5–3 с до первого I/O. Если >3 с — реализовать прогрев при старте приложения.
   - **Статус:** Open (feasibility фаза).

5. **Chaquopy Community license**
   - **Вопрос:** Chaquopy Community (Apache 2.0) ограничен non-commercial use или open-source. noLegal — личное использование, не распространяется.
   - **Статус:** Resolved — допустимо.

6. **yt-dlp зависимости с GPL**
   - **Вопрос:** `mutagen` (GPL-2.0) входит в wheel. При личном использовании некритично.
   - **Статус:** Resolved — допустимо.

7. **W^X policy и SELinux на Android 14+**
   - **Вопрос:** Строгие ограничения на динамическое исполнение кода в Android 14+.
   - **Статус:** Resolved — Chaquopy использует нативные `.so` через JNI, не запускает бинарники из data-директорий. При ADR-2 (yt-dlp как Python library, не subprocess) SELinux-блокировки не применяются.

8. **16 KB page-size (Android 15 / API 35)**
   - **Вопрос:** Chaquopy 16.x поставляет `libpython3.x.so`, не выровненный по 16 KB страницам. Это блокирует Play Store для API 35 targetSdk после ноября 2025.
   - **Статус:** Resolved — noLegal не публикуется в Play Store, личное использование. Не блокер.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Instagram extractor ломается 2–4 раза в год при изменении API/HTML | Высокая (исторически) | yt-dlp extractor не работает до обновления пакета | Фиксировать версию yt-dlp; мониторить releases; обновление — одна строка в Gradle |
| YouTube PO token (yt-dlp < 2024.08 не работает) | Низкая (если версия актуальна) | YouTube-видео не извлекаются | Пинить yt-dlp ≥ 2024.08.06 |
| TikTok CDN signing ломается 2–3 раза в год | Средняя | Watermark-free поток недоступен, fallback на watermarked | Явный format-filter; fallback к лучшему доступному |
| Facebook требует login cookies для большинства публичного контента | Высокая (с 2023) | Анонимное извлечение ненадёжно | Cookie bridge + KnownAuthResources для facebook.com |
| Chaquopy cold-start > 3 с на слабых устройствах | Средняя | UX degradation при первом share | Lazy init + progress UI; прогрев в background при старте |
| `socket_timeout=8` не покрывает медленные сети при probe | Низкая | probe() возвращает NotApplicable на валидном URL | Увеличить до 10–12 с; добавить network-aware timeout |
| Cookie file не удалён при crash в open() | Низкая | Сессионные cookies в plaintext в temp-файле | `try/finally` в cookie bridge; `deleteOnExit()` как страховка |
| `CANONICAL_ORDER` не содержит `"ytdlp"` — стратегия идёт последней | Определённая (нынешний код) | YtDlpStrategy никогда не запускается первой | Решить архитектурный вопрос §6.1 в /spec-tech |
| yt-dlp не thread-safe на уровне shared instance | Низкая (если соблюдать per-call instance) | Race condition при concurrent downloads | Создавать новый `YoutubeDL()` на каждый вызов; single-thread executor на Kotlin side |

---

## 8. Влияние на пользователя (docs/FEATURES)

**EN:** noLegal flavor: universal media download from 1800+ sites (YouTube, Instagram, TikTok, Facebook, Threads, X, and more) via yt-dlp engine with automatic auth cookie passthrough.
**RU:** noLegal: универсальная загрузка медиа с 1800+ сайтов (YouTube, Instagram, TikTok, Facebook, Threads, X и др.) через движок yt-dlp с автоматической передачей cookies авторизации.
**UK:** noLegal: універсальне завантаження медіа з 1800+ сайтів (YouTube, Instagram, TikTok, Facebook, Threads, X тощо) через рушій yt-dlp з автоматичною передачею cookies авторизації.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Chaquopy как Python runtime, а не libytdlp.so binary**

- **Решение:** Chaquopy (Python JVM bridge) вместо PyInstaller-скомпилированного ELF binary.
- **Альтернативы:** `libytdlp.so` (PyInstaller ELF), cobalt REST API, нативный Kotlin extractor per-site.
- **Почему:** Chaquopy даёт нативный Python API — yt-dlp вызывается как Python-модуль без subprocess overhead; обновление yt-dlp — одна строка в Gradle без перекомпиляции бинарника. PyInstaller ELF неприменим на Android API 34+ из-за SELinux W^X. Cobalt — REST-зависимость от внешнего сервиса (нет гарантий аптайма публичного инстанса, rate limits ~5–10 req/min); self-hosting — операционная нагрузка; ценность cobalt в отсутствие APK-overhead не актуальна для noLegal. gallery-dl рассмотрен как complementary модуль (§5.3), не замена.

**ADR-2: Использование Python API (extract_info), не прямая загрузка через yt-dlp**

- **Решение:** yt-dlp используется как Python-библиотека только для разрешения URL и метаданных (`extract_info(..., download=False)`); сама загрузка — через существующий CDN-download компонент приложения.
- **Альтернативы:** Полная загрузка через yt-dlp (встроенный downloader).
- **Почему:** Переиспользует существующий progress-tracking, error-handling, storage-routing, и cookies infrastructure. yt-dlp downloader дублировал бы их. Критически: на Android `subprocess.Popen` заблокирован — yt-dlp post-processors (в т.ч. ffmpeg-merging) упали бы с `FileNotFoundError`. ADR-2 полностью устраняет потребность в FFmpeg: загрузка и мультиплексирование потоков — нативными компонентами приложения.

**ADR-3: Overlay-принцип, не замена стратегий**

- **Решение:** YtDlpStrategy встаёт первой в multibinding; при `NotApplicable` NewPipe и WebView-fallback продолжают работать.
- **Почему:** Standard-код не трогается; при отказе yt-dlp (новый сайт, обрыв сети, extractor breakage) пользователь получает деградированный, но рабочий fallback, а не полный отказ.

**ADR-4: Новый `YoutubeDL()` instance per call, single-thread executor**

- **Решение:** Каждый `probe()`/`open()` вызов создаёт новый `YoutubeDL` instance; Kotlin-side использует dedicated single-thread executor для Python-вызовов.
- **Почему:** yt-dlp не thread-safe на уровне shared `YoutubeDL` state. CPython GIL сериализует Python-выполнение, поэтому реальной параллельности нет — single-thread executor не создаёт UX-проблем, но устраняет race conditions.

---

## 10. Связи с другими спеками

- **S0156** (noLegal Capability Surface Audit) — родительский epic.
- **S0171** (eTLD+1 cookie wildcard) — cookie bridge зависит от корректной работы eTLD+1 lookup; `registrableDomain()` helper должен быть shared utility до или параллельно с S0174.
- **S0176** (applySessionContext eTLD+1 fix) — complementary fix в shared-коде.
- **S0175** (NewPipeExtractor version bump) — независим; параллельно.
- **S0177** (native site extractors) — complementary; покрывает сайты где yt-dlp избыточен.

---

## 11. Критерии готовности (strategic-level)

1. Пользователь передаёт Instagram-ссылку на пост с каруселью через share → все медиаэлементы карусели скачиваются.
2. Пользователь передаёт YouTube-ссылку → видео скачивается (не хуже текущего NewPipeExtractor).
3. Пользователь передаёт TikTok-ссылку на видео → видео скачивается без watermark (при наличии watermark-free потока).
4. Cookies, сохранённые через WebView-логин для instagram.com, автоматически используются при загрузке приватного поста.
5. Standard flavor APK не содержит ни одного класса Chaquopy или yt-dlp.
6. При недоступности yt-dlp (simulate timeout, extractor error) — управление передаётся NewPipeExtractor и WebView-fallback без ошибки для пользователя.
7. Cookie file с session cookies удаляется немедленно после каждого вызова (success или failure).

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0174` — создаст `PLAN/S0174_nolegal-ytdlp-universal-extractor/` с фазами реализации.

---

## Last Audit

**Date:** 2026-05-14
**Mode:** device-test
**Flags:** —
**Outcome:** Broken
**Counts:** PASS 3 (TikTok video, Threads video, Instagram video) · FAIL 4 (YouTube, YT Music, Instagram photo+carousel, Threads carousel)

### Device test results 2026-05-14

Logs: `fastmediasorter_20260514_004634.log`, `_004828.log`, `_004916.log`,
`_005112.log`.

- **TikTok video** (`vm.tiktok.com/ZNRGUJ9L9/`): yt-dlp returned 8 formats,
  CDN auth 403 → fallback to yt-dlp Python download → 1.46 MB mp4 saved
  successfully. `result=FellBackToDownloads`. ✓
- **Threads video** (`/post/DYStz5hErdB`): dynamic-extractor (yt-dlp not
  attempted on `threads.com` hosts) → 1 mp4 saved. ✓
- **Instagram video**: reported working by owner (not in these specific log
  excerpts but consistent with `vm.tiktok.com`-style flow).
- **YouTube** (`shorts/nVU3RNFLrV8`, two attempts):
  `com.chaquo.python.PyException: DownloadError: ERROR: [youtube]
  nVU3RNFLrV8: Requested format is not available. Use --list-formats for a
  list of available formats` at `YtDlpExtractionStrategy.kt:138`.
  `result=Other`. PyException aborts cascade — NewPipe never tried. ✗
- **YouTube Music** (`watch?v=V3qUoiwr5kQ`): identical
  `Requested format is not available` for `V3qUoiwr5kQ`. Also flagged
  `[S0166] unknown host: youtube.com` — YouTube is not in the known-social
  list, so the account picker never appears. ✗
- **Instagram photo** (`/p/DYSU9o1Mwfk/`): yt-dlp correctly reports
  `There is no video in this post`; html strategy `outcome=not-found`;
  dynamic strategy returns `social-preview-only` (OG meta, not real media).
  `result=SocialPreviewOnly`. ✗
- **Threads carousel** (`/post/DYSIu04jaI4`): dynamic-extractor returns ONE
  asset; remaining carousel slides not retrieved. ✗

### Follow-up tickets created 2026-05-14

- **S0187** — noLegal YouTube extraction recovery (re-route YouTube/YTMusic
  to NewPipe; add youtube.com / music.youtube.com to known-social; yt-dlp
  format-selector hardening / version bump).
- **S0186** — noLegal pipeline cascade resilience (wrap strategy probe/open
  so PyException no longer aborts the chain; ensures NewPipe / html /
  dynamic still get a shot after yt-dlp throws).
- **S0181** (rescoped, prio 70) — Threads + Instagram image/carousel
  extraction via embedded `data-sjs` JSON.

### Manual / on-device

- [ ] §11.1 Instagram carousel share → all media elements download. — code path covered by S0181 (Verified) → S0197 (Verified) + S0223/S0224 (Verified); S0174 still needs a fresh umbrella device re-run.
- [ ] §11.2 YouTube link → video downloads (not worse than NewPipeExtractor). — S0187 is archived; residual gate sits in S0190 + S0186. Current state: S0186 = BlockNeedUserTest, S0190 = BlockExternal in catalog while its tactical implementation is code-complete and the noLegal build passes.
- [x] §11.3 TikTok link → watermark-free video download. — confirmed 2026-05-14.
- [ ] §11.4 Instagram WebView cookies auto-used for private post download. — public photo/carousel extraction moved forward via S0197 (Verified) + S0223 (Verified); private-post proof still needs a dedicated device run with an authenticated session.
- [ ] §11.7 Cookie temp file deleted after each call (success + failure paths). — not directly verifiable from current logs.

### Status decision (/spec-all 2026-05-14)

S0174 переведён `Broken → BlockByOtherTask`. Stage 3 sync обнаружил расхождение: header был ручно откатан до `Verified` после того как `/spec-check` зафиксировал `Broken` в каталоге; header исправлен. Остаточные §11.1, §11.2, §11.4 критерии были вынесены в follow-up specs. Актуальный state refresh на 2026-05-19:

- S0187 (Archived) — YouTube/YTMusic cascade work folded into S0190/S0186.
- S0186 (BlockNeedUserTest) — cascade resilience.
- S0190 (catalog = BlockExternal, tactical INDEX = BlockNeedUserTest) — yt-dlp Python download для googlevideo CDN, canonicalization и audio-only hint присутствуют в коде; `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` → PASS on 2026-05-19.
- S0197 (Verified) — Threads/IG data-sjs extractor.
- S0223 (Verified) — Instagram photo post download.
- S0224 (Verified) — Threads batch notification count.

Внутри S0174 forward progress невозможен — universal yt-dlp scope передан per-site follow-up ticket'ам. После их Verified повторно запустить `/spec-check S0174` для re-evaluation.
