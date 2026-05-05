# S0065 — Visible controller-ray indicator in immersive

**Ticket:** S0065
**Status:** Implemented
**Implemented date:** 2026-05-03
**Date:** 2026-05-03
**Tier:** 3 — Moderate
**Priority:** 60
**Tactical plan:** [`S0065_vr-controller-ray-visual/INDEX.md`](S0065_vr-controller-ray-visual/INDEX.md)
**Roadmap entry:** Discovered by `/spec-all S0008 force` 2026-05-03 — out-of-scope dependency for §11.2.

<!-- discovered by /spec-all — 2026-05-03 -->

> **Scope:** STRATEGIC. Goal, constraints. Implementation file paths and line budgets belong in the tactical plan.

---

## 1. Проблема

S0008 §11.2 требует видимого луча контроллера в иммерсивном пространстве. Сегодня
`VrControllerRayManager` намеренно не рисует курсор (KDoc: «No cursor dot — Touch
controller users receive hardware LED + haptic feedback»). На Quest 3 в focused-XR
session аппаратный LED не виден пользователю, и Goal §2.2 проваливается.
Math-pass луча уже работает (S0024 Phase 02 + полевые наблюдения 2026-05-02:
1449 hover-событий за сессию). Не хватает только визуальной отрисовки.

С приземлением S0024 (2026-05-03) hover-highlight на HUD-элементах даёт частичную
обратную связь — пользователь видит, какой элемент под лучом. Но при aim'е вне
HUD-плоскости (видеослой, environment) обратной связи нет — пользователь
не понимает, куда направлен контроллер.

---

## 2. Цели

1. В иммерсивной XR-сессии луч контроллера виден от aim-pose до точки пересечения
   с ближайшей зарегистрированной плоскостью (HUD / panel) — тонкая линия.
2. На точке пересечения отрисован маленький курсор (диск или крест), достаточный
   для попадания по элементам ≥ 5° телесного угла на 2 м (S0008 §3.2).
3. При промахе по всем плоскостям луч либо обрезается на максимальной длине
   (например, 5 м), либо скрывается — выбор делается ниже в §6.
4. Hand-tracking aim (S0007) использует тот же визуальный примитив без
   дополнительной кастомизации.

**Non-goals:**

- Tеleport-арки, drag-визуализация, цветовые состояния hover/active — только базовый луч.
- Стилизация под Meta system pointer.
- Отрисовка лучей других контроллеров вне иммерсива.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Луч полупрозрачный, тонкий — не закрывает контент при просмотре (S0008 §3.1.2).
2. Курсор-точка субтильная, не отвлекает от видео.

### 3.2 Жёсткие ограничения

- **Flavor:** только VR.
- **Render path:** GLES3 VBO + passthrough shader из C++ render-loop. Fixed-function pipeline
  отсутствует в GLES3 (`OpenXrInput.cpp:573-575` уже отмечает этот TODO).
- **Performance:** ≤ 16 вершин на луч + квад на курсор; не должно влиять на 72 FPS бюджет.
- **Composition order:** луч + курсор рисуются ПОСЛЕ HUD/panel layers, чтобы быть
  поверх их в xrEndFrame (Research Q3 уровня S0008).
- **Идемпотентность:** включение через `nativeSetControllerRayEnabled(boolean)` уже
  существует — Phase должна реализовать эффект, а не добавлять API.

---

## 4. Контекст текущей архитектуры

`syncControllerAimRay` ([OpenXrInput.cpp:512-577](app_v2/src/vr/cpp/OpenXrInput.cpp#L512-L577))
вычисляет ray-vs-HUD-plane intersection и эмитит NDC через `emitControllerPointerMove`.
Линия 573-575 содержит явный TODO:

```cpp
// TODO(Phase 03): draw a visual ray using a GLES3 VBO + passthrough shader.
// GLES3 has no fixed-function pipeline; a proper vertex+fragment program is
// required. Deferred — NDC emission above is the Phase 02 deliverable.
(void)ctx.controllerRayEnabled.load();
```

Kotlin-API `OpenXrNative.nativeSetControllerRayEnabled(enabled)` уже зарегистрирован
([OpenXrNative.kt:122](app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt#L122))
и читается C++-стороной в `controllerRayEnabled`. Реализация эффекта — единственный
оставшийся шаг.

---

## 5. Предлагаемый подход

### 5.1 GL примитив

Создать VBO + index buffer на сессии создания (один раз). Vertex shader проецирует
два мировых endpoint в clip-space. Fragment shader рисует постоянный цвет с альфой.
Курсор — отдельный VBO квада (4 вершины) в плоскости hit-точки, ориентированный
по нормали HUD (билборд для panel/HUD; для miss — нет курсора).

### 5.2 Lifecycle

`createSessionAndSwapchains` инициализирует shader program и VBO; `destroyAll`
очищает. Per-frame путь в `renderFrame` после draw'а HUD/panel layers вызывает
`drawControllerRays` который читает aim-pose + hit-точки (уже посчитаны в
`syncControllerAimRay`) и заливает их в VBO.

### 5.3 Hide policy

При промахе — луч укорачивается до 5 м без курсора (предпочтительный вариант,
менее раздражает). Альтернатива «полностью скрывать луч» отклонена: пользователь
теряет orientation cue.

---

## 6. Открытые вопросы / Research items

1. **Render thread ownership.** Должен ли draw'ить за один JNI-call вместе с HUD,
   или регистрироваться как отдельный composition layer?
   - **Решение:** in-place в renderFrame после HUD/panel — composition layer
     для линии — overkill, требует swapchain.
   - **Статус:** Resolved.

2. **Screen-space line vs world-space.** Толщина луча в пикселях или метрах?
   - **Решение:** screen-space pixel thickness (constant 2-3 px) через geom shader
     или billboard quad по экрану. World-space metric line визуально ломается на
     дистанции.
   - **Статус:** Resolved (billboard quad, GLES3 не имеет geometry shaders).

3. **Hand-tracking aim source.** `OpenXrHandTracking::syncAim` уже существует?
   Нужно проверить, что он эмитит aim-pose так же, как контроллер.
   - **Статус:** Open — проверить в начале tactical plan.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Луч заслоняет HUD-кнопки | Средняя | Пользователь не видит, на что нажимает | Тонкий луч (2-3 px) + курсор размером с palm tip |
| FPS падение от per-frame draw | Низкая | Дискомфорт | 16 вершин — пренебрежимо мало; профилировать после |
| Z-fighting с HUD-плоскостью на 1.5 м | Средняя | Луч мерцает на дистанции | Glsl-side polygon-offset или drawing с DEPTH_TEST OFF |

---

## 8. Влияние на пользователя (docs/FEATURES)

EN: «In immersive VR, the controller and hand aim-ray is now visible as a thin line ending in a small cursor at the hit point — visible feedback for HUD interaction.»
RU: «В иммерсивном VR луч контроллера и руки виден тонкой линией с маленьким курсором в точке пересечения с HUD/панелью — видимая обратная связь при взаимодействии.»
UK: «В імерсивному VR промінь контролера та руки видно тонкою лінією з маленьким курсором в точці перетину з HUD/панеллю — видимий зворотний зв'язок при взаємодії.»

---

## 9. Архитектурные решения (ADR)

**ADR-1: GL primitive в renderFrame, не отдельный OpenXR layer.**
- **Решение:** луч рисуется как GLES3 VBO в той же FBO, что и HUD layer.
- **Альтернативы:** отдельный composition layer с swapchain — отвергнуто как избыточная
  сложность для линии в 2 вершины.
- **Почему:** минимальный latency, минимальный VRAM.

---

## 10. Связи с другими спеками

- **S0008** (BlockNeedUserTest) — родитель. §11.2 и Goal §2.2 ждут реализации.
- **S0024** (Verified) — уже даёт hover-highlight как частичную обратную связь;
  S0065 даёт ortho-feedback (видимый луч) для off-HUD aim'а.
- **S0007** (Partial) — hand-tracking aim. После приземления S0065 hand aim также
  получает визуальный луч без дополнительной работы.

---

## 11. Критерии готовности (strategic-level)

1. В immersive XR-сессии при поднятом Touch-контроллере виден луч от aim-pose до
   точки пересечения с HUD-плоскостью (или 5 м при промахе).
2. На точке пересечения отрисован курсор — диск/крест ≤ 1 см в HUD-плоскости.
3. Hand-tracking aim рисует тот же луч без дополнительного кода.
4. `nativeSetControllerRayEnabled(false)` выключает рендер (regression-safe для
   будущих fps-чувствительных режимов).
5. Нет видимого падения FPS на Quest 3 (профилирование пост-факто).
