# Tactical Plan: S0526 - widget-dictaphone-resource-destination

**Strategic spec:** [`../S0526_widget-dictaphone-resource-destination.md`](../S0526_widget-dictaphone-resource-destination.md)
**Research inputs:** none
**Feature:** Widget dictaphone saves to the configured mic destination
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 3 / 3 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-mic-saver | - | ✅ Done | 3/3 | [PHASE_01__shared-mic-saver.md](PHASE_01__shared-mic-saver.md) |
| 02 | widget-delegation | 01 | ✅ Done | 4/4 | [PHASE_02__widget-delegation.md](PHASE_02__widget-delegation.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No unresolved research items. All strategic §6 items are Resolved (shared mic destination setting, network upload + local fallback in scope, system notification for the background widget flow).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited here; capability recorded in `docs/ALL_FEATURES.jsonl`, surfaced by `/skill-release`.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0526` returns `Verified` (gated on device test - `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0526`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.
