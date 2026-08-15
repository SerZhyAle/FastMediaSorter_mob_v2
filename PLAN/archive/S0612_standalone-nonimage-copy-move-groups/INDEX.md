# Tactical Plan: S0612 - standalone-nonimage-copy-move-groups

**Strategic spec:** [`../S0612_standalone-nonimage-copy-move-groups.md`](../S0612_standalone-nonimage-copy-move-groups.md)
**Research inputs:** [`research/01__host-wiring-inventory.md`](research/01__host-wiring-inventory.md)
**Feature:** Copy/Move destination groups in the standalone audio / document / text players
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (BlockNeedUserTest - device verification pending)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec. Reuses the assets generalised by S0610 (no changes to `DestinationButtonsManager` / `StandaloneFileOperationsHandler` / the shared include).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | audio-copy-move-groups | - | ✅ Done | 5/5 | [PHASE_01__audio-copy-move-groups.md](PHASE_01__audio-copy-move-groups.md) |
| 02 | document-copy-move-groups | - | ✅ Done | 5/5 | [PHASE_02__document-copy-move-groups.md](PHASE_02__document-copy-move-groups.md) |
| 03 | text-copy-move-groups | - | ✅ Done | 5/5 | [PHASE_03__text-copy-move-groups.md](PHASE_03__text-copy-move-groups.md) |
| 04 | docs-catalog-inventory | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-inventory.md](PHASE_04__docs-catalog-inventory.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01-03 are mutually independent (distinct hosts + distinct layout files). They follow one identical recipe; only the per-host hook point and the audio playback nuance differ. Phase 04 depends on all three.

---

## Shared recipe (applies to each host phase)

Every host phase performs the same 7 wiring actions, sourced verbatim from the S0610 reference host `PhotoVideoStandaloneActivity`:

1. Inject `GetDestinationsUseCase` (`fileOperationUseCase` is already injected and already passed to the file-ops handler in all three hosts - do NOT re-add it).
2. Add `<include android:id="@+id/bottomPanelsContainer" layout="@layout/player_bottom_panels_container_content" />` as the last child of the root vertical `LinearLayout`, after `mediaContentArea`, in BOTH the portrait and landscape layout.
3. Lazily construct `DestinationButtonsManager(root = binding.root, ...)`.
4. Implement the inline `DestinationButtonsCallback`.
5. Add the `OpenDocumentTree` ActivityResult launcher + `pendingCustomPathOp` field.
6. Migrate the nav-bottom inset listener from `mediaContentArea` to `bottomPanelsContainer`.
7. Call `destinationButtonsManager.populateDestinationButtons()` once per shown file at the host's populate hook.

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see [`research/01__host-wiring-inventory.md`](research/01__host-wiring-inventory.md)). No blockers - Phase 01 may start. Reusable S0610 assets confirmed present in the working tree.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 introduces a new perceived capability; populated at release time via `/skill-release` from the `ALL_FEATURES` diff (do not hand-edit per-spec).
- [ ] `docs/ALL_FEATURES.jsonl` records the delivered capability (one record, EN) via `scripts/all_features/add.ps1`.
- [ ] `dev/CHANGELOG.md` has an entry per logical change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0612` returns `Verified` (or `BlockNeedUserTest` pending device test).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0612`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-all` (F2).
