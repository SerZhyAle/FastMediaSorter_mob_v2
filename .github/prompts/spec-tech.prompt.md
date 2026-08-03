---
mode: agent
description: "Use when: breaking an approved strategic spec into a tactical phase plan, asked to run /spec-tech. Triggers on: spec-tech, tactical plan, phases, phase breakdown, spec-tech <Sxxxx>."
---

# Tactical Specification Writer

Break an approved strategic spec into sequenced phases. Requires `Status: Approved` or later, or a `Draft` whose owner approval gate is complete and is being continued by an explicit human `/spec-tech` invocation.
Creates `PLAN/Sxxxx_<short-name>/INDEX.md` + phase files. Language: English, imperative, no rationale prose.

## Usage

```text
/spec-tech <Sxxxx-or-slug>
/spec-tech <Sxxxx-or-slug> --phase <NN>
/spec-tech <Sxxxx-or-slug> --dry-run
```

`Draft` is **not** auto-promoted anymore. The only allowed `Draft` → `Approved` promotion inside this skill is the explicit human-driven proceed signal created by invoking `/spec-tech <Sxxxx>` on a draft whose `## 0. Approval Gate (owner input)` is complete. Block states (`Block*`) and incomplete approval gates cause a hard abort.

The strategic spec must exist at `PLAN/Sxxxx_<short-name>.md`.

---

## Directory layout

```text
PLAN/Sxxxx_<short-name>.md          # strategic (Russian) - owned by /spec
PLAN/Sxxxx_<short-name>/
  INDEX.md
  PHASE_01__<slug>.md
  ..
  PHASE_NN__docs-catalog-cleanup.md
```

No `_spec_` segment in any path. Phase-slug: kebab-case, ≤4 words. Examples: `foundations`, `input-dispatch`, `db-migration`.

---

## Process

**1 - Validate strategic spec.**

Resolve `Sxxxx` and slug via `select.ps1`. Read `PLAN/Sxxxx_<short-name>.md`. Abort if missing or `Status: Block*` (block states require resolution first).

If `Status: Draft`, read `## 0. Approval Gate (owner input)` and validate every mandatory line. If any mandatory item is absent, marked `MISSING`, or filled only with agent inference, abort and list the gate items that still require owner input.

If `Status: Draft` and the gate is complete, the current user-invoked `/spec-tech` call counts as the explicit proceed signal. Promote to `Approved` and continue:

```powershell
(Get-Content "PLAN/${ticketId}_<short-name>.md") -replace '^(\*\*Status:\*\*\s*)Draft', '${1}Approved' |
    Set-Content "PLAN/${ticketId}_<short-name>.md"
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -Status Approved
```

Note in chat: `Status was Draft. Approval gate complete - promoted to Approved on explicit /spec-tech request.`

Extract: feature name, tier, priority, goals (§2), constraints (§3.2), pillars (§5.1), open research items (§6), ADRs (§9), criteria (§11).

**2 - Read project context.**

- `dev/PROJECT_OPERATIONS_INDEX.md`
- `dev/CATALOG/<module>.md` or `.jsonl`
- `docs/ARCHITECTURE.md`
- `app_v2/build.gradle.kts`
- All source files for the affected area. Every file path referenced in a step must exist or be explicitly marked "New".

**2.5 - Evaluate complexity (PRIMITIVE check).**

Score the task against the primitive checklist:

- [ ] ≤ 3 existing files need changes - no new files required
- [ ] No new classes, interfaces, or abstract types introduced
- [ ] No Room schema change (`@Database` version bump or new `@Entity`)
- [ ] No new Hilt `@Module` or `@Provides` required
- [ ] No new UI screens, fragments, or navigation destinations
- [ ] Implementation is mechanically deterministic - no design decisions deferred
- [ ] Estimated line delta < 100 lines total

**If ALL pass → PRIMITIVE path** (skip steps 3–8):

1. Implement the changes directly in the source files identified in step 2.
2. Insert `Timber.d("Sxxxx: <entry-point description>")` at each changed flow entry - per CLAUDE.md "Debug Verification Tags", the ticket is about to enter `BlockNeedUserTest`, so the tags must be present. One tag per flow entry, not per modified line. The `Sxxxx:` prefix is reserved for these temporary probes only; do not reuse it in `Timber.i/w/e` or any message meant to remain after the task.
3. Run post-change mandatory steps: `add_to_dev_log.ps1`, catalog sync via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render), strings audit if applicable.
4. Advance ticket to `BlockNeedUserTest` via `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest`. The step-2 tags stay in code until the ticket leaves this status (removed by `/spec-check` on `Verified`, or by `/spec-update` on re-open).
5. Chat output: `<Sxxxx> - Primitive. No phase files created. Implemented directly. Status: BlockNeedUserTest. Debug tags: N.`

No `INDEX.md`, no `PHASE_NN__*.md` files are written. No `/spec-dev` chain.

**If ANY criterion fails → COMPLEX path:** continue with step 3 below.

---

**3 - Design phase graph.**

Partition into sequential phases, each:

- Mergeable as a coherent unit.
- Has one build-time invariant proving completion.
- Leaves no half-broken state between steps.

Ordering rules:

1. Foundations first: data classes, repo interfaces, DI, Room schema+migration.
2. Dependency order within phases - state in `Depends on`.
3. User-visible changes last within their area.
4. Final phase always `PHASE_NN__docs-catalog-cleanup.md`: FEATURES trilingual, catalog regen, dev log.
5. Minimum one phase per strategic pillar (§5.1). Small pillars may fuse.

Target 3–8 phases. >10 → split the feature into multiple specs.

**4 - Write `INDEX.md`** using the template below.

**5 - Write each `PHASE_NN__<slug>.md`** using the phase template. Steps numbered `NN.M`.

> **Step form (S1343, adopted 2026-08-02).** Every written step carries a `**Why:**` field between `**Prompt for developer:**` and `**Verification:**` - at least one complete sentence, sourced from the strategic spec, stating what breaks without the step or which constraint it satisfies, never a restatement of the prompt. Source it or write `not stated in strategic spec` verbatim; never invent a reason the strategic spec does not state. `Prompt for developer:` itself drops filler words and redundant turns of phrase ("please", "in order to", restating the step title), but is not otherwise shortened, and causal wording is never compressed.

> **Communication policy gate:** For any step that adds or rewrites user-visible strings, include in its `Prompt for developer:` a check against `docs/COMMUNICATION_POLICY.md` §2 (message formula for the relevant type) and §6 (tone checklist). Make the tone checklist a Verification predicate: `Strings pass COMMUNICATION_POLICY §6 checklist`.

**6 - Update strategic spec.** Flip `Status:` to `Tactical`. Add:

```markdown
**Tactical plan:** `PLAN/Sxxxx_<short-name>/INDEX.md`
```

**7 - Run dev log** for every file written.

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>/INDEX.md" "spec-tech" "Create tactical plan for <Sxxxx>"
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>/PHASE_01__<slug>.md" "spec-tech" "Phase 01: <slug>"
# one line per phase file
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-tech" "Status → Tactical"
```

**8 - Auto-chain to `/spec-dev`.** *(COMPLEX path only - skip if PRIMITIVE path was taken in step 2.5.)*

If there are no unchecked Pre-Implementation Blockers in INDEX - immediately invoke `/spec-dev <Sxxxx>` to start implementation. If any blocker is unchecked - list them and stop: implementation cannot proceed until they are resolved.

**Chat output:** `<Sxxxx>: N phases. Blockers: [list or none]. → Running /spec-dev…` (or `→ Blocked: [list]. Resolve and run /spec-dev <Sxxxx>` if blockers present.)

---

## `INDEX.md` Template

```markdown
# Tactical Plan: <Sxxxx> - <short-name>

**Strategic spec:** [`../Sxxxx_<short-name>.md`](../Sxxxx_<short-name>.md)
**Feature:** <feature name>
**Tier:** <tier label>
**Priority:** <0..100>
**Status:** Not started
**Phases:** 0 / N done
**Last updated:** <YYYY-MM-DD>

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | <slug> | - | ⬜ Not started | 0/N | [PHASE_01__<slug>.md](PHASE_01__<slug>.md) |
| 02 | <slug> | 01 | ⬜ Not started | 0/N | [PHASE_02__<slug>.md](PHASE_02__<slug>.md) |
| NN | docs-catalog-cleanup | all | ⬜ Not started | 0/N | [PHASE_NN__docs-catalog-cleanup.md](PHASE_NN__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

<Every §6 research item with `Status: Open` becomes a checkbox. Phase 01 must not start while any blocker is unchecked.>

- [ ] **Research:** <title> - required before Phase <NN>. See strategic §6.X.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <Sxxxx>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- <YYYY-MM-DD> - Phase NN blocked: <cause>. Next: <who/what/when>.

---

## Change Log

- <YYYY-MM-DD> - Initial tactical plan authored by `/spec-tech`.
```

---

## Phase File Template

```markdown
# Phase NN - <Phase Title>

**Strategic spec:** [`../Sxxxx_<short-name>.md`](../Sxxxx_<short-name>.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase NN-M (or "none - foundation phase")
**Blocks:** Phase NN+K, Phase NN+L
**Steps done:** 0 / N
**Started:** -
**Completed:** -

---

## Objective

<One sentence. What this phase produces. Example: "Introduce `InputBindingRepository` with Room persistence and Hilt wiring; no UI or dispatch changes yet.">

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] <any phase-specific precondition>

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<File>.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<Existing>.kt` | Modified | ≤ 500 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step NN.1 - <Imperative title>

**Files:** `path/to/File.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> <Self-contained imperative, 1-4 sentences, filler removed. Reader must not need to open the strategic spec.>

**Why:**

<At least one complete sentence, sourced from the strategic spec. The reason this step exists: what breaks without it, or which constraint it satisfies. Never a restatement of the prompt. Not compressed. No reason in the strategic spec -> `not stated in strategic spec` verbatim.>

**Verification:**

- `Glob` - `path/to/File.kt` exists.
- `Grep` - `class <ClassName>` matches exactly once in that file (declaration line, not comment).
- `Grep` - `<ExpectedMethodSignature>` present.

**Status:** `[ ]` not done

---

### Step NN.2 - <Imperative title>

**Files:** ..
**Depends on:** Step NN.1

**Prompt for developer:**

> ..

**Why:**

<..>

**Verification:**

- ..

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step NN.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-<NN>)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

<Invariants this phase established. If final phase - "Final phase - see INDEX.md Completion Gate.">

---

## Rollback Plan

<If risk warrants: which commits to revert, config to restore. For low-risk: "Revert phase commit(s) - no data migration or user-facing surface changed.">
```

---

## Constraints

- One step = one atomic unit: committable in isolation without breaking the build.
- Every step Verification must be static (Glob/Grep/value equality) - no "works correctly".
- No step references read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- File >500 lines after edit → backup step required. File >1500 lines → refuse; split via Manager pattern first.
- Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.
- Room schema change: bump `@Database(version)`, add `Migration`, never rename prior migrations. One phase per schema change.
- Hilt bindings: every new `@Inject`/`@Provides` names the `@Module` file in the step body.
- Trilingual strings: one step covering all three `values/strings.xml` files with three Grep verifications.
- Timber only: `Grep -n "Log\.d\("` returning zero hits mandatory for any file the step modifies.
- Final phase always `PHASE_NN__docs-catalog-cleanup.md`.
- Do not duplicate strategic content - tactical says *what*. The one exception is each step's `**Why:**` field (step 5), which carries one sentence of sourced rationale so `/spec-dev` does not have to open the strategic spec to judge an uncovered edge case; it quotes the reason, it does not restate the section.
- Never write phase steps that create audit / fix files in `PLAN/` - those are abolished.
- **Landscape parity (MANDATORY):** any step that edits `res/layout/*.xml` MUST list `res/layout-land/<file>.xml` in `Files Touched` (if the landscape variant exists) or include an explicit note: "landscape variant absent - not needed / to be created in step NN.M". Never produce a phase file with a portrait-only layout step when a landscape counterpart exists.
- **Lazy optimization (MANDATORY):** any step introducing an optional feature (guarded by `AppSettings`), a heavy DI dependency (e.g. network/cloud client), or a heavy UI overlay must explicitly plan for lazy loading. Heavy injected dependencies must use `dagger.Lazy<T>`, optional UI views must be loaded via `<ViewStub>`, and player/decoder allocations must run on-demand and release immediately when inactive.

---

## Spec Catalog hooks

- **Argument resolution.** First positional argument is `Sxxxx` (preferred) or a slug. If slug, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json` to obtain the id.
- **File / folder names.** Strategic spec is at `PLAN/<Sxxxx>_<slug>.md`. Tactical folder is `PLAN/<Sxxxx>_<slug>/`. Phase files inside follow the existing `PHASE_NN__<topic>.md` convention (no per-phase `Sxxxx` prefix). The `_spec_` segment is forbidden anywhere.
- **Status transition.** After the tactical folder is fully written, run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Tactical`.
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never create a tactical folder at `PLAN/<Sxxxx>_spec_<slug>/` or `PLAN/spec_<slug>/`.
