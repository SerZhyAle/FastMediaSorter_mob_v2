# Tactical Plan: S1216 - device-profile-preset-matrix-coverage

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Research inputs:** [`research/01__matrix-coverage-inventory.md`](research/01__matrix-coverage-inventory.md)
**Feature:** Device profile preset matrix coverage
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | registry-and-gate-rules | - | ✅ Done | 5/5 | [PHASE_01__registry-and-gate-rules.md](PHASE_01__registry-and-gate-rules.md) |
| 02 | applier-branches | 01 | ✅ Done | 5/5 | [PHASE_02__applier-branches.md](PHASE_02__applier-branches.md) |
| 03 | matrix-fill | 01, 02 | ✅ Done | 6/6 | [PHASE_03__matrix-fill.md](PHASE_03__matrix-fill.md) |
| 04 | gate-wiring | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__gate-wiring.md](PHASE_04__gate-wiring.md) |
| 05 | overwrite-warning-count | - | ✅ Done | 4/4 | [PHASE_05__overwrite-warning-count.md](PHASE_05__overwrite-warning-count.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Owner sign-off - received 2026-07-29.** Final per-column values for the matrix, required before Phase 03. Strategic §3.3 "Owner sign-off" makes the column values a separate signature from the §6 decisions; that signature arrived 2026-07-29 and approved all five blocks of [`research/01__matrix-coverage-inventory.md`](research/01__matrix-coverage-inventory.md) §7.1-7.5 without edits. Phases 01, 02, 04 and 05 were never blocked by it. Phase 06 was, indirectly - it depends on all phases, so it was parked behind Phase 03 (Blockers Log, 2026-07-27). Both Phase 03 steps 03.3-03.5 and Phase 06 are now unblocked.

All strategic §6 research items are `Resolved` - no research blockers, and no open pre-implementation blockers remain.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] Public-facing wording: the capability record in `docs/ALL_FEATURES.jsonl` carries the strategic §8 sentence and the Quick Start guide was extended in EN/RU/UK. `docs/FEATURES*.md` deliberately untouched - CLAUDE.md Rule 11 reserves those three for `/skill-release`, which regenerates them from the `ALL_FEATURES` diff.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1216` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1216`.

---

## Blockers Log

- 2026-07-27 - Phase 03 SPLIT. Its structural steps 03.1-03.2 (remove 3 stale rows, append 26 empty rows) carry no value decision and were executed; that alone turned the coverage gate green, which unblocked Phase 04 ahead of the written dependency. Steps 03.3-03.5 write the per-column values and remain blocked on owner sign-off. Next: owner reviews `research/01__matrix-coverage-inventory.md` §7 tables.
- 2026-07-27 - Phase 05 done. Phase 06 (docs + catalog cleanup) is the only remaining phase and depends on all others, so it stays parked until Phase 03 values land.
- 2026-07-29 - Owner sign-off on the per-column values received; strategic §3.3 records it as approving research §7.1-7.5 without edits. Phase 03 steps 03.3-03.5 are unblocked, and with them Phase 06. No blockers remain open.

---

## Change Log

- 2026-07-27 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-27 - Phases 01, 02, 04 implemented and Phase 03 partially, by `/spec-all` under `/spec-next`.
- 2026-07-27 - Phase 05 implemented; `ApplyProfilePresetUseCase` gained `overrideCount()` and `WelcomeEvent.ConfirmProfilePresetReapply` gained `overrideCount`.
- 2026-07-31 - Phases 03 and 06 finished by `/spec-all`. Two signed-off values were mechanically impossible and were snapped to what their screens accept (`epubLineHeight` to the 0.2 slider step, `launcherDensityFactor` to `LAUNCHER_DENSITY_OPTIONS`); both constraints are now gate rules (new step 03.6), proven by a negative run that names field and profile. `docs/FEATURES*.md` replaced by the Quick Start guide as the per-spec user-facing surface, per Rule 11.

