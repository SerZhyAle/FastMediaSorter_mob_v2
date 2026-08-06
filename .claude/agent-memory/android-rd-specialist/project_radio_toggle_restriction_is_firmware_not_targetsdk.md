---
name: radio-toggle-restriction-is-firmware-not-targetsdk
description: Wi-Fi/Bluetooth toggle bans live in the device firmware, not our targetSdk - the direct path still works on the owner's Android 8 head unit (S1433/S1441)
metadata:
  type: project
---

`WifiManager.setWifiEnabled` and `BluetoothAdapter.enable()/disable()` are refused by the **Android 10+ / Android 13+ frameworks**, not by our `targetSdk`. On an API 26-28 device the check does not exist in the system image, so the direct call with `CHANGE_WIFI_STATE` / `BLUETOOTH_ADMIN` genuinely toggles the radio - including on the owner's car head unit (see [[owner-runs-app-on-car-head-unit]]).

Verified 2026-08-06 against `android.jar` API 36: `Settings$Panel` exposes `ACTION_WIFI` + `ACTION_INTERNET_CONNECTIVITY`; `BluetoothAdapter` has `ACTION_REQUEST_ENABLE` but **no** `ACTION_REQUEST_DISABLE`; `SubscriptionManager` exposes **no** public enable/disable at all, so SIM control is impossible for any non-privileged app, and mobile data needs `MODIFY_PHONE_STATE`.

**Why:** the reflex answer "deprecated, so it's dead code, just open Settings" is wrong here and would have removed the only working path on exactly the devices the launcher mode was written for. The owner pushed back on that reflex and was right.

**How to apply:**
- A radio toggle is "try the direct call quietly, then fall back to `Settings.Panel` / `ACTION_REQUEST_ENABLE`" - never fallback-only, never direct-only.
- Success is proven by the **observed state changing**, never by the returned `Boolean`: some firmwares accept the call and ignore it.
- One shared component owns this (S1441 produces it, S1433 consumes it) - do not let a second copy appear in a screen.
