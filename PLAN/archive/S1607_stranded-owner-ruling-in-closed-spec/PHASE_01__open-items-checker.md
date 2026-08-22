# Phase 01 - Open-items checker

**Strategic spec:** [`../S1607_stranded-owner-ruling-in-closed-spec.md`](../S1607_stranded-owner-ruling-in-closed-spec.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Produce a standalone checker that reads a spec's research section and reports every item that is neither `Resolved` nor carried by a named ticket; no status-change path calls it yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/_lib.ps1` | Modified | ≤ 90 added |
| `scripts/spec_catalog/check-open-items-carried.ps1` | New | ≤ 160 |

---

## Steps

### Step 01.1 - Add a section extractor to the catalog library

**Files:** `scripts/spec_catalog/_lib.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three functions to `scripts/spec_catalog/_lib.ps1`. `Get-SpecSectionLines` takes either a spec path or an array of lines, plus a list of heading regexes, and returns the lines of the first top-level section whose heading TEXT matches one of them, each line carrying its 1-based file line number; the section ends at the next `## ` heading. Read a path with `-Encoding UTF8`, because spec files carry Russian prose and are decoded as OEM without it, and return an empty collection when the section is absent rather than throwing. `Get-ResearchSectionHeadingPattern` returns the heading spellings that mean "open questions belonging to the owner", covering `Research items`, `Open questions`, `Open items` and `Открытые вопросы`. `Get-OpenStatusPattern` returns the status-line regex, matching `Open` as a word, with or without bold, in either language, and not anchored to the start of the line.

**Why:**

The strategic spec's section 5.3 requires this parsing to be extracted so the next gate over a spec section does not re-author it; the idiom is already written three times independently in the repository, in the two existing gates and in `preview.ps1`. Section 5.1 forbids keying on the section number, because `## 6.` is not a stable slot - live specs put risks, criteria and implementation notes there - so a number-keyed reader both misses real sections and reads unrelated ones.

**Verification:**

- `Grep` - `function Get-SpecSectionLines`, `function Get-ResearchSectionHeadingPattern` and `function Get-OpenStatusPattern` each match exactly once in `scripts/spec_catalog/_lib.ps1`.
- `Grep` - `-Encoding UTF8` present inside `Get-SpecSectionLines`.
- Run `pwsh -NoProfile -Command ". scripts/spec_catalog/_lib.ps1; @(Get-SpecSectionLines -Path 'PLAN/S1612_add-new-maestro-features.md' -HeadingPattern (Get-ResearchSectionHeadingPattern)).Count"` - prints a non-zero integer, exit 0.
- Run the same call against a spec with no such section - prints `0`, exit 0.

**Status:** `[x]` done

---

### Step 01.2 - Write the open-items checker

**Files:** `scripts/spec_catalog/check-open-items-carried.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `scripts/spec_catalog/check-open-items-carried.ps1` modelled on the sibling gate `check-evidence-durable.ps1`: a mandatory `-Id` parameter, `_lib.ps1` dot-sourced, catalog record resolved across active and archive journals, and a header block naming the contract and the exit codes. Read the research section with `Get-SpecSectionLines` and the shared heading list, then split it into items at column zero, so that both the template shape `1. **Title**` with indented sub-bullets and a flat bullet list carrying one question per unindented bullet group correctly. Find every item whose status line matches the shared open-status pattern. An open item passes only when a literal `Carrier: Sxxxx` or `Носитель: Sxxxx` token appears within that item's own lines. Report each failing item with the repo-relative path, the 1-based line number and the item's title, naming an unfilled `Open / Resolved` placeholder as such, then print both ways out: flip the item to `Resolved` with its answer, or append the carrier token, quoting the exact literal form and the `/spec-draft` command that creates a carrier. Exit 0 when nothing fails, 1 when items fail, 2 when the invocation or the catalog cannot be read; write the exit codes in the header and use `Write-Error -ErrorAction Continue` before any non-1 exit so the documented code is actually reached.

**Why:**

The strategic spec's section 5.1 makes this checker the pillar that turns the research section into a closing contract, and its section 3.1 requires the message to name the concrete line and a ready command rather than a general rule; the `Write-Error -ErrorAction Continue` form is required because `_lib.ps1` sets `$ErrorActionPreference = 'Stop'`, under which a bare `Write-Error` throws and the documented exit code is never reached (S1070).

**Verification:**

- `Glob` - `scripts/spec_catalog/check-open-items-carried.ps1` exists.
- `Grep` - `Carrier` and `Носитель` both present in the file.
- `Grep` - `Exit codes:` present in the header block.
- Run the checker for `S1612` - exit 1, output names both open items with their line numbers.
- Run it for `S1177`, whose questions are a flat bullet list with a mid-line `Status: Open.` marker - exit 1, five items reported. This case is the reason the item split and the status pattern are not anchored.
- Run it for `S1607` - exit 0, output starts `PASS S1607`.
- Run with `-Id BADVALUE` and with an id absent from the catalog - exit 2 in both cases.
- Self-test the two exits on this spec's own file, restoring it afterwards and confirming the restore byte-for-byte: an item with `**Статус:** Open` and no token exits 1; the same item plus `Carrier: S1595` exits 0; an `Open / Resolved` placeholder exits 1 and prints the placeholder note.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Both scripts run and return their documented exit codes - no gradle build applies, this phase touches no Kotlin.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration not applicable - no Kotlin touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The checker exists and is correct in isolation, but nothing invokes it - a closing transition still succeeds with an uncarried open item. Phase 02 supplies the invocation. `Get-SpecSectionLines` is now the shared way to read a spec section and Phase 02 must not add a second one.

---

## Rollback Plan

Revert phase commit(s) - two script files, no catalog data touched, no user-facing surface changed.
