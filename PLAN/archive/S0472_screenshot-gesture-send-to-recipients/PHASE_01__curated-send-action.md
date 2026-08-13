# Phase 01 - Curated send-to gesture action

**Strategic spec:** [`../S0472_screenshot-gesture-send-to-recipients.md`](../S0472_screenshot-gesture-send-to-recipients.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add a new `SEND_TO_RECIPIENTS` screenshot-gesture action that, after capture, opens the curated «Send to..» dialog from the configured «Send file to» recipients - reusing the existing standalone-viewer `EXTRA_AUTO_ACTION` host path. Existing `SHARE` (system chooser) is left untouched.

---

## Prerequisites

- [ ] Strategic §6 items resolved (see INDEX - both resolved during planning).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 string |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 string |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 string |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 970 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureAction.kt` | Modified | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | Modified | ≤ 100 |

> `PhotoVideoStandaloneActivity.kt` is 955 LOC (>500) - take a timestamped backup into `temp/` before editing.
>
> **Flavor placement.** All files are `src/main` (shared). The action is reachable only in noLegal because the gesture settings group renders only there (inherited gate). Do NOT add `src/<flavor>` files or any `BuildConfig.IS_*` / `SUPPORT_*` guard.
> **Landscape parity.** No `res/layout*` files are edited (the picker is a `MaterialAlertDialog`, not a layout) - landscape rule N/A.

---

## Steps

### Step 01.1 - Add the trilingual action label string

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one new string key `screenshot_gesture_action_send_to` across EN/RU/UK in lockstep with `scripts/utils/set-android-string.ps1 -Action add -Key screenshot_gesture_action_send_to -En "<EN>" -Ru "<RU>" -Uk "<UK>"`. The label names the curated-recipients send (distinct from the existing `screenshot_gesture_action_share` = system chooser). Suggested EN "Send to my recipients"; pick wording that makes the «own configured list» meaning clear versus plain Share. Wording must pass `docs/COMMUNICATION_POLICY.md` §2 (label/CTA formula) and §6 (tone checklist): no platform brand literals, sentence case, concise.

**Verification:**

- `Grep` - `screenshot_gesture_action_send_to` matches once in `values/strings.xml`.
- `Grep` - `screenshot_gesture_action_send_to` matches once in `values-ru/strings.xml`.
- `Grep` - `screenshot_gesture_action_send_to` matches once in `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_gesture_action_send_to"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 5/5 PASS. Added `screenshot_gesture_action_send_to` EN="Send to recipients" / RU="Отправить получателям" / UK="Надіслати отримувачам" via set-android-string.ps1 -Action add. Parity audit exit 0. Dev log recorded.

---

### Step 01.2 - Add the standalone-viewer auto-action host branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** - independent (build-green on its own)

**Prompt for developer:**

> Back up the file into `temp/` first (>500 LOC). Add a companion constant `const val AUTO_ACTION_SEND_TO = "send_to"` next to the existing `AUTO_ACTION_DRAW` / `AUTO_ACTION_TRANSLATE`. In `maybeRunAutoAction`, add a branch `AUTO_ACTION_SEND_TO -> { autoActionConsumed = true; fileOperations.shareCurrentFile() }`. Reuse the existing `fileOperations.shareCurrentFile()` - it already builds `ShareableContent` from the current image and calls `sendToMenuManager.show(activity, content, settings)` (single receiver → direct send; multiple → bottom sheet; empty → no-op). Do not duplicate that logic and do not change `shareCurrentFile()`.

**Verification:**

- `Grep` - `AUTO_ACTION_SEND_TO = "send_to"` matches once in the file.
- `Grep` - `AUTO_ACTION_SEND_TO ->` matches once (the `maybeRunAutoAction` branch).
- `Grep` - `shareCurrentFile()` present inside the new branch line.
- `Glob` - a timestamped backup of the file exists under `temp/`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS. Added `AUTO_ACTION_SEND_TO` const (line 958) + `maybeRunAutoAction` branch (line 621) reusing `fileOperations.shareCurrentFile()`. Backup in temp/. Dev log recorded.

---

### Step 01.3 - Introduce SEND_TO_RECIPIENTS across enum, picker, dispatcher

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureAction.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** Step 01.1 (string), Step 01.2 (host constant)

**Prompt for developer:**

> This is one atomic compile unit - the new enum constant breaks the two exhaustive `when` sites, which are fixed in the same step. (1) In `ScreenshotGestureAction`, add `SEND_TO_RECIPIENTS` immediately before `SHARE` so it sits next to it in the picker order. (2) In `ScreenshotGestureActionPickerManager.labelResFor`, add `ScreenshotGestureAction.SEND_TO_RECIPIENTS -> R.string.screenshot_gesture_action_send_to`; leave `availableActions()` as-is so the value is always offered (no capability gate, like `SHARE`). (3) In `ScreenshotGestureActionDispatcher.runPostSave`, add `ScreenshotGestureAction.SEND_TO_RECIPIENTS -> openInViewer(context, savedUri, autoAction = PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO)`. Reuse `openInViewer` - it already null-guards `savedUri` and adds `FLAG_ACTIVITY_NEW_TASK` + read permission. Do not touch the `SHARE` branch. No new `Timber.d("S0472:` probe in this step - debug tags are added only at the BlockNeedUserTest transition (cleanup phase / `/spec-dev`).

**Verification:**

- `Grep` - `SEND_TO_RECIPIENTS` matches once in `ScreenshotGestureAction.kt` (the declaration).
- `Grep` - `SEND_TO_RECIPIENTS -> R.string.screenshot_gesture_action_send_to` matches once in `ScreenshotGestureActionPickerManager.kt`.
- `Grep` - `SEND_TO_RECIPIENTS -> openInViewer` matches once in `ScreenshotGestureActionDispatcher.kt`.
- `Grep` - `AUTO_ACTION_SEND_TO` present in `ScreenshotGestureActionDispatcher.kt`.
- `Grep` - the `ScreenshotGestureAction.SHARE -> ` branch still present in `ScreenshotGestureActionDispatcher.kt` (unchanged, additive).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 5/5 PASS. Atomic compile unit: enum `SEND_TO_RECIPIENTS` (ScreenshotGestureAction.kt:9), picker label case (ScreenshotGestureActionPickerManager.kt:49), dispatcher route via `openInViewer(.. AUTO_ACTION_SEND_TO)` (ScreenshotGestureActionDispatcher.kt:58-59); SHARE branch untouched (line 61). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (fast compile `.\a.ps1 fk` proves the exhaustive-`when` sites; noLegal build is the Completion-Gate check).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new enum constant).

---

## Handoff Notes to Next Phase

The behavior is complete and compiles: a new gesture action routes capture → standalone viewer → curated «Send to..». Phase 02 records docs (noLegal FEATURES trilingual), dev log, catalog sync, and the BlockNeedUserTest debug probe.

---

## Rollback Plan

Revert the phase commit(s) - additive only, no data migration or schema change. The new enum constant parses back to its default via `fromName`, so no persisted setting is corrupted on rollback.
