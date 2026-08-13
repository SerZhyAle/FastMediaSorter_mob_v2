# Phase 01 - Stereo Config Resolver

**Strategic spec:** [`../S0989_vr-diagxr-activity-decompose.md`](../S0989_vr-diagxr-activity-decompose.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Extract filename-to-projection/layout resolution into `VrStereoConfigResolver`; relocate the `ProjectionType`, `StereoLayout`, `RenderConfig` types out of the Activity file so downstream helpers depend on the resolver, not the Activity.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `StereoDetector` reachable from `src/vr` (already imported by the Activity).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrStereoConfigResolver.kt` | New | ≤ 180 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1500 |

> Flavor placement: vr-only helper lives under `src/vr/java/...` - correct per `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

## Steps

### Step 01.1 - Create VrStereoConfigResolver with relocated types

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrStereoConfigResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `VrStereoConfigResolver(private val stereoDetector: StereoDetector)` in package `com.sza.fastmediasorter.ui.xr.helpers`. Move the top-level `enum class ProjectionType`, `enum class StereoLayout`, and `data class RenderConfig` verbatim from `DiagnosticXrActivity.kt` into this file (top-level, same package `...ui.xr.helpers` - update the Activity to import them). Move `parseFilenameConfig(filename: String): RenderConfig` and the private `StereoMode.toRenderConfigOrNull()` extension into the resolver as `fun resolve(filename: String): RenderConfig` (keep the exact projection/layout token logic, the `StereoDetector` first pass, and all three diagnostic `Timber.d` lines byte-for-byte). Do not change any branch or token.

**Verification:**

- `Glob` - `VrStereoConfigResolver.kt` exists.
- `Grep` - `class VrStereoConfigResolver` matches exactly once.
- `Grep` - `fun resolve(filename: String): RenderConfig` present.
- `Grep` - `enum class ProjectionType` matches exactly once across `src/vr` (moved, not duplicated).

**Status:** `[x]` done

---

### Step 01.2 - Rewire Activity to the resolver

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `DiagnosticXrActivity`, remove the moved `parseFilenameConfig` / `toRenderConfigOrNull` / enum / data-class declarations. Instantiate `private val stereoConfigResolver by lazy { VrStereoConfigResolver(stereoDetector) }` (or assign in `proceedWithInitialization`). Replace every `parseFilenameConfig(x)` call site with `stereoConfigResolver.resolve(x)`. Keep the `@Inject lateinit var stereoDetector` field. Build must stay green.

**Verification:**

- `Grep` - `parseFilenameConfig` returns zero hits in `DiagnosticXrActivity.kt`.
- `Grep` - `stereoConfigResolver.resolve(` present at each former call site (>= 3).
- `/build` - `standard debug` compiles; `vr debug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - `/build` `standard debug` + `vr debug`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] Phase-boundary audit - no unresolved P0/P1.

---

## Handoff Notes to Next Phase

`ProjectionType` / `StereoLayout` / `RenderConfig` now live in `...ui.xr.helpers`; Phase 02/03 helpers reference them there.

---

## Rollback Plan

Revert phase commit(s) - pure code move, no data migration or user-facing surface changed.
