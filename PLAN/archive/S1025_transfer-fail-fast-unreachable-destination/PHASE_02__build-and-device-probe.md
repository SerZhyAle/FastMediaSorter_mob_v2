# Phase 02 - build-and-device-probe

**Goal:** Prove the build and place the device probe.

## Steps

- [ ] **2.1** Capability record: `scripts/all_features/add.ps1` - S1025 (area "File transfer", flavors "standard,lite,photos,legacy", EN "Batch transfer aborts fast with a clear message when the destination server is unreachable"). Catalog sync `scripts/catalog_sync.ps1 -Module app_v2` (+ set.ps1 if a new class was added). Verify: ALL_FEATURES has S1025.
- [ ] **2.2** Insert one `Timber.d("S1025: preflight destination probe host=<..> reachable=<..>")` at the probe site (device probe - ticket goes to BlockNeedUserTest) before the final build. Build: `a.ps1 dq` standard debug PASS; re-run `*FileOperationUseCaseTest*`. Verify: `BUILD SUCCESSFUL`; test green.

## Done criteria
- Build green; capability recorded; device probe in place.
