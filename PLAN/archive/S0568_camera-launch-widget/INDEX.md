# Tactical Plan: S0568 - camera-launch-widget

**Strategic spec:** [`../S0568_camera-launch-widget.md`](../S0568_camera-launch-widget.md)
**Research inputs:** [`research/01__flavor-availability.md`](research/01__flavor-availability.md) · [`research/02__degenerate-mode-gating.md`](research/02__degenerate-mode-gating.md) · [`research/03__shared-save-routing.md`](research/03__shared-save-routing.md)
**Feature:** Home-screen widget that launches the unified in-app camera (photo+video)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (device-test pending)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-save-routing | - | ✅ Done | 2/2 | [PHASE_01__shared-save-routing.md](PHASE_01__shared-save-routing.md) |
| 02 | launch-trampoline | 01 | ✅ Done | 2/2 | [PHASE_02__launch-trampoline.md](PHASE_02__launch-trampoline.md) |
| 03 | widget-provider-resources | 02 | ✅ Done | 5/5 | [PHASE_03__widget-provider-resources.md](PHASE_03__widget-provider-resources.md) |
| 04 | manifest-registration | 03 | ✅ Done | 1/1 | [PHASE_04__manifest-registration.md](PHASE_04__manifest-registration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All strategic §6 research items are Resolved (see Research inputs above).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here; the public showcase is populated only by `/skill-release` from the `ALL_FEATURES` diff (CLAUDE.md §11).
- [ ] `docs/ALL_FEATURES.jsonl` capability record - deferred to the `Verified` flip (written by `/spec-check` after device-test, per CLAUDE.md §11; `/spec-all` does not write it directly).
- [x] `dev/CHANGELOG.md` has an entry for every modified file (8 S0568 entries from commit ab3f5d02).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added; gitignored local index).
- [ ] `/spec-check S0568` returns `Verified` - pending on-device verification.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` - pending device-test.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0568`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-20 - Initial tactical plan authored by `/spec-tech`.
