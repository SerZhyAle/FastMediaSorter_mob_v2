# Tactical Plan: S1832 - stable-channel-identity-survives-prune

**Strategic spec:** [`../S1832_stable-channel-identity-survives-prune.md`](../S1832_stable-channel-identity-survives-prune.md)
**Research inputs:** [`research/01__user-data-bound-to-channel-identity.md`](research/01__user-data-bound-to-channel-identity.md), [`research/02__url-normalization-collisions.md`](research/02__url-normalization-collisions.md)
**Feature:** Channel identity survives a catalog prune
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 85
**Status:** Not started
**Phases:** 0 / 5 done
**Last updated:** 2026-08-20

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | channel-identity | - | ✅ Done | 2/2 | [PHASE_01__channel-identity.md](PHASE_01__channel-identity.md) |
| 02 | identity-schema | 01 | ✅ Done | 6/6 | [PHASE_02__identity-schema.md](PHASE_02__identity-schema.md) |
| 03 | identity-merge | 02 | ✅ Done | 7/7 | [PHASE_03__identity-merge.md](PHASE_03__identity-merge.md) |
| 04 | identity-consumers | 03 | ✅ Done | 6/6 | [PHASE_04__identity-consumers.md](PHASE_04__identity-consumers.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are `Resolved`, and both were re-measured on 2026-08-20 against
the live bank and the working tree; the corrections are recorded in the strategic spec and in the two
research artifacts above.

---

## Schema hops owned by this ticket

Two hops, one per phase that needs one, so every phase stays independently shippable.

- **52** - `Migration51To52` in `data/local/db/Migration51To52.kt` (Phase 02): adds `identityKey` to
  `stream_sources` and creates `stream_user_state`.
- **53** - `Migration52To53` in `data/local/db/Migration52To53.kt` (Phase 04): rewrites
  `launcher_cells.target` from `stream:<row id>` to `stream:<identity key>`.

Never renumber or edit a prior migration. Both are registered in `core/di/DatabaseModule.kt` after
`MIGRATION_50_51`, and each is covered by its own instrumented test.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 routes the capability to `docs/ALL_FEATURES.jsonl`, not to the showcase.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed.
- [ ] `/spec-check S1832` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1832`.

---

## Blockers Log

- 2026-08-20 - Phase 01 code written and green (11/11 tests), but its `post-change` closure is blocked: the repo-wide `ticket-log-audit` gate fails on `S1838`'s live probe in `wear/..VideoPlayerViewModel.kt:116`, inserted by a concurrent session that still holds that ticket's lease. Not this ticket's file and not stale - do not remove it. Re-run the closure once S1838 reaches `BlockNeedUserTest`.

---

## Change Log

- 2026-08-20 - Initial tactical plan authored by `/spec-all` stage F2, after re-measuring both strategic §6 items and correcting §3.2, §4, §6.1, §6.2 and ADR-2, and adding ADR-3.
