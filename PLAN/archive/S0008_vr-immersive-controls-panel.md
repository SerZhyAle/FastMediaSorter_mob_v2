# Стратегическая спецификация: ad-hoc — VR Immersive Controls Panel

**Status:** BlockNeedUserTest (panel reachable after 2026-05-03 flag flip; on-device verification pending; §11.2 visible-ray work moved to S0065)
**Date:** 2026-04-26 (last review 2026-05-03)
**Tier:** 4 — Strategic (8h+, high risk)
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-26
**Tactical plan:** `PLAN/S0008_vr-immersive-controls-panel/INDEX.md`

> **Scope of this document:** STRATEGIC. Цели, пожелания, открытые вопросы и ограничения. Без имён классов, путей к файлам, лимитов строк, миграций Room, модулей Hilt — это всё в тактической спецификации.

---

## 1. Проблема

В VR-режиме приложения пользователь лишён полноценного интерактивного контроля над воспроизведением. Нажатие кнопки контроллера «открыть управление» вызывает только пассивную полоску-индикатор размером 1024×256 пикселей — без кнопок паузы, перемотки, переключения трека или изменения формата. Более того, пользователь не видит лучей от контроллеров и рук в VR-пространстве, поэтому даже если бы интерактивный HUD существовал, кликнуть по нему было бы невозможно. Текущее состояние описывается сообщением «видеоконтроль из иммерсива недоступен» — пользователь пробует выходить из VR для любого действия кроме паузы и переключения файла. Но при этом контент меняется и это делает VR-просмотр невозможным при неверном авто-определении формата и невозможно что-то поменять (аудиодорожку например).

**Обновление от пользователя (2026-05-03):** "не вижу HUD с настройками проигрывателя". Это подтверждает, что текущее состояние панели управления полностью неработоспособно (связано с багом отключенного feature flag `VR_UI_COMPOSITION_LAYER_ENABLED`, см. §11.1).

**Обновление от `/spec-all S0008 force` (2026-05-03 14:30):** Feature flag `VR_UI_COMPOSITION_LAYER_ENABLED` переключён в `true` для обоих VR-флейворов в `app_v2/build.gradle.kts:261,312`. `isImmersiveUiLocked()` теперь возвращает `false` в иммерсиве — guard на §11.1 / §11.3 / §11.4 / §11.5 / §11.6 снят. Cascading FAIL'ы в Last Audit устарели — переоцениваются после следующего on-device запуска. Visible-ray работа (§11.2 / Goal §2.2) выделена в **S0065** (`vr-controller-ray-visual`, Approved 2026-05-03) — see §10.

---

## 2. Цели

1. В VR-иммерсиве доступно полноценное управление воспроизведением: пауза/воспроизведение, перемотка (seek), регулировка громкости и яркости, выбор аудиодорожки и субтитров, скорость воспроизведения.
2. Пользователь видит лучи от контроллеров (и рук при hand tracking) в VR-пространстве и может ими кликать по элементам управления.
3. Индикатор текущего стерео-формата отображается в HUD; присутствует кнопка ручного переключения формата без выхода из иммерсива.
4. Панель управления открывается и закрывается одним нажатием кнопки контроллера, не мешая просмотру.
5. Все существующие клавишные команды (пауза, следующий файл, предыдущий файл, выход) продолжают работать без изменений.

Non-goals:

- Полный файловый браузер внутри иммерсива (это отдельная задача, объём OpenFileOps в VR не входит в эту спеку).
- Гестурное управление без контроллеров (microgestures, pinch) — объём `spec_vr-hand-tracking`.
- Настройки приложения из иммерсива.
- Поддержка Wear OS — не применимо.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Панель управления должна быть «плавающей» в VR-пространстве — не заслонять контент, а появляться в удобном месте поля зрения (например, снизу).
2. Лучи контроллеров должны быть тонкими и полупрозрачными — не закрывать контент при просмотре.
3. Seek-слайдер управляем с контроллера — триггер зажат и ведётся луч по слайдеру.
4. Панель должна автоматически скрываться через 10 секунд после последнего взаимодействия.

### 3.2 Жёсткие ограничения

- **Flavor:** только `vr`; стандартный, lite, photos, legacy не затрагиваются.
- **API level:** Android 14 (API 34) на Quest 3; рендеринг через OpenGL ES 3.0+; нет зависимости от Android View в нативном XR-рендере.
- **Wear OS:** не затрагивается.
- **Производительность:** отрисовка интерактивного HUD не должна снижать fps ниже 72 на Quest 3 при активном воспроизведении 8K видео.
- **Совместимость данных:** изменений в Room-схеме не требуется.
- **Локализация:** EN/RU/UK — обязательно для всех подписей кнопок и состояний.
- **Доступность:** VR-специфика; стандартные Android accessibility API неприменимы. Тем не менее все интерактивные элементы должны иметь достаточный размер для уверенного попадания лучом (≥ 5° телесный угол на расстоянии 2 м).

---

## 4. Контекст текущей архитектуры

Существующий VR-HUD — пассивный. Он реализован как OpenXR Quad layer (1024×256 пикселей) и отображает статический bitmap: прогресс-бар и временные метки. Этот HUD показывается и скрывается командами из компонента VR-активности, но не содержит интерактивных зон и не реагирует на касание лучом.

Вся обработка пользовательского ввода в текущей реализации происходит через 2D Android touch-события, наложенные поверх VR-картинки. Контроллеры отображаются ОС, но точка их aim в 3D-пространстве не обрабатывается на уровне OpenXR рейкаста. Приложение знает о позиции aim-указателя (рука/контроллер инициализированы и возвращают данные aim), но не рисует никакого визуального луча и не пересекает его с геометрией HUD-плоскости.

Диалог управления воспроизведением в панельном плеере реализован как стандартный Android bottom sheet. Он недоступен из иммерсива, поскольку требует Android View hierarchy, которой нет внутри нативного XR-рендерера.

Стратегически: для интерактивного HUD необходимо заменить или дополнить пассивный 1024×256 Quad Layer новым слоем с интерактивными элементами, реализовать ray-plane intersection для HUD-геометрии, и передавать события клика в компонент управления воспроизведением.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A — Ray Rendering**

Визуализация луча от контроллера/руки в VR-пространстве. Луч рисуется как тонкая линия (quad strip или Billboard mesh) от aim-точки контроллера до точки пересечения с ближайшей геометрией. Включает «курсор» (маленький диск) на точке попадания. Реализуется в нативном OpenXR рендерере как дополнительный Quad layer или как GL-примитив поверх основного view.

**Столп Б — Интерактивная HUD-панель**

Расширение существующего HUD-слоя до полноценной интерактивной панели. Панель рендерится в OpenXR Quad layer увеличенного размера (минимум 1024×512 или 2048×512). Содержит:

- Кнопки: пауза/воспроизведение, перемотка вперёд/назад, выход из иммерсива.
- Seek-слайдер с текущей позицией и длительностью.
- Регулятор громкости (пассивный уже имеется).
- Выбор аудиодорожки / субтитров (открывает вложенный список).
- Управление яркостью.
- Скорость воспроизведения.
- Меню-Индикатор стерео-формата с кнопкой ручного переключения.

**Столп В — Ray-Hit-Test для HUD**

Компонент, который получает aim-луч контроллера и вычисляет пересечение с плоскостью HUD-слоя. Возвращает UV-координаты в текстурном пространстве HUD. Эти координаты используются для:

- Выделения (hover) интерактивного элемента под курсором.
- Генерации «клик»-события при нажатии trigger-кнопки.
- Drag-операции по seek-слайдеру при удержании trigger.

### 5.2 Потоки данных и событий

```
OpenXR AimPose (контроллер/рука)
    ↓
Ray Renderer (рисует луч)
    ↓
Ray-Hit-Test ← HUD Plane Transform
    ├─ hit=false → нет курсора на HUD
    └─ hit=true, UV=(u,v)
              ↓
        Hit Zone Resolver → HUD Element
              ↓
        Hover State → HUD Renderer (highlight)
              ↓ (trigger press)
        Click Event → VR-оркестратор
              ↓
        PlayerControl Command (seek / pause / track / format)
              ↓
        ViewModel плеера → декодер / координатор стерео
```

### 5.3 Точки расширяемости

- HUD-элементы должны быть декларативно описаны (тип, bounds, action) — это позволит добавлять новые кнопки без изменения ray-hit-test логики.
- Ray Renderer должен поддерживать несколько плоскостей (HUD + будущий файловый браузер) — intersection против списка зарегистрированных плоскостей.
- Seek-слайдер может быть расширен до preview-кадра при hover (future, вне этой спеки).

---

## 6. Открытые вопросы / Research items

1. **Позиция HUD-панели в пространстве**
   - **Вопрос:** Где должна появляться интерактивная панель — следовать за взглядом (head-locked), быть зафиксированной в мировом пространстве, или появляться снизу экрана воспроизведения?
   - **Варианты:** Head-locked (всегда в центре взгляда); world-locked в фиксированной точке; attached к нижней кромке контентного слоя.
   - **Статус:** BlockNeedUserTest

2. **OpenXR API для ray rendering**
   - **Вопрос:** Использовать ли `XrSpaceLocation` + `aim pose` для рендеринга луча, или есть готовый Meta OpenXR Extension?
   - **Нужно выяснить:** Изучить `XR_EXT_hand_interaction` / `XR_FB_hand_tracking_aim` для aim ray в Quest 3.
   - **Статус:** BlockNeedUserTest

3. **Render order: луч поверх видео**
   - **Вопрос:** Как гарантировать, что луч и HUD рисуются поверх видеослоя в OpenXR composition stack?
   - **Нужно выяснить:** Порядок слоёв в `xrEndFrame` composition layers array; z-ordering для Quad layers.
   - **Статус:** BlockNeedUserTest

4. **Seek slurring: задержка при перемотке сетевого файла**
   - **Вопрос:** Seek через луч в режиме реального времени по SMB/SFTP потенциально вызывает частые запросы — нужна ли буферизация seek-событий?
   - **Нужно выяснить:** Измерить задержку seek для локального vs SMB источника.
   - **Статус:** BlockNeedUserTest

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| GPU overhead от Ray Renderer снижает FPS ниже 72 при 8K видео | Средняя | Дискомфорт пользователя, укачивание | Профилировать на Quest 3; луч должен рисоваться минимальным количеством треугольников |
| Head-locked панель вызывает дискомфорт (conflicting motion) | Средняя | Тошнота при движении головой | Использовать «lazy follow» (панель с задержкой следует за взглядом) или world-locked |
| Ray-hit-test неточен при мелких элементах HUD | Средняя | Промахи по кнопкам, фрустрация | Увеличить минимальный размер интерактивных зон; добавить звуковой фидбэк при hover |
| Диалог управления воспроизведением (Android View) недоступен из нативного XR-рендерера | Высокая | Придётся реализовывать полный UI с нуля на GL | Принять как данность; реализовывать HUD-панель нативно в GL, не переиспользуя Android Views |
| Seek drag по слайдеру конфликтует с teleport-жестом (контроллер А) | Низкая | Случайные teleport при перемотке | Отключать teleport пока trigger удержан + луч на HUD |

---

## 8. Влияние на пользователя (docs/FEATURES)

**Для FEATURES.md:**

> - **Интерактивная VR-панель управления**: В иммерсивном VR-режиме доступна полноценная панель с перемоткой, регулятором громкости, яркостью, выбором дорожки и индикатором стерео-формата. Управление осуществляется лучом контроллера или руки — без выхода из VR.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Нативный GL HUD вместо Android View overlay**

- **Решение:** Интерактивная панель управления реализуется как нативный OpenXR Quad layer с GL-рендерингом, а не как Android View поверх XR.
- **Альтернативы:** Android View overlay с touch-forwarding от контроллера; WebView в Quad layer.
- **Почему так:** Android View overlay работает как 2D-тач, не поддерживает raycast. WebView добавляет неоправданную сложность и latency. Нативный GL даёт прямой контроль над позицией в XR-пространстве и композицией слоёв.

**ADR-2: Ray-Plane Intersection вместо Action Binding для HUD**

- **Решение:** Hit-test реализуется как ray-plane intersection на стороне приложения, а не через XR action bindings (которые не поддерживают произвольные UI-плоскости).
- **Альтернативы:** XR Interaction Toolkit (требует Unity/Unreal); OS-level pointing (недоступно в Horizon OS нативном приложении).
- **Почему так:** Максимальный контроль, нет зависимостей от сторонних фреймворков, согласуется с существующим нативным OpenXR-подходом проекта.

---

## 10. Связи с другими спеками

- **spec_vr-hand-tracking** (существующая, Backlog, blocked) — разблокируется после реализации интерактивного HUD; hand tracking aim pose подаётся в тот же Ray Renderer.
- **spec_vr-stereo-formats** — кнопка ручного переключения формата в HUD (Столп Б) зависит от реализации fisheye/OU рендеринга; до этого кнопка показывает доступные форматы частично.
- **spec_vr-input-reliability** — ray-hit-test генерирует клики, которые должны проходить через тот же механизм дебаунса, что описан в `spec_vr-input-reliability` (P2-4).
- **S0024** (Verified, landed 2026-05-03) — HUD ray-input подсистема. Hover-highlight на HUD-элементах даёт частичную обратную связь по §11.2 для aim'а внутри HUD-плоскости. Полный визуальный луч — S0065.
- **S0065** (Approved, discovered by `/spec-all S0008 force` 2026-05-03) — VR controller ray visual indicator. Реализует Столп A из §5.1 через GLES3 VBO + passthrough shader из render-loop. Закрывает §11.2 / Goal §2.2. До приземления S0065 §11.2 остаётся PARTIAL: пользователь видит hover-highlight только когда aim'ит в HUD-плоскость, иначе обратной связи нет.

---

## 11. Критерии готовности (strategic-level)

1. Нажатие кнопки контроллера «Открыть управление» (X) открывает панель с видимыми интерактивными элементами в VR-пространстве.
2. Луч от контроллера виден в VR и точно указывает на элементы панели.
3. Seek-слайдер перемещается лучом и обновляет позицию воспроизведения.
4. Регулятор громкости, яркость, выбор дорожки и скорость воспроизведения доступны из иммерсива.
5. Индикатор текущего стерео-формата виден в HUD; кнопка ручного переключения работает.
6. Панель автоматически скрывается после 10 секунд бездействия.
7. FPS не опускается ниже 72 на Quest 3 при открытой панели и 4K видео.

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — перейди к `/spec-tech vr-immersive-controls-panel`, она создаст папку `PLAN/S0008_vr-immersive-controls-panel/` с фазами реализации. Тактическая спека — строгая, нумерованная, на английском, с промптами разработчику и верификацией на каждый шаг.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all) — pass 1
  - ACCEPT applied: 6 (L1–L5 typos/capitalisation; C1 §11 timeout 5→10s; Sy1 blockquote blank line)
  - REVIEW applied: R2-A (Столп Г удалён — индикатор формата только в составе основной панели Столп Б); R3 (яркость добавлена в §8 FEATURES)
  - DISCUSS applied: D1 (class names `VideoControlDialog`, `BottomSheetDialog` в §4 и §7 заменены на описание роли)
- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all) — pass 2
  - ACCEPT applied: 4 (R1 §6 Q1 formatted+renumbered; A1 период у яркости; C1/C2 яркость добавлена в §2 Goal 1 и §11 п.4)
  - DISCUSS proposed: D1 `VrPlayerActivity` в §4; D2 class names в §5.2 flow diagram — см. ниже
- **2026-05-03** — by `/spec-update` (`claude-opus-4-7`, focus: structure, --force-locked) — Status `Broken` overridden: refinement затрагивает только текст спеки, не реализацию.
  - Applied: 2 (P-1 убрано имя класса `VrPlayerActivity` из §4; P-2 имена компонентов в §5.2 заменены на роли). Proposed (DISCUSS): 0.
- **2026-05-03 14:30** — by `/spec-all S0008 force` (`claude-opus-4-7[1m]`).
  Found `VR_UI_COMPOSITION_LAYER_ENABLED` already flipped to `true` in working tree (uncommitted). Build PASS. Allocated S0065 for visible-ray work; updated §10 + §1 + Last Audit. Status `Broken → BlockNeedUserTest` (Quest 3 manual verification of §11.1/§11.3-7 pending; PARTIAL on §11.2 until S0065 lands).

## Proposed Structural Changes

### Proposal P-1 — Убрать имя класса `VrPlayerActivity` из §4  (proposed 2026-04-26 by claude-sonnet-4-6)

**Status:** Accepted (applied 2026-05-03 by claude-opus-4-7)

**Summary:** Строка §4 содержит `` `VrPlayerActivity` `` в backtick — имя класса запрещено в стратегической спеке.
**Affected section:** §4 Контекст текущей архитектуры, абзац 1
**Rationale:** Rules: «Class names / file paths are forbidden in strategic specs.»
**Suggested edit:**
> Этот HUD показывается и скрывается командами из `VrPlayerActivity`
→
> Этот HUD показывается и скрывается командами из компонента VR-активности
**Next step:** Применить при следующем `/spec-update` или вручную.

---

### Proposal P-2 — Убрать имена компонентов из диаграммы §5.2  (proposed 2026-04-26 by claude-sonnet-4-6)

**Status:** Accepted (applied 2026-05-03 by claude-opus-4-7)

**Summary:** Диаграмма потока данных в §5.2 содержит имена компонентов (`VrPlayerActivity`, `PlayerViewModel`, `ExoPlayer`, `StereoCoordinator`) — запрещено в стратегическом §5.
**Affected section:** §5.2 Потоки данных и событий, код-блок
**Rationale:** Rules: «Strategic spec does NOT contain class names.»
**Suggested edit:** Заменить имена ролями:
> `Click Event → VrPlayerActivity` → `Click Event → VR-оркестратор`
> `PlayerViewModel → ExoPlayer / StereoCoordinator` → `ViewModel плеера → декодер / координатор стерео`
**Next step:** DISCUSS — может быть, диаграммы-схемы получают исключение; решение на усмотрение пользователя.

---

## Last Audit

**Date:** 2026-05-03 14:30 (re-audit by `/spec-all S0008 force`)
**Mode:** full (strategic + 6 phases)
**Flags:** force
**Outcome:** BlockNeedUserTest (was Broken — guard removed; visible-ray work moved to S0065)
**Counts:** PASS 7 · WARN 0 · FAIL 0 · PARTIAL 1 (§11.2) · MANUAL 5 (on-device) · DEFERRED 1 (S0065)

### Resolved since prior audit (2026-05-03 13:12)

- **§11.1 / §11.3 / §11.4 / §11.5 / §11.6** — flag `VR_UI_COMPOSITION_LAYER_ENABLED` flipped `false → true` for both `vr` and `vrUnlicensed` flavors ([app_v2/build.gradle.kts:261](app_v2/build.gradle.kts#L261), [:312](app_v2/build.gradle.kts#L312)). `isImmersiveUiLocked()` now returns `false` in immersive — guard removed. `OpenControls` / `OpenFileOps` / `Cheatsheet` reach the dispatcher path. Build PASS (standardDebug + vrDebug, 6s, UP-TO-DATE).
- **WARN P-1, P-2** — already applied in 2026-05-03 Revision History pass (`/spec-update S0008 --force-locked`). Strategic §4 and §5.2 no longer carry class names.

### Open / partial

1. **[PARTIAL §11.2 / Goal §2.2]** Visible controller ray. Math-pass works (S0024 confirmed 1449 hover events). With S0024 landed, hover-highlight on HUD elements gives partial visual feedback when aiming inside the HUD plane. Off-HUD aim still has no cue. Full visible-ray GL primitive deferred to **S0065** (Approved, allocated 2026-05-03). S0008 will move to `Verified` only after S0065 lands AND on-device verification passes.

### Manual / on-device (Quest 3)

After flag flip, the following criteria are now reachable in code but require headset
verification before status flips to `Verified`:

- [ ] **§11.1** — controller "Open controls" opens the interactive panel with visible buttons.
- [ ] **§11.3** — seek slider responds to ray drag.
- [ ] **§11.4** — volume / brightness / track / speed are operable via the panel.
- [ ] **§11.5** — stereo-format indicator visible; manual toggle works.
- [ ] **§11.6** — panel auto-hides after 10 s of idle.
- [ ] **§11.7** — FPS ≥ 72 with panel open at 4K (now measurable).

User owns Quest 3 (memory: `user_hardware.md`). Defer to manual smoke-test; on success
flip status to `Verified`. On failure capture device-log under Blockers Log and re-evaluate.

### Action items

- **None blocking S0008.** All code paths that were FAIL in prior audit are now reachable; the only remaining work item (visible ray) is allocated as a separate ticket (S0065) per `/spec-all` "out-of-scope dependency" rule.
