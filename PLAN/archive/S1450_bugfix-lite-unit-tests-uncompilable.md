# Спецификация (compact bugfix): S1450 - Юнит-тесты не компилируются на flavor lite

**Ticket:** S1450
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1179 (шаг 05.2)

**Текст:**

lite unit-test source set does not compile: shared src/test carries tests for link/streaming classes that only exist in flavors with the link-download source set. `check-standard-fast.ps1 -Mode Unit -Flavor Lite` fails at compileLiteDebugUnitTestKotlin with Unresolved reference 'ManifestDrmDetector', 'StreamingCacheCleaner', 'Media3SegmentDownloader', 'MediaMuxerRemuxer' in app_v2/src/test/java/com/sza/fastmediasorter/data/link/streaming/*. Consequence: no unit test can be run on lite at all, including PermissionRegistryManifestParityTest, which docs/RELEASE_READINESS_STANDARD.md names a release blocker and which S1179 phase 05 needs to run per-variant. Found 2026-08-07 while implementing S1179 step 05.2; standard passed (exit 0), lite exited 1 on compilation, not on assertions.

---

## 1. Проблема / симптом

Общий набор исходников `app_v2/src/test` содержит тесты классов, которые существуют не во всех флейворах. На `lite` компиляция юнит-тестов падает целиком, поэтому на этом флейворе нельзя запустить ни один тест.

Наблюдалось 2026-08-07:

```
> Task :app_v2:compileLiteDebugUnitTestKotlin FAILED
e: .../app_v2/src/test/java/com/sza/fastmediasorter/data/link/streaming/ManifestDrmDetectorTest.kt:24:67 Unresolved reference 'ManifestDrmDetector'.
e: .../StreamingCacheCleanerTest.kt:20:27 Unresolved reference 'StreamingCacheCleaner'.
e: .../StreamingDownloadStrategyTest.kt:35:43 Unresolved reference 'Media3SegmentDownloader'.
e: .../StreamingDownloadStrategyTest.kt:36:33 Unresolved reference 'MediaMuxerRemuxer'.
BUILD FAILED in 2m 30s
```

Та же команда на `standard` в том же прогоне завершилась кодом 0.

Почему это важно за пределами удобства: `PermissionRegistryManifestParityTest` объявлен релиз-блокирующим в `docs/RELEASE_READINESS_STANDARD.md` и проверяет расхождение состава разрешений **по вариантам**. Именно на тех флейворах, где состав отличается от `standard` (в частности `lite`), его сейчас нельзя выполнить - то есть гейт, который должен ловить лишнее разрешение в урезанном флейворе, на этом флейворе не запускается.

---

## 2. Корневая причина

Подтверждено. Тестируемые классы живут не в `src/main`, а в общих флейвор-наборах, которые AGP монтирует явно (`app_v2/build.gradle.kts`, блок `sourceSets`), тогда как их тесты лежат в общем `src/test`, который компилируется для **каждого** флейвора.

Два независимых кластера, а не один:

- `app_v2/src/test/java/com/sza/fastmediasorter/data/link/streaming/` - три файла (`ManifestDrmDetectorTest`, `StreamingCacheCleanerTest`, `StreamingDownloadStrategyTest`) обращаются к типам из `src/streamingEnabled/java`. Набор смонтирован в `standard`, `noLegal`, `legacy`, `vr`; `lite` и `photos` монтируют `src/streamingDisabled/java`, где этих классов нет.
- `app_v2/src/test/java/com/sza/fastmediasorter/identity/PrimaryGoogleAccountStateTest.kt` - конструирует `CredentialManagerGoogleIdentityRepository`, `GoogleTokenIssuer`, `PrimaryGoogleAccountStore` из `src/cloudEnabled/java`. Набор смонтирован в `standard`, `noLegal`, `legacy`, `vr`, `photos`; `lite` монтирует `src/cloudDisabled/java`.

Из этого следует, что сломан не только `lite`: `photos` падает на streaming-кластере, `lite` - на обоих. Захваченный лог перечислял только четыре первые ошибки и потому занижал охват.

Механизм уже есть и используется: AGP сам подхватывает `src/test<Flavor>/java`, и в репозитории живут `src/testStandard`, `src/testNoLegal`, `src/testVr`. Не хватало общего тестового набора для теста, который нужен более чем одному флейвору - зеркала того, чем `src/streamingEnabled/java` является для основных исходников.

Прочие совпадения имён при сплошном скане общего `src/test` (`Add`, `Block`, `Icon`, `KeyEvent`, `MotionEvent`, `Parsed`) - это вложенные члены sealed-классов и типы платформы, одноимённые с флейвор-объявлениями; кодом они не задеты.

---

## 3. Исправление

Зеркалить для тестов ту же схему, что уже действует для основных исходников: общий набор исходников на возможность, смонтированный в юнит-тестовый набор каждого флейвора, который эту возможность несёт.

1. Создать `app_v2/src/testStreamingEnabled/java` и перенести туда три теста из `src/test/.../data/link/streaming/` без изменения их содержимого.
2. Создать `app_v2/src/testCloudEnabled/java` и перенести туда `PrimaryGoogleAccountStateTest.kt`.
3. В `app_v2/build.gradle.kts`, блок `sourceSets`, смонтировать наборы в юнит-тестовые наборы флейворов, строго повторяя карту основных исходников: `testStreamingEnabled` в `testStandard`, `testNoLegal`, `testLegacy`, `testVr`; `testCloudEnabled` в те же четыре плюс `testPhotos`.
4. `lite` не монтирует ни один из двух - именно это делает его юнит-тесты компилируемыми.
5. Записать правило для людей: `dev/FLAVOR_DEVELOPMENT_RULES.md`, RULE 7 - тест живёт в той же области, что и его предмет. Документ описывал размещение основных исходников по флейворам и молчал про тесты, из-за чего дефект и не был очевиден при написании тестов. Механическое подкрепление правила вынесено в S1453: по собственному аудиту CLAUDE.md негейтованные правила соблюдаются в 1-8% случаев против ~99% у гейтованных, так что RULE 7 сам по себе рецидив не остановит.

Отвергнутая альтернатива: объявить в `streamingDisabled`/`cloudDisabled` одноимённые заглушки, чтобы общий `src/test` компилировался везде. Тесты тогда проверяли бы no-op вместо поведения, а гейт, который должен ловить расхождение, стал бы зелёным по построению.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1179 (нашёл симптом; его шаг 05.2 не смог выполнить свой per-variant предикат), S1442 (тот же класс дефекта - объявление, живущее не в том флейворе), S1453 (механический гейт против рецидива), S1454 и S1455 (находки первого прогона на `lite`, ставшего возможным благодаря этому тикету), S1449 (существовавший отказ DataStore, дающий 15 из 20 оставшихся), S1436 (владеет паритетом разрешений, чей гейт здесь впервые смог запуститься на `lite`)

---

## 4. Проверка

Предикат исправлен по ходу работы. Изначально здесь стояло «`-Flavor Lite` завершается кодом 0»; первый же прогон показал, что это условие тикету не принадлежит: компиляция чинится здесь, а зелёный набор на `lite` дополнительно требует S1449, S1454 и S1455. Формулировка ниже описывает то, что этот тикет действительно закрывает и чем это доказывается.

**Собственный предикат тикета**

- `compileLiteDebugUnitTestKotlin` проходит: `Unresolved reference` не осталось ни одной. Факт 2026-08-07: задача завершилась, в логе `0` совпадений.
- `photos` - второй сломанный флейвор - собирается: `compilePhotosDebugUnitTestKotlin`, `BUILD SUCCESSFUL`, код 0. Монтирование при этом избирательное, а не «всем всё»: `PrimaryGoogleAccountStateTest` дал 6 тестов при 0 отказов, а три streaming-теста отчётов не дали вовсе - ровно потому, что `photos` монтирует `streamingDisabled`.
- Юнит-тесты на `lite` действительно исполняются, а не просто компилируются: `3157 tests completed, 20 failed, 17 skipped` против нуля исполнимых тестов до исправления.
- Набор не усечён: `assert-test-suite-complete: 437 report(s) for 437 *Test.kt file(s) (ratio 1)` - `PASS`.
- `PermissionRegistryManifestParityTest` исполняется на `lite` - релиз-блокирующий тест из `docs/RELEASE_READINESS_STANDARD.md`, который до этого не мог запуститься на флейворе с наиболее отличающимся составом разрешений. То, что он теперь падает, - результат работы гейта, а не регресс: находка вынесена в S1454.
- Перенесённые тесты не исчезли из тех флейворов, где их предмет существует - главный риск такой правки, поэтому проверено отчётами, а не фактом компиляции. В `testStandardDebugUnitTest` все четыре класса дали по 6 тестов, `failures=0 errors=0 skipped=0`. Арифметика гейта полноты сходится точно: `443 report(s) for 437 *Test.kt file(s)` = 437 общих + 2 ранее существовавших в `src/testStandard` + 4 перенесённых.
- Правка не добавила ни одного нового отказа. На `standard` 15 отказов, и все 15 из 15 - тот же `SingleProcessDataStore` (S1449), не зависящий от флейвора. На `lite` 20 = те же 15 плюс ровно 5 флейворных расхождений (4 - S1454, 1 - S1455). Разница между флейворами состоит только из расхождений, ради поиска которых per-variant тесты и существуют.

**Что этот тикет не закрывает** - 20 оставшихся отказов на `lite`, все существовавшие до него и невидимые ровно потому, что набор не компилировался:

- 15 отказов `SingleProcessDataStore` (`BrowseStateDataStoreTest`, `GameStateRepositoryImplTest`, `ReviewEligibilityDataStoreTest`, `SettingsRepositoryImplTest`) - S1449.
- 4 отказа паритета разрешений - S1454.
- 1 отказ `MediaFamilyResolverTest` - S1455.

Поэтому `-Mode Unit -Flavor Lite` пока завершается кодом 1, и это ожидаемо: код 0 на `lite` становится достижим после S1449 и S1454; S1455 - последний шаг.

---

## Last Audit

**Date:** 2026-08-07
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Статические проверки (9): четыре перенесённых файла лежат по новым путям; освободившиеся каталоги в общем `src/test` удалены; оба набора смонтированы; карта монтирования зеркалит основную (четыре флейвора плюс `photos` только на cloud); `lite` не монтирует ни одного; RULE 7 записан в `dev/FLAVOR_DEVELOPMENT_RULES.md`; строка в `dev/CHANGELOG.md` есть; инвариант отладочных меток соблюдён - при статусе не `BlockNeedUserTest` ни одной `Timber.d("S1450:` в `.kt` нет.

Проверки сборкой (4), выполненные при реализации: `compileLiteDebugUnitTestKotlin` без единого `Unresolved reference`; `photos` - `BUILD SUCCESSFUL`, код 0; на `lite` выполнилось 3157 тестов против нуля до правки; гейт полноты набора `PASS` на обоих флейворах, причём на `standard` арифметика сходится точно (443 = 437 + 2 + 4).

EXEMPT: раздел о влиянии на пользователя - изменение внутреннее, инфраструктура тестов; записи в `docs/ALL_FEATURES.jsonl` намеренно нет, проверено грепом по `S1450`.

### Manual / on-device

- Ничего. Изменение не затрагивает исполняемый код приложения: перенесены только тестовые исходники, изменены карта монтирования наборов, конфиг detekt и его же префлайт-скрипт. Устройство ни для одного предиката не требуется.
