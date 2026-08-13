# Phase 03 - Debt-ticket touch frequency

**Strategic spec:** [`../S1595_detekt-preflight-coverage-gap.md`](../S1595_detekt-preflight-coverage-gap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01-02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Compute how often each detekt-debt ticket's file is actually touched, and present the four
tickets ranked by it. Write nothing to the catalog.

---

## Prerequisites

- [ ] `dev/CHANGELOG.md` present.
- [ ] S1198, S1247, S1311, S1541 resolvable through `select.ps1`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/measure-file-touch-frequency.ps1` | New | ≤ 150 |

---

## Steps

### Step 03.1 - Derive touch frequency from the dev log

**Files:** `scripts/quality/measure-file-touch-frequency.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a script that counts how many dev-log rows in `dev/CHANGELOG.md` name each given path,
> over a window passed as a parameter with a sensible default. Accept `-Paths` as one comma-joined
> argument. Print one row per path: count, first and last date seen, and the path. Read
> `dev/CHANGELOG.md` only - do not read git history.

**Why:**

Strategic §5.1 pillar 4 requires the priority input to be an observable quantity rather than a
feeling, and the dev log already records one row per logical change with its file path, so it is
the repository's own record of what gets touched. Git history is excluded because this repository
treats the working tree as truth and reserves git for release and commit flows.

**Verification:**

- `Glob` - `scripts/quality/measure-file-touch-frequency.ps1` exists.
- `Grep` - no `git log`, `git blame` or `git diff` invocation in the file.
- Run it over the four debt-ticket files - four rows print, each with a count.

**Status:** `[x]` done

---

### Step 03.2 - Rank the four debt tickets and record the result

**Files:** `temp/S1595/debt-ticket-ranking.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Resolve the file each of S1198, S1247, S1311 and S1541 is about, run the measurement over those
> four paths, and write the ranked result to `temp/S1595/debt-ticket-ranking.md` together with each
> ticket's current catalog priority. State plainly which ranking the measurement implies and where
> it disagrees with the current priorities. Do not call `update.ps1`; do not edit
> `PLAN/RELEASE_QUEUE.md`.

**Why:**

Strategic ADR-4 keeps this measurement advisory: work order belongs to the release queue and the
owner, and the catalog priority is only a tiebreak for tickets the queue does not list, so an
automatic rewrite would put the tool above the queue.

**Verification:**

- `Glob` - `temp/S1595/debt-ticket-ranking.md` exists and names all four ticket ids.
- `Grep` - `update.ps1` does not appear in this phase's scripts.
- `select.ps1` for each of the four ids shows an unchanged `priority` after this phase.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Four ticket priorities unchanged in the catalog (40 / 40 / 35 / 40, re-read after the phase).
- [x] Dev log entry added for the new script.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Result, and it contradicts the capture.** Measured over 90 days: S1198 41 touches, S1541 17,
S1247 1, S1311 1. The capture assumed all four files were "touched weekly"; the real spread is
41:1, while three of the four tickets carry the same priority of 40. Full record with caveats in
`temp/S1595/debt-ticket-ranking.md`.

**One defect found and fixed inside this phase.** The first version of the matcher scored all four
files zero against 25,511 rows - the dev log wraps its path column in backticks, and the raw
capture never matched. A zero that means "broken matcher" is indistinguishable from a zero that
means "never touched", so the fix carries a comment saying which one it prevents, and the run now
carries a sanity check against a file known to be busy.

---

## Handoff Notes to Next Phase

A measured ranking exists as a deferred owner decision (strategic §6.3). Phase 04 does not
document it as behaviour, because nothing changed in behaviour - it is an input awaiting a ruling.

---

## Rollback Plan

Delete the new script and the scratch report. No catalog or queue state was written.
