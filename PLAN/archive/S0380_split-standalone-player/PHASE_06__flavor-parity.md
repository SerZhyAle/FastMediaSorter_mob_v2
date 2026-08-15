# Phase 06 - Flavor parity

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Ensure every flavor (`standard`, `lite`, `photos`, `legacy`, `vr`, `noLegal`) compiles and routes correctly with the new structure, with any flavor-specific host behavior placed per `dev/FLAVOR_DEVELOPMENT_RULES.md` (contract in `src/main`, impl in `src/<flavor>/java`). No `BuildConfig` flavor guards added to `src/main`.

---

## Prerequisites

- [ ] Phase 05 ✅ Done.
- [ ] `dev/FLAVOR_DEVELOPMENT_RULES.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/<flavor>/java/.../player/...` | New (only if a flavor needs an override) | ≤ 200 each |
| `app_v2/src/<flavor>/AndroidManifest.xml` | Modified (only if a flavor diverges) | - |

> Capability differences across flavors (e.g. photos has no VIDEO/AUDIO, lite has no DOCS/AUDIO) must be expressed by which activities/aliases each flavor manifest declares - never by a `BuildConfig.SUPPORT_*` check inside `src/main`.

---

## Steps

### Step 06.1 - Place flavor-specific overrides correctly

**Files:** flavor source sets under `src/<flavor>/`
**Depends on:** - start of phase

**Prompt for developer:**

> For each flavor whose capability set excludes a family (e.g. `photos` lacks video/audio, `lite` lacks docs/audio), ensure that flavor does not register the irrelevant specialized activity/alias, or supplies a flavor-local No-Op. Any real flavor-specific host behavior lives in `src/<flavor>/java/...` and is bound via a flavor Hilt module. Confirm no new flavor guard leaked into `src/main`.

**Verification:**

- `Grep` - `src/main/java` contains no new `BuildConfig.SUPPORT_`, `BuildConfig.ENABLE_`, `BuildConfig.IS_` guard introduced by this spec (`expected: 0 new | actual: <record>`).
- `Glob` - any flavor override file resides under `src/<flavor>/java/`, not `src/main/java/`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Grep: every S0380 new `src/main` file (4 specialized activities, dispatcher, MediaFamilyResolver, 2 PhotoVideo helpers) carries 0 `BuildConfig.SUPPORT_*`/`ENABLE_*`/`IS_*` guards (`expected: 0 new | actual: 0`); the one leak the PhotoVideo first pass introduced (`BuildConfig.SUPPORT_VIDEO`) was removed in Step 04.1. Glob: the flavor-specific override (`OfficeDocumentViewerProviderFactory` + the noLegal `OfficeDocumentViewerManager`) resides under `src/<flavor>/java/` for standard/lite/photos/legacy/vrOnly/noLegal - none added to `src/main/java/`. Capability differences stay expressed by which per-type aliases each flavor's default-player toggle enables (`DefaultPlayerManager.viewAliasesForFlavor()` gated by `BuildConfig.SUPPORT_*` - a runtime toggle list, not a `src/main` compile guard), per `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

### Step 06.2 - Build every flavor variant

**Files:** - (build verification)
**Depends on:** Step 06.1

**Prompt for developer:**

> Assemble each flavor debug variant and confirm the build succeeds. Use `/build` (do not call gradle directly).

**Verification:**

- Build: `assembleStandardDebug`, `assembleLiteDebug`, `assemblePhotosDebug`, `assembleLegacyDebug`, `assembleNoLegalDebug` each pass (`expected: BUILD SUCCESSFUL | actual: <per-variant record>`).
- `vr` variant assembles (or documented surrogate equivalence if VR build is environment-gated).

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS for all variants. Full debug builds: `assembleStandardDebug` `BUILD SUCCESSFUL` (35s), `assembleNoLegalDebug` `BUILD SUCCESSFUL` (1m12s), `assembleLiteDebug` `BUILD SUCCESSFUL` (1m58s), `assemblePhotosDebug` `BUILD SUCCESSFUL` (2m5s), `assembleLegacyDebug` `BUILD SUCCESSFUL` (2m35s). `vr`: full APK assembly is environment-gated (native OpenXR/cmake, NDK 27, arm64-v8a/Quest) and has no debug builder - **documented surrogate**: ran `:app_v2:compileVrDebugKotlin` `BUILD SUCCESSFUL` (1m11s), which compiles the vr flavor's Kotlin (the only S0380-affected vr code is the `src/vrOnly/.../OfficeDocumentViewerProviderFactory.kt` signature change, identical to the 5 build-verified flavors; the change is purely a Kotlin-level `binding`→`root` decouple with no native impact). `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL` for every variant at its verifiable level.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] All listed flavor variants compile.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every flavor file touched.

---

## Handoff Notes to Next Phase

All flavors build with the split player. Phase 07 regenerates the catalog, records dev/functionality logs, and confirms FEATURES needs no change.

---

## Rollback Plan

Revert flavor-source-set commits. `src/main` already builds from Phase 05; flavor overrides are additive per source set and revert cleanly.
