# Tactical Plan: S0498 - material-statusbar-deprecation

**Strategic spec:** [`../S0498_material-statusbar-deprecation.md`](../S0498_material-statusbar-deprecation.md)
**Research inputs:** [`research/01__material-1-14-statusbar-guard.md`](research/01__material-1-14-statusbar-guard.md)
**Feature:** Bump Material Components 1.13.0 → 1.14.0 to clear Play Console deprecated setStatusBarColor edge-to-edge warning
**Tier:** 1 - Quick Win (ad-hoc)
**Priority:** 50
**Status:** Implemented (awaiting device test)
**Phases:** 2 / 2 done
**Last updated:** 2026-06-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | material-bump-theme-cleanup | - | ✅ Done | 3/3 | [PHASE_01__material-bump-theme-cleanup.md](PHASE_01__material-bump-theme-cleanup.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The single Open strategic §6 item (visual regressions, §6.2) is a POST-implementation device verification, not a research blocker - it is resolved by the `BlockNeedUserTest` device pass after Phase 01, never before it. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration not required (no `.kt` / public API change).
- [ ] Device pass confirms strategic §11.2 / §11.3 (bottom sheets render correctly, system bars correct) - portrait + landscape, light + dark.
- [ ] Play Console next pre-launch report no longer flags `android.view.Window.setStatusBarColor` (strategic §11.5) - confirmed at next release via `/skill-release`, deferred beyond `/spec-check`.
- [ ] `/spec-check S0498` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0498`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-18 - Initial tactical plan authored by `/spec-tech`.
