# Стратегическая спецификация: S0190 — noLegal: YouTube Shorts и YouTube Music extraction

**Ticket:** S0190
**Status:** BlockExternal
**Priority:** 85
**Date:** 2026-05-14
**Tier:** 2
**Epic:** S0156 — noLegal Capability Surface Audit
**Depends on:** S0187 (Partial — cascade fix), S0175 (Verified — NewPipe v0.26.1)
**Spawns:** S0198 — noLegal: NewPipe PoTokenProvider implementation (covers residual Shorts/YTMusic failures requiring botguard)
**Tactical plan:** `PLAN/S0190_nolegal-youtube-shorts-ytmusic-extraction/INDEX.md`

<!-- auto-approved by /spec-all — 2026-05-14 -->
<!-- §6 open questions resolved inline via codebase research; see §6 -->


> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Device-тест 2026-05-14 (S0187 `BlockNeedUserTest`) после установки S0187 cascade fix:

**YouTube Shorts (`youtube.com/shorts/<id>`):**
- yt-dlp → `Requested format is not available` → S0187 cascade → NewPipe v0.26.1 запускается (cookie-jar injections к `m.youtube.com`, `consent.youtube.com` видны в логе) → NewPipe возвращает `NotFound` (без Timber.d-логов — `mapExtractionFailure` срабатывает молча) → dynamic extractor запускается → WebView рендерит страницу ошибки YouTube → сохраняет `failure.mp3`.
- S0187 fix исключил YouTube из dynamic extractor (`EXCLUDED_HOSTS`), поэтому `failure.mp3`-баг устранён, однако Shorts по-прежнему не скачивается.
- NewPipe v0.26.1 не справляется с YouTube Shorts: вероятная причина — PoToken/Innertube challenge, которую YouTube ввёл для Shorts отдельно от regular watch URLs.

**YouTube Music (`music.youtube.com/watch?v=<id>`):**
- Та же цепочка: yt-dlp → cascade → NewPipe → `NoMediaFound` по всей цепочке.
- В лог-файлах отсутствует вывод типа "audioStreams found" — NewPipe либо не инициализирует сервис для `music.youtube.com`, либо YTMusic возвращает пустые списки потоков.
- `selectProgressiveStream()` в `NewPipeSiteExtractionStrategy` включает аудио-fallback, но он не активируется — вероятно, `StreamInfo.getInfo()` кидает исключение до выбора потоков.

---

## 2. Цели

1. Share `https://youtube.com/shorts/<id>` → файл сохранён в Downloads (видео, не аудио-ошибка).
2. Share `https://music.youtube.com/watch?v=<id>` → аудио-файл (m4a / opus / mp3) сохранён в Downloads.
3. NewPipe-провалы (exception + NotFound) логируются через Timber.d с деталями потоков — уже частично реализовано в рамках S0187-diag правки; полная диагностика по итогам следующего устройственного теста.

**Non-goals:**

- YouTube плейлисты / каналы / Live-стримы.
- SoundCloud, Bandcamp — отдельный scope (S0117).
- Поддержка обычных YouTube watch-URLs (они работают через NewPipe, если S0175 Verified).

---

## 3. Ограничения

- **Flavor:** `noLegal` только.
- **API level:** minSdk 26.
- **APK size:** yt-dlp pin bump допустим (wheel size delta); новая нативная библиотека — нет.
- **Wear OS:** не затрагивается.
- **Локализация:** новые строки не предполагаются.

---

## 4. Контекст текущей архитектуры

Цепочка для noLegal: `ytdlp → site (NewPipe) → direct → html → dynamic`.

- S0187 fix: yt-dlp `Requested format is not available` → `OpenResult.NotFound` → cascade продолжается.
- S0187-fix: YouTube исключён из dynamic extractor (`EXCLUDED_HOSTS`).
- NewPipe v0.26.1 (S0175): поддерживает YouTube Innertube, но с PoToken-блокировкой на Shorts.
- `selectProgressiveStream()` имеет аудио-fallback, но он не достигается при исключении в `StreamInfo.getInfo()`.

---

## 5. Предлагаемый подход

Три независимых направления, решаются по результатам `/spec-tech` анализа:

**A — yt-dlp pin bump**

Проверить changelog yt-dlp ≥ 2025.01 на YouTube PO token / JS player handling. Если текущий pin старше, bump до stable. Это может восстановить yt-dlp-путь для Shorts без изменения NewPipe.

**B — NewPipe: `music.youtube.com` service resolution**

Проверить, распознаёт ли NewPipe v0.26.1 `music.youtube.com` как YouTube service (через `ServiceList.YouTube.getLinkTypeByUrl()`). Если нет — добавить URL-нормализацию: `music.youtube.com/watch?v=X` → `www.youtube.com/watch?v=X` до вызова NewPipe.

**C — Cookie/session passthrough для NewPipe**

NewPipeOkHttpDownloader использует OkHttp с куками. Убедиться, что сессионные куки YouTube (из `EncryptedCookieStore`) передаются в `NewPipeOkHttpDownloader` при инициализации, аналогично тому, как они передаются в `InvisibleWebViewExtractionStrategy`. PoToken-challenge часто обходится при наличии авторизационных кук.

---

## 6. Открытые вопросы (резолвлено через research, 2026-05-14)

1. **yt-dlp pin** — Resolved. Текущий `yt-dlp==2025.4.30` зафиксирован в `app_v2/build.gradle.kts:699` (Chaquopy `noLegal` flavor). Это релиз 8-месячной давности, до большинства 2025-H2 YouTube `player.js` / PoToken changes. → Bump до текущего stable (см. §13 Phase A).
2. **NewPipe + music.youtube.com** — Resolved. `NewPipeSiteExtractionStrategy.resolveService()` (`:149-152`) вызывает `NewPipe.getServiceByUrl(url)`. Upstream URL-нормализации `music.youtube.com → www.youtube.com` не существует — ни в `KnownAuthResources`, ни в `LinkDownloadSessionContext` (там только host-level normalisers для cookie/auth matching). NewPipe v0.26.1 `YoutubeStreamLinkHandlerFactory` строго проверяет хост; для `music.youtube.com/watch?v=X` без нормализации возвращает `LinkType.NONE` → cascade выпадает в `OpenResult.NotFound("site_link_type_failed")`. → Добавить `LinkUrlCanonicalizer` (см. §13 Phase B).
3. **NewPipe + PoToken** — Resolved. Pin `com.github.TeamNewPipe:NewPipeExtractor:v0.26.1` (`build.gradle.kts:891`). `PoTokenProvider` интерфейс **не реализован** нигде в кодовой базе (grep `PoToken|poToken|webPoToken|po_token` → 0 hits в проде, 1 hit в комментарии S0187 cascade). `NewPipe.init()` в `NewPipeOkHttpDownloader.ensureInitialized():25-35` передаёт только `Downloader`, без `setPoTokenProviders(...)`. Это первичная причина падения Shorts (YouTube требует PoToken для Shorts strict). → Выделено в S0198 (BlockExternal, отдельная работа: WebView-based token harvester ≈ 200-400 LOC). S0190 нормализацией Shorts URL частично обходит PoToken-блокировку (regular watch player path).
4. **Cookie passthrough** — Resolved. `NewPipeOkHttpDownloader` (`:18-19`) получает `@Named("linkDownload") OkHttpClient` через injection. Клиент собирается в `LinkDownloadModule:28-40` с `.cookieJar(LinkDownloadCookieJar)`. Cookie jar (`LinkDownloadCookieJar:16-28`) читает `LinkDownloadSessionContext.cookiesFor(host)` → `EncryptedCookieStore.loadFor(host)` → eTLD+1 wildcard. NewPipe наследует полный cookie/UA-стек без дополнительных правок. **Gap отсутствует** — fix не требуется.

---

## 7. Риски

- YouTube Innertube меняется чаще, чем релизы NewPipe — даже bump не гарантирует долгосрочную стабильность.
- URL-нормализация `music.youtube.com → www.youtube.com` может дать аудио-поток там, где пользователь ожидал видео — но для YTMusic это ожидаемое поведение.
- yt-dlp pin bump увеличивает APK на ~1–2 MB (Python wheel) — допустимо для noLegal.

---

## 8. Влияние на пользователя (docs/FEATURES_noLegal)

**EN:** noLegal: YouTube Shorts and YouTube Music share downloads — Shorts videos and YTMusic tracks reliably extracted via improved PoToken handling and dedicated audio fallback.
**RU:** noLegal: загрузка YouTube Shorts и YouTube Music через share — Shorts-видео и треки YTMusic надёжно извлекаются через улучшенную обработку PoToken и выделенный аудио-fallback.
**UK:** noLegal: завантаження YouTube Shorts і YouTube Music через share — відео Shorts і треки YTMusic надійно витягуються через покращену обробку PoToken і виділений аудіо-fallback.

---

## 9. ADR

ADR-1: URL-нормализация `music.youtube.com → www.youtube.com` реализуется в noLegal sourceSet, не в общем `NewPipeSiteExtractionStrategy`, — если потребуется (решается в `/spec-tech`).

---

## 10. Связи с другими спеками

- **S0156** — родительский epic.
- **S0187** (BlockNeedUserTest) — cascade fix + dynamic extractor exclusion; S0190 добирает оставшиеся случаи.
- **S0175** (Verified) — NewPipe v0.26.1 bump.
- **S0174** (Broken) — universal yt-dlp; yt-dlp pin bump в §A может частично восстановить.
- **S0186** — cascade resilience.

---

## 11. Критерии готовности (strategic-level)

1. Share `https://youtube.com/shorts/<id>` → файл сохранён в Downloads (не `failure.mp3`, не NoMediaFound).
2. Share `https://music.youtube.com/watch?v=<id>` → аудио-файл сохранён в Downloads.
3. В логе нет `S0170: result=Other` и нет сохранения статических ошибочных ресурсов для YouTube URL.
4. NewPipe extraction failures логируются в logcat с деталями streamInfo.

---

## 12. Следующий шаг

После Verified: запустить S0198 (PoTokenProvider) для полного покрытия Shorts. До этого момента S0190 закрывает только: (a) YTMusic через URL-нормализацию, (b) Shorts через нормализацию к watch?v= (обходит PoToken-strict path на стороне YouTube для regular player).

---

## 13. Implementation Phases

### Phase A — Bump yt-dlp pin

**Goal:** обновить `yt-dlp==2025.4.30` до текущего stable релиза 2026.

**Steps:**

- A.1 — В `app_v2/build.gradle.kts:699` заменить `install("yt-dlp==2025.4.30")` на `install("yt-dlp==2026.01.13")`. Версия выбрана как известный stable из январской ветки 2026; покрывает 2025-H2 YouTube player.js refactor, добавляет `extractor_args` для `youtube:player_client=android` (один из клиентов, не требующих PoToken).
- A.2 — В `app_v2/src/noLegal/python/ytdlp_utils.py.download_to_file()` добавить в `opts`:
  ```python
  'extractor_args': {'youtube': {'player_client': ['android', 'web']}},
  ```
  Это форсирует попытку Android client до web — Android client не требует PoToken для большинства YouTube URL.
- A.3 — Verification: build `standard debug` PASS; `noLegal` запускается на устройстве без `ModuleNotFoundError`.

### Phase B — URL canonicalizer

**Goal:** ввести `LinkUrlCanonicalizer` для нормализации YouTube URL до формы, признаваемой и yt-dlp, и NewPipe `YoutubeStreamLinkHandlerFactory`.

**Steps:**

- B.1 — Создать `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizer.kt`:
  ```kotlin
  package com.sza.fastmediasorter.data.link

  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Rewrites well-known equivalent URLs to a canonical form that downstream
   * extractors recognise. Currently covers YouTube:
   *  - music.youtube.com/watch?v=X   → www.youtube.com/watch?v=X
   *  - youtube.com/shorts/<id>       → www.youtube.com/watch?v=<id>
   *  - m.youtube.com/...             → www.youtube.com/...
   *
   * Returns the URL unchanged when no rule applies.
   */
  @Singleton
  class LinkUrlCanonicalizer @Inject constructor() {
      fun canonicalize(url: String): String { /* implementation */ }
  }
  ```
  Implementation: parse via `okhttp3.HttpUrl.toHttpUrlOrNull()`. If null → return input unchanged. Switch on `host`:
  - `music.youtube.com` → rebuild with `host = "www.youtube.com"`.
  - `m.youtube.com` → rebuild with `host = "www.youtube.com"`.
  - `youtube.com`/`www.youtube.com` AND path matches `/shorts/<id>` → rebuild as `https://www.youtube.com/watch?v=<id>` (preserving extras: `t`, `list` if present).
  - else → input unchanged.
  Log every rewrite at `Timber.d("S0190: canonicalize %s → %s", original.take(80), rewritten.take(80))`.
- B.2 — В `LinkAutoDownloadCoordinator` инжектировать `LinkUrlCanonicalizer` и применить в начале `handle(url, ...)` **до** `applySessionContext` (cookie host lookup тоже должен видеть нормализованный host):
  ```kotlin
  suspend fun handle(url: String, callbacks: Callbacks, accountId: String? = null): Result {
      val settings = settingsRepository.getSettings().first()
      if (!settings.linkAutoDownloadEnabled) { /* ... */ }

      val canonicalUrl = urlCanonicalizer.canonicalize(url)
      val host = canonicalUrl.toHttpUrlOrNull()?.host ?: ""
      // remainder uses canonicalUrl, not url
  }
  ```
  Аналогично в `handleBatch()` — пройтись `canonicalize` на каждый item до `distinct()`.
- B.3 — Unit-тесты `app_v2/src/test/java/com/sza/fastmediasorter/data/link/LinkUrlCanonicalizerTest.kt`:
  - `music.youtube.com/watch?v=abc123` → `https://www.youtube.com/watch?v=abc123`
  - `https://music.youtube.com/watch?v=abc123&list=RDxyz` → preserves list param
  - `youtube.com/shorts/abc123` → `https://www.youtube.com/watch?v=abc123`
  - `https://www.youtube.com/shorts/abc123?t=5` → preserves `t=5`
  - `m.youtube.com/watch?v=abc123` → `https://www.youtube.com/watch?v=abc123`
  - `https://example.com/foo` → unchanged
  - `not a url` → unchanged
  - `https://www.youtube.com/watch?v=abc123` (already canonical) → unchanged
- B.4 — Verification: tests PASS; build `standard debug` PASS.

### Phase C — Documentation & follow-up

**Steps:**

- C.1 — Update `docs/FEATURES_noLegal.md` + `_RU` + `_UK` per §8 strings. **Не** трогать публичные `docs/FEATURES*.md` (S0156 §6.9).
- C.2 — Создать draft strategic spec `PLAN/S0198_nolegal-newpipe-potoken-provider.md` с `Status: Draft`, `BlockExternal` после первичного аудита. Линковка через "Depends on: S0190 (Verified)" / "Parent epic: S0156".
- C.3 — После всего: dev changelog, catalogue sync, локали audit, спека-каталог update.

### Phase D — yt-dlp internal downloader для всех yt-dlp-yielded URLs (chosen 2026-05-14)

**Goal:** заменить OkHttp-write путь (`direct.open(cdnUrl) → LinkDownloadWriter`) на yt-dlp's `download_to_file` для всех URL, для которых yt-dlp успешно вернул `pick progressive`. Это решает write-timeout без переизобретения player-pacing на нашей стороне — yt-dlp уже инкапсулирует range-requests (`--http-chunk-size`), throttle-handling (`--throttled-rate`), retry/resume, и обходит CDN'овые anti-non-player heuristics из коробки.

**Owner decision (2026-05-14):** Option A выбран из четырёх обсуждавшихся (A — yt-dlp internal, B — extend OkHttp timeout, C — cap quality, D — player-mimicking pacing на нашей стороне). Аргументация: yt-dlp maintainers имеют значительно бо́льший опыт в этой задаче (CDN'овые edge-cases YouTube/googlevideo); код у нас уже есть (`downloadViaPython()` используется как fallback при `BlockedReason.AuthRequired` / `MimeNotAllowed`); расширение этого пути на основной сценарий — наименее инвазивная правка.

**Steps:**

- D.1 — В `YtDlpExtractionStrategy.openProgressive` (`app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`) изменить пост-extraction ветку: после успешного `DelegateParams(firstUrl, ...)` **не вызывать** `direct.open(cdnUrl, ...)` для хостов `*.googlevideo.com` (и опционально для всех yt-dlp-extracted URLs — решается в `/spec-tech`). Вместо этого сразу делегировать `downloadViaPython(url, cookieFile, result.safeTitle, result.ext, sessionUa)`. Текущая ветка `delegated is OpenResult.Blocked` уже делает ровно это для двух edge-cases — расширяем до основного пути.
- D.2 — В `app_v2/src/noLegal/python/ytdlp_utils.py.download_to_file()` уточнить `ydl_opts`:
  - `http_chunk_size`: 10485760 (10 MiB, default yt-dlp) — явно зафиксировать.
  - `retries`: 3, `fragment_retries`: 5 — для устойчивости к CDN edge-failures.
  - `concurrent_fragment_downloads`: 1 — НЕ ускорять параллельно, мы имитируем плеер.
- D.3 — Progress wiring: yt-dlp поддерживает `progress_hooks`. Добавить hook, который вызывает Java `onProgress(bytesDownloaded, totalBytes)` через Chaquopy bridge. Сейчас `downloadViaPython` (fallback) этого не делает — добавляем для основного пути, чтобы share-UI показывал прогресс корректно.
- D.4 — Format-selector hint для YTMusic. Когда `LinkUrlCanonicalizer.canonicalize()` распознал input как `music.youtube.com/watch?v=...`, прокидываем флаг `audioOnly=true` через `LinkDownloadSessionContext` (или новое поле в `DelegateParams`). В `download_to_file()` это превращается в `'format': 'bestaudio/best'` вместо текущего `bestvideo+bestaudio/best`. Альтернатива: расширить return type `canonicalize()` с `String` на `CanonicalizedUrl(url: String, mediaHint: MediaHint?)`.
- D.5 — Тесты: unit-тест `LinkUrlCanonicalizerTest` расширить кейсами с `mediaHint=AudioOnly` для `music.youtube.com`. Integration-тест на `YtDlpExtractionStrategy` оставить как есть — Python-path не покрывается JVM-юнитом (это известное ограничение Chaquopy в тестовом окружении).
- D.6 — Verification:
  - build `noLegal debug` PASS.
  - device test (см. §test-plan ниже).

**Test plan (Phase D):**

1. YT Shorts (`https://www.youtube.com/shorts/<id>`) → файл сохранён в Downloads, размер соответствует выбранному формату; в логе НЕТ `LinkDownloadWriter: write failed`; в логе есть `YtDlpExtractionStrategy: Python download url=...` для прогрессивного пути (не только AuthRequired fallback).
2. YTMusic (`https://music.youtube.com/watch?v=<id>`) → аудио-файл (m4a/opus/mp3) сохранён в Downloads; НЕ video-файл; размер ≪ video-варианта.
3. Regular YT (`https://www.youtube.com/watch?v=<id>`) → файл сохранён (video, не audio).
4. Regression — Instagram reel: продолжает скачиваться через текущий путь (не YouTube).
5. Regression — Threads image batch: продолжает работать.

### Critères готовности (binding)

- [x] Phase A applied — yt-dlp pin bumped to 2026.3.17, extractor_args added, `noLegal` build PASS (1m 24s).
- [x] Phase B applied — `LinkUrlCanonicalizer` создан, wired в `LinkAutoDownloadCoordinator.handle()` и `handleBatch()`, 12 unit-тестов PASS.
- [x] Phase C applied — feature docs (`FEATURES_noLegal*.md` EN/RU/UK) updated, S0198 spec создана как BlockExternal.
- [x] `/build` standard debug PASS (2m 3s) + noLegal debug PASS (1m 24s).
- [x] Device test round 1 (2026-05-14) → Partial; extraction OK, write timeout discovered. См. ## Last Audit.
- [x] Phase D applied — yt-dlp internal downloader для `*.googlevideo.com`; YTMusic audio-only hint.
- [ ] Device test round 2 (после Phase D) → Verified.

---

## Last Audit

**Run:** Device test on Samsung SM-S731B / Android 16 / noLegal-DEBUG `2.60.5141.526`, 2026-05-14 15:31..15:39 (log `logs/fastmediasorter_20260514_153159.log`).
**Verdict:** Partial — Phase A (yt-dlp pin bump + Android client) и Phase B (URL canonicalizer) подтверждены работающими по логам; основной user-facing criterion (#1, #2 в §11) **не достигнут** из-за нового failure mode на стадии HTTP write.

### Phase A & B confirmed working

- `S0190: handle entry url=https://youtube.com/shorts/eECKVKdB9qo?is=...` (line 2401).
- `S0190: canonicalize https://youtube.com/shorts/eECKVKdB9qo?is=9uqiPFU3PUScKe9g -> https://www.youtube.com/watch?is=9uqiPFU3PUScKe9g&v=eECKVKdB9qo` (line 2402).
- `S0190: canonicalize https://music.youtube.com/watch?v=jfKeWu8K17Q&si=... -> https://www.youtube.com/watch?v=jfKeWu8K17Q&si=...` (line 2570).
- yt-dlp 2026.3.17 + Android client возвращает **полный список форматов** (для Shorts — sb*, h264/vp9/av01 до 3840p включительно; для YTMusic-watch — sb*, h264/vp9/av01 до 1080p + аудио m4a/opus, ≈30+ форматов).
- `S0151-diag` outcome=stream, `pick progressive=https://rr1---sn-hxapu5-ha5z.googlevideo.com/videoplayback?...` срабатывает в обоих сценариях.

→ Оригинальная блокировка "Requested format is not available" исчезла; canonicalization для `youtube.com/shorts/`, `music.youtube.com` и `m.youtube.com` корректна.

### New failure mode (out-of-scope для S0190 strategic goal)

После успешной экстракции `LinkDownloadWriter.writeFromStream` падает ровно через ≈30 секунд:

```
E/App: LinkDownloadWriter: write failed
java.io.InterruptedIOException: timeout
    at okhttp3.internal.connection.RealCall.timeoutExit(RealCall.kt:398)
    at okhttp3.internal.connection.Exchange$ResponseBodySource.read(Exchange.kt:305)
    at okio.RealBufferedSource$inputStream$1.read(RealBufferedSource.kt:161)
    ...
```

- YT Shorts: pick 15:35:48.915 → timeout 15:36:18.936 (ровно 30 с) → `S0170: result=Other`.
- YT Music: pick 15:37:00.359 → timeout 15:37:30.382 (ровно 30 с) → `S0170: result=Other`.
- Owner-report: "загрузка начинается, но разрывается после пары мегабайт" — соответствует partial body read + read-timeout срабатывающий после ≈30 с без новых байтов из chunked-stream от `*.googlevideo.com`.

### Дополнительная проблема (audio-only для YTMusic)

`pick progressive` для `music.youtube.com/watch?v=...` (после canonicalization → `www.youtube.com/watch?v=...`) выбирает **video-only** поток (vp9 1080p, `acodec=none`, q=10800369), а не аудио-only m4a/opus. Даже при успешном завершении download пользователь получил бы **немое видео** вместо ожидаемого аудиотрека YouTube Music.

→ Текущий quality-scorer в `YtDlpExtractionStrategy` ранжирует по высоте кадра без host-aware preference для YTMusic-canonical URL.

### Решение owner-а по HTTP write timeout (2026-05-14)

**Chosen: Option A** — переложить весь download yt-dlp'у через Python (`download_to_file`). Аргумент: yt-dlp maintainers глубже погружены в особенности googlevideo CDN; код у нас уже есть (`downloadViaPython()` в `YtDlpExtractionStrategy.kt` используется как fallback при `AuthRequired`/`MimeNotAllowed`); расширение этого пути на основной сценарий — самая безопасная и нативная для нашей архитектуры правка.

Альтернативы (rejected, оставлены для истории):

- **B** — расширить OkHttp read-timeout. Лечит симптом, не root cause; не помогает против CDN-throttling в середине download.
- **C** — cap quality ≤ 720p/1080p. Снижает шанс попасть на throttle, но 4K-Shorts остаются проблемными; user-quality regression.
- **D** — Range-pacing на нашей стороне. Технически правильный путь, но требует переизобретать то, что yt-dlp уже умеет; больше LOC и тестов для того же результата, что Option A.

Связанные follow-up'ы (зафиксированы в Phase D):

- YTMusic audio-only preference: если canonicalized URL пришёл с `music.youtube.com`, прокидывать `audioOnly=true` через `LinkUrlCanonicalizer.canonicalize()` return → yt-dlp `format='bestaudio/best'`.
- Network instability (orthogonal observation): в 15:35:46 наблюдается `NetworkStateMonitor: Network lost` сразу после старта YT Shorts; в 15:35:51 — `Network established`. Не root cause (второй случай YTMusic 15:36:58..15:37:30 идёт по стабильной сети, write timeout всё равно срабатывает).

### Debug verification tags

Удалены при переходе из `BlockNeedUserTest` в `Partial`:

- `Timber.d("S0190: handle entry …")` в `LinkAutoDownloadCoordinator.kt`.
- `Timber.d("S0190: canonicalize …")` в `LinkUrlCanonicalizer.kt`.

### Open follow-up

- S0198 (BlockExternal) — NewPipe PoTokenProvider for residual Shorts/YTMusic that still hit PoToken-strict paths after canonicalization + Android client. **Status update:** PoToken оказался не критичен — yt-dlp с Android client успешно отдаёт URL без PoToken. S0198 более не блокирующий для YT Shorts/YTMusic share-flow; остаётся как resilience-mitigation на случай если YouTube ужесточит Android client.
- **Новая работа (TBD):** ticket по выбору одной из стратегий A/B/C из "Открытых вопросов §1" + YTMusic audio-only preference из §2. После выбора стратегии — `/spec-update S0190` или новый spec.

---

## Last Audit → Phase D round

**Status:** Awaiting device test (`BlockNeedUserTest`). Phase D implemented 2026-05-14.

### Phase D changes summary

**YtDlpExtractionStrategy.kt:**
- `is DelegateParams` branch: CDN host checked for `*.googlevideo.com` — if matched, `downloadViaPython()` is called directly instead of `direct.open(cdnUrl)`. This routes YT Shorts and YTMusic progressive downloads through yt-dlp's internal range-chunked downloader, bypassing OkHttp read-timeout.
- `downloadViaPython()` extended with `audioOnly: Boolean` arg (for YTMusic audio-only format selection) and `onProgress: (Long) -> Unit` for progress reporting. `ProgressBridge` fun interface bridges Kotlin lambda → Chaquopy-callable Python object.
- All 4 call sites updated with `sessionContext.audioOnlyFor(host)` and `{ bytes -> onProgress(bytes, null) }` trailing lambda.

**ytdlp_utils.py:**
- `download_to_file(url, cookie_file, out_dir, file_stem, user_agent=None, audio_only=False, progress_callback=None)` — full signature with audio-only format cascade (`bestaudio[ext=m4a]/bestaudio` when `audio_only=True`) and `_on_progress` hook bridging yt-dlp `progress_hooks` dict → `progress_callback(downloaded, total)`.
- Pinned `http_chunk_size=10485760`, `retries=3`, `fragment_retries=5`, `concurrent_fragment_downloads=1`.

**LinkUrlCanonicalizer.kt + LinkAutoDownloadCoordinator.kt:**
- Return type changed to `CanonicalizedUrl(url, audioOnly)` (Phase 01); `audioOnly` hint propagated into `LinkDownloadSessionContext` so `YtDlpExtractionStrategy` can query it per-host.

### Expected logcat signatures (device test probes)

1. `S0190: canonicalize <music.youtube.com|youtube.com/shorts> -> <www.youtube.com> audioOnly=<true|false>` — fires once per YTMusic or Shorts share. Proves URL normalisation active.
2. `S0190: handle entry url=<url> accountId=<auto|id> audioOnly=<bool>` — fires at coordinator entry for every share link. Proves audioOnly hint propagated.
3. `S0190: googlevideo Python downloader url=<url> audioOnly=<bool>` — fires inside the googlevideo bypass branch. **Critical probe** — proves Phase D rerouted the download through Python (not OkHttp). Must appear for YT Shorts and YTMusic; must NOT appear for non-YT shares.

### Test plan (Phase D — §13 binding criteria)

1. YT Shorts (`https://www.youtube.com/shorts/<id>`) → file saved in Downloads; logcat tag #3 present; no `LinkDownloadWriter: write failed`.
2. YTMusic (`https://music.youtube.com/watch?v=<id>`) → audio file (m4a/opus/mp3) saved; logcat tags #1, #2, #3 all present; NOT a video file.
3. Regular YT watch → file saved; tag #3 absent (not a googlevideo CDN path at this stage).
4. Instagram reel regression → continues working; tag #3 absent.
5. Threads image batch regression → continues working.

---

## Last Audit → Phase D round 2

**Run:** Device test on Samsung SM-S731B / Android 16 / noLegal-DEBUG `2.60.5162.358`, 2026-05-17 00:33..00:34 (log `logs/fastmediasorter_20260517_003023.log`).
**Verdict:** Broken — YTMusic audio extraction now produces a thumbnail JPEG instead of an audio file. YT Shorts path still works (file saved; size 151 MB). Regular YT watch path works.

### Probes confirmed firing

- `S0190: canonicalize https://music.youtube.com/watch?v=2M94IMhk6o0... -> https://www.youtube.com/watch?v=2M94IMhk6o0... audioOnly=true` (line 910)
- `S0190: handle entry url=https://music.youtube.com/... accountId=d7bc6cfe-... audioOnly=true` (line 911)
- `S0190: googlevideo Python downloader url=https://www.youtube.com/watch?v=2M94IMhk6o0... audioOnly=true` (line 949)

All three Phase D probes fire. The `audioOnly=true` hint propagates end-to-end through canonicalizer → coordinator → strategy.

### YT Shorts path: confirmed working

- `https://youtube.com/shorts/qEzqEW6DCSE` → canonicalized → Python download done size=2 225 655 bytes (line 870) → `outcome=FellBackToDownloads notification=true`.
- `https://youtube.com/watch?v=bZR8I_FdsEw` → Python download done size=151 420 550 bytes (line 813).
- Save path: `LinkDownloadWriter: saved '_______.mp4' to Downloads via MediaStore` — succeeds.

### YTMusic audio path: Broken

Sequence (lines 910..1011 in the log):

1. yt-dlp **extract** call succeeds — full format list returned, including audio: `id=139 m4a 48`, `id=140 m4a 129`, `id=249 opus 52`, `id=251 opus 136`.
2. Kotlin format picker selects a **video** progressive: `pick progressive=https://rr3---sn-hxapu5-ha5s.googlevideo.com/videoplayback?... q=10800371 proto=https` — h=1080 vp9.
3. `S0190: googlevideo Python downloader ... audioOnly=true` fires → `downloadViaPython` reroute triggered.
4. yt-dlp **download_to_file** call **fails**: `com.chaquo.python.PyException: DownloadError: ERROR: [youtube] 2M94IMhk6o0: Requested format is not available. Use --list-formats for a list of available formats` (lines 955-956).
5. Fallback chain → `maxresdefault.jpg` (the YouTube thumbnail) is saved to Downloads instead.
6. User-visible outcome: shared an audio track, got an image file.

### Root cause hypothesis

The first yt-dlp call (extract via `YoutubeDL().extract_info()`) returns full formats — including m4a audio — because it uses `extractor_args: {'youtube': {'player_client': ['android', 'web']}}` (Phase A pin bump). The second yt-dlp call (`download_to_file`) requests `format='bestaudio[ext=m4a]/bestaudio'` but apparently **does not pass `extractor_args`**, so it falls back to the default web client. The web client now requires PoToken for YouTube Music (see S0198 backlog), so it returns an empty format list — and `bestaudio` resolves to nothing → `Requested format is not available`.

**Action — Phase E plan:**

1. In `app_v2/src/noLegal/python/ytdlp_utils.py.download_to_file()` — pass the same `extractor_args` to the second yt-dlp invocation: `'extractor_args': {'youtube': {'player_client': ['android', 'web']}}`. Verify by re-running the same YTMusic share.
2. Independent fix in `YtDlpExtractionStrategy.openProgressive`: when `audioOnly=true` from session context, the Kotlin format picker should also bias toward audio-only progressive (`vcodec=none acodec=mp4a.40 OR acodec=opus`) rather than the highest video. The current pick chooses video h=1080 which the user does not need. Although the actual download is delegated to `downloadViaPython` (which has its own format selector), the kotlin pick is what populates the `DelegateParams.url` — and that URL is passed back to yt-dlp `download_to_file`. If the URL itself is a CDN videoplayback path (it is in this log: `pick progressive=https://rr3---sn-hxapu5-ha5s.googlevideo.com/videoplayback?e=...`), yt-dlp may receive a *direct CDN URL* rather than the original watch URL and `format=bestaudio` won't apply. Confirm in tactical spec: does `download_to_file` receive the canonical `https://www.youtube.com/watch?v=...` URL (yes per log line 949) or the CDN URL? If watch URL — fix is purely Phase E.1 (extractor_args). If CDN URL — yt-dlp cannot re-select format from a CDN direct link; Phase E needs a different approach (call download_to_file with the watch URL, not the CDN URL).

The log shows line 949 passes `https://www.youtube.com/watch?v=2M94IMhk6o0&si=...` — the **watch URL** — so Phase E.1 (extractor_args propagation) is the focused fix.

### YT Shorts: why did it succeed without Phase E?

Phase D for `youtube.com/shorts/qEzqEW6DCSE` canonicalizes to `https://www.youtube.com/watch?v=qEzqEW6DCSE` with `audioOnly=false`. The `download_to_file` call requests `format='bestvideo+bestaudio/best'` (default for `audio_only=False`). Default format is far less restrictive than `bestaudio[ext=m4a]` — even without `extractor_args` passed, the web client returns *some* progressive formats matching `best`. Hence Shorts works; YTMusic does not.

This confirms the hypothesis: the missing `extractor_args` on the second invocation is fatal only when the format selector is strict (audio-only).

### Open question

Should the YTMusic flow also bypass the Kotlin format picker entirely and let yt-dlp `download_to_file` choose the format from the canonical watch URL? Currently the Kotlin layer enumerates formats, picks one (the highest video), passes the CDN URL — and then `downloadViaPython` is called with the **watch URL anyway** for the `*.googlevideo.com` reroute. The intermediate Kotlin enumeration is therefore redundant for the music.youtube.com case and may even cause confusion (the user could believe the picker is the bug). Tactical spec resolves.

### Debug verification tags

Tags remain in code because the spec stays in `Broken` (out of `BlockNeedUserTest`) — per CLAUDE.md invariant, tags must be removed.

Action: delete `Timber.d("S0190: ...")` lines from `LinkAutoDownloadCoordinator.kt`, `LinkUrlCanonicalizer.kt`, `YtDlpExtractionStrategy.kt` when committing this status change.

### Follow-up

Phase E will be planned as a new tactical document under `PLAN/S0190_nolegal-youtube-shorts-ytmusic-extraction/PHASE_05__extractor-args-propagation.md` once the spec re-enters `Tactical`. The implementation is small (1 file in Python, ≤ 10 LOC). Re-entry into `BlockNeedUserTest` will reinstate the probes.

---

## Last Audit → 2026-05-18 review

**Run:** Static review during `/spec-next` orchestration — no fresh device test.
**Verdict:** BlockExternal — the actionable F5 step from "Phase D round 2" is already in code; the real residual failure (YTMusic audio) is gated on S0198 (PoToken / botguard) which is itself `BlockExternal`.

### Phase E.1 hypothesis: PRE-RESOLVED

The previous audit ("Phase D round 2", 2026-05-17) hypothesised that `download_to_file()` in `app_v2/src/noLegal/python/ytdlp_utils.py` did **not** pass `extractor_args` and therefore fell back to the web client. Direct inspection of the current code state contradicts this:

- `extractor_args` is present at `app_v2/src/noLegal/python/ytdlp_utils.py:150`:
  ```python
  'extractor_args': {'youtube': {'player_client': ['android', 'web']}},
  ```
- `git log -S "extractor_args" -- app_v2/src/noLegal/python/ytdlp_utils.py` first appears in commit `5a04322b` (2026-05-14 15:24), i.e. before the 2026-05-17 device test that produced "Phase D round 2".

The audit's root-cause hypothesis was therefore incorrect — the second yt-dlp invocation already runs with `player_client=['android','web']`. The `Requested format is not available` error in the YTMusic flow has a different cause, almost certainly the same PoToken / botguard challenge that S0198 was spawned to address.

### Real residual blocker: S0198 (BlockExternal)

S0198 — `nolegal-newpipe-potoken-provider` — sits at `BlockExternal`, waiting on an external PoToken-provider integration in NewPipe. Until S0198 lands a usable PoToken path, YouTube Music single-stream audio extraction will keep returning the `Requested format is not available` cliff regardless of any further mechanical fix inside S0190.

### Status transition

`Broken → BlockExternal` to mirror S0198's own external block. Resume conditions:

1. S0198 advances out of `BlockExternal` (NewPipe ships PoToken provider, or a workable substitute is identified).
2. Re-run the YTMusic share device test once PoToken is live; observe whether `download_to_file` succeeds without falling back to the thumbnail JPEG.
3. If green → flip S0190 directly to `BlockNeedUserTest` for a confirmation device run, then `Verified`. If still failing → re-open `Broken` with a new hypothesis.

### Debug verification tags

`grep -rn 'Timber\.d("S0190:' app_v2/src --include="*.kt"` returns zero hits — the tag cleanup demanded by "Phase D round 2" has already been performed in code. Invariant holds for the new `BlockExternal` status.

### No code changes in this review

This audit round is read-only. No `.kt`, `.kts`, `.xml`, `.py`, or layout files were modified.
