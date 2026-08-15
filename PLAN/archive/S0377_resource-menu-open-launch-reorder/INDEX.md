# Tactical Plan: S0377 - resource-menu-open-launch-reorder

**Strategic spec:** [`../S0377_resource-menu-open-launch-reorder.md`](../S0377_resource-menu-open-launch-reorder.md)
**Feature:** Меню ресурса: «Открыть», «Запустить», перемещение в край и рамка иконки медиахранилищ
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | reorder-to-edge | - | ✅ Done | 2/2 | [PHASE_01__reorder-to-edge.md](PHASE_01__reorder-to-edge.md) |
| 02 | menu-open-launch-move | 01 | ✅ Done | 4/4 | [PHASE_02__menu-open-launch-move.md](PHASE_02__menu-open-launch-move.md) |
| 03 | icon-frame | 02 | ✅ Done | 2/2 | [PHASE_03__icon-frame.md](PHASE_03__icon-frame.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers. Strategic §6 items are non-blocking design defaults, already resolved here:

- Icon frame style → oval stroke matching the existing oval ripple click-mask (`ripple_icon_quick_slideshow.xml`), applied as the icon's `background` in code. Not colour-only.
- New menu string keys → dedicated new keys (`resource_menu_open`, `resource_menu_launch`, `resource_menu_move_to_top`, `resource_menu_move_to_bottom`) to avoid reusing the ambiguous existing `open` / `action_open` / `play` keys.
- Move-to-edge in inline-actions mode → popup menu only; inline-actions row keeps its current 5 fixed buttons.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `ResourceOrderManager` / `MainViewModel` / `ResourceAdapter` changed).
- [ ] `/spec-check S0377` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0377`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-07 - Initial tactical plan authored by `/spec-tech`.
