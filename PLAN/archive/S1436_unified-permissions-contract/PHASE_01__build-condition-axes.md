# Phase 01 - Build-condition axes

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Give the registry a condition channel for every axis the merged manifest actually varies on - flavor, build type and the two gradle switches that add permissions to `standard` - by raising each to a compile-time `BuildConfig` boolean and renaming the entry's gate set to match its new reach. No entry composition changes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - items 1 and 2, both Resolved with artifacts.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 1700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt` | Modified | ≤ 140 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `app_v2/build.gradle.kts` is over 500 LOC - step 01.1 carries the backup sub-step.

---

## Steps

### Step 01.1 - Declare the three missing manifest axes as BuildConfig booleans

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `app_v2/build.gradle.kts` to `temp/S1436/` with a timestamped name first (Rule 5, the file is over 500 LOC).
>
> Add three build-config booleans:
>
> - `DECLARES_BATTERY_OPTIMIZATION` - `buildConfigField` in `defaultConfig` as `true`, overridden to `false` in the `release` build type, and restored to `true` in `staging` and `benchmark` after their `initWith(getByName("release"))`, because `initWith` copies the release value while the `src/release` manifest overlay does not apply to their source sets. This mirrors the `tools:node="remove"` of `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in `app_v2/src/release/AndroidManifest.xml:20-21`.
> - `DECLARES_SCREEN_CAPTURE` and `DECLARES_OVERLAY_PERMISSION` - set per variant with `variant.buildConfigFields.put(..)` inside the existing `androidComponents.onVariants` block, immediately after the manifest injections they mirror. `DECLARES_SCREEN_CAPTURE` reuses the `injectSharedCaptureManifest` val already computed there; `DECLARES_OVERLAY_PERMISSION` is `noLegal`, or `standard` while `edgeGestureOverlayStandardEnabled`. Import `com.android.build.api.variant.BuildConfigField`.
>
> These two may **not** go in the flavor blocks: their value is a gradle-property expression rather than a literal, and `scripts/docs/generate-flavor-matrix.ps1` parses `productFlavors` for literal booleans and fails the flavor-matrix gate on anything else. `onVariants` is also the better home - the axis then sits beside the condition it mirrors and cannot drift from it.
>
> Do not add an axis for `fms.edgeGestureTile`: its injected manifest declares no permission, and the Verification below pins that fact so a future change to it is caught.

**Why:**

Strategic §4 states the registry can filter on two axes while the manifest varies on at least five, and that permissions depending on the unexpressed axes were therefore never entered into it at all; ADR-3 requires every such axis to be expressed as a build feature flag resolved at compile time, because S0970 showed the reflective form being constant-folded away by R8 and silently disabling a permission on every release build.

**Verification:**

- `Grep` - `DECLARES_SCREEN_CAPTURE`, `DECLARES_OVERLAY_PERMISSION` and `DECLARES_BATTERY_OPTIMIZATION` each appear in `app_v2/build.gradle.kts`.
- `Grep` - `uses-permission` returns zero hits in `app_v2/src/standardEdgeTile/AndroidManifest.xml`.
- `Glob` - a timestamped `build.gradle.kts` backup exists under `temp/S1436/`.
- `.\a.ps1 fr` and `.\a.ps1 fk` each exit 0.
- `Grep` - all three fields appear in `app_v2/build/generated/source/buildConfig/standard/debug/com/sza/fastmediasorter/BuildConfig.java` after that compile, proving the `onVariants` values reach the generated class.
- `pwsh -NoProfile -File scripts/docs/generate-flavor-matrix.ps1` exits 0.

**Status:** `[x]` done

---

### Step 01.2 - Rename the entry gate set from flavor-scoped to build-scoped

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Rename `PermissionEntry.flavorGates` to `buildGates`, `PermissionRegistryRepositoryImpl.evaluateFlavorGates` to `evaluateBuildGates`, `resolveFlavorGate` to `resolveBuildGate` and `declaredFlavorGateFields` to `declaredBuildGateFields`. Update every call site and the existing test. Keep the KDoc on `resolveBuildGate` that records the S0970 reflection incident - it explains why the `when` may never become a reflective lookup.

**Why:**

The set now carries build-type and gradle-switch conditions as well as flavor capability flags, and strategic §5.1 defines the axis set as "version, build feature, build type" - a field still named after only one of the three would invite the next author to add a second, ungated mechanism for the others, which is the defect ADR-3 exists to prevent.

**Verification:**

- `Grep` - `flavorGates` returns zero hits under `app_v2/src/main` and `app_v2/src/test`.
- `Grep` - `buildGates` matches in `PermissionEntry.kt` and `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `S0970` still matches in `PermissionRegistryRepositoryImpl.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 01.3 - Resolve the new gate names and assert every gate name has an arm

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Turn the `resolveBuildGate` `when` into a `private val buildGateValues: Map<String, Boolean>` whose values are direct `BuildConfig.<FIELD>` reads - still the compile-time table the S0970 KDoc requires, but now a value a test can inspect instead of a control structure it cannot. `resolveBuildGate` becomes a lookup with the same `Timber.e` fallback. Add `DECLARES_SCREEN_CAPTURE`, `DECLARES_OVERLAY_PERMISSION`, `DECLARES_BATTERY_OPTIMIZATION`, `IS_NO_LEGAL_FLAVOR` and `SUPPORT_MIC_RECORDING` alongside the four existing names.
>
> Expose the map's keys as `@get:VisibleForTesting val mappedBuildGateFields`, and extend the test to assert `declaredBuildGateFields - mappedBuildGateFields` is empty - that the resolver is total over the declared set, not merely that a `BuildConfig` field of that name exists.

**Why:**

Research artifact 01 records that the current test asserts only that a gate name maps to an existing `BuildConfig` field, so it cannot catch a gate that exists but is the wrong one - the exact shape of the microphone defect strategic §1 names, and a check that is about to be relied on by four more gate names.

**Verification:**

- `Grep` - all five new field names appear inside `buildGateValues` in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `declaredBuildGateFields` and `mappedBuildGateFields` are both referenced in `PermissionRegistryRepositoryImplTest.kt`.
- `.\a.ps1 fu --tests "*PermissionRegistryRepositoryImplTest*"` runs green, and the fresh `test-results` XML for the class reports a non-zero test count (record `expected: PASS | actual: <result>`) - a green gradle run alone does not prove the class executed.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` `expected: exit 0 | actual: exit 0`, APK `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` - one row per `post-change` run; the full per-file batch runs at ticket finalization.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog-sync` PASS inside each `post-change` run.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Phase-boundary audit - 2026-08-06

- **Layer 1 (architecture / readability).** `buildGateValues` is an eager nine-entry `mapOf` on a `@Singleton`; cost is negligible and the eager form is what makes the table a value rather than a control structure. `mappedBuildGateFields` is test-only surface and carries `@get:VisibleForTesting`, matching the neighbouring `declaredBuildGateFields`. No finding.
- **Rule 14 (flavor isolation), P3 - accepted and documented.** The table gained a `BuildConfig.IS_NO_LEGAL_FLAVOR` read inside `src/main`. `assert-flavor-flags-not-growing` reports `baseline 26 | actual 26 | delta 0`, exit 0. This is the name-to-value table the S1379 comment above it already exempts - the single resolution point ADR-3 requires - not a consumer guard. Consumer guards continue to go through `CapabilityAvailability`.
- **Layers 2, 3, 4** - not applicable: this phase touched no lifecycle, coroutine, listener or Room surface.

---

## Step Log

- 2026-08-06 - Step 01.1 DONE. `DECLARES_BATTERY_OPTIMIZATION` in `defaultConfig` (true), `release` (false), `staging` and `benchmark` (true, restored after `initWith(release)` because the `src/release` overlay does not apply to their source sets). `DECLARES_SCREEN_CAPTURE` and `DECLARES_OVERLAY_PERMISSION` set per variant in `androidComponents.onVariants`.
  - First attempt put the latter two in the `standard` / `noLegal` flavor blocks as `"$screenCaptureStandardEnabled"`. `scripts/docs/generate-flavor-matrix.ps1` refused it - `non-literal boolean for DECLARES_SCREEN_CAPTURE at flavor 'standard'`, exit 2 - and the `flavor-matrix-doc-gate` in `post-change.ps1` failed with it. The step prompt above was corrected to the `onVariants` placement, which is also the better one: the axis now sits beside the manifest injection it mirrors.
  - `src/standardEdgeTile/AndroidManifest.xml` confirmed to declare zero `uses-permission`, so `fms.edgeGestureTile` needs no axis.
  - `expected: exit 0 | actual: exit 0` for `.\a.ps1 fr`, `.\a.ps1 fk` and `generate-flavor-matrix.ps1`. Generated `standard/debug/BuildConfig.java` carries `DECLARES_BATTERY_OPTIMIZATION = true`, `DECLARES_OVERLAY_PERMISSION = true`, `DECLARES_SCREEN_CAPTURE = true`.
  - Backup: `temp/S1436/build.gradle.kts.20260806-163000.bak`.
- 2026-08-06 - Step 01.3 DONE. The `when` became `buildGateValues: Map<String, Boolean>` with direct `BuildConfig` reads - the same compile-time table, but now inspectable, which is what makes totality testable at all. Nine names mapped; `mappedBuildGateFields` exposed for the test. `expected: PASS | actual: PASS` - `tests=8 failures=0 errors=0 skipped=0`, timestamp 2026-08-06T14:42:06Z, read from `TEST-com.sza.fastmediasorter.data.permissions.PermissionRegistryRepositoryImplTest.xml` rather than from the green gradle line.
- 2026-08-06 - Step 01.2 DONE. `flavorGates` -> `buildGates`, `evaluateFlavorGates` -> `evaluateBuildGates`, `resolveFlavorGate` -> `resolveBuildGate`, `declaredFlavorGateFields` -> `declaredBuildGateFields`; the `Timber.e` message and the test name follow. All 13 references lived in 3 files. `expected: 0 hits | actual: 0 hits` for `flavorGates` across `app_v2/src` and `wear/src`; S0970 KDoc retained; `expected: exit 0 | actual: exit 0` for `.\a.ps1 fk`.

---

## Handoff Notes to Next Phase

An entry can now be conditioned on any axis the manifest varies on, and every gate name is resolved by a compile-time arm proven total by the unit test. No entry uses a new axis yet - composition is unchanged, which is what makes this phase safe to merge alone.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. The three new `BuildConfig` fields are unread until phase 03, so reverting them cannot break a consumer.
