# Phase 03 - Network status metric

**Strategic spec:** [`../S1178_launcher-system-status-widgets.md`](../S1178_launcher-system-status-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Produce a typed network status - transport, network name, reachability - in shared code, and leave exactly one transport classifier in the tree.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - the Wi-Fi SSID finding of 2026-08-02 is Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `MetricValue` and `DeviceStatusProvider` from Phase 01 exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/NetworkStatus.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetNetworkStatusUseCase.kt` | New | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | Modified | ≤ 400 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `LauncherTrayManager.kt` was 211 lines when this plan was written on 2026-08-05; S1415 has since grown it to 385, so the budget above was corrected from 220 to 400 on 2026-08-08. Still under the 500-line backup threshold, and this step removes lines rather than adding them.
>
> **Live ticket on this file.** S1415 sits in `BlockNeedUserTest` and its `Timber.d("S1415: ..")` probes live in this file. Step 03.3 must leave every one of them in place - deleting another ticket's probe breaks the invariant that ties the owner's device observation to that ticket.
>
> **Flavor placement.** The model and the use case are shared code under `src/main/java/`. `LauncherTrayManager.kt` already lives in `src/launcherEnabled/` and stays there; this phase adds no file to a flavor source set.

---

## Steps

### Step 03.1 - Add the network status model with a shared transport enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/NetworkStatus.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `NetworkTransport` as an enum with `WIFI`, `CELLULAR`, `ETHERNET`, `NONE`, carrying no drawable or string resource - the enum is a domain type and each surface keeps its own icon and label mapping. Add `NetworkStatus` with `transport: NetworkTransport`, `networkName: MetricValue<String>` and `isOnline: Boolean`. KDoc `networkName` as unknown whenever the name cannot be read without asking for a permission.

**Why:**

Strategic §2.1 requires the gadget to show connection type, Wi-Fi or carrier name and the fact of internet access, and §5.1.3 defines this metric as the wrapper that adds to the existing observation exactly what it lacks for display.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/NetworkStatus.kt` exists.
- `Grep` - `enum class NetworkTransport` matches exactly once.
- `Grep` - `data class NetworkStatus` matches exactly once.
- `Grep` - `DrawableRes`, `StringRes` each return zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4/4 PASS. Files: domain/model/devicestatus/NetworkStatus.kt (+26 LOC). Dev log recorded.

---

### Step 03.2 - Add the network status use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetNetworkStatusUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `GetNetworkStatusUseCase` implementing `DeviceStatusProvider<NetworkStatus>`. Classify the active network's transport from `NetworkCapabilities`, reproducing the order `LauncherTrayManager.transportOf` uses - Wi-Fi, then Ethernet, then cellular, then none - and expose that classification as a public function so the tray can call it in step 03.3. Take `isOnline` from the injected `NetworkStateMonitor.isInternetAvailable()`. For the name, read `WifiInfo.getSSID()` on Wi-Fi and `TelephonyManager.networkOperatorName` on cellular, returning `MetricValue.Unknown` when the platform answers a blank, a placeholder SSID, or throws. Never call any permission-request API from this file, and never gate the read on a runtime permission prompt.

**Why:**

Strategic §3.2 and ADR-4 forbid a single new permission for these readings, and the 2026-08-02 §6 finding records that the SSID is readable only while system location is on, so the type-only display is the designed outcome rather than a degraded one.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetNetworkStatusUseCase.kt` exists.
- `Grep` - `class GetNetworkStatusUseCase` matches exactly once and the declaration line contains `DeviceStatusProvider<NetworkStatus>`.
- `Grep` - `isInternetAvailable()` present.
- `Grep` - `requestPermissions`, `ActivityCompat` each return zero hits in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `Grep` - `uses-permission` in `app_v2/src/main/AndroidManifest.xml` and `app_v2/src/launcherEnabled/AndroidManifest.xml` produces the same set of permissions as before this step.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 6/6 PASS. Files: domain/usecase/devicestatus/GetNetworkStatusUseCase.kt (+109 LOC).
  `class GetNetworkStatusUseCase` = 1 hit, `DeviceStatusProvider<NetworkStatus>` = 1 hit; the declaration is
  multi-line because the single-line form exceeds the 120-char ktlint limit, so the predicate is met on the
  declaration block rather than on one physical line. `isInternetAvailable()` = 1; `requestPermissions`,
  `ActivityCompat`, `Log.d(` = 0. Manifest permission count unchanged (main + launcherEnabled = 29 lines).
  Classification exposed as `GetNetworkStatusUseCase.classify(NetworkCapabilities?)` for step 03.3.

---

### Step 03.3 - Point the taskbar tray at the shared classifier

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Replace the tray's private `transportOf` and its `Transport` enum with the shared `NetworkTransport` and the classification function added in step 03.2. Keep the tray's own drawable and string mapping, now keyed off `NetworkTransport`, and keep its `registerDefaultNetworkCallback` subscription untouched - this step changes the classifier, not the tray's update mechanism. Delete the private duplicate rather than leaving it unused.

**Why:**

The 2026-08-02 §6 finding rules that the tray's existing classifiers are reused as reading code so the classification is not written a third time; CLAUDE.md Rule 20 requires the superseded copy to go in the same change.

**Verification:**

- `Grep` - `private enum class Transport` returns zero hits in `LauncherTrayManager.kt`.
- `Grep` - `hasTransport(` returns zero hits in `LauncherTrayManager.kt`.
- `Grep` - `NetworkTransport` present in `LauncherTrayManager.kt`.
- `Grep` - `registerDefaultNetworkCallback` still present in `LauncherTrayManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 5/5 PASS. Files: ui/launcher/helpers/LauncherTrayManager.kt (385 -> 386 LOC).
  `private enum class Transport` = 0, `hasTransport(` = 0, `NetworkTransport` = 15, `Log.d(` = 0,
  `registerDefaultNetworkCallback` = 1 (subscription untouched). The tray keeps its own icon and label
  mapping as `iconOf`/`labelOf`, now keyed off `NetworkTransport`. All 9 `S1415:` probe lines left in place.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL in 53s, `.\a.ps1 fkn` BUILD SUCCESSFUL in 21s (2026-08-08).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - see Audit below.

---

## Phase-boundary audit (2026-08-08)

- **Listener symmetry (trigger: the tray's `registerDefaultNetworkCallback`).** Untouched by this phase:
  `registerNetwork`/`unregisterNetwork` still pair through `networkCallbackRegistered`, and the step changed
  only the classifier the callback feeds. No P0/P1.
- **New subscription introduced?** None. `GetNetworkStatusUseCase` registers no receiver and no callback - it
  reads on demand inside `withContext(Dispatchers.IO)`, so a detached gadget cell leaves nothing behind. This
  is the strategic §7 top risk and it is structurally absent here rather than merely handled.
- **Main-safety.** Every platform read (`ConnectivityManager`, `WifiManager`, `TelephonyManager`) runs off the
  main thread. `NetworkStateMonitor.isInternetAvailable()` is a cached-capability read on the same IO context.
- **Permissions.** No new manifest permission and no runtime request; the SSID path degrades to
  `MetricValue.Unknown` when the platform withholds the name (strategic §11.8, ADR-4).
- **Duplicate classifier.** One transport vocabulary now: the tray's private copy is deleted, not orphaned
  (CLAUDE.md Rule 20).

---

## Handoff Notes to Next Phase

- `NetworkTransport` is the single transport vocabulary in the tree; a fifth surface maps its own icons off it.
- The network metric asks for no permission and requests none; a later change that adds one contradicts strategic §11.8.

---

## Rollback Plan

Revert phase commit(s). Step 03.3 is the only edit to shipped behaviour and it restores byte-identically - no data migration and no user-facing string changed.
