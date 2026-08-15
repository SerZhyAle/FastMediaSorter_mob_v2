# Phase 05 - SIM1 and SIM2 signal indicators

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Fill the two SIM slots with per-subscription signal level, behind the runtime `READ_PHONE_STATE` request the
owner authorised, with a denied permission or an absent SIM producing no slot at all.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/AndroidManifest.xml` | Modified | ≤ 10 changed |
| `app_v2/src/launcherEnabled/res/drawable/launcher_tray_signal_level.xml` | New | ≤ 20 |
| `app_v2/src/launcherEnabled/res/drawable/ic_signal_cellular_bar_0.xml` .. `_4.xml` | New (5 files) | ≤ 20 each |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTraySimSignalMonitor.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | Modified | ≤ 40 added |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 30 added |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 2 added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 2 added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 2 added |

> `LauncherHomeActivity.kt` is 783 LOC - back it up under `temp/S1415/` before editing, per CLAUDE.md Rule 5.
> The permission is declared in the `launcherEnabled` manifest, not the main one, so the flavors that do not
> mount this source set never inherit it.

---

## Steps

### Step 05.1 - Declare READ_PHONE_STATE for the launcher source set

**Files:** `app_v2/src/launcherEnabled/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `<uses-permission android:name="android.permission.READ_PHONE_STATE" />` and rewrite the header comment,
> which currently states this manifest adds no permissions: record that S1415 added exactly this one, that it is
> requested at run time, and that no `<uses-feature>` accompanies it. Add no `android.hardware.telephony`
> declaration.

**Why:**

Strategic §3.2 records the owner lifting the tray's zero-new-permissions rule on 2026-08-05 and confirms
`READ_PHONE_STATE` carries no implied feature requirement, so Play device reach is unaffected and declaring
`<uses-feature>` would narrow it for nothing.

**Verification:**

- `Grep` - `uses-permission android:name="android.permission.READ_PHONE_STATE"` matches once in the
  launcherEnabled manifest and `READ_PHONE_STATE` zero times in the main manifest.
- `Grep` - `<uses-feature` naming `android.hardware.telephony` returns zero hits across `app_v2/src`.
  Narrowed from a bare token search during execution, for the same reason as Step 04.1: the header comment
  has to name the feature to record why it is deliberately absent.
- `Grep` - the header comment no longer claims the file adds no permissions.

**Status:** `[x]` done

---

### Step 05.2 - Add the signal-level artwork

**Files:** `app_v2/src/launcherEnabled/res/drawable/launcher_tray_signal_level.xml`, `.../ic_signal_cellular_bar_0.xml` .. `_4.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add five vector drawables for signal bars 0 through 4, sized like the existing `ic_signal_cellular`, and a
> `<level-list>` mapping levels 0..4 onto them so the slot renders with `setImageLevel`. Use `?attr/colorOnSurface`
> or a theme-aware tint, never a literal colour.

**Why:**

`SignalStrength.getLevel()` reports 0..4, so a single glyph could only show that a SIM exists, not the state the
owner asked to see in strategic §1.

**Verification:**

- `Glob` - all six drawable files exist.
- `Grep` - `<level-list` matches once and contains five `<item` entries with `android:maxLevel` 0..4.
- `Grep` - `="#` returns zero hits across the six files.

**Status:** `[x]` done

---

### Step 05.3 - Add the spoken descriptions

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `launcher_tray_sim_signal` taking the slot number and the level (`%1$d`, `%2$d`) and
> `launcher_tray_sim_signal_none` for a subscription with no service, in EN/RU/UK with one
> `set-android-string.ps1 -Action add` call each. Check both against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §3.3 requires a `contentDescription` on every tray icon, and a bar glyph carries no meaning at all
without one.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_tray_sim"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 05.4 - Read per-subscription signal level

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTraySimSignalMonitor.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `LauncherTraySimSignalMonitor(context)` exposing `fun levels(): Flow<Map<Int, Int>>` keyed by
> `SubscriptionInfo.getSimSlotIndex()`. Enumerate with `SubscriptionManager.getActiveSubscriptionInfoList()`,
> re-enumerate on `addOnSubscriptionsChangedListener`, and per subscription obtain a
> `TelephonyManager.createForSubscriptionId(id)`. On API 31 and above register a
> `TelephonyCallback.SignalStrengthsListener` with `registerTelephonyCallback`; below it use
> `listen(PhoneStateListener, LISTEN_SIGNAL_STRENGTHS)` in a branch annotated `@Suppress("DEPRECATION")` with a
> one-line comment naming the API-31 cutover. Take the value from `SignalStrength.getLevel()`. Unregister every
> callback and the subscriptions listener in `awaitClose`. Emit an empty map - never a zero level - when the
> permission is missing or the enumeration throws `SecurityException`.

**Why:**

ADR-1 requires an unreadable indicator to be absent rather than drawn empty, and strategic §3.2 rules out
polling, which is why every level arrives through a registered callback instead of a timer.

**Verification:**

- `Glob` - `.../ui/launcher/tray/LauncherTraySimSignalMonitor.kt` exists.
- `Grep` - both `registerTelephonyCallback` and `LISTEN_SIGNAL_STRENGTHS` match, each inside its own SDK branch.
- `Grep` - the `awaitClose` on the live path unregisters both the telephony callbacks and the subscriptions
  listener. Corrected from "matches once" during execution: `callbackFlow` requires an `awaitClose` on every
  path, so the no-permission early return carries a second, empty one - two occurrences is the correct
  shape, not a leak.
- `Grep` - `simSlotIndex` matches; `Log\.d\(` and `GlobalScope` return zero hits.

**Status:** `[x]` done

---

### Step 05.5 - Ask for the permission when the tray first needs it

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Register a `RequestPermission` contract in the activity and pass its launch as a lambda into the tray
> manager's `bind()`. The tray asks once per activity instance, and only when the status content is visible, at
> least one SIM switch is on and the permission is not already granted. Keep the decision in the tray manager;
> the activity holds only the launcher, per CLAUDE.md Rule 3.

**Why:**

Strategic §3.3 records the owner's sign-off that the SIM switches ship on and the request appears at the tray's
first show, and strategic §7 treats a refusal as a normal path rather than a failure.

**Verification:**

- `Grep` - `ActivityResultContracts.RequestPermission` matches once in the file.
- `Grep` - `READ_PHONE_STATE` appears in the activity only as the launched permission, with no branch that hides
  or disables the switches on denial.
- `Grep` - no `if (` on permission state inside `onCreate` - the gate lives in the tray manager.

**Status:** `[x]` done

---

### Step 05.6 - Render the two slots

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`
**Depends on:** Step 05.4, Step 05.5

**Prompt for developer:**

> Collect the monitor while the status content is visible and at least one SIM switch is on. Map slot index 0 to
> `traySim1` and slot index 1 to `traySim2`: a slot present in the map and switched on shows
> `launcher_tray_signal_level` at its level with the matching description; a slot absent from the map is `gone`
> whatever its switch says. Tear the collection down when both switches go off.

**Why:**

Strategic §11 criterion 5 requires a single-SIM device to show slot 1 and no slot 2 at all, and criterion 4
requires a denied permission to produce exactly the same absence for both without disturbing the rest of the
tray.

**Verification:**

- `Grep` - `traySim1` and `traySim2` each match once in the render branch.
- `Grep` - the absent-slot branch sets `isVisible = false` or `gone`, and no branch sets image level 0 as a
  stand-in for absence.
- `Grep` - `collectOnLifecycle` is used; a bare `lifecycleScope.launch` collect returns zero hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly), and `.\a.ps1 fkn` passes for `noLegal`,
      which mounts the same source set.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Step Log

- 2026-08-06 - Step 05.1 Verification 3/3 PASS (one predicate narrowed, see the step). `READ_PHONE_STATE` is declared in the launcher source set only; the main manifest is untouched and no `<uses-feature>` was added.
- 2026-08-06 - Step 05.2 Verification 3/3 PASS. Six new drawables: five bar vectors plus `launcher_tray_signal_level` mapping levels 0..4 onto them.
- 2026-08-06 - Step 05.3 Verification 2/2 PASS. `launcher_tray_sim_signal` and `launcher_tray_sim_signal_none` added in EN/RU/UK.
- 2026-08-06 - Step 05.4 Verification 4/4 PASS, with the `awaitClose` predicate corrected (see the step). Files: `LauncherTraySimSignalMonitor.kt` (New, 148 LOC).
- 2026-08-06 - Step 05.5 Verification 3/3 PASS. Files: `LauncherHomeActivity.kt` (+9 LOC). The contract is registered as a field initialiser, next to the existing `contactPickManager`, because a contract registered after STARTED throws; the decision to ask stays in the tray manager.
- 2026-08-06 - Step 05.6 Verification 3/3 PASS. Files: `LauncherTrayManager.kt` (+55 LOC). A slot missing from the level map is hidden regardless of its switch, and level 0 is rendered as "no service" rather than used as a stand-in for absence.
- 2026-08-06 - Phase close: `.\a.ps1 fk` exit 0 and `.\a.ps1 fkn` exit 0 (noLegal mounts the same source set); `post-change.ps1 -ScopeToFile` verdict `PASS`. Two compiler deprecations surfaced and were fixed in-phase per CLAUDE.md Rule 7: `BluetoothAdapter.getDefaultAdapter()` (phase 04's file, replaced by the system service) and the `PhoneStateListener` import, which is now named in full inside the one `@Suppress("DEPRECATION")` function instead of pulling the deprecation into the import list. Both recompiles are warning-free in the touched files.
- 2026-08-06 - Phase-boundary audit (Layers 1-3): no P0/P1 findings. Layer 2 - every telephony callback and the subscriptions listener are unregistered in `awaitClose`, and `resubscribe()` unregisters the previous set before building a new one, so a SIM swap cannot leak a callback. Layer 3 - `simJob` is cancelled and nulled on the switched-off path, and the monitor holds a `Context` and no view. Layer 1 - `LauncherTrayManager` is 300 LOC, still far under the ceiling; the permission decision is in the manager and the activity holds only the launcher.

---

## Handoff Notes to Next Phase

All six indicators are live. One new permission is declared, in the launcher source set only, and every
indicator resolves to absent rather than empty when it cannot be read.

---

## Rollback Plan

Revert phase commit(s). The permission disappears with the manifest edit; no stored preference other than the
Phase 01 switches is involved, and those keep working with the slots hidden.
