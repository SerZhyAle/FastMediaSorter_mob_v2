# Стратегическая спецификация: S1161 - Колонки свёрнутых групп настроек в landscape

**Ticket:** S1161
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-24
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-24
**Tactical plan:** `PLAN/S1161_landscape-settings-collapsed-groups-columns/INDEX.md`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-24

**Текст:**

Рассмотреть возможно в ландшафтном виде группированные (неразвернутые) группы настроекв окне настроек сворачивались в дые колонки

---

## 1. Проблема

В альбомной ориентации окно настроек шире, чем нужно одной группе, а группы всё равно идут одна под другой во всю ширину. Свёрнутая группа - это одна строка заголовка, и такая строка занимает всю ширину экрана ради заголовка и стрелки. В итоге в альбомном виде на экран помещается меньше групп, чем в книжном, хотя места больше.

---

## 2. Цели

1. В альбомной ориентации свёрнутые группы настроек занимают две колонки, а не одну.
2. Разворачивание и сворачивание группы продолжает работать и сохраняться между запусками.
3. Поворот экрана при открытых настройках перестраивает раскладку без перезапуска экрана.

**Non-goals:**

- Книжная ориентация - там ширины на две колонки нет.
- Перекладка строк внутри групп; у части групп своя раскладка по колонкам уже есть и она не трогается.
- Изменение состава групп, их порядка и названий.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Запрос был сформулирован как «рассмотреть возможность». Разведка оценила цену, владелец выбрал форму 2026-07-24 - см. §6 и ADR-2; делать решено.

### 3.2 Жёсткие ограничения

- **Flavor:** экран настроек общий; вкладки-расширения отдельных сборок подчиняются тому же правилу.
- **API level:** без API-специфики.
- **Wear OS:** вкладка синхронизации с часами - такая же вкладка настроек и правилу подчиняется; сам модуль часов не затрагивается.
- **Производительность:** перестроение раскладки допускается при повороте и при смене состояния группы, но не в прокрутке.
- **Совместимость данных:** состояние свёрнутости хранится по-прежнему; новых ключей и миграций нет.
- **Локализация:** новых строк нет. Заголовки становятся вдвое уже - нужен запас на длинные русские и украинские варианты (приложение поставляется на EN, RU, UK).
- **Доступность:** порядок обхода с клавиатуры и D-pad должен остаться предсказуемым - две колонки не должны перепутать последовательность групп.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0999, S0693

---

## 4. Контекст текущей архитектуры

Экран настроек - это набор вкладок-фрагментов. Внутри каждой вкладки группы не собираются кодом и не приходят из адаптера: каждая группа руками объявлена в разметке как отдельная карточка во всю ширину, а карточки сложены в один вертикальный стек внутри прокрутки. Разметок две - книжная и альбомная - и обе перечисляют те же карточки поимённо.

Свёрнутость хранится в отдельном хранилище по ключу «экран плюс группа»; менеджер сворачивания просто прячет и показывает содержимое карточки, анимируя переход.

Многоколоночность в настройках уже есть, но другого масштаба: она раскладывает строки внутри одной группы, а не сами группы. Число колонок при этом берётся из ресурса, разного для книжной и альбомной ориентации, а перестроение вызывается на смену конфигурации.

Важная особенность: экран настроек объявлен как переживающий поворот сам, а вкладки создаются один раз. Разметка альбомной ориентации при повороте на живом экране повторно не применяется - поэтому любая раскладка по колонкам должна пересобираться кодом на смену конфигурации, а не полагаться на альбомную разметку.

---

## 5. Предлагаемый подход

Раскладку задаёт код, а не альбомная разметка: карточки групп распределяются по двум колонкам во время настройки вкладки и пересобираются на поворот. Число колонок берётся из ресурса, как уже сделано для строк внутри групп.

### 5.1 Основные столпы / модули

- Распределение карточек по колонкам - общее для всех вкладок настроек, а не своё в каждой. Свёрнутая карточка занимает одну колонку, развёрнутая - все.
- Пересборка на смену конфигурации и на смену состояния группы - в той же точке, где вкладки уже реагируют на поворот.

**Порядок при смешанном списке.** Развёрнутая карточка во всю ширину разрывает колонки, поэтому «по столбцам» (ADR-3) применяется не ко всему списку сразу, а к каждой непрерывной группе свёрнутых карточек между развёрнутыми. Внутри такой группы левая колонка заполняется сверху вниз первой и при нечётном числе длиннее на одну карточку - ровно как уже работает раскладка строк внутри группы. Другого прочтения ADR-2 и ADR-3 вместе не существует: сквозной обход по столбцам через полноширинный разрыв не определён.

### 5.2 Потоки данных и событий

Вкладка настроена -> карточки групп распределяются по колонкам по ширине -> поворот или смена состояния группы вызывает пересборку -> состояние свёрнутости берётся из прежнего хранилища и не меняется.

### 5.3 Точки расширяемости

- Правило распределения одно на все вкладки, включая вкладки-расширения сборок.
- Число колонок остаётся ресурсом, чтобы широкий экран мог получить больше двух.

---

## 6. Открытые вопросы / Research items

Запрос был «рассмотреть возможность», и разведка 2026-07-24 её оценила: возможность есть, но дороже, чем выглядит. Форму выбрал владелец в тот же день, открытых вопросов не осталось.

**Что выяснилось.** Группы - не элементы списка, а руками написанные карточки в вертикальном стеке, и таких стеков столько, сколько вкладок настроек, в двух разметках каждый. Готового способа «сказать элементу занимать одну колонку из двух» здесь нет: карточки придётся перекладывать кодом в колонки-контейнеры. Существующая многоколоночность настроек делает ровно это, но со строками внутри одной группы, а не с самими группами.

1. **Решён.** Что происходит при разворачивании группы -> буквально по запросу: свёрнутые группы идут в две колонки, развёрнутая занимает всю ширину. См. ADR-2.
2. **Решён.** Порядок обхода -> по столбцам, вслед за существующей многоколоночной раскладкой настроек. См. ADR-3; владельцу не задавалось.

**Снято исследованием, владельцу не задавалось:** раскладку нельзя отдать альбомной разметке - экран переживает поворот сам, вкладки создаются один раз, и альбомная разметка на живом повороте не применяется; пересобирать нужно кодом на смену конфигурации, как уже делают соседние места. Число колонок берётся из ресурса, а не зашивается. Порядок обхода - по столбцам: многоколоночная раскладка строк внутри группы уже разложена по столбцам, и второй порядок в соседнем месте того же экрана путал бы сильнее любого из двух вариантов по отдельности.

### Quiz decisions (2026-07-24)

- Что происходит при разворачивании группы? -> Свёрнутые в две колонки, развёрнутая во всю ширину. Развёрнутая группа сохраняет полную ширину, поэтому её строки не сжимаются и существующие альбомные раскладки внутри групп остаются целы; перестановка карточек при развороте принята как цена.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Заголовок группы не помещается в половину ширины | Высокая | Заголовок переносится или обрезается | У заголовка сегодня нет ограничения на число строк; его нужно задать вместе с раскладкой и проверить на длинных переводах |
| Перекладка карточек ломает анимацию сворачивания | Средняя | Мигание при разворачивании | Анимация привязана к прежнему родителю карточки; при смене родителя её нужно перезапускать явно |
| Правило разъедется по вкладкам | Средняя | На части вкладок колонок нет | Распределение общее, а не скопированное в каждую вкладку |
| Порядок обхода с клавиатуры перепутался | Средняя | Навигация скачет между колонками | Порядок по столбцам зафиксирован в ADR-3; проверяется D-pad-обходом |
| Карточки прыгают при каждом развороте | Высокая | Место нажатия уезжает из-под пальца | Принято в ADR-2, но перестановка должна происходить одним переходом вместе с анимацией сворачивания, а не двумя рывками; развёрнутая карточка остаётся на виду после перестановки |
| Вкладки-расширения сборок остались одноколоночными | Средняя | Поведение зависит от сборки | Общая точка распределения применяется и к ним |

---

## 8. Влияние на пользователя (docs/FEATURES)

В альбомной ориентации группы настроек раскладываются в две колонки, и на экран помещается вдвое больше.

---

## 9. Архитектурные решения (ADR)

**ADR-1. Раскладка задаётся кодом, а не альбомной разметкой.**

Экран настроек объявлен переживающим поворот, а вкладки создаются один раз, поэтому альбомная разметка при повороте на живом экране не применяется. Колонки, отданные разметке, появлялись бы только при открытии настроек уже в альбомной ориентации - то есть через раз. Соседние места экрана настроек по той же причине пересобирают раскладку кодом.

**ADR-2. Свёрнутые группы идут в две колонки, развёрнутая занимает всю ширину.**

Решение владельца 2026-07-24. Разворачивание - намеренное действие, и перестановка карточек в этот момент приемлема. Обратный вариант, где карточка навсегда закреплена за колонкой, оставил бы развёрнутую группу половинной ширины: её строки, слайдеры и уже существующие внутригрупповые раскладки по колонкам пришлось бы пересчитывать под вдвое меньшую ширину. Полная ширина у развёрнутой группы защищает всё, что уже сделано внутри групп.

**ADR-3. Порядок - по столбцам.**

Многоколоночная раскладка строк внутри группы, которая в настройках уже работает, раскладывает по столбцам. Второй порядок на том же экране, но уровнем выше, сбивал бы и чтение, и обход с клавиатуры. Владельцу вопрос не задавался - выбор задан существующим кодом.

---

## 10. Связи с другими спеками

- S0999 - колонки строк внутри группы и пересборка на поворот; та же механика на уровень ниже.
- S0693 - признак широкой раскладки; тот же экран настроек.

---

## 11. Критерии готовности (strategic-level)

1. В альбомной ориентации свёрнутые группы настроек занимают две колонки, а развёрнутая - всю ширину.
2. Развёрнутая группа выглядит внутри так же, как до изменения: её строки не сжаты.
3. В книжной ориентации раскладка не меняется.
4. Поворот при открытых настройках перестраивает колонки без перезапуска экрана.
5. Сворачивание и разворачивание работает и переживает перезапуск приложения.
6. Заголовок группы читается целиком на всех трёх языках.
7. Обход с клавиатуры и D-pad идёт по столбцам.
8. Правило действует на всех вкладках настроек, включая вкладки отдельных сборок.

---

## Last Audit

### Исправление проверки (1) - 2026-07-25

Аудит назвал причину точно: `SettingsGroupColumnsManager.install` вызывается только из `BaseSettingsFragment.onViewCreated`, а из четырёх вкладок верхнего уровня от этого класса наследовалась одна - Management. Три остальные (`General`, `Media`, `Playback`) наследовались напрямую от `Fragment`, поэтому сетку не получали.

Исправлено переводом трёх вкладок на `BaseSettingsFragment`. Это сохраняет требование §5.1 - одна общая точка распределения, а не скопированный в каждую вкладку вызов - и автоматически даёт им же `onConfigurationChanged` с пересборкой сетки.

Сопутствующее:

- В `PlaybackSettingsFragment` удалено собственное поле `isUpdatingFromSettings`: оно перекрывало одноимённое из базового класса. Семантика не менялась - тот же флаг подавления обратной связи.
- Из `General` и `Playback` убран прямой вызов `SettingsRowStackManager::stackNarrowPortraitRows`: базовый класс делает это сам, причём **до** переноса карточек в сетку. Оставшийся дубль отработал бы уже по перевешенному дереву.

Не закрыто этим изменением: проверка (6) - анимация перекладки одним движением. На AVD окно настроек снимается чёрным кадром, так что подтвердить её можно только на реальном аппарате.

### Manual device test - 2026-07-26 (emulator-5554, standard debug) - re-test after the check-(1) fix

Device: `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424 @ 420dpi (411dp portrait), package
`com.sza.fastmediasorter.debug`. Nothing was rebuilt or reinstalled: the installed APK
(`versionName=2.60.7220.314-DEBUG`, installed 2026-07-26 17:53:33) postdates every source file in this
ticket's change set (`BaseSettingsFragment.kt` 23:11 on 07-25, `MediaSettingsFragment.kt` 23:05 on 07-25,
`PlaybackSettingsFragment.kt` 23:06 on 07-25, `GeneralSettingsFragment.kt` 11:00 on 07-26), so it carries the
fix. The fast debug target does not bump `versionName`, which is why the string repeats the 2026-07-24 value.

Method unchanged from 2026-07-24: the settings window still captures as an all-black frame on this AVD, so
layout is read from `uiautomator dump` bounds. Evidence: `temp/S1161/mobile_test_scenario_20260726_2014.md`
plus the `run2_*` dumps and reports in `temp/S1161/`. One AVD note worth keeping: `user_rotation` is reset to
0 while `MainActivity` is foreground, so landscape must be applied after `SettingsActivity` is on top.

- (1) Collapsed groups pair up two per row on **every** settings tab: **PASS** - this is the check that
  failed on 2026-07-24. Expected two columns on General, Media, Player and Management in landscape; all four
  now deliver it. General with every group collapsed is the cleanest case: eight cards, no full-width card
  left, left column `x=[16,1196]` (Interface, MainWindowInterface, FileBrowser, RemoteSources) against right
  column `x=[1228,2408]` (Authorization, AppData, System). Management gives five full rows of two
  (CopyMove|CameraPhotos, Safety|VideoCapture, Destinations|MicRecording, Scheduled|ScreenRecording,
  Behaviour|AdditionalPrograms). Media and Player likewise. The probe confirms the call site rather than the
  symptom: `GeneralSettingsFragment`, `MediaSettingsFragment`, `PlaybackSettingsFragment` and
  `OperationsSettingsFragment` all log `groups grid installed=true`.
- (8) Rule applies on every tab: **PASS** by the same evidence. `installed=false` is now logged only by
  `VideoSettingsFragment`, `AudioSettingsFragment`, `StreamsSettingsFragment` and
  `DocumentsSettingsFragment` - section bodies below `MIN_GROUP_CARDS`, correctly skipped, not misses.
  Flavor-only tabs were not exercised directly (standard flavor), but coverage now follows from the install
  call site living in `BaseSettingsFragment`, which every settings fragment extends.
- (2) Expanded group spans the full width: **PASS**. Held on each tab that had an expanded card -
  `headerSystemApps` `x=[11,2413]` on Management, `headerBackgroundAudio` on Player, `headerStreams` on
  Media - while the collapsed cards around it stayed in their columns.
- (3) Portrait layout unchanged: **PASS**. Management (11 headers) and Media (6 headers) are single column at
  `x=[11,1069]`, original order preserved.
- (4) Rotation with settings open re-flows without a restart: **PASS**. Landscape -> portrait kept the same
  `ActivityRecord{230be8c}` and collapsed two columns into one.
- Regression named in the status note - the move to `BaseSettingsFragment` must not disturb the fragments
  themselves: **PASS**. `fragment_settings_documents.xml` is the only settings layout with a weighted
  horizontal `SettingsToggleRow` pair, and in a freshly-opened portrait Settings its rows are full width and
  sequential (`rowSupportText` y=[925,1062], `rowShowTextLineNumbers` y=[1062,1188], both w=1016) - still
  stacked. `General` and `Playback`, which lost their direct `stackNarrowPortraitRows` call, contain no
  weighted toggle pair at all, so for them the removal is a no-op by construction.
- (6) Re-flow animates as one motion: **UNVERIFIED**, unchanged from 2026-07-24. The settings window captures
  black on this AVD, so neither a recording nor a mid-flight dump can sample the transition. Needs a real
  device; this is the only check still outstanding.

Log: 4.5 MB / 22225 lines, 0 exceptions, 0 app-tagged `E/` lines (only unrelated
`E/AppOps: op=SYSTEM_ALERT_WINDOW` system noise).

Checks (5) collapse state surviving restart and (7) D-pad traversal were confirmed on 2026-07-24 and not
re-run - the fix moves the install call site and does not touch the grid itself.

Second logging-tooling papercut, alongside the `-Grep` gap noted on 2026-07-24: `scripts/utils/search-log.ps1`
parses 0 structured lines from a `-v time` capture ("Loaded 22225 raw lines 0 structured"), so error scanning
had to fall back to raw grep. It appears to want `-v threadtime`.

Status and debug tags left untouched.

### Manual device test - 2026-07-24 (emulator-5554, standard debug)

Device: `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424 @ 420dpi, package `com.sza.fastmediasorter.debug`. Orientation forced via `settings put system user_rotation 0|1`; layout read from `uiautomator dump` bounds (screencap of the SettingsActivity window returns an all-black PNG on this AVD, so pixel screenshots are not usable evidence here). Evidence: `temp/S1161/` (`land_mgmt_collapsed_all.xml`, `ui_land_mgmt_expanded.xml`, `ui_land_mgmt_scrolled.xml`, `ui_port_mgmt_after_rotate.xml`, `port_mgmt_after_restart.xml`, `land_media_tab.xml`, `land_player_tab.xml`, `ui_land_fresh.xml`, `probe_lines.txt`).

Build note: the installed APK reports `versionName=2.60.7220.314-DEBUG` (not the `2.60.7241.433-DEBUG` named in the test request), but a dex scan of the matching artefact confirms it carries this ticket's code - `SettingsGroupsGridLayout`, `SettingsGroupColumnsManager` and the literal `S1161: ` are present in `classes3/7/16.dex`. Nothing was rebuilt or reinstalled for this run.

- (1) Collapsed groups pair up two per row on **every** settings tab: **FAIL**. Only the Management tab does. Expected: two columns on General, Media, Player, Management in landscape. Actual: Management is two-column (left column `x=[16,1196]`, right column `x=[1223,2413]`, twelve headers in six rows); General, Media and Player stay one column, every header spanning `x=[11,2413]`. Cause is the install call site: `SettingsGroupColumnsManager.install` is invoked only from `BaseSettingsFragment.onViewCreated`, and `GeneralSettingsFragment`, `MediaSettingsFragment` and `PlaybackSettingsFragment` extend `androidx.fragment.app.Fragment` directly - only `OperationsSettingsFragment` (Management) among the four `SettingsPagerAdapter` tabs extends `BaseSettingsFragment`. The probe confirms it: no `S1161:` line is emitted for the three unconverted tabs, while Management logs `S1161: OperationsSettingsFragment groups grid installed=true`. (`StreamsSettingsFragment` logs `installed=false`, which is correct - it is a section body with no group cards, caught by the `MIN_GROUP_CARDS` guard, not a miss.)
- (2) Expanded group spans the full width: **PASS**. Expanding "Copy, move and overwrite behavior" on Management in landscape gives `headerCopyMove` `x=[16,2408]` (full width) while the collapsed cards below it re-flow into the two columns.
- (3) Rotation with settings open re-flows without losing scroll: **PASS**. Scrolled Management down in landscape (top visible row `Scheduled` / `AdditionalPrograms`), rotated to portrait: same `ActivityRecord` (`fe214`) so the activity was not recreated, layout collapsed to one column (`x=[11,1069]`), and the view stayed scrolled - the expanded `CopyMove` card and its body remained above the viewport rather than the list snapping back to the top. Scroll is preserved as a pixel offset, so the exact anchor row shifts when the content height changes; it does not reset.
- (4) Collapse state survives an app restart: **PASS**. Left `CopyMove` expanded, `force-stop` + relaunch, re-opened Settings -> Management: `CopyMove` at `y=[342,469]` with the next header `Safety` at `y=1137` (668 px body gap) - still expanded, every other group still collapsed.
- (5) D-pad traversal runs down the left column first: **PASS**. From the top of Management in landscape, `DPAD_DOWN` visits CopyMove -> Safety -> Destinations -> Scheduled -> Behaviour -> Photography - the entire left column top-to-bottom in original order - before leaving the grid for the full-width rows below. The right column is not orphaned: entering the tab with `DPAD_DOWN` from the "Management" tab chip lands on "Video recording", the top of the right column.
- (6) Re-flow animates as one motion with the collapse animation: **UNVERIFIED**. Not observable on this AVD - the SettingsActivity window captures as an all-black frame, so neither `screencap` nor a screen recording can show the transition, and a `uiautomator` dump raced against the tap is too slow to sample mid-flight bounds. Code-level only: `SettingsGroupColumnsManager` installs a `LayoutTransition` with `CHANGING` enabled on the grid. Needs a real device.

Overall: 4 of 6 checks PASS, check (1) FAILS on three of the four top-level tabs, check (6) unverified on emulator. The grid itself behaves per ADR-2/ADR-3 wherever it is installed; the gap is coverage of the install call site (strategic criterion 8). Status and debug tags left untouched.

Tooling note: `scripts/devtest/adb.ps1 log -Grep` cannot see these probes - it pre-filters logcat to lines containing the package name, and Timber tags the line `BaseSettingsFragment` with no package prefix, so `-Grep 'S1161'` returns 0 matches while the raw capture file it writes does contain them.

---

## Revision History

- **2026-07-26** - by `/spec-test-device` (`claude-opus-5[1m]`, device: emulator-5554, Android 15 / SDK 35)
  - Scenario: `temp/S1161/mobile_test_scenario_20260726_2014.md` · re-test after the check-(1) fix ·
    PASS/FAIL/UNVERIFIED 6/0/1 · errors in log: 0 · status and debug tags untouched.
