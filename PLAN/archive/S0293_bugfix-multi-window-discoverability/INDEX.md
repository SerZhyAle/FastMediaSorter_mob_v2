# Tactical Plan: S0293 - bugfix-multi-window-discoverability

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Feature:** Multi-window discoverability fixes (Bug A: mouse context routing · Bug B: capability-paired defaults · Bug C: player portrait inline · plus DeX runtime detection)
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 8 / 8 done
**Last updated:** 2026-05-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | detector-foundations | - | ✅ Done | 3/3 | [PHASE_01__detector-foundations.md](PHASE_01__detector-foundations.md) |
| 02 | settings-defaults-wiring | 01 | ✅ Done | 2/2 | [PHASE_02__settings-defaults-wiring.md](PHASE_02__settings-defaults-wiring.md) |
| 03 | browse-mouse-routing-fix | - | ✅ Done | 1/1 | [PHASE_03__browse-mouse-routing-fix.md](PHASE_03__browse-mouse-routing-fix.md) |
| 04 | player-portrait-inline | - | ✅ Done | 1/1 | [PHASE_04__player-portrait-inline.md](PHASE_04__player-portrait-inline.md) |
| 05 | runtime-reactivity | 01, 02 | ✅ Done | 3/3 | [PHASE_05__runtime-reactivity.md](PHASE_05__runtime-reactivity.md) |
| 06 | docs-catalog-cleanup | 01, 02, 03, 04, 05 | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |
| 07 | browse-observer-runtime-orcompose | 05 | ✅ Done | 1/1 | [PHASE_07__browse-observer-runtime-orcompose.md](PHASE_07__browse-observer-runtime-orcompose.md) |
| 08 | main-list-per-resource-new-window | 07 | ✅ Done | 1/1 | [PHASE_08__main-list-per-resource-new-window.md](PHASE_08__main-list-per-resource-new-window.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three §6 research items resolved in strategic spec on 2026-05-22:

- §6.1 Toast suppression on multi-window devices → variant (a) symmetrical suppression.
- §6.2 Runtime signal source → variant (a) `UI_MODE_TYPE_DESK` only; risk documented in §7.
- §6.3 Task lifecycle through DeX cycle → variant (a) non-goal.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 01 adds public method).
- [ ] `/spec-check S0293` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status.
5. All done: flip `Status:` to `Done`, run `/spec-check S0293`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-25 - Phase 07 and Phase 08 retrofitted by `/spec-all` from audit findings; both phases were delivered in code between 2026-05-22 and 2026-05-24 without explicit tactical-spec entries. Phase 07 closes the BrowseObserverManager runtime hookup for per-row `⋮` visibility (extends Phase 05). Phase 08 adds per-resource "Open in new window" entry on the main resource list (MainActivity / ResourceAdapter / resource_item_actions.xml) - extension beyond original strategic §11, but consistent with the spec's discoverability goal.
