# Стратегическая спецификация: S0264 - Исправление panel stereo crop для single-eye режима

**Ticket:** S0264
**Status:** Verified
**Priority:** 75
**Date:** 2026-05-20
**Tier:** 2 - Easy (ad-hoc player bugfix)
**Roadmap entry:** Ad-hoc - bugfix по запросу владельца от 2026-05-20
**Tactical spec:** `PLAN/S0264_panel-stereo-crop-fix/`
**Tactical plan:** [`PLAN/S0264_panel-stereo-crop-fix/INDEX.md`](S0264_panel-stereo-crop-fix/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room и деталей DI.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - `implementation` через `/spec-all S0264`
- **Goal / expected outcome:** Provided by user - устранить дефект single-eye crop в panel player, убрать лишний VR CTA в VR-capable сборках и сделать поведение настройки предсказуемым на первой установке
- **Local anchor:** Provided by user - `S0264`, воспроизводимый симптом на SBS_FULL-видео в panel player и наблюдаемое лишнее появление VR CTA
- **Scope boundaries / forbidden areas:** Provided by user - вне объёма остаются immersive VR rendering path, отдельный image stereo pipeline и специальная отдельная работа только под OU-формат
- **Done / success signal:** Provided by user - в VR-capable flavor CTA больше не показывается, default single-eye = ON для новых установок, а SBS_FULL в panel player визуально показывает один глаз без двойного изображения
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions
- **UI decisions / delegation:** Delegated by user - новых UI-экранов и layout-решений не вводить; допустимо только скорректировать существующее условие показа CTA и текстовое описание настройки без отдельного UI review

`Approved` разрешён: все обязательные поля заполнены значениями, предоставленными или явно делегированными владельцем.

---

## 1. Проблема

В panel-режиме воспроизведения стерео-видео single-eye режим для side-by-side контента не даёт ожидаемый визуальный результат: пользователь включает показ одного глаза, но на экране всё ещё остаётся полное двойное изображение. Параллельно VR-capable сборки показывают CTA на установку VR-редакции даже там, где эта редакция уже и так запущена, что создаёт ложный шум.

Дополнительно настройка single-eye имеет разный default между обычными и VR-capable flavor, хотя runtime уже умеет отключать crop в immersive-сценарии отдельно. В результате пользователь получает неожиданное стартовое состояние и не понимает, почему toggle не работает одинаково в разных сборках.

---

## 2. Цели

1. Single-eye режим для stereo video в panel player должен реально менять изображение и убирать второй глаз с экрана в не-immersive сценарии.
2. VR-capable flavor не должны показывать CTA на установку VR-версии при детекции 3D-контента.
3. Настройка single-eye должна иметь единый и предсказуемый default для новых установок независимо от flavor.
4. Уже сохранённые пользовательские настройки не должны теряться или тихо переопределяться.

**Non-goals:**

- Не перерабатывается immersive VR rendering path.
- Не вводится новый UI поток или новый экран настроек.
- Не выполняется широкий upgrade media stack, если bugfix можно закрыть локально.
- Не вводятся отдельные правила только для OU-формата сверх общего stereo path.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление должно быть минимально рискованным для существующего panel player.
2. Если возможно, нужно сохранить текущую визуальную компоновку panel player без перехода на более рискованную surface-модель.
3. Пользовательское описание настройки должно явно соответствовать фактическому default.

### 3.2 Жёсткие ограничения

- **Flavor:** затрагиваются как минимум `standard`, `vr`, `noLegal`; поведение должно быть консистентным между VR-capable и non-VR flavor по смыслу требований
- **API level:** без новой API-зависимости; решение должно работать в текущем baseline приложения
- **Wear OS:** не затрагивается
- **Производительность:** нельзя заметно ухудшить старт или плавность panel video playback
- **Совместимость данных:** существующее сохранённое значение настройки single-eye должно сохраняться; меняется только fallback для новых установок
- **Локализация:** если меняется текст описания настройки, требуется синхронное обновление EN/RU/UK и соблюдение `docs/COMMUNICATION_POLICY.md`
- **Доступность:** новых UI-элементов не добавляется; существующее поведение CTA не должно деградировать в standard flavor

### 3.3 Owner inputs (Approval gate)

Каждое поле ниже заполнено для совместимости с текущим promotion-gate скриптом репозитория.

- **Flavor scope:** `standard`, `vr`, `noLegal`; bugfix applies to both VR-capable and non-VR capability paths
- **API level constraints:** no new API constraint; keep current app baseline
- **Wear OS:** no - Wear module is out of scope
- **Performance budget:** no noticeable regression in panel playback startup or smoothness; prefer a local fix over a broad media-stack migration
- **Data compatibility:** existing stored single-eye preference values must be preserved; only the fallback for fresh installs may change
- **Localization:** EN/RU/UK must stay aligned if the setting summary text changes
- **Accessibility:** no new UI controls; CTA behavior in non-VR builds must remain understandable and unobtrusive
- **Communication policy:** existing user-facing strings and any updated setting summary must remain compliant with `docs/COMMUNICATION_POLICY.md`
- **UI placement contract:** no new placement decisions; only the existing CTA visibility rule is adjusted by capability context
- **Validation level:** code-level validation - target build for affected flavor plus control build for standard; manual playback verification for the single-eye visual result
- **Related tickets:** none
- **Owner sign-off:** 2026-05-20 - owner explicitly asked to bring `S0264` to `/spec` format and continue the pipeline in the same turn

---

## 4. Контекст текущей архитектуры

Стерео-обработка panel playback уже опирается на существующий слой player orchestration, который принимает решение о формате контента, о применении single-eye режима и о том, когда runtime должен отключать crop для immersive-сценария. Это означает, что дефект находится не в отсутствии флага как такового, а в несрабатывании или неэффективности конкретного механизма визуальной трансформации в panel-потоке.

Параллельно продукт уже различает обычные и VR-capable flavor на уровне capability surface. CTA для VR-редакции должен быть частью non-VR UX, а не общим поведением для любого stereo detection события. Нынешнее расхождение говорит о том, что слой принятия решений о CTA недостаточно учитывает capability контекст текущей сборки.

---

## 5. Предлагаемый подход

Нужно разделить задачу на три независимые линии: capability-gate для CTA, унификация default настройки и локальный bugfix самого panel crop path. Первые две линии детерминированы и должны быть исправлены без архитектурного риска. Третья линия должна выбрать самый узкий механизм, который гарантированно меняет итоговую картинку в panel player и не требует широкого пересмотра media stack.

### 5.1 Основные столпы / модули

- **Capability-aware CTA gate.** Решение о показе CTA принимает во внимание, находится ли пользователь уже в VR-capable редакции.
- **Unified settings default.** Fallback single-eye унифицируется для новых установок и больше не зависит от flavor-capability там, где runtime уже умеет делать исключение по режиму воспроизведения.
- **Reliable panel crop path.** Panel video pipeline использует такой механизм crop/transform, который реально доходит до итогового отображения на экране в текущей surface-композиции.

### 5.2 Потоки данных и событий

При детекции stereo content playback-поток сначала определяет capability контекст и решает, нужен ли CTA вообще. Затем читается сохранённая пользовательская настройка single-eye либо её единый fallback. После этого panel playback применяет визуальную трансформацию только в тех сценариях, где single-eye включён и immersive override не активен.

### 5.3 Точки расширяемости

- Должна остаться возможность в будущем заменить локальный crop-механизм на более новый media-stack путь без повторного пересмотра capability-gate и default-настройки.
- Должна сохраниться возможность расширить ту же логику на другие stereo presentation path, если это понадобится отдельной задачей.

---

## 6. Открытые вопросы / Research items

1. **Конкретный механизм panel crop**
   - **Вопрос:** Какой локальный механизм на текущем media stack надёжнее всего даёт видимый single-eye результат в panel player без ломки overlay-композиции?
   - **Варианты:** локальная texture/surface transform; исправление существующего effects path; upgrade media stack как крайний вариант.
   - **Нужно выяснить:** выбрать наименьшее по риску решение после чтения текущего player path.
   - **Статус:** Resolved - выбран локальный `TextureView` transform path без смены общей surface-модели panel player

2. **Объём верификации по flavor**
   - **Вопрос:** Нужна ли отдельная обязательная проверка для `vr` и `noLegal`, или достаточно одного VR-capable flavor плюс `standard` как контрольного non-VR сценария?
   - **Варианты:** `standard + noLegal`; `standard + vr`; все три flavor.
   - **Нужно выяснить:** выбрать минимальный, но достаточный validation set для завершения тикета.
   - **Статус:** Resolved - обязательный набор `standard + noLegal`; `vr` остаётся опциональным follow-up smoke только при наличии затронутого flavor-specific кода

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Локальный crop bugfix затронет surface-композицию panel player | Средняя | Видео начнёт отображаться некорректно или сломается overlay | Предпочесть локальную трансформацию без смены общей surface-модели, если она покрывает симптом |
| Унификация default затронет существующих пользователей | Низкая | Пользователь неожиданно потеряет свой выбор | Менять только fallback для новых установок, не трогая сохранённое значение |
| CTA gate случайно отключится и для non-VR flavor | Низкая | Пользователь standard flavor перестанет получать полезную подсказку | Отдельно валидировать сохранение CTA-поведения в standard flavor |
| Исправление сведётся к широкому media-stack upgrade | Средняя | Непропорциональный объём изменений и риск побочных регрессий | Рассматривать stack upgrade только как последний вариант после проверки локальных путей |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES`. Это bugfix существующего поведения playback и CTA, а не новая пользовательская возможность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: CTA должен зависеть от capability текущей сборки, а не только от факта stereo detection**

- **Решение:** CTA показывается только там, где он может привести пользователя к новой полезной capability.
- **Альтернативы:** показывать CTA всегда; убирать CTA полностью.
- **Почему:** CTA внутри VR-capable сборки не несёт полезного действия и только зашумляет playback flow.

**ADR-2: Default single-eye унифицируется, а runtime override остаётся отдельной обязанностью playback path**

- **Решение:** Стартовый fallback single-eye делается единым, а исключения для immersive-сценария сохраняются как runtime-поведение.
- **Альтернативы:** продолжать flavor-specific default; полностью убрать runtime override.
- **Почему:** Это убирает неожиданное отличие между flavor без потери контроля над immersive path.

**ADR-3: Для panel crop сначала выбирается локальный bugfix, а не широкий platform/media upgrade**

- **Решение:** Предпочесть минимальный механизм, который исправляет symptom в текущем player stack.
- **Альтернативы:** немедленный апгрейд media stack; полная смена surface-модели panel player.
- **Почему:** Тикет bugfix-уровня не должен тянуть за собой широкую платформенную миграцию без крайней необходимости.

---

## 10. Связи с другими спеками

Связей, блокирующих выполнение, нет.

---

## 11. Критерии готовности (strategic-level)

1. В VR-capable flavor stereo detection больше не вызывает CTA на установку VR-редакции.
2. В non-VR flavor полезный CTA продолжает работать как раньше.
3. Для новых установок single-eye настройка имеет единый default ON независимо от flavor.
4. Для уже существующих установок сохранённое значение single-eye не меняется.
5. В panel player single-eye режим действительно убирает второй глаз из видимого результата для SBS stereo content вне immersive override.
6. Тактическая спецификация может разложить работу на capability gate, settings fallback, panel crop implementation и validation без дополнительных стратегических решений.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0264` - создаст `PLAN/S0264_panel-stereo-crop-fix/` с фазами реализации и верификации.

## Revision History

- **2026-05-20** - by `/spec` (`Codex`, focus: strategic template alignment)
  - Rebuilt `S0264` into the current strategic `/spec` template, added `§0 Approval Gate`, preserved the original bugfix intent, and auto-approved the draft because the owner explicitly requested pipeline continuation in the same turn.

- **2026-05-20** - by `/spec-tech` (`Codex`, focus: tactical breakdown)
  - Resolved strategic research items, created the tactical phase plan, and advanced the spec to `Tactical`.

- **2026-05-20** - by `/spec-dev` (`Codex`, focus: implementation alignment)
  - Matched the tactical plan to the current repository state, closed build and catalog validation, and advanced the spec to `Implemented`.

## Last Audit

**Date:** 2026-05-20
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

### Manual / on-device

- [ ] On a VR-capable device, open an SBS_FULL sample in panel playback and confirm only one eye is visible without double image.
- [ ] On `standard`, confirm the VR CTA still appears for flat 3D detection; on `noLegal`, confirm it does not.
