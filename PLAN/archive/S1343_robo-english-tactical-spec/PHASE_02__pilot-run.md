# Phase 02 - Pilot run

**Strategic spec:** [`../S1343_robo-english-tactical-spec.md`](../S1343_robo-english-tactical-spec.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-02
**Completed:** 2026-08-02

> **Unblocked 2026-08-02 - the owner picked option B (proxy read-through) in strategic §6.3.** The steps below already implement that branch and need no rewrite: pilot tickets come from `Implemented` / `Verified`, no plan is executed, and the substituted metric is surfaced for owner review in Phase 04 rather than adopted silently.

---

## Objective

Measure the two plan forms against each other on three comparable already-implemented tickets and record the numbers, producing the `adopt` / `reject` verdict that Phase 03 consumes.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `.claude/templates/phase-file.robo.md` exists (Phase 01).
- [ ] `scripts/spec_catalog/plan-form-metrics.ps1` exists and `-Action list` exits 0 (Phase 01).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/spec-form-pilot.jsonl` | New | ≤ 40 rows |
| `temp/S1343/pilot/<Sxxxx>/robo/PHASE_NN__<slug>.md` | New (scratch, 3 files) | ≤ 200 each |
| `temp/S1343/pilot/<Sxxxx>/current/PHASE_NN__<slug>.md` | New (scratch, 3 files) | ≤ 200 each |
| `temp/S1343/select-pilots.ps1` | New (scratch) | ≤ 100 |
| `temp/S1343/scrub-plan.ps1` | New (scratch) | ≤ 100 |

> Pilot plan variants are scratch artifacts and live under `temp/S1343/` per CLAUDE.md Rule 1. No file under `PLAN/` other than this ticket's own folder is created or edited by this phase, and no other ticket's plan is rewritten in place.

---

## Steps

### Step 02.1 - Select the three pilot tickets

**Files:** `dev/spec-form-pilot.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Query the catalog for candidates with `pwsh -NoProfile -File scripts/spec_catalog/search.ps1 -Status Implemented -Format json` and again with `-Status Verified`. Keep only tickets whose `tier` is `3`, whose tactical folder `PLAN/<Sxxxx>_<slug>/` exists, and whose phase count is within one of the other candidates. From that set take three tickets whose total step counts are closest together.
>
> Require `Implemented` or `Verified` status: divergence can only be counted against a reference implementation that already exists in the working tree.
>
> Record the choice with one `-Action add -Kind selection` call per chosen ticket, putting the ticket's phase count and step count in `note`.

**Why:**

Strategic §6.3 fixes the comparability rule as same Tier and comparable phase/step counts, and §7 rates "эффект окажется в пределах шума" as high-probability - with an effect that small, an uncontrolled difference in ticket size between the two arms would dominate the measurement and produce a confident verdict about ticket size rather than about plan form.

**Verification:**

- `Glob` - `dev/spec-form-pilot.jsonl` exists.
- `Grep` - `"kind":"selection"` matches exactly 3 times in `dev/spec-form-pilot.jsonl`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/plan-form-metrics.ps1 -Action list` - exit code 0, prints 3 rows.
- For each selected `Sxxxx`: `Glob` - `PLAN/<Sxxxx>_*/INDEX.md` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. Only 5 catalog tickets satisfy tier=3 + `Implemented`/`Verified` + a
  tactical folder, so the comparability rule (phase spread <= 1) admitted exactly two candidate triples;
  `temp/S1343/select-pilots.ps1` picked the one with the smaller total-step spread - S1350 (3 phases,
  6 steps), S1168 (3 phases, 9 steps), S1152 (4 phases, 12 steps), spread 6, versus S1010/S1152/S1009 at
  spread 14. Three `selection` rows recorded; `-Action list` exit 0 printing 3 rows; all three
  `PLAN/<Sxxxx>_*/INDEX.md` present.

---

### Step 02.2 - Author the robo-form copy of one phase per pilot ticket

**Files:** `temp/S1343/pilot/<Sxxxx>/robo/PHASE_NN__<slug>.md` (3 files)
**Depends on:** Step 02.1

**Prompt for developer:**

> For each of the three selected tickets pick the phase with the median step count and rewrite that one phase file into the robo form, using `.claude/templates/phase-file.robo.md` as the only formatting authority. Write the result to `temp/S1343/pilot/<Sxxxx>/robo/PHASE_NN__<slug>.md` and leave the original under `PLAN/` untouched.
>
> Carry over every step, every `Files:` row, and every `Verification:` predicate unchanged - the rewrite touches `Prompt for developer:` wording only, and adds the `Why:` field required by the variant template.
>
> Take the `Why:` content from the strategic spec of that ticket, not from the step body, and do not invent a reason the strategic spec does not state. If a step's reason cannot be found in its strategic spec, write `Why: not stated in strategic spec` verbatim and record the count of such steps in step 02.3's `note`.

**Why:**

Holding the step set, file rows, and verification predicates identical across both arms is what makes the later comparison attributable to wording rather than to content, and the explicit `not stated in strategic spec` marker keeps an unsourced rationale from being invented during the rewrite - strategic §7 names exactly that failure ("сжатый текст звучит увереннее, чем обоснован") as the S1225 repeat risk.

**Verification:**

- `Glob` - `temp/S1343/pilot/*/robo/PHASE_*.md` matches exactly 3 files.
- For each pilot file: `Grep` - `\*\*Why:\*\*` match count equals the `### Step` match count in the same file.
- For each pilot file: its `### Step` match count equals the `### Step` count of the corresponding original under `PLAN/`.
- For each pilot file: `Grep` - `\*\*Verification:\*\*` match count equals that of the corresponding original.
- `Grep` - `please|in order to` returns zero hits across `temp/S1343/pilot/*/robo/PHASE_*.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 5/5 PASS. Three robo files written (S1350 PHASE_03 2 steps, S1168 PHASE_01
  3 steps, S1152 PHASE_03 3 steps); per file `**Why:**` count equals `### Step` count and both the step
  count and the `**Verification:**` count equal the corresponding original's; zero `please|in order to`
  hits. One step could not source a reason and carries `not stated in strategic spec` verbatim (S1350
  step 03.1, catalog regeneration - S1350's strategic spec states no reason for it); the other seven
  are sourced from a named section of their own strategic spec.
- 2026-08-02 - **Deviation, recorded not silent.** The step as written left the two arms carrying
  different information, not just different wording: an already-executed `PLAN/` phase file also carries
  `**Step Log:**` blocks and `[x] done` markers, and S1350's logs name the exact files, line numbers and
  even the mistakes of the real implementation - an answer key the robo copy (which carries over only
  steps, `Files:` rows and `Verification:` predicates) never had. Measuring that would have measured who
  saw the answer key. Both arms are therefore built from the same mechanical scrub
  (`temp/S1343/scrub-plan.ps1`: strips `**Step Log:**` blocks, resets step/criteria done-markers and the
  phase header's completion fields, touches nothing else), and the current-form arm is served from
  `temp/S1343/pilot/<Sxxxx>/current/` instead of straight from `PLAN/`. Side effect worth keeping: both
  arms now sit under an identically-shaped `temp/S1343/pilot/` path, so neither is distinguishable by
  its location either.
- 2026-08-02 - The robo template's `<!-- S1343 PILOT VARIANT -->` header comment is deliberately not
  copied into the pilot files - step 02.3 requires the reading agents to be blind to this ticket.

---

### Step 02.3 - Run the paired read-through and record six measurements

**Files:** `dev/spec-form-pilot.jsonl`
**Depends on:** Step 02.2

**Prompt for developer:**

> For each pilot ticket run two independent read-only agents. Give agent A only the current-form copy under `temp/S1343/pilot/<Sxxxx>/current/`; give agent B only the robo-form copy under `temp/S1343/pilot/<Sxxxx>/robo/`. Both copies come from the same scrub of the `PLAN/` original, so the arms differ in wording alone (see step 02.2's deviation log). Neither agent sees the other form, the strategic spec, or this ticket.
>
> Instruct each agent to return an execution intent and nothing else: the list of files it would touch, one line per concrete edit it would make, and the list of questions it could not answer from the plan it was given. Forbid both agents from editing any file.
>
> Record one `-Action add -Kind measurement` row per agent with `toolCalls` set to that agent's tool-call count, `questions` set to its question count, and `divergences` set to the number of files in its intended file list that the ticket's shipped implementation did not touch, plus the number of files the shipped implementation touched that the agent missed.

**Why:**

The two agents must be blind to each other and to the strategic spec because the measured quantity is how much a reader can execute from the plan alone - an agent that can consult the strategic spec recovers the causality the robo form is suspected of dropping, which would mask the exact effect §7 flags as the top risk.

**Verification:**

- `Grep` - `"kind":"measurement"` matches exactly 6 times in `dev/spec-form-pilot.jsonl`.
- `Grep` - `"form":"current"` matches exactly 3 times among measurement rows.
- `Grep` - `"form":"robo"` matches exactly 3 times among measurement rows.
- Run `pwsh -NoProfile -File scripts/spec_catalog/plan-form-metrics.ps1 -Action list` - exit code 0, prints 9 rows.
- No measurement row carries a null or absent `toolCalls`, `questions`, or `divergences` value.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 5/5 PASS. Six `android-solution-researcher` agents (read-only tool set, no
  `Write`/`Edit`), one per ticket per form, each forbidden to read anything under `PLAN/` or any other
  file under `temp/`. Recorded current -> robo: S1350 toolCalls 7 -> 6, questions 3 -> 4, divergences
  0 -> 0; S1168 toolCalls 6 -> 9, questions 0 -> 1, divergences 0 -> 0; S1152 toolCalls 10 -> 14,
  questions 4 -> 3, divergences 1 -> 1.
- 2026-08-02 - `toolCalls` is taken from the harness's own `tool_uses` counter, not from the agent's
  self-report, because the two disagreed once (S1152 robo: harness 14, self-report 13) and a
  self-reported count is the agent grading its own metric. The other five matched exactly.
- 2026-08-02 - Divergence reference set per phase = its `Files Touched` table expanded to concrete
  paths, plus the spec-lifecycle files a step explicitly commands (`PLAN/spec-catalog.jsonl` and the
  strategic spec where a step runs `update.ps1`). The plan file itself and its `INDEX.md` are excluded
  from both sides: they are the artifact under measurement, appear in both arms by construction, and
  are not part of the shipped code change. Both S1152 agents missed `MainActivity.kt`, and for the same
  stated reason - the wiring it asks for already exists in the tree.
- 2026-08-02 - Worth carrying into Phase 03's reading of the verdict: five of the six agents reported
  that the work is already implemented, which is inherent to option B (pilot tickets must be
  `Implemented`/`Verified` for a divergence reference to exist at all). That inflates `questions` for
  both arms symmetrically, but it means the numbers measure comprehension of a plan whose work is done,
  not of a plan about to be executed.

---

### Step 02.4 - Compute and record the verdict

**Files:** `dev/spec-form-pilot.jsonl`
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/spec_catalog/plan-form-metrics.ps1 -Action summary` and append its `adopt` or `reject` result as a `-Kind verdict` row without editing, re-weighting, or overriding it. Put the per-ticket improved / not-improved breakdown the script printed into `note`.

**Why:**

Strategic §3.1 records the owner's instruction verbatim - "Решение принимается по замеру на паре тикетов, а не по ощущению" - so the recorded verdict has to be the script's output rather than a reading of the numbers, otherwise the decision Phase 03 executes is a judgement wearing a measurement's clothes.

**Verification:**

- Run `pwsh -NoProfile -File scripts/spec_catalog/plan-form-metrics.ps1 -Action summary` - exit code 0.
- `Grep` - `"kind":"verdict"` matches exactly once in `dev/spec-form-pilot.jsonl`.
- The verdict row's value equals the string printed by `-Action summary` - `adopt` or `reject`, no third value.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. `-Action summary` exit 0 printing `adopt` (2 of 3 improved,
  threshold 2): S1350 improved on `toolCalls`, S1152 improved on `questions`, S1168 improved on
  neither; no arm increased `divergences`. Exactly one `verdict` row, its value the script's own token,
  recorded without re-weighting. The row's `note` carries the token first and the per-ticket breakdown
  after it, because step 02.4 asks for both in that one field.
- 2026-08-02 - The verdict is `adopt` on a 2-of-3 rule where each ticket improved on a different
  metric and each improvement was one unit, while the losing metric moved against it by the same
  order. Strategic §7 rates "эффект окажется в пределах шума" as high-probability and this is what
  that looks like from inside the rule. The rule is still the rule - §3.1 forbids substituting a
  reading of the numbers for the measurement - so `adopt` stands as recorded and Phase 03 executes it.
  Phase 04's owner review of the substituted metric is where this observation belongs, and it is
  carried there rather than used to override the row here.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no Kotlin, resources, or gradle files.
- [x] `Grep` for `TODO(phase-02)` returns zero hits - 0 in code (`app_v2`, `wear`, `scripts`,
  `temp/S1343`) and 0 in this ticket's own markdown. A repo-wide grep returns 379, every one of them
  the criterion sentence itself quoted in another phase file; the predicate has to be scoped or it can
  never pass anywhere.
- [x] Dev log entry added for `dev/spec-form-pilot.jsonl` via `post-change.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layers 2-8 do not apply (no Kotlin,
  no lifecycle, no Room, no DI, no build change). Layer 1: line budgets hold (robo files 123/126/149
  and current files 111/114/140, budget 200; `dev/spec-form-pilot.jsonl` 10 rows, budget 40); the two
  helpers under `temp/S1343/` are scratch by Rule 1, are not under `scripts/`, and carry documented
  exit codes anyway; no file outside this ticket's own folder, `dev/spec-form-pilot.jsonl` and
  `temp/S1343/` was created or edited, and no other ticket's plan was rewritten in place.

---

## Handoff Notes to Next Phase

`dev/spec-form-pilot.jsonl` carries three selection rows, six measurement rows, and exactly one verdict row. Phase 03 branches on that verdict value alone and does not re-read the measurements. The value is `adopt`.

Two observations belong in Phase 04's owner review of the substituted metric, not in Phase 03's branch: the margin is one unit per ticket on a different metric each time, which is what strategic §7's "эффект в пределах шума" looks like from inside a 2-of-3 rule; and option B's requirement that pilot tickets be already implemented means five of six agents answered about work already done.

---

## Rollback Plan

Delete `dev/spec-form-pilot.jsonl` and `temp/S1343/pilot/`. No tracked file outside this ticket was modified, and no other ticket's plan was rewritten.
