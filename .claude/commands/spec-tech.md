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
- Before fixing an approach, open `dev/REFUTED_APPROACHES.md`; if a measurement rejects a proposed approach, add the ticket, measurement, and shipped alternative there.

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

**4 - Write `INDEX.md`** from `.claude/templates/tactical-index.md` (see "Templates" below).

**5 - Write each `PHASE_NN__<slug>.md`** from `.claude/templates/phase-file.md`. Steps numbered `NN.M`.

> **Step form (S1343, adopted 2026-08-02).** Every written step carries a `**Why:**` field between `**Prompt for developer:**` and `**Verification:**` - at least one complete sentence, sourced from the strategic spec, stating what breaks without the step or which constraint it satisfies, never a restatement of the prompt. Source it or write `not stated in strategic spec` verbatim; never invent a reason the strategic spec does not state. `Prompt for developer:` itself drops filler words and redundant turns of phrase ("please", "in order to", restating the step title), but is not otherwise shortened, and causal wording is never compressed. Full rule in `.claude/templates/phase-file.md`'s header.

> **Communication policy gate:** any step adding/rewriting user-visible strings - include in its `Prompt for developer:` a check against `docs/COMMUNICATION_POLICY.md` §2 (message formula for the type) and §6 (tone checklist). Make tone checklist a Verification predicate: `Strings pass COMMUNICATION_POLICY §6 checklist`.

**5.5 - Plan self-review (mandatory).** After all phase files written and before any status flip, re-read `INDEX.md` and every phase file against 3.1 inventory and 3.2 topology:

- Every inventory line maps to a *written* step (not intended one), or carries its `out-of-scope` reason.
- Every symbol a step consumes either greps in current codebase or is created by earlier step - check actual `Files Touched` + prompts, not plan's intent.
- Every `Depends on` matches 3.2 topology; no phase or step references artifact from later phase.
- No step violates 3.4 real-work filter.
- Research findings reflected: step contradicting Resolved §6 artifact is planning bug to fix here, not implementation detail to discover later.
- **UI placement decision recorded (refusal, S1338).** Any phase whose `Files Touched` names `app_v2/src/main/res/layout*`, an `Activity`/`Fragment`/`*View`/`ui/**` class, or a settings surface **fails this self-check** unless the strategic spec carries a recorded placement decision for it: either a `/ui-clarify` record, or an owner ruling quoted verbatim. There is no "decide during implementation" path - guessing placement is the single largest correction class the owner reports (33% of all corrections), while `/ui-clarify` was invoked once in a month. Missing → run `/ui-clarify` now and write its answer into the spec, or mark the ticket `BlockQuestions`; do not write the phase and hope.

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

## Templates

Both skeletons live in `.claude/templates/` - one template, many referrers. Read each before writing the first file of its kind in a run.

- `INDEX.md` (step 4): write it from `.claude/templates/tactical-index.md` - substitute `<Sxxxx>`, `<short-name>`, the feature/tier/priority frontmatter, one Phase Overview row per phase and the `Research inputs:` links.
- `PHASE_NN__<slug>.md` (step 5): write it from `.claude/templates/phase-file.md` - substitute `<Sxxxx>`, `<short-name>`, the phase number `NN`, the phase slug and title, the `Depends on` / `Blocks` phases and the step numbers `NN.M`.

---

## Constraints

- One step = one atomic unit: committable in isolation without breaking build.
- Every step Verification must be static (Glob/Grep/value equality) - no "works correctly".
- No step references a read-only zone: per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
- Per CLAUDE.md Rule 5 (backup before editing >500 LOC) - obey it as written; a step crossing that line carries an explicit backup sub-step.
- Per CLAUDE.md Rule 2 (1500 LOC file size limit) - obey it as written; a step that would cross it is refused at planning time, not at impl time.
- Per CLAUDE.md Rule 6 (class naming suffixes) - obey it as written.
- Room schema change: bump `@Database(version)`, add `Migration`, never rename prior migrations. One phase per schema change.
- Hilt bindings: every new `@Inject`/`@Provides` names the `@Module` file in step body.
- Trilingual strings: one step covering all three `values/strings.xml` files with three Grep verifications; step body should use `scripts/utils/set-android-string.ps1 -Action add -Key -En -Ru -Uk` (one lockstep call, parity-enforced) rather than three manual edits.
- Per CLAUDE.md section 8 "Project Structure & Tech Stack" on logging - obey it as written; the step's Verification states it as `Grep -n "Log\.d\("` returning zero hits for any file the step modifies.
- Per CLAUDE.md Rule 19 (neuroslop avoidance, detekt-clean-first) - obey it as written. The planning-time addition: phase `Prompt for developer:` text must not invite AI-slop in the first place. `post-change.ps1`'s `neuroslop-gate` enforces at impl time.
- Final phase always `PHASE_NN__docs-catalog-cleanup.md`.
- Do not duplicate strategic content - tactical says *what*. The one exception is each step's `**Why:**` field (step 5), which carries one sentence of sourced rationale so `/spec-dev` does not have to open the strategic spec to judge an uncovered edge case; it quotes the reason, it does not restate the section.
- Never write phase steps that create audit / fix files in `PLAN/` - abolished.
- Research artifacts under `PLAN/Sxxxx_<short-name>/research/` are mandatory planning input: read all before step 3, list in INDEX `Research inputs:`.
- Real-work filter (step 3.4) binds every step, not just planning pass: no step whose primary action edits `PLAN/**` text, outside final cleanup phase.
- **Landscape parity (MANDATORY):** per CLAUDE.md Rule 11 (layout-land parity) - obey it as written. Planning-time form of it: any step editing `res/layout/*.xml` MUST list `res/layout-land/<file>.xml` in `Files Touched` when the landscape variant exists, or carry the explicit note "landscape variant absent - not needed / to be created in step NN.M".
- **Flavor source-set discipline (MANDATORY).** Per CLAUDE.md Rule 14 (flavor isolation) - obey it as written; `/spec-dev` hard-stops on a violation. Planning-time form of it: if strategic §3.2 names a non-`standard` flavor target (`vr`, `vrUnlicensed`, `noLegal`, `lite`, `photos`, `legacy`) - or differentiates behavior between flavors - every flavor-specific file in `Files Touched` MUST live under `src/<flavor>/java/` (or `src/<flavor>/res/`, `src/<flavor>/AndroidManifest.xml`), with the contract interface and No-Op fallback in `src/main/java/` and the binding in a flavor-local Hilt `@Module` under `src/<flavor>/java/.../di/`. Reference layout: `dev/FLAVOR_DEVELOPMENT_RULES.md` §3-§4. Correct patterns on disk: `src/vr/java/.../vr/di/VrModule.kt` (binds `FullscreenCommandOverride` / `BrowsePassthroughCaptureProvider` / `VrLayerFactory`), `src/noLegal/java/.../di/NoLegalLinkDownloadModule.kt` (multibinding `@IntoSet` for link extraction strategies).
- **Catalog hint for flavor-only classes.** A phase introducing flavor-only class under `src/<flavor>/java/` SHOULD include sub-step in `PHASE_NN__docs-catalog-cleanup` to call `set.ps1 -NoFlavors "<other flavors>"` - e.g. vr-only class declares `-NoFlavors "standard,lite,photos,legacy,noLegal"`. Source-set placement governs physical isolation; catalog hint makes intent searchable.

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or slug. If slug, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json` to obtain id.
- **File / folder names.** Strategic spec at `PLAN/<Sxxxx>_<slug>.md`. Tactical folder `PLAN/<Sxxxx>_<slug>/`. Phase files follow `PHASE_NN__<topic>.md` (no per-phase `Sxxxx` prefix). `_spec_` segment forbidden anywhere.
- **Status transition.** After tactical folder fully written, run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Tactical`. For any `Block*` transition include `-StatusNote '<reason and what resolves it>'` - mandatory per CLAUDE.md §4.
- **Forbidden:** per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written. Additionally, never create a tactical folder at `PLAN/<Sxxxx>_spec_<slug>/` or `PLAN/spec_<slug>/`.
