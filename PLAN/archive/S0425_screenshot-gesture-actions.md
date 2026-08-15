# Стратегическая спецификация: S0425 - Назначаемые действия направленных жестов скриншота

<!-- auto-approved by /spec-all - 2026-06-15 -->
**Ticket:** S0425
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-14
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-14
**Tactical spec:** `PLAN/S0425_screenshot-gesture-actions/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-14, объединено 2026-06-15 (слияние S0424 + S0425; S0424 → архив).

**Текст (консолидированная формулировка, 2026-06-15):**

Есть три жеста - вниз, вправо, вверх. И несколько вариантов поведения:
- «молчаливый скриншот» (реализовано)
- открыть результат в плеере
- открыть результат для редакции (draw)
- отправить на OCR-перевод
- отправить SHARE сразу - например бросить в Telegram (ещё не описывал)
- «не использовать» - без поведения

Пользователь может задать для каждого из трёх жестов любое из этих поведений.

**Текст (исходный S0424, верх → draw):**

Дополнтельнная опция для механизма создания скриншотов - "Жестом вверх открывать скриншот для редактирования сразу после создания" - это жест под 45 градусов верх - открываетс яне посто плеер, но сразу DRAW

**Текст (исходный S0425, вправо → плеер / OCR):**

Дополнтельнная опция для механизма создания скриншотов - "Жестом вправо открывать скриншот в плеере сразу после создания" - это жест под 90 градусов вправо - открываетс в плеер (там можно
у этой опции есть другой варинт - скриншот отправляться для OCR-перевода, по пути который сейчас есть для фотографий

**Вложения:**

Вложений нет.

---

## 1. Проблема

Механизм скриншота по краевому жесту сейчас выполняет единственное фиксированное действие - «молчаливый» снимок (сохранить и ничего не показывать). Пользователь не может задать другое поведение после захвата и не может задействовать разные направления жеста под разные сценарии. В результате частые после-съёмочные сценарии (открыть, отредактировать, перевести, поделиться) требуют ручных шагов вне приложения.

Область: механизм краевого жеста-скриншота (семейство S0405 → S0418), настройки приложения.

---

## 2. Цели

1. Пользователь назначает поведение независимо для каждого из трёх направленных жестов: вниз, вправо, вверх.
2. Доступен общий набор после-съёмочных действий, выбираемых для любого жеста.
3. Любой жест можно выключить значением «не использовать».
4. Текущее поведение (молчаливый скриншот) остаётся доступным как один из вариантов и поведением по умолчанию.

**Non-goals:**

- Произвольные пользовательские (скриптуемые) действия - только фиксированный набор.
- Новые направления/число касаний сверх трёх перечисленных жестов.
- Изменение самого механизма захвата экрана (это S0405/S0418).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Набор поведений:
   - молчаливый скриншот (реализовано, дефолт);
   - открыть результат в плеере;
   - открыть результат для редактирования (DRAW);
   - отправить на OCR-перевод по существующему для фотографий пути;
   - отправить через системный SHARE сразу (например, в Telegram) - ещё не проектировалось;
   - «не использовать» (жест без поведения).
2. Жесты и их «естественные» углы из исходных захватов: вправо ≈ 90°, вверх ≈ 45°; вниз - третье направление.
3. OCR-перевод переиспользует уже существующий маршрут перевода фотографий, а не вводит отдельный.
4. DRAW открывает скриншот сразу в режиме редактирования, минуя обычный просмотр в плеере.

### 3.2 Жёсткие ограничения

- **Flavor:** механизм краевого жеста-скриншота примонтирован только во флавор noLegal (Hilt-мультибинд `Set<ScreenGestureOverlayController>` пуст в остальных). Настройка действий жестов доступна там же. Перенос в standard - отдельный S0418 (`Archived`); несмонтированный задел `screenCapturePlay` вынесен в S0450.
- **API level:** noLegal API 30+ - тихий захват через `AccessibilityService.takeScreenshot()`; API 26-29 - через MediaProjection (диалог согласия). Оба пути заканчиваются на `SaveScreenshotUseCase`; новое действие подключается в обеих ветках.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично.
- **Совместимость данных:** маппинг «жест → действие» хранится в существующем `ScreenshotSettingsStore` (DataStore Preferences) тремя строковыми ключами (значение = имя enum действия). Room-миграции нет. Мёртвый ключ `screenshot_gesture_down_enabled` заменяется на enum-ключ (поведенческой миграции не требуется - старый bool не читался ни одним сервисом).
- **OCR/SHARE гейтинг:** действие OCR-перевода доступно только при `CAP_OCR` + `CAP_TRANSLATION` (в noLegal оба скомпилированы); недоступную способность скрывать в списке выбора.
- **Запуск из сервиса:** плеер/DRAW/OCR/SHARE стартуют из контекста сервиса захвата - обязателен `FLAG_ACTIVITY_NEW_TASK`; учесть ограничения background-activity-start (Android 10+).
- **Локализация:** EN/RU/UK - обязательно для подписей действий и настроек.
- **Доступность:** настройка визуальная - текстовые подписи действий, не только иконки направлений.

### 3.3 Owner inputs (Approval gate)

> Авто-одобрено через /spec-all 2026-06-15. Решения приняты forward-bias из кода (research/01); спорные UX-точки помечены для подтверждения на устройстве.

- **Related tickets:** S0405 (родительская способность оверлей + захват экрана, `Archived`), S0418 (перенос в standard, `Archived`), S0450 (orphaned `screenCapturePlay`, запаркован при исследовании). Поглощает S0424 (вверх → DRAW, → архив).
- **UI (новые элементы настроек):** три строки выбора действия (вниз / вправо / вверх) в секции «Screen Gestures» фрагмента `OperationsSettingsFragment`; список фрагмента уже 1172 LOC → логику пикеров вынести в helper-manager. Подписи текстовые, фокусируемые (D-pad/TV/мышь).
- **Flavor:** только noLegal (где примонтирован механизм).
- **Data:** `ScreenshotSettingsStore` (DataStore), три string-ключа enum-действий; мёртвый bool-ключ удаляется.
- **API/маршруты:** переиспользуются существующие - `PhotoVideoStandaloneActivity` (плеер; DRAW через launch-extra; OCR через launch-extra авто-перевода), `SystemShareInvoker.invokeFiles` (системный chooser).

---

## 4. Контекст текущей архитектуры

Краевой жест-скриншот реализован в рамках способности always-on-top оверлея и захвата экрана (S0405, флавор noLegal). Сейчас после захвата выполняется единственное действие - тихое сохранение. Точки выбора поведения по направлению жеста и пользовательской настройки этого выбора в текущем механизме нет.

---

## 5. Предлагаемый подход

Ввести маппинг «направленный жест → после-съёмочное действие» как настраиваемую таблицу из трёх записей (вниз / вправо / вверх), где значением является элемент общего набора действий (включая «не использовать»). После захвата экрана направление жеста разрешается в действие из этой таблицы.

### 5.1 Основные столпы / модули

- Набор после-съёмочных действий (общий enum поведений).
- Хранилище настроек маппинга трёх жестов.
- Диспетчер: по завершении захвата выбирает действие по направлению.
- Переиспользование существующих маршрутов: просмотр в плеере, режим DRAW, OCR-перевод фотографий, системный SHARE.

### 5.2 Потоки данных и событий

Жест (направление) → захват экрана → разрешение направления в действие по настройке → запуск соответствующего маршрута (тихое сохранение / плеер / DRAW / OCR / SHARE) или ничего при «не использовать».

### 5.3 Точки расширяемости

- Набор действий открыт к добавлению новых вариантов без изменения механизма жестов.
- SHARE-вариант проектируется как один из элементов набора, чтобы позже уточнить цель/формат без переделки маппинга.

---

## 6. Открытые вопросы / Research items

Разрешено исследованием (см. `research/01__capture-dispatch-and-routes.md`):

1. **Флаворы** - только noLegal (механизм примонтирован лишь там). Standard - позже, через S0418/S0450. **Артефакт:** research/01.
2. **Хранилище** - существующий `ScreenshotSettingsStore` (DataStore Preferences), три string-ключа; отдельная сущность не нужна. **Артефакт:** research/01.
3. **SHARE** - системный chooser (`SystemShareInvoker.invokeFiles`); предсохранённая цель отложена (`preferredPackage` поддержан движком, но в v1 не используется). **Артефакт:** research/01.
4. **OCR-перевод** - `CameraOcrTranslateActivity` принимает только камеру, не файл; существует лишь in-process путь из bitmap (`TranslationManager` внутри `PhotoVideoStandaloneActivity`). Решение: открыть сохранённый скриншот в `PhotoVideoStandaloneActivity` с launch-extra авто-перевода. **Артефакт:** research/01.
5. **Дефолты** - вниз = «молчаливый скриншот» (сохраняет текущее поведение), вправо и вверх = «не использовать». **Артефакт:** research/01.

Остаточная UX-точка (подтвердить на устройстве, не блокирует): геометрия трёх направлений из одной левой кромочной полосы - угловые окна не должны пересекаться (см. ADR-2).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перегрузка трёх жестов разными действиями путает пользователя | Средняя | Случайные нежелательные действия после съёмки | Понятные подписи, дефолт = молчаливый, «не использовать» по умолчанию для неназначенных |
| OCR/SHARE-маршруты ожидают иной вход, чем даёт захват | Средняя | Сбой действия после захвата | Research §6.4, переиспользовать существующие пути |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая пользовательская возможность: настройка действий направленных жестов скриншота. Одно предложение для FEATURES + _RU + _UK будет добавлено при доработке/реализации.

---

## 9. Архитектурные решения (ADR)

- **ADR-1: `SaveScreenshotUseCase.SaveResult.Success` возвращает локацию файла.** Сейчас возвращает только `(fileName, destinationLabel)`. Добавляется `savedUri: Uri?` (MediaStore insert URI для публичной коллекции; FileProvider/SAF URI для выбранного ресурса), чтобы пост-действие (плеер/DRAW/OCR/SHARE) могло найти результат. Затрагивает все вызовы UseCase.
- **ADR-2: направление переносится enum-ом в колбэке.** `ScreenGestureOverlayManager.onGestureMatched: () -> Unit` → `(direction: ScreenshotGestureDirection) -> Unit`. Три непересекающихся угловых окна от левой кромочной полосы: вверх (диагональ вверх-вправо), вправо (около-горизонталь), вниз (диагональ вниз-вправо - сохраняет текущее окно ~25-65°). Точные границы окон - в тактической спеке. Обновляются обе точки инстанцирования (`ScreenshotAccessibilityService`, `OverlayHostService`).
- **ADR-3: диспетчер пост-захвата.** Новый компонент разрешает `direction → action` из настроек и запускает соответствующий существующий маршрут; вызывается сразу после `SaveScreenshotUseCase` в обеих ветках захвата. `DO_NOT_USE` и `SILENT_SCREENSHOT` дополнительных действий не запускают.
- **ADR-4: enum действий + удаление мёртвого ключа.** Общий enum `ScreenshotGestureAction` (SILENT_SCREENSHOT, OPEN_IN_PLAYER, OPEN_IN_DRAW, OCR_TRANSLATE, SHARE, DO_NOT_USE). Мёртвый bool-ключ `screenshot_gesture_down_enabled` удаляется (не читался ни одним сервисом), заменяется тремя enum-ключами.

---

## 10. Связи с другими спеками

- S0405 - родительская способность (оверлей + захват экрана), `Archived`.
- S0418 - перенос краевого жеста-скриншота в standard, `Archived`.
- S0424 - поглощён этим тикетом (вверх → DRAW), архивирован при слиянии.
- S0450 - orphaned `screenCapturePlay` source set, запаркован при исследовании S0425.

---

## 11. Критерии готовности (strategic-level)

1. Для каждого из трёх жестов пользователь видит и меняет назначенное действие, включая «не использовать».
2. Каждое действие из набора корректно запускается после захвата согласно настройке.
3. Поведение по умолчанию сохраняет текущий молчаливый скриншот.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0425` - создаст `PLAN/S0425_screenshot-gesture-actions/` с фазами.

---

## Last Audit

### Manual / on-device (2026-06-17)

**Verdict: INCONCLUSIVE** - feature wiring confirmed up to the gesture-touch boundary; the directed-swipe path itself is not drivable via synthetic input injection.

Device: emulator-5554 (Pixel 4, Android 17 / SDK 37), noLegal debug v2.60.6170.947. Evidence: `temp/S0425_devtest/` (`evidence.txt`, `settings_gestures_assigned.png`).

Confirmed (expected vs actual):
- Settings UI present: «Left-edge screen gestures» в Management with three action rows (Down / Right / Up) plus overlay toggle, destination, clipboard - expected three pickers, actual three pickers. PASS.
- Action picker offers the full set: Silent screenshot, Open in player, Open for editing, Send to OCR translation, Send to recipients, Share, Do not use - expected the §3.1 enum (OCR visible because translation capability is compiled in noLegal), actual all seven (Send to recipients is the S0472 extension). PASS.
- Defaults: Down = Silent screenshot, Right = Do not use, Up = Do not use - expected §6.5 defaults, actual matches. PASS.
- Assignment + persistence: set Right = Open in player, Up = Open for editing; values survived an app relaunch - expected DataStore persistence, actual persisted. PASS.
- Accessibility service binds and hosts the strip: after enabling the service, `dumpsys accessibility` lists it enabled and `dumpsys window` shows the `screen_gesture_overlay_strip` window (`appop=CREATE_ACCESSIBILITY_OVERLAY`, frame `(0,281) 50x1396`) - expected TYPE_ACCESSIBILITY_OVERLAY strip when overlay enabled, actual present. PASS.

Blocked (the gesture path itself):
- Directed left-edge swipes (RIGHT horizontal, UP up-right diagonal) injected via both `adb shell input swipe` and mobile-mcp, started inside the strip (x < 50) and below the system back-gesture zone - expected `S0425: gesture matched`, `S0425: a11y capture direction=.. action=..`, `S0425: runPostSave action=.. uri=..` plus the configured route (player / DRAW); actual zero S0425 probes, foreground unchanged, no route launched.
- Root cause: `InputManager.injectInputEvent` (the mechanism behind `adb input` and UiAutomator) does not deliver synthetic touches to a `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` translucent accessibility overlay; events route to the window stack beneath the strip. The overlay `setOnTouchListener` never fires. Logcat pipeline itself verified working (rich app Timber output captured on launch).

Not verified (gated by the blocker, require a real finger / instrumented gesture-injection harness on a physical device):
- Per-direction toast + post-capture route dispatch for each action.
- DO_NOT_USE skipping capture entirely (no screenshot saved).
- Angle-window separation of the three directions from the single left-edge strip (portrait and landscape).

Device state restored after the run: gesture overlay toggle OFF, Right/Up reset to «Do not use», accessibility service disabled. No Timber tags removed, status unchanged (remains `BlockNeedUserTest`).
