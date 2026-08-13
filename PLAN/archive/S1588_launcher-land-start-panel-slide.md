# Спецификация (compact bugfix): S1588 - Ландшафт: панель по кнопке "Пуск" выезжает не полностью

**Ticket:** S1588
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-11
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Текст:**

луанчера. ландшафт. по кнопке "Пуск" панель только верхушка появляется и нужно выдвигать. По кнопке пуск она должна выдвигаться над кнопкой вся, по аналогии с портретным исполнением

---

## 1. Проблема / симптом

Режим лаунчера (флейворы `standard`, `noLegal` - только они собирают `src/launcherEnabled`), ландшафтная ориентация. Тап по кнопке "Пуск" на таскбаре открывает меню `LauncherStartMenuFragment`, но видна только верхняя кромка листа - остальное приходится вытягивать пальцем. В портретной ориентации тот же тап показывает лист целиком.

Эвиденс:

- `LauncherHomeActivity.kt:695-699` - `showStartMenu()` вызывает `LauncherStartMenuFragment().show(..)` и ничего не говорит о состоянии листа.
- `LauncherStartMenuFragment.kt:98-102` - `onStart()` трогает только клавиатурный делегат и фокус, `BottomSheetBehavior` не настраивается.
- Grep по `app_v2/src` на `BottomSheetBehavior.from|STATE_EXPANDED|peekHeight|skipCollapsed` - ноль совпадений: состояние листа не задаётся нигде в проекте.
- `fragment_launcher_start_menu.xml` существует ровно в одном варианте (`src/launcherEnabled/res/layout/`), вариантов `layout-land`/`layout-sw*` нет - расхождение не разметочное.

---

## 2. Корневая причина

Лист открывается в состоянии `STATE_COLLAPSED`, а высота "кромки" считается автоформулой Material: при `peekHeight == PEEK_HEIGHT_AUTO` она равна `max(peekHeightMin, parentHeight - parentWidth * 9 / 16)`.

- Портрет: `parentWidth * 9/16` мал относительно `parentHeight`, автовысота получается большой и перекрывает всё содержимое из восьми строк - лист визуально выглядит раскрытым. Это побочный эффект формулы, а не заданное поведение.
- Ландшафт: `parentWidth` велик, `parentHeight` мал, разность уходит в минус, и формула падает на библиотечный минимум `design_bottom_sheet_peek_height_min` - видна одна строка.

То есть портретное "правильное" поведение никем не задано; в ландшафте ломается ровно та же несформулированная зависимость от метрик экрана.

---

## 3. Исправление

Задать состояние листа явно в `LauncherStartMenuFragment`: в `onStart()` взять `BottomSheetBehavior` у `BottomSheetDialog` и выставить `state = STATE_EXPANDED` вместе с `skipCollapsed = true`.

Решение безусловное, без ветвления по ориентации:

- буквально повторяет формулировку владельца - "выдвигаться вся, по аналогии с портретным исполнением";
- убирает зависимость от автоформулы целиком, поэтому не может сломаться снова при другой геометрии экрана (планшет, складной, окно multi-window);
- `skipCollapsed = true` не даёт свайпу вниз залипнуть в промежуточном collapsed-состоянии - жест вниз закрывает лист, как и ожидается от меню "Пуск".

Содержимое уже лежит в `NestedScrollView`, поэтому на низком ландшафтном экране раскрытый лист скроллится, а не обрезается.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- Компиляция: `.\a.ps1 fk` (standard) - exit 0.
- На устройстве, режим лаунчера, ландшафт: тап "Пуск" -> лист раскрыт целиком над таскбаром, ручное вытягивание не требуется.
- На устройстве, портрет: тап "Пуск" -> поведение не изменилось, лист по-прежнему открыт целиком.
- Свайп вниз по раскрытому листу закрывает его сразу, без остановки на промежуточной высоте.

---

## 5. Фазы

### Phase 01 - Expand the Start-menu sheet on open

**Objective:** the Start menu opens fully expanded in every orientation, with no dependence on the Material auto-peek-height formula.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 200 |

#### Step 01.1 - Force the expanded state in `onStart()`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `LauncherStartMenuFragment.onStart()`, cast `dialog` to `BottomSheetDialog` and on its `behavior` set `state = BottomSheetBehavior.STATE_EXPANDED` and `skipCollapsed = true`. Add a comment naming the auto-peek-height formula as the reason the sheet must not be left in the collapsed state. Do not branch on orientation.

**Why:**

Without an explicit state the sheet opens collapsed, and its collapsed height comes from `max(peekHeightMin, parentHeight - parentWidth * 9 / 16)` - a value that covers the content in portrait by accident and collapses to a single row in landscape, which is the reported defect.

**Verification:**

- `Grep` - `STATE_EXPANDED` present in the file exactly once.
- `Grep` - `skipCollapsed = true` present in the file.
- `Grep` - no `Configuration.ORIENTATION` reference added (the fix is orientation-agnostic).
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

#### Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `.\a.ps1 d` exit 0.
- [x] Dev log entry added via `scripts/post-change.ps1` - `post-change: PASS (Kotlin)`.

---

## Last Audit

**Дата:** 2026-08-12
**Вердикт:** Verified

Изменение: `LauncherStartMenuFragment.expandSheet()` - `skipCollapsed = true` + `state = STATE_EXPANDED`, вызывается из `onStart()`. Ветвления по ориентации нет.

Проверка на устройстве RFCR110NBQJ (Galaxy S21+, Android 15, standard debug `v2.60.8112.319`), режим лаунчера включён, HOME-роль выдана и по окончании возвращена `com.sec.android.app.launcher`:

- Ландшафт (2400x1080), тап "Start" - лист раскрыт от статусбара до низа экрана, видны 7 строк из 8, восьмая доступна скроллом `NestedScrollView`. Ручное вытягивание не требуется. Скриншот `temp/scratch/RFCR110NBQJ_20260812_160151.png`.
- Probe-тег: `D LauncherStartMenuFragment: S1588: start menu sheet forced to expanded state` (logcat, 16:01:50). Тег снят после проверки.
- Свайп вниз по раскрытому листу закрывает его сразу, без остановки на промежуточной высоте. Скриншот `temp/scratch/RFCR110NBQJ_20260812_160235.png`.
- Портрет (1080x2400), тап "Start" - регрессии нет, видны все 8 строк. Скриншот `temp/scratch/RFCR110NBQJ_20260812_160307.png`.

Остаточных замечаний нет.
