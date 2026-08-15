# Tactical Plan: S0448 - photos-flavor-exposes-network-sources

**Strategic spec:** [`../S0448_photos-flavor-exposes-network-sources.md`](../S0448_photos-flavor-exposes-network-sources.md)
**Research inputs:** [`research/01__lite-existing-network-records.md`](research/01__lite-existing-network-records.md), [`research/02__local-network-permission-gating.md`](research/02__local-network-permission-gating.md)
**Feature:** Flavor-gated local-network sources (lite OFF)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | network-availability-foundation | - | ✅ Done | 4/4 | [PHASE_01__network-availability-foundation.md](PHASE_01__network-availability-foundation.md) |
| 02 | welcome-permission-gating | 01 | ✅ Done | 4/4 | [PHASE_02__welcome-permission-gating.md](PHASE_02__welcome-permission-gating.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 items are Resolved (see Research inputs). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES change for `lite`).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0448` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0448`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
