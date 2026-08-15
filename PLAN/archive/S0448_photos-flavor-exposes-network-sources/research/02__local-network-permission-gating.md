# Research 02 - Local-network permission gating in `lite`

Resolves strategic §6 item 2.

## Question

How is the local-network runtime permission removed from `lite` - flavor manifest removal, or runtime-request gate only?

## Findings

- `ACCESS_LOCAL_NETWORK` is declared once in `src/main/AndroidManifest.xml` with `android:minSdkVersion="37"`.
- `PermissionRegistryRepositoryImpl` declares an `access_local_network` registry entry with `flavorGates = emptySet()`, so it surfaces on every flavor at API 37+ (welcome flow, Settings).
- `src/lite/AndroidManifest.xml` already uses `tools:node="remove"` 12+ times for components not used in `lite` - the merger pattern is established and safe here.
- The registry evaluates `flavorGates` by reflecting `BuildConfig` field names (`evaluateFlavorGates`). A `flavorGates = setOf("SUPPORT_LOCAL_NETWORK")` entry will hide the permission from the registry on `lite` once the flag exists.

## Resolution

Two layers, both required:

1. **Primary - manifest removal.** Add `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" tools:node="remove" />` to `src/lite/AndroidManifest.xml`. This drops the declaration from the merged `lite` manifest entirely, so the OS never associates the permission with the build (store-clean, no unexpected permission).
2. **Defense-in-depth - registry gate.** Add `flavorGates = setOf("SUPPORT_LOCAL_NETWORK")` to the `access_local_network` entry in `PermissionRegistryRepositoryImpl`. This prevents the welcome "Enable all" flow and Settings from attempting to request a permission the manifest no longer declares (which would otherwise be a silent no-op but still render UI).

Both are cheap and address different surfaces (OS-declared permission set vs in-app request UI). Use both.

## Impact on plan

- Phase 02 step: add `tools:node="remove"` line to `src/lite/AndroidManifest.xml`.
- Phase 02 step: add `flavorGates = setOf("SUPPORT_LOCAL_NETWORK")` to the registry entry.
- Depends on the `SUPPORT_LOCAL_NETWORK` BuildConfig flag from Phase 01 (reflection target must exist).
