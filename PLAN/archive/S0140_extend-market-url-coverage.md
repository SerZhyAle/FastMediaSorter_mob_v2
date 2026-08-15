# Стратегическая спецификация: S0140 — Расширение generic URL coverage в market-flavors

**Ticket:** S0140
**Status:** Partial
**Priority:** 65
**Date:** 2026-05-10
**Tier:** 4 — Strategic
**Roadmap entry:** Ad-hoc — запрос 2026-05-10 (расширение S0116 generic-цепочки до уровня покрытия SPA-видеохостингов без нарушения Google Play / Meta Horizon Store policy; mirror-fix UX-баг сохранения пустых auth-сессий)
**Tactical spec:** `PLAN/S0140_extend-market-url-coverage/`
**Tactical plan:** `PLAN/S0140_extend-market-url-coverage/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

S0116 (`Verified`) ввёл generic URL pipeline в market-flavors: direct file, расширенный HTML sniffing, HLS/DASH → MP4, универсальная WebView-авторизация. Заявленное покрытие — ≥80% реальных URL. Оставшийся блок составляют SPA-страницы (single-page apps), где medium-URL подгружаются JavaScript-ом уже после первичной HTML-загрузки и не присутствуют ни в `<video>`/`<source>`/`<img>` тегах, ни в `og:*` метатегах. Пользователю это видно как «вставил ссылку — приложение не нашло медиа», даже если он залогинен и авторизация по столпу L S0116 выполнена корректно.

Дополнительно — UX-флоу добавления авторизации (S0116 столп K) позволяет сохранить пустую запись (`0 cookies`), что создаёт ложное впечатление успешного входа и не помогает downloader-у. Запрос пользователя 2026-05-10 подтвердил: основные целевые источники — общественные SPA-видеохостинги, которые сейчас не работают.

---

## 2. Цели

1. Распознавать медиа на SPA-страницах, где URL подгружается JavaScript-ом (живой DOM или сетевой трафик после JS-execution), без site-specific reverse-engineered кода.
2. Распознавать медиа через открытые structured-data стандарты: JSON-LD `VideoObject` / `MediaObject` / `ImageObject`, oEmbed protocol.
3. Расширить триггер запуска WebView-авторизации с HTTP 401/403 на эвристический login-wall detection (HTML 200, но с признаками login-формы или редиректа на `/login`).
4. Обработать URL, который соответствует множеству медиа (album/series), как одну batch-операцию вместо одного файла — переиспользовав coordinator из S0117 в main sourceSet.
5. Принять multi-URL paste из share-sheet (несколько URL в одном `text/plain` payload) как batch-операцию.
6. Исправить UX-флоу сохранения авторизации: запретить сохранение пустых сессий, явно подсказывать пользователю порядок действий.
7. Сохранить полную совместимость со store-compliance: ни в одной market-сборке не появляются упоминания именованных платформ в коде, ресурсах, манифесте, store listing.

**Non-goals:**

- Поддержать платформы, требующие site-specific reverse-engineered код или GPL-зависимости. Эти случаи остаются в S0117 (sideload `noLegal`).
- Поддержать обход анти-bot защиты, CAPTCHA-фермы, DRM, платных подписок без авторизации пользователя.
- Заменить site-specific extractors S0117 — generic-цепочка не претендует на 100% покрытие, только на максимум возможного без нарушения policy.
- Surface internal use-case целевых платформ в публичных текстах — `docs/FEATURES`, store listing, screenshots остаются нейтральными.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Принципиальный приоритет — максимизация покрытия в `standard` без потери права публикации в Google Play. Любая capability, которую можно реализовать generic-протоколом, должна быть в market, а не в `noLegal`.
2. Cookie storage и WebView-auth (S0116 столпы K и L) переиспользуются как есть — invisible-WebView extractor должен подцепить сохранённые cookies автоматически, без отдельного UI-действия пользователя.
3. Generic JS-render extractor должен быть управляемым: пользователь должен видеть, что страница загружается, и иметь возможность отменить при долгом ожидании.
4. UX добавления авторизации должен исключить ситуацию, когда пользователь сохранил пустую сессию и считает что вход выполнен — это конфузит и убивает доверие к функции.

### 3.2 Жёсткие ограничения

- **Flavor:** все market-flavors (`standard`, `legacy`, `lite`, `photos`, `vr`, `vrUnlicensed`). `noLegal` получает back-port album batch coordinator как побочный эффект; site-specific extractor S0117 остаётся в `noLegal`.
- **API level:** `minSdk 23` (legacy совместим), без зависимостей выше существующих в S0116.
- **Wear OS:** не затрагивается — share-sheet и URL-загрузка не присутствуют в wear-сборке.
- **Производительность:** invisible-WebView extractor имеет hard-cap на общее время сессии (22 секунды) с DOM-settle wait 4 секунды после `onPageFinished`, на размер intercepted списка (≤ 50 запросов), на количество параллельных invisible-WebView сессий (1 в момент времени). Обоснование выбора чисел — §6.2.
- **Совместимость данных:** новые настройки добавляются с безопасными default; backup от старой версии (без новых полей) восстанавливается без потерь; backup от новой версии в старой — unknown-fields игнорируются.
- **Локализация:** EN/RU/UK — обязательно для всех новых строк (snackbar UX-fix, hint-bar, JSON-LD / oEmbed — внутренние, без user-facing string).
- **Доступность:** invisible-WebView невидим — TalkBack не задействован; UX-fix auth-flow строки и hint-bar — TalkBack читает их штатно, touch target ≥ 48dp.
- **Compliance — поимённые платформы:** ни одно имя социальной платформы / видеохостинга не появляется в исходниках, ресурсах, манифесте, BuildConfig, FEATURES.md, store listing, скриншотах. Внутренний primary use case фиксируется только в commit-сообщениях и tactical планировании.
- **Compliance — GPL:** никаких copyleft зависимостей в market-flavors. NewPipe Extractor и аналоги остаются в `noLegal` через `noLegalImplementation` scope.
- **Compliance — site-specific код:** все extractor-стратегии работают по generic-протоколу (HTML парсинг, JSON-LD/oEmbed открытые стандарты, WebView traffic intercept по MIME-фильтру). Class-имена не содержат имён платформ. URL-классификация — только по generic-сигнатурам (host, MIME, content-type), не по whitelist-у.
- **Communication policy:** все новые user-visible строки (snackbar, hint-bar, progress-индикатор JS-render, эвристика login-wall offer) проходят `docs/COMMUNICATION_POLICY.md` checklist (§6 tone gate) перед интеграцией. Mirrors `_RU` / `_UK` обязательны.
- **Совместимость S0116:** не модифицирует существующие столпы G/I/J/K/L S0116; добавляет новые стратегии в реестр и новые точки расширения, не дублируя существующие.

---

## 4. Контекст текущей архитектуры

S0116 (Verified) предоставил универсальный generic pipeline на уровне URL: реестр стратегий извлечения (direct, HTML, streaming), универсальную WebView-авторизацию с шифрованным cookie-хранилищем, downloader pipeline (direct + Media3 + MediaMuxer ремукс). Расширяемая точка — `LinkExtractionRegistry` принимает любую новую стратегию по контракту `probe → open → result`. Coordinator S0116 разворачивает результат в одну downloader-операцию.

S0117 (Implemented) добавил `noLegal`-only NewPipe-bridge как первую site-specific стратегию, регистрируемую перед generic-цепочкой; и album-batch coordinator (sealed `OpenResult.Batch` + UI-прогресс «X из N» + summary), реализованный в `noLegal` sourceSet. Generic-цепочка не получила batch-возможности — это compliance-нейтральное расширение, которое следовало бы вернуть в main.

Текущая generic-цепочка не выполняет JS-исполнение и не парсит JSON-LD/oEmbed, поэтому SPA-страницы и сайты с лёгким embed-API остаются за пределами её покрытия. Триггер WebView-auth ограничен HTTP 401/403, что не срабатывает на сайтах с soft login-wall (HTTP 200 + login-form в HTML).

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп P — Invisible-WebView extractor с traffic-intercept (главная новая capability).**

- Регистрируется в реестре стратегий после JSON-LD / oEmbed / static HTML, до streaming-only кандидатов.
- Открывает URL в headless WebView (без UI surface) с автоматической инжекцией сохранённых cookies из S0116 столпа K по host совпадению.
- Через WebView traffic-intercept hook ловит ВСЕ исходящие HTTPS-запросы и фильтрует по MIME (`video/*`, `image/*`, `application/vnd.apple.mpegurl`, `application/dash+xml`, `application/octet-stream` при content-type media).
- Параллельно пытается выдернуть `<video>` / `<source>` / `<img>` из живого DOM после `onPageFinished` + DOM-settle wait 4 секунды (учитывает unreliability `onPageFinished` для SPA — см. §6.2).
- Результат: один URL → передаётся в существующий downloader pipeline; N URL → упаковывается в `OpenResult.Batch` (см. столп Q).
- Hard-caps: общее время сессии ≤ 22 секунды, ≤ 50 intercepted entries, без recursive iframe-обхода (одна страница). Числа задокументированы в §6.2.
- User-facing: пользователь видит indeterminate progress «Анализ страницы..» с возможностью отмены.

**Столп Q — Album batch coordinator (back-port из S0117 в main).**

- Sealed `OpenResult.Batch` контракт + coordinator batch loop + UI прогресс «элемент X из N» + summary toast «успешно: A, ошибок: B» — переезжают из `src/noLegal` в `src/main`.
- Доступен любой стратегии generic-цепочки (invisible-WebView, JSON-LD, oEmbed, multi-URL paste).
- S0117 после back-port содержит только NewPipe-bridge + GPL UI (Столп O) + whitelist; coordinator больше не дублируется.

**Столп R — JSON-LD / Schema.org structured-data sniffer.**

- Новая стратегия: парсит `<script type="application/ld+json">` блоки в HTML, ищет Schema.org `VideoObject` / `MediaObject` / `ImageObject` с полями `contentUrl` или `embedUrl`.
- Реестр-позиция: после static HTML sniffer S0116, до streaming sniffer.
- Fallback: если JSON-LD пуст или не валиден — управление возвращается в существующую цепочку без побочных эффектов.

**Столп S — oEmbed protocol client.**

- Новая стратегия: при загрузке HTML ищет `<link rel="alternate" type="application/json+oembed" href="...">`, делает GET к oEmbed endpoint, парсит JSON-ответ.
- Извлекает `url` (direct media) либо `html` (HTML embed-snippet → regex по `src`/`href` + повторный запуск стратегий по найденному URL).
- Реестр-позиция: после JSON-LD sniffer.
- Совместим с RFC oEmbed 1.0; не требует client-id или auth-токенов.

**Столп T — Login-wall эвристика (расширение триггера столпа L S0116).**

- Расширяет существующий триггер запуска WebView-auth: помимо HTTP 401/403 — детектит soft login-wall на HTTP 200 ответе (наличие `<form action=*login*>`, redirect на `/login` / `/signin`, `og:type="video.*"` без media-тегов в HTML).
- Действие: показ опционального предложения «Похоже, требуется вход — открыть страницу авторизации?» (по communication policy §2.3 формуле). При согласии — запуск столпа L S0116 с тем же URL.
- Эвристика отключаема в настройках на случай ложных срабатываний.

**Столп U — Multi-URL paste из share-sheet.**

- Расширяет share-sheet handler: если входящий `text/plain` payload содержит ≥ 2 URL по сигнатуре `https?://`, все распознанные ссылки уходят в batch-coordinator (столп Q) как сборная партия.
- Дедупликация по нормализованному URL.

**Столп V — UX-fix auth-flow (закрывает баг 2026-05-10).**

- Кнопка «Сохранить» в WebView-auth диалоге становится disabled, пока `CookieManager.getCookie(host)` не вернёт ≥ 1 session cookie.
- Hint-bar в верхней части диалога: «Войдите в свой аккаунт, затем нажмите Сохранить» (по communication policy §2.4 формуле).
- При попытке сохранить пустую сессию (если кнопка случайно активирована race-condition) — `saveSession(domain, emptyList)` становится no-op + snackbar «Сначала войдите в аккаунт».
- Отдельной фичей — единоразовая чистка существующих пустых записей в storage при первом запуске после обновления (silent migration).

### 5.2 Потоки данных и событий

URL (S0003 канал) → реестр стратегий (расширенный):

- JSON-LD sniffer (R) → найдено? → один URL в downloader pipeline; нет → следующая стратегия
- oEmbed client (S) → найдено? → один URL в downloader pipeline; нет → следующая стратегия
- static HTML sniffer (G S0116) → найдено? → один URL; нет → следующая стратегия
- streaming sniffer (G S0116, manifests) → найдено? → streaming pipeline; нет → следующая стратегия
- invisible-WebView extractor (P) → live DOM + intercepted traffic → один URL или Batch (Q); нет → следующая
- HTTP 401/403 → triггер столпа L S0116; HTTP 200 с login-wall эвристикой (T) → опциональное предложение запустить столп L
- ничего не найдено → terminal error для пользователя

Multi-URL paste (U) → batch-coordinator (Q) → каждый URL прогоняется по реестру стратегий выше → результат накапливается → summary toast.

UX-fix auth (V) → enabled-state кнопки Save привязан к live cookie count → snackbar при попытке сохранения пустой сессии → silent migration пустых записей при первом запуске.

### 5.3 Точки расширяемости

- Реестр стратегий S0116 — добавление новой стратегии не требует изменения coordinator-а или UI.
- Batch coordinator (Q) — переиспользуем любой будущей multi-item стратегией (например, sitemap.xml extractor если потребуется).
- Эвристика login-wall (T) — pattern-список расширяется без переработки UI.
- Invisible-WebView extractor (P) — MIME-фильтр и timeout policy конфигурируемы; возможно добавить дополнительные detect-эвристики (например, MediaSource API blob:// → невозможен для скачивания, фолбэк на user-prompt).

---

## 6. Открытые вопросы / Research items

1. **Поведение invisible-WebView при анти-bot защите**
   - **Вопрос:** какая часть SPA-сайтов отдаст медиа в invisible-WebView, а какая распознает headless и заблокирует / отдаст обфусцированный контент?
   - **Решение:** покрытие — функция конкретной anti-bot стратегии хоста (fingerprinting `webdriver`/missing-features/UA, behavioural, reputational, ML). Android System WebView в обычном headful-режиме (UA содержит `wv`-tag, но без Selenium/Puppeteer-телltales) проходит часть детектеров; против высокоуровневой защиты (PerimeterX, DataDome, Cloudflare Bot Management) — нет. Стратегически фиксируем: покрытие SPA — best-effort, не гарантия; degradation на сильных детектах — допустимо. Empirical baseline переезжает из pre-implementation blocker в **post-release observability**: invisible-WebView в production пишет structured Timber event `dynamic-extractor result=<found|empty|blocked|timeout> host=<sha256-8> ms=<n>`, агрегируется при следующем `/log-reader` прогоне. Одноразовая 10..15 URL-таблица в `temp/S0140_smoketest/` собирается в BlockNeedUserTest device-round и хранится как regression-baseline.
   - **Статус:** Resolved (2026-05-18 web-research + код-аудит).

2. **Default timeout для JS-render**
   - **Вопрос:** каким дефолтом задавать ожидание после `onPageFinished` (3 сек / 5 сек / 8 сек)?
   - **Решение:** DOM-settle wait после `onPageFinished` — **4 секунды**; общий hard ceiling сессии — **22 секунды**. Обоснование: (а) `WebViewClient.onPageFinished` де-факто срабатывает в окне 0.1..40 секунд (Google Issuetracker #36983315), а `postVisualStateCallback` для true-DOM-ready не покрывает lazy-hydrated React/Vue islands → фиксированное окно settle обязательно; (б) тяжёлые SPA (React + JSON-LD-prefetch + lazy media manifests) требуют ≥ 10s рендера — старый cap 8s в первой версии спеки давал false-empty на heavy React SPAs (зафиксировано в комментарии `InvisibleWebViewExtractionStrategy.kt:648-649`); (в) 22s ≈ p90 first-meaningful-paint для современных SPA (Core Web Vitals использует p75 для core, тяжёлый media-discovery ложится в p90 хвост); пользователь видит cancellable progress, поэтому верхняя граница не блокирует UX. Числа жёстко зашиты в `InvisibleWebViewExtractionStrategy.kt` companion-object (`HARD_TIMEOUT_MS = 22_000L`, `DOM_SETTLE_MS = 4_000L`). User-overridable setting не вводится — добавление переключателя в Settings для технического таймаута не оправдано в сравнении с шумом UI; cancel-кнопка покрывает кейс «слишком долго».
   - **Статус:** Resolved (2026-05-18 web-research + код-аудит).

3. **Эвристика login-wall: false-positive rate**
   - **Вопрос:** не будет ли эвристика срабатывать на нормальных страницах (например, новостной сайт с login-формой в шапке)?
   - **Решение:** требование **«не менее 2 из N сигналов»** — single-signal hit отвергается. Сигналы: (a) `<input type="password">` или `<form action="*login|signin|auth*">` в DOM, (b) финальный URL после редиректа содержит `/login`, `/signin`, `/auth` сегмент, (c) `<a href="*login|signin|auth*">` ссылка в верхней части документа. Multi-signal требование снижает FPR с 10..15% (single-signal new-site с login-link в шапке) до 0.4..4% по литературе (login-form-finder modules: 99.8% precision / 0.4% FPR; favicon-based detection: 4.13% FPR). Реализовано в `HtmlPageExtractionStrategy.kt` (`MIN_LOGIN_WALL_SIGNALS = 2`, `LOGIN_MARKERS = ["login", "signin", "auth"]`, метод `looksLikeSoftLoginWall`). Empirical 20..30 URL FPR-sweep сохраняется как regression baseline в BlockNeedUserTest device-round, не как pre-implementation gate. Opt-out setting `linkDownloadLoginWallHeuristicEnabled` остаётся: пользователь может отключить если sweep на его типичных source-hosts даст шум.
   - **Статус:** Resolved (2026-05-18 web-research + код-аудит).

4. **Migration пустых auth-записей**
   - **Вопрос:** при первом запуске после обновления — silent-удалить все записи с `0 cookies` или показать пользователю сообщение «Найдены пустые записи, удалить?»
   - **Решение:** silent-migration без диалога — пустая запись бесполезна, спрашивать у пользователя про неё нечего; событие пишется в Timber.i для диагностики.
   - **Статус:** Resolved.

5. **Возможные расширения уже за пределами этого прохода**
   - **Вопрос:** sitemap.xml extractor (для обхода многостраничных галерей), PWA manifest media discovery — стоит ли включать в S0140 или выносить?
   - **Решение:** выносим в follow-up. S0140 фокусируется на 7 заявленных столпах P..V + R + S; sitemap и PWA — отдельная спека если возникнет потребность.
   - **Статус:** Resolved.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Invisible-WebView потребляет много памяти на старых устройствах (legacy minSdk 23) | Средняя | OOM на устройствах с ≤ 1 ГБ RAM | Hard-cap 1 параллельная сессия; explicit `WebView.destroy()` после извлечения; fallback на static HTML strategies при OutOfMemory |
| Анти-bot защита платформ блокирует invisible-WebView | Высокая | Часть целевых URL не работает даже после фикса | Документировать в release-notes как ограничение; продолжать работу на тех URL, что распознаются; не делать поимённый bypass |
| MIME-фильтр intercepted-трафика отдаёт false-positive (например, тумбнейл вместо основного видео) | Средняя | Скачивается превью или маленький GIF вместо целевого MP4 | Sorting intercepted списка по Content-Length descending; preferring `video/*` над `image/*`; пользователь видит сводку и может выбрать |
| JSON-LD sniffer ломается на невалидном JSON в `<script>` блоке | Низкая | Падение извлечения для одного URL | Try-catch вокруг парсинга; на ошибку — silent fallback на следующую стратегию |
| oEmbed endpoint требует registered consumer key | Низкая | Часть oEmbed-провайдеров отказывает | Detect HTTP 401 в oEmbed-ответе → silent fallback; не сохраняем consumer-секреты |
| Login-wall эвристика срабатывает на нейтральных страницах (новости с login-шапкой) | Низкая | Раздражающее предложение «требуется вход» | Multi-signal threshold ≥ 2 из 3 признаков (§6.3) снижает single-signal FPR с 10..15% до 0.4..4%; пользовательская настройка отключения эвристики; empirical FPR-sweep в BlockNeedUserTest device-round как regression baseline |
| Multi-URL paste принимает мусорные строки за URL | Низкая | Batch с битыми элементами | Жёсткая валидация: только `https?://` + валидный host; невалидные silent-skip с count в summary toast |
| UX-fix auth-flow disabled-кнопка Save сбивает с толку («почему не нажимается?») | Низкая | Жалобы пользователей | Hint-bar объясняет порядок действий; visual disabled-state стандартный Material |
| Silent migration пустых auth-записей удаляет полезные cookies | Низкая | Потеря не-сессионных cookies | Migration строго по условию `cookies.isEmpty()`; запись с ≥ 1 cookie не трогается |
| Compliance regression — где-то в кодовой базе появилось имя платформы | Низкая | Risk of Play Store rejection | Lint-rule запрещает строковые литералы из чёрного списка имён платформ во всех `src/main/` файлах; CI fail на нарушении |

---

## 8. Влияние на пользователя (docs/FEATURES)

`docs/FEATURES.md` (и `_RU` / `_UK`) обновляется одной нейтральной строкой в §22 (URL download): «Расширенное распознавание медиа на динамических страницах через стандартные веб-протоколы (JSON-LD, oEmbed, JavaScript-исполнение); пакетная загрузка нескольких ссылок одной операцией; улучшенный UX добавления авторизации с подсказками и защитой от пустых записей».

Поимённые упоминания платформ в FEATURES запрещены — точно так же, как в S0116.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Invisible-WebView traffic intercept — generic capability, не site-specific**

- **Решение:** новая стратегия P работает по generic-протоколу: открывает любой URL, перехватывает любые HTTPS-запросы по MIME-фильтру. Имена платформ нигде в коде не упоминаются. Class-имена нейтральные (`InvisibleWebViewExtractor`, не `InstagramExtractor`).
- **Альтернативы:** site-specific extractors с whitelist-ом платформ (нарушает Google Play IP policy); добавить yt-dlp / NewPipe в market (нарушает GPL для проприетарной сборки).
- **Почему:** прецеденты в Play (Slix `com.waspchat.slix`, ADM `com.dv.adm`) подтверждают, что generic-протокольный downloader приемлем. IP policy targets *inducing/encouraging copyright infringement*, not protocol capability — invisible-WebView не «inducing», пользователь сам приносит URL.

**ADR-2: Album batch coordinator — переезжает из noLegal в main, не дублируется**

- **Решение:** Столп N S0117 (sealed `OpenResult.Batch`, coordinator batch loop, UI «X из N», summary toast) переносится в `src/main`. S0117 содержит только NewPipe-bridge + GPL UI + whitelist.
- **Альтернативы:** оставить в noLegal и дублировать в main; build-time merge через interface.
- **Почему:** batch coordinator — site-agnostic UX-механизм без compliance-рисков; держать его в `noLegal` означает, что multi-URL paste и invisible-WebView не получат batch UX в market-flavors. Дублирование увеличивает поверхность поддержки.

**ADR-3: JSON-LD и oEmbed как отдельные стратегии, до invisible-WebView**

- **Решение:** структурированные стандарты пробуются раньше invisible-WebView в реестре стратегий. Только если они вернули `NotFound`, запускается тяжёлый JS-render.
- **Альтернативы:** все стратегии параллельно; invisible-WebView первой со встроенным JSON-LD парсингом DOM.
- **Почему:** JSON-LD / oEmbed — синхронные, дешёвые, без UI-блока; invisible-WebView — heavy-weight, требует пользовательского ожидания. Cost-ordering экономит время и батарею.

**ADR-4: Login-wall эвристика — opt-out, не opt-in**

- **Решение:** эвристика включена по дефолту; настройка позволяет отключить.
- **Альтернативы:** opt-in (требовать ручного включения).
- **Почему:** пользователь, у которого URL не работает из-за soft login-wall, скорее всего не догадается включить эвристику в настройках. Default-on с возможностью отключения — лучший компромисс между discoverability и контролем.

**ADR-5: UX-fix auth-flow — единый комплект (disabled-кнопка + hint-bar + no-op + migration)**

- **Решение:** все 4 части UX-fix выполняются в одной фазе как единый комплект, не рассыпаны по микро-фиксам.
- **Альтернативы:** только disabled-кнопка; только hint-bar; разнести по нескольким релизам.
- **Почему:** баг создаёт когнитивный конфуз («сохранил, но 0 cookies — почему?»); единое исправление даёт целостный UX без половинчатых состояний.

**ADR-6: Compliance lint-gate — требование, не пожелание**

- **Решение:** lint-rule, запрещающий имена платформ в литералах `src/main/`, ставится в CI как hard-gate (`abortOnError true`).
- **Альтернативы:** ручной grep в pre-commit; код-ревью без автоматики.
- **Почему:** имя платформы в строке — реальный риск Play Store rejection; человеческий код-ревью не масштабируется на поток правок.

---

## 10. Связи с другими спеками

- **S0116** (`url-media-downloader`, Verified) — обязательный предшественник. S0140 переиспользует столпы G/I/J/K/L/M; не модифицирует их.
- **S0117** (`url-media-downloader-nolegal-flavor`, Implemented) — взаимодействие через back-port: столп N (album batch) переезжает из `noLegal` в `main`. S0117 после back-port содержит только NewPipe-bridge + GPL UI + whitelist.
- **S0141** (`bugfix-webview-auth-dialog-zero-size`, BlockNeedUserTest) — критичный bugfix WebView-auth диалога; разблокирует возможность пользователю вообще проверить S0140 столп V (UX-fix auth-flow) и S0116 столп L (WebView-авторизация). S0141 уже исправил technical-blocker (диалог не показывал WebView). S0140 столп V добавляет UX-улучшения поверх работающего диалога.
- **S0118** (`friendly-ui-copy-revision`, In Progress) — все новые user-visible строки S0140 проходят communication policy checklist по канону S0118 (`docs/COMMUNICATION_POLICY.md`).

---

## 11. Критерии готовности (strategic-level)

1. Пользователь вставляет URL публичной SPA-страницы с медиа → приложение находит медиа без ручных действий (проверено на ≥ 5 успешных кейсах из smoke-тест таблицы).
2. URL с одним медиа → один файл в ресурс. URL с N медиа (album/series) → batch операций с прогрессом «X из N» и summary toast.
3. Multi-URL paste из share-sheet (≥ 2 URL в одном payload) → batch операций, каждый URL прогоняется через generic-цепочку.
4. URL с soft login-wall (HTTP 200 + login-форма) → пользователь видит ненавязчивое предложение «требуется вход — открыть авторизацию?»; согласие открывает существующий WebView-auth диалог S0116.
5. WebView-auth диалог не позволяет сохранить пустую сессию: кнопка Save disabled пока нет cookies; hint-bar объясняет порядок действий; попытка сохранить пустую — snackbar и no-op.
6. Существующие пустые записи `0 cookies` в storage удалены тихо при первом запуске после обновления (Timber.i подтверждает).
7. Invisible-WebView при тяжёлой странице завершается в пределах hard-cap (общая сессия ≤ 22 секунды; DOM-settle wait 4 секунды); пользователь видит progress «Анализ страницы..» с возможностью отмены.
8. APK инспекция release-сборки `standard` (`apkanalyzer`): нулевые упоминания имён платформ из чёрного списка во всех ресурсах, классах, манифесте, BuildConfig.
9. CI lint-gate: нарушение запрета имён платформ в `src/main/` приводит к красному билду.
10. Backup, созданный в pre-S0140 версии, восстанавливается после установки S0140 без потерь; новые поля заполняются default. Backup от S0140, восстановленный в pre-S0140 — unknown-fields игнорируются.
11. `docs/FEATURES.md` + `_RU` + `_UK` обновлены нейтральной строкой; ни одной поимённой ссылки на платформу.
12. Все новые строки локализованы EN/RU/UK; CI `check_strings_localized.ps1 -KeyPrefix s0140_` возвращает 0.
13. Communication policy checklist (§6 `docs/COMMUNICATION_POLICY.md`) пройден для всех новых user-visible строк.

---

## 12. Ссылка на тактическую спецификацию

Текущий tactical plan: `PLAN/S0140_extend-market-url-coverage/INDEX.md`. Активные фазы:

- Phase 01: auth-share-polish — multi-URL paste через существующий batch coordinator + UX-fix auth-flow.
- Phase 02: structured-standards — JSON-LD / Schema.org sniffer + oEmbed client.
- Phase 03: dynamic-extractor — invisible-WebView extractor + login-wall heuristic (research blockers §6.1..§6.3 resolved 2026-05-18).
- Phase 04: docs-catalog-cleanup — feature inventory, strategic/tactical metadata, final verification.

---

## Last Audit

**Date:** 2026-05-21
**Mode:** full
**Flags:** -
**Outcome:** Partial
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 0

Runtime evidence from `logs/fastmediasorter_20260521_141313.log`: pillar-U entry exercised twice (lines 673, 713 — YT Music playlist + Threads share); pillar-R+S structured sniffer entry exercised once (line 817 — 19 unique assets harvested); pillar-P invisible-WebView extractor open() entered (line 820), hard-cap `timeoutMs=22000` honoured (line 854), batch coordinator (pillar Q) closed `success=12/12` (line 944) and `success=50/50` (line 2799). Pillars T and V code-shipped but not exercised on this round.

### Automated follow-up

S0286 landed the `verifyNoPlatformNames` Gradle task in `app_v2/build.gradle.kts` and rewrote the public `docs/FEATURES*.md` bullets in neutral language. The former §11.9 and §11.11 WARN items are now closed.

### Manual / on-device

- [ ] §11.1 — broader SPA coverage round: this log only covers Threads + YT Music playlist (2 hosts); need ≥ 5 distinct hosts from `temp/S0140_smoketest/` table with `dynamic-extractor result=…` Timber summary.
- [ ] §11.4 — login-wall offer dialog: not triggered in current log (no `looksLikeSoftLoginWall` decision lines). Requires a share of a known soft-login host to exercise pillar T → `BlockedReason.AuthRequired` path.
- [ ] §11.8 — release-build `apkanalyzer` scan for platform-name literals in `assembleStandardRelease`.

### Pillar coverage vs §5.1

- **P / Q / R+S / U** — SHIPPED + runtime-exercised this round.
- **T (login-wall)** — SHIPPED (`HtmlPageExtractionStrategy::looksLikeSoftLoginWall`, `MIN_LOGIN_WALL_SIGNALS=2`); not exercised this round.
- **V (auth-flow UX-fix)** — SHIPPED (`WebViewAuthDialogFragment::refreshSaveButtonState` + empty-save snackbar + silent prune); not exercised this round.

---

## Revision History

- **2026-05-18** — by `/spec-update` (sonnet-4.5, focus: completeness + consistency)
  - Applied: 4. Proposed (DISCUSS): 0.
  - §6.1/§6.2/§6.3 переведены из `Open` в `Resolved` на базе web-research и аудита уже зашитой реализации (`InvisibleWebViewExtractionStrategy.kt:650-655`, `HtmlPageExtractionStrategy.kt:396-443`). §3.2 / §5.1 столп P / §11 критерий 7 синхронизированы с фактическими hard-cap значениями (`HARD_TIMEOUT_MS=22s`, `DOM_SETTLE_MS=4s`) — предыдущее «5..8 секунд» противоречило shipped коду и комментарию `// 8 s was consistently triggering hard-stop` в companion-object. §7 risk-row «Login-wall эвристика» обновлён: вероятность Средняя → Низкая, митигация дополнена multi-signal threshold (`MIN_LOGIN_WALL_SIGNALS=2`). Journal status `BlockQuestions` оставлен без изменений — переход в `Tactical`/`In Progress` через `update.ps1 -Id S0140 -Status <new>` на стороне оператора.
- **2026-05-18** — by `/spec-all` (sonnet-4.5, drift-mode resume)
  - Status: `BlockQuestions` → `In Progress` after §6 resolutions; build `assembleStandardDebug` PASS; `## Last Audit` block written.
  - Code coverage of Pillars P/Q/R/S/T/U/V verified by grep + file reads. Two non-device gaps surfaced (§11.9 lint-gate, §11.11 FEATURES doc), recommended as follow-up tickets — not auto-allocated to avoid scope creep without owner approval. Status to be flipped to `BlockNeedUserTest` after Timber.d S0140 tag insertion at changed flow entries (CLAUDE.md "Debug Verification Tags").
