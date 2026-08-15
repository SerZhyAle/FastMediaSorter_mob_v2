# Стратегическая спецификация: S0622 - Действие жеста «Открыть программу»

**Ticket:** S0622
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-22
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-22
**Tactical spec:** свёрнута в эту спеку (малый объём - 6 файлов, см. §5.4)

> **Scope:** STRATEGIC + краткие тактические заметки. Цель - добавить один новый вариант действия в уже существующий механизм жестов с левого края.

---

## 1. Проблема

Механизм жестов с левого края уже существует: полоска-оверлей на левом крае распознаёт свайп вправо и раскладывает его на три направления (вверх/вправо/вниз). Каждому направлению назначается действие из фиксированного списка, и сейчас все действия завязаны на захват экрана (тихий скриншот, открыть в плеере, OCR-перевод, отправить, поделиться). Нет действия, которое просто открывает само приложение - то есть жестом нельзя быстро вернуться в программу из любого места системы.

---

## 2. Цели

1. Пользователь может назначить любому направлению жеста с левого края действие «Открыть программу».
2. Это действие выводит главное окно приложения на передний план с сохранением текущего состояния, если оно уже запущено, либо запускает приложение с нуля.
3. Действие не делает скриншот и не требует прохождения через диалог согласия MediaProjection.

**Non-goals:**

- Диалог «Открыть панель программ» с большими кнопками-картинками - вынесен в S0623.
- Новый, независимый от захвата экрана слой жестов - владелец выбрал переиспользовать существующий механизм (§3.3).
- Добавление четвёртого направления жеста (свайп влево от левого края физически не имеет смысла).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. «Открыть программу» - это просто новый пункт в существующем списке действий жеста, назначаемый на направление наравне с остальными.

### 3.2 Жёсткие ограничения

- **Flavor:** наследует scope оверлея жестов = standard + noLegal (оверлей вынесен на standard в S0621). Отдельной flavor-логики не добавляется.
- **API level:** без новой API-специфики. Запуск приложения через launch-intent работает на всех поддерживаемых уровнях; оба пути захвата (MediaProjection API 26+, accessibility API 30+) уже существуют.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично - действие лишь стартует activity вместо захвата.
- **Совместимость данных:** новый enum-вариант читается толерантным `fromName`; миграция настроек не нужна.
- **Локализация:** EN/RU/UK обязательно.
- **Доступность:** действие появляется как пункт в существующем диалоге-пикере; отдельных visual-элементов нет.

### 3.3 Owner inputs (Approval gate)

- **Подход (решено 2026-06-23):** добавить действие в существующий per-direction пикер жестов-скриншотов, а не строить отдельный слой жестов. Дословно: «у нас есть список доступных действий жеста - это новый вариант действия который можно назначить на жест».
- **Следствие:** действие работает только когда включён оверлей жестов (нужно разрешение overlay/accessibility, тумблер `gestureOverlayEnabled`) - то же условие, что и для всех остальных действий жеста.
- **Related tickets:** S0623 (действие «Открыть панель программ» - использует тот же механизм жестов с левого края).

---

## 4. Контекст текущей архитектуры

Жест распознаётся менеджером оверлея в source-set `screenCapture` (общий для standard+noLegal) и приходит как направление в диспетчер действий в `src/main`. Диспетчер решал только одну дихотомию: действие либо отключено (пропустить захват), либо требует захвата (сделать скриншот, затем выполнить пост-действие). «Открыть программу» - третий класс: действие без захвата, но с побочным эффектом. Поэтому появляется единая pre-capture-точка, которая обрабатывает оба «бес-захватных» действия (отключено и открыть-программу).

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- Доменный enum действий жеста получает новый вариант `OPEN_APP`.
- Диспетчер действий получает единый pre-capture-обработчик: возвращает «обработано, дальше не идти» для отключённого направления и для «открыть программу», иначе - «идти на захват».
- Запуск приложения - через системный launch-intent пакета (вынос существующей задачи на передний план с сохранением состояния, либо холодный старт).

### 5.2 Потоки данных и событий

- Жест (оверлей) -> диспетчер.actionFor(направление) -> диспетчер.handlePreCaptureAction(action).
- Если pre-capture вернул true: для `DO_NOT_USE` - тихий no-op, для `OPEN_APP` - запуск приложения; захват/согласие пропускаются.
- Если false: прежний путь захвата (MediaProjection-согласие в standard/noLegal-fallback или silent accessibility-захват в noLegal API 30+).

### 5.3 Точки расширяемости

- Pre-capture-обработчик - естественное место для будущих бес-захватных действий (например, действие S0623 «Открыть панель программ» добавится туда же или рядом).

### 5.4 Тактические заметки (фактическая реализация)

- `domain/model/ScreenshotGestureAction.kt` - добавлен вариант `OPEN_APP` (перед `DO_NOT_USE`, чтобы в пикере он шёл предпоследним).
- `core/screencapture/ScreenshotGestureActionDispatcher.kt` - добавлены `handlePreCaptureAction(context, action)` и приватный `launchApp(context)` (через `getLaunchIntentForPackage`); `runPostSave` дополнен веткой `OPEN_APP` для исчерпывающего `when`.
- `OverlayHostService.kt` (source-set `screenCapture`) - перехват заменён на `handlePreCaptureAction`; убран неиспользуемый импорт enum.
- `ScreenshotAccessibilityService.kt` (source-set `noLegal`) - перехват в `captureNow` заменён на `handlePreCaptureAction`.
- `ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` - label-маппинг для `OPEN_APP`.
- strings EN/RU/UK: `screenshot_gesture_action_open_app`.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Имя enum/фичи `Screenshot*` становится неточным после не-скриншотного действия | Низкая | Когнитивный диссонанс при чтении кода | Отложенный рефакторинг-переименование вне объёма S0622; зафиксировано как кандидат |
| launch-intent отсутствует на экзотическом ROM | Низкая | Жест ничего не делает | Защищён `runCatching` + лог-предупреждение, без краша |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новый вариант действия жеста: жестом с левого края можно мгновенно открыть приложение (или вернуть его на передний план с сохранением состояния).

---

## 9. Архитектурные решения (ADR)

ADR-1: бес-захватные действия централизованы в одном pre-capture-обработчике диспетчера, чтобы обе точки запуска жеста (MediaProjection-оверлей и accessibility-сервис) обрабатывали их единообразно, а не дублировали проверки.

---

## 10. Связи с другими спеками

- S0621 - вынос группы жестов на standard flavor (предпосылка доступности действия на публичной сборке).
- S0623 - «Открыть панель программ», тот же механизм жестов.

---

## 11. Критерии готовности (strategic-level)

1. В пикере действий каждого из трёх направлений жеста присутствует пункт «Открыть программу» (EN/RU/UK).
2. Назначив его на направление и выполнив жест из другого приложения, пользователь видит, что FastMediaSorter выходит на передний план.
3. Если приложение уже было открыто, восстанавливается его прежнее состояние, а не стартует новый чистый экран.
4. При этом скриншот не делается и диалог согласия на захват не показывается.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спека свёрнута в §5.4 ввиду малого объёма изменений.

---

## Last Audit

### Manual device test - 2026-06-23 (INCONCLUSIVE - config verified, edge-swipe not triggerable on AVD)

**Device:** emulator-5554, Android SDK 37, x86_64, landscape 2560x1600.
**Build:** `2.60.6231.642-NoLegal-DEBUG` (`com.sza.fastmediasorter.debug`). Accessibility service kept OFF throughout, so the overlay/launch-intent path is exercised.

**Path note:** "Open the app" bypasses capture entirely - it is handled by the `src/main` dispatcher pre-capture handler (`ScreenshotGestureActionDispatcher.handlePreCaptureAction` -> `launchApp` via `getLaunchIntentForPackage`) before any capture backend runs. So it behaves identically regardless of capture backend (standard MediaProjection overlay, noLegal silent accessibility). This build verifies the overlay/launch-intent variant.

**Sub-step results:**
- Overlay re-enabled + edge strip back: PASS - re-toggled "Gesture overlay" on (it was left off ending S0621); overlay grant still `allow` (no re-grant prompt). `OverlayHostService` restarted as a foreground service (`isForeground=true`, channel `screen_capture_overlay_host`); the strip window is on screen: `Window{screen_gesture_overlay_strip} mAttrs={(0,197)(36x967) gr=TOP START} ty=APPLICATION_OVERLAY ... Surface: shown=true isOnScreen=true`.
- "Open the app" present in picker + assignable: PASS - opened the Right-direction action picker; the list is, in order: Silent screenshot, Open in player, Open for editing, Send to OCR translation, Send to recipients, Share, **Open the app**, Do not use. "Open the app" is second-to-last, just before "Do not use" (matches §5.4). Assigned it to Right; the row value then read "Open the app" (RU string `screenshot_gesture_action_open_app` = "Открыть программу").
- App left in recognizable state + backgrounded: PASS - app was on Settings > Management (the gesture group), then sent to background via `KEYCODE_HOME`; `topResumedActivity` became `com.google.android.apps.nexuslauncher/.NexusLauncherActivity` (launcher foreground, FastMediaSorter alive in background).
- Left-edge swipe -> foreground + restore prior state + no screenshot + no consent: INCONCLUSIVE for the gesture trigger; negative side-effects confirmed clean. Four edge-swipe attempts in the Right direction (adb `input swipe` from x=18/20/25 horizontally rightwards, plus a mobile-mcp right swipe) all failed to reach the dispatcher: the system steals the gesture every time - `InputDispatcher: Channel [Gesture Monitor] edge-swipe is stealing input gesture ... from [screen_gesture_overlay_strip]`. The 36px-wide strip sits inside the system back-edge zone, so on this AVD the system gesture monitor wins. This is an AVD gesture-recognition limitation, not an app defect (same class seen in S0621, where only a diagonal down-right swipe eventually got through). Because the gesture never reached the dispatcher: `topResumedActivity` stayed on the launcher (no foreground bring-up to observe), AND - the important positive evidence that nothing misfired - NO new screenshot file was created (Screenshots max `_id` stayed 109, unchanged from the S0621 baseline of ids 104/107/109) and NO MediaProjection consent / cast dialog appeared (no `MediaProjectionPermissionActivity` / `logPermissionRequestDisplayed` in logcat).

**S0622 probe:**
- The code contains exactly one S0622 tag: `Timber.d("S0622: left-edge gesture OPEN_APP -> bring app to foreground")` at `core/screencapture/ScreenshotGestureActionDispatcher.kt:47`. It did NOT fire this run - the edge-swipe never reached the dispatcher (system stole the gesture), so the OPEN_APP branch was not entered. Tag presence is consistent with BlockNeedUserTest; it remains untriggered pending a gesture that actually reaches the dispatcher (real device, or Maestro/instrumented injection past the system edge monitor).

**Screenshots (under `temp/S0622_sweep/`):**
- `01_overlay_reenabled.png` - gesture group with overlay toggle re-enabled.
- `02_right_action_picker.png` - action picker showing "Open the app" second-to-last before "Do not use".
- `03_open_app_assigned.png` - Right gesture action now reads "Open the app".
- `04_backgrounded_launcher.png` - app backgrounded, launcher in foreground (strip active).
- `05_after_swipe_attempts.png` - state after the (system-stolen) swipe attempts; still on launcher, no capture artifacts.

**Verdict:** INCONCLUSIVE. Config criteria 1 (picker contains "Open the app", EN/RU) and the assignment flow are PASS; the behavioral criteria 2-4 (foreground bring-up + state restore, with no screenshot/consent) could not be exercised because the AVD system gesture monitor steals the left-edge swipe before the overlay strip recognizes it. No misfire occurred (no screenshot, no consent). Re-run the gesture step on a physical device or via an injection method that bypasses the system back-edge monitor.
