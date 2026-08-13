# Phase 08 - Main list per-resource "Open in new window"

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Status:** ✅ Done
**Depends on:** Phase 07 (observer OR-compose)

> Retroactive entry: code shipped between 2026-05-22 and 2026-05-24 without a tactical record. Scope extension beyond strategic §11.1..§11.5, but aligned with the spec's overall discoverability goal: capability-aware multi-window entry should also reach the main resource list (DeX / Quest 3 panel / ChromeOS users can open a chosen resource directly into a new window without first entering its file list).

## Goal

Add a per-resource "Open in new window" action to the main resource list's row overflow / context menu. Visibility uses the same OR-composition as the rest of S0293 (`persisted allowSeparateWindow || MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)`). On tap, launches `BrowseActivity` for the resource as a new task so the platform can place it in a separate window.

## Files Touched

- `app_v2/src/main/res/menu/resource_item_actions.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`

## Steps

### 08.1 Wire the per-resource entry end-to-end

- `resource_item_actions.xml`: add `@+id/action_open_in_separate_window` menu item with title and icon.
- `MainActivity.setupViews()`: capture `mainAllowSeparateWindow` once via OR-composition at adapter construction; pass it as `isOpenInNewWindowVisible` to `ResourceAdapter`.
- `MainActivity.openResourceInNewWindow(resourceId)`: launch `BrowseActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK` and a fresh `windowId`.
- `ResourceAdapter`: accept `isOpenInNewWindowVisible: Boolean` and `onOpenInNewWindowClick: (Resource) -> Unit`; render the menu entry conditionally; route taps to the callback.

**Verification:**
- `Grep` `resource_item_actions.xml` for `action_open_in_separate_window` - present.
- `Grep` `MainActivity.kt` for `openResourceInNewWindow` and `FLAG_ACTIVITY_NEW_TASK` - both present.
- `Grep` `ResourceAdapter.kt` for `isOpenInNewWindowVisible` and `onOpenInNewWindowClick` - both present.
- Build `standardDebug` PASS.

## Phase Done Criteria

- Resource overflow menu in main list shows "Open in new window" iff multi-window is effectively available.
- Tap launches `BrowseActivity` for that resource as a separate task.
- Build passes.

## Out of Strategic Scope

- The strategic spec's §11 criteria target per-file entries (inside a resource's file list) and the player. This phase adds the parallel per-resource entry on the main list, which is a natural extension but not explicitly listed in §11. The owner accepted the extension implicitly by the work landing in the working tree; if a future audit needs an explicit acceptance, raise it via `/spec-update` with a new §11.6 criterion.
