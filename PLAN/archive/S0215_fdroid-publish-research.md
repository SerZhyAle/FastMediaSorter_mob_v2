# Стратегическая спецификация: S0215 — Публикация FastMediaSorter в IzzyOnDroid (Фаза 1) и F-Droid main (Фаза 2 — research)

**Ticket:** S0215
**Status:** Archived
**Priority:** 50
**Date:** 2026-05-15
**Tier:** 4 — Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc — запрос 2026-05-15: «выяснить как опубликовать приложение в F-Store (искать в интернете)». **Уточнено владельцем 2026-05-15 20:42:** целевые каналы — **главный F-Droid (`f-droid.org`) и IzzyOnDroid (`apt.izzysoft.de/fdroid/repo`)**, оба. Главный F-Droid — строгие правила (FLOSS-only, запрет Google Play Services / ML Kit / MSAL / Dropbox SDK). IzzyOnDroid — менее строгий, допускает proprietary SDK через Anti-Features флаги (`NonFreeDep`, `NonFreeNet`), но требует FLOSS-лицензию самого приложения.
**Tactical spec:** `PLAN/S0215_fdroid-publish-research/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Владелец хочет, чтобы FastMediaSorter появился в F-Droid-экосистеме: главный F-Droid (`f-droid.org`) **и** IzzyOnDroid (`apt.izzysoft.de/fdroid/repo`). Дополнительное требование владельца: «опубликовать STANDARD с сохранением всех функций — Cloud, Cast, Wear, ML Kit, In-App Review — раз они прошли в Google Play». Это требование напрямую конфликтует с Inclusion Policy главного F-Droid, которая буквально запрещает proprietary библиотеки в APK (`play-services-*`, ML Kit, Firebase, Crashlytics, Microsoft MSAL, Dropbox SDK). IzzyOnDroid, в отличие от главного F-Droid, **допускает** проприетарные SDK при условии FLOSS-лицензии самого приложения и пометок Anti-Features (`NonFreeDep`, `NonFreeNet`).

Реальность такова: один артефакт не покрывает оба канала с сохранением функций. Возможен только двухэтапный путь — IzzyOnDroid принимает STANDARD как есть быстро (Фаза 1, минимум работы), main F-Droid требует осознанного компромисса по функциям (Фаза 2 — отдельная research-задача, в которую идём только если владелец готов на отключение Cloud/Cast/Wear/ML/Review для FOSS-flavor).

Текущая инфраструктура проекта (build.gradle.kts, релизный pipeline, worktree `FastMediaSorter_release`, signing key) к F-Droid-каналам сейчас не подключена, fastlane-метаданные отсутствуют, LICENSE-файл и `topics` репозитория под FOSS-каталоги не проверены.

---

## 2. Цели

**Фаза 1 — IzzyOnDroid (STANDARD as-is, минимум работы):**

1. STANDARD-flavor release APK опубликован в репозитории IzzyOnDroid (`apt.izzysoft.de/fdroid/repo`) с корректными метаданными и Anti-Features флагами (`NonFreeDep` — proprietary Google/MS/Dropbox SDK; `NonFreeNet` — зависимость от non-free сервисов: Google Drive, OneDrive, Dropbox, Cast, ML Kit Translate).
2. Карточка приложения в IzzyOnDroid содержит: иконку, скриншоты, EN/RU/UK описания (`short` ≤ 80 chars; `full` ≤ 4000 chars), changelogs привязанные к `versionCode`, ссылку на GitHub.
3. Auto-update через IzzyOnDroid работает: signing fingerprint между релизами стабилен; recipe в IzzyOnDroid `/repo` отслеживает новые GitHub-теги нашего репозитория.
4. LICENSE репозитория приведён к FLOSS-совместимому виду (Apache 2.0 / MIT / GPL — на выбор владельца) — это требование IzzyOnDroid даже при наличии proprietary SDK в зависимостях.
5. README репозитория содержит бейдж IzzyOnDroid с deep-link на страницу приложения.

**Фаза 2 — main F-Droid (отложенная research-фаза):**

1. Зафиксированный документ-ресёрч: исчерпывающий перечень того, какие функции STANDARD теряются при сборке без proprietary SDK (Cloud-провайдеры Drive/OneDrive/Dropbox, Cast, Wear OS companion, ML Kit Translation, In-App Review).
2. Чёткое решение владельца «идём в main F-Droid с компромиссом / не идём» — принимается после оценки фазы 2 ресёрча и до начала любого рефакторинга.
3. Если решение «идём» — план рефакторинга: вынос proprietary подсистем за интерфейсы, новый `fdroid`-flavor с FOSS-replacement (Tesseract вместо ML Kit, WebDAV/Nextcloud вместо Drive/OneDrive/Dropbox), без поломки `standard`/`vr`/`noLegal`-каналов.

**Non-goals:**

- Включение Wear OS companion в F-Droid main repo — `play-services-wearable` запрещён, FOSS-альтернатива не разрабатывается; companion в этой спеке не поддерживается ни в одной фазе.
- Публикация `noLegal`-flavor в любой из F-Droid каналов — категорически вне объёма (легальные ограничения, Chaquopy + yt-dlp).
- Поддержка собственного F-Droid-репозитория владельца (self-hosted fdroidserver) — только публичные каналы IzzyOnDroid и `f-droid.org`.
- Перевод фазы 1 в полный FOSS-flavor в обход IzzyOnDroid Anti-Features — фаза 1 принципиально использует существующий STANDARD APK.
- Замена signing key между фазами 1 и 2 — это сломает auto-update; один и тот же ключ обслуживает все каналы.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. По возможности — единый кодовый базис, без форка проекта; F-Droid-сборка живёт как отдельный flavor в том же репозитории.
2. Минимизировать потерю функций в F-Droid-сборке — сохранить максимум возможностей через FOSS-замены.
3. Понимать, какие функции реально нельзя заменить FOSS-аналогом — чтобы пользователю F-Droid-версии было честно объявлено, что отсутствует.
4. Текущая модель распространения (Google Play, Meta Horizon, GitHub Store — см. S0214) не должна пострадать от появления F-Droid-канала.
5. Использовать уже существующий flavor-isolation паттерн (`dev/FLAVOR_DEVELOPMENT_RULES.md`) — не плодить параллельные подходы.

### 3.2 Жёсткие ограничения

- **Flavor:** для F-Droid вводится отдельный flavor (рабочее имя `fdroid`) либо расширяется один из существующих (`lite` / `photos` — см. §6 item 2). Реализация строго следует `dev/FLAVOR_DEVELOPMENT_RULES.md`: интерфейсы в `src/main/java/`, FOSS-реализации в `src/<flavor>/java/`, Hilt-модули per-flavor. Любые `BuildConfig.SUPPORT_*` / `BuildConfig.IS_*` гейты внутри `src/main/java/` запрещены (CLAUDE.md Rule 15).
- **Лицензия проекта:** в `LICENSE` репозитория должна быть указана единственная FLOSS-лицензия, одобренная F-Droid (GPL/AGPL/Apache/MIT/BSD — список Inclusion Policy). Текущее состояние лицензионного файла — research item §6 item 4.
- **Запрещённые зависимости (hard block в F-Droid main):** `com.google.android.gms:play-services-*`, `com.google.android.play:review-ktx`, `com.google.mlkit:*`, `com.microsoft.identity.client:msal`, `com.dropbox.core:dropbox-core-sdk`, `com.google.android.gms:play-services-cast-framework`, Firebase, Crashlytics, любые tracking/analytics SDK. Все они должны отсутствовать в скомпилированном APK F-Droid-flavor.
- **Application ID:** F-Droid требует уникальный applicationId, владелец которого совпадает с заявленным upstream. Используется отдельный суффикс (например, `.fdroid`), чтобы F-Droid-сборка ставилась рядом с Google Play версией без конфликтов.
- **API level:** F-Droid строит из исходников через собственный CI; необходимо, чтобы `gradle` и Android Gradle Plugin версии, используемые в проекте, совпадали с тем, что доступно в build-окружении F-Droid (`fdroidserver`). Совместимость — research item §6 item 5.
- **Reproducible builds:** не обязательны, но крайне желательны. Если включаются — `signingConfig` / `applicationId` / `versionCode` должны быть детерминированы; ключи подписи проверяются через `AllowedAPKSigningKeys`.
- **Wear OS:** в F-Droid-flavor companion-приложение для часов **не поддерживается** — `play-services-wearable` запрещён в F-Droid main. Wear-модуль остаётся в `standard`/`legacy`/`noLegal`.
- **Локализация:** F-Droid использует fastlane-структуру с локалями `en-US`, `ru-RU`, `uk-UA` (или `en/ru/uk` — точная нотация — research item §6 item 6). EN/RU/UK обязательны, что согласуется с текущим `androidResources.localeFilters` проекта.
- **Доступность и UI-копия:** все пользовательские строки в F-Droid-flavor подчиняются `docs/COMMUNICATION_POLICY.md`. Метаданные F-Droid (`short_description.txt`, `full_description.txt`) проходят тон-чеклист §6 политики перед коммитом.
- **Anti-Features:** если по итогам ресёрча выявится, что часть FOSS-замен зависит от non-free сетевого сервиса (например, FOSS cloud SDK всё равно требует non-free serverside) — приложение помечается соответствующим F-Droid Anti-Feature флагом (`NonFreeNet`, `NonFreeDep` — research item §6 item 7).
- **Лицензионный режим `noLegal`:** `noLegal`-функционал (Chaquopy + yt-dlp + NewPipeExtractor + DTS decoder) в F-Droid-flavor отсутствует целиком. Никакие упоминания `noLegal`-only возможностей не попадают в публичные метаданные F-Droid.

---

## 4. Контекст текущей архитектуры

Проект собирает несколько flavor через единый `app_v2/build.gradle.kts` с матрицей `standard` / `noLegal` / `lite` / `photos` / `legacy` / `vr` / `vrUnlicensed`. Все flavor подтягивают общий блок зависимостей, в который входят проприетарные SDK Google (Play Services, ML Kit, Cast), Microsoft (MSAL для OneDrive) и Dropbox. Часть из этих зависимостей формально ограничена per-flavor через `"standardImplementation"(..)` / `"noLegalImplementation"(..)`, но базовые `implementation(..)` строки тянут Google-зависимости во все flavor, включая `lite` и `photos`. Это видно по присутствию `play-services-auth`, `play-services-wearable`, `play-services-cast-framework`, `mlkit:translate` и `msal` в общем `dependencies` блоке без per-flavor конфигуратора.

Архитектурно проект уже знает паттерн flavor-isolation (см. `dev/FLAVOR_DEVELOPMENT_RULES.md`): интерфейс в `src/main/`, FLOSS-реализация в одном source-set, proprietary в другом. Однако для целого ряда подсистем — Cloud-провайдеры (Google Drive, OneDrive, Dropbox), Translation/OCR (ML Kit), Cast, Wear, In-App Review — интерфейсного слоя нет: код напрямую вызывает SDK из `src/main/`. Соответственно, прежде чем F-Droid-flavor станет возможным, нужно выделить эти подсистемы за интерфейсы и обеспечить FLOSS / No-Op альтернативу.

Корень проблемы: главный F-Droid repo принципиально запрещает proprietary зависимости в APK. Текущая архитектура не разделяет «функцию» и «реализацию через конкретный proprietary SDK», поэтому массовая часть features не строится без Google SDK.

---

## 5. Предлагаемый подход

Подход разбивается на четыре параллельных столпа: ревизия зависимостей и FOSS-замен, рефакторинг подсистем за интерфейсы, выделение `fdroid`-flavor и оформление submission-пакета. Все четыре остаются на стратегическом уровне — конкретные классы, пути и Hilt-модули адресуются в `/spec-tech`.

### 5.1 Основные столпы / модули

**Столп 1 — Audit зависимостей и FOSS-замены:**

- Полный inventory текущих proprietary библиотек, по каждой — статус «hard block / FOSS-замена существует / отсутствует».
- Для каждого hard block — решение: убрать (потеря функции), заменить (FOSS-аналог), пометить Anti-Feature (если FOSS-замена частична).
- Кандидаты на замену (предварительные, уточняются в §6):
  - **Translation/OCR:** Tesseract уже подключён — потенциально полное покрытие OCR без ML Kit. Translation — FOSS-альтернативы (LibreTranslate API клиент / offline argos-translate биндинг) — research.
  - **Cloud (Google Drive, OneDrive, Dropbox):** официальные SDK заменить нельзя; вариант — переключиться на стандартный OAuth + REST-клиент на OkHttp; или отключить cloud целиком в F-Droid-flavor и пометить как удалённую функцию.
  - **Cast:** альтернатив для Google Cast в FLOSS-мире нет; функция отключается в F-Droid-flavor.
  - **Wear OS companion:** `play-services-wearable` без FOSS-альтернативы; companion отключается, на F-Droid-flavor не поставляется.
  - **In-App Review (Play):** Google-only; функция отключается в F-Droid-flavor.
- DTS / FFmpeg-AAR: легальность FOSS-сборки с DTS требует отдельной оценки (DTS Patent vs GPL FFmpeg) — research item.

**Столп 2 — Рефакторинг подсистем за интерфейсы:**

- Сейчас Cloud / Translation / OCR / Cast / Wear / Review вызываются из `src/main/` напрямую. Для F-Droid нужно превратить каждую такую подсистему в pluggable интерфейс: `CloudProviderRegistry`, `TranslationEngine`, `OcrEngine`, `CastController`, `WearBridge`, `InAppReviewLauncher`.
- В `src/main/` — интерфейс и default No-Op / Stub-реализация.
- В `src/standard/`, `src/legacy/`, `src/vr/`, `src/vrUnlicensed/`, `src/noLegal/` — реальные реализации, использующие proprietary SDK.
- В `src/fdroid/` — реализации на FOSS-стеке (Tesseract-only OCR, OkHttp-REST для cloud либо No-Op, и т. д.).
- Hilt-модули per-flavor биндят правильную реализацию.
- Объём рефакторинга — большой и заведомо растянутый. Стратегическая спека фиксирует объём, тактическая декомпозирует на фазы.

**Столп 3 — Выделение `fdroid`-flavor (или extension существующего):**

- Новый flavor `fdroid` с `applicationIdSuffix = ".fdroid"`, `versionNameSuffix = "-FDroid"`. CLOUD/MIC/CAST/WEAR — отключены через BuildConfig (но не через гейты в `src/main`! — реальная разводка через Hilt и source-set, как требует `dev/FLAVOR_DEVELOPMENT_RULES.md`).
- Альтернатива: расширить `lite` / `photos` так, чтобы они стали FOSS-compatible. Проблема — `lite` сейчас тоже линкуется с Google SDK через общий `dependencies` блок; расширение потребует разделения по `"liteImplementation"(..)` всех Google-зависимостей. Целевое решение — research item §6 item 2.
- Native: `disableNativeBuild()` (как в `lite`/`photos`/`legacy`) — F-Droid-flavor без OpenXR и custom FFmpeg-AAR.

**Столп 4 — Submission-пакет F-Droid:**

- Метаданные fastlane в источнике: `fastlane/metadata/android/<locale>/short_description.txt` (≤80 chars), `full_description.txt` (≤4000 chars), `images/icon.png`, `images/phoneScreenshots/*.png`, `images/featureGraphic.png`, `changelogs/<versionCode>.txt` (≤500 chars). Локали — `en-US`, `ru-RU`, `uk-UA` (точная нотация — §6 item 6). Метаданные хранятся в локализованных папках в самом репозитории (вариант через `src/fdroid/fastlane/metadata/` тоже допустим — §6 item 8).
- Build recipe в `fdroiddata` GitLab-репозитории: `metadata/com.sza.fastmediasorter.fdroid.yml` с указанием `commit`, `subdir=app_v2`, `gradle=fdroid`, `versionName`, `versionCode`. Recipe пишется и тестируется через `fdroidserver` контейнер, затем — merge request в `fdroid/fdroiddata`.
- Reproducible builds: опционально. Если включаются — `AllowedAPKSigningKeys` извлекается из подписанного APK и фиксируется в recipe.
- LICENSE file: единая корневая FLOSS-лицензия. Если в проекте есть `noLegal`-only код под GPL-зависимостью, он должен быть исключён из `fdroid`-flavor source-set и не вкомпилирован в APK.

### 5.2 Потоки данных и событий

Submission flow (одноразовый, при первом включении в F-Droid): локально полностью FOSS-flavor APK → fastlane-метаданные в репозитории → fork `fdroid/fdroiddata` → write metadata YAML → local test build через `fdroidserver` Docker → merge request → review F-Droid maintainers → принятие → автоматический build на инфраструктуре F-Droid из публичного git tag/commit → подпись build-сервером F-Droid (либо verification против developer-signed APK, если включён reproducible-build режим) → публикация в репозиторий F-Droid.

Update flow (на каждом релизе): git push нового тега `v<versionName>` в публичный репозиторий → F-Droid CI обнаруживает новый тег → собирает по существующей recipe → публикует обновление. Никаких ручных действий со стороны разработчика на каждом релизе после принятия первой версии не требуется (при условии корректно настроенной recipe и стабильности `versionCode`/`versionName` формата проекта).

### 5.3 Точки расширяемости

- **Pluggable cloud provider registry:** интерфейс, который позволяет `standard`-flavor биндить три реальных провайдера (Drive, OneDrive, Dropbox), а `fdroid`-flavor — либо WebDAV/Nextcloud FOSS-биндинг, либо пустой реестр (cloud секции UI прячутся).
- **Pluggable translation engine:** `TranslationEngine` с реализациями `MlKitTranslationEngine` (standard) и `LibreTranslateEngine` или `NoOpTranslationEngine` (fdroid).
- **Pluggable OCR engine:** Tesseract уже покрывает FOSS-кейс; `standard` дополнительно подключает ML Kit (для not-Latin скриптов). В `fdroid`-flavor — только Tesseract.
- **Pluggable cast controller:** `standard` биндит Google Cast; `fdroid` биндит No-Op.
- **Pluggable wear bridge:** `standard` биндит `play-services-wearable`; `fdroid` биндит No-Op.
- **Pluggable in-app review launcher:** `standard` биндит Play Review API; `fdroid` биндит «открыть страницу F-Droid в браузере».
- **Flavor-isolation note:** все вышеперечисленные интерфейсы реализуют паттерн из `dev/FLAVOR_DEVELOPMENT_RULES.md` — interface в `src/main/`, default No-Op в `src/main/` (или `src/standard/`), реальные в per-flavor source-sets, Hilt biniding per-flavor. `BuildConfig`-гейты в `src/main/` под F-Droid не вводятся.
- **F-Droid Anti-Features (расширяемость метаданных):** recipe в `fdroiddata` может декларировать `AntiFeatures: NonFreeNet, NonFreeDep` per-version, если конкретная функция (например, опциональный LibreTranslate API) зависит от non-free сервиса. Структура recipe — открытая, добавление флагов не ломает inclusion.

---

## 6. Открытые вопросы / Research items

1. **«F-Store» = главный F-Droid или IzzyOnDroid? — RESOLVED 2026-05-15**
   - **Вопрос:** какой именно репозиторий имелся в виду в запросе владельца?
   - **Ответ владельца:** оба — главный `f-droid.org` И IzzyOnDroid (`apt.izzysoft.de/fdroid/repo`).
   - **Следствие:** стратегия требует решения по последовательности (см. новый item 12 ниже) — один общий FOSS-flavor (более строгий — main F-Droid), либо двухэтапно (IzzyOnDroid first через Anti-Features на менее строгом flavor, F-Droid main позже через полноценный FOSS-flavor).
   - **Статус:** Resolved.

2. **Где живёт F-Droid-flavor: новый `fdroid` или расширение `lite`/`photos`?**
   - **Вопрос:** вводить отдельный `fdroid` flavor или сделать существующий FOSS-совместимым?
   - **Варианты:** A — новый dedicated `fdroid` flavor (минимум сюрпризов, чистая семантика). B — `lite` становится FOSS-compatible — но тогда теряются Cast и другие текущие функции `lite`, меняется обратная совместимость для пользователей Google Play `.lite`. C — `photos` — то же возражение.
   - **Нужно выяснить:** есть ли практическая ценность объединения; цена потери Cast в `lite` для Google Play аудитории.
   - **Статус:** Open.

3. **DTS / custom FFmpeg AAR — допустим в F-Droid?**
   - **Вопрос:** AAR `fms-ffmpeg-dts.aar` собран из исходников media3 1.2.1 + FFmpeg; включает DTS-декодер. FFmpeg — LGPL/GPL (build-flag), DTS — патент-обременённый кодек.
   - **Варианты:** A — отключить DTS в `fdroid` flavor (`ENABLE_DTS_DECODER=false`, как в `lite`); B — выяснить, можно ли F-Droid собирать FFmpeg из исходников на своей CI; C — выяснить, не блокирует ли DTS submission даже при наличии build-from-source pipeline (патент-проблема в США).
   - **Нужно выяснить:** позицию F-Droid по патентно-обременённым декодерам и реальные прецеденты (есть ли в F-Droid приложения с DTS-поддержкой). DTS не критичен для `fdroid` — отключение приемлемо.
   - **Статус:** Open.

4. **Текущий LICENSE файл проекта.**
   - **Вопрос:** какая лицензия указана в `LICENSE` репозитория, и совместима ли она со списком F-Droid Inclusion Policy?
   - **Варианты:** A — уже FLOSS-совместимая (GPL/Apache/MIT/BSD). B — отсутствует / proprietary — требуется добавить FLOSS-лицензию.
   - **Нужно выяснить:** проверить содержимое `LICENSE` файла; если отсутствует — выбрать (рекомендуется Apache 2.0 как уже используется в подавляющем большинстве зависимостей).
   - **Статус:** Open.

5. **Совместимость AGP / Gradle / SDK с `fdroidserver` build-окружением.**
   - **Вопрос:** F-Droid собирает приложения на своей CI с фиксированным набором версий Android SDK / NDK / Gradle. Совместима ли текущая комбинация (`AGP 10+`, `compileSdk 35`, `NDK r27c`)?
   - **Варианты:** проверить документацию `fdroidserver` и список поддерживаемых версий.
   - **Нужно выяснить:** не упирается ли проект в свежие AGP/Gradle, которые F-Droid CI ещё не поддерживает; в этом случае first inclusion может быть отложена.
   - **Статус:** Open.

6. **Нотация локалей в fastlane-структуре.**
   - **Вопрос:** F-Droid принимает `en-US`/`ru-RU`/`uk-UA` или `en`/`ru`/`uk`?
   - **Варианты:** A — `<lang>-<region>` (`en-US`). B — `<lang>` (`en`). Согласно документации F-Droid, поддерживаются оба, но рекомендация — следовать ISO BCP47.
   - **Нужно выяснить:** какая нотация лучше для совпадения с автоматической локализацией в клиенте F-Droid; согласовать с текущим `androidResources.localeFilters` (`en`, `ru`, `uk`).
   - **Статус:** Open.

7. **Какие функции получают F-Droid Anti-Features флаги?**
   - **Вопрос:** если в `fdroid`-flavor остаётся, например, опциональная интеграция с LibreTranslate API (онлайн-сервис) — ставить `NonFreeNet`?
   - **Варианты:** A — все опциональные онлайн-функции помечены `NonFreeNet`. B — функции, требующие non-free серверной стороны, удаляются совсем. C — гибрид: критичные оставляем под флагом, эстетические убираем.
   - **Нужно выяснить:** какие функции `standard`-flavor сохранить в `fdroid` через FOSS-стек, и какие из них нуждаются в network-сервисах.
   - **Статус:** Open.

8. **Расположение fastlane-метаданных: корень или `src/fdroid/fastlane/`?**
   - **Вопрос:** хранить fastlane-метаданные в `<root>/fastlane/metadata/android/<locale>/` (общие для всего проекта) или в `src/fdroid/fastlane/metadata/android/<locale>/` (per-flavor)?
   - **Варианты:** A — `src/fdroid/fastlane/` — метаданные F-Droid-specific, тексты можно адаптировать под FOSS-аудиторию. B — корень — метаданные общие, переиспользуются для Play Store и других каналов.
   - **Нужно выяснить:** насколько full description для F-Droid отличается от Google Play версии; нужны ли разные скриншоты.
   - **Статус:** Open.

9. **Cloud-провайдеры в F-Droid-flavor — есть FOSS-альтернатива?**
   - **Вопрос:** возможно ли заменить Google Drive / OneDrive / Dropbox SDK на FOSS-стек (OAuth + REST на OkHttp)?
   - **Варианты:** A — да, реализовать вручную (объём работы — велик, но реализуемо). B — отключить cloud целиком в `fdroid` — оставить только WebDAV/Nextcloud в качестве FOSS-облака. C — частично (только Google Drive через OAuth+REST, остальные отключить).
   - **Нужно выяснить:** оценка трудозатрат на in-house реализацию vs ценность cloud для F-Droid-аудитории (которая исторически предпочитает self-hosted решения вроде Nextcloud).
   - **Статус:** Open.

10. **Translation engine — FOSS-альтернатива ML Kit Translate.**
    - **Вопрос:** есть ли практичный FOSS-движок offline-перевода, который можно встроить вместо ML Kit?
    - **Варианты:** A — `argos-translate` Android-биндинг (если существует и поддерживается). B — `LibreTranslate` HTTP-клиент к внешнему серверу (требует `NonFreeNet` anti-feature). C — `Apertium` Android-биндинг. D — отключить translation в `fdroid`-flavor совсем.
    - **Нужно выяснить:** наличие maintained Android-биндингов; размер языковых моделей; качество перевода на ключевых парах (en↔ru, en↔uk).
    - **Статус:** Open.

11. **Wear OS companion в F-Droid — единственный путь публикации?**
    - **Вопрос:** Wear-модуль зависит от `play-services-wearable`. Можно ли публиковать companion отдельным F-Droid-пакетом или только отключать совсем?
    - **Варианты:** A — отключить совсем в `fdroid`-flavor (рекомендуется). B — публиковать companion как отдельный F-Droid-пакет, использующий не Google Data Layer, а собственный BT/Wi-Fi протокол (огромный объём работы).
    - **Нужно выяснить:** позицию F-Droid по `play-services-wearable` (формально — proprietary, hard block); приоритет Wear-функции для F-Droid-аудитории.
    - **Статус:** Open.

12. **Стратегия публикации: один FOSS-flavor или двухэтапно? — RESOLVED 2026-05-15**
    - **Вопрос:** один flavor покрывает оба канала (main F-Droid + IzzyOnDroid) или две отдельные стратегии?
    - **Ответ владельца:** **Вариант B — двухэтапно**: Фаза 1 IzzyOnDroid с STANDARD as-is через Anti-Features, Фаза 2 — main F-Droid позже (отдельный research-этап).
    - **Дополнительное требование:** в STANDARD-flavor функции **не отключаются** ради F-Droid (Cloud, Cast, Wear, ML Kit, In-App Review сохраняются — «они уже прошли в Google Play»). Это требование автоматически делает фазу 2 либо невозможной без компромиссов, либо отложенной до пересмотра — см. item 13.
    - **Статус:** Resolved.

13. **Объём потерь функций для main F-Droid (Фаза 2).**
    - **Вопрос:** какие функции STANDARD теряются ради публикации в `f-droid.org` main repo?
    - **Hard блокеры (proprietary, FOSS-альтернативы нет):** Google Cast, Wear OS companion (`play-services-wearable`), Play In-App Review, Google Play `review-ktx`.
    - **Hard блокеры (proprietary SDK, FOSS-альтернативы возможны):** Google Drive SDK → OAuth+REST на OkHttp; OneDrive MSAL → OAuth+REST; Dropbox SDK → OAuth+REST; ML Kit Translate → LibreTranslate / argos-translate / отключение; ML Kit OCR/LangID → только Tesseract (уже есть).
    - **Ответ владельца на момент 2026-05-15:** «опубликовать STANDARD с сохранением всех функций». Это означает: **main F-Droid (`f-droid.org`) с STANDARD as-is невозможен** — их Inclusion Policy прямо запрещает proprietary SDK в APK. Возможные исходы фазы 2:
      - **A — Отказаться от main F-Droid**: остановиться на IzzyOnDroid + GitHub Store (S0214) + Obtainium как достаточном покрытии FOSS-аудитории.
      - **B — Согласиться на компромисс**: ввести отдельный `fdroid`-flavor с потерей Cloud (или с WebDAV-only) + Cast + Wear + ML Kit + In-App Review; держать его параллельно STANDARD.
      - **C — Гибрид**: вынести Cloud/ML/Cast/Wear/Review за интерфейсы (рефакторинг по `dev/FLAVOR_DEVELOPMENT_RULES.md`) и собрать `fdroid`-flavor с отключёнными подсистемами; UI этих секций просто скрывается в F-Droid edition. STANDARD остаётся полным.
    - **Решение по фазе 2:** принимается **после завершения фазы 1**. Запуск фазы 2 в рамках этой спеки не требуется. Если фаза 2 ни одним из исходов не нужна, спека закрывается после фазы 1 (статус Verified, без открытой фазы 2).
    - **Статус:** Open (decision needed после фазы 1).

---

## 7. Риски

**Фаза 1 — IzzyOnDroid:**

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| IzzyOnDroid правила изменились с момента ресёрча (Anti-Features флаги перестали покрывать proprietary SDK набор проекта) | Низкая | Recipe отклонён или отозван | Перед submission — повторно сверить актуальные требования IzzyOnDroid; формально подтвердить, что `NonFreeDep` + `NonFreeNet` достаточны для нашего набора (`play-services`, `mlkit`, `msal`, `dropbox`) |
| LICENSE файл отсутствует или указывает proprietary лицензию | Средняя (статус не проверен) | Submission rejected на этапе review | Проверить состояние `LICENSE` (§6 item 4); при отсутствии или несовместимости — добавить Apache 2.0 (соответствует большинству зависимостей) |
| Метаданные fastlane не валидны (превышен лимит chars в short_description / full_description / changelog) | Низкая | Recipe принят, но карточка отображается обрезанной | Линт-чек длины полей до submission; короткая версия — ≤ 80 chars, полная — ≤ 4000, changelog — ≤ 500 |
| Сменился signing key между релизами | Низкая | Auto-update блокируется fingerprint-mismatch | Зафиксировать единый ключ в `dev/DEV_OPS.md` (см. ADR-5); перед каждым релизом сверять fingerprint с предыдущим (skill уровня release/`/skill-release`) |
| GitHub-тег формата `Y.YM.MDDH.Hmm` (`2.60.5152.017`) не распознаётся IzzyOnDroid auto-update | Низкая | IzzyOnDroid не подхватывает новые версии без ручной правки recipe | На POC-этапе фазы 1 проверить, что IzzyOnDroid recipe корректно мапит наш тег в `versionName` / `versionCode` |
| Случайная публикация `noLegal`-APK в IzzyOnDroid | Низкая | Юридический риск, нарушение правила публичного канала (§3.2) | Релизный скрипт публикует в GitHub Releases только `standard` (+ опционально `vr`); recipe IzzyOnDroid жёстко указывает `applicationId = com.sza.fastmediasorter` (без `.nolegal`/`.vr` суффиксов) |
| Anti-Features флаги отпугивают часть FOSS-аудитории, низкие установки | Средняя | Канал не оправдывает усилий | Honest marketing: `full_description.txt` явно перечисляет FOSS-альтернативы (SMB / FTP / SFTP — нет proprietary; OCR — Tesseract; UI — FOSS); proprietary части помечены честно. Аудитория, для которой это важно, выбирает осознанно |

**Фаза 2 — main F-Droid:**

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Решение по фазе 2 откладывается бесконечно, спека «зависает» | Высокая | F-Droid main канал не появляется | Решение по фазе 2 принимается явно в течение 3 месяцев после завершения фазы 1; если не принято — фаза 2 закрывается со статусом «не делаем» через ADR-6 |
| Объём рефакторинга подсистем за интерфейсы (если фаза 2 = «идём») недооценён | Высокая | Фаза 2 не завершается, F-Droid main канал не запускается | Если идём в фазу 2 — открыть отдельный спек с собственной декомпозицией; в S0215 не пытаться удержать обе фазы в одной тактической спеке |
| FOSS-замены в `fdroid`-flavor ухудшают UX настолько, что вариант непригоден | Средняя | Потраченные усилия не приводят к выходу на main F-Droid | Перед началом рефакторинга — POC по каждой FOSS-альтернативе (LibreTranslate, argos-translate, WebDAV-cloud); если POC показывает деградацию ниже приемлемой, фича отключается полностью, а не подменяется хуже работающей |
| Custom FFmpeg AAR с DTS блокирует submission в main F-Droid | Средняя | Recipe отклонён | Не включать DTS в `fdroid`-flavor (§6 item 3); fallback на стандартный media3 |
| AGP / Gradle / NDK версии проекта несовместимы с `fdroidserver` build-окружением | Низкая | Build recipe не собирается до обновления `fdroidserver` | Проверить совместимость заранее (§6 item 5); при необходимости — поддерживать `fdroid`-совместимую старшую AGP-ветку как минимально допустимую |
| Сломанная Room-миграция в публичном релизе main F-Droid | Низкая | Массовый краш при auto-update у пользователей F-Droid | Стандартный гейт проекта (Room version bump + migration test); ничего нового не добавляется, но риск выше из-за публичности канала |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md` на стратегической итерации. Спека описывает канал distribution и инфраструктурный рефакторинг под FOSS-flavor — не вводит новой пользовательской возможности внутри приложения. Если по факту разделения подсистем за интерфейсы какая-то функция станет доступна для расширения сторонними реализациями — это будет отдельным спеком и тогда зайдёт в `docs/FEATURES*.md`. На этой итерации — нет.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Двухэтапная стратегия — IzzyOnDroid first, main F-Droid отложен**

- **Решение:** Фаза 1 — IzzyOnDroid с STANDARD as-is через Anti-Features (`NonFreeDep`, `NonFreeNet`). Фаза 2 — main F-Droid (`f-droid.org`) — отдельная research-задача, запускается после фазы 1 и только если владелец согласен на компромисс по функциям.
- **Альтернативы:** A — один общий FOSS-flavor для обоих каналов сразу (исключено требованием «сохранить все функции STANDARD»). B — только IzzyOnDroid, main F-Droid игнорируем. C — только main F-Droid через FOSS-flavor, IzzyOnDroid игнорируем.
- **Почему:** двухэтапный путь даёт быстрый выход в FOSS-экосистему (IzzyOnDroid принимает STANDARD as-is с Anti-Features флагами) без потери функций; main F-Droid требует радикального компромисса по функциям, решение по этому компромиссу можно принять позже, не блокируя фазу 1.

**ADR-2: Фаза 1 не вводит нового flavor — публикуется STANDARD release APK**

- **Решение:** в фазе 1 в IzzyOnDroid публикуется существующий `standard`-release APK, который собирается из той же `main`-ветки через тот же релизный pipeline (`a.ps1 r`). Никакого нового flavor, никакого рефакторинга подсистем, никаких изменений в `build.gradle.kts` (за исключением, возможно, добавления fastlane-метаданных в источник).
- **Альтернативы:** A — даже для IzzyOnDroid ввести отдельный flavor (избыточно — IzzyOnDroid это не требует). B — публиковать `lite` (меньше зависимостей, меньше Anti-Features флагов, но и меньше функций — теряем смысл «STANDARD as-is»).
- **Почему:** IzzyOnDroid принимает APK as-is при условии FLOSS-лицензии приложения и пометок Anti-Features; рефакторинг для фазы 1 не требуется; минимум работы, максимум скорости публикации.

**ADR-3: Anti-Features флаги в IzzyOnDroid recipe — `NonFreeDep` + `NonFreeNet`**

- **Решение:** в recipe для IzzyOnDroid выставляются оба флага: `NonFreeDep` (зависимость от proprietary SDK — Google Play Services, ML Kit, MSAL, Dropbox) и `NonFreeNet` (зависимость от non-free сетевых сервисов — Google Drive, OneDrive, Dropbox, Cast, ML Kit Translate). Конкретный текст обоснования каждого флага — research item §6 item 7.
- **Альтернативы:** A — только `NonFreeDep` (формально недостаточно, есть сетевые зависимости). B — никаких флагов (нарушение правил IzzyOnDroid, recipe будет отклонён).
- **Почему:** честная маркировка соответствует политике IzzyOnDroid; пользователи каталога видят, на что подписываются; снижает риск отзыва recipe после review.

**ADR-4: Решение по фазе 2 (main F-Droid) откладывается до завершения фазы 1**

- **Решение:** §6 item 13 остаётся `Open` до завершения фазы 1. После публикации в IzzyOnDroid владелец решает: A — отказаться от main F-Droid (фаза 2 не выполняется, спека закрывается). B — компромисс на отключение Cloud/Cast/Wear/ML/Review в отдельном `fdroid`-flavor. C — гибрид через вынос подсистем за интерфейсы.
- **Альтернативы:** A — принять решение по фазе 2 сейчас (исключено — нужна обратная связь от IzzyOnDroid аудитории и оценка ценности main F-Droid). B — параллельная разработка фазы 2 (исключено — рефакторинг подсистем затрагивает `src/main/`, без необходимости делать сейчас).
- **Почему:** фазы независимы; фаза 2 имеет высокий объём работы и спорную ценность (большая часть FOSS-аудитории доступна через IzzyOnDroid, GitHub Store S0214, Obtainium); решение «идём в main F-Droid с компромиссом» — стратегическое, его лучше принимать на основании реальных данных, а не предположений.

**ADR-5: Single signing key для всех каналов**

- **Решение:** один и тот же `keystore.properties` обслуживает Google Play, IzzyOnDroid, GitHub Store (S0214), Obtainium. Ключ не ротируется между каналами. Если в фазе 2 появится `fdroid`-flavor — он может использовать тот же ключ (другой `applicationId` через `.fdroid` суффикс гарантирует, что приложения не конфликтуют на устройстве).
- **Альтернативы:** A — отдельный ключ для F-Droid-каналов (ломает auto-update при миграции пользователя между каналами; F-Droid main всё равно подпишет своим build-key, если не используем `AllowedAPKSigningKeys`).
- **Почему:** auto-update в любом канале опирается на signing fingerprint; смена ключа = массовый «непохожая подпись» error и принудительная переустановка для всех пользователей.

---

## 10. Связи с другими спеками

- **S0214 — Публикация в GitHub Store.** Параллельный канал distribution с радикально другими требованиями (там — публикация подписанного APK в GitHub Releases без ограничений на dependency tree; здесь — обязательная FLOSS-чистота APK). Решения по S0214 (asset naming, release-notes-источник, signing fingerprint фиксирован) частично переиспользуются для F-Droid (тот же release pipeline на ветке `main`, тот же `WHATS_NEW.md` для changelogs). Спеки не блокируют друг друга, но согласование asset naming и signing желательно — S0215 может быть `BlockByOtherTask: S0214` на фазе submission, если S0214 ещё не закрыт.
- **S0135 — Play Store listing optimization.** Полезно для содержимого `short_description.txt` / `full_description.txt` — тексты, отполированные для Play, частично переиспользуются для fastlane-метаданных F-Droid (с поправкой на тон F-Droid аудитории).
- **S0156 (ADR-8, noLegal flavor)** и **S0117 (noLegal isolation)** — паттерн «отдельный flavor с собственными зависимостями» уже отработан на `noLegal`; `fdroid`-flavor применяет тот же подход в обратную сторону (минус, а не плюс зависимостей).

---

## 11. Критерии готовности (strategic-level)

**Фаза 1 — IzzyOnDroid:**

1. `LICENSE` файл проекта приведён к FLOSS-совместимому виду (Apache 2.0 / MIT / GPL — выбор владельца в §6 item 4).
2. Fastlane-метаданные подготовлены в EN/RU/UK: `short_description.txt` (≤ 80 chars), `full_description.txt` (≤ 4000 chars), `images/icon.png`, `images/phoneScreenshots/*.png`, `images/featureGraphic.png`, `changelogs/<versionCode>.txt` (≤ 500 chars). Расположение метаданных — `<root>/fastlane/metadata/android/<locale>/` или `src/standard/fastlane/metadata/android/<locale>/` (§6 item 8).
3. Тексты `short_description.txt` и `full_description.txt` прошли тон-чеклист `docs/COMMUNICATION_POLICY.md` §6 и честно декларируют наличие proprietary SDK через формулировку «integrates with Google Drive / Dropbox / OneDrive / Cast — proprietary services».
4. Recipe для IzzyOnDroid `apps.json` / per-app file подготовлен и подан в IzzyOnDroid (через issue или MR в их публичную инфраструктуру — точный механизм submission — §6 item 8 или новый research-item в тактической спеке) с корректными Anti-Features флагами (`NonFreeDep`, `NonFreeNet`).
5. STANDARD-release APK опубликован в GitHub Releases с детерминированным именем актива (согласованным с S0214); IzzyOnDroid CI обнаруживает релиз по GitHub-тегу.
6. Установка из IzzyOnDroid на тестовом устройстве проходит штатно; auto-update между двумя последовательными релизами не блокируется fingerprint-проверкой.
7. README репозитория во всех существующих локалях (EN / RU / UK) содержит бейдж «Get it on IzzyOnDroid» с deep-link на корректную страницу приложения.
8. Карточка приложения в IzzyOnDroid содержит корректное название, иконку, описание, скриншоты и список релизов с changelogs.

**Фаза 2 — main F-Droid (research-only):**

9. Документ-результат ресёрча зафиксирован: список всех proprietary зависимостей с per-каждой решением (FOSS-замена возможна / отключение / неустранимый блок).
10. Принципиальное решение владельца «идём в main F-Droid / не идём» зафиксировано как ADR-6 этой спеки.
11. Если решение «не идём» — спека закрывается со статусом Verified после выполнения критериев фазы 1. Фаза 2 явно отмечается «не выполняется» с обоснованием.
12. Если решение «идём» — открывается новая стратегическая спека `Sxxxx-fdroid-flavor-foss-refactor` (текущая S0215 не расширяется в полноценный flavor-рефакторинг — это самостоятельный объём работы).

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0215` — создаст `PLAN/S0215_fdroid-publish-research/` с фазами **только для фазы 1 (IzzyOnDroid)**. Тактическая декомпозиция фазы 2 (main F-Droid) откладывается до явного решения владельца (см. ADR-4, §6 item 13); если решение «идём» — фаза 2 открывается отдельным спеком, чтобы не смешивать «быстрое подключение IzzyOnDroid» и «многомесячный FOSS-рефакторинг» в одной тактической спеке.

---

## Источники ресёрча (web search 2026-05-15)

- [F-Droid Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) — FLOSS license requirement, proprietary library prohibition (`play-services`, Firebase, Crashlytics, MLKit), unique Application ID, user-consent for binary downloads.
- [F-Droid Submitting Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/) — fastlane structure, build recipe in `fdroiddata`, merge request workflow.
- [F-Droid Anti-Features](https://f-droid.org/docs/Anti-Features/) — `NonFreeNet` / `NonFreeDep` flags for partial FOSS-compliance.
- [F-Droid All About Descriptions, Graphics and Screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/) — fastlane metadata structure: `short_description.txt` (≤80), `full_description.txt` (≤4000), `changelogs/<versionCode>.txt` (≤500), `phoneScreenshots/`.
- [F-Droid Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/) — optional but recommended; `AllowedAPKSigningKeys` mechanism for developer-signed APKs.
- [F-Droid Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) — recipe YAML schema (Build, versionName, versionCode, commit, subdir, gradle).
- [F-Droid FAQ for App Developers](https://f-droid.org/en/docs/FAQ_-_App_Developers/) — practical guidance on FOSS dependency replacements and Anti-Feature workflow.
