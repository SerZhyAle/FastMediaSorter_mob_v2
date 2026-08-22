# Спецификация (compact bugfix): S1858 - Реестр фингерпринтов локалей не разделён по модулям

**Ticket:** S1858
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-20
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-20

**Захвачено во время:** /spec-prerelease, шаг 0.8

**Текст:**

Locale source-fingerprint registry is not namespaced by module, so app_v2 and wear collide and the ten-locale gate cannot be green for both modules at once.

Symptom: after importing translations for BOTH modules, `assert-new-lexemes-translated.ps1` (app_v2) reports exit 1 with exactly 6 keys - app_name, slideshow_interval, no_files_found, ssh_key_required, slideshow_settings, connection_test_not_supported - claiming they are "missing in: zh-Hans, hi, es, fr, ar, bn, pt, ur, de, it". All 6 keys are demonstrably PRESENT with correct values in all ten app_v2 locale files. Those 6 are precisely the key names that exist in both app_v2 and wear.

Root cause (proven): `scripts/quality/locale-source-fingerprints.json` keys entries by unitId `set|file|key` (e.g. `main|strings.xml|app_name`) with NO module segment. app_v2 and wear both have `src/main/res/values/strings.xml` with a key `app_name`, but different English text. Whichever module's bulk import ran last wins the slot.

Evidence 2026-08-20:
- registry slot `main|strings.xml|app_name` holds `2ce080a1a4d9b877`
- Get-EnglishStringFingerprint 'FastMedia Wear' (wear) = 2ce080a1a4d9b877
- Get-EnglishStringFingerprint 'Fast Media Sorter & Organizer' (app_v2) = 348517bad936ad61
- registry file mtime 23:04 = the wear bulk import
- `list-new-lexemes.ps1` Get-PresentKeys branch 3 flags a unit when `$fingerprints[$tag][$unitId] -ne $enHash`, so the app_v2 run compares app text against wear's stored hash and reports a false "untranslated".

Consequence: ping-pong. Re-importing the app_v2 six would overwrite the slot with app hashes and flip the WEAR gate red for the same 6 keys. Step 0.8 of /spec-prerelease therefore cannot reach exit 0 for both modules simultaneously, and it is a hard release blocker, so this blocks releasing at all.

Fix direction (not researched): namespace the unitId by module (e.g. `wear|main|strings.xml|app_name`) in `scripts/quality/lib/locale-fingerprints.ps1` and every consumer - `list-new-lexemes.ps1`, `locale-bulk-import.ps1`, `assert-new-lexemes-translated.ps1` - plus a migration for the existing 3.5 MB registry, which currently holds a mixture of app_v2 and wear hashes in shared slots and cannot be trusted per-module until rewritten.

Related: S1824 introduced the fingerprint check; S1628 pointed the gate at the wear module. Neither covers the collision between them.

---

## 1. Проблема / симптом

Реестр происхождения переводов адресует запись ключом `set|file|key`, в котором нет сегмента модуля. `app_v2` и `wear` - разные модули с собственными `src/main/res/values/strings.xml`, и у них совпадают имена 14 строковых ключей. Все 14 делят один и тот же слот реестра, поэтому импорт переводов одного модуля затирает происхождение другого.

Наблюдаемое поведение на 2026-08-20:

- `assert-new-lexemes-translated.ps1` для `app_v2` -> exit 1, шесть ключей объявлены непереведёнными во всех десяти best-effort локалях.
- Те же шесть ключей физически присутствуют с корректными значениями во всех десяти файлах локалей `app_v2`.
- `assert-new-lexemes-translated.ps1 -Module wear` -> exit 0.
- Повторный импорт шести ключей `app_v2` инвертирует картину: зелёным станет `app_v2`, красным - `wear`.

Проверка корневой улики выполнена заново в этом тикете и совпала с захваченной:

- слот `main|strings.xml|app_name` локали `de` хранит `2ce080a1a4d9b877`;
- `Get-EnglishStringFingerprint 'FastMedia Wear'` = `2ce080a1a4d9b877` - текст модуля `wear`;
- `Get-EnglishStringFingerprint 'Fast Media Sorter & Organizer'` = `348517bad936ad61` - текст модуля `app_v2`.

Шаг 0.8 `/spec-prerelease` запускает гейт для обоих модулей подряд и требует exit 0 от каждого, поэтому одновременно зелёными они быть не могут, и релиз заблокирован.

---

## 2. Корневая причина

Идентификатор единицы перевода собирается вручную в каждом потребителе, и ни один из них не подмешивает модуль, хотя параметр `-Module` есть у всех.

Места сборки идентификатора:

- `scripts/utils/list-new-lexemes.ps1:134` - форма `set|file|key` плюс необязательный слот.
- `scripts/utils/locale-bulk-import.ps1:239` - та же форма.
- `scripts/utils/set-android-string.ps1:557` и `:791` - форма `main|file|key`, где даже сегмент набора исходников зашит константой.
- `scripts/utils/set-android-string.ps1:651` и `:925` - переименование через `Rename-LocaleSourceFingerprint` на тех же строках.

`scripts/quality/lib/locale-fingerprints.ps1` к дефекту не причастна: она хранит отображение идентификатора в хеш и о формате идентификатора ничего не знает. Именно отсутствие единого дома у формата и позволило потребителям разойтись - `set-android-string.ps1` зашил `main` там, где остальные читают набор исходников из записи экспорта.

Дополнительно обнаружено при расследовании и не названо в захваченном материале:

- `scripts/post-change.ps1:556` - шестой потребитель, вызывает `list-new-lexemes.ps1` в информационном режиме.
- Общих имён ключей у модулей не шесть, а четырнадцать: `app_name`, `play`, `previous`, `next`, `no_files_found`, `slideshow_settings`, `slideshow_interval`, `save`, `connection_test_not_supported`, `cancel`, `loading`, `error`, `retry`, `ssh_key_required`. Шесть попали в отчёт лишь потому, что у них расходится английский текст. У остальных восьми текст совпадает, хеш совпадает тоже, и коллизия сегодня безвредна - но станет ложным срабатыванием в момент, когда любой из этих восьми текстов отредактируют в одном модуле.
- `$identity` в `list-new-lexemes.ps1:133` - тоже `set|file|key`, и именно он вычитается из `scripts/quality/locale-untranslated-baseline.txt`. Значит baseline живёт в том же неразделённом пространстве имён: запись, внесённая для `app_v2`, глушит одноимённый ключ и в `wear`. Сегодня это скрытый дефект - все 19 записей baseline относятся к ключам, которых в `wear` нет, - но он того же происхождения.

---

## 3. Исправление

Формат идентификатора получает сегмент модуля и единственный дом.

- Новая форма: `module|set|file|key`, со слотом - `module|set|file|key|slot`.
- Собирается только функцией `Get-LocaleUnitId` в `scripts/quality/lib/locale-fingerprints.ps1`. Ни один потребитель больше не склеивает строку сам.
- Та же функция обслуживает `$identity` для baseline, поэтому baseline тоже разделяется по модулям.

Реестр получает версию схемы, потому что молчаливая порча происхождения - ровно тот дефект, который тикет и закрывает.

- Верхнеуровневый ключ `__schema` со значением версии 2 и литеральным описанием формата идентификатора.
- `Get-LocaleSourceFingerprints` возвращает карту локалей без него, а версию отдаёт отдельной функцией.
- Гейт отказывается работать с реестром версии 1 кодом 2 - "не смог проверить", а не "нашёл дефект". Реестр версии 1, прочитанный кодом версии 2, дал бы ту же ложную непереведённость, только тише.

Миграция существующего реестра детерминирована почти везде, потому что почти каждый ключ принадлежит ровно одному модулю.

- Для каждого слота определяется множество модулей, объявляющих этот ключ в этом наборе исходников и файле.
- Один владелец - слот переименовывается без потерь. Это подавляющее большинство из 3.5 МБ.
- Ноль владельцев - слот удаляется как сирота: ключа больше нет ни в одном модуле.
- Два владельца - хеш сохраняется под тем модулем, чьему текущему английскому тексту он соответствует, и отбрасывается для второго. Восстановить затёртое значение нечем, а проставить хеш текущего текста означало бы объявить перевод свежим без доказательства.

Отброшенные записи миграция перечисляет поимённо. Их восстановление - один обычный bulk-round-trip, тот же, что гейт уже печатает в своей подсказке.

Файлы:

- `scripts/quality/lib/locale-fingerprints.ps1` - изменяется: `Get-LocaleUnitId`, версия схемы в загрузке и сохранении, `Get-LocaleFingerprintsSchemaVersion`.
- `scripts/utils/list-new-lexemes.ps1` - изменяется: идентификатор и единица перевода через хелпер.
- `scripts/utils/locale-bulk-import.ps1` - изменяется: единица перевода через хелпер.
- `scripts/utils/set-android-string.ps1` - изменяется: четыре места сборки идентификатора через хелпер.
- `scripts/quality/assert-new-lexemes-translated.ps1` - изменяется: отказ кодом 2 на реестре версии 1.
- `scripts/quality/migrate-locale-fingerprints-module.ps1` - новый: миграция реестра и baseline.
- `scripts/quality/locale-source-fingerprints.json` - перезаписывается миграцией.
- `scripts/quality/locale-untranslated-baseline.txt` - перезаписывается миграцией.
- `scripts/quality.tests/locale-fingerprints.Tests.ps1` - изменяется: покрытие нового формата и версии схемы.
- `docs/DEV_OPS.md` - изменяется: раздел "Thirteen locales" описывает разделение по модулям.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1824, S1628, S1627, S1626

---

## 4. Проверка

- `scripts/quality.tests/locale-fingerprints.Tests.ps1` -> exit 0.
- `assert-new-lexemes-translated.ps1` для `app_v2` -> exit 0, либо exit 1 со списком, состоящим только из ключей, отброшенных миграцией.
- `assert-new-lexemes-translated.ps1 -Module wear` -> exit 0.
- Оба предыдущих запуска дают свой результат одновременно, без повторного импорта между ними. Это и есть предмет тикета.
- Поиск по `scripts/` не находит ни одной сборки идентификатора вне `Get-LocaleUnitId`.
- Реестр содержит `__schema` версии 2, и ни один ключ локали не начинается с сегмента набора исходников.

### 4.1 Результат миграции (2026-08-21)

Классификация 48 025 слотов реестра:

- 47 325 переименованы без потерь - у единицы ровно один модуль-владелец.
- 220 сохранены под обоими модулями - общая единица, хеш совпал с текущим английским текстом каждого.
- 60 отброшены - общая единица, хеш не совпал; это шесть идентификаторов `app_v2` в десяти локалях.
- 420 отброшены как сироты - ключа больше нет ни в одном модуле (42 различных идентификатора).

Baseline: 18 записей из 19 получили префикс модуля; одна отброшена как сирота - `main|strings.xml|network_monitor_bandwidth_pair` не объявлен ни одним модулем ни в одном наборе исходников, то есть ключ удалён, а не переведён.

Шесть идентификаторов, потерявших происхождение и требующих одного bulk-round-trip:

- `app_v2|main|strings.xml|app_name`
- `app_v2|main|strings.xml|connection_test_not_supported`
- `app_v2|main|strings.xml|no_files_found`
- `app_v2|main|strings.xml|slideshow_interval`
- `app_v2|main|strings.xml|slideshow_settings`
- `app_v2|main|strings.xml|ssh_key_required`

### 4.2 Измеренные результаты (2026-08-21)

- `scripts/quality.tests/locale-fingerprints.Tests.ps1` -> expected: 0 | actual: 0 (PASS 25, FAIL 0).
- Гейт на реестре версии 1 -> expected: 2 | actual: 2, с именем миграции в сообщении.
- `assert-new-lexemes-translated.ps1 -Module wear` -> expected: 0 | actual: 0.
- `assert-new-lexemes-translated.ps1` для `app_v2` -> actual: 1, ровно шесть идентификаторов из 4.1 и ни одного сверх них. Это отброшенное происхождение, а не регрессия.
- Оба запуска выполнены подряд без импорта и правок между ними.
- Симуляция на копии реестра, где шесть идентификаторов `app_v2` проставлены заново: оба модуля одновременно -> expected: 0 | actual: 0. Пинг-понг устранён - до исправления это состояние было недостижимо.
- `assert-exit-contract.ps1` -> expected: 0 | actual: 0.
- `assert-script-cheatsheet-sync.ps1` -> expected: 0 | actual: 0.

---

## Last Audit

**Дата:** 2026-08-21
**Режим:** /spec-code (без устройства - тикет не поставляет ничего в APK)
**Вердикт:** Verified

Проверено:

- Все 11 шагов трёх фаз выполнены и верифицированы предикатами из спецификации.
- Сборка идентификатора существует ровно в одном месте: `Get-LocaleUnitId`. Поиск конкатенаций по `scripts/` пуст.
- Шесть потребителей реестра согласованы: пять через хелпер, шестой (`post-change.ps1`) - транзитивно через продьюсера.
- `-Module` объявлен обязательным, поэтому пропуск параметра - ошибка привязки, а не тихий возврат к старому формату. Проверено на живом вызове.
- Видимость параметра `-Module` внутри функций `set-android-string.ps1` подтверждена запуском харнесса той же формы, а не рассуждением.
- Гейт отказывается кодом 2 на реестре версии 1 и называет миграцию.

Открытых вопросов нет. Расхождений между спецификацией и кодом не найдено.

---

## Фазы

### Phase 01 - Single-home the identity and namespace it by module

**Objective:** the unit identity carries the module, is built in exactly one place, and the registry declares its schema version.

**Files touched:**

- `scripts/quality/lib/locale-fingerprints.ps1` - modified, <= 260 LOC.
- `scripts/utils/list-new-lexemes.ps1` - modified, <= 240 LOC.
- `scripts/utils/locale-bulk-import.ps1` - modified, <= 265 LOC.
- `scripts/utils/set-android-string.ps1` - modified, <= 960 LOC.
- `scripts/quality/assert-new-lexemes-translated.ps1` - modified, <= 140 LOC.

#### Step 01.1 - Add the identity builder and the schema version to the library

**Files:** `scripts/quality/lib/locale-fingerprints.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `Get-LocaleUnitId` taking `-Module`, `-Set`, `-File`, `-Key` and an optional `-Slot`, returning the module-qualified identity with the slot appended when supplied. Make `-Module` mandatory so a caller cannot omit it and silently rebuild the old format. Add a script-scoped schema version constant set to 2 and `Get-LocaleFingerprintsSchemaVersion`, reading the version captured at load time and returning 1 when the marker is absent.

**Why:**

The format drifted precisely because five call sites each concatenated it by hand, and `set-android-string.ps1` hardcoded a source-set segment the others read from the export record; giving the format one home is what stops the next consumer from diverging again.

**Verification:**

- `Grep` - `function Get-LocaleUnitId` matches exactly once in the library.
- `Grep` - `function Get-LocaleFingerprintsSchemaVersion` matches exactly once.
- Dot-source the library and confirm the builder returns `wear|main|strings.xml|app_name` for that input.
- Dot-source and confirm omitting `-Module` raises a parameter-binding error rather than returning a string.

**Status:** `[x]` done

---

#### Step 01.2 - Carry the schema marker through load and save

**Files:** `scripts/quality/lib/locale-fingerprints.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `Get-LocaleSourceFingerprints`, skip any top-level key beginning with a double underscore when building the locale map, and stash the parsed schema version so `Get-LocaleFingerprintsSchemaVersion` can report it. In `Save-LocaleSourceFingerprints`, always emit the schema marker with version 2 and the literal identity format before the locale entries.

**Why:**

A version 1 registry read by version 2 code produces exactly the false-untranslated report this ticket exists to remove, only without the loud slot collision to explain it, so the file must state its own format rather than leave the reader to infer it.

**Verification:**

- Save a two-locale map to a temp path, then `Grep` the written file for the schema marker and for version 2.
- Load that file back and confirm the returned map has no schema key and its locale count is 2.
- Load a hand-written map with no marker and confirm the version function returns 1.

**Status:** `[x]` done

---

#### Step 01.3 - Route list-new-lexemes through the builder

**Files:** `scripts/utils/list-new-lexemes.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the hand-built identity and unit id with `Get-LocaleUnitId`, passing the script's own module parameter. Keep the identity slot-free and the unit id slot-bearing exactly as now; only the module segment is new. The reporting line and the baseline subtraction both consume the identity, so both become module-qualified together.

**Why:**

This script's third comparison branch is where the collision surfaces as a false "untranslated", and its baseline subtraction shares the same unqualified identity space, so an app_v2 baseline entry silences the same key name in wear.

**Verification:**

- `Grep` - no hand-built identity concatenation remains in the file.
- `Grep` - `Get-LocaleUnitId` matches at least twice.
- Run the script against the wear module with an absent baseline and confirm it completes with exit 0 or 3, not a binding error.

**Status:** `[x]` done

---

#### Step 01.4 - Route locale-bulk-import through the builder

**Files:** `scripts/utils/locale-bulk-import.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Replace the hand-built unit id in the fingerprint-stamping loop with `Get-LocaleUnitId`, passing the script's module parameter. The existing fallback to the source-set parameter when a record carries no set stays as it is.

**Why:**

This is the writer whose last run wins the shared slot, so leaving it unqualified would keep overwriting the other module's provenance no matter how carefully the readers are fixed.

**Verification:**

- `Grep` - no hand-built unit-id concatenation remains in the file.
- `Grep` - `Get-LocaleUnitId` matches exactly once.

**Status:** `[x]` done

---

#### Step 01.5 - Route the four set-android-string sites through the builder

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Replace the four hardcoded identities with `Get-LocaleUnitId`, passing the script's module parameter and the literal source set this script already operates on. Two are fingerprint updates, two are rename old/new pairs. Take the backup CLAUDE.md Rule 5 requires before editing - the file is over 500 LOC.

**Why:**

This consumer hardcodes the source-set segment the others read from the export record, which is the concrete evidence that a format with no single home drifts per call site.

**Verification:**

- `Grep` - the hardcoded identity prefix matches zero times in the file.
- `Grep` - `Get-LocaleUnitId` matches at least four times.
- All four sites sit inside functions, so prove the script parameter reaches them rather than assuming it: run a harness of the same shape - a function at script scope reading the script's own `-Module` - with `-Module wear`, and confirm it returns a `wear`-prefixed identity rather than the `app_v2` default.

**Status:** `[x]` done

---

#### Step 01.6 - Refuse a superseded registry at the gate

**Files:** `scripts/quality/assert-new-lexemes-translated.ps1`
**Depends on:** Step 01.5

**Prompt for developer:**

> Before invoking the producer, load the registry and read its schema version. When it is below 2, write an error naming the migration script as the repair and exit 2. Document the new condition under the existing exit-code contract in the comment header.

**Why:**

The gate's own contract already separates "found a defect" from "could not verify", and a registry in the superseded format is the second of those - it cannot tell a real gap from a format mismatch.

**Verification:**

- `Grep` - the new branch exits 2 and the header lists the condition.
- Point the gate at a superseded fixture registry and confirm exit 2 with the migration script named in the output.

**Status:** `[x]` done

---

### Phase 02 - Migrate the registry and the baseline

**Objective:** both data files carry module-qualified identities, and every dropped record is named.

**Files touched:**

- `scripts/quality/migrate-locale-fingerprints-module.ps1` - new, <= 220 LOC.
- `scripts/quality/locale-source-fingerprints.json` - rewritten by the migration.
- `scripts/quality/locale-untranslated-baseline.txt` - rewritten by the migration.

#### Step 02.1 - Write the migration

**Files:** `scripts/quality/migrate-locale-fingerprints-module.ps1`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> Build an ownership index by scanning each module's resource values directory and collecting which modules declare each set, file and key. Rewrite every registry slot: one owner renames losslessly; zero owners drops as an orphan; two owners keeps the hash only under the module whose current English text fingerprints to that same hash, and drops it for the other. Prefix each baseline line with its single owning module, leaving comment lines untouched. Print counts for renamed, orphaned and ambiguous-dropped, list every dropped identity, and support a dry run. Give the script a reachable exit-code contract in its header per CLAUDE.md Rule 7.

**Why:**

The existing 3.5 MB registry holds a mixture of app_v2 and wear hashes in shared slots and cannot be trusted per module until rewritten, and the overwritten half of an ambiguous slot is unrecoverable, so the migration must drop it rather than assert a freshness it cannot prove.

**Verification:**

- `Glob` - the script exists.
- The dry run exits 0, prints counts, and writes nothing.
- `scripts/quality/assert-exit-contract.ps1` covering the new script -> exit 0.

**Status:** `[x]` done

---

#### Step 02.2 - Run the migration and record what it dropped

**Files:** `scripts/quality/locale-source-fingerprints.json`, `scripts/quality/locale-untranslated-baseline.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the migration for real. Confirm the registry now carries the schema marker at version 2 and that no locale key begins with a bare source-set segment. Confirm the baseline's 19 entries are all module-prefixed. Record the dropped identities in section 4 of this spec so the follow-up import is a named list, not a rediscovery.

**Why:**

The dropped records are the ticket's only visible cost and their repair is one bulk round trip, so they have to be enumerated where the owner will read them rather than left in a console scrollback.

**Verification:**

- `Grep` - the schema marker is present in the registry at version 2.
- `Grep` - zero registry keys begin with a bare source-set segment.
- `Grep` - 19 non-comment baseline lines, all module-prefixed.

**Status:** `[x]` done

---

### Phase 03 - Prove both modules green at once

**Objective:** the two gates hold their verdicts simultaneously, and the tooling docs describe the module split.

**Files touched:**

- `scripts/quality.tests/locale-fingerprints.Tests.ps1` - modified, <= 160 LOC.
- `docs/DEV_OPS.md` - modified.
- `docs/SCRIPT_CHEATSHEET.md` - regenerated.

#### Step 03.1 - Cover the new format in the library tests

**Files:** `scripts/quality.tests/locale-fingerprints.Tests.ps1`
**Depends on:** Phase 02 complete

**Prompt for developer:**

> Update the existing identities to the module-qualified form and add cases for the builder with and without a slot, for the mandatory module parameter, for the schema round trip, and for the version function returning 1 on a markerless map. Add the case this ticket is about: two modules stamping the same set, file and key with different hashes both survive and neither overwrites the other.

**Why:**

The collision survived two tickets that each touched this registry because no test ever stamped the same key name from two modules, which is the one assertion that would have failed.

**Verification:**

- `scripts/quality.tests/locale-fingerprints.Tests.ps1` -> exit 0.
- `Grep` - the two-module non-collision case is present by name.

**Status:** `[x]` done

---

#### Step 03.2 - Run both gates without an import between them

**Files:** none - verification only
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the gate for app_v2 and then for wear, back to back, with no import or edit in between. Record both exit codes. app_v2 may legitimately report the identities Phase 02 dropped; anything beyond that list is a regression in this ticket, not a pre-existing gap.

**Why:**

The defect is defined as the two modules being unable to hold a verdict at the same time, so the proof has to be the two runs adjacent with nothing between them.

**Verification:**

- The wear invocation exits 0.
- The app_v2 invocation exits 0, or exits 1 with a key list that is a subset of the Phase 02 dropped list.

**Status:** `[x]` done

---

#### Step 03.3 - Describe the module split where the loop is documented

**Files:** `docs/DEV_OPS.md`, `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> In the "Thirteen locales" section of `docs/DEV_OPS.md`, state that provenance is tracked per module, that the gate runs once per module, and that a registry predating the split is refused with exit 2 until migrated. Regenerate the cheatsheet so the new script appears.

**Why:**

That section is the documented home of this loop and currently describes a single shared registry, which is the model the ticket replaces.

**Verification:**

- `Grep` - `docs/DEV_OPS.md` mentions per-module provenance in the "Thirteen locales" section.
- `scripts/quality/assert-script-cheatsheet-sync.ps1` -> exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `scripts/quality.tests/locale-fingerprints.Tests.ps1` exits 0.
- [ ] Both gate invocations hold their verdicts back to back.
- [ ] Dev log entry added via `scripts/add_to_dev_log.ps1`.
- [ ] No gradle target is required - this ticket ships nothing in an APK.

---

## Rollback Plan

Both migrated data files are version-controlled, so the rollback needs no side copy: revert `scripts/quality/locale-source-fingerprints.json`, `scripts/quality/locale-untranslated-baseline.txt` and the five changed scripts to their committed state, and the pre-split behaviour returns intact. No user-facing surface and no resource file is touched, so nothing reaches a build. The migration is one-way by design - it drops provenance it cannot prove - which is why reverting the data files matters more here than re-running the migration backwards.
