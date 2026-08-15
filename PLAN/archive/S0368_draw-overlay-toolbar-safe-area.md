---
ticket: S0368
status: Draft
priority: 75
date: 2026-06-06
tier: 3
---

# Стратегическая спецификация: S0368 - Safe area for draw editor toolbar over player bottom panels

**Ticket:** S0368
**Status:** Archived
**Priority:** 75
**Date:** 2026-06-06
**Tier:** 3 - Moderate, player overlay layout / safe-area contract
**Roadmap entry:** Ad-hoc - запрос 2026-06-06: во время редактирования картинки toolbar рисования не должен попадать под системную панель Android; `Copy to` / `Move to` можно оставлять как нижний слой прямо над системными элементами.
**Tactical spec:** `PLAN/S0368_draw-overlay-toolbar-safe-area/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, риски и открытые вопросы. Без имён классов, путей, лимитов строк, Room migration и Hilt-деталей.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - создать тикет на исправление layout-поведения в draw editor: команды рисования должны быть доступны сразу под изображением, а `Copy to` / `Move to` могут оставаться видимыми как обычно, но у нижнего края над системной панелью.
- **Local anchor:** Provided by user - экран редактирования картинки в player, где нижняя панель команд рисования уходит под системную навигацию Android, а группы `Copy to` / `Move to` остаются видимыми и съезжают выше.
- **Scope boundaries / forbidden areas:** Provided by user - речь про размещение и доступность нижних панелей во время редактирования картинки; пользователь не просил менять набор draw-команд, набор destination buttons или поведение copy/move операций.
- **Done / success signal:** Provided by user - создан specification task, который фиксирует желаемый порядок нижних слоёв UI и требования к доступности команд рисования относительно системной панели.
- **Autonomy rule:** agent may decide with explicit assumptions (granted by owner via /goal directive 2026-06-06).
- **UI decisions / delegation:** Provided by user - во время редактирования картинки `Copy to` / `Move to` можно показывать как обычно сразу над системной панелью; команды редактора рисунка должны быть отдельным верхним слоем сразу под изображением и не должны перекрываться системной навигацией. Compact-height fallback и landscape specifics остаются для тактического уточнения.

Owner gate закрыт: все строки заполнены, `MISSING - requires owner input` не осталось.

---

## 1. Проблема

Сейчас в draw mode на экране player одновременно живут две нижние группы управления: destination-панели `Copy to` / `Move to` и toolbar редактора рисунка. По факту они подчиняются разным правилам размещения относительно системных inset-ов Android.

Нижний слой с destination-кнопками визуально поднимается выше системной панели и остаётся доступным. При этом toolbar рисования оказывается прижатым к физическому низу экрана и на устройствах с заметной нижней системной навигацией частично или полностью уходит под неё. Пользователь видит второстепенные transfer-команды, но теряет доступ к первичным edit-командам текущего режима.

Это ломает базовую иерархию взаимодействия в draw mode:

1. Активные команды редактирования должны находиться ближе всего к редактируемому изображению.
2. Системная панель не должна перекрывать ни одну tappable-команду draw editor.
3. `Copy to` / `Move to` могут оставаться на экране, но не должны занимать позицию, из-за которой toolbar рисования оказывается под системными элементами.

На практике дефект делает часть режима рисования недоступной именно в тот момент, когда пользователь уже вошёл в редактирование и ожидает быстрый доступ к кисти, сохранению, overflow и закрытию.

---

## 2. Цели

1. Обеспечить полную tappable-доступность draw toolbar во время редактирования картинки.
2. Зафиксировать вертикальную иерархию нижнего UI в draw mode как `image/canvas -> draw commands -> Copy/Move panels -> system bar`.
3. Разрешить `Copy to` / `Move to` оставаться видимыми, если это не нарушает доступность draw-команд.
4. Исключить любое перекрытие draw toolbar системной навигацией, gesture area или display cutout safe bounds.
5. Сохранить текущий набор и семантику draw-команд, copy/move-панелей и их действий.
6. Убрать зависимость корректного размещения от конкретного режима системной навигации Android.

**Non-goals:**

- redesign toolbar рисования или смена его набора команд;
- изменение логики copy/move, destination shortcuts, favourites или file operations;
- добавление новых draw-инструментов;
- общий redesign player screen вне draw mode;
- изменение save/share/delete semantics в draw editor.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Во время редактирования картинки `Copy to` / `Move to` можно не скрывать.
2. Destination-панели должны оставаться внизу, как обычно, но уже над системной панелью.
3. Toolbar рисования должен быть расположен сразу под изображением, а не под системной навигацией.
4. Главный приоритет - доступность draw-команд, потому что пользователь уже находится в режиме редактирования.

### 3.2 Жёсткие ограничения

- **Safe area first:** ни одна draw-команда не может уходить под `systemBars` или `displayCutout` safe bounds.
- **Priority of interaction:** при конфликте по месту первыми сохраняются доступными именно draw-команды, а не вторичный transfer-layer.
- **No hardcoded-only fix:** решение не должно опираться только на одну магическую margin/offset-константу под конкретное устройство.
- **Mode continuity:** вход и выход из draw mode не должны ломать текущую логику видимости `Copy to` / `Move to`, кроме необходимого перераспределения нижнего safe area.
- **Accessibility:** touch, mouse, keyboard и D-pad доступ к draw-командам должен оставаться рабочим после исправления.
- **Orientation coverage:** tactical phase обязана проверить не только текущий portrait-симптом, но и landscape / compact-height варианты, где нижний safe area может вести себя иначе.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** во время draw mode нижний стек подчиняется инварианту `image/canvas -> draw toolbar -> Copy/Move panels -> system bar`; draw toolbar занимает ближайшую к изображению безопасную control band, а `Copy to` / `Move to` остаются нижним in-app слоем над системной панелью.
- **Safe-area contract:** размещение нижних поверхностей draw mode основывается на реальных `systemBars` + `displayCutout` insets, а не на фиксированном предположении о высоте навигационной панели или device-specific offset.
- **Compact-height fallback:** при дефиците высоты деградирует lower-priority слой (`Copy to` / `Move to`), а не primary draw-команды; конкретный способ деградации (collapse, scroll, временное скрытие) выбирает тактическая фаза, но draw toolbar обязан остаться доступным.
- **Orientation coverage:** portrait и landscape допускают разный form factor toolbar, но обе ориентации обязаны сохранять один и тот же приоритет слоёв и safe-area доступность; portrait-only фикс не считается завершением.
- **Accessibility:** после фикса touch, keyboard, D-pad и mouse сохраняют достижимость всех нижних draw-команд в обеих ориентациях.
- **Validation level:** сборка `standardDebug` проходит; ручная UI-проверка подтверждает отсутствие overlap draw toolbar с системной панелью хотя бы в 3-button и gesture navigation modes, в portrait и landscape.
- **Related tickets:** none.

---

## 4. Контекст текущей архитектуры

Локальный аудит показывает, что проблема не в отсутствии самих команд рисования, а в рассинхроне между двумя соседними нижними слоями player UI.

1. Destination-панели уже ведут себя как отдельный нижний контейнер и визуально отступают от системной панели.
2. Toolbar draw editor является другим overlay-слоем и не следует тому же safe-area contract.
3. Во время draw mode оба слоя сосуществуют на одном экране, но порядок их ответственности за нижний край явно не оформлен.
4. Из-за этого вторичный нижний слой выглядит корректно, а первичный edit-layer оказывается частично вне доступной области.

Архитектурно это больше похоже на gap в правилах inset/layout ownership, чем на дефект самих feature-флагов или command availability.

---

## 5. Предлагаемый подход

### 5.1 Явно зафиксировать нижнюю иерархию draw mode

Во время редактирования у экрана должен быть детерминированный порядок слоёв от контента к системной панели: сначала изображение и canvas, затем toolbar рисования как primary action layer, затем `Copy to` / `Move to` как lower secondary layer, и только после этого системная панель Android.

### 5.2 Распространить safe-area contract на draw toolbar

Draw toolbar должен участвовать в том же edge-to-edge / inset-driven размещении, что и другие нижние контейнеры player UI. Поведение должно зависеть от реальных `systemBars` / cutout insets, а не от фиксированного предположения о высоте navigation bar.

### 5.3 Сохранить destination-панели как допустимый нижний контекстный слой

Пользователь явно разрешил не скрывать `Copy to` / `Move to`. Значит исправление не обязано убирать эти панели, но обязано удержать их в нижнем слое и не допустить, чтобы они вытеснили toolbar рисования под системную навигацию.

### 5.4 Определить fallback для compact-height сценариев

Если на экране одновременно не помещаются canvas, draw toolbar, destination-панели и system bar, fallback должен деградировать lower-priority слой, а не primary draw-команды. Конкретный вид деградации нужно решить тактически.

### 5.5 Сохранить существующую ментальную модель draw mode

Исправление должно восприниматься как восстановление правильного порядка команд, а не как новый режим player UI. Пользователь не должен заново учиться, где искать save / cancel / overflow и где искать destination shortcuts.

---

## 6. Открытые вопросы / Research items

1. **Compact-height fallback**
   - **Вопрос:** что делать, если суммарная высота draw toolbar + destination panels + system bar не помещается на малой высоте экрана?
   - **Варианты:** auto-collapse `Copy to` / `Move to`; ограничить их высоту скроллом; временно скрывать lower secondary layer в draw mode; перестраивать стек иначе.
   - **Нужно выяснить:** какой вариант сохраняет доступность draw-команд и не ломает текущий UX transfer-панелей.
   - **Статус:** Open - tactical UX decision required.

2. **Landscape behavior**
   - **Вопрос:** должен ли landscape оставаться в своей текущей ориентационно-специфичной форме, если при этом сохраняется invariant доступности и safe area, или для draw mode нужен отдельный stacked layout?
   - **Варианты:** сохранить orientation-specific form factor; перестраивать toolbar относительно media area; отдельный landscape fallback.
   - **Нужно выяснить:** где находится эквивалент user-requested правила `сразу под изображением` для landscape.
   - **Статус:** Open - tactical layout decision required.

3. **Visibility policy for destination panels during draw mode**
   - **Вопрос:** должны ли `Copy to` / `Move to` сохранять текущую visibility policy в draw mode без дополнительных ограничений?
   - **Варианты:** keep current policy; keep but collapse by default; show only when already expanded before entering draw mode.
   - **Нужно выяснить:** нужно ли дополнительное правило, если именно expanded panels делают нижний стек слишком высоким.
   - **Статус:** Open - tactical behavior decision required.

4. **Validation matrix across system navigation modes**
   - **Вопрос:** достаточно ли tactical validation на одном устройстве, или нужно обязательное сравнение 3-button navigation и gesture navigation?
   - **Варианты:** single-device check; dual-navigation validation; emulator + device pair.
   - **Нужно выяснить:** минимальный проверочный набор, чтобы fix не оказался device-specific.
   - **Статус:** Open - tactical verification decision required.

---

## 7. Риски

1. Исправление поднимет draw toolbar только для portrait, но оставит landscape без эквивалентной safe-area защиты.
2. Слишком простой margin-based patch сработает на одном устройстве и снова сломается на другом navigation mode.
3. Попытка сохранить оба нижних слоя без fallback приведёт к новому наложению друг на друга на маленьких экранах.
4. Агрессивное решение может полностью скрыть `Copy to` / `Move to`, хотя пользователь явно разрешил их сохранять.
5. Исправление только позиции без явной иерархии снова приведёт к регрессии при следующем изменении player overlays.

---

## 8. Влияние на пользователя (docs/FEATURES)

Это bugfix существующего draw editor UX, а не новый end-user feature. Отдельное обновление `docs/FEATURES*.md` не ожидается, если tactical реализация не добавит новый сценарий, а только восстановит корректную доступность уже существующих команд.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Draw toolbar is the primary bottom action layer in draw mode**

- **Решение:** во время редактирования картинки toolbar рисования имеет более высокий приоритет размещения, чем destination-панели.
- **Альтернативы:** сохранять нынешний неявный порядок; поднимать только `Copy to` / `Move to`; скрывать draw toolbar за системной панелью на части устройств.
- **Почему:** пользователь уже находится в режиме редактирования и ожидает мгновенный доступ именно к edit-командам.

**ADR-2: Destination panels may stay visible, but only as the lower secondary layer**

- **Решение:** `Copy to` / `Move to` можно оставлять на экране, но они живут ниже draw toolbar и выше system bar.
- **Альтернативы:** всегда скрывать destination-панели в draw mode; менять их местами с draw toolbar.
- **Почему:** это напрямую соответствует owner-requested UX и сохраняет текущую доступность transfer shortcuts.

**ADR-3: Safe area must be inset-driven**

- **Решение:** размещение обоих нижних слоёв должно основываться на реальных system bar / cutout insets.
- **Альтернативы:** фиксированные bottom margins или device-specific offsets.
- **Почему:** только inset-driven подход масштабируется на разные Android navigation modes и устройства.

**ADR-4: Orientation-specific form factors are allowed only if accessibility invariants stay identical**

- **Решение:** portrait и landscape могут использовать разную форму toolbar-а, но обязаны сохранять одну и ту же иерархию приоритета и safe-area доступность.
- **Альтернативы:** чинить только portrait, где уже есть скриншот; допустить orientation-specific divergence.
- **Почему:** bugfix должен закрывать не только текущий symptom, но и класс проблемы.

---

## 10. Связи с другими спеками

- По функциональной области связано с существующим draw overlay feature и с предыдущими edge-to-edge / player command layout изменениями.
- Возможные hard dependencies нужно проверить в `/spec-tech`, если tactical phase найдёт связанные незакрытые player UI tickets.

---

## 11. Критерии готовности (strategic-level)

1. Во время draw mode ни одна команда рисования не уходит под системную панель Android.
2. На экране соблюдается invariant `image/canvas -> draw commands -> Copy/Move panels -> system bar`, либо tactical spec явно документирует эквивалентный accessible layout для orientation-specific cases.
3. `Copy to` / `Move to` остаются доступными как нижний слой или деградируют предсказуемым fallback-способом без потери draw-команд.
4. Исправление не зависит от одного конкретного navigation mode и проходит проверку хотя бы на тех вариантах, которые tactical phase определит обязательными.
5. Draw toolbar остаётся доступным для touch, keyboard, D-pad и mouse.
6. Набор draw-команд, их действия и semantics save/cancel/overflow не меняются.
7. Исправление не ломает copy/move feature availability и не меняет смысл destination-панелей.
8. Portrait и landscape либо оба исправлены, либо тактическая спецификация явно объясняет и валидирует orientation-specific эквивалент.
9. Target debug build и целевая UI-проверка проходят после реализации.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация будет создана через `/spec-tech S0368` после закрытия owner gate.

---

## Revision History

- **2026-06-06** - created by Copilot via `/spec`
  - Added strategic draft for draw editor bottom safe-area conflict with `Copy to` / `Move to` panels and Android system navigation.
- **2026-06-06** - by `/spec` hygiene pass
  - Removed a duplicated second strategic draft accidentally concatenated into the same file; kept the refined version with the explicit layer-hierarchy invariant and compact-height handling.
  - Added §3.3 Owner inputs and closed the §0 approval gate under the 2026-06-06 owner autonomy directive.
