# Phase 03 — Saved-authorizations help handler

**Strategic spec:** [`../S0142_ui-settings-behaviour-group-regroup.md`](../S0142_ui-settings-behaviour-group-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Wire the new `iconHelpSavedAuthorizations` help icon in `PlaybackSettingsFragment` to show the tooltip dialog, mirroring the existing `iconHelpCameraCapture` handler. No other behaviour changes.

---

## Prerequisites

- [ ] Phase 02 ✅ Done — `iconHelpSavedAuthorizations` exists in both layouts.
- [ ] Phase 01 ✅ Done — `tooltip_saved_authorizations_title` / `_message` exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 620 |

> File is 604 lines — projected >500 after edit → timestamped backup in `temp/` required before editing (Step 03.1).

---

## Steps

### Step 03.1 — Add the `iconHelpSavedAuthorizations` click listener

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> First create a timestamped backup of `PlaybackSettingsFragment.kt` under `temp/` (file >500 LOC). Then, next to the existing `binding.iconHelpCameraCapture.setOnClickListener { … }` block, add an equivalent listener for `binding.iconHelpSavedAuthorizations` that calls `com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(childFragmentManager, R.string.tooltip_saved_authorizations_title, R.string.tooltip_saved_authorizations_message)` — match the exact `TooltipDialog.show(...)` argument shape used by the surrounding handlers. Insert `Timber.d("S0142: saved-authorizations help tooltip opened")` as the first statement inside the new listener. Do not modify the existing `binding.rowSavedAuthorizations.setOnClickListener { … }` (sub-screen navigation) or the `binding.rowSavedAuthorizations.isEnabled = settings.linkAutoDownloadEnabled` gating.

**Verification:**

- `Grep -n "iconHelpSavedAuthorizations"` — at least 1 hit in `PlaybackSettingsFragment.kt`.
- `Grep -n "tooltip_saved_authorizations_title"` and `tooltip_saved_authorizations_message` — present in `PlaybackSettingsFragment.kt`.
- `Grep -n 'Timber.d("S0142:'` — exactly 1 hit in `PlaybackSettingsFragment.kt`.
- `Grep -n "Log\.d\("` — zero hits in `PlaybackSettingsFragment.kt`.
- `Grep -n "rowSavedAuthorizations.setOnClickListener"` — still exactly 1 hit (unchanged navigation handler).
- A timestamped copy of the original `PlaybackSettingsFragment.kt` exists under `temp/`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS. Backup `temp/PlaybackSettingsFragment.kt.20260510_142637.bak` created. Added `binding.iconHelpSavedAuthorizations` click listener (lines 151-158) next to `iconHelpCameraCapture` handler, calling `TooltipDialog.show(requireContext(), R.string.tooltip_saved_authorizations_title, R.string.tooltip_saved_authorizations_message)`; `timber.log.Timber.d("S0142: saved-authorizations help tooltip opened")` as first statement. `rowSavedAuthorizations` click + enabled-gating untouched. Dev log recorded.

---

## Phase Done Criteria

- [x] Step 03.1 is `[x] done`.
- [x] Project compiles — `build-debug.PS1` BUILD SUCCESSFUL (standard debug, v2.60.5101.427).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `PlaybackSettingsFragment.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` — deferred to Phase 04.

---

## Handoff Notes to Next Phase

Implementation complete. Phase 04 handles FEATURES trilingual update, catalog regen, dev log consolidation, and final strings audit.

---

## Rollback Plan

Restore `PlaybackSettingsFragment.kt` from the `temp/` backup or revert the phase commit — no persisted state or migration involved.
