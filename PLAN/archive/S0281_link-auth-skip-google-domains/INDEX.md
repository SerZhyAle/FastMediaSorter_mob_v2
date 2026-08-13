# Tactical Plan: S0281 - link-auth-skip-google-domains

**Strategic spec:** [`../S0281_link-auth-skip-google-domains.md`](../S0281_link-auth-skip-google-domains.md)
**Feature:** Skip auth offer for Google domains in link auto-download
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 5 done + 1 ⏭️ Skipped (Phase 03 per Decision Q3=B)
**Status:** Done (awaiting on-device verification — see strategic spec status `BlockNeedUserTest`)
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | inventory-and-decisions | - | ✅ Done | 3/3 | [PHASE_01__inventory-and-decisions.md](PHASE_01__inventory-and-decisions.md) |
| 02 | suppress-auth-offer | 01 | ✅ Done | 5/5 | [PHASE_02__suppress-auth-offer.md](PHASE_02__suppress-auth-offer.md) |
| 03 | stale-record-cleanup | 01 | ⏭️ Skipped | - | [PHASE_03__stale-record-cleanup.md](PHASE_03__stale-record-cleanup.md) |
| 04 | optional-explanation-toast | 01, owner-Q2 | ✅ Done | 4/4 | [PHASE_04__optional-explanation-toast.md](PHASE_04__optional-explanation-toast.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No global blockers. §6 research items are either tactically resolvable (Q1, Q3 → resolved by Phase 01 inventory) or scoped to a specific phase (Q2 → gates Phase 04 only, see that phase's Prerequisites). Implementation may start with Phase 01 immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done or ⏭️ Skipped (with documented reason).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 declares "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0281` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0281`.

---

## Blockers Log

- 2026-05-21 - Phase 04 awaiting §6 Q2 owner decision (no toast / once per session / once per failed extract). Phases 01-03 proceed independently. Status will be `BlockQuestions` only if Phase 04 reaches the head of the queue before the answer arrives; otherwise Phase 04 may be marked ⏭️ Skipped.

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech`.
