# Tactical Plan: S0197 — threads-ig-data-sjs-extractor

**Strategic spec:** [`../S0197_threads-ig-data-sjs-extractor.md`](../S0197_threads-ig-data-sjs-extractor.md)
**Feature:** Threads/Instagram data-sjs JSON extractor wiring
**Tier:** 3 — Moderate
**Priority:** 70
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | activation-surface | — | ✅ Done | 3/3 | [PHASE_01__activation-surface.md](PHASE_01__activation-surface.md) |
| 02 | selection-bias | 01 | ✅ Done | 1/1 | [PHASE_02__selection-bias.md](PHASE_02__selection-bias.md) |
| 03 | batch-and-bypass | 02 | ✅ Done | 4/4 | [PHASE_03__batch-and-bypass.md](PHASE_03__batch-and-bypass.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |
| 05 | carousel-dedup | 03 | ✅ Done | 2/2 | [PHASE_05__carousel-dedup.md](PHASE_05__carousel-dedup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 research items (candidate index choice, JSON-image vs DOM-video priority, mobile IG subdomain payload parity) are post-implementation tuning decisions with reasonable defaults already chosen in the strategic spec; they do not block phase execution.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` / `_RU.md` / `_UK.md` — no changes (strategic §8 explicitly "Без изменений в docs/FEATURES.md").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` regenerated.
- [ ] `/spec-check S0197` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0197`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
