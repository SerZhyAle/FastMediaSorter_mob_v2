# Спецификация (compact bugfix): S1498 - Весь UI лаунчера без единого юнит-теста, и негде его завести

**Ticket:** S1498
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1461 (исследование подсистемы лаунчера для архитектурной секции)

**Текст:** текста владельца нет - находка агента.

**Симптом:** пакет `ui/launcher/**`, живущий в флейворном source set `launcherEnabled`, не покрыт ни
одним тестом, и тестового source set под этот флейвор не существует вовсе.

**Что установлено 2026-08-07:**

- Без покрытия: `LauncherHomeActivity`, `LauncherHomeViewModel`, оба класса отрисовки сетки
  (`LauncherDesktopLayout`, `LauncherCellViewBinder`), оба менеджера взаимодействия
  (`LauncherEditModeManager`, `LauncherResizeManager`), `LauncherTaskbarManager`,
  `LauncherTrayManager`, четырнадцать гаджетов и все фрагменты меню, выбора и пиннинга - около
  шестидесяти классов.
- Тестового source set под флейвор нет: существуют только `app_v2/src/test` и `app_v2/src/testVr`.
  Прецедент флейворного набора тестов в проекте уже есть - это `testVr` - значит препятствие
  инфраструктурное, а не принципиальное.
- Доменный и датный слой лаунчера при этом покрыт шестью тестами в `src/test`, включая
  `LauncherDesktopRepositoryImplTest` на Robolectric с Room в памяти. То есть разрыв проходит ровно по
  границе флейворного набора.
- KDoc самого `LauncherDesktopRepositoryImplTest` называет цену прямым текстом: «These are the rules
  edit mode is built on, and all three are invisible to every static gate: a broken one still
  compiles, still passes detekt, and only shows up as two cells drawn on top of each other». Правила
  покрыты на уровне репозитория - а менеджеры перетаскивания и изменения размера, которые на этих
  правилах и построены, не покрыты ничем.

- **Это нарушение записанного правила, а не просто пробел.** `dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 7
  (происхождение S1450): «Adding a new capability source set means adding its test counterpart and
  mirroring the mount list. A mount map that drifts from the main one reintroduces this defect
  silently». Набор `launcherEnabled` заведён без тестового двойника, то есть правило существует и не
  выполнено. Там же объяснено, почему нельзя просто положить тест в `src/test`: тот компилируется под
  **каждый** флейвор, и тест на класс из флейворного набора ломает компиляцию тестов во всех сборках,
  где смонтирован no-op, - после чего не идёт ни один тест.

**Почему это стоит тикета:** речь не о желаемом проценте покрытия, а о том, что инварианты раскладки
не ловятся ни компилятором, ни detekt, ни одним из существующих гейтов - только глазом на устройстве.
Пять живых тикетов лаунчера (S1178 в работе, S1421, S1422, S1428, S1441) правят именно эти классы.

**Дедуп:** `search.ps1 -Query "launcher test coverage"` и `-Query "launcher unit test"` - записей нет.

**Поправка к захвату, установлено 2026-08-10 при проработке.** Два утверждения выше не выдержали
проверки, и §1-§4 написаны уже по факту, а не по захвату:

- «Тестового source set под флейвор нет: существуют только `app_v2/src/test` и `app_v2/src/testVr`» -
  неверно. `app_v2/src/testStandard` существует и содержит четыре теста, три из которых - лаунчерные:
  `LauncherGridGeometryTest` (от 2026-07-17), `LauncherStarterSetsParityTest`,
  `LauncherWallpaperResolutionTest`, плюс `QueryAllAppsUseCaseTest`. Комментарий в
  `app_v2/build.gradle.kts:598` перечисляет `src/testStandard`, `src/testNoLegal`, `src/testVr` прямым
  текстом.
- «`ui/launcher/**` не покрыт ни одним тестом» - неверно для `LauncherGridGeometry`: он покрыт
  одиннадцатью тестами с 2026-07-17. Не покрыто всё, что появилось позже: `renderSpanW`,
  `renderPlan`, `rowsForRendered`, `footprintOfRendered` (S1428) и `rowsForViewport` (S1288).

Остальное в захвате подтвердилось: шестьдесят с лишним классов набора действительно без покрытия, и
Rule 7 действительно нарушен - но не тем, что тестового дома нет, а тем, что он смонтирован уже, чем
его субъект.

---

## 1. Проблема / симптом

Тесты лаунчера лежат в `app_v2/src/testStandard`, а сам лаунчер - в `app_v2/src/launcherEnabled`,
который монтируют **два** флейвора: `standard` и `noLegal`. Тестовый дом уже субъекта ровно на один
флейвор, поэтому на `noLegal` - сборке, где лаунчер тоже шипится, - ни один лаунчерный тест никогда не
исполняется. Это и есть разъезд списка монтирования, который Rule 7 называет молчаливым.

Собственный KDoc `LauncherGridGeometryTest` формулирует этот разъезд, сам того не замечая: «Lives in
`src/testStandard` because `LauncherGridGeometry` ships in the `launcherEnabled` source set, mounted
only into the `standard`/`noLegal` flavors». Названы два флейвора, выбран дом с одним.

Второй симптом - покрытие остановилось на дате заведения теста. `LauncherGridGeometryTest` написан
2026-07-17 и покрывает `rowsFor`, `boundsFor`, `columns`, `cellSizePx`. Всё, что добавилось в
`LauncherGridGeometry` позже, не покрыто ничем: `renderSpanW` и вся проекция свёрнутых секций
(`renderPlan`, `rowsForRendered`, `footprintOfRendered`) от S1428, `rowsForViewport` от S1288 и клампы
`footprint` напрямую. Это ровно те правила, про которые KDoc соседнего теста пишет, что их поломка
«still compiles, still passes detekt, and only shows up as two cells drawn on top of each other».

Тот же разъезд есть и в обратную сторону: `QueryAllAppsUseCaseTest` лежит в `src/testStandard`, хотя
его субъект целиком в `src/main`. Он не исполняется на пяти флейворах из шести без всякой причины.

---

## 2. Корневая причина

Rule 7 в `dev/FLAVOR_DEVELOPMENT_RULES.md` разбирает три случая: субъект в `src/main`, субъект в одном
флейворе, субъект в capability set, который делят несколько флейворов. Третий случай требует общего
тестового набора, смонтированного в те же флейворы, - именно так сделаны `testStreamingEnabled`,
`testCloudEnabled` и `testNetworkMonitor`. Лаунчерные тесты этот случай не опознали и уехали в дом
второго случая, `src/testStandard`, который совпал с одним из двух нужных флейворов и потому выглядел
работающим.

Гейт `assert-shared-test-flavor-scope.ps1` этого не ловит по построению: он сверяет список монтирования
существующего общего тестового набора с его основным двойником. Набора `testLauncherEnabled` не
существовало, сверять было нечего, а тест, лежащий в наборе одного флейвора, - легальная форма второго
случая, и отличить её от неверно выбранного дома гейт не может, не зная, где живёт субъект.

---

## 3. Исправление

Завести `app_v2/src/testLauncherEnabled/java` и смонтировать его в `testStandard` и `testNoLegal` -
ровно туда, куда смонтирован сам `launcherEnabled`. Прецедент один в один: `src/testNetworkMonitor/java`
монтируется теми же двумя строками для того же набора флейворов.

Разложить по домам то, что уже написано:

- `LauncherGridGeometryTest`, `LauncherStarterSetsParityTest` и `LauncherWallpaperResolutionTest` -
  субъекты в `launcherEnabled` (`LauncherGridGeometry`, `LauncherGadgetRegistry`,
  `LauncherHomeViewModel.resolveLauncherWallpaper`), переезжают в новый набор и начинают идти на
  `noLegal`. Третий опознан не сразу: по списку импортов он выглядит чисто `src/main`, а флейворную
  функцию зовёт без импорта, будучи с ней в одном пакете, - см. §4.5.
- `QueryAllAppsUseCaseTest` - субъект целиком в `src/main`, переезжает в общий `src/test` и начинает
  идти на всех шести флейворах.

Догнать покрытие `LauncherGridGeometry` до сегодняшнего состава: клампы `footprint`, расширение
заголовка секции до полной ширины, проекция свёрнутых секций и высота вьюпорта. Robolectric не нужен -
у объекта ноль Android-импортов.

Снять ставшее ложным утверждение в KDoc `LauncherSectionMembership` - «`src/launcherEnabled` has no test
source set, so arithmetic placed there cannot be unit-tested at all».

**Вне объёма:** покрытие менеджеров и активити лаунчера. Исследование показало, что ни один из шести
менеджеров (`LauncherEditModeManager`, `LauncherResizeManager`, `LauncherTaskbarManager`,
`LauncherTrayManager`, `LauncherDesktopLayout`, `LauncherCellViewBinder`) не имеет ни одной публичной
чистой функции: всё, что в них есть, принимает `View`, `MotionEvent` или `Context`. Их покрытие
начинается с выделения логики из менеджеров, а не с написания тестов. Этот тикет делает такую работу
возможной; выполнять её здесь означало бы подменить объём.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** `standard` и `noLegal` - ровно те, куда смонтирован `launcherEnabled`.
- **Validation level:** юнит-тесты флейвора `standard`, компиляция юнит-тестов флейвора `lite` и гейт
  `assert-shared-test-flavor-scope.ps1`.
- **Related tickets:** S1461 - архитектурная секция лаунчера, при исследовании для которой пробел
  найден; S1178 - в работе, правит реестр гаджетов; S1421, S1422, S1428, S1441 - правят те же классы;
  S1450 - происхождение Rule 7; S1453 - гейт, который этот разъезд не ловит; S1288 и S1428 - авторы
  непокрытых функций.

---

## 4. Проверка

Все шесть выполнены 2026-08-10.

1. `app_v2/src/testLauncherEnabled/java` смонтирован в `testStandard` и `testNoLegal` - и больше
   никуда, потому что остальные четыре флейвора самого `launcherEnabled` не монтируют.
   `app_v2/build.gradle.kts`, две строки рядом с `testNetworkMonitor`.
2. В `src/testStandard` не осталось теста, чей субъект живёт в `launcherEnabled`: набор опустел
   целиком и удалён. Гейт `assert-shared-test-flavor-scope.ps1` при этом читает флейворы из того же
   `build.gradle.kts`, так что монтирование `getByName("testStandard")` остаётся валидным.
3. `LauncherGridGeometryTest` - 28 тестов, 0 падений на `standard`; было 11 и только по
   `rowsFor`/`boundsFor`/`columns`/`cellSizePx`. Добавлены `renderSpanW`, `renderPlan`,
   `rowsForRendered`, `footprintOfRendered`, `rowsForViewport` и клампы `footprint` напрямую.
4. **То, ради чего тикет и заводился:** на `noLegal` `LauncherGridGeometryTest` даёт 28 тестов,
   0 падений (`:app_v2:testNoLegalDebugUnitTest`). До этого на `noLegal` не исполнялся ни один
   лаунчерный тест.
5. Юнит-тесты `lite` компилируются и идут: `PermissionRegistryManifestParityTest` - 3 теста,
   0 падений. Эта проверка отработала по назначению: первая попытка положить
   `LauncherWallpaperResolutionTest` в общий `src/test` сломала компиляцию `lite` ровно дефектом
   S1450, потому что тест вызывает `resolveLauncherWallpaper` - внутреннюю функцию
   `LauncherHomeViewModel` из `launcherEnabled`, которой не видно по списку импортов. Тест переехал в
   `testLauncherEnabled`. Это же наблюдение - аргумент к research item 1 в S1557: субъект теста по
   импортам не определяется.
6. `assert-shared-test-flavor-scope.ps1 -Gate` - PASS, 474 общих теста просканировано.
7. В `LauncherSectionMembership` формулировка исправлена: набор теперь существует, а класс остаётся в
   `src/main` по другой причине - его зовёт слой размещения оттуда же.

Закрытие: `post-change.ps1 -ChangeType Mixed -ScopeToFile` по шести файлам - PASS.
