# Phase 05 - Stateful gadgets

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress - 05.1 store half done, 05.3 done, 05.2 and the photo-frame gadget blocked on the same missing piece
**Depends on:** Phase 03
**Blocks:** Phase 07
**Steps done:** 1 / 3
**Started:** 2026-07-30
**Completed:** -

---

## What this phase found (2026-07-30)

The plan treats "re-key the store" as the whole cost of a stateful gadget. It is not. **The store was the easy half; the configuration activity is the hard half, and the plan does not mention it.**

`RandomPhotoFrameConfigActivity` reads `AppWidgetManager.EXTRA_APPWIDGET_ID` in `onCreate` and finishes immediately when it is `INVALID_APPWIDGET_ID`; it then writes through `RandomPhotoFrameWidgetRefresher.refresh(context, appWidgetId)` and `RandomPhotoFrameWidgetProvider.updateAppWidget(...)`, and returns the widget id in its result. `CameraQuickCaptureConfigActivity` has the same shape over `cam_capture_target_*`. So the owner token has to be threaded through the config screen, the refresher and the result contract - not just the store - before either gadget can exist. Registering one sooner would put a cell on the desktop that opens a config screen which immediately closes.

**Done and verified**

- The store re-key itself (Step 05.1's first half). `RandomPhotoFrameSnapshotStore` now namespaces by a `SnapshotOwner`: `Widget(id)` renders the bare number the prefs already hold, `LauncherCell(id)` renders `cell<id>`. The `Int` overloads are kept, so all three existing call sites are untouched. `updateWidgets` fires only for a widget owner - asking `AppWidgetManager` to refresh a cell id would poke whatever widget happens to hold that number.
- `RandomPhotoFrameSnapshotStoreTest` pins the widget key format with **literal** strings (`resource_id_7`), not the constants. Renaming a constant and its test together would otherwise pass while orphaning every configured frame on every device - a failure no static gate can see and the user would read as "my photo frame reset itself".
- Step 05.3, the now-playing gadget, in full - it has no configuration screen, which is exactly why it was not blocked.

**Left for a follow-up**

- Thread `SnapshotOwner` through `RandomPhotoFrameConfigActivity` + `RandomPhotoFrameWidgetRefresher`, then add `RandomPhotoFrameGadget`.
- Same for `CameraQuickCaptureConfigActivity`, then add `QuickCaptureGadget`.

Twelve of the fourteen keys resolve today.

---

## Objective

Ship the three cells whose behaviour is not a single launch: the photo frame and quick capture, which key their settings by launcher cell id instead of widget id, and now-playing, whose transport controls are service commands.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `research/01` §3 read - it states why none of these three is command-shaped.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameSnapshotStore.kt` | Modified | ≤ 160 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/RandomPhotoFrameGadget.kt` | New | ≤ 240 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/QuickCaptureGadget.kt` | New | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt` | New | ≤ 240 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/HomeWidgetGadgetModule.kt` | Modified | ≤ 200 |

---

## Steps

### Step 05.1 - Re-key the photo-frame store, then add its gadget

**Files:** `RandomPhotoFrameSnapshotStore.kt`, `RandomPhotoFrameGadget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The store namespaces every key as `"${key}_$appWidgetId"`. Generalise that suffix to an opaque owner token so the same store serves both an `appWidgetId` and a launcher cell id, keeping the existing widget call sites byte-identical in behaviour - an existing user's placed widget must keep reading its own snapshot, so the token for a widget stays exactly the number it is today. The launcher gadget passes a token derived from the cell id in a form that cannot collide with a bare widget id. Then write `RandomPhotoFrameGadget`: it takes the cell id through `LauncherGadget.createView`'s `param` (set `requiresResourceParam` accordingly), renders the current snapshot, and reproduces the widget's three tap outcomes - unconfigured opens the config screen, a present photo opens the player at that file, otherwise browse.

**Verification:**

- `Grep` - the store's namespacing function takes an owner token, not an `appWidgetId: Int`.
- `Grep` - every existing widget call site still compiles unchanged in meaning (no call site drops its id).
- `Grep` - `class RandomPhotoFrameGadget` present in `src/launcherEnabled/`.

**Status:** `[ ]` not done

---

### Step 05.2 - Quick capture gadget

**Files:** `QuickCaptureGadget.kt`, `HomeWidgetGadgetModule.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> `CameraQuickCaptureWidgetProvider` stores its target under `cam_capture_target_*` keyed by widget id and has its own config activity. Apply the same owner-token treatment Step 05.1 established so a launcher cell keeps its own target. An unconfigured cell opens the config screen; a configured one runs the capture. Do not register this one as a mechanical `HomeWidgetGadget` - Phase 03 deliberately left `camera_quick_capture` out of its ten because the config branch is per-instance state.

**Verification:**

- `Grep` - `class QuickCaptureGadget` present.
- `Grep` - `camera_quick_capture` appears exactly once in the module, as this gadget's registration.

**Status:** `[ ]` not done

---

### Step 05.3 - Now-playing gadget with transport controls

**Files:** `AudioNowPlayingGadget.kt`, `HomeWidgetGadgetModule.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> The widget's previous / play-pause / next buttons are `startService` calls on `AudioPlaybackService` and its favourite toggle is a broadcast; `ExecuteLauncherCommandUseCase` only ever calls `startActivity`, so none of this can go through `LauncherCellCommand`. The gadget invokes the same service commands directly, reusing the provider's existing action and extra constants rather than retyping their values. Its body tap opens the app. Reflect playback state live through `LauncherGadgetView.onActive()`, not a bare flow collection, and make sure nothing keeps a reference to the view after detach.

**Verification:**

- `Grep` - `class AudioNowPlayingGadget` present.
- `Grep` - the service action and extra constants are referenced by name, not retyped as string literals.
- `Grep` - `onActive()` present; `lifecycleScope.launch` returns zero hits in the file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` for `standard` and `noLegal`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] An existing Android-home photo-frame or quick-capture widget still reads its own stored target after the re-key - verify by grepping the produced key format for the widget path and confirming it is unchanged.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings. This phase touches a shared prefs store and a media service; check listener symmetry and that no gadget view outlives its cell.

---

## Handoff Notes to Next Phase

All fourteen keys now resolve through `LauncherGadgetRegistry.byKey`. Phase 06 may place any of them.

---

## Rollback Plan

Revert the phase commit. The store re-key is the only change touching persisted user data - confirm the widget-side key format is identical before and after, so a revert cannot orphan an existing widget's target.
