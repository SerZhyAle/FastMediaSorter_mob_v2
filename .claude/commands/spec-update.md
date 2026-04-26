# Specification Refinement

Review and refine a strategic or tactical spec in-place. Modifies spec files — not the codebase (`/spec-fix` does that).

## Usage

```text
/spec-update <short-name>                      # strategic spec
/spec-update <short-name> --tactical           # INDEX + every phase file
/spec-update <short-name> --tactical --phase 03
/spec-update <short-name> --index
/spec-update <short-name> --review-only        # analyse only, no writes
/spec-update <short-name> --apply-all          # same as default; DISCUSS still never auto-applied
/spec-update <short-name> --focus <aspect>     # language|structure|verifiability|consistency|completeness|style
```

Targets:

- Strategic: `PLAN/spec_<short-name>.md`
- Tactical INDEX: `PLAN/spec_<short-name>/INDEX.md`
- Tactical phase: `PLAN/spec_<short-name>/PHASE_NN__*.md`

---

## Review Focus Areas

### 1. `language`

- Strategic body: Russian. Tactical body: English. No mixed sentences.
- `..` not `...`; `ё`/`Ё` in Russian where grammatically correct.

### 2. `structure`

- All mandatory template sections present, in order.
- Strategic: no class names, file paths, line budgets, Room versions, Hilt modules.
- Tactical: no rationale prose (lives in strategic).

### 3. `verifiability`

- Every tactical step has a `Verification:` block with ≥1 static predicate (Glob/Grep/value equality/file-size).
- No untestable phrasing ("works correctly", "behaves as expected").
- Every phase has Phase Done Criteria with ≥3 invariants.
- Strategic §11: observable outcomes only.

### 4. `consistency`

- Strategic `Status:` ↔ tactical INDEX `Status:` aligned.
- INDEX phase counter matches phase-file `Status:` headers.
- Every tactical phase links to strategic spec at top.
- `Depends on` values reference existing earlier steps.

### 5. `completeness`

- Every strategic §2 Goal maps to ≥1 tactical phase (or marked deferred).
- Every open §6 Research item appears in INDEX "Pre-Implementation Blockers".
- Every §9 ADR applied somewhere in tactical.

### 6. `style`

- Markdown well-formed: tables have header+separator, lists have blank lines, fences have language tags.
- No TODO/FIXME markers. No "TBD" — either resolved or promoted to §6.

---

## Process

**1 — Parse arguments, locate target.**

Resolve target file(s) from flags. Abort if target does not exist (suggest `/spec` or `/spec-tech`).

**1a — Status gate.**

| Strategic `Status:` | Allowed? |
| --- | :---: |
| `Draft` | ✅ |
| `Approved` | ✅ |
| `Tactical` | ✅ |
| `In Progress` | ⛔ spec locked for execution |
| `Implemented` / `Verified` / `Partial` / `Broken` | ⛔ historical record |

If locked: abort. Offer: (1) new spec `/spec <id> <name>-v2`, (2) wait until closed.
Flag `--force-locked` overrides — record override reason in Revision History.

**2 — Review pass.**

Read target(s). For each focus area (all six, or `--focus` selection), produce observations:

- **Finding, Location, Severity** (`Critical`/`Major`/`Minor`/`Nitpick`), **Evidence** (quoted excerpt), **Classification**.

Classification rules:

| Kind of change | Classification |
| --- | --- |
| Typo, `...`→`..`, missing `ё`, blank lines around list/fence | ACCEPT |
| Missing mandatory section skeleton | ACCEPT |
| Rewording ambiguous `Verification:` predicate (obvious fix) | ACCEPT |
| Removing class names/paths from strategic spec | ACCEPT |
| Adding/removing/merging a step or phase | DISCUSS |
| Renaming a class in tactical step | DISCUSS |
| Demoting strategic↔tactical content | DISCUSS |
| Adding/editing an ADR | DISCUSS |

**3 — Apply pass** (skip if `--review-only`).

- **ACCEPT** — apply via `Edit`. Log in Revision History.
- **DISCUSS** — record in "Proposed Structural Changes" block with `Status: Proposed`. Never apply regardless of `--apply-all`.

Edits are minimal and localized. Never renumber steps/phases unless that is the specific finding.

**4 — Maintain Revision History block.**

Append to target file bottom (create section if absent):

```markdown
## Revision History

- **<YYYY-MM-DD>** — by `/spec-update` (`<model-id>`, focus: <aspects>)
  - Applied: N. Proposed (DISCUSS): N.
```

DISCUSS items append under:

```markdown
## Proposed Structural Changes

### Proposal P-<N> — <title>  (proposed <YYYY-MM-DD> by <model-id>)

**Status:** Proposed
**Affected:** <section>
**Rationale:** <why>
**Suggested edit:**
> <before> → <after>
```

Proposals are never removed. Accept → flip `Status: Accepted` and apply. Reject → flip to `Rejected`.

**5 — Cross-file checks.**

If strategic target and tactical folder both exist: run `consistency` focus between them. Edits to the other file are DISCUSS only.

**6 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-update" "Refinement (<model-id>, focus: <aspects>)"
# one line per tactical file if touched
```

**Chat output:** `Applied N, proposed N DISCUSS. Clean: [aspects].`

---

## Constraints

- Never invent translations. Missing RU/UK → `<!-- TODO translate: <EN> -->` or DISCUSS.
- Never renumber steps/phases — cascades into all references.
- Never touch `Status:` fields — only `/spec-check` moves those.
- Class names/file paths in strategic specs: auto-fix via ACCEPT (replace with architectural term).
- Tactical steps with non-static Verification: ACCEPT with Glob/Grep template if obvious; otherwise DISCUSS.
- Read-only zones never edited: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- `--review-only`: no writes, no dev log.
- Revision History is append-only — never rewrite earlier entries.
