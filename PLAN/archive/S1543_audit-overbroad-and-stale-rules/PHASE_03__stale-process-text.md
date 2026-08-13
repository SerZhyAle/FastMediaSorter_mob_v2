# Phase 03 - Process text stops describing machinery that no longer exists

**Strategic spec:** [`../S1543_audit-overbroad-and-stale-rules.md`](../S1543_audit-overbroad-and-stale-rules.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Bring every text that describes a check into agreement with the check: three places promise a style sweep at the Draft to Approved flip that Phase 01 removed, and one gate header describes a superseded internal structure and a closed ticket as in progress.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-draft.md` | Modified | ≤ 160 |
| `.claude/reference/spec.md` | Modified | ≤ 140 |
| `.claude/commands/spec.md` | Modified | ≤ 210 |
| `scripts/quality/assert-neuroslop.ps1` | Modified | ≤ 100 |
| `.claude/commands/spec-update.md` | Modified | ≤ 200 |
| `.github/prompts/spec.prompt.md` | Modified | ≤ 200 |
| `.github/prompts/spec-update.prompt.md` | Modified | ≤ 200 |

> The last three rows were added during execution, not at planning time - the closure facade's document-registry gate named the sibling set and a grep over it found the same promise in three more places. Recorded in Results below.

> Other sessions edit this tree concurrently. Re-read each file immediately before editing it; do not edit from a copy read in an earlier phase.

---

## Steps

### Step 03.1 - Stop the drafting skill promising a later style sweep

**Files:** `.claude/commands/spec-draft.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Three statements in this file defer style sanitation to the Draft to Approved gate, which no longer performs it. In the opening paragraph, keep "no style sanitation" but drop the clause tying it to the Approval gate. In the numbered instruction that reads "Draft exempt - sanitation is Draft→Approved gate, not drafting friction", replace the parenthetical reason with the real one: the house text style does not apply to specification files at all, at any status (S1543). In the constraint bullet "No sanitation sweep", delete the trailing sentence "Cleanup at Draft → Approved, not here." and replace it with a sentence stating that no later stage performs it either. Leave the verbatim-capture guarantees untouched - they are the promise this change makes keepable.

**Why:**

Strategic §5.1 pillar C requires that no text promise a style sweep that will not happen; leaving the deferral in place is what sends the next session to edit verbatim captured material when the gate refuses, which is the failure this ticket exists to end.

**Verification:**

- `Grep` - `Cleanup at Draft` returns zero hits in `.claude/commands/spec-draft.md`.
- `Grep` - `sanitation is Draft` returns zero hits in that file.
- `Grep` - `no rewriting` still matches in that file, at least twice.
- `Grep` - `S1543` matches at least once in that file.

**Status:** `[x]` done

---

### Step 03.2 - Correct the spec-writer reference constraint

**Files:** `.claude/reference/spec.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> The Constraints section states that both the house text style and the Spec Writing style are gated at the Draft to Approved flip, so a Draft may keep rough phrasing and unclean punctuation and be cleaned as part of approval. Rewrite that sentence so it says two separate true things: the Spec Writing style constraints in CLAUDE.md section 1 apply to the file being authored, and the house text style does not apply to specification files at all, so nothing about a specification's punctuation is gated at any transition (S1543). Do not change the language, section and boundary constraints in the same bullet.

**Why:**

Strategic §11 criterion 5 requires that no process text promise a style cleanup of specifications at any stage, and this reference is what `/spec` reads immediately before the status flip.

**Verification:**

- `Grep` - `clean it as part of approval` returns zero hits in `.claude/reference/spec.md`.
- `Grep` - `S1543` matches at least once in that file.
- `Grep` - `body Russian` still matches in that file.

**Status:** `[x]` done

---

### Step 03.3 - Correct the spec-writer step that names the gate

**Files:** `.claude/commands/spec.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Step 6 tells the author that "Those constraints are gate-enforced at this flip and nowhere else" before running the two promotion commands. After Phase 01 the flip enforces only the §3.3 owner-inputs section. Rewrite the sentence to say that the flip is gated on §3.3 alone, and that the Spec Writing style constraints are an authoring standard with no gate behind them (S1543). Keep the instruction to read the reference Constraints section before promoting.

**Why:**

Strategic §5.1 pillar C requires the texts describing the gate to match it; a sentence claiming style is gate-enforced would keep producing style edits that the gate no longer asks for.

**Verification:**

- `Grep` - `gate-enforced at this flip` returns zero hits in `.claude/commands/spec.md`.
- `Grep` - `S1543` matches at least once in that file.
- `Grep` - `update.ps1 -Id \$ticketId -Status Approved` still matches in that file.

**Status:** `[x]` done

---

### Step 03.4 - Correct the umbrella gate header

**Files:** `scripts/quality/assert-neuroslop.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> The `.DESCRIPTION` block lists nine child scripts and states that cleanup of the catch and layout-color dimensions "is still in progress (S0383 Phases 03/04)". Neither is true: since S1338 the script forwards with no rule filter to `assert-source-gates.ps1`, which applies every rule in `Get-SourceRules` - sixteen today, including three the list never mentions - and `select.ps1 -Id S0383` reports the ticket Archived on 2026-06-09. Replace the child list with one sentence stating that the rule set is whatever `scripts/quality/lib/source-matchers.ps1` defines and is not duplicated here, and replace the in-progress sentence with a statement that the baselines are the current floors and ratchet down only (S1543 corrected the stale framing). Leave the ratchet contract, the in-process call-operator paragraph and the Modes block as they are.

**Why:**

Strategic §11 criterion 6 requires the umbrella gate header to describe what it does today, and research 02 found this the only artefact in the 51-gate inventory that is stale with evidence.

**Verification:**

- `Grep` - `still in progress` returns zero hits in `scripts/quality/assert-neuroslop.ps1`.
- `Grep` - `assert-trivial-comments.ps1` returns zero hits in that file.
- `Grep` - `source-matchers.ps1` matches in that file.
- `Grep` - `Ratchet contract` still matches in that file.
- Command `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate` exits with the same code as before the edit.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no compiled source changed in this phase.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added - deferred to Phase 04, which batches the whole ticket into one entry.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated - not applicable.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Results 2026-08-09

- Step 03.1 - the step anticipated three statements in `spec-draft.md`; on re-read only two tie sanitation to the Approval gate. The opening paragraph's "no `..`/`ё`/style sanitation, no Approval gate" lists the two as separate facts and is already true, so it was left as written. Both deferral statements were rewritten. Greps: `Cleanup at Draft` = 0, `sanitation is Draft` = 0, `no rewriting` = 2, `S1543` = 2.
- Step 03.4 - the prompt said the runner applies sixteen rules; the runner's own output on execution says **17** over one walk of 3,740 files. The header was written to name no count at all and point at `source-matchers.ps1` instead, which is why the discrepancy does not survive into the file. Research 02 corrected to the measured figure.
- Step 03.4 gate parity - `assert-neuroslop.ps1 -Gate` exit 0 before the edit and exit 0 after, both reporting `PASS (all dimensions at or below baseline)`.
- **Three files the plan missed, found by the closure facade.** The `document-registry` gate refused to pass the first closure run because the `repository-rules` record owns `.claude/commands/*.md`, `.claude/reference/*.md` and `.github/prompts/*.prompt.md` as one set, and named the sibling list. A grep over that set for the same promise found three more statements of it, all now corrected the same way: `.claude/commands/spec-update.md:36` ("sanitation .. enforced gate only at `Draft` -> `Approved`"), `.github/prompts/spec.prompt.md:52` ("Before promotion to `Approved`, clean and enforce the Author Style"), `.github/prompts/spec-update.prompt.md:130` ("Approval preparation owns that cleanup"). `.claude/commands/spec-update.md` is the one that mattered most - it is the instruction a refinement pass reads, so leaving it would have kept producing the exact edits this ticket exists to stop.
- Deliberately not changed in the same sweep: `CLAUDE.md:12` and `.github/copilot-instructions.md:10` ("Spec Writing .. Draft specs exempt") describe the Spec Writing rule, not the house text style, and remain true; `.github/prompts/catalog.prompt.md:207` and `.claude/commands/catalog.md:187` apply the style to catalog descriptions, which are not spec files. `CLAUDE.md:11`, `AGENTS.md:10` and `.github/copilot-instructions.md:8` already state the canon scope correctly and needed no edit - the always-on rule text was right all along; only the machinery and the process texts were wrong.

---

## Handoff Notes to Next Phase

Every text this ticket touched now agrees with the machinery it describes. Nothing remains that would instruct a future session to edit a specification for punctuation.

---

## Rollback Plan

Revert the four files - all are process text or a comment block, no behaviour and no user-facing surface changed.
