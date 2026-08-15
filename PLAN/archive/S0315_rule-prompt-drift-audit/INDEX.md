# Tactical Plan: S0315 - rule-prompt-drift-audit

**Strategic spec:** [`../S0315_rule-prompt-drift-audit.md`](../S0315_rule-prompt-drift-audit.md)
**Feature:** Executable drift audit between repo rules, prompt skills, agent profiles, workflow docs, and on-disk scripts
**Tier:** 3 - Moderate, ad-hoc
**Priority:** 60
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-31

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec.

---

## Tooling contract (applies to every phase)

This ticket is PowerShell DX tooling, not a Kotlin feature. No `.kt`, no XML, no flavor source set is touched. The Timber / `Log.d` / landscape-layout / flavor-isolation rules are N/A. Every script created or edited here honors the shared S0311 §5.1 contract:

- `-NoProfile`-safe: no dependency on the user's PowerShell profile.
- Stable, documented exit codes declared in the script `.DESCRIPTION` header.
- `-DryRun` where meaningful: prints the resolved source set and category list, exits 0, writes no `temp/` artifact.
- Optional machine output: `-Json` emits a single JSON object on stdout, all human noise suppressed.
- Human summary: a concise verdict line when `-Json` is absent.
- Expected-vs-actual: every mismatch record names the expected value and the actual value.
- Artifacts (JSON report, evidence dump) land under `temp/` only.

Per-phase build gate substitution: where the Kotlin template demands a gradle `/build`, this plan substitutes "script parses with `-NoProfile` and `-DryRun` exits 0". No gradle build closes any step in this ticket.

ADR-1 (strategic §9): the audit reports executable conflicts only - never prose or style drift. It does not introduce a competing entrypoint. It reuses the existing `scripts/doc-drift/` architecture (dot-sourced library modules, `Output.ps1`-style record grammar) and lives as a sibling script in that directory. It delegates version-pin drift to `scripts/check-doc-vs-gradle.ps1` and spec-id-marker drift to `scripts/spec_catalog/drift-check.ps1` - it re-implements neither.

Scope boundary vs adjacent tickets (strategic §3.3 Related tickets): S0278 (drift_checker_wear_coverage) and S0279 (drift_checker_pr_gate_wiring) concern the existing doc-vs-gradle checker. S0315 must not fork, restore, or absorb their scope. The rule/prompt audit layers on top of the existing drift family; it owns rule-vs-prompt-vs-script executable mismatch only and leaves version-pin and PR-gate concerns to those tickets.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | sources-and-record-contract | - | ✅ Done | 4 | [PHASE_01__sources-and-record-contract.md](PHASE_01__sources-and-record-contract.md) |
| 02 | mismatch-detectors-and-report | 01 | ✅ Done | 6 | [PHASE_02__mismatch-detectors-and-report.md](PHASE_02__mismatch-detectors-and-report.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase 02 depends on the source-discovery and record contract defined in Phase 01. Phase 03 is the final docs-catalog-cleanup phase and depends on both.

---

## Pre-Implementation Blockers

Every strategic §6 research item with `Status: Open` is an unchecked box below. Phase 01 must not start while any box is unchecked.

- [x] **Research (strategic §6.1) - Rule drift baseline.** Decide which rule sources are canonical for the audit and how conflicts between them are reported, without turning the audit into a noisy style checker. Default carried by this plan: **`CLAUDE.md` is the canonical rule authority**; `.claude/commands/*.md` (skill prompts), `.claude/agents/*.md` (agent profiles), `AGENTS.md`, `.github/copilot-instructions.md`, and named workflow docs (`dev/AGENT_WORKFLOW.md`, `dev/PROJECT_OPERATIONS_INDEX.md`) are comparison surfaces audited against it and against the on-disk script reality. The source set is declared as data in a manifest (`sources.psd1`), so it is extended without code change. Detection is restricted to executable mismatch categories enumerated in Phase 02; prose, wording, and tone differences are out of scope by construction. Unblock by recording the owner decision (or the agent's finalized default under strategic §0 autonomy rule) in the Blockers Log.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged - S0315 is internal DX tooling with no user-visible capability (strategic §8).
- [ ] `dev/CHANGELOG.md` has a dev-log entry for every created or modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` unchanged - no Kotlin file is touched.
- [ ] `scripts/doc-drift/check-rule-prompt-drift.ps1` exists, runs `-NoProfile`, and `-DryRun` exits 0.
- [ ] The audit reports executable mismatch categories only; a `-DryRun` run prints the category list and contains no prose/style category.
- [ ] The record grammar and category list are documented next to the owning script and linked from `scripts/doc-drift/README.md`.
- [ ] `/spec-check S0315` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0315`.

---

## Blockers Log

- 2026-05-31 - Pre-Implementation Blocker open: strategic §6.1 rule drift baseline unresolved. Plan default is `CLAUDE.md`-canonical with skills/agents/AGENTS.md/workflow docs as comparison surfaces, executable-mismatch-only, source set declared in a manifest.
- 2026-05-31 - RESOLVED: owner accepted the default (`CLAUDE.md`-canonical, executable-mismatch-only, `sources.psd1` manifest) under strategic §0 autonomy rule. Phase 01 unblocked.

---

## Change Log

- 2026-05-31 - Initial tactical plan authored by `/spec-tech`.
