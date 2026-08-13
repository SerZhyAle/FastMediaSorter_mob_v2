# Tactical Plan: S1205 - launcher-host-third-party-pinned-shortcuts

**Strategic spec:** [`../S1205_launcher-host-third-party-pinned-shortcuts.md`](../S1205_launcher-host-third-party-pinned-shortcuts.md)
**Research inputs:** none
**Feature:** Host a third-party app's pinned-shortcut request on the launcher desktop
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pinned-shortcut-seam | - | ✅ Done | 3/3 | [PHASE_01__pinned-shortcut-seam.md](PHASE_01__pinned-shortcut-seam.md) |
| 02 | pin-cell-command | 01 | ✅ Done | 4/4 | [PHASE_02__pin-cell-command.md](PHASE_02__pin-cell-command.md) |
| 03 | accept-pin-usecase | 01, 02 | ✅ Done | 1/1 | [PHASE_03__accept-pin-usecase.md](PHASE_03__accept-pin-usecase.md) |
| 04 | pin-request-activity | 03 | ✅ Done | 2/2 | [PHASE_04__pin-request-activity.md](PHASE_04__pin-request-activity.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 carries no open research items; every §4 decision is closed.

---

## Decisions taken at planning time

Recorded here because implementation must not re-open them.

- **Notice medium is a Toast, not a system notification.** `HomeWidgetSettingsHelper` already answers this for the identical outcome - the same `addCellInFirstFreeSlot` call, the same two strings - with `Toast.makeText`. A system notification can be silently dropped when `POST_NOTIFICATIONS` is denied, and the owner ruled out a dialog, so the notice is the only feedback the flow has.
- **Strings are reused, none added.** `launcher_widget_placed` ("Added to the launcher desktop") and `launcher_widget_no_room` already exist and are already localized; both describe exactly this outcome.
- **"Desktop full" is unreachable.** `addCellInFirstFreeSlot` appends a new row when no existing row has space, so its null answer only means the grid has never been measured (`columns < 1`). Strategic §4's refusal case therefore maps to `launcher_widget_no_room`, exactly as the widget path maps it.
- **A vanished shortcut is marked by the placeholder glyph, not by dimming.** `ResolveLauncherCommandLabelUseCase.appVisual` already sets the precedent for "target gone": keep an identifying caption, swap the icon for `ic_launcher_mode`, keep the visual non-null. A null visual would replace the caption with the generic "Unavailable" string and lose which shortcut the cell was.
- **No `LauncherApps.Callback` is registered.** Strategic §2 anticipated one for liveness, and it is not needed: liveness is a `FLAG_MATCH_PINNED` query inside the existing per-cell resolve, which already runs off the main thread on every desktop emission. Nothing is registered, so `assert-listener-symmetry` has nothing to enforce.
- **Unpinning on cell removal is out of scope.** `pinShortcuts` replaces a package's whole pin set, so removing one pin means rebuilding that set from the desktop - its own work. A pin we no longer show is invisible to the user and does not count against a publisher's shortcut limits.
- **The new activity ships enabled.** Only the HOME component is toggled by `LauncherRoleManager`; the system routes `ACTION_CONFIRM_PIN_SHORTCUT` solely to the current default launcher, so an always-enabled receiver is inert until the role is held.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic spec carries no §8 FEATURES sentence.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed.
- [ ] `/spec-check S1205` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1205`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
