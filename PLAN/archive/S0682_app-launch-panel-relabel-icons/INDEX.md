# Tactical Plan: S0682 - app-launch-panel-relabel-icons

**Strategic spec:** [`../S0682_app-launch-panel-relabel-icons.md`](../S0682_app-launch-panel-relabel-icons.md)
**Research inputs:** none
**Feature:** App-launch panel - relabel add-chooser items + distinct OS-setting icons
**Tier:** 1 - Quick Win (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | os-setting-icons | - | ✅ Done | 2/2 | [PHASE_01__os-setting-icons.md](PHASE_01__os-setting-icons.md) |
| 02 | relabel-chooser-strings | - | ✅ Done | 3/3 | [PHASE_02__relabel-chooser-strings.md](PHASE_02__relabel-chooser-strings.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0682` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0682`.

---

## Blockers Log

- none

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`.
