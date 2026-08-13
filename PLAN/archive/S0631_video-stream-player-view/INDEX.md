# Tactical Plan: S0631 - video-stream-player-view

**Strategic spec:** [`../S0631_video-stream-player-view.md`](../S0631_video-stream-player-view.md)
**Research inputs:** [`research/01__player-stream-mode-architecture.md`](research/01__player-stream-mode-architecture.md)
**Feature:** Режим плеера для видео-трансляций
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - awaiting on-device verification (journal: BlockNeedUserTest)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stream-detection | - | ✅ Done | 1/1 | [PHASE_01__stream-detection.md](PHASE_01__stream-detection.md) |
| 02 | command-panel-profile | 01 | ✅ Done | 4/4 | [PHASE_02__command-panel-profile.md](PHASE_02__command-panel-profile.md) |
| 03 | share-stream-link | 01 | ✅ Done | 2/2 | [PHASE_03__share-stream-link.md](PHASE_03__share-stream-link.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Нет. Все §6 research items в стратегической спеке имеют статус Resolved (item 1 - режим-в-существующем,
item 2 - набор контролов следует §0). Phase 01 может стартовать.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` НЕ редактируются per-spec - способность записывается в `docs/ALL_FEATURES.jsonl`
  через `scripts/all_features/add.ps1`; публичный showcase наполняет только `/skill-release`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0631` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0631`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-23 - Implemented Phases 01-04. Journal -> BlockNeedUserTest (on-device check). Deviations from
  the tactical text, all toward a working result:
  - Phase 02.2 landscape: also show the rotation toggle (`btnRotationToggleCmd`) when
    `state.showRotationToggle`, since the owner set includes rotation and §11.5 requires portrait/landscape
    parity. The stream branch hides the full non-stream set via `getOverflowableButtons()` then re-shows the
    allowed subset (robust against future-added buttons), instead of enumerating each hide.
  - Phase 02.3 also suppresses `btnOpenInSeparateWindowCmd` for a stream in portrait/big-buttons (the plan
    listed only slideshow + favorite, but §2 goal 2 requires hiding everything outside the stream set).
  - Phase 03.2 menu filter: the plan's `requiresLocalFile == false` filter would have yielded an empty
    (dead) menu for a video stream, because the only `requiresLocalFile == false` receiver is `keep_text`
    (TEXT-only). Implemented the intent correctly with a new declarative `ShareTarget.textCapable` flag
    (set on `system_share` + `keep_text`) and a `ShareableContent.isTextOnly` predicate, filtering in the
    single `SendToMenuManager.receiversFor` resolution point. For a video stream this resolves to a single
    text-capable receiver (system share), which dispatches straight to the system text chooser carrying the
    stream URL - exactly the owner's "share to any app" requirement, with no dead entries. Side benefit:
    the pre-existing text-editor text-only share now also drops its dead file-only entries.
  - Out of scope, trivial inline fix: `SftpClientTest` constructor arity (missing `networkStateMonitor`
    mock) was failing to compile and blocking the entire unit-test source set; fixed so the planner test
    could run.
