# Phase 04 - Bluetooth indicator

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Fill the `trayBluetooth` slot from a permission-free, subscription-based read of the Bluetooth state.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `research/03__bluetooth-state-without-permission.md` read - it fixes the source and the fallback.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTrayBluetoothMonitor.kt` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | Modified | ≤ 30 added |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1 added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1 added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1 added |

---

## Steps

### Step 04.1 - Read the Bluetooth state without a permission

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTrayBluetoothMonitor.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `LauncherTrayBluetoothMonitor(context)` exposing `fun state(): Flow<Boolean?>` built with
> `callbackFlow`: emit the current value of `Settings.Global.BLUETOOTH_ON`, register a `ContentObserver` on
> `Settings.Global.getUriFor(Settings.Global.BLUETOOTH_ON)` and re-emit on every change, and unregister it in
> `awaitClose`. `null` means the state could not be read at all. When the settings read throws or the row is
> absent, fall back once to `BluetoothAdapter.getDefaultAdapter()?.isEnabled`, treating a `SecurityException`
> from it as `null` rather than as `false`. Log the fallback with `Timber.w`, never with `android.util.Log`.

**Why:**

Strategic §3.2 requires the tray's sources to be subscription-based rather than polled, and research item §6.3
established that the settings row carries the state with no permission at all, so the tray gains an indicator
without touching the permission set.

**Verification:**

- `Glob` - `.../ui/launcher/tray/LauncherTrayBluetoothMonitor.kt` exists.
- `Grep` - `Settings.Global.BLUETOOTH_ON` matches in the file.
- `Grep` - `awaitClose` matches once and `unregisterContentObserver` appears inside it.
- `Grep` - `Log\.d\(` and `GlobalScope` return zero hits in the file.
- `Grep` - `BLUETOOTH_CONNECT` returns zero hits across every `app_v2/src/**/AndroidManifest.xml`. Narrowed
  from "across `app_v2/src`" during execution: the KDoc has to name the permission to explain why the adapter
  is only the fallback, and a predicate that forbids the word in prose measures the comment, not the manifest.

**Status:** `[x]` done

---

### Step 04.2 - Add the spoken description

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `launcher_tray_bluetooth_on` in EN/RU/UK with one `set-android-string.ps1 -Action add` call, wording it as
> the state the icon announces. Check it against `docs/COMMUNICATION_POLICY.md` §2 and §6 first.

**Why:**

Strategic §3.3 requires a `contentDescription` on every tray icon, because an icon-only indicator is otherwise
silent to a screen reader.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_tray_bluetooth"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 04.3 - Show the icon only while Bluetooth is on

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Collect the monitor's flow with `collectOnLifecycle` only while the tray's status content is visible and the
> Bluetooth switch is on. Set `trayBluetooth` to `ic_bluetooth` with the `launcher_tray_bluetooth_on`
> description when the state is `true`; make the view `gone` when the state is `false` or `null`.

**Why:**

Strategic §3.4 hides the icon while the adapter is off, matching the Android status bar that §3.3 names as the
reference for this row, and ADR-1 keeps an unreadable state out of the tray rather than drawing it as off.

**Verification:**

- `Grep` - `LauncherTrayBluetoothMonitor` matches in the file.
- `Grep` - `trayBluetooth` is set `gone` on both the `false` and the `null` branch.
- `Grep` - `collectOnLifecycle` is used; a bare `lifecycleScope.launch` collect returns zero hits.

**Status:** `[x]` done

---

### Step 04.4 - Prove the slot obeys its switch

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Confirm the collection is torn down when the Bluetooth switch goes off, not merely hidden, and that turning it
> back on re-subscribes without restarting the activity.

**Why:**

Strategic §5.2 states an invisible indicator holds neither receiver nor callback, so a hidden-but-still-collecting
Bluetooth observer would break the rule the whole tray is built on.

**Verification:**

- `Grep` - the Bluetooth collection is started from inside the composition branch, not unconditionally in `bind()`.
- `Grep` - `Job` or the collection handle is cancelled on the switched-off path.

**Status:** `[x]` done - required a project-tooling fix inside the step (CLAUDE.md Rule 13):
`LifecycleOwner.collectOnLifecycle` returned `Unit`, so no caller could ever cancel early. It now returns the
`Job`; every existing caller ignores the value and is unaffected.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Step Log

- 2026-08-06 - Step 04.1 Verification 5/5 PASS, with one predicate narrowed during execution (see the step). Files: `LauncherTrayBluetoothMonitor.kt` (New, 61 LOC). No permission was declared.
- 2026-08-06 - Step 04.2 Verification 2/2 PASS. `launcher_tray_bluetooth_on` added in EN/RU/UK.
- 2026-08-06 - Step 04.3 Verification 3/3 PASS. Files: `LauncherTrayManager.kt` (+30 LOC).
- 2026-08-06 - Step 04.4 Verification 2/2 PASS. The prompt could not be satisfied as written: `LifecycleOwner.collectOnLifecycle` returned `Unit`, so no caller could cancel a collection early. Fixed the helper inside the step per CLAUDE.md Rule 13 - it now returns the `Job`, and every existing caller ignores the value.
- 2026-08-06 - Phase close: `.\a.ps1 fk` exit 0, `post-change.ps1 -ScopeToFile` verdict `PASS`. The compile surfaced a deprecation on `BluetoothAdapter.getDefaultAdapter()`; per CLAUDE.md Rule 7 it was replaced with the `BluetoothManager` system service in the same phase, and the recompile is warning-free with the scoped detekt gate at exit 0.
- 2026-08-06 - Phase-boundary audit (Layers 1-3): no P0/P1 findings. Layer 2 - the `callbackFlow` unregisters its `ContentObserver` in `awaitClose`, and the collection is additionally bounded by `repeatOnLifecycle(STARTED)` inside `collectOnLifecycle`. Layer 3 - `bluetoothJob` is the only new long-lived handle and it is cancelled and nulled on the switched-off path; the observer holds a `Handler` on the main looper, not a view. Layer 1 - the monitor holds no view reference at all, which is what keeps it reusable for the S1431 top strip.

---

## Handoff Notes to Next Phase

The tray now has one indicator that resolves to "absent" rather than "empty", which is the shape Phase 05 copies
for the two SIM slots. No new permission was added.

---

## Rollback Plan

Revert phase commit(s) - view state only, no persisted data and no permission change.
