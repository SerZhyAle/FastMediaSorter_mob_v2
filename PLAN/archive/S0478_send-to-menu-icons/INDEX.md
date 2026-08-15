# Tactical Plan: S0478 - send-to-menu-icons

**Strategic spec:** [`../S0478_send-to-menu-icons.md`](../S0478_send-to-menu-icons.md)
**Research inputs:** none
**Feature:** Recognizable icons for «Send to..» menu items
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Awaiting device test (BlockNeedUserTest)
**Phases:** 3 / 3 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | receiver-icon-assets | - | ✅ Done | 1/1 | [PHASE_01__receiver-icon-assets.md](PHASE_01__receiver-icon-assets.md) |
| 02 | wire-menu-icons | 01 | ✅ Done | 3/3 | [PHASE_02__wire-menu-icons.md](PHASE_02__wire-menu-icons.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The two strategic §6 items are not pre-implementation prerequisites:

- §6.1 (icons render in the nested overflow submenu) is a device-test verification, covered at `BlockNeedUserTest`, not before coding.
- §6.2 (visual canon of the analogs) is a design decision made inside Phase 01 while drawing the vectors.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 is "Без изменений".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (declarations changed in the share module).
- [ ] `/spec-check S0478` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0478`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
