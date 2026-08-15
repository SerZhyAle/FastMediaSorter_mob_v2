# Phase 02 - One settings toggle per indicator

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Put six switches into the launcher settings dialog, one per indicator, wired to the Phase 01 settings fields in
both orientations.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 8 added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 8 added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 8 added |
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 60 added |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 60 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 60 added |

> The landscape variant of `dialog_launcher_settings.xml` exists and shares one ViewBinding with the portrait
> one, so every new row id must appear in both files (CLAUDE.md Rule 11).

---

## Steps

### Step 02.1 - Add the eight strings in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add six titles and two subtitles with one `set-android-string.ps1 -Action add` call each, so EN/RU/UK stay in
> lockstep: `launcher_settings_tray_clock_title`, `launcher_settings_tray_bluetooth_title`,
> `launcher_settings_tray_sim1_title`, `launcher_settings_tray_sim2_title`,
> `launcher_settings_tray_network_title`, `launcher_settings_tray_battery_title`,
> `launcher_settings_tray_bluetooth_subtitle`, `launcher_settings_tray_sim_subtitle`. The two subtitles state
> that the indicator is hidden when the device cannot report the value - the SIM one also names the phone-state
> permission. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist
> before writing.

**Why:**

Strategic §3.2 requires every new setting label to land in EN/RU/UK in one lockstep call, and strategic §11
criterion 4 requires the switch itself to explain why an indicator can be missing from the tray.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_tray_"` exits 0.
- `Grep` - each of the eight keys matches once per locale file.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.2 - Add the six rows to the portrait dialog

**Files:** `app_v2/src/main/res/layout/dialog_launcher_settings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Insert six `SettingsToggleRow` views directly after `rowLauncherShowTray`, in the order
> `rowLauncherTrayClock`, `rowLauncherTrayBluetooth`, `rowLauncherTraySim1`, `rowLauncherTraySim2`,
> `rowLauncherTrayNetwork`, `rowLauncherTrayBattery`, each with the matching `app:str_title`. Give the Bluetooth
> row and both SIM rows their `app:str_subtitle`. Copy the margin attributes from the neighbouring rows; use no
> literal colour values.

**Why:**

Strategic §3.4 places the six switches directly under the existing tray switch in the tray's own left-to-right
order, and strategic §2 non-goals leave the grouping of that block to S1410 rather than to this ticket.

**Verification:**

- `Grep` - the six row ids each match once in the file.
- `Grep` - the six ids appear after `rowLauncherShowTray` and in the order listed above.
- `Grep` - `="#` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.3 - Mirror the six rows in the landscape dialog

**Files:** `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add the same six rows with the same ids and titles to the landscape variant, following that file's own column
> arrangement rather than copying the portrait tree verbatim.

**Why:**

Both orientations inflate the same generated ViewBinding, so a row present in only one of them makes the
fragment's binding reference null at run time in the other orientation.

**Verification:**

- `Grep` - the six row ids each match once in the landscape file.
- `Grep` - `="#` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.4 - Wire the six rows to settings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `setupRows()` add one `setOnCheckedChangeListener` per new row that writes its Phase 01 field through
> `viewModel.updateSettings`, guarded by `isUpdatingFromSettings` exactly like the existing rows. In
> `observeSettings()` add one `setCheckedSilently` per row.

**Why:**

Strategic §11 criterion 1 requires the tray to change as soon as a switch is flipped, which the existing
apply-immediately wiring of this dialog already delivers for its other rows.

**Verification:**

- `Grep` - `rowLauncherTray` matches 12 times in the file (6 listeners + 6 renders).
- `Grep` - every new listener body contains `isUpdatingFromSettings`.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Step Log

- 2026-08-06 - Step 02.1 Verification 3/3 PASS. Eight keys added in EN/RU/UK through `set-android-string.ps1 -Action add`; `check_strings_localized.ps1 -KeyPrefix launcher_settings_tray_` exit 0. Ten best-effort locales report the keys untranslated - that backlog is S1420, not this ticket.
- 2026-08-06 - Step 02.2 Verification 3/3 PASS. Files: `layout/dialog_launcher_settings.xml` (+47 LOC).
- 2026-08-06 - Step 02.3 Verification 2/2 PASS. Files: `layout-land/dialog_launcher_settings.xml` (+47 LOC). Kept identical to portrait, as that file's own S1088 comment requires.
- 2026-08-06 - Step 02.4 Verification 3/3 PASS. Files: `LauncherSettingsDialogFragment.kt` (+35 LOC).
- 2026-08-06 - Phase close, two passes. First `post-change.ps1 -ScopeToFile` FAILED on a detekt finding attributable to this change: `setupRows` reached 82 lines against a limit of 80. Fixed inside the phase by moving the six new listeners into a private `setupTrayRows()`; the scoped gate then exits 0. `.\a.ps1 fc` exit 0 before the fix, `.\a.ps1 fk` exit 0 after it, second `post-change.ps1` verdict `PASS`.
- 2026-08-06 - UI phase gate (S1338): placement decision is recorded in strategic §3.4 (derived, and labelled as derived rather than attributed to the owner). Screenshot deferred (no device) - `device-ready.ps1` reported `no-device` at session start, and this phase's own Done Criteria do not demand the shot. The rows must be seen on a device before the ticket is called verified.
- 2026-08-06 - Phase-boundary audit (Layer 1; the phase adds no lifecycle, coroutine or Room surface): no findings. The `isUpdatingFromSettings` re-entrancy guard is applied on all six new listeners exactly as on the pre-existing ones, and no business logic entered the fragment.

---

## Handoff Notes to Next Phase

Six switches persist their values in both orientations. The tray does not read them yet - Phase 03 connects
them.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface outside this dialog changed.
