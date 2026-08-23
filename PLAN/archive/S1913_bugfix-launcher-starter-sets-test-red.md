# Стратегическая спецификация: S1913 - Три постоянно красных теста LauncherStarterSetsTest

**Ticket:** S1913
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - найдено при работе над S1886
**Tactical spec:** `PLAN/S1913_bugfix-launcher-starter-sets-test-red/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21
**Захвачено во время:** S1886

**Текст:**

LauncherStarterSetsTest has three assertions that have been failing continuously since at least 2026-08-21 15:58 and are unrelated to S1886: (1) "photo frame seeds folder-preview gadget and slideshow shortcut when a resource exists" expects a "sec:resources" header that itemsFor cannot emit, because profileGadgets puts the PHOTO_FRAME slideshow shortcut inside the widgets section while commonResources never reads lastResourceId, so resItems is empty and the header is suppressed; (2) "mainstream profile seeds the full resource and padding set" expects no padding feature cells under sec:app_functions but the code emits fn:streams, fn:quick_camera, fn:quick_voice, fn:calculator, fn:network_monitor; (3) "google section is absent without google services even when the apps are installed" fails with "seeded without services: com.android.chrome". Evidence: temp/check_fast_20260821_155803.log lines 100-106 and temp/check_fast_20260821_171804.log lines 103-109, both predating any S1886 edit. Needs research to decide per assertion whether the seed code or the expectation is wrong; do not simply rewrite the expectations to match current behaviour.

**Доказательства (выдержки из прогонов, оба до любой правки S1886):**

```text
temp/check_fast_20260821_155803.log:100  LauncherStarterSetsTest > google section is absent without google services even when the apps are installed FAILED
temp/check_fast_20260821_155803.log:103  LauncherStarterSetsTest > photo frame seeds folder-preview gadget and slideshow shortcut when a resource exists FAILED
temp/check_fast_20260821_155803.log:106  LauncherStarterSetsTest > mainstream profile seeds the full resource and padding set FAILED
temp/check_fast_20260821_171804.log:103  (те же три)
```

Сообщения ассертов из `app_v2/build/test-results/testStandardDebugUnitTest/TEST-com.sza.fastmediasorter.core.launcher.LauncherStarterSetsTest.xml`:

- photo frame: `expected: [.. sec:widgets, folder_preview:5, sec:resources, res:5:SLIDESHOW, ..]` / `but was: [.. sec:widgets, folder_preview:5, res:5:SLIDESHOW, ..]` - заголовок `sec:resources` не выдаётся.
- mainstream profile: `expected: [.. sec:app_functions, act:app_settings, ..]` / `but was: [.. sec:app_functions, fn:streams, fn:quick_camera, fn:quick_voice, fn:calculator, fn:network_monito.. ]`.
- google section: `seeded without services: com.android.chrome`.

**Почему это отдельный тикет, а не часть S1886:** S1886 меняет наполнение группы «Виджеты» и плотность при сбросе. Эти три ассерта красны независимо от него и требуют решения по каждому - неправ код посева или неправо ожидание. Переписать ожидания под текущее поведение нельзя: это спрячет дефект, если неправ код.

---

## 1. Проблема

Три ассерта `LauncherStarterSetsTest` падают постоянно и независимо от чьей-либо текущей работы. Прогон на живом дереве 2026-08-21: `32 tests completed, 3 failed` - те же три, что в логах от 15:58 и 17:18.

Красны они примерно тридцать часов: все три переписаны одним коммитом `f19fadf5` (2026-08-20 01:25), разбившим единый `SECTION_EVERYTHING_ELSE` на шесть блоков, и с тех пор файлы не менялись. Набор перед тем коммитом начисто не прогоняли.

Цена не в самих ассертах, а в том, что постоянно красный набор перестаёт быть сигналом: следующая настоящая поломка в этом классе не будет отличима от фона.

## 2. Цели

1. Набор `LauncherStarterSetsTest` зелёный целиком, без исключённых и без `@Ignore`.
2. По каждому из трёх ассертов решение принято осознанно - названа сторона, которая неправа, и причина, а не подгонка ожидания под вывод.
3. Поведение посева не меняется: если решение «неправ тест», код остаётся как есть.

**Non-goals:**

- Пересмотр состава стартовых наборов и правил секций - это S0404 / S1560 / S1566 / S1644.
- Устранение двойной достижимости Chrome (см. §6 пункт 2): это правка поведения за пределами области тикета.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Не прятать дефект: захваченный текст тикета прямо запрещает переписывать ожидания под текущее поведение без обоснования.

### 3.2 Жёсткие ограничения

- **Flavor:** правка только в `app_v2/src/test` - общий тестовый набор, не флейворный.
- **Wear OS:** не затрагивается.
- **Совместимость:** посевной код не меняется, поэтому уже засеянные рабочие столы не затрагиваются.
- **Локализация:** новых строк нет.
- **Чужая работа в тех же файлах:** в дереве лежат незакоммиченные правки S1886 (`In Progress`), добавившие `media_image_window:5` в ожидание ассерта (1). Правка пишется поверх них, а не вместо.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** не применимо - правка в общем тестовом наборе `app_v2/src/test`, ни один вариант сборки не различается.
- **Validation level:** класс прогоняется целиком до и после; «до» уже снято - `32 tests completed, 3 failed`. Зелёным считается только прогон без исключённых тестов.
- **Owner sign-off:** не требуется - поведение приложения не меняется, меняются только ожидания в тестах. Два вопроса размещения (§6) отвечены по коду и прецеденту, а не догадкой; если владелец решит иначе, правка станет обратной - чинить посев.
- **Related tickets:** S1886 - тикет, при работе над которым найдено, и он же держит незакоммиченные правки в этих файлах; S0404 / S1560 / S1566 / S1644 - авторы затронутых ассертов; `f19fadf5` - коммит, в котором все три покраснели.

## 4. Контекст текущей архитектуры

`LauncherStarterSets.itemsFor` собирает стартовый набор шестью блоками: виджеты, ресурсы, функции приложения, сторонние приложения, условная гугловая секция. Каждый блок печатает свой заголовок **только если корзина непуста** - правило применено одинаково четыре раза и явно объяснено в KDoc `googleSection`: членство в секции позиционное, поэтому заголовок без единой ячейки под ним забрал бы себе всё, что идёт ниже.

Профильные ярлыки сеются из `profileGadgets`, то есть попадают в корзину *виджетов*, а `commonResources` заведомо ограничена одним `BROWSE`-ярлыком на существующий виртуальный ресурс.

Подробности и построчный разбор: `research/01__which-side-is-wrong-per-assertion.md`.

## 5. Предлагаемый подход

Три независимые правки в одном файле `LauncherStarterSetsTest.kt`, каждая со своим обоснованием:

1. **photo frame** - убрать из ожидания заголовок `sec:resources`, оставив `res:5:SLIDESHOW` и `media_image_window:5` под `sec:widgets` рядом с `folder_preview:5`.
2. **mainstream profile** - не использовать `sectionTail(profile)`, у которого нет ячеек-добивок по построению, а перечислить блок явно вместе с шестью `fn:*`.
3. **google section** - добавить четвёртое исключение для Chrome, который сеется безусловно через `commonThirdPartyApps` в секцию Android apps.

Посевной код не трогается ни в одном из трёх случаев.

## 6. Открытые вопросы / Research items

1. **Кто неправ в каждом из трёх ассертов**
   - **Вопрос:** неправ код посева или ожидание?
   - **Решение:** во всех трёх - ожидание, но по трём разным причинам: неверное предположение о корзине (1), осечка рефакторинга, потерявшая элементы списка при переходе на помощник (2), неполный список исключений (3).
   - **Статус:** Resolved
   - **Артефакт:** `research/01__which-side-is-wrong-per-assertion.md`

2. **Двойная достижимость Chrome**
   - **Вопрос:** Chrome сеется и безусловно, и в гугловой секции - это дефект посева?
   - **Решение:** нет. Решение S1644 из того же коммита прямо разрешает приложению занимать столько ячеек, сколько есть свободных позиций: «повторяющаяся цель не делает ячейку дубликатом». Устранение двойного пути было бы правкой поведения и вынесено в Non-goals §2.
   - **Статус:** Resolved

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Правка ожидания прячет настоящий дефект посева | Средняя | Дефект доживает до пользователя | По каждому ассерту названа сторона и причина со ссылкой на код и прецедент; §6 фиксирует, чем каждое решение опровергается |
| Конфликт с незакоммиченной работой S1886 в тех же файлах | Средняя | Потеря чужой правки | Трогаются только три ассерта; вставка S1886 `media_image_window:5` сохраняется дословно |
| Зелёный прогон достигнут исключением теста | Низкая | Проверка исчезает молча | Приёмка требует прогона без исключённых и без `@Ignore` |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений - правка тестовых ожиданий, поведение приложения не меняется.

## 9. Архитектурные решения (ADR)

**ADR-1: чиним тест, а не посев - по каждому ассерту отдельно**

- **Решение:** во всех трёх случаях правится ожидание.
- **Альтернативы:** править посев так, чтобы он совпал с ожиданиями.
- **Почему:** каждое текущее поведение подтверждено independently - KDoc `commonResources` и прецедент `EBOOK_READER` для (1), два соседних проходящих теста для (2), решение S1644 для (3). Подгонка посева под ошибочное ожидание сломала бы работающее поведение.

## 10. Связи с другими спеками

- **S1886** - обнаружил, не блокирует и не блокируется.
- **S1928** - выделен из этого тикета: устаревший `JAVA_HOME` мешал прогону. Не блокирует.

---

## 11. Критерии готовности (strategic-level)

1. `LauncherStarterSetsTest` проходит целиком: `32 tests completed, 0 failed`.
2. Ни один тест не исключён, не помечен `@Ignore` и не удалён.
3. `LauncherStarterSets.kt` не изменён этим тикетом.
4. Вставка S1886 `media_image_window:5` сохранена в ожидании ассерта (1).

---

## Implementation State (2026-08-21, session stopped by operator)

**Сделано:** все три ожидания правлены в `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`, каждое с комментарием, называющим причину:

1. photo frame - убран заголовок `sec:resources`; `res:5:SLIDESHOW` и `media_image_window:5` (вставка S1886 сохранена) стоят под `sec:widgets`.
2. mainstream profile - `sectionTail(..)` заменён явным перечислением с шестью `fn:*`.
3. google section - список исключений вынесен в `seededOutsideGoogleSection` и дополнен Chrome.

Посевной код `LauncherStarterSets.kt` не тронут - критерий §11 пункт 3 соблюдён.

**Проверка выполнена (2026-08-21 22:22):** класс прогнан целиком, задача `testStandardDebugUnitTest` действительно выполнилась (в списке задач она без пометки `UP-TO-DATE`).

```
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.core.launcher.LauncherStarterSetsTest"
expected: tests=32 failures=0 errors=0 | actual: tests=32 skipped=0 failures=0 errors=0
```

Результат снят из XML, а не из кода выхода: `app_v2/build/test-results/testStandardDebugUnitTest-filtered/TEST-com.sza.fastmediasorter.core.launcher.LauncherStarterSetsTest.xml`, атрибут `timestamp="2026-08-21T20:22:59.767Z"` - это время самого прогона, а не время файла. Прежний зелёный код выхода был снят с задачи, которая не запускалась, поэтому сверялся именно `timestamp` внутри XML.

**Почему прошлый прогон не состоялся:** переменная `JAVA_HOME` процесса указывала на удалённый `jdk-21.0.10`, и билдер отказывался стартовать gradle. Правка на время сессии: `export JAVA_HOME="C:\Program Files\Java\latest\jdk-21"`. Это не дефект тикета - вынесено в S1928.

**Критерии §11 - сверка:**

1. `32 tests completed, 0 failed` - выполнен, см. XML выше.
2. Ни один тест не исключён: `skipped=0`, `tests=32` (столько же, сколько в красном прогоне), `@Ignore` в файле нет.
3. `LauncherStarterSets.kt` не изменён: ни одного маркера `S1913` в файле, время правки 18:32:25 - раньше правок тикета (22:10:49).
4. Вставка S1886 `media_image_window:5` на месте - строка 206, под `sec:widgets`.

---

## Last Audit

**Date:** 2026-08-21
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Проверено статически плюс один прогон класса; результат снят из XML, а не из кода выхода.

- §11.1 `tests=32 skipped=0 failures=0 errors=0`, `timestamp="2026-08-21T20:22:59.767Z"` - задача `testStandardDebugUnitTest` выполнилась, а не была пропущена как up-to-date.
- §11.2 исключённых нет: `skipped=0`, столько же тестов (32), сколько в красном прогоне; `@Ignore` в файле 0.
- §11.3 посев не тронут: в `LauncherStarterSets.kt` ноль маркеров `S1913`.
- §11.4 вставка S1886 `media_image_window:5` на месте, строка 206 под `sec:widgets`.
- §2.2 сторона и причина названы в трёх комментариях `// S1913:` - строки 156, 200, 475.
- §3.2 объём: маркеры `S1913` только в одном файле под `app_v2/src/test`; в `wear/` и в `strings.xml` их нет.
- §6 `check-open-items-carried.ps1` - PASS, оба пункта Resolved.
- Инвариант отладочных меток: статус не `BlockNeedUserTest`, `Timber.d("S1913:` в `.kt` - 0 вхождений.
- §8 - EXEMPT: «Без изменений в docs/FEATURES».

Вне контракта тикета, запарковано: **S1928** - устаревший `JAVA_HOME` процесса не давал запустить gradle; из-за него прошлый прогон не состоялся, а код выхода был зелёным.
