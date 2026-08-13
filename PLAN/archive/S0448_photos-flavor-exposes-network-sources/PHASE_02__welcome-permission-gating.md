# Phase 02 - Welcome Rows & Local-Network Permission Gating

**Strategic spec:** [`../S0448_photos-flavor-exposes-network-sources.md`](../S0448_photos-flavor-exposes-network-sources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Hide the two surfaces that are not already gate-driven: the welcome "networks" page SMB/FTP rows, and the local-network runtime permission (manifest declaration + in-app request registry). After this phase, `lite` neither shows network onboarding rows nor declares/requests `ACCESS_LOCAL_NETWORK`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`SUPPORT_LOCAL_NETWORK` flag + `supportsLocalNetworkSources` + gate branch exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/RemoteSourceAvailabilityGate.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeRemoteSourcesController.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 200 |
| `app_v2/src/lite/AndroidManifest.xml` | Modified | ≤ 90 |

> The welcome controller and permission registry stay in `src/main/java` (shared). The flavor difference flows through the injected gate (`isNetworkGroupSupported()`) and the reflected `SUPPORT_LOCAL_NETWORK` flag - no `BuildConfig` guard is written into `src/main`. The only flavor-local file is `src/lite/AndroidManifest.xml`.

---

## Steps

### Step 02.1 - Add `isNetworkGroupSupported()` to the gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/RemoteSourceAvailabilityGate.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Add `fun isNetworkGroupSupported(): Boolean` returning whether any NETWORK id is compile-supported, mirroring the existing `isCloudGroupSupported()`. Implement it via the Phase-01 capability (`mediaCapabilities.supportsLocalNetworkSources`) or by folding over `RemoteSourceId.NETWORK` with `compileSupported(..)` - match the style of `isCloudGroupSupported()`.

**Verification:**

- `Grep` - `fun isNetworkGroupSupported` matches exactly once in `RemoteSourceAvailabilityGate.kt`.
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 1/1 PASS (exactly one `fun isNetworkGroupSupported`). Mirrors `isCloudGroupSupported`. Files: RemoteSourceAvailabilityGate.kt (+3 lines).

---

### Step 02.2 - Hide SMB and FTP rows in `WelcomeRemoteSourcesController`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeRemoteSourcesController.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> The SMB row (`bindSmbRow`) and FTP row (`bindFtpRow`) currently set `View.VISIBLE` unconditionally. Gate both rows on `gate.isNetworkGroupSupported()`, exactly as the cloud row is gated on `gate.isCloudGroupSupported()` - set the row container to `View.GONE` (not INVISIBLE) when unsupported so it leaves the focus order. Do not introduce a hardcoded hex colour or any flavor `BuildConfig` check here.

**Verification:**

- `Grep` - `isNetworkGroupSupported` referenced in `WelcomeRemoteSourcesController.kt`.
- `Grep` - the SMB/FTP row visibility is no longer an unconditional `View.VISIBLE` (it reads the gate).
- `Grep -n "Log\.d\("` returns zero hits in `WelcomeRemoteSourcesController.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (SMB + FTP rows gate on `isNetworkGroupSupported()`, GONE when unsupported; no `Log.d`). KDoc updated for accuracy. Files: WelcomeRemoteSourcesController.kt (+8 lines).

---

### Step 02.3 - Flavor-gate the `access_local_network` permission registry entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> The `access_local_network` registry entry has `flavorGates = emptySet()`. Change it to `flavorGates = setOf("SUPPORT_LOCAL_NETWORK")` so `evaluateFlavorGates` (which reflects the `BuildConfig` field) suppresses the entry in `lite`. The `SUPPORT_LOCAL_NETWORK` field exists on every flavor after Phase 01, so the reflection never falls into the silent-`false` catch for any flavor. Do not change any other entry's gates.

**Verification:**

- `Grep` - `setOf("SUPPORT_LOCAL_NETWORK")` present in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - the `access_local_network` entry no longer carries `flavorGates = emptySet()`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS (`flavorGates = setOf("SUPPORT_LOCAL_NETWORK")` on the entry; reflection target exists on every flavor after P01). Files: PermissionRegistryRepositoryImpl.kt (+1 line).

---

### Step 02.4 - Remove `ACCESS_LOCAL_NETWORK` from the `lite` merged manifest

**Files:** `app_v2/src/lite/AndroidManifest.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" tools:node="remove" />` to `src/lite/AndroidManifest.xml`, following the existing `tools:node="remove"` entries already in that file. Ensure the `tools` namespace is declared on the `<manifest>` root (it already is, given the existing removals). This drops the permission from the merged `lite` manifest entirely.

**Verification:**

- `Grep` - `ACCESS_LOCAL_NETWORK` with `tools:node="remove"` present in `src/lite/AndroidManifest.xml`.
- `/build` of `assembleLiteDebug` succeeds; the merged `lite` manifest under `build/intermediates/merged_manifests/liteDebug/` does NOT contain a non-removed `ACCESS_LOCAL_NETWORK` `<uses-permission>`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Edit verified by grep (`ACCESS_LOCAL_NETWORK` + `tools:node="remove"` present). Merged-manifest assertion PASS: `assembleLiteDebug` merged manifest (`merged_manifest/liteDebug/.../AndroidManifest.xml`) has 0 `ACCESS_LOCAL_NETWORK` occurrences. Files: src/lite/AndroidManifest.xml (+5 lines).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `assembleLiteDebug` BUILD SUCCESSFUL (validates lite flavor + manifest merge).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` (gate gained public `isNetworkGroupSupported()`).

---

## Handoff Notes to Next Phase

`lite` now fully hides network sources: tabs, Add-Resource cards, filter chips, ALL-tab records (Phase 01), welcome rows and the local-network permission (Phase 02). Phase 03 reconciles documentation: corrects the stale S0035 premise and updates `docs/FEATURES` to state `lite` is local-files-only.

---

## Rollback Plan

Revert the phase commit(s) - no persisted state changed. Manifest and registry edits are declarative; reverting restores the prior (ungated) behaviour.
