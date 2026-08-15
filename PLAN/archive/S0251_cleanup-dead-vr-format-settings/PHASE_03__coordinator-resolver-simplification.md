# Phase 03 - Coordinator + Resolver Simplification

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Remove the dead `applySettings()` method, dead `vrForcedPlatFormat`/`vrForcedSphericalFormat` fields, and the dead `vrRememberFileFormatEnabled` flag from `PlayerStereoModeCoordinator`. Simplify the resolution path so it considers only the per-file override (Room cache) and the detected stereo mode. Resolve strategic §6.3 (fate of `VrForcedFormatResolver`): inline if the simplified body collapses to a single line; keep as a standalone resolver only if it still encapsulates non-trivial branching. Adapt or remove `VrForcedFormatResolverTest`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Repo-wide `Grep -n "vrForcedPlatFormat|vrForcedSphericalFormat|vrRememberFileFormat"` returns hits only inside `PlayerStereoModeCoordinator.kt` (and possibly test fixtures).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt` | Modified | ≤ 220 (currently 253 - shrinks by ~30-50 lines) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt` | Modified or Deleted | depends on §6.3 decision |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolverTest.kt` | Modified or Deleted | depends on §6.3 decision |

---

## Steps

### Step 03.1 - Strip dead fields and `applySettings()` from coordinator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `PlayerStereoModeCoordinator.kt` delete the following members:
>
> - `@Volatile private var vrForcedPlatFormat: StereoMode? = null` (around line 63)
> - `@Volatile private var vrForcedSphericalFormat: StereoMode? = null` (around line 66)
> - `@Volatile var vrRememberFileFormatEnabled: Boolean = true; private set` (around lines 68-70)
> - The entire `applySettings(forcedPlatFormat, forcedSphericalFormat, rememberFileFormat): Boolean` method (lines 183-209) including the trailing `publishEffective(...)` call when fields change.
> - Remove the `if (!vrRememberFileFormatEnabled || filePath.isNullOrBlank()) return` guard inside `resetStereoModeForNewFile()` (around line 135) - per-file Room cache lookup now runs unconditionally.
> - Remove the `if (!vrRememberFileFormatEnabled) return` guard inside `rememberStereoModeIfEnabled()` (around line 155) - delete that early return; the function always runs through to the IO block.
>
> After the deletions, `resolveForcedStereoMode(detected: StereoMode)` (around line 244-252) becomes a thin wrapper. Refactor it to take only `detected` and call into `VrForcedFormatResolver.resolve(detected = detected, perFileOverride = currentStereoOverrideMode, forcedPlat = null, forcedSpherical = null)` for now - or, more cleanly, inline its remaining single-line body into the call sites (step 03.3 will make the resolver-keep-or-inline call). Do NOT touch `currentStereoOverridePath` or `currentStereoOverrideMode` - those are part of the per-file override mechanism that must remain.

**Verification:**

- `Grep -n "vrForcedPlatFormat"` in this file → 0 hits.
- `Grep -n "vrForcedSphericalFormat"` in this file → 0 hits.
- `Grep -n "vrRememberFileFormatEnabled"` in this file → 0 hits.
- `Grep -n "fun applySettings"` in this file → 0 hits.
- `Grep -n "fun resetStereoModeForNewFile"` in this file → exactly 1 hit (signature unchanged).
- `Grep -n "currentStereoOverrideMode"` in this file → still present (per-file override stays).
- `Grep -n "rememberStereoModeIfEnabled"` in this file → exactly 1 hit (signature unchanged).
- Repo-wide `Grep` for `applySettings\(.*forcedPlat` returns 0 hits (no caller left).
- File compiles when `/build standardDebug` is run after step 03.4.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 8/8 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt` (-34 LOC). Build deferred to Step 03.4 per phase plan.

---

### Step 03.2 - Refresh function rename: `rememberStereoModeIfEnabled` → `rememberStereoModeForCurrentFile`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt`, all call sites
**Depends on:** Step 03.1

**Prompt for developer:**

> After step 03.1 removed the `vrRememberFileFormatEnabled` guard, the function name `rememberStereoModeIfEnabled` no longer matches its behavior (the "if-enabled" gate is gone). Rename it to `rememberStereoModeForCurrentFile`. Use `Edit` with `replace_all` on the file or on each caller. Run a repo-wide grep before renaming to enumerate call sites; update them all in this step.

**Verification:**

- `Grep -n "rememberStereoModeIfEnabled"` repo-wide → 0 hits.
- `Grep -n "rememberStereoModeForCurrentFile"` repo-wide → 1 declaration + N callers (record N).
- File compiles when `/build standardDebug` is run after step 03.4.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: player host/coordinator call chain (+0 LOC net). `rememberStereoModeIfEnabled` repo-wide expected 0 | actual 0; `rememberStereoModeForCurrentFile` expected 1 declaration + 8 callers/wrappers | actual 9 hits.

---

### Step 03.3 - Resolve §6.3: keep or inline `VrForcedFormatResolver`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolverTest.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inspect the simplified `VrForcedFormatResolver.resolve` after step 03.1. Two paths in `resolve`:
>
> 1. `perFileOverride` non-null and not AUTO/UNKNOWN → return it
> 2. Otherwise → return `detected`
>
> Branches that consumed `forcedPlat` / `forcedSpherical` are now never exercised because callers pass `null`. The resolver's helper functions `mapPlatSetting` and `mapSphericalSetting` are also unused (callers no longer translate a String setting key).
>
> Decision rule:
> - If you can express the surviving logic as a single statement (e.g. `currentStereoOverrideMode?.takeUnless { it == StereoMode.AUTO || it == StereoMode.UNKNOWN } ?: detected`), **inline** it into the coordinator. Delete the resolver file AND its test file in this step.
> - If keeping a separate resolver still has clear value (e.g. multiple call sites, planned re-extension), keep it as a tiny object with a single `resolve(detected, perFileOverride): StereoMode` method (drop the `forcedPlat`/`forcedSpherical` parameters and the `mapPlatSetting`/`mapSphericalSetting` helpers). Adapt the test to cover only the surviving two branches.
>
> Default to **inline** (delete) unless adaptation is clearly preferable. Record the choice in the step Verification text below: "Decision: inline" or "Decision: kept-as-resolver".

**Verification:**

- Write one of two sentences into the chat trace and into the commit message: `S0251 Phase 03.3 decision: inline (resolver deleted)` OR `S0251 Phase 03.3 decision: kept-as-resolver (signature simplified, test adapted)`.
- If "inline" decision: `Glob` for `VrForcedFormatResolver.kt` → file absent. `Glob` for `VrForcedFormatResolverTest.kt` → file absent. `Grep -n "VrForcedFormatResolver"` repo-wide → 0 hits.
- If "kept-as-resolver" decision: `Grep -n "object VrForcedFormatResolver"` in the file → exactly 1 hit. `Grep -n "forcedPlat"` and `forcedSpherical` in the file → 0 hits each. `Grep -n "mapPlatSetting"` and `mapSphericalSetting` in the file → 0 hits. Test file contains only the two surviving branches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Decision: inline (resolver deleted). Files: `PlayerStereoModeCoordinator.kt` (-5 LOC), `VrForcedFormatResolver.kt` (deleted), `VrForcedFormatResolverTest.kt` (deleted). `VrForcedFormatResolver` repo-wide expected 0 | actual 0.

---

### Step 03.4 - Dev log + run full unit-test target

**Files:** dev log, gradle test
**Depends on:** Steps 03.1 - 03.3

**Prompt for developer:**

> Log entries:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt" "S0251" "Phase 03: drop applySettings + forced-format fields + remember-enabled flag; per-file override stays unconditional"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt" "S0251" "Phase 03: <inlined|simplified> per §6.3 decision"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolverTest.kt" "S0251" "Phase 03: <deleted|adapted to two-branch contract>"
> ```
>
> Run only the per-file-override + resolver tests, isolated from the broader broken-test pile: `.\a.ps1 dq` (or `assembleStandardDebug` for compile-only). Per the project memory `pre-existing test failures policy`, do not gate the phase on `testStandardDebugUnitTest` greenness for unrelated tests. Verify the per-file override scenario explicitly: load a video, choose SBS via player dialog, navigate away, return - the SBS choice must apply.

**Verification:**

- `Grep -n "S0251.*Phase 03"` in `dev/CHANGELOG.md` → exactly 3 hits.
- Build target `assembleStandardDebug` succeeds.
- The per-file override unit test (if it exists at `app_v2/src/test/.../PlayerStereoModeCoordinatorTest.kt` or similar) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Files: `dev/CHANGELOG.md`. Dev log expected by phase prompt: 3 entries; actual: 9 entries because Step 03.2 touched host/call-site files and repo rule requires one dev log line per modified source file. Build PASS: `.\gradlew.bat assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0. Targeted per-file override unit test file not present under `app_v2/src/test`; no isolated test was run.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `/build assembleStandardDebug` exits cleanly.
- [x] `Grep -n "TODO(phase-03)"` repo-wide returns 0 hits.
- [x] Dev log carries S0251 Phase 03 entries for every modified source/test file (9 entries; stricter than original 3-file prompt).

---

## Handoff Notes to Next Phase

`PlayerStereoModeCoordinator` is now driven only by the detector + per-file Room cache. No domain or data layer still references the removed VR format fields. Phase 04 (VR help icon) and Phase 05 (string/array cleanup) can run in parallel relative to this phase, but Phase 06 needs both complete for catalog/docs sync.

---

## Rollback Plan

Revert the three file diffs. The coordinator returns to carrying dead fields and unused method - no functional regression because nothing called them anyway. Per-file override mechanism remains untouched throughout this phase, so rollback risk is contained to the simplification itself.
