# Спецификация (compact bugfix): S0935 - Instagram reel: yt-dlp extraction падает с HTTP 404

**Ticket:** S0935
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

**Текст:**

> не работает загрузка видео из инстаграмм

Найдено при анализе лога `logs/fastmediasorter_20260704_055031.log` (сборка 2.60.7040.458-NoLegal-DEBUG, SM-S731B, Android 16 / API 36).

Три отказа за сессию, все идентичны - reel не извлекается:
- 13:21:32 reel `DaVDLLZM3cB`
- 13:23:20 reel `DaVDLLZM3cB` (повтор)
- 13:28:27 reel `DaVnfr5xPp8`

Диагностическая строка перед каждым отказом (сессия применена, это НЕ проблема авторизации):

```
S0151-diag: host=www.instagram.com strategy=ytdlp sessionApplied=true outcome=error
LinkDownloadWorker: done result=Other
```

Куки подгружены (9 штук: csrftoken, datr, ig_did, mid, wd, ds_user_id, sessionid, dpr, rur).

Корень (exception blocks в логе, строки 1032/1178/1388):

```
com.chaquo.python.PyException: DownloadError:
  ERROR: [Instagram] DaVDLLZM3cB: Video info extraction failed:
  HTTP Error 404: Not Found (caused by <HTTPError 404: Not Found>)
    at <python>.yt_dlp.YoutubeDL.extract_info(YoutubeDL.py:1716)
    at ...YtDlpExtractionStrategy$open$2.invokeSuspend$lambda$18(YtDlpExtractionStrategy.kt:138)
```

Контрпример из той же сессии: пост `/p/DZ2EDzUoYn8` в 13:27 пошёл по **html**-стратегии (`strategy=html .. outcome=stream`), ассеты сняты `ig-api-sniffer` - т.е. другой путь отработал. Падает именно **ytdlp-путь для reels**.

Гипотеза: два разных reel-а дают чистый HTTP 404 при валидной сессии -> это не «пост удалён», а **устаревший Instagram-экстрактор в бандленном yt-dlp** (Chaquopy pip). Классическая ежемесячная поломка yt-dlp против Instagram; обычно лечится бампом версии.

Связь: [S0822](PLAN/S0822_instagram-authenticated-extraction-fails-with-saved-cookies.md) (BlockNeedUserTest) покрывает ДРУГОЙ режим - протухшая/зачекпойнченная сессия -> login redirect-loop -> re-login prompt. Его statusNote прямо выносит «reel empty-media-response (yt-dlp)» в upstream-лимит («его не проверяем»). Этот тикет переоценивает тот вынос: 404 на нескольких reels скорее чинибелен бампом, чем вечный upstream.

---

## 1. Проблема / симптом

Загрузка видео из Instagram reels не работает: yt-dlp-стратегия получает от Instagram HTTP 404 на этапе `extract_info` несмотря на валидную применённую сессию. Флейвор noLegal (yt-dlp живёт только там). См. §0 - эвиденс, лог-строки, стек.

---

## 2. Корневая причина

Подтверждено чтением кода (не версия yt-dlp):

1. yt-dlp **уже** на nightly `2026.07.02.234458` - владелец бампнул 2026-07-03 (`app_v2/build.gradle.kts`, pip-блок noLegal) именно ради Instagram reels («Instagram: Rework extractor #17075»). Прошлый симптом был *«empty media response»*, nightly сменил его на *«HTTP Error 404»*. Бамп версии - не фикс.
2. Реальный дефект - в классификаторе ошибок `YtDlpExtractionStrategy.open()` (`app_v2/src/noLegal/.../YtDlpExtractionStrategy.kt`, ветки ~407-421). Известные «not applicable» ошибки (`There is no video in this post`, `Unsupported URL`, `Instagram sent an empty media response`, `Requested format is not available`) -> `OpenResult.NotFound`. Всё прочее -> `OpenResult.Error`.
3. `HTTP Error 404: Not Found` не совпадает ни с одной known-строкой -> уходит в `else` -> `OpenResult.Error`.
4. Контракт каскада (`LinkAutoDownloadCoordinator.kt:317/340`): `NotFound -> continue` (следующая стратегия), `Error -> mapIoError(cause)` (обрыв). `CANONICAL_ORDER` = `[.., ytdlp, site, direct, html, dynamic]` - html после ytdlp.
5. Итог: reel-404 обрывает каскад (`LinkDownloadWorker: done result=Other`) вместо провала в html/dynamic. В том же логе html-стратегия сработала для `/p/` поста (`strategy=html outcome=stream`, ig-api-sniffer), т.е. downstream-путь жив - его просто не достигают.

---

## 3. Исправление

`open()` вызывает `extract_info(url, download=false)` - фаза **извлечения**. Значит **любой** отказ yt-dlp здесь = «эта стратегия не смогла достать медиа» -> правильный сигнал `OpenResult.NotFound` (каскад пробует `site/html/dynamic`), а не `Error` (обрывает каскад через `LinkAutoDownloadCoordinator -> mapIoError`).

- В not-applicable ветку `YtDlpExtractionStrategy.open()` добавить: `msg.contains("HTTP Error")` (любой статус: 403/404/410/429/5xx), `msg.contains("DownloadError")`, `msg.contains("ExtractorError")` -> `OpenResult.NotFound("ytdlp_not_applicable")`.
- **Реверс первоначальной оговорки (по device-эвиденсу 2026-07-04):** ранее 5xx/403 намеренно оставались `Error`. Тест показал, что это ошибка - retry-механизма нет, `HTTP Error 500` просто убивал каскад (2 из 7 reels), тогда как fallthrough давал `dynamic` шанс (3 из 7 - реальный .mp4). Все extraction-HTTP-ошибки теперь проваливаются единообразно.
- Непредвиденные исключения (без маркеров `HTTP Error`/`DownloadError`/`ExtractorError`) по-прежнему -> `Error` (else-ветка).
- Файл (единственный): `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`. Flavor-only (yt-dlp живёт лишь в noLegal), `src/main` не затрагивается.

Оговорка о достаточности: fallthrough - необходимое, но недостаточное. Остаток - upstream Instagram: (a) `dynamic` иногда получает 56-80-байтные заглушки (rate-limit) -> валидатор `LinkDownloadWriter`/S0166 корректно бракует (`DownloadCorrupted`); (b) серия запросов провоцирует 500/заглушки. Это вне контроля приложения (ref S0822).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0822 (смежный: auth redirect-loop UX; этот тикет снимает его вынос reel-404 в upstream)
- **Flavor:** noLegal (yt-dlp-стратегия существует только в этом source set; прочие флейворы не затрагиваются)

---

## 4. Проверка

**Device-test 2026-07-04 (лог `fastmediasorter_20260704_182935.log`, 7 reels), первая итерация (только 404/410):** probe `S0935` сработал 5×, каскад продолжился. Исходы: 3× успех (`dynamic` -> реальный .mp4, `FellBackToDownloads`), 2× `HTTP 500` -> обрыв (`result=Other`, НЕ ловилось), 2× `DownloadCorrupted` (upstream-заглушки). Вывод: fallthrough работает, но 5xx-пробел -> расширение (см. §3).

Повторная проверка после расширения:
1. Compile (flavor touch): `.\a.ps1 fkn` (Kotlin compile noLegal) - PASS.
2. Device (BlockNeedUserTest): шарить серию живых Instagram reels в noLegal-сборке. Ожидать: ни `HTTP 404`, ни `HTTP 500` не дают `result=Other` от yt-dlp-шага - в логе `S0935` probe срабатывает на всех extraction-ошибках, затем `site/html/dynamic`. Успех = reel скачан (`FellBackToDownloads`/в ресурс). Допустимо = `DownloadCorrupted` при upstream-заглушке.
3. Остаточные провалы только вида `DownloadCorrupted` / upstream 500-заглушка -> зафиксировать как лимит Instagram (ref S0822), фикс каскада валиден.

**Device-test 2026-07-05 (лог `fastmediasorter_20260705_002529.log`, 12:36-12:40, 3 reels), повторная итерация после расширения:** probe-текст обновлён на `S0935: ytdlp extraction failed - fallthrough to cascade`, сработал 3/3. Все 3 reel-а: `ytdlp not-applicable (HTTP 404)` -> `html not-found` -> `dynamic outcome=stream` -> `DownloadCorrupted` (68/68/80-байтные заглушки). Ни одного `result=Other`. Ровно допустимый исход из шага 2 - каскад полный, остаток = upstream rate-limit (ref S0822). Критерий подтверждён.

## Last Audit

**Date:** 2026-07-05
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] Fix implemented exactly as §3 describes: `YtDlpExtractionStrategy.kt` not-applicable branch matches `HTTP Error`/`DownloadError`/`ExtractorError` (case-insensitive) -> `OpenResult.NotFound("ytdlp_not_applicable")`.
- [x] Cascade contract intact: `LinkAutoDownloadCoordinator.kt:317` `NotFound -> continue`, `:340` `Error -> mapIoError` - line numbers match §2 point 4 verbatim.
- [x] `CANONICAL_ORDER` in `LinkExtractionRegistry.kt` still routes `ytdlp -> site -> direct -> html -> dynamic`.
- [x] Flavor isolation held: only file touched is `app_v2/src/noLegal/.../YtDlpExtractionStrategy.kt`; `src/main` untouched.
- [x] Debug-tag invariant: exactly one `Timber.d("S0935: ...")` hit (line 426), consistent with journal status `BlockNeedUserTest` before this audit.
- [x] Device evidence (`logs/fastmediasorter_20260705_002529.log`, 2026-07-05 12:36-12:40): 3/3 live Instagram reels - probe fired every time, cascade reached `html`->`dynamic` (zero `result=Other`), residual = `DownloadCorrupted` (upstream stub) - matches §4 step 2/3 acceptance criterion exactly.
