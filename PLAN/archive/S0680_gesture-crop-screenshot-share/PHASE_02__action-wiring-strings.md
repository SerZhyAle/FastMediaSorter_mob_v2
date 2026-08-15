# Phase 02 - Gesture action enum, dispatch, picker label, strings

**Strategic spec:** [`../S0680_gesture-crop-screenshot-share.md`](../S0680_gesture-crop-screenshot-share.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (consumes `AUTO_ACTION_CROP_AND_SHARE`)
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

**Step Log:**

- 2026-06-25 - 02.1 Verification 3/3 PASS (`screenshot_gesture_action_crop_and_share` present + parity OK in EN/RU/UK; Cyrillic intact). strings.xml x3.
- 2026-06-25 - 02.2 Verification 1/1 PASS (`CROP_AND_SHARE` enum constant). ScreenshotGestureAction.kt.
- 2026-06-25 - 02.3 Verification 2/2 PASS (`CROP_AND_SHARE` branch routes `AUTO_ACTION_CROP_AND_SHARE`). ScreenshotGestureActionDispatcher.kt.
- 2026-06-25 - 02.4 Verification 1/1 PASS (picker label mapping). ScreenshotGestureActionPickerManager.kt.
- 2026-06-25 - Final-phase debug tag `Timber.d("S0680: ..")` inserted at the crop-and-share auto-action entry. Phase build: `fk` BUILD SUCCESSFUL (validates Phase 02 + tag).

---

## Objective

Add the `CROP_AND_SHARE` gesture action end-to-end: enum constant, post-save dispatch routing to the Phase 01 auto-action, picker label, and the trilingual label string. After this phase the action is selectable and fully functional wherever the edge-gesture subsystem is mounted.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (the `AUTO_ACTION_CROP_AND_SHARE` constant exists).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureAction.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` | Modified | ≤ 80 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> The enum participates in two exhaustive `when` blocks (dispatcher + picker). Steps 02.1-02.3 must land together so the variant compiles; do not stop after the enum edit.
> All `src/main` - no flavor source set; the action is reachable only where the gesture subsystem is mounted (noLegal always; standard with `-Pfms.edgeGestureOverlay=on`), per existing gating.

---

## Steps

> Step order note: the label string (02.1) lands before the picker reference (02.4) so the `R.string` resolves. The enum + both exhaustive `when` edits (02.2 dispatcher, 02.4 picker) are inherently one atomic unit - adding an enum value forces both `when` blocks - so they merge together in this phase.

### Step 02.1 - Add the trilingual label string

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add string key `screenshot_gesture_action_crop_and_share` in lockstep across EN/RU/UK via a single call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key screenshot_gesture_action_crop_and_share -En "Crop screenshot and share" -Ru "Обрезать скриншот и отправить" -Uk "Обрізати знімок і надіслати"`.
> Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (action labels: imperative, concise, no trailing period). Use Ё where grammatical (none needed here).

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_gesture_action_crop_and_share"` exits 0.
- `Grep` - `screenshot_gesture_action_crop_and_share` matches once in each of the three `strings.xml` files.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.2 - Add the enum constant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureAction.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add `CROP_AND_SHARE` to the `ScreenshotGestureAction` enum. Place it adjacent to `SEND_TO_RECIPIENTS` / `SHARE` (post-capture share-family actions). The tolerant `fromName` parser needs no change. This breaks the two exhaustive `when` blocks until 02.3 and 02.4 add their branches - land all three together.

**Verification:**

- `Grep` - `CROP_AND_SHARE` matches once in `ScreenshotGestureAction.kt`.

**Status:** `[x]` done

---

### Step 02.3 - Route the action to the crop-and-share auto-action

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** Step 02.2, Phase 01 Step 01.4

**Prompt for developer:**

> In `runPostSave`'s `when`, add a `ScreenshotGestureAction.CROP_AND_SHARE` branch that calls `openInViewer(context, savedUri, autoAction = PhotoVideoStandaloneActivity.AUTO_ACTION_CROP_AND_SHARE)`, mirroring the existing `SEND_TO_RECIPIENTS` branch. The shared `openInViewer` already degrades to a silent save when `savedUri` is null (network destination), satisfying the §3.2 fallback.

**Verification:**

- `Grep` - `ScreenshotGestureAction.CROP_AND_SHARE` matches once in `ScreenshotGestureActionDispatcher.kt`.
- `Grep` - `AUTO_ACTION_CROP_AND_SHARE` appears in that branch.

**Status:** `[x]` done

---

### Step 02.4 - Map the action to a picker label

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt`
**Depends on:** Step 02.1 (string), Step 02.2 (enum)

**Prompt for developer:**

> In `labelResFor`'s `when`, map `ScreenshotGestureAction.CROP_AND_SHARE -> R.string.screenshot_gesture_action_crop_and_share`. Do NOT add a capability filter in `availableActions()` - crop and the send-to menu are always present in `src/main`, so the action is visible whenever the gesture picker itself is shown.

**Verification:**

- `Grep` - `CROP_AND_SHARE -> R.string.screenshot_gesture_action_crop_and_share` matches once in `ScreenshotGestureActionPickerManager.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- The action is now selectable in the gesture picker and runs the full capture -> crop -> send-to flow. Phase 03 regenerates the catalog, records the capability, and reconciles the settings docs for the new selectable value.

---

## Rollback Plan

- Revert phase commit(s). The Phase 01 auto-action becomes unreachable again; no data migration or schema change.
