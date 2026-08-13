# Стратегическая спецификация: S0448 - Гейтирование локально-сетевых источников по флейворам

**Ticket:** S0448
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-16
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено при device-test S0035 (2026-06-15)
**Tactical spec:** `PLAN/S0448_photos-flavor-exposes-network-sources/`
**Tactical plan:** `PLAN/S0448_photos-flavor-exposes-network-sources/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Сетевые источники (SMB/SFTP/FTP) доступны во всех флейворах одинаково, потому что для них нет ни одного флага сборки - они не гейтятся нигде. Флейвор `lite` позиционируется как «только локальные файлы», но фактически показывает вкладки типов ресурсов и пункты добавления сетевых папок наравне со `standard`, а заодно тянет за собой runtime-пермишн локальной сети, который этому флейвору не нужен. Это вводит пользователя `lite` в заблуждение и расходится с заявленным назначением сборки.

Обнаружено при device-test S0035 на `photos`-сборке: набор вкладок и «Select Folder Type» совпадает со `standard`, а welcome-флоу «Включить всё» запрашивает пермишн локальной сети. Для `photos` такое поведение признано корректным (там намеренно включено облако для бэкапа фото), а для `lite` - нет.

---

## 2. Цели

1. Появляется единый продуктовый признак доступности локально-сетевых источников, задаваемый на уровне флейвора.
2. В `lite` исчезают вкладки и пункты добавления SMB/SFTP/FTP - флейвор становится действительно «только локальные файлы».
3. `lite` перестаёт объявлять и запрашивать пермишн локальной сети.
4. В остальных флейворах (`standard`, `noLegal`, `vr`, `legacy`, `photos`) поведение сетевых источников не меняется.
5. Текст S0035 приводится в соответствие: ложная премисса «`photos` без сетевых источников» снимается, `photos` сохраняет сеть.

**Non-goals:**

- Облако не трогаем - его доступность по-прежнему определяется отдельным существующим механизмом.
- Не меняем поведение сетевых источников в full-featured флейворах.
- Не вводим пользовательский тумблер сети в настройках - это решение уровня сборки, а не рантайма.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Скрытие должно быть «чистым»: ни вкладок, ни пунктов меню, ни мёртвых веток welcome-флоу.
2. Минимизировать дублирование: переиспользовать существующий приём enabled/disabled source-set, как у потоковой передачи и облака.

### 3.2 Жёсткие ограничения

- **Flavor:** меняется только `lite` (сеть OFF); `standard`/`noLegal`/`vr`/`legacy`/`photos` - без изменений. Реализация следует `dev/FLAVOR_DEVELOPMENT_RULES.md`: интерфейс в `src/main/` + включённая реализация и NoOp в соответствующих flavor source-set, флейвор-специфичный Hilt-модуль. Запрещён `BuildConfig.IS_*`-гейт внутри `src/main/` (CLAUDE.md Rule 15).
- **API level:** без API-специфики; затрагивается лишь объявление и запрос runtime-пермишна локальной сети, без привязки к уровню Android.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично.
- **Совместимость данных:** ранее добавленные пользователем сетевые ресурсы в `lite` перестают быть видимыми/добавляемыми - судьба таких записей определяется в §6.
- **Локализация:** новых строк не предполагается; если элемент скрывается - EN/RU/UK затрагиваются согласованно.
- **Доступность:** скрываемые элементы убираются из порядка фокуса (D-pad/TV/клавиатура), не остаются недостижимыми фокус-целями.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** сеть OFF только в `lite`; ON в `standard`/`noLegal`/`vr`/`legacy`/`photos`. Подтверждено владельцем 2026-06-16.
- **UI placement contract:** вкладки типов ресурсов и пункты «Network Folder (SMB)» / «SFTP/FTP» в Add Resource скрываются в `lite` целиком; в остальных флейворах без изменений.
- **Accessibility:** скрытые элементы удаляются из порядка фокуса, не превращаются в невидимые фокус-цели.
- **Validation level:** сборка + ручная проверка `lite` debug (нет вкладок/пунктов сети, welcome не просит пермишн) и регресс `photos`/`standard` (сеть на месте).
- **Owner sign-off:** 2026-06-16.
- **Related tickets:** S0035 - этим тикетом корректируется его премисса про `photos` и про пермишн локальной сети.

---

## 4. Контекст текущей архитектуры

Состав источников (локальные, SMB, SFTP/FTP, облако) формируется общим UI-слоем главного экрана и потоком добавления ресурса; сейчас он одинаков для всех флейворов, потому что включение сетевых источников нигде не гейтится. Облако уже отделено собственным механизмом доступности (enabled/disabled source-set), а сеть - нет, поэтому погасить её в одном флейворе сейчас нечем. Объявление и запрос пермишна локальной сети тоже живут в общем слое и срабатывают независимо от флейвора.

---

## 5. Предлагаемый подход

Ввести флейвор-зависимую доступность локально-сетевых источников по тому же образцу, что уже применён для потоковой передачи и облака: общий интерфейс-провайдер в основном модуле, включённая реализация по умолчанию и NoOp-реализация для флейвора без сети. UI типов ресурсов, поток добавления ресурса и объявление/запрос пермишна локальной сети опираются на этот провайдер вместо безусловного показа.

### 5.1 Основные столпы / модули

1. **Провайдер доступности сетевых источников** - единая точка ответа «доступны ли SMB/SFTP/FTP в этой сборке». Цель: убрать прямую безусловную видимость сетевых источников из общего слоя.
2. **Гейт UI типов ресурсов** - вкладки и пункты добавления сетевых папок отображаются только когда провайдер разрешает.
3. **Гейт пермишна локальной сети** - объявление в манифесте и запрос в welcome-флоу присутствуют только в сетевых флейворах.

### 5.2 Потоки данных и событий

UI главного экрана / поток добавления ресурса → провайдер доступности сети → разрешено: показать вкладки и пункты SMB/SFTP/FTP; запрещено: скрыть. Welcome-флоу «включить всё» → провайдер → запрашивать пермишн локальной сети только если сеть доступна.

### 5.3 Точки расширяемости

- Вводимый интерфейс провайдера доступности сетевых источников - абстракция флейвор-изоляции; включённая реализация мостится во все сетевые флейворы через enabled source-set, NoOp - в `lite` через disabled source-set (по образцу cloudEnabled/cloudDisabled, streamingEnabled/streamingDisabled).
- Матрица «флейвор → доступность сети» остаётся единственной точкой, которую правят при изменении состава флейворов.

---

## 6. Открытые вопросы / Research items

1. **Судьба уже добавленных сетевых ресурсов в `lite`**
   - **Вопрос:** что делать с записями SMB/SFTP/FTP, которые пользователь мог добавить в `lite` до гейтинга?
   - **Решение:** недеструктивное скрытие - существующая фильтрация по провайдеру (`gate.isEnabled`) убирает записи из показа без новой логики; миграции/удаления нет.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0448_photos-flavor-exposes-network-sources/research/01__lite-existing-network-records.md`

2. **Способ скрытия пермишна локальной сети в `lite`**
   - **Вопрос:** объявление пермишна убирается через flavor-манифест `lite` или остаётся в общем манифесте, а гейтится только запрос?
   - **Решение:** два слоя - `tools:node="remove"` в `src/lite/AndroidManifest.xml` (первичный, store-clean) плюс flavor-gate на записи реестра пермишнов (защита UI-запроса).
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0448_photos-flavor-exposes-network-sources/research/02__local-network-permission-gating.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Скрытие вкладок ломает индексацию позиций вкладок в `lite` | Средняя | рассинхрон сохранённой выбранной вкладки | строить список вкладок динамически от провайдера, не по фиксированным индексам |
| Пермишн остаётся объявленным в `lite` по недосмотру | Средняя | непрошеный пермишн, риск стор-ревью | проверять манифест `lite` в критериях готовности |
| Случайно погашена сеть в full-featured флейворе | Низкая | пользователи `standard` теряют SMB/SFTP | дефолт провайдера «включено», disabled только в `lite` |

---

## 8. Влияние на пользователя (docs/FEATURES)

`lite` перестаёт показывать сетевые источники - это видимое изменение возможностей для пользователей `lite`. Обновить `docs/FEATURES.md` + `_RU` + `_UK`: уточнить, что `lite` работает только с локальными файлами (без SMB/SFTP/FTP). Для остальных флейворов изменений нет.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Гейт на уровне сборки, а не рантайм-настройки.**

- **Решение:** доступность сети определяется флейвором через source-set провайдер, без пользовательского тумблера.
- **Альтернативы:** рантайм-настройка / feature-flag; `BuildConfig`-проверка прямо в `src/main/`.
- **Почему:** соответствует существующему приёму streaming/cloud и правилу флейвор-изоляции (Rule 15); состав источников - свойство дистрибутива, а не пользовательский выбор.

---

## 10. Связи с другими спеками

- **S0035** (android17-local-network-permission): этот тикет снимает с S0035 ложную премиссу «`photos` без сети» и фиксирует, что пермишн локальной сети не нужен в `lite`. После реализации текст S0035 §2.6 / §3.2 / §11.8 корректируется.

---

## 11. Критерии готовности (strategic-level)

1. В `lite`-сборке на главном экране нет вкладок SMB и S/FTP.
2. В `lite` Add Resource «Select Folder Type» не предлагает Network Folder (SMB) и SFTP/FTP.
3. В `lite` welcome-флоу «включить всё» не запрашивает пермишн локальной сети.
4. Манифест `lite` не объявляет пермишн локальной сети.
5. В `standard` и `photos` сетевые источники и их добавление присутствуют как прежде.
6. `docs/FEATURES` (EN/RU/UK) отражает, что `lite` - только локальные файлы.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0448` - создаст `PLAN/S0448_photos-flavor-exposes-network-sources/` с фазами.

---

## Last Audit

**Date:** 2026-06-17
**Mode:** full (strategic + 3 phases)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 17 · WARN 0 · FAIL 0 · MANUAL 6 · EXEMPT 0

### Static contract (step 5.2 of /spec-sweep)

- Phase 01: `SUPPORT_LOCAL_NETWORK` = 6 hits in build.gradle.kts (5 `true` + 1 `false` in lite); `supportsLocalNetworkSources` on `MediaCapabilities` (no default); wired in 5 flavor modules (standard/lite/photos/legacy/vr - vr serves noLegal); gate `compileSupported` reads capability for NETWORK ids. PASS.
- Phase 02: `fun isNetworkGroupSupported()` declared once; welcome controller gates SMB+FTP rows on it (GONE when unsupported); `flavorGates = setOf("SUPPORT_LOCAL_NETWORK")` on the `access_local_network` registry entry; `ACCESS_LOCAL_NETWORK` + `tools:node="remove"` in `src/lite/AndroidManifest.xml`. PASS.
- Phase 03: FEATURES EN/RU/UK all state lite is local-files-only (no SMB/FTP/SFTP), lockstep. PASS. S0035 reconciliation step is a documented no-op (S0035 Archived in `temp/done/`); corrected matrix recorded in §10. EXEMPT-equivalent, no gap.
- Phase status consistency: INDEX 3/3 Done, all phase headers ✅ Done. PASS.
- Debug-tag invariant: 1 `Timber.d("S0448:` tag verified present under BlockNeedUserTest; deleted on this Verified flip (WelcomeRemoteSourcesController + now-unused Timber import). PASS.
- `app_v2/build.gradle.kts` working tree clean (device-runner temp edits restored). PASS.

### Device evidence (step 5.1)

**Type:** Manual / on-device flavor-comparison device-test
**Device:** emulator-5554 (phone, x86_64, Android 17 / SDK 37)
**Verdict:** PASS
**Builds:** `assemblePhotosDebug` + `assembleLiteDebug` via project builders (`scripts/builders/build-{photos,lite}-device.ps1`); `standardDebug` already installed as regression baseline.
**Evidence:** `temp/S0448_devtest/`

### PHOTOS flavor - network ON (corrected premise: photos keeps network)

- Main screen tab row - expected: SMB + S/FTP tabs present; actual: `tabResourceTypes` = ALL / Local / **SMB** / **S/FTP** / Cloud. PASS. (`photos_main_tabs.png`)
- Add Resource "Select Folder Type" - expected: Network Folder + SFTP/FTP cards offered; actual: Local Folder + **Network Folder (Add SMB network shares)** + **SFTP / FTP** + Cloud Storage cards all shown. PASS. (`photos_add_resource.png`)
- Welcome "Network sources" page - expected: SMB / (S)FTP rows shown; actual: `rowSourceSmb` (Local network SMB) + `rowSourceFtp` ((S)FTP) + `rowSourceCloud` rows visible with toggles. PASS. (`photos_welcome_network_page.png`)
- S0448 welcome probe - expected: fires with network supported; actual: `S0448: welcome network rows bind - networkGroupSupported=true, cloudGroupSupported=true`. PASS. (`probe_photos.log`)
- Merged manifest `ACCESS_LOCAL_NETWORK` - expected: present; actual: present (count=1) in `merged_manifests/photosDebug/.../AndroidManifest.xml`. PASS.

### LITE flavor - network OFF (negative case)

- Main screen tab row - expected: no SMB / S-FTP tabs; actual: `tabResourceTypes` element **absent entirely** (no tab strip at all). PASS. (`lite_main_no_tabs.png`)
- Add Resource - expected: no Network Folder / SFTP-FTP offered; actual: folder-type chooser bypassed, opens directly into "Add Local Folder" (SCAN / ADD MANUALLY), no Network/SFTP/Cloud cards. PASS. (`lite_add_resource_local_only.png`)
- Welcome "Network sources" page - expected: no SMB / (S)FTP rows, no permission request; actual: page header present but all three source rows GONE (no toggles). PASS. (`lite_welcome_network_page_empty.png`)
- S0448 welcome probe - expected: gate reports network unsupported; actual: `S0448: welcome network rows bind - networkGroupSupported=false, cloudGroupSupported=false`. PASS. (`probe_lite.log`)
- Merged manifest `ACCESS_LOCAL_NETWORK` - expected: absent; actual: absent (count=0) in `merged_manifests/liteDebug/.../AndroidManifest.xml`, removed via `tools:node="remove"` in `src/lite/AndroidManifest.xml`. PASS.

### STANDARD flavor - regression spot-check

- Merged manifest `ACCESS_LOCAL_NETWORK` - expected: present (unchanged); actual: present (count=1) in `merged_manifests/standardDebug/.../AndroidManifest.xml`. PASS. (`merged_manifest_audit.txt`)

### Out-of-scope finding parked

- `parked: S0477 lite-default-player-bootstrap-missing-components` - on `lite` startup `DefaultPlayerStateBootstrapper` toggles player/share components (`StandaloneAudioSender`, `MediaButtonRestartReceiver`) that the lite manifest removes, throwing `IllegalArgumentException` and showing a red ERROR toast on the welcome screen. Unrelated to network gating. Evidence: `temp/S0448_devtest/oos_defaultplayer_error.log`.
