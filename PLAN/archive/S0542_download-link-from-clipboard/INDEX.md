# Tactical Plan: S0542 - download-link-from-clipboard

**Strategic spec:** [`../S0542_download-link-from-clipboard.md`](../S0542_download-link-from-clipboard.md)
**Research inputs:** none
**Feature:** Download by link from clipboard (manual main-menu entry)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented
**Phases:** 4 / 4 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 1/1 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | menu-and-dialog | 01 | ✅ Done | 2/2 | [PHASE_02__menu-and-dialog.md](PHASE_02__menu-and-dialog.md) |
| 03 | mainactivity-wiring | 02 | ✅ Done | 4/4 | [PHASE_03__mainactivity-wiring.md](PHASE_03__mainactivity-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No research blockers - strategic §6 has no open items. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 defers showcase to `/skill-release` (no per-spec edit).
- [ ] `docs/ALL_FEATURES.jsonl` has a record for the new capability (Phase 04).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (two new classes added).
- [ ] `/spec-check S0542` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state with `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0542`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.
