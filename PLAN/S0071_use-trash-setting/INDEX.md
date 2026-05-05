# Tactical Plan: S0071 — use-trash-setting

**Strategic spec:** [`../S0071_use-trash-setting.md`](../S0071_use-trash-setting.md)
**Feature:** Use-trash toggle — expose `useTrash` setting in UI and wire it into all delete paths
**Tier:** 2 — Easy
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-ui | — | ✅ Done | 3/3 | [PHASE_01__settings-ui.md](PHASE_01__settings-ui.md) |
| 02 | browse-delete-fix | 01 | ✅ Done | 3/3 | [PHASE_02__browse-delete-fix.md](PHASE_02__browse-delete-fix.md) |
| 03 | player-delete-fix | 01 | ✅ Done | 2/2 | [PHASE_03__player-delete-fix.md](PHASE_03__player-delete-fix.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers — all research is resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 04).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0071` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0071`.

---

## Blockers Log

_None._

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
