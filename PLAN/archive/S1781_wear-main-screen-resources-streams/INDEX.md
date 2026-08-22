# Tactical Plan: S1781 - wear-main-screen-resources-streams

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Research inputs:** none - all strategic §6 items resolved inline (quiz 2026-08-18, `/ui-clarify` 2026-08-18, emulator measurement 2026-08-18)
**Feature:** Wear main screen sections, view mode, resource selection
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 8 / 8 done
**Last updated:** 2026-08-18

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | wear-view-foundations | - | ✅ Done | 4/4 | [PHASE_01__wear-view-foundations.md](PHASE_01__wear-view-foundations.md) |
| 02 | home-section-catalog | 01 | ✅ Done | 5/5 | [PHASE_02__home-section-catalog.md](PHASE_02__home-section-catalog.md) |
| 03 | home-view-modes-command-bar | 01, 02 | ✅ Done | 6/6 | [PHASE_03__home-view-modes-command-bar.md](PHASE_03__home-view-modes-command-bar.md) |
| 04 | phone-resource-selection | - | ✅ Done | 4/4 | [PHASE_04__phone-resource-selection.md](PHASE_04__phone-resource-selection.md) |
| 05 | resources-page-and-transfer | 01, 02, 04 | ✅ Done | 4/4 | [PHASE_05__resources-page-and-transfer.md](PHASE_05__resources-page-and-transfer.md) |
| 06 | screen-keep-awake | 01, 03 | ✅ Done | 3/3 | [PHASE_06__screen-keep-awake.md](PHASE_06__screen-keep-awake.md) |
| 07 | companion-settings-mirror | 01, 06 | ✅ Done | 3/3 | [PHASE_07__companion-settings-mirror.md](PHASE_07__companion-settings-mirror.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 research item carries `Status: Resolved`, and both UI placement decisions this plan needs are recorded in the strategic spec as owner rulings dated 2026-08-18.

---

## Scope boundaries carried from the strategic spec

- The Streams **screen**, the streaming capability, its command bar, filters, channel ordering and playing-radio behaviour belong to **S1708**. This plan produces only the Streams entry in the section catalog - the entrance S1708 §3.3 left to the owner.
- The Apps **content** belongs to **S1710**. This plan produces only the Apps entry in the section catalog.
- Transferring one manual stream from phone to watch belongs to **S1799**.
- The file-list view mode inside a resource belongs to **S1730** and uses its own stored value. Only the column-fit rule from Phase 01 is shared.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 carries a FEATURES sentence, so `/skill-release` picks it up from the `ALL_FEATURES` diff; no per-spec edit here.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/wear.jsonl` and `dev/CATALOG/app_v2.jsonl` regenerated - public API changed in both modules.
- [ ] `/spec-check S1781` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1781`.

---

## Blockers Log

- 2026-08-18 - All eight phases done; closure facade run over the 57-file changed set, verdict PASS with one advisory. The advisory is `new-lexeme-count`: the repo-wide backlog of strings not yet in all thirteen locales, which Rule 30 defers to the pre-release sweep on purpose and which the facade itself could not attribute to this change - the entries it named belong to S1686, not here.
- 2026-08-18 - One question this ticket could not settle from code, carried to the audit: the phase-06 boundary audit recorded two `KeepScreenOnEffect` instances either side of the player boundary and argued from Compose disposal order that the flag survives the hand-off. That is reasoning, not measurement. It needs a watch with each of the three players opened; nothing in the source can answer it.

---

## Change Log

- 2026-08-18 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-18 - Phases 04-08 executed; 03 reopened for step 03.6 and closed again. Every step is `[x]` and every phase `✅ Done`, each closed through `post-change.ps1` (the final facade run over the 19-file set returned `post-change: PASS WITH ADVISORIES (2)`, both advisories resolved or Rule-30 expected). Handover note, because the ticket lease changed hands mid-run: **the only work left is the terminal transition, not implementation.** It needs, in this order, (1) `Timber.d("S1781: ..")` probe tags at the changed flow entries - HomeScreen, NetworkSourcesScreen, KeepScreenOnEffect, WearResourceSelectionActivity, SendResourcesToWatchUseCase, ApplyWearSettingsUseCase - (2) one build validating code plus tags, (3) status to `BlockNeedUserTest`. No tags are in the tree yet: `grep 'Timber.d("S1781:'` returns 0. The reason the terminal is `BlockNeedUserTest` and not `Implemented` is recorded per phase: the watch emulator is unpaired, so the Resources list-vs-grid over real sources, keep-awake actually holding the screen in the three players, long-press delete on a grid cell, and the Companion settings round trip are all unproven and need the paired watch.
