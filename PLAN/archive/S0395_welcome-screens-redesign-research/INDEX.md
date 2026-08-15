# Tactical Plan: S0395 - welcome-screens-redesign-research

**Strategic spec:** [`../S0395_welcome-screens-redesign-research.md`](../S0395_welcome-screens-redesign-research.md)
**Research inputs:** none at plan time - this plan *produces* the `research/` artifacts (research-only ticket)
**Feature:** Welcome screens redesign - research and decision document
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> **Research-ticket note:** this ticket changes NO source code. Each step's "real work" is authoring a research artifact under `research/` - the deliverable of this ticket, equal in rank to source changes on a code ticket. The strategic §6 items are this plan's deliverables, not pre-implementation blockers. No debug verification tags apply (no `.kt` touched; ticket must never enter `BlockNeedUserTest`).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | current-flow-inventory | - | ✅ Done | 2/2 | [PHASE_01__current-flow-inventory.md](PHASE_01__current-flow-inventory.md) |
| 02 | page-content-research | 01 | ✅ Done | 4/4 | [PHASE_02__page-content-research.md](PHASE_02__page-content-research.md) |
| 03 | permissions-downloads-research | 02 | ✅ Done | 2/2 | [PHASE_03__permissions-downloads-research.md](PHASE_03__permissions-downloads-research.md) |
| 04 | cross-cutting-research | 03 | ✅ Done | 4/4 | [PHASE_04__cross-cutting-research.md](PHASE_04__cross-cutting-research.md) |
| 05 | synthesis-and-split | 04 | ✅ Done | 2/2 | [PHASE_05__synthesis-and-split.md](PHASE_05__synthesis-and-split.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Artifact Map (strategic §6 item → file)

- §6.1 → `research/01__current-flow-inventory.md` (Phase 01)
- §6.2 → `research/02__page0-language-theme.md` (Phase 02)
- §6.3 → `research/03__page1-device-profiles.md` (Phase 02)
- §6.4 → `research/04__page2-network-toggles.md` (Phase 02)
- §6.5 → `research/05__permissions-ordering.md` (Phase 03)
- §6.6 → `research/06__page4-functionality-toggles.md` (Phase 02)
- §6.7 → `research/07__onboarding-downloads.md` (Phase 03)
- §6.8 → `research/08__reentry-upgrade.md` (Phase 04)
- §6.9 → `research/09__flavor-matrix.md` (Phase 04)
- §6.10 → `research/10__length-defaults.md` (Phase 04)
- §6.11 → `research/11__accessibility-input.md` (Phase 04)
- §6.12 → `research/12__dev-ticket-split.md` (Phase 05)
- Synthesis deliverable → `SYNTHESIS.md` (Phase 05, folder root)

Every artifact follows the uniform skeleton: `## Question` / `## Sources` / `## Findings` / `## Options` (optional) / `## Conclusion` / `## Impact on recommendation`.

---

## Pre-Implementation Blockers

None. All strategic §6 items are Open by design - resolving them IS this plan's work product. Phase 01 may start immediately.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every authored artifact (14 entries via post-change.ps1).
- [x] `dev/CATALOG/` regen - skipped (no Kotlin touched).
- [x] Strategic spec §6: all 12 items flipped to Resolved with `**Артефакт:**` links (0 Open / 12 links verified).
- [x] Owner review of `SYNTHESIS.md` requested 2026-06-10 (strategic §11.4); dev tickets are created only after owner sign-off.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockQuestions` / `BlockExternal` (never `BlockNeedUserTest` - nothing to device-test).
5. All done: flip `Status:` to `Done`, advance journal to `Implemented`, request owner review of `SYNTHESIS.md`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-06-10 - Initial tactical plan authored by `/spec-tech`.
