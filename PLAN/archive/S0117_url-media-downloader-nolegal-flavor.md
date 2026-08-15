# Стратегическая спецификация: S0117 — `noLegal` flavor: site-specific extractors и album batch (sideload-only)

**Ticket:** S0117
**Status:** Implemented
**Priority:** 30
**Date:** 2026-05-08
**Tier:** 3 — Moderate
**Roadmap entry:** Phase 2 после S0116 — sideload-only расширение возможностей URL-загрузки за пределы того, что приемлемо для Google Play / Meta Horizon Store.
**Tactical spec:** `PLAN/S0117_url-media-downloader-nolegal-flavor/INDEX.md`

**Tactical plan:** `PLAN/S0117_url-media-downloader-nolegal-flavor/INDEX.md`
**Implemented date:** 2026-05-09

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

S0116 (`PLAN/S0116_url-media-downloader.md`) реализует generic-протокольный downloader в market-flavors: direct file, расширенный HTML sniffing с поддержкой `m3u8`/`mpd`, HLS/DASH → MP4 через MediaMuxer, универсальная WebView-авторизация. Этого достаточно для ≥80% реальных URL.

Оставшийся ≤20% — страницы, на которых:

- медиа отдаётся только через JavaScript-рендеринг или GraphQL-запросы, недоступные статическому HTML-сниффингу;
- один URL действительно соответствует множеству медиафайлов (карусель/альбом/постинг с N вложениями);
- подходящий extractor существует только в open-source проектах с copyleft-лицензией (NewPipe Extractor — GPL v3) или требует тяжёлый runtime (yt-dlp + Python).

В market-сборках это покрытие закрыть нельзя: либо нарушается store-compliance (site-specific reverse-engineered код, упоминания платформ в metadata), либо лицензионная граница (GPL viral в проприетарной сборке).

---

## 2. Цели

1. Расширить покрытие URL-загрузки за счёт site-specific экстракторов, опубликованных как open-source (NewPipe Extractor и эквиваленты), для пользователей, готовых установить sideload-сборку.
2. Поддержать album/multi-item URL: один URL → batch скачиваемых элементов, каждый пишется в ресурс отдельной операцией.
3. Сохранить лицензионную и compliance-границу с market-сборками: код, GPL-зависимости и упоминания платформ существуют только в `noLegal` sourceSet, не утекают в `standard`/`legacy`/`vr`/`vrUnlicensed`/`lite`/`photos` APK.
4. Не публиковать `noLegal` в Google Play и Meta Horizon Store; распространение — только sideload (прямая загрузка APK с собственного канала).
5. Использовать инфраструктуру S0116 без переписывания: тот же `LinkExtractionRegistry`, тот же downloader pipeline, та же WebView-авторизация и cookie storage.

**Non-goals:**

- Превратить `noLegal` в основной канал распространения.
- Поддержать сценарии, требующие обхода DRM или платных подписок без авторизации пользователя.
- Поддержать playlist/feed-batch (один URL → весь канал/лента) — это отдельная follow-up спека.
- Surface `noLegal` capability в публичных текстах любого APK (`standard`, `lite`, `photos`, `legacy`, `vr`, `vrUnlicensed`).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Экстракторы изолированы compile-time, не runtime gate. `BuildConfig.IS_NO_LEGAL_FLAVOR` существует, но используется только как safety belt, а не основной механизм исключения кода — primary isolation через Gradle sourceSet `src/noLegal/`.
2. Site-specific код не существует в `standard` APK даже в виде reflection-loaded jar; APK-инспекция (`unzip -l` на release artefact) не должна находить site-specific class names.
3. NewPipe Extractor подгружается фиксированной версией на момент сборки. Никакого remote config, динамической загрузки rules или server-managed extractor logic — это конфликтовало бы с Google Play политикой, применяемой к подписи app developer.
4. `noLegal` распространяется через тот же signing key, что и market-сборки, — пользователь должен видеть, что это «та же программа в расширенной комплектации».

### 3.2 Жёсткие ограничения

- **Flavor:** `noLegal` — единственный, добавляемый этой спекой; реализуется как новый productFlavor в `app_v2/build.gradle.kts` поверх существующих (`standard`, `lite`, `photos`, `legacy`, `vr`, `vrUnlicensed`).
- **API level:** `minSdk 26` — без зависимостей выше; legacy-эквивалент `noLegal` вне scope.
- **Distribution:** sideload-only, не публикуется в Google Play и Meta Horizon Store; production signing key — общий с market-сборками.
- **Dependencies:** NewPipe Extractor 0.24.0+ (GPL v3) подключается через `noLegalImplementation` Gradle scope; никакие GPL-артефакты не попадают в market сборки.
- **Source isolation:** site-specific extractor классы и album-batch coordinator живут в `src/noLegal/java/`; market-flavors не видят этот код на этапе компиляции.
- **License compliance:** добавление NewPipe Extractor требует размещения уведомления GPL v3 в About-экране `noLegal` сборки и публикацию ссылки на upstream исходники (стандартное GPL требование).
- **Совместимость данных:** новые настройки добавляются с безопасными значениями; `standard` backup, восстанавливаемый в `noLegal`, не падает (unknown fields игнорируются); `noLegal` backup, восстанавливаемый в `standard`, тоже не падает (lost site-specific entries).
- **Локализация:** EN/RU/UK — обязательно для всех новых строк.
- **Совместимость S0116:** `noLegal` не модифицирует и не дублирует столпы G/I/J/K/L из S0116; добавляет только столп H (site-resolver) и расширяет coordinator для batch.
- **Compliance wording:** в публичных метаданных `noLegal` APK (manifest, About, release notes) не упоминаются конкретные платформы поимённо — даже в sideload-канале. Реальный whitelist живёт в commit-сообщениях и внутреннем tactical планировании.
- **Правовая/политическая:** site-specific экстракторы ориентируются только на медиа, доступное самому пользователю; авторизованный доступ — через WebView-флоу из S0116.

---

## 4. Контекст текущей архитектуры

S0116 (Verified) добавил к S0003 расширенный generic pipeline и универсальную WebView-авторизацию. После Verified S0116 в реестре стратегий живут: `direct`, `html` (расширенный), streaming downloader (HLS/DASH → MP4). Cookie storage и WebView auth работают на уровне инфраструктуры и не привязаны к конкретным платформам.

S0117 переиспользует всю эту инфраструктуру и добавляет новую стратегию-надстройку (Site-Resolver) поверх существующего реестра. Site-Resolver проверяет URL первым; распознанный URL → site-specific extractor, использующий те же cookies из столпа K S0116; нераспознанный → стандартная generic-цепочка. Album-результат от extractor разворачивается в N последовательных операций `LinkDownloadWriter`.

В проекте уже есть прецедент flavor-isolation для дистрибуционных границ: `vr` и `vrUnlicensed` отличаются именно политикой канала распространения (Meta Horizon Store vs sideload), при этом большая часть кода общая. `noLegal` следует тому же паттерну.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп H — Site-Resolver и реестр site-specific экстракторов (`noLegal`-only).**

- Hilt multibinding `Set<SiteSpecificExtractor>`, классы живут в `src/noLegal/java/`. Market-сборки физически не содержат класса.
- Контракт: «URL → список форматов с метаданными ИЛИ структурированный исход (нужен вход / временная ошибка / возврат в generic-цепочку / album с N элементами / недоступно)».
- В `LinkExtractionRegistry` вставляется первой — до direct/html/streaming. «Не мой URL» возвращает управление в существующую generic-цепочку без изменения её поведения.
- Каждый extractor реализует один сайт; whitelist живёт в tactical planning, не в публичных текстах.

**Столп N — Album batch coordinator (`noLegal`-only).**

- Цель: развернуть album-исход site-specific extractor-а в последовательные операции скачивания.
- Требования: каждый элемент — отдельный вызов downloader pipeline (direct/streaming) с тем же выбором качества (столп J S0116) и cookie-инъекцией (столп K S0116); прогресс отображается как «элемент i из N»; при ошибке одного элемента — продолжить с остальными, в финальном тосте/диалоге показать сводку «успешно: X, не удалось: Y, причины: …».
- Reuse: переиспользует существующий `LinkAutoDownloadProgressDialog` (S0003 Столп F) с расширенным state «batch прогресс».

**Столп O — License compliance UI (`noLegal`-only).**

- Цель: выполнить требования GPL v3 для встроенной NewPipe Extractor (полный текст лицензии в приложении + ссылка на upstream исходники).
- Размещение: новая секция в About-экране, видна только при `BuildConfig.IS_NO_LEGAL_FLAVOR == true`.

### 5.2 Потоки данных и событий

URL (S0003 канал) → Site-Resolver (столп H) → распознан? → site-specific extractor возвращает один из исходов:

- direct media URL → существующий downloader pipeline S0116 (direct/streaming) → один файл в ресурс
- streaming manifest URL → существующий streaming downloader S0116 (Media3 + MediaMuxer) → один MP4 в ресурс
- album с N элементами → Album batch coordinator (столп N) → N последовательных операций
- нужен вход → существующий WebView auth flow S0116 (столп L) → повторный запуск extractor-а
- возврат в generic-цепочку → стандартная цепочка S0116 (direct/html/streaming) — без специальной обработки
- недоступно → terminal error для пользователя

Нераспознанный URL → стандартная generic-цепочка S0116 (как будто `noLegal` flavor нет).

### 5.3 Точки расширяемости

- Добавление нового site-extractor — один новый класс в `src/noLegal/java/` + Hilt multibinding entry; изменения в UI и публичных текстах не требуются.
- NewPipe Extractor может быть заменён на форк или аналог — изменение зависимости в `noLegalImplementation` без переписывания столпа H.
- Album batch coordinator переиспользуется любой будущей multi-item стратегией (например, playlist-source если такая когда-то появится).

---

## 6. Открытые вопросы / Research items

1. **Конкретная версия и форк NewPipe Extractor**
   - **Решение:** MVP фиксируется на upstream JitPack dependency `com.github.TeamNewPipe:NewPipeExtractor:v0.24.0`.
   - **Дальше:** обновление версии допускается отдельной follow-up задачей при churn-е upstream.
   - **Статус:** Closed

2. **License notice placement**
   - **Решение:** GPL notice и upstream source links живут только на existing Open Source Licenses экране `noLegal` сборки; first-run диалог не вводится.
   - **Статус:** Closed

3. **Внутренний whitelist платформ MVP**
   - Не публикуется в спеке; решается в tactical planning.
   - **Статус:** Closed для strategic уровня (определяется в `/spec-tech`)

4. **Распространение sideload APK**
   - Прямая загрузка с собственного сайта vs F-Droid vs GitHub Releases.
   - **Решение:** канал распространения не блокирует текущую implementation phase и выносится из объёма этого прохода.
   - **Статус:** Deferred

5. **Album batch error policy**
   - **Решение:** продолжать остальные элементы, а в конце показывать сводку успехов/ошибок с причинами.
   - **Статус:** Closed

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| GPL v3 viral conflict — site-specific код или классы NewPipe попадают в market APK по ошибке Gradle | Средняя | License violation, потенциальный strike в Google Play | Compile-time isolation через sourceSet; CI-проверка `apkanalyzer` на release artefacts всех market flavors; lint-rule запрещает import `org.schabi.newpipe.*` вне `src/noLegal/` |
| Site-specific extractor ломается при изменении платформы | Высокая | Функция перестаёт работать для конкретного источника | NewPipe Extractor поднимается до latest stable при каждой `noLegal` сборке; пользовательские ошибки нейтральны; market builds не подвержены |
| Sideload distribution path не покрывает аудиторию | Низкая | `noLegal` собирает мало пользователей, ROI низкий | Это ожидаемо — `standard` остаётся primary каналом; `noLegal` — для power-users |
| Backup, созданный в `noLegal`, не восстанавливается в `standard` | Низкая | Потеря пользовательских настроек при flavor migration | Backup format остаётся forward/backward compatible (unknown fields игнорируются на read) |
| GPL compliance — отсутствие upstream sources link в About экране | Средняя | License violation, потенциальный legal risk | Столп O (license compliance UI) — обязательное требование MVP; CI-проверка на присутствие GPL notice в `noLegal` APK |
| Extractor требует CAPTCHA/2FA для платформы — пользователь не может пройти через WebView S0116 | Средняя | Часть платформ недоступна даже в `noLegal` | WebView auth S0116 уже поддерживает интерактивный CAPTCHA-flow; ограничение принято как архитектурное |
| Album из N=100+ элементов перегружает downloader pipeline | Низкая | Сборка занимает часы, переполнение кэша | Batch coordinator имеет hard-cap (например, ≤ 30 элементов на запуск); сверх-cap → пользовательский диалог «начать только первые N?» |

---

## 8. Влияние на пользователя (docs/FEATURES)

`docs/FEATURES.md` (и `_RU` / `_UK`) **не упоминают `noLegal` capability**. Это намеренно — публичные тексты `standard` APK остаются нейтральными по платформам и не заявляют о возможностях, которых там нет.

Внутренний release-notes для `noLegal` сборки — допустим, в `temp/` каталоге сборок sideload-канала, без поимённых упоминаний платформ.

---

## 9. Архитектурные решения (ADR)

**ADR-1: `noLegal` — отдельный productFlavor, не runtime feature flag**

- **Решение:** новый `noLegal { ... }` блок в `productFlavors` с `BuildConfig.IS_NO_LEGAL_FLAVOR = true`, отдельный `applicationIdSuffix = ".nolegal"`, отдельный `versionNameSuffix = "-NoLegal"`. Site-specific код живёт в `src/noLegal/java/`, не в `src/main/`.
- **Альтернативы:** runtime feature flag (не даёт license isolation); общий код с if-проверками на `IS_NO_LEGAL_FLAVOR` (классы NewPipe попадают в market APK).
- **Почему:** проект уже использует flavor-isolation для дистрибуционных границ (`vr` ↔ `vrUnlicensed`); compile-time isolation проверяема статически; APK security review market-сборок не находит restricted-use кода.

**ADR-2: NewPipe Extractor — фиксированная версия, не remote-loaded**

- **Решение:** зависимость подключается через `noLegalImplementation("com.github.TeamNewPipe:NewPipeExtractor:0.24.0")` (конкретная версия на момент сборки). Никакого dynamic loading rules с сервера, никакого extractor logic, обновляемого через Firebase Remote Config или подобное.
- **Альтернативы:** server-managed extractor rules (как у некоторых streaming front-end приложений); dynamic JAR-loading из Downloads.
- **Почему:** Google Play политика на динамический исполняемый код применяется к app-developer signing key, даже если конкретная сборка не публикуется в Play; единый signing key между `standard` (Play) и `noLegal` (sideload) делает динамическую загрузку рискованной для всего портфолио developer-а.

**ADR-3: Reuse инфраструктуры S0116, не дублирование**

- **Решение:** `noLegal` использует существующий `LinkExtractionRegistry`, `LinkAutoDownloadCoordinator`, `LinkDownloadWriter`, столпы G/I/J/K/L из S0116 как есть; добавляет только столп H (Site-Resolver), столп N (Album batch) и столп O (license UI).
- **Альтернативы:** отдельный pipeline для site-specific URL-ов; форк `LinkAutoDownloadCoordinator` под batch-сценарий.
- **Почему:** меньше кода в поддержке; site-specific extractors получают cookie-injection, quality policy и WebView auth «бесплатно»; consistency пользовательского UX между market и sideload сборками.

---

## 10. Связи с другими спеками

- **S0116** (`url-media-downloader`, Verified) — обязательный предшественник, уже закрытый для старта S0117.
- **S0003** (`link-receive-download`, Verified) — базовая инфраструктура auto-download.
- Зависимостей, блокирующих strategic approval, нет, но tactical и implementation блокируются S0116 Verified.

---

## 11. Критерии готовности (strategic-level)

1. `noLegal` flavor собирается и устанавливается отдельно от market-flavors на одно и то же устройство (другой `applicationIdSuffix`).
2. Один распознанный single-item URL в `noLegal` → один файл в ресурс через site-specific extractor + S0116 downloader pipeline.
3. Один распознанный album URL в `noLegal` → batch скачивания: каждый элемент пишется отдельной операцией, итоговый отчёт показывает успехи/неудачи.
4. Нераспознанный URL в `noLegal` → стандартная generic-цепочка S0116 без изменений в поведении.
5. APK-инспекция release-сборки `standard` (`apkanalyzer` или `unzip -l`) не находит классов из `org.schabi.newpipe.*` или site-specific extractor packages.
6. About-экран `noLegal` сборки содержит GPL v3 notice и ссылку на upstream NewPipe Extractor исходники.
7. Cookie storage и WebView auth (столпы K и L S0116) работают для site-specific extractors без дополнительной конфигурации.
8. Все новые строки присутствуют в EN/RU/UK; CI-проверка `check_strings_localized.ps1 -KeyPrefix s0117_` возвращает 0.
9. `standard` backup, восстановленный в `noLegal`, и наоборот — не падают; unknown fields игнорируются.
10. Lint-rule запрещает импорты `org.schabi.newpipe.*` вне `src/noLegal/`; CI красный при нарушении.

---

## 12. Ссылка на тактическую спецификацию

Текущий tactical plan: `PLAN/S0117_url-media-downloader-nolegal-flavor/INDEX.md`.

**Оставшиеся вне текущего implementation pass вопросы:**

- Канал распространения sideload APK (§6.4) — отдельное release/distribution решение.

---

## Revision History

- **2026-05-08** — initial strategic draft (claude-opus-4-7, derived from S0116 §13 capability matrix `noLegal` column and ADR-5)
- **2026-05-09** — tactical plan created, implementation defaults fixed (`v0.24.0`, existing license screen, continue-on-error batch)
- **2026-05-09** — implementation completed; `noLegal` flavor, site resolver, batch UX, GPL notice, catalog sync, and isolation validation landed
