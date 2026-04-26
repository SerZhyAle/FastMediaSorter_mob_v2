# Phase 01 — stereo Filename Token

**Strategic spec:** [`../spec_vr-stereo-state.md`](../spec_vr-stereo-state.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Extend `StereoDetector.detectFromFilename` to recognise `stereo` as a spherical-SBS hint, add `mono` as an explicit flat marker, and resolve conflicts between them deterministically.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved (all resolved 2026-04-26).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` | Modified | ≤ 380 |

> File is 332 lines — within budget, no backup required.

---

## Steps

### Step 1.1 — Add `hasStereo` and `hasMono` precomputed tokens

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `detectFromFilename`, in the block that precomputes token presence (lines starting with `val hasCylinder`, `val hasVr180`, …), add two new boolean variables immediately after the `hasCubemap` line:
>
> ```kotlin
> val hasStereo = containsToken(stem, "stereo")
> val hasMono   = containsToken(stem, "mono")
> ```
>
> Do not change any other logic in this step.

**Verification:**

- `Grep` — `val hasStereo = containsToken\(stem, "stereo"\)` present exactly once in `StereoDetector.kt`.
- `Grep` — `val hasMono   = containsToken\(stem, "mono"\)` present exactly once in `StereoDetector.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `StereoDetector.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 2/2 PASS. Files: StereoDetector.kt (+2 LOC). Dev log pending (batched at phase end).

---

### Step 1.2 — Add conflict rule: `hasStereo && hasMono` → MONO

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In the `return when { … }` expression of `detectFromFilename`, add a new branch as the **first case** (before `hasCylinder`):
>
> ```kotlin
> hasStereo && hasMono -> {
>     Timber.w("$TAG: filename conflict stereo+mono — mono wins for stem='$stem'")
>     logMatch("MONO", StereoMode.MONO)
> }
> ```
>
> This ensures an explicit `mono` marker always beats the generic `stereo` word when both appear in the same filename.

**Verification:**

- `Grep` — `hasStereo && hasMono` present in `StereoDetector.kt`.
- `Grep` — `filename conflict stereo\+mono` present in `StereoDetector.kt`.
- The new branch appears before `hasCylinder ->` in the `when` block (open file and confirm ordering visually or via line number check).

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 3/3 PASS (branch present, warning text present, before hasCylinder). Files: StereoDetector.kt (+4 LOC).

---

### Step 1.3 — Add spherical `stereo` detection branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> In the `return when { … }` expression of `detectFromFilename`, inside the "Spherical / panoramic" block, add two new branches:
>
> 1. After `hasVr180 -> …` and before `has180 && hasSbs -> …`:
>    ```kotlin
>    has180 && hasStereo -> logMatch("EQUIRECT_180_SBS", StereoMode.EQUIRECT_180_SBS)
>    ```
>
> 2. After `has360 && hasSbs -> …` and before `has360 && hasOu -> …`:
>    ```kotlin
>    has360 && hasStereo -> logMatch("EQUIRECT_360_SBS", StereoMode.EQUIRECT_360_SBS)
>    ```
>
> The `stereo` token is treated as an explicit SBS indicator only when paired with a spherical context
> (`360` or `180`). Standalone `stereo` without spherical context is intentionally left unhandled
> (§7 risk mitigation: avoids false positives on non-VR files).
>
> After both changes, update the KDoc block for `detectFromFilename` to list the two new recognised
> patterns:
> - `` `360` + `stereo` → [StereoMode.EQUIRECT_360_SBS] ``
> - `` `180` + `stereo` → [StereoMode.EQUIRECT_180_SBS] ``

**Verification:**

- `Grep` — `has180 && hasStereo -> logMatch\("EQUIRECT_180_SBS"` present in `StereoDetector.kt`.
- `Grep` — `has360 && hasStereo -> logMatch\("EQUIRECT_360_SBS"` present in `StereoDetector.kt`.
- `Grep` — `` `360` + `stereo` `` present in the KDoc (confirms doc updated).
- `Grep` — `Log\.d\(` returns zero hits in `StereoDetector.kt`.
- Manual: `detectFromFilename("Boersensaal_Hamburg_stereo_360_8K_25s.webm")` returns `EQUIRECT_360_SBS`
  — trace through the when-expression: `has360=true`, `hasStereo=true` → branch 2 fires before `has360 && hasSbs` (which is false).

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 5/5 PASS (both branches present, KDoc updated, no Log.d). Files: StereoDetector.kt (+6 LOC total).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-04-26.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `StereoDetector.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `StereoDetector.detectFromFilename` now recognises `stereo` + `360` → `EQUIRECT_360_SBS` and `stereo` + `180` → `EQUIRECT_180_SBS`.
- Conflict rule: `stereo` + `mono` in same stem → `MONO` (explicit wins over generic).
- No changes to `detectFromFormat`, `detectForVideo`, or any callers — Phase 02 proceeds on the existing call surface.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
