# Specification Refinement

Review and refine an existing specification (strategic or tactical) through a review-then-propose-then-apply workflow. Designed for cross-model iteration: the user alternates models (Opus ↔ Sonnet ↔ Haiku) to get independent quality passes on the same document, each pass recorded in a revision history at the bottom of the spec.

Unlike `/spec-fix` (which modifies the **codebase** based on audit outcomes), `/spec-update` modifies the **spec files themselves** — wording, structure, missing sections, unclear verification predicates, stale references.

## Usage

```text
/spec-update <short-name>                      # interactive refinement of the strategic spec
/spec-update <short-name> --tactical           # refine tactical INDEX + every phase file
/spec-update <short-name> --tactical --phase 03  # refine one phase file
/spec-update <short-name> --index              # refine INDEX.md only
/spec-update <short-name> --review-only        # analyse and propose; do not write
/spec-update <short-name> --apply-all          # skip per-proposal confirmation; apply all ACCEPT-classified proposals
/spec-update <short-name> --focus <aspect>     # narrow the review: language | structure | verifiability | consistency | completeness | style
```

Examples:

- `/spec-update player-keybinding-remapping`
- `/spec-update background-thumbnail-preload --tactical --phase 02`
- `/spec-update vr-hand-tracking --review-only --focus verifiability`
- `/spec-update decompose-giant-files --tactical --apply-all --focus style`

Targets:

- Strategic spec — `PLAN/spec_<short-name>.md`.
- Tactical INDEX — `PLAN/spec_<short-name>/INDEX.md`.
- Tactical phase — `PLAN/spec_<short-name>/PHASE_NN__*.md` (chosen via `--phase NN`).

---

## Review Focus Areas

When the user omits `--focus`, run all six. Each aspect has explicit heuristics so that different models produce comparable, reproducible feedback.

### 1. `language`

- Strategic body in Russian; frontmatter / paths / code identifiers in English.
- Tactical body in English; no Russian prose.
- Author style: `..` not `...`; `ё`/`Ё` in Russian where grammatically correct.
- No mixed-language sentences.

### 2. `structure`

- All mandatory sections from the `/spec` or `/spec-tech` template are present (no silent skipping).
- Section order matches the template. Missing sections are flagged; extra sections are flagged unless they add value.
- Strategic spec does NOT contain class names, file paths, line budgets, Hilt modules, Room versions (those belong in tactical).
- Tactical spec does NOT contain rationale prose (that lives in strategic).

### 3. `verifiability`

- Every tactical step has a `Verification:` block with at least one static predicate (Glob / Grep / value equality / file-size).
- No `Verification:` entry says "works correctly", "behaves as expected", or similar untestable phrasing.
- Every phase has Phase Done Criteria with ≥ 3 ticked-off invariants.
- Strategic §11 criteria are phrased as observable outcomes, not internal architecture claims.

### 4. `consistency`

- Strategic `Status:` ↔ tactical INDEX `Status:` alignment.
- INDEX phase counter matches phase-file `Status:` headers.
- Every tactical phase references the strategic spec via relative link at the top.
- Every step's `Depends on` value refers to an existing earlier step.
- `Blocks:` back-references are symmetric.
- FEATURES text in strategic §8 matches FEATURES.md + `_RU` + `_UK` if the spec is already `Implemented`.

### 5. `completeness`

- Every strategic §2 Goal maps to at least one tactical phase (or is marked deferred).
- Every open strategic §6 Research item appears in INDEX "Pre-Implementation Blockers".
- Every ADR in strategic §9 is applied somewhere in tactical (pillar, phase, or step).
- Risk items in strategic §7 have a mitigation referenced in at least one phase step.

### 6. `style`

- Markdown well-formed: tables have header + separator, lists have blank lines around them, code fences have language tags.
- Sentences are short and imperative in tactical; clear and declarative in strategic.
- No TODO / FIXME markers that were supposed to be resolved before the spec was Approved.
- No stray commentary ("TBD", "to think about this later") — either resolved or promoted to a Research item in §6.

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse arguments, locate target.**

- Extract `<short-name>` and flags.
- Resolve target file(s):
  - No flags → strategic `PLAN/spec_<short-name>.md`.
  - `--tactical` without `--phase` or `--index` → INDEX + every phase file.
  - `--tactical --phase NN` → single phase file.
  - `--index` → INDEX.md only.
- Abort if the target file / folder does not exist. Suggest `/spec` or `/spec-tech`.
- Record model identity (e.g. `claude-opus-4-7`) for the revision history entry.

**Step 1a — Status gate (mandatory).**

Read the strategic spec's `Status:` field. `/spec-update` is only allowed when the spec is **not yet locked for implementation**:

| Strategic `Status:` | Allowed? | Rationale |
|---------------------|:--------:|-----------|
| `Draft` | ✅ | Spec still being shaped. |
| `Approved` | ✅ | Tactical not yet authored — refinement welcome. |
| `Tactical` | ✅ | Tactical plan exists but developer has not started. |
| `In Progress` | ⛔ | Developer is executing against the contract. Changing the spec now creates drift and invalidates ongoing work. |
| `Implemented` | ⛔ | Awaits audit. Editing the spec would retroactively change the criteria `/spec-check` will use. |
| `Verified` / `Partial` / `Broken` | ⛔ | Historical record. Refinement would rewrite history against an already-audited state. |

If `Status:` is `In Progress` or later, abort with a message explaining the reason and offer two alternatives:

1. Open a new spec (`/spec <id> <name>-v2`) for follow-up changes.
2. Wait until the feature is closed (`Verified`); then author a fresh spec for the next iteration.

If the user insists they have a legitimate reason (e.g. a Critical audit finding exposed a strategic gap), require explicit confirmation with the flag `--force-locked` and still record the override in Revision History as:

```markdown
- **<YYYY-MM-DD>** — by `/spec-update` (`<model-id>`, **--force-locked** — Status: <current>)
  - Override reason: <one line stated by the user>
```

Without `--force-locked`, a locked spec halts at Step 1a — no review, no writes.

**Step 2 — Review pass.**

Read the target file(s). For each focus area (all six, or the single area from `--focus`), produce a list of observations:

- **Finding:** what is wrong, missing, or weak.
- **Location:** section / subsection / line range in the spec.
- **Severity:** `Critical` (template violation, missing mandatory section), `Major` (clarity / verifiability gap that impairs downstream tools), `Minor` (wording / style), `Nitpick` (cosmetic).
- **Evidence:** short quoted excerpt.
- **Classification:** `ACCEPT` (safe auto-apply), `REVIEW` (needs user approval even in `--apply-all`), `DISCUSS` (structural — propose, do not rewrite).

Classification rules:

| Kind of change | Classification |
|---------------|----------------|
| Typo, punctuation, `...` → `..`, missing `ё` | ACCEPT |
| Missing blank line around list / fence | ACCEPT |
| Adding a missing mandatory section skeleton | REVIEW |
| Rewording an ambiguous `Verification:` predicate | REVIEW |
| Adding a new step / phase | DISCUSS |
| Removing / merging a step / phase | DISCUSS |
| Renaming a class in a tactical step | DISCUSS (cascades into code) |
| Demoting strategic content to tactical (or vice versa) | DISCUSS |
| Adding / editing an ADR | DISCUSS |
| Cross-language synchronisation (translating a missing RU bullet) | REVIEW (never invent — add TODO-translate placeholder or ask) |

**Step 3 — Proposal pass.**

Emit the findings to the user as a single review report (not saved to a file in `--review-only` mode — printed to chat). Group by focus area, sorted by severity. Example layout:

```text
Focus: verifiability  (2 Critical, 3 Major, 1 Minor)
  [Critical] §Phase 02 — Step 02.3
    Finding: Verification reads "works correctly"; no static predicate.
    Evidence: > Verification: - the feature works end to end
    Proposal:  replace with Grep + Glob predicates — see suggested edit.
    Classification: REVIEW
  ..
```

If `--review-only`: stop here. Print the full report; do not modify files.

**Step 4 — Apply pass (unless `--review-only`).**

Iterate findings in severity order (Critical → Nitpick):

- **ACCEPT** — apply directly via `Edit`. Record in revision history.
- **REVIEW** — present the proposed edit (old block ↔ new block diff form) and ask the user to accept / reject / skip. Unless `--apply-all` is set, in which case auto-apply.
- **DISCUSS** — never auto-apply. Record the proposal in a "Proposed Structural Changes" block added to the file's revision history with `Status: Proposed` so another model or the user can decide later.

Edits must be minimal and localised. Do not rewrite whole sections when a single line is wrong. Never renumber steps / phases unless that is the specific finding.

**Step 5 — Maintain the Revision History block.**

Every refined file must contain a `## Revision History` section at the very bottom. If absent, append it. Each `/spec-update` run appends one entry:

```markdown
## Revision History

- **<YYYY-MM-DD>** — by `/spec-update` (`<model-id>`, focus: <aspect list>)
  - ACCEPT applied: N findings (<one-line summary>)
  - REVIEW applied: N findings (<one-line summary>) / rejected: M
  - DISCUSS proposed: N items — see "Proposed Structural Changes" below.
- **<YYYY-MM-DD>** — by `/spec-update` (<earlier model>, focus: ..)
  - ..
```

If there are DISCUSS items, append (or extend) a block directly under the Revision History:

```markdown
## Proposed Structural Changes

### Proposal P-<N> — <title>  (proposed <YYYY-MM-DD> by <model-id>)

**Status:** Proposed | Accepted | Rejected | Superseded

**Summary:** <one sentence>
**Affected section:** <path within spec>
**Rationale:** <why>
**Suggested edit:**
> <quoted before>
→
> <quoted after>
**Next step:** user or another model to decide via `/spec-update`.
```

Proposals are never silently removed. When the user accepts a proposal in a later run, flip `Status: Accepted` and apply the edit; when rejected, flip to `Rejected` and keep the block as an audit trail.

**Step 6 — Cross-file checks.**

If the target is strategic and tactical folder exists, also run the `consistency` focus between them (regardless of `--focus` value) and surface mismatches. Edits to the *other* file are offered as DISCUSS — do not silently touch files outside the chosen target unless explicitly authorised by the user.

**Step 7 — Run the dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-update" "Refinement pass by <model-id> (focus: <aspects>)"
# If tactical files were touched, one line per file.
```

**Step 8 — Summarise to the user** (Russian): total findings by severity, per-classification counts (ACCEPT / REVIEW / DISCUSS), which edits were applied vs proposed, and which focus areas came out clean. If DISCUSS proposals exist, recommend running `/spec-update` with a different model next to get a second opinion.

---

## Cross-model Iteration Workflow

The skill is designed for iteration across models. Typical sequence:

1. `/spec-update <name>` with model A — first pass, many findings, mostly ACCEPT + a few REVIEW.
2. Switch model (e.g. via `/model`), run `/spec-update <name>` again — the second model may disagree with some choices or surface findings the first missed.
3. If DISCUSS proposals accumulate, use a third model as a tie-breaker with `/spec-update <name> --review-only` to read proposals and recommend accept / reject.
4. Finalise via `/spec-update <name>` on any model with `--apply-all` after reviewing outstanding DISCUSS items.

The Revision History + Proposed Structural Changes blocks make this legible: each pass sees what previous models did and either extends, contradicts, or confirms.

---

## Quality Rules

- **Never invent translations.** Missing RU/UK text becomes a placeholder `<!-- TODO translate: <EN> -->` or a DISCUSS proposal asking the user, never fabricated.
- **Never renumber existing steps / phases** unless that is literally the finding. Renumbering cascades into every downstream reference.
- **Never touch `Status:` fields** on strategic spec, tactical INDEX, or phase headers. Only `/spec-check` flips those.
- **Preserve language invariants.** Strategic body = Russian prose. Tactical body = English. A finding that a language rule is violated is Critical; auto-translate fixes are forbidden — the user decides.
- **Preserve section contract.** Do not silently add non-template sections beyond `Revision History` + `Proposed Structural Changes`. Any other new section is a DISCUSS proposal.
- **Class names / file paths are forbidden in strategic specs.** A finding of such a name is Critical; auto-fix is REVIEW-only with two alternative phrasings offered.
- **Line-budget / Room version / Hilt module references are forbidden in strategic specs.** Same treatment.
- **Tactical steps must have static Verification predicates.** A finding of non-static phrasing is Major; auto-fix offers a REVIEW proposal with Glob/Grep templates. Reject if a static predicate cannot be constructed — promote to DISCUSS.
- **Read-only zones are never edited** (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`).
- **`--review-only` is strictly read-only.** No file writes, no dev log. It must print findings and exit.
- **`--apply-all` does NOT override DISCUSS.** Structural changes are never auto-applied regardless of flags.
- **Author style is enforced across all edits:** `..` not `...`; `ё`/`Ё` in Russian text.
- **Revision History order is append-only.** Never rewrite earlier entries. The block is an audit trail.
- **Proposals are durable.** Do not delete a `## Proposed Structural Changes` item when rejected — flip its `Status:` to `Rejected` so future model passes see prior reasoning.
- **Do not cross boundaries silently.** A refinement pass on the strategic spec does NOT modify tactical files. If a strategic change implies a tactical change, it is a DISCUSS proposal or a recommendation to run `/spec-update <name> --tactical` next.

---

## Failure Modes to Watch

- **Thrash between models.** Two models may disagree on a Minor finding and overwrite each other across runs. Use DISCUSS classification aggressively for borderline calls — never ACCEPT a debatable wording change.
- **Silent context loss.** A model that re-reads the spec cold may miss why a counter-intuitive choice was made. If the Revision History of a section says "kept this phrasing despite ambiguity (ADR-2)", respect the ADR and do NOT refile the same finding.
- **Template creep.** A new mandatory section added by this skill without updating `/spec` or `/spec-tech` creates drift. If the skill finds itself proposing the same new section across multiple specs, suggest editing the upstream `/spec` / `/spec-tech` templates instead.
- **Stale Revision History.** A Revision History entry from three months ago may refer to a finding the spec has since fixed through other channels. Current state is the source of truth; the history is informational.
- **Over-aggressive ACCEPT list.** If ACCEPT changes compose to a meaning shift, flag the composition in the chat summary and ask the user to re-read the diff. ACCEPT is for single-line mechanical edits; accumulated across a section, even small edits can change intent.
- **Cross-file proposal explosion.** When strategic and tactical disagree, do not auto-sync. Emit DISCUSS proposals per file and let the user decide which is source-of-truth.
