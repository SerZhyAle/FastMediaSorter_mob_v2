# Спецификация (compact bugfix): S1096 - В режиме редакции стола ярлыки не должны срабатывать по нажатию

**Ticket:** S1096
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-18
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-18

**Захвачено во время:**  тестирования (контекст «по тестированию»); связанный тикет - S0404

**Текст:**

по тестированию
S0404
лаунчер

во время редакции рабочего стола ярлыки не должны "нажиматься" работать - инаяе я не могу подвинуть часы например - запускается будитльник

---

## 1. Проблема / симптом

<Что наблюдается, где (flavor/устройство/экран), эвиденс - лог-строки, stack trace, repro. Без имён классов на этапе захвата.>

Симптом (из захвата): в режиме редакции рабочего стола лаунчера нажатие на ярлык всё ещё активирует его действие (например по нажатию на виджет часов запускается будильник), из-за чего нельзя перетащить/переместить элемент. Ожидание: во время редакции клики по ярлыкам/виджетам подавляются, остаётся только drag/reposition и служебные действия (удаление и т.п.).

---

## 2. Корневая причина

В режиме редакции содержимое ячейки продолжает получать касания:

- `LauncherCellViewBinder.decorateForEdit` навешивает на корень ячейки только remove-badge и long-press для drag, но не подавляет исходные обработчики касаний внутри ячейки.
- Ярлык: клик-листенер (`onCellClick`) навешивается в `bindShortcut` безусловно. На уровне ViewModel `onCellTapped` уже отсеивает edit-mode/gadget, поэтому тап по ярлыку в edit-режиме и сейчас ничего не запускает - защита есть, но косвенная.
- Гаджет: его собственный view держит внутренний `setOnClickListener` (например `ClockGadgetView` → `openSystemClock`, запуск будильника). Этот тап минует `onCellTapped` целиком и срабатывает всегда, включая режим редакции. Тот же внутренний view может перехватывать и long-press, из-за чего гаджет нельзя начать тащить.

То есть корень - отсутствие единой точки перехвата касаний ячейки во время редакции; защита ViewModel не покрывает интерактивные гаджеты (ADR-5, свои view с собственными обработчиками).

---

## 3. Исправление

Единый перехват касаний на уровне ячейки, только пока активна редакция, в `LauncherCellViewBinder.decorateForEdit`:

- Поверх содержимого ячейки (до remove-badge, чтобы badge остался сверху и кликабельным) добавить прозрачный edit-scrim на весь размер ячейки (`MATCH_PARENT`).
- Scrim `clickable`+`longClickable`: клик проглатывается (в режиме редакции ячейка ничего не запускает), long-press стартует drag через `onCellDragStart`.
- Существующий long-press на корне ячейки убрать - drag теперь принадлежит scrim (иначе внутренний view гаджета мог перехватить его первым).
- Scrim не важен для accessibility (`IMPORTANT_FOR_ACCESSIBILITY_NO`): TalkBack продолжает читать саму ячейку и remove-badge; drag через D-pad вне объёма итерации-1.

Работает единообразно и для ярлыков, и для гаджетов: перехватывает и внутренний клик гаджета (часы→будильник), и делает drag надёжным.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (родительский эпик; дефект в поставленном поведении итерации-1)

---

## 4. Проверка

- Компиляция: `.\a.ps1 fk` (standard) - BUILD SUCCESSFUL.
- On-device (BlockNeedUserTest): войти в режим редакции стола → тап по гаджету часов НЕ открывает будильник; тап по ярлыку НЕ запускает его действие; гаджет и ярлык перетаскиваются long-press'ом; remove-badge по-прежнему удаляет ячейку; после выхода из редакции тапы снова работают штатно.

---

## Last Audit

- **2026-07-24** - `/spec-test-device` (`claude-opus-4-8[1m]`, device emulator-5554, Android 15 / SDK 35, standard-debug v2.60.7220.314). Result: PASS 6/6, 0 log errors. Evidence: `temp/S1096/mobile_test_scenario_20260724_0016.md` + `temp/S1096/screens/`.

### Manual / on-device

- [x] Enter edit mode fires the changed-flow probe - verified on-device 2026-07-24 (expected: `D/.. S1096: edit mode on ..`; actual: logged at 00:20:52.968).
- [x] Tap on the clock gadget in edit mode does NOT open the alarm/clock app - verified on-device 2026-07-24 (expected: foreground stays LauncherHomeActivity, no deskclock; actual: topResumedActivity = LauncherHomeActivity, no clock/alarm activity started).
- [x] Tap on a shortcut cell in edit mode does NOT launch its action - verified on-device 2026-07-24 (expected: Android-settings shortcut does not open Settings; actual: foreground stayed LauncherHomeActivity, com.android.settings not started).
- [x] A cell can be moved via long-press drag - verified on-device 2026-07-24 (expected: dragged cell relocates; actual: "Voice recording" moved to the drop slot, old slot freed).
- [x] Remove-badge deletes the cell - verified on-device 2026-07-24 (expected: cell disappears; actual: own-app cell removed, its square became an empty slot).
- [x] After leaving edit mode taps work again - verified on-device 2026-07-24 (expected: shortcut launches; actual: Android-settings tap opened com.android.settings/.homepage.SettingsHomepageActivity).

Note: benign `W/InputManager-JNI .. Input channel object .. was disposed without first being removed` fires once at edit-mode entry when the binder rebuilds every cell view (ADR-9 full re-render). Not a crash, not app-level - no follow-up.
