---
mode: agent
description: "Use when: breaking an approved strategic spec into a tactical phase plan, asked to run /spec-tech. Triggers on: spec-tech, tactical plan, phases, phase breakdown, spec-tech <Sxxxx>."
---

# Tactical Specification Writer

Break an approved strategic spec into sequenced phases. Requires `Status: Approved` or later.
Creates `PLAN/Sxxxx_<short-name>/INDEX.md` + phase files. Language: English, imperative, no rationale prose.

## Usage

```text
/spec-tech <Sxxxx-or-slug>
/spec-tech <Sxxxx-or-slug> --phase <NN>
/spec-tech <Sxxxx-or-slug> --dry-run
```

Aborts if `Status: Draft`. The strategic spec must exist at `PLAN/Sxxxx_<short-name>.md`.

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

No `_spec_` segment in any path. Phase-slug: kebab-case, <=4 words. Examples: `foundations`, `input-dispatch`, `db-migration`.

---

## Process

**1 - Validate strategic spec.**

Resolve `Sxxxx` and slug via `select.ps1`. Read `PLAN/Sxxxx_<short-name>.md`. Abort if missing or `Status: Draft` / `Block*` (Block states require resolution first).
Extract: feature name, tier, priority, goals (§2), constraints (§3.2), pillars (§5.1), open research items (§6), ADRs (§9), criteria (§11).

**2 - Read project context.**

- `dev/PROJECT_OPERATIONS_INDEX.md`
- `dev/CATALOG/<module>.md` or `.jsonl`
- `docs/ARCHITECTURE.md`
- `app_v2/build.gradle.kts`
- All source files for the affected area. Every file path referenced in a step must exist or be explicitly marked "New".

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

Target 3-8 phases. >10 -> split the feature into multiple specs.

**4 - Write `INDEX.md`** using the template below.

**5 - Write each `PHASE_NN__<slug>.md`** using the phase template. Steps numbered `NN.M`.

**6 - Update strategic spec.** Flip `Status:` to `Tactical`. Add:

```markdown
**Tactical plan:** `PLAN/Sxxxx_<short-name>/INDEX.md`
```

**7 - Run dev log** for every file written.

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>/INDEX.md" "spec-tech" "Create tactical plan for <Sxxxx>"
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>/PHASE_01__<slug>.md" "spec-tech" "Phase 01: <slug>"
# one line per phase file
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<short-name>.md" "spec-tech" "Status -> Tactical"
```

**Chat output:** `<Sxxxx>: N phases. Blockers: [list or none]. Index: PLAN/Sxxxx_<short-name>/INDEX.md`

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
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<File>.kt` | New | <= 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<Existing>.kt` | Modified | <= 500 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step NN.1 - <Imperative title>

**Files:** `path/to/File.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> <Self-contained imperative, 1-4 sentences. Reader must not need to open the strategic spec.>

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

**Verification:**

- ..

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step NN.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-<NN>)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>`.

---

## Handoff Notes to Next Phase

<Invariants this phase established. If final phase - "Final phase - see INDEX.md Completion Gate.">

---

## Rollback Plan

<If risk warrants: which commits to revert, config to restore. For low-risk: "Revert phase commit(s) - no data migration or user-facing surface changed.">
```
