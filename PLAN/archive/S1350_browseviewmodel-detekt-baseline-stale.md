# Стратегическая спецификация: S1350 - BrowseViewModel: a stale baseline signature un-freezes its LongParameterList

**Ticket:** S1350
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-01
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-08-01, обнаружено при реализации S1334
**Tactical plan:** `PLAN/S1350_browseviewmodel-detekt-baseline-stale/INDEX.md`

<!-- auto-approved by /spec-all - 2026-08-02 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-01

**Текст:**

BrowseViewModel's own LongParameterList detekt baseline entry has silently drifted, same disease as S1334.

Discovered 2026-08-01 while implementing S1334 phase 02 (BrowseStateSyncManager dependency-holder refactor). Editing an unrelated inline initializer inside BrowseViewModel.kt (the stateSyncManager construction, not BrowseViewModel's own primary constructor) tripped the diff-scoped detekt gate on a completely separate, pre-existing finding:

`config/detekt/baseline-app_v2.xml:3509` freezes a `LongParameterList` entry for `BrowseViewModel`'s primary constructor. Comparing the frozen signature against the live gate output: the baseline entry does NOT include a `materializeFavoritesUseCase` parameter between `favoritesUseCase` and `statsSink`, but the live constructor (and live detekt finding) DOES have it there. Same root cause as S1334's own BrowseStateSyncManager instance - S0783 added `materializeFavoritesUseCase: MaterializeFavoritesUseCase` to BrowseViewModel's constructor without refreshing this baseline entry, so the debt has been silently thawed since S0783 landed, waiting for any future edit to this large, frequently-touched file to surface it as that edit's problem.

Confirmed via `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`.

Out of scope for S1334: S1334 is scoped to BrowseStateSyncManager specifically (a ~10-parameter manager, fixed via a 3-field dependency-holder) plus a general baseline-drift diagnostic tool (scripts/quality/audit-detekt-baseline-drift.ps1, delivered in S1334 phase 01 - can be pointed at BrowseViewModel.kt to enumerate its own baseline drift once this ticket is picked up). BrowseViewModel's constructor is a much larger surface (~40 parameters across many unrelated dependency groups: use cases, repositories, cloud clients, caches, prefs) - bringing it under threshold needs its own research into how to group ~40 params into holders without disrupting the many existing call-site and test wiring, which is a non-trivial design task in its own right, not a one-line fix alongside S1334.

Dedup checked: scripts/spec_catalog/search.ps1 for "BrowseViewModel" and "BrowseViewModel LongParameterList" both return zero matches - no existing ticket covers this.

Related: S1334 (sibling instance, same root cause, delivers the detector this ticket should use), S0783 (introduced the drift by growing the constructor), S1198/S1247/S1311/S1314/S1328 (the honest never-frozen per-class detekt-debt family - different cause, listed for pattern precedent only).

**Вложения:** нет

---

## 1. Проблема

`BrowseViewModel`'s primary constructor (41 параметр) давно превысил detekt-порог `LongParameterList`
(`constructorThreshold: 10`), но его baseline-запись это скрывает - и уже дважды тихо переморожена
на неодобренном числе параметров чужими `detektBaseline`-прогонами (в т.ч. сегодня, побочным эффектом
закрытия S1351). Пользователя это не касается напрямую - касается разработки: любая будущая правка
этого 969-строчного файла (центр экрана Browse) рискует унаследовать чужой оттаявший долг как
провал собственного diff-scoped detekt-гейта, ровно как уже случилось в S1334.

---

## 2. Цели

1. Конструктор `BrowseViewModel` возвращается под порог `LongParameterList` (10) структурно -
   группировкой связанных зависимостей в объекты-держатели, а не переморозкой baseline на текущем
   (никогда не одобренном) числе параметров - тот же принцип, что ADR-1 в S1334.
2. Мёртвая baseline-запись `LongParameterList` для `BrowseViewModel` из `config/detekt/baseline-app_v2.xml`
   удаляется, а не остаётся висеть, описывая код, которого больше нет в такой форме.
3. Внешние точки чтения `fileOperationUseCase` (единственного публичного поля конструктора) переходят
   на чтение через новый держатель без изменения поведения.

**Non-goals:**

- Полный разбор `BrowseViewModel` на несколько независимых ViewModel/классов - вне объёма, класс
  остаётся 969 LOC, меняется только форма получения зависимостей.
- Новые unit-тесты для `BrowseViewModel` - существующее покрытие нулевое (см. распаркованный
  **S1352**), написание тестов явно вынесено в отдельный тикет, секвенированный после этого.
- Изменение поведения любого use case/репозитория/клиента - только форма их доставки в конструктор.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Пожеланий сверх зафиксированного объёма нет.

### 3.2 Жёсткие ограничения

- **Flavor:** все - `BrowseViewModel.kt` лежит в `src/main`, без BuildConfig-гейта; шесть внешних
  точек чтения `fileOperationUseCase` тоже все в `src/main`.
- **API level:** без API-специфики - чистый DI/конструктор-рефакторинг.
- **Wear OS:** не затрагивается - у `BrowseViewModel` нет wear-аналога.
- **Производительность:** не критично - держатели строятся один раз на время жизни
  `ViewModelComponent`, без scope-аннотации (соответствует прецеденту `LauncherHomeDependencies`),
  никакого дополнительного eager-инициализации `Lazy<T>`-обёрток.
- **Совместимость данных:** нет - изменений в форматах хранения нет.
- **Локализация:** не требуется - пользовательских строк нет.
- **Доступность:** н/п - изменений UI нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1334 (родственный экземпляр, тот же держатель-паттерн, источник ADR-1),
  S0783 (вырастил конструктор без обновления записи), S1351 (случайно переморозил текущий счётчик
  побочным эффектом project-wide `detektBaseline`), S1352 (распаркован из исследования этого тикета -
  нулевое тестовое покрытие `BrowseViewModel`, секвенирован после), S1198/S1247/S1311/S1314/S1328
  (честная per-class detekt-debt семья - другая причина, не блокируют).

---

## 4. Контекст текущей архитектуры

`BrowseViewModel` - `@HiltViewModel`, единственная точка входа UI-слоя экрана Browse в
use-case/репозиторный слой; Hilt конструирует его один раз на `ViewModelComponent` (на экран/окно),
разрешая все параметры конструктора из графа DI. Внутри тела конструктора часть параметров вручную
переупаковывается в дочерние менеджеры (`BrowseStateSyncManager` и др.) - S1334 уже сократил один
такой менеджер держателем, но это не уменьшило счётчик параметров самого `BrowseViewModel`, потому
что тот держатель (`data class`, ручная сборка на месте вызова) - другая идиома, не сокращающая
конструктор Hilt-конструируемого класса. Проблему §1 нельзя решить переморозкой (см. ADR в S1334) -
это лишь тихо принимает рост без одобрения и не защищает от следующего дрейфа.

---

## 5. Предлагаемый подход

Шесть новых ролей-держателей (`@Inject constructor`, без scope-аннотации, как
`LauncherHomeDependencies`) группируют 38 из 41 параметра конструктора по доменному сходству; три
параметра (контекст приложения, IO-диспетчер, `SavedStateHandle`) остаются прямыми - это
фреймворковая инфраструктура, не бизнес-зависимости, и ни один существующий менеджер их тоже не
группирует. Держатели строятся Hilt-графом напрямую как обычные inject-конструируемые классы, а не
вручную на месте вызова (это ключевое отличие от идиомы, использованной в S1334 для собственного,
не-Hilt-конструируемого менеджера).

### 5.1 Основные столпы / модули

- **Облачные/удалённые зависимости** - клиенты облачных провайдеров, SMB-операции, гейт доступности
  удалённого источника. Общая черта - все обращаются к внешнему по отношению к устройству ресурсу.
- **Операции очистки** - use case'ы удаления по размеру, очистки корзины, очистки временных файлов,
  удаления директорий. Общая черта - деструктивные операции над файлами.
- **Кэш и обнаружение контента** - юнифайд-кэш, извлечение метаданных (медиа/аудио), репозиторий
  кэшированного списка файлов, синхронизация с MediaStore, фабрика сканера, получение ресурсов/файлов.
  Общая черта - участие в конвейере скан → кэш → обогащение → синхронизация листинга.
- **Персистентное состояние Browse** - datastore состояния Browse, пользовательский порядок,
  избранное (включая материализацию), состояние возобновления (get/save/clear), сток статистики.
  Общая черта - состояние, привязанное к пользователю/ресурсу, переживающее сессию экрана.
- **Создание/трансформация контента** - use case'ы создания директории/заметки/рисунка, архивации,
  распаковки, добавления ресурса как назначения, обновления ресурса, файловых операций (включая
  единственное публичное поле конструктора). Общая черта - пользовательские действия, создающие или
  трансформирующие контент внутри просматриваемой папки.
- **Инфраструктура мутации файлов** - обработчик унифицированных файловых операций, журнал мутаций,
  нормализатор путей, репозиторий настроек. Первые три уже связаны существующими комментариями в коде
  (S0242); `settingsRepository` присоединён сюда прагматично, а не по чистой смысловой близости -
  ни одна из остальных пяти групп не подходит лучше.

### 5.2 Потоки данных и событий

Hilt-граф → шесть держателей (каждый строится собственным `@Inject constructor`) → конструктор
`BrowseViewModel` (9 параметров: 6 держателей + 3 прямых) → внутренние точки использования читают
поле через `holderName.paramName` вместо плоского имени. Единственное публичное поле
(`fileOperationUseCase`) читается извне как `viewModel.contentAuthoringUseCases.fileOperationUseCase`
вместо `viewModel.fileOperationUseCase` - одна внешняя точка чтения обновляется вместе с этим.

**Исправление найдено при реализации (F3, 2026-08-02):** исходное исследование (§1, §7 риск-таблица)
насчитало шесть внешних точек чтения - `BrowseDialogCallbacksImpl.kt`, `PlayerFileOpsInitializer.kt`
(x2), `PlayerDialogHelper.kt` (x3) - но пять из шести читают `activity.viewModel.fileOperationUseCase`
/ `viewModel.fileOperationUseCase`, где `viewModel` там типизирован как `PlayerViewModel`, а не
`BrowseViewModel`. `PlayerViewModel` несёт собственное, отдельное публичное поле
`fileOperationUseCase: FileOperationUseCase` (свой `@Inject constructor`, не связано с этим тикетом) -
исследование спутало два одноимённых поля на двух разных классах grep'ом без проверки типа `viewModel`
в каждом файле. Настоящая внешняя точка чтения `BrowseViewModel.fileOperationUseCase` - ровно одна:
`BrowseDialogCallbacksImpl.getFileOperationUseCase()`. Подтверждено полной компиляцией: попытка
обновить все шесть дала `Unresolved reference 'contentAuthoringUseCases'` на пяти сайтах в
`PlayerDialogHelper.kt`/`PlayerFileOpsInitializer.kt`; откат этих пяти + компиляция - `BUILD
SUCCESSFUL`.

### 5.3 Точки расширяемости

Любая новая зависимость добавляется в существующий по смыслу держатель, а не как седьмой прямой
параметр конструктора - так счётчик прямых параметров (3) и счётчик держателей (6) остаются
стабильными, и порог `LongParameterList` не грозит вернуться от одного нового поля. Три держателя
(«Кэш и обнаружение», «Персистентное состояние», «Создание контента») уже на потолке в 8 полей,
совпадающем с крупнейшим существующим прецедентом (`VideoPlayerNetworkDependencies`) - следующее
добавление в любой из них потребует либо нового under-8 держателя, либо признания, что сам держатель
вырос до отдельной находки.

---

## 6. Открытые вопросы / Research items

1. **Куда поместить `fileOperationUseCase` - единственное публичное поле конструктора**
   - **Вопрос:** переносить ли в держатель «Создание/трансформация контента» (6 внешних точек чтения
     обновляются) или оставить прямым параметром (0 внешних правок, но итог - ровно 10 параметров,
     на самом пороге).
   - **Варианты:** перенести в держатель (итог 9 параметров, запас в 1) / оставить прямым (итог 10,
     запас 0 - следующий held-in-check параметр снова триггерит находку).
   - **Нужно выяснить:** ничего дополнительного - оба варианта технически осуществимы, выбор чисто
     по риск-профилю.
   - **Статус:** Resolved - перенести в держатель. Ноль запаса на пороге - именно тот сценарий, что
     `feedback_detekt_baseline_signature_resurface.md` называет следующей ловушкой; шесть
     механических правок точек чтения дешевле, чем повторный дрейф.

2. **Как сгруппировать 41 параметр по держателям и какую идиому держателя использовать**
   - **Вопрос:** какая форма держателя (Hilt-инжектируемый класс vs вручную собираемый data class)
     реально уменьшает счётчик параметров самого `BrowseViewModel`, и как сгруппировать параметры по
     доменному сходству.
   - **Варианты:** повторить идиому `BrowseStateSyncUseCases`/`VideoPlayerDependencies` (`data
     class`, ручная сборка - не работает для этой цели, см. §4) / повторить идиому
     `LauncherHomeDependencies` (`class` с собственным `@Inject constructor`, без scope-аннотации -
     единственная идиома, реально сокращающая Hilt-конструируемый конструктор).
   - **Статус:** Resolved.
   - **Артефакт:** исследование проведено `android-solution-researcher` 2026-08-02 (не сохранено
     отдельным файлом - тактическая папка ещё не создана; полная группировочная таблица (41 параметр
     → 6 держателей + 3 прямых) воспроизведена в §5.1/§7 этого документа и в тактическом плане).

<Открытых вопросов нет - оба research item выше Resolved.>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Скопировать не ту идиому держателя (`data class`, ручная сборка) - скомпилируется, пройдёт detekt на самом держателе, но не сократит счётчик параметров `BrowseViewModel` | Средняя | Тикет закрывается формально, находка остаётся живой | Использовать идиому `LauncherHomeDependencies` (`@Inject constructor`, без `data`), явно зафиксированную в §5 |
| Держатель «Кэш и обнаружение»/«Персистентное состояние»/«Создание контента» достигает 8 полей - потолка крупнейшего прецедента | Низкая | Любое будущее добавление снова триггерит `LongParameterList`, уже на держателе | Зафиксировано в §5.3 как известный предел; не блокирует эту итерацию |
| Спутать одноимённое поле `PlayerViewModel.fileOperationUseCase` с `BrowseViewModel.fileOperationUseCase` (реализовалось - см. §5.2) | Материализовалась | Пять сайтов ошибочно правились и откатывались | Обнаружено немедленно `.\a.ps1 fk` (`Unresolved reference`), откат + компиляция подтвердили: реальная точка ровно одна - `BrowseDialogCallbacksImpl.kt` |
| `BrowseViewModel.kt` 969 LOC, правка касается конструктора и ~7 внутренних точек вызова | Низкая | Нарушение CLAUDE.md Rule 5 (backup >500 LOC), как уже раз случилось в S1334 | Явный backup-шаг в тактическом плане перед первой правкой файла |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - внутренний DI-рефакторинг без изменения поведения.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Держатели зависимостей вместо переморозки baseline**

- **Решение:** вернуть конструктор `BrowseViewModel` под порог `LongParameterList` группировкой 38
  из 41 параметра в шесть Hilt-инжектируемых держателей (`@Inject constructor`, без
  scope-аннотации), три параметра остаются прямыми (framework-инфраструктура). Мёртвая baseline-
  запись удаляется, а не переиспользуется.
- **Альтернативы:** переморозка на текущих 41 (отклонено - тот самый анти-паттерн, который этот
  тикет и его предшественник S1334 существуют, чтобы прекратить); полный разбор `BrowseViewModel`
  на несколько ViewModel (отклонено - несоразмерно масштабу находки, задевает состояние экрана,
  вне объёма по §2 non-goals).
- **Почему:** прямое повторение прецедента ADR-1 из S1334, уже принятого и верифицированного;
  идиома держателя (`LauncherHomeDependencies`) уже дважды доказана в кодовой базе.

**ADR-2: `fileOperationUseCase` переносится в держатель, не остаётся прямым**

- **Решение:** см. §6 research item 1 - перенос в держатель «Создание/трансформация контента», шесть
  внешних точек чтения обновляются на `viewModel.contentAuthoringUseCases.fileOperationUseCase`.
- **Альтернативы:** оставить прямым параметром (отклонено - итог ровно на пороге 10, нулевой запас).
- **Почему:** избегает известной ловушки "sitting exactly at threshold" из
  `feedback_detekt_baseline_signature_resurface.md`; правки точек чтения чисто механические.

---

## 10. Связи с другими спеками

- **S1334** - источник паттерна держателя и ADR-1, уже Verified.
- **S0783** - исходно вырастил конструктор без обновления baseline-записи.
- **S1351** - его project-wide `detektBaseline`-прогон случайно переморозил текущий (41-параметровый)
  счётчик побочным эффектом; этот тикет заменяет ту переморозку структурным решением.
- **S1352** - распаркован из исследования этого тикета (нулевое тестовое покрытие
  `BrowseViewModel`), рекомендован к выполнению после этого тикета.
- **S1198/S1247/S1311/S1314/S1328** - соседняя честная per-class detekt-debt семья, не блокируют и
  не блокируются.

Блокирующих зависимостей нет.

---

## 11. Критерии готовности (strategic-level)

1. Свежий отчёт detekt по `BrowseViewModel.kt` не содержит находки `LongParameterList` при
   действующем пороге - подтверждено `assert-detekt.ps1 -Gate -ChangedFiles` и полным (не
   diff-scoped) прогоном.
2. Мёртвая baseline-запись `LongParameterList:BrowseViewModel.kt$BrowseViewModel$..` удалена из
   `config/detekt/baseline-app_v2.xml` - подтверждено, что `audit-detekt-baseline-drift.ps1`
   классифицировал её `DEAD` перед удалением.
3. Поведение экрана Browse не меняется - подтверждено сборкой (`standard debug`) и ручной
   device-проверкой основных сценариев (просмотр, избранное, создание/архивация/удаление,
   переименование через `RenameDialog`, воспроизведение с быстрыми файловыми операциями).
4. Единственная реальная внешняя точка чтения `BrowseViewModel.fileOperationUseCase`
   (`BrowseDialogCallbacksImpl.getFileOperationUseCase()`) не осталась на старом плоском имени поля -
   подтверждено полной компиляцией (`.\a.ps1 fk`/`.\a.ps1 d`). Пять точек, первоначально
   насчитанных исследованием как внешние, читают отдельное одноимённое поле `PlayerViewModel` и вне
   объёма этого тикета - см. §5.2 "Исправление найдено при реализации".

---

## Revision History

- **2026-08-02** - by `/spec-test-device` (Pixel 9, device: emulator-5554 Android 15)
  - Scenario: temp/S1350/mobile_test_scenario_20260802_1030.md - PASS/FAIL/SKIPPED 6/0/0 - Errors in log: 0 (crash/ANR/holder-class scoped)

---

## Last Audit

**Date:** 2026-08-02
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 15 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [x] Browse screen behavior unchanged (§11.3) - verified on-device 2026-08-02, 6/6 scenario steps
  PASS, zero crashes/ANRs, zero errors tagged to any of the six new holder classes or
  `BrowseViewModel`/`BrowseDialogCallbacksImpl`. Both `Timber.d("S1350: ..")` probes fired correctly
  (init + rename path). Scenario: `temp/S1350/mobile_test_scenario_20260802_1030.md`.
