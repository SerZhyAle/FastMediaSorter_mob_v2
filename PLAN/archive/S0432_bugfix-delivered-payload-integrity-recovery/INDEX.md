# Tactical Plan: S0432 - bugfix-delivered-payload-integrity-recovery

**Strategic spec:** [`../S0432_bugfix-delivered-payload-integrity-recovery.md`](../S0432_bugfix-delivered-payload-integrity-recovery.md)
**Research inputs:** none (architecture research conducted inline during F1/F2)
**Feature:** Self-recovery on corrupted delivered payload
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Implemented - BlockNeedUserTest
**Phases:** 3 / 3 done
**Last updated:** 2026-06-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | recovery-seam | - | ✅ Done | 3/3 | [PHASE_01__recovery-seam.md](PHASE_01__recovery-seam.md) |
| 02 | consumer-message | 01 | ✅ Done | 2/2 | [PHASE_02__consumer-message.md](PHASE_02__consumer-message.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 has no Open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8: "Без изменений в docs/FEATURES").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class added).
- [ ] `/spec-check S0432` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0432`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-06-15 - Initial tactical plan authored by `/spec-tech`.
