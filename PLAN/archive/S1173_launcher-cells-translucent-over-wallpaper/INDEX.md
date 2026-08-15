# Tactical Plan: S1173 - launcher-cells-translucent-over-wallpaper

**Strategic spec:** [`../S1173_launcher-cells-translucent-over-wallpaper.md`](../S1173_launcher-cells-translucent-over-wallpaper.md)
**Research inputs:** none
**Feature:** Launcher desktop - transparent shortcut cells over the wallpaper
**Tier:** 3 - Moderate
**Priority:** 45
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-30

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-outlined-text | - | ✅ Done | 5/5 | [PHASE_01__shared-outlined-text.md](PHASE_01__shared-outlined-text.md) |
| 02 | outlined-icon-widget | 01 | ✅ Done | 2/2 | [PHASE_02__outlined-icon-widget.md](PHASE_02__outlined-icon-widget.md) |
| 03 | transparent-shortcut-cell | 01, 02 | ✅ Done | 4/4 | [PHASE_03__transparent-shortcut-cell.md](PHASE_03__transparent-shortcut-cell.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 item is either resolved by owner decision (§3.3) or scheduled as a value-tuning task inside a phase step.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 capability is recorded in `docs/ALL_FEATURES.jsonl`, the showcase is `/skill-release`-owned.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - two public widgets added and one class moved.
- [ ] `/spec-check S1173` returns `Verified`.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1173`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-29 - Initial tactical plan authored by `/spec-tech`.
