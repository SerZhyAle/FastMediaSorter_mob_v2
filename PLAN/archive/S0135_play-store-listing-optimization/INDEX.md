# Tactical Plan: S0135 — play-store-listing-optimization

**Strategic spec:** [`../S0135_play-store-listing-optimization.md`](../S0135_play-store-listing-optimization.md)
**Feature:** Google Play listing optimization + In-App Review integration
**Tier:** 4
**Priority:** 65
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | copywriting | — | ✅ Done | 4/4 | [PHASE_01__copywriting.md](PHASE_01__copywriting.md) |
| 02 | design-brief | — | ✅ Done | 1/1 | [PHASE_02__design-brief.md](PHASE_02__design-brief.md) |
| 03 | review-foundation | — | ✅ Done | 3/3 | [PHASE_03__review-foundation.md](PHASE_03__review-foundation.md) |
| 04 | review-integration | 03 | ✅ Done | 3/3 | [PHASE_04__review-integration.md](PHASE_04__review-integration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01, 02, 03 have no mutual dependencies — they can run concurrently.

---

## Pre-Implementation Blockers

No global blockers. Phases 01, 02, 03 start immediately.

Phase 05 (Play Console action) has three owner-decision prerequisites tracked in `PHASE_05__docs-catalog-cleanup.md § Prerequisites`:
- Category selection (strategic §6.2) — research competitors, choose Photography / Productivity / Tools.
- Developer name decision (strategic §6.3) — register studio account or keep personal. External, no code impact.
- Design brief execution (strategic §6.4) — who executes Phase 02 output (freelancer / owner / AI).

---

## Open Questions Resolution

| # | Question | Resolution |
|---|----------|-----------|
| §6.1 | Threshold N for review | **Resolved** — initial: ≥20 cumulative Move+Copy ops AND ≥3 sessions with sorting activity AND 90-day cooldown since last show. Constants in `RecordSortSuccessUseCase.Companion`; tune without domain-layer change. |
| §6.5 | Store assets location | **Resolved** — `store_assets/` already exists; use existing structure with locale-suffix convention (`_en`, `_ru`, `_uk`). No new folder needed. |
| §6.2 | Play Console category | **Deferred to Phase 05** — owner reviews Play Console competitor data and decides. Suggested: Productivity over Photography. |
| §6.3 | Developer name | **Deferred to Phase 05** — owner decision, external, no code impact. |
| §6.4 | Design source | **Deferred to Phase 02 delivery** — brief is self-contained; execution path (freelancer / owner / AI) is owner decision. |

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` not updated — store listing and in-app review are infrastructure, not user-facing features (per strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after Phase 04 code changes.
- [ ] `pwsh -File scripts/check_strings_localized.ps1` passes (no new user-visible strings added — review dialog is native Play platform UI).
- [ ] `/spec-check S0135` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0135`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-13 — Initial tactical plan authored by `/spec-tech`.
