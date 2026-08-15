# Tactical Plan: S0912 - quick-launch-panel-programs-scenarios

**Strategic spec:** [`../S0912_quick-launch-panel-programs-scenarios.md`](../S0912_quick-launch-panel-programs-scenarios.md)
**Research inputs:** [`research/01__panel-programs-scenarios-gap.md`](research/01__panel-programs-scenarios-gap.md)
**Feature:** Extend the app-launch panel's internal-route registry with the four Programs-and-Scenarios items it is missing (quick camera, quick voice, screen recording, link download)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | standalone-launch-surfaces | - | ✅ Done | 3/3 | [PHASE_01__standalone-launch-surfaces.md](PHASE_01__standalone-launch-surfaces.md) |
| 02 | panel-registry-wiring | 01 | ✅ Done | 3/3 | [PHASE_02__panel-registry-wiring.md](PHASE_02__panel-registry-wiring.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all five strategic §6 research items are `Resolved` (see research artifact).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` has a new record for this capability (strategic §8 mandates a user-visible-capability entry; `docs/FEATURES*.md` itself stays untouched here - owned only by `/skill-release`, CLAUDE.md §11).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public registry/use-case surface changed).
- [ ] `/spec-check S0912` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0912`.

---

## Blockers Log

None.

---

## Change Log

- 2026-07-03 - Initial tactical plan authored by `/spec-tech`.
