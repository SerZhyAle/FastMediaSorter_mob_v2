# Спецификация (compact bugfix): S1459 - Строка микрофона в реестре разрешений закрыта не тем флагом

**Ticket:** S1459
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1442

**Текст:**

The `record_audio` row in the permission registry is gated on `SUPPORT_MIC_RECORDING` (`PermissionRegistryRepositoryImpl.kt`, `buildGates = setOf("SUPPORT_MIC_RECORDING")`). That flag names the Browse voice-note feature S0100 excluded from `lite`, not whether the app touches the microphone. In `lite` the flag is false, so the row is hidden - yet the flavor still records video with audio through the camera (`SUPPORT_VIDEO = true`), and the app really does request `RECORD_AUDIO` from the user there. Result: a `lite` user is asked for the microphone by a runtime dialog but finds no microphone row on the app's own permissions screen, so the one place built for reviewing and revoking a granted permission is lying by omission - the same failure mode S0429 fixed from the other direction for the notification-listener row.

The gate should express "this build can ask for the mic", which is `SUPPORT_MIC_RECORDING || SUPPORT_VIDEO` today, or a capability that means exactly that. Until it moves, `PermissionManifestExemptions.kt` carries the case as a named exemption citing this ticket.

Second, smaller finding from the same audit: the release-readiness checklist runs `PermissionRegistryManifestParityTest` against `standard`, `lite` and `noLegal` variants but not `photos` (`docs/RELEASE_READINESS_STANDARD.md`), and `photos` is exactly the flavor whose manifest S1442 just changed.

---

## 1. Проблема / симптом

В сборке `lite` экран разрешений приложения не показывает строку микрофона, хотя приложение в этой сборке действительно просит `RECORD_AUDIO` системным диалогом: видеосъёмка камеры пишет звук, `SUPPORT_VIDEO` там `true`. Пользователь `lite` получает запрос на микрофон, а затем не находит его в единственном месте, построенном для просмотра и отзыва выданного разрешения. Это отказ того же класса, что S0429 чинил с другой стороны, только зеркальный: там строка была без объявления, здесь объявление без строки.

Эвиденс, снятый по рабочему дереву 2026-08-09:

- `app_v2/src/lite/AndroidManifest.xml` не удаляет `RECORD_AUDIO` - `grep` даёт 0 совпадений, то есть `lite` наследует объявление из `src/main`.
- `app_v2/build.gradle.kts`: у `lite` `SUPPORT_VIDEO` = `true`, `SUPPORT_MIC_RECORDING` = `false` с комментарием «Excluded per S0100 §6».
- `PermissionRegistryRepositoryImpl.kt`, строка 193: строка `record_audio` закрыта `buildGates = setOf("SUPPORT_MIC_RECORDING")`.
- `PermissionManifestExemptions.kt`, строка 53: расхождение уже описано как именованное исключение, и текст исключения сам называет виновника - «The row gate is the wrong flag and is tracked as S1459».

`lite` - единственная сборка, где два флага расходятся: `photos` имеет оба `false` и удаляет разрешение из манифеста (S1442), остальные четыре имеют оба `true`.

**Второй пункт захвата §0 уже закрыт другим тикетом.** `docs/RELEASE_READINESS_STANDARD.md` перечисляет `photos` в списке вариантов теста паритета (строка 85), и текст рядом прямо ссылается на S1454\S1460 как на тикет, добавивший его. Здесь переделывать нечего - пункт снят проверкой, а не работой.

---

## 2. Корневая причина

Флаг `SUPPORT_MIC_RECORDING` называет фичу голосовых заметок Browse, исключённую из `lite` по S0100 §6, а не способность сборки обратиться к микрофону. Строка реестра спрашивает «есть ли в сборке голосовые заметки», хотя должна спрашивать «может ли сборка попросить микрофон». В `lite` эти два вопроса впервые дали разные ответы, и строка исчезла.

Починить это набором из двух флагов нельзя: `evaluateBuildGates` (строка 428) сводит набор через `gates.all { .. }`, то есть И, а нужное условие - ИЛИ. Ни одна существующая строка реестра не задаёт больше одного флага, поэтому семантика набора до сих пор ни на чём не проверялась.

---

## 3. Исправление

Ввести отдельный флаг `DECLARES_MIC_RECORDING`, означающий ровно «эта сборка объявляет `RECORD_AUDIO`», и закрыть строку им.

Имя выбрано не произвольно: в проекте уже есть семейство `DECLARES_SCREEN_CAPTURE`, `DECLARES_OVERLAY_PERMISSION`, `DECLARES_BATTERY_OPTIMIZATION` - и означают они именно это, «сборка объявляет разрешение», в отличие от `SUPPORT_*`, которые называют фичи. Значение флага - литерал на вариант (`false` только у `photos`), поэтому он ложится в `productFlavors`, а не в `androidComponents.onVariants`, где живут два флага с вычисляемым значением.

Состав правки:

1. `app_v2/build.gradle.kts` - `buildConfigField("boolean", "DECLARES_MIC_RECORDING", ..)` во все шесть вариантов: `true` у `standard`, `noLegal`, `lite`, `legacy`, `vr`; `false` у `photos`.
2. `PermissionRegistryRepositoryImpl.kt` - строка `record_audio` переходит на `buildGates = setOf("DECLARES_MIC_RECORDING")`, и имя добавляется в таблицу `buildGateValues`.
3. `PermissionManifestExemptions.kt` - запись `RECORD_AUDIO` в `declaredWithoutRow` удаляется: после правки строка есть везде, где есть объявление, и исключение становится мёртвым весом (правило 20).
4. `docs/FLAVOR_MATRIX.md` - перегенерировать, так как изменился блок `productFlavors`.

Пункт 2 из §0 (`photos` в списке вариантов теста паритета) уже сделан в S1454\S1460 - см. §1.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1442 - сузил снятие разрешения до `photos` и оставил это исключение; S0100 - ввёл флаг; S0429 - тот же класс дефекта с другой стороны (строка есть, объявления нет); S1454\S1460 - закрыли второй пункт §0 (`photos` в списке вариантов теста паритета).
- **Flavor scope:** затрагиваются все шесть вариантов - каждый получает новый флаг. Поведение меняется только у `lite`, где строка появляется; у `photos` флаг `false` и строка остаётся скрытой, как и должна.
- **Validation level:** тест паритета на `lite` и `photos` плюс компиляция обоих флаговых осей.

---

## 4. Проверка

1. `PermissionRegistryManifestParityTest` проходит на `lite`: до правки разрешение объявлено без строки и держится на исключении, после - строка есть, исключение удалено, и обе стороны теста сходятся без него.
2. `PermissionRegistryManifestParityTest` проходит на `photos`: флаг `false`, строки нет, объявления нет - расхождения нет ни в одну сторону.
3. Тест `no exemption outlives the divergence it excuses` проходит: удалённое исключение не оставляет мёртвой записи.
4. `PermissionRegistryRepositoryImplTest` проходит: `declaredBuildGateFields` остаётся подмножеством `mappedBuildGateFields`, то есть новое имя гейта разрешается таблицей, а не проваливается в `false`.
5. Компиляция `standard` и `noLegal` проходит.
6. `docs/FLAVOR_MATRIX.md` содержит строку `DECLARES_MIC_RECORDING`, а гейт соответствия матрицы документам проходит.

---

## Last Audit

**Date:** 2026-08-09
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Все шесть пунктов §4 закрыты прогонами, а не чтением кода:

1. `lite`, тест паритета - 3 теста, 0 падений (`testLiteDebugUnitTest`). Ключевое: `every declared permission is a registry row or a named exemption` проходит **без** исключения для `RECORD_AUDIO`, то есть строка в `lite` действительно появилась. Это и есть доказательство починки.
2. `photos`, тест паритета - 3 теста, 0 падений. Флаг `false`, строки нет, объявления нет.
3. `no exemption outlives the divergence it excuses` - зелёный на обоих вариантах, то есть удалённое исключение не оставило мёртвой записи.
4. `PermissionRegistryRepositoryImplTest` - 9 тестов, 0 падений на `photos`: новое имя гейта разрешается таблицей, а не проваливается в `false`.
5. Компиляция: `fk` exit 0 (`standard`), `fkn` exit 0 (`noLegal`).
6. `docs/FLAVOR_MATRIX.md` строка 31 - `DECLARES_MIC_RECORDING` со значением `[-]` только у `photos`; гейт `flavor-matrix-doc-gate` PASS.

Закрытие: `post-change: PASS`, exit 0, по всему набору из пяти файлов с `-ScopeToFile`.

**Один прогон был красным и это не был дефект.** Первый запуск тестов на `photos` вернул `BUILD FAILED` / exit 1, тогда как оба отчёта в `test-results` были зелёными. Повторный запуск на том же дереве вернул exit 0 без единой строки `FAILURE`. В то же время в дереве работали соседние сессии - `post-change` дважды отметил чужой живой `CODE.LOCK`. Красный отнесён к состязанию за общее дерево, а не к правке; выводом считается повторный прогон со свежими отчётами (13:24:58).

### Manual / on-device

- Проверки на устройстве не требуется: §4 полностью механическая, а тест паритета сверяет собранный манифест с реестром именно на тех двух вариантах, где поведение меняется.

### Запись в инвентарь возможностей

Записи в `docs/ALL_FEATURES.jsonl` намеренно нет. Правка не вводит возможность, а восстанавливает уже заинвентаризованную: экран разрешений и строка микрофона существуют и описаны, в `lite` строка была скрыта дефектом гейта. Запись на каждое исправление такого рода превратила бы инвентарь возможностей в журнал багфиксов.
