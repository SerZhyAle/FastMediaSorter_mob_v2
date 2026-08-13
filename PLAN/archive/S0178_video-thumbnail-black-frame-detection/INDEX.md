# Tactical Plan: S0178 — video-thumbnail-black-frame-detection

**Strategic spec:** [`../S0178_video-thumbnail-black-frame-detection.md`](../S0178_video-thumbnail-black-frame-detection.md)
**Feature:** Video thumbnail black frame detection and retry
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Implemented
**Phases:** 4 / 4 done
**Last updated:** 2026-05-12

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | darkness-evaluator | — | ✅ Done | 3/3 | [PHASE_01__darkness-evaluator.md](PHASE_01__darkness-evaluator.md) |
| 02 | network-decoder-retry | 01 | ✅ Done | 4/4 | [PHASE_02__network-decoder-retry.md](PHASE_02__network-decoder-retry.md) |
| 03 | background-extractor-retry | 01 | ✅ Done | 3/3 | [PHASE_03__background-extractor-retry.md](PHASE_03__background-extractor-retry.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items — all §6 questions in the strategic spec are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after new class added.
- [ ] `/spec-check S0178` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0178`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-12 — Initial tactical plan authored by `/spec-tech`.
