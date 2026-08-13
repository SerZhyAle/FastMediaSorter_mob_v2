# Tactical Plan: S0618 - landscape_settings_density_alignment

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Research inputs:** [`research/01__container-mechanism.md`](research/01__container-mechanism.md)
**Feature:** Плотность и выравнивание ландшафтных настроек
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-06-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | row-primitives | - | ✅ Done | 2/2 | [PHASE_01__row-primitives.md](PHASE_01__row-primitives.md) |
| 02 | general-pilot | 01 | ✅ Done | 3/3 | [PHASE_02__general-pilot.md](PHASE_02__general-pilot.md) |
| 03 | media-tab | 02 + owner pilot sign-off | ✅ Done | 4/4 | [PHASE_03__media-tab.md](PHASE_03__media-tab.md) |
| 04 | playback-streams-other | 02 + owner pilot sign-off | ✅ Done | 3/3 | [PHASE_04__playback-streams-other.md](PHASE_04__playback-streams-other.md) |
| 05 | operations-destinations | 02 + owner pilot sign-off | ✅ Done | 1/1 | [PHASE_05__operations-destinations.md](PHASE_05__operations-destinations.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research blockers - strategic §6 item 1 (container mechanism) Resolved via [`research/01__container-mechanism.md`](research/01__container-mechanism.md).

> **Pilot gate (not a research blocker):** CLEARED 2026-06-23 - owner approved the General pilot landscape direction via `/spec-all`, unblocking Phases 03-05. Final on-device landscape screenshot review per tab is the remaining `BlockNeedUserTest` gate.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every modified file. (batched via `close-and-log.ps1 -DevLogs`)
- [x] `dev/CATALOG/app_v2.jsonl` regenerated. (`catalog_sync.ps1 -Module app_v2` exit 0)
- [x] `scripts/quality/assert-settings-doc-sync.ps1` green (layout reflow does not alter the settings manifest; no false positive).
- [ ] `/spec-check S0618` returns `Verified`. (pending on-device landscape verification - ticket parked `BlockNeedUserTest`)
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0618`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-23 - Owner approved General pilot (pilot gate cleared). Phases 03-06 executed via `/spec-all`: left-aligned music/photos/player blocks + scheduled-notification button + empty-state placeholders (R5); packed audio/video top toggles and playback file-op row (R1/R2); documents/other/streams confirmed already compliant; ARCHITECTURE.md R3 rule + catalog regen + gates green. Status -> `BlockNeedUserTest` for on-device landscape review.
