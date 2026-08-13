# Tactical Plan: S0522 - fallback-save-unavailable-resource

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Research inputs:** none
**Feature:** Fallback save when destination resource is unavailable
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 65
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 4/4 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | strings-notifier | 01 | ✅ Done | 3/3 | [PHASE_02__strings-notifier.md](PHASE_02__strings-notifier.md) |
| 03 | screenshot-flow | 01, 02 | ✅ Done | 3/3 | [PHASE_03__screenshot-flow.md](PHASE_03__screenshot-flow.md) |
| 04 | capture-flows | 01, 02 | ✅ Done | 4/4 | [PHASE_04__capture-flows.md](PHASE_04__capture-flows.md) |
| 05 | frame-download-flows | 01, 02 | ✅ Done | 3/3 | [PHASE_05__frame-download-flows.md](PHASE_05__frame-download-flows.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No unresolved research items. All strategic §6 items are Resolved (transport-based detection, write-time error catch, notify-only-on-unavailable, system notification for background flows, destination list not filtered).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited here; strategic §8 capability is recorded in `docs/ALL_FEATURES.jsonl` and surfaced by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0522` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0522`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-06-18 - Initial tactical plan authored by `/spec-tech`.
