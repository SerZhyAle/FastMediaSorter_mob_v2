# S1433 research: permission registry and Welcome integration

**Date:** 2026-08-06  
**Scope:** Network Monitor permissions

## Existing mechanism

- The app has one permission registry used by Settings -> Permissions and the Welcome permissions page.
- Both surfaces render the SDK/flavor-applicable registry entries, offer individual grants and support the same Grant all flow.
- Grant all requests regular runtime permissions together, then walks special permissions through their required system settings pages. It survives activity recreation and refreshes status on return.

## Decision

- Network Monitor must add each of its runtime permissions to this registry and must not create a separate permission screen, custom bulk-grant flow or Welcome-only branch.
- The feature's required optional entries are: `ACCESS_FINE_LOCATION` (already registered, GNSS and location-sensitive Wi-Fi), `READ_PHONE_STATE` (SIM detail and per-subscription telemetry), `NEARBY_WIFI_DEVICES` on Android 13+ where required by the chosen Wi-Fi calls, and `BLUETOOTH_CONNECT` on Android 12+ for an explicitly selected connected-device RSSI series.
- `BLUETOOTH_SCAN` and Wi-Fi scan APIs are excluded from the first release, so no scan-only permission is added for charts.
- Each monitor section remains usable without the related grant and uses the shared unavailable/rationale pattern. Welcome and Grant all can proactively request the registered optional entries; opening a section can also use its standard individual permission row.
