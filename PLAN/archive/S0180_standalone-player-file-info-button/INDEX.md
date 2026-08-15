# Tactical Plan: S0180 — standalone-player-file-info-button

**Strategic spec:** [`../S0180_standalone-player-file-info-button.md`](../S0180_standalone-player-file-info-button.md)
**Feature:** "File Info" button in Standalone Player (portrait + landscape parity with normal player)
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-05-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Research Resolved (was §6 Open)

Both §6 questions resolved before phase authoring:

1. **Landscape in normal player** — `btnInfoCmd` is present in `res/layout-land/activity_player_unified.xml` (line 105). Normal player wires it the same way as portrait via `CommandPanelController`. No overflow-only fallback — the button exists in both layouts.

2. **Standalone layout** — `StandalonePlayerActivity` uses `ActivityPlayerUnifiedBinding`, i.e., the same `activity_player_unified.xml` (portrait and landscape). No separate layout. `btnInfoCmd` is already present in both orientations — no XML work required.

**Current situation:** in `StandalonePlayerActivity.setupFileOperationButtons()` (lines 756–759), `btnInfoCmd` is repurposed to "Open in FMS" (icon `ic_open_in_browse`, contentDescription `open_in_fms`, click → `openInFms()`). The keyboard handler `onShowFileInfo` (line 313) shows a minimal `MaterialAlertDialogBuilder` with file name and path — not `FileInfoDialog`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | file-info-wiring | — | ✅ Done | 4/4 | [PHASE_01__file-info-wiring.md](PHASE_01__file-info-wiring.md) |
| 02 | open-in-fms-relocation | 01 | ✅ Done | 2/2 | [PHASE_02__open-in-fms-relocation.md](PHASE_02__open-in-fms-relocation.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all §6 research items resolved (see "Research Resolved" above).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update required (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` changes.
- [ ] `/spec-check S0180` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/3 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0180`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-13 — Initial tactical plan authored by `/spec-tech`.
