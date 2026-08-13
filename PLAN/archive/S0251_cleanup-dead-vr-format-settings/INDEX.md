# Tactical Plan: S0251 - cleanup-dead-vr-format-settings

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Feature:** Removal of dead VR forced-format settings (UI + domain + data + strings)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Tactical refinement of strategic ADR-3

Strategic ADR-3 prescribed placing the new help icon and strings under the VR source set. Tactical investigation found that the existing S0249 pattern already keeps the VR section header (`headerVr`) and its title string (`vr_settings_block_title`) in `src/main/` - visibility is gated through `VrMediaSectionContract.isAvailable`, not via `BuildConfig`. To preserve consistency with that pattern, the new help icon and its two strings (`settings_3d_vr_help_title`, `settings_3d_vr_help_message`) are placed in `src/main/` alongside the existing header. CLAUDE.md Rule 15 is not violated because the icon's visibility is also driven by `vrMediaSection.isAvailable`, not a `BuildConfig.SUPPORT_*` flag. Strategic ADR-3 stands as guidance for cases without a pre-existing main-side anchor; here the anchor already exists.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ui-layout-cleanup | - | ✅ Done | 5/5 | [PHASE_01__ui-layout-cleanup.md](PHASE_01__ui-layout-cleanup.md) |
| 02 | domain-data-cleanup | 01 | ✅ Done | 4/4 | [PHASE_02__domain-data-cleanup.md](PHASE_02__domain-data-cleanup.md) |
| 03 | coordinator-resolver-simplification | 02 | ✅ Done | 4/4 | [PHASE_03__coordinator-resolver-simplification.md](PHASE_03__coordinator-resolver-simplification.md) |
| 04 | vr-help-icon-addition | - | ✅ Done | 4/4 | [PHASE_04__vr-help-icon-addition.md](PHASE_04__vr-help-icon-addition.md) |
| 05 | strings-arrays-cleanup | 01, 04 | ✅ Done | 3/3 | [PHASE_05__strings-arrays-cleanup.md](PHASE_05__strings-arrays-cleanup.md) |
| 06 | docs-catalog-cleanup | 01-05 | ✅ Done | 5/5 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Both blockers resolved on 2026-05-19 before `/spec-dev` chain.

- [x] **UI placement decision** (§6.2) - RESOLVED 2026-05-19. Owner choice: place `switchPlayerShowFps` and `switchAllowSeparateWindow` inside the existing Video card, as standalone rows under the PNG/JPG snapshot-format block, no subheader. See Phase 01 step 01.3 for the encoded target structure.
- [x] **Help text drafting** (§6.4) - RESOLVED 2026-05-19. Owner choice: short variant (3 sentences). Russian reference text:
  - Title: `О режиме 3D VR`
  - Body: `3D VR — это погружённый просмотр видео и фото на гарнитурах типа Meta Quest 3 и Android XR. На устройствах без поддержки XR-среды переключатель остаётся недоступным. Приложение само определяет формат видео (SBS, OU, 360°, VR180); ручной выбор доступен в диалоге плеера.`
  - EN/UK translations encoded into Phase 04 step 04.3.

The remaining §6 items (6.1 FEATURES.md bullet semantics, 6.3 resolver fate) are resolved inside the tactical phases (Phase 06 and Phase 03 respectively) - they do not require pre-implementation owner input.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - §6.1 research concluded the bullet describes the alive in-player override dialog (`PlaybackControlDialogFragment` with `radioGroup3D` + `radioGroupSpherical3D`), not the removed Settings UI - bullet KEPT in all three locales.
- [x] `dev/CHANGELOG.md` has entries for every modified file via `add_to_dev_log.ps1` (phases 01-06).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`; verified absence of `VrForcedFormatResolver` and `applySettings` post-cleanup.
- [ ] `/spec-check S0251` returns `Verified` - **pending operator on-device test (BlockNeedUserTest)**.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` - **pending**.
- [x] Functionality log entry appended via `scripts/add_to_functionality_log.ps1 -Id S0251 -Op CHANGE` at 2026-05-19 12:02.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent alone.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0251`.

---

## Blockers Log

- 2026-05-19 - RESOLVED: owner placed `switchPlayerShowFps` and `switchAllowSeparateWindow` inside Video card under PNG/JPG block (no subheader).
- 2026-05-19 - RESOLVED: owner picked short variant of help text; Russian wording encoded, EN/UK drafted in Phase 04 step 04.3.

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-19 - Phases 01-05 implemented (UI cleanup, domain/data cleanup, coordinator+resolver simplification, VR help icon addition, strings/arrays cleanup).
- 2026-05-19 - Phase 06 closed: §6.1 resolved (bullet KEPT - describes alive in-player dialog); catalog regenerated; functionality + dev logs appended; two `Timber.d("S0251:")` probes inserted; spec flipped to `BlockNeedUserTest` for on-device verification.
