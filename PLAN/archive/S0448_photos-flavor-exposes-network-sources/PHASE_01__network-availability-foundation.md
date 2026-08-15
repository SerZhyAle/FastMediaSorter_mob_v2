# Phase 01 - Network Availability Foundation

**Strategic spec:** [`../S0448_photos-flavor-exposes-network-sources.md`](../S0448_photos-flavor-exposes-network-sources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Introduce `SUPPORT_LOCAL_NETWORK` per-flavor BuildConfig flag (`true` everywhere except `lite`), surface it through `MediaCapabilities.supportsLocalNetworkSources`, and extend `RemoteSourceAvailabilityGate` so NETWORK ids (SMB/SFTP/FTP) become unavailable in `lite`. This alone hides the network tabs, Add-Resource cards, filter chips, and ALL-tab network records in `lite` because every consumer already queries the gate.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (both are - see INDEX Research inputs).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 1320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` | Modified | ≤ 40 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 45 |
| `app_v2/src/lite/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 45 |
| `app_v2/src/photos/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 45 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 45 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 45 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/RemoteSourceAvailabilityGate.kt` | Modified | ≤ 130 |

> `vr` MediaCapabilitiesModule is also the module compiled into `noLegal` (noLegal mounts `src/vr/java`); both build `SUPPORT_LOCAL_NETWORK = true`, so no separate `noLegal` module edit is needed. Confirm there is no standalone `src/noLegal/java/.../MediaCapabilitiesModule.kt` before finishing.
>
> **Flavor placement.** The new field lives on the shared `MediaCapabilities` contract in `src/main/java`. Each per-flavor value is supplied by that flavor's own `MediaCapabilitiesModule` under `src/<flavor>/java/.../di/`, reading its own `BuildConfig.SUPPORT_LOCAL_NETWORK`. No `BuildConfig.SUPPORT_*` guard is added inside `src/main/java`. See `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

## Steps

### Step 01.1 - Add `SUPPORT_LOCAL_NETWORK` BuildConfig flag to every flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> In each `productFlavors { create("<flavor>") { .. } }` block add `buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true")` for `standard`, `noLegal`, `photos`, `legacy`, `vr`, and `buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "false")` for `lite`. Place it next to the existing `SUPPORT_CLOUD` line in each block for locality. Do not add the field to `defaultConfig` or to any `buildTypes` block - it is flavor-scoped, mirroring `SUPPORT_CLOUD`.

**Verification:**

- `Grep` - `SUPPORT_LOCAL_NETWORK` matches exactly 6 times in `app_v2/build.gradle.kts`.
- `Grep` - `"SUPPORT_LOCAL_NETWORK", "false"` matches exactly once (the `lite` block).
- `.\a.ps1 fk` (or `/build`) configures without error.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 grep PASS (6 occurrences, 1 `false` in lite). Config/compile deferred to Phase Done build. Files: build.gradle.kts (+6 lines).

---

### Step 01.2 - Add `supportsLocalNetworkSources` to `MediaCapabilities`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `val supportsLocalNetworkSources: Boolean` property to the `MediaCapabilities` data class, placed beside `supportsCloud`. Do not give it a default value - every constructor site (the 5 flavor modules) must supply it explicitly so a missing flavor wiring fails the build rather than silently defaulting to enabled.

**Verification:**

- `Grep` - `supportsLocalNetworkSources` present in `MediaCapabilities.kt`.
- `Grep` - the property has no `= true`/`= false` default in the declaration.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. Field added beside `supportsCloud`, no default. Files: MediaCapabilities.kt (+1 line).

---

### Step 01.3 - Wire the flag in every flavor `MediaCapabilitiesModule`

**Files:** `app_v2/src/standard/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt`, `app_v2/src/lite/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt`, `app_v2/src/photos/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt`, `app_v2/src/legacy/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In each flavor `MediaCapabilitiesModule`, set `supportsLocalNetworkSources = BuildConfig.SUPPORT_LOCAL_NETWORK` in the `MediaCapabilities(..)` construction, mirroring the existing `supportsCloud = BuildConfig.SUPPORT_CLOUD` assignment. All five modules read their own variant `BuildConfig`. Verify there is no separate `src/noLegal/.../MediaCapabilitiesModule.kt`; if one exists, wire it too.

**Verification:**

- `Grep` - `supportsLocalNetworkSources = BuildConfig.SUPPORT_LOCAL_NETWORK` matches once per flavor module (5 occurrences total across the listed files).
- `Grep` - no remaining `MediaCapabilities(` constructor call omits `supportsLocalNetworkSources` (search all `src/*/java/.../MediaCapabilitiesModule.kt`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS (5 flavor modules wired; no separate noLegal module - vr serves it). Note: `src/test/.../ApplyEnableAllSettingsUseCaseTest.kt` builds `MediaCapabilities` with only 7 of 11 required args - already non-compiling before this change (pre-existing broken test, not an S0448 regression); left untouched, out of scope. Files: 5x MediaCapabilitiesModule.kt (+1 line each).

---

### Step 01.4 - Gate NETWORK ids in `RemoteSourceAvailabilityGate.compileSupported`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/RemoteSourceAvailabilityGate.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `compileSupported(id)`, where CLOUD ids are already resolved via `mediaCapabilities.supportsCloud`, add a parallel branch: NETWORK ids (`RemoteSourceId.NETWORK` = SMB/SFTP/FTP) return `mediaCapabilities.supportsLocalNetworkSources` instead of unconditional `true`. Keep CLOUD behaviour unchanged. Do not introduce a new `BuildConfig` read here - the value already arrives via the injected `MediaCapabilities`.

**Verification:**

- `Grep` - `supportsLocalNetworkSources` referenced in `RemoteSourceAvailabilityGate.kt`.
- `Grep` - `compileSupported` no longer returns a bare `true` for NETWORK ids (the network branch reads the capability).
- `Grep -n "Log\.d\("` returns zero hits in `RemoteSourceAvailabilityGate.kt` (Timber only).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (capability read for NETWORK ids, no bare `true`, no `Log.d`). Compile validated by Phase Done build. Files: RemoteSourceAvailabilityGate.kt (+3 lines net).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fk` BUILD SUCCESSFUL (after clearing stale Kotlin daemons that caused OOM).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` (1814 records).

---

## Handoff Notes to Next Phase

`RemoteSourceAvailabilityGate.compileSupported(SMB/SFTP/FTP)` now returns `false` in `lite`. Tabs, Add-Resource cards, filter chips and ALL-tab filtering already consume the gate, so they hide automatically - no UI edits needed for those surfaces. Phase 02 handles the two surfaces that are NOT yet gate-driven: the welcome page SMB/FTP rows and the local-network permission. The `SUPPORT_LOCAL_NETWORK` flag is available for the registry `flavorGates` reflection in Phase 02.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or user-facing persisted state changed; the change is compile-time capability wiring only.
