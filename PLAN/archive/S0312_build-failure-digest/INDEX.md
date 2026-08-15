# Tactical Plan: S0312 - build-failure-digest

**Strategic spec:** [`../S0312_build-failure-digest.md`](../S0312_build-failure-digest.md)
**Feature:** Structured build/lint failure digest for agents
**Tier:** 3 - Moderate, ad-hoc
**Priority:** 75
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-31

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Tooling contract (applies to every phase)

This ticket is PowerShell DX tooling, not a Kotlin feature. No `.kt`, no XML, no flavor source set is touched. The Timber / `Log.d` / landscape-layout / flavor-isolation rules are N/A. Every script created or edited here must honor the shared S0311 §5.1 contract:

- `-NoProfile`-safe: no dependency on the user's PowerShell profile.
- Stable, documented exit codes (declared in the script's `.DESCRIPTION` header).
- `-DryRun` where meaningful: prints the resolved plan and exits 0 without running a build or writing a non-temp artifact.
- Optional machine output: `-Json` emits a single JSON object on stdout, all human noise suppressed.
- Human summary: a concise verdict line when `-Json` is absent.
- Artifacts (raw logs, digest JSON) land under `temp/` only.

Per-phase build gate substitution: where the Kotlin template demands a gradle `/build`, this plan substitutes "script parses with `-NoProfile` and `-DryRun` (or equivalent dry path) exits 0". No gradle build is required to close any step in this ticket.

ADR-1 (strategic §9): the digest delegates failure-block extraction to the existing `scripts/builders/get-last-build-failure.ps1` (`a.ps1 bf`). It does not re-implement `FAILURE:`-block scanning. The new layer adds structure (parse to fields + JSON + verdict), it does not duplicate the scan.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | digest-contract-parser | - | ✅ Done | 3/3 | [PHASE_01__digest-contract-parser.md](PHASE_01__digest-contract-parser.md) |
| 02 | one-shot-digest-command | 01 | ✅ Done | 4/4 | [PHASE_02__one-shot-digest-command.md](PHASE_02__one-shot-digest-command.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase 02 depends on the data contract and parser defined in Phase 01. Phase 03 is the final docs-catalog-cleanup phase and depends on both.

---

## Pre-Implementation Blockers

Every strategic §6 research item with `Status: Open` is an unchecked box below. Phase 01 must not start while any box is unchecked.

- [x] **Research (strategic §6.1) - Build feedback trigger model.** Decide whether S0312 ships one-shot only, one-shot plus an explicit watcher with timeout, or IDE-task integration. Resolution must confirm that continuous watch does not conflict with harness session-cleanup rules. Default carried by this plan: **one-shot only** (canonical per strategic §3 and §11.2); any watcher work stays deferred to a follow-up ticket and is not implemented here. Unblock by recording the owner decision (or the agent's finalized default under the §0 autonomy rule) in the Blockers Log.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged - S0312 is internal DX tooling with no user-visible capability (strategic §8). Verified: `git diff --name-only` on the three files is empty.
- [ ] `dev/CHANGELOG.md` has a dev-log entry for every created or modified file. (Deferred to central operator closure - not written by `/spec-dev` execution per task HARD PROHIBITIONS.)
- [x] `dev/CATALOG/app_v2.jsonl` unchanged - no Kotlin file is touched. Verified: `git diff --name-only` on it is empty.
- [x] `scripts/builders/build-failure-digest.ps1` exists, runs `-NoProfile`, and `-DryRun` exits 0 (actual exit 0 recorded in Phase 02).
- [x] The JSON schema is documented next to the owning script (`build-failure-digest.SCHEMA.md`) and referenced from `scripts/builders/README.md`.
- [ ] `/spec-check S0312` returns `Verified`. (Operator-run.)
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`. (Operator-run.)

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0312`.

---

## Blockers Log

- 2026-05-31 - Pre-Implementation Blocker open: strategic §6.1 trigger model unresolved. Plan default is one-shot only; watcher deferred.
- 2026-05-31 - RESOLVED: owner accepted the default (one-shot only; watcher deferred to a follow-up ticket) under strategic §0 autonomy rule. Phase 01 unblocked.

---

## Change Log

- 2026-05-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-31 - All three phases implemented by `/spec-dev` execution. Files: `scripts/builders/build-failure-digest.contract.ps1` (new), `scripts/builders/build-failure-digest.ps1` (new), `scripts/builders/build-failure-digest.SCHEMA.md` (new), `a.ps1` (+`bfd` alias, 2 help lines), `scripts/README.md` (+`a bfd` line), `scripts/builders/README.md` (failure-diagnostics note extended). Every step's Verification predicates run and recorded `expected | actual`. Per-phase dry-path gates exit 0.
- 2026-05-31 - Contract note (Step 02.4): the Verification's first OR-branch (`a.ps1 bfd -DryRun` forwards `-DryRun` and exits 0) is not achievable because `a.ps1` declares only `param([string]$Command)` and forwards no extra args to any target script - a pre-existing launcher design, not specific to `bfd`. The second OR-branch was taken and recorded (no-args/help lists `bfd`, exit 1; plus `a.ps1 bfd` resolves and runs the digest). Re-architecting `a.ps1` arg-forwarding was out of scope ("Do not change any other command mapping"; the launcher change was limited to one alias line).
- 2026-05-31 - Deferred to central operator closure (excluded from `/spec-dev` execution by the run's HARD PROHIBITIONS): `scripts/post-change.ps1` dev-log entries for all created/modified files; `scripts/add_to_functionality_log.ps1 -Id S0312 -Op ADD`; spec-catalog status transition (`update.ps1`); `/spec-check S0312`. The three dependent Verification greps in Step 03.3 (`dev/CHANGELOG.md` x2, functionality-log S0312 line) remain for the operator.
