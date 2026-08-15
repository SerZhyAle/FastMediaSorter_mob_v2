# Phase 06 - Gesture Action Wiring

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Add the `OPEN_PANEL` gesture action: a new enum value, its picker label, and a pre-capture dispatch branch that opens `AppLaunchPanelActivity`. This makes the panel reachable from a left-edge gesture.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done (`AppLaunchPanelActivity` exists and is registered).
- [ ] Reference read: `domain/model/ScreenshotGestureAction.kt`, `core/screencapture/ScreenshotGestureActionDispatcher.kt`, `ui/settings/helpers/ScreenshotGestureActionPickerManager.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureAction.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | Modified | ≤ 150 |
| `app_v2/src/main/res/values/strings.xml` (+ ru/uk) | Modified | - |

> All four files are in `src/main` - always compiled into every flavor. The dispatcher's `OPEN_PANEL` branch only runs at runtime where the overlay is mounted (standard with `fms.screenCapture` on, noLegal); no flavor source-set split is needed (the panel UI is flavor-agnostic).

---

## Steps

### Step 06.1 - Add the OPEN_PANEL enum value

**Files:** `domain/model/ScreenshotGestureAction.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `OPEN_PANEL` to `enum class ScreenshotGestureAction`. Place it next to `OPEN_APP` (both pre-capture actions). The tolerant `fromName` companion needs no change. Update the KDoc line that lists pre-capture actions to mention `OPEN_PANEL`.

**Verification:**

- `Grep` - `OPEN_PANEL` present in `ScreenshotGestureAction.kt`.

**Status:** `[x]` done

---

### Step 06.2 - Add the action label string (trilingual)

**Files:** `res/values/strings.xml` (+ ru/uk)
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `set-android-string.ps1 -Action add` the key `screenshot_gesture_action_open_panel` (EN "Open app panel", RU "Открыть панель программ", UK "Відкрити панель програм"). Match the wording style of the existing `screenshot_gesture_action_*` keys. Check against `docs/COMMUNICATION_POLICY.md` §2/§6.

**Verification:**

- `Grep` - `screenshot_gesture_action_open_panel` present in `values/strings.xml`, `values-ru`, `values-uk`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_gesture_action_open_panel"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 06.3 - Map the action to its label

**Files:** `ui/settings/helpers/ScreenshotGestureActionPickerManager.kt`
**Depends on:** Steps 06.1, 06.2

**Prompt for developer:**

> Add the `ScreenshotGestureAction.OPEN_PANEL -> R.string.screenshot_gesture_action_open_panel` branch to `labelResFor`. `availableActions()` already returns all entries except the translation-gated `OCR_TRANSLATE`, so `OPEN_PANEL` shows unconditionally (the design decision) - no filter change needed.

**Verification:**

- `Grep` - `ScreenshotGestureAction.OPEN_PANEL -> R.string.screenshot_gesture_action_open_panel` present.
- Build: `.\a.ps1 fk` exits 0 (`when` over the enum is exhaustive again).

**Status:** `[x]` done

---

### Step 06.4 - Dispatch OPEN_PANEL pre-capture

**Files:** `core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** Steps 06.1, Phase 05 (`AppLaunchPanelActivity`)

**Prompt for developer:**

> In `handlePreCaptureAction`, add a branch `ScreenshotGestureAction.OPEN_PANEL ->` that starts `AppLaunchPanelActivity` with `FLAG_ACTIVITY_NEW_TASK` (mirror the existing `launchApp` private method - add a sibling `private fun launchPanel(context)` that builds `Intent(context, AppLaunchPanelActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK)` inside `runCatching`), and returns `true`. In `runPostSave`, add `ScreenshotGestureAction.OPEN_PANEL` to the early-`return` group alongside `OPEN_APP`/`DO_NOT_USE` (kept for `when`-exhaustiveness; it never reaches post-save). Use a plain-English `Timber.w` on launch failure - no ticket id in the message.

**Verification:**

- `Grep` - `ScreenshotGestureAction.OPEN_PANEL ->` present in `handlePreCaptureAction`.
- `Grep` - `AppLaunchPanelActivity` referenced in the dispatcher.
- `Grep` - `OPEN_PANEL` present in the `runPostSave` `when` (exhaustiveness).
- `Grep -n "Timber\.(i|w|e)\(\"S0623"` - zero hits.

**Status:** `[x]` done

---

### Step 06.5 - Insert the device-test debug tag

**Files:** `core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** Step 06.4

**Prompt for developer:**

> This ticket enters `BlockNeedUserTest` after the build. Insert exactly one `Timber.d("S0623: left-edge gesture OPEN_PANEL -> show app launch panel")` at the entry of the new `OPEN_PANEL` dispatch branch (the changed-flow entry point). One tag only - do not scatter probes across the UseCases or UI. The `S0623:` prefix is reserved for this temporary probe and is removed when the ticket leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` - exactly one `Timber.d("S0623:` line in the repository.
- That line sits inside the `OPEN_PANEL` dispatch path.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` (this build validates code + the inserted tag in one pass).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Exactly one `Timber.d("S0623:` tag exists (device-test probe).
- [ ] Dev log entry added; `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

The feature is end-to-end reachable: assign "Open app panel" to a gesture direction -> swipe -> panel opens over the foreground app. Phase 07 finishes docs/catalog/settings-manifest sync.

---

## Rollback Plan

Revert phase commit(s). Removing the enum value, label branch, and dispatch branch fully detaches the panel from the gesture system; the panel classes remain but become unreachable (matching the post-Phase-05 state).
