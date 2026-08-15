# Phase 01 - Crop-then-share orchestration in the standalone image host

**Strategic spec:** [`../S0680_gesture-crop-screenshot-share.md`](../S0680_gesture-crop-screenshot-share.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

**Step Log:**

- 2026-06-25 - 01.1 Verification 2/2 PASS (`onCropFlowCancelled()` default no-op added; `fk` compiles). PlayerActionHost.kt.
- 2026-06-25 - 01.2 Verification 2/2 PASS (`host.onCropFlowCancelled()` in onCropModeExited only). PlayerCropDelegate.kt.
- 2026-06-25 - 01.3 Verification 3/3 PASS (`shareLocalCopy` shares `file.path` via FileProvider through sendToMenuManager). StandaloneFileOperationsHandler.kt.
- 2026-06-25 - 01.4 Verification 5/5 PASS (`AUTO_ACTION_CROP_AND_SHARE`, pending flag x3 sites, `shareLocalCopy` in reload, `CropMode.CROP`). PhotoVideoStandaloneActivity.kt (backup in temp/). Phase build: `fk` BUILD SUCCESSFUL.

---

## Objective

Add a one-shot "crop then share" flow to the standalone image host: a new auto-action that enters the existing in-place crop overlay, and on crop success shares the cropped local copy through the app's existing "Отправить в.." menu; on crop cancel it shares nothing. No enum/dispatcher/picker wiring yet (Phase 02).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 items 1/2/3 are Resolved (they are - see research/01).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerActionHost.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1100 |

> `PhotoVideoStandaloneActivity.kt` is ~1060 LOC (> 500) - create a timestamped backup in `temp/` before editing. Keep the activity delta minimal (one constant, one `when` branch, one flag, one consume site); share I/O lives in `StandaloneFileOperationsHandler`.
> No layout XML touched - the crop overlay (`player_crop_overlay_content.xml`) and send-to menu are reused unchanged; no `layout-land` parity needed.
> All files are shared `src/main` - the action piggybacks on the existing gesture subsystem's flavor gating; no flavor source set introduced.

---

## Steps

### Step 01.1 - Add a cancel hook to the player action host contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerActionHost.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun onCropFlowCancelled() {}` (default no-op body) to the `PlayerActionHost` interface, documented as "fired when the crop overlay is dismissed without applying a crop". The default body means the in-app player host needs no change. This gives the standalone host a signal to drop a pending post-crop action when the user cancels.

**Verification:**

- `Grep` - `fun onCropFlowCancelled\(\)` matches once in `PlayerActionHost.kt`.
- `.\a.ps1 fk` compiles (no other host implementor forced to change).

**Status:** `[x]` done

---

### Step 01.2 - Fire the cancel hook from the crop delegate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `PlayerCropDelegate`'s `imageCropCallback.onCropModeExited()`, after `hideCropOverlay()`, call `host.onCropFlowCancelled()`. The crop engine fires `onCropModeExited` only on cancel/exit (the success path fires `onSuccess` instead), so this signals cancellation without false positives on a successful crop.

**Verification:**

- `Grep` - `host.onCropFlowCancelled()` matches once in `PlayerCropDelegate.kt`, inside `onCropModeExited`.
- `Grep` - `onCropFlowCancelled` does NOT appear inside the `onSuccess` body of `PlayerCropDelegate.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Add a "share an explicit cropped copy" entry to the standalone file-operations handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt`
**Depends on:** - independent of 01.1/01.2

**Prompt for developer:**

> Add `fun shareLocalCopy(file: MediaFile)` mirroring `shareCurrentFile()` but sharing the passed `file` by its LOCAL `file.path` (ignore `file.contentUri`), routed through the same `sendToMenuManager.show(..)` surface. Reason: after an in-place crop of a MediaStore-backed screenshot, the cropped bytes live only in the writable local copy (`editableImageFile`), while the original `contentUri` still points at the un-cropped image; sharing must target the local cropped path. Build the share content from `file.path` (the send-to menu materializes it into a shareable URI). Resolve mime via the content resolver on the local path, falling back to `image/*`. Keep the existing `getCurrentSettings()` gating.

**Verification:**

- `Grep` - `fun shareLocalCopy\(` matches once in `StandaloneFileOperationsHandler.kt`.
- `Grep` - the new method body references `file.path` and does NOT use `file.contentUri` to build its share uri.
- `Grep` - `sendToMenuManager.show` appears inside the new method.

**Status:** `[x]` done

---

### Step 01.4 - Wire the crop-and-share auto-action in the standalone activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 01.1, Step 01.2, Step 01.3

**Prompt for developer:**

> Backup the file to `temp/` first (it is > 500 LOC). Then:
> 1. Add companion constant `const val AUTO_ACTION_CROP_AND_SHARE = "crop_and_share"`.
> 2. Add a private `var pendingShareAfterCrop = false`.
> 3. In `maybeRunAutoAction`, add a branch for `AUTO_ACTION_CROP_AND_SHARE`: set `autoActionConsumed = true`, set `pendingShareAfterCrop = true`, then enter crop mode via the existing crop delegate using `ImageCropManager.CropMode.CROP` (the same call the manual in-place crop uses).
> 4. In the existing `reloadCurrentImageInPlace()` override, after the reload call, if `pendingShareAfterCrop` is true, set it false and call `fileOperations.shareLocalCopy(viewModel.editableImageFile.value ?: return)`. This runs only on crop success (the host reload hook is the crop-success path).
> 5. Override `onCropFlowCancelled()` to set `pendingShareAfterCrop = false`.
>
> Do not add a trivial restating comment; a one-line WHY on the pending flag (cancel-vs-success gating) is enough.

**Verification:**

- `Grep` - `AUTO_ACTION_CROP_AND_SHARE = "crop_and_share"` matches once.
- `Grep` - `pendingShareAfterCrop` matches in: the `maybeRunAutoAction` branch (set true), the `reloadCurrentImageInPlace` consume site (set false + share), and the `onCropFlowCancelled` override (set false).
- `Grep` - `shareLocalCopy(` appears once in the activity, inside `reloadCurrentImageInPlace`.
- `Grep` - `CropMode.CROP` is the mode passed by the new branch (not `CROP_TO_FILE`).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `AUTO_ACTION_CROP_AND_SHARE` constant now exists on `PhotoVideoStandaloneActivity` and the whole crop-then-share behavior fires when an `EXTRA_AUTO_ACTION` launch carries it. Phase 02 wires the gesture enum + dispatcher to actually request this auto-action, and adds the picker label.

---

## Rollback Plan

- Revert phase commit(s). No data migration, no schema, no user-facing surface persisted - the new auto-action is unreachable until Phase 02 wires the dispatcher.
