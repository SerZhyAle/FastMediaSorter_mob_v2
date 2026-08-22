# Tactical Plan: S1640 - vr-unpaired-surface-and-player-registrations

**Strategic spec:** [`../S1640_vr-unpaired-surface-and-player-registrations.md`](../S1640_vr-unpaired-surface-and-player-registrations.md)
**Research inputs:** none as separate files - strategic §6 items 1 and 2 were resolved in place from the ownership reading of the four files on 2026-08-14
**Feature:** Paired removal for the four unpaired registrations in the vr source set
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Not started
**Phases:** 2 / 2 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | registration-parity | - | ✅ Done | 4/4 | [PHASE_01__registration-parity.md](PHASE_01__registration-parity.md) |
| 02 | baseline-and-build | 01 | ✅ Done | 2/2 | [PHASE_02__baseline-and-build.md](PHASE_02__baseline-and-build.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 items carry `Status: Resolved`, and §3.3 records that owner sign-off was not required because the decision is paired removal rather than a widened gate discount.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped, strategic §8 reads "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated - two controllers gain a field, so the catalog is re-scanned by the closing facade.
- [ ] `/spec-check S1640` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1640`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored during `/spec-all` stage F2.
