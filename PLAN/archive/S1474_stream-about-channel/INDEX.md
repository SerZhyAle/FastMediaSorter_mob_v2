# Tactical Plan: S1474 - stream-about-channel

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Research inputs:** [`research/01__stream-format-readout.md`](research/01__stream-format-readout.md) · [`research/02__observed-data-rate.md`](research/02__observed-data-rate.md) · [`research/03__playing-engine-access.md`](research/03__playing-engine-access.md) · [`research/04__measurement-concurrency.md`](research/04__measurement-concurrency.md)
**Feature:** About this channel - per-channel info window with a live measurement of the transmission
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | info-model-and-strings | - | ✅ Done | 4/4 | [PHASE_01__info-model-and-strings.md](PHASE_01__info-model-and-strings.md) |
| 02 | format-probe | 01 | ✅ Done | 4/4 | [PHASE_02__format-probe.md](PHASE_02__format-probe.md) |
| 03 | info-dialog | 01, 02 | ✅ Done | 5/5 | [PHASE_03__info-dialog.md](PHASE_03__info-dialog.md) |
| 04 | card-menu-entry | 03 | ✅ Done | 4/4 | [PHASE_04__card-menu-entry.md](PHASE_04__card-menu-entry.md) |
| 05 | player-menu-entry | 03 | ✅ Done | 4/4 | [PHASE_05__player-menu-entry.md](PHASE_05__player-menu-entry.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All four strategic §6 items are Resolved with artifacts under `research/`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched; the capability is recorded in `docs/ALL_FEATURES.jsonl` as `streams.about-channel`.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2593 records, all four new classes carrying a role.
- [ ] `/spec-check S1474` returns `Verified` - gated on the device test; the ticket is `BlockNeedUserTest`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1474`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-08 - All six phases executed by `/spec-dev`. Ticket moved to `BlockNeedUserTest` with three `Timber.d("S1474: ..")` probes. Two plan corrections are recorded in place rather than worked around: Phase 04's menu steps were written before S1424 unified the row and tile menus behind `StreamActionCatalog`, and Phase 05 needed a two-line read on `PlayerViewModel` that its Files Touched did not list.
