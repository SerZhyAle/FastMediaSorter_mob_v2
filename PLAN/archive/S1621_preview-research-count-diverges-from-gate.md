# Стратегическая спецификация: S1621 - preview.ps1 считает открытые вопросы по своим правилам

**Ticket:** S1621
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-13
**Tier:** не определён
**Roadmap entry:** Ad-hoc - находка при работе над S1607

<!-- auto-approved by /spec-all - 2026-08-13 -->

---

## 1. Проблема

`scripts/spec_catalog/preview.ps1` считает открытые research-пункты приватными функциями `Get-SectionBodyByHeading` и `Get-UnresolvedResearchItemCount` и публикует результат полем `research_open_count`. S1607 завёл в `_lib.ps1` общие `Get-ResearchSectionHeadingPattern`, `Get-OpenStatusPattern` и `Get-SpecSectionLines`, которыми пользуется закрывающий гейт `check-open-items-carried.ps1`. Две реализации считают одно и то же по разным правилам.

**Измерено 2026-08-13** разовой пробой - приватный счётчик `preview.ps1` против общего разбора S1607, обе реализации прогнаны по 1601 файлу спек в `PLAN/` и `PLAN/archive/`:

- Числа расходятся на **163 спеках (10.2%)**.
- В **3 случаях** `preview.ps1` печатает `0` там, где гейт видит открытые пункты: `PLAN/S1177_launcher-google-app-widgets.md` (5 пунктов), `PLAN/archive/S0621_hotfix-standard-gesture-settings.md` (3), `PLAN/archive/S0944_app-wide-focus-reachability.md` (2). Это опасная сторона расхождения: оператор читает «вопросов нет» и ведёт тикет к закрытию, где его останавливает гейт.
- В остальных **160 случаях** `preview.ps1` печатает больше нуля там, где гейт видит ноль. Причина не в опечатке шаблона, а в другом определении: у `preview.ps1` есть эвристический запасной проход, считающий открытым любой пункт, в тексте которого встретился знак вопроса и нет слова `Resolved`.

Две причины первого класса расхождений, обе подтверждены на живых файлах:

- Список заголовков в `preview.ps1` не содержит `Open items`, поэтому раздел с таким заголовком не находится вовсе.
- Шаблон статуса в `preview.ps1` привязан к началу строки, поэтому плоская форма `- **Заголовок.** Status: Open. ..` им не видна.

---

## 2. Цели

1. Один разбор research-раздела на весь `spec_catalog`: и гейт, и превью читают спеку одним кодом.
2. `research_open_count` означает то же, что означает гейт, - иначе число либо шумит, либо врёт ровно перед закрытием.
3. Оператор видит в превью не только «есть открытые пункты», но и «сколько из них не имеет носителя», то есть заранее знает вердикт закрывающего гейта.
4. JSON-контракт `preview.ps1` не ломается: поля только добавляются, существующие сохраняют имя и тип.

### 2.1 Не входит в цели

- Менять определение открытого пункта, введённое S1607. Общий разбор - источник истины; сходятся к нему, а не к середине.
- Трогать остальные потребители `_lib.ps1`. Функции переезжают, их имена и сигнатуры сохраняются.

---

## 3. Ограничения и решения

### 3.1 Ограничения

- `preview.ps1` лежит на горячем пути `/spec-next`: он вызывается для каждого кандидата. Исключение из него возвращает код 2 и останавливает подбор тикета, поэтому цена регрессии высокая.
- `_lib.ps1` объявляет `Set-StrictMode -Version Latest`. Точечная проверка: `preview.ps1` читает `$rec.statusNote` (строка 201), а записи каталога без заметки этого свойства не имеют - под strict mode обращение к несуществующему свойству бросает исключение. То есть прямой `. _lib.ps1` в `preview.ps1` ломает скрипт, и ломает именно на горячем пути.
- `$ErrorActionPreference = 'Stop'`, названный в захвате как причина нетривиальности, на деле безразличен: `preview.ps1` выставляет его сам на строке 49.

### 3.2 Решения

**ADR-1: общий разбор переезжает в отдельный лист-файл, а не импортируется из `_lib.ps1`.** Решение: три функции S1607 плюс новая `Get-ResearchItems` переезжают в `scripts/spec_catalog/_research-items.ps1` - файл без побочных эффектов: без `Set-StrictMode`, без `$ErrorActionPreference`, без путей каталога и мьютексов. `_lib.ps1` подключает его первой строкой тела, поэтому все существующие вызовы продолжают работать без правок. `preview.ps1` подключает только лист.

Отвергнуто: подключить `_lib.ps1` целиком и починить найденные strict-mode места. Одну поломку я нашёл чтением, но гарантии, что она единственная в скрипте на 337 строк, чтение не даёт, а цена промаха - остановка подбора тикетов. Лист-файл даёт ту же сходимость с нулевым радиусом поражения.

Отвергнуто: вызывать `check-open-items-carried.ps1` из `preview.ps1`. Это лишний процесс на каждого кандидата `/spec-next`, ради числа, которое считается разбором одного файла.

**ADR-2: эвристика знака вопроса удаляется, а не сохраняется рядом.** Запасной проход `preview.ps1` отвечает на другой вопрос - «встречается ли в разделе знак вопроса у пункта без слова Resolved». Он даёт ненулевое число на 160 спеках, где гейт видит ноль. Держать рядом два числа с похожими именами и разными определениями - это то же расхождение, только узаконенное. Поле остаётся одно и означает то, что означает гейт.

Что теряется: на спеках со свободным разделом вопросов без строк статуса превью станет печатать `0`. Это верное число по определению S1607 - пункт без статуса не открыт и не закрыт, он не оформлен, и закрытию не мешает.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1607

---

## 4. Фазы

### Phase 01 - Shared research-item parser

**Files touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/_research-items.ps1` | New | ≤ 130 |
| `scripts/spec_catalog/_lib.ps1` | Modified | ≤ 90 removed, ≤ 5 added |

---

#### Step 01.1 - Create the leaf parser file

**Files:** `scripts/spec_catalog/_research-items.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/_research-items.ps1` holding `Get-ResearchSectionHeadingPattern`, `Get-OpenStatusPattern` and `Get-SpecSectionLines`, moved verbatim from `_lib.ps1` including their comments. Add `Get-ResearchItems`, which takes the same `-Path` the other helpers take and returns one object per research item with `Title`, `LineNumber`, `Lines`, `IsOpen`, `OpenLine`, `Carrier` and `IsPlaceholder`. Lift the item-grouping rules from `check-open-items-carried.ps1` unchanged: an item starts at column zero, the starting line joins its own item, a title longer than 90 characters is clipped, and `Open / Resolved` copied from the template counts as open. Bring the carrier-token regex with them as `Get-CarrierTokenPattern`, and give the file its own path resolver so it needs nothing from `_lib.ps1`. Give the file a header stating that it is dot-sourced, sets no preferences and holds no state, and naming both consumers.

**Why:**

Section 3.2 fixes a leaf file rather than an import of `_lib.ps1` because `preview.ps1` sits on the `/spec-next` hot path and `_lib.ps1` would impose `Set-StrictMode -Version Latest` on it, which section 3.1 shows already breaks one live line.

**Verification:**

- `Glob` - `scripts/spec_catalog/_research-items.ps1` exists.
- `Grep` - each of `function Get-ResearchSectionHeadingPattern`, `function Get-OpenStatusPattern`, `function Get-SpecSectionLines`, `function Get-ResearchItems` matches exactly once in it.
- `Grep` - no line in it *sets* `Set-StrictMode` or `$ErrorActionPreference`; the header names both to say why they are absent, so match the assignment, not the word.
- Run `pwsh -NoProfile -Command ". scripts/spec_catalog/_research-items.ps1; (Get-ResearchItems -Path 'PLAN/S1177_launcher-google-app-widgets.md' | Where-Object IsOpen).Count"` - prints `5`.

**Status:** `[x]` done

---

#### Step 01.2 - Point `_lib.ps1` at the leaf

**Files:** `scripts/spec_catalog/_lib.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the three moved function bodies from `_lib.ps1` and dot-source `_research-items.ps1` instead, resolving it from `$libDir` so the load works whatever the caller's working directory is. Place the dot-source before the first function that uses those helpers. Leave every other consumer of `_lib.ps1` untouched - the function names and signatures do not change.

**Why:**

Goal 2.1 keeps the shared definition as the single source of truth, so the move must be invisible to the existing callers of `_lib.ps1` rather than a second copy living beside the first.

**Verification:**

- `Grep` - `_research-items.ps1` matches exactly once in `scripts/spec_catalog/_lib.ps1`.
- `Grep` - `function Get-SpecSectionLines` returns zero hits in `scripts/spec_catalog/_lib.ps1`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/check-open-items-carried.ps1 -Id S1607` - exit 0, output starts with `PASS S1607`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Phase 02 - Converge the two consumers

**Files touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/check-open-items-carried.ps1` | Modified | ≤ 40 changed |
| `scripts/spec_catalog/preview.ps1` | Modified | ≤ 60 changed |

---

#### Step 02.1 - Rebuild the gate on `Get-ResearchItems`

**Files:** `scripts/spec_catalog/check-open-items-carried.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the inline item-grouping block in `check-open-items-carried.ps1` with a call to `Get-ResearchItems`, and build the blocker list by filtering on `IsOpen` and an absent `Carrier`. Keep the reporting format, the exit codes and the whole header comment as they are - only the parsing moves. Keep the "no research section" early exit, deciding it from an empty item set plus the section lookup the helper already performs.

**Why:**

Goal 1 requires one parse for both scripts, and leaving the grouping rules duplicated in the gate would let them drift from the copy the preview reads, which is the defect this ticket exists to remove.

**Verification:**

- `Grep` - `Get-ResearchItems` matches in `scripts/spec_catalog/check-open-items-carried.ps1`.
- `Grep` - `itemStartRx` returns zero hits in that file.
- Run `pwsh -NoProfile -File scripts/spec_catalog/check-open-items-carried.ps1 -Id S1177` - exit 1, output names 5 items.
- Run `pwsh -NoProfile -File scripts/spec_catalog/check-open-items-carried.ps1 -Id S1607` - exit 0.
- Run it with `-Id NOPE` - exit 2.

**Status:** `[x]` done

---

#### Step 02.2 - Converge `preview.ps1` on the shared parser

**Files:** `scripts/spec_catalog/preview.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete `Get-SectionBodyByHeading` and `Get-UnresolvedResearchItemCount` from `preview.ps1` and dot-source `_research-items.ps1` from `$PSScriptRoot`. Compute `research_open_count` as the number of items with `IsOpen`, and add a `research_uncarried_count` field counting the open items with no `Carrier`. Add both to the JSON documentation block at the top of the file and to the table-format line that already prints `research_open`. State in a comment that the number is the closing gate's number by construction, name S1621, and record that the question-mark fallback was dropped because it answered a different question.

**Why:**

Goals 2 and 3 require the preview to report what the closing gate will report - the measurement in section 1 found three specs where the old counter printed zero against live open items, which is the operator reading "no questions" immediately before the gate refuses the close.

**Verification:**

- `Grep` - `Get-UnresolvedResearchItemCount` and `Get-SectionBodyByHeading` return zero hits in the repository.
- `Grep` - `research_uncarried_count` matches in `scripts/spec_catalog/preview.ps1`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/preview.ps1 -Id S1177 -Format json` - exit 0, valid JSON, `research_open_count` is 5 and `research_uncarried_count` is 5.
- Run `pwsh -NoProfile -File scripts/spec_catalog/preview.ps1 -Id S1607 -Format json` - exit 0, `research_open_count` is 0.
- Run `pwsh -NoProfile -File scripts/spec_catalog/preview.ps1 -Id S1607 -Format table` - exit 0, prints the research line.
- Run `preview.ps1 -Id S1621 -Format json` before and after this step and compare every field except the two research counts - all equal, proving the JSON contract held.

**Status:** `[x]` done

---

### Phase 03 - Docs and closure

**Files touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/SCHEMA.md` | Modified | ≤ 8 added |
| `.claude/reference/spec-next.md` | Modified | ≤ 4 changed |

---

#### Step 03.1 - Record the new meaning where it is read

**Files:** `scripts/spec_catalog/SCHEMA.md`, `.claude/reference/spec-next.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `scripts/spec_catalog/SCHEMA.md`, note next to the closing-gates paragraph that `preview.ps1` reports the same open-item count the gate enforces, through the shared `_research-items.ps1`. In `.claude/reference/spec-next.md`, extend the line that calls `research_open_count` informational: it stays informational for auto-skip purposes, and it now also predicts the closing gate, with `research_uncarried_count` naming the items that would refuse a close.

**Why:**

Goal 3 is only delivered if the operator reading the preflight handoff knows the number now predicts the closing verdict; the existing sentence describes the old, purely advisory meaning.

**Verification:**

- `Grep` - `_research-items.ps1` matches in `scripts/spec_catalog/SCHEMA.md`.
- `Grep` - `research_uncarried_count` matches in `.claude/reference/spec-next.md`.

**Status:** `[x]` done

---

#### Step 03.2 - Close the change through the facade

**Files:** all files touched by phases 01 to 03
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set with `-Files`, `-ScopeToFile`, `-ChangeType Tooling` and a description naming S1621. Run the document-registry closing trio, because `.claude/reference/spec-next.md` and `scripts/spec_catalog/SCHEMA.md` belong to registered records. Read the verdict and report an advisory rather than ignoring it.

**Why:**

The repository requires mechanical closure through the facade rather than hand-rolled steps, and the document-registry loop must close whenever a registered document changes.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<set>" -ScopeToFile -ChangeType Tooling -Target "spec-catalog" -Description "S1621: one research-item parser for the gate and the preview"` - exit 0, final line `post-change: PASS` or `PASS WITH ADVISORIES`.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.
- `Grep` - `S1621` matches in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## 5. Критерии готовности

- Ни один research-раздел не разбирается дважды разным кодом: правила группировки пунктов, шаблон статуса и шаблон токена-носителя встречаются в `scripts/spec_catalog/` ровно по одному разу, и все три - в `_research-items.ps1`.
- Общий разбор проходит по всем 1601 файлам спек без единого исключения.
- `preview.ps1` и `check-open-items-carried.ps1` согласны: на выборке из 20 живых тикетов с открытыми пунктами `research_uncarried_count > 0` совпадает с кодом возврата 1 закрывающего гейта, ноль расхождений.
- Три спеки, на которых старый счётчик печатал `0`, теперь печатают своё настоящее число: S1177 - пять пунктов, S0621 - три, S0944 - два.
- JSON-контракт `preview.ps1` сохранён: сравнение поле за полем до и после на трёх спеках не потеряло ни одного поля, добавило `research_uncarried_count`, и единственное изменившееся значение - `research_open_count` у S1177 с 0 на 5.
- `validate.ps1` возвращает 0.

## 6. Открытые вопросы

Открытых вопросов нет.

## 7. Риски

- Перенос функций между файлами ломает вызывающих `_lib.ps1`. Смягчение: имена и сигнатуры не меняются, а шаг 01.2 проверяет гейт и `validate.ps1` сразу после переноса.
- Смена смысла `research_open_count` меняет число на 160 архивных спеках. Смягчение: программных потребителей у поля нет (проверено grep по репозиторию), решение и его цена записаны в ADR-2.

## 8. FEATURES

Без изменений - инструментальная правка, пользователю не видна.
---

## Last Audit

**Date:** 2026-08-13
**Mode:** strategic (compact spec - phases inline)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 22 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- none - tooling change, no runtime surface. FEATURES exempt per section 8.

### Notes

- Two step predicates were corrected during the audit rather than the code: step 01.1 asked for zero grep hits on `Set-StrictMode` / `ErrorActionPreference` in the new file, but its header names both to explain their absence, so the predicate now matches the assignment; and the same step's prompt did not name `Get-CarrierTokenPattern` and the file-local path resolver, both required by ADR-1 for the leaf to stand free of `_lib.ps1`.
- Corpus evidence: the shared parser ran over all 1601 spec files with zero failures, reporting 174 specs carrying 454 open items, all 454 uncarried. On 20 live tickets `research_uncarried_count > 0` and the closing gate's exit code agreed without exception.
