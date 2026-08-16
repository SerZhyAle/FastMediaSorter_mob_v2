# Phase 01 - Crop request seam

**Strategic spec:** [`../S1558_cast-single-eye-crop.md`](../S1558_cast-single-eye-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Carry the panel's already-decided single-eye geometry across the `CastController` seam, so the Cast path receives a crop request instead of deriving one. No transcoding and no Cast behaviour change yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1558 phase 01"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastStereoCrop.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastController.kt` | Modified | ≤ 60 |
| `app_v2/src/castDisabled/java/com/sza/fastmediasorter/core/cast/NoOpCastController.kt` | Modified | ≤ 40 |
| `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 975 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1425 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 960 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> `VideoPlayerManager.kt` (951), `PlayerActivity.kt` (1417) and `PlayerManagerInitializer.kt` (956) all exceed 500 LOC - step 01.0 backs them up before any edit. `PlayerActivity.kt` sits 83 lines under the 1500 limit; this phase adds one argument at one call site and must not approach it.
>
> **Flavor placement.** The enum and the interface change live in `src/main/java/`; the no-op override stays in `src/castDisabled/`, the real override in `src/castEnabled/`. No flavor guard in `src/main`.
>
> **No UI placement decision is taken here, despite three `ui/player/` files appearing above.** This phase adds one accessor and passes one extra argument at two existing call sites; it creates no control, no dialog, no layout and no visible surface, and the user-visible behaviour after this phase is byte-identical to today. CLAUDE.md Rule 10 and the `/spec-tech` self-check exist to stop placement being guessed - there is nothing here to place. The one surface this ticket does add is Phase 04's Toast, which reuses the pattern already used five times in the same method.

---

## Steps

### Step 01.0 - Back up the three oversized player files

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy each of the three files to `temp/S1558/` with a timestamped name before editing them.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup under the ticket's `temp/` directory before editing any file over 500 LOC, and all three cross that line.

**Verification:**

- `Glob` - `temp/S1558/VideoPlayerManager*.kt` matches at least one file.
- `Glob` - `temp/S1558/PlayerActivity*.kt` matches at least one file.
- `Glob` - `temp/S1558/PlayerManagerInitializer*.kt` matches at least one file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Backed up VideoPlayerManager/PlayerActivity/PlayerManagerInitializer to temp/S1558 (20260814_0150); all three Glob predicates PASS

---

### Step 01.1 - Add the `CastStereoCrop` model and the `StereoMode` mapping

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastStereoCrop.kt`
**Depends on:** Step 01.0

**Prompt for developer:**

> Create `CastStereoCrop` as an enum with exactly two entries, `RIGHT_HALF` and `BOTTOM_HALF`, plus a top-level `fun StereoMode.toCastStereoCrop(): CastStereoCrop?`. Map `SBS_FULL`, `SBS_HALF`, `EQUIRECT_360_SBS`, `EQUIRECT_180_SBS` and `VR180_FISHEYE_SBS` to `RIGHT_HALF`; map `OU` and `EQUIRECT_360_OU` to `BOTTOM_HALF`; return `null` for every other mode. Mirror `PanelStereoCropApplier.buildMatrixFor` branch for branch and add a KDoc line saying that file is the source of the mapping, so a future mode added there is added here too.

**Why:**

Strategic §11 criterion 1 requires the receiver to show the same eye as the panel, and the panel keeps the right half for SBS and the bottom half for OU; encoding that mapping once, next to a pointer at its origin, is what stops the two from drifting apart.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastStereoCrop.kt` exists.
- `Grep` - `enum class CastStereoCrop` matches exactly once in that file.
- `Grep` - `RIGHT_HALF` and `BOTTOM_HALF` each match in that file.
- `Grep` - `fun StereoMode.toCastStereoCrop(): CastStereoCrop?` present.
- `Grep` - `VR180_FISHEYE_SBS` present in that file (proves the full SBS set was mapped, not just the two obvious modes).

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - CastStereoCrop.kt created; all 5 predicates PASS (enum x1, both entries, extension signature, VR180_FISHEYE_SBS mapped); max line 100 chars

---

### Step 01.2 - Widen the `CastController` seam with an optional crop argument

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastController.kt`, `app_v2/src/castDisabled/java/com/sza/fastmediasorter/core/cast/NoOpCastController.kt`, `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Change `CastController.sendCurrentMedia` to `fun sendCurrentMedia(file: MediaFile, stereoCrop: CastStereoCrop? = null)` and update the KDoc to state that `null` means cast the file untouched. Update the `castDisabled` no-op override to match. In `CastMediaManagerImpl`, accept the argument and hand it to `resolveAndSend` as a parameter; do not use it yet beyond passing it through.

**Why:**

Strategic §5 places the crop decision in the player and the crop execution in the Cast path, so the seam between them has to carry the decision; the default of `null` keeps every existing caller compiling and keeps the untouched-file path the fallback that strategic §11 criterion 2 requires.

**Verification:**

- `Grep` - `fun sendCurrentMedia(file: MediaFile, stereoCrop: CastStereoCrop? = null)` present in `CastController.kt`.
- `Grep` - `stereoCrop` present in `NoOpCastController.kt`.
- `Grep` - `private suspend fun resolveAndSend` in `CastMediaManagerImpl.kt` shows a second parameter.
- `.\a.ps1 fk` exits 0 (standard flavor compiles with the widened seam).

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Seam widened across CastController/NoOpCastController/CastMediaManagerImpl; all 3 greps PASS; fk (standard) exit 0 and check-standard-fast -Flavor Vr exit 0 - vr is the only flavor compiling castDisabled, so the no-op override is proven too

---

### Step 01.3 - Expose the panel's effective crop from `VideoPlayerManager` and pass it at both call sites

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `val currentPanelStereoCrop: CastStereoCrop?` to `VideoPlayerManager`, whose getter yields `null` when `panelStereoSingleEyeEnabled` is false or `vrImmersiveActive` is true, and otherwise `stereoVideoProcessor.getCurrentMode().toCastStereoCrop()`. Reuse the same `panelStereoSingleEyeEnabled && !vrImmersiveActive` condition the three existing `PanelStereoCropApplier.apply` call sites use rather than writing a new one. It must be a property, not a function: `VideoPlayerManager` already sits at detekt's 40-function `TooManyFunctions` threshold and is not baselined for it, so a 41st function fails the scoped gate. Then pass it as the second argument at `PlayerActivity.kt:852` and `PlayerManagerInitializer.kt:727`, reading it off the `_videoPlayerManager` backing field rather than the `videoPlayerManager` getter - that getter lazily constructs the manager, and casting an image must not be what instantiates it.

**Why:**

Strategic §3.1 fixes the trigger as the same panel flag with no separate Cast setting, and the VR override already suppresses the panel crop to avoid double-cropping, so the Cast request has to honour both conditions or it would crop content the panel is deliberately leaving whole.

**Verification:**

- `Grep` - `val currentPanelStereoCrop: CastStereoCrop?` matches exactly once in `VideoPlayerManager.kt`.
- `Grep` - `vrImmersiveActive` present inside that getter body.
- `Grep` - `_videoPlayerManager?.currentPanelStereoCrop` present in both call-site files (backing field, not the lazy getter).
- `Grep` - `sendCurrentMedia(currentFile` in `PlayerActivity.kt` shows a second argument.
- `Grep` - `sendCurrentMedia(currentFile` in `PlayerManagerInitializer.kt` shows a second argument.
- `Grep` - `Log\.d\(` returns zero hits in all three modified files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Accessor + both call sites done; used _videoPlayerManager backing field instead of the lazy getter so casting an image cannot instantiate VideoPlayerManager; all greps PASS, fk exit 0

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`CastStereoCrop` is the only vocabulary the later phases speak: `RIGHT_HALF` and `BOTTOM_HALF` are already resolved against the panel's own condition, so Phase 02 turns a crop value into pixels and never consults `StereoMode`, the single-eye setting, or the VR override again. Cast output is still byte-identical to today - nothing observable changed yet.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. The widened signature has a default argument, so reverting the player call sites alone leaves a compiling tree.
