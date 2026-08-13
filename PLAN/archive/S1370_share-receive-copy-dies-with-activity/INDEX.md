# Tactical Plan: S1370 - share-receive-copy-dies-with-activity

**Strategic spec:** [`../S1370_share-receive-copy-dies-with-activity.md`](../S1370_share-receive-copy-dies-with-activity.md)
**Research inputs:** none
**Feature:** Share-receive copy survives the receive screen
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | request-source-ownership | - | ✅ Done | 4/4 | [PHASE_01__request-source-ownership.md](PHASE_01__request-source-ownership.md) |
| 02 | share-receive-handoff | 01 | ✅ Done | 5/5 | [PHASE_02__share-receive-handoff.md](PHASE_02__share-receive-handoff.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - every strategic §6 research item is Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped, strategic §8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1370` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1370`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-03 - Initial tactical plan authored by `/spec-tech`.
