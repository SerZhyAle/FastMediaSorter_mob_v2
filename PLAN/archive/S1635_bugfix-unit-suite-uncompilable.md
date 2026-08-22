# Спецификация (compact bugfix): S1635 - юнит-набор не компилируется после S1511

**Ticket:** S1635
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-14
**Tier:** 1 - Quick Win (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Захвачено во время:** S1329

**Текст:**

Unit test suite does not compile: four tests miss the streamQualityMemoryDao constructor argument added by S1511.

Evidence, from `.\a.ps1 fu` on 2026-08-14 13:10 (exit 1, task :app_v2:compileStandardDebugUnitTestKotlin FAILED, coverage ratio 0 - the suite never ran):

e: app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamSourceCatalogMergeTest.kt:34:69 No value passed for parameter 'streamQualityMemoryDao'.
e: app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/AddStreamSourceUseCaseTest.kt:34:69 No value passed for parameter 'streamQualityMemoryDao'.
e: app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCaseTest.kt:36:69 No value passed for parameter 'streamQualityMemoryDao'.
e: app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCaseTest.kt:30:69 No value passed for parameter 'streamQualityMemoryDao'.

Source: S1511 (stream-quality-rung-memory-and-probe-up, BlockNeedUserTest, updated 2026-08-14 12:03) widened a stream-source constructor with streamQualityMemoryDao and did not update its four unit-test call sites. Consequence: the entire unit suite is uncompilable, so `a.ps1 fu` fails for every ticket and every pre-release gate until this is fixed - the failure is not a test regression, the tests never run. Found while closing S1329 phase 05; unrelated to that ticket.

---

## 1. Проблема / симптом

`:app_v2:compileStandardDebugUnitTestKotlin` падает на четырёх тестовых файлах, поэтому ни один юнит-тест не запускается: `assert-test-suite-complete` сообщает `1 report(s) for 492 *Test.kt file(s) (ratio 0)`. Это не регресс тестов - это отсутствие прогона, и в таком виде гейт `a.ps1 fu` красный для любого тикета и для пре-релизного прогона.

---

## 2. Корневая причина

- S1511 добавил четвёртым параметром `StreamSourceRepository` зависимость `streamQualityMemoryDao`. Боевая проводка идёт через Hilt и правки не требовала, а четыре теста конструируют репозиторий руками и остались на трёх аргументах.
- Гейт самого S1511 этого увидеть не мог: `a.ps1 fk` и `assembleStandardDebug` компилируют только `main`. Тестовый исходный набор компилируется отдельной задачей, и до неё расширение конструктора не доходит.
- Следствие несоразмерно причине: тестовый набор компилируется целиком, поэтому четыре файла делают красным прогон из 3546 тестов - падения нет, есть отсутствие прогона.

---

## 3. Исправление

- Передать в каждом из четырёх мест недостающий аргумент - `dbRule.db.streamQualityMemoryDao()`. Правило `InMemoryRoomRule` строит настоящий `AppDatabase`, поэтому DAO берётся оттуда же, откуда уже берутся два соседних; мок не нужен и был бы хуже - соседние DAO настоящие.
- Вызов разнести по одному аргументу на строку: в одну строку он выходит за 120 символов, а ktlint требует тогда именно такой формы.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1511 (источник изменения конструктора), S1623 (его проверка упирается в этот же красный прогон)

---

## 4. Проверка

- `assert-test-suite-complete` - expected: ratio не 0 | actual: `492 report(s) for 492 *Test.kt file(s) (ratio 1)`, PASS (2026-08-14). До правки было `1 report(s) for 492 file(s) (ratio 0)`. Это и есть предмет тикета: набор снова компилируется и исполняется.
- `.\a.ps1 fu` - expected: exit 0 | actual: exit 1, `3571 tests completed, 1 failed, 17 skipped`. Компиляция прошла, набор отработал целиком. Единственное падение - `PermissionRegistryManifestParityTest`, предмет **S1623**, к расширению конструктора отношения не имеющее. Требовать здесь нулевого кода возврата значило бы держать этот тикет открытым ради чужого дефекта, поэтому критерий разделён: компиляция и прогон - тут, зелёный код возврата - за S1623.
