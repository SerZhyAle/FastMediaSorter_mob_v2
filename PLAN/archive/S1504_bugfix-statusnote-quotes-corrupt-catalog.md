# Спецификация (compact bugfix): S1504 - StatusNote с кавычками портит запись в журнале спеков

**Ticket:** S1504
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** S1474 (`/spec-dev`, закрытие тикета)

**Текст:**

update.ps1 -StatusNote corrupts the catalog record when the note contains escaped double quotes. Observed 2026-08-08 while closing S1474: the note ended with `Probe tags: 3 lines tagged \"S1474:\" in logcat.` (escaped quotes, passed from a PowerShell double-quoted string). After the write, `select.ps1 -Id S1474` returned `"name":"S1474:\\ in logcat."` - the ticket's name field had been overwritten with a fragment of the note, and the note itself was truncated at the first escaped quote. Status and file survived. Repaired by hand with `update.ps1 -Id S1474 -Name 'stream-about-channel' -StatusNote '<same text, no quote characters>'`. Impact: any Block* transition whose note quotes a string - which the probe-tag convention actively encourages, since the tag is written `Timber.d("Sxxxx: ..")` - can silently rename a ticket in the journal. A renamed ticket breaks slug resolution (`select.ps1 -Name`), the release-queue reconciliation that matches on name, and every later reader. Needs: reproduce with a minimal note, find whether the corruption is in the JSONL escaping on write or in the argument parsing, fix it in `scripts/spec_catalog/update.ps1`, and add a regression test plus a catalog-integrity check that would have caught a name field that no longer matches its file slug.

---

## 1. Проблема / симптом

Переход в `Block*` с примечанием, содержащим экранированные двойные кавычки, молча переименовывает тикет в журнале.

Наблюдалось на S1474, 2026-08-08:

- Записывали: `-StatusNote "... Probe tags: 3 lines tagged \"S1474:\" in logcat."`
- После записи `select.ps1 -Id S1474 -Format json` вернул `"name":"S1474:\\ in logcat."` вместо `stream-about-channel`, а само примечание оборвалось на первой экранированной кавычке.
- Поля `status` и `file` уцелели, поэтому ни один гейт не сработал - дефект виден только при чтении записи.

Ущерб шире одного тикета: соглашение о debug-тегах само подталкивает писать кавычки в примечании, потому что тег выглядит как `Timber.d("Sxxxx: ..")`. Переименованный тикет ломает разрешение по слагу (`select.ps1 -Name`), сверку с планом релиза, которая сопоставляет по имени, и любого следующего читателя.

---

## 2. Корневая причина

Экранирование JSONL ни при чём. `Format-CatalogLines` сериализует запись через `ConvertTo-Json -Compress`, а `Read-JsonlFile` разбирает её через `ConvertFrom-Json`; оба корректно обрабатывают кавычки и обратные слэши в любом поле. Примечание с кавычками записывается и читается без потерь.

Дефект - в привязке аргументов, и разваливает строку **парсер самого PowerShell**, а не передача аргументов процессу. Обратный слэш в PowerShell не экранирует кавычку, поэтому в тексте команды `-StatusNote "... tagged \"S1474:\" in logcat."` строковый литерал закрывается на первой же `\"`. Токенизатор возвращает два аргумента вместо одного:

- `String: [Probe tags: 3 lines tagged \]` - садится в `-StatusNote`, отсюда обрыв примечания;
- `CommandArgument: [S1474:\ in logcat.]` - остаётся без имени параметра.

Все параметры `update.ps1` объявлены позиционными, поэтому безымянный хвост садится в первый свободный позиционный слот. `-Id` и `-Status` переданы по имени, значит первый свободный слот - `$Name`, третий в блоке `param()`. Тикет переименовывается молча.

Ни один гейт не срабатывает, потому что `Assert-Record` валидирует `id`, `status`, `priority` и `file`, а про `name` знает только то, что поле непустое. Любая строка проходит, включая осколок чужого примечания.

Воспроизведено 2026-08-08 на копии param-блока (`temp/S1504/probe-binding.ps1`):

- `StatusNote=[Probe tags: 3 lines tagged \]`
- `Name=[S1474:\ in logcat.]`

Совпадает с записью S1474 посимвольно (`"name":"S1474:\\ in logcat."` - тот же одиночный слэш, экранированный при сериализации).

Две версии проверены и отвергнуты, обе важны для формы теста:

- Передача того же текста **через переменную** раскола не даёт: PowerShell 7 перекавычивает аргумент корректно, примечание доходит целиком.
- Передача сырой командной строкой через `ProcessStartInfo` раскола тоже не даёт: правила `CommandLineToArgvW` трактуют `\"` как литеральную кавычку внутри строки.

Поэтому регрессионный тест обязан разбирать именно **текст команды** (`Invoke-Expression`), иначе он воспроизводит не тот дефект и проходит на непочиненном коде.

Класс дефекта в репозитории уже известен: `close-and-log.ps1` получил `PositionalBinding = $false` по S1063 после того, как многоэлементный `-DevLogs` через `-File` ровно так же сел в `-FeatId`. `update.ps1` и `insert.ps1` того же барьера не получили.

---

## 3. Исправление

Три шага: закрыть привязку, поймать порчу на записи, зафиксировать регрессию.

Барьер ставится только на `update.ps1` и `insert.ps1`. У остальных мутаторов каталога (`close`, `complete`, `archive`, `delete`, `bulk-update`) следующий свободный позиционный слот защищён `ValidateSet` или `Mandatory`, поэтому шальной токен там падает громко, а не портит данные. Позиционных вызовов этих скриптов в репозитории нет.

### Step 1 - Close positional binding on the two data-corrupting mutators

**Files:** `scripts/spec_catalog/update.ps1`, `scripts/spec_catalog/insert.ps1`

**Prompt for developer:**

> Replace `[CmdletBinding()]` with `[CmdletBinding(PositionalBinding = $false)]` in both scripts. Above each, add a comment naming S1504 and stating that a stray positional argument is never intentional here, so it must die at bind time rather than land in `-Name`.

**Why:**

An unnamed token silently binds to `$Name` and renames the ticket, and a renamed ticket breaks slug resolution (`select.ps1 -Name`), release-queue reconciliation which matches on name, and every later reader.

**Verification:**

- `Grep` - `PositionalBinding = $false` present in both files.
- Run `update.ps1 -Id S1504 -Status Draft extra-token`: exit non-zero, message contains `positional parameter cannot be found`, catalog `updated` unchanged.

**Status:** `[x]` done

### Step 2 - Reject impossible characters in `name` at write time

**Files:** `scripts/spec_catalog/_lib.ps1`

**Prompt for developer:**

> In `Assert-Record`, reject a `name` containing a double quote, a backslash, or a control character, with a message that names the field and the offending value. Keep the existing checks untouched.

**Why:**

The rename passed every gate because `Assert-Record` only required `name` to be non-empty, so the corruption reached disk and surfaced only when a human read the record back.

**Verification:**

- All 1504 current records pass: `validate.ps1` reports `Schema OK`, exit 0.
- A record whose `name` carries a backslash throws from `Assert-Record`.

**Status:** `[x]` done

### Step 3 - Regression suite for the mis-bind and for quoted notes

**Files:** `scripts/spec_catalog/update.tests/Run-Tests.ps1`

**Prompt for developer:**

> Add a suite following the shape of `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`: a stray positional argument is rejected at bind time with no catalog mutation; a `-StatusNote` containing real double quotes round-trips through the journal intact; `Assert-Record` refuses a `name` carrying a backslash. Echo the subject ticket's current status back at it so the suite never causes a lifecycle transition, and restore anything it writes in a `finally` block.

**Why:**

Without an executable guard the barrier rots the same way it did between S0082 and S1063, where the failure mode silently degraded from hard error to mis-bind as new optional string parameters were added.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/update.tests/Run-Tests.ps1` exits 0.
- `validate.ps1` exits 0 afterwards, proving the suite left the journal clean.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1474 - тикет, на котором дефект проявился и чья запись была починена вручную. S1063 - тот же класс дефекта в `close-and-log.ps1`, откуда взяты и барьер, и форма теста.

---

## 4. Проверка

- Прямой repro больше не проходит: вызов `update.ps1` с примечанием, содержащим `\"`, завершается ненулевым кодом и не трогает журнал.
- Легитимное примечание с настоящими кавычками записывается и читается без потерь - именно эту форму подталкивает писать соглашение о debug-тегах.
- `pwsh -NoProfile -File scripts/spec_catalog/update.tests/Run-Tests.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` - exit 0, `Schema OK` на всех записях.
- Порча `name` теперь ловится на записи, а не при чтении глазами.

---

## Last Audit

**Date:** 2026-08-08
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

- `PositionalBinding = $false` present in `update.ps1:6` and `insert.ps1:11`, each with the S1504 rationale comment.
- `Assert-Record` rejects a `name` carrying a quote, a backslash or a control character.
- `scripts/spec_catalog/update.tests/Run-Tests.ps1` - 18 cases, exit 0, including the S1474 keystrokes verbatim and the legitimate quoted note.
- `validate.ps1` - exit 0, `Schema OK` on all 1504 records: the new rule rejects no existing name.
- `post-change.ps1 -ScopeToFile` - PASS, exit 0; one batched dev-log row for the set of 6 files.
- `script-cheatsheet-sync` - in sync after regeneration.
- Debug-tag invariant - zero `Timber.d("S1504:` lines, correct for a non-`BlockNeedUserTest` status.
- EXEMPT: no user-visible capability, so no `ALL_FEATURES` record - the change ships repository tooling only.

### Manual / on-device

- Ничего: дефект и его проверка целиком в скриптах репозитория, устройство не нужно.
