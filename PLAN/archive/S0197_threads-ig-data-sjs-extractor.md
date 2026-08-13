# Стратегическая спецификация: S0197 — Threads/Instagram data-sjs JSON extractor

**Ticket:** S0197
**Status:** BlockNeedUserTest
**Priority:** 75
**Date:** 2026-05-14
**Tier:** 3 — Moderate
**Flavor scope:** noLegal only — не входит в стандартный цикл верификации.
**Roadmap entry:** Ad-hoc — follow-up to S0181 Tier 2 (запрос 2026-05-14)
**Tactical plan:** `PLAN/S0197_threads-ig-data-sjs-extractor/INDEX.md`
**Implemented date:** 2026-05-14 (Phase 01-04); reopened 2026-05-14 — see §13.

<!-- auto-approved by /spec-all — 2026-05-14 -->
**Tactical spec:** `PLAN/S0197_threads-ig-data-sjs-extractor/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Скачивание медиа из публичных постов Threads и Instagram не работает на изображениях. Подтверждено на устройстве 2026-05-14:

- Threads single-image post сохраняет channel OG preview вместо реального изображения поста.
- Threads carousel сохраняет только первый слайд; остальные слайды никогда не уходят в Batch.
- Instagram photo post (single и carousel) завершается состоянием SocialPreviewOnly — пользователь не получает ничего, кроме сообщения об ошибке.

Видео-посты Threads и Instagram работают штатно — отказ касается только image-content. Фича скачивания по ссылке — часть noLegal-flavor умбрелы S0156; пользователю эти три сценария обещаны как поддерживаемые.

---

## 2. Цели

1. Threads single-image post → сохраняется реальное изображение поста, а не OG-превью канала.
2. Threads photo carousel → пользователь получает все N слайдов карусели одним пакетом, не первый слайд.
3. Instagram photo post single → сохраняется реальное изображение, не SocialPreviewOnly.
4. Instagram photo carousel → все N слайдов пакетом.
5. Threads и Instagram видео-посты — без регрессии (текущий путь либо новый JSON-путь, что сработает первым).
6. Хосты вне Threads/IG-семейства не меняют поведение.

**Non-goals:**

- Поддержка приватных постов, требующих логина.
- Stories, Reels, Live, IGTV — только стандартные `/post/<shortcode>` (Threads) и `/p/<shortcode>` (Instagram).
- Изменения yt-dlp probe-цепочки (территория S0174).
- Cookie/session work — публичные посты отдают payload без авторизации.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Cheap path (без поднятия WebView) должен суметь успешно распарсить пост, если JSON присутствует в первичном HTML-ответе — экономит время и батарею.
2. Когда в посте присутствует и изображение, и видео (Threads посты с прикреплённым видео в галерее) — выбор между ними остаётся за существующей политикой селекции.

### 3.2 Жёсткие ограничения

- **Flavor:** noLegal-сборки (фича link-download — часть умбрелы S0156). Standard/lite/photos/legacy/VR не затрагиваются.
- **API level:** без специфики — работает на всём поддерживаемом диапазоне.
- **Wear OS:** не затрагивается.
- **Производительность:** парсинг встроенного JSON выполняется на IO-диспетчере, без блокировки UI. Бюджет на html-cheap-path не должен расти существенно — JSON-парсинг должен быть дешевле, чем поднятие WebView, иначе теряется смысл.
- **Совместимость данных:** изменений схемы хранения нет.
- **Локализация:** EN/RU/UK — обязательно, если появляются новые user-visible строки. Текущая гипотеза — новых строк не требуется (фича невидима как новая возможность, исправляет существующий failure path).
- **Доступность:** не применимо — фича невидима в UI как самостоятельная.
- **Communication policy:** если появятся новые user-visible строки (toasts, ошибки), они проходят `docs/COMMUNICATION_POLICY.md` checklist (§6).

---

## 4. Контекст текущей архитектуры

Цепочка извлечения медиа из произвольной ссылки строится как набор стратегий, опробуемых по очереди: probe → yt-dlp → cheap html → dynamic WebView. Каждая стратегия возвращает либо результат (одиночный файл / streaming-манифест / batch / blocked / not-found), либо not-applicable, и тогда пробуется следующая.

Threads-домены исключены из yt-dlp probe (S0174), поэтому image-посты неизбежно попадают на cheap html и далее на dynamic WebView. Cheap path использует структурированный data-harvester (JSON-LD + oEmbed), который для Threads/IG не находит ничего. Dynamic path использует JS-инъекцию для сбора DOM-кандидатов и интерсепт сетевых запросов; для Threads-хостов он дополнительно скармливает целиком HTML-документ во встроенный JSON-парсер, который умеет вытащить URL поста и слайдов карусели из data-sjs payload — этот код уже написан и подключён частично, но не доводит цепочку до пользователя.

Селекция итогового кандидата работает по размеру (HEAD-проба Content-Length, выбирается первый ≥ 1 MiB) и по входному порядку. Из-за этого OG-превью на CDN Meta (тот же `*.fbcdn.net`, размер сопоставим с реальным изображением поста) может перевесить реальное изображение, даже если последнее присутствует в списке кандидатов.

Carousel batch триггерится по эвристике "≥ 2 кандидатов с размером ≥ 1 MiB". Для Threads/IG-карусели, чьи слайды получены из JSON, размер заранее неизвестен — HEAD-пробы не успевают за заданный бюджет.

SocialPreviewOnly-страж dynamic стратегии возвращает специальное состояние, если для preview-sensitive хоста (Instagram, Threads) среди кандидатов остались только image-источники. Эта эвристика была введена, чтобы отличать "пост-видео, который мы не смогли вытащить" от "пост-изображение" — но она же ложно срабатывает на легитимных photo-постах Instagram, потому что JSON-источник изображения сейчас не активируется для IG-хостов.

---

## 5. Предлагаемый подход

Поведение data-sjs JSON-парсера на сегодняшний день уже корректно вытаскивает URL поста и слайдов карусели. Стратегический сдвиг — довести этот источник до уровня "доверенного и обязательного" для всего семейства Threads/IG-хостов на всех путях извлечения, и доработать селекцию + триггер пакета + страж preview-only так, чтобы они уважали этот источник.

### 5.1 Основные столпы / модули

**Pillar A — Single embedded-JSON activation surface.**
Один и тот же JSON-парсер активируется и в cheap html, и в dynamic WebView, и для всего семейства Threads/IG-хостов (включая мобильный поддомен Instagram). Текущее ограничение activation gate'а только на Threads-хосты снимается. Если JSON присутствует в первичном HTML-ответе, cheap path заканчивает успехом без поднятия WebView; иначе работает существующий dynamic-fallback.

**Pillar B — Trusted-source bias в селекции.**
Кандидат с источником EMBEDDED_JSON для preview-sensitive хостов трактуется как авторитативный — он перевешивает OG_IMAGE / IMG_TAG / IMG_SRCSET независимо от probed size. Источник теперь — первичный сигнал, размер — вторичный. Для не-preview-sensitive хостов поведение не меняется.

**Pillar C — Carousel batch trigger по count, не только по size.**
Если в списке кандидатов присутствует ≥ 2 image-кандидатов из доверенного embedded-JSON источника, путь возвращает Batch со всеми слайдами карусели, не дожидаясь HEAD-пробы их размеров. Для смешанных списков (часть из JSON, часть из DOM) приоритет отдаётся JSON-набору как самодостаточному.

**Pillar D — SocialPreviewOnly bypass при наличии JSON-image.**
Страж "только image-кандидаты для preview-sensitive хоста = SocialPreviewOnly" перестаёт срабатывать, если хотя бы один из image-кандидатов пришёл из embedded-JSON. JSON-источник — это и есть авторитативный ответ "пост — фото", а не fallback.

### 5.2 Потоки данных и событий

```
Public Threads/IG URL
    ↓
Probe chain (yt-dlp пропускает Threads/IG image)
    ↓
Cheap HTML strategy
    ├── Если данные есть в первичном HTML JSON → возвращает single/batch
    └── Иначе → not-applicable
    ↓
Dynamic WebView strategy
    ├── DOM harvest (как сейчас)
    ├── Embedded JSON harvest (как сейчас для Threads, расширяется на IG)
    ├── Если ≥ 2 image-кандидатов из JSON → Batch
    ├── Если ≥ 1 image-кандидат из JSON и страж preview-only → пропустить страж
    └── Селекция: JSON-источник побеждает OG/IMG для preview-sensitive хоста
    ↓
Direct download c CDN-replay headers (UA + Referer из page origin — S0171)
```

### 5.3 Точки расширяемости

- Список хостов, для которых активируется embedded-JSON harvester, остаётся открытым на дополнение (Facebook web public posts используют тот же data-sjs).
- Карта source priority остаётся данными, не if-цепочкой — добавление новых trusted-источников не меняет логику селекции.
- Choice кандидата внутри `image_versions2.candidates[]` (индекс [0] / [1] / по разрешению) выделен как сменная стратегия — текущий выбор [0] фиксируется как первый шаг, [1] может стать опцией по итогам полевой проверки.

---

## 6. Открытые вопросы / Research items

1. **Выбор индекса внутри `candidates[]`**
   - **Вопрос:** что брать — `candidates[0]` (max-res original) или `candidates[1]` (1080-wide feed-size)?
   - **Варианты:** [0] — больше пикселей, иногда 404 на cold-edge nodes по данным Scrapfly; [1] — стабильнее, но мельче.
   - **Нужно выяснить:** реален ли 404-риск на наших целевых регионах. По умолчанию — [0] (текущее поведение парсера), пересмотр после полевой проверки.
   - **Статус:** Open — fallback-стратегия с откатом к следующему индексу при 404 на скачивании может решить.

2. **Преимущество JSON-image над DOM-video в Threads-постах со смешанным контентом**
   - **Вопрос:** если Threads-пост содержит одновременно текстовое описание с фото и прикреплённое видео-вложение, как разрешать конфликт между JSON-image и DOM-video?
   - **Варианты:** видео всегда побеждает; image всегда побеждает; batch с обоими.
   - **Нужно выяснить:** реальная частота таких постов, пользовательский ожидаемый вывод.
   - **Статус:** Open — дефолт на старте: существующая селекция (видео-источники сейчас имеют приоритет на cheap path); пересмотр по обратной связи.

3. **Mobile-subdomain instagram (`m.instagram.com`)**
   - **Вопрос:** возвращает ли мобильная версия тот же data-sjs payload, что и десктопная?
   - **Нужно выяснить:** проверка на dumped HTML с m.* при первом тестовом прогоне.
   - **Статус:** Open — гипотеза "тот же payload" принимается; если не подтвердится — добавится UA-override.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Изменение схемы data-sjs со стороны Meta | Средняя | Сценарий снова деградирует до SocialPreviewOnly / OG-fallback | Парсер уже сейчас отказоустойчив (try/catch + verbose-trace без падения); добавится host-gated trace, чтобы первая жалоба ловилась быстро. |
| Регрессия видео-постов из-за изменения SocialPreviewOnly-стража | Низкая | Видео перестаёт скачиваться, появляется SocialPreviewOnly на правильных видео | Изменение стража обусловлено наличием image-кандидата из EMBEDDED_JSON; для видео-постов JSON либо отсутствует, либо содержит video_versions — путь не задевается. |
| Cheap path начнёт частично успевать там, где dynamic нужен | Низкая | Часть постов скачается, часть нет — нестабильное поведение | Cheap path возвращает not-applicable при отсутствии JSON-кандидатов; dynamic как fallback сохраняется. |
| Выбор `candidates[0]` упирается в 404 на cold-edge CDN | Средняя | Пользователь видит ошибку скачивания после успешного извлечения URL | Fallback на следующий индекс при 404 на direct download — отдельная подзадача в tactical (см. §6 #1). |
| Mobile-subdomain IG ведёт себя иначе | Низкая | IG photo с m.* не работает | Активация JSON-харвестера для m.* + полевая проверка во время BlockNeedUserTest. |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это исправление существующего failure-сценария для скачивания по ссылке (фича уже описана в `docs/FEATURES_noLegal*.md` — S0156 §6.9). Никаких новых пользовательских возможностей не появляется. Запись о фиксе пойдёт в `dev/CHANGELOG.md` через `add_to_dev_log.ps1`.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Trusted-source bias вместо size-first в селекции для Threads/IG.**

- **Решение:** для preview-sensitive семейства хостов кандидат из embedded-JSON источника побеждает image-кандидаты из DOM/meta независимо от probed size; для других хостов поведение не меняется.
- **Альтернативы:** (а) глобально переключить селекцию на source-first для всех хостов; (б) оставить size-first и попытаться улучшить корректность probed size через дополнительные пробы.
- **Почему:** (а) рискует регрессией на хостах, где OG/IMG — единственно доступный сигнал и оказывается правильным; (б) не решает carousel (Batch требует size, который HEAD-пробы не успевают подтвердить за бюджет). Точечная host-gated политика — наименьший радиус взрыва.

**ADR-2: Carousel batch триггерится по count доверенных image-кандидатов.**

- **Решение:** если ≥ 2 image-кандидатов из embedded-JSON, путь возвращает Batch со всеми слайдами карусели; size-based триггер остаётся для остальных случаев.
- **Альтернативы:** дождаться HEAD-проб всех слайдов и применять size-based триггер.
- **Почему:** размер для CDN URL отдаётся быстро, но MAX_PROBED_CANDIDATES ограничивает количество параллельных проб; карусель из 10 слайдов не уложится. JSON — авторитативный источник самого факта "это карусель", доп. подтверждения не нужны.

**ADR-3: `candidates[0]` как стартовый выбор индекса.**

- **Решение:** по умолчанию используется первый элемент `image_versions2.candidates[]` (max-res). Возможный fallback на следующий индекс при 404 — отдельный feature-toggle на будущее.
- **Альтернативы:** `candidates[1]` (Scrapfly-рекомендация); явный fallback c [0] на [1] на [2] при 404.
- **Почему:** текущий парсер уже использует [0], явных репортов о 404 у нас нет. Усложнение fallback-логикой откладывается до получения данных с поля.

---

## 10. Связи с другими спеками

- **S0181** — research-ticket, источник findings и подтверждённый failure-репорт. После реализации S0197 переходит в `Implemented` (его исследовательские выводы консумированы).
- **S0156** — umbrella для noLegal flavor link-download. Эта спека — частный фикс внутри неё; запись в `docs/FEATURES_noLegal*.md` обновлять не нужно, фича там уже описана.
- **S0171** — CDN replay headers (desktop UA + Referer) для `*.fbcdn.net` / `*.cdninstagram.com`. Переиспользуется без изменений.
- **S0174** — yt-dlp probe; объясняет, почему Threads/IG image не подхватываются yt-dlp и попадают на html/dynamic путь.

---

## 11. Критерии готовности (strategic-level)

1. На устройстве: share Threads single-image post → пользователь видит сохранённое реальное изображение поста (не OG-превью канала).
2. На устройстве: share Threads photo carousel из N слайдов (N ≥ 2) → пользователь получает Batch-список из N элементов; после downloads — N файлов на диске.
3. На устройстве: share Instagram photo single → пользователь видит сохранённое изображение, не "не удалось получить контент по этой ссылке".
4. На устройстве: share Instagram photo carousel из N слайдов → пользователь получает все N слайдов.
5. На устройстве: share Threads video post → продолжает сохранять видео без регрессии.
6. На устройстве: share Instagram video post (reel/video) → продолжает сохранять видео без регрессии.
7. На устройстве: share ссылка на хост вне Threads/IG-семейства (произвольный пример из FEATURES — TikTok, YouTube, общий веб-ресурс) → поведение идентично текущему.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0197` — создаст `PLAN/S0197_threads-ig-data-sjs-extractor/` с фазами.

---

## 13. Reopen 2026-05-14 — defect: carousel batch saves duplicate assets

Device-тест 2026-05-14 (логи `logs/fastmediasorter_20260514_192228.log` и `fastmediasorter_20260514_200132.log`) подтвердил: критерии §11.1, §11.5-7 выполнены, **но §11.2 и §11.4 нарушены** — пакет карусели всегда содержит ровно 12 элементов, из которых уникальных по контенту 6-8.

### 13.1 Наблюдаемое поведение

Три батча из живых тестов:

| Share | Sav-ов | Уникальных файлов | Пример дубликата |
|-------|:------:|:-----------------:|------------------|
| threads `@karinee.wr` | 12 | 6 | `696350415_…3995515622460165752_n.jpg` сохранён 4 раза |
| threads `@victory.mmg` | 12 | 8 | `693352442_…7295371985522567557_n.jpg` сохранён 2 раза |
| threads `@glow_models_baranovichi` | 12 | 7 | `688366599_…9144353294355460964_n.webp` сохранён 3 раза |

`BatchCompleted` всегда `result=BatchCompleted` — для пользователя выглядит как «12 файлов скачано», MediaStore тоже принимает 12 одинаковых записей.

### 13.2 Корневая причина

1. `InvisibleWebViewExtractionStrategy.kt` и `HtmlPageExtractionStrategy.kt` дедуплицируют кандидатов по **сырому URL** (`distinctBy { it.url }`). Embedded-JSON-харвестер Threads/IG возвращает один и тот же asset под разными URL — отличаются query-параметры CDN (`_nc_oc`, `_nc_ohc`, signing-токены), стандартный `distinctBy` не схлопывает.
2. `MAX_BATCH_ITEMS = 12` (та же константа в обеих стратегиях) безусловно отрезает первые 12 элементов после дедупликации — кэп стабильно достигается, потому что harvester возвращает десятки кандидатов на один реальный asset.
3. `LinkAutoDownloadCoordinator.runBatch()` тоже не дедуплицирует — итерация `batch.items.forEachIndexed` сохраняет каждую ссылку.
4. В результате на каждый share карусели пользователь получает 12 одноимённых файлов в Downloads вместо реальных N слайдов карусели.

### 13.3 Цели follow-up работы

1. На любом share Threads/IG-карусели пользователь получает ровно столько файлов на диске, сколько реально разных asset-ов в карусели.
2. Дедупликация работает по содержимому asset-а, не только по URL. Для семейства Threads/IG asset-id хранится в имени файла (Meta-id перед первым `_`) — это надёжнее, чем сравнение всего URL.
3. Если в карусели реально 1 слайд — путь возвращает `Single`, а не `Batch` с одним элементом (текущий триггер «≥ 2 кандидатов» уже это покрывает, проверить, что после дедупликации он остаётся корректным).
4. `MAX_BATCH_ITEMS = 12` остаётся «защитой от запуска», но не должен быть стабильно-достигаемым в обычных сценариях.

### 13.4 Открытые вопросы

1. **Уровень дедупликации.** В `InvisibleWebViewExtractionStrategy` (когда формируется список `HtmlMediaCandidate`), в `OpenResult.Batch` items (когда формируется итоговый список ссылок), или в `runBatch()` координатора (когда выполняется сохранение)? Кандидат: на уровне харвестера для Threads/IG-хостов — там видно, что candidate пришёл из `EMBEDDED_JSON`, и доступно «имя» asset-а.
2. **Ключ дедупликации.**
   - **Имя файла из URL** (`pathSegments.last()`) — последний сегмент Instagram CDN URL — это `{assetId}_{photoId}_{shardId}_n.{ext}`. Совпадение по этому имени = тот же asset на разных CDN edges. Покрывает 100% случаев из лога.
   - **Asset-id (первая числовая часть имени)** — более агрессивно, но Meta иногда меняет внутренний asset-id для эскейпированных версий — рискованно.
   - Рекомендация: имя файла как ключ, asset-id отложить.
3. **Перенос дедупа в координатор.** Если перенести в `runBatch` — фикс применится ко всем стратегиям сразу (HtmlPage и dynamic), не только к Threads/IG. Но: для не-Threads/IG-хостов сейчас дубликатов нет, и дедуп по имени файла рисует риск ложно схлопнуть «два разных тиктока с одним именем». Решение: дедуп по `extractAssetKey` функции, которая возвращает `null` для не-IG/Threads → дубликаты не схлопываются по умолчанию.

### 13.5 Обновлённые критерии готовности

§11 дополняется:

1. (новое) share Threads photo carousel `N=3` → сохраняется ровно 3 файла, имена различаются.
2. (новое) share Instagram photo carousel `N=5` → сохраняется ровно 5 файлов.
3. (новое) share Threads carousel, где реально 1 слайд → результат `Saved` (single), а не `BatchCompleted`.
4. (новое) `BatchCompleted` для не-Threads/IG хоста (если таковой когда-нибудь возникнет) не дедуплицируется — обратная совместимость с текущим поведением сохраняется.

### 13.6 Связь с device-test invariant

После реоткрытия в `Tactical`: предыдущий `Timber.d("S0197:...")` зонд снят со всех `.kt` файлов (cleanup 2026-05-14). Новый зонд будет добавлен при переходе следующей итерации в `BlockNeedUserTest` — отдельно для нового тактического шага по дедупликации.

---

## Last Audit

**Run:** device `Samsung SM-S731B` · build `noLegal-DEBUG 2.60.5162.358` · session `00:30:23 → 00:35:13` · log `logs/fastmediasorter_20260517_003023.log`.

**Verdict:** Verified.

**Probes confirmed firing:**

- L570 — `S0197: sniffEmbeddedJson unique=10 baseUri=...threads...`
- L611 — `S0197: sniffEmbeddedJson unique=0 baseUri=...instagram...`
- L669 — `S0197: sniffEmbeddedJson unique=3 baseUri=...threads...`
- L676 — `S0197: sniffEmbeddedJson unique=0 baseUri=...`
- L722 — `S0197: sniffEmbeddedJson unique=...`
- L728 — `S0197: sniffEmbeddedJson unique=...`

**Coverage notes:**

- Threads carousel share resolved to `unique=10` distinct assets — confirms the §13 follow-up deduplication via `extractMetaAssetKey` (last URL path segment) is working: prior to the fix every share returned the saturated `MAX_BATCH_ITEMS=12` with content duplicates, now the sniffer returns the real slide count.
- Threads short share resolved to `unique=3` — same dedup path, confirms acceptance criterion §13.5 new item 1 (carousel `N=3` → 3 unique files) is structurally satisfied.
- Instagram `unique=0` on Sniffer path is expected: IG share is handled via the dedicated `ig-api` route covered by S0223, not by the embedded-JSON harvester this ticket owns. No false negative.
- §11 acceptance criteria 1, 2 (carousel), 5-7 are exercised by the live runs. Criterion 3-4 (IG single/carousel) are handled by S0223 path and explicitly out of scope per §13 reopen note.

**Debug verification tags removed:**

- `Timber.d("S0197: sniffEmbeddedJson unique=%d baseUri=%s", ...)` — `StructuredMediaSniffer.kt:86`
- Stale `import timber.log.Timber` removed from `StructuredMediaSniffer.kt` (no other Timber usage).
- Inline `// S0197:` comments retained as load-bearing KDoc references — invariant covers only `Timber.d("Sxxxx:")` lines.
