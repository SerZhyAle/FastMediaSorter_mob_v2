# Phase 02 - Real radio controller

**Strategic spec:** [`../S1441_launcher-radio-toggle-tiles.md`](../S1441_launcher-radio-toggle-tiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Implement the contract for the flavors that ship the network monitor: read both radio states as flows, attempt a toggle off the main thread, and confirm it only by an observed state change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `RadioControlContract` and `RadioKind` exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/radio/RadioStateReader.kt` | New | ≤ 120 |
| `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/radio/RadioControlContractImpl.kt` | New | ≤ 150 |
| `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/di/NetworkMonitorModule.kt` | Modified | ≤ 15 added |
| `app_v2/src/networkMonitor/AndroidManifest.xml` | Modified | ≤ 10 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionManifestExemptions.kt` | Modified | ≤ 4 added |

> **Flavor placement.** Every file under `src/networkMonitor` ships only in `standard` and `noLegal`. The one `src/main` edit is the parity exemption, which is a single flavor-neutral list.

---

## Steps

### Step 02.1 - Read both radio states as flows

**Files:** `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/radio/RadioStateReader.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Create `RadioStateReader(private val context: Context)` exposing `fun state(kind: RadioKind): Flow<Boolean?>`.
> Copy the shape of `LauncherTrayBluetoothMonitor` in
> `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/tray/LauncherTrayBluetoothMonitor.kt`:
> a `callbackFlow` registering a `ContentObserver` on the relevant `Settings.Global` uri, an immediate
> `trySend` of the current value, `awaitClose` unregistering, and `distinctUntilChanged()`.
>
> Bluetooth observes `Settings.Global.BLUETOOTH_ON` with the `BluetoothManager` adapter as fallback; Wi-Fi
> observes `Settings.Global.WIFI_ON` with `WifiManager.isWifiEnabled` as fallback. A read that throws yields
> `null`, meaning unknown - never `false`. Do not duplicate `LauncherTrayBluetoothMonitor`: that class stays as
> it is, in a source set this one cannot see.

**Why:**

Strategic ADR-1 makes the observed state the only proof a toggle worked, so a subscription to the real state has
to exist before the toggle can be judged, and §3.4 forbids blocking the UI, which a polled read would risk.

**Verification:**

- `Grep` - `class RadioStateReader` matches exactly once.
- `Grep` - `Settings.Global.WIFI_ON` and `Settings.Global.BLUETOOTH_ON` both appear.
- `Grep` - `distinctUntilChanged` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.2 - Implement the toggle with observed confirmation

**Files:** `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/radio/RadioControlContractImpl.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Create `RadioControlContractImpl @Inject constructor(@ApplicationContext context: Context, reader: RadioStateReader)`
> implementing `RadioControlContract`:
>
> - `isToggleSupported = true`.
> - `state(kind)` delegates to the reader.
> - `toggle(kind)`: read the current state; return `false` immediately when it is `null` or when the permission
>   the attempt needs is missing (`BLUETOOTH_CONNECT` on API 31+ for Bluetooth, checked with
>   `ContextCompat.checkSelfPermission`); otherwise call the platform on `Dispatchers.IO` -
>   `WifiManager.setWifiEnabled(!current)` for Wi-Fi, `BluetoothAdapter.enable()` / `disable()` for Bluetooth -
>   ignoring the returned boolean, then `withTimeoutOrNull(RADIO_TOGGLE_CONFIRM_WINDOW_MS) { reader.state(kind).first { it == !current } }` and return whether that produced a value.
>
> Define `RADIO_TOGGLE_CONFIRM_WINDOW_MS` as a private const in a companion object with a comment naming the
> trade-off from strategic §6.2: on a platform that silently refuses, this window is a visible pause before the
> system screen opens, so it is short enough not to feel stuck. Start at 1200 ms. Wrap every platform call in
> `runCatching` and log a refusal with `Timber.w` - a `SecurityException` here is an expected outcome on a modern
> firmware, not an error to shout about.

**Why:**

Strategic §5.2 and ADR-1 state that a returned `true` from the platform is not evidence, because some firmwares
accept the call and do nothing, and §7 requires a missing `BLUETOOTH_CONNECT` to fall back immediately rather
than attempt and fail.

**Verification:**

- `Grep` - `class RadioControlContractImpl` matches exactly once and it declares `: RadioControlContract`.
- `Grep` - `withTimeoutOrNull` present.
- `Grep` - `RADIO_TOGGLE_CONFIRM_WINDOW_MS` present and defined once.
- `Grep` - `setWifiEnabled` and `BluetoothAdapter` both appear.
- `Grep` - `checkSelfPermission` appears with `BLUETOOTH_CONNECT`.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.3 - Bind the implementation

**Files:** `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/di/NetworkMonitorModule.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> Add a `@Provides @Singleton` returning `RadioControlContract` from `RadioControlContractImpl` to the existing
> module in this source set, mirroring how it already provides `NetworkMonitorContract`. Construct
> `RadioStateReader` there from the `@ApplicationContext` rather than adding a second Hilt entry point for it.

**Why:**

Strategic §6.7 fixes this seam as the placement, and the module in this source set is the only place a
`standard` or `noLegal` build can bind the real controller without a `BuildConfig` guard in `src/main`.

**Verification:**

- `Grep` - `RadioControlContract` appears in `src/networkMonitor/.../di/NetworkMonitorModule.kt`.
- `Grep` - the file still provides `NetworkMonitorContract` - the existing binding is untouched.
- `.\a.ps1 fk` exits 0.
- `.\a.ps1 fkn` exits 0 - noLegal mounts the same source set.

**Status:** `[x]` done

---

### Step 02.4 - Declare the Wi-Fi permission and explain it to the parity gate

**Files:** `app_v2/src/networkMonitor/AndroidManifest.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionManifestExemptions.kt`

**Depends on:** Step 02.3

**Prompt for developer:**

> Replace the `S1472` placeholder comment in `src/networkMonitor/AndroidManifest.xml` with a real
> `<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />` and a comment stating that S1441's
> radio toggle is the code that uses it. Add `android.permission.CHANGE_WIFI_STATE` to
> `PermissionManifestExemptions.declaredWithoutRow` with the reason that it is a normal install-time permission
> the user never decides about, in the same wording style as the `ACCESS_WIFI_STATE` entry above it.
>
> Leave `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` and `NEARBY_WIFI_DEVICES` exactly as they are - they already
> cover the Bluetooth path.

**Why:**

The manifest's own S1472 comment records that the permission was removed precisely because nothing called
`setWifiEnabled` and that it comes back in the same change as the code that uses it, which Step 02.2 has now
written.

**Verification:**

- `Grep` - `CHANGE_WIFI_STATE` matches exactly once in `app_v2/src/networkMonitor/AndroidManifest.xml`.
- `Grep` - `CHANGE_WIFI_STATE` matches exactly once in `PermissionManifestExemptions.kt`.
- `Grep` - the S1472 "does not exist yet" comment is gone.
- `.\a.ps1 fu` run in the background (it is far over the 120 s foreground threshold), then read `app_v2/build/test-results/testStandardDebugUnitTest/TEST-com.sza.fastmediasorter.data.permissions.PermissionRegistryManifestParityTest.xml` and confirm `failures="0" errors="0"` on a file whose timestamp belongs to this run - an old XML proves nothing.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Steps 02.1-02.3 done. Greps PASS: `RadioStateReader` once with both `Settings.Global` keys and `distinctUntilChanged`; `RadioControlContractImpl` once with `withTimeoutOrNull`, `CONFIRM_WINDOW_MS` and `checkSelfPermission`; zero `Log.d(`; no line over 120 chars in any of the three files. Compiles PASS: `.\a.ps1 fk` then `.\a.ps1 fkn`, chained so the second runs only if the first exits 0 - the chain reported exit 0 with noLegal's `BUILD SUCCESSFUL in 22s`.
- 2026-08-08 - `toggle()` was written with two `return`s rather than three (the `null`-state and missing-permission cases share one guard) because detekt's `ReturnCount` threshold is 2; this is a shape constraint, not a preference.
- 2026-08-08 - Step 02.4 done. `CHANGE_WIFI_STATE` declared once in `src/networkMonitor/AndroidManifest.xml` with S1472's placeholder comment removed, and exempted once in `PermissionManifestExemptions.declaredWithoutRow`. `.\a.ps1 fu` exit 1, but the parity predicate is green: `TEST-...PermissionRegistryManifestParityTest.xml` written at 00:16 this run reads `tests="3" skipped="0" failures="0" errors="0"`.
- 2026-08-08 - Pre-existing unrelated suite failure, not this ticket's: the only red class is `IconInventoryExportTest`, complaining that `docs/icons/icon-inventory.json` is stale over a camera-widget label (`widget_camera_quick_capture_label` vs `quick_camera_menu_label`). Nothing in Phases 01-02 touches a drawable, a label or the inventory. Dedup per CLAUDE.md §3.1 found an existing open ticket for this symptom - **S1194 `icon-inventory-stale-settings-header-entry`** (Draft) - so no duplicate draft was created. Regenerating the inventory here would also overwrite whichever session's in-flight change produced the drift.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk`, `.\a.ps1 fkn` and `.\a.ps1 dq` all exit 0; `dq` covers the manifest merge the new permission changes.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `PermissionRegistryManifestParityTest` passes - 3 tests, 0 failures, 0 errors, this run.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: the controller stays in its source set and exposes only the contract's three members. Layer 2: `toggle` hops to `Dispatchers.IO` for the platform call and bounds the wait with `withTimeoutOrNull`, so no path blocks the caller indefinitely and a cancelled caller cancels the wait; the confirmation collects a cold flow that completes at `first`, so no collector outlives the call. Layer 3: `RadioStateReader`'s `callbackFlow` unregisters its observer in `awaitClose`, the same shape `LauncherTrayBluetoothMonitor` already uses. Layer 4 not applicable.

---

## Handoff Notes to Next Phase

`RadioControlContract` is injectable and real on standard and noLegal. Nothing calls it yet - Phase 03 wires the
catalog and the two execution funnels.

---

## Rollback Plan

Revert the phase commit. The permission declaration goes back with it, which is the state S1472 left behind, so
no half-declared permission survives a revert.
