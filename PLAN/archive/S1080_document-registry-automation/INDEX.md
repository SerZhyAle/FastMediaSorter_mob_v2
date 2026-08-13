# Tactical Plan: S1080 - document-registry-automation

**Strategic spec:** [`../S1080_document-registry-automation.md`](../S1080_document-registry-automation.md)
**Feature:** Document registry and freshness automation
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | registry-foundation | - | ✅ Done | 2/2 | [PHASE_01__registry-foundation.md](PHASE_01__registry-foundation.md) |
| 02 | tooling-generation | 01 | ✅ Done | 2/2 | [PHASE_02__tooling-generation.md](PHASE_02__tooling-generation.md) |
| 03 | workflow-integration | 02 | ✅ Done | 2/2 | [PHASE_03__workflow-integration.md](PHASE_03__workflow-integration.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None.

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- [ ] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- [ ] `/spec-check S1080` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to a matching `Block*` value.
5. All done: flip `Status:` to `Done`, run `/spec-check S1080`.

## Blockers Log

- None.

## Change Log

- 2026-07-17 - Initial tactical plan authored by `/spec-tech`.
