# Стратегическая спецификация: S0289 - Полная multimodal input поддержка во всех in-house Activity

**Ticket:** S0289
**Status:** Archived
**Priority:** 50
**Date:** 2026-05-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-05-21/2026-05-22 (исполнение CLAUDE.md Rule 17 и расширение до multimodal input parity).
**Tactical plan:** `PLAN/S0289_tv-keyboard-dpad-navigation/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы и delegated assumptions. Без имён файлов, лимитов строк, миграций Room и wiring-деталей.

---

## 1. Проблема

На ТВ, Quest3 и других устройствах без чисто сенсорного сценария приложением до сих пор нельзя управлять полноценно и единообразно. Текущий S0289 уже закрыл значительную часть focus-навигации для keyboard / D-pad, но поведение по остальным input-каналам остаётся фрагментированным: mouse wheel и extra mouse buttons обрабатываются только точечно, hard-buttons и Bluetooth/HID remote buttons не имеют общего activity-level contract, gamepad / joystick полноценно интегрированы лишь в нескольких сложных экранах, а на большинстве form/list Activity эти события либо игнорируются, либо ведут себя непредсказуемо.

Итоговый дефект уже не сводится только к фокусу. Пользователь на Android TV, Quest3, телефоне с Bluetooth-клавиатурой, мышью, пультом или геймпадом должен уметь выполнять одинаково предсказуемые действия на любом in-house user-facing экране: попасть в нужный элемент, активировать его, прокрутить содержимое, открыть контекстное действие, вернуться назад и управлять playback там, где экран это допускает. Сейчас этот контракт не оформлен как единая системная способность приложения.

---

## 2. Цели

1. **Scope.** В S0289 входят все in-house user-facing Activity модуля `app_v2`, которые рисуют интерактивный UI. Transparent / no-UI Activity документируются как `n/a` и не требуют искусственной input-конфигурации.
2. **Keyboard / Bluetooth keyboard / HID remote.** На любом in-scope экране клавиши навигации, подтверждения, возврата и стандартные media / hard buttons либо приводят к ожидаемому действию, либо безопасно игнорируются, но не «теряются» случайно внутри экрана.
3. **Mouse.** На любом in-scope экране primary click активирует элемент, wheel прокручивает активный scrollable-контейнер, right-click открывает контекстное действие текущей цели или её long-click equivalent, XButton1 работает как Back, XButton2 работает только там, где у экрана уже есть осмысленный forward/history сценарий.
4. **Gamepad / joystick.** На non-player поверхностях D-pad и left stick эквивалентны для focus navigation, `A` активирует, `B` возвращает назад, `X` открывает secondary/context action, `Y` вызывает видимое auxiliary action либо остаётся no-op. На player-поверхностях triggers и analog stick сохраняют media-specific semantics.
5. **MainActivity.** Верхняя панель, вкладки, список ресурсов и launch CTA остаются полностью достижимыми keyboard / D-pad / left stick navigation; mouse wheel и gamepad buttons не ломают уже реализованную focus-логику.
6. **BrowseActivity.** Дерево / список, панель действий и вспомогательные действия одинаково доступны keyboard, mouse и gamepad; list-centric действия не требуют touch.
7. **PlayerActivity и StandalonePlayerActivity.** HUD остаётся fully focusable, pointer / wheel / gamepad / hard-buttons routed consistently, а media-specific commands не деградируют на фоне новой focus parity.
8. **Form / list screens.** Settings, AddResource, ResourceEditor, AuthSessions, KeybindingRemap, Welcome, Duplicates, cloud pickers и widget config получают единый multimodal contract поверх уже внедрённого focus-layer.
9. **Accessibility и orientation.** Focused-state различим не только цветом, поведение одинаково в portrait / landscape, а hover / pointer presence сами по себе не создают навязчивый focus-ring.
10. **Touch safety.** На устройстве с сенсорным вводом приложение не навязывает initial-focus и не рисует фокус без действия пользователя.

**Non-goals:**

- Переработка gesture-first touch UX.
- Полный redesign DialogFragment / AlertDialog input model.
- Перенос multimodal contract на Wear OS.
- Поддержка внешних Activity вне контроля приложения.
- Поддержка vendor-private HID / Bluetooth протоколов, которые Android не доставляет как стандартные `KeyEvent` / `MotionEvent`.
- Редизайн пользовательской системы remap'а hotkeys. Эта спека обеспечивает поддержку input, а не новую UI-семантику редактора биндов.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Использовать уже существующие foundation-слои, а не строить второй параллельный input stack.
2. На ТВ и Quest3 поведение должно совпадать: отдельной ветки для Quest controller не вводить.
3. Расширение не должно ломать уже реализованный focus-layer S0289 на Main / Browse / Player.

### 3.2 Жёсткие ограничения

- **In-scope Activity:** все in-house user-facing Activity модуля `app_v2` с интерактивным UI. Тактический план начинает с уже покрытых 15 экранов и добавляет catalog-audit для остальных in-house Activity. `ReceiveShareActivity` фиксируется как `n/a` (transparent, без UI). `DiagnosticXrActivity` (`src/vr/java/..`) тоже `n/a`: ввод идёт через OpenXR controllers (vendor-protocol), а стандартный Android KeyEvent/MotionEvent path сводится к long-press-back exit gesture - не имеет focus-chain, wheel или context-click semantics.
- **Input scope:** только стандартные Android-события keyboard, mouse, hard-buttons, Bluetooth/HID remotes, gamepad и joystick.
- **Flavor:** `standard`, `lite`, `photos`, `legacy`, `vr`, `noLegal`. Флейворные различия не создают отдельный input stack; они подключаются к тем же контрактам.
- **API level:** без специфики; работает на `minSdk 23` и выше.
- **Wear OS:** не затрагивается.
- **Производительность:** влияние на ресурсы должно оставаться пренебрежимым; допустимы только локальные router/helper расширения и lightweight dispatch logic.
- **Совместимость данных:** изменений persisted-state и schema не требуется ради самого multimodal contract.
- **Локализация:** новые пользовательские строки не планируются.
- **Доступность:** focused-state различим без цвета, а pointer hover не подменяет focus и не ломает TalkBack-порядок.

### 3.3 Owner inputs (Approval gate)

- **Scope delegation:** "any activity" трактуется как все interactive in-house Activity в `app_v2`; transparent/no-UI Activity = `n/a`; внешние auth-host Activity out-of-scope.
- **Mouse contract:** primary click использует native click, wheel прокручивает активный scrollable-контейнер, right-click открывает context/long-click semantics текущей цели, XButton1 = Back, XButton2 = Forward only when meaningful, hover не включает принудительный focus-ring.
- **Gamepad / joystick contract:** D-pad и left stick эквивалентны для non-player focus navigation; `A` = Select, `B` = Back, `X` = Context, `Y` = visible auxiliary action or no-op; `L1/R1` = page jump / tab switch там, где это логично; triggers и right stick сохраняют media semantics только на player-поверхностях.
- **Hard-buttons / Bluetooth buttons:** media keys, volume / mute, menu/search, headset hook и стандартные HID remote buttons входят в обязательный глобальный слой поддержки.
- **Dialog scope:** activity-only; dialogs не блокируют реализацию.
- **Validation level:** компиляция минимум `standardDebug` и `noLegalDebug`, ручная проверка keyboard + mouse + gamepad на Main / Browse / Player, smoke-проверка остальных activity groups.
- **Owner sign-off:** 2026-05-22 (delegated assumptions accepted by proceed signal).
- **Related tickets:** none.

---

## 4. Контекст текущей архитектуры

В проекте уже есть частично пригодный foundation, но он разделён по слоям и экранам. Общий base-layer умеет initial-focus и non-gamepad key routing для TV/keyboard сценариев. Отдельный shared router уже обрабатывает hard-buttons и media keys, но gamepad / joystick сознательно исключает. Для Main / Browse / Player существует специальная gamepad-routing логика, однако она не распространяется на остальные Activity. Shared mouse parser в кодовой базе уже существует, но фактически используется только в player-stack; на других экранах pointer / wheel support либо локален, либо отсутствует.

Из-за этого приложение живёт в смешанном состоянии. Focus-chain и initial-focus для многих экранов уже начали выравниваться в S0289, но input routing по-прежнему распределён между base-layer, screen-specific overrides и локальными helper'ами. Расширение спецификации должно не заменить эти слои, а собрать их в один multimodal contract: общий default path для простых экранов и специализированные адаптеры только для Main / Browse / Player / Standalone, где семантика сложнее.

---

## 5. Предлагаемый подход

Стратегически S0289 расширяется от "focus-only" к "multimodal input as a first-class capability". Фокус остаётся базой, но поверх него появляется единый activity-level contract для keyboard, mouse, hard-buttons, Bluetooth/HID remotes, gamepad и joystick.

### 5.1 Основные столпы / модули

1. **Унифицированный multimodal contract.** Общий набор правил: что значит Select / Back / Context / Scroll / PageJump / TabSwitch / AuxiliaryAction на non-player и player поверхностях.
2. **Base activity multimodal hooks.** Общий default path для Activity, которым не нужен bespoke input tree: mouse wheel, extra mouse buttons, non-touch initial focus, default back/context handling, safe no-op policy.
3. **Complex surface adapters.** Main, Browse, Player и Standalone сохраняют свои screen-specific semantics, но используют общий contract как фундамент, а не отдельную самодельную систему.
4. **Simple screen parity.** Формы и списочные экраны получают минимум screen-specific кода: в основном surface declaration, default scroll target и при необходимости one-line overrides.
5. **Catalog-driven audit.** Тактическая спека обязана пройти по activity catalog и подтвердить для каждой in-house Activity: supported, delegated to default foundation или `n/a`.

### 5.2 Потоки данных и событий

- **Keyboard / D-pad / hard-buttons / Bluetooth buttons** → shared key router → semantic action или Android focus traversal → screen-specific override только там, где у экрана есть более сильная локальная семантика.
- **Mouse** → shared mouse parser → scroll / click / context / back-forward callbacks → default activity hooks или screen-specific mouse target.
- **Gamepad / joystick** → gamepad router / fallback navigation layer → non-player focus traversal либо media-specific player semantics.
- **State-driven visibility** → active action set пересчитывается без ручного перебора скрытых элементов, чтобы input contract не ломался при динамическом UI.

### 5.3 Точки расширяемости

- **Surface profile per screen.** Каждый экран объявляет, к какому multimodal profile он относится: main-like, browse-like, player-like, form-like, list-like, `n/a`.
- **Default scroll target.** Простые экраны могут объявить один target для wheel / page-jump и не писать свой pointer router.
- **Flavor extension point.** Если флейвор добавляет собственные controls, они подключаются к общему multimodal contract через тот же surface profile, а не через новый input stack.
- **Future dialog support.** Dialogs остаются следующей волной и могут использовать тот же contract позже.

---

## 6. Открытые вопросы / Research items

1. **Что значит "any activity"?**
   - **Решение:** все interactive in-house Activity в `app_v2`; transparent/no-UI = `n/a`; external auth hosts out-of-scope.
   - **Статус:** Resolved (2026-05-22).

2. **Какой mouse contract считается обязательным?**
   - **Решение:** primary click, wheel, right-click/context, XButton1 Back, XButton2 Forward when meaningful, hover без принудительного focus-ring.
   - **Статус:** Resolved (2026-05-22).

3. **Как трактовать gamepad / joystick вне player?**
   - **Решение:** D-pad + left stick = focus navigation; `A/B/X/Y` = select/back/context/auxiliary-or-no-op; `L1/R1` = page/tab navigation where relevant; triggers/right stick reserved to player/media surfaces.
   - **Статус:** Resolved (2026-05-22).

4. **Какие hard-buttons / Bluetooth buttons входят в глобальную поддержку?**
   - **Решение:** media keys, volume/mute, menu/search, headset hook и стандартные HID remote buttons, доставляемые Android как standard events.
   - **Статус:** Resolved (2026-05-22).

5. **Нужно ли включать dialogs в этот ticket?**
   - **Решение:** нет, текущая волна activity-only.
   - **Статус:** Resolved (2026-05-22).

---

## 7. Риски

- **Риск:** mouse right-click и long-click semantics могут конфликтовать с уже существующими touch-only паттернами.
  **Митигация:** default contract = context/long-click only where target already supports it; иначе безопасный fallback.
- **Риск:** gamepad face buttons начнут конфликтовать с уже существующими player/browser hotkeys.
  **Митигация:** complex surfaces остаются screen-specific adapters поверх общего contract; foundation не переопределяет их media semantics.
- **Риск:** activity catalog выявит дополнительные interactive screens вне исходных 15.
  **Митигация:** тактический Phase 09 содержит обязательный audit pass и явные `supported/default/n/a` отметки.
- **Риск:** hover / pointer presence на touch-устройствах приведёт к лишнему focus-ring.
  **Митигация:** hover и pointer не считаются поводом для initial-focus; focus остаётся user-driven.
- **Риск:** аналоговые оси joystick будут flood'ить navigation.
  **Митигация:** использовать debounce / deadzone policy и не распространять analog semantics на формы без явной необходимости.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это не новая фича, а приведение существующих экранов к обязательной multimodal input поддержке.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Координация фокуса остаётся явной, а не платформенно-автоматической.**

- **Решение:** сохранять explicit focus chains и initial-focus contract вместо опоры только на platform auto-focus.
- **Почему:** именно этот слой уже дал предсказуемость на Main / Browse / Player и нужен как база для multimodal input.

**ADR-2: Initial-focus только при отсутствии touch-input.**

- **Решение:** initial-focus запрашивается только в non-touch сценариях.
- **Почему:** это убирает визуальные регрессы на телефонах и не мешает mouse / hover сценарию.

**ADR-3: Multimodal input расширяется поверх существующих shared routers, а не через новый parallel stack.**

- **Решение:** использовать существующие shared key / mouse / gamepad слои как foundation и дополнять их common hooks для simple activities.
- **Альтернативы:** отдельный input framework per screen; полная Leanback-style миграция.
- **Почему:** минимальный риск регресса, reuse уже написанного кода и меньший объём флейворных расхождений.

**ADR-4: Pointer hover не равен focus.**

- **Решение:** hover events не создают forced focus-ring и не подменяют keyboard/gamepad focus model.
- **Почему:** pointer presence сама по себе не должна менять visual hierarchy и accessibility contract.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. На любом in-scope экране пользователь может выполнить базовое действие без touch: попасть в управляющий элемент, активировать его и вернуться назад хотя бы одним из поддерживаемых non-touch input channels.
2. **Keyboard / D-pad / Bluetooth keyboard / HID remote:** navigation, select и back semantics работают предсказуемо на всех in-scope Activity.
3. **Mouse:** wheel прокручивает активный scrollable-контейнер на list/document/form surfaces, right-click открывает context/long-click semantics там, где они есть, XButton1 работает как Back.
4. **Gamepad / joystick:** D-pad и left stick перемещают focus на non-player поверхностях; `A/B/X/Y` соблюдают зафиксированный contract; player-поверхности сохраняют media-specific analog behavior.
5. **MainActivity:** keyboard, mouse и gamepad не ломают уже реализованную chain между top bar, tabs и resource list.
6. **BrowseActivity:** keyboard, mouse и gamepad одинаково обслуживают tree/list navigation и верхнюю панель.
7. **PlayerActivity / StandalonePlayerActivity:** HUD остаётся focusable, mouse/gamepad/hard-buttons routed consistently, а выход из плеера не теряет возвратный focus contract.
8. **Form/list screens:** Settings, AddResource, ResourceEditor, AuthSessions, KeybindingRemap, Welcome, Duplicates, cloud pickers и widget config поддерживают общий multimodal contract без зависимости от touch.
9. Focused-state визуально различим без цвета и поведение воспроизводится одинаково в portrait и landscape.
10. На touch-first устройствах без действия пользователя не появляется навязанный focus-ring.
11. Проверка воспроизводится как минимум в `standard` и `noLegal`; на других флейворах отсутствуют regressions в реально доступных экранах.

---

## 12. Ссылка на тактическую спецификацию

Исполнение продолжается в `PLAN/S0289_tv-keyboard-dpad-navigation/INDEX.md`. После расширения strategic scope тактический план дополняется multimodal foundation и parity phases.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: completeness, consistency, verifiability)
  - Applied: S0289 broadened from TV keyboard / D-pad focus support to full multimodal input parity across in-house activities.
- **2026-06-03** - by `/spec-test-device` (`Claude Opus 4.8`, device: emulator-5554 Android TV 1920x1080)
  - Scenario: temp/S0289_mobile_test_scenario_20260603_0224.md · PASS/FAIL/INCONCLUSIVE 1/2/1 · welcome probe observed.
  - Welcome D-pad navigation FAILED: edge-aware FocusFinder carousel does not work with ViewPager2; owner symptom persists. Rework required before `Verified`.

- **2026-06-18** - by `/spec-sweep` (emulator-5554, adb keyevent + uiautomator focus, no touch)
  - D-pad navigation PASS on MainActivity / BrowseActivity / PlayerActivity / SettingsActivity; FAIL on WelcomeActivity (carousel controls unreachable). Verdict Broken; 16 debug probes removed on the BlockNeedUserTest -> Broken transition.

- **2026-06-18** - by `/spec-all` F5 (emulator-5554, fix + on-device re-test)
  - Fixed the WelcomeActivity D-pad focus bug and re-verified on device (portrait + landscape). Verdict Verified.

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 1

### Manual / on-device

Device test 2026-06-18, emulator-5554 (Android 13, SDK 33), standard debug, driven by adb keyevent + uiautomator focus dumps (touch-wedge-immune). D-pad navigation PASS on every in-scope screen:

- [x] MainActivity / BrowseActivity / PlayerActivity / SettingsActivity / crash dialog - reachable + activatable by D-pad (unchanged from prior pass).
- [x] WelcomeActivity (§11.8) - **fixed this run.** Language buttons (En/Ru/Uk), theme buttons (Auto/Light/Dark), and the bottom bar (btnNext/btnPrevious) are all reachable + activatable by D-pad alone (no touch, no TAB); LEFT/RIGHT move within a row and flip the page only at the edge; feature tiles are intentionally non-focusable decorative cards (DOWN skips to the bar). Identical in portrait + landscape.

Root cause: the first D-pad key parked focus on the ViewPager2 internal RecyclerView (a descendant of neither the page nor the bottom-bar scope), so `handleSliderHorizontal` resolved a null scope and fell through to `flipPage`, never entering the in-page pickers. Fix (`WelcomeActivity.kt` only): detect focus on the pager container (or null) and enter the current page on a real focusable control before the scope/flip logic; the vertical handler reuses the same helper (`firstPageFocusable` / `enterPageFromContainer` / `isOnPagerContainer`). No layout change. `compileStandardDebugKotlin` GREEN.

Note (framework reality, not a defect): on a touch-mode phone the very first D-pad key is absorbed to exit touch mode (`requestFocus` is a no-op in touch mode), so key 2+ works; on a real non-touch TV/Quest `getInitialFocusView()` (btnNext) applies at setup and key 1 is immediately actionable. 16 device probes already removed at the BlockNeedUserTest -> Broken transition; 0 S0289 debug tags remain. (§8 FEATURES EXEMPT - "Без изменений в docs/FEATURES".)

**Evidence:** temp/S0289_fix/ (per-keystroke focus dumps + portrait/landscape screenshots).
