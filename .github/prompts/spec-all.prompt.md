---
mode: agent
description: "Use when: asked to run the full spec pipeline from a raw idea to verified implementation, unattended and automated. Triggers on: spec-all, full spec pipeline, spec from idea, automate spec."
---

# Full Spec Pipeline Orchestrator

Execute the complete spec pipeline from idea to verified implementation, fully automated.
Forward bias over correctness theatre - patch the spec and continue. Stop only when forward progress is genuinely impossible without a human. Ready to pick up a spec at any stage, any status. Defers unresolvable human questions to the final report - never blocks mid-pipeline on something that can be skipped and revisited.

Strategic approval is the explicit exception to full automation: newly written strategic or compact specs must not be auto-promoted from `Draft` without the owner-input gate defined by `/spec` (`## 0. Approval Gate (owner input)`).

## Usage

```text
/spec-all <idea text>           # new spec from idea
/spec-all <path/to/idea.md>     # new spec from file
/spec-all <Sxxxx>               # resume existing spec by ticket id
/spec-all <slug>                # resume existing spec by slug
```

`$ARGUMENTS` is treated as:
1. Existing ticket id `S\d{4}` → resolve spec and **resume**.
2. Path to an existing file → read idea from file, then determine if spec already exists by slug collision.
3. Slug that matches an existing `PLAN/Sxxxx_<slug>.md` → **resume**.
4. Otherwise → new spec from idea text.

---

## Paths

```text
Simple  →  Stage 0 → S1(compact spec) → S2(impl) → S3(build) → S4(audit+report)
Full    →  Stage 0 → F1(strategic)    → F2(tactical) → F3(impl) → F4(build) → F5(audit+report)
```

MAX_FIX_ITERATIONS = 5. MAX_BUILD_RETRIES = 3.

---

## Stage 0 - Bootstrap + State Detection

Parse `$ARGUMENTS`. If blank → abort: "No idea provided."

### 0a - Resolve existing spec (resume mode)

If argument looks like a ticket id or slug, resolve via:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
# or
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json
```

If resolved → read strategic spec file, read current `Status:` → **jump to the appropriate resume stage** per the Resume Map below. Do NOT re-create or re-validate what is already done.

### 0b - Resume Map (existing spec)

| Current `Status:` | Resume stage |
| --- | --- |
| `Draft` | Validate `## 0. Approval Gate (owner input)`. If complete, promote to `Approved` and continue to the next post-draft stage (`F2` for strategic, `S2` for compact). If incomplete, stop and report missing owner inputs. |
| `Approved` | F2 (tactical plan) |
| `Tactical` | F3 (implementation, first non-done step) |
| `In Progress` | F3 (continue, `--resume`) |
| `Implemented` | F5 (audit loop) |
| `Partial` | F5 (fix loop then audit) |
| `Broken` | F5 (fix loop then audit) |
| `Verified` | Print final report - already done |
| `BlockNeedUserTest` | Add to manual items; delete this spec's `Timber.d("Sxxxx:` tags from `.kt` (status is leaving `BlockNeedUserTest` - CLAUDE.md "Debug Verification Tags"); set status back to `Implemented`; jump to F5 |
| `BlockQuestions` | Read §6 Open items; resolve any answerable from codebase; continue from last active stage; add unresolvable to manual list |
| `BlockByOtherTask` | Read §10; check if blocking spec is `Verified`; if yes → unblock and continue from last stage; if no → add to manual list and continue non-blocked work |
| `BlockExternal` | Add to manual items; continue non-blocked work from last stage |
| `Archived` | Abort: spec is archived; suggest creating a new one. |

### 0c - New spec flow

Derive `short-name`: kebab-case slug, 3–5 words. Glob `PLAN/Sxxxx_*.md` for slug collisions - append `-v2`, `-v3` if needed. The id `Sxxxx` is allocated by `/spec` via `insert.ps1`.

**Complexity assessment** - classify as **Simple** or **Full**:

| Signal | Weight |
| ------ | ------ |
| Estimated phases > 3 | → Full |
| Room schema change required | → Full |
| New Hilt scope or qualifier needed | → Full |
| Touches > 2 subsystems / feature areas | → Full |
| Cross-cutting change (multiple layers end-to-end) | → Full |
| Otherwise | → Simple |

Log complexity decision in chat: `Complexity: Simple | Full - <one-line reason>.`

---

## Simple Path

### Stage S1 - Compact Spec

Allocate `Sxxxx` via `insert.ps1`. Write a single `PLAN/Sxxxx_<short-name>.md` that combines strategic goal and phases inline.
Use the `spec_tech` phase template directly (English, imperative steps with Verification predicates).
Include a brief **Goal** section (2–4 sentences, Russian) before the phases. Auto-derive priority per `/spec` rules.

Add the same owner-input gate used by `/spec` (`## 0. Approval Gate (owner input)`) and fill it only from the human request. Unknown items stay `MISSING - requires owner input`.

Keep `Status: Draft` for a newly created compact spec. Do not auto-promote it in the same run.
Run dev log: `.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-all" "Compact spec: <Sxxxx>"`

If this was a new idea flow, stop here and report: `Draft created. Waiting for owner approval gate / explicit resume.`

### Stage S2 - Implementation

Same as **Stage F3** below. Reference `PLAN/Sxxxx_<short-name>.md` phases directly.

### Stage S3 - Build Gate

Same as **Stage F4** below.

### Stage S4 - Audit + Report

Audit loop max 3 iterations (not 5). Otherwise same as **Stage F5** below.

---

## Full Path

### Stage F1 - Strategic Spec

Follow `/spec` process with `roadmap-id: ad-hoc`. The id is allocated by `insert.ps1` inside `/spec`.
Do **not** auto-promote the newly written strategic draft. Run dev log for the strategic spec file and stop if this run created a new draft.

Resume beyond F1 only when the spec already has a complete `## 0. Approval Gate (owner input)` and the current `/spec-all <Sxxxx|slug>` invocation is the explicit human proceed signal.

### Stage F2 - Tactical Plan

Follow `/spec-tech` process. If tactical folder exists, refresh phases without discarding `[x] done` steps.

Run dev log for INDEX.md and each phase file.

> **Refinement passes** (`/spec-update`) are skipped unless §6 contains Open research items that cannot be resolved from the codebase. If they can be resolved inline - resolve and patch the spec, continue.

### Stage F3 - Implementation

Follow `/spec-dev` process executing all phases from first non-done step.

**BUILD-REQUIRED stop override:**

1. Invoke `/build` → `standard debug`.
2. PASS → tick criterion `[x] (auto-build - PASS)`, continue `--resume`.
3. FAIL → fix minimal error. Retry up to MAX_BUILD_RETRIES.
4. Still failing → hard-stop → jump to final report as Blocked.
5. If any `src/vr/` file modified: also run `vr debug` after standard passes.

**MANUAL-REQUIRED stop:** tick as `[manual - deferred to human]`. Continue `--resume`. If the manual gate is on-device verification, at end of pipeline insert `Timber.d("Sxxxx: <entry-point description>")` at each changed flow entry (CLAUDE.md "Debug Verification Tags"), then set status `BlockNeedUserTest`.

**Hard stop - attempt inline resolution:**

- Missing symbol/wrong path → Grep/Glob actual location; patch spec; resume.
- Verification fail → re-read file, correct edit, re-run predicates.
- Trilingual gap → add `<!-- TODO translate: <EN text> -->` in missing locale; continue.
- Line budget warning (>500 LOC) → timestamped backup in `temp/`; continue.
- Ambiguous step (placeholder, missing name) → attempt to resolve from codebase; if resolved, patch step and continue; if still ambiguous after 1 attempt → mark step `[DEFERRED - ambiguous]`, add to manual list, skip to next step. Never stop the pipeline for one ambiguous step when others are unblocked.
- Unresolvable after 2 attempts → mark `[DEFERRED]`, add to manual list, continue with remaining steps.

**Spec self-correction:** spec wrong → patch tactical/strategic directly regardless of `Status:` lock. Status locks do not apply inside `/spec-all`.

**Out-of-scope dependency:**

- Minor (no new classes, no schema change, ≤ ~30 min of work) → implement inline.
- Significant → allocate a new `Sxxxx` via `insert.ps1`, write `PLAN/Sxxxx_<dependency-slug>.md` in `Status: Draft` with the owner approval gate. Record it under §10. Do not auto-approve it and do not create a tactical folder for it in the same run. Set the parent's status to `BlockByOtherTask` only if the dependency must finish before continuing - otherwise just record it under §10.

**Override does NOT apply to:** read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`).

### Stage F4 - Build Gate

Run `git diff --name-only HEAD`. Exclude `PLAN/`, `docs/`, `dev/CHANGELOG.md`, `*.md`.

- Code files present → `/build` → `standard debug`. Persistent FAIL → hard-stop.
- `src/vr/` in diff → also `/build` → `vr debug`.
- Docs-only diff → skip.

### Stage F5 - Audit Loop (max 5 iterations)

Follow `/spec-check` (full mode). If `Verified` → final report.

Each iteration:

1. `/spec-fix <Sxxxx>`.
2. Implement "Action items" directly. If requires design decision not derivable from codebase → mark `[FOLLOW-UP]`, skip.
3. If code modified → `/build` → `standard debug` (+ `vr debug` if `src/vr/` touched).
4. `/spec-check <Sxxxx>`. If `Verified` → final report.

MAX_FIX_ITERATIONS exhausted → final report as Incomplete.

---

## Final Report

```text
spec-all: <Sxxxx> <short-name> - <Verified ✅ | Partial ⚠️ | Blocked 🛑 | Incomplete ⏱️>
Spec:   PLAN/Sxxxx_<short-name>.md  [Simple]
  - or -
Spec:   PLAN/Sxxxx_<short-name>/INDEX.md  [Full]
Audit:  inline in spec - `## Last Audit` section.

Manual / unresolved:
- <item>   (empty → "All closed automatically.")
```

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-all" "Pipeline <status>: <Sxxxx>"
```

---

## Hard-Stop Conditions

These are the **only** reasons to stop before the final report. Everything else is resolved inline or deferred to the manual items list.

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
| Device/hardware verification required | Defer to manual items, insert `Timber.d("Sxxxx: …")` debug tags at changed flow entries, set status `BlockNeedUserTest`, continue pipeline |
| External dependency missing | Add to deferred list, set status `BlockExternal`, final report - Blocked |
| `Archived` status | Abort - spec is archived, create new one |
| `$ARGUMENTS` blank | Abort - no input |

**Defer-first rule:** if a stop condition would block the current step but other steps in the phase (or later phases) are independent - skip the blocked step, add it to the manual list, and continue from the next unblocked step. Only issue a final report stop if no forward progress is possible at all.

---

## Constraints

- **No user prompts between stages.** Resolve ambiguity from code/docs context. If unresolvable, defer to manual items and keep moving.
- **Resume-first.** When given an existing spec id or slug, always resume from current state - never recreate stages that are already done.
- **Defer-first.** Blocked steps don't stop the pipeline. Skip and continue; collect all blocked items in the manual list for the final report.
- **Specs are mutable inside `/spec-all`** - patch and continue. Status locks (`Implemented`, `Verified`) do not apply inside this skill.
- **Build mandatory on code changes** - skip only for docs-only diffs.
- **All sub-skill constraints in force** (line budgets, Timber, trilingual, naming).
- **Debug verification tags follow `BlockNeedUserTest`** - insert `Timber.d("Sxxxx: …")` at changed flow entries only when this pipeline sets the status to `BlockNeedUserTest`; delete every `Timber.d("Sxxxx:` line for the spec whenever the pipeline moves it out of that status (e.g. resume → `Implemented`, audit → `Verified`/`Partial`/`Broken`). Reserve the `Sxxxx:` prefix for these temporary probes only; do not put it into persistent `Timber.i/w/e` or long-lived `Timber.d` messages. See CLAUDE.md "Debug Verification Tags".
- **MANUAL items are not failures** - `Verified` with deferred manual checks is success.
- Never edit `dev/CHANGELOG.md` directly - always via `.\scripts\add_to_dev_log.ps1`.
- Read-only zones never touched: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Never create audit / fix files in `PLAN/`. All audit findings live inside the spec's `## Last Audit` block.
- **Progress output:** After each stage completes, print a one-line status: `[Stage X done] → next: Stage Y`. This gives the user a live progress trace without requiring interaction.

---

## Spec Catalog hooks

- **Argument resolution.** Accept `Sxxxx`, a slug, or a path (`PLAN/Sxxxx_<slug>.md`). For `Sxxxx`, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` and skip Stage 0 short-name derivation.
- **Stage transitions** (orchestrator does not duplicate sub-skill updates - these fire from the underlying skills as documented in their own "Spec Catalog hooks" sections):
  - F1 (Strategic Spec): `/spec` performs `insert.ps1` (Status `Draft`); `/spec-all` then auto-flips `Draft → Approved` via `update.ps1 -Status Approved`.
  - F2 (Tactical): `/spec-tech` flips to `Tactical`.
  - F3 (Implementation): `/spec-dev` flips to `In Progress` then `Implemented`.
  - F5 (Audit): `/spec-check` flips to `Verified` / `Partial` / `Broken`.
- **Final report.** Always include `Ticket: Sxxxx` on the first line, alongside the spec slug.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly. Never produce a path with a `_spec_` segment. Do not bypass an underlying skill's catalog update.
