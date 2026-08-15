# Phase 03 - Ordered tray slots and the outlined battery number

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Turn the tray into the six ordered slots of the registry, honour each switch, and replace the battery icon and
percent pair with a single outlined number carrying the warning colours and the low-charge blink.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/drawable/launcher_tray_battery_outline.xml` | New | ≤ 20 |
| `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml` | Modified | ≤ 60 changed |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3 changed |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3 changed |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3 changed |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | Modified | ≤ 330 total |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 20 added |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 10 changed |

> `launcher_taskbar.xml` has no `layout-land` counterpart by design - S0404 made it one shared file included by
> both orientations of `activity_launcher_home`, so CLAUDE.md Rule 11 has nothing to mirror here.
> `LauncherHomeActivity.kt` is 783 LOC - back it up under `temp/S1415/` before editing, per CLAUDE.md Rule 5.

---

## Steps

### Step 03.1 - Draw the battery outline

**Files:** `app_v2/src/launcherEnabled/res/drawable/launcher_tray_battery_outline.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `<shape>` drawable: rectangle, transparent solid, rounded corners, a thin stroke coloured
> `?attr/colorOnSurface`. It is the frame around the charge number, so it must tint with the view's
> `backgroundTintList` when the charge crosses a warning threshold.

**Why:**

Strategic §2 goal 2 asks for the charge as a number in an outline, with neither the percent sign nor a battery
pictogram, following the modern Android manner the owner named in §3.1.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/drawable/launcher_tray_battery_outline.xml` exists.
- `Grep` - `<stroke` matches once and `="#` returns zero hits.

**Status:** `[x]` done

---

### Step 03.2 - Replace the battery pair with the numeric view and add the empty slots

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Rebuild the children of `trayContainer` into exactly six views in this order: `trayClock` (unchanged
> `TextClock`), `trayBluetooth` (`ImageView`, `visibility="gone"`), `traySim1` and `traySim2` (`ImageView`,
> `visibility="gone"`), `trayNetwork` (unchanged `ImageView`), and `trayBatteryLevel` - a `TextView` with
> `launcher_tray_battery_outline` as its background, `?attr/colorOnSurface` text colour and the same text size
> the old percent view used. Delete `trayBattery` and `trayBatteryPercent`. Keep `launcher_tray_icon_size` for
> every icon and the existing end margins.

**Why:**

Strategic §3.3 fixes the left-to-right order as clock, Bluetooth, SIM1, SIM2, network type, battery, with the
clock first and the battery last at the right edge exactly as today.

**Verification:**

- `Grep` - `trayClock`, `trayBluetooth`, `traySim1`, `traySim2`, `trayNetwork`, `trayBatteryLevel` each match
  once inside `trayContainer` and in that order.
- `Grep` - `trayBatteryPercent` and `android:id="@+id/trayBattery"` return zero hits.
- `Grep` - `="#` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 03.3 - Retire the percent-formatted string

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `launcher_tray_battery_value` with the value `%1$d` in all three locales via one
> `set-android-string.ps1 -Action add` call, then remove `launcher_tray_battery_percent` with
> `-Action remove` once nothing references it. Leave `launcher_tray_battery_level` and
> `launcher_tray_battery_charging` alone - they remain the spoken description.

**Why:**

Strategic §2 goal 2 removes the percent sign from the tray, and CLAUDE.md Rule 20 requires the string key that
carried it to go in the same change rather than linger as dead weight.

**Verification:**

- `Grep` - `launcher_tray_battery_percent` returns zero hits across `app_v2/src`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_tray_battery"` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Publish the composition from the ViewModel

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Expose a `StateFlow<LauncherTrayComposition>` built from `settingsRepository.getSettings()` through the Phase 01
> companion factory, mirroring how `replaceSystemStatusArea` is already derived in this class.

**Why:**

Strategic §5.2 routes the switch through the same path as every other tray input - the setting decides only
whether an indicator is subscribed, and the ViewModel is where the launcher's settings already become flows.

**Verification:**

- `Grep` - `LauncherTrayComposition` matches in the file.
- `Grep` - the new property is a `StateFlow`, not a `Flow` built per collector.

**Status:** `[x]` done

---

### Step 03.5 - Render per-indicator visibility and the battery number

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Extend `bind()` to take the composition flow and collect it with `collectOnLifecycle`. Each of the six views
> is visible only when the tray's status content is on **and** its own switch is on; the Bluetooth and SIM slots
> additionally stay `gone` until Phase 04 and Phase 05 give them a value. Rewrite `renderBattery` to put the bare
> number into `trayBatteryLevel` via `launcher_tray_battery_value`, keeping the existing charging-aware
> `contentDescription`. Register the battery receiver and the network callback only while their own indicator is
> enabled, so a switched-off indicator holds no receiver.

**Why:**

Strategic §5.2 states an invisible indicator holds neither receiver nor callback, and strategic §11 criterion 1
requires a switched-off indicator to leave no gap in the row.

**Verification:**

- `Grep` - `LauncherTrayComposition` matches in the file.
- `Grep` - `registerBattery` is called only from a branch that tests the battery switch.
- `Grep` - `trayBatteryPercent` returns zero hits.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 03.6 - Colour the thresholds and blink below ten

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Tint `trayBatteryLevel`'s text and `backgroundTintList` from named constants: below 30 use
> `@color/warning_color`, below 15 use `@color/error_color`, at or above 30 use `?attr/colorOnSurface`. Below 10
> also run an alpha `ObjectAnimator` with `REVERSE` repeat, started when that level is rendered while the tray is
> visible and cancelled in `onStop`, when the battery indicator is switched off, and when the level rises back to
> 10. Pass the composition flow from `LauncherHomeActivity` into the tray manager's `bind()` call. Declare the
> three thresholds as named companion constants, not inline literals.

**Why:**

Strategic §2 goal 3 sets the three thresholds, and strategic §3.2 makes the blink the only animation in the tray
and requires it to stop whenever the panel is not visible, because a repaint loop at low charge costs battery
exactly when there is least of it.

**Verification:**

- `Grep` - three named threshold constants with the values 30, 15 and 10 exist in the file.
- `Grep` - `cancel()` on the animator appears in `onStop` and in the switched-off branch.
- `Grep` - `ObjectAnimator` matches in the file and `GlobalScope` returns zero hits.
- `Grep` - `="#` returns zero hits in every file this step touched.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Step Log

- 2026-08-06 - Step 03.1 Verification 3/3 PASS. Files: `launcher_tray_battery_outline.xml` (New, 16 LOC).
- 2026-08-06 - Step 03.2 Verification 3/3 PASS. Files: `launcher_taskbar.xml` (+27 LOC net). Slot order confirmed by line number: clock 93, Bluetooth 105, SIM1 114, SIM2 123, network 132, battery 139. Note on step atomicity: this step alone leaves the tree uncompilable, because `LauncherTrayManager` still referenced the deleted views until Step 03.5 - the phase, not the step, is the mergeable unit here.
- 2026-08-06 - Step 03.3 Verification 2/2 PASS, completed out of order: `launcher_tray_battery_value` was added first, and `launcher_tray_battery_percent` removed only after Step 03.5 dropped its last reference, exactly as the prompt requires.
- 2026-08-06 - Step 03.4 Verification 2/2 PASS. Files: `LauncherHomeViewModel.kt` (+9 LOC).
- 2026-08-06 - Step 03.5 Verification 4/4 PASS. Files: `LauncherTrayManager.kt`. `registerBattery`/`registerNetwork` are now called only from the per-indicator branch of `apply()`.
- 2026-08-06 - Step 03.6 Verification 4/4 PASS. Files: `LauncherTrayManager.kt`, `LauncherHomeActivity.kt`. `stopBlink()` is reached from three places: `onStop`, the switched-off branch of `apply()`, and the level rising back to 10.
- 2026-08-06 - Phase close, two passes. `.\a.ps1 fc` exit 0. First `post-change.ps1` FAILED on a detekt `ImportOrdering` finding attributable to this change (`android.content.res.ColorStateList` and `android.view.View` inserted out of lexicographic order); imports resorted, `.\a.ps1 fk` exit 0, second `post-change.ps1` verdict `PASS`.
- 2026-08-06 - UI phase gate (S1338): placement decision recorded verbatim in strategic §3.3 (owner, 2026-08-06). Screenshot deferred (no device) - this phase's own Done Criteria do not demand it, but the tray order and the battery outline are exactly what the device test owes.
- 2026-08-06 - Phase-boundary audit (Layers 1-3; the phase touches lifecycle and listener ownership): no P0/P1 findings. Layer 2 - both flows are collected with `collectOnLifecycle`, and the double-registration that `onStart` plus a fresh flow emission could cause is absorbed by the two `*Registered` flags that already existed. Layer 3 - the animator holds the view, and it is cancelled and nulled in `onStop`, which always precedes `onDestroy`; the blink restarts by itself on the next `onStart` because `registerBattery` re-reads the sticky broadcast. Layer 1 - the file is 285 LOC, well under the ceiling, and no logic moved into the activity.

---

## Handoff Notes to Next Phase

`trayBluetooth`, `traySim1` and `traySim2` exist in the layout and are wired to their switches, but no source
fills them - they stay `gone`. Phases 04 and 05 add only a source and a render for their own slot; neither
touches the layout order again.

---

## Rollback Plan

Revert phase commit(s) - the tray is view state only, so nothing persists across the revert except the Phase 01
switches, which keep their defaults.
