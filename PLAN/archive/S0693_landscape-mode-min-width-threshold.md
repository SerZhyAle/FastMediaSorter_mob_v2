# Стратегическая спецификация: S0693 - Ландшафтный UI по порогу ширины, а не по соотношению сторон

**Ticket:** S0693
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - parked from user `/spec-draft` request on 2026-06-25
**Tactical plan:** [`PLAN/S0693_landscape-mode-min-width-threshold/INDEX.md`](S0693_landscape-mode-min-width-threshold/INDEX.md)

---

## 0. Captured material (inbox)

**Captured:** 2026-06-25 (direct user request)

**Raw request (verbatim):**

> принимать "ландшафтный" вид для окон, активити , диалогов не когда ширина больше высоты, как сейчас, а когда ширина больше определенного чсла пикслей, которое нужно вычислить. Поможет на широких устройствах, которые с высоким разрешением и при этом повернуты как портрет - там изза портретного подхода всё съезжает в левый верх

**Captured intent:**

- Landscape-style layouts for windows, activities, and dialogs should switch by a computed minimum width threshold, not by the current `width > height` rule.
- Very wide high-resolution devices held in portrait should still be able to receive the landscape-style layout when the available width is large enough.
- The change is intended to prevent portrait-only placement from collapsing content toward the top-left corner on such devices.

**Attachments: none.**

**Owner decision 2026-06-25 (during the S0689-S0701 batch): deferred - stays Draft.** Recon found ~43
independent orientation / `width > height` / `widthPixels` checks across activities, dialogs and
`layout-land` resources, so a global switch to a computed width threshold is the highest-blast-radius
change in the batch and risks regressing landscape layout app-wide. To be revisited standalone, likely as
`layout-w600dp` resource qualifiers plus one shared rule rather than 43 hand-edits. The specific
"landscape = more than one column in the streams list" wish ships separately and immediately as **S0692**.

**Reactivation 2026-06-25 (later same day): promoted to Approved.** Owner asked for full research up to a
tactical plan. Owner inputs collected (§3.3) confirm the direction the deferral note anticipated: one
shared decision node + `-w600dp` resource alignment, not 43 hand-edits. Player family stays out of scope as
a follow-up.

---

## 1. Problem

- Landscape-стиль раскладки решается сегодня от соотношения сторон: `Configuration.orientation == ORIENTATION_LANDSCAPE` на ~20 независимых non-player runtime-сайтах, две raw-проверки `widthPixels > heightPixels`, плюс resource-квалификаторы `res/layout-land/` (78 файлов) и `res/values-land/` (тоже aspect-based).
- На широких высокоплотных устройствах в портрете (большая доступная ширина в dp, но `width < height`) приложение остаётся в портретной раскладке. Контент, рассчитанный на узкий портрет, прижимается к левому верхнему углу и оставляет пустое поле справа.
- Нет единого источника правды: каждый сайт переисчисляет решение сам. Поведение несогласованно - рядом уже живут width-based проверки `screenWidthDp >= 600` - и нет одной точки, где правило можно изменить или откатить.

---

## 2. Goals

- Ввести единый узел решения "должно ли это окно использовать landscape-раскладку?" по доступной ширине окна, а не только по соотношению сторон.
- Провести все non-player runtime decision-сайты через этот узел.
- Сделать порог единым именованным значением - откат правила одной строкой.
- Сохранить нулевой регресс для устройств, которые landscape сегодня (аддитивное union-правило).

**Non-goals:**

- Семья плеера (PlayerActivity, StandalonePlayerActivity, Document/Text/Audio/PhotoVideo standalone, command panel, PDF/EPUB-кнопки) - отдельный follow-up тикет; плеер сохраняет поведение по реальной ориентации и связку landscape->FULLSCREEN.
- Изменение политики физического вращения (`requestedOrientation`) и двух настроек вращения.
- Модуль `wear/` - orientation-логики в нём нет.

---

## 3. Wishes and constraints

### 3.1 Owner wishes

- Единый узел принятия решения "ландшафт или портрет", распространённый на всё и везде, ради быстрого роллбэка идеи.
- Концептуально связать решение о раскладке с реакцией на датчик вращения, ОС и две настройки вращения - но саму политику вращения не трогать (см. §3.3: узел только про раскладку).
- Переиспользовать порог 600dp.

### 3.2 Hard constraints

- **Flavor:** все (standard/lite/photos/legacy). Правило сквозное, без flavor-гейтинга; никаких `BuildConfig.IS_*` в `src/main` (Rule 14).
- **API level:** minSdk 26 (standard) / 23 (legacy). Источник ширины - `Configuration.screenWidthDp` (доступен с API 1, multi-window-aware). Без новой зависимости (androidx.window НЕ подключён), без `@RequiresApi`.
- **Wear OS:** вне scope.
- **Performance:** решение - чистое чтение `Configuration`, без аллокаций, безопасно вызывать на каждом layout-проходе.
- **Data compatibility:** персистентных данных не меняем; порог - константа/`R.integer`.
- **Localization:** пользовательских строк нет.
- **Accessibility:** сохранить работу D-pad/TV/мышь; порядок фокуса не меняется (Rule 16).

### 3.3 Owner inputs (Approval gate)

- **Rule shape:** Union (аддитивно) - `isLandscapeStyle = orientation == LANDSCAPE || screenWidthDp >= 600`. Нулевой регресс: всё, что landscape сейчас, остаётся; плюс wide-portrait.
- **Node scope:** только раскладка (B). Политика вращения (A: `requestedOrientation`, две настройки) остаётся в `AppOrientationManager`/`ScreenRotationManager`; настройки влияют на раскладку лишь косвенно - меняют, какой `Configuration` получится.
- **Width source:** ширина текущего окна (`screenWidthDp`), multi-window-aware. Узкое окно в split-screen на планшете -> портретная раскладка (корректно для окна).
- **Threshold:** 600dp, переиспользовать существующий tablet-брейкпоинт, единая именованная константа.
- **Host scope:** все non-player decision-сайты.
- **Player:** вне scope (отдельный тикет).
- **Related tickets:** S0692, S0606, S0162, S0439.

---

## 4. Current architecture context

Три слоя решают "landscape" сегодня:

- **(a) Resource qualifiers:** `res/layout-land/` (78 файлов, у всех есть портретный counterpart), `res/values-land/` (`bools/dimens/integers`: `grid_column_count_landscape`, `resource_grid_column_count`, dialog min-width и т.д.). Триггер - `land` (соотношение сторон). `activity_welcome.xml` - исключение: варьируется через `layout-sw480dp/`/`layout-sw720dp/`, без `layout-land`.
- **(b) Runtime Kotlin (~20 сайтов, non-player):** тулбар-лейблы Main/Browse (`MainLayoutChromeManager`, `BrowseButtonSetupHelper`); span/layout-manager списков (`MainActivity`, `MainLayoutChromeManager`, `MainResourceTabsManager`, `BrowseRecyclerViewManager`, `StreamGridModeManager`); grid span (`AppLaunchPanelDialogFragment`, `EditAppLaunchPanelActivity`, `KeybindingRemapActivity`, `AddResourceFormManager`); settings-табы/тулбар (`SettingsActivity`, `GeneralSettingsFragment`); размеры диалогов (`CalculatorInputManager`; `ScheduledOperationDialog` - единственный raw `widthPixels > heightPixels`). `ProfilesPageViewHolder` уже использует `smallestScreenWidthDp` - близко к целевому подходу.
- **(c) Shared helper:** отсутствует. Ближайшее - `PlayerOrientationModeManager` (player-only, принимает готовый boolean) и `WindowMetricsCompat` (player-only, отдаёт real-screen size).

Прочее:

- Хук конфигурации: `BaseActivity.onConfigurationChanged()` -> `protected open onLayoutConfigurationChanged(newConfig)` (сейчас no-op stub, переопределяется подклассами). Естественная точка вставки.
- Политика вращения: `programFollowSystemRotation` (non-player, через `AppOrientationManager`: ON->`UNSPECIFIED`, OFF->`SENSOR`, никогда не лочит); `playerFollowSystemRotation` + `playerRotationSensorEnabled` (player, через `ScreenRotationManager`).
- Manifest: ни у одной activity нет `android:screenOrientation`; ключевые экраны используют `configChanges=orientation|screenSize|..` и сами обрабатывают поворот.
- Прецедент порога: `screenWidthDp >= 600` уже используется в Main/Browse - 600dp согласуется с существующим tablet-брейкпоинтом и landscape-`R.integer`.

---

## 5. Proposed approach

### 5.1 Main pillars / modules

- **A. Decision node.** Расширение `Configuration.isWideLayout(): Boolean` (плюс удобный `Context.isWideLayout()`) в `core/orientation`, возвращающее `orientation == ORIENTATION_LANDSCAPE || screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP`. Порог - единая константа `WIDE_LAYOUT_MIN_WIDTH_DP = 600` (или `R.integer.wide_layout_min_width_dp`). Одно правило, одна точка отката.
- **B. Distribution.** Заменить каждую non-player landscape-проверку вызовом узла, включая raw-pixel проверку в `ScheduledOperationDialog`. Узел читает `Configuration`, который уже отражает результат политики вращения, поэтому корректно реагирует на датчик/ОС/настройки без прямой связи с ними.
- **C. XML qualifier alignment.** Чтобы выбор ресурсов Android совпадал с узлом для wide-portrait, добавить `-w600dp` варианты (`layout` + `values` integers/bools/dimens), зеркалящие содержимое `-land`. Это закрывает причину "контент съезжает в левый верх", которая живёт в XML, а не в Kotlin. Тактика решает по-экранно: держать оба (`-land` и `-w600dp`) ради union либо мигрировать. Берётся `-w600dp` (доступная ширина), не `-sw600dp` - реагирует на поворот/multi-window так же, как узел.

### 5.2 Data and event flows

- `onConfigurationChanged` срабатывает на поворот, multi-window resize, fold/unfold -> каждый мигрированный сайт перечитывает узел -> пересчитывает span/visibility/layout.
- Resource-слой: Android переселектит `layout`/`values` на смену конфигурации; `-w600dp` матчится, когда ширина окна >= 600dp.

### 5.3 Extension points

- Единственная константа порога; откат - вернуть правило к orientation-only (ширинная ветвь отключается).
- Узел - готовый шов для будущей миграции плеера (follow-up).

---

## 6. Open questions / Research items

Разрешено владельцем (см. §3.3): rule shape (union), node scope (layout-only), width source (current window), threshold (600dp), host scope (все non-player), player (вне scope).

Остаётся на тактический уровень:

- По-экранная обработка XML: зеркалить `-land` в `-w600dp` или мигрировать; на каких именно экранах "съезд в левый верх" вызван XML, а не рантаймом.
- Нужны ли копии `R.integer` grid-счётчиков в `values-w600dp` для каждого экрана, читающего `resources.getInteger(..)`.
- Малые телефоны в landscape (<600dp) под union: регресса нет by design (ветвь orientation сохраняет landscape).

---

## 7. Risks

| Risk | Probability | Impact | Mitigation |
|------|:-----------:|--------|------------|
| Экраны с landscape-адаптацией только в `res/layout-land/` не отреагируют на wide-portrait от Kotlin-узла | High | Wide-portrait остаётся "в левом верху" на этих экранах | Pillar C: `-w600dp` зеркала layout |
| `R.integer` landscape-значения не читаются на wide-portrait (qualifier mismatch) | Medium | Несогласованные grid-счётчики: узел говорит wide, integer портретный | Копии в `values-w600dp` |
| Широкая поверхность правок (~20 сайтов + ресурсы) | Medium | Регрессии раскладки на разных экранах | Центральный узел + по-экранная проверка + константа-роллбэк |
| Узкое split-screen окно на планшете под `values-land` на некоторых OEM | Medium | Лишняя landscape-стилизация в узком окне | Узел на `screenWidthDp` корректен; задокументировать OEM-ограничение ресурсов |
| Union меняет поведение существующих landscape-телефонов | Low | - | Union - надмножество текущего правила; проверить на телефоне |

---

## 8. User impact (docs/FEATURES)

- Wide-portrait планшеты и раскрытые фолды получают разнесённую landscape-раскладку вместо прижатого к левому верху портрета на ключевых экранах. Обычные телефоны - без изменений. Политика вращения и настройки - без изменений.
- Capability фиксируется в `docs/ALL_FEATURES.jsonl` при доставке.

---

## 9. Architectural decisions (ADR)

- **ADR-1:** Union-правило (`landscape || width>=600`) вместо чистого порога - нулевой регресс для малых телефонов в альбоме.
- **ADR-2:** Узел только про раскладку (B); политика вращения не трогается - меньше радиус поражения, плеер вне scope.
- **ADR-3:** `Configuration.screenWidthDp` вместо `WindowMetrics`/androidx.window - без новой зависимости, multi-window-aware на minSdk 23/26.
- **ADR-4:** `-w600dp` (available width) для выравнивания XML с узлом, не `-sw600dp` - реагирует на поворот/multi-window так же, как узел.
- **ADR-5:** Порог 600dp - переиспользование существующего tablet-брейкпоинта и landscape-`R.integer`.

---

## 10. Related specs

- S0692 (stream-list-landscape-multi-column) - `StreamGridModeManager` входит в число мигрируемых сайтов; согласовать.
- S0606 (3D/VR settings group not scrollable in landscape) - смежный landscape-экран.
- S0162 / S0439 - архитектура политики вращения; этот тикет не должен её регрессировать.

---

## 11. Strategic done criteria

- Существует единый узел решения landscape-раскладки; все non-player сайты вызывают его.
- Порог - одна именованная константа; правило union.
- Wide-portrait устройство показывает landscape-раскладку на ключевых экранах; телефоны без изменений.
- Политика вращения и две настройки вращения не изменены.
- Откат - одной строкой (правило -> orientation-only).

---

## 12. Link to tactical spec

Next step: `/spec-tech S0693` - создаёт `PLAN/S0693_landscape-mode-min-width-threshold/` с фазами.
