# Phase 01 - Form and harness

**Strategic spec:** [`../S1343_robo-english-tactical-spec.md`](../S1343_robo-english-tactical-spec.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Produce the two artifacts the pilot needs: the robo-english step form as a pilot-only template variant, and the script that records pilot measurements. Neither the live template nor any command file is touched in this phase.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.1, §6.2, §6.4 are Resolved; §6.5 is deferred by design (see INDEX).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/templates/phase-file.robo.md` | New | ≤ 120 |
| `scripts/spec_catalog/plan-form-metrics.ps1` | New | ≤ 220 |

> No `res/layout*` file, no Activity/Fragment/View class, no settings surface is touched - the UI-placement decision requirement does not apply to this phase.

---

## Steps

### Step 01.1 - Write the pilot-only robo-english step template

**Files:** `.claude/templates/phase-file.robo.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `.claude/templates/phase-file.md` to `.claude/templates/phase-file.robo.md` and change only the step skeleton and the header comment. The header comment must state, in English, that this file is the S1343 pilot variant, that no command consumes it yet, and that it is deleted or promoted by S1343 Phase 03.
>
> Encode strategic §6.2 verbatim as the compression rule inside the header: the only things forbidden in `Prompt for developer:` are filler words and redundant turns of phrase - the named examples are `please`, `in order to`, and restating the step title in the step body. Full sentences are not shortened, and causal wording is never compressed.
>
> Encode strategic §6.4 by adding a mandatory `**Why:**` field to the step skeleton, placed between `**Prompt for developer:**` and `**Verification:**`. State in the header that `Why:` is at least one complete sentence, carries the reason the step exists rather than a restatement of what it does, and is exempt from every compression rule above.

**Why:**

Strategic §7 rates "потеря причинности при сжатии" as the highest-probability risk of this ticket, with S1225 as the precedent for a confidently-worded document asserting an invariant the code never had. A dedicated non-compressible field is the owner's chosen mitigation (§6.4), so the compression rule and the causality field must ship in the same artifact - shipping the compression rule alone would field-test exactly the failure mode the mitigation exists to prevent.

**Verification:**

- `Glob` - `.claude/templates/phase-file.robo.md` exists.
- `Grep` - `S1343` matches in the header comment of that file.
- `Grep` - `\*\*Why:\*\*` matches at least twice in that file (skeleton step plus header rule).
- `Grep` - `in order to` matches in that file (the forbidden-token list quotes it).
- `Grep` - `phase-file.robo` returns zero hits in `.claude/commands/` - no command consumes the variant yet.
- `Grep` - `\*\*Why:\*\*` returns zero hits in `.claude/templates/phase-file.md` - the live template is untouched.

**Status:** `[x] done`

**Step Log:**

- 2026-08-02 - Verification 6\6 PASS. Files: .claude/templates/phase-file.robo.md (+141 LOC, new). Counts: S1343 x4, `**Why:**` x3, `in order to` x1; zero hits for `phase-file.robo` under .claude/commands/ and zero `**Why:**` in the live template. Dev log recorded.

---

### Step 01.2 - Write the pilot metrics recorder

**Files:** `scripts/spec_catalog/plan-form-metrics.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `scripts/spec_catalog/plan-form-metrics.ps1` writing newline-delimited JSON rows to `dev/spec-form-pilot.jsonl`, creating the file when absent. Pass `-NoProfile` conventions of the surrounding `scripts/spec_catalog/` scripts and follow their parameter and output style.
>
> Actions: `-Action add` appends one row; `-Action list` prints existing rows; `-Action summary` computes the verdict. A row carries `kind` (`selection`, `measurement`, or `verdict`), `ticket`, `form` (`current` or `robo`), `toolCalls`, `questions`, `divergences`, and `note`.
>
> `-Action summary` implements strategic §6.3 verbatim: for each ticket compare its `robo` row against its `current` row and count the ticket as improved when `toolCalls` OR `questions` decreased AND `divergences` did not increase. Emit `adopt` when at least 2 of 3 tickets improved, otherwise `reject`, and print the per-ticket table it derived the verdict from.
>
> Per CLAUDE.md Rule 7, document every exit code the script returns in its header block and use `Write-Error -ErrorAction Continue` before any `exit N` where N is not 1. Exit 0 on success, 2 on an unreadable or malformed data file, 3 when `-Action summary` runs with fewer than two tickets measured.

**Why:**

The verdict rule of §6.3 is the one part of this ticket that must not be re-derived by an agent reading prose at decision time - strategic §1 and §7 both warn that the expected effect sits inside noise, which is exactly the regime where a hand-eyeballed comparison silently becomes the answer the reader already expected. Computing `adopt` / `reject` mechanically from recorded rows makes Phase 03 a lookup instead of a judgement call.

**Verification:**

- `Glob` - `scripts/spec_catalog/plan-form-metrics.ps1` exists.
- Run `pwsh -NoProfile -File scripts/spec_catalog/plan-form-metrics.ps1 -Action list` - exit code 0.
- Run `pwsh -NoProfile -File scripts/spec_catalog/plan-form-metrics.ps1 -Action summary` - exit code 3 (fewer than two tickets measured).
- `Grep` - `Exit codes` matches in the script header.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit code 0.
- `Grep` - `Write-Error` lines in the script each carry `-ErrorAction Continue`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-02 - Verification 6\6 PASS. Files: scripts/spec_catalog/plan-form-metrics.ps1 (+186 LOC, new). `-Action list` exit 0, `-Action summary` exit 3 (no data yet), `assert-exit-contract.ps1` exit 0, `Exit codes` present in header, all 8 `Write-Error` lines carry `-ErrorAction Continue`. Dev log recorded.
- 2026-08-02 - Out-of-scope, not parked (trivial, CLAUDE.md 3.1): `assert-exit-contract` reports `scripts/spec_catalog/spec-next-session.ps1:207` exits 3 with no reason printed. One-line fix, unrelated to S1343, gate still PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no Kotlin, resources, or gradle files.
- [x] `Grep` for `TODO(phase-01)` returns zero hits - 0 matches repo-wide.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1` - both runs PASS.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1 only (no Kotlin, so Layers 2-4 do not apply): script naming matches its `scripts/spec_catalog/` siblings; the `exit 2` inside `Read-Rows` terminates the process by design; empty-input path returns exit 3 and was exercised live; `-Action summary` prints the verdict token as its last line, which is what step 02.4 reads.

**Boundary notes:**

- `docs/SCRIPT_CHEATSHEET.md` was regenerated here rather than waiting for step 04.3 - the new script made a repo-wide gate stale, and leaving it red hands a failing closure to any concurrent session. `assert-script-cheatsheet-sync.ps1` now exits 0. Step 04.3 stays in the plan and is idempotent.
- Step 04.3's verification named a non-existent gate (`assert-script-cheatsheet.ps1`); corrected in the phase file to `assert-script-cheatsheet-sync.ps1`.
- `post-change` raised a `document-registry` advisory for the `repository-rules` record on step 01.1. No sibling rule file needs the same edit: the variant template is inert by construction and no command reads it. Sibling updates are exactly the adopt branch of step 03.1. Ack deferred to the ticket's final closure in step 04.4 (`-RegistryAck 'repository-rules'`) to avoid a duplicate changelog row.

---

## Handoff Notes to Next Phase

`.claude/templates/phase-file.robo.md` is the exact form under test - Phase 02 authors pilot plans from it and from nothing else. `plan-form-metrics.ps1` owns the verdict rule; Phase 03 reads its `summary` output rather than re-deriving significance from the recorded rows.

---

## Rollback Plan

Delete both new files. Nothing else references them - no command, script, or gate consumes either artifact until Phase 03.
