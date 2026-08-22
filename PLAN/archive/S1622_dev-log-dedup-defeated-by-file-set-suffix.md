# Стратегическая спецификация: S1622 - дедуп dev log не срабатывает при перезапуске закрытия

**Ticket:** S1622
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-13
**Tier:** не определён
**Roadmap entry:** Ad-hoc - находка при работе над S1607

<!-- auto-approved by /spec-all - 2026-08-13 -->

---

## 1. Проблема

`scripts/add_to_dev_log.ps1` защищён от повторной записи: он сравнивает подпись `файл | цель | описание` с восемью последними строками журнала и пропускает дубликат. Комментарий самой защиты называет случай, ради которого она ставилась: «three identical S1181 rows within 6 min from repeated post-change runs», то есть перезапуск закрытия.

Ровно этот случай она и не покрывает. `scripts/post-change.ps1` приписывает к описанию хвост `[set of N: ..]` со списком изменённых файлов, а типичная починка advisory - регенерация файла, который после этого попадает в набор. Второй прогон уходит с другим N и другим списком, подпись расходится, и в `dev/CHANGELOG.md` появляются две строки на одно логическое изменение.

**Измерено 2026-08-13** по всему `dev/CHANGELOG.md` (25 928 строк данных), в окне из восьми строк - том самом, которое инспектирует защита:

- Хвост `[set of N: ..]` существует с **2026-08-08**, его несут **267 строк**.
- С этой даты защита не пропустила **ни одного** точного повтора: все 258 точных повторов в журнале старше, самый свежий - 2026-07-24.
- За тот же период мимо неё прошли **2 строки**, и обе - ровно этого вида: `2026-08-10` (`generate-oss-notices.ps1`, S1562) и `2026-08-13` (`_lib.ps1`, S1607).

То есть абсолютное число невелико, но это **все** оставшиеся промахи защиты: единственная дыра, через которую она ещё течёт.

Побочно: подпись сравнивается как подстрока с хвостовым пробелом, поэтому описание `Fix A` уже сейчас считается дубликатом строки с описанием `Fix A and B`. Это второй способ ошибиться, в ту же сторону.

---

## 2. Цели

1. Перезапуск закрытия после починки advisory даёт ровно одну строку журнала, даже если набор файлов вырос.
2. Дедуп не начинает склеивать разное: описание, являющееся началом другого описания, дубликатом не считается.
3. Обещание `CLAUDE.md` раздела 12 о «ровно одной строке» становится верным и для закрытия с вердиктом `PASS WITH ADVISORIES`, а не только для упавшего.

### 2.1 Не входит в цели

- Менять формат строки журнала или сам хвост `[set of N: ..]`: он читаем и восстанавливает набор файлов, ради чего и вводился (S1338).
- Менять окно из восьми строк. Оно ловит перезапуск, а перезапуск идёт следом за первым прогоном; измерение подтверждает, что окна хватает.

---

## 3. Ограничения и решения

### 3.1 Развилка и решение

Развилка захвата: что считать подписью логического изменения. Убрать описание из подписи целиком - и два честно разных закрытия с одним файлом склеятся. Оставить как есть - и штатный сценарий восстановления продолжит плодить строки.

**ADR-1: волатильный хвост нормализуется с обеих сторон сравнения, а описание остаётся в подписи целиком.** Подпись строится из описания с отрезанным хвостом `[set of N: ..]`, и тот же хвост вырезается из строки журнала перед сравнением. Всё остальное описание участвует в сравнении как раньше. Хвост - единственная часть строки, которую пишет не вызывающий, а фасад, и единственная, которая меняется между двумя прогонами одного закрытия; всё, что писал вызывающий, остаётся различающим признаком.

Отвергнуто: передавать ключ дедупа отдельным параметром из `post-change.ps1`. Сравнение идёт с уже записанными строками журнала, а ключа, которого в строке нет, в ней и не найти. Параметр потребовал бы либо второго хранилища, либо записи ключа в строку - и то и другое дороже нормализации.

Отвергнуто: считать дубликатом совпадение по началу описания. Это текущее поведение из-за хвостового пробела в подписи, и оно ошибочно в другую сторону - см. цель 2. Подпись поэтому замыкается на следующий за описанием маркер `[branch:`.

### 3.2 Ограничения

- `add_to_dev_log.ps1` вызывается из десятков мест напрямую, без хвоста. Для таких вызовов нормализация обязана быть тождественной.
- Вырезать хвост из строки журнала нужно только там, где его пишет фасад, - непосредственно перед маркером `[branch:`. Безусловное вырезание сработало бы и на тексте, который автор написал сам.
- Аварийный выход `-AllowDuplicate` остаётся: он и есть ответ на «это правда второе, одинаково описанное изменение».

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1607, S1545

---

## 4. Фазы

### Phase 01 - Dedup signature

**Files touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/add_to_dev_log.ps1` | Modified | ≤ 25 changed |

---

#### Step 01.1 - Normalise the file-set suffix out of both sides

**Files:** `scripts/add_to_dev_log.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Build the dedup signature from the description with a trailing `[set of N: ..]` suffix removed, and close the signature with the `[branch:` marker that follows the description in every written row, so it matches a whole description rather than a prefix of one. Strip the same suffix from each inspected row before comparing, matching it only where the facade writes it - immediately before ` [branch:` - so a suffix a caller typed itself is left alone. Replace the comment above the guard with one naming S1622 and stating that the suffix is the facade's, not the caller's, and is therefore the one part of the row that must not identify the change.

**Why:**

Goal 1 requires a re-run after an advisory fix to produce one row rather than two, and section 1 measures that the growing file set is the only remaining way a repeat escapes this guard; goal 2 requires the same edit to stop treating one description as a duplicate of a longer one that starts with it.

**Verification:**

- `Grep` - `set of` matches in `scripts/add_to_dev_log.ps1`.
- `Grep` - `[branch:` appears in the signature construction, not only in the row it writes.
- Append two rows to a scratch copy of the changelog through the real script with descriptions differing only by the set suffix - the second prints `SKIP duplicate` and the file gains no row.
- Same scratch copy, in this order: the longer description first, then the shorter one that is its prefix - both write a row. Order matters; written the other way round the case proves nothing.
- Same scratch copy: a genuinely different description writes a row, and `-AllowDuplicate` writes a row even for an exact repeat.
- Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Name add_to_dev_log.ps1` - exit 0.

**Status:** `[x]` done

---

### Phase 02 - Rule text and closure

**Files touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 4 changed |
| `AGENTS.md` | Modified | ≤ 4 changed |

---

#### Step 02.1 - Make the closure promise true for an advisory pass

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> The closure-verdict paragraph of `CLAUDE.md` section 12 promises that a failed closure writes no changelog row and a re-run after a fix produces exactly one. Extend it: a closure that passes **with advisories** returns 0 and does write its row, and re-running it after fixing what the advisory named still produces exactly one, because the dev-log guard identifies the change and ignores the size of its file set. Name S1622. Mirror the same sentence into `AGENTS.md`, which the rules require to be synced whenever a shared rule changes.

**Why:**

Goal 3 names the gap directly: the existing sentence is true only for a closure that failed, while the advisory path - the one the fix is about - returns 0, writes a row, and was the path that produced two.

**Verification:**

- `Grep` - `S1622` matches in `CLAUDE.md`.
- `Grep` - `S1622` matches in `AGENTS.md`.
- `Grep` - `advisor` matches within the closure-verdict paragraph of both files.

**Status:** `[x]` done

---

#### Step 02.2 - Close the change through the facade

**Files:** all files touched by phases 01 and 02
**Depends on:** Step 02.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set with `-Files`, `-ScopeToFile`, `-ChangeType Tooling` and a description naming S1622. Run the document-registry closing trio, because `CLAUDE.md` and `AGENTS.md` belong to the `repository-rules` record. Read the verdict; an advisory is a result to report.

**Why:**

The repository requires mechanical closure through the facade rather than hand-rolled steps, and the document-registry loop must close whenever a registered document changes.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<set>" -ScopeToFile -ChangeType Tooling -Target "dev-log" -Description "S1622: dedup identifies the change, not the size of its file set"` - exit 0, final line `post-change: PASS` or `PASS WITH ADVISORIES`.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.
- `Grep` - `S1622` matches in `dev/CHANGELOG.md`, exactly one row for this change.

**Status:** `[x]` done

---

## 5. Критерии готовности

- Два прогона с описанием, различающимся только хвостом `[set of N: ..]`, дают одну строку.
- Описание и более длинное описание, начинающееся с него, дают две строки.
- `-AllowDuplicate` по-прежнему пишет строку.
- Прямые вызовы `add_to_dev_log.ps1` без хвоста ведут себя как раньше.
- `dev/CHANGELOG.md` получает ровно одну строку об этом изменении.

## 6. Открытые вопросы

Открытых вопросов нет.

## 7. Риски

- Нормализация вырежет текст, который автор написал сам. Смягчение: вырезание в строке журнала привязано к позиции перед `[branch:`, куда фасад и пишет хвост.
- Ужесточение подписи до `[branch:` перестанет считать дубликатами пары, которые считались ими раньше. Это и есть цель 2. Частоту прежней ошибки измерить нельзя в принципе: пропущенная по префиксу строка не пишется и следа в журнале не оставляет - что само по себе довод в пользу ужесточения, а не против. Поэтому обе стороны проверяются прогоном, а не подсчётом: шаг 01.1 требует, чтобы более длинное описание и его префикс, записанные именно в этом порядке, дали две строки.

## 8. FEATURES

Без изменений - инструментальная правка, пользователю не видна.
---

## Last Audit

**Date:** 2026-08-13
**Mode:** strategic (compact spec - phases inline)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 17 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- none - tooling change, no runtime surface. FEATURES exempt per section 8.

### Notes

- Both defects were proved by running the SAME 11-case harness against the pre-change script and the shipped one, on a throwaway repository root rather than on `dev/CHANGELOG.md`. Before: 2 failures - the re-run whose file set grew wrote a second row, and a description that is the prefix of an earlier, longer one was silently swallowed. After: 11 of 11. The nine unaffected behaviours - exact repeat, direct call with no suffix, `-AllowDuplicate`, different file, different target - are asserted in both runs and never moved.
- The prefix case is order-dependent, and the first version of the harness had it the wrong way round: it wrote the short description first, which passes even on the broken script. Written long-then-short it fails on the old script and passes on the new one. The step predicate now states the order.
- The frequency of the prefix defect cannot be measured after the fact: a wrongly skipped row leaves no trace. Recorded in section 7 rather than left as an unstated gap in the evidence.
- This ticket's own closure wrote exactly one changelog row, and the S1621 closure earlier today re-ran `post-change.ps1` after acknowledging a registry record and correctly produced no second row.
