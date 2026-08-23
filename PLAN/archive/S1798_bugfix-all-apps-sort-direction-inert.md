# Спецификация (compact bugfix): S1798 - Направление сортировки списка «Все приложения» не сохраняется

**Ticket:** S1798
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-18
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-18

**Захвачено во время:** S1797

**Текст:**

Launcher all-apps sort direction never persists: KEY_ALL_APPS_SORT_DESCENDING is write-only. In app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt the key `all_apps_sort_descending` is declared (line 235) and written in updateSettings (line 859), but getSettings() has no read site for it - every other launcher key has one. Result: AppSettings.allAppsSortDescending always resolves to its declared default `false`. LauncherAllAppsViewModel (app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAllAppsViewModel.kt) observes it at :43 (`.map { it.allAppsSortDescending }`) and toggles it at :64 (`updateSettings { it.copy(allAppsSortDescending = !it.allAppsSortDescending) }`), so the toggle writes true to DataStore and the next getSettings() emission snaps the UI back to false - the sort direction cannot be changed. Backup/restore round-trips the field (BackupMapper.kt:289/:500, covered by BackupMapperTest) so a restored value is equally inert. Fix is one read line in getSettings() next to allAppsSortOrder, but it changes behaviour, so it stays out of S1797 which is a behaviour-preserving extraction refactor. Found during S1797 while extracting the launcher key group into LauncherSettingsStore.

---

## 1. Проблема / симптом

Переключатель направления сортировки в списке «Все приложения» (launcher) не держит выбранное
значение: после нажатия список возвращается к сортировке по возрастанию.

Эвиденс, снятый при работе над S1797:

- `all_apps_sort_descending` объявлен в `SettingsRepositoryImpl.kt:235` и записывается в
  `updateSettings` (`SettingsRepositoryImpl.kt:859`).
- Ни одной точки чтения этого ключа в `getSettings()` нет - `grep` по `app_v2/src/` даёт только
  объявление, запись и потребителей доменного поля. У всех остальных launcher-ключей точка чтения есть.
- Следствие: `AppSettings.allAppsSortDescending` всегда разрешается в объявленный дефолт `false`
  (`AppSettings.kt:415`).
- `LauncherAllAppsViewModel.kt:43` подписан на поле, `:64` инвертирует его через
  `updateSettings { .. }`, поэтому запись уходит в DataStore, а следующая эмиссия `getSettings()`
  возвращает `false`.
- Резервная копия переносит поле (`BackupMapper.kt:289` и `:500`, тест `BackupMapperTest`), то есть
  восстановленное значение так же не доезжает до UI.

## 1.1 Implementation State

**Ничего не реализовано.** Ни строки исправления в дереве нет.

Единственное упоминание `S1798` в исходниках - перекрёстная ссылка, поставленная в S1797 при выносе
launcher-ключей в `LauncherSettingsStore`: в `write` стоит комментарий о том, что ключ пишется, но
намеренно не читается, и что исправление принадлежит этому тикету. Это пометка о причине, а не след
начатой работы.

Блок записан потому, что проверка дрейфа видит `S1798:` в комментарии и поднимает `DRIFT`, а
`/spec-next` Stage 3 требует объяснить, чем маркеры вызваны. Объяснение такое: маркер описывает
отсутствие исправления. Реализация начинается с нуля.

Расхождение самих проверок дрейфа между собой заведено отдельно как S1800.

---

## 2. Корневая причина

Ключ `all_apps_sort_descending` объявили и добавили в запись, но точку чтения в сборку `AppSettings`
не добавили - классическая пара «объявил и записал, читать забыл». Компилятор такое не ловит:
у `AppSettings.allAppsSortDescending` есть значение по умолчанию, поэтому пропущенный именованный
аргумент - валидный код.

Последствие оказалось шире, чем «настройка не запоминается». `LauncherAllAppsViewModel` не просто
показывает флаг, а подмешивает его в запрос списка: `apps` собирается из `combine(query, order,
descending)`, и `descending` берётся из настроек. Раз настройки всегда возвращали `false`, список
всех приложений сортировался по возрастанию **всегда**.

Переключатель при этом не мог сработать в принципе: `toggleDirection` инвертирует значение,
пришедшее из тех же настроек, то есть каждый раз считает `!false` и записывает `true`, а следующая
эмиссия снова отдаёт `false`. Пользователь не мог получить обратный порядок ни одним числом нажатий.

---

## 3. Исправление

Ключ читается там же, где остальные launcher-ключи - в `LauncherSettingsStore` (куда они переехали
в S1797):

1. Поле `allAppsSortDescending` добавлено в `LauncherSettingsStore.Values`.
2. В `read` добавлена строка чтения с дефолтом `false`, совпадающим с `AppSettings`.
3. В `SettingsRepositoryImpl.getSettings()` значение прокинуто в `AppSettings` рядом с
   `allAppsSortOrder`.
4. Из `write` убран комментарий-объяснение, почему чтения нет: оно теперь есть.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1797 (в его рамках находка обнаружена; там же ключ переезжает в `LauncherSettingsStore`)

---

## 4. Проверка

Новый `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/settings/LauncherSettingsStoreTest.kt`
по образцу `ScreenshotSettingsStoreTest` - методы хранилища чисты над `Preferences`, поэтому
`mutablePreferencesOf()` гоняет тот же путь чтения и записи, что и на устройстве, без Robolectric:

1. `all-apps sort direction round-trips` - точечный тест на сам дефект.
2. `every persisted launcher field round-trips through write then read` - проверяет **все** поля,
   которые пишет хранилище, а не только сломанное. Ключ, который пишут и не читают, невидим ровно до
   тех пор, пока такой проверки нет, поэтому она покрывает весь набор.
3. `absent keys resolve to the documented defaults` - фиксирует дефолты, чтобы вынос из S1797 нельзя
   было незаметно сдвинуть.

Отдельной проверки на устройстве не требуется: неисправность была в слое настроек, а не в UI, и
round-trip доказывается тестом.

---

## 5. Замечание про инвентарь возможностей

Запись `launcher.all-apps-screen` в `docs/ALL_FEATURES.jsonl` (тикет S1401) уже утверждает про
список всех приложений: «..with a reverse-direction entry; the chosen order is remembered between
visits». То есть инвентарь заявлял возможность, которой не было: направление не запоминалось и не
применялось вовсе.

Новой записи в `ALL_FEATURES` тикет не заводит - возможность уже описана, этот тикет её чинит.

---

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Проверено: поле `allAppsSortDescending` в `LauncherSettingsStore.Values`; строка чтения с дефолтом
`false`; проброс значения в `AppSettings` внутри `getSettings()`; сохранённая строка записи; новый
тест-файл; ноль отладочных тегов `Timber.d("S1798:`; запись в `dev/CHANGELOG.md`; отсутствие
несённых открытых вопросов.

Тесты: `LauncherSettingsStoreTest` (3), `SettingsRepositoryImplTest` (6),
`SettingsRepositoryMirrorTest` (2), `BackupMapperTest` (20) - 31 тест, 0 failures, 0 errors.
`post-change` по трём изменённым файлам - PASS без замечаний.

Единственное упоминание `S1798` в исходниках - строка KDoc в тест-файле, отмечающая, какому тикету
принадлежит регрессионный тест. Это принятая здесь форма: `ScreenshotSettingsStoreTest` открывается
точно так же строкой «S1038 Phase 02: ..». Отладочным тегом она не является, и правило про теги её
не касается.

EXEMPT - записи в `docs/ALL_FEATURES.jsonl` не заводится: возможность уже описана записью
`launcher.all-apps-screen` (S1401), которая прямо обещает «a reverse-direction entry; the chosen
order is remembered between visits». Тикет приводит поведение в соответствие с этим обещанием.

### Manual / on-device

- [ ] Отдельной проверки на устройстве не требуется: дефект жил в слое настроек, round-trip доказан
      тестом над тем же путём чтения и записи.
