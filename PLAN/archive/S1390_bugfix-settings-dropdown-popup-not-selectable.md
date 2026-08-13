# Спецификация (compact bugfix): S1390 - Выпадающий список настроек не выбирается и не виден инспекторам

**Ticket:** S1390
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-04
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-04

**Захвачено во время:** S0484 (`/spec-prerelease` sweep)

**Текст:**

Settings dropdown row popup (SettingsDropdownRow, id/sdr_autocomplete, e.g. "Цветовая тема") opens as a PopupWindow that is invisible to both uiautomator dump and the accessibility tree, and does not respond to DPAD_DOWN/ENTER; touch taps on blind coordinates also fail to commit a selection. Evidence: dumpsys window windows shows Window{PopupWindow:447fd66} with mParentWindow=SettingsActivity while uiautomator dump --compressed and mobile-mcp mobile_list_elements_on_screen both return only the parent activity tree; spinner value stayed "Авто (за устройством)" after every attempt. Impact: no automated test can set any dropdown-backed setting, and D-pad/TV/screen-reader users likely cannot either (CLAUDE.md Rule 16). Maestro settings_toggle_sweep.yaml passes, so switches are fine - the defect is specific to dropdown rows.

---

## 1. Проблема / симптом

Наблюдалось на emulator-5554 (API 35), standard-debug `v2.60.8041.533-DEBUG`, экран `SettingsActivity` -> секция "Общие настройки интерфейса" -> строка "Цветовая тема" (`id/spinnerColorTheme` / `id/sdr_autocomplete`).

Эвиденс:

- Нажатие на строку открывает popup: `dumpsys window windows` показывает `Window{9ab2182 u0 PopupWindow:447fd66}` с `mParentWindow=Window{... SettingsActivity}` и `mAnimationIsEntrance=true`.
- `uiautomator dump` и `uiautomator dump --compressed` возвращают только дерево родительской активности - ни одного узла popup.
- `mobile_list_elements_on_screen` (accessibility tree) - тот же результат, popup отсутствует.
- `input keyevent KEYCODE_DPAD_DOWN` x2 + `KEYCODE_ENTER` при открытом popup не меняют значение.
- Слепые тапы по предполагаемым координатам элементов списка значение тоже не меняют.
- После каждой попытки значение остаётся `Авто (за устройством)`.

Скриншот экрана снять нельзя - `SettingsActivity` под FLAG_SECURE (настройка "Защищать секретные экраны"), скриншот полностью чёрный (ожидаемое поведение, см. S1284).

Контраст: Maestro-флоу `settings_toggle_sweep.yaml` в том же прогоне прошёл - переключатели (`str_switch`) доступны и щёлкаются. Дефект специфичен для dropdown-строк.

---

## 2. Корневая причина

Установлена (исследование 2026-08-04).

`SettingsDropdownRow` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt`, 219 строк) - составной вид: `TextInputLayout` со стилем `Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu`, внутри которого лежит обычный `android.widget.AutoCompleteTextView` (`app_v2/src/main/res/layout/view_settings_dropdown_row.xml`). Нажатие на строку отдаёт управление штатному `AutoCompleteTextView.showDropDown()`, а тот открывает немодальное `ListPopupWindow`, которое по своей конструкции не фокусируемо - так сделано, чтобы фокус клавиатуры оставался в поле ввода.

Отсюда оба симптома, и это один дефект, а не два:

- Окно такого класса не попадает в обход дерева узлов, который делают `uiautomator dump` и accessibility-дерево, поэтому popup не виден инструментам.
- Окно никогда не получает системный фокус ввода, поэтому DPAD_DOWN и ENTER продолжают уходить в тот вид, который держал фокус до нажатия, и значение не меняется.

FLAG_SECURE к этому отношения не имеет. Флаг документирован как защита от захвата экрана и не скрывает окно от accessibility; `PopupWindow` к тому же строит собственные `WindowManager.LayoutParams` и не наследует флаг родителя автоматически. FLAG_SECURE объясняет только чёрный скриншот, что уже описано в S1284.

Сопутствующее расхождение: Material для стиля `ExposedDropdownMenu` предписывает `com.google.android.material.textfield.MaterialAutoCompleteTextView`, а здесь стоит платформенный `AutoCompleteTextView`. Версия библиотеки - `com.google.android.material:material:1.14.0` (`app_v2/build.gradle.kts`).

Масштаб: `SettingsDropdownRow` стоит на девяти поверхностях (проверено grep по `src/main/res` 2026-08-04) - `fragment_settings_general.xml`, `fragment_settings_playback.xml`, `fragment_settings_streams.xml`, `dialog_add_stream.xml`, `dialog_camera_settings.xml`, `dialog_player_settings.xml`, `dialog_translation_settings.xml`, `dialog_launcher_settings.xml`, `dialog_filter_resource.xml`, плюс их `layout-land`-двойники. Дефект одинаков на всех.

Проект уже держит рабочую альтернативу: `SettingsSelectionRow` плюс `SearchableOptionPickerDialog`. Это настоящий `DialogFragment`, он виден инструментам и навигируется с D-pad через общий `DialogKeyboardDelegate`. В том же экране `fragment_settings_general.xml` соседние строки «Язык» и «Профиль устройства» уже переведены на этот путь (S1190), а сломанная строка «Цветовая тема» осталась на старом.

---

## 3. Исправление

Направление выбрано владельцем 2026-08-04 - кандидат 4 (общий слой, inline-список сохраняется), с откатом на кандидат 3, если проверка на устройстве покажет, что модальное окно всё равно не видно инструментам. См. §3.3.

Кандидаты, от узкого к широкому:

1. Заменить платформенный `AutoCompleteTextView` на `MaterialAutoCompleteTextView` внутри `SettingsDropdownRow`, оставив inline-popup. Закрывает расхождение с Material, но само по себе не делает немодальное popup-окно фокусируемым, то есть скорее всего не снимает ни один из двух симптомов.
2. Перевести только строку «Цветовая тема» на `SettingsSelectionRow` плюс `SearchableOptionPickerDialog`, повторив то, что уже сделано с соседними строками. Проверенный путь, но чинит одну строку из девяти поверхностей.
3. Починить на общем слое: оставить публичный API `SettingsDropdownRow` (`setEntries`, `itemSelectedListener`) без изменений, но заставить сам компонент открывать `SearchableOptionPickerDialog` вместо inline-popup. Все девять мест чинятся без правки места вызова.
4. Починить на общем слое, сохранив inline-вид: `SettingsDropdownRow` перестаёт отдавать нажатие штатному `AutoCompleteTextView.showDropDown()` и сам открывает `ListPopupWindow` с `isModal = true`, привязанное к полю. Модальное окно фокусируемо, поэтому получает системный фокус ввода (D-pad и ENTER работают) и попадает в обход окон, который делают `uiautomator` и accessibility. Публичный API строки не меняется, UX не меняется ни на одной из девяти поверхностей. Прецедент в самом проекте: `PlayerBigButtonsModeManager` (`ui/player/helpers/PlayerBigButtonsModeManager.kt:344-353`) уже показывает меню именно так.

Выбран вариант 4. Он единственный чинит все девять поверхностей, не меняя способ выбора значения. Риск варианта 4 в том, что видимость модального popup для `uiautomator` доказана прецедентом внутри проекта, но не проверкой этого конкретного окна - поэтому в §4 добавлена ранняя проверка на устройстве, и при её провале работа переходит на вариант 3 без нового вопроса владельцу. Вариант 2 отвергнут по прецеденту: S1095 починил один пикер, и за это заплатил S1286, которому пришлось делать то же самое на общем слое.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** РЕШЕНО (владелец, 2026-08-04). Inline-выпадающий список сохраняется на всех девяти поверхностях; чиним общий слой `SettingsDropdownRow`, заменив немодальный popup от `AutoCompleteTextView` на собственное модальное `ListPopupWindow` (вариант 4). Если проверка на устройстве покажет, что модальное окно всё равно не видно `uiautomator` и accessibility, разрешён переход на вариант 3 (`SearchableOptionPickerDialog` из той же строки) без нового согласования - со сменой способа выбора значения на девяти поверхностях.
- **Accessibility:** любой принятый вариант обязан дать D-pad-навигацию по списку и попадание списка в accessibility-дерево - иначе дефект не закрыт.
- **Validation level:** Maestro-флоу, выбирающий значение в строке настроек, плюс ручная проверка D-pad.
- **Related tickets:** S1284 (чёрный скриншот = FLAG_SECURE), S1286 (общий слой searchable-picker), S1190 (перевод соседних строк на `SettingsSelectionRow`).

---

## 4. Проверка

0. Ранняя проверка развилки: на первой же сборке с модальным `ListPopupWindow` открыть список и снять `uiautomator dump`. Узлы элементов есть - вариант 4 подтверждён, идём дальше. Узлов нет - переходим на вариант 3 по §3.3 и переписываем шаги ниже под диалог.
1. Maestro-флоу, который открывает строку настроек с выбором значения, выбирает значение, отличное от текущего, и подтверждает, что значение сменилось. Флоу обязан находить элементы списка по тексту, а не по слепым координатам - именно это сегодня невозможно.
2. `uiautomator dump` при открытом списке содержит узлы элементов списка.
3. D-pad: DPAD_DOWN перемещает выделение по списку, ENTER применяет значение (CLAUDE.md Rule 16).
4. Проверка на второй поверхности из списка девяти - чтобы доказать, что починен компонент, а не одна строка.

Покрытия тестами у `SettingsDropdownRow`, `SettingsSelectionRow` и семейства пикеров сегодня нет ни в `src/test`, ни в `src/androidTest`.

---

## 5. Результат проверки (emulator-5554, API 35, standard-debug `v2.60.8041.533-DEBUG`, 2026-08-04)

Реализован вариант 4. `SettingsDropdownRow` больше не отдаёт нажатие `AutoCompleteTextView`: разметка перешла со стиля `ExposedDropdownMenu` на `OutlinedBox` с кастомной иконкой-стрелкой и read-only полем `sdr_value`, а строка сама открывает `ListPopupWindow` с `isModal = true`.

- Шаг 0 (развилка): при открытом списке `uiautomator dump` содержит узлы всех девяти вариантов темы (`Auto (follow device)`, `Light`, `Dark`, ..). До правки дамп не содержал ни одного. Откат на вариант 3 не потребовался.
- Фокус окна: `dumpsys window | grep mCurrentFocus` -> `Window{.. PopupWindow:..}`, то есть popup действительно забирает системный фокус ввода.
- Шаг 3 (D-pad): первый `DPAD_DOWN` выводит список из touch-режима и подсвечивает текущее значение (`selected="true"` на `Auto (follow device)`), следующие два доводят до `Dark`, `ENTER` применяет - сработал `GeneralSettingsColorThemeHelper` и показал диалог перезапуска. Кнопка «Позже» штатно возвращает прежнее значение, поэтому строка снова показывает `Auto (follow device)` - это поведение хелпера, а не дефект.
- Шаг 4 (вторая поверхность): «Плеер» -> «Режим сортировки по умолчанию»: тап по элементу списка по тексту сменил значение с `Name (A-Z)` на `Name (Z-A)`.
- Шаг 1 (Maestro): добавлен флоу `maestro/smoke/settings_dropdown_select.yaml`, который находит вариант по тексту и проверяет значение строки после выбора. `scripts/devtest/maestro-run.ps1` - PASS.

Сопутствующая находка, вне рамок тикета: `GeneralSettingsColorThemeHelper` показывает диалог перезапуска с текстом `restart_required_message` («Settings have been imported..»), хотя никакого импорта настроек не было. Запаркована отдельным тикетом.

---

## 6. Решения владельца

### Quiz decisions (2026-08-04)

- Какой вариант UI принимаем -> модальное `ListPopupWindow` на общем слое `SettingsDropdownRow`, с откатом на `SearchableOptionPickerDialog` при провале проверки (чинит все девять поверхностей, не меняя способ выбора значения; откат разрешён заранее, чтобы не блокировать работу вторым вопросом).
