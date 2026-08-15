# Стратегическая спецификация: S0611 - Контраст кастомных цветовых тем (WCAG)

**Ticket:** S0611
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-22
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос владельца 2026-06-22
**Tactical spec:** `PLAN/S0611_bugfix-custom-theme-contrast/INDEX.md` (создан, 4 фазы Done)

<!-- auto-approved by /spec-all - 2026-06-22 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-22

**Текст (вербатим):**

светлозелёный на сером - отвратительно

**Вложения:**

- Скриншот: диалог «Фільтр» на экране «Трансляції» в теме Тёмно-зелёная (DARK_GREEN). Видны зелёные значения «Категорія»/«Мова» и кнопки на сером фоне диалога - `PLAN/S0611_bugfix-custom-theme-contrast/attachments/01__user-complaint-streams-filter-darkgreen.png`

**Технический evidence (диагноз, собран при разборе жалобы):**

- Корень - недоделка S0569 (custom-color-themes, заархивирован 2026-06-21). Его же аудит оставил незакрытым пункт `[ ] §11.3 / §3.2 WCAG AA: exact accent + background colors and >= 4.5:1 text contrast per theme - owner visual judgment`. Контраст палитр так и не был проверён. Эта жалоба - реализация того отложенного пункта.
- Шесть overlay-тем (`ThemeOverlay.FastMediaSorter.DarkGreen/DarkBlue/DarkRed/LightGreen/LightBlue/LightRed`) переопределяют только `colorPrimary`/`colorPrimaryVariant`/`colorOnPrimary`/`colorBackground`/`colorSurface`/`colorOnSurface`/`windowBackground`. Они НЕ трогают tonal-роли M3 (`colorSurfaceContainer`/`Low`/`High`/`Highest`/`colorOnSurfaceVariant`/`colorPrimaryContainer` и т.п.).
- Material3 рисует фон диалогов/меню/bottom-sheet на `colorSurfaceContainerHigh`, который overlay не переопределяет -> остаётся нейтрально-серым из дефолтной палитры `Theme.Material3.DayNight`. Отсюда серый фон диалога при зелёной теме.
- DARK_* темы используют тёмные primary-тона (`theme_dark_green_primary = #2E7D32`, Material 800 - тон для светлого фона). В тёмном режиме акцент должен быть светлым тоном (~tone 80, напр. Green 200). Тёмно-зелёный акцент на сером/тёмном даёт контраст ~3:1 (ниже AA).
- Кнопка подтверждения (`Widget.FastMediaSorter.Button.DialogConfirm`, S0538) красится `backgroundTint=@color/success_color` + `textColor=@color/white`. Ночной `success_color = #81C784` (светло-зелёный); белый текст на нём даёт контраст ~1.9:1 - нечитаемо. Затрагивает кнопку OK во всех ночных диалогах (включая обычную Dark-тему), не только кастомные.
- Охват подтверждён вживую на эмуляторе (Android 13, тема DARK_GREEN): диалог «Фильтр ресурсов» (серый фон, зелёные иконки/чипы), вкладка «Настройки → Управление» (outlined-кнопки `colorPrimary` на тёмном фоне - тусклые). Проблема системная, не специфична экрану «Трансляції».
- `success_color` используется не только в кнопке: фон превью в color-picker и статус-цвет в менеджере расширений - правка цвета должна учитывать эти места.

**Заплатка (уже применена 2026-06-22, вне этого тикета):**

- `app_v2/src/main/res/layout/dialog_streams_filter.xml`: значения категории/языка переведены с `?attr/colorPrimary` на `?attr/colorOnSurface` (читаемый светлый текст). Это снимает только «зелёные значения на сером» в одном диалоге; серый фон, кнопка OK, светлота акцента и остальные диалоги остаются на этот тикет.

---

## 1. Проблема

Шесть кастомных цветовых тем (S0569) перекрашивают только базовый набор цветовых ролей и не задают полный M3 tonal-набор. Из-за этого диалоги, меню и карточки рисуются на нейтрально-сером контейнере дефолтной палитры, а не на оттенке темы - при «Тёмно-зелёной» теме виден серый диалог с зелёными подписями. Дополнительно тёмные акценты на тёмных поверхностях и белый текст на светло-зелёной кнопке подтверждения дают контраст ниже WCAG AA, то есть часть текста и кнопок нечитаема. Затронут весь слой темизации UI; обычная Dark-тема тоже задета через кнопку подтверждения.

---

## 2. Цели

1. Каждая из 6 кастомных тем несёт полный согласованный с оттенком набор M3-поверхностей: фон диалогов/меню/карточек тематический, а не нейтрально-серый.
2. Текст, вторичные подписи, иконки и акценты во всех 9 темах (Auto/Light/Dark + 6 кастомных) читаемы с числовым контрастом >= 4.5:1 для обычного текста и >= 3:1 для графики/границ.
3. Кнопка подтверждения диалога читаема в тёмном режиме, её заливка развязана с семантическим success-цветом, потребители которого остаются нетронуты.
4. Закрыт незавершённый WCAG-пункт аудита S0569 (§11.3 / §3.2) с зафиксированными измерениями контраста.

**Non-goals:**

- Не вводятся новые цветовые темы и не меняется механизм их применения.
- Не перекрашиваются намеренно-тёмные поверхности камеры/OCR (фиксированные не-тематические по дизайну).
- Не пересматривается базовая Auto/Light/Dark палитра сверх правки кнопки подтверждения (она самосогласована в M3).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Сохранить узнаваемость каждой темы по оттенку (зелёная остаётся зелёной и т.д.), а не свести к нейтральному M3.
2. Минимально трогать слой за пределами ресурсов темы - правка должна быть преимущественно в палитрах/overlay, без логики.

### 3.2 Жёсткие ограничения

- **Flavor:** все (темизация в `src/main`, общая для всех вариантов сборки).
- **API level:** без API-специфики (цветовые ресурсы и атрибуты темы).
- **Wear OS:** не затрагивается.
- **Производительность:** без влияния (статические ресурсы).
- **Совместимость данных:** без миграции (сохранённое значение темы не меняет форму).
- **Локализация:** EN/RU/UK - не затрагивается (изменения только цветовые, без строк).
- **Доступность:** WCAG AA >= 4.5:1 для обычного текста в каждой из 9 тем - центральный критерий этого тикета.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0569 (custom-color-themes, Archived - источник недоделки), S0538 (unify-dialog-action-buttons - владелец стиля DialogConfirm).
- **UI surface затронут:** да - видимые цвета тем (фон диалогов/меню/карточек, тулбар, кнопки, акценты) во всех 9 темах.
- **Visibility/placement:** новые роли невидимы как отдельные элементы - меняют только заливку/текст уже существующих поверхностей; новых экранов и контролов нет.
- **Решённый дизайн-форк (требует визуального подтверждения на устройстве):** в DARK_* темах `colorPrimary` осветляется до light-тона, `colorOnPrimary` становится тёмным -> тулбар в тёмных темах становится светлым-цветным с тёмным текстом (M3-корректно), вместо нынешнего тёмно-цветного с белым. Числовой контраст подтверждён; эстетику тулбаров владелец подтверждает на устройстве.

---

## 4. Контекст текущей архитектуры

За цвет отвечает один слой ресурсов: базовая тема `Theme.Material3.DayNight`, поверх которой при выборе кастомной темы накладывается overlay-стиль (через `theme.applyStyle`), а яркость форсится отдельным night-режимом (`AppCompatDelegate`). Overlay переопределяет лишь несколько ролей, поэтому все незаданные M3 tonal-роли (контейнеры поверхностей, on-variant, container-акценты, outline) приходят из нейтральной baseline-палитры Material3 и не согласованы с оттенком темы. Решить проблему «из §1» правкой одного экрана нельзя: серый фон диалога и низкий контраст - следствие неполноты палитры темы, а не верстки конкретного диалога; нужна полнота tonal-набора на уровне каждой темы.

---

## 5. Предлагаемый подход

Дополнить каждую кастомную тему до полного M3 tonal-набора (фиксированная палитра на тему, поскольку night-режим не переключает overlay), развязать кнопку подтверждения от семантического success-цвета и привести тёмные акценты к читаемому светлому тону. Все значения подбираются под числовой порог контраста, а не «на глаз».

### 5.1 Основные столпы / модули

1. **Полный tonal-набор на тему.** Для каждой из 6 тем задаётся лесенка поверхностей (от фоновой до самой приподнятой), вторичный текст, границы и container-роли акцентов, согласованные с оттенком темы.
2. **Развязанная кнопка подтверждения.** Отдельная пара цветовых ролей под заливку/текст confirm-кнопки с day/night-вариантами; семантический success-цвет (статусы расширений, превью color-picker, результат проверки соединения) остаётся как есть.
3. **Читаемые тёмные акценты.** В тёмных темах акцент (`colorPrimary`) осветляется до light-тона с тёмным `colorOnPrimary`; согласованно правится единственное место, где текст поверх акцента захардкожен, чтобы не сломать контраст.

### 5.2 Потоки данных и событий

Поток применения темы не меняется: то же место накладывает overlay и форсит night-режим. Меняется только содержимое палитр - больше ролей с тематически-согласованными значениями. Любой M3-компонент (диалог, меню, карточка, чип, кнопка) начинает читать тематические поверхности вместо нейтральных дефолтов автоматически, без правок самих компонентов.

### 5.3 Точки расширяемости

- Набор ролей фиксируется как шаблон: будущая тема заполняет тот же список ролей и проходит ту же числовую проверку контраста.
- Калькулятор контраста (артефакт research) переиспользуется как гейт при добавлении/правке любой палитры.

---

## 6. Открытые вопросы / Research items

1. Источник палитр: генерировать полный M3 tonal-набор либо вручную подобрать container/on-variant тона. **Решено:** ручной подбор по Material-тональным ступеням каждого оттенка с числовой WCAG-проверкой всех пар; полный набор значений и таблица контраста - **Артефакт:** `PLAN/S0611_bugfix-custom-theme-contrast/research/01__m3-tonal-palettes-wcag.md`.
2. Правка кнопки подтверждения. **Решено:** выделенная пара токенов фона/текста confirm-кнопки с day/night-вариантами (день - белый текст на тёмно-зелёном, ночь - тёмный текст на светло-зелёном); `success_color` не трогается. **Артефакт:** тот же.
3. Светлота primary в DARK_* темах и каскад на топбар. **Решено:** осветлить primary до light-тона + тёмный onPrimary; топбар в тёмных темах становится светлым-цветным с тёмным текстом (M3-корректно). Единственное место с захардкоженным белым заголовком поверх primary (player-топбар) переводится на `?attr/colorOnPrimary`. **Артефакт:** тот же + §7 риск 1.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Каскад от смены primary меняет топбар во всём приложении | Высокая (подтверждено: топбары на `?attr/colorPrimary`) | Тёмные темы получают светлый-цветный топбар вместо тёмного | onPrimary меняется согласованно (тёмный текст), захардкоженный белый заголовок player переводится на `?attr/colorOnPrimary`; контраст подтверждён численно, эстетика - device-проверка |
| Правка `success_color` ломает прочих потребителей (color-picker, статусы расширений, результат проверки соединения) | Снято | - | Кнопка развязана на отдельные токены; `success_color` не трогается |
| Контраст «починили на глаз», но не измерили | Снято | - | Числовая проверка >= 4.5:1 по всем парам текст/фон в каждой из 9 тем, зафиксирована в артефакте research |
| Overlay живёт только в `values/` - night-режим не переключит его | Средняя | Ночные значения могли бы не примениться | Палитра темы baked-in фиксированными токенами (не night-зависимыми), как и существующие `theme_*` цвета |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (исправление контраста существующих тем, не новая фича).

---

## 9. Архитектурные решения (ADR)

- **ADR-1:** Полнота палитры обеспечивается на уровне темы (overlay + night-bucket), а не правкой отдельных компонентов - так любой M3-виджет наследует тематические поверхности без точечных правок верстки.
- **ADR-2:** Кнопка подтверждения получает выделенные токены вместо семантического `success_color`, по образцу уже существующего отдельного токена деструктивной кнопки (`delete_button`) - роль кнопки не должна зависеть от семантики статуса.
- **ADR-3:** В тёмных темах `colorPrimary` приводится к M3-каноничному light-тону (tone ~80) - это устраняет двойную роль «тёмный акцент на тёмном» в пользу читаемости, ценой ожидаемого осветления топбара.

---

## 10. Связи с другими спеками

- S0569 custom-color-themes (Archived) - ввёл 6 кастомных тем; этот тикет закрывает его незавершённый WCAG-пункт.
- S0538 unify-dialog-action-buttons (Archived) - владелец стиля `DialogConfirm`, чья ночная заливка нечитаема.

---

## 11. Критерии готовности (strategic-level)

1. В каждой из 9 тем (Auto/Light/Dark + 6 кастомных) фон диалогов/меню согласован с темой, а не нейтрально-серый при цветной теме.
2. Текст значений, акцентные подписи, иконки и кнопки действий в диалогах читаемы во всех темах (числовой контраст >= 4.5:1 для обычного текста).
3. Кнопка подтверждения диалога читаема в тёмном режиме (текст на заливке >= 4.5:1).
4. Закрыт пункт аудита S0569 §11.3 / §3.2 (WCAG AA per theme) с зафиксированными измерениями.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация: `PLAN/S0611_bugfix-custom-theme-contrast/INDEX.md` (4 фазы, все Done).

---

## Last Audit

**Date:** 2026-06-24 (Manual device run, `/spec-test-device` parent-sweep FIRST run - fresh build+install of standard debug `2.60.6241.447` on emulator-5554, Android 17, 1080x2280@440) · **Verdict:** PASS-objective for DARK_GREEN (the owner's exact complaint theme) across all four criteria; owner aesthetic sign-off on the lightened DARK_* toolbar (ADR-3) still PENDING - status not flipped, stays owner-gated BlockNeedUserTest.

**Theme applied via Settings UI (Settings > General > Color theme -> Restart Now -> force-stop + explicit MainActivity relaunch):** DARK_GREEN (full coverage). `applied color theme mode=DARK_GREEN` + a D-level `S0611:` probe confirmed installed before each capture.

**Per-criterion objective results (DARK_GREEN):**

- §11.1 dialog/menu/card backgrounds themed (not neutral grey): PASS. Main cards dark-green `surfaceContainer`; resource-ops popup = dark-green `surfaceContainerHigh` (the original grey-bg complaint surface, now themed); Delete-confirm dialog = dark-green surface; theme dropdown popup itself dark-green tinted.
- §11.2 values/labels/icons readable: PASS visually. White titles, legible secondary text, green icon rings - no unreadable text. (Exact ratios per static numeric, research artifact 01: DARK_GREEN dialog text 10.04:1, accent 8.35:1.)
- §11.3 dialog action button readable in dark mode: PASS for the destructive path (Delete-confirm: red `delete_button` fill + white text, high contrast; Cancel outlined themed). The success/confirm green filled `DialogConfirm` was not isolated in a live dialog this run (Restart dialog uses plain text buttons); its night readability stays covered by static numeric (night 7.41:1).
- §11.4 lightened DARK_* toolbar (ADR-3): PASS objective. DARK_GREEN Settings = pale-green status bar + tab strip + dark text ("Settings"/back/search) - light coloured bar + dark text, M3-correct.

**Crashes / errors:** none. 0 app FATAL. The 3 InflateException are Pixel Launcher's own widget (PID 3416, not FMS); the 12 NotFoundException are emulator `persistent_data_block`/system_server package-query noise during force-stop - none from the FMS process, no theme-resource inflation failure.

**Other DARK_*/LIGHT_* themes:** not re-sampled this run - the `SettingsDropdownRow` dropdown popup proved unreliable under mobile-mcp coordinate taps (taps fall through to the underlying ViewPager swipe; one stray tap reset settings to first-run, recovered cleanly). Their full coverage stands from the 2026-06-23 sweep and 2026-06-24 1434 runs.

**Artifacts:** scenario `temp/S0611_mobile_test_scenario_20260624_1447.md`; screenshots `temp/S0611_screens/run2_step_01..07_*.png`; logcat `temp/S0611_run_20260624_1447.log`.

**Why still BlockNeedUserTest:** DARK_GREEN objective parts pass on device; §3.3 reserves the owner's subjective sign-off on the lightened DARK_* toolbar look (ADR-3), which the test runner cannot make. No objective FAIL found.

---

**Date:** 2026-06-24 (Manual device run, `/spec-test-device` sweep-isolated evidence on emulator-5554 — standard debug, Android 17, 1080x2280@440) · **Verdict:** PASS-objective (dialog/menu/card backgrounds themed not grey, text + action buttons readable, lightened DARK_* toolbar correct across sampled themes); owner aesthetic sign-off on the lightened DARK_* toolbars still PENDING — ticket stays owner-gated BlockNeedUserTest, status not flipped.

**Themes applied via Settings UI (Settings > General > Color theme -> Restart -> force-stop + explicit MainActivity relaunch):** DARK_GREEN (owner complaint, full coverage), DARK_BLUE, LIGHT_BLUE. Each apply logged a D-level `S0611:` probe + `applied color theme mode=` line (DARK_GREEN / DARK_BLUE / LIGHT_BLUE all confirmed installed before capture).

**Per-criterion objective results:**

- §11.1 dialog/menu/card backgrounds themed (not neutral grey): PASS. DARK_GREEN resource-ops popup = dark-green `surfaceContainer` (the original grey-bg complaint surface, now themed); DARK_GREEN Delete-confirm dialog = dark-green `surfaceContainerHigh`; DARK_BLUE popup = dark-blue; LIGHT_BLUE popup = pale-blue; main cards tinted per theme in every case.
- §11.2 values/labels/icons readable: PASS visually. Titles white on dark themes / near-black on light theme, secondary text legible, themed icon rings - no unreadable text found. (Exact ratios per static numeric in research artifact 01; DARK_GREEN dialog text 10.04:1, accent 8.35:1.)
- §11.3 dialog action button readable: PASS for the destructive path (DARK_GREEN Delete-confirm: red `delete_button` fill + white text, high contrast; Cancel outlined themed). The success/confirm green filled `DialogConfirm` was not isolated in a live dialog this run (Restart dialog uses plain text buttons); its night readability stays covered by static numeric (night 7.41:1).
- §11.4 lightened DARK_* toolbar (ADR-3): PASS objective. DARK_GREEN Settings = pale-green tab bar + dark text; DARK_BLUE Settings = pale-blue tab bar + dark text - light coloured bar + dark text, M3-correct.

**Crashes / errors:** none. No app FATAL, no InflateException/Resources$NotFound across the 13073-line capture; 8 `E/` lines are framework noise from the deliberate force-stop restarts.

**Artifacts:** scenario `temp/S0611_mobile_test_scenario_20260624_1434.md`; screenshots `temp/S0611_screens/step_01..09_*.png`; logcat `temp/S0611_run_20260624_1434.log`.

**Why still BlockNeedUserTest:** objective parts pass on device; §3.3 reserves the owner's subjective sign-off on the lightened DARK_* toolbar look (ADR-3), which the test runner cannot make. No objective FAIL found.

---

**Date:** 2026-06-23 (Manual device sweep, `/spec-sweep` evidence run on emulator-5554, noLegal debug; theming in `src/main/res` renders identically across flavors) · **Verdict:** PASS-objective (dialogs/menus themed not grey, text + action buttons readable across all sampled custom themes); owner aesthetic sign-off on the lightened DARK_* toolbars still PENDING - ticket stays owner-gated, not auto-Verified.

**Themes sampled (coordinate-tap drive; `SettingsDropdownRow` not in a11y tree as expected):** Dark Green (owner's complaint), Dark Blue, Dark Red, Light Green. Each: set in Settings > General > Color theme -> Restart Now -> force-stop + explicit relaunch of `MainActivity` (the `monkey`/mobile-mcp LAUNCHER intent resolves to the LeakCanary alias on this debug build, so relaunch must target `com.sza.fastmediasorter.ui.main.MainActivity`).

**S0611 probe fired (theme-application seam `ColorThemePrefs.applyThemeOverlay`, logcat tag `S0611:`):** `DARK_GREEN`, `DARK_BLUE`, `DARK_RED`, `LIGHT_GREEN` overlays each logged on apply - confirms the selected overlay was actually installed before each capture set.

**Per-theme objective results** (dialog bg themed? / text readable? / action button readable? / lightened-toolbar captured?):

- Dark Green - dialog bg themed (dark-green, not grey) Y / text readable Y / Delete-confirm Cancel+filled-red button readable Y / Settings toolbar lightened (pale-green bar + dark text, ADR-3) captured Y. Themed surfaces: device-profile dialog, theme dropdown menu, resource ops popup, Delete-confirm dialog, resource cards - all green-tinted.
- Dark Blue - dialog bg themed (dark-blue) Y / text Y / Delete-confirm button readable Y / Settings toolbar lightened (pale-blue bar + dark text) captured Y. Ops menu + cards blue-tinted.
- Dark Red - dialog bg themed (dark-maroon) Y / text Y / Delete-confirm button readable Y / Settings toolbar lightened (pale-pink bar + dark text) captured Y. Ops menu + cards maroon-tinted.
- Light Green - dialog bg themed (pale-green/cream, not neutral grey) Y / dark text readable Y / Delete-confirm button readable Y / toolbar is the light-theme green bar + white text (ADR-3 lightening applies only to DARK_*; not applicable here) Y. Ops menu + cards pale-green-tinted; day accent button (white on dark-green "Sign in to Google") readable.

**Caveat on the green `DialogConfirm` (S0538) button:** the destructive Delete-confirm (red `delete_button` token, white-on-red) was visually verified readable in every theme. The *success/confirm* green filled button was not isolated in a live dialog this sweep (the "Restart Required" dialog uses plain text buttons, not the filled confirm style); its readability remains covered by the static numeric measurement (day 5.13:1 / night 7.41:1 in research artifact 01).

**Artifacts:** `temp/S0611_sweep/<theme>/` - per theme: `a_*` themed dialog/menu, `b_delete_confirm.png` action-button dialog, `c_toolbar_settings.png` lightened toolbar, `d_main_cards.png` themed cards. Helper/intermediate shots under `temp/S0611_sweep/_misc/`.

**Why still BlockNeedUserTest (not auto-Verified):** objective parts pass on device, but §3.3 reserves the owner's subjective sign-off on the lightened DARK_* toolbar look (ADR-3). That call cannot be made by the test runner; the ticket stays owner-gated. No objective FAIL found (no neutral-grey dialog, no unreadable text/button in any sampled theme).

---

**Date:** 2026-06-22 (F5 re-audit, `/spec-next` -> `/spec-all`) · **Verdict:** BlockNeedUserTest (numeric AA + compile + launch-smoke verified; owner on-device visual sign-off pending).

**F5 additions over the prior no-build audit:**

- Compile now proven: today's `standard debug` builds (`a.ps1 fr`/`fk`/`d`) compiled the full `src/main/res` incl. `themes.xml`/`colors.xml`/`values-night/colors.xml` - BUILD SUCCESSFUL, aapt2 clean. Closes the prior "no-build" caveat for resource compilation.
- Launch smoke: app installed and launched on emulator-5554; settings + multiple surfaces inflate with the new palettes, no `InflateException`/FATAL.
- Token presence re-confirmed: 114 `theme_*` refs, `confirm_button_bg/on` in day+night, 6 overlay container-role blocks.
- Debug tag inserted at the theme-application seam (`ColorThemePrefs.applyThemeOverlay`) per the `BlockNeedUserTest` invariant; logs the active custom theme for the device tester.
- Device UI-drive to switch theme declared inconclusive this round (uiautomator dump empty on emulator for the custom `SettingsDropdownRow` views) - not a code issue; owner verifies directly.

**Why BlockNeedUserTest, not Verified:** §3.3 reserves owner on-device sign-off on the lightened DARK_* toolbar aesthetic (ADR-3 consequence). Numeric correctness is proven; the toolbar look is a subjective owner call that must not be auto-verified.

**Prior audit (no-build mode):**

**What landed (resources only, no Kotlin):**

- `values/themes.xml` - all 6 custom overlays extended with the full M3 tonal role set (`colorSurfaceContainer*`, `colorSurfaceVariant`, `colorOnSurfaceVariant`, `colorOutline`/`Variant`, `colorPrimaryContainer`/`On`, `colorSecondaryContainer`/`On`); `DialogConfirm` repointed to `confirm_button_*`.
- `values/colors.xml` - per-theme tonal tokens added (114 `theme_*` refs all resolve); DARK_* `primary`/`on_primary` lightened to M3 tone-80 + dark; `confirm_button_bg/on` (day).
- `values-night/colors.xml` - `confirm_button_bg/on` (night); `success_color` untouched.
- `layout/activity_player_unified.xml` + `layout-land/activity_player_unified.xml` - toolbar title + nav-icon migrated from hardcoded `@color/white` to `?attr/colorOnPrimary` (parity, Rule 11).

**Static verification (no-build):**

- Closed-loop WCAG on SHIPPED resources (`temp/wcag_verify_resources.ps1`, parses the actual XML): `ALL RESOURCE CHECKS PASS` - every theme >= 4.5:1 text, >= 3:1 graphical; confirm button day 5.13:1 / night 7.41:1.
- No dangling `@color/theme_*` references (114/114 defined). Overlay role symmetry: container roles in exactly 6 blocks; `onSurfaceVariant`/`outline`/`outlineVariant` in 7 (6 overlays + base) - expected.
- Quality gates green: `assert-neuroslop.ps1` (layout-hardcoded-colors 88, no delta), `assert-settings-doc-sync.ps1` OK.

**S0569 closeout:** the deferred audit point "§11.3 / §3.2 WCAG AA exact colors + >= 4.5:1 per theme - owner visual judgment" is now closed by numeric measurement (replacing visual judgment) across all 9 themes.

**Residual (deferred under no-build):**

- Device build + visual confirmation of all 9 themes, esp. owner sign-off on the lightened DARK_* toolbars (light-coloured bar + dark text, ADR-3). Numeric contrast is proven; the toolbar aesthetic is the one subjective call left.

---

## Change history

- 2026-06-24 - `/spec-test-device` (claude-opus-4-8[1m], emulator-5554 Android 17, standard debug `2.60.6241.447`): parent-sweep FIRST run, fresh build+install. PASS-objective for DARK_GREEN (owner-complaint theme) across §11.1-§11.4; S0611 D-probes fired (10 hits, all D-level); 0 app crash. DARK_BLUE/etc not re-sampled (flaky dropdown popup). Status unchanged (owner toolbar sign-off pending). Scenario: temp/S0611_mobile_test_scenario_20260624_1447.md
- 2026-06-24 - `/spec-test-device` (claude-opus-4-8[1m], emulator-5554 Android 17, standard debug): sweep-isolated device-evidence run. PASS-objective across DARK_GREEN (full)/DARK_BLUE/LIGHT_BLUE; S0611 D-probes fired per apply; no app crash. Status unchanged (owner toolbar sign-off pending). Scenario: temp/S0611_mobile_test_scenario_20260624_1434.md
- 2026-06-22 - `/spec-all` (no-build): Draft skeleton -> Approved -> Tactical -> Implemented. Research artifact 01 (locked palettes), 4 tactical phases, all resource edits applied + statically verified.
