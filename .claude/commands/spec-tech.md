# Tactical Specification Writer

Break an **approved strategic spec** (`PLAN/spec_<short-name>.md`, `Status: Approved`) into a sequenced, phase-based tactical plan. The tactical plan lives as a folder of Markdown files in `PLAN/spec_<short-name>/`, with one file per phase plus an `INDEX.md` that tracks overall progress. Each phase lists atomic steps, each step carries a developer-facing prompt and a machine-verifiable check.

This spec is a **developer handoff**. Strategic rationale stays in `/spec`; this file is dry, English, imperative.

## Usage

```text
/spec-tech <short-name>                 # create / refresh all phases
/spec-tech <short-name> --phase <NN>    # (re)generate a single phase file
/spec-tech <short-name> --dry-run       # print the phase plan without writing files
```

Examples:

- `/spec-tech player-keybinding-remapping`
- `/spec-tech background-thumbnail-preload --phase 03`

The strategic spec must exist and be `Status: Approved` (or later) before this command will generate anything. If it is `Draft`, abort and tell the user to approve it first.

---

## Language & Audience

- **Body language:** English. Dry, imperative, technical. No prose rationale — that lives in the strategic spec.
- **Audience:** a developer picking up the work cold with the repo open. Prompts must be self-contained at the phase level (reader does not need to open the strategic spec to execute a step).
- **Style:** code blocks for commands, exact file paths, exact class names, line budgets, grep patterns, PowerShell commands. Short sentences. Imperative verbs.

---

## Directory Layout

```text
PLAN/
  spec_<short-name>.md                 # strategic (Russian) — owned by /spec
  spec_<short-name>/                   # tactical (English) — owned by /spec-tech
    INDEX.md                           # phase list, overall progress, completion gate
    PHASE_01__<phase-slug>.md
    PHASE_02__<phase-slug>.md
    ..
    PHASE_NN__<phase-slug>.md
```

Phase-slug format: kebab-case, ≤ 4 words, describes the chunk of work. Examples: `foundations`, `input-dispatch`, `settings-ui`, `wear-parity`, `db-migration`, `docs-catalog-cleanup`.

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Locate and validate the strategic spec.**

- Read `PLAN/spec_<short-name>.md`. Abort with a clear error if missing.
- Verify `Status:` is `Approved`, `Tactical`, `In Progress`, or `Implemented`. If `Draft` — abort: "Strategic spec is Draft. Run `/spec` review first and move Status to Approved."
- Extract: feature name, tier, strategic goals (§2), constraints (§3.2), pillars/modules (§5.1), research items (§6 — every `Status: Open` item becomes a blocker to flag), ADRs (§9), completion criteria (§11).

**Step 2 — Read project context.**

- `dev/PROJECT_OPERATIONS_INDEX.md` — feature-to-path map for accurate file paths.
- `dev/CATALOG/<module>.md` (or `.jsonl`) — existing classes, roles, DI graph.
- `docs/ARCHITECTURE.md` — layer contracts.
- `app_v2/build.gradle.kts` — current flavors, `BuildConfig` fields, minSdk/targetSdk, Room schema version.
- All source files relevant to the affected area. Use Grep/Glob; every file path referenced in a step must either exist or be explicitly a "New" entry in §Files Touched.

**Step 3 — Design the phase graph.**

Partition the work into **sequential phases**. A phase is a chunk of work that:

- Can be merged to main as a coherent unit (own PR or commit group).
- Has a single build-time invariant that proves the phase is complete (code compiles + a named grep/file check passes).
- Does not leave the codebase half-broken between its first and last step.

Phase ordering rules:

1. **Foundations first.** Data classes, repository interfaces, DI scaffolding, Room schema + migration — before anything that depends on them.
2. **Dependency order within phases.** If step 3 imports a class created in step 2, that order is mandatory. State it in the step's `Depends on` field.
3. **User-visible changes last within their area.** UI changes come after the domain layer they consume is stable.
4. **Docs & catalog last phase.** Final phase is always `PHASE_NN__docs-catalog-cleanup.md`. It updates FEATURES trilingual docs, regenerates `dev/CATALOG/<module>.jsonl`, runs `add_to_dev_log.ps1` for every touched file.
5. **Minimum one phase per pillar from strategic §5.1.** If the strategic spec has 4 pillars, expect at least 4 phases (some small pillars may fuse).

Target 3–8 phases. More than 10 — the feature likely needs to split into multiple specs. Fewer than 3 — probably too small for a tactical folder; inline in `/spec` instead.

**Step 4 — Write `INDEX.md`** in `PLAN/spec_<short-name>/` using the template below.

**Step 5 — Write each `PHASE_NN__<slug>.md`** using the phase template below. Use `Step N.M` numbering (phase number + step index) so steps are uniquely addressable across the whole spec.

**Step 6 — Update the strategic spec's `Status:` to `Tactical`** and add one line under the status header:

```markdown
**Tactical plan:** `PLAN/spec_<short-name>/INDEX.md`
```

**Step 7 — Run the dev log command** for every file written / modified:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>/INDEX.md" "spec-tech" "Create tactical plan for <short-name>"
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>/PHASE_01__<slug>.md" "spec-tech" "Phase 01: <slug>"
# .. one line per phase file ..
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-tech" "Move strategic status to Tactical; link tactical plan"
```

**Step 8 — Summarise to the user** (Russian): count of phases, estimated order, any `Research Open` blockers from strategic §6 that must be resolved before Phase 01 starts, and the path to `INDEX.md`.

---

## `INDEX.md` Template

```markdown
# Tactical Plan: <short-name>

**Strategic spec:** [`../spec_<short-name>.md`](../spec_<short-name>.md)
**Feature:** <feature name from strategic spec header>
**Tier:** <tier label>
**Status:** Not started | In Progress | Done
**Phases:** 0 / N done
**Last updated:** <YYYY-MM-DD>

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_<short-name>.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | <slug — short descriptive name> | — | ⬜ Not started | 0/N | [PHASE_01__<slug>.md](PHASE_01__<slug>.md) |
| 02 | <slug> | 01 | ⬜ Not started | 0/N | [PHASE_02__<slug>.md](PHASE_02__<slug>.md) |
| .. | .. | .. | .. | .. | .. |
| NN | docs-catalog-cleanup | all | ⬜ Not started | 0/N | [PHASE_NN__docs-catalog-cleanup.md](PHASE_NN__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

<Every strategic §6 research item with `Status: Open` becomes a checkbox here. Phase 01 must not start while any blocker is unchecked. If none — state "No open research items from strategic spec.">

- [ ] **Research:** <title> — answer required before Phase <NN>. See strategic §6.X.

---

## Completion Gate

The feature is Done when **every** item below is ticked:

- [ ] All phases show ✅ Done in the Phase Overview.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <short-name>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. **Before starting a phase:** flip its row to `🚧 In Progress` in the Phase Overview. Update `Phases: X/N done` at the top.
2. **During a phase:** inside the phase file, flip each step's `Status:` line to `[~] in progress` when you start it, `[x] done` when its Verification passes. Never flip a step to `[x]` on intent — only on verified signal.
3. **On phase completion:** confirm every step is `[x]`, then confirm every item in the phase's "Phase Done Criteria". Flip the phase row in this INDEX to `✅ Done` and bump the counter.
4. **If blocked:** flip the row to `⛔ Blocked`, add a bullet to "Blockers Log" below with date + cause + next action.
5. **On all phases done:** flip this file's top `Status:` to `Done` and run `/spec-check <short-name>` for the final audit.

---

## Blockers Log

<Appended as issues arise. Empty on first write.>

- <YYYY-MM-DD> — Phase NN blocked: <cause>. Next action: <who/what/when>.

---

## Change Log

<Meaningful changes to the tactical plan itself. Not code changes — those go to `dev/CHANGELOG.md`.>

- <YYYY-MM-DD> — Initial tactical plan authored by `/spec-tech`.
- <YYYY-MM-DD> — Phase 04 added after ADR-2 in strategic spec revised the approach.
```

---

## Phase File Template

Use this exact structure for every `PHASE_NN__<slug>.md`. Fill every field; no placeholders.

```markdown
# Phase NN — <Phase Title>

**Strategic spec:** [`../spec_<short-name>.md`](../spec_<short-name>.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started | 🚧 In Progress | ✅ Done | ⛔ Blocked
**Depends on:** Phase NN-M (or "none — this is the foundation phase")
**Blocks:** Phase NN+K, Phase NN+L (list every downstream dependent)
**Steps done:** 0 / N
**Started:** <YYYY-MM-DD or —>
**Completed:** <YYYY-MM-DD or —>

---

## Objective

<One sentence. What this phase produces. No rationale. Example: "Introduce the `InputBindingRepository` with Room persistence and Hilt wiring; no UI or dispatch changes yet.">

---

## Prerequisites

Check each before starting Step 1:

- [ ] All phases in "Depends on" are `✅ Done`.
- [ ] Strategic spec §6 research items blocking this phase are `Resolved`.
- [ ] Working tree is clean or changes are on a feature branch for this phase.
- [ ] <any phase-specific precondition>

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<File>.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<Existing>.kt` | Modified | ≤ 500 |
| .. | .. | .. |

> Any file projected > 500 lines after the change requires a backup step in §Steps (timestamped copy in `temp/`). Any file > 1000 lines is forbidden — split via Manager pattern before starting.

---

## Steps

### Step NN.1 — <Imperative title>

**Files:** `path/to/File.kt`
**Depends on:** Step NN.0 (or "— start of phase")

**Prompt for developer:**

> <Self-contained imperative. 1–4 sentences. Example: "Create class `InputBindingRepository` at `app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt`. Constructor-inject `InputBindingDao` and `@ApplicationContext Context`. Expose `fun observeBindings(): Flow<List<InputBinding>>` and `suspend fun updateBinding(id: String, trigger: InputTrigger)`. Delegate all DB work to the DAO; no business logic in the repository." Reader must not need to open the strategic spec.>

**Verification:**

- `Glob` — `path/to/File.kt` exists.
- `Grep` — `class <ClassName>` matches exactly once in that file.
- `Grep` — `<ExpectedMethodSignature>` matches in that file.
- <any other grep predicate, file-size check, or value equality>

**Status:** `[ ]` not done

---

### Step NN.2 — <Imperative title>

**Files:** ..

**Prompt for developer:**

> ..

**Verification:**

- ..

**Status:** `[ ]` not done

---

<Repeat for every step. Steps are ordered by dependency — Step NN.M cannot start before Step NN.M-1's Verification passes.>

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step NN.*` above is `[x] done`.
- [ ] Project compiles — run the `/build` skill (do not invoke gradle directly).
- [ ] <phase-level invariant, e.g. "Grep for `TODO(phase-<NN>)` returns zero hits">.
- [ ] <phase-level invariant, e.g. "`AppDatabase.version == N+1` with migration `M_N_to_N+1` present">.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API of any file changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>`.

---

## Handoff Notes to Next Phase

<Assumptions the next phase can rely on, invariants this phase established, known side effects. If this is the final phase — write "Final phase — see INDEX.md Completion Gate.">

---

## Rollback Plan

<If this phase introduces risk that might need to revert independently, describe the rollback: which commits to revert, which config to restore, which DB migration to downgrade. For low-risk phases: "Revert the phase commit(s) — no data migration or user-facing surface changed.">
```

---

## Quality Rules

- **One step = one atomic unit of work.** If a step reads "Create class X AND wire it into DI AND add tests" — split it. A developer must be able to commit after any single `[x] done` step without leaving the build broken.
- **Every step Verification must be machine-checkable.** `/spec-check` consumes these. Bad: "the class works correctly". Good: "`Grep` for `class FooManager` returns one hit at `path/File.kt`".
- **No step may reference a file in read-only zones** (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`).
- **File size discipline:** if the projected size after the change exceeds 500 lines, the step must include a backup action (timestamped copy to `temp/`) before the edit. Over 1000 — refuse; split via Manager pattern instead.
- **Naming convention enforced on every new class:** `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`, `VerbNounWorker`. Rename in the step if the initial name violates — the reader does the wrong thing otherwise.
- **Room schema changes:** the migration step must (a) bump `@Database(version = ..)`, (b) add a `Migration` object, (c) never rename prior migrations. One phase per schema change — do not combine unrelated migrations.
- **Hilt bindings:** every new `@Inject` / `@Provides` must name the `@Module` file where the binding is declared, in the step body.
- **Trilingual strings:** if a step adds a UI string, it MUST reference all three files — `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` — with the same key. One step with three files listed and three Grep verifications; do not split across steps.
- **Timber only:** any step adding logging uses `Timber.d()` / `Timber.e()`. A Verification check `Grep -n "Log\.d\(" <touched file>` returning zero hits is mandatory for any file the step modifies.
- **Final phase always exists** and is named `PHASE_NN__docs-catalog-cleanup.md`. It runs `add_to_dev_log.ps1`, updates FEATURES trilingual docs (if user-facing from strategic §8), regenerates the catalog, and closes the spec.
- **Do not duplicate strategic content.** If a reader needs rationale, they open `../spec_<short-name>.md`. Tactical phases say only *what* to type, not *why*.
- **Progress markers are the single source of truth.** Do not leave a step `[~] in progress` overnight without updating the INDEX `Blockers Log`. `/spec-check` treats stale in-progress markers as WARN.
- **When the strategic spec changes after tactical plan exists:** re-run `/spec-tech <short-name>` with `--dry-run` first to preview the diff, then apply. Log regeneration in the INDEX "Change Log". If a regeneration would drop a step that is `[x] done` or `[~] in progress`, flag in chat and ask the user.
- **Author style still applies** in any free-text (e.g. "Handoff Notes"): `..` not `...`. English otherwise — no mixing.
