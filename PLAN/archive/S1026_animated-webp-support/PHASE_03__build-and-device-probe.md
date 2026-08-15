# Phase 03 - build-and-device-probe

**Goal:** Prove the build and place the device probe.

## Steps

- [ ] **3.1** Capability record: `scripts/all_features/add.ps1` - S1026 (area "Media / Image viewer", flavors "standard,lite,photos,legacy", EN-only: "Animated WebP and APNG images play in the viewer like GIFs"). Catalog sync `scripts/catalog_sync.ps1 -Module app_v2`; set role/status for any new decoder class. Verify: ALL_FEATURES has S1026.
- [ ] **3.2** Insert one `Timber.d("S1026: animated-image decoded animatable=<..> ext=<..>")` at the decode/bind entry (device probe; this ticket goes to BlockNeedUserTest) before the final build. Build: `a.ps1 dq` standard debug PASS. Verify: `BUILD SUCCESSFUL`.

## Done criteria
- Build green; capability recorded; device probe in place.
