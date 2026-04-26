# Full Spec Pipeline Orchestrator

Execute the complete spec pipeline from idea to verified implementation, fully automated.
Forward bias over correctness theatre — patch the spec and continue. Stop only when forward progress is genuinely impossible without a human.

## Usage

```text
/spec-all <idea text>
/spec-all <path/to/idea_file.md>
```

`$ARGUMENTS` is treated as a file path when it resolves to an existing file; otherwise used as idea text verbatim.

---

## Paths

```text
Simple  →  Stage 0 → S1(compact spec) → S2(impl) → S3(build) → S4(audit+report)
Full    →  Stage 0 → F1(strategic)    → F2(tactical) → F3(impl) → F4(build) → F5(audit+report)
```

MAX_FIX_ITERATIONS = 5. MAX_BUILD_RETRIES = 3.

---

## Stage 0 — Bootstrap + Complexity

Parse `$ARGUMENTS`. If blank → abort: "No idea provided."

Derive `short-name`: kebab-case slug, 3–5 words. Glob `PLAN/spec_*.md` for collisions — append `-v2`, `-v3` if needed.

**Existing-spec guard:**

- `Status: Approved` or later → abort: "Spec exists (Status: X). Use individual skills to continue."
- `Status: Draft` → skip spec-writing stage, use existing draft.

**Complexity assessment** — classify as **Simple** or **Full**:

| Signal | Weight |
| ------ | ------ |
| Estimated phases > 3 | → Full |
| Room schema change required | → Full |
| New Hilt scope or qualifier needed | → Full |
| Touches > 2 subsystems / feature areas | → Full |
| Cross-cutting change (multiple layers end-to-end) | → Full |
| Otherwise | → Simple |

Log complexity decision in chat: `Complexity: Simple | Full — <one-line reason>.`

---

## Simple Path

### Stage S1 — Compact Spec

Write a single `PLAN/spec_<short-name>.md` that combines strategic goal and phases inline.
Use the `spec_tech` phase template directly (English, imperative steps with Verification predicates).
Include a brief **Goal** section (2–4 sentences, Russian) before the phases.

Flip `Status: Draft` → `Status: Approved`.
Run dev log: `.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-all" "Compact spec: <short-name>"`

### Stage S2 — Implementation

Same as **Stage F3** below. Reference `PLAN/spec_<short-name>.md` phases directly.

### Stage S3 — Build Gate

Same as **Stage F4** below.

### Stage S4 — Audit + Report

Audit loop max 3 iterations (not 5). Otherwise same as **Stage F5** below.

---

## Full Path

### Stage F1 — Strategic Spec

Follow `/spec` process with `roadmap-id: ad-hoc`.
After writing: flip `Status: Draft` → `Status: Approved`. Add:

```markdown
<!-- auto-approved by /spec-all — <YYYY-MM-DD> -->
```

Run dev log for the strategic spec file.

### Stage F2 — Tactical Plan

Follow `/spec-tech` process. If tactical folder exists, refresh phases without discarding `[x] done` steps.

Run dev log for INDEX.md and each phase file.

> **Refinement passes** (`/spec-update`) are skipped unless §6 contains Open research items that cannot be resolved from the codebase. If they can be resolved inline — resolve and patch the spec, continue.

### Stage F3 — Implementation

Follow `/spec-dev` process executing all phases from first non-done step.

**BUILD-REQUIRED stop override:**

1. Invoke `/build` → `standard debug`.
2. PASS → tick criterion `[x] (auto-build — PASS)`, continue `--resume`.
3. FAIL → fix minimal error. Retry up to MAX_BUILD_RETRIES.
4. Still failing → hard-stop → jump to final report as Blocked.
5. If any `src/vr/` file modified: also run `vr debug` after standard passes.

**MANUAL-REQUIRED stop:** tick as `[manual — deferred to human]`. Continue `--resume`.

**Hard stop — attempt inline resolution:**

- Missing symbol/wrong path → Grep/Glob actual location; patch spec; resume.
- Verification fail → re-read file, correct edit, re-run predicates.
- Trilingual gap → add `<!-- TODO translate: <EN text> -->` in missing locale; continue.
- Line budget warning (>500 LOC) → timestamped backup in `temp/`; continue.
- Unresolved after 2 attempts → hard-stop, jump to final report.

**Spec self-correction:** spec wrong → patch tactical/strategic directly regardless of `Status:` lock. Status locks do not apply inside `/spec-all`.

**Out-of-scope dependency:**

- Minor (no new classes, no schema change, ≤ ~30 min of work) → implement inline.
- Significant → create `PLAN/spec_<dependency-slug>.md` (`Status: Approved`, `<!-- discovered by /spec-all — <date> -->`). If the dependency itself is **Full**-complexity, create full tactical folder too. Continue current pipeline.

**Override does NOT apply to:** read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`).

### Stage F4 — Build Gate

Run `git diff --name-only HEAD`. Exclude `PLAN/`, `docs/`, `dev/CHANGELOG.md`, `*.md`.

- Code files present → `/build` → `standard debug`. Persistent FAIL → hard-stop.
- `src/vr/` in diff → also `/build` → `vr debug`.
- Docs-only diff → skip.

### Stage F5 — Audit Loop (max 5 iterations)

Follow `/spec-check` (full mode). If `Verified` → final report.

Each iteration:

1. `/spec-fix <short-name>`.
2. Implement "Suggested next action" items directly. If requires design decision not derivable from codebase → mark `UNRESOLVABLE`, skip.
3. If code modified → `/build` → `standard debug` (+ `vr debug` if `src/vr/` touched).
4. `/spec-check <short-name>`. If `Verified` → final report.

MAX_FIX_ITERATIONS exhausted → final report as Incomplete.

---

## Final Report

```text
spec-all: <short-name> — <Verified ✅ | Partial ⚠️ | Blocked 🛑 | Incomplete ⏱️>
Spec:   PLAN/spec_<short-name>.md  [Simple]
  — or —
Spec:   PLAN/spec_<short-name>/INDEX.md  [Full]
Audit:  PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md

Manual / unresolved:
- <item>   (empty → "All closed automatically.")
```

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-all" "Pipeline <status>: <short-name>"
```

---

## Hard-Stop Conditions

| Trigger | Action |
| ------- | ------ |
| Build fails after MAX_BUILD_RETRIES | Final report — Blocked |
| Room schema change required by spec | Stop — irreversible, requires human |
| Room schema change avoidable | Patch spec, skip migration, continue |
| Hilt — new scope/qualifier needed | Stop — requires human |
| Hilt — only `@Inject constructor` wiring | Apply, continue |
| Read-only zone reference | Stop — hard boundary |
| MAX_FIX_ITERATIONS exhausted | Final report — Incomplete |
| Stage F3 unresolvable after 2 attempts | Final report — Blocked |
| Device/hardware verification required | Defer to manual items, continue |

---

## Constraints

- No user prompts between stages. Resolve ambiguity from code/docs context.
- Specs are mutable inside `/spec-all` — patch and continue.
- Build mandatory on code changes — skip only for docs-only diffs.
- All sub-skill constraints in force (line budgets, Timber, trilingual, naming).
- MANUAL items are not failures — `Verified` with deferred manual checks is success.
- Never edit `dev/CHANGELOG.md` directly — always via `.\scripts\add_to_dev_log.ps1`.
- Read-only zones never touched.
