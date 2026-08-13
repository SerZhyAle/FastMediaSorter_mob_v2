# Стратегическая спецификация: S0187 — noLegal: восстановление YouTube extraction

**Ticket:** S0187
**Status:** BlockByOtherTask
**Implemented date:** 2026-05-14
**Priority:** 80
**Date:** 2026-05-14
**Tier:** TBD
**Roadmap entry:** Ad-hoc — device test 2026-05-14 после S0174 Broken
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical plan:** `PLAN/S0187_nolegal-youtube-extraction-recovery/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

На носителях noLegal flavor пользовательский share-flow YouTube / YouTube Music полностью сломан. Device-тест 2026-05-14 (логи `fastmediasorter_20260514_004916.log`) показал:

- Любой YouTube URL (`youtube.com/shorts/...`, `music.youtube.com/watch?v=...`) → `YtDlpExtractionStrategy` поднимает `com.chaquo.python.PyException: DownloadError: ERROR: [youtube] <id>: Requested format is not available. Use --list-formats for a list of available formats` → `S0170: result=Other`.
- `[S0166] unknown host, standard pipeline: host=youtube.com` — YouTube/Music не входит в `KnownSocialResources`, account picker не показывается, аккаунт выбирается автоматически.
- `S0117: NewPipe extractor initialized with app OkHttp downloader` появляется в startup-логах, но `NewPipeSiteExtractionStrategy.probe` после yt-dlp PyException не запускается — pipeline останавливается на `Other`.

Итог: пользователь видит "не получилось подготовить общий файл" на любую YouTube/YTMusic ссылку. S0174 (yt-dlp universal) числится Broken; S0175 (NewPipe v0.26.1 bump) Verified, но fallback в реальном пайплайне не происходит.

---

## 2. Цели

1. Любая YouTube или YouTube Music ссылка через share-flow в noLegal flavor → файл сохраняется (стрим или audio-stream для YTMusic).
2. Если yt-dlp возвращает `Requested format is not available` — pipeline передаёт управление NewPipeExtractor (S0175) для тех же сайтов.
3. youtube.com и music.youtube.com добавлены в `KnownSocialResources`/`KnownAuthResources` так, чтобы account picker показывался корректно и `[S0166] applying stored session` срабатывал.
4. `KnownSocialResources.host` distinguishing для music.youtube.com (eTLD+1 `youtube.com`, host `music.youtube.com`) учитывается так же, как для `vm.tiktok.com` → `www.tiktok.com`.

**Non-goals:**

- Изменение архитектуры extraction chain (S0186 покрывает cascade resilience отдельно).
- Поддержка YouTube playlists / channels — фокус single URL.
- Поддержка plain music.youtube.com browse / library — только share одного трека/видео.

---

## 3. Ограничения

- **Flavor:** `noLegal` только.
- **API level:** minSdk 26.
- **Wear OS:** не затрагивается.
- **APK size:** без новых зависимостей. Если потребуется bump yt-dlp pin — overhead в пределах wheel size delta.
- **Локализация:** новые строки (если появятся) — EN/RU/UK через `/doc-update` гейт.

---

## 4. Контекст текущей архитектуры

Цепочка для noLegal: `ytdlp` (S0174) → `site` (NewPipe, S0175) → `direct` → `html` → `dynamic`. Сейчас приоритет yt-dlp выше NewPipe; для YouTube это означает что NewPipe (специализированный extractor именно для YouTube) даже не пробуется, пока yt-dlp не вернёт `NotApplicable`. PyException в yt-dlp не транслируется в `NotApplicable` — обрывает каскад (см. S0186).

`youtube.com` отсутствует в списке known social, поэтому `LinkAutoDownloadCoordinator` не вызывает account picker и применяет автоматически выбранный аккаунт.

NewPipe v0.26.1 (S0175) поддерживает актуальный YouTube Innertube — теоретически способен распарсить YouTube/YTMusic URL без yt-dlp.

---

## 5. Предлагаемый подход

Три независимые корректировки, каждая может быть применена отдельно:

**A — Приоритет NewPipe выше yt-dlp для YouTube/YTMusic hosts**

Per-host re-ordering в `LinkExtractionRegistry`: если host совпадает с одним из NewPipe-поддерживаемых сервисов (`youtube.com`, `youtu.be`, `music.youtube.com`, `soundcloud.com`, `bandcamp.com`, и т.д.) — `site` (NewPipe) пробуется первой, `ytdlp` — fallback. Для всех остальных hosts остаётся `ytdlp` → `site`.

Альтернатива: универсальный `ytdlp` остаётся первым, но при PyException обязательно вызывается NewPipe — это покрывается S0186 (cascade resilience). При выборе альтернативы S0187 сводится только к §B и §C.

**B — youtube.com / music.youtube.com в `KnownSocialResources`**

Добавить записи: `youtube.com` (host pattern), `music.youtube.com` (eTLD+1 → `youtube.com` для account resolution). Account picker показывается; `[S0166] applying stored session` срабатывает; `[S0166] unknown host` не возникает. Сейчас в реестре есть `tiktok.com`, `instagram.com`, `threads.com` — добавить аналогично.

**C — yt-dlp format-selector hardening**

Текущий `ydl_opts.format` (вероятно дефолтный `bestvideo+bestaudio/best`) на YouTube возвращает `Requested format is not available` из-за PoToken/JS challenge. Опции для исследования в `/spec-tech`:
- bump yt-dlp pin до самой свежей stable (PO token handling улучшен в 2025.x);
- более терпимый format string: `best[ext=mp4]/best` или `bv*+ba/b`;
- передача `cookies` (уже передаются через `cookiefile`) + `user-agent` через `ydl_opts.http_headers` — некоторые YouTube ответы зависят от UA.

---

## 6. Открытые вопросы

1. **Per-host priority vs universal cascade fallback** — реализовать §A (re-ordering) или положиться только на S0186 (cascade resilience после PyException)? Решить в `/spec-tech`.
2. **yt-dlp version pin** — какая актуальная stable версия yt-dlp на момент реализации? Проверить changelog на YouTube PO token / JS player handling.
3. **YTMusic audio-only** — для music.youtube.com нужен ли audio-only fallback (если video-stream недоступен)?

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| YouTube ломает Innertube API → NewPipe тоже падает | Высокая (исторически) | Оба пути недоступны | yt-dlp bump + NewPipe fallback chain дают двойное покрытие |
| Per-host re-ordering ломает архитектурную чистоту registry | Средняя | Сложнее поддерживать | Изолировать в DI-конфиге noLegal, не в shared registry |
| Account picker меняет UX при добавлении youtube.com | Низкая | Лишний шаг для пользователей без YouTube account | Picker не показывается если ровно один аккаунт зарегистрирован |

---

## 8. Влияние на пользователя (docs/FEATURES_noLegal)

**EN:** noLegal: reliable YouTube and YouTube Music share downloads — NewPipe and yt-dlp work together, format mismatches no longer break the share.
**RU:** noLegal: надёжная загрузка YouTube и YouTube Music через share — NewPipe и yt-dlp работают вместе, несовпадение форматов больше не ломает обмен.
**UK:** noLegal: надійне завантаження YouTube і YouTube Music через share — NewPipe і yt-dlp працюють разом, невідповідність форматів більше не ламає обмін.

---

## 9. ADR

ADR-1 (предварительно): per-host extraction priority в noLegal sourceSet, без изменения main registry contract — окончательно решается в `/spec-tech`.

---

## 10. Связи с другими спеками

- **S0156** — родительский epic.
- **S0174** (Broken) — universal yt-dlp; S0187 закрывает YouTube-специфичный gap.
- **S0175** (Verified) — NewPipe bump; S0187 делает NewPipe фактически достижимым в pipeline для YouTube.
- **S0186** (BlockNeedUserTest) — cascade resilience при yt-dlp PyException; complementary к §A альтернативе.
- **S0166** — known social registry; S0187 §B расширяет его.
- **S0190** (BlockNeedUserTest, **blocking**) — YT Shorts / YTMusic extraction. Phase D реализует Option A (yt-dlp Python `download_to_file` для `*.googlevideo.com` CDN), который закрывает write-timeout в `LinkDownloadWriter.writeFromStream` — root cause недостигнутых критериев §11.1, §11.2 в S0187. Verified status S0190 разблокирует финальное подтверждение S0187.

---

## 11. Критерии готовности (strategic-level)

1. Share `https://youtube.com/shorts/<id>` → файл сохранён в Downloads.
2. Share `https://music.youtube.com/watch?v=<id>` → файл сохранён в Downloads.
3. В логе нет `S0170: result=Other` для YouTube URL.
4. `[S0166] applying stored session: host=youtube.com` появляется при наличии сохранённой сессии.

---

## 12. Следующий шаг

`/spec-update S0187` после выбора варианта §A vs S0186 dependency, затем `/spec-tech S0187`.

---

## Last Audit

**Run:** Device test 2026-05-14 (log `logs/fastmediasorter_20260514_153159.log`), Samsung SM-S731B / Android 16 / noLegal-DEBUG `2.60.5141.526`.
**Verdict:** Partial — extraction-layer цели §2.1..2.4 достигнуты, но user-facing критерии §11.1, §11.2 (файл сохранён в Downloads) **не достигнуты** из-за нового failure mode уровня HTTP write (см. S0190 Last Audit).

### Achieved (extraction layer)

- yt-dlp 2026.3.17 + Android client возвращает полный список форматов для `youtube.com/shorts/<id>` (до 3840p) и `music.youtube.com/watch?v=<id>` (до 1080p + audio). Оригинальная ошибка `Requested format is not available` исчезла. → §2.1, §11.3 (no `S0170: result=Other` from extraction failure) частично.
- `[S0166] known social: host=youtube.com` и `host=music.youtube.com` фиксируются; `applying stored session: host=www.youtube.com resolvedHost=youtube.com accountId=…` срабатывает. → §2.3, §11.4.
- `Timber.d("S0187: yt-dlp format-unavailable cascade to NewPipe url=…")` в `YtDlpExtractionStrategy.kt:343` в логе теста **не сработал** — это ожидаемо: после Phase A yt-dlp больше не падает на YouTube с этой ошибкой, cascade в NewPipe не нужен для современного yt-dlp pin.

### Not achieved (download layer, новый scope)

- `LinkDownloadWriter.writeFromStream` падает с `java.io.InterruptedIOException: timeout` (OkHttp `Exchange.ResponseBodySource.read`) ровно через ≈30 с после `pick progressive=…googlevideo.com/videoplayback?…` — оба случая (YT Shorts, YTMusic).
- `S0170: result=Other` всё ещё появляется, но теперь как симптом write-timeout, а не extraction failure.
- → §11.1, §11.2 не достигнуты. Подробности и опции стратегии — в `PLAN/S0190 → ## Last Audit → Открытые вопросы §1` (включая player-mimicking pacing предложенный owner-ом).

### Debug verification tags

Удалён при переходе из `BlockNeedUserTest` в `Partial`:

- `Timber.d("S0187: yt-dlp format-unavailable cascade to NewPipe url=…")` в `YtDlpExtractionStrategy.kt`.

### Открытые follow-up'ы

- Решение по HTTP-write стратегии — общее для S0187 и S0190; ведётся в S0190 Last Audit. Owner выбрал Option A (yt-dlp Python `download_to_file` для `*.googlevideo.com`); реализация — S0190 Phase D, в `BlockNeedUserTest` ожидает device test round 2.
- S0186 (cascade resilience) — теперь secondary, поскольку Phase A убрал главный сценарий PyException → cascade.
- S0174 (Broken) — переоценить status: yt-dlp universal в 2026.3.17 в принципе работает для YouTube, но общий feature остаётся Broken по причине того же write-timeout. Требует отдельного device-теста для non-YouTube хостов.

### Status decision (/spec-all 2026-05-14)

S0187 переведён `Partial → BlockByOtherTask`. Внутри S0187 forward progress невозможен: остаточный gap (write-timeout) полностью покрыт S0190 Phase D. После S0190 → Verified повторно запустить `/spec-check S0187` для re-evaluation §11.1, §11.2.
