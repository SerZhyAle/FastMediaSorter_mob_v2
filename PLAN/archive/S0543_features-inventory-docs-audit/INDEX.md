# Tactical Plan: S0543 - features-inventory-docs-audit

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Research inputs:** none (recon folded into strategic §1/§4)
**Feature:** ALL_FEATURES + FEATURES + docs/site reconciled to the real app surface and kept in sync by skill duties + a consistency gate
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 7 / 7 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Code scanning is READ-ONLY - no app build, no device. Only docs/inventory/skill files change. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | surface-extraction | - | ✅ Done | 4/4 | [PHASE_01__surface-extraction.md](PHASE_01__surface-extraction.md) |
| 02 | inventory-audit | 01 | ✅ Done | 5/5 | [PHASE_02__inventory-audit.md](PHASE_02__inventory-audit.md) |
| 03 | showcase-audit | 01, 02 | ✅ Done | 4/4 | [PHASE_03__showcase-audit.md](PHASE_03__showcase-audit.md) |
| 04 | dev-skill-mandate | - | ✅ Done | 3/3 | [PHASE_04__dev-skill-mandate.md](PHASE_04__dev-skill-mandate.md) |
| 05 | release-skill-mandate | - | ✅ Done | 3/3 | [PHASE_05__release-skill-mandate.md](PHASE_05__release-skill-mandate.md) |
| 06 | docs-site-audit | 03 | ✅ Done | 5/5 | [PHASE_06__docs-site-audit.md](PHASE_06__docs-site-audit.md) |
| 07 | synthesis-gate | all | ✅ Done | 3/3 | [PHASE_07__synthesis-gate.md](PHASE_07__synthesis-gate.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Execution note: Phases 04 and 05 are dependency-free process edits (cheap, fast) and may run first. Phases 02/03/06 are the read-heavy audits - parallelizable across agents and passes.

---

## Pre-Implementation Blockers

Strategic §6 has 2 Open research items, both resolvable inside the plan (Phase 07 step 1). No external blockers.

S0440 seam: settings documentation, the future `SETTINGS_REFERENCE*` page and settings rows in `DOCS_MAP`/`FEATURES*` are S0440-owned. This plan only inventories/flags them - never rewrites.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `scripts/all_features/validate.ps1` exits 0.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` reconciled with the inventory; flavor labels correct; parity holds.
- [ ] Dev/release skills mandate inventory + showcase population (incl. `removed` on archival).
- [ ] `dev/CHANGELOG.md` has an entry for every logical change.
- [ ] `/spec-check S0543` returns `Verified`.

---

## How to Track Progress

1. Before a phase: flip row to `🚧 In Progress`, update `Phases: X/N done`.
2. During: flip step `[~]` when started, `[x]` when Verification passes.
3. On completion: confirm steps `[x]`, confirm Phase Done Criteria, flip row `✅ Done`, bump counter.
4. Blocked: flip `⛔ Blocked`, add to Blockers Log.
5. All done: flip `Status: Done`, run `/spec-check S0543`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-all` (F2).
