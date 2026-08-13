# Tactical Plan: S0621 - standard edge-gesture screenshot (consent path)

**Strategic spec:** [`../S0621_hotfix-standard-gesture-settings.md`](../S0621_hotfix-standard-gesture-settings.md)
**Research inputs:** none (blueprint reused from archived S0418 tactical phases)
**Feature:** Bring the edge-gesture screenshot capability to the `standard` Play flavor via MediaProjection consent only; the accessibility silent path stays a `noLegal` opt-in. Settings group becomes visible on standard with the accessibility-shortcut rows hidden there.
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 95
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | extract-shared-overlay-machinery | - | ✅ Done | 3/3 | [PHASE_01__extract-shared-overlay-machinery.md](PHASE_01__extract-shared-overlay-machinery.md) |
| 02 | standard-capture-controller | 01 | ✅ Done | 3/3 | [PHASE_02__standard-capture-controller.md](PHASE_02__standard-capture-controller.md) |
| 03 | standard-manifest | 02 | ✅ Done | 2/2 | [PHASE_03__standard-manifest.md](PHASE_03__standard-manifest.md) |
| 04 | settings-ui-split | 02 | ✅ Done | 2/2 | [PHASE_04__settings-ui-split.md](PHASE_04__settings-ui-split.md) |
| 05 | docs-catalog-cleanup | 01, 02, 03, 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- (none) - all strategic §6 items are release-gate or cosmetic follow-ups, none block implementation/build:
  - §6.1 (gesture-action "silent" label nuance on standard) - cosmetic follow-up, parked.
  - §6.2 (reduce consent-dialog frequency) - out of scope, future iteration.
  - §6.3 (Play Console declarations) - release gate, filed at submission, not here.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `assembleStandardDebug` green; merged standard manifest carries `OverlayHostService` + `SYSTEM_ALERT_WINDOW` + `SPECIAL_USE`/`MEDIA_PROJECTION` FGS, NO accessibility service.
- [ ] `assembleNoLegalDebug` green (no regression from the machinery move).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (capability now in standard) - strategic §8 mandates.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new standard-flavor classes).
- [ ] `/spec-check S0621` returns `Verified` - pending on-device test (status `BlockNeedUserTest`).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~]` when started, `[x]` when Verification passes. Never `[x]` on intent.
3. On phase completion: confirm every step `[x]` + Phase Done Criteria, flip row `✅ Done`, bump counter.
4. If blocked: flip `⛔ Blocked`, add a Blockers Log bullet; set journal status if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0621`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech` (blueprint adapted from archived S0418).
