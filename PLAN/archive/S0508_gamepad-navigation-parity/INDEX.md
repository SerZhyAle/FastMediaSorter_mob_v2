# Tactical Plan: S0508 - gamepad-navigation-parity

**Strategic spec:** [`../S0508_gamepad-navigation-parity.md`](../S0508_gamepad-navigation-parity.md)
**Research inputs:** [`research/01__axis-distribution.md`](research/01__axis-distribution.md) · [`research/02__shoulder-priority.md`](research/02__shoulder-priority.md) · [`research/03__repeat-acceleration-model.md`](research/03__repeat-acceleration-model.md) · [`research/04__testability-without-device.md`](research/04__testability-without-device.md)
**Feature:** Gamepad/joystick navigation parity on all screens
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | translator-foundations | - | ✅ Done | 3/3 | [PHASE_01__translator-foundations.md](PHASE_01__translator-foundations.md) |
| 02 | base-motion-hook | 01 | ✅ Done | 4/4 | [PHASE_02__base-motion-hook.md](PHASE_02__base-motion-hook.md) |
| 03 | shoulder-page-jump | 02 | ✅ Done | 2/2 | [PHASE_03__shoulder-page-jump.md](PHASE_03__shoulder-page-jump.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All strategic §6 research items are Resolved (see Research inputs).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited per-spec; strategic §8 capability is recorded in `docs/ALL_FEATURES.jsonl` and surfaced at release.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).
- [ ] `/spec-check S0508` returns `Verified`.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0508`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-18 - Initial tactical plan authored by `/spec-tech`.
