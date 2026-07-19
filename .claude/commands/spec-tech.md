---
description: "Use to break an approved strategic spec into a tactical plan of phases and steps. Triggers: 'spec-tech Sxxxx', 'make the tactical plan', 'break this spec into steps'."
---

# Tactical Specification Writer

Break approved strategic spec into sequenced phases. Requires `Status: Approved` or later (see auto-promote below).
Creates `PLAN/Sxxxx_<short-name>/INDEX.md` + phase files. Language: English, imperative, no rationale prose.

## Usage

```text
/spec-tech <Sxxxx-or-slug>
/spec-tech <Sxxxx-or-slug> --phase <NN>
/spec-tech <Sxxxx-or-slug> --dry-run
```

**Draft auto-promote:** if `Status: Draft`, advance to `Approved` before proceeding. Note in chat. `Block*` states are the only statuses causing hard abort - require explicit resolution.

Auto-promote runs through `update.ps1`, firing Owner-Inputs gate (`scripts/spec_catalog/check-owner-inputs.ps1`). If strategic spec lacks §3.3 or any bullet is placeholder, gate blocks promotion - abort with gate's exact error, ask operator to fix §3.3 (never invent or backfill `n/a` lines to pass). Gate relevance-driven: only bullets `/spec` step 5.1 emitted are validated; `Related tickets` is only universally-required field.

Strategic spec must exist at `PLAN/Sxxxx_<short-name>.md`.

---

## Directory layout

```text
PLAN/Sxxxx_<short-name>.md          # strategic (Russian) - owned by /spec
PLAN/Sxxxx_<short-name>/
  INDEX.md
  research/                         # research artifacts - written by /spec, /research, /spec-all
    <NN>__<topic-slug>.md           # NN = strategic §6 item number
  PHASE_01__<slug>.md
  ..
  PHASE_NN__docs-catalog-cleanup.md
```

The `research/` subfolder may exist before INDEX.md (created when §6 items resolved). Its files are first-class planning input - equal rank with strategic spec.

No `_spec_` segment in any path. Phase-slug: kebab-case, ≤4 words. Examples: `foundations`, `input-dispatch`, `db-migration`.

---

## Process

**1 - Validate strategic spec.**

Resolve `Sxxxx` and slug via `select.ps1`. Read `PLAN/Sxxxx_<short-name>.md`. Abort if missing or `Status: Block*` (block states require resolution first).

If `Status: Draft` → auto-promote to `Approved`:

```powershell
(Get-Content "PLAN/${ticketId}_<short-name>.md") -replace '^(\*\*Status:\*\*\s*)Draft', '${1}Approved' |
    Set-Content "PLAN/${ticketId}_<short-name>.md"
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -Status Approved
```

Note in chat: `Status was Draft - auto-promoted to Approved.`

Extract: feature name, tier, priority, goals (§2), constraints (§3.2), pillars (§5.1), open research items (§6) plus their `**Артефакт:**` links, ADRs (§9), criteria (§11).

**2 - Read project context.**

- `PLAN/Sxxxx_<short-name>/research/*.md` - **mandatory when present**. Read every file in full before designing phases. Resolved §6 finding contradicting intended approach is planning input, not footnote - plan from findings.
- `dev/PROJECT_OPERATIONS_INDEX.md`
- `dev/CATALOG/<module>.md` or `.jsonl`
- `docs/ARCHITECTURE.md`
- `app_v2/build.gradle.kts`
- All source files for affected area. Every file path referenced in a step must exist or be explicitly marked "New".

**2.5 - Evaluate complexity (PRIMITIVE check).** Score against checklist:

- [ ] ≤ 3 existing files change - no new files
- [ ] No new classes, interfaces, or abstract types
- [ ] No Room schema change (`@Database` version bump or new `@Entity`)
- [ ] No new Hilt `@Module` or `@Provides`
- [ ] No new UI screens, fragments, or navigation destinations
- [ ] Mechanically deterministic - no deferred design decisions
- [ ] Estimated line delta < 100 lines total

**If ALL pass → PRIMITIVE path** (skip steps 3–8):

0. **CODE.LOCK (CLAUDE.md Rule 23).** Before touching source: `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "/spec-tech <Sxxxx>: primitive path"`. This path lists post-change's constituent steps manually rather than calling the `post-change.ps1` facade, so release is not automatic - free it explicitly in step 3 below.
1. Implement changes directly in source identified in step 2.
2. Insert `Timber.d("Sxxxx: <entry-point description>")` at each changed flow entry - per CLAUDE.md "Debug Verification Tags", ticket about to enter `BlockNeedUserTest`, tags must be present. One tag per flow entry, not per modified line. `Sxxxx:` prefix reserved for temporary probes; never reuse in `Timber.i/w/e` or any persisted message.
3. Run post-change mandatory steps: `add_to_dev_log.ps1`, `scan.ps1` + `render.ps1`, strings audit if applicable. Then release the lock: `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1`.
4. Advance to `BlockNeedUserTest` via `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest -StatusNote '<what the user must verify on device>'`. Step-2 tags stay until ticket leaves this status (removed by `/spec-check` on `Verified`, or `/spec-update` on re-open).
5. Chat output: `<Sxxxx> - Primitive. No phase files created. Implemented directly. Status: BlockNeedUserTest. Debug tags: N.`

No `INDEX.md`, no `PHASE_NN__*.md`. No `/spec-dev` chain.

**If ANY criterion fails → COMPLEX path:** continue with step 3.

---

**3 - Design phase graph.**

Phase ordering is highest-risk output of this skill: wrong order or missed strategic requirement costs full `/spec-dev` cycle. Do NOT write `INDEX.md` or any phase file until 3.1–3.4 all pass.

**3.1 - Coverage inventory.** Re-read strategic spec end-to-end plus every file in `PLAN/Sxxxx_<short-name>/research/`. Build working inventory (scratch, chat-side - never a PLAN file): one line per §2 goal, §5.1 pillar, §3.2 constraint with implementation impact, Resolved §6 finding, §9 ADR decision, §11 criterion. Map every line to >=1 planned phase, or mark `out-of-scope: <reason>`. Unmapped line = phase set incomplete; fix before proceeding.

**3.2 - Produces/Consumes topology.** For each candidate phase list two sets: `Produces` (new/changed artifacts: classes, methods, Room schema, DI bindings, resources, gradle/BuildConfig fields) and `Consumes` (artifacts phase needs: pre-existing in code - verified step 2 - or produced by strictly earlier phase). Validate topological order: no phase consumes artifact produced by later phase. Forward reference = order wrong - reorder now, not during implementation.

**3.3 - Ordering heuristics** (refine 3.2 topology, never override it):

1. Foundations first: data classes, repo interfaces, DI, Room schema+migration, gradle/BuildConfig flags.
2. Producer before consumer for every new symbol; migration before code reading new columns; strings/resources before or with UI referencing them.
3. User-visible changes last within their area.
4. Final phase always `PHASE_NN__docs-catalog-cleanup.md`: catalog regen, dev log; FEATURES trilingual only if strategic §8 mandates update (not "Без изменений").
5. Minimum one phase per strategic pillar (§5.1). Small pillars may fuse.

**3.4 - Real-work filter (anti-bureaucracy).** Every step's primary action must change source, resources, config, or scripts. Forbidden as steps:

- Edits to `PLAN/**` text - status flips, counters, retitling, renumbering, "align headers". Progress tracking is `/spec-dev` bookkeeping; plan authoring is this skill's own output - neither is plan *content*.
- "Review / sync / align documentation" without concrete file delta outside `PLAN/`.
- Restating or re-verifying a previous step's outcome as a separate step.

Sole exception: final docs-catalog-cleanup phase. A phase where most steps fail this filter is not a phase - merge surviving steps into a real one.

Phase shape (unchanged invariants): each phase mergeable as coherent unit; one build-time invariant proving completion; no half-broken state between steps. Target 3–8 phases. >10 → split feature into multiple specs.

**4 - Write `INDEX.md`** using template.

**5 - Write each `PHASE_NN__<slug>.md`** using phase template. Steps numbered `NN.M`.

> **Communication policy gate:** any step adding/rewriting user-visible strings - include in its `Prompt for developer:` a check against `docs/COMMUNICATION_POLICY.md` §2 (message formula for the type) and §6 (tone checklist). Make tone checklist a Verification predicate: `Strings pass COMMUNICATION_POLICY §6 checklist`.

**5.5 - Plan self-review (mandatory).** After all phase files written and before any status flip, re-read `INDEX.md` and every phase file against 3.1 inventory and 3.2 topology:

- Every inventory line maps to a *written* step (not intended one), or carries its `out-of-scope` reason.
- Every symbol a step consumes either greps in current codebase or is created by earlier step - check actual `Files Touched` + prompts, not plan's intent.
- Every `Depends on` matches 3.2 topology; no phase or step references artifact from later phase.
- No step violates 3.4 real-work filter.
- Research findings reflected: step contradicting Resolved §6 artifact is planning bug to fix here, not implementation detail to discover later.

Fix findings directly (reorder phases, rewrite steps, renumber), then re-run failed check once. Report in chat: `Plan self-check: PASS - <N> inventory items mapped, <M> reorders applied.` Never skip this pass - phase-order bugs are dominant tactical-plan defect.

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

**8 - Auto-chain to `/spec-dev`.** *(COMPLEX path only - skip if PRIMITIVE in step 2.5.)*

If no unchecked Pre-Implementation Blockers in INDEX - immediately invoke `/spec-dev <Sxxxx>`. If any blocker unchecked - list them and stop: implementation cannot proceed until resolved.

**Chat output:** `<Sxxxx>: N phases. Blockers: [list or none]. → Running /spec-dev…` (or `→ Blocked: [list]. Resolve and run /spec-dev <Sxxxx>` if blockers present.)

---

## `INDEX.md` Template

```markdown
# Tactical Plan: <Sxxxx> - <short-name>

**Strategic spec:** [`../Sxxxx_<short-name>.md`](../Sxxxx_<short-name>.md)
**Research inputs:** [`research/<NN>__<topic-slug>.md`](research/<NN>__<topic-slug>.md) <one link per artifact, or "none">
**Feature:** <feature name>
**Tier:** <tier label>
**Priority:** <0..100>
**Status:** Not started
**Phases:** 0 / N done
**Last updated:** <YYYY-MM-DD>

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

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

<Every §6 research item with `Status: Open` becomes checkbox. Phase 01 must not start while any blocker unchecked.>

- [ ] **Research:** <title> - required before Phase <NN>. See strategic §6.X.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 contains FEATURES sentence (not "Без изменений"); skip otherwise.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <Sxxxx>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
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

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** Flavor-only classes (vr / vrUnlicensed / noLegal / lite / photos / legacy) MUST be listed under `app_v2/src/<flavor>/java/...` - not under `src/main/java/`. Shared contract interface and No-Op fallback stay in `src/main/java/`. Hilt binding modules for real impl go under `src/<flavor>/java/.../di/`. See `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

## Steps

### Step NN.1 - <Imperative title>

**Files:** `path/to/File.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> <Self-contained imperative, 1–4 sentences. Reader must not need to open strategic spec.>

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
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

<Invariants this phase established. If final phase - "Final phase - see INDEX.md Completion Gate.">

---

## Rollback Plan

<If risk warrants: which commits to revert, config to restore. Low-risk: "Revert phase commit(s) - no data migration or user-facing surface changed.">
```

---

## Constraints

- One step = one atomic unit: committable in isolation without breaking build.
- Every step Verification must be static (Glob/Grep/value equality) - no "works correctly".
- No step references read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- File >500 lines after edit → backup step required. File >1500 lines → refuse; split via Manager pattern first.
- Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.
- Room schema change: bump `@Database(version)`, add `Migration`, never rename prior migrations. One phase per schema change.
- Hilt bindings: every new `@Inject`/`@Provides` names the `@Module` file in step body.
- Trilingual strings: one step covering all three `values/strings.xml` files with three Grep verifications; step body should use `scripts/utils/set-android-string.ps1 -Action add -Key -En -Ru -Uk` (one lockstep call, parity-enforced) rather than three manual edits.
- Timber only: `Grep -n "Log\.d\("` returning zero hits mandatory for any file the step modifies.
- Neuroslop avoidance (CLAUDE.md Rule 20): phase `Prompt for developer:` text must not invite AI-slop; code-adding steps implement clean - no trivial restating comments, no empty/broad swallowing `catch`, no hardcoded `="#hex"` in `res/layout*` (use `?attr/`/`@color/`), no bare `lifecycleScope.launch { flow.collect { } }` on view-bound Flows (use `collectOnLifecycle`). `post-change.ps1`'s `neuroslop-gate` enforces at impl time.
- Final phase always `PHASE_NN__docs-catalog-cleanup.md`.
- Do not duplicate strategic content - tactical says *what*, not *why*.
- Never write phase steps that create audit / fix files in `PLAN/` - abolished.
- Research artifacts under `PLAN/Sxxxx_<short-name>/research/` are mandatory planning input: read all before step 3, list in INDEX `Research inputs:`.
- Real-work filter (step 3.4) binds every step, not just planning pass: no step whose primary action edits `PLAN/**` text, outside final cleanup phase.
- **Landscape parity (MANDATORY):** any step editing `res/layout/*.xml` MUST list `res/layout-land/<file>.xml` in `Files Touched` (if landscape variant exists) or include explicit note: "landscape variant absent - not needed / to be created in step NN.M". Never produce a phase file with portrait-only layout step when landscape counterpart exists.
- **Flavor source-set discipline (MANDATORY).** If strategic §3.2 names a non-`standard` flavor target (`vr`, `vrUnlicensed`, `noLegal`, `lite`, `photos`, `legacy`) - or differentiates behavior between flavors - every flavor-specific file in `Files Touched` MUST live under `src/<flavor>/java/` (or `src/<flavor>/res/`, `src/<flavor>/AndroidManifest.xml`), never under `src/main/`. Contract interface and No-Op fallback go to `src/main/java/`; real impl to target flavor source set; binding in flavor-local Hilt `@Module` under `src/<flavor>/java/.../di/`. Phase steps writing `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` flavor guards into `src/main/java/**` are forbidden - `/spec-dev` hard-stops on them. Reference layout: `dev/FLAVOR_DEVELOPMENT_RULES.md` §3–§4. Correct patterns on disk: `src/vr/java/.../vr/di/VrModule.kt` (binds `FullscreenCommandOverride` / `BrowsePassthroughCaptureProvider` / `VrLayerFactory`), `src/noLegal/java/.../di/NoLegalLinkDownloadModule.kt` (multibinding `@IntoSet` for link extraction strategies).
- **Catalog hint for flavor-only classes.** A phase introducing flavor-only class under `src/<flavor>/java/` SHOULD include sub-step in `PHASE_NN__docs-catalog-cleanup` to call `set.ps1 -NoFlavors "<other flavors>"` - e.g. vr-only class declares `-NoFlavors "standard,lite,photos,legacy,noLegal"`. Source-set placement governs physical isolation; catalog hint makes intent searchable.

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or slug. If slug, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json` to obtain id.
- **File / folder names.** Strategic spec at `PLAN/<Sxxxx>_<slug>.md`. Tactical folder `PLAN/<Sxxxx>_<slug>/`. Phase files follow `PHASE_NN__<topic>.md` (no per-phase `Sxxxx` prefix). `_spec_` segment forbidden anywhere.
- **Status transition.** After tactical folder fully written, run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Tactical`. For any `Block*` transition include `-StatusNote '<reason and what resolves it>'` - mandatory per CLAUDE.md §4.
- **Forbidden:** never write `PLAN/spec-catalog.jsonl` directly; never create tactical folder at `PLAN/<Sxxxx>_spec_<slug>/` or `PLAN/spec_<slug>/`.
