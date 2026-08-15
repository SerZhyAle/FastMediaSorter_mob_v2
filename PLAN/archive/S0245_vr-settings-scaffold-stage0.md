---
ticket: S0245
status: Verified
priority: 85
date: 2026-05-18
tier: 2
---

<!-- auto-approved by /spec-all — 2026-05-18 -->

# S0245 — Этап 0: каркас настроек VR + master toggle

**Ticket:** S0245
**Status:** Verified
**Tactical plan:** `PLAN/S0245_vr-settings-scaffold-stage0/INDEX.md`
**Priority:** 85
**Date:** 2026-05-18
**Tier:** 2 — конкретный реализационный шаг (тонкий каркас, без реальной VR-функциональности)
**Roadmap entry:** `S0240 §10.1` — Этап 0.
**Depends on:**
- `S0241` (`vr-stack-removal-plan`) — должен быть `Verified` (старый VR-стек удалён, площадка чиста).
- `S0244` (`vr-preliminary-research`) — минимально `R-02` (структура Settings), `R-05` (manifest/flavor history из git), `R-06` + `R-07` (XR manifest declarations) должны быть зафиксированы в `RESEARCH.md`.
**Blocks:** Этап 1 (видео в иммерсии, отдельный `Sxxxx` после `Verified` S0245).

---

## 1. Цель

Подготовить инфраструктуру для появления VR-функций в UI приложения, **без единой реальной VR-возможности**. После `Verified` S0245:

- Существует выделенный `vr` flavor source set (после S0241 — пустой; здесь оживает).
- В Settings-экране VR-сборки есть **новый блок «VR»** сразу после блока «Video».
- В блоке — **только** master toggle «Включить 3D VR» (точная строка — `/ui-clarify`).
- Все остальные controls внутри блока — задел на следующие этапы (пустой контейнер с placeholder-ом, без отдельных preference-entries).
- В `standard`/`lite`/`photos`/`legacy` блок **отсутствует физически** (Fragment живёт в `src/vr/`, не компилируется в эти flavor-ы).
- В `vr` / `noLegal` блок есть (наследование через `standard` ⊂ `vr` ⊂ `noLegal`).
- Из `src/main/` через контракт `XrEntryGateway`/`XrDetectionFacade` любая часть приложения может спросить: «есть VR + он включён?» — и получить честный ответ.

---

## 2. Что НЕ делает этот этап

- Не запускает OpenXR. Не создаёт XR-сессию. Не рендерит ничего в 3D.
- Не добавляет ни одной VR-entry-кнопки (кнопка «Immerse» — это Этап 1, отдельный тикет).
- Не реализует hand-tracking, controller mapping, HUD, panel.
- Не подключает Meta XR SDK / OpenXR loader (это делает следующий этап с реальной OpenXR-инициализацией).
- Не реализует cloud-OAuth caveat-ы для Quest (это всплывёт на этапах, где открываются конкретные cloud-провайдеры в VR).
- Не правит ничего в `src/standard/`, `src/lite/`, `src/photos/`, `src/legacy/` — там VR не существует.

---

## 3. Объём изменений (поверхностно — точно режется `/spec-tech`)

### 3.1. Контрактные интерфейсы в `src/main/`

Создаются **два** интерфейса в существующем пакете `core/xr/` (или адекватная альтернатива по `R-02`):

- `XrEntryGateway` — фасад «запустить VR-сессию» для будущих этапов. На Этапе 0 имеет один метод-заглушку, который возвращает «недоступно» в no-op-реализации.
- `XrDetectionFacade` — фасад «есть ли VR на устройстве + включён ли master toggle». Возвращает enum-состояние (минимум: `NONE`, `AVAILABLE_DISABLED_BY_USER`, `AVAILABLE_ENABLED`).

No-op-реализации обоих интерфейсов лежат в `src/main/` (или `src/standard/` — решается `R-02`) и всегда возвращают «VR недоступен».

### 3.2. Реальные реализации в `src/vr/`

- `RealXrEntryGateway` — на Этапе 0 тоже-stub: возвращает «недоступно», потому что иммерс ещё не реализован. Существует, чтобы тестировать Hilt-binding и flavor isolation.
- `RealXrDetectionFacade` — настоящая логика: читает state `XrEnvironment` (см. §3.4) + master-toggle preference, возвращает корректный enum.

### 3.3. Hilt модули

- В `src/main/` (или `src/standard/`) — модуль, биндящий no-op-реализации (фолбэк-вариант для не-VR flavor-ов).
- В `src/vr/` — модуль, биндящий `Real*`-реализации; в multibinds / `@Replaces`-стратегии замещает дефолт.
- Точная стратегия (`@BindsOptionalOf`, разные Hilt-компоненты, или просто разные modules per flavor) — `R-02` + `/spec-tech`.

### 3.4. `XrEnvironment` (single source of truth про устройство)

Минимальная реализация для Этапа 0 — детектит:
- `NONE` — обычный Android-телефон, никакого XR runtime.
- `VR_QUEST` — Meta Quest (любой из Quest 2 / 3 / 3S / Pro по §11 Q2).
- `ANDROID_XR` — устройство / эмулятор Android XR.

Источник детекта — `PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE` + специфичные feature-флаги Quest и Android XR из `R-06` / `R-07`. Реализация живёт в `src/vr/`, в `src/main/` no-op возвращает `NONE`.

### 3.5. VR-блок в Settings

- Новый Fragment `VrSettingsBlockFragment` (или имя по `R-02`) в `src/vr/`.
- Подключается в Settings-экране **сразу после** существующего блока «Video». Способ подключения — Preference-fragment, отдельный PreferenceCategory, или Fragment-embed; точный механизм — `R-02`.
- Внутри блока на Этапе 0 — единственный preference: master toggle «Включить 3D VR».
- Содержимое блока сворачивается / скрывается, если `XrDetectionFacade` вернул `NONE` (мы на телефоне) — блок может вообще не появляться. Финальное поведение (всегда показывать с disabled-state или скрывать) — `/ui-clarify`.

### 3.6. Master toggle

- Preference-key: `pref_vr_enable_3d` (имя — TBD по `R-02` для согласованности с существующими keys).
- Storage: DataStore vs SharedPreferences — `R-02` + `/spec-tech`. Скорее всего DataStore, если приложение уже мигрировано.
- **Default ON**, если `XrDetectionFacade` при первом запуске опознал `VR_QUEST` или `ANDROID_XR`.
- **Default OFF** на всех остальных устройствах (включая `NONE`).
- Когда **OFF** — `XrDetectionFacade.state == AVAILABLE_DISABLED_BY_USER` (или `NONE`, если устройство не XR); ни одна часть приложения не показывает VR-entry.
- Когда **ON** — `XrDetectionFacade.state == AVAILABLE_ENABLED`; в Settings-блоке открываются placeholder-secondary-controls (на Этапе 0 пустые — задел для Этапа 1+).

### 3.7. Строки

- EN/RU/UK для:
  - Заголовка блока «VR».
  - Master toggle label («Включить 3D VR» — рабочий вариант; финал — `/ui-clarify` + `docs/COMMUNICATION_POLICY*.md`).
  - Master toggle summary («Показывать VR-функции в приложении» — рабочий вариант).
- После добавления — `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"` (или конкретный префикс — TBD).

### 3.8. Manifest / build.gradle

- `src/vr/AndroidManifest.xml` — добавляется manifest-фрагмент с базовыми XR-категориями (точное содержимое — `R-06` + `R-07`); на Этапе 0 это **только декларация устройства**, без launcher-категории `com.oculus.intent.category.VR` (она появится на этапе, где включается реальный XR-host).
- `app_v2/build.gradle.kts` — добавляется / восстанавливается `vr` (и `vrUnlicensed` по `R-09`) productFlavor, источники, зависимости (на Этапе 0 — пусто, OpenXR loader подключается позже).

---

## 4. UI (выносится на `/ui-clarify` перед `/spec-dev`)

Эти вопросы блокируют implementation до прохождения `/ui-clarify`:

- Точный финальный текст master toggle (EN/RU/UK).
- Иконка блока «VR» в Settings (использовать ли существующий VR-иконочный шрифт / Material Icons).
- Поведение блока на не-VR устройствах: скрыть полностью или показать disabled с пояснением.
- Состояние placeholder-controls внутри блока на Этапе 0 — пустой PreferenceCategory или один disabled-preference «Дополнительные настройки появятся по мере включения VR-функций».
- Точная позиция блока в Settings: сразу после Video или после Display + Video.
- Поведение, если устройство сменилось (debug-сборка переехала с Quest 3 на телефон) — сбросить toggle в default или сохранить пользовательский выбор.

---

## 5. Hilt / DI / архитектура

Соответствие правилу 15 (flavor isolation, `dev/FLAVOR_DEVELOPMENT_RULES.md`):

- Никаких `if (BuildConfig.SUPPORT_VR_PLAYER)` в `src/main/`.
- Никаких `BuildConfig.IS_*_FLAVOR` веток в `src/main/`.
- `XrEntryGateway` / `XrDetectionFacade` — единственные точки соприкосновения VR с `main`.
- VR-зависимости (OpenXR loader, Meta XR SDK) подключаются **только** в `vr` / `vrUnlicensed` productFlavor — на Этапе 0 они вообще не подключены (это Этап 3+).

---

## 6. Тестирование

- **Unit-тесты:**
  - `XrEnvironment` — поведение на mocked `PackageManager` (3 кейса: NONE, VR_QUEST, ANDROID_XR).
  - `XrDetectionFacade` — комбинации (XrEnvironment × master toggle preference): 6 кейсов.
  - No-op-реализации `XrEntryGateway` / `XrDetectionFacade` всегда возвращают «недоступно» — тривиальный тест.
- **Compile-проверки:**
  - `assembleStandardDebug` — собирается, в APK нет ни одного VR-класса (проверка через `unzip -l <apk>` + grep).
  - `assembleVrDebug` — собирается; класс `VrSettingsBlockFragment` присутствует в APK.
- **Manual on-device gate (BlockNeedUserTest):**
  - Quest 3: открыть Settings → видим блок «VR» сразу после «Video» → master toggle = ON по default → выключить → проверить, что нигде в приложении не появляется ничего «VR-ного».
  - Android XR emulator: то же самое.
  - Телефон (`standard`-сборка): открыть Settings → блока «VR» нет вообще.
- **Логкат-маркер:** `Timber.d("S0245: XrEnvironment=<state>")` в `RealXrDetectionFacade` на инициализации, и `Timber.d("S0245: master toggle changed -> <bool>")` при изменении. Удаляются при выходе из `BlockNeedUserTest`.

---

## 7. Критерий завершения (`Verified`)

- Сборка `vr` (`vrUnlicensed` по результату `R-09`) и `noLegal` собирается успешно. `standard` собирается успешно и НЕ содержит VR-классов.
- На Quest 3 и в Android XR emulator: Settings → блок «VR» сразу после «Video»; master toggle есть, default ON, изменение состояния сохраняется между запусками.
- На телефоне (`standard`): блок «VR» отсутствует в Settings; никаких VR-классов в APK.
- Unit-тесты по §6 проходят.
- `Timber.d("S0245: ...")`-маркеры наблюдены в логкате на обоих VR-таргетах, после `Verified` маркеры удалены.
- `dev/CHANGELOG.md` + `dev/FUNCTIONALITY.log` обновлены (если новая user-visible capability — Data toggle в Settings под VR-сборкой, это `ADD`).
- `docs/FEATURES*.md` — **не обновляются** на Этапе 0 (нет user-visible capability ещё: master toggle не делает ничего видимого, кроме разрешения будущим этапам появляться).
- `STORE_TODO.md` пополнен пунктами, всплывшими при добавлении manifest-фрагмента (если есть).

---

## 8. Открытые вопросы

- Имя `vr`-flavor: оставляем `vr` / `vrUnlicensed` или переименовываем (`R-09`).
- Точное место `XrEntryGateway` / `XrDetectionFacade` в пакетной структуре `src/main/java/.../core/xr/` (`R-02`).
- Способ embed-а Fragment-а блока в Settings-экран — Preference fragment vs обычный Fragment + container (`R-02`).
- Что показывать в блоке «VR» при master toggle = OFF: collapsed-state, hidden secondary, или просто disabled placeholder (`/ui-clarify`).

---

## 9. Зависимости и порядок

1. `S0241` — `Verified`. Без удаления старого стека новый каркас столкнётся с конфликтами имён / Hilt-биндингами.
2. `S0244` — минимально `R-02`, `R-05`, `R-06`, `R-07` записаны в `RESEARCH.md` с разделом «Best practice».
3. После выполнения 1 и 2 — `/spec-tech S0245` режет этот документ в детальный план с конкретными file paths и class names.
4. `/ui-clarify S0245` закрывает §4.
5. `/spec-dev S0245` пишет код.
6. `BlockNeedUserTest` → device gate → `/spec-check S0245` → `Verified`.

---

## Last Audit

**Date:** 2026-05-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [x] env=NONE branch verified in session log (XrDetectionFacadeImpl on non-VR device returns NONE).
- [ ] env=VR_HEADSET / Quest 3 actual immersive entry verified — pending on Quest 3 device-test (Stage 0 master toggle visibility + No-Op fallback in standard build).
