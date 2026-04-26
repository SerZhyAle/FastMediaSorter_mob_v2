# Full Spec Pipeline Orchestrator

Execute the complete spec pipeline from a raw idea to a verified implementation, fully automated and unattended. Chains every spec skill in dependency order, handles build stops, and iterates the audit loop until the spec reaches `Verified` — or reports exactly what remains when it cannot.

**Operating principle — forward bias over correctness theatre.** Specs are living documents. If reality contradicts the spec, patch the spec and move on — do not stop to ask for permission. If something out-of-scope is discovered, spawn a side-spec and keep going. Stop only when forward progress is genuinely impossible without a human (device test, physical button press, irreversible data migration with no known safe default).

## Usage

```text
/spec-all <idea text>
/spec-all <path/to/idea_file.md>
```

`$ARGUMENTS` is treated as a file path when the path resolves to an existing file; otherwise the argument is used verbatim as the idea text.

Examples:

- `/spec-all "Add swipe-to-delete gesture in the media grid"`
- `/spec-all PLAN/notes.md`

---

## Constants

| Name | Value |
|------|-------|
| `MAX_FIX_ITERATIONS` | `5` |
| `MAX_BUILD_RETRIES` | `3` |
| `PIPELINE_LOG` | `PLAN/spec-all_<short-name>_<YYYY-MM-DD>.md` |

---

## Pipeline Overview

```
Stage 0 — Bootstrap         (parse input, derive short-name, init log)
Stage 1 — /spec             (create strategic spec → auto-advance to Approved)
Stage 2 — /spec-update      (refine strategic, --apply-all)
Stage 3 — /spec-tech        (create tactical plan)
Stage 4 — /spec-update      (refine tactical, --tactical --apply-all)
Stage 5 — /spec-dev         (implement all phases; handle build stops inline)
Stage 6 — Build gate        (standard-debug mandatory if code changed; vr-debug if src/vr/ touched)
Stage 7 — Audit loop        (/spec-check → /spec-fix → build → /spec-check, up to MAX_FIX_ITERATIONS)
Stage 8 — Final report
```

---

## Process

### Stage 0 — Bootstrap

**Step 0.1 — Parse input.**

If `$ARGUMENTS` is a path to an existing file: read the file; its entire content becomes `idea_text`.
Otherwise: `idea_text = $ARGUMENTS` verbatim.

If `idea_text` is blank after trimming → abort: "No idea text provided. Usage: `/spec-all <idea text or file path>`."

**Step 0.2 — Derive `short-name`.**

From `idea_text`, produce a kebab-case slug of 3–5 words that:

- Names the feature domain clearly (e.g. `media-grid-swipe-delete`, `cloud-folder-sync-progress`).
- Does not duplicate an existing spec: `Glob PLAN/spec_*.md` and compare. If a slug collides, append `-v2`, `-v3`, etc.

**Step 0.3 — Existing-spec guard.**

`Glob PLAN/spec_<short-name>.md`.

- If the file exists and its `Status:` is `Approved` or later → abort:
  "Spec `PLAN/spec_<short-name>.md` already exists (Status: `<X>`). Use the individual skills to continue from the current stage."
- If the file exists with `Status: Draft` → skip Stage 1, proceed from Stage 2 (re-use the draft).
- If the file does not exist → continue.

**Step 0.4 — Initialise the pipeline log.**

Write `PLAN/spec-all_<short-name>_<YYYY-MM-DD>.md`:

```markdown
# spec-all pipeline log: <short-name>

**Started:** <YYYY-MM-DD HH:MM>
**Idea source:** <"inline text" | path>
**Idea (excerpt):** <first 300 chars of idea_text>

---

## Stage log

<!-- append entries below as each stage completes -->
```

All subsequent pipeline log writes are **append-only** — never rewrite earlier entries.

---

### Stage 1 — Strategic Spec

Follow the complete Process from `.claude/commands/spec.md` with:

- Roadmap ID: `ad-hoc`
- Short name: `<short-name>`
- Treat `idea_text` as the user's feature description when populating the template.

After the spec file is written:

**Auto-advance Status to `Approved`:** edit `PLAN/spec_<short-name>.md`, replace `Status: Draft` with `Status: Approved`. Add on the following line:

```markdown
<!-- auto-approved by /spec-all — <YYYY-MM-DD> -->
```

Do NOT recommend `/spec-tech` to the user (Stage 3 handles that automatically).

Append to pipeline log:

```
- Stage 1 DONE <HH:MM> — strategic spec created, Status: Approved.
  File: PLAN/spec_<short-name>.md
```

---

### Stage 2 — Strategic Refinement

Follow the complete Process from `.claude/commands/spec-update.md` with:

- Short name: `<short-name>`
- Flags: `--apply-all`

ACCEPT + REVIEW findings are applied automatically. DISCUSS items are recorded in "Proposed Structural Changes" without blocking the pipeline.

Append to pipeline log:

```
- Stage 2 DONE <HH:MM> — spec-update --apply-all.
  Applied: <N> ACCEPT + <M> REVIEW. Proposed (DISCUSS): <K>.
```

---

### Stage 3 — Tactical Plan

Follow the complete Process from `.claude/commands/spec-tech.md` with:

- Short name: `<short-name>`

If the tactical folder `PLAN/spec_<short-name>/` already exists from a prior interrupted run, call `spec-tech` anyway; it will refresh the phases without discarding steps that are already `[x] done`.

Append to pipeline log:

```
- Stage 3 DONE <HH:MM> — tactical plan created. Phases: <N>.
  Index: PLAN/spec_<short-name>/INDEX.md
```

---

### Stage 4 — Tactical Refinement

Follow the complete Process from `.claude/commands/spec-update.md` with:

- Short name: `<short-name>`
- Flags: `--tactical --apply-all`

This refines `INDEX.md` and every phase file. DISCUSS items are recorded without blocking.

Append to pipeline log:

```
- Stage 4 DONE <HH:MM> — spec-update --tactical --apply-all.
  Applied: <N> total. Proposed (DISCUSS): <K>.
```

---

### Stage 5 — Implementation

Follow the complete Process from `.claude/commands/spec-dev.md` with:

- Short name: `<short-name>`
- No `--phase` or `--step` flags (execute all phases from the first non-done step).

**Override: BUILD-REQUIRED stop**

When `spec-dev` would hard-stop with "BUILD-REQUIRED" on a Phase Done Criterion:

1. Invoke the `/build` skill with `standard debug` target.
2. If the build **PASSES**: tick the criterion in the phase file with `[x] (auto-build by /spec-all — PASS)`, then continue `spec-dev --resume`.
3. If the build **FAILS**:
   a. Read the error output. Identify the offending file(s) and line(s).
   b. Apply the minimal fix (type error, missing import, wrong method signature, etc.).
   c. Re-run the build. Repeat up to `MAX_BUILD_RETRIES` total attempts.
   d. If still failing after `MAX_BUILD_RETRIES`: append `BLOCKED (Stage 5): build fails after <N> retries — <error summary>` to the pipeline log and **hard-stop the entire pipeline** (jump to Stage 8 with status `Blocked`).
4. If any file under `src/vr/` was modified during the phase: also run `vr debug` build after the standard build passes.

**Override: MANUAL-REQUIRED stop**

When `spec-dev` would stop on a criterion that requires visual/runtime check:

- Tick the criterion with `[manual — deferred to human]`.
- Note in the pipeline log: `MANUAL-REQUIRED (Phase <NN>): <criterion text>`.
- Continue `spec-dev --resume`. These are surfaced in Stage 8.

**Override: other hard stops**

For any other stop signal (verification fail, missing symbol, ambiguous prompt, line budget, trilingual gap, etc.):

1. Attempt to resolve inline:
   - **Missing symbol / wrong path:** `Grep` and `Glob` to find the actual location; update the spec step with the corrected path (treat as a DISCUSS-level spec edit via `Edit`); resume.
   - **Verification fail after an edit:** re-read the file, correct the edit, re-run the verification predicates.
   - **Trilingual gap:** add `<!-- TODO translate: <EN text> -->` placeholder in the missing locale file; the Verification predicate for key presence will then pass.
   - **Line budget warning (> 500 LOC):** create a timestamped backup in `temp/` before proceeding, as required by project rules.
2. If resolved: continue `spec-dev --resume`.
3. If still unresolved after 2 attempts: append `BLOCKED (Stage 5): <stop-signal type> — <detail>` to the pipeline log and hard-stop (jump to Stage 8 with status `Blocked`).

#### Override: spec self-correction (bypass status lock)

If a stop occurs because the spec itself is wrong — the class doesn't exist at that path, the architecture described is unworkable, a step asks for something the codebase already handles differently — do **not** hard-stop. Instead:

1. Determine the correct implementation path from the actual codebase (Grep/Glob/Read the relevant files).
2. Patch the spec step directly: edit the tactical phase file and/or strategic spec, regardless of `Status:` lock (`In Progress`, `Implemented`, etc.). The status gates in sub-skills do not apply here — `/spec-all` owns the pipeline and its specs.
3. Record the correction in the pipeline log: `SPEC-PATCHED (Stage 5, Phase <NN>, Step <M>): <one-line description of what changed and why>`.
4. Resume `spec-dev --resume` against the corrected step.

This also applies to over-engineered spec decisions: if a step calls for a Room migration but the data can be handled without one, simplify the step in-place, skip the migration, and continue.

#### Override: out-of-scope dependency discovered

When implementation reveals a significant prerequisite or dependency that is explicitly OUT_OF_SCOPE in the current spec (or simply not mentioned):

- **Minor** (< 1 hour of work, no new classes, no schema change): implement inline, note in pipeline log as `OOS-INLINE (Phase <NN>): <description>`, add a step to the current tactical phase.
- **Significant** (new class, new data layer, non-trivial logic): create a compact strategic spec `PLAN/spec_<dependency-slug>.md` with `Status: Approved` and a note `<!-- discovered by /spec-all run for <short-name> — <date> -->`. Do NOT run the full pipeline for it now — log it as `OOS-SPEC-CREATED: PLAN/spec_<dependency-slug>.md` and continue the current pipeline. The side-spec will be a standalone item for the next `/spec-all` run.

**Do not override** stops for: read-only zone references. These are project hard boundaries, not bureaucracy.

Append to the pipeline log after each completed phase:

```
- Stage 5 Phase <NN> DONE <HH:MM> — <slug>. Steps: <N>/<N>. Build: PASS | SKIP | N/A.
```

---

### Stage 6 — Build Gate

After all phases in Stage 5 complete:

1. Run `git diff --name-only HEAD` (or compare against pre-pipeline state) to list modified files.
2. Exclude `PLAN/`, `docs/`, `dev/CHANGELOG.md`, and `*.md` paths.
3. **If code files are present in the diff:**
   a. Invoke `/build` → `standard debug`. On FAIL: fix and retry up to `MAX_BUILD_RETRIES`. On persistent FAIL: hard-stop.
   b. If any `src/vr/` file is in the diff: also invoke `/build` → `vr debug`. Same retry logic.
4. **If only documentation changed:** skip build. Note "Build skipped — docs-only change" in pipeline log.

Append to pipeline log:

```
- Stage 6 DONE <HH:MM> — build gate.
  standard-debug: PASS | SKIP. vr-debug: PASS | SKIP | N/A.
```

---

### Stage 7 — Audit Loop

**Initial audit:**

Follow the complete Process from `.claude/commands/spec-check.md` with:

- Short name: `<short-name>`
- No additional flags (full mode: strategic + tactical).

Record the outcome: `Verified`, `Partial`, or `Broken`.

If `Verified` → jump to Stage 8.

**Fix loop — repeat up to `MAX_FIX_ITERATIONS` times:**

For each iteration `i = 1 .. MAX_FIX_ITERATIONS`:

**7.A — Auto-fix (`/spec-fix`):**

Follow `.claude/commands/spec-fix.md` with `<short-name>`. Record: auto-applied count, manual follow-up count.

**7.B — Implement manual follow-ups:**

For each item in the fix log's "Manual Follow-ups" section:

- Read the item's "Suggested next action" and "Files" fields.
- Implement the fix directly by editing the source files as described.
- After each fix, run the Verification predicates from the relevant spec step (Grep/Glob) to confirm the fix held.
- If the fix requires a design decision that cannot be derived from the codebase context alone: append `UNRESOLVABLE (Stage 7.i): <item title>` to the pipeline log and skip this item only — do not stop the loop.

**7.C — Build check:**

If any code file was modified in steps 7.A or 7.B:
- Invoke `/build` → `standard debug`. On FAIL: fix + retry up to `MAX_BUILD_RETRIES`. On persistent FAIL: hard-stop.
- If any `src/vr/` file was touched: also invoke `/build` → `vr debug`.

**7.D — Re-audit:**

Follow `.claude/commands/spec-check.md` with `<short-name>`.

- If `Verified` → jump to Stage 8.
- Otherwise: continue loop.

Append to pipeline log after each iteration:

```
- Stage 7 iteration <i> <HH:MM>: spec-fix auto=<X> manual=<Y> unresolvable=<Z>.
  Build: PASS | SKIP. spec-check outcome: <Verified|Partial|Broken>.
```

**If `MAX_FIX_ITERATIONS` exhausted and still not `Verified`:**

Append to pipeline log:

```
- Stage 7 EXHAUSTED after 5 iterations. Remaining issues: <list of FAIL items from last audit>.
```

Proceed to Stage 8 with status `Incomplete`.

---

### Stage 8 — Final Report

Print the final summary to the user (Russian):

```
## spec-all — итог: <short-name>

**Статус пайплайна:** Verified ✅ | Partial ⚠️ | Broken ❌ | Blocked 🛑 | Incomplete ⏱️
**Стратегическая спека:** PLAN/spec_<short-name>.md
**Тактический план:** PLAN/spec_<short-name>/INDEX.md
**Лог пайплайна:** PLAN/spec-all_<short-name>_<YYYY-MM-DD>.md
**Аудит:** PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md

**Прогресс по этапам:**

| Этап | Результат |
|------|-----------|
| 1. Стратегическая спека | ✅ / ⏭️ уже существовала |
| 2. Рефайнинг стратегии | ✅ |
| 3. Тактический план | ✅ |
| 4. Рефайнинг тактики | ✅ |
| 5. Имплементация | ✅ / ⚠️ N деф. ручных проверок |
| 6. Билд | ✅ / ⏭️ пропущен (только доки) |
| 7. Аудит (<N> итер.) | ✅ / ⚠️ / ❌ |

**Требуют внимания человека:**
<bullet per MANUAL-REQUIRED from Stage 5>
<bullet per UNRESOLVABLE from Stage 7>
<bullet per remaining FAIL/WARN if not Verified>
<empty section → "Всё автоматически закрыто." >
```

If the pipeline reached `Verified`, run the dev log command:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec-all_<short-name>_<YYYY-MM-DD>.md" "spec-all" "Full pipeline completed: <short-name> → Verified"
```

For any other final status, run:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec-all_<short-name>_<YYYY-MM-DD>.md" "spec-all" "Pipeline run <status>: <short-name>"
```

---

## Hard-Stop Conditions (abort entire pipeline)

| Trigger | Action |
|---------|--------|
| Build fails after `MAX_BUILD_RETRIES` in Stage 5, 6, or 7 | Jump to Stage 8 with status `Blocked` |
| Room schema change — **spec genuinely requires it** | Hard-stop: irreversible data shape change, requires human confirmation |
| Room schema change — **spec is over-engineered, change avoidable** | Patch the spec step to skip migration; continue |
| Hilt module graph change — **new scope/qualifier needed** | Hard-stop: scope decision requires human confirmation |
| Hilt module graph change — **only wiring a new `@Inject` class** | Apply and continue (single `@Inject constructor` is safe) |
| `spec-dev` references a read-only zone | Hard-stop: "Read-only zone violation — cannot proceed" |
| `MAX_FIX_ITERATIONS` exhausted | Jump to Stage 8 with status `Incomplete` |
| Stage 5 unresolvable stop after 2 attempts | Jump to Stage 8 with status `Blocked` |
| Device test / physical hardware verification required | Defer to Stage 8 MANUAL list; continue pipeline |

---

## Quality Rules

- **Forward bias is the prime directive.** Every stop signal must first be evaluated: "Can I resolve this without a human?" If yes — resolve and continue. Hard-stop only when forward progress is literally impossible without human action (device hardware, irreversible data operation with no safe default, read-only zone).
- **Specs are mutable, not contracts.** A spec that contradicts reality is a wrong spec. Patch it, log the correction, keep going. Status locks (`In Progress`, `Implemented`) do not apply inside `/spec-all` — the orchestrator owns the pipeline.
- **No user prompts between stages.** The only user-visible output during the run is brief progress lines and the Stage 8 report. Do not ask questions — resolve ambiguity from code/docs context.
- **Side-specs for significant out-of-scope work.** When a significant dependency is discovered, create a compact `PLAN/spec_<slug>.md` (strategic only, `Status: Approved`) and continue. Do not inline a large out-of-scope feature into the current spec.
- **Status auto-advance is /spec-all's sole privilege.** The `Draft → Approved` flip in Stage 1 and any mid-pipeline spec patches are the orchestrator's domain. All other normal Status transitions belong to `spec-tech`, `spec-dev`, `spec-check`.
- **Build is mandatory on code changes.** If code was modified and the build was skipped for any reason other than a docs-only diff, record this as `WARN: build skipped despite code changes` in the pipeline log and escalate to Stage 8 as an item requiring attention.
- **All sub-skill quality rules remain in force.** This orchestrator does not relax line budgets, Timber rules, trilingual requirements, or any project-wide strict rule.
- **MANUAL items are not failures.** A `Verified` outcome with deferred manual checks is a success — surface those checks clearly in Stage 8 so the developer can verify them.
- **Pipeline log is append-only.** Never rewrite earlier entries.
- **`dev/CHANGELOG.md` is never edited directly.** Always use `.\scripts\add_to_dev_log.ps1`.
- **Author style throughout:** `..` not `...`; `ё`/`Ё` in all Russian-language output.
- **Read-only zones** (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) are never touched — not even during fix iterations.
