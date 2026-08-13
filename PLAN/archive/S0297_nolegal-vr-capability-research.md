---
ticket: S0297
status: Verified
priority: 55
date: 2026-05-25
tier: 3
---

# Стратегическая спецификация: S0297 — Research: noLegal-расширения для VR (out of legal field)

**Ticket:** S0297
**Status:** Archived
**Priority:** 55
**Date:** 2026-05-25
**Tier:** 3 — Strategic, research-only
**Roadmap entry:** `S0240 §8` («дополнительные noLegal-specific VR-надстройки — отдельные тикеты после закрытия S0240») — этот тикет начинает разведывательную фазу для будущих noLegal-VR-extension тикетов, не дожидаясь полного закрытия S0240. Артефакт не пишет код, рождает дочерние implementation-тикеты только после владельческого выбора.

**Depends on:**
- `S0156` — noLegal capability surface audit (текущий статус `BlockByOtherTask`, фиксирует основную noLegal-методологию: store-blocker-классификация, gitignored docs/FEATURES_noLegal.md, отдельный flavor isolation).
- Косвенно: `S0240` направление и иерархия `standard` ⊂ `vr` ⊂ `noLegal`. Этот тикет не требует `Verified` для всего эпика, но опирается на установленную flavor-иерархию.

**Blocks:** конкретные implementation-тикеты, рождающиеся **после** owner-input по матрице capabilities ниже. Сам этот тикет блокирует только следующие noLegal-VR-extension Sxxxx, не блокирует ни один существующий active спека.

---

## 0. Approval Gate (owner input)

- **Requested mode:** разведка возможностей, которые **noLegal** flavor может добавить **поверх** VR-baseline (`src/vr/`), но которые **не могут** быть отгружены ни в Google Play XR (Android XR), ни в Meta Store (Quest), ни в любую market-сборку (`standard`, `lite`, `photos`, `legacy`).
- **Goal / expected outcome:** матрица кандидатов (capability × store-blocker-причина × польза для пользователя × сложность импла × риск × приоритет), на основании которой владелец выбирает 2..4 направления для реализации отдельными тикетами после закрытия `S0240`. Артефакт не пишет код, не аллоцирует impl-тикеты, не меняет ни одного `.kt` / `.cpp` файла.
- **Local anchor:** `app_v2/src/noLegal/java/`, `docs/FEATURES_noLegal.md` (текущая инвентаризация noLegal capabilities), `S0156` (epic-родитель noLegal-функциональности), `dev/FLAVOR_DEVELOPMENT_RULES.md` (правила изоляции noLegal-кода).
- **Scope boundaries / forbidden areas:** не входит реализация ни одной capability, не входит сборка прототипов, не входит юридическая консультация (research собирает факты про store-policy / лицензии — но не выступает legal advice), не входит выбор приоритетов (это owner-input после ознакомления).
- **Done / success signal:** один research-artifact (`PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md` + структурированная сводка в этом стратегическом файле). Владелец может прочитать матрицу и одним проходом выбрать 2..4 наиболее ценные direction-а для будущих impl-тикетов.
- **Autonomy rule:** конкретный набор источников (web-search vs знание стека vs обзоры конкурентов), глубина исследования по каждой capability — делегированы агенту в рамках этого этапа.
- **UI Clarification Status:** `N/A` — research-only, без user-visible UI.

### Approved scope decisions

- **Pure research, не implementation.** Матрица + рекомендации. Конкретные импл-тикеты рождаются дочерними после владельческого выбора, не из этого тикета напрямую.
- **Только out-of-legal-field.** Кандидаты, которые **технически** могут быть в `standard` (`vr` без noLegal), сюда не входят — они уйдут отдельным roadmap-ом эпика `S0240`. Здесь фиксируем то, что store-build-у недоступно по политике / лицензии / патенту.
- **Owner-controlled prioritization.** Матрица содержит **рекомендации** агента (best practice), но финальный выбор делает владелец. Owner-input после прочтения rolling-research-а.
- **Опираемся на иерархию `standard` ⊂ `vr` ⊂ `noLegal`.** Каждая capability априори может рассчитывать на: весь VR-baseline (OpenXR, immerse, HUD, controller input), весь noLegal-baseline (yt-dlp, Chaquopy, native site extractors, APK install, PaddleOCR), весь standard-baseline (плеер, browser, sync). Не нужно перепроверять «доступно ли это технически в noLegal-сборке» — иерархия гарантирует.

### Delegated assumptions

- Источники research-а: web (developer.android.com, developer.meta.com, developer.oculus.com policies, GitHub VR-projects, OSS catalogs), внутренние документы (`docs/FEATURES_noLegal.md`, `S0156`, `S0174`, `S0177`, `S0190`, `S0288`), конкурентный анализ (DeoVR, SkyboxVR, Pigasus, специализированные VR-content приложения).
- Каждая capability в матрице фиксирует один или несколько store-blocker-факторов из закрытого списка: `STORE_POLICY` (явный запрет market-стора), `LICENSE_INCOMPAT` (несовместимость лицензии для коммерческой market-сборки), `PATENT_ENCUMBERED` (патентные ограничения по кодекам / алгоритмам), `BINARY_SIZE` (несовместимость с market APK-size constraints), `META_REVIEW` (требует Meta entitlement-review, который владелец не хочет проходить), `API_RESTRICTED` (Android-permission, который Google Play отклоняет для file-manager-сценария).
- Каждая capability получает 2..3 альтернативы реализации (где применимо), не одну — это требование по research-формату S0244.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** depends on `S0156` (noLegal capability surface audit — policy parent for documentation routing to `docs/FEATURES_noLegal.md` and flavor isolation); unblocks future implementation tickets for noLegal-VR capabilities listed in §3 matrix (first will be `S0298` for B3 VR companion APK badge).
- **Proceed signal:** owner composite default approval 2026-05-25 — «всё recommended» в `PLAN/S0297_nolegal-vr-capability-research/DECISION_BRIEF.md`. Все 7 decision-блоков D-1..D-7 закрыты выбором recommended-варианта; см. Last Audit для конкретики.
- **Delegated scope:** research-artefact format and depth (web sources, competitive analysis, variant pros/cons) delegated to agent per §0 Approval Gate. Implementation tickets, allocated separately as a follow-up to this research, carry their own delegated-scope blocks.

---

## 1. Проблема

- `noLegal` сейчас имеет 5 публично известных capabilities (`docs/FEATURES_noLegal.md`): yt-dlp + Chaquopy, native site extractors, APK install, YouTube/YT Music recovery, offline PaddleOCR. Из них **ноль** относятся к VR-сурфейсу — все эти capabilities работают на плоском Android.
- После закрытия `S0240` `noLegal`-сборка автоматически получит весь VR-baseline через иерархию. Но **ничего сверх baseline**, что было бы доступно только для sideload, ещё не зафиксировано как направление работ. В §8 эпика S0240 явно сказано: «Дополнительные noLegal-specific VR-надстройки — отдельные тикеты после закрытия S0240».
- Без явного списка кандидатов с обоснованием store-blocker-а и пользовательской ценности — каждый раз, когда у владельца появляется идея «а вот это можно в VR», она будет либо забыта, либо приведена в случайном порядке. Research-артефакт фиксирует все известные направления один раз, выстраивает их по сравниваемой матрице и оставляет владельцу выбор.
- Конкуренты на Quest (DeoVR, Skybox, Pigasus) активно используют sideload-only возможности — yt-dlp-стиль extraction, custom codec packs, DRM-bypass, adult content libraries. Без сравнительного research-а нам неясно, где у нас real differentiating opportunity, а где избыточное копирование.

---

## 2. Цели

- Зафиксировать матрицу noLegal-VR кандидатов с полями: `name`, `store_blocker`, `user_value`, `complexity`, `risk`, `recommendation`.
- По каждому кандидату дать 2..3 альтернативы реализации (где применимо) с pros/cons.
- Идентифицировать 2..4 направления с самым высоким соотношением «польза / сложность», достойные владельческого выбора в первую волну impl-тикетов после `S0240`.
- Зафиксировать те направления, которые **отвергаются** (избыточно рискованные, политически чувствительные, дублирующие конкурентов без выигрыша) — чтобы они не возвращались как «а что если» в следующих циклах.
- Подготовить артефакт `RESEARCH.md` в подпапке `PLAN/S0297_nolegal-vr-capability-research/` для подробных факт-листингов по каждому кандидату — этот стратегический файл содержит только консолидированную сводку.

**Non-goals:**

- Реализация ни одной capability.
- Аллокация impl-тикетов в спек-каталог (это произойдёт после `/spec` отдельным запросом владельца).
- Юридическая консультация — research собирает публично известные факты про store-policy / лицензии, не выдаёт legal advice.
- Конкретный выбор «делаем это, не делаем это» — это owner-input после прочтения матрицы.
- Изменение `docs/FEATURES.md` / `docs/FEATURES_noLegal.md` — текущие capabilities остаются как есть; обновление произойдёт после реализации выбранных направлений.

---

## 3. Матрица кандидатов

Все кандидаты в матрице ниже **технически** могут быть реализованы только в `noLegal`-flavor (не в `standard` / `vr` / `lite` / `photos` / `legacy`). Каждый имеет минимум один из факторов `STORE_POLICY`, `LICENSE_INCOMPAT`, `PATENT_ENCUMBERED`, `BINARY_SIZE`, `META_REVIEW`, `API_RESTRICTED`. Колонки:

- `Капасити` — название.
- `Store-blocker` — главные причины невозможности отгрузки в market.
- `User-value` — что получает пользователь noLegal-сборки (`H`/`M`/`L` относительно типового VR-сценария).
- `Complexity` — оценка инженерной сложности (`H` = недели, `M` = дни, `L` = часы-дни).
- `Risk` — operational/maintenance risk (`H`/`M`/`L`).
- `Recommendation` — `A` (active first wave), `B` (second wave), `C` (defer / archive).

| Капасити | Store-blocker | User-value | Complexity | Risk | Recommendation |
|----------|---------------|:----------:|:----------:|:----:|:--------------:|
| VR media extraction через yt-dlp (YouTube-360, Reddit-VR, специализированные tube-сайты) | STORE_POLICY, LICENSE_INCOMPAT, BINARY_SIZE | H | M | M | **A** |
| Adult-VR catalog/library поверхность (DeoVR-style discovery) | STORE_POLICY | H | M | H | **B** |
| DRM-free patent-encumbered codec pack (HEVC/AV1/DTS/AC4 через libVLC/FFmpeg) | PATENT_ENCUMBERED, BINARY_SIZE | M | H | M | **B** |
| Passthrough camera capture / mid-VR-session screen recording | STORE_POLICY, API_RESTRICTED, META_REVIEW | M | H | M | **C** |
| Widevine L1 / Custom CDM secure-decryption for premium streams | STORE_POLICY, PATENT_ENCUMBERED | M | H | H | **C** |
| Прямой SMB/NAS-streaming больших VR-файлов (7K, 21+ GB) без скачивания | None (technically standard может) | H | M | L | **(скип, см. §6)** |
| Real-time субтитры через PaddleOCR на VR-видео (sideload PaddleOCR уже есть) | LICENSE_INCOMPAT (PaddleOCR-license), BINARY_SIZE | M | M | M | **A** |
| Voice transcription / Whisper-based для VR-роликов | LICENSE_INCOMPAT, BINARY_SIZE | M | H | M | **C** |
| Experimental OpenXR runtime loader / pre-release Meta SDK без store review | META_REVIEW | L | M | M | **C** |
| Direct APK install для sideloaded VR companion apps (нет VR-CTA в Browse) | API_RESTRICTED | L | L | L | **B** |
| Custom controller mapping / non-standard input profiles (BT-controllers, экзотические gamepad-ы) | None (technically standard может) | L | M | L | **(скип, см. §6)** |
| Locally hosted yt-dlp-mirror catalog (web UI поверх extraction-pipeline) | STORE_POLICY, LICENSE_INCOMPAT | M | H | M | **C** |

### 3.1. Кандидаты с рекомендацией A (active first wave)

#### A1. VR media extraction через yt-dlp

- **Что получает пользователь:** в плоском плеере / browse появляется path «открыть VR-видео по URL» (YouTube-360 / Reddit-VR-tube / специализированные публичные источники), которое после extraction в местный cache можно сразу запустить в immerse через S0292-бейдж.
- **Store-blocker:** yt-dlp + Chaquopy уже исключены из store-сборки (см. `S0174`); этот кандидат — расширение существующей capability на VR-content URLs.
- **Альтернативы реализации:**
  - **A1.1:** добавить VR-aware пресет в существующий `BaseUrlMediaExtractor` (yt-dlp с `extractor_args.youtube.formatids=<format_selector>` для 360°-форматов). Минимум кода, максимум переиспользования.
  - **A1.2:** написать отдельный `VrUrlExtractor` поверх существующего pipeline с собственными URL-паттернами и собственным choice-UI «извлечь как VR».
  - **A1.3:** добавить кастомный handler в `KnownAuthResources` для VR-specific сайтов.
- **Рекомендация (best practice):** A1.1. Минимально-инвазивный путь, максимум переиспользования существующего noLegal-pipeline-а.
- **Открытые риски:** многие VR-tube сайты блокируют yt-dlp по UA / IP / rate-limiting → нужно тестовое покрытие на 5..10 публично-доступных URL-ах перед `Verified`.

#### A2. Real-time субтитры через PaddleOCR на VR-видео

- **Что получает пользователь:** во время immerse-воспроизведения видео без subtitle-track, PaddleOCR раз в N кадров читает subtitle-области кадра и накладывает их на HUD-layer как текст. Доступно только в noLegal (PaddleOCR — sideload-only, см. `S0288`).
- **Store-blocker:** PaddleOCR / Paddle-Lite binaries уже исключены из store-сборок (license-incompatibility + binary size).
- **Альтернативы реализации:**
  - **A2.1:** периодический OCR-pass с интервалом 1..2 сек, fade-in/fade-out текста на HUD-layer. Минимальная нагрузка на GPU.
  - **A2.2:** continuous OCR (каждые ~10 кадров) с tracking subtitle-region между запусками — более точное, но дороже по CPU.
  - **A2.3:** OCR только по on-demand-запросу (controller-button «прочитать субтитры сейчас») — минимум нагрузки, но требует ручного действия пользователя.
- **Рекомендация (best practice):** A2.1 как baseline + опциональный A2.3 как настройка «экономия батареи».
- **Открытые риски:** субтитры в нижней части плоского кадра могут попадать в зону геометрической искажения CINEMA-quad-а — нужно on-device-тестирование readability.

### 3.2. Кандидаты с рекомендацией B (second wave)

#### B1. Adult-VR catalog/library поверхность (DeoVR-style)

- **Что получает пользователь:** отдельный VR-browser-mode для каталогизированного adult-VR-content-а с фильтрацией по жанрам / актёрам / форматам. Конкуренты: DeoVR.
- **Store-blocker:** Meta Store / Google Play не примут content-discovery для adult.
- **Альтернативы реализации:**
  - **B1.1:** интеграция с публичными adult-VR-API (SLR-API / PassionVR / BadoinkVR) — самая полная capability, но требует регулярного обслуживания API-breakage-ов.
  - **B1.2:** browser-mode без API, только URL-list-based — owner ведёт свой curated-список, приложение его рендерит и стримит.
  - **B1.3:** локальный media-library mode с metadata-инжекцией (теги, постеры) для уже скачанных файлов — без сети, без API.
- **Рекомендация (best practice):** B1.3 как baseline (минимум maintenance), B1.1 — опциональное расширение если владелец готов к maintenance-нагрузке.
- **Открытые риски:** maintenance — API-breakage на стороне adult-VR-сайтов происходит регулярно; реалистичная нагрузка — раз в 2..3 месяца требуется правка extractor-а.

#### B2. DRM-free patent-encumbered codec pack

- **Что получает пользователь:** воспроизведение HEVC / AV1 / DTS / AC4 / уникальных профилей H.264 на устройствах, где встроенный MediaCodec их не поддерживает. Через libVLC или FFmpeg-binding.
- **Store-blocker:** market-сборка не может включить эти кодеки без per-device patent-licensing (overhead на каждый APK слайс).
- **Альтернативы реализации:**
  - **B2.1:** libVLC AAR (~30 MB на slice) с full codec coverage. Production-tested на других sideload-приложениях.
  - **B2.2:** FFmpeg-Android prebuilt с custom selective-codec-config — меньше размер, больше работы.
  - **B2.3:** software-decode только для специфических codec-profile-ов через mini-FFmpeg slice — minimal binary, минимум функциональности.
- **Рекомендация (best practice):** B2.1. Production-tested, минимум поддержки, максимум codec coverage. Размер APK для noLegal-slice — это уже не критерий после yt-dlp / PaddleOCR.
- **Открытые риски:** ExoPlayer + libVLC integration требует extension `ExoPlayer.MediaSource`, что добавляет ~1 неделю на adaptation layer. Без тщательного device-test возможен audio-sync drift на специфических контейнерах.

#### B3. Direct APK install для sideloaded VR companion apps

- **Что получает пользователь:** в Browse / Tile-grid `.apk` файлы получают VR-aware install-CTA (как в S0183, но с явным VR-companion-классификатором — например, OpenXR runtimes, VR sideload tools). Прямое расширение существующей S0183 capability.
- **Store-blocker:** `REQUEST_INSTALL_PACKAGES` Permission уже исключён из market-сборок (см. `docs/FEATURES_noLegal.md` §3).
- **Альтернативы реализации:**
  - **B3.1:** просто унаследовать S0183 без VR-specific детектов — minimum work, no extra UX.
  - **B3.2:** добавить VR-companion-классификатор по `<application>` манифест-флагам в APK (например, `com.oculus.intent.category.VR`) и показать VR-icon на tile в Browse.
  - **B3.3:** добавить отдельный VR-companion-section в Browse с встроенным каталогом известных sideload-tools.
- **Рекомендация (best practice):** B3.2 как baseline (минимальная UX-надстройка над S0183), B3.3 — опциональное расширение.
- **Открытые риски:** парсинг манифеста APK без полного PackageParser API — нужен внутренний парсер; альтернативно — лёгкий внешний util.

### 3.3. Кандидаты с рекомендацией C (defer / archive)

- **Passthrough camera capture / mid-VR session recording** — `META_REVIEW`-блокирующий, технически сложный, owner-confidence в нагрузке поддержки низкий. Defer до прямого пользовательского запроса.
- **Widevine L1 / Custom CDM secure-decryption** — высокий complexity + риск; обходит политики Meta/Google по DRM. Defer.
- **Voice transcription / Whisper для VR-роликов** — Whisper binary ~150 MB; high-complexity audio-pipeline; user-value M, но сложно конкурировать с native Quest-API speech recognition. Defer.
- **Experimental OpenXR loader** — низкий пользовательский value, высокий operational risk. Defer.
- **Custom controller mappings** — может быть реализовано в `standard` через `vr` source set без noLegal-attachment-а; не требует noLegal-канала. Defer на роадмап S0240.
- **Locally hosted yt-dlp-mirror catalog с web UI** — высокий complexity, дублирует функциональность desktop-приложений (PinchFlat, Tube-mirror). Defer.

---

## 4. Что отвергается (не пишется как noLegal-VR)

- **SMB/NAS-streaming больших VR-файлов** — технически может (и должно) быть в `standard`/`vr` через существующий cloud-stack. Это не noLegal-specific, выноситься в noLegal неоправданно.
- **Custom controller mappings** — то же; принадлежит общему VR-roadmap-у (`S0240`).
- **Любое сетевое тelemetry-собирательство noLegal-only** — `S0240` Q4 owner-input зафиксировал «no data collected» на всех сборках, включая noLegal.

---

## 5. Подход к research-артефакту

`PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md` (создаётся отдельно, **не** в рамках этого .md) будет содержать:

- По каждому A/B-кандидату: один блок «Вопрос → Источник → Варианты → Best practice → Открытые риски» (по формату S0244).
- Web-research: 3..5 публичных источников per-кандидат (developer.meta.com, developer.android.com, GitHub OSS projects, конкурентные приложения, opensource libraries).
- Прецеденты в существующем коде: на какие noLegal-utilities (yt-dlp pipeline, PaddleOCR, APK installer, Chaquopy) каждая capability опирается.
- Маркер blocking-зависимости (если capability требует завершения какого-то VR-baseline тикета §10.3 — например, A1 yt-dlp-VR-extraction зависит от `S0296` потому что без рабочего VIDEO-immerse extracted-файл не имеет user-flow для просмотра).

C-кандидаты в `RESEARCH.md` фиксируются короткой строкой «defer; rationale: …» без полного блока. Если в будущем владелец захочет вернуться — он попросит подробную разведку отдельно.

---

## 6. Что НЕ входит в этот research

- **Капабилити, доступные в `standard` или `vr`.** Если store-build технически может это шипнуть — это `S0240` roadmap, не noLegal-разведка.
- **Реализация любой capability.** Только research, никакого кода / impl-тикетов из этого spec-а напрямую.
- **Legal advice.** Research собирает публичные факты про store-policy и opensource license — но не выдаёт юридическую консультацию.
- **Конкурентные приложения как референс «делаем как у них».** Конкурентный анализ DeoVR / Skybox / Pigasus делается для понимания landscape, не для копирования.

---

## 7. Открытые вопросы

Все вопросы ниже закрыты owner-decision 2026-05-25 в `DECISION_BRIEF.md` и Decision Record этого файла. Для будущих impl-тикетов они остаются как входные решения, не как блокеры research-а.

- **Приоритет first-wave A-кандидатов.** Между A1 (yt-dlp-VR-extraction) и A2 (PaddleOCR-субтитры) — какая capability имеет больший пользовательский value для владельца лично? Это влияет на порядок impl-тикетов после `S0240`.
- **Объём adult-VR-catalog опции (B1).** Owner определяет, нужен ли вообще такой раздел в noLegal-сборке. Без owner-input этот кандидат уезжает в C.
- **Стек для DRM-free codec pack (B2).** Если B2 проходит — libVLC vs FFmpeg-Android — выбор делает владелец после прочтения pros/cons в `RESEARCH.md`.
- **Документация noLegal-VR capabilities.** Все реализованные направления уходят в `docs/FEATURES_noLegal.md` (gitignored, см. `S0156`). Никаких публичных feature-докуменций по noLegal-VR не пишется.
- **Совмещение с `S0156` epic.** `S0156` сейчас `BlockByOtherTask`. Готов ли владелец, чтобы будущие impl-тикеты ссылались на `S0297` как research-источник, минуя пробуждение `S0156`?
- **Тестирование на физическом Quest 3.** Все A/B impl-тикеты после этого research-а потребуют on-device verification на Quest 3 (доступно владельцу, см. agent-memory `user_hardware`). Это не блокер research-фазы, но фиксируется как ритм всех будущих implementation-тикетов.

---

## 8. Архитектурные решения (ADR)

**ADR-1: noLegal-VR — это надстройка поверх VR-baseline, а не параллельный VR-stack**

- **Решение:** каждая capability в матрице опирается на VR-baseline из `src/vr/` (OpenXR session, HUD, controller input, immerse Activity) и расширяет его noLegal-specific логикой в `src/noLegal/java/`. Никакого дублирования OpenXR-кода в `src/noLegal/`.
- **Альтернативы:** (а) отдельный VR-pipeline в noLegal — массивный дубль кода, нарушение DRY; (б) перенос noLegal-VR-кода в `src/vr/` — нарушение flavor isolation (`dev/FLAVOR_DEVELOPMENT_RULES.md`).
- **Почему:** иерархия `standard` ⊂ `vr` ⊂ `noLegal` уже механически даёт VR-baseline в noLegal-сборке. noLegal только аддитивно расширяет.

**ADR-2: Research-only тикет; impl-тикеты рождаются отдельно**

- **Решение:** S0297 не аллоцирует impl-тикеты, не пишет код, не правит `docs/FEATURES*.md`. После owner-input по матрице запускается отдельный `/spec` per-направлению.
- **Альтернативы:** (а) один большой epic-spec на все направления — теряется per-capability scope; (б) research + первая capability в одном тикете — смешивает research-результат и implementation-blocker.
- **Почему:** разделение research / implementation позволяет владельцу пройти матрицу один раз, выбрать направления один раз, и дальше делать impl-тикеты по одной за раз без re-litigation выбора.

**ADR-3: Owner-driven prioritization, не agent-driven**

- **Решение:** агент даёт recommendations (`A`/`B`/`C`), но финальный выбор делает владелец. Никаких автоматических escalations / автоматического создания impl-тикетов из C → A.
- **Альтернативы:** агент выбирает first-wave 2..3 направления на основе heuristics — рискует не совпасть с реальными приоритетами владельца.
- **Почему:** noLegal — личный sideload-канал владельца (см. `docs/FEATURES_noLegal.md`); приоритеты — owner-personal, не universal.

---

## 9. Связи с другими спеками

- **S0156** (epic `nolegal-capability-surface-audit`, `BlockByOtherTask`) — родительский noLegal epic, фиксирует политику документации (gitignored `_noLegal.md`) и flavor isolation. Этот тикет — VR-расширение S0156-направления.
- **S0240** §8 — фиксирует, что noLegal-VR-надстройки — отдельные тикеты после закрытия S0240. S0297 запускает research-фазу для них, не дожидаясь полного закрытия `S0240`.
- **S0174** (yt-dlp + Chaquopy) — основа для кандидата A1 (VR media extraction).
- **S0288** (PaddleOCR) — основа для кандидата A2 (subtitle OCR).
- **S0183** (APK install) — основа для кандидата B3 (VR companion apps install).
- **S0296** (VR immerse VIDEO playback) — формальный блокер для кандидата A1: yt-dlp-extracted VR-видео имеет смысл только после рабочего VIDEO-immerse pipeline. Этот тикет research опирается на работающий S0296 как баseline для импл-тикетов.

---

## 10. Критерии готовности (strategic-level)

1. Артефакт `PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md` создан, содержит подробный блок «Вопрос → Источник → Варианты → Best practice → Открытые риски» по каждому A/B-кандидату.
2. Этот стратегический файл содержит консолидированную матрицу с явными recommendation-меттами.
3. Владелец прочитал матрицу и зафиксировал выбор 2..4 направлений из A+B для будущих impl-тикетов (запись в Last Audit этого файла + переход статуса `Draft → Approved`).
4. C-кандидаты явно зафиксированы как «defer» с короткой rationale-строкой; владелец может в любой момент перевести любой C → A через `/spec-update`.
5. После прочтения матрицы — следующие impl-тикеты per-направлению рождаются отдельными `/spec` запусками, со ссылкой на этот тикет в их `Roadmap entry`.

---

## 11. Что делать с этим документом

1. Создать `PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md` подпапку и подробно расписать A1/A2/B1/B2/B3 по формату «Вопрос → Источник → Варианты → Best practice → Открытые риски». Это шаг 1 после Approved.
2. Прочитать матрицу + RESEARCH.md → владелец фиксирует first-wave выбор (2..4 направления) в Last Audit этого файла.
3. После выбора — статус переходит `Draft → Approved`, и для каждого выбранного направления запускается `/spec` отдельным impl-тикетом.
4. C-кандидаты остаются в матрице как «defer»; revisit раз в полгода (или по запросу владельца).
5. После закрытия research-ритуала этот тикет переводится в `Verified`. Реализация first-wave направлений и обновление `docs/FEATURES_noLegal.md` принадлежат дочерним impl-тикетам; S0297 остаётся rationale/source-of-truth, не implementation gate.

---

## Decision Record

2026-05-25 (owner-decision) — owner выбрал composite default «всё recommended» по `DECISION_BRIEF.md`. Зафиксированные решения:
- **D-1:** first-wave порядок `B3 → A2 → A1 → B1.3 → B2.1`. B3 стартует немедленно, остальные ждут Verified S0296 / Verified S0291 либо завершения предшественника.
- **D-2:** A1 реализуется как A1.2 (extractor preset + `SpatialMetadataInjector`); A1.1 как stand-alone отвергнут.
- **D-3:** B1 ограничивается B1.3 (локальные `.hsp` sidecar для already-downloaded files); B1.1 / B1.2 не рассматриваются в first/second wave.
- **D-4:** B2 реализуется как B2.1 — libVLC AAR как parallel Player в noLegal-flavor.
- **D-5:** C-список остаётся как есть в `RESEARCH.md` (revisit policy: через 6 месяцев либо по dedicated user-pull).
- **D-6:** Все impl-тикеты ссылаются на S0156 (policy) + S0297 (rationale); S0156 не разблокируется.
- **D-7:** `SpatialMetadataInjector` выносится в shared `noLegal/util`-модуль на этапе A1.2 (первый использующий); B1.3 потом инжектится через тот же helper.

Сразу после этой галки: S0297 переходит `Draft → Approved`. Первый impl-тикет `S0298` (B3 — VR companion APK badge / classifier) аллоцируется немедленно как непривязанный к S0296/S0291. Остальные impl-тикеты (A2, A1, B1.3, B2.1) аллоцируются по одному непосредственно перед стартом соответствующей итерации (правило `S0240 §10.3` — «не пачкой заранее»).

2026-05-25 — research artefact `PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md` создан. Содержит развёрнутые блоки «Вопрос → Источник → Варианты → Best practice → Открытые риски» по A1 / A2 / B1 / B2 / B3 + one-line defer-rationales для C-кандидатов + cross-cutting concerns + рекомендуемый порядок first-wave. Web-research включил yt-dlp issue tracker (баг VR180 anonymous extraction #14413 открыт), Google spatial-media v2 RFC, PaddleOCR PP-OCRv5 docs + Quest 3 thermal study (arXiv 2509.18929), Meta Quest manifest docs + Google issue tracker #36908355, libVLC + Media3 supported-formats + FFmpegKit retirement notice, HereSphere `.hsp` format + DeoVR Web Stream protocol. Главные surprise-факты: (а) YouTube VR180 anonymous extraction сейчас сломан upstream — A1 требует A1.2 (`SpatialMetadataInjector`) как load-bearing piece, не A1.1 в одиночку; (б) ни DeoVR, ни Skybox, ни Pigasus, ни HereSphere не делают real-time subtitle OCR — A2 это genuine differentiation; (в) Media3 FFmpeg extension audio-only, FFmpegKit archived June 2025 → libVLC AAR единственный production-viable вариант для B2. Owner-input закрыт блоком 2026-05-25 выше.

## Revision History

- **2026-05-25** - created by android-rd-specialist (focus: noLegal-VR capability landscape, out-of-legal-field options inventory)
  - Зафиксирована матрица 12 кандидатов с recommendation-метками A/B/C. Pure research-spec, без impl-тикетов и без кода. Опирается на иерархию `standard` ⊂ `vr` ⊂ `noLegal` зафиксированную в S0240 §1.1, без дублирования VR-baseline кода в `src/noLegal/`. First-wave рекомендация: A1 (yt-dlp VR-extraction) + A2 (PaddleOCR-субтитры). B-кандидаты — second wave. C-кандидаты — defer. SMB/NAS-streaming и custom controller mappings исключены из noLegal-фокуса (принадлежат `standard`/`vr` roadmap-у).
- **2026-05-25** - research artefact populated by android-rd-specialist (focus: web-research + competitive analysis + variant pros/cons)
  - Три параллельных research-агента покрыли A1+B3, A2, B1+B2. Подробный артефакт в `PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md`. Order recommendation уточнён: B3 first (lowest effort, durable), A2 second (largest differentiation), A1 third (gated на S0296 + обязательная A1.2 variant). Major risks: yt-dlp YouTube VR180 broken upstream, libVLC AAR 16 KB page-alignment review нужен.
- **2026-05-27** - refined by GitHub Copilot via `/spec-update`
  - Removed the forbidden three-dot placeholder from the strategic body and recorded one structural `DISCUSS` item about implementation-level anchors.
- **2026-05-30** - verified by GitHub Copilot via `/spec-all`
  - Clarified that S0297 closes as research-only source-of-truth; child implementation specs own feature delivery and noLegal feature-doc updates.

## Non-blocking Follow-up

- **2026-05-27 / accepted non-blocking:** Strategic body contains implementation-level anchors (`app_v2/src/noLegal/java/`, `docs/FEATURES_noLegal.md`, `BaseUrlMediaExtractor`, `KnownAuthResources`, `SpatialMetadataInjector`). This is retained as research context; concrete class/path decisions move to future implementation specs when each child ticket starts.

## Last Audit

**Date:** 2026-05-30
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 2

### Manual / on-device

- None — research-only ticket; implementation and device verification are delegated to child specs.
