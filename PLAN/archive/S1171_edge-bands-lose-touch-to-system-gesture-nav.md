# S1171 - Полосы краевых жестов теряют касание под системной жестовой навигацией

**Status:** Archived
**Priority:** 55
**Created:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)

## 0. Исходный материал (verbatim)

Обнаружено при device-тесте S1162 на emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35), 2026-07-24.

Сообщение исполнителя:

> Environment, out of S1162 scope (pre-existing since S0847): with the AVD's default **gesture navigation**
> the system back-gesture monitor steals the touch from the band ~80 ms after DOWN
> (`InputDispatcher: Channel [Gesture Monitor] edge-swipe (server) is stealing input gesture … from
> screen_gesture_overlay_left_top`), so no edge gesture ever completes and the hint vanishes instantly.
> All drag checks were run on 3-button navigation; nav mode was restored to gestural afterwards.

Строка из logcat, по которой это диагностируется:

```
InputDispatcher: Channel [Gesture Monitor] edge-swipe (server) is stealing input gesture from screen_gesture_overlay_left_top
```

Артефакты прогона: `temp/S1162/`.

## 1. Симптом

На устройстве с включённой системной жестовой навигацией (значение по умолчанию на Android 10+)
левая и правая полосы краевых жестов не срабатывают: система забирает касание себе под жест «назад»
примерно через 80 мс после нажатия. Пользователь видит, что подсказка направления мигает и пропадает,
а действие не выполняется.

На трёхкнопочной навигации те же полосы работают штатно - именно так и прогонялись проверки S1162.

## 2. Почему это отдельный тикет

- Не относится к контракту S1162: тот отвечает за подсказку направления, а не за доставку касания.
- Затрагивает всю функцию краевых жестов (S0847 и далее), а не одну её деталь.
- Требует собственного исследования: у Android есть штатный механизм отказа от системного жеста -
  `Window.setSystemGestureExclusionRects` / `WindowInsets.getSystemGestureInsets` - и надо выяснить,
  применим ли он к overlay-окну типа `TYPE_APPLICATION_OVERLAY`, которое живёт вне обычного окна
  приложения, а также что делать на `TYPE_ACCESSIBILITY_OVERLAY` в noLegal.
- Скорее всего требует проверки на реальном устройстве, а не только на AVD.

## 3. Исправление

Полосы - обычные `View` в окнах WindowManager (`ScreenGestureOverlayManager.addBand`, `src/screenCapture`), каждая размером `frame.width x frame.height`. Штатный механизм отказа от системного жеста - `View.setSystemGestureExclusionRects` (API 29+): сообщаем системе, что прямоугольник полосы исключён из системных жестов, и она перестаёт красть касание.

**3.1** Добавить `applyGestureExclusion(view, frame)`: под `SDK_INT >= Q` ставит `view.systemGestureExclusionRects = listOf(Rect(0, 0, frame.width, frame.height))` (весь прямоугольник полосы в её локальных координатах).

**3.2** Звать его в `addBand` после `addView` и в `relayout` после `updateViewLayout` (при повороте размер полосы меняется - исключение надо переставить).

Механизм - тот же `View`-атрибут, окно оверлея им владеет, поэтому он действует и на `TYPE_APPLICATION_OVERLAY` (standard), и на `TYPE_ACCESSIBILITY_OVERLAY` (noLegal) - код общий в `src/screenCapture`.

### 3.1 Ограничение (системный лимит)

Android честит зону исключения на сторону примерно 200dp суммарно; полоса высотой ~30% экрана (`BAND_HEIGHT`) выше этого. Значит система защитит только нижнюю часть высокой полосы, а не всю. Это лимит платформы, а не дефект: полный обход системного «назад» приложению не даётся намеренно. Ставим исключение на весь прямоугольник - система сама возьмёт из него столько, сколько разрешает.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1162, S0847

## 4. Проверка

- `:app_v2:compileStandardDebugKotlin` - BUILD SUCCESSFUL.
- На устройстве с **жестовой навигацией** (значение по умолчанию Android 10+): включить полосы (`fms.edgeGestureOverlay=on`), провести внутрь от левой/правой полосы. Ожидаемо: жест доходит до `handleTouch`, подсказка не мигает и не пропадает, действие выполняется; в logcat нет строки `Channel [Gesture Monitor] edge-swipe (server) is stealing input gesture from screen_gesture_overlay_*`. Полоса воспроизводит steal на emulator-5554 в режиме жестов (эвиденс S1162), поэтому AVD годится для проверки; полная уверенность - на реальном устройстве.
- 3-кнопочная навигация: полосы по-прежнему работают штатно (регресс не введён).

## Last Audit

### Manual / on-device

- [x] Жестовая навигация, левая полоса: жест внутрь доходит до `handleTouch`, подсказка держится до
  собственного порога приложения, действие выполняется - verified on-device 2026-07-26
- [x] Жестовая навигация, правая полоса: жест внутрь доходит до `handleTouch`, действие выполняется -
  verified on-device 2026-07-26
- [x] В logcat нет строки `stealing input gesture from screen_gesture_overlay_*` - verified on-device 2026-07-26
- [x] 3-кнопочная навигация: полосы работают штатно, регресса нет - verified on-device 2026-07-26

Прогон: `emulator-5554` (Android 15 / SDK 35), standard debug `2.60.7220.314-DEBUG`, режим `navigation_mode=2`
(жесты). Обе полосы `47x666` px. Порядок доказательства:

- Пре-фикс baseline того же AVD и той же полосы: `temp/S1162/logcat_swipe_right.txt` - кража касания
  через 81 мс после DOWN, подсказка снята через 86 мс, жест не доходил.
- Сегодня: 0 строк кражи на весь лог прогона (27948 строк). Подсказка живёт ~380 мс и снимается
  собственным порогом в 120 px, после чего стартует `ScreenCaptureConsentActivity` - жест завершился.
- Контроль живости системного монитора: свайп от левого края вне полосы (y=1500) даёт
  `W/InputDispatcher: Attempted to pilfer points ..`, то есть монитор краевых жестов в этой сессии активен.
- Зона исключения работает целиком в нижней части полосы: при старте с y=900 система даже не начинает
  `startBackNavigation`. При старте с y=400 (выше платформенного лимита ~200dp) системный «назад»
  срабатывает параллельно, но касание у полосы больше не отбирает - ровно ограничение из 3.1.

Сценарий и артефакты: `temp/S1171/mobile_test_scenario_20260726_1910.md`.

Осталось за кадром: подтверждение на реальном устройстве (собственная оговорка раздела 4).

## Revision History

- **2026-07-26** - by `/spec-test-device` (`claude-opus-5[1m]`, device: emulator-5554, Android 15)
  - Сценарий: `temp/S1171/mobile_test_scenario_20260726_1910.md` · PASS/FAIL/SKIPPED 4/0/1 · ошибок в логе: 0
