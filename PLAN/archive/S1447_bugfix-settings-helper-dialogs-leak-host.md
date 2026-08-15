# Спецификация (compact bugfix): S1447 - Диалоги хелперов настроек удерживают уничтоженный экран

**Ticket:** S1447
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-06
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-08-07 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-06

**Захвачено во время:** S1436

**Текст:**

Settings helper dialogs are shown untracked and leak the host on rotation. 34 MaterialAlertDialogBuilder(..).show() call sites across app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/*.kt keep no reference to the created dialog and no settings helper has a dismiss hook called from the fragment's onDestroyView, so a dialog left on screen during a configuration change keeps the destroyed Fragment/Activity alive. S1197 established the opposite pattern for MainStoragePermissionsHelper (a held rationaleDialog plus dismissPendingDialog() from the host onDestroy); the settings helpers never got it. Found during the S1436 phase-05 boundary audit while adding one more such dialog (OperationsScheduledManager.explainThenOpenBatteryOptimizationScreen) - the new dialog matches the local convention rather than the S1197 one, which is why the fix belongs to the whole family and not to that one call site. Evidence: 34 builder call sites, 6 of the helper files mention dismiss() at all.

---

## 1. Проблема / симптом

Диалог настроек, открытый на экране в момент пересоздания хоста, удерживает уничтоженный `Fragment` и его `Activity` до тех пор, пока пользователь его не закроет.

Наблюдаемое:

- Экран: любой таб настроек (General, Destinations), любой flavor - код лежит в `src/main/`.
- Утечка: окно диалога держит `ContextThemeWrapper` над старым `Activity`; ни `Fragment.onDestroyView`, ни `Activity.onDestroy` его не закрывают.

**Поворот экрана триггером не является** - вопреки формулировке в §0. `SettingsActivity` объявляет `configChanges="orientation|screenSize|keyboardHidden"` (`AndroidManifest.xml:256`), поэтому на повороте она не пересоздаётся и диалог остаётся на живом хосте. Пересоздание вызывают изменения, которых нет в этом списке: смена системной темы (`uiMode`), смена языка приложения (`locale`), смена размера шрифта (`fontScale`), «Не сохранять действия» в параметрах разработчика и смерть процесса. Ровно на этот же список опирается S1331 - тот же класс дефекта на том же экране.

Эвиденс на дереве:

- 34 вызова `MaterialAlertDialogBuilder(..)..show()` в `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/*.kt`, ни один не сохраняет полученный `AlertDialog`.
- Ни один из 14 файлов-хелперов не имеет метода закрытия, вызываемого хостом; `dismiss()` в них относится к другим объектам.
- `MainStoragePermissionsHelper` (S1197) - единственное место с правильной формой: поле `rationaleDialog`, `dismissPendingDialog()` и вызов из `MainActivity.onDestroy` (строка 650).

---

## 2. Корневая причина

`MaterialAlertDialogBuilder.show()` возвращает `AlertDialog`, но возвращаемое значение везде отбрасывается, поэтому закрыть диалог физически некому. Само по себе окно диалога не привязано к жизненному циклу хоста: `Dialog` держит `Context`, переданный билдеру, а это `fragment.requireContext()` - обёртка над `Activity`, которая после `configuration change` уже уничтожена.

Прецедент S1197 закрывает проблему вручную: хелпер держит поле, хост зовёт `dismissPendingDialog()`. Такая форма требует трёх согласованных правок на каждый диалог (поле, метод, вызов из хоста) и поэтому не масштабируется на 34 места - её и не применили ни разу за пределами `MainStoragePermissionsHelper`.

Ключевое наблюдение по дереву: **во всех 34 местах в области видимости уже есть `Fragment`** - как поле конструктора (`private val fragment: Fragment` в 12 хелперах), как `hostContext.fragment` (`GeneralSettingsViewSetupHelper`) или как параметр функции (`DefaultPlayerHelper`, он `object`). Значит `LifecycleOwner` доступен в каждой точке показа, и привязку можно сделать самим показом, без состояния в хелпере и без правок в хостах.

---

## 3. Исправление

Ввести одну функцию-расширение, которая показывает диалог и сама закрывает его на `ON_DESTROY` владельца, и перевести на неё все места показа в семействе хелперов настроек. Хелперы не получают ни полей, ни методов закрытия; хосты не трогаются вовсе.

### Шаг 1 - Расширение `showBoundTo`

**Файлы:** `app_v2/src/main/java/com/sza/fastmediasorter/util/LifecycleDialogExt.kt` (новый, <= 90 LOC)

**Prompt for developer:**

> Создать `LifecycleDialogExt.kt` в `util/` с двумя расширениями `MaterialAlertDialogBuilder`: `showBoundTo(owner: LifecycleOwner): AlertDialog?` и перегрузка `showBoundTo(fragment: Fragment): AlertDialog?`. Основная версия отказывается показывать диалог, если владелец уже в состоянии `DESTROYED` (возврат `null`), иначе вызывает `show()`, регистрирует `DefaultLifecycleObserver`, который в `onDestroy` закрывает диалог, и возвращает диалог. Перегрузка для `Fragment` берёт `viewLifecycleOwnerLiveData.value` и падает обратно на сам фрагмент, когда view уже нет.

**Why:**

Без владельца жизненного цикла закрыть диалог некому - именно отброшенный результат `show()` и есть корневая причина из §2; привязка внутри самого показа снимает необходимость в трёх согласованных правках на каждый диалог, которых форма S1197 требует и из-за которых её не применили ни разу за 34 места.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/util/LifecycleDialogExt.kt` существует.
- `Grep` - `fun AlertDialog.Builder.showBoundTo` встречается ровно дважды.
- `Grep` - `DefaultLifecycleObserver` присутствует в файле.

**Status:** `[x]` done - приёмник объявлен на `AlertDialog.Builder`, а не на `MaterialAlertDialogBuilder`: второй наследует первый, и одна привязка накрывает оба вида билдеров, которые встречаются в семействе (2 места используют голый `AlertDialog.Builder`).

### Шаг 2 - Перевод мест показа в хелперах настроек

**Файлы:** 14 файлов `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/*.kt`

**Prompt for developer:**

> Заменить завершающий `.show()` на `.showBoundTo(fragment)` во всех цепочках `MaterialAlertDialogBuilder` внутри `ui/settings/helpers/`. Владелец берётся из того, что уже есть в области видимости: поле `fragment`, `hostContext.fragment` в `GeneralSettingsViewSetupHelper`, параметр `fragment` в функциях `DefaultPlayerHelper`. Цепочки, чей результат уже используется (`.create()`, присваивание переменной), и `AlertDialog.Builder` в `GeneralSettingsResetHelper` привести к той же форме, а не оставлять как есть. Ничего кроме терминального вызова не менять - заголовки, кнопки и слушатели остаются прежними.

**Why:**

Находка §0 относится ко всему семейству, а не к одному месту: новый диалог в `OperationsScheduledManager` повторил локальную форму просто потому, что она была единственной в файлах вокруг, - пока в этой папке остаётся хоть один голый `.show()`, следующий добавленный диалог скопирует его.

**Verification:**

- `Grep` - `\.show\(\)` в `ui/settings/helpers/*.kt` не встречается ни в одной цепочке `MaterialAlertDialogBuilder` / `AlertDialog.Builder` (совпадения `Toast..show()` и `Snackbar..show()` допустимы).
- `Grep` - `showBoundTo` встречается в 14 файлах хелперов.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done - 36 замен в 14 файлах (34 цепочки `MaterialAlertDialogBuilder` + 2 `AlertDialog.Builder`), скрипт правки лежит в `temp/S1447/apply-showboundto.ps1`, бэкапы - в `temp/S1447/backup/`. `.\a.ps1 fk` - exit 0.

### Шаг 3 - Закрепить форму в архитектурном документе

**Файлы:** `docs/ARCHITECTURE.md`

**Prompt for developer:**

> Дописать в раздел про диалоги одно правило: диалог, показанный из хелпера или менеджера, привязывается к жизненному циклу хоста через `showBoundTo`; голый `.show()` на билдере допустим только внутри `DialogFragment`, который и так управляется `FragmentManager`.

**Why:**

Конвенция, не записанная нигде, воспроизводится обратно при следующем добавлении - ровно так и появился диалог `explainThenOpenBatteryOptimizationScreen` из §0.

**Verification:**

- `Grep` - `showBoundTo` присутствует в `docs/ARCHITECTURE.md`.

**Status:** `[x]` done - новый раздел "Dialog Lifecycle Binding (MANDATORY)" рядом с "Dialog Result Delivery".

### 3.1 Область

Только семейство `ui/settings/helpers/`. Такие же голые показы есть в `ui/browse/managers/` и в плеере - это отдельный тикет вместе с механическим гейтом, чтобы правка не разъехалась на полдерева.

### 3.3 Owner inputs (Approval gate)

- **Видимых изменений UI нет:** ни один диалог не меняет текст, кнопки, положение или условие показа - меняется только то, кто его закрывает. Настройка не добавляется и не переименовывается, поэтому Rule 22 (settings docs sync) не срабатывает.
- **Related tickets:** S1197 - прецедент удержания и закрытия диалога в MainStoragePermissionsHelper; S1436 - тикет, в аудите которого находка обнаружена.

---

## 4. Проверка

- `.\a.ps1 fk` - exit 0.
- `Grep` - в `ui/settings/helpers/*.kt` не осталось ни одной цепочки билдера, оканчивающейся голым `.show()`.
- `post-change.ps1 -ScopeToFile` - `post-change: PASS`.
- На устройстве (отложено, устройства в сессии нет): открыть диалог хелпера настроек и вызвать пересоздание хоста одним из триггеров §1 - диалог закрывается сам, в логе появляется `S1447: owner destroyed, dialog showing=true`.
- На устройстве: пройти по диалогам всех четырнадцати хелперов - подтверждение и отмена работают как раньше, ни один диалог не закрывается сам во время обычной работы.

---

## Last Audit

### Manual device test - 2026-08-11 (RFCR110NBQJ, SM-G996U1, Android 15 / SDK 35, standard debug)

**Сборка:** `2.60.8111.809-DEBUG` (установлена 18:10:53). Процесс pid `11176` - один и тот же от начала до конца прогона, переустановки под руками не было. `animator_duration_scale` = `null` (системная единица, анимации включены), `always_finish_activities` = `null` (не использовалось).

**Положительный контроль (до любого отрицательного утверждения):** канал проб живой - открытие диалога очистки кэша дало `08-11 18:41:59.575 11176 11176 D LifecycleDialogExtKt: S1447: dialog bound to FragmentViewLifecycleOwner (state RESUMED)`.

#### Тест A - сама правка: PASS

- Триггер - смена `fontScale`, не поворот: `SettingsActivity` объявляет `configChanges="orientation|screenSize|keyboardHidden"`, поэтому поворот её не пересоздаёт, а `fontScale` в списке отсутствует и пересоздание вызывает.
- `font_scale` до прогона - `1.0`; на время теста выставлен `1.15`; **восстановлен в `1.0`** и перечитан с устройства.
- Ожидалось: диалог закрывается сам, в логе `S1447: owner destroyed, dialog showing=true`.
- Получено: диалог `Clear Cache` исчез без участия пользователя (0 узлов диалога в дампе после триггера), хост остался `SettingsActivity`, pid не менялся.
- Несущая строка лога: `08-11 18:42:46.363 11176 11176 D LifecycleDialogExtKt$bindTo: S1447: owner destroyed, dialog showing=true` - `showing=true`, то есть диалог был на экране в момент смерти владельца, что и доказывает закрытие вместо утечки.

#### Тест B - отсутствие регрессии: PASS на 12 хелперах из 14

Пройдено по одному связанному диалогу на хелпер; каждый проверялся по четырём признакам - появилась новая строка `dialog bound to` (значит показ прошёл через `showBoundTo`), диалог виден через 1.2 с, диалог всё ещё виден через 3.7 с (сам не закрылся), диалог закрылся по команде. Ни один из одиннадцати не закрылся сам:

- cache - `Clear Cache`, отмена.
- prefetch - `Clear streaming cache`, отмена.
- reset - `Reset General section`, отмена.
- logs - `System information`, отмена.
- view setup - `Restart Application` от переключателя компактных элементов, отмена возвращает строку.
- colour theme - `Restart Application` от смены темы, отмена возвращает спиннер в `Auto (follow device)`; пересоздание не запускалось, потому что оно висит на кнопке подтверждения, а не на показе.
- import/export - `Export settings`, отмена.
- backup - `Export resources to file`, отмена.
- default player - `Set as default`, только отмена: подтверждение выдаёт системную роль приложения по умолчанию, а этого на телефоне владельца делать нельзя.
- destinations - `Remove Destination`, отмена.
- gestures - `Enable screen gestures`, отмена; отдельный прогон с отозванным разрешением, см. ниже.
- scheduled - `Delete this scheduled operation?`, пройдены обе ветки: отмена оставляет операцию, подтверждение удаляет её (тестовая операция заведена и убрана в этом же прогоне, переключатель расписаний возвращён в исходное «выключено»).

**gestures - пройден через отзыв разрешения.** Единственный связанный диалог хелпера показывается только при **отсутствии** разрешения на оверлей (`OperationsGesturesManager.kt:40`), а на устройстве оно выдано. `RFCR110NBQJ` - выделенное тестовое устройство с полномочиями выдавать и отзывать любые разрешения, поэтому разрешение снималось и возвращалось штатно:

- До: `SYSTEM_ALERT_WINDOW: allow; time=+16m38s369ms ago; rejectTime=+44d20h55m53s75ms ago; duration=+40s994ms`.
- После отзыва (`appops set .. deny`) включение строки `Gesture overlay` подняло диалог `Enable screen gestures`; проба - `08-11 19:17:10.159 11176 11176 D LifecycleDialogExtKt: S1447: dialog bound to FragmentViewLifecycleOwner (state RESUMED)`.
- Диалог держался открытым и через 3.7 с, сам не закрылся, закрылся по «Отмена». Строка переключателя вернулась в «выключено» сама - это `setOnDismissListener` хелпера, который снимает галочку, если разрешение так и не выдано, а не самопроизвольное закрытие диалога.
- После восстановления (`appops set .. allow`): `SYSTEM_ALERT_WINDOW: allow; time=+19m30s629ms ago; rejectTime=+44d20h58m45s335ms ago; duration=+40s994ms`. Режим совпадает с исходным; переключатель оставлен выключенным, как и был найден.

Два хелпера **не пройдены**, у каждого ровно один связанный диалог:

- Google account - `confirmSignOut` требует выполненного входа, а `showSignInErrorDialog` - неудачи входа. Настоящие учётные данные в прогоне не используются и подделывать поток авторизации нельзя, поэтому исключение постоянное, а не пробел покрытия.
- profile - диалог висит на `profileImpliesAllFilesUseCase(type) && !ensureAllFilesPredefinedResourceUseCase.exists()`, а предопределённый ресурс `ALL_FILES` на устройстве существует. Самовосстанавливающегося маршрута нет: чтобы диалог вообще появился, ресурс надо удалить **до** показа, а вернуть его умеет только ветка подтверждения (`saveProfile(type, ensureAllFilesResource = true)`); отрицательная кнопка объявлена как `setNegativeButton(R.string.no, null)` и не делает ничего. То есть ровно та ветка, которую требует тест B - отмена, - оставляет ресурс удалённым, и восстановление выходит за пределы самого диалога. Ресурс не трогался.

Отдельно проверено, что показы **вне** семейства `showBoundTo` ведут себя как раньше и в счёт правки не идут: языковой список (`SearchableLanguagePickerDialog`) и палитра цвета (`ColorPickerDialog`) - это `DialogFragment` под управлением `FragmentManager`, а выбор назначения - `ListSelectionDialog` с собственным `lifecycleOwner`. Ни один из них не даёт строки `dialog bound to`, и это ожидаемо: §3 шага 3 прямо оставляет голый показ внутри `DialogFragment`.

**Эвиденс:** `temp/S1447/testA_probe_lines.txt`, `temp/S1447/testA_logcat_full.log`, `temp/S1447/testA_before.xml`, `temp/S1447/testA_after.xml`, `temp/S1447/testB_bind_lines.log` (13 строк `dialog bound to`), инструменты прогона - `temp/S1447/ui.ps1`, `temp/S1447/probe-dialog.ps1`.

**Ловушки прогона, стоившие времени:** счётчик проб через `logcat -d | grep -c 'S1447: dialog bound'` считает и собственную командную строку, которую adb пишет в тот же буфер, поэтому растёт на единицу при каждом вызове даже без единого диалога - считать надо по тегу (`logcat -d -s LifecycleDialogExtKt`). Кнопки у правого и нижнего края в ландшафте (`btnClearAllScheduled`, `btnDelete`) попадают в зону системных жестов: тап по центру такой кнопки уходит в «назад» и выбрасывает приложение из фокуса, поэтому целиться надо во внутренний угол.
