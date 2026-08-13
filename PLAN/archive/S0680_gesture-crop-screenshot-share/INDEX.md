# Tactical Plan: S0680 - gesture-crop-screenshot-share

**Strategic spec:** [`../S0680_gesture-crop-screenshot-share.md`](../S0680_gesture-crop-screenshot-share.md)
**Research inputs:** [`research/01__crop-source-and-share-target.md`](research/01__crop-source-and-share-target.md)
**Feature:** Edge-gesture action "Обрезать скриншот и отправить"
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | crop-share-orchestration | - | ✅ Done | 4/4 | [PHASE_01__crop-share-orchestration.md](PHASE_01__crop-share-orchestration.md) |
| 02 | action-wiring-strings | 01 | ✅ Done | 4/4 | [PHASE_02__action-wiring-strings.md](PHASE_02__action-wiring-strings.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 items 1, 2, 3 are Resolved (see [`research/01__crop-source-and-share-target.md`](research/01__crop-source-and-share-target.md)).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 routes the showcase entry to the gitignored noLegal set via `/skill-release`, not per-spec).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `docs/ALL_FEATURES.jsonl` records the new capability.
- [ ] Settings doc-sync gate passes (new gesture-action value).
- [ ] `/spec-check S0680` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0680`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`.
