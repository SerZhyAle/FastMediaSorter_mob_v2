---
ticket: S0369
status: Draft
priority: 60
date: 2026-06-06
tier: 3
---

# Стратегическая спецификация: S0369 - Виджет быстрого фото в выбранный ресурс

**Ticket:** S0369
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-06
**Tier:** 3 - Moderate (ad-hoc widget follow-up)
**Roadmap entry:** Ad-hoc - запрос 2026-06-06: добавить icon-style widget для быстрого camera-to-resource flow с выбором целевого writable ресурса или папки камеры устройства.
**Tactical spec:** `PLAN/S0369_widget-camera-capture-target-resource/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room и Hilt-деталей.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - создать новый тикет на widget, который в стиле основных home-screen widgets запускает быстрый photo-capture flow и сохраняет результат в заранее выбранный target.
- **Local anchor:** Provided by user - existing home widgets + current `Camera-to-Resource` flow.
- **Scope boundaries / forbidden areas:** Provided by user - target при конфигурации должен быть либо невиртуальным ресурсом с возможностью записи, либо папкой камеры устройства; пользователь просит именно спецификацию, не реализацию.
- **Done / success signal:** Provided by user - создан strategic ticket, фиксирующий configurable widget flow, target-selection model и ограничения для дальнейшей тактической разработки.
- **Autonomy rule:** agent may decide with explicit assumptions (granted by owner via /goal directive 2026-06-06).
- **UI decisions / delegation:** Provided by user - widget должен быть в стиле основных compact widgets; при выборе пользователь настраивает получатель снимка: writable non-virtual resource или device camera folder. Формулировка `опционально если не отключено` требует tactical clarification для точного поведения относительно глобального camera-capture gate.

Owner gate закрыт: все строки заполнены, `MISSING - requires owner input` не осталось.

---

## 1. Проблема

В проекте уже есть несколько compact home-screen entry widgets: Camera Photos, Camera OCR, Quick Audio Recorder и configurable widgets с выбором ресурса. Но нет standalone widget entry point для сценария `снять фото и сразу сохранить в заранее выбранный получатель`.

Сейчас `Camera-to-Resource` живёт только внутри browse-context: пользователь сначала открывает приложение, затем нужный ресурс, затем запускает камеру из Browse. Для сценария «быстро сделать снимок и положить его в конкретный ресурс» это лишние шаги.

Дополнительно существующие surfaces уже показывают, что:

1. `Camera Photos` widget открывает виртуальный aggregate, а не выполняет захват.
2. `Quick Audio Recorder` доказывает, что standalone action widget возможен без предварительного входа в Browse.
3. `ResourceLaunch` и `RandomPhotoFrame` уже используют per-widget configuration с выбором ресурса.
4. Виртуальный `Camera Photos` resource не подходит как конфигурируемый writable target: он существует как агрегат, а не как прямой приёмник записи.

Итоговый gap: есть foundation для widgets, есть foundation для camera-to-resource, но нет связующей configurable shortcut surface между ними.

---

## 2. Цели

1. Добавить новый compact home-screen widget в том же визуальном семействе, что и основные `1x1` action widgets.
2. При конфигурации widget instance пользователь выбирает один target-получатель:
   - невиртуальный writable resource;
   - либо специальную цель `папка камеры устройства`.
3. Тап по widget запускает быстрый photo-capture flow без предварительного открытия Browse на выбранном ресурсе.
4. Полученный снимок сохраняется именно в заранее выбранный target widget instance.
5. Widget переиспользует существующие camera-capture правила, а не вводит отдельный параллельный capture subsystem.
6. Конфигурация и runtime-поведение остаются понятны в случае отсутствующего target, невалидного ресурса, отключённого camera flow или недоступной записи.

**Non-goals:**

- полноценный content-widget с превью, историей кадров или списком последних фото;
- выбор виртуальных aggregate resources как целевого получателя;
- новая photo gallery / browser surface внутри widget;
- отдельные per-widget advanced toggles для имени файла, пост-редактирования или OCR;
- изменение общего widget foundation, если существующие config/pin patterns достаточно покрывают задачу.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Widget должен выглядеть как основные compact widgets, а не как крупный content panel.
2. Основной сценарий - быстро открыть камеру и положить результат в заранее выбранный получатель.
3. Пользователь должен выбирать именно writable non-virtual target, а не абстрактный виртуальный раздел.
4. Device camera folder должна быть доступна как отдельная цель наряду с ресурсами.

### 3.2 Жёсткие ограничения

- **Widget style:** compact action-widget language из существующей home-widget foundation; без перегруза текстом или вторичными контролами на самом widget surface.
- **Target eligibility:** resource picker для этого widget не должен показывать виртуальные ресурсы; доступны только реально writable targets и отдельная pseudo-target entry для camera folder.
- **Camera folder semantics:** target `папка камеры устройства` должен трактоваться как явный special-case destination, а не как виртуальный aggregate `Camera Photos`.
- **Behavior reuse:** widget обязан опираться на уже существующий camera capture / save routing контракт, а не дублировать вторую реализацию записи снимков.
- **State persistence:** widget instance хранит выбранный target отдельно для каждого `appWidgetId`.
- **Flavor:** только image-capable app_v2 builds, где camera capture имеет смысл; `Wear` не входит.
- **Localization:** EN/RU/UK обязательны для label, description, config-screen copy, target labels, empty/error/fallback messages.
- **Accessibility:** widget и config activity должны сохранять touch, keyboard/D-pad, mouse и TalkBack parity с существующими widget-config surfaces.
- **No schema migration:** новая конфигурация хранится в widget-specific prefs/snapshot layer, без Room migration.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** только image-capable app_v2 builds, где camera capture имеет смысл (`standard`, `noLegal`, `lite`, `photos`, `legacy`); `Wear` не входит; новых flavor-гейтов не добавляем сверх существующего capture-гейта.
- **UI placement contract:** новый compact `1x1` action-widget в визуальном семействе существующих icon-style home widgets; появляется в том же widget picker / pinning flow; финальный footprint и label policy подтверждаются через `/ui-clarify`.
- **Target selection contract:** при конфигурации widget instance пользователь выбирает один получатель - writable non-virtual resource или явную pseudo-target `папка камеры устройства`; виртуальные aggregate resources (включая `Camera Photos`) в picker не показываются.
- **Data compatibility:** выбранный target хранится per `appWidgetId` в widget-specific prefs/snapshot layer без Room-миграции.
- **Capture reuse contract:** widget переиспользует существующий camera-to-resource save routing и app-level camera settings (filename / open-for-editing / global enable), а не вводит второй capture subsystem; broken/removed target обрабатывается явным reconfigure/error fallback, а не silent no-op.
- **Accessibility:** widget surface и config activity сохраняют touch, keyboard/D-pad, mouse и TalkBack parity с существующими widget-config экранами; проходят тот же `/ui-clarify` gate.
- **Communication policy:** label, description, config-screen copy, target labels и empty/error/fallback messages проходят `docs/COMMUNICATION_POLICY.md` (§6) и EN/RU/UK parity.
- **Validation level:** целевые image-capable варианты сборки компилируются; затронутые unit-тесты проходят; ручная проверка на устройстве для capture-в-выбранный-target и fallback при недоступном target.
- **Related tickets:** S0348 (widget foundation), S0349 (standalone action-widget pattern), S0352 (configurable resource-bound widget), S0359 (in-app camera capture и permission implications), S0367 (settings regrouping влияет на global camera gate semantics). Зависимостей-блокеров нет.

---

## 4. Контекст текущей архитектуры

Исследование текущей реализации показывает четыре готовых опорных поверхности:

1. **Icon-style action widgets уже существуют.** `CameraPhotosWidgetProvider`, `CameraOcrTranslateWidgetProvider` и `QuickAudioRecorderWidgetProvider` уже живут в compact widget foundation и дают понятный визуальный паттерн для нового entry point.
2. **Per-widget configuration уже существует.** `ResourceLaunchWidgetConfigActivity` и `RandomPhotoFrameConfigActivity` уже умеют работать с `AppWidgetManager.EXTRA_APPWIDGET_ID`, выбирать ресурс и сохранять конфигурацию widget instance.
3. **Camera capture routing уже умеет сохранять в разные типы targets.** Текущий browse-side camera flow пишет либо в local/network/cloud resource, либо в `DCIM/Camera` для special-case camera-like targets.
4. **Виртуальный `Camera Photos` не является прямым writable target.** Текущий scan/provisioning path помечает его как агрегатную сущность, а не как конфигурируемый получатель записи.

Следовательно, задача не в том, чтобы изобрести новый widget stack или новый save backend. Задача в том, чтобы связать уже существующие foundation pieces в новый standalone configurable shortcut.

---

## 5. Предлагаемый подход

### 5.1 New configurable action widget

Новый widget остаётся compact `1x1` action entry, а не content widget. Он наследует существующий визуальный язык icon-style home widgets и появляется в том же widget picker / pinning flow, что и остальные standalone widgets.

### 5.2 Target selection during widget configuration

При первом добавлении widget instance проходит configuration activity. Эта конфигурация переиспользует существующий resource-selection pattern, но накладывает новый eligibility filter:

- показать только non-virtual writable resources;
- добавить отдельную pseudo-target entry `папка камеры устройства`.

Таким образом, widget получает устойчивую привязку к одному получателю результата.

### 5.3 Reuse current camera-to-resource save semantics

После тапa widget запускает быстрый camera-capture flow и передаёт ему заранее выбранный target. Дальнейшая семантика сохранения должна переиспользовать текущий camera-to-resource contract:

- writable local resource -> локальное сохранение;
- writable network/cloud resource -> существующий upload/save path;
- device camera folder -> явное сохранение в системную camera directory.

Новая спека не вводит отдельный save backend.

### 5.4 Respect the existing camera settings surface

Widget должен вести себя согласованно с глобальными camera settings там, где они уже определяют capture semantics. Tactical phase должна явно решить, какие existing settings наследуются автоматически:

- ask filename / timestamp naming;
- open result for editing;
- global enable/disable of camera capture.

Предпочтительная модель первой версии: widget reuse existing app-level camera settings вместо создания отдельного per-widget settings слоя.

### 5.5 Explicit fallback states

Если target исчез, стал недоступен для записи, больше не существует в базе, или runtime capture path недоступен, widget не должен молча ломаться. Нужен явный fallback contract: reconfigure, message, or safe no-op with explanation.

---

## 6. Открытые вопросы / Research items

1. **Global camera gate semantics**
   - **Вопрос:** что означает в owner request фраза `опционально если не отключено`?
   - **Варианты:** widget respects existing global `disableCameraCapture` and disappears from picker; widget stays visible but tap is blocked with explanation; phrase refers only to the optional camera-folder target.
   - **Нужно выяснить:** какой именно глобальный gate должен применяться к availability и runtime поведения виджета.
   - **Статус:** Open - owner clarification or tactical decision required.

2. **Target list composition**
   - **Вопрос:** достаточно ли фильтра `non-virtual + isWritable`, или нужно дополнительно исключать определённые resource categories?
   - **Варианты:** all writable non-virtual resources; only resources whose media-type model includes images; only local + supported upload targets.
   - **Нужно выяснить:** как не показать target, который technically writable, но не соответствует camera capture expectations.
   - **Статус:** Open - tactical audit required.

3. **Widget surface exact size and label policy**
   - **Вопрос:** `в стиле основных` означает ровно `1x1` icon-style widget без текста, как quick widgets первой волны, или допустим другой compact size?
   - **Варианты:** fixed `1x1`; resizable compact action widget; textless icon with accessible name only.
   - **Нужно выяснить:** зафиксировать конкретный launcher footprint для tactical implementation и preview assets.
   - **Статус:** Open - tactical UI decision required.

4. **Reconfiguration path after pinning**
   - **Вопрос:** как пользователь меняет target уже размещённого widget instance?
   - **Варианты:** launcher-driven widget reconfigure activity; in-app widget picker creates a new instance only; tap on broken state opens reconfigure flow.
   - **Нужно выяснить:** минимально достаточный UX для первой версии без лишней сложности.
   - **Статус:** Open - tactical UX decision required.

5. **Inherited post-capture behavior**
   - **Вопрос:** должен ли widget безусловно наследовать `ask filename` и `open for editing` из общих настроек camera capture?
   - **Варианты:** inherit both global settings; inherit only filename policy; widget uses fixed timestamp save for speed.
   - **Нужно выяснить:** лучший баланс между predictability и speed для standalone widget flow.
   - **Статус:** Open - tactical product decision required.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Config picker покажет виртуальные или псевдо-незаписываемые targets | Средняя | widget обещает direct capture, но реально сохранить некуда | отдельный eligibility filter для этого widget config flow |
| Widget начнёт расходиться по логике с browse-side camera-to-resource | Средняя | разные naming/save/fallback contracts для одного и того же сценария | reuse current camera capture routing и app-level settings |
| Device camera folder будет спутан с виртуальным `Camera Photos` | Высокая | пользователь выберет не тот semantic target | отдельная pseudo-target entry и явное текстовое различение |
| Broken/removed resource приведёт к silent failure | Средняя | пользователь думает, что tap ничего не делает | explicit error / reconfigure fallback |
| Existing global camera disable setting поведёт себя непредсказуемо для widget | Средняя | widget либо появляется мёртвым, либо игнорирует app settings | вынести gate semantics в tactical decision и проверить picker/runtime parity |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации это уже новый end-user capability: standalone home-screen widget для быстрого фото в выбранный target. Понадобится обновить `docs/FEATURES.md` + `_RU` + `_UK`, описав новый widget, выбор получателя и сохранение в выбранный writable resource или camera folder.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Reuse widget foundation instead of inventing a new widget stack**

- **Решение:** новый widget строится поверх уже существующих compact widgets, `HomeWidgetCatalog` и existing pin/config flow.
- **Альтернативы:** отдельный custom widget subsystem; hidden internal shortcut outside widget foundation.
- **Почему:** foundation уже существует и покрывает both action widgets and configurable widgets.

**ADR-2: Device camera folder is an explicit pseudo-target, not a virtual resource**

- **Решение:** `папка камеры устройства` даётся как отдельная цель конфигурации.
- **Альтернативы:** reuse virtual `Camera Photos`; скрыто маппить camera folder на aggregate resource.
- **Почему:** virtual `Camera Photos` описывает aggregate browsing surface, а не прямой writable destination.

**ADR-3: Widget must stay aligned with current camera capture semantics**

- **Решение:** standalone widget reuse current camera capture contracts and save routing.
- **Альтернативы:** separate widget-only capture/save semantics.
- **Почему:** пользователь видит один и тот же product capability, только с другим entry point.

**ADR-4: Configuration-time target binding is mandatory**

- **Решение:** widget instance always binds to one chosen target during configuration.
- **Альтернативы:** ask target on every tap; infer target from last opened resource.
- **Почему:** standalone widget loses Browse context by design, so target identity must be explicit and durable.

---

## 10. Связи с другими спеками

- **S0348** - widget foundation, icon-style compact widgets, in-app picker and pinning flow.
- **S0349** - standalone action-widget pattern with trampoline/service ownership.
- **S0352** - configurable widget pattern with resource-bound config activity.
- **S0359** - current in-app camera capture contract and camera permission implications.
- **S0367** - settings regrouping around camera/microphone surfaces; may affect discoverability and global camera gate semantics for this widget.

---

## 11. Критерии готовности (strategic-level)

1. В приложении появляется новый compact camera widget entry в существующем home-widget ecosystem.
2. При конфигурации widget instance пользователь выбирает writable non-virtual resource или explicit device camera folder target.
3. Virtual aggregates не выдаются пользователю как сохраняемый target для этого widget.
4. Тап по widget запускает быстрый capture flow и сохраняет результат в заранее выбранный target.
5. Widget не требует предварительно открытого Browse context.
6. Broken/missing target обрабатывается явным fallback вместо silent failure.
7. EN/RU/UK localization and accessibility parity проходят для widget surface и config flow.
8. Tactical implementation явно фиксирует связь виджета с текущими camera settings и global enable/disable semantics.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация будет создана через `/spec-tech S0369` после закрытия owner gate.

---

## Revision History

- **2026-06-06** - created by Copilot via `/spec`
  - Added strategic draft for a configurable quick-camera widget bound to a writable target resource or device camera folder.

## Last Audit

**Date:** 2026-06-06
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] Verify quick photo widget configuration, target picking, camera capture, file saving and fallback states on an emulator or Android device.