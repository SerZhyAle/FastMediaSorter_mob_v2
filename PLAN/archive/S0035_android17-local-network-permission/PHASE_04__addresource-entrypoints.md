# Phase 04 — Add Resource Entrypoints

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-05-04
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Stop host discovery and SMB share listing before the first LAN socket opens, and route missing-permission cases to a dedicated rationale dialog instead of generic `ShowError` noise.

---

## Prerequisites

- [ ] Phase 01, Phase 02, and Phase 03 are ✅ Done.
- [ ] The new Settings copy and `PermissionHelper` methods compile cleanly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | n/a |

---

## Steps

### Step 04.1 — Add a dedicated Add Resource permission event

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a dedicated Add Resource event for the local-network rationale flow. Do not reuse `ShowError`. The event must carry enough information for `AddResourceActivity` / `AddResourceConnectionManager` to open the rationale dialog and optionally retry the originating action after returning from settings.

**Verification:**

- `Grep` — `ShowLocalNetworkPermission` matches in `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt`.
- `Grep` — `ShowError` remains unchanged for non-permission failures in the same file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: AddResourceViewModel.kt (+2 LOC). Dev log recorded.

---

### Step 04.2 — Gate auto-scan and manual discovery

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Before `DiscoverNetworkResourcesUseCase.execute()` is collected, check `PermissionHelper.hasLocalNetworkPermission(...)`. If permission is missing, do not set `isScanning = true`, do not start the Flow, and emit the dedicated Add Resource permission event. Apply the same guard to the `NetworkDiscoveryDialog.onStart()` auto-scan path so the dialog does not immediately trigger a denied network probe on API 37.

**Verification:**

- `Grep` — `hasLocalNetworkPermission` appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt`.
- `Grep` — `ShowLocalNetworkPermission` appears in the same coordinator file.
- `Grep` — `viewModel.scanNetwork()` in `NetworkDiscoveryDialog.kt` is guarded or deferred through the new permission path.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: AddResourceNetworkScanCoordinator.kt (+4 LOC). Dev log recorded.

---

### Step 04.3 — Gate SMB share scan and render the rationale dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Before `smbOperationsUseCase.listShares(...)` is called, perform the same permission gate. Missing permission must open a two-button rationale dialog (`Open settings` / `Cancel`) through `AddResourceConnectionManager`; it must not fall through to `msg_share_scan_failed` or a generic toast. `AddResourceActivity.observeData()` owns the event-to-dialog routing.

**Verification:**

- `Grep` — `smbOperationsUseCase.listShares` still appears in `AddResourceNetworkScanCoordinator.kt` after a `hasLocalNetworkPermission` guard.
- `Grep` — `ShowLocalNetworkPermission` appears in `AddResourceActivity.kt`.
- `Grep` — `routeToLocalNetworkSettings|requestLocalNetworkPermission` appears in `AddResourceConnectionManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: AddResourceNetworkScanCoordinator.kt (+5 LOC), AddResourceActivity.kt (+1 LOC), AddResourceConnectionManager.kt (+14 LOC). Dev log recorded.

---

### Step 04.4 — Run the focused compile gate

**Files:** none modified — verification only
**Depends on:** Step 04.3

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> The Add Resource slice must compile before protocol readers start adopting the same contract.

**Verification:**

- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every Step 04.* above is `[x] done`.
- [ ] Host discovery does not probe the LAN when permission is missing.
- [ ] SMB share listing does not emit generic failure copy for missing permission.
- [ ] `AddResourceActivity` routes the dedicated permission event to a rationale dialog.

---

## Handoff Notes to Next Phase

Phase 05 extends the same contract to browse, playback, and thumbnail readers. Keep the Add Resource strings and dialog copy shared; do not fork separate messages.

---

## Rollback Plan

Revert the Add Resource event and coordinator/dialog wiring together. A partial rollback would leave unreachable UI events or silent LAN probes.