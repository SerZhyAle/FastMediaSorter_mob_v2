# Phase 03 - Docs-catalog-cleanup

**Strategic spec:** [`../S0315_rule-prompt-drift-audit.md`](../S0315_rule-prompt-drift-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Document the six executable-mismatch categories and the record grammar next to the owning script, link them from `scripts/doc-drift/README.md`, and close the dev-log bookkeeping; no detection logic changes.

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done.
- [x] `scripts/doc-drift/check-rule-prompt-drift.ps1 -DryRun` exits 0.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/RULE_PROMPT_DRIFT.md` | New | ≤ 160 |
| `scripts/doc-drift/README.md` | Modified | ≤ 90 |

> No `docs/FEATURES*.md` update - strategic §8 says "Без изменений" (internal DX tooling). No `.kt`, no XML, no flavor source set - landscape-parity and flavor-isolation rules are N/A.

---

## Steps

### Step 03.1 - Document categories and record grammar next to the script

**Files:** `scripts/doc-drift/RULE_PROMPT_DRIFT.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the owning-script doc. Include: purpose (executable mismatch between `CLAUDE.md`, prompt skills, agent profiles, `AGENTS.md`, copilot instructions, workflow docs, and on-disk scripts - never prose or style); the canonical source (`CLAUDE.md`) and the manifest-declared surfaces; one short subsection per mismatch kind (`MissingDocumentedScript`, `ConflictingRouteName`, `MissingNoProfile`, `AbolishedArtifactReference`, `StaleCommandExample`, `OutdatedValidationRule`), each stating what it flags and one concrete evidence example; the output grammar line `<MISMATCHKIND> | <sourceA> | <sourceB> | expected: <X> | actual: <Y>` and the `SUMMARY` line; exit codes 0/1/2; the quick-start invocations (`-DryRun`, `-Json`, plain); the explicit out-of-scope note (style/tone/wording/prose) and the reuse boundary (version-pin drift -> `check-doc-vs-gradle.ps1`; spec-id-marker drift -> `spec_catalog/drift-check.ps1`; S0278/S0279 own doc-vs-gradle wear/PR-gate scope). Author style: `..` not `...`.

**Verification:**

- `Glob` - `scripts/doc-drift/RULE_PROMPT_DRIFT.md` exists.
- `Grep` - all six tokens `MissingDocumentedScript`, `ConflictingRouteName`, `MissingNoProfile`, `AbolishedArtifactReference`, `StaleCommandExample`, `OutdatedValidationRule` each match in the file.
- `Grep` - `check-doc-vs-gradle.ps1` matches (reuse boundary documented).
- `Grep` - `spec_catalog/drift-check.ps1` matches.
- `Grep` - `S0278` and `S0279` both match (adjacent-scope boundary documented).
- `Grep` - `Exit codes` or `exit code` matches in the file.
- `Grep` - `\.\.\.` returns zero hits (author style: `..` not `...`). | expected: 0 | actual: record on run.

**Status:** `[x]` done

---

### Step 03.2 - Link the new audit from the doc-drift README

**Files:** `scripts/doc-drift/README.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Append a short section to `scripts/doc-drift/README.md` titled "Rule/prompt executable drift (S0315)" with a one-line purpose, the quick-start `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1` invocation, a link to `RULE_PROMPT_DRIFT.md`, and an explicit "scope: executable mismatch only; version-pin drift stays in `check-doc-vs-gradle.ps1`" line so the two checkers in this directory are not confused. Do not alter the existing S0271 sections.

**Verification:**

- `Grep` - `check-rule-prompt-drift.ps1` matches inside `scripts/doc-drift/README.md`.
- `Grep` - `RULE_PROMPT_DRIFT.md` matches inside `scripts/doc-drift/README.md`.
- `Grep` - `S0315` matches inside `scripts/doc-drift/README.md`.
- `Grep` - `check-doc-vs-gradle.ps1` still matches inside `scripts/doc-drift/README.md` (existing S0271 content preserved). | expected: present | actual: record on run.
- `Grep` - `-NoProfile` matches in the appended quick-start line.

**Status:** `[x]` done

---

### Step 03.3 - Close dev-log bookkeeping for all created and modified files

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1` - never edited directly)
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Append one `dev/CHANGELOG.md` entry per file created or modified across all three phases that does not already have one, via `.\scripts\add_to_dev_log.ps1 "<path>" "spec-tech" "<english description>"`. Covers `sources.psd1`, `RulePromptRecord.ps1`, `RulePromptSources.ps1`, `RulePromptDetectors.ps1`, `RulePromptOutput.ps1`, `check-rule-prompt-drift.ps1`, `RULE_PROMPT_DRIFT.md`, and the `README.md` edit. Do not edit `dev/CHANGELOG.md` by hand. Do not run `scan.ps1`/`render.ps1`/`catalog_sync.ps1` - no `.kt` file is touched.

**Verification:**

- `Grep` - `check-rule-prompt-drift.ps1` matches in `dev/CHANGELOG.md`. | expected: present | actual: record on run.
- `Grep` - `RulePromptDetectors.ps1` matches in `dev/CHANGELOG.md`.
- `Grep` - `RULE_PROMPT_DRIFT.md` matches in `dev/CHANGELOG.md`.
- Manual: `dev/CATALOG/app_v2.jsonl` is unchanged versus HEAD (no Kotlin touched). | expected: unchanged | actual: record on run.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `scripts/doc-drift/RULE_PROMPT_DRIFT.md` exists and enumerates all six mismatch kinds plus exit codes plus the reuse/adjacent-scope boundary.
- [x] `scripts/doc-drift/README.md` links the new audit and preserves the existing S0271 content.
- [x] `dev/CHANGELOG.md` has one entry per created/modified file from all three phases.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged (strategic §8 internal tooling).
- [x] `dev/CATALOG/app_v2.jsonl` unchanged - no Kotlin file touched.
- [x] `Grep` for `TODO(phase-03)` across `scripts/doc-drift/` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this phase, run `/spec-check S0315`; the audit is a standalone manual / agent-invoked tool. Wiring it into post-change or a commit/push helper is strategic S0311 research item §6.3 (Open) and belongs to a separate ticket, not S0315.

---

## Rollback Plan

Revert the phase commit(s) - documentation-only changes plus dev-log entries; no executable behavior changed.
