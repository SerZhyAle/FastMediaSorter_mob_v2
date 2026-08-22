# Стратегическая спецификация: S1698 - release-queue.ps1 -Reconcile не устраняет дубли строк

**Ticket:** S1698
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-15
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при работе над S1695, 2026-08-15

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Захвачено во время:** S1695

**Текст:**

`release-queue.ps1 -Validate` reports duplicate lines in `PLAN/RELEASE_READY.md`, and the remedy the script itself prints for that error - `Fix with: release-queue.ps1 -Reconcile` - does not fix it. Observed 2026-08-15 while closing S1695.

Symptom, in order:

1. `-Validate` reported two duplicated tickets: `S1684_bugfix-wear-smb-track-title-empty` and `S1682_bugfix-wear-sync-success-reported-before-ack`, each appearing on two consecutive byte-identical lines in `PLAN/RELEASE_READY.md` (lines 101-104), both in release package 33, both `Verified`, both dated 2026-08-15.
2. Ran `release-queue.ps1 -Reconcile`. It printed `reconciled - 78 ticket(s) before, 78 after` and exited 0. `-Validate` afterwards still failed, now naming only `S1682` - so the S1684 duplicate went away but the S1682 one did not.
3. Ran `-Reconcile` a second time. It printed `reconciled - 79 ticket(s) before, 79 after` and exited 0. `-Validate` still fails on `S1682`.

The count moving from 78 to 79 between two consecutive reconciles of an unchanged file is the part that suggests this is not merely a missing dedupe pass: the duplicate line appears to be counted as a ticket, so reconcile may be preserving it as a distinct entry rather than collapsing it. Whatever the mechanism, the loop does not converge, and a validator whose printed remedy leaves the error in place trains the reader to ignore it.

Evidence, both lines verbatim from `PLAN/RELEASE_READY.md`:

```text
33   S1682_bugfix-wear-sync-success-reported-before-ack             2026-08-15  Verified
33   S1682_bugfix-wear-sync-success-reported-before-ack             2026-08-15  Verified
```

Catalog record for S1682 is singular and healthy: `{"id":"S1682","status":"Verified","priority":60,"closed_at":"2026-08-15"}`. So the duplication lives in the plan file, not in the journal.

Not caused by S1695's own writes as far as could be told: S1695 is package 32 and reconciled cleanly into `RELEASE_READY.md` on its own line. Both affected tickets are wear tickets in package 33, closed earlier the same day.

Relevant per CLAUDE.md §4: the two plan files own ASSIGNMENT and ORDER, and `release-queue.ps1` is the only script permitted to reorder lines - so a dedupe defect here has no other owner and cannot be worked around by hand-editing the file.

---

## 1. Проблема

`-Validate` detects a duplicate ticket line and prints `Fix with: release-queue.ps1 -Reconcile`, but the reconcile pass has no dedupe step at all, so the remedy is a no-op against the error it is offered for. A validator whose own remedy leaves the error standing teaches the operator to ignore the validator - which is the real cost, since this validator is also the only guard on status drift and on renamed spec files.

## 2. Причина

`Select-ReleaseLines` in `scripts/spec_catalog/_lib.ps1` walks each release file and records `$Seen[$line.Id] = $true`, but never *reads* that hashtable. `$Seen` existed only so that `Sync-ReleaseQueue` could tell which catalog records are in neither file. Every ticket line therefore survives on its own merits: two lines carrying the same id are both refreshed from the catalog and both written back, forever.

Two distinct shapes follow from that one omission:

- Duplicate inside one file - preserved verbatim by every reconcile. The stable case.
- A ticket listed in BOTH files - actively made worse. The queue pass moves it across the ready boundary into `$toReady`, the ready pass keeps the copy already there, and `Add-ReleaseLines` appends the moved row next to it. Each reconcile can therefore mint a *new* duplicate line, which is the mechanism behind the ticket count that grew between two runs over an unchanged file.

Measured against the pre-fix library (probe, 2026-08-16): after two reconciles of a fixture holding one in-file duplicate per file plus one cross-file ticket, the in-file duplicates stood unchanged and the cross-file ticket had two ready lines with different dates.

The `78 -> 79` growth in the captured report is the second shape, not proof that a duplicate line is counted as a ticket.

## 3. Решение

One line per ticket id, across both files, enforced where the lines are read.

- `Select-ReleaseLines` drops a ticket line whose id was already seen. `$Seen` is shared by the queue pass and the ready pass, so one check covers the in-file and the cross-file shape.
- First occurrence wins - it keeps its position and its `rel` column, both of which are owner data. The queue file is walked before the ready file.
- `Add-ReleaseLines` refuses an addition whose id already stands in the target file. Unreachable for well-formed input once the pass above runs; kept as the last barrier, because it is the only place a line is created rather than carried over.
- `-Reconcile` reports `removed N duplicate line(s)`. Its existing before/after counts cover the queue file only, so a duplicate collapsed in the ready file moves neither number, and a silent repair looks exactly like the defect being reported here.

### 3.1 Область изменений

- `scripts/spec_catalog/_lib.ps1` - `Select-ReleaseLines`, `Add-ReleaseLines`, `Sync-ReleaseQueue`, new counter and its accessor.
- `scripts/spec_catalog/release-queue.ps1` - the `-Reconcile` output line.
- `scripts/spec_catalog/release-queue.tests/Run-Tests.ps1` - regression cases.

### 3.2 Побочная находка: тест истекал на каждом релизе

The suite pinned `S1183` and release package 32 as its lease fixture. Both shipped, `-List -Release 32` went empty, and the lease case failed for a reason with nothing to do with leases. The subject ticket and its package are now read from the live queue at run time.

## 4. Проверка

- `scripts/spec_catalog/release-queue.tests/Run-Tests.ps1` - PASS, exit 0. Hermetic cases: in-file duplicate in the queue collapses to one line, in-file duplicate in the ready file collapses to one line, a ticket listed in both files ends as exactly one ready line, the dropped count is 3, verbatim lines and the `rel` column survive, and a second reconcile reports 0.
- The same fixture against the pre-fix library leaves every duplicate standing and grows the cross-file one - the cases fail without the fix.
- `release-queue.ps1 -Reconcile` on the live plan files - exit 0, byte-identical `PLAN/RELEASE_QUEUE.md` and `PLAN/RELEASE_READY.md`.
- `release-queue.ps1 -Validate` - `OK - 101 ticket(s), no drift`, exit 0.

## 5. Открытые вопросы / Research items

- **Resolved** - what makes the reconcile loop non-convergent: no dedupe step exists, and the cross-file shape mints a new line per run (§2).
- **Resolved** - which occurrence to keep when two lines disagree on `rel`: the first, because it is the topmost line the owner sees; content is refreshed from the catalog either way (§3).

## 10. Связи

- Найдено при S1695.
- Правит `Sync-ReleaseQueue`, вызываемый из `Write-Catalog` при каждой мутации каталога, то есть путь, общий для всех skill-ов.

---

## Last Audit

- **Date:** 2026-08-21
- **Auditor:** Antigravity AI
- **Scope:** Scripts, tests, spec catalog reconciliation
- **Findings:** P0: 0, P1: 0, P2: 0, P3: 0
- **Status:** Verified
