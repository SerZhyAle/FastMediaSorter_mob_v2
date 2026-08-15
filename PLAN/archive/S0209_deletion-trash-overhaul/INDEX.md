# Tactical Plan: S0209 — deletion-trash-overhaul

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Feature:** Full local-deletion and trash refactor
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 85
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | trash-naming-contract | — | ✅ Done | 4/4 | [PHASE_01__trash-naming-contract.md](PHASE_01__trash-naming-contract.md) |
| 02 | switch-all-callers-to-contract | 01 | ✅ Done | 5/5 | [PHASE_02__switch-all-callers-to-contract.md](PHASE_02__switch-all-callers-to-contract.md) |
| 03 | remove-forced-reload-cleanup | 02 | ✅ Done | 3/3 | [PHASE_03__remove-forced-reload-cleanup.md](PHASE_03__remove-forced-reload-cleanup.md) |
| 04 | manage-media-fallback | 02 | ✅ Done | 4/4 | [PHASE_04__manage-media-fallback.md](PHASE_04__manage-media-fallback.md) |
| 05 | consolidate-mediastore-delete | — | ✅ Done | 3/3 | [PHASE_05__consolidate-mediastore-delete.md](PHASE_05__consolidate-mediastore-delete.md) |
| 06 | restore-and-clear-trash-button | 02 | ✅ Done | 3/3 | [PHASE_06__restore-and-clear-trash-button.md](PHASE_06__restore-and-clear-trash-button.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — updated only because strategic §8 explicitly states a one-sentence update may be added for the new TTL behaviour (see Phase 07).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated.
- [ ] `/spec-check S0209` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0209`.

---

## Blockers Log

- 2026-05-15 — Gradle/KAPT stalls during `:app_v2:kaptGenerateStubsStandardDebugKotlin`, blocking the final executable proof for Phase 06.3 and the final `/build` gate in Phase 07. IDE diagnostics on the touched S0209 files are clean.
- 2026-05-15 — Resolved: `TrashMetadata` switched to Gson; `RestoreDeletedUseCaseTest` passes. `BrowseFileObserverManager` and `BrowseUndoManager` hardcoded `.trash_` literals replaced with `TrashFolderContract.matchesTrashSegment()`. Compile confirmed: `compileStandardDebugKotlin` BUILD SUCCESSFUL.

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-15 — Progress synced after Phases 02–05 implementation, Phase 07 docs/log updates, and the Phase 06.3 restore-test follow-up. Final executable validation remains blocked by the Gradle/KAPT stall above.
