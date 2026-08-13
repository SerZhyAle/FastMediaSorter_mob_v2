# Tactical Plan: S0173 — refactor-playback-position-persistence

**Strategic spec:** [`../S0173_refactor-playback-position-persistence.md`](../S0173_refactor-playback-position-persistence.md)
**Feature:** Unified playback-position save/restore infrastructure
**Tier:** 3 — Tech Debt
**Priority:** 30
**Status:** Not started
**Phases:** 0 / 4 done
**Last updated:** 2026-05-12

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | utility-classes | — | ⬜ Not started | 0/2 | [PHASE_01__utility-classes.md](PHASE_01__utility-classes.md) |
| 02 | refactor-save-loop | 01 | ⬜ Not started | 0/3 | [PHASE_02__refactor-save-loop.md](PHASE_02__refactor-save-loop.md) |
| 03 | refactor-media-loader | 01 | ⬜ Not started | 0/2 | [PHASE_03__refactor-media-loader.md](PHASE_03__refactor-media-loader.md) |
| 04 | docs-catalog-cleanup | 02 03 | ⬜ Not started | 0/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 has no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no update needed (pure tech-debt refactor, no user-facing change).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0173` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0173`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-12 — Initial tactical plan authored by `/spec-tech`.
