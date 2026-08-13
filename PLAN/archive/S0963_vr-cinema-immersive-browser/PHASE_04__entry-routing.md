# Phase 04 - Entry routing

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Make `ImmersiveBrowseActivity` reachable: declare it in the VR manifest and route `VrLaunchMode.RESOURCE_BROWSE` to it from `XrEntryGatewayImpl`, while `DIAGNOSTIC_PLAYLIST` / `FILE_URI` keep targeting `DiagnosticXrActivity`. Validate the new mode in the use-case impl.

---

## Prerequisites

- [ ] Phase 03 ✅ Done - `ImmersiveBrowseActivity` compiles.
- [ ] `src/vr/AndroidManifest.xml` and `StartVrPlaybackUseCaseImpl.kt` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt` | Modified | ≤ 110 |
| `app_v2/src/vr/AndroidManifest.xml` | Modified | ≤ 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCaseImpl.kt` | Modified | ≤ 170 |

> `vr` flavor auto-mounts `src/vr`; `noLegal` mounts it explicitly. Both share this manifest and gateway. No `src/main` flavor guard.

---

## Steps

### Step 04.1 - Route RESOURCE_BROWSE in the gateway

**Files:** `core/xr/XrEntryGatewayImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `createImmersiveIntent(input)`, after the native-availability check, branch the target Activity by `input.launchMode`: `RESOURCE_BROWSE` -> `ImmersiveBrowseActivity::class.java` with a guard `if (input.launchMode == RESOURCE_BROWSE && input.resourceId == null) return null` (log at `Timber.w`, no `S0963:` prefix - permanent log); all other modes -> `DiagnosticXrActivity::class.java` as today. Keep the existing `FILE_URI` blank-uri guard. Token/extra plumbing unchanged (same `EXTRA_LAUNCH_INPUT_TOKEN`).

**Verification:**

- `Grep` - `ImmersiveBrowseActivity::class.java` present in `XrEntryGatewayImpl.kt`.
- `Grep` - `RESOURCE_BROWSE` referenced in a `when`/`if` target-selection branch.
- `Grep` - `input.resourceId == null` guard present.

**Status:** `[x]` done

---

### Step 04.2 - Declare the Activity in the VR manifest

**Files:** `src/vr/AndroidManifest.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add an `<activity android:name="..ui.xr.ImmersiveBrowseActivity">` entry mirroring the `DiagnosticXrActivity` declaration. **Correction (2026-07-11):** HorizonOS renders in headset mode only for an Activity carrying the `com.oculus.intent.category.VR` intent-filter category, so this must mirror the diagnostic block's `<intent-filter>` (MAIN + `com.oculus.intent.category.VR` + DEFAULT) and therefore `android:exported="true"` (required for the filter to be discoverable) - not `exported="false"`. No LAUNCHER category, so it never appears in the app grid. Same `configChanges`, `launchMode="singleTask"`, `resizeableActivity="false"`, `screenOrientation="landscape"`, theme, and the API-36 restricted-resizability `<property>` as the diagnostic host.

**Verification:**

- `Grep` - `ImmersiveBrowseActivity` present in `src/vr/AndroidManifest.xml`.
- `Grep` - `com.oculus.intent.category.VR` present in the `ImmersiveBrowseActivity` block (headset-mode filter).

**Status:** `[x]` done

---

### Step 04.3 - Validate RESOURCE_BROWSE in the use-case impl

**Files:** `core/xr/StartVrPlaybackUseCaseImpl.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In the request-validation path of `StartVrPlaybackUseCaseImpl`, add a `RESOURCE_BROWSE` branch: require `request.resourceId != null` (else return the existing `Unavailable(InvalidUri)`/`Failed` result the impl already uses for bad input); build the `VrLaunchInput` via `VrLaunchInput.fromRequest` (already copies `resourceId` after Phase 01). Preflight (XR-detection gate) and Activity dispatch stay the shared path - no duplication.

**Verification:**

- `Grep` - `RESOURCE_BROWSE` referenced in `StartVrPlaybackUseCaseImpl.kt`.
- `Grep` - `resourceId` referenced in the validation branch.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fkn` (noLegal) and `.\a.ps1 vr debug`; `.\a.ps1 fc` (standard) unaffected.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

A `RESOURCE_BROWSE` request with a `resourceId` now cold-launches `ImmersiveBrowseActivity` on the resource. Phase 05 wires the resource-menu entry that builds and dispatches that request.

---

## Rollback Plan

Revert the phase commit - gateway falls back to only `DiagnosticXrActivity`; manifest entry orphaned but harmless. No data migration.
