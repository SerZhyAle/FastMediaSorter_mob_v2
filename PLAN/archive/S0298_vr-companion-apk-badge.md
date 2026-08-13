---
ticket: S0298
status: Verified
priority: 70
date: 2026-05-25
tier: 3
---

# Стратегическая спецификация: S0298 — VR companion APK badge в Browse

**Ticket:** S0298
**Status:** Archived
**Priority:** 70
**Date:** 2026-05-25
**Implemented date:** 2026-05-27
**Tier:** 3 — Strategic
**Tactical plan:** `PLAN/S0298_vr-companion-apk-badge/INDEX.md`
**Roadmap entry:** `S0297` first-wave D-1.a (recommended choice 2026-05-25) — самая лёгкая и независимая noLegal-VR capability из матрицы research-а. Опирается на существующую S0183 (`nolegal-apk-install`) install-инфраструктуру, добавляет visual classification на тиле без новых зависимостей.

**Depends on:**
- `S0183` Archived — установочный pipeline для `.apk` файлов в noLegal-сборке (REQUEST_INSTALL_PACKAGES handling, system PackageInstaller integration). Этот тикет добавляет UX-маркировку поверх существующего install-action, не пересоздаёт install-pipeline.
- `S0156` Approved (или живой — `BlockByOtherTask` приемлем) — policy parent: документация в `docs/FEATURES_noLegal.md`, flavor isolation в `src/noLegal/java/`. Не разблокируется этим тикетом, см. `S0297` D-6.a.

**Blocks:** косвенно — последующий «full VR-companion section в Browse» (отдельный тикет, не аллоцирован) опирается на работающую B3.2 classification.

---

## 0. Approval Gate (owner input)

- **Requested mode:** добавить visual VR-маркировку на `.apk` тилы в noLegal Browse, чтобы пользователь видел «это VR-companion APK» до того, как нажмёт Install. Минимально-инвазивная UX-надстройка над существующим S0183 install-pipeline.
- **Goal / expected outcome:** в noLegal-сборке при просмотре папки с `.apk` файлами VR-capable APK получают компактный visual badge (иконка + opциональный микро-текст) на тиле; non-VR APK остаются как есть. Tap на VR-APK ведёт по тому же install-пути, что и tap на любой `.apk`. Classification кэшируется по `(path, size, mtime)` и не пересчитывается на каждом скролле.
- **Local anchor:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/<browse-or-action-handler-path>` (точный класс — тактическая спека), Browse-уровневая adapter-логика отображения `.apk` тилов в noLegal-flavor, существующая S0183 install-CTA.
- **Scope boundaries / forbidden areas:** не входит — full VR-companion section в Browse (отдельный тикет), Android XR `com.google.intent.category.VR_ONLY` детект (отдельный future-step), AXML walk для intent-category (B3.3 — отложен до конкретного miss-репорта), любое UI вне `.apk` тилов, любое изменение install-pipeline в S0183.
- **Done / success signal:** на устройстве в noLegal-сборке: открыть папку с несколькими `.apk` файлами (минимум 3 VR-companion + 3 обычных) → VR-companion APK имеют badge → обычные APK без badge → tap по любому ведёт в обычный S0183-install-flow → повторный скролл не вызывает заметной задержки (кэш работает).
- **Autonomy rule:** конкретное место badge на тиле (top-end overlay, side icon, отдельная строка под имя), точная иконка, размер, локализация микро-текста — делегированы агенту в тактической спеке + `/ui-clarify` gate перед написанием layout-кода.
- **UI Clarification Status:** `READY` — mockups зафиксированы в `temp/sketches/S0298_grid_badge.png`, `temp/sketches/S0298_list_badge.png`, `temp/sketches/S0298_accessibility_badge.png`.

### UI Clarification Outcome

**Status:** READY

### Approved Decisions

- **Placement / portrait:** grid и list используют один и тот же не-кликабельный pill badge c `ic_vr_headset` + текстом `VR` в `top|start` углу thumbnail area. В list view badge живёт поверх thumbnail, а не в text-column, чтобы не сдвигать filename/action row.
- **Placement / landscape:** идентично portrait. Отдельного landscape-layout для item tile сейчас нет; badge остаётся в том же thumbnail-corner и не меняет geometry tile.
- **Visibility / priority:** badge показывается только для `.apk` файлов в noLegal, только после положительной classification. Во время loading, cache miss без результата, parse failure, corrupted APK, download failure или non-VR result badge скрыт. Никакого disabled-state и никакого overflow spill нет — badge чисто decorative.
- **Interaction / wording:** badge не кликается, не получает long-click, tooltip и отдельный action. Tap/long-click по tile продолжает existing binary-file flow (`onBinaryFileClick` → S0183 bottom sheet/install path). Внутренний label фиксирован как `VR`; отдельный микро-текст кроме `VR` не нужен.
- **State / failure UX:** loading placeholder не показывается — чтобы не мигать на скролле. Ошибка classification остаётся тихой (`Timber` + hidden badge). Для remote/cloud APK допускается background materialization в cache; если локальная копия не получена, tile остаётся без badge.
- **Accessibility:** badge `focusable=false`, `clickable=false`, D-pad traversal идёт только по tile root. Для TalkBack badge имеет отдельный `contentDescription` (`VR app for Quest`) и `importantForAccessibility=yes`, чтобы semantic signal не потерялся.

### Delegated Assumptions

- Reuse `@drawable/ic_vr_headset` и existing dark-pill language из S0292 вместо нового artwork.
- Не создавать отдельную list-specific iconography или secondary help-text: один visual token лучше для scan speed и не раздувает layout.
- Network/cloud classification имеет право скачать APK в cache только в background и только через существующие cache/download abstractions, без progress UI на tile.

### Approved scope decisions

- **Variant B3.2 (no AXML walk):** classification использует только `PackageManager.getPackageArchiveInfo(GET_META_DATA | GET_CONFIGURATIONS)` для чтения `<meta-data android:name="com.oculus.supportedDevices">` + `<uses-feature android:name="android.software.vr.mode">` + `<uses-feature android:name="android.hardware.vr.headtracking">`. Признак положительного hit — наличие **любого** из трёх сигналов. По research B3.2 покрывает 90%+ Quest-targeted APK.
- **B3.3 (AXML walk для `com.oculus.intent.category.VR`) отложен** до конкретного miss-report-а на device-test (см. `S0297` RESEARCH.md §B3).
- **Android XR (`com.google.intent.category.VR_ONLY`) отложен** — XR-экосистема нащальна (2026-05), incremental add позже.
- **Flavor scope:** только noLegal. В `vr` / `standard` тикет не имеет смысла — S0183 install-CTA там отсутствует.
- **Кэширование classification:** ключ — `(path, fileSize, lastModified)`. На совпадение кэша возвращается boolean classification без re-открытия APK. Granularity миллисекунды для mtime приемлема (`File.lastModified()`).
- **Lazy classification:** badge запрашивается только когда `.apk` тил попадает в viewport (`onBindViewHolder`-like событие). Foreground-классификации в момент enumerate'а папки не делается.
- **Не-VR fallback:** если `getPackageArchiveInfo` бросает `IOException` / file corrupted — APK не классифицируется как VR (no badge), обычная install-CTA остаётся. Никаких error-toasts на тиле.

### Delegated assumptions

- `PackageManager.getPackageArchiveInfo(apkPath, GET_META_DATA | GET_CONFIGURATIONS)` доступен на minSdk 26 (noLegal-baseline) — confirmed по `developer.android.com/reference/android/content/pm/PackageManager#getPackageArchiveInfo`.
- Browse-adapter в noLegal-сборке имеет точку расширения для overlay-бейджей на тилы. Если её нет — тактическая спека вводит её (BrowseAdapter helper).
- Iconography для VR-бейджа берётся из существующего material symbols набора либо создаётся как один SVG в `res/drawable-noLegal/` (минимальный artwork).

---

## 1. Проблема

- В noLegal-сборке `.apk` файлы в Browse сейчас отличаются от обычных файлов **только** наличием Install-CTA в action menu (см. `docs/FEATURES_noLegal.md` §3). Пользователь, листающий папку с десятком sideload-APK, не видит «эти три — VR-companion для Quest, остальные — обычные phone-apps» до тех пор, пока не откроет Install-CTA или не установит и не запустит APK.
- На Quest 3 / Android XR это особенно неудобно: пользователь скачивает несколько APK (SideQuest companions, Quest Games Optimizer extensions, VR file-manager-ы, OpenXR-utility-приложения) и должен помнить «это какой из них VR» по имени файла. Имя файла — ненадёжный сигнал.
- Research (`S0297` matrix B3) показал, что детект VR-capability через `PackageManager.getPackageArchiveInfo(GET_META_DATA | GET_CONFIGURATIONS)` покрывает 90%+ Quest-targeted APK без новых зависимостей и без AXML парсинга — это самый low-cost low-maintenance pick из всей noLegal-VR матрицы.
- Без этого простого визуального сигнала любая дальнейшая noLegal-VR capability (A2 субтитры, A1 yt-dlp VR-extraction, B1.3 sidecar library) будет иметь worse first-impression UX: пользователь не понимает, что noLegal-сборка вообще «знает» про VR.

---

## 2. Цели

- На `.apk` тилы в noLegal Browse добавляется visual VR-badge, когда APK имеет минимум один из трёх сигналов: `com.oculus.supportedDevices` meta-data, `android.software.vr.mode` uses-feature, `android.hardware.vr.headtracking` uses-feature.
- Badge виден в grid view и в list view одинаково; следует общей визуальной системе overlay-бейджей (см. S0292 floating VR-бейдж — единый визуальный язык).
- Classification кэшируется по `(path, size, mtime)`; повторные просмотры тех же APK не перезапускают `getPackageArchiveInfo`.
- Performance: на NAS share с 100+ `.apk` файлов скролл остаётся плавным (no observable jank на viewport-events).
- Tap по VR-APK ведёт в существующий S0183 install-flow без изменений; никакого нового modal-диалога.
- В `standard` / `vr` / `lite` / `photos` / `legacy` сборках классификации нет (никаких новых классов в `src/main/`, тем более — никаких BuildConfig-гэйтов).
- Accessibility: badge не блокирует focus traversal; имеет осмысленный `contentDescription` для TalkBack («VR-приложение для Quest 3»); D-pad navigation проходит мимо badge как мимо decorative element.

**Non-goals:**

- Full VR-companion section / отдельная вкладка в Browse — отдельный тикет.
- Android XR `com.google.intent.category.VR_ONLY` детект — отдельный future-step.
- AXML walk для `<category>` тегов внутри intent-filter — отложен до концретного miss-report-а (B3.3).
- Установка / запуск VR-APK — лежит в S0183 install-flow, не дублируется.
- Анализ APK-permissions, signature, version, или любых других мета-полей кроме трёх VR-сигналов.
- Каталог известных VR-companion-приложений или integration с SideQuest API — outside scope.

---

## 3. Решение

### 3.1. Classification interface

- В `src/noLegal/java/` появляется один утилитарный класс `VrApkClassifier` (точное имя и пакет — тактическая спека). API минимальный: `fun classify(file: File): VrApkClassification`, где `VrApkClassification` — sealed/data-class с полями `isVrCapable: Boolean` + опционально `signals: Set<VrSignal>` для диагностики.
- Реализация: `context.packageManager.getPackageArchiveInfo(file.absolutePath, GET_META_DATA or GET_CONFIGURATIONS)` → проверка `applicationInfo.metaData?.containsKey("com.oculus.supportedDevices")` + `reqFeatures?.any { it.name == "android.software.vr.mode" }` + `reqFeatures?.any { it.name == "android.hardware.vr.headtracking" }`.
- При любой exception (`IOException`, `RuntimeException` от corrupted APK) — graceful: `VrApkClassification(isVrCapable = false)`, лог через Timber на уровне `i` (информационный, не ошибочный — см. agent-memory `feedback_log_levels`).
- Класс не имеет state; все обращения thread-safe (PackageManager call безопасен из background coroutine).

### 3.2. Caching layer

- Между Browse-adapter и `VrApkClassifier` — `VrApkClassificationCache` (in-memory, LRU, capacity ~256 entries — точное число тактическая спека).
- Ключ кэша: triple `(absolutePath, fileSize, lastModified)`. Любое изменение mtime или size инвалидирует запись.
- Cache живёт в caller-side слое (singleton под Hilt либо в repository-слое noLegal). Не persists на disk — холодный старт пересчитывает.
- Поведение при cache miss: возвращается `null`/`Loading`-state синхронно, classification запускается в background coroutine, через callback / Flow подписчик обновляется.

### 3.3. Browse-adapter integration

- Browse-adapter в noLegal-сборке (точное имя класса — тактическая спека) при `onBindViewHolder` для `.apk` тила: запрашивает classification через `VrApkClassificationCache`, привязывает state-change-callback к ViewHolder.
- Если кэш hit и `isVrCapable == true` — на ViewHolder включается VR-badge overlay (View visibility = VISIBLE).
- Если cache miss — badge остаётся скрытым; classification стартует; на callback badge становится видим (или остаётся скрытым).
- Если ViewHolder recycled до завершения classification — callback safely no-op (typed cancellation pattern).

### 3.4. Visual design

- Badge — компактный overlay на тиле, аналогичный визуальной системе S0292 floating VR-бейдж в плеере: pill / round shape, VR-иконка (material symbol либо custom SVG), без текста (или с микро-текстом «VR»), tint следует общей теме приложения.
- Точная позиция (top-end overlay, side icon, под именем файла) — `/ui-clarify` gate перед тактической спекой.
- В list view и grid view бейдж выглядит **одинаково** (та же иконка, тот же размер 16..24dp, тот же tint). Никакой отдельной list-version.
- В portrait и landscape — одинаково. В fullscreen Browse (если такой существует) — одинаково.
- Min target 48dp — **не применимо**: badge сам по себе non-clickable decoration. Click по тилу обрабатывается existing tile click-handler (ведёт в action menu → Install).

### 3.5. Performance and lifecycle

- Classification выполняется в background coroutine (`Dispatchers.IO`), результат публикуется на main через StateFlow / callback.
- На scroll-velocity (быстрая прокрутка списка) — стандартный pattern: запросы classification отменяются для recycled ViewHolder-ов, чтобы не блокировать background pool бесполезной работой.
- Background pool: ограничен ~3..4 concurrent classification jobs (тактическая спека уточняет). Большие batch (100+ APK в папке) не должны генерировать 100 параллельных PackageManager-вызовов.
- Cache survives configuration change (Hilt-scoped или ViewModel-scoped); cache cleared on Browse-screen-exit (memory hygiene).

### 3.6. Flavor isolation

- Весь новый код — в `src/noLegal/java/`. Никаких изменений в `src/main/java/`. Никаких `BuildConfig.IS_NO_LEGAL_FLAVOR` гэйтов в `src/main/` (правило CLAUDE.md Strict Rules §15).
- Если Browse-adapter живёт в `src/main/` и нужна точка расширения для overlay-бейджей — она вводится как абстрактный interface в `src/main/` с no-op default impl, реальная impl в `src/noLegal/java/`, binding через flavor-specific Hilt-модуль (паттерн из `dev/FLAVOR_DEVELOPMENT_RULES.md`).
- Иконка / drawable badge живут в `src/noLegal/res/drawable-nodpi/` либо `src/noLegal/res/drawable/`. Локализованные строки (`contentDescription`) — в `src/noLegal/res/values{,_RU,_UK}/strings.xml`.

### 3.7. Документация

- `docs/FEATURES_noLegal.md` (gitignored, EN / RU / UK) получает один новый bullet под раздел про APK install (§3 в текущем файле): «VR companion APK badge — `.apk` tiles in Browse get a small VR icon when the APK declares VR capability (com.oculus.supportedDevices meta-data, android.software.vr.mode uses-feature, или android.hardware.vr.headtracking uses-feature).»
- `docs/FEATURES.md` / `_RU` / `_UK` — без изменений (noLegal-only capability, не публичная фича).
- Functionality log — одна строка через `scripts/add_to_functionality_log.ps1 -Id S0298 -Op ADD` на этапе `Verified`.

---

## 4. Открытые вопросы

- **`arm64-v8a`-only soft signal:** если APK не имеет ни одного из трёх явных VR-сигналов, но содержит **только** `arm64-v8a` native libs (typical for Quest-only sideload) — считать ли это soft VR-signal? Default — нет (избегаем false positives на phone-only arm64-приложениях).
- **Кэш eviction policy:** LRU capacity 256 — достаточно для типичного использования (50..150 APK в одном sideload-folder), но может быть мало для NAS share с 500+ APK. Тактическая спека уточняет.
- **Backward-compat для `com.oculus.supportedDevices` старых APK:** Meta SDK эволюционировал; некоторые старые APK могут иметь deprecated form ("Pacific" instead of "Quest3"). Игнорировать значение, проверять только presence ключа.
- **Multi-window-aware badge:** если Browse открыт в split-screen / multi-window — badge остаётся виден без изменений (никакой adaptive logic).
- **Symlinks и junctions на NAS:** если `.apk` доступен по symlink — `getPackageArchiveInfo` должен работать (он принимает absolute path); тактическая спека верифицирует на устройстве.

---

## 5. Риски

- **`getPackageArchiveInfo` отказывает на больших APK или corrupted-APK.** Митигация: try/catch + Timber.i + non-VR fallback (badge не показывается, install-CTA остаётся).
- **PackageManager performance на старых devices.** На Quest 3 (Android 14) call ~10..50 мс на типичный APK; на slow NAS read может занять секунды. Митигация: вся classification в background coroutine; cache по `(path, size, mtime)`.
- **Cache invalidation false miss.** Если APK заменён на disk но `mtime` совпал — cache даст устаревший результат. Низкая вероятность; митигация — добавить SHA-1 prefix (первые 8 байт) в cache key как secondary signal, если будет показано на практике.
- **Visual badge диссонирует с общей design language.** Митигация: `/ui-clarify` gate с mockup-ами перед тактической спекой; visual review on-device на Quest 3 и phone.
- **Cross-flavor leak:** если interface для overlay-badge введён в `src/main/java/` без аккуратной no-op default, `standard`-сборка может попытаться его resolve через Hilt и упасть. Митигация: явная no-op default impl в `src/main/java/` (returns false для всех APK).
- **APK Android XR detection regression** — если в будущем Android XR APK массово начнут declare `VR_ONLY` без `vr.mode` / `vr.headtracking` — наша B3.2-логика их пропустит. Митигация: monitor реальные XR-APK после Q1 2027; добавить XR-сигналы в follow-up тикете.

---

## 6. Влияние на пользователя (docs/FEATURES)

- `docs/FEATURES.md` + `_RU` + `_UK` — **без изменений** (noLegal-only capability).
- `docs/FEATURES_noLegal.md` + `_RU` + `_UK` — **одна новая строка** в §3 (после уже существующего bullet про APK install).

Текст для noLegal-FEATURES (EN, эквивалент в RU / UK):

> «VR companion APK badge: in Browse, .apk tiles get a small VR icon when the APK declares VR capability (Meta `com.oculus.supportedDevices` meta-data, or `android.software.vr.mode` / `android.hardware.vr.headtracking` uses-feature). Classification is cached by file mtime; no extra dependencies, no AXML parsing. Tap follows the existing APK install flow.»

Communication-policy check (§6 tone) проходит на этапе тактической спеки.

---

## 7. Архитектурные решения (ADR)

**ADR-1: B3.2 без AXML walk как baseline**

- **Решение:** classification использует только `PackageManager.getPackageArchiveInfo(GET_META_DATA | GET_CONFIGURATIONS)`. AXML walk для `<category>` тегов intent-filter не делается.
- **Альтернативы:** (а) B3.3 — добавить `jaredrummler/APKParser` (~80 КБ) или vendored 250-LOC binary-AXML парсер; (б) B3.1 — не делать вообще.
- **Почему:** research B3 показал, что B3.2 покрывает 90%+ Quest-targeted APK. Add AXML walk только при concrete miss-report. Минимальный зависимостный footprint.

**ADR-2: Cache key `(path, size, mtime)`**

- **Решение:** triple-key cache; eviction LRU; in-memory only.
- **Альтернативы:** (а) SHA-1 prefix (первые 8 байт) как ключ — overkill; (б) только path как ключ — false hits после replace на месте.
- **Почему:** mtime/size — стандартный pattern Android file-system; ложноположительные срабатывания редки в обычных условиях.

**ADR-3: Lazy classification on viewport, не on enumerate**

- **Решение:** classification стартует только когда `.apk` тил попадает в viewport (visible), не во время первичного enumerate папки.
- **Альтернативы:** (а) eager classification при listing — стартует pool из 100+ задач сразу, блокирует UI; (б) on-demand при tap — пользователь уже принял решение, badge бесполезен.
- **Почему:** lazy + cache = baseline performance pattern для list-heavy UI.

**ADR-4: Иконка и UI следуют S0292 visual language**

- **Решение:** VR-badge на APK тиле визуально согласован с floating VR-бейджом в плеере (S0292): pill / round, VR-иконка, тот же tint.
- **Альтернативы:** (а) отдельный visual language для APK-бейджа — раздёргивает design system; (б) text-only «VR» metka — менее scannable.
- **Почему:** один visual signal для «эта штука относится к VR» по всему noLegal-приложению — лучшая discoverability + lowest cognitive load.

---

## 8. Связи с другими спеками

- **S0297** Approved 2026-05-25 — research-источник; D-1.a first-wave choice.
- **S0156** (BlockByOtherTask) — policy parent: документация в `docs/FEATURES_noLegal.md`, flavor isolation. Не разблокируется этим тикетом.
- **S0183** Archived — родительский APK install pipeline; этот тикет добавляет UX-маркировку поверх существующего install-action.
- **S0292** Partial — общий visual language для VR-бейджей в noLegal-сборке. Этот тикет переиспользует тот же дизайн-язык.

---

## 9. Критерии готовности (strategic-level)

1. В noLegal-сборке в Browse: `.apk` файлы, имеющие `com.oculus.supportedDevices` meta-data ИЛИ `android.software.vr.mode` uses-feature ИЛИ `android.hardware.vr.headtracking` uses-feature, отображаются с visual VR-badge на тиле.
2. `.apk` файлы без любого из трёх сигналов отображаются как обычно (без badge).
3. Tap по VR-APK ведёт в существующий S0183 install-flow без изменений.
4. Повторный просмотр тех же APK (на скролле или после re-open Browse) не вызывает повторного `getPackageArchiveInfo` (cache работает).
5. На NAS share с 100+ `.apk` файлов скролл остаётся плавным; no observable jank или ANR.
6. В `standard` / `vr` / `lite` / `photos` / `legacy` сборках классификации нет; `.apk` тилы выглядят как до S0298.
7. Accessibility: TalkBack читает badge как «VR-приложение для Quest 3» (или эквивалент); D-pad focus traversal не вешается на badge.
8. `docs/FEATURES_noLegal.md` + `_RU` + `_UK` обновлены одной новой строкой в §3.
9. Functionality log: одна запись `ADD S0298: VR companion APK badge in noLegal Browse`.
10. `assembleStandardDebug` + `assembleNoLegalDebug` PASS; catalog sync на app_v2 PASS.

---

## 10. Что делать с этим документом

1. Перевести S0298 из `Draft` в `Approved` после `/ui-clarify` gate (mockup в `temp/sketches/S0298_*.png`).
2. `/spec-tech S0298` — разрезать на фазы (classifier class, cache, adapter integration, visual badge, docs/strings).
3. `/spec-dev S0298` поэтапно реализует фазы. Каждая закрывается `assembleStandardDebug` + `assembleNoLegalDebug` + on-device verify на Quest 3 / phone.
4. После `Verified` — следующий impl-тикет first-wave D-1.a порядка: A2 (`vr-realtime-subtitle-ocr-paddleocr`), при условии Verified S0296.

---

## Revision History

- **2026-05-25** - tactical gate completed by android-rd-specialist
  - UI clarification переведён в `READY`: top-start decorative pill badge для grid/list, без loading placeholder, с отдельным TalkBack description. Подготовлен tactical plan `PLAN/S0298_vr-companion-apk-badge/INDEX.md`.
- **2026-05-27** - implemented by Codex
  - Реализованы main Browse hook/no-op binding, noLegal manifest classifier/cache, noLegal list/grid badge layouts, Hilt override, noLegal feature docs и tactical tracking. `build-nolegal-debug.ps1` PASS.
- **2026-05-25** - created by android-rd-specialist (focus: S0297 first-wave D-1.a allocation)
  - Зафиксирован scope B3.2 (no AXML walk), кэширование `(path, size, mtime)`, lazy classification on viewport, flavor isolation noLegal-only, visual согласование с S0292 VR-бейджем. Депенденс на S0183 (install pipeline) + S0156 (policy parent). `/ui-clarify` gate `PENDING` до mockup-ов. Открытые вопросы по arm64-v8a soft signal и cache eviction policy — тактическая спека уточняет.

## Last Audit

**Date:** 2026-05-27
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 32 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [x] `build-debug.PS1` / `assembleStandardDebug` PASS.
- [x] `scripts/builders/build-nolegal-debug.ps1` / `assembleNoLegalDebug` PASS.
- [ ] On-device visual verification on Quest / phone not run in this session; static implementation and builds are verified.
