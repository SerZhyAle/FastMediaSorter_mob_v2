# Phase 01 - Contracts Models

**Strategic spec:** [`../S0295_vr-generic-immerse-playback-contract.md`](../S0295_vr-generic-immerse-playback-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-25
**Completed:** 2026-05-25

---

## Objective

Introduce the shared XR launch contract models and a generic immersive-intent seam in `XrEntryGateway` without changing `DiagnosticXrActivity` internals yet.

---

## Prerequisites

- [ ] INDEX Pre-Implementation Blockers are closed.
- [ ] Working tree is clean or current branch ownership is confirmed.
- [ ] Existing KDoc in every touched XR contract is read before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt` | New | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt` | Modified | <= 180 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEntryGateway.kt` | Modified | <= 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt` | Modified | <= 240 |

---

## Steps

### Step 01.1 - Add shared launch contract model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add one shared contract file in `src/main/` containing `VrLaunchMode`, `VrLaunchDeliveryMode`, `VrMediaType`, `VrLaunchPoint`, `StartVrPlaybackRequest`, `PlayerStateSnapshot`, `VrLaunchInput`, `VrLaunchUnavailableReason`, and `VrLaunchResult`. Keep the file free of `src/vr` implementation types so phone flavors compile it unchanged.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt` exists.
- `Grep` - `enum class VrLaunchMode` appears in `VrLaunchContract.kt`.
- `Grep` - `data class StartVrPlaybackRequest` appears in `VrLaunchContract.kt`.
- `Grep` - `data class VrLaunchInput` appears in `VrLaunchContract.kt`.
- `Grep` - `sealed class VrLaunchResult` appears in `VrLaunchContract.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt`. Ready for Step 01.2.

---

### Step 01.2 - Extend XrEntryGateway with a generic intent seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun createImmersiveIntent(input: VrLaunchInput): Intent?` to `XrEntryGateway`. Keep `enterDiagnosticImage()` for compatibility, but document that the new intent seam is the transport contract for `ActivityResultContract` callers.

**Verification:**

- `Grep` - `fun createImmersiveIntent(input: VrLaunchInput): Intent?` appears in `XrEntryGateway.kt`.
- `Grep` - `suspend fun enterDiagnosticImage(): XrEntryResult` still appears in `XrEntryGateway.kt`.
- `Grep` - `Log.d(` returns zero hits in `XrEntryGateway.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt`. Ready for Step 01.3.

---

### Step 01.3 - Update the no-op gateway path

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEntryGateway.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement `createImmersiveIntent()` in the phone-only no-op gateway and return `null`. Keep the legacy diagnostic method mapped to the existing unavailable-runtime result so all non-VR flavors stay deterministic.

**Verification:**

- `Grep` - `createImmersiveIntent` appears in `NoOpXrEntryGateway.kt`.
- `Grep` - `Intent?\s*=\s*null` OR `return null` appears in `NoOpXrEntryGateway.kt` (expression body or block body both acceptable).
- `Grep` - `UnavailableNoRuntime` appears in `NoOpXrEntryGateway.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Resume run. Verification 3/3 PASS via expression-body form `override fun createImmersiveIntent(input: VrLaunchInput): Intent? = null`. Predicate widened to accept either expression-body or block-body return.

---

### Step 01.4 - Build typed intents in the real VR gateway

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement `createImmersiveIntent()` in the real gateway. Validate runtime availability, encode the shared `VrLaunchInput` into `DiagnosticXrActivity` extras, and make `enterDiagnosticImage()` delegate through `VrLaunchMode.DIAGNOSTIC_PLAYLIST` with legacy panel-return delivery so future callers and the legacy path share one intent builder.

**Verification:**

- `Grep` - `createImmersiveIntent` appears in `XrEntryGatewayImpl.kt`.
- `Grep` - `VrLaunchMode.DIAGNOSTIC_PLAYLIST` appears in `XrEntryGatewayImpl.kt`.
- `Grep` - `Intent(appContext, DiagnosticXrActivity::class.java)` appears in `XrEntryGatewayImpl.kt`.
- `Grep` - `XrEntryResult.UnavailableNoRuntime` still appears in `XrEntryGatewayImpl.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Resume run. Verification 4/4 PASS. Files: `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt`. `enterDiagnosticImage()` now delegates through `createImmersiveIntent(VrLaunchMode.DIAGNOSTIC_PLAYLIST + VrMediaType.IMAGE)`. Phase 01 complete.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x]` done.
- [ ] Project compiles - run `/build` for standard debug and noLegal debug once Phase 01 files are complete.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

---

## Handoff Notes to Next Phase

The shared launch transport exists in `src/main/`, and `XrEntryGateway` can now manufacture typed immersive intents without exposing `DiagnosticXrActivity` to non-VR callers.

---

## Rollback Plan

Revert Phase 01 commit(s); no data migration or persisted state is introduced.