---
description: "Use to review and refine an existing spec file. Triggers: 'spec-update Sxxxx', 'refine this spec', 'tidy the spec'."
---

# Specification Refinement

Review and refine a strategic or tactical spec in-place. Modifies spec files - not the codebase (`/spec-fix` does that).

## Usage

```text
/spec-update <Sxxxx-or-slug>                      # strategic spec
/spec-update <Sxxxx-or-slug> --tactical           # INDEX + every phase file
/spec-update <Sxxxx-or-slug> --tactical --phase 03
/spec-update <Sxxxx-or-slug> --index
/spec-update <Sxxxx-or-slug> --review-only        # analyse only, no writes
/spec-update <Sxxxx-or-slug> --apply-all          # same as default; DISCUSS still never auto-applied
/spec-update <Sxxxx-or-slug> --focus <aspect>     # language|structure|verifiability|consistency|completeness|style
/spec-update <Sxxxx-or-slug> --priority N         # update journal priority
```

Targets:

- Strategic: `PLAN/Sxxxx_<short-name>.md`
- Tactical INDEX: `PLAN/Sxxxx_<short-name>/INDEX.md`
- Tactical phase: `PLAN/Sxxxx_<short-name>/PHASE_NN__*.md`

---

## Review Focus Areas

### 1. `language`

- Strategic body: Russian. Tactical body: English. No mixed sentences.
- `..` not `...`; `ё`/`Ё` in Russian where grammatically correct.
- Style timing: never raise an ellipsis or `ё` finding on a spec file, at any status. The house text style does not cover specification files - the canon's scope list excludes specs by name - so no gate checks a spec's punctuation and no stage sweeps it (S1543). Spec Writing Style (lists over tables, no section summaries) remains an authoring standard with no gate behind it: raise it while a section is being rewritten anyway, never as a standalone sweep, and never on a `Draft`. Language correctness for `Approved`+ specs enforced as before.

### 2. `structure`

- All mandatory template sections present, in order.
- Strategic: no class names, file paths, line budgets, Room versions, Hilt modules.
- Tactical: no rationale prose (lives in strategic).
- No legacy `_spec_` segment in any path reference.

### 3. `verifiability`

- Every tactical step has a `Verification:` block with >=1 static predicate (Glob/Grep/value equality/file-size).
- No untestable phrasing ("works correctly", "behaves as expected").
- Every phase has Phase Done Criteria with >=3 invariants.
- Strategic §11: observable outcomes only.

### 4. `consistency`

- Strategic `Status:` <-> tactical INDEX `Status:` checked for alignment; mismatches are findings only, never auto-edited here.
- Strategic `Priority:` <-> journal `priority` aligned.
- INDEX phase counter matches phase-file `Status:` headers.
- Every tactical phase links to strategic spec at top.
- `Depends on` values reference existing earlier steps.

### 5. `completeness`

- Every strategic §2 Goal maps to >=1 tactical phase (or marked deferred).
- Every open §6 Research item appears in INDEX "Pre-Implementation Blockers".
- Every Resolved §6 item resolved by performed research carries `**Артефакт:**` link to `PLAN/Sxxxx_<slug>/research/<NN>__<topic-slug>.md`, and file exists.
- INDEX `Research inputs:` lists every file under `research/` (or "none"). Absent line on pre-convention tactical plans is a finding, not an error.
- Every §9 ADR applied somewhere in tactical.
- Resolving a §6 item during this skill follows same artifact rule: persist findings to `research/`, link via `**Артефакт:**`.

### 6. `style`

- Markdown well-formed: tables have header+separator, lists have blank lines, fences have language tags.
- No TODO/FIXME markers. No "TBD" - either resolved or promoted to §6.

---

## Process

**1 - Parse arguments, locate target.**

Resolve `Sxxxx` and slug via `select.ps1`. Resolve target file(s) from flags. Abort if target missing (suggest `/spec` or `/spec-tech`).

**1a - Status gate.**

| Strategic `Status:` | Allowed? |
| --- | :---: |
| `Draft` | ✅ |
| `Approved` | ✅ |
| `Tactical` | ✅ |
| `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal` | ✅ - refinement may unblock |
| `In Progress` | ⛔ spec locked for execution |
| `Implemented` / `Verified` / `Partial` / `Broken` | ⛔ historical record |

Locked -> abort. Offer: (1) new spec `/spec <id> <name>-v2`, (2) wait until closed. `--force-locked` overrides - record override reason in Revision History.

**1b - Re-open `BlockNeedUserTest`.** If journal status is `BlockNeedUserTest` and this is **not** `--review-only`, refining implies re-opening - do before review pass:

- `Grep` all `.kt` for `Timber.d("<Sxxxx>:` and delete every matching line. Debug-tag invariant (CLAUDE.md "Debug Verification Tags"): tags exist iff status `BlockNeedUserTest`, so leaving the status requires removing them. Run a dev log line per `.kt` file that lost a tag.
- Flip status to prior working stage: `Tactical` if `PLAN/Sxxxx_<slug>/INDEX.md` exists, else `Approved` if strategic spec exists, else `Draft`. Patch `**Status:**` line in spec file and run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status <new>`.
- Append Revision History line: `Re-opened from BlockNeedUserTest -> <new>; debug tags removed: N.`
- This is the **only** status change `/spec-update` performs. With `--review-only`, skip 1b entirely (no writes).

**1c - Readability gate.**

Any resolved target file cannot be read or parsed as markdown → abort that target with `Unreadable target: <path>`. Do not write or dev-log that file. Suggest restoring from history and rerunning `/spec-update`.

**2 - Review pass.**

Read target(s). For each focus area (all six, or `--focus` selection), produce observations:

- **Finding, Location, Severity** (`Critical`/`Major`/`Minor`/`Nitpick`), **Evidence** (quoted excerpt), **Classification**.

Classification rules:

| Kind of change | Classification |
| --- | --- |
| Typo, `...`->`..`, missing `ё`, blank lines around list/fence | ACCEPT |
| Missing mandatory section skeleton | ACCEPT |
| Rewording ambiguous `Verification:` predicate (obvious fix) | ACCEPT |
| Removing class names/paths from strategic spec | ACCEPT |
| Removing `_spec_` segment from path references | ACCEPT |
| Adding/removing/merging a step or phase | DISCUSS |
| Renaming a class in tactical step | DISCUSS |
| Demoting strategic<->tactical content | DISCUSS |
| Adding/editing an ADR | DISCUSS |

**3 - Apply pass** (skip if `--review-only`).

Per memory rule: **fix all non-structural issues silently**. Only structural decisions go into a DISCUSS block.

- **ACCEPT** - apply via `Edit`. Append a single Revision History line covering the run.
- **DISCUSS** - record in "Proposed Structural Changes" block with `Status: Proposed`. Never apply regardless of `--apply-all`.

Edits minimal and localized. Never renumber steps/phases unless that is the specific finding - structural changes that would renumber stay in DISCUSS until explicitly accepted.

**4 - Maintain Revision History block.**

Append to target file bottom (create section if absent):

```markdown
## Revision History

- **<YYYY-MM-DD>** - by `/spec-update` (`<model-id>`, focus: <aspects>)
  - Applied: N. Proposed (DISCUSS): N.
```

DISCUSS items append under:

```markdown
## Proposed Structural Changes

### Proposal P-<N> - <title>  (proposed <YYYY-MM-DD> by <model-id>)

**Status:** Proposed
**Affected:** <section>
**Rationale:** <why>
**Suggested edit:**
> <before> -> <after>
```

Proposals never removed. Accept -> flip `Status: Accepted` and apply. Reject -> flip to `Rejected`.

**5 - Cross-file checks.**

If strategic target and tactical folder both exist: run `consistency` focus between them. Edits to other file are DISCUSS only. `Status:` mismatches remain findings or DISCUSS items - never auto-edit them from this skill.

**6 - Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/Sxxxx_<slug>.md" "spec-update" "Refinement (<model-id>, focus: <aspects>)"
# one line per tactical file if touched
```

**Chat output:** `<Sxxxx>: applied N, proposed N DISCUSS. Clean: [aspects].`

---

## Constraints

- Never invent translations. Missing RU/UK -> `<!-- TODO translate: <EN> -->` or DISCUSS.
- Never renumber steps/phases - cascades into all references.
- Never touch `Status:` fields - sole exception is the `BlockNeedUserTest` re-open in step 1b (which also deletes the spec's `Timber.d("Sxxxx:` debug tags from `.kt`). Otherwise only `/spec-check` moves status. Alignment checks may report mismatches, but only the owning status-transition skill changes them.
- Class names/file paths in strategic specs: auto-fix via ACCEPT (replace with architectural term).
- Tactical steps with non-static Verification: ACCEPT with Glob/Grep template if obvious; otherwise DISCUSS.
- Per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
- Never create audit / fix files (`__audit_*.md` / `__fix_*.md`) - abolished.
- `--review-only`: no writes, no dev log.
- Revision History is append-only - never rewrite earlier entries.

---

## Spec Catalog hooks

- **Argument resolution.** First positional argument is `Sxxxx` (preferred) or a slug.
- **Status transition.** After refinement applied, touch journal `updated` timestamp without changing status: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>`. On `--review-only` skip the update. With `--priority N` also pass `-Priority N`. **Exception:** the `BlockNeedUserTest` re-open (step 1b) does change status - to `Tactical` / `Approved` / `Draft` - and deletes the spec's debug tags from `.kt`.
- **Forbidden:** never set journal status from this skill except the step-1b re-open. Never write to `PLAN/spec-catalog.jsonl` directly.
