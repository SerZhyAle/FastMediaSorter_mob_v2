---
mode: agent
description: "Use when: asked to run the full spec pipeline from a raw idea to verified implementation, unattended and automated. Triggers on: spec-all, full spec pipeline, spec from idea, automate spec."
---

# Full Spec Pipeline Orchestrator

Execute the complete spec pipeline from idea to verified implementation, fully automated.
Forward bias over correctness theatre - patch the spec and continue. Stop only when forward progress is genuinely impossible without a human.

## Usage

```text
/spec-all <idea text>
/spec-all <path/to/idea_file.md>
```

`$ARGUMENTS` is treated as a file path when it resolves to an existing file; otherwise used as idea text verbatim.

---

## Paths

```text
Simple  ->  Stage 0 -> S1(compact spec) -> S2(impl) -> S3(build) -> S4(audit+report)
Full    ->  Stage 0 -> F1(strategic)    -> F2(tactical) -> F3(impl) -> F4(build) -> F5(audit+report)
```

MAX_FIX_ITERATIONS = 5. MAX_BUILD_RETRIES = 3.

---

## Stage 0 - Bootstrap + Complexity

Parse `$ARGUMENTS`. If blank -> abort: "No idea provided."

Derive `short-name`: kebab-case slug, 3-5 words. Glob `PLAN/Sxxxx_*.md` for slug collisions - append `-v2`, `-v3` if needed. The id `Sxxxx` is allocated by `/spec` via `insert.ps1`.

**Existing-spec guard:**

- `Status: Approved` or later -> abort: "Spec exists (Status: X). Use individual skills to continue."
- `Status: Draft` -> skip spec-writing stage, use existing draft.
- `Status: Block*` -> abort: "Spec is blocked. Resolve via the appropriate channel before re-running."

**Complexity assessment** - classify as **Simple** or **Full**:

| Signal | Weight |
| ------ | ------ |
| Estimated phases > 3 | -> Full |
| Room schema change required | -> Full |
| New Hilt scope or qualifier needed | -> Full |
| Touches > 2 subsystems / feature areas | -> Full |
| Cross-cutting change (multiple layers end-to-end) | -> Full |
| Otherwise | -> Simple |

Log complexity decision in chat: `Complexity: Simple | Full - <one-line reason>.`

---

## Simple Path

### Stage S1 - Compact Spec

Allocate `Sxxxx` via `insert.ps1`. Write a single `PLAN/Sxxxx_<short-name>.md` that combines strategic goal and phases inline.
Use the `spec_tech` phase template directly (English, imperative steps with Verification predicates).
Include a brief **Goal** section (2-4 sentences, Russian) before the phases. Auto-derive priority per `/spec` rules.

Flip `Status: Draft` -> `Status: Approved` in the file and via `update.ps1`.
Run dev log: `.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-all" "Compact spec: <Sxxxx>"`

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
After writing: flip `Status: Draft` -> `Status: Approved` (file + journal). Add:

```markdown
<!-- auto-approved by /spec-all - <YYYY-MM-DD> -->
```

Run dev log for the strategic spec file.

### Stage F2 - Tactical Plan

Follow `/spec-tech` process. If tactical folder exists, refresh phases without discarding `[x] done` steps.

Run dev log for INDEX.md and each phase file.

> **Refinement passes** (`/spec-update`) are skipped unless §6 contains Open research items that cannot be resolved from the codebase. If they can be resolved inline - resolve and patch the spec, continue.

### Stage F3 - Implementation

Follow `/spec-dev` process executing all phases from first non-done step.

**BUILD-REQUIRED stop override:**

1. Invoke `/build` -> `standard debug`.
2. PASS -> tick criterion `[x] (auto-build - PASS)`, continue `--resume`.
3. FAIL -> fix minimal error. Retry up to MAX_BUILD_RETRIES.
4. Still failing -> hard-stop -> jump to final report as Blocked.
5. If any `src/vr/` file modified: also run `vr debug` after standard passes.

**MANUAL-REQUIRED stop:** tick as `[manual - deferred to human]`. Continue `--resume`. If the manual gate is on-device verification, set status `BlockNeedUserTest` at end of pipeline.

**Hard stop - attempt inline resolution:**

- Missing symbol/wrong path -> Grep/Glob actual location; patch spec; resume.
- Verification fail -> re-read file, correct edit, re-run predicates.
- Trilingual gap -> add `<!-- TODO translate: <EN text> -->` in missing locale; continue.
- Line budget warning (>500 LOC) -> timestamped backup in `temp/`; continue.
- Unresolved after 2 attempts -> hard-stop, jump to final report.

**Spec self-correction:** spec wrong -> patch tactical/strategic directly regardless of `Status:` lock. Status locks do not apply inside `/spec-all`.

**Out-of-scope dependency:**

- Minor (no new classes, no schema change, <= ~30 min of work) -> implement inline.
- Significant -> allocate a new `Sxxxx` via `insert.ps1`, write `PLAN/Sxxxx_<dependency-slug>.md` (`Status: Approved`, `<!-- discovered by /spec-all - <date> -->`). If the dependency itself is **Full**-complexity, create full tactical folder too. Continue current pipeline. Set the parent's status to `BlockByOtherTask` only if the dependency must finish before continuing - otherwise just record it under §10.

**Override does NOT apply to:** read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`).

### Stage F4 - Build Gate

Run `git diff --name-only HEAD`. Exclude `PLAN/`, `docs/`, `dev/CHANGELOG.md`, `*.md`.

- Code files present -> `/build` -> `standard debug`. Persistent FAIL -> hard-stop.
- `src/vr/` in diff -> also `/build` -> `vr debug`.
- Docs-only diff -> skip.

### Stage F5 - Audit Loop (max 5 iterations)

Follow `/spec-check` (full mode). If `Verified` -> final report.

Each iteration:

1. `/spec-fix <Sxxxx>`.
2. Implement "Action items" directly. If requires design decision not derivable from codebase -> mark `[FOLLOW-UP]`, skip.
3. If code modified -> `/build` -> `standard debug` (+ `vr debug` if `src/vr/` touched).
4. `/spec-check <Sxxxx>`. If `Verified` -> final report.

MAX_FIX_ITERATIONS exhausted -> final report as Incomplete.

---

## Final Report

```text
spec-all: <Sxxxx> <short-name> - <Verified | Partial | Blocked | Incomplete>
Spec:   PLAN/Sxxxx_<short-name>.md  [Simple]
  - or -
Spec:   PLAN/Sxxxx_<short-name>/INDEX.md  [Full]
Audit:  inline in spec - `## Last Audit` section.

Manual / unresolved:
- <item>   (empty -> "All closed automatically.")
```

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-all" "Pipeline <status>: <Sxxxx>"
```

---

## Hard-Stop Conditions

| Trigger | Action |
| ------- | ------ |
| Build fails after MAX_BUILD_RETRIES | Final report - Blocked |
| Room schema change required by spec | Stop - irreversible, requires human |
| Room schema change avoidable | Patch spec, skip migration, continue |
| Hilt - new scope/qualifier needed | Stop - requires human |
| Hilt - only `@Inject constructor` wiring | Apply, continue |
| Read-only zone reference | Stop - hard boundary |
| MAX_FIX_ITERATIONS exhausted | Final report - Incomplete |
| Stage F3 unresolvable after 2 attempts | Final report - Blocked |
| Device/hardware verification required | Defer to manual items, set status `BlockNeedUserTest`, continue |
| External dependency missing | Set status `BlockExternal`, final report - Blocked |

---

## Constraints

- No user prompts between stages. Resolve ambiguity from code/docs context.
- Specs are mutable inside `/spec-all` - patch and continue.
- Build mandatory on code changes - skip only for docs-only diffs.
- All sub-skill constraints in force (line budgets, Timber, trilingual, naming).
- MANUAL items are not failures - `Verified` with deferred manual checks is success.
- Never edit `dev/CHANGELOG.md` directly - always via `.\scripts\add_to_dev_log.ps1`.
- Read-only zones never touched.
- Never create audit / fix files in `PLAN/`. All audit findings live inside the spec's `## Last Audit` block.

---

## Spec Catalog hooks

- **Argument resolution.** Accept `Sxxxx`, a slug, or a path (`PLAN/Sxxxx_<slug>.md`). For `Sxxxx`, resolve via `pwsh -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` and skip Stage 0 short-name derivation.
- **Stage transitions** (orchestrator does not duplicate sub-skill updates - these fire from the underlying skills as documented in their own "Spec Catalog hooks" sections):
  - F1 (Strategic Spec): `/spec` performs `insert.ps1` (Status `Draft`); `/spec-all` then auto-flips `Draft -> Approved` via `update.ps1 -Status Approved`.
  - F2 (Tactical): `/spec-tech` flips to `Tactical`.
  - F3 (Implementation): `/spec-dev` flips to `In Progress` then `Implemented`.
  - F5 (Audit): `/spec-check` flips to `Verified` / `Partial` / `Broken`.
- **Final report.** Always include `Ticket: Sxxxx` on the first line, alongside the spec slug.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly. Never produce a path with a `_spec_` segment. Do not bypass an underlying skill's catalog update.
