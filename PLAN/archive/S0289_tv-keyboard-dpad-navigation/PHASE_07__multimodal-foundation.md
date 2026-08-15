# Phase 07 - Multimodal foundation

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 08, Phase 09, Phase 10
**Steps done:** 4 / 4
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Introduce the shared multimodal foundation that later phases can reuse: base-level mouse dispatch, fallback semantics for extra mouse buttons, and Activity hooks that let simple screens inherit the contract without bespoke routers.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Strategic §3.3 delegated assumptions are accepted.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MouseEventHandler.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ActivityMouseDispatchHelper.kt` | New | ≤ 220 |

> This phase must not widen into screen-specific behaviour. Main / Browse / Player adaptations belong to Phase 08, and simple-screen parity belongs to Phase 09.

---

## Steps

### Step 07.1 - Add shared activity mouse dispatch helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ActivityMouseDispatchHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a small helper that turns `MouseEventHandler` callbacks into safe Activity-level defaults for simple screens. It must expose wheel scrolling for the current scroll target, context / long-click fallback for right-click, Back handling for XButton1, and a no-op-safe forward path for XButton2.
>
> Keep it BaseActivity-friendly: the helper must not depend on Main / Browse / Player-specific classes.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ActivityMouseDispatchHelper.kt` exists.
- `Grep` - `class ActivityMouseDispatchHelper` matches exactly once.
- `Grep` - `fun handleGenericMotionEvent` matches exactly once in the new file.

**Status:** `[x]` done

**Step Log:** 2026-05-22 - Added `ActivityMouseDispatchHelper` with activity-safe wheel, right-click, middle-click, back, and forward fallbacks for simple screens.

---

### Step 07.2 - Extend MouseEventHandler legacy fallback for secondary buttons

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MouseEventHandler.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Extend the existing shared mouse parser so legacy callers without a remappable `KeyBindingManager` still get sensible defaults for right-click, middle click, XButton1 and XButton2. Preserve the resolver path when a manager is available. Do not add screen-specific branching in this class.

**Verification:**

- `Grep` - `dispatchSecondaryButton` matches exactly once.
- `Grep` - `BUTTON_SECONDARY|BUTTON_TERTIARY|BUTTON_BACK|BUTTON_FORWARD` matches at least 4 times.
- `Grep` - `keyBindingManager ?: return false` no longer appears in `MouseEventHandler.kt`.

**Status:** `[x]` done

**Step Log:** 2026-05-22 - Extended legacy fallback dispatch so generic activities still get secondary-button semantics when no `KeyBindingManager` is present.

---

### Step 07.3 - Wire BaseActivity into the shared mouse foundation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ActivityMouseDispatchHelper.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> In `BaseActivity`, add default mouse routing by delegating both touch-pipeline and generic-motion events into the new helper before falling back to the existing Activity dispatch chain. Keep user-action logging intact and do not break subclass overrides in Main / Browse / Player.
>
> Add small open hooks only when the helper needs them for later phases. Do not add gamepad logic in this step.

**Verification:**

- `Grep` - `override fun dispatchGenericMotionEvent` matches exactly once in `BaseActivity.kt`.
- `Grep` - `activityMouseDispatchHelper` matches at least 2 times in `BaseActivity.kt`.
- `Grep` - `override fun dispatchTouchEvent` still matches exactly once in `BaseActivity.kt`.

**Status:** `[x]` done

**Step Log:** 2026-05-22 - Wired `BaseActivity` touch and generic-motion dispatch through the shared helper and added narrow open hooks for later phases.

---

### Step 07.4 - Validate foundation build and catalog sync

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (regenerated)
**Depends on:** Step 07.3

**Prompt for developer:**

> Run the target build for the touched Kotlin foundation files and then regenerate the app catalog:
> ```powershell
> .\a.ps1 bd
> pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
> ```
>
> Confirm the new helper / BaseActivity surface is visible in the catalog.

**Verification:**

- Build command exits `0`.
- Catalog sync exits `0`.
- `Grep` - `ActivityMouseDispatchHelper` matches in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:** 2026-05-22 - `./a.ps1 bd` passed after the local `dispatchGenericMotionEvent` signature repair in player activities; `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` passed and rendered 1422 records.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] Dev log entries added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 07 establishes the shared mouse/default-dispatch base only. Main / Browse / Player still own their complex screen semantics and are handled in Phase 08. Forms and list-heavy screens adopt the new default path in Phase 09.

---

## Rollback Plan

Revert the phase commit(s). The phase only touches shared dispatch helpers and does not alter persistence.
