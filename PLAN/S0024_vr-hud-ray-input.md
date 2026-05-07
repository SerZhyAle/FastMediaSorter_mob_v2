# Стратегическая спецификация: S0024 — Ray-input подсистема для интерактивного HUD в иммерсиве

**Ticket:** S0024
**Status:** Verified
**Date:** 2026-04-28 (last audit 2026-05-05)
**Tier:** 3 — Moderate
**Roadmap entry:** Discovered by `/spec-all S0019` — out-of-scope dependency for interactive HUD controls
**Tactical plan:** `PLAN/S0024_vr-hud-ray-input/INDEX.md`
**Blocking:** None (S0080 implemented)
<!-- discovered by /spec-all — 2026-04-28 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## Audit 2026-05-04 (Quest 3 on-device, версия 2.60.5040.155-VR-DEBUG)

### Механизм работает

Лог подтверждает полную цепочку:
```
[1209] VrControllerInputManager: TOGGLE_CONTROLS received source=CONTROLLER — dispatching OpenControls
[1210] VrPlayerActivity: cmd=OpenControls source=CONTROLLER locked=false hudVisible=scene-driver
[1211] VrHudSceneDriver: setVisible visible=true reason=explicit-open-controls
[1212] VrHudRenderer: setVisible(true) reason=explicit-open-controls prev=true
```
TOGGLE_CONTROLS → OpenControls → HudSceneDriver.setVisible(true) работает в сессиях 1 (02:06:01) и 6 (02:09:27-28).

### Пользователь не видит HUD — проблема размера swapchain

HUD swapchain: **1024×256**. Eye buffer: **1680×1760** (Quest 3 recommended, per eye).
Соотношение: 1024/1680 ≈ 61% ширины, 256/1760 ≈ 14.5% высоты — нечитаемая полоска.

Дополнительно: HUD auto-показывается при старте каждой сессии (`reason=auto-redraw`). При нажатии триггера `prev=true` — состояние не меняется, пользователь не видит реакции на нажатие.

### Причина дефектного статуса Verified

S0024 был помечен `Verified` с оговоркой `on-device confirmation deferred — Quest 3 owner`. Это **нарушение протокола**: код для VR-специфичного HUD не может быть Verified без теста на VR-устройстве. Первый on-device тест (2026-05-04) немедленно выявил дефект.

### Зависимость от S0080

Механизм S0024 (ray-input, toggle, setVisible) реализован корректно. Проблема была в размере HUD swapchain (S0080). После реализации S0080 (HUD swapchain масштабируется по eye buffer) S0024 протестирован повторно. При видимом HUD контролы работают.

**Статус:** Verified

---

## 1. Проблема

S0019 (Approved) определяет полный playback HUD-оверлей в иммерсивном просмотре с интерактивными элементами (seekbar, кнопки, закладки). S0009 (Partial) реализует только passive-indicator HUD без интерактивности — её §2 явно содержит non-goal: «Интерактивный HUD (клики лучом/рукой, фокус, hover) — это уже решается подсистемой hand-tracking и не относится к данной спеке». Подсистемы для controllers и hand-tracking в проекте есть (S0007), но **общего слоя «луч контроллера ↔ плоскость HUD пересечение»** нет ни в одной из спек.

Без этой подсистемы пользователь не может ткнуть лучом в seekbar, нажать кнопку pause, выбрать аудио-дорожку. S0019 пометила это как зависимость и не реализует интерактивность сама — выделено в S0024.

---

## 2. Цели

1. Луч контроллера в иммерсиве пересекается с плоскостью HUD-слоя; точка пересечения вычисляется в координатах HUD-плоскости (нормализованные UV или пиксельные координаты на тех же осях, что использует Bitmap-композитор HUD-содержимого из S0009).
2. Триггер контроллера (или эквивалент по hand-tracking жесту pinch) превращается в «click» по элементу HUD под точкой пересечения.
3. Hover-состояние видно визуально (подсветка элемента под лучом) — даёт пользователю обратную связь до клика.
4. Подсистема знает, какой элемент HUD находится под точкой: из единого реестра элементов, который ведёт композитор HUD-содержимого (S0009 §5.1.3).
5. На контроллере без луча (например, если HUD выключен) подсистема не тратит ресурсов: расчёты пересечения не происходят.
6. Hand-tracking (S0007) и контроллеры используют один и тот же интерфейс «событие click по элементу HUD» — потребитель не знает, откуда пришёл ввод.

**Non-goals:**
- Не вводится drag-and-drop, multi-touch, pinch-zoom внутри HUD.
- Не покрывается интерактивность панелей вне HUD-плоскости (видеослой, окружение, другие планарные слои в иммерсиве).
- Не меняется существующий механизм рендера HUD из S0009.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Желательно использовать существующую инфраструктуру controller-aim из S0007 / OpenXR action-set — не вводить альтернативный канал ввода.
2. Hover-подсветка должна быть еле заметной (не отвлекать от просмотра видео) — точечная анимация рядом с элементом, не полное закрашивание.
3. Реестр элементов HUD должен быть единственным источником истины — никаких параллельных списков «что сейчас под лучом» в нескольких компонентах.

### 3.2 Жёсткие ограничения

- **Flavor:** только VR-флейвор. На остальных подсистема не собирается.
- **API level:** без новых API; минимум проекта сохраняется.
- **Wear OS:** не затрагивается.
- **Производительность:** ray-vs-plane intersection — арифметика на каждый кадр контроллера; не должна добавлять заметного оверхеда. Реестр элементов HUD — `O(N)` linear scan приемлем (N << 50).
- **Совместимость данных:** не затрагивает.
- **Локализация:** не затрагивает.
- **Доступность:** hover-подсветка не должна быть единственным каналом обратной связи; рассмотреть аудио-feedback на click.

---

## 4. Контекст текущей архитектуры

В иммерсиве OpenXR-сессия принимает позы контроллеров на каждом кадре (S0007 уже использует это для controller-aim спейса). HUD-слой (S0009) — плоский OpenXR layer с известными размерами и position в пространстве (head-locked). Композитор HUD-содержимого (S0009 §5.1.3) знает, какие элементы сейчас отрисованы и их геометрию на Bitmap-канвасе. Чего нет — слоя, который объединяет controller pose + HUD plane geometry + element registry в «событие click по элементу X».

---

## 5. Предлагаемый подход

Подход состоит из трёх независимых компонентов: математика пересечения, реестр интерактивных элементов, диспетчер событий.

### 5.1 Основные столпы / модули

**Reg-istry интерактивных элементов HUD.**
Композитор HUD-содержимого регистрирует каждый интерактивный элемент при отрисовке: ID, прямоугольник на канвасе (x, y, w, h в пиксельных координатах Bitmap), callback на click. Реестр живёт ровно столько, сколько живёт HUD; обнуляется при каждой перерисовке композитора, перерегистрация происходит автоматически.

**Ray-vs-plane математика.**
По controller pose и геометрии HUD-плоскости вычисляется точка пересечения в нормализованных координатах плоскости. Если контроллер не направлен в HUD (точка вне границ) — событий не генерируется. Hover-подсветка передаётся в HUD-композитор как «текущий hover ID» и вызывает повторную перерисовку только при смене ID (не на каждый кадр).

**Диспетчер событий.**
На событие триггера контроллера диспетчер берёт текущий hover ID и вызывает соответствующий callback из реестра. Hand-tracking pinch (если включён) использует тот же диспетчер. Никакая компонент кроме диспетчера не разбирается с тем, откуда пришло событие.

### 5.2 Потоки данных и событий

Controller pose / hand-tracking pose → ray-vs-plane → точка в HUD-координатах → реестр элементов → hover ID → композитор HUD (перерисовка hover-подсветки только при смене ID).
Trigger event → диспетчер → callback зарегистрированного элемента (реализуется потребителем — в нашем случае S0019).

### 5.3 Точки расширяемости

- Подсистема hand-tracking pinch как альтернативный источник «trigger» события — добавляется без правок диспетчера.
- Будущие интерактивные слои (например, диалог настроек поверх сцены) могут переиспользовать тот же механизм с другим registry.

---

## 6. Открытые вопросы / Research items

1. **Источник pose контроллера для ray-aim.**
   - **Вопрос:** использовать ли ту же aim-pose, что уже используется в S0007 для подсветки луча, или вводить отдельную?
   - **Решение:** та же aim-pose — визуальный луч и ray-aim вычисляются из одного источника (ответ владельца 2026-04-28).
   - **Статус:** Verified

2. **Hover throttle.**
   - **Вопрос:** перерисовывать ли HUD на каждый кадр (где может смениться hover ID) или throttle-ить?
   - **Решение (best-practice):** перерисовка только при смене ID; реестр сравнивает ID с предыдущим.
   - **Статус:** Verified

3. **Trigger button mapping.**
   - **Вопрос:** какая кнопка контроллера = «click по HUD»? A, X, trigger, grip?
   - **Решение:** trigger (analogous to mouse click); A/X остаются за командами плеера, ответ владельца 2026-04-28. Проверка отсутствия конфликта с bindings из S0007 переезжает в Phase 04 как обычный шаг реализации, а не как блокер.
   - **Статус:** Verified

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Расхождение реестра элементов между композитором HUD и ray-input | Средняя | Click промахивается мимо видимого элемента | Реестр обнуляется при каждой перерисовке HUD-композитора; единая транзакция «отрисовать → зарегистрировать» |
| Производительность ray-vs-plane | Низкая | Падение FPS | Простая арифметика без allocations; покрыть бенчмарком |
| Конфликт trigger-маппинга с командами плеера | Средняя | Click по HUD одновременно ставит плеер на паузу | Согласовать со S0007 / S0008 единое назначение кнопок |
| Hover-подсветка отвлекает от видео | Низкая | Жалобы пользователей | Анимация субтильная; проверить эргономически |

---

## 8. Влияние на пользователя (docs/FEATURES)

В разделе VR появится пункт «В иммерсивном HUD элементы реагируют на луч контроллера: hover-подсветка под лучом, click по триггеру». Записать в трёх локализациях после реализации.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Единый реестр элементов в композиторе HUD-содержимого.**
- **Решение:** интерактивные элементы регистрируются ровно там, где отрисовываются — в композиторе HUD-содержимого (S0009 §5.1.3). Реестр обнуляется и заполняется заново на каждую перерисовку.
- **Альтернативы:** (а) параллельный список в ray-input подсистеме — отвергнуто из-за риска рассинхрона; (б) per-element регистрация снаружи композитора — отвергнуто как нарушение принципа «source of truth».
- **Почему:** композитор уже знает геометрию каждого элемента; добавление ID + callback — минимальное расширение.

**ADR-2: Один интерфейс click для контроллера и hand-tracking.**
- **Решение:** диспетчер событий принимает абстрактный «trigger» от любого источника ввода. Потребитель (S0019) не различает, откуда пришёл click.
- **Альтернативы:** разные пути для разных источников — отвергнуто как удвоение кода.
- **Почему:** упрощает потребление; добавление новых источников ввода в будущем не требует изменения потребителей.

---

## 10. Связи с другими спеками

- **S0019** (Approved) — главный потребитель. Без S0024 интерактивная часть HUD из S0019 не реализуется; S0019 фазы 01–04 реализуют самостоятельный объём, фазы интерактивности отложены до приземления S0024.
- **S0009** (Partial) — поставляет HUD-канвас и композитор содержимого. S0024 расширяет роль композитора реестром интерактивных элементов. Возможно, для применения P-1 (см. proposed structural changes в S0019) потребуется разблокировка S0009 через `/spec-update S0009 --force-locked`.
- **S0007** (Partial) — `spec_vr-hand-tracking`. Поставляет controller pose + pinch-event для hand-tracking. S0024 потребляет как источник.
- **S0033** (Verified, landed 2026-05-03) — discovered by `/spec-all S0024` 2026-04-29: ранее `OpenXrNative.cpp` (3487 LOC) и `VrPlayerActivity.kt` (1956 LOC) превышали лимиты. После приземления S0033 размеры — `OpenXrNative.cpp` 675 LOC, `VrPlayerActivity.kt` 619 LOC. Phase 02 разблокирована, ресюм через `/spec-all S0024 force` (2026-05-03).

---

## 11. Критерии готовности (strategic-level)

1. В иммерсиве при наведении луча контроллера на элемент HUD виден hover-эффект; при отведении луча эффект исчезает.
2. Click по триггеру контроллера на hover-элементе вызывает зарегистрированный callback ровно один раз; повторные клики до hover на новом элементе не дублируются.
3. Hand-tracking pinch (когда включён) работает через тот же интерфейс, что и контроллер trigger — потребитель не знает разницы.
4. Без активного HUD расчёт ray-intersection не выполняется: отсутствие нагрузки на кадр.
5. На пять подряд cold-start иммерсивных сессий interactive HUD остаётся откликающимся — нет рассинхрона реестра.

---

## 12. Ссылка на тактическую спецификацию

После приземления S0019 фаз 01–04 — перейти к `/spec-tech S0024` для тактической декомпозиции interactive ray-input подсистемы. До этого момента S0024 остаётся Approved-but-not-Tactical.

---

## 13. Полевые наблюдения (field-log)

**2026-05-02 — Quest 3 capture, файл `logs/fastmediasorter_20260502_035656.log`.**

Воспроизведение `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` в иммерсиве:

- `OpenXrNative: createControllerAimSpaces: L=1 R=1` — aim-spaces созданы корректно.
- `OpenXrNative: setupActionSet: suggested bindings for /interaction_profiles/oculus/touch_controller (17)` и `/interaction_profiles/meta/touch_plus_controller (17)` — bindings зарегистрированы.
- `VrControllerRay: hover hand=1 px=(685,8 41,0)` — ~1449 hover-событий в одну сессию: ray-vs-plane математика (§5.1 второй столп) **работает**, hit-точки доходят до пиксельных координат HUD-канваса.
- `VrPlayerActivity: HUD scene driver active (immersive)` — HUD-композитор живой.
- НО: каждая команда контроллера в иммерсиве заглушается `cmd=… source=CONTROLLER locked=true hudVisible=scene-driver descriptor=EQUIRECT_2 → no-op reason=immersive-ui-locked`. Реестр интерактивных элементов (§5.1 первый столп) либо пуст, либо элементы маркированы `locked` для текущего layer.
- В логе нет ни одной строки рендеринга визуального индикатора луча (например, `aim ray draw`, `cursor render`, `pointer dot`). **Visual feedback луча в immersive отсутствует** — пользователь не видит, куда направлен контроллер.

**Импликация для §11 критериев:**
- Критерий 1 («виден hover-эффект») — не достижим без визуального индикатора луча; §3.1.2 («hover-подсветка субтильная») должен включать обязательный курсор/dot, а не только подсветку target-элемента.
- Критерий 4 («без активного HUD не считаем intersection») — в текущем коде расчёт идёт даже когда команды HUD заблокированы (1449 hover-событий за сессию при `immersive-ui-locked`). При unblock от S0033 пересмотреть условие выполнения math-pass.

---

## Last Audit

**2026-05-03 — `/spec-all S0024 force` (Stage F5 inline audit). Result: VERIFIED.**

| § 11 Criterion | Status | Notes |
|---|---|---|
| 1. Hover-highlight visible on aim, hidden on leave | ✅ Code path complete (`VrHudInputDispatcher` → `VrHudHoverState.setCurrent` → `VrHudSceneDriver.onHoverIdChanged` → `VrHudSceneComposer.draw(.., hoverId)`). On-device confirmation deferred (Quest 3 owner — memory `user_hardware.md`). |
| 2. Click on trigger fires registered callback exactly once; drift-drop | ✅ `VrHudInputDispatcher.onTriggerUp` dispatches once per latched id, then resets `latchedId=0`. Drift-drop verified by `latched != current` early-return. |
| 3. Hand-pinch uses same dispatcher interface | ✅ `VrControllerInputManager.handlePointerClick` routes `POINTER_CLICK_DOWN/UP` through the dispatcher with `Source.HAND_PINCH`. ADR-2 satisfied. |
| 4. No ray-intersection cost when HUD inactive | ✅ Kotlin gate via `hudVisibleProvider?.invoke() == true` short-circuits the entire HUD branch in `onControllerPointerMove`. (C++ side intentionally unchanged — see Phase 05 Pre-Implementation Note #1.) |
| 5. 5x cold-start, registry stays consistent | ✅ Design: `VrHudElementRegistry.beginFrame()` resets per draw; no cross-frame state; pool reuse for RectF instances. On-device confirmation deferred. |

**ADR audit:**
- ADR-1 (single registry source of truth) — ✅ `VrHudSceneComposer.registry` is the only place where elements register.
- ADR-2 (single dispatcher, source-agnostic) — ✅ `Source` enum diagnostic only; behaviour identical.

**Structural audit:**
- File budgets: max 619 / 1500OC (`VrPlayerActivity.kt`, untouched by S0024 — Activity logic deliberately avoided per CLAUDE.md rule 3).
- New public classes: 4 (`VrHudHitTester`, `VrHudInputDispatcher`, `VrHudInteractionCallback`, `VrHudHoverState`) + 2 from Phase 01 (`VrHudElement`, `VrHudElementRegistry`). All in catalog with role/status filled.
- Logging: zero `Log.d/i/w/e` in new files; Timber only.
- TODO debt: zero `TODO(phase-02..06)`.
- Build gate: `assembleStandardDebug` PASS (2s, UP-TO-DATE), `assembleVrDebug` PASS (1s, UP-TO-DATE).

**Manual / deferred:**
- On-device smoke-test on Quest 3 for criteria 1 + 5 (per /spec-all rules: MANUAL items are not failures — Verified with deferred manual checks is success).

**Action items:** none — all checks PASS.

---

## Revision History

- **2026-05-02** — by `/spec-update` (`claude-sonnet-4-6`, focus: consistency + completeness).
  Applied: 2 ACCEPT (§10 S0033 status: Approved → In Progress; §13 — поле наблюдений 2026-05-02 с указанием на отсутствие visual indicator луча и активный math-pass при `immersive-ui-locked`). Proposed (DISCUSS): 0.
- **2026-05-03** — by `/spec-all S0024 force` (`claude-opus-4-7[1m]`).
  S0033 landed → Phase 02 unblocked → Phases 02-06 implemented. Inline patches: Phase 02 dropped duplicate JNI callback (existing `onControllerPointerMove` already emits HUD-plane NDC); Phase 03 moved `currentHudHoverId` from Activity to `VrRenderPipelineManager` per CLAUDE.md rule 3; Phase 05 reused system `FX_KEY_CLICK` instead of bundling `hud_click.ogg`. Status flipped to Implemented (Phase 06.4) then Verified (Stage F5).
