в# Specification Implementation Audit

Audit a specification against the actual state of the repository. The skill auto-detects whether it is auditing:

- A **strategic** spec — qualitative check: goals are addressed somewhere in tactical phases, research items are resolved, user-facing text lands in FEATURES docs, cross-references are consistent.
- A **tactical** spec (folder with phases) — machine-verifiable check of every step's Verification signal and every phase's Done Criteria, aggregated into overall progress.

Runs after the developer claims implementation is complete, or on demand for a health snapshot.

## Usage

```text
/spec-check <short-name>                     # auto-detect mode: audits strategic + every tactical phase if present
/spec-check <short-name> --strategic         # strategic audit only (ignore tactical folder)
/spec-check <short-name> --tactical          # tactical audit only (every phase)
/spec-check <short-name> --phase <NN>        # tactical audit of one phase
/spec-check <short-name> --phases <01,03,05> # tactical audit of a subset of phases
/spec-check <short-name> --strict            # treat WARN as FAIL
/spec-check <short-name> --quick             # skip grep-heavy invariants
```

Examples:

- `/spec-check player-keybinding-remapping`
- `/spec-check background-thumbnail-preload --tactical --strict`
- `/spec-check vr-hand-tracking --phase 03`

Spec location contract:

- Strategic: `PLAN/spec_<short-name>.md`.
- Tactical: `PLAN/spec_<short-name>/INDEX.md` + `PHASE_NN__*.md`.
- Report: `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md` (suffix `_2`, `_3`, .. if multiple runs on the same date).

---

## Auto-detection

At Step 1 the skill determines the audit mode:

| Strategic file | Tactical folder | Flag | Mode |
|:--------------:|:---------------:|------|------|
| exists | exists | none | **full** — strategic + every phase (default) |
| exists | missing | none | strategic only |
| exists | exists | `--strategic` | strategic only |
| exists | exists | `--tactical` | every phase, no strategic checks |
| exists | exists | `--phase NN` | single phase, no strategic checks |
| exists | exists | `--phases A,B,..` | subset of phases, no strategic checks |
| missing | any | any | abort — spec does not exist |
| exists | `--phase NN` but no folder | — | abort — tactical folder not created yet; run `/spec-tech` first |

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse arguments and locate the spec.**

- Extract `<short-name>` and flags.
- Check if `PLAN/spec_<short-name>.md` exists — abort if not.
- Check if `PLAN/spec_<short-name>/INDEX.md` exists — record presence for mode decision.
- Apply the auto-detection table above to pick the mode.
- Record today's date for the report filename.

**Step 2 — Extract the verification contract(s).**

For strategic mode, parse `PLAN/spec_<short-name>.md` and extract:

- §2 Goals (qualitative — each goal must be referenced in at least one tactical phase or resolved in the strategic itself).
- §3.2 Hard constraints (each constraint becomes a predicate: flavor coverage, Wear OS impact, accessibility requirement).
- §6 Research items with `Status: Open` (each becomes a WARN in the audit — unresolved research should not survive `Verified`).
- §8 User-facing feature text (becomes a `docs/FEATURES.md` + `_RU.md` + `_UK.md` trilingual check).
- §11 Completion criteria (each becomes a MANUAL signal — the skill cannot auto-tick these).

For tactical mode, parse `PLAN/spec_<short-name>/INDEX.md` and every targeted `PHASE_NN__*.md`:

- INDEX Phase Overview — phase statuses + step counters (cross-check against phase files).
- INDEX Pre-Implementation Blockers — every unchecked blocker is a WARN.
- INDEX Completion Gate — each item a discrete check.
- Phase `Files Touched` — each row: file exists (New or Modified) + line budget respected.
- Phase `Steps` — each step's `Verification` section becomes one or more predicates; `Status:` line is cross-checked against actual evidence.
- Phase `Phase Done Criteria` — each checkbox a discrete check.

If §15 is absent from a pre-existing strategic spec authored before `/spec-tech` split, fall back to §5–§13 of that old format and emit `WARN: spec predates tactical split — coverage degraded`.

**Step 3 — Run each check and record the outcome.**

For each check, produce one row with:

- **Status:** `PASS`, `WARN`, `FAIL`, `MANUAL`, `UNCHECKABLE`, `EXEMPT`.
- **Evidence:** clickable `file:line` reference, grep hit count, or short quoted excerpt.
- **Action:** empty for PASS / EXEMPT / MANUAL; concrete next step for WARN / FAIL (this feeds `/spec-fix`).

Verification mechanics — always use these tools, never shell wildcards:

| Check kind | How |
|-----------|-----|
| File exists | `Glob` with the exact path. |
| Class / function declared | `Grep` for `class <Name>` / `fun <name>` with `-n`; verify hit is on a declaration line, not a comment/string. |
| No forbidden call | `Grep` for the pattern (e.g. `Log\.d\(`) under the feature path; PASS iff zero hits. |
| String resource present | `Grep` for `name="<key>"` in each of `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`. |
| Room schema version | Read `app_v2/src/main/java/com/sza/fastmediasorter/data/db/AppDatabase.kt`, match `@Database(version = N`. |
| Dev log entry present | `Grep` for the file's relative path in `dev/CHANGELOG.md`. |
| Catalog up-to-date | `Grep` for the class name in `dev/CATALOG/<module>.jsonl`. |
| FEATURES trilingual | `Grep` for an identifying keyword in each of `docs/FEATURES.md`, `_RU.md`, `_UK.md`. PASS only if all three hit. |
| File size respects budget | `Read` the file once, count lines, compare to the step's budget. |
| Flavor gating | `Grep` for `BuildConfig.<FLAG>` in the relevant files if §3.2 or the step names a flag. |
| Step status consistency | Parse `Status: [x] done` in phase file; cross-check against Verification predicates. |
| Phase status consistency | INDEX row status == phase file `Status:` header. Mismatch = WARN. |
| Commit freshness (tactical) | `git log -- <file>` has at least one commit newer than the strategic spec's date. |

**Step 4 — Score the audit.**

Aggregate per section, then overall:

- `Verified` — every check is PASS, MANUAL, or EXEMPT. Zero WARN and zero FAIL.
- `Partial` — zero FAIL, at least one WARN. Collapses to `Broken` under `--strict`.
- `Broken` — at least one FAIL.

For tactical-only or phase-subset mode, the overall score applies to the audited surface only — the strategic spec `Status:` is not changed in those modes.

**Step 5 — Write the audit report** to `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md` using the template below.

**Step 6 — Update the strategic spec's `Status:` field** to the score (only in full or `--strategic` mode). Do not touch any other section of the strategic spec. If the spec is `Broken` or `Partial`, add one line under the status header:

```markdown
**Audit:** see `PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md`
```

In tactical mode (tactical-only, single or subset of phases), update the INDEX `Status:` + the audited phases' INDEX rows + each audited phase file's `Status:` header according to the per-phase score. Do **not** touch the strategic `Status:` from tactical-only mode.

**Step 7 — Run the dev log command** for every file modified (audit report + strategic spec + INDEX + phase files):

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>__audit_<YYYY-MM-DD>.md" "spec-check" "Audit <short-name>"
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec-check" "Advance status to <score>"
# Plus one line per updated phase file / INDEX.
```

**Step 8 — Summarise to the user** (Russian): overall score, counts of PASS / WARN / FAIL / MANUAL, the top three action items, and the path to the report. If any FAIL exists, recommend `/spec-fix <short-name>` as the next step.

---

## Audit Report Template

```markdown
# Spec Audit: <short-name>

**Strategic spec:** [`spec_<short-name>.md`](spec_<short-name>.md)
**Tactical plan:** [`spec_<short-name>/INDEX.md`](spec_<short-name>/INDEX.md) (or "— tactical not yet authored")
**Audit date:** <YYYY-MM-DD>
**Auditor:** `/spec-check`
**Mode:** full | strategic | tactical | phase-<NN> | phases-<A,B,..>
**Flags:** strict | quick | —
**Outcome:** Verified | Partial | Broken

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | N |
| PASS | N |
| WARN | N |
| FAIL | N |
| MANUAL (unverified) | N |
| EXEMPT | N |
| UNCHECKABLE | N |

<One-paragraph verdict. If Verified: confirm readiness to close. If Partial: name the top WARNs and whether they block release. If Broken: name the FAILs and state that the spec must not be closed until they resolve.>

---

## 2. Strategic Audit (if in scope)

### 2.1 Goals Coverage (strategic §2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | <goal text> | Phase 01, 04 | PASS | — |
| 2 | <goal text> | — | FAIL | "No phase implements this goal — add a phase or mark goal as deferred." |

### 2.2 Constraints (strategic §3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | Flavor: standard only | `BuildConfig.FEATURE_CLOUD` gate on new code | PASS | [File.kt:42](..) | — |

### 2.3 Open Research Items (strategic §6)

<Every §6 item with Status: Open → WARN entry here, requiring resolution before score becomes Verified.>

- **WARN** — §6.1 "A-axis activation threshold" still `Status: Open`. Resolve the research before marking the spec Verified.

### 2.4 User-Facing Text (strategic §8)

| Artefact | Required? | Status | Evidence | Action |
|---------|:---------:|:------:|----------|--------|
| `docs/FEATURES.md` bullet | Yes | PASS / FAIL | line N | — |
| `docs/FEATURES_RU.md` mirror | Yes | .. | .. | .. |
| `docs/FEATURES_UK.md` mirror | Yes | .. | .. | .. |

### 2.5 Completion Criteria (strategic §11)

<Each §11 criterion enters as MANUAL — unverified. The reviewer ticks them after acceptance testing.>

- [ ] <criterion 1>
- [ ] <criterion 2>

---

## 3. Tactical Audit (if in scope)

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| `Phases: X/N done` counter matches phase statuses | PASS / FAIL | 3/5 in INDEX vs actual 4/5 | "Bump counter to 4/5" |
| Every phase-file `Status:` header matches INDEX row | PASS / FAIL | .. | .. |
| Pre-Implementation Blockers all ticked | PASS / WARN | 1 unchecked | "Resolve blocker B1 before Phase 03 starts" |
| Completion Gate items ticked where applicable | PASS / WARN | 3/6 ticked | "Remaining 3 need manual review" |

### 3.2 Phase NN — <Title>

**Phase status:** ⬜ / 🚧 / ✅ / ⛔ (from phase file header)
**Outcome:** Verified | Partial | Broken

#### 3.2.1 Files Touched

| File | Expected | Exists? | Line count vs budget | Status | Evidence | Action |
|------|---------|:-------:|:--------------------:|:------:|----------|--------|
| path/to/New.kt | New, ≤ 250 | ✅ | 187 / 250 | PASS | [path/to/New.kt:1](..) | — |

#### 3.2.2 Steps

| # | Step | Status (claimed) | Verification | Outcome | Evidence | Action |
|---|------|:----------------:|--------------|:-------:|----------|--------|
| NN.1 | <title> | `[x] done` | grep `class NewClass` | PASS | [NewClass.kt:5](..) | — |
| NN.2 | <title> | `[x] done` | grep `@Inject` | FAIL | 0 hits | "Add `@Inject` to constructor" |
| NN.3 | <title> | `[ ] not done` | — | UNCHECKABLE | — | "Step not started, skip verification" |

#### 3.2.3 Phase Done Criteria

<Each checkbox in the phase file → one row here.>

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|
| All steps `[x] done` | FAIL | 4/5 done | "Complete Step NN.5" |
| Build passes (/build) | MANUAL | — | "Run /build and record outcome" |
| Dev log entry per file | FAIL | 2/3 logged | "Run add_to_dev_log.ps1 for path/File3.kt" |

<Repeat §3.2 for every audited phase.>

---

## 4. Cross-Reference Checks

<Consistency between strategic and tactical — only in full mode.>

- Goal §2.3 (strategic) ↔ Phase(s) implementing it — PASS / MISSING.
- ADR §9 (strategic) ↔ phases applying it — PASS / MISSING.
- Any phase uses a class name that strategic §5 forbids (strategic is class-free but may reference roles) — WARN if tactical invents roles not present in strategic.

---

## 5. Manual Acceptance Signals

<Enumerate strategic §11 + phase-level `[ ] Build passes`, `[ ] UI visual check` items. Each starts MANUAL — unverified. `/spec-check` never flips these to PASS automatically.>

- [ ] <signal 1 — from strategic §11>
- [ ] <signal 2 — from phase NN Done Criteria>

---

## 6. Accepted Exemptions

<Items the spec authors marked as deliberate deviations. Recorded as EXEMPT — do not count against the score.>

- <exemption> — justified by: <reason from spec>

---

## 7. Action Items (FAIL + WARN, prioritised)

<Numbered list. FAIL first, then WARN. Each item: origin (section/phase/step), concrete fix, files to touch. This list is the direct input to `/spec-fix`.>

1. **[FAIL § 3.2.2 — Step 02.3]** Missing `@Inject` on `FooManager` constructor at `path/Foo.kt` — add `@Inject` annotation.
2. **[FAIL § 3.2.3 — Phase 02]** Dev log missing for `path/File3.kt` — run `add_to_dev_log.ps1`.
3. **[WARN § 2.3]** Open research item §6.1 — resolve or mark deferred with ADR.

---

## 8. Recommended Follow-ups

<Broader issues surfaced during the audit, not tied to specific FAIL rows. Examples: "Catalog for app_v2 is stale across many files, not just this spec — schedule a full rescan.", "INDEX counter drift suggests the team isn't updating INDEX after phase completion — add a git hook.">

---

## 9. Next Commands

<Recommendations in order:>

- `/spec-fix <short-name>` — apply the trivial fixes from §7 (only if at least one FAIL is auto-fixable).
- `/spec-check <short-name>` — re-run to confirm Verified after fixes.
- `/spec-update <short-name>` — refine the spec if the audit exposed structural gaps in the spec itself, not just the implementation.
```

---

## Quality Rules

- **Never mutate spec content beyond `Status:` and the `**Audit:**` pointer line.** The spec is the contract; audits go into the audit file.
- **Strategic audit is qualitative.** Goals are phrased in prose — a goal is "covered" if any phase's Objective or Steps reference the same subject. Do not require literal string match; use keyword overlap.
- **Tactical audit is strict.** Every step Verification must be executable as a static predicate. Missing Verification = UNCHECKABLE, and UNCHECKABLE on a step claimed `[x] done` is a WARN.
- **A grep miss is a FAIL, not a WARN.** A hit-count mismatch (expected 1, found 3) is a WARN and must list every hit in evidence so the reviewer can judge.
- **Do not run `./gradlew` or any build command.** Audits are static analysis only. Delegate to `/build` if the user asks for a compile check.
- **Read-only zones** (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) are ignored. If a spec references them, record `WARN: reference crosses read-only zone`.
- **`--quick`** skips grep-heavy invariants (tactical §Files Touched `no forbidden call` checks, trilingual string grep) and annotates summary with "Quick mode — invariants skipped".
- **`--strict`** collapses `Partial` to `Broken` and records every WARN in §7 Action Items.
- **Never approve Verified** on a strategic spec if any phase in the tactical plan is Broken. Overall score = strongest failure across strategic + tactical.
- **The audit report is the single artefact produced by this skill.** Do not create secondary reports, summaries, or checklists elsewhere.
- **Report filename collision:** if `..._audit_<YYYY-MM-DD>.md` exists, append `_2`, `_3`, .. — never overwrite a prior audit.
- **Author style** in free-text (report intros, follow-ups): `..` not `...`. Russian only in chat summary; the audit report body is English to match the tactical spec language.
- **Step status cross-check is load-bearing.** If a step claims `[x] done` but Verification fails, this is the most actionable signal — escalate it to the top of §7 Action Items.

---

## Failure Modes to Watch

- **Over-eager PASS:** grepping for `class NewClass` matches a comment or string literal. Confirm hits are on declaration lines (start with `class`/`object`/`interface` after modifiers).
- **Trilingual drift:** EN doc updated, RU/UK missed. Must FAIL if strategic §8 is non-empty but only one file hits.
- **Implicit "implemented":** developer flipped `Status:` without committing. `git log -- <file>` shows no new commits — WARN (could be uncommitted WIP) and suggest staging before re-auditing.
- **Catalog drift:** new class exists in source but not in `dev/CATALOG/<module>.jsonl`. FAIL — the catalog is a hard project invariant.
- **Stale progress markers:** a phase is `🚧 In Progress` for > 14 days or a step is `[~] in progress` across multiple audits — WARN with "consider finishing or parking".
- **INDEX counter drift:** phase files show 5/5 steps done but INDEX row says "4/5" — auto-fixable, WARN with `/spec-fix` recommendation.
- **Stale strategic spec:** strategic Status is `Approved` but tactical folder exists — WARN, bump strategic to `Tactical`.
