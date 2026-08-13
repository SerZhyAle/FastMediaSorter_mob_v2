# Стратегическая спецификация: S1315 - Browse обновляет содержимое после изменения показа подпапок

**Ticket:** S1315
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-30
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - сообщение владельца 2026-07-30
**Tactical spec:** `PLAN/S1315_reload-browse-after-subfolder-setting/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user 2026-07-31 - вызов `/spec-tech S1315` санкционирует переход к тактическому плану и реализации.
- **Local anchor:** Provided by user - после изменения «показывать подпапки как элементы» и возврата из редактора Browse показывает пустое состояние до ручного обновления.
- **Scope boundaries / forbidden areas:** Delegated by user - только синхронизация уже открытого Browse с сохранёнными настройками ресурса; без изменения поведения ручного обновления и сканирования.
- **Done / success signal:** Provided by user - после сохранения настройки и возврата из редактора список сразу показывает файлы и подпапки без ручного обновления.
- **Autonomy rule:** Provided by user 2026-07-31 - тем же вызовом; расширение объёма за пределы одной настройки паркуется отдельным тикетом, а не решается по ходу.
- **UI decisions / delegation:** N/A - новых элементов интерфейса и новых текстов нет.

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

Когда пользователь открывает ресурс с большим числом файлов, переходит в его редактор и меняет настройку показа подпапок как отдельных элементов, при возврате Browse может показать пустое состояние. Текст пустого состояния сообщает, что проверка была выполнена дважды, хотя данные ресурса доступны и появляются после ручного обновления.

Причина подтверждена: возврат из редактора проверяет не все настройки, влияющие на форму списка. Изменение показа подпапок не запускает сброс устаревшего кэша и повторную загрузку.

## 2. Цели

1. После сохранения настройки показа подпапок открытый Browse обнаруживает изменение и обновляет список автоматически.
2. Автоматическое обновление использует тот же надёжный путь, что и ручное обновление: актуальные настройки, сброс устаревших данных и повторная загрузка.
3. Сценарий не создаёт повторных загрузок, когда настройка фактически не менялась.

**Non-goals:**

- Изменение дизайна редактора ресурса или пустого состояния.
- Пересмотр логики ручного обновления.
- Расширение проверки на несвязанные настройки без отдельного исследования.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- После сохранения настройки пользователь не должен нажимать «Обновить» для возврата файлов.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты, использующие Browse и редактор ресурсов.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** повторная загрузка допускается только после фактической смены настройки.
- **Совместимость данных:** без миграции данных; используется уже сохранённая настройка ресурса.
- **Локализация:** новых строк нет.
- **Доступность:** без изменений UI.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1311 (`browseloadingmanager-detekt-debt`) - сворачивает аргументы
  `sortMode / sizeFilter / showHiddenFiles / currentPath / isSubfolderMode` в объект-значение на том же
  пути загрузки, который переиспользует этот тикет. Правки не пересекаются - S1315 меняет
  `BrowseStateSyncManager`, S1311 меняет `BrowseResourceLoadManager` и `BrowseLoadingManager` - но
  порядок слияния имеет значение. Утверждение §10 «связей нет» этим отменяется.
- **Scope:** только поле `showSubfoldersAsItems`, и в обоих местах, где определено «структурное
  изменение»: `BrowseStateSyncManager.checkAndReloadIfResourceChanged` и
  `ResourceEditorUseCase` (сброс кэша при сохранении). Остальные поля из таблицы §5.4 - отдельный
  тикет, как того и требует §2 Non-goals.
- **Flavor:** все - затронутый код лежит в `src/main`, флейвор-гейтов на пути нет.
- **Локализация:** новых строк нет; текст пустого состояния не меняется.
- **Room:** без изменений схемы - колонка `showSubfoldersAsItems` существует с миграции в
  `AppDatabase.kt:349`.
- **Done signal:** проверка на устройстве по метке `S1315:` в logcat плюс критерии §11.

## 4. Контекст текущей архитектуры

Browse держит текущее состояние ресурса и при возврате из дочернего экрана сверяет его с сохранённой версией. При обнаружении изменений он очищает кэш списка, обновляет состояние ресурса и запускает повторную загрузку.

Сейчас проверка охватывает фильтр типов и рекурсивное сканирование, но не охватывает отображение подпапок как элементов. Поэтому UI продолжает использовать состояние, несовместимое с сохранённой конфигурацией.

### 4.1 Замер 2026-07-31: какой именно вход в редактор даёт дефект

Проверка §4 подтвердилась дословно: в `BrowseStateSyncManager.kt:87-88` сравниваются ровно два поля,
`supportedMediaTypes` и `scanSubdirectories`; `showSubfoldersAsItems` среди них нет.

Но существенно, что дефект даёт **не всякий** путь в редактор. Входов пять, и они ведут себя по-разному:

- Из самого Browse (`BrowseManagerInitializer.kt:988`) редактор открывается через
  `editResourceLauncher`, и по `RESULT_OK` срабатывает `BrowseActivity.kt:241-243` -
  безусловная полная перезагрузка. По этому пути настройка подхватывается и дефекта нет.
- С экрана Main (`MainActivity.kt:468`, `:909`, `:917`, `MainEventHandler.kt:78`,
  `ResourcePasswordManager.kt:80`) редактор открывается обычным `startActivity(..)`, без
  `registerForActivityResult`. Результат в Browse не возвращается вообще, поэтому при
  возобновлении Browse из стека отрабатывает только сверка на `onResume` - та самая неполная.

Отсюда и пустой список: `BrowseObserverManager.kt:202` показывает «Здесь пусто. Мы проверили дважды»
всегда, когда набор пуст, а у ресурса, все файлы которого лежат в подпапках, протухший режим подпапок
даёт ровно ноль элементов.

Вывод для реализации: правка в `BrowseStateSyncManager` закрывает именно тот путь, на котором дефект
воспроизводится, и не трогает уже работающий путь из Browse.

## 5. Предлагаемый подход

Расширить перечень настроек ресурса, которые считаются изменениями структуры списка. Изменение показа подпапок должно проходить существующий путь синхронизации при возврате из редактора.

### 5.1 Основные столпы / модули

- Сопоставление сохранённой и открытой конфигурации ресурса включает все параметры, меняющие набор или форму отображаемых элементов.
- При расхождении применяется единый существующий путь обновления списка.

### 5.2 Потоки данных и событий

Редактор сохраняет настройку -> пользователь возвращается в Browse -> Browse получает актуальную конфигурацию -> обнаруживает структурное изменение -> очищает устаревший кэш -> загружает и показывает актуальный список.

### 5.3 Точки расширяемости

- Тактический этап должен зафиксировать, какие ещё настройки ресурса влияют на структуру списка, чтобы последующие изменения не пропускались молча.

### 5.4 Перечень структурных настроек, замер 2026-07-31

Требование §5.3 выполнено здесь. Источник истины - `ResourceEditorUseCase.buildPersistenceModel`:
это ровно то, что редактор записывает в ресурс. Столбец «покрыто» означает присутствие поля в
сравнении `BrowseStateSyncManager.kt:87-88`.

| Настройка | Что меняет | Покрыто сегодня |
|---|---|---|
| `supportedMediaTypes` | набор | да |
| `scanSubdirectories` | набор (глубина) | да |
| `showSubfoldersAsItems` | набор и форму | **нет - предмет этого тикета** |
| `allFiles` | набор (перекрывает фильтр типов) | нет |
| `showHiddenFiles` | набор | нет |
| `sortMode` | форму (порядок) | нет |
| `displayMode` | форму (список или сетка) | нет |
| `disableThumbnails` | форму (миниатюра или иконка) | нет |
| `rememberFileList` | источник данных | нет |
| `path`, `type`, `credentialsId`, `cloudProvider`, `cloudFolderId` | набор, но смена делает ресурс другим | нет |

Определение «структурного изменения» продублировано в двух местах и неполно в обоих: второе - сброс
кэша при сохранении в `ResourceEditorUseCase`, где отсутствуют те же `showSubfoldersAsItems`,
`allFiles` и `showHiddenFiles`. Это и есть механизм риска из §7 «повторится для другой настройки»:
пока перечень живёт двумя списками, любой новый пропуск бесшумен. Сведение обоих списков в один
общий предикат - отдельная работа, вынесенная из этого тикета по §2 Non-goals.

## 6. Открытые вопросы / Research items

1. **Полнота перечня настроек**
   - **Вопрос:** есть ли другие сохранённые настройки ресурса, которые меняют набор или представление элементов и должны участвовать в той же проверке?
   - **Ответ:** да, восемь - перечислены в §5.4 с указанием, что каждая меняет. В объём этого тикета они не входят: §2 Non-goals прямо исключает расширение проверки на другие настройки, а §5.3 требовал их зафиксировать, а не починить. Фиксация выполнена.
   - **Статус:** Resolved 2026-07-31.

2. **Автономия реализации**
   - **Вопрос:** разрешён ли переход к тактическому плану и реализации сразу после ответа на первый вопрос?
   - **Ответ:** да - владелец санкционировал вызовом `/spec-tech S1315` 2026-07-31.
   - **Статус:** Resolved 2026-07-31.

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Сравнение охватит не все структурные настройки | Средняя | Аналогичный пустой список повторится для другой настройки | Проверить все настройки редактора, влияющие на загрузку и представление |
| Обновление сработает без реального изменения | Низкая | Лишний перескан большого ресурса | Сравнивать предыдущее и сохранённое значения перед очисткой кэша |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES: это исправление уже существующего поведения списка.

## 9. Архитектурные решения (ADR)

**ADR-1: Структурные настройки запускают единый путь синхронизации Browse**

- **Решение:** не добавлять отдельный обход для подпапок, а включить настройку в существующую проверку структурных изменений.
- **Альтернативы:** принудительно обновлять Browse после каждого возврата из редактора.
- **Почему:** обновление только при фактическом изменении сохраняет производительность на больших ресурсах и не дублирует существующую логику.

## 10. Связи с другими спеками

Связей нет.

## 11. Критерии готовности (strategic-level)

1. После смены показа подпапок как элементов и возврата из редактора Browse показывает актуальные файлы и подпапки без ручного обновления.
2. При возврате без изменения настройки повторная загрузка не запускается.
3. Ручное обновление сохраняет текущее поведение.

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S1315` - создаст `PLAN/S1315_reload-browse-after-subfolder-setting/` с фазами.

---

## Last Audit

**Date:** 2026-08-11
**Mode:** device test (`/spec-test-device`)
**Flags:** real hardware, no rebuild
**Outcome:** PASS - both acceptance legs
**Counts:** PASS 2 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

**Device:** Samsung Galaxy S21+ `RFCR110NBQJ` (SM-G996U1, Android 15 / SDK 35, 1080x2400 @450dpi),
landscape. Package `com.sza.fastmediasorter.debug`, versionName `2.60.8111.809-DEBUG`,
`lastUpdateTime=2026-08-11 18:10:53`. Nothing was rebuilt or reinstalled for this run.
App pid **11176** held constant across every measurement window; the foreground activity was
re-checked immediately before each observation. `BrowseActivity` instance `bde7451` is the same
record before and after both legs, so Browse was **resumed**, never recreated.

### Fixture

`/storage/emulated/0/Download/S1315_SubOnly` - two subfolders `A` (2 files) and `B` (3 files),
**zero** loose files at the resource root (`find -maxdepth 1 -type f` returns nothing). Added as
LOCAL resource id **9** with `scanSubdirectories=true`, so the initial list is the 5 files pulled
out of the subfolders. Proof: `temp/S1315/fixture-proof.txt`.

### Path selection - why the stack shape matters

`BrowseActivity`'s own Back button **finishes** Browse, so that route leaves nothing to resume and
cannot exercise §4.1. The path where the defect lives needs Main sitting *above* a live Browse, and
the genuine user route to it is HOME followed by tapping the app icon: the launcher intent stacks a
second `MainActivity` on top, giving `Main -> Browse(alive) -> Main`. Both legs were driven through
that stack, editing the resource from the upper Main via `more_options -> Edit` - i.e. the plain
`startActivity` route of §4.1, with no result delivered back to Browse.

### Leg A - positive: toggle the setting, return to Browse

- **Expected:** the list refreshes itself with no manual pull; probe reports `show=true`.
- **Actual:** PASS. `cbShowSubfoldersAsItems` flipped `false -> true`, saved, BACK to Browse:

  `S1315: resume diff types=false scan=false show=true`
  `checkAndReloadIfResourceChanged: settings changed, reloading`
  `BrowseLoadingManager: START loading - resource='S1315_SubOnly' (id=9), type=LOCAL, showHiddenFiles=true, currentPath=null, isSubfolderMode=true`

  The probe isolates the cause: only the third flag moved, so the reload was driven by
  `showSubfoldersAsItems` alone and by nothing else. A reload genuinely **ran** rather than the list
  merely looking different - the loading pipeline re-entered with `isSubfolderMode=true`, and the
  header went from `S1315_SubOnly (5 files)` listing `a1.jpg a2.png b1.jpg b2.jpg b3.jpg` to
  `S1315_SubOnly (2 files)` listing the subfolder items `A` and `B`. No refresh gesture was issued.
  Evidence: `temp/S1315/legA_positive_toggle.log`.

### Leg B - negative: enter the editor, change nothing, return to Browse

- **Expected:** no reload; probe reports `types=false scan=false show=false`.
- **Actual:** PASS. Editor opened from the upper Main and dismissed without touching a control:

  `S1315: resume diff types=false scan=false show=false`
  `checkAndReloadIfResourceChanged: no settings change; Reconciler owns cache sync`

  In the same window there is **no** `settings changed, reloading`, **no**
  `BrowseLoadingManager: START loading` and **no** `LocalMediaScanner.scanFolder: START`, so the
  reload path was not entered at all. §11 criterion 2 holds.
  Evidence: `temp/S1315/legB_negative_editor_nochange.log`.

### Notes for the next run

- The probe fires only on a **return into a live Browse**; a cold Browse open produces no probe line,
  because the `stateFlow.value.resource` guard has not been populated yet. That is expected, not a
  missing tag - confirmed separately in `temp/S1315/probe_presence_check.log`.
- Inherited device state: fixture resource `S1315_SubOnly` (id 9) is left in place with
  `showSubfoldersAsItems=ON`; flip it back OFF before re-running leg A.
- Grep for the probe with the literal `S1315: resume`, never bare `S1315` - the fixture resource
  name contains the ticket id and floods a loose pattern.
