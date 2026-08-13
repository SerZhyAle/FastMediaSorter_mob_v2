# Phase 01 - Flavor capability for VR media controls

**Strategic spec:** [`../S0670_compact-playback-control-dialog.md`](../S0670_compact-playback-control-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Add a flavor-resolved capability flag that is true only on builds exposing VR media controls (`vr` + `noLegal`), so the dialog can gate the 3D tab without reading `BuildConfig` in `src/main` and without the noLegal-only `supportsVrPlayer` trap.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Research [`research/02__3d-tab-flavor-gate.md`](research/02__3d-tab-flavor-gate.md) read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` | Modified | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | Modified | ≤ 60 |

> `vr` flavor module also serves `noLegal` (noLegal mounts `src/vr/java`; see its KDoc). The four non-VR flavor modules (`standard`/`lite`/`photos`/`legacy`) are intentionally NOT edited - they inherit the data-class default `false`. Rule 14/15: no `BuildConfig.IS_*`/`SUPPORT_*` in `src/main`.

---

## Steps

### Step 01.1 - Add `supportsVrMediaControls` to the capability surface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val supportsVrMediaControls: Boolean = false` to the `MediaCapabilities` data class. The default `false` keeps all existing constructors (test helpers, the four non-VR flavor modules) compiling unchanged. Add a one-line KDoc comment stating it is true only on VR-capable builds (`vr` + `noLegal`) and gates VR media UI such as the player 3D tab.

**Verification:**

- `Grep` - `supportsVrMediaControls: Boolean = false` matches once in `MediaCapabilities.kt`.
- `.\a.ps1 fk` compiles (no positional-constructor breakage).

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: MediaCapabilities.kt (+2 LOC). `.\a.ps1 fk` BUILD SUCCESSFUL.

---

### Step 01.2 - Set the flag true in the VR-serving module

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `vr` flavor's `MediaCapabilitiesModule.provideMediaCapabilities`, add `supportsVrMediaControls = true` to the `MediaCapabilities(..)` constructor call. This module compiles into both `vr` and `noLegal`, so both get `true`; the literal `true` (not a `BuildConfig` read) is deliberate because `BuildConfig.SUPPORT_VR_PLAYER` is false on `vr` (S0241).

**Verification:**

- `Grep` - `supportsVrMediaControls = true` matches once in the `vr` module.
- `Grep` - `supportsVrMediaControls` returns zero hits in `src/standard`, `src/lite`, `src/photos`, `src/legacy` modules (they rely on the default).

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: vr/MediaCapabilitiesModule.kt (+3 LOC). Non-VR modules confirmed clean via grep; vr build validates in Phase 04.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard) and confirm `.\a.ps1 fk`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1` (batched in Phase 05).

---

## Handoff Notes to Next Phase

`MediaCapabilities.supportsVrMediaControls` is available via the existing `MediaCapabilitiesEntryPoint` the dialog already uses. Phase 04 reads it to gate the 3D tab; do not re-add `supportsVrPlayer` for this purpose.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed; the new field defaults to `false` so removing it is inert.
