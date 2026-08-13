# Tactical Plan: S1036 - gesture-launch-app-selection

**Strategic spec:** [`../S1036_gesture-launch-app-selection.md`](../S1036_gesture-launch-app-selection.md)
**Research inputs:** [`research/01__per-slot-payload-and-app-picker.md`](research/01__per-slot-payload-and-app-picker.md)
**Feature:** Launch a user-chosen installed app from an edge gesture
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 5 done
**Last updated:** 2026-08-10

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dispatcher-app-launch | - | ✅ Done | 2/2 | [PHASE_01__dispatcher-app-launch.md](PHASE_01__dispatcher-app-launch.md) |
| 02 | app-slot-strings | - | ✅ Done | 2/2 | [PHASE_02__app-slot-strings.md](PHASE_02__app-slot-strings.md) |
| 03 | app-picker-wiring | 02 | 🚧 In Progress | 2/2 | [PHASE_03__app-picker-wiring.md](PHASE_03__app-picker-wiring.md) |
| 04 | inline-app-row | 02, 03 | ✅ Done | 3/3 | [PHASE_04__inline-app-row.md](PHASE_04__inline-app-row.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All four strategic §6 items are `Resolved` as of 2026-08-09 - three of them by code rather than by decision, see the research artifact.

---

## Facts this plan is built on

These come from `research/01__per-slot-payload-and-app-picker.md` and decide phase content. A step contradicting one of them is a planning bug, not an implementation detail.

1. The twelve per-slot payload fields, their DataStore keys, the `AppSettings` resolver and the `applyPayload` mutator **already exist** - S1038 introduced them naming this ticket as the second consumer. No new setting, no new key, no migration.
2. `ScreenshotGestureActionDispatcher` already receives the slot identity and already reads a payload for the URL action, so the app-launch branch mirrors an existing branch rather than inventing a path.
3. `AppPickerDialogFragment.newInstance(requestKey: String)` exists specifically so a host outside the panel editor can reuse the picker, and today has zero call sites. It returns through the Fragment Result API.
4. The `<queries>` block granting package visibility is app-wide and already declared. This ticket adds no manifest permission and no `<queries>` entry.
5. The direction rows do **not** render their payload today, which is why the inline row is new UI and not a reuse of the URL action's transient prompt.
6. Edge gestures are compiled off by default on `standard`; `noLegal` always has them. Device verification runs on `noLegal debug` or on `standard` with `-Pfms.edgeGestureOverlay=on`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **not** touched here. Strategic §8 explicitly defers the wording to `/skill-release` from the `ALL_FEATURES` diff; the record this ticket owns is the `docs/ALL_FEATURES.jsonl` row in Phase 05.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1036` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1036`.

---

## Blockers Log

- 2026-08-09 - Phase 03 steps are done but the phase is held open by the S1338 UI-phase screenshot gate. The edge-gesture dialog is compiled off on standard debug, so it cannot be reached on the emulator's current APK. Next: finish Phase 04, build noLegal debug, capture the dialog once, discharge the gate for Phases 03 and 04 together.
- 2026-08-10 - Phases 04 and 05 are done and the ticket is `BlockNeedUserTest`; Phase 03 stays 🚧 for the one reason above, and only for it. No device was attached this run, so the capture is still owed - the device pass that clears the ticket clears Phase 03 with the same screenshot.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
