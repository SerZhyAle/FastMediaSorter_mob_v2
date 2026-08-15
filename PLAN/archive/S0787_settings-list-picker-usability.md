# Спецификация: S0787 - Удобный универсальный выбор значения из списка

**Ticket:** S0787
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:**

Меню выбора настройки из списка (например выбор действия жеста с левого края) - слабоуловимый текст в овалах, текущий выбор не подсвечен, список невозможно увеличивать. Меню должно остаться универсальным для большого числа элементов, но быть удобным выбором из списка: список может быть больше (прокручиваться), и ясно/подсвечено видно текущее значение.

---

## 1. Проблема

Универсальный `ListSelectionDialog` (S0567, ADR-3) - общий picker для многих настроек (действие жеста, назначения, ресурсы, простые значения). Пункты рендерятся как outlined `MaterialButton` («овалы» с бледным текстом), текущий выбор помечен только маленькой leading-иконкой (не подсвечен), а `RecyclerView` имеет `layout_height=wrap_content` и `android:maxHeight` (который RecyclerView игнорирует) - большой список выходит за экран и не прокручивается.

## 2. Цели

1. Пункты - читаемые строки-ряды (не «овалы»), текст в on-surface цвете.
2. Текущий выбор явно подсвечен (фон-highlight + check-иконка), различим и без цвета.
3. Большой список ограничен по высоте и прокручивается; малый - сжимается по контенту.
4. Picker остаётся универсальным - улучшение применяется ко всем пользователям `ListSelectionDialog`.

**Non-goals:**

- Поиск/фильтрация внутри списка (есть отдельный `SearchableOptionPickerDialog`).
- Изменение самих наборов значений в конкретных настройках.

## 3. Ограничения

- **Flavor:** общий UI-компонент (все флейворы).
- **API level:** minSdk 23; theme-attr в drawable/color-selector и `foreground` на LinearLayout - API 23+.
- **Локализация:** без новых строк.
- **Доступность:** выбор кодируется фоном И иконкой (не только цветом); строки focusable/clickable.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0680 (жест-действия), S0567/ADR-3 (сам `ListSelectionDialog`).

## 4. Критерии готовности

1. Пункт выбора - читаемая строка-ряд, не outlined-кнопка.
2. Выбранная строка подсвечена (фон primary-container) + check-иконка + on-primary-container текст.
3. Список >высоты диалога прокручивается (cap 400dp), малый - wrap.
4. Проект компилируется.

## Реализация (2026-07-01, Simple-путь)

- `res/layout/item_list_selection.xml`: outlined `MaterialButton` -> `LinearLayout`-ряд (опц. leading-иконка + start-текст `TextAppearance.Material3.BodyLarge` + trailing check); фон `@drawable/bg_list_selection_row` (highlight при `state_activated`), `foreground=?attr/selectableItemBackground` (ripple), текст `@color/list_selection_row_text` (selector on-surface / on-primary-container).
- `res/drawable/bg_list_selection_row.xml`, `res/color/list_selection_row_text.xml`: новые selector-ресурсы (только theme-attr, без hex).
- `res/layout/dialog_list_selection.xml`: `LinearLayout` -> `ConstraintLayout`; `RecyclerView` с `layout_constrainedHeight=true` + `layout_constraintHeight_max=400dp` между заголовком и панелью кнопок - реально прокручивается при большом списке (обычный `wrap_content` + `android:maxHeight` игнорировался).

## Доработка (2026-07-03, видимость прокрутки)

- Фидбэк: пикер удобнее (больше вариантов сразу, текущий подсвечен), но не видно что это прокручиваемый список - дефолтный бегунок исчезает и не намекает что список продолжается.
- `res/layout/dialog_list_selection.xml`: на `RecyclerView` добавлены `android:scrollbarThumbVertical="@color/scrollbar_thumb"` + `android:fadeScrollbars="false"` - постоянно видимый вертикальный бегунок при переполнении (зеркалит трактовку главного списка ресурсов в `activity_main.xml`). Без новых ресурсов/строк.
- `ListSelectionAdapter`: биндит строку-ряд (`isActivated`=выбран -> подсветка+цвет текста, check VISIBLE, опц. иконка) вместо `MaterialButton`.
- `ListSelectionDialog`: probe-лог при показе.
- Ids сохранены (`tvTitle`, `list_selection_recycler`, `btnClear`, `btnCancel`) - `ListSelectionDialog` не менял привязки.
- Компиляция `compileStandardDebugKotlin` + `processStandardDebugResources` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** открыть любой list-picker (например действие жеста-скриншота в Settings -> Operations): пункты - читаемые строки, текущее значение подсвечено фоном + галочкой; длинный список (destination/resource picker) прокручивается внутри диалога, кнопки Cancel/Clear остаются видимы.
