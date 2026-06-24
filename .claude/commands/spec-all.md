# Full Spec Pipeline Orchestrator

Run complete spec pipeline (idea -> verified impl), fully automated. Forward bias over correctness theatre: patch spec and continue. Stop only when human input genuinely required. Picks up a spec at any stage/status. Defer unresolvable human questions to final report; never block mid-pipeline on skippable item.

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

### 0a-drift - Code-vs-spec drift check (resume modes only)

Skip this step entirely when `preflight:` context line present - `/spec-next` already ran drift-check and handed verdict. Otherwise, before delegating to F1/F2 for a `Draft` / `Approved` / `Tactical` / `Broken` spec:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/drift-check.ps1 -Id <Sxxxx>
```

Exit 1 (`DRIFT`) = git commits with spec id marker and/or inline `// Sxxxx:` markers already exist in `app_v2/src/`. Action:

- `## Last Audit` missing -> switch to **review mode**: read strategic file, write a `## Last Audit` block summarising what is already in code (file paths, commit shas, residual gaps), jump to F5 (or set `BlockNeedUserTest` if device-test is the only remaining gate).
- `## Last Audit` present -> proceed with normal Resume Map (audit block already reflects code).

Catches "spec written ahead of code, fix committed later, spec never updated" - otherwise wastes full F2+F3 cycle.

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

Allocate `Sxxxx` via `insert.ps1`. Write one `PLAN/Sxxxx_<short-name>.md` combining strategic goal + phases inline. Use `spec_tech` phase template directly (English, imperative steps with Verification predicates). Include brief **Goal** section (2-4 sentences, Russian) before phases. Auto-derive priority per `/spec` rules.

**Approval gate stub.** Compact specs still hit Draft -> Approved gate. Append minimal §3.3 block before flipping status:

```markdown
### 3.3 Owner inputs (Approval gate)

- **Related tickets:** <none | Sxxxx,Sxxxx>
```

If compact spec touches sensitive scope (UI / flavor / data / API), follow `/spec` Process step 5.1 detection and emit matching bullets in same block - gate uses same `check-owner-inputs.ps1`.

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

> **Refinement passes** (`/spec-update`) skipped unless §6 has Open research items unresolvable from codebase. If resolvable inline - resolve, persist findings to `PLAN/Sxxxx_<slug>/research/<NN>__<topic-slug>.md` with `**Артефакт:**` link in §6 (see `/research` step 5), patch spec, continue.

### Stage F3 - Implementation

Follow `/spec-dev` executing all phases from first non-done step.

**BUILD-REQUIRED stop override:**

1. Invoke `/build` -> `standard debug`. Use `a.ps1 dq` (quiet debug - same `assembleStandardDebug`, suppresses UP-TO-DATE / deprecated-DSL / known-acceptable warnings) for resume-mode iteration builds; use `a.ps1 d` only when investigating build failure needing full Gradle output.
2. PASS -> tick criterion `[x] (auto-build - PASS)`, continue `--resume`.
3. FAIL -> fix minimal error. Retry up to MAX_BUILD_RETRIES.
4. Still failing -> hard-stop -> final report as Blocked.
5. Any `src/vr/` file modified: also run `vr debug` after standard passes.

**MANUAL-REQUIRED stop:** tick as `[manual - deferred to human]`. Continue `--resume`. If manual gate is on-device verification, `/spec-dev` inserts `Timber.d("Sxxxx: <entry-point description>")` tags as final code edits **before the last phase's build** (CLAUDE.md "Debug Verification Tags") - that build validates code + tags in one pass, no extra build after. Then set status `BlockNeedUserTest` and apply **Device-test gate** (see Finalization) - auto-run `/spec-test-device` + `/spec-check` when device online.

**Hard stop - attempt inline resolution:**

- Missing symbol/wrong path -> Grep/Glob actual location; patch spec; resume.
- Verification fail -> re-read file, correct edit, re-run predicates.
- Trilingual gap -> add `<!-- TODO translate: <EN text> -->` in missing locale; continue.
- Line budget warning (>500 LOC) -> timestamped backup in `temp/`; continue.
- Ambiguous step (placeholder, missing name) -> attempt to resolve from codebase; resolved -> patch step, continue; still ambiguous after 1 attempt -> mark `[DEFERRED - ambiguous]`, add to manual list, skip to next step. Never stop pipeline for one ambiguous step when others unblocked.
- Unresolvable after 2 attempts -> mark `[DEFERRED]`, add to manual list, continue with remaining steps.

**Spec self-correction:** spec wrong -> patch tactical/strategic directly regardless of `Status:` lock. Status locks do not apply inside `/spec-all`.

**Out-of-scope dependency:**

- Minor (no new classes, no schema change, <= ~30 min) -> implement inline.
- Significant -> allocate new `Sxxxx` via `insert.ps1`, write `PLAN/Sxxxx_<dependency-slug>.md` (`Status: Approved`, `<!-- discovered by /spec-all - <date> -->`). If dependency is **Full**-complexity, create full tactical folder too. Continue current pipeline. Set parent's status to `BlockByOtherTask` only if dependency must finish first - otherwise just record under §10.

**Override does NOT apply to:** read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`).

### Stage F4 - Build Gate

Consider only files **this pipeline run actually edited** (F3 tracks them - do NOT derive from `git diff`, which mixes in unrelated WIP across many tickets on this repo). Exclude `PLAN/`, `docs/`, `dev/CHANGELOG.md`, `*.md`.

- **Skip when F3 already built post-tags.** If F3's final phase ended with `Project compiles` build that already included inserted Timber tags (the `BlockNeedUserTest` path) and no code changed since, F4 is redundant second build - skip.
- Code files present (and no post-tags build in F3) -> `/build` -> `standard debug`. Persistent FAIL -> hard-stop.
- `src/vr/` among edited files -> also `/build` -> `vr debug`.
- Docs-only changes -> skip.

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

```text
spec-all: <Sxxxx> <short-name> - <Verified ✅ | Partial ⚠️ | Blocked 🛑 | Incomplete ⏱️>
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

**Finalization shortcut.** When pipeline closes a ticket (`Verified` / final `BlockNeedUserTest` / `BlockExternal`) and code was touched, prefer `close-and-log.ps1` - one pwsh process instead of 6-7 launches:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status <Verified|BlockNeedUserTest|...> `
    -DevLogs @(
        '{"file":"PLAN/Sxxxx_*.md","target":"spec-all","desc":"<spec-level msg>"}',
        '{"file":"app_v2/src/.../X.kt","target":"spec-all","desc":"<code msg>"}'
      ) `
    -FuncOp FIX -FuncDesc "<user-visible summary>" `
    -CatalogModule app_v2
```

Sub-skills (`/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`) call this internally. Use directly from `/spec-all` only when orchestrator itself owns the closing step (rare - usually a sub-skill ran last).

**Device-test gate.** Whenever this pipeline sets a ticket to `BlockNeedUserTest` (resume-mode MANUAL-REQUIRED stop, or `Device/hardware verification required` hard-stop row), do not just park the block - probe for attached device and auto-run on-device verification when present. Keeps `/spec-all` unattended: adds device test only when device online, silent no-op otherwise.

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json
```

- **Exit 0 (device online):** tags already inserted, status `BlockNeedUserTest`; auto-chain `/spec-test-device <Sxxxx>` (full evidence) -> `/spec-check <Sxxxx>`. `/spec-check` converts evidence into `Verified` / `Partial` / `Broken` and removes tags on transition out of `BlockNeedUserTest`. Record resulting status in final report instead of `BlockNeedUserTest`.
- **Exit 2/1/3/6 (no usable device):** silent no-op. Leave ticket in `BlockNeedUserTest`, keep tags, add one-line `Manual / unresolved` note: `device-test deferred (no device) - run /spec-sweep when a device is online`.

Non-blocking: failed device-ready probe never stops pipeline. Batch drain for parked tickets is `/spec-sweep`.

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
- **All sub-skill constraints in force** (line budgets, Timber, trilingual, naming).
- **Debug verification tags follow `BlockNeedUserTest`** - insert `Timber.d("Sxxxx: …")` at changed flow entries as final code edits before last phase's build (one build validates code + tags), only when this pipeline sets status `BlockNeedUserTest`; delete every `Timber.d("Sxxxx:` line for the spec whenever pipeline moves it out of that status (resume -> `Implemented`, audit -> `Verified`/`Partial`/`Broken`). Reserve `Sxxxx:` prefix for these temporary probes only; never in persistent `Timber.i/w/e` or long-lived `Timber.d`. See CLAUDE.md "Debug Verification Tags".
- **Feature inventory owned by sub-skills** - `/spec-all` does NOT write `docs/ALL_FEATURES.jsonl` directly. Record comes from `/spec-dev` on `Implemented` (ADD/CHANGE via `close-and-log.ps1 -FuncOp`), `/spec-check` on `Verified` flip (fallback when `/spec-dev` bypassed), `/spec-fix` on user-visible fixes (FIX). At start of final report, grep `docs/ALL_FEATURES.jsonl` for `<Sxxxx>` in `spec` field: if spec delivered user-visible capability and inventory has zero records for this id, surface `[ALL_FEATURES MISSED] add via scripts/all_features/add.ps1` under "Manual / unresolved" - never paper over by writing record blindly.
- **MANUAL items are not failures** - `Verified` with deferred manual checks is success.
- Never edit `dev/CHANGELOG.md` directly - always via `.\scripts\add_to_dev_log.ps1`.
- **Route mechanical closure through facade.** Per-file post-change goes through `scripts/post-change.ps1 -ChangeType <type>`; ticket closure goes through `close-and-log.ps1`. Even when impl runs inline (Simple path) rather than via literal `/spec-dev` invocation, do not hand-roll separate `catalog_sync.ps1` + quality-gate + per-file `add_to_dev_log.ps1` calls - facade already chains them in one process.
- Read-only zones never touched: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Never create audit / fix files in `PLAN/`. All audit findings live in spec's `## Last Audit` block.
- **Progress output:** after each stage completes, print one-line status: `[Stage X done] -> next: Stage Y`. Live progress trace without interaction.

---

## Spec Catalog hooks

- **Argument resolution.** Accept `Sxxxx`, slug, or path (`PLAN/Sxxxx_<slug>.md`). For `Sxxxx`, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` and skip Stage 0 short-name derivation.
- **Stage transitions** (orchestrator does not duplicate sub-skill updates - these fire from underlying skills):
  - F1: `/spec` runs `insert.ps1` (Status `Draft`); `/spec-all` then auto-flips `Draft -> Approved` via `update.ps1 -Status Approved`.
  - F2: `/spec-tech` flips to `Tactical`.
  - F3: `/spec-dev` flips to `In Progress` then `Implemented`.
  - F5: `/spec-check` flips to `Verified` / `Partial` / `Broken`.
- **Final report.** Always include `Ticket: Sxxxx` on first line, alongside spec slug.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly. Never produce path with `_spec_` segment. Do not bypass an underlying skill's catalog update.
