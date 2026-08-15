# Tactical Plan: S0383 - neuroslop-code-and-resource-hygiene

**Strategic spec:** [`../S0383_neuroslop-code-and-resource-hygiene.md`](../S0383_neuroslop-code-and-resource-hygiene.md)
**Feature:** Neuroslop code and resource hygiene
**Tier:** 3 - Moderate
**Priority:** 70
**Status:** In Progress
**Phases:** 5 / 7 done (01, 02, 05, 06, 07); 03 + 04 ⏭️ Deferred (descoped - detectors hold floors)
**Last updated:** 2026-06-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | detection-harness | - | ✅ Done | 5/5 | [PHASE_01__detection-harness.md](PHASE_01__detection-harness.md) |
| 02 | comments-hygiene | 01 | ✅ Done | 3/3 | [PHASE_02__comments-hygiene.md](PHASE_02__comments-hygiene.md) |
| 03 | exception-handling | 01 | ⏭️ Deferred (detector holds floor 75) | 0/3 | [PHASE_03__exception-handling.md](PHASE_03__exception-handling.md) |
| 04 | resource-dedup | 01 | ⏭️ Deferred (detection done; detector holds floor 150) | 1/4 | [PHASE_04__resource-dedup.md](PHASE_04__resource-dedup.md) |
| 05 | lifecycle-safety | 01 | ✅ Done | 3/3 | [PHASE_05__lifecycle-safety.md](PHASE_05__lifecycle-safety.md) |
| 06 | automation-gates | 01,02,03,04,05 | ✅ Done (interim baselines) | 3/3 | [PHASE_06__automation-gates.md](PHASE_06__automation-gates.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

> Phase 01 builds the read-only detection harness (no source edits) and is safe to run unattended. Phases 02–05 are the destructive cleanups, each gated by the blocker below. Phases 02–05 are mutually independent (each owns a distinct concern) and may run in any order once Phase 01 is done.

---

## Pre-Implementation Blockers

- [x] **Owner decision: execution mode for destructive cleanup** - RESOLVED 2026-06-08: owner chose full `/spec-dev` autopilot across all 7 phases (comment removal and catch-block rewrites included), accepting the per-site automation risk. The per-site developer prompts in Phases 02–05 still apply as the executor's guidance, but execution is unattended.

Phase 04 owner inputs (added 2026-06-08 after detection re-scope; block only Phase 04 sub-tracks, not 02/03/06/07):

- [ ] **04b-ii parity translations** - owner/translator supplies RU/UK copy for ~9 RU + ~30 UK user-facing keys. Cannot be fabricated.
- [ ] **04c color convention** - owner confirms the theme-attr mapping (which `?attr/` for text/surface; which stay named `@color/` scrims; which immersive overlays are kept).
- [ ] **04a dedup shortlist** - owner reviews the few genuine semantic duplicates, or confirms dropping blind dedup.

Resolved strategic research items (no blocker - decided in strategic §6):

- §6.1 comment threshold - resolved: narrow heuristic candidates, manual verification per candidate.
- §6.2 catch notification strategy - resolved: UI-initiated failures get user feedback; background degradation logs at correct level.
- §6.3 dedup scope - resolved by default: automate over `src/main/res`; flavor overrides touched only on key collision, with manual EN/RU/UK grep parity.
- §6.4 hardcoded-color boundary - resolved by default: replace only in `layout/` + `layout-land/`; vector drawables (`ic_*`, `ico_*`) excluded.

---

## Completion Gate

- [x] All non-deferred phases show ✅ Done (01, 02, 05, 06, 07). Phases 03 + 04 are ⏭️ Deferred by descope (2026-06-08): their detectors are wired and hold the current floors; bulk cleanup is managed debt, not a release blocker. See strategic §2 «Доставленный объём».
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8: no FEATURES change; internal hygiene/tooling). Verified 0 S0383 refs.
- [x] `dev/CHANGELOG.md` has an entry for every modified file (47 S0383 entries; ~140-file comment sweep covered by one summary entry).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 07.1).
- [ ] `/spec-check S0383` returns `Verified` (run next; audits against the descoped §10 criteria - 03/04 marked Deferred).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0383`.

---

## Blockers Log

- 2026-06-08 - Pre-impl: destructive cleanup (Phases 02–05) awaited owner confirmation of execution mode. RESOLVED same day - owner chose full `/spec-dev` autopilot; blocker cleared.
- 2026-06-08 - Phase 04 blocked after detection (04.1). Autopilot cannot complete 04.2-04.4: string-dedup premise is mostly false positives (would damage thematic split), parity fill needs RU/UK translations (forbidden to fabricate - Hard Stop #12), color cleanup needs a theme-attr convention. Needs owner inputs or `/spec-update` re-scope. Phases 02/03/06/07 unaffected.

---

## Change Log

- 2026-06-08 - Initial tactical plan authored by `/spec-tech`.
