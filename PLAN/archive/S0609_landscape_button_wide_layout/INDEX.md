# Tactical Plan: S0609 - landscape-button-wide-layout (settings multi-column)

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Research inputs:**
- [`research/01__settings-fragment-element-inventory.md`](research/01__settings-fragment-element-inventory.md)
- [`research/02__column-count-rule.md`](research/02__column-count-rule.md)
- [`research/03__help-text-placement.md`](research/03__help-text-placement.md)
- [`research/04__canonical-mechanism.md`](research/04__canonical-mechanism.md)
- [`research/05__fragments-without-landscape.md`](research/05__fragments-without-landscape.md)
- [`research/06__focus-order.md`](research/06__focus-order.md)

**Feature:** Multi-column landscape layout of settings fragments
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - BlockNeedUserTest (device smoke PASS; thorough visual pass pending)
**Phases:** 6 / 6 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Landscape-only XML changes; portrait untouched. Canonical mechanism: weighted horizontal LinearLayout (research 04). Every step has a static verification predicate.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | flat-media-fragments | - | ✅ Done | 3/3 | [PHASE_01__flat-media-fragments.md](PHASE_01__flat-media-fragments.md) |
| 02 | general-fragment | 01 | ✅ Done | 3/3 | [PHASE_02__general-fragment.md](PHASE_02__general-fragment.md) |
| 03 | playback-fragment | 01 | ✅ Done | 2/2 | [PHASE_03__playback-fragment.md](PHASE_03__playback-fragment.md) |
| 04 | documents-streams-land | 01 | ✅ Done | 2/2 | [PHASE_04__documents-streams-land.md](PHASE_04__documents-streams-land.md) |
| 05 | destinations-fragment | 01 | ✅ Done | 2/2 | [PHASE_05__destinations-fragment.md](PHASE_05__destinations-fragment.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

> Phase 01 note: audio/land left unchanged - its 3 remaining solo toggles are section masters / gating toggles with no safe partner (would orphan dependent blocks). Phase 05 note: only 1 pair added in destinations (rowDetailedErrors + rowResumeOnNextLaunch); other solo toggles are structural masters or LOC-budget-deferred.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (codebase-driven, 2026-06-22).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` - regenerate only if any new class added (none expected; XML-only spec).
- [ ] `scripts/quality/assert-settings-doc-sync.ps1` passes (settings semantics unchanged; regenerate manifest only if it flags).
- [ ] `/spec-check S0609` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~]` when started, `[x]` when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status if whole spec blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0609`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech` after codebase research resolved all §6 items.
