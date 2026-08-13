# Tactical Plan: S0381 - neuroslop-hygiene-hardening

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Feature:** Neuroslop hygiene hardening
**Tier:** 3 - Moderate
**Priority:** 70
**Status:** In Progress
**Phases:** 4 / 6 done
**Last updated:** 2026-06-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | log-hygiene | - | ✅ Done | 3/3 | [PHASE_01__log-hygiene.md](PHASE_01__log-hygiene.md) |
| 02 | verification-debt | 01 | ⬜ Not started | 0/3 | [PHASE_02__verification-debt.md](PHASE_02__verification-debt.md) |
| 03 | flavor-containment | 01 | ✅ Done (pilot) | 2/3 | [PHASE_03__flavor-containment.md](PHASE_03__flavor-containment.md) |
| 04 | canonical-doc-sync | 01 | ✅ Done | 3/3 | [PHASE_04__canonical-doc-sync.md](PHASE_04__canonical-doc-sync.md) |
| 05 | hotspot-simplification | 01, 03 | ✅ Done (measurement) | 1/3 | [PHASE_05__hotspot-simplification.md](PHASE_05__hotspot-simplification.md) |
| 06 | docs-catalog-cleanup | 01, 02, 03, 04, 05 | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (owner decisions 2026-06-07; §6.2 resolved by existing CLAUDE.md rule). No open blockers.

- [x] **Research:** First-wave boundary - full scope, all five pillars. Strategic §6.1.
- [x] **Research:** Ticket-id policy for permanent logs - absolute zero outside probes. Strategic §6.2.
- [x] **Research:** Verification-debt execution pace - single `/spec-sweep` of all 49. Strategic §6.3.
- [x] **Research:** Hotspot-ready criterion - responsibility-based selection. Strategic §6.4.
- [x] **Research:** Doc-sync automation level - generated from build config. Strategic §6.5.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0381` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0381`.

---

## Blockers Log

- 2026-06-07 - Tactical plan authored.
- 2026-06-07 - All §6 blockers cleared after owner decisions; implementation unblocked.

---

## Change Log

- 2026-06-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-07 - Reconciled with owner §6 decisions: Phase 02 → single full sweep of all 49; Phase 04 → generated doc versions (removed fabricated Kotlin/Room version facts); Phase 05 → refactor target selected from measurement output, not pre-named.
