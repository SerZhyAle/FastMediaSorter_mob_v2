# Спецификация (compact bugfix): S1455 - общие тесты предполагают способности standard

**Ticket:** S1455
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1450

**Текст:**

`MediaFamilyResolverTest.testResolveDocument` fails on `lite` with `expected:<DOCUMENT> but was:<null>`. Observed 2026-08-07 in `app_v2/build/test-results/testLiteDebugUnitTest` on the S1450 tree, the first run in which any unit test could execute on that flavor at all.

This is not obviously a product bug: `docs/FLAVOR_MATRIX.md` records `SUPPORT_DOCUMENTS` as `[-]` on both `lite` and `photos`, so a build that resolves no document family may be behaving exactly as specified. The test lives in the shared `app_v2/src/test`, which is compiled and executed for every flavor, and asserts a capability only some flavors carry.

This is the behavioural sibling of the defect S1450 fixed. S1450 handled shared tests referencing a **type** that exists only in a flavor-scoped source set - those break compilation loudly. This is a shared test asserting **behaviour** that only some flavors have, which compiles everywhere and fails only where the capability is off. The compile-time variant was invisible because lite tests never ran; the behavioural variant was invisible for the same reason, and one run has now surfaced at least one instance.

Research needed, in this order:

1. Confirm the mechanism. Neither `MediaFamilyResolver` nor `MediaTypeUtils` reads `BuildConfig` directly (checked during S1450), so establish where document resolution actually becomes flavor-dependent before deciding anything - the assumption that `SUPPORT_DOCUMENTS` drives it is unverified.
2. Decide the rule: guard the assertion by capability, or move the test to a capability test set the way S1450 did for `src/testStreamingEnabled` / `src/testCloudEnabled`. Prefer whichever keeps the assertion running where the capability exists rather than deleting coverage.
3. Sweep for further instances. Only one surfaced in this run because 15 of the 20 lite failures were the unrelated DataStore defect (S1449) and 4 were the permission divergence (S1454); a run on `photos`, and a re-run of `lite` after S1449 and S1454 land, would expose any behavioural assumptions still hidden behind those.

Note the ordering constraint: `lite` cannot reach a green unit suite until S1449 (DataStore, 15 failures) and S1454 (permissions, 4 failures) are fixed, so this ticket's own verification predicate cannot be "the suite is green" - it has to name its own tests.

---

## 1. Проблема / симптом

`MediaFamilyResolverTest.testResolveDocument` падает на `lite` с `expected:<DOCUMENT> but was:<null>`. Тест лежит в общем `app_v2/src/test`, который компилируется и выполняется для каждого флейвора, а утверждает способность, которая есть не у всех.

---

## 2. Корневая причина

Механизм установлен, а не предположен, и он не тот, на который указывала формулировка захвата.

- Расхождение приходит не из `BuildConfig`: ни `MediaFamilyResolver`, ни `MediaTypeUtils` его не читают. Оно приходит из `OfficeDocumentFamilyCatalog` - объекта с одним и тем же полным именем, у которого **шесть разных копий** по флейворным каталогам исходников. В `lite` и `photos` это `emptySet()` / `emptyMap()`.
- `MediaTypeUtils` берёт оттуда `OFFICE_DOCUMENT_MIME_TYPES` и `OFFICE_DOCUMENT_EXTENSIONS`, поэтому в этих двух сборках `application/msword` и `text/rtf` не опознаются вовсе.
- Флаг `SUPPORT_DOCUMENTS` с этим механизмом не связан, а лишь совпадает с ним по распределению. Он читается только флейворными `MediaCapabilitiesModule` и потребляется `GetMediaFilesUseCase` при фильтрации просмотра - это другой путь.
- Это делает подход «загейтить утверждение по `BuildConfig`» непригодным: шва внедрения здесь нет вообще (статический объект читает статический объект), а флаг был бы лишь коррелятом, который однажды разойдётся с механизмом молча.
- Отдельно проверено, какие утверждения универсальны: `PDF` и `EPUB` в `MediaTypeUtils` опознаются безусловно, без обращения к флейворному каталогу. Падают только две строки из четырёх - офисные форматы.

---

## 3. Исправление

Переносится не файл целиком, а только флейворно-зависимые утверждения - по образцу капабилити-наборов, введённых S1450.

- Новый набор исходников `app_v2/src/testDocumentsEnabled/java`, монтируемый в те же четыре тестовых набора, где уже монтируются `testStreamingEnabled` и `testCloudEnabled`. Проверено по сгенерированной `docs/FLAVOR_MATRIX.md`: распределение `SUPPORT_DOCUMENTS` совпадает с `SUPPORT_STREAMS` посимвольно, то есть это ровно `standard`, `noLegal`, `legacy`, `vr` - готовый цикл, в который добавляется одна строка.
- Офисные утверждения уходят в новый класс `MediaFamilyResolverDocumentsTest` в этом наборе. Имя обязано отличаться: AGP сливает `src/test` и смонтированные наборы в одну компиляцию, и два файла с одним полным именем дали бы ошибку дублирующегося класса на каждом флейворе, который набор монтирует.
- В общем тесте остаются `PDF` и `EPUB` - они универсальны и продолжают выполняться на всех шести флейворах.

Почему не перенос файла целиком, как делал S1450: там каждый метод переносимого файла обращался к флейворному предмету, здесь - только один из пяти. Целиком перенеся файл, мы перестали бы гонять четыре универсальных теста на `lite` и `photos`, то есть потеряли бы покрытие ровно там, где §4 требует его сохранить.

### 3.3 Owner inputs (Approval gate)

- **Продуктового решения не требуется.** Выбор между переносом и гейтом разрешён отсутствием шва внедрения и предпочтением, уже записанным в §4 этого тикета.
- **Related tickets:** S1450 (починил compile-time вариант того же класса дефекта и проявил этот; его капабилити-наборы - переиспользуемый образец), S1453 (механический гейт на флейвор-область общих тестов; его область стоит расширить на объекты с одинаковым именем и разным содержимым - это класс, к которому относится данный дефект), S1449 и S1454 (остальные отказы того же прогона)

---

## 4. Проверка

- `MediaFamilyResolverTest` проходит на `lite` (все пять методов) и на `standard`.
- `MediaFamilyResolverDocumentsTest` выполняется и проходит на `standard`, где способность есть.
- Покрытие не удалено: офисные утверждения продолжают выполняться там, где формат поддержан.

---

## 5. Фазы

### Phase 01 - Вынести флейворно-зависимые утверждения в капабилити-набор

**Objective:** офисные форматы проверяются там, где они поддержаны, а универсальные утверждения продолжают выполняться на всех флейворах.

#### Step 01.1 - Смонтировать набор testDocumentsEnabled

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - начало фазы

**Prompt for developer:**

> In the existing `listOf("testStandard", "testNoLegal", "testLegacy", "testVr")` loop add `src/testDocumentsEnabled/java` next to the streaming and cloud test sets. Extend the comment above to say the new set carries tests whose subject is the per-flavor `OfficeDocumentFamilyCatalog`, and that those four flavors are exactly where `SUPPORT_DOCUMENTS` is on.

**Why:**

Общий `src/test` компилируется и выполняется для каждого флейвора, поэтому утверждение о формате, которого в сборке нет, может жить только в наборе, смонтированном туда, где формат есть.

**Verification:**

- `Grep` - `src/testDocumentsEnabled/java` присутствует в `build.gradle.kts`.

**Status:** `[x]` done

#### Step 01.2 - Перенести офисные утверждения

**Files:** `app_v2/src/testDocumentsEnabled/java/com/sza/fastmediasorter/ui/player/dispatch/MediaFamilyResolverDocumentsTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/dispatch/MediaFamilyResolverTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `MediaFamilyResolverDocumentsTest` in the new set holding the `application/msword` and `text/rtf` assertions. Leave `testResolveDocument` in the shared test with only the PDF and EPUB assertions, and add a comment saying where the office ones went and why. Do not reuse the original class name in the new set.

**Why:**

Опознание офисных форматов приходит из флейворной копии `OfficeDocumentFamilyCatalog`, пустой в `lite` и `photos`, тогда как `PDF` и `EPUB` опознаются безусловно и потому обязаны продолжать проверяться на всех шести флейворах.

**Verification:**

- Прогон `-Flavor Lite -Tests "*MediaFamilyResolver*"` - без отказов.
- Прогон на `standard` - выполняются оба класса.

**Status:** `[x]` done

#### Phase Done Criteria

- [x] Все шаги `[x] done`.
- [x] Прогон на `lite` и на `standard` без отказов.

---

## Last Audit

**Дата:** 2026-08-07. **Проведён:** `/spec-all`, Simple path, S4.

Эвиденс, и он же доказывает обе половины §4 - что падение ушло и что покрытие не удалено, а переехало:

- `standard`, XML от 13:57:19: `MediaFamilyResolverDocumentsTest` 2 теста / 0 отказов, `MediaFamilyResolverTest` 5 / 0.
- `lite`, XML от 13:57:32: `MediaFamilyResolverTest` 5 / 0, файла `MediaFamilyResolverDocumentsTest` нет вовсе.
- `post-change.ps1 -ScopeToFile -ChangeType Mixed` - exit 0, `post-change: PASS`, без advisories.

Существование самого XML нового класса на `standard` и его отсутствие на `lite` - это и есть доказательство, что набор `testDocumentsEnabled` смонтирован именно туда, куда задумано. Без этой проверки прогон выглядел бы зелёным и в случае, когда новый класс не подхватился вовсе: фильтр `*MediaFamilyResolver*` продолжал бы совпадать с общим тестом.

Разобранное при аудите:

- Перенесены ровно два флейворно-зависимых утверждения из пяти методов; четыре универсальных метода продолжают выполняться на `lite`, что и требовало §4 - целиком перенесённый файл эту проверку бы потерял.
- В новый класс добавлено третье утверждение, которого в исходном не было: разбор расширения при отсутствии mime-типа. Ветка отдельная от mime-ветки и до сих пор не покрывалась, а диспетчер часто получает из внешнего интента голое имя файла.
- Имя класса намеренно отличается от общего: AGP сливает `src/test` со смонтированными наборами в одну компиляцию, поэтому второй файл с тем же полным именем был бы ошибкой дублирующегося класса.

Остаточная работа, вне этого тикета:

- Третий пункт исходного плана - сплошной обход остальных общих тестов - выполнен исследованием структурно, а не наугад: сравнением одинаковых относительных путей по шести флейворным каталогам. Объектов с одним именем и разным содержимым в дереве всего четыре; кроме `OfficeDocumentFamilyCatalog` из общих тестов не адресуется ни один. То есть других экземпляров этого класса дефекта сейчас нет.
- Прогон на `photos` не выполнялся: механизм там тот же, что на `lite` (пустой каталог), и общий тест после правки не содержит утверждений, зависящих от каталога.

**Вердикт:** `Implemented`.
