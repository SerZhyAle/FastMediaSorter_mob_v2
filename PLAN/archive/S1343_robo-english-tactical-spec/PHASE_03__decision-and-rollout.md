# Phase 03 - Decision and rollout

**Strategic spec:** [`../S1343_robo-english-tactical-spec.md`](../S1343_robo-english-tactical-spec.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Execute the recorded verdict: on `adopt`, fold the robo form into the live template and every command that writes or reads a step; on `reject`, remove the variant. Then answer strategic §6.5 on the tooling side.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `dev/spec-form-pilot.jsonl` carries exactly one `verdict` row (Phase 02).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/templates/phase-file.md` | Modified (adopt branch only) | ≤ 120 |
| `.claude/commands/spec-tech.md` | Modified (adopt branch only) | ≤ 210 |
| `.claude/commands/spec-dev.md` | Modified (adopt branch only) | ≤ 175 |
| `.claude/commands/spec-all.md` | Modified (adopt branch only) | ≤ 250 |
| `.claude/templates/phase-file.robo.md` | Deleted (both branches) | - |
| `scripts/quality/assert-tactical-step-form.ps1` | New (gate branch only) | ≤ 180 |
| `scripts/quality/tactical-step-form-baseline.txt` | New (gate branch only) | 1 |
| `scripts/quality/assert-fast-gates.ps1` | Modified (gate branch only) | ≤ 165 |
| `.github/prompts/spec-tech.prompt.md` | Modified (adopt branch only) | ≤ 360 |
| `.github/prompts/spec-dev.prompt.md` | Modified (adopt branch only) | ≤ 175 |

> Budgets corrected 2026-08-02 after the `/spec-check` audit. The original table set every command file at ≤ 200 without measuring them: `spec-all.md` was already 241 lines before this ticket existed and `spec-tech.md` 201, so two of the three budgets were unmeetable the moment they were written. The numbers above are each file's real ceiling (current size plus a small margin), not a target the files were trimmed to reach.

---

## Steps

### Step 03.1 - Execute the verdict

**Files:** `.claude/templates/phase-file.md`, `.claude/commands/spec-tech.md`, `.claude/commands/spec-dev.md`, `.claude/commands/spec-all.md`, `.claude/templates/phase-file.robo.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read the single `verdict` row from `dev/spec-form-pilot.jsonl` and branch on its value.
>
> On `adopt`: move the `**Why:**` field and the compression rule from `.claude/templates/phase-file.robo.md` into `.claude/templates/phase-file.md`, keeping the live template's existing sections intact. In `.claude/commands/spec-tech.md` step 5, require the field on every written step. In `.claude/commands/spec-all.md` Stage S1, require the same for compact-spec phases. In `.claude/commands/spec-dev.md` step 3, make the reader read `Why:` alongside `Prompt for developer:`. Then delete the variant file.
>
> On `reject`: change no live template and no command file, and delete the variant file.
>
> Both branches end with the variant gone - it exists only for the duration of the pilot.

**Why:**

The writer and the reader have to change together: strategic §5.2 names `/spec-tech`, `/spec-dev`, and `/spec-check` as the three consumers of the tactical form, and a field that `/spec-tech` emits but `/spec-dev` never reads is pure cost - it lengthens every future plan while delivering none of the causality the field exists to carry.

**Verification:**

- `Glob` - `.claude/templates/phase-file.robo.md` does not exist.
- Adopt branch: `Grep` - `\*\*Why:\*\*` matches in `.claude/templates/phase-file.md`.
- Adopt branch: `Grep` - `Why:` matches in `.claude/commands/spec-dev.md` within its step-3 paragraph.
- Adopt branch: `Grep` - `Why:` matches in `.claude/commands/spec-tech.md` step 5 and in `.claude/commands/spec-all.md` Stage S1.
- Reject branch: `Grep` - `\*\*Why:\*\*` returns zero hits in `.claude/templates/phase-file.md`.
- Reject branch: `Grep` - `Why:` returns zero hits in the step-5 section of `.claude/commands/spec-tech.md`, the step-3 section of `.claude/commands/spec-dev.md`, and the Stage S1 section of `.claude/commands/spec-all.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS on the adopt branch (the verdict row reads `adopt`). Reject-branch
  predicates not applicable. `phase-file.robo.md` deleted; `**Why:**` present three times in
  `.claude/templates/phase-file.md` (header rule plus both step skeletons); `Why:` present in
  `spec-tech.md:127` (step 5), `spec-dev.md:51` (step 3) and `spec-all.md:113` (Stage S1). The only
  surviving `phase-file.robo` reference repo-wide is the historical `dev/CHANGELOG.md` row that records
  its creation, which is a journal of what happened and is correct to keep.
- 2026-08-02 - Corrected a contradiction the rollout created rather than shipping it: `spec-tech.md`'s
  constraint "Do not duplicate strategic content - tactical says *what*, not *why*" directly forbade the
  field being added two sections above it. Amended to carve out the `Why:` field explicitly - it quotes
  one sentence of sourced reason so `/spec-dev` need not open the strategic spec, it does not restate
  the section.
- 2026-08-02 - Sibling rule files synced in the same edit, which the step did not name. `post-change`'s
  `document-registry` gate flagged the `repository-rules` record; grepping its sibling list for
  `Prompt for developer` / `phase-file.md` found exactly two that describe the step form -
  `.github/prompts/spec-tech.prompt.md` (which carries its own inline copy of the phase skeleton, plus
  the same "tactical says what, not why" constraint) and `.github/prompts/spec-dev.prompt.md` (step 3).
  Both got the same edit as their `.claude/commands/` counterparts. `CLAUDE.md`, `AGENTS.md`,
  `GEMINI.md`, `.claude/reference/*` and `.claude/agents/*` mention neither string and need no change.
  Leaving the non-Claude prompts behind would have made this form Claude-only, which is exactly the
  drift `AGENTS.md`'s own sync rule exists to prevent. Registry ack is deferred to step 04.4 so the
  ticket produces one changelog row, per Phase 01's boundary note.
- 2026-08-02 - Line budgets. `.claude/templates/phase-file.md` finished at 116 of its 120 budget, but
  only after the header rule was rewritten in long unwrapped lines - the first draft landed at 128 and
  was over. The three command-file budgets in "Files Touched" above (≤ 200) were authored without
  checking the files: `spec-all.md` was already 241 lines before this ticket touched it and
  `spec-tech.md` 201, so those two budgets were unmeetable as written. Each got a single added line;
  the budget numbers are wrong, not the edits.

---

### Step 03.2 - Answer strategic §6.5 on the tooling side

**Files:** `scripts/quality/assert-tactical-step-form.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Decide the §6.5 gate question from the verdict, not from preference. On `reject`, write no gate - there is no form to enforce. On `adopt`, write `scripts/quality/assert-tactical-step-form.ps1` checking that every `### Step` block in `PLAN/*/PHASE_*.md` carries a non-empty `**Why:**` field of at least one sentence, and register it in `scripts/quality/assert-fast-gates.ps1`.
>
> Scope the gate so the always-dirty tree and the existing plan backlog do not fail it, matching how the other count-ratchet gates behave. A `HEAD` diff cannot do that here - `PLAN/` is gitignored, so no phase file has a HEAD blob to compare against (see this step's log); use a checked-in single-integer baseline instead, the same contract the em-dash / stub-todo / flavor-flag gates use. Per CLAUDE.md Rule 7 document its exit codes in the header: 0 pass, 1 a step missing `Why:`, 2 cannot verify.
>
> Record which branch was taken in `dev/spec-form-pilot.jsonl` with one `-Action add -Kind verdict` row noting `gate` or `no-gate`.

**Why:**

Strategic §6.5 is the one open item in this spec, and it is open precisely because it is downstream of the pilot - a gate written before the verdict would enforce a form the measurement might have rejected, and a gate skipped after an `adopt` verdict leaves the rule to agent memory, which strategic §5.3 explicitly rules out as the enforcement mechanism.

**Verification:**

- Adopt branch: `Glob` - `scripts/quality/assert-tactical-step-form.ps1` exists; run it - exit code 0 on a clean tree.
- Adopt branch: `Grep` - `assert-tactical-step-form` matches in `scripts/quality/assert-fast-gates.ps1`.
- Adopt branch: run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit code 0.
- Reject branch: `Glob` - `scripts/quality/assert-tactical-step-form.ps1` does not exist.
- `Grep` - `"kind":"verdict"` matches exactly twice in `dev/spec-form-pilot.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 5/5 PASS on the adopt branch. `assert-tactical-step-form.ps1` written
  (145 lines, budget 180), exit 0 clean; registered in `assert-fast-gates.ps1` (header list plus the
  `$gates` table, `-Quiet`); `assert-exit-contract.ps1` exit 0; second `verdict` row recorded as `gate`.
  Ratchet proven both ways before being trusted: with the baseline forced to 905 the gate exits 1 and
  prints `expected: <= 905 | actual: 906`, and at 906 it exits 0.
- 2026-08-02 - **Deviation, and the reason the step's own design could not be built.** The step
  specified diff-scoping against `HEAD`. `PLAN/` is gitignored (`.gitignore` line 144), so
  `git diff --name-only HEAD` returns zero phase files and `git ls-files --others --exclude-standard`
  excludes them as ignored - the first implementation of this gate reported "judged 0 phase file(s)"
  and would have passed forever without reading anything. Replaced with a single-integer baseline at
  `scripts/quality/tactical-step-form-baseline.txt`, which is what the sibling ratchet gates
  (`em-dash`, `stub-todo`, `flavor-flag`) actually use; the step text above is corrected to match.
  Seeded at 906 Why-less steps across 302 existing phase files - that is the pre-form backlog, and the
  ratchet's only claim is that it does not grow.
- 2026-08-02 - Out-of-scope finding, parked (CLAUDE.md 3.1): `assert-exit-contract` reports one
  "reasonless exit" at `scripts/spec_catalog/spec-next-session.ps1:207` on every run. It is a false
  positive - that `exit 3` is `CheckContext`'s normal signal and its reason is the JSON printed
  immediately above - and the obvious fix would break the JSON contract `/spec-next` Stage 5b parses.
  Phase 01's step log had recorded the same finding as a trivial one-liner, which it is not. Parked as
  `S1368 bugfix-exit-contract-false-reasonless` after dedup (`S1192` is the same gate, different cause).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no Kotlin, resources, or gradle files.
- [x] `Grep` for `TODO(phase-03)` returns zero hits - 0 in code and 0 in this ticket's markdown.
- [x] Dev log entry added for every modified file via `post-change.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layers 2-8 do not apply (no Kotlin,
  no lifecycle, no Room, no DI). Layer 1 on the new gate: exit codes documented and all three
  reachable, every `Write-Error` carries `-ErrorAction Continue`, `assert-exit-contract` exit 0, and
  the ratchet was exercised in both directions rather than only on the green path. One defect in the
  gate's own output was found and fixed during the audit - `-UpdateBaseline` printed "(was present)"
  unconditionally because it tested for the file after writing it; it now reports the real previous
  value. Registration verified by reading `assert-fast-gates.ps1` back, not by assuming the edit took.

---

## Handoff Notes to Next Phase

The verdict has been executed in tooling and `dev/spec-form-pilot.jsonl` carries two verdict rows: the form decision and the gate decision. Phase 04 writes both back into the strategic spec - Phase 03 deliberately edits no file under `PLAN/`.

---

## Rollback Plan

Revert the phase commit. The adopt branch touches four text files under `.claude/` and optionally adds one gate script; no product code, resource, or build file is involved, and no already-written plan is rewritten by either branch.
