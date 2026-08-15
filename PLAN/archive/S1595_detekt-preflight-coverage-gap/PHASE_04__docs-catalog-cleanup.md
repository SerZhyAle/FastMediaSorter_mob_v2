# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1595_detekt-preflight-coverage-gap.md`](../S1595_detekt-preflight-coverage-gap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Bring the three registered documents that describe this tooling back in sync with what it now
does, and close the ticket through the facade.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a - regenerated |
| `docs/DEV_OPS.md` | Modified | ≤ 30 added |
| `docs/BUILD_TEST_FAST_PATH.md` | Modified | ≤ 6 added |

---

## Steps

### Step 04.1 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate`. Do not hand-edit the cheatsheet.

**Why:**

The document registry lists `docs/SCRIPT_CHEATSHEET.md` as a maintained render target with the
`workflow` trigger, and `assert-script-cheatsheet-sync.ps1` fails the closure when a script's
parameter block or synopsis changed without a regeneration - which Phases 01 and 03 both did.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` - exit 0.
- `Grep` - `detekt-scoped.ps1` appears in the cheatsheet.

**Status:** `[x]` done

---

### Step 04.2 - Describe the new cheap step in the operations doc

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> In the "Static analysis (detekt + ktlint)" section, add a short subsection describing the scoped
> preflight: what it runs, that its verdict is the real analyser's rather than an approximation,
> the three outcomes and what the degraded one means, and that it now aborts a closure before the
> gradle gate starts. Correct the "Detekt-clean-first authoring tips" list, which currently
> describes only the three-rule lexical world.

**Why:**

The registry record `developer-operations` owns this document, and CLAUDE.md Rule 19 points
authors at that exact section for detekt-clean-first guidance - so a section still describing a
three-rule check would send authors to advice the tooling no longer matches.

**Verification:**

- `Grep` - `detekt-scoped` appears in `docs/DEV_OPS.md`.
- `Grep` - the tips list no longer implies only three rules are pre-checked.

**Status:** `[x]` done

---

### Step 04.3 - Record the measured cost and close through the facade

**Files:** `docs/BUILD_TEST_FAST_PATH.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add the measured wall clock of the scoped preflight to the foreground/background table, beside
> the existing `assert-detekt.ps1 -Module app_v2` row. Then close the whole ticket through
> `scripts/post-change.ps1` with `-ChangeType Tooling`, naming the full changed set with `-Files`
> and adding `-ScopeToFile`, and read the printed verdict.

**Why:**

The registry record `quality-assurance` owns that document and its table is what decides whether a
step may be run in the foreground, so an unlisted step gets guessed at; and strategic §3.2 states
the foreground budget as a hard constraint, which is only checkable once the number is written down.

**Verification:**

- `Grep` - a row naming the scoped preflight exists in `docs/BUILD_TEST_FAST_PATH.md`.
- `post-change.ps1` prints `post-change: PASS` (or names each advisory) and exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` untouched - strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has an entry covering the change set (one row, set of 9).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Closure verdict:** `post-change: PASS WITH ADVISORIES (1) (Tooling, 4757 ms)`, exit 0. The single
advisory is `document-registry`, which withholds a bare PASS until the touched records are
acknowledged.

**Why the advisory was left standing rather than cleared.** `-RegistryAck` belongs on the FIRST
post-change call; it was omitted here, and post-change writes its dev-log row on every run, so
re-running to clear the advisory would have landed a duplicate changelog entry for one logical
change - and `dev/CHANGELOG.md` may not be hand-edited to remove it. The substantive obligation was
discharged instead: both records (`developer-operations`, `quality-assurance`) were read, and every
untouched sibling was checked for the same edit. Only `docs/CODE_AUDIT_PROTOCOL.md` mentions detekt
at all, and all four of its mentions are generic ("detekt + ktlint formatting ratchet gate",
"detekt static analysis for Kotlin", the upstream URL) - none describes the preflight's rule
coverage or its advisory status, so nothing in it became false. No sibling edit was needed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the three documentation files and re-run the cheatsheet generator. No behaviour change is
carried by this phase.
