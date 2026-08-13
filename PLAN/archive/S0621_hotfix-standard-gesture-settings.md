# Стратегическая спецификация: S0621 - Edge-gesture скриншот на standard (consent-путь)

**Ticket:** S0621
**Status:** Archived
**Priority:** 95
**Date:** 2026-06-22
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-22
**Tactical spec:** `PLAN/S0621_hotfix-standard-gesture-settings/`
**Tactical plan:** `PLAN/S0621_hotfix-standard-gesture-settings/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-22
**Захвачено во время:** перенос кнопки «Тест скриншота» в группу жестов (правка layout настроек)

**Текст:**

недавно была уже задача сделать жесты частично доступными в STANDART - видимо интерфейс не подтянулся

**Уточнение владельца (2026-06-22):**

- В standard жесты-скриншоты делаются через промежуточный диалог (MediaProjection consent), без «Спец. возможностей».
- В noLegal жесты-скриншоты по умолчанию тоже через промежуточный диалог, без «Спец. возможностей», но пользователь может включить «Спец. возможности» и получить «тихие» жесты-скриншоты.
- Выкатка - только на standard (не photos).

**Контекст/доказательства (собрано при разборе):**

- Симптом: на standard в Настройки > Управление группа «Жесты с левого края экрана» отсутствует.
- Способность уже выкатывалась на standard через S0418 (MediaProjection-путь, on-device тест 2026-06-15), затем откатана S0423 из-за Play-review риска (`SPECIAL_USE`/`SYSTEM_ALERT_WINDOW`), orphaned-код удалён S0450, а S0559 оставил на standard только menu-кнопку захвата без оверлея.
- Текущий гейт: `ScreenGestureOverlayController` биндится `@IntoSet` только в `src/noLegal`; `src/main` объявляет пустой `@Multibinds Set`, поэтому на standard набор контроллеров пуст и `OperationsGesturesManager.setup()` скрывает группу.
- noLegal-контроллер уже реализует «диалог по умолчанию + a11y-silent при включённых спец-возможностях» (runtime-выбор пути), то есть noLegal-поведение менять не нужно.

**Вложения:** нет.

---

## 1. Проблема

На Play-флаворе standard пользователь не может краевым жестом снять текущий экран: настроечная группа «Жесты с левого края экрана» скрыта, потому что для standard нет реализации контроллера оверлея. Способность есть только на noLegal. Это сознательное состояние после отката S0423 (Play-риск always-on оверлея), а не случайный баг, но владелец решил вернуть способность на standard через Play-приемлемый consent-механизм.

---

## 2. Цели

1. На standard вернуть способность «краевая жест-полоса → снимок текущего экрана → сохранение в назначение» через MediaProjection с диалогом согласия, без accessibility-сервиса.
2. Настроечная группа жестов видна и функциональна на standard; accessibility-специфичные элементы на standard скрыты.
3. noLegal сохраняет текущее поведение без изменений: диалог по умолчанию, опциональный «тихий» a11y-путь при включённых спец-возможностях.
4. Переиспользовать общую (a11y-агностичную) overlay/capture-машинерию без дублирования и без регрессии noLegal.

**Non-goals:**

- photos, lite, legacy, vr - вне объёма (только standard).
- Тихий accessibility-захват на standard - запрещён (Play-риск); accessibility-сервис в манифесте standard не объявляется.
- Изменение capture-поведения noLegal.
- Снижение частоты consent-диалога (одна сессия на несколько снимков) - вне объёма первой итерации.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Поведение на standard максимально близко к noLegal-overlay-пути в части включения, краевого жеста, выбора назначения и сохранения; отличие - только механизм захвата (consent-диалог вместо silent).
2. Цена Play-безопасности (диалог согласия на снимок) принята осознанно.

### 3.2 Жёсткие ограничения

- **Flavor:** только standard. noLegal не трогается. photos/lite/legacy/vr исключены. Реализация по `dev/FLAVOR_DEVELOPMENT_RULES.md`: контракт способности в `src/main`, реализация - во flavor source set, без `BuildConfig.IS_*`-гейтов способности в `src/main` (CLAUDE.md Rule 14/15).
- **Механизм захвата (standard):** только MediaProjection с явным посессионным согласием и системным индикатором записи; accessibility-путь не используется и не объявляется.
- **Разрешения (standard):** `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` (часть уже приходит из общего манифеста). Заполнение деклараций Play Console - релизный гейт, не блокирует имплементацию.
- **API level:** minSdk standard - 26; способность работает на 26+.
- **Индикатор захвата:** каждый снимок сопровождается системным индикатором + тостом с назначением и именем файла. Тихий захват на standard не допускается.
- **Производительность/батарея:** foreground-сервис под оверлей лёгкий, явно останавливаемый; ресурсы захвата освобождаются сразу после снимка (CLAUDE.md Rule 18).
- **Совместимость данных:** снимок встраивается в существующую модель ресурсов/сохранения; новой формы хранения и миграции нет.
- **Локализация:** EN/RU/UK обязательно для любых новых пользовательских строк.
- **Доступность:** прозрачная полоса не озвучивается как элемент; альтернативный доступ к действию «скриншот» (кнопка-тест/меню) обязателен.
- **Системные границы:** оверлей уважает `systemBars` + `displayCutout` в обеих ориентациях (CLAUDE.md Rule 17).

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** только standard (решение владельца 2026-06-22); noLegal неизменен; photos/lite/legacy/vr исключены.
- **Capture mechanism (standard):** MediaProjection consent, без accessibility (решение владельца 2026-06-22).
- **noLegal:** диалог по умолчанию + опциональный a11y-silent - оставить как есть (решение владельца 2026-06-22).
- **Play-риск:** владелец осознанно повторно вводит `SYSTEM_ALERT_WINDOW` + `SPECIAL_USE` FGS на standard, которые убрал S0423 (решение 2026-06-22); декларации Play Console заполняются при сабмите.
- **UI split:** группа жестов на standard показывает overlay-тоггл + селекторы действий жеста + назначение + копирование в буфер + кнопку-тест; строки «Спец. возможностей» (подсказка + кнопка «Открыть настройки спец-возможностей») - только noLegal.
- **Validation level:** сборка standard + on-device проверка (`/spec-test-device`).
- **Related tickets:** S0418 (реализация-прародитель, откатана), S0423 (откат из-за Play-риска), S0450 (удаление orphaned set), S0559 (menu-путь на standard), S0405 (родительская noLegal-способность).

---

## 4. Контекст текущей архитектуры

Способность расслоена на flavor-агностичное ядро (контракт способности, сохранение, выбор назначения, настройки, гейтинг входа в UI по наличию реализации) и flavor-реализацию (overlay-полоса, foreground-сервисы, контроллер). Ядро живёт в `src/main` и не знает деталей захвата. Сейчас реализация контроллера и overlay-машинерия существуют только для noLegal; общий consent-движок захвата уже разделён и смонтирован в standard (S0559) ради menu-кнопки. Поэтому на standard ядро присутствует, но набор контроллеров пуст и группа скрыта. Проблема §1 недостижима без Play-реализации контроллера для standard и общей overlay-машинерии на его classpath.

---

## 5. Предлагаемый подход

Повторно применить проверенный S0418-подход для одного флавора standard поверх текущего состояния: вынести общую overlay-машинерию в разделяемый source set, добавить MediaProjection-only контроллер для standard и подключить его, объявить нужные компоненты/разрешения в манифесте standard без accessibility.

### 5.1 Основные столпы / модули

**A. Общая overlay-машинерия (a11y-агностичная).**
- Роль: держать у края always-on-top прозрачную зону, распознавать жест-триггер и хостить её через foreground-сервис; без ссылок на accessibility.
- Выносится в разделяемый source set, чтобы standard и noLegal делили её без дублирования; accessibility-специфика остаётся эксклюзивом noLegal.

**B. standard-реализация контроллера (consent-based).**
- Роль: на standard включать/выключать оверлей и маршрутизировать захват только через MediaProjection-путь; accessibility не используется.
- Подключается к контракту способности тем же multibind-механизмом, что и noLegal.

**C. Манифест standard.**
- Роль: объявить overlay-host сервис + нужные разрешения (`SYSTEM_ALERT_WINDOW`, `SPECIAL_USE` FGS) на standard; accessibility-сервис не объявляется. Consent-активность и media-projection-сервис уже приходят из общего манифеста.

**D. UI-split и поисковый гейт.**
- Роль: показать группу жестов на standard; accessibility-специфичные строки оставить видимыми только на noLegal; синхронизировать индекс поиска настроек с рантайм-видимостью.

### 5.2 Потоки данных и событий

- Включение: пользователь активирует способность в настройках standard → запрос разрешения «поверх других приложений» → запуск foreground-сервиса, показывающего полосу.
- Снимок: жест от полосы → MediaProjection запрашивает согласие и снимает кадр (с индикатором) → существующее ядро сохранения применяет назначение → запись через файловый слой → тост.
- Выключение: пользователь убирает полосу → сервис и оверлей снимаются, ресурсы захвата освобождаются.
- noLegal: без изменений - runtime-выбор a11y-silent vs MediaProjection-consent остаётся в noLegal-контроллере.

### 5.3 Точки расширяемости

- Контракт способности в ядре не меняется - standard-реализация подключается как ещё одна flavor-реализация.
- Механизм захвата остаётся за абстракцией: standard выбирает consent-путь, noLegal - runtime-выбор; добавление новой стратегии не требует переписывания ядра.
- Общая overlay-машинерия делится без дублирования; a11y-специфика - эксклюзив noLegal.

---

## 6. Открытые вопросы / Research items

1. **Метка «Тихий скриншот» в действиях жеста на standard.** На standard захват всегда с диалогом, поэтому опция/значение «тихий» вводит в заблуждение. Решить на `/spec-tech`: скрыть значение, переименовать для standard или оставить как есть. Статус: Open.
2. **Снижение частоты consent-диалога.** Переиспользование одной сессии согласия под несколько снимков (S0418 §6.2). По умолчанию - согласие на снимок. Статус: Open, вне первой итерации.
3. **Декларации Play Console.** Обоснования для `SYSTEM_ALERT_WINDOW` + `SPECIAL_USE`/`MEDIA_PROJECTION` FGS. Статус: Open, релизный гейт, не блокирует имплементацию.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Отклонение в Play из-за overlay + FGS-разрешений | Средняя | Блокировка standard-релиза | Только consent-механизм без a11y-сервиса; видимый индикатор; декларации Play Console при сабмите |
| Занос accessibility-специфики в standard-манифест при выносе кода | Средняя | Тот самый Play-вектор | Чёткое разделение source-set: a11y-классы и манифест-объявление остаются эксклюзивом noLegal; проверка merged-манифеста standard |
| Регрессия noLegal при выносе общей машинерии | Средняя | Поломка обкатанной фичи | Минимизировать изменения noLegal; собрать и проверить noLegal после выноса |
| Группа жестов на standard показывает accessibility-строки | Средняя | Нерабочие/вводящие в заблуждение элементы | Per-row гейт: accessibility-строки видны только при наличии accessibility-пути (noLegal) |
| Consent-диалог на каждый снимок раздражает | Средняя | Слабый UX | Честный индикатор; §6.2 как будущее улучшение |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая пользовательская способность в standard-сборке. На фазе интеграции добавить одно предложение в `docs/FEATURES.md` + `_RU` + `_UK` (например: «Снимок любого экрана краевым жестом с сохранением в выбранный ресурс или папку скриншотов»). Запись идёт в основные `docs/FEATURES*.md` (Play-флавор), не в `_noLegal`.

---

## 9. Архитектурные решения (ADR)

**ADR-1: На standard захват только MediaProjection-consent; accessibility - эксклюзив noLegal.** standard снимает экран только через путь с явным согласием и системным индикатором; accessibility-сервис в standard не объявляется и не используется. Наследует решение S0418 ADR-1, повторно санкционировано владельцем 2026-06-22.

**ADR-2: standard переиспользует flavor-агностичное ядро, не дублируя его.** Сохранение, выбор назначения, настройки, восстановление после старта и гейтинг входа берутся из существующего ядра; добавляется только standard-реализация контроллера и проводка.

**ADR-3: noLegal неприкосновенен; общая overlay-машинерия выносится без его регрессии.** Общая (не a11y) машинерия выносится в разделяемый source set; a11y-специфика остаётся в noLegal; изменения noLegal минимизируются и проверяются сборкой.

---

## 10. Связи с другими спеками

- S0418 - реализация-прародитель (Play-фаза на standard+photos), откатана; источник переиспользуемого чертежа (4 фазы) и продуктовых решений.
- S0423 - откат screencapture из store-флаворов из-за Play-риска; настоящий тикет осознанно реверсирует его на standard.
- S0450 - удаление orphaned `screenCapturePlay`; код восстанавливается заново по чертежу S0418.
- S0559 - menu-путь захвата на standard; общий consent-движок уже смонтирован в standard.
- S0405 - родительская noLegal-способность (overlay + a11y silent).

---

## 11. Критерии готовности (strategic-level)

1. На standard пользователь включает способность в настройках, и краевая полоса активна поверх других приложений.
2. Жест от полосы снимает текущий экран через MediaProjection с диалогом согласия и видимым индикатором, снимок сохраняется в назначение (по умолчанию - папка скриншотов устройства, иначе Downloads).
3. В собранном standard-манифесте accessibility-сервис для скриншотов не объявлен.
4. Способность полностью выключается пользователем; после выключения полоса и сервис не остаются активными.
5. Реализация noLegal и её runtime-выбор пути не изменены (сборка noLegal зелёная, поведение прежнее).
6. Группа настроек жестов на standard не показывает accessibility-специфичные строки; индекс поиска настроек соответствует рантайм-видимости.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0621` - создаст `PLAN/S0621_hotfix-standard-gesture-settings/` с фазами (чертёж - 4 фазы S0418, адаптированные под текущее состояние и scope=standard).

---

## Last Audit

### Manual device test - 2026-06-24 (INCONCLUSIVE on standard - capability compiled out by `fms.screenCapture=off`)

**Device:** emulator-5554, sdk_gphone16k_x86_64, Android 17 / SDK 37, portrait 1080x2280, density 440.
**Build:** `2.60.6241.320-DEBUG` (`com.sza.fastmediasorter.debug`, genuine **standard** - suffix `-DEBUG`, not `-NoLegal-DEBUG`), built via `scripts/builders/build-standard-device.ps1` with project default gradle properties.
**Scenario:** `temp/S0621_mobile_test_scenario_20260624_1942.md`. Run log: `temp/S0621_run_20260624_1942.log`.

**Why this run:** the 2026-06-23 entry verified the shared capability on the noLegal superset and left the two standard-only assertions N/A. This run targeted a real standard build to close them.

**Sub-step results:**
- C3 standard manifest has no a11y screenshot service: **PASS** (closes prior N/A) - installed standard pkg declares zero `AccessibilityService` in its merged manifest (`dumpsys package`); `src/standard/AndroidManifest.xml` has no accessibility entries; `ScreenshotAccessibilityService` is declared only in `src/noLegal`.
- C1 gesture group present + functional on standard: **INCONCLUSIVE (group ABSENT)** - the Operations ("Управление") tab, scrolled top to bottom, ends at `headerSystemApps`/`groupSystemApps`/`rowControlsKeybindings`/`btnResetOperationsSection`; `headerScreenGestures`/`groupScreenGestures`/`rowGestureOverlayEnabled` are nowhere in the tree.
- C6 accessibility rows ABSENT on standard: **INCONCLUSIVE** - the entire gesture group is absent, so the rows cannot be asserted present-or-absent.
- C2 swipe -> MediaProjection consent -> save + toast; C4 toggle off removes strip + FGS: **BLOCKED** - no group entry point on this build.
- C5 noLegal unchanged: out-of-scope for a standard run.

**Root cause (build-config, NOT an S0621 code defect):**
- `OperationsGesturesManager.setup()` hides `groupScreenGestures` when the injected `Set<ScreenGestureOverlayController>` is empty.
- The standard controller `@Binds @IntoSet` (`src/standardScreenCapture/.../di/ScreenCaptureModule.kt`, impl `ScreenGestureOverlayControllerImpl.kt`) is mounted into the standard variant only when `screenCaptureStandardEnabled == true` (`build.gradle.kts:583-588`), i.e. gradle prop `fms.screenCapture != "off"` (`build.gradle.kts:171-172`).
- `gradle.properties:17` sets `fms.screenCapture=off` (the Play-fast escape hatch). The default standard build therefore excludes the controller source set.
- Binary proof: `ScreenGestureOverlayControllerImpl`/`standardScreenCapture` strings are absent from every `classes*.dex` in the built APK. The S0621 capability is compiled out.
- Corroboration: zero `S0621:` probes in the 2756-line capture; the only S0621 tag (`OperationsGesturesManager.setup()` line 46) fires only with a non-empty controller set, so its absence confirms the empty set.

**Verdict:** INCONCLUSIVE for the standard device-evidence run - the APK built with project defaults does not contain S0621's capability. C3 is PASS. C1/C2/C4/C6 are unreachable until standard is built with `fms.screenCapture=on` (e.g. `build-standard-device.ps1 -P fms.screenCapture=on`, or flip `gradle.properties:17`), after which `/spec-test-device S0621` should be re-run. Spec status left BlockNeedUserTest (sweep owns finalization).

---

### Manual device test - 2026-06-23 (PASS - shared capture capability verified on noLegal superset)

**Device:** emulator-5554, Android SDK 37, x86_64, landscape 2560x1600.
**Build:** `2.60.6231.642-NoLegal-DEBUG` (`com.sza.fastmediasorter.debug`).

**Scope note:** owner directed verification on the installed **noLegal** build (the all-inclusive superset; standard's behavior is a subset). The accessibility service was kept DISABLED throughout (`enabled_accessibility_services=null`, `accessibility_enabled=0`), so noLegal defaults to the MediaProjection consent path - the exact shared `screenCapture` machinery that S0621 ships to standard. The two pure standard-build-config assertions ("accessibility rows ABSENT" and "group appears where it was hidden") are NOT runtime-verifiable on noLegal and are marked N/A; they require a standard build or a static merged-manifest check.

**Sub-step results:**
- Gesture group present + functional: PASS - "Left-edge screen gestures" group (`headerScreenGestures` / `groupScreenGestures`) present and expanded with `rowGestureOverlayEnabled` + Up/Right/Down action selectors + "Screenshot test" + "Save screenshots to.." (= "Device screenshots folder (or Downloads)") + "Save to clipboard". Probe fired on entry.
- Enable overlay + grant draw-over-apps: PASS - toggling "Gesture overlay" on raised the in-app "Enable screen gestures" dialog; chose "Old method" ("a version that asks for permission on every shot") to stay on the consent path with a11y off. This routed to the system "Display over other apps" screen; granted FastMediaSorter (`appops SYSTEM_ALERT_WINDOW: allow`). `OverlayHostService` then started as a foreground service (`isForeground=true`, channel `screen_capture_overlay_host`); log `Background started FGS: Allowed ... OVERLAY_HOST_START`.
- Gesture capture via MediaProjection consent: PASS - a diagonal swipe started just inside the left-edge strip (`adb input swipe 25 500 350 950`; a first swipe from x=8 was swallowed by the system back-edge - logged as one wasted attempt) launched `ScreenCaptureConsentActivity` -> system `MediaProjectionPermissionActivity` ("Share your screen with Fast Media Sorter..?", "Share one app"). Accepted (Next -> chose the FastMediaSorter task). A `screen_capture_service` VirtualDisplay was created, the frame captured, and the file saved: MediaStore `_id=109`, `_display_name=screenshot_20260623_172016.png`, `relative_path=Pictures/Screenshots/`, `_size=19840`. Projection stopped immediately after the shot (resources released, Rule 18). A Toast indicator was shown (`NotificationService ... Toast pkg=com.sza.fastmediasorter.debug`).
- Toggle off: PASS - turning "Gesture overlay" off removed the strip; `OverlayHostService` left the running-services list and received `Service#onDestroy()` (LeakCanary watch line), i.e. the FGS cleared.
- Accessibility rows ABSENT (standard-only): N/A - noLegal correctly shows `tvAccessibilityShortcutHint` + `btnOpenAccessibilitySettings`; absence is a standard merged-manifest/gating fact, not observable here.
- Group appears where it was hidden (standard-only): N/A - on noLegal this group is always visible (parent S0405); the standard un-hide is a build-config fact, not observable here.

**Notes:**
- Open question §6.1: "Down gesture action" default reads "Silent screenshot". On standard the consent dialog fires on every shot, so this label needs the §6.1 relabel/hide decision; observed value is from noLegal and not authoritative for standard.
- The consent path verified here is a11y-independent (a11y stayed off), which is exactly the standard capture mechanism per ADR-1.

**Fired S0621 probes:**
- `S0621: gestures settings group setup; supportsA11ySilent=true (accessibility-shortcut rows shown)` - the only S0621 tag in the code is this settings-group probe; it fired on every entry to the gesture group. `supportsA11ySilent=true` is correct for noLegal; the standard branch (`false`) is not exercised on this build.

**Screenshots (under `temp/S0621_sweep/`):**
- `01_launch.png` - main screen.
- `02_settings_general.png` - Settings, General tab.
- `03_gesture_group_standard.png` - "Left-edge screen gestures" group, top rows.
- `04_a11y_rows_present_nolegal.png` - scrolled group showing the accessibility hint + "Open accessibility settings" button (noLegal-only rows).
- `06_after_adb_tap.png` - "Enable screen gestures" dialog after enabling the overlay toggle.
- `07_after_old_method.png` / `08_overlay_grant_screen.png` / `09_overlay_granted.png` - "Old method" -> "Display over other apps" grant flow.
- `10_back_in_app.png` / `11_main_with_strip.png` - overlay toggle ON, strip active over the app.
- `14_mediaprojection_consent.png` / `15_consent_step2.png` / `16_after_app_select.png` - MediaProjection consent dialog + app chooser.
- `17_after_capture.png` - post-capture.
- `18_overlay_toggled_off.png` - overlay toggle OFF, strip gone.

**Verdict:** PASS for the shared screenCapture + MediaProjection-consent capability (the path S0621 brings to standard), verified on the noLegal superset with accessibility disabled. The two standard-only flavor-config assertions remain N/A here and need a standard build or merged-manifest check.
