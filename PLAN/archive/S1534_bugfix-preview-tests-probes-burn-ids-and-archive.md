# Спецификация: S1534 - Пробы preview.tests безвозвратно сжигают id спек и оседают в архивном журнале

**Ticket:** S1534
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** S1490

**Текст:**

scripts/spec_catalog/preview.tests/Run-Tests.ps1 allocates a real ticket id via next-id.ps1 for each of its 4 throwaway probe specs per run, then cleans them up with delete.ps1 - which is a SOFT delete: the record leaves PLAN/spec-catalog.jsonl but lands in PLAN/spec-catalog-archive.jsonl with status Archived, and select.ps1 -Id reads it back from there. Two costs, both permanent per run:

1. Four spec ids are burned for good. Never reused, because next-id.ps1 must not hand back an id present in the archive. Measured on 2026-08-08 during S1490: three harness runs consumed S1522-S1533 - twelve ids, none of which will ever name a real ticket.
2. The archive journal grows by 4 junk records named preview-tests-probe* every run. 13 such records were already there before that day's runs; the archive went 1238 -> 1242 lines on a single run.

The id burn is not merely untidy - it races real work. During S1490 next-id.ps1 returned S1526 for a genuine parked draft, a harness run consumed that id before insert.ps1 was called, and the insert died with "Duplicate id 'S1526'". Anything that allocates an id and does not insert within the same instant is exposed, which under parallel sessions (S1437) is routine.

The whole-journal restore that S1490 removed never covered any of this - it only ever restored spec-catalog.jsonl, not the archive, and it could not un-burn an id. This is a pre-existing leak S1490 surfaced rather than caused.

No CLI can currently undo it: delete.ps1 takes only -Id/-Confirm and is itself the soft delete, and hand-editing the journals is forbidden (CLAUDE.md Rule 12). A fix needs a decision first - either a hard-purge capability in the spec_catalog CLI restricted to archived records, or a hermetic probe mechanism for the harness that never enters the real catalog and never calls next-id.ps1 - plus a one-off cleanup of the records already there.

---

## 1. Проблема / симптом

Замерено 2026-08-09 на рабочем дереве:

- В `PLAN/spec-catalog-archive.jsonl` лежит **21** мусорная запись `preview-tests-probe*` при общем размере журнала 1246 строк. Каждый прогон харнесса добавляет ещё 4.
- Сожжены 21 id: `S1074`, `S1485`-`S1488`, `S1491`-`S1494`, `S1522`-`S1533`. Ни один никогда не назовёт настоящий тикет.
- `select.ps1 -Id S1526` отвечает пробой-фикстурой, а не тикетом: поиск «что это за id» даёт ложный ответ.
- Гонка за id уронила настоящую вставку: во время S1490 `next-id.ps1` вернул `S1526` для реального припаркованного драфта, прогон харнесса занял этот id раньше вызова `insert.ps1`, и вставка умерла с `Duplicate id 'S1526'`.

Первые три пункта - гигиена, четвёртый - дефект корректности: тестовый харнесс ломает продуктовую операцию соседней сессии.

---

## 2. Корневая причина

Композиция трёх механизмов, каждый из которых по отдельности корректен:

- `preview.tests/Run-Tests.ps1::New-Probe` (строка 90) вызывает настоящий `next-id.ps1`, затем настоящий `insert.ps1`. `_lib.ps1` жёстко задаёт `$script:CatalogPath` и `$script:ArchivePath` от `$PSScriptRoot` (строки 11 и 14) без единого переопределения, поэтому любой дочерний процесс CLI, порождённый харнессом, пишет в продуктовые журналы.
- `delete.ps1` - мягкое удаление: `Add-ArchiveRecord` дописывает запись в архив со статусом `Archived` (строки 51-69), а `Find-Record` (`_lib.ps1` 519-530) при промахе по активному журналу читает архив. Строка остаётся видимой навсегда.
- `New-CatalogId` (`_lib.ps1` 504-517) намеренно сканирует архив - «an archived id must never be reissued». Поэтому архивированный id пробы не возвращается в пространство имён никогда.

Дефект не в одном из трёх, а в том, что тестовый харнесс стоит на продуктовой стороне всех трёх сразу.

---

## 3. Исправление

### 3.1 Развилка закрыта архитектурой

§0 оставлял выбор владельцу: жёсткое удаление в CLI или герметичный харнесс. Выбор механически определён и владельца не требует.

**Жёсткое удаление отклонено.** Оно добавляет необратимую операцию в продуктовый инструмент, чтобы компенсировать дефект теста - зависимость переворачивается: продукт получает footgun ради неаккуратности теста. Rule 12 запрещает править журналы руками именно потому, что записи не должны исчезать; ключ `-Purge` открывает ту же дверь с парадного входа. И главное - гонку это не чинит: харнесс продолжал бы тянуть настоящие id во время прогона.

**Герметичная песочница принята.** Она снимает все четыре симптома разом: id не сжигается, архив не растёт, гонки нет, `select.ps1` не отвечает фикстурой. И она следует шву, который архитектура уже документирует: `_lib.ps1` строка 391 содержит `$env:FMS_SKIP_RELEASE_QUEUE`, а `SCHEMA.md` строка 126 называет его назначение - «tests, **alternate-catalog runs**». Прогон против альтернативного каталога уже предусмотрен; отсутствует лишь вторая половина шва - перенаправление самих журналов.

**Ценность харнесса сохраняется.** Кейсы A/B/C/E/F читают живые спеки, и заголовок харнесса (строки 22-32) объявляет это намеренным. Песочница - **снимок-копия** живых журналов, поэтому чтение остаётся настоящим; перенаправляется только запись.

### 3.2 Состав

1. Шов путей журналов в `_lib.ps1`: `$env:FMS_SPEC_CATALOG_DIR` переопределяет `$script:CatalogPath` и `$script:ArchivePath`. `$script:RepoRoot` не трогается, поэтому файлы спек продолжают резолвиться из настоящего `PLAN/`.
2. Переписка харнесса: снимок двух журналов в `temp/scratch/spec-catalog-sandbox-<pid>/`, экспорт `FMS_SPEC_CATALOG_DIR` и `FMS_SKIP_RELEASE_QUEUE`, снос песочницы в `finally`.
3. Фиксированный блок id проб `S9991`-`S9994`, передаваемый в `insert.ps1 -Id`. `next-id.ps1` не вызывается вовсе, и ни один id пробы не может совпасть с id, выделенным параллельной сессией, - значит имя файла `PLAN/S999x_preview-tests-probe*.md` тоже не столкнётся.
4. Проверка утечки в самом харнессе: после очистки `select.ps1 -Id S999x` против **настоящего** каталога обязан вернуть пустоту, а настоящий архив - содержать ноль строк `preview-tests-probe`. Регрессия становится самозаявляющей.
5. Разовая уборка: `scripts/spec_catalog/purge-probe-records.ps1` в форме `migrate-archive-split.ps1` - узкий, идемпотентный. Удаляет только строки, у которых одновременно `status = Archived` и `name` совпадает с `^preview-tests-probe`. Страховка: `New-CatalogId` считается до и после; если значение изменилось, скрипт откатывается и падает, поэтому уборка физически не может вернуть id в оборот.
6. Реестр сожжённых id `PLAN/spec-catalog-burned-ids.jsonl` (добавлено по ходу реализации, см. ниже). `New-CatalogId` берёт максимум и по нему тоже, а `validate.ps1` вычитает зарегистрированные id из списка дыр `Monotonicity`.
7. Документация: `SCHEMA.md` получает описание `FMS_SPEC_CATALOG_DIR` рядом с существующей строкой про `FMS_SKIP_RELEASE_QUEUE`; `docs/SCRIPT_CHEATSHEET.md` перегенерируется как порождаемый артефакт.

**Почему появился пункт 6.** Первый прогон уборки прошёл, но `validate.ps1` сменил `Monotonicity` с OK на WARN с 21 дырой в id-последовательности. Это не косметика: проверка существует ровно для того, чтобы поймать потерянную запись, и 21 постоянная известная дыра похоронила бы настоящую потерю. Уборка, ломающая гейт, - не уборка, поэтому реестр входит в скоуп (Rule 13 - чинить инструмент, а не обходить). Он же закрывает более общую дыру: удаление записи было единственным способом вернуть id в оборот, и `New-CatalogId` теперь структурно на это не способен.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1490 - снял восстановление журнала целиком в этом же харнессе и обнажил утечку; S1073 - ввёл харнесс и механику проб через реальный CLI; S1437 - разрешил параллельные сессии, из-за чего гонка за id стала обыденной; S0454 - разделил журнал на активный и архивный.
- **Требуется решение владельца:** нет. Развилка §0 закрыта в §3.1 архитектурой (`SCHEMA.md` строка 126 + `_lib.ps1` строка 391), а не вкусовым выбором.
- **Чувствительный охват:** отсутствует. Ни пользовательской поверхности, ни строк, ни разрешений, ни флэйворов, ни публикуемых артефактов - только внутренний инструментарий каталога.

---

## 4. Проверка

- `pwsh -NoProfile -File scripts/spec_catalog/preview.tests/Run-Tests.ps1` завершается с кодом 0.
- До и после этого прогона `New-CatalogId` возвращает одно и то же значение - ни один id не сожжён.
- До и после прогона число строк в `PLAN/spec-catalog-archive.jsonl` совпадает - архив не вырос.
- `Select-String -Path PLAN/spec-catalog-archive.jsonl -Pattern 'preview-tests-probe'` даёт ноль совпадений после уборки.
- `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` завершается с кодом 0 после уборки, и проверка `Monotonicity` остаётся `OK` - дыры объяснены реестром, а не замолчаны.
- Повторный запуск `purge-probe-records.ps1` сообщает «nothing to purge» и выходит с кодом 0 - идемпотентность.
- Предикат утечки различает в обе стороны: существующий id даёт `leak=True`, зарезервированный и вычищенный - `leak=False`. Проверка, которая не умеет падать, ничего не доказывает.

---

## 5. Фазы

### Phase 01 - Hermetic sandbox seam and harness rewrite

**Objective:** the harness stops writing to the production journals; probe ids come from a fixed reserved block instead of `next-id.ps1`.

**Files:** `scripts/spec_catalog/_lib.ps1` (Modified, ≤ 600), `scripts/spec_catalog/preview.tests/Run-Tests.ps1` (Modified, ≤ 330)

---

#### Step 01.1 - Add the journal-path override to `_lib.ps1`

**Files:** `scripts/spec_catalog/_lib.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> After the existing `$script:ArchivePath` assignment, redirect both journal paths when `$env:FMS_SPEC_CATALOG_DIR` is set: resolve `spec-catalog.jsonl` and `spec-catalog-archive.jsonl` under that directory instead of `PLAN/`. Leave `$script:RepoRoot` and every release-queue path untouched. Throw a clear error when the variable names a directory that does not exist, so a typo fails loudly instead of silently creating a second production journal.

**Why:**

Every CLI child process the harness spawns writes the production journals because `_lib.ps1` hardcodes both paths from `$PSScriptRoot` with no override (§2); redirecting them is the one change that makes an alternate-catalog run - already named as a supported mode in `SCHEMA.md` - actually possible.

**Verification:**

- `Grep` - `FMS_SPEC_CATALOG_DIR` matches in `scripts/spec_catalog/_lib.ps1`.
- Shell - with the variable set to a temp directory holding copies of both journals, `select.ps1 -Id S0001 -Format json` resolves from the copy; unset, it resolves from `PLAN/`.
- Shell - with the variable set to a non-existent directory, any catalog script exits non-zero with a message naming the variable.

**Status:** `[x]` done

---

#### Step 01.2 - Run the harness inside a sandbox snapshot

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Before the first case, create `temp/scratch/spec-catalog-sandbox-<pid>/`, copy `PLAN/spec-catalog.jsonl` and `PLAN/spec-catalog-archive.jsonl` into it, then set `$env:FMS_SPEC_CATALOG_DIR` to that directory and `$env:FMS_SKIP_RELEASE_QUEUE` to `1` for the rest of the run. Remove the directory and clear both variables in the existing `finally` block. Rewrite the header note: the suite still reads live catalog data, because the sandbox is a snapshot of it, but no longer writes the real journals.

**Why:**

The snapshot keeps cases A/B/C/E/F reading real specs - the value the header calls deliberate - while diverting every write, so a test harness stops standing on the production side of the journal (§3.1).

**Verification:**

- `Grep` - `FMS_SPEC_CATALOG_DIR` and `FMS_SKIP_RELEASE_QUEUE` both match in the harness.
- Shell - `Run-Tests.ps1` exits 0, and `PLAN/spec-catalog-archive.jsonl` has the same line count before and after.
- Shell - `temp/scratch/` holds no `spec-catalog-sandbox-*` directory after the run.

**Status:** `[x]` done

---

#### Step 01.3 - Allocate probe ids from a fixed reserved block

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the `next-id.ps1` call in `New-Probe` with a fixed id drawn from `S9991`-`S9994`, one per probe, passed to `insert.ps1 -Id`. Drop the `$nextIdPs1` variable. Record in the header why the block is fixed rather than allocated.

**Why:**

An id allocated from the live counter is what raced a genuine insert into `Duplicate id 'S1526'` during S1490 (§1), and a fixed block far above the live maximum can collide with neither a concurrent allocation nor the `PLAN/` filename it produces.

**Verification:**

- `Grep` - `next-id.ps1` returns zero hits in `scripts/spec_catalog/preview.tests/Run-Tests.ps1`.
- `Grep` - `S9991` matches in that file.
- Shell - `New-CatalogId` returns the same value before and after a full harness run.

**Status:** `[x]` done

---

#### Step 01.4 - Assert no leak into the real journals

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> At the end of the `finally` block, after the sandbox is torn down and the environment variables are cleared, assert against the real journals: `select.ps1` finds none of the probe ids, and `PLAN/spec-catalog-archive.jsonl` contains no `preview-tests-probe` row. Fail the run naming the offending id when either check trips.

**Why:**

The whole defect was invisible for 13 archive records before anyone measured it (§1), so the harness must report its own leak rather than wait for the next audit to notice.

**Verification:**

- `Grep` - `preview-tests-probe` appears in an assertion inside the `finally` block.
- Shell - `Run-Tests.ps1` exits 0 and prints the leak check as passing.

**Status:** `[x]` done

---

### Phase 02 - One-off cleanup of the records already in the archive

**Objective:** the 21 probe rows leave the archive journal without any id changing hands.

**Files:** `scripts/spec_catalog/purge-probe-records.ps1` (New, ≤ 90)

---

#### Step 02.1 - Write the narrow purge script

**Files:** `scripts/spec_catalog/purge-probe-records.ps1`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> Model it on `migrate-archive-split.ps1`: dot-source `_lib.ps1`, take `Enter-CatalogLock`, read the archive journal, and drop only rows that are simultaneously `status = Archived` and whose `name` matches `^preview-tests-probe`. Compute `New-CatalogId` before and after the rewrite; if the two differ, restore the original archive and exit non-zero. Print the removed count and exit 0 with "nothing to purge" when the set is empty. Document the exit codes in the header per CLAUDE.md section 7.

**Why:**

Hand-editing the journals is forbidden by Rule 12, and the general-purpose purge CLI was rejected in §3.1, so the residue needs a single-purpose script whose guard makes it structurally incapable of returning an id to the namespace.

**Verification:**

- `Glob` - `scripts/spec_catalog/purge-probe-records.ps1` exists.
- `Grep` - `Enter-CatalogLock` and `New-CatalogId` both match in it.
- Shell - the script exits 0 and reports 21 removed.
- Shell - a second run exits 0 and reports nothing to purge.

**Status:** `[x]` done

---

#### Step 02.2 - Run the cleanup and validate the journals

**Files:** `PLAN/spec-catalog-archive.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the purge script once, then `validate.ps1`. Record the archive line count and the `New-CatalogId` value before and after in the run output.

**Why:**

The archive carries 21 junk rows that make `select.ps1 -Id` answer an id lookup with a fixture (§1), and only a validated journal proves the removal did not break the schema, uniqueness or monotonicity checks.

**Verification:**

- Shell - `Select-String -Pattern 'preview-tests-probe'` over the archive returns zero matches.
- Shell - `validate.ps1` exits 0.
- Shell - `New-CatalogId` is unchanged from before the purge.

**Status:** `[x]` done

---

#### Step 02.3 - Register burned ids so the purge cannot mute a gate

**Files:** `scripts/spec_catalog/_lib.ps1`, `scripts/spec_catalog/purge-probe-records.ps1`, `scripts/spec_catalog/validate.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `PLAN/spec-catalog-burned-ids.jsonl` with `Read-BurnedIds` / `Add-BurnedIds` in `_lib.ps1`, redirected by `FMS_SPEC_CATALOG_DIR` alongside the journals. Make `New-CatalogId` take its maximum over the registry as well as both journals. Have the purge write the registry before rewriting the archive. Have `validate.ps1` subtract registered ids from the `Monotonicity` gap list and report how many were accounted for.

**Why:**

The purge turned `Monotonicity` from OK into a permanent 21-hole WARN, and that check exists to catch a lost record - burying it under known holes destroys the only signal it carries, so the cleanup would have traded one defect for another.

**Verification:**

- Shell - `validate.ps1` reports `Monotonicity` as `OK` naming the accounted-for count.
- Shell - `New-CatalogId` is unchanged across a restore-and-repurge cycle.
- `Grep` - `Read-BurnedIds` matches in `_lib.ps1`, `purge-probe-records.ps1` and `validate.ps1`.

**Status:** `[x]` done

---

### Phase 03 - Documentation sync

**Objective:** the new seam and the new script are discoverable where the catalog is documented.

**Files:** `scripts/spec_catalog/SCHEMA.md` (Modified, ≤ 200)

---

#### Step 03.1 - Document the seam and regenerate the cheatsheet

**Files:** `scripts/spec_catalog/SCHEMA.md`
**Depends on:** Phase 02 complete

**Prompt for developer:**

> Add `$env:FMS_SPEC_CATALOG_DIR` to `SCHEMA.md` beside the existing `FMS_SKIP_RELEASE_QUEUE` sentence, stating that it redirects both journals and that spec files still resolve from the real `PLAN/`. Mention `purge-probe-records.ps1` as the one-off maintenance script. Then regenerate `docs/SCRIPT_CHEATSHEET.md` via `scripts/utils/help.ps1 -Generate`.

**Why:**

`SCHEMA.md` already advertises alternate-catalog runs as a supported mode without saying how to point at an alternate catalog (§3.1), and `SCRIPT_CHEATSHEET.md` is a generated target whose drift is gated by `assert-script-cheatsheet-sync.ps1`.

**Verification:**

- `Grep` - `FMS_SPEC_CATALOG_DIR` matches in `scripts/spec_catalog/SCHEMA.md`.
- `Grep` - `purge-probe-records.ps1` matches in `docs/SCRIPT_CHEATSHEET.md`.
- Shell - `scripts/quality/assert-script-cheatsheet-sync.ps1` exits 0.

**Status:** `[x]` done

---

## Last Audit

**Дата:** 2026-08-09. **Вердикт:** Verified.

**Что в коде:**

- `scripts/spec_catalog/_lib.ps1` - `$env:FMS_SPEC_CATALOG_DIR` перенаправляет активный журнал, архив и реестр сожжённых id; несуществующая директория - жёсткая ошибка. `Read-BurnedIds` / `Add-BurnedIds`; `New-CatalogId` берёт максимум и по реестру.
- `scripts/spec_catalog/preview.tests/Run-Tests.ps1` - снимок журналов в `temp/scratch/spec-catalog-sandbox-<pid>/`, пробы из блока `S9991`-`S9994`, `next-id.ps1` не вызывается, проверка утечки против настоящих журналов в `finally` после снятия редиректа.
- `scripts/spec_catalog/purge-probe-records.ps1` - новый, узкий, идемпотентный; реестр пишется до переписи архива, guard по `New-CatalogId` оставлен вторым поясом.
- `scripts/spec_catalog/validate.ps1` - `Monotonicity` вычитает зарегистрированные id.
- `scripts/spec_catalog/SCHEMA.md` - разделы «Burned ids», «Alternate-catalog runs», «One-off maintenance»; `docs/SCRIPT_CHEATSHEET.md` перегенерирован.

**Доказательства:**

- `preview.tests/Run-Tests.ps1`: expected exit 0 | actual 0, 20 passed.
- Архив: expected 1225 | actual 1225 - прогон не добавил ни строки.
- `New-CatalogId`: expected S1538 | actual S1538 - ни один id не сожжён.
- `preview-tests-probe` в архиве: expected 0 | actual 0 (был 21).
- Реестр: expected 21 | actual 21 строк.
- `validate.ps1`: expected exit 0 | actual 0; `Monotonicity` - `OK dense S0001..S1537 (21 burned id(s) accounted for)`.
- `purge-probe-records.ps1` повторно: expected exit 0 | actual 0, «nothing to purge».
- Регрессия сиблингов: `update.tests` 18 passed (exit 0), `close-and-log.tests` 40 passed (exit 0).
- `post-change.ps1 -ChangeType Mixed -ScopeToFile`: PASS WITH ADVISORIES (1), advisory - document-registry; закрыт `validate` + `generate` + `generate -Check`, все exit 0.

**Две ошибки, пойманные до закрытия, обе - ложное зелёное:**

- `Read-BurnedIds` обернула результат `Read-JsonlFile` в `@()`, что пере-вложило его анти-анроллинг-идиому; предупреждение об этом стоит прямо в `Add-ArchiveRecord`. Упало громко на первом же вызове.
- Первая версия проверки утечки считала утечкой любой непустой вывод `select.ps1`, а промах печатает литерал `[]` - непустую строку. Проверка объявила утечку там, где журналы не изменились ни на строку. После починки предикат проверен в обе стороны: существующий id даёт `leak=True`, зарезервированный и вычищенный - `leak=False`.

**Отклонения от §3.2:** добавлен пункт 6 (реестр сожжённых id) - обоснование в самом §3.2.

## Completion Gate

- Every step above is `[x] done`.
- Every §4 check recorded with `expected: X | actual: Y`.
- Closure through `scripts/post-change.ps1 -ChangeType Script -ScopeToFile`.
