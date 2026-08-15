---
description: "Use to run the full spec pipeline end to end - idea to verified implementation (research, spec, tactical, dev, check). Triggers: 'spec-all', 'take this from idea to done'."
---

# Full Spec Pipeline Orchestrator

Run complete spec pipeline (idea -> verified impl), fully automated. Forward bias over correctness theatre: patch spec and continue. Stop only when human input genuinely required. Picks up a spec at any stage/status. Defer unresolvable human questions to final report; never block mid-pipeline on skippable item.

Reference companion: `.claude/reference/spec-all.md` - never read wholesale; each pointer below names its section and the condition that makes it worth opening.

## Usage

```text
/spec-all <idea text>           # new spec from idea
/spec-all <path/to/idea.md>     # new spec from file
/spec-all <Sxxxx>               # resume existing spec by ticket id
/spec-all <slug>                # resume existing spec by slug
```

`$ARGUMENTS` resolution order:
1. Ticket id `S\d{4}` -> resolve and **resume**.
2. Path to existing file -> read idea, then check slug collision for existing spec.
3. Slug matching existing `PLAN/Sxxxx_<slug>.md` -> **resume**.
4. Otherwise -> new spec from idea text.

---

## Paths

```text
Simple  ->  Stage 0 -> S1(compact spec) -> S2(impl) -> S3(build) -> S4(audit+report)
Full    ->  Stage 0 -> F1(strategic)    -> F2(tactical) -> F3(impl) -> F4(build) -> F5(audit+report)
```

MAX_FIX_ITERATIONS = 5. MAX_BUILD_RETRIES = 3.

---

## Stage 0 - Bootstrap + State Detection

Parse `$ARGUMENTS`. Blank -> abort: "No idea provided."

### 0a - Resolve existing spec (resume mode)

Argument is ticket id or slug -> resolve:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
# or
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json
```

Resolved -> read strategic spec file, read current `Status:`, **jump to resume stage** per Resume Map. Do NOT re-create / re-validate done work.

**Preflight handoff (from `/spec-next`).** If `$ARGUMENTS` carries a `preflight:` context line, trust it and skip 0a `select.ps1` resolve **and** 0a-drift for this ticket - `/spec-next`'s preflight already resolved `status`, `tactical_folder`, `last_audit`, `timber_tags_kt`, `depends_on`, and drift verdict. Key Resume Map off the handed `status`; only re-read spec file body (needed for content), not catalog metadata.

### 0a.5 - Ticket lease ownership

After a ticket id is resolved and before research, planning, or implementation, claim its lease:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id <Sxxxx> -Reason "/spec-all"
```

Exit 0 owns or refreshes the lease. Exit 3 means a live sibling owns the ticket: report its id and stop before any work. The top-level `/spec-all` invocation is the lease owner, including when `/spec-next` already claimed the same ticket in the same session. Re-claim the lease at long-running phase boundaries to refresh its heartbeat.

When delegating to `/spec-dev`, include the literal context `lease-owner=spec-all`. That is a parent-owned lease: `/spec-dev` may refresh it but must not release it.

### 0a-drift - Code-vs-spec drift check (resume modes only)

Skip this step entirely when `preflight:` context line present - `/spec-next` already ran drift-check and handed verdict. Otherwise, before delegating to F1/F2 for a `Draft` / `Approved` / `Tactical` / `Broken` spec:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/drift-check.ps1 -Id <Sxxxx>
```

Exit 1 (`DRIFT`) = git commits with spec id marker and/or inline `// Sxxxx:` markers already exist in `app_v2/src/`. Action:

- `## Last Audit` missing -> switch to **review mode**: read strategic file, write a `## Last Audit` block summarising what is already in code (file paths, commit shas, residual gaps), jump to F5 (or set `BlockNeedUserTest` if device-test is the only remaining gate).
- `## Last Audit` present -> proceed with normal Resume Map (audit block already reflects code).

What it catches: `.claude/reference/spec-all.md` section 1 - read only if tempted to skip this check.

### 0b - Resume Map (existing spec)

| Current `Status:` | Resume stage |
| --- | --- |
| `Draft` | F1 (finish/overwrite strategic spec) or S1 |
| `Approved` | F2 (tactical plan) |
| `Tactical` | F3 (impl, first non-done step) |
| `In Progress` | F3 (continue, `--resume`) |
| `Implemented` | F5 (audit loop) |
| `Partial` | F5 (fix loop then audit) |
| `Broken` | F5 (fix loop then audit) |
| `Verified` | Print final report - already done |
| `BlockNeedUserTest` | Add to manual items; delete this spec's `Timber.d("Sxxxx:` tags from `.kt` (status leaving `BlockNeedUserTest` - CLAUDE.md "Debug Verification Tags"); set status `Implemented`; jump to F5 |
| `BlockQuestions` | Read §6 Open items; resolve any answerable from codebase; continue from last active stage; add unresolvable to manual list |
| `BlockByOtherTask` | Read §10; if blocking spec `Verified` -> unblock and continue from last stage; else -> add to manual list, continue non-blocked work |
| `BlockExternal` | Add to manual items; continue non-blocked work from last stage |
| `Archived` | Abort: spec archived; suggest new one. |

### 0c - New spec flow

Derive `short-name`: kebab-case slug, 3-5 words. Glob `PLAN/Sxxxx_*.md` for slug collisions - append `-v2`, `-v3` if needed. Id `Sxxxx` allocated by `/spec` via `insert.ps1`.

**Complexity assessment** - classify **Simple** or **Full**:

| Signal | Weight |
| ------ | ------ |
| Estimated phases > 3 | -> Full |
| Room schema change required | -> Full |
| New Hilt scope or qualifier needed | -> Full |
| Touches > 2 subsystems / feature areas | -> Full |
| Cross-cutting change (multiple layers end-to-end) | -> Full |
| Otherwise | -> Simple |

Log decision in chat: `Complexity: Simple | Full - <one-line reason>.`

---

## Simple Path

### Stage S1 - Compact Spec

Allocate `Sxxxx` via `insert.ps1`. Write one `PLAN/Sxxxx_<short-name>.md` combining strategic goal + phases inline. Use the phase template at `.claude/templates/phase-file.md` directly - read it before writing the phases (English, imperative steps with Verification predicates). Every phase step carries the mandatory `**Why:**` field between `**Prompt for developer:**` and `**Verification:**` (S1343, adopted 2026-08-02): one complete sentence of rationale sourced from this spec's own Goal/problem statement, or `not stated in strategic spec` verbatim - never an invented reason. Include brief **Goal** section (2-4 sentences, Russian) before phases. Auto-derive priority per `/spec` rules.

**Approval gate stub** (§3.3 owner-inputs block, plus the sensitive-scope bullets): `.claude/reference/spec-all.md` section 2 - read while writing the compact spec, before the status flip.

Flip `Status: Draft` -> `Status: Approved` in file and via `update.ps1`.
Dev log: `.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-all" "Compact spec: <Sxxxx>"`

### Stage S2 - Implementation

Same as **Stage F3**. Reference `PLAN/Sxxxx_<short-name>.md` phases directly.

### Stage S3 - Build Gate

Same as **Stage F4**.

### Stage S4 - Audit + Report

Audit loop max 3 iterations (not 5). Otherwise same as **Stage F5**.

---

## Full Path

### Stage F1 - Strategic Spec

Follow `/spec` with `roadmap-id: ad-hoc`. Id allocated by `insert.ps1` inside `/spec`. After writing: flip `Status: Draft` -> `Status: Approved` (file + journal). Add:

```markdown
<!-- auto-approved by /spec-all - <YYYY-MM-DD> -->
```

Run dev log for strategic spec file.

### Stage F2 - Tactical Plan

Follow `/spec-tech`. If tactical folder exists, refresh phases without discarding `[x] done` steps. Run dev log for INDEX.md and each phase file.

Research artifacts in `PLAN/Sxxxx_<slug>/research/*.md` are mandatory F2 input - `/spec-tech` steps 2–3 plan from them (coverage inventory + ordering). Never skip on resume.

Refinement passes (`/spec-update`): `.claude/reference/spec-all.md` section 3 - read when §6 still carries Open research items.

### Stage F3 - Implementation

Follow `/spec-dev` executing all phases from first non-done step.

**BUILD-REQUIRED stop override:** `.claude/reference/spec-all.md` section 4 - read before this run's first build; it fixes the `/build` target, the retry budget against MAX_BUILD_RETRIES and the `vr debug` follow-up.

**MANUAL-REQUIRED stop:** tick as `[manual - deferred to human]`, continue `--resume`; `.claude/reference/spec-all.md` section 5 - read when a step's gate is on-device verification, it ends in `Timber.d("Sxxxx: …")` tags + `BlockNeedUserTest` + the Device-test gate (`.claude/reference/spec-all.md` section 11).

**Hard stop - attempt inline resolution:** `.claude/reference/spec-all.md` section 6 - read the first time a step fails, before deferring it. Baseline when in doubt: one inline attempt, then `[DEFERRED]` + manual list + next step; never stop the pipeline for one blocked step.

**Spec self-correction:** spec wrong -> patch tactical/strategic directly regardless of `Status:` lock. Status locks do not apply inside `/spec-all`.

**Out-of-scope dependency:** `.claude/reference/spec-all.md` section 7 - read when a step needs work this spec does not cover; minor goes inline, significant gets its own `Sxxxx` and this pipeline continues.

**Override does NOT apply to:** read-only zones. Per CLAUDE.md Rule 4 (read-only zones) - obey it as written; no `/spec-all` override reaches it.

### Stage F4 - Build Gate

Build only from files **this pipeline run actually edited**, never from `git diff`; docs-only -> skip. Edited-file accounting, the "F3 already built post-tags" skip and the `vr debug` case: `.claude/reference/spec-all.md` section 8 - read on entering F4. Persistent FAIL -> hard-stop.

### Stage F5 - Audit Loop (max 5 iterations)

Follow `/spec-check` (full mode). `Verified` -> final report.

Each iteration:

1. `/spec-fix <Sxxxx>`.
2. Implement "Action items" directly. If requires design decision not derivable from codebase -> mark `[FOLLOW-UP]`, skip.
3. Code modified -> `/build` -> `standard debug` (+ `vr debug` if `src/vr/` touched).
4. `/spec-check <Sxxxx>`. `Verified` -> final report.

MAX_FIX_ITERATIONS exhausted -> final report as Incomplete.

---

## Final Report

- Report format + its `add_to_dev_log.ps1` line: `.claude/reference/spec-all.md` section 9 - read when composing the final report.
- `close-and-log.ps1` finalization shortcut and its `-DevLogs` / `-FuncOp` flag contract: `.claude/reference/spec-all.md` section 10 - read when this orchestrator itself closes a ticket (`Verified` / final `BlockNeedUserTest` / `BlockExternal`) with code touched; a sub-skill that ran last already did it.
- Device-test gate: `.claude/reference/spec-all.md` section 11 - read the moment this pipeline sets a ticket to `BlockNeedUserTest`, before parking the block.

Before every final report, release the top-level lease in a `finally`-equivalent step. Run `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Release -Id <Sxxxx>` for success, a hard stop, or a deferred manual item. A failed release is reported but does not replace the pipeline verdict because the lease expires by liveness.

---

## Hard-Stop Conditions

The **only** reasons to stop before final report. Everything else resolved inline or deferred to manual items.

| Trigger | Action |
| ------- | ------ |
| Build fails after MAX_BUILD_RETRIES | Final report - Blocked |
| Room schema change required AND version/migration class NOT named in step | Stop - irreversible, requires human |
| Room schema change AND version/migration class explicit in step | Apply, note in chat, continue |
| Hilt - new scope/qualifier AND scope NOT named in step | Stop - requires human |
| Hilt - scope explicit in step or only `@Inject constructor` wiring | Apply, note in chat, continue |
| Read-only zone reference | Stop - hard boundary, no exceptions |
| MAX_FIX_ITERATIONS exhausted | Final report - Incomplete |
| Stage F3 unresolvable after 2 inline attempts | Add to deferred list, continue remaining steps |
| Device/hardware verification required | Defer to manual items; `Timber.d("Sxxxx: …")` tags inserted before final phase's build (no extra build), set status `BlockNeedUserTest`, apply **Device-test gate** (auto-run `/spec-test-device` + `/spec-check` if device online; silent no-op otherwise), continue pipeline |
| External dependency missing | Add to deferred list, set status `BlockExternal`, final report - Blocked |
| `Archived` status | Abort - spec archived, create new one |
| `$ARGUMENTS` blank | Abort - no input |

**Defer-first rule:** if a stop condition blocks current step but other steps (this phase or later) are independent - skip blocked step, add to manual list, continue from next unblocked step. Final report stop only if no forward progress possible at all.

---

## Constraints

- **No user prompts between stages.** Resolve ambiguity from code/docs context. Unresolvable -> defer to manual items, keep moving.
- **Resume-first.** Existing spec id/slug -> always resume from current state; never recreate done stages.
- **Defer-first.** Blocked steps don't stop pipeline. Skip, continue; collect all blocked items in manual list.
- **Specs are mutable inside `/spec-all`** - patch and continue. Status locks (`Implemented`, `Verified`) do not apply here.
- **Build mandatory on code changes** - skip only for docs-only diffs.
- **Detekt-clean-first** - per CLAUDE.md Rule 19 (neuroslop avoidance, detekt-clean-first) - obey it as written. On the always-dirty tree, close through `post-change.ps1 -ScopeToFile` per CLAUDE.md section 12 "Validation & Post-Change".
- **All sub-skill constraints in force** (line budgets, Timber, trilingual, naming).
- **Debug verification tags follow `BlockNeedUserTest`** - insert `Timber.d("Sxxxx: …")` at changed flow entries as final code edits before last phase's build (one build validates code + tags), only when this pipeline sets status `BlockNeedUserTest`; delete every `Timber.d("Sxxxx:` line for the spec whenever pipeline moves it out of that status (resume -> `Implemented`, audit -> `Verified`/`Partial`/`Broken`). Reserve `Sxxxx:` prefix for these temporary probes only; never in persistent `Timber.i/w/e` or long-lived `Timber.d`. See CLAUDE.md "Debug Verification Tags".
- **Feature inventory owned by sub-skills** - `/spec-all` does NOT write `docs/ALL_FEATURES.jsonl` directly. Record comes from `/spec-dev` on `Implemented` (ADD/CHANGE via `close-and-log.ps1 -FuncOp`), `/spec-check` on `Verified` flip (fallback when `/spec-dev` bypassed), `/spec-fix` on user-visible fixes (FIX). At start of final report, grep `docs/ALL_FEATURES.jsonl` for `<Sxxxx>` in `spec` field: if spec delivered user-visible capability and inventory has zero records for this id, surface `[ALL_FEATURES MISSED] add via scripts/all_features/add.ps1` under "Manual / unresolved" - never paper over by writing record blindly.
- **MANUAL items are not failures** - `Verified` with deferred manual checks is success.
- Never edit `dev/CHANGELOG.md` directly - always via `.\scripts\add_to_dev_log.ps1`.
- **Route mechanical closure through facade.** Per CLAUDE.md section 12 "Validation & Post-Change" - obey it as written; ticket closure goes through `close-and-log.ps1`. The `/spec-all` addition: this holds even when impl runs inline (Simple path) rather than via a literal `/spec-dev` invocation.
- Per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
- Never create audit / fix files in `PLAN/`. All audit findings live in spec's `## Last Audit` block.
- **Progress output:** after each stage completes, print one-line status: `[Stage X done] -> next: Stage Y`. Live progress trace without interaction.

---

## Spec Catalog hooks

Argument-resolution shortcut for a resolved `Sxxxx`, which sub-skill owns each stage transition, the `Ticket: Sxxxx` report line and the forbidden catalog writes: `.claude/reference/spec-all.md` section 12 - read before this orchestrator touches catalog state itself instead of letting a sub-skill do it.
