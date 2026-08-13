# Tactical Plan: S0551 - maestro-regression-flow-library

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Research inputs:** [`research/02__oracle-markers-and-locators.md`](research/02__oracle-markers-and-locators.md), [`research/03__existing-flows-and-runner.md`](research/03__existing-flows-and-runner.md), [`research/06__doc-cleanup.md`](research/06__doc-cleanup.md)
**Feature:** Revive the root `maestro/` capability regression suite (off-context runner, real oracles, core matrix coverage, prerelease integration)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Verified
**Phases:** 7 / 7 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec. No app-runtime code - test infrastructure only (YAML flows, PowerShell runner, skill/script integration, docs).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | runner-oracle-foundation | - | ✅ Done | 4/4 | [PHASE_01__runner-oracle-foundation.md](PHASE_01__runner-oracle-foundation.md) |
| 02 | browse-navigation-flows | 01 | ✅ Done | 4/4 | [PHASE_02__browse-navigation-flows.md](PHASE_02__browse-navigation-flows.md) |
| 03 | file-operations-flows | 01 | ✅ Done (device-only) | 5/5 | [PHASE_03__file-operations-flows.md](PHASE_03__file-operations-flows.md) |
| 04 | player-family-flows | 01 | ✅ Done | 5/5 | [PHASE_04__player-family-flows.md](PHASE_04__player-family-flows.md) |
| 05 | slideshow-edge-resume-info | 01 | ✅ Done | 4/4 | [PHASE_05__slideshow-edge-resume-info.md](PHASE_05__slideshow-edge-resume-info.md) |
| 06 | prerelease-integration | 01, 02, 03, 04, 05 | ✅ Done | 3/3 | [PHASE_06__prerelease-integration.md](PHASE_06__prerelease-integration.md) |
| 07 | docs-catalog-cleanup | 02, 03, 04, 05 | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see strategic spec §6 + `research/`). No open blockers - Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений", test infra).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` - skip (no app-runtime API change).
- [ ] Full suite runs green on a clean seeded emulator via the revived runner (one PASS/FAIL line).
- [ ] `/spec-check S0551` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0551`.

---

## Blockers Log

- Device proof pending: run `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -Json` on a clean OWNER_TRIGGER-seeded standard-debug emulator. File-operation flows are now explicit device-only because current emulator import exposes `Ops/*` files on disk but not as a reliable writable file-operation resource.
- Device proof 2026-06-22 (initial): `-Suite all` returned `pass=false,total=16,failed=10` (player locators, seeded-media names, stylus input, restored scroll position, 3D media). Resolved this cycle - see strategic spec `## Last Audit`.
- Device proof 2026-06-22 (final): `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -DeviceId emulator-5554 -Json` returned `pass=true,total=14,failed=0`. All emulator-default flows green; `features/files`, `critical/file_operations.yaml`, and `device_only/3d-video-*` are explicitly device-only and excluded from `all`.

---

## Change Log

- 2026-06-20 - Initial tactical plan authored by `/spec-tech` (7 phases; engine decision: revive root `maestro/`, keep S0420 per-ticket engine separate).
