---
ticket: S0379
status: Implemented
priority: 50
date: 2026-06-07
tier: 4
---

# Стратегическая спецификация: S0379 - standard vs noLegal storage surface

**Ticket:** S0379
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-07
**Tier:** 4 - Strategic, ad-hoc
**Roadmap entry:** Ad-hoc - запрос 2026-06-07: написать спецификацию, которая разделяет максимальное усиление storage/file-manager surface в `standard` (с акцентом на OTG/SD и Android storage rules) и отдельный `noLegal`-слой поверх этого.
**Tactical spec:** `PLAN/S0379_standard-nolegal-storage-surface/INDEX.md`

> **Scope:** STRATEGIC. Capability envelope, platform boundaries, flavor split, documentation contract. Без имён классов, путей, лимитов строк, миграций Room и модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec
- **Goal / expected outcome:** Provided by user - стратегическая спека должна зафиксировать, что можно максимально усилить в `standard` для сценария файлового менеджера и доступа к OTG/SD/shared storage, а что можно добавить только в `noLegal` поверх этого
- **Local anchor:** Provided by user - текущий файловый менеджер / File Manager Mode / локальное хранилище / OTG / SD / различие между `standard` и `noLegal`
- **Scope boundaries / forbidden areas:** Provided by user - нужен split по capability surface; `standard` рассматривается отдельно, `noLegal` только как надстройка поверх него; реализация в этот запрос не входит
- **Done / success signal:** Provided by user - существует одна strategic spec, где отдельно описан потолок `standard` и отдельно перечислен допустимый `noLegal`-overlay поверх него
- **Autonomy rule:** Provided by user - применить ответы владельца на все вопросы раздела 6, выполнить стилевую санитацию и автономно перевести спеку в `Approved`
- **UI decisions / delegation:** N/A - текущий запрос про capability split и platform boundaries, а не про placement конкретных пользовательских controls

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

В продукте уже есть сильный файловый сценарий, но его границы описаны не как единая capability matrix, а как смесь Android-ограничений, текущих технических решений и исторических уступок под Google Play. Из-за этого легко перепутать три разных вопроса: что реально разрешает Android platform, что допустимо для публичного `standard`, и что можно включить только в sideload-only `noLegal`.

На практике это особенно заметно вокруг локального хранилища, OTG, SD-карт, `Android/media`, скрытых файлов, системных точек входа и broader file-manager positioning. Без отдельной стратегической рамки команда рискует либо недоусилить `standard`, хотя Android это позволяет, либо переобещать доступ туда, куда Android sandbox всё равно не пустит даже в `noLegal`.

Нужна отдельная спецификация, которая зафиксирует верхнюю границу `standard` как market-safe файлового менеджера и отдельно опишет `noLegal` как additive sideload-only storage surface, не смешивая эти слои между собой.

---

## 2. Цели

1. Определить максимальный practical storage/file-manager surface, который можно и стоит развивать в `standard` без выхода за Android platform rules и distribution expectations публичной сборки.
2. Зафиксировать `standard`-потолок для локального файлового сценария: shared storage, OTG, SD, `Android/media`, SAF-granted trees, файловые операции, permission messaging и system integration.
3. Явно отделить то, что в `standard` можно усилить через лучший UX, SAF/MediaStore/direct-path orchestration и volume discovery, от того, что Android platform всё равно запрещает.
4. Определить отдельный `noLegal`-overlay поверх `standard`, который может быть шире по sideload-поверхности, тяжёлым runtime, package-aware workflows, diagnostics и non-store feature surface, но не отменяет Android sandbox сам по себе.
5. Разделить `noLegal`-надстройку на базовый non-privileged слой и отдельный optional privileged/experimental lane, если позже владелец захочет обсуждать root/Shizuku-подобные расширения отдельно.
6. Зафиксировать documentation contract: что идёт в публичные docs для `standard`, а что остаётся только в `docs/FEATURES_noLegal*.md`.
7. Подготовить базу для последующего `/spec-tech`, где `standard` и `noLegal` будут разложены на фазы без повторного platform research.

**Non-goals:**

- Не внедрять реализацию в рамках этой `/spec`.
- Не обещать доступ к `Android/data`, `Android/obb` или чужим app-private каталогам в обычной sandbox-модели.
- Не считать `noLegal` автоматически root-build, privileged build или платформой без ограничений.
- Не принимать в этой спеке решение о внедрении `DocumentsProvider`, root-интеграции, Shizuku-мостов или silent install.
- Не расширять scope до всего `noLegal` backlog вне storage/file-manager surface.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Спека должна отдельно описывать, что можно максимально усилить в `standard`.
2. Внутри `standard` отдельный акцент нужен на OTG и SD-картах.
3. Спека должна отдельно показать, что можно добавить именно в `noLegal` поверх этого.
4. Разделение должно быть достаточно ясным, чтобы потом не смешивать требования Android platform и личной sideload-only сборки.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard` задаёт базовый потолок; `noLegal` рассматривается только как additive overlay поверх него, а не как альтернативная базовая продуктовая модель.
- **API level:** `standard` / `lite` / `photos` / `noLegal` идут по основной линии API 26+; решения должны учитывать современную storage-модель Android 11+ и fallback для более ранних API внутри поддерживаемого диапазона.
- **Wear OS:** вне объёма, кроме случаев, когда storage-contract позже должен быть отражён в документации.
- **Производительность:** усиление файлового сценария не должно означать eager full-device scan, тяжёлую индексацию всех томов при старте или безусловную генерацию миниатюр для больших бинарных деревьев.
- **Совместимость данных:** существующие ресурсы, URI-доступы и пользовательские настройки не должны терять совместимость; если позже потребуются миграции, они должны быть оправданы tactical-слоем.
- **Локализация:** любые будущие user-visible strings по этой теме обязаны иметь EN/RU/UK parity.
- **Доступность:** будущие UI-изменения в file-manager surface обязаны сохранять touch, keyboard, D-pad, mouse и TalkBack coverage.
- **Communication policy:** любые новые user-facing тексты по storage permissions, denied states, OTG/SD mounting и file-manager limitations обязаны проходить через `docs/COMMUNICATION_POLICY.md`.
- **Storage policy:** public wording должно оставаться технически честным: broad shared-storage access допустим, но protected app-specific directories и SAF-restricted roots нельзя обещать как доступные.
- **Flavor isolation:** `noLegal`-only storage features, diagnostics, package workflows и heavy runtimes не должны просачиваться в market-flavors через общий код, строки или capability hints.
- **Security model:** даже `noLegal` не должен неявно включать сценарии обхода DRM, silent install, неавторизованный доступ к чужим данным или обещания «полного root-like доступа» без отдельного осознанного канала.

### 3.3 Owner inputs (Approval gate)

- **Implementation scope:** трёхслойная capability-модель (standard ceiling / noLegal sandbox-plus overlay / noLegal privileged lane), storage-lane-centric контракт OTG/SD, SAF-делегирование записи на съёмных томах, API-ветвлённое определение корня тома и documentation contract; реализация - предмет последующего `/spec-tech`.
- **Product posture:** equal dual identity - file manager и media browser описываются как равноправные роли публичного `standard`.
- **Scope breadth:** первая implementation-волна покрывает local shared storage, OTG/SD и единые file-manager правила для network/cloud lanes.
- **Flavor boundary:** `standard` задаёт ceiling; `noLegal` - additive overlay поверх него; privileged/experimental lane - отдельная дочерняя epic-ветка с собственным trust/threat model.
- **Storage truth contract:** в `standard` доступны broad shared storage, OTG/SD roots и поддеревья, `Android/media` и user-selected SAF trees; `Android/data`, `Android/obb` и чужие app-private каталоги нельзя обещать как доступные.
- **noLegal aggressiveness:** baseline `noLegal` максимально агрессивен и включает SAF-доступ к `Android/data` и `Android/obb` с API-ветвлением; полный доступ к закрытым каталогам выносится в privileged lane.
- **Docs contract:** public storage surface идёт в `docs/FEATURES*` на уровне FEATURES + LIMITATIONS + HOW_TO/FAQ; `noLegal`-дельта - только в `docs/FEATURES_noLegal*`.
- **Validation level:** strategic spec без кода; будущие user-visible strings обязаны иметь EN/RU/UK parity, а UI-изменения - touch/keyboard/D-pad/mouse/TalkBack coverage.
- **Related tickets:** S0302 (File Manager Mode), S0376 (All Files predefined resource), S0082 (ChromeOS storage/SAF routing), S0156 (noLegal capability audit), S0336 (noLegal diagnostics), S0183 / S0298 (APK/package workflows).

---

## 4. Контекст текущей архитектуры

Текущий продукт уже умеет работать как гибрид media browser и file manager: у него есть локальные ресурсы, file operations, скрытые файлы, all-files resource mode, SAF-поддержка для выбранных деревьев, broad shared-storage permission rationale и отдельное файловое позиционирование в документации. Одновременно в проекте уже существует compile-time separation для `noLegal` как sideload-only capability surface.

Проблема не в отсутствии файловых механизмов как таковых. Проблема в том, что storage envelope не описан как иерархия слоёв. Сейчас рядом существуют `File Manager Mode`, `All Files` resource, `MANAGE_EXTERNAL_STORAGE`, SAF fallback, ChromeOS-specific routing, noLegal capability docs и отдельные sideload-only улучшения, но нет одной стратегической рамки, которая отвечала бы на вопрос: где заканчивается честный `standard` и где начинается допустимый `noLegal`-overlay.

Именно поэтому следующая tactical декомпозиция без этой рамки будет каждый раз заново спорить о базовых вещах: OTG через какой контракт, SD как volume или как SAF tree, `Android/media` как supported lane или special-case, APK workflows в публичной сборке или только в sideload, и нужен ли вообще privileged lane.

---

## 5. Предлагаемый подход

Ввести трёхслойную capability-модель для storage/file-manager surface:

1. **Layer A - Standard ceiling**: всё, что можно развивать в публичной сборке как file-manager-like product в рамках Android sandbox и market-safe distribution.
2. **Layer B - noLegal sandbox-plus overlay**: всё, что можно добавить поверх Layer A в sideload-only сборке, не ломая обычную Android security model.
3. **Layer C - noLegal privileged / experimental lane**: отдельный, не базовый и не обязательный слой для будущих обсуждений интеграций, которые требуют осознанного privileged bridge, root-like channel или отдельного trust model.

Эта спека не выбирает конкретные классы или implementation paths. Она задаёт product contract и platform boundaries для каждого слоя.

### 5.1 Основные столпы / модули

#### A. Standard ceiling - максимум в рамках Android platform + public distribution

- `standard` позиционируется по модели equal dual identity: file manager и media browser - равноправные роли, без подчинения файлового сценария media-подаче.
- В этот слой входит:
  - first-class local file-manager mode;
  - broad shared storage browsing;
  - OTG и SD volume discovery;
  - стабильная работа с user-granted SAF trees;
  - работа с `Android/media`, когда Android permission model это допускает;
  - единые file-manager правила для network/cloud lanes наравне с локальными в первой implementation-волне;
  - файловые операции над любыми видимыми типами файлов;
  - явное различение между "видно и можно управлять" и "можно открыть внутренним viewer".
- `standard` ceiling не должен зависеть от `noLegal`-компонентов, скрытых runtime bridge-ов или непубличных бинарников.

#### B. Standard OTG/SD contract

- OTG и SD в `standard` должны рассматриваться как first-class storage lanes, а не как экзотический edge-case.
- Product contract для них:
  - обнаружение и именование томов;
  - явный attach flow;
  - устойчивое повторное открытие ресурса после remount;
  - file-manager UX для root тома или user-chosen subtree;
  - честные degraded states при извлечении носителя, смене UUID, потере grant-а или read-only mount.
- Для OTG/SD следует считать нормой hybrid model:
  - direct path там, где Android и конкретный volume это позволяют;
  - SAF tree там, где platform-safe путь надёжнее;
  - единый user story поверх обоих технических маршрутов.
- При отсутствии `MANAGE_EXTERNAL_STORAGE` файловые операции записи, удаления и перемещения на съёмных томах прозрачно делегируются в SAF `DocumentFile` при наличии выданного пользователем tree URI на корень тома.

#### C. Android truth contract for standard

- `standard` должен открыто фиксировать, что Android разрешает, а что нет:
  - **доступно**: shared storage, `MediaStore`, OTG/SD roots или поддеревья, `Android/media`, user-selected SAF trees;
  - **ограничено / недоступно**: `Android/data`, `Android/obb`, чужие app-private dirs, generic unrestricted tree-picking в некоторых platform-zones.
- Permission copy, onboarding и docs должны объяснять не только how-to, но и hard limits.
- Это снижает риск ложных ожиданий и одновременно делает `MANAGE_EXTERNAL_STORAGE` объяснимым именно как file-manager permission, а не как "слишком широкое медиа-разрешение".

#### D. noLegal sandbox-plus overlay

- `noLegal` поверх `standard` может быть шире там, где blocker - не Android sandbox, а store policy, size budget, heavy runtime, sideload-only UX или package-facing functionality.
- В ядро этого слоя по storage/file-manager теме входят:
  - package workflows как ядро overlay: install / диагностика пакетов / companion APK flows;
  - richer APK-centric browsing and install handoff;
  - SAF-доступ к `Android/data` и `Android/obb` с API-ветвлением (прямой доступ на Android 8, обходной путь на Android 11+);
  - расширенная диагностика mounts / volumes / permissions / installer provenance;
  - heavier archive or converter stacks;
  - richer built-in handlers for file families, нежелательные в public build из-за distribution surface;
  - local-only diagnostics around storage state, removable media health, signing and sideload context.
- Этот слой остаётся в рамках обычной Android app sandbox; самые закрытые сценарии полного доступа к чужим каталогам выносятся в privileged lane (E).

#### E. noLegal privileged / experimental lane

- Privileged/experimental lane заранее резервируется как отдельная дочерняя epic-ветка `noLegal`, а не как опция внутри baseline.
- Этот слой не считается автоматически утверждённым. Его задача - изолировать идеи типа root/Shizuku/service-bridge от обычного `noLegal`, чтобы они не загрязняли baseline.
- Ветка несёт собственные trust model, threat model и explicit opt-in; продвижение по ней - отдельный осознанный канал с owner-signoff.

### 5.2 Потоки данных и событий

На стратегическом уровне storage story читается так:

1. Пользователь выбирает файловый сценарий.
2. Приложение определяет тип storage lane: shared local / OTG / SD / SAF tree / removable volume.
3. Storage layer подбирает platform-safe способ доступа.
4. Browse показывает единый file-manager UX независимо от того, direct path это или SAF grant.
5. File operations и viewers работают по capability matrix ресурса.
6. Если capability упирается в platform limit, продукт показывает truth-based limitation, а не скрытую техническую поломку.

Для `noLegal` поверх этого добавляется второй маршрут:

1. Сборка определяется как `noLegal`.
2. Включаются additive storage extensions и sideload-only diagnostics.
3. Базовая user story не меняется: сначала standard ceiling, потом additive overlay.
4. Privileged lane остаётся отдельным opt-in треком и не смешивается с baseline.

### 5.3 Точки расширяемости

- В будущем можно отдельно добавить tactical ветку для `DocumentsProvider` / system picker exposure, не меняя базовое разделение `standard` vs `noLegal`.
- OTG/SD discovery может расширяться от simple volume-listing к profile-driven resources и reusable templates.
- `noLegal`-overlay может расти по file-family handlers, diagnostics и package workflows без пересмотра `standard` ceiling.
- Privileged lane может существовать как отдельная дочерняя epic-ветка, не меняя public docs и не влияя на baseline acceptance criteria.

---

## 6. Решённые вопросы / Research items

1. **Product posture of standard**
   - **Вопрос:** должен ли `standard` публично и явно позиционироваться как file manager first-class, или файл-менеджерский сценарий остаётся одной из ролей внутри media-first продукта?
   - **Решение:** equal dual identity - media browser и file manager описываются как равноправные роли публичного `standard`, без приоритета одной подачи над другой.
   - **Статус:** Resolved

2. **Scope breadth for standard**
   - **Вопрос:** в первую tactical реализацию входят только local/OTG/SD/shared storage, или туда же сразу входят cloud/network consistency rules для file-manager contract?
   - **Решение:** local + removable + network/cloud parity - первая implementation-волна обязана покрыть локальное хранилище, OTG/SD и единые file-manager правила для network/cloud lanes.
   - **Статус:** Resolved

3. **noLegal baseline vs privileged lane**
   - **Вопрос:** должен ли будущий scope `noLegal` оставаться strictly sandbox-plus, или владелец хочет сохранить право на отдельную privileged ветку как follow-up research?
   - **Решение:** full separate future branch - privileged/experimental lane заранее выделяется как отдельная дочерняя epic-ветка с собственным trust/threat model, не смешанная с baseline `noLegal`.
   - **Статус:** Resolved

4. **APK/package workflows**
   - **Вопрос:** package-centric storage flows считать ядром `noLegal`-overlay или отдельной параллельной веткой рядом с storage surface?
   - **Решение:** part of storage overlay - package install, package diagnostics и companion APK flows входят в ядро `noLegal` storage overlay.
   - **Статус:** Resolved

5. **Docs breadth**
   - **Вопрос:** насколько глубоко публичные docs должны фиксировать `standard` storage limits и OTG/SD contract?
   - **Решение:** FEATURES + LIMITATIONS + HOW_TO/FAQ - первая documentation wave описывает возможности, честные platform limits и отдельный how-to/FAQ по OTG/SD и permissions.
   - **Статус:** Resolved

6. **System integration depth**
   - **Вопрос:** ограничиваться ли `APP_FILES` / permission rationale / resource UX, или позже включать более широкую Android system integration around file management?
   - **Решение:** moderate - `APP_FILES`, permission rationale и resource UX плюс разумные точки входа; `DocumentsProvider` / system picker exposure в эту волну не входит.
   - **Статус:** Resolved

7. **Динамическое обнаружение и мониторинг томов (SD-карты, OTG)**
   - **Вопрос:** должен ли `standard` динамически отслеживать подключение и отключение SD-карт/USB OTG и отображать их статус (объём, свободное место) в реальном времени?
   - **Решение:** опрос томов только при ручном обновлении списка; реактивный мониторинг через `BroadcastReceiver`/`StorageManager` в первую фазу не входит.
   - **Статус:** Resolved

8. **Альтернативная запись на SD/OTG без MANAGE_EXTERNAL_STORAGE**
   - **Вопрос:** как выполнять операции записи на съёмные носители в `standard`, если разрешение `MANAGE_EXTERNAL_STORAGE` отклонено или запрещено политиками магазина?
   - **Решение:** делегировать операции записи через SAF `DocumentFile` при наличии выданного пользователем tree URI (Proposal P-1 принят).
   - **Статус:** Resolved

9. **Использование стабильного API StorageVolume для получения пути**
   - **Вопрос:** следует ли заменить скрытый рефлексивный вызов `StorageVolume.getPath()` на официальный метод `StorageVolume.getDirectory()` для Android 11+?
   - **Решение:** API-ветвление - официальный `getDirectory()` на современных Android (API 30+) и legacy fallback `getPath()` для нижней границы поддержки (Android 8, API 26); обе ветки обязаны работать (Proposal P-2 принят).
   - **Статус:** Resolved

10. **Ограниченные папки Android/data и Android/obb в noLegal**
    - **Вопрос:** должен ли базовый `noLegal`-overlay предоставлять доступ к закрытым системным каталогам `Android/data` и `Android/obb` через известные обходные пути Android 11+?
    - **Решение:** `noLegal` держится максимально агрессивно - базовая SAF-интеграция к этим каталогам входит в sandbox-plus с API-ветвлением (прямой доступ на Android 8, обходной путь на Android 11+); самые закрытые сценарии полного доступа выносятся в отдельную privileged/experimental ветку.
    - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| `standard` переобещает доступ, которого Android не даёт | Высокая | Пользователь ожидает доступ к protected dirs и считает приложение сломанным | Жёстко отделить broad shared storage от app-private zones во всех contracts и docs |
| `standard` останется недоусиленным из-за страха перед Play policy | Средняя | Продукт не использует разрешённый OTG/SD/shared-storage потенциал | Зафиксировать отдельный public-safe ceiling и file-manager justification |
| `noLegal` начнёт течь в market-flavors | Средняя | Capability hints, strings или зависимости попадут в public surface | Compile-time isolation и отдельный docs contract |
| OTG/SD реализация окажется слишком path-centric | Средняя | Поведение будет нестабильно между OEM и Android versions | Делать removable-media contract storage-lane-centric, а не path-centric |
| Privileged ideas загрязнят базовый `noLegal` scope | Средняя | Tactical план станет слишком широким и спорным | Держать privileged lane отдельным и optional |
| Storage docs станут противоречивыми между public и noLegal surface | Средняя | Пользователь не поймёт, что относится к его сборке | Жёстко разделить public docs и `FEATURES_noLegal*.md` |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации публичные `docs/FEATURES*.md` должны описывать усиленный `standard` file-manager surface для shared storage, OTG и SD с честными platform limits, а `docs/FEATURES_noLegal*.md` - отдельно фиксировать sideload-only storage extensions поверх этого базового слоя.

---

## 9. Архитектурные решения (ADR)

**ADR-1: `standard` ceiling определяется Android platform truth, а не `noLegal` backlog-ом**

- **Решение:** сначала фиксируется максимум, который честно и устойчиво можно дать в `standard`.
- **Альтернативы:** проектировать от `noLegal` вниз или держать `standard` intentionally weaker.
- **Почему:** иначе platform-safe потенциал shared storage, OTG и SD будет постоянно недоиспользован.

**ADR-2: `noLegal` - additive overlay, а не другая базовая продуктовая реальность**

- **Решение:** любой `noLegal` storage feature сначала проверяется вопросом "что уже должен уметь `standard`?".
- **Альтернативы:** развести два почти независимых продукта.
- **Почему:** это удерживает понятную capability hierarchy и снижает расхождение UX/docs.

**ADR-3: Privileged storage ideas выносятся в отдельный lane**

- **Решение:** root/Shizuku/service-bridge-подобные идеи не попадают в baseline `noLegal`.
- **Альтернативы:** смешать privileged и обычный sideload surface в одном контракте.
- **Почему:** privileged trust model требует другого уровня риска, поддержки и owner-signoff.

**ADR-4: OTG/SD в `standard` должны строиться вокруг storage lanes и grants, а не вокруг набора жёстко прошитых путей**

- **Решение:** removable media рассматриваются как first-class storage lanes с volume identity, remount behavior и SAF/direct-path strategy.
- **Решение:** корень тома резолвится через официальный `StorageVolume.getDirectory()` на API 30+ и через legacy `getPath()` на нижней границе поддержки (Android 8, API 26); обе ветки обязательны.
- **Альтернативы:** path-first heuristics как главный контракт; единственный рефлексивный путь без API-ветвления.
- **Почему:** это даёт более устойчивую модель на разных OEM, Android versions и form factors и убирает зависимость от скрытого reflection на целевом SDK 35.

**ADR-5: Documentation contract обязан разделять три вещи - visibility, manageability и openability**

- **Решение:** во всех будущих описаниях отдельно различать "файл виден", "с ним можно выполнять операции" и "он открывается внутренним viewer".
- **Альтернативы:** использовать одно расплывчатое "поддерживается".
- **Почему:** это ключевой источник путаницы в file-manager сценариях.

---

## 10. Связи с другими спеками

- **S0302** - File Manager Mode и Android-позиционирование файлового сценария.
- **S0376** - отдельный predefined resource `All Files` / file-manager entry point.
- **S0082** - ChromeOS storage behavior, SAF routing и desktop-like Android environment.
- **S0156** - umbrella audit `noLegal` capability surface; новый тикет берёт из него storage/file-manager подмножество.
- **S0336** - noLegal-only diagnostics, полезные как часть будущего storage overlay.
- **S0183 / S0298** - package / APK workflows как смежная sideload-only ветка.

---

## 11. Критерии готовности (strategic-level)

1. Спека фиксирует отдельный и понятный `standard` ceiling для file-manager surface, включая OTG/SD/shared storage.
2. Спека явно отделяет platform-safe возможности `standard` от platform-forbidden зон, которые нельзя обещать даже в максимально усиленном обычном приложении.
3. Спека описывает `noLegal` как additive storage overlay поверх `standard`, а не как расплывчатое "там можно всё".
4. Внутри `noLegal` отдельно выделен optional privileged/experimental lane, не смешанный с baseline sideload surface.
5. Зафиксирован documentation contract: public storage surface идёт в `docs/FEATURES*.md`, `noLegal`-дельта - только в `docs/FEATURES_noLegal*.md`.
6. Связь со смежными спеками (`File Manager Mode`, `All Files` resource, ChromeOS, noLegal audit) описана так, чтобы следующий tactical слой не делал platform research заново.
7. После owner approval из этой спеки можно строить `/spec-tech` без повторного спора о базовой иерархии `standard` vs `noLegal`.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0379` - создаст `PLAN/S0379_standard-nolegal-storage-surface/` с фазами после owner approval gate.

---

## Proposed Structural Changes

### Proposal P-1 - Интеграция SAF-делегирования для записи на съемные носители в `standard` (proposed 2026-06-07 by Gemini 3.5 Flash)

**Status:** Accepted
**Affected:** Раздел 5.1 (B. Standard OTG/SD contract), ADR-4
**Rationale:** В публичной сборке `standard` разрешение `MANAGE_EXTERNAL_STORAGE` может быть отклонено пользователем или не пропущено Google Play. Для сохранения возможности записи на SD-карты и USB OTG необходима поддержка операций через Storage Access Framework (SAF) с использованием `DocumentFile`.
**Suggested edit:**
> Добавить в секцию B. Standard OTG/SD contract явное требование: "При отсутствии MANAGE_EXTERNAL_STORAGE файловые операции (запись, удаление, перемещение) на съёмных томах должны прозрачно делегироваться в SAF DocumentFile API при наличии выданных разрешений на корень тома."

### Proposal P-2 - Переход от рефлексивного getPath к StorageVolume.getDirectory() (proposed 2026-06-07 by Gemini 3.5 Flash)

**Status:** Accepted
**Affected:** Раздел 9 (ADR-4)
**Rationale:** В Android 11+ (API 30+) появился официальный метод `StorageVolume.getDirectory()`. Использование рефлексивного вызова `getPath()` на современных версиях Android (включая целевой targetSdk 35) снижает стабильность и может приводить к сбоям.
**Suggested edit:**
> Добавить в ADR-4: "На Android 11+ (API 30+) для определения корня тома использовать официальный API `StorageVolume.getDirectory()`, сохраняя рефлексивный fallback `getPath()` только для API < 30."

## Last Audit

**Date:** 2026-06-07
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 19 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 0

### Manual / on-device

- [ ] SAF-tree copy/move to OTG/SD writes child documents when no writable direct path exists.
- [ ] noLegal build allows restricted-tree (`Android/data`/`Android/obb`) targets; standard build keeps the conservative default.
- [ ] `standardDebug` and `noLegalDebug` both build.

## Revision History

- **2026-06-07** - by `/spec-update` (Gemini 3.5 Flash, focus: language, structure, completeness)
  - Applied: 0. Proposed (DISCUSS): 2.
- **2026-06-07** - by `/spec-update` (Claude Opus 4.8, focus: completeness, style, consistency)
  - Resolved all 10 section-6 questions with owner answers; filled Approval Gate Autonomy rule; accepted Proposals P-1/P-2 and folded them into §5.1 B + ADR-4; reflected scope decisions in §5.1 A/D/E; converted §5.2 flow arrows to numbered lists; normalized em-dashes to hyphens; removed class-path reference from P-2. Applied: 16. Proposed (DISCUSS): 0.
  - Promoted `Draft` -> `Approved` per owner Autonomy rule (approval-gate style sanitation applied before flip).
