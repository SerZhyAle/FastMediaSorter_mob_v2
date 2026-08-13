# Phase 02 - Photo surface

**Strategic spec:** [`../S0352_widget-random-photo-frame.md`](../S0352_widget-random-photo-frame.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log:**
> - 2026-06-04 - 02.1 PASS. Added `RandomPhotoFrameWidgetRefresher.kt` and extended `RandomPhotoFrameSnapshotStore.kt` with silent writes; grep verified cache/thumbnail anchors and both Kotlin files are diagnostic-free.
> - 2026-06-04 - 02.2 PASS. Updated `RandomPhotoFrameWidgetProvider.kt` to refresh from cache-backed snapshot, render via `setImageViewUri`, and bind dynamic image content descriptions with no Kotlin diagnostics.
> - 2026-06-04 - 02.3 PASS. Updated `RandomPhotoFrameWidgetProvider.kt` tap routing to use `PlayerActivity.createPanelIntent`, `BrowseActivity.createIntent`, and config fallback, with no Kotlin diagnostics.
> - 2026-06-04 - Phase closure PASS. `assembleStandardDebug` succeeded; `TODO(phase-02)` grep returned zero hits; scanner/network pattern grep returned zero hits in `RandomPhotoFrameWidget*.kt`.

---

## Objective

Resolve one random image from cache, bind it to the widget surface, and make taps open the selected file fullscreen or fall back to browse/config safely.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [ ] Strategic cache-first rule remains unchanged: no live network scan in the provider or refresh path.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetRefresher.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt` | Modified | ≤ 340 |

---

## Steps

### Step 02.1 - Implement the cache-backed random-photo refresher

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetRefresher.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Add a helper object that resolves the current widget snapshot from cache only. Use `MediaFilesCacheManager.getCachedList(resourceId)` first, then `CachedFileListRepository.getCachedFiles(resourceId)` as the persistent fallback. Filter to image files only, avoid repeating the previous `selectedFilePath` when multiple candidates exist, resolve a render URI from `ThumbnailCacheRepository` first and from an already-local file URI only when it is safely device-local, then write the result back through `RandomPhotoFrameSnapshotStore`. If no renderable image exists, write the explicit empty/error state instead of leaving stale data.

**Verification:**

- `Glob` - `RandomPhotoFrameWidgetRefresher.kt` exists.
- `Grep` - `MediaFilesCacheManager.getCachedList` present.
- `Grep` - `CachedFileListRepository` present.
- `Grep` - `ThumbnailCacheRepository` present.
- `Grep` - `selectedFilePath` present.

**Status:** `[x]` done

---

### Step 02.2 - Render the photo or fallback state in the provider

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Upgrade the provider skeleton so `updateAppWidget(...)` renders the hero image via `RemoteViews.setImageViewUri(...)` when `selectedThumbnailUri` is present, otherwise shows the localized fallback copy from the snapshot. Keep unconfigured widgets routed to `RandomPhotoFrameConfigActivity`; configured-but-empty widgets stay tappable and readable. Add a clear `contentDescription` to the root image surface.

**Verification:**

- `Grep` - `setImageViewUri` present in `RandomPhotoFrameWidgetProvider.kt`.
- `Grep` - `widget_random_photo_frame_image` referenced.
- `Grep` - `contentDescription` binding or `setContentDescription` equivalent present in the provider/layout.

**Status:** `[x]` done

---

### Step 02.3 - Bind direct-open and fallback tap intents

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Wire the root widget tap without adding a new `MainActivity` action. If the snapshot has a valid `selectedFilePath`, launch `PlayerActivity.createPanelIntent(...)` with `resourceId`, `skipAvailabilityCheck=true`, and `initialFilePath=selectedFilePath` so the existing player loader resolves the file by path. If the widget is configured but has no renderable photo, launch `BrowseActivity.createIntent(context, resourceId, skipAvailabilityCheck = true)`; if it is unconfigured, launch `RandomPhotoFrameConfigActivity`.

**Verification:**

- `Grep` - `PlayerActivity.createPanelIntent` present.
- `Grep` - `BrowseActivity.createIntent` present.
- `Grep` - `initialFilePath` present.
- `Grep` - `RandomPhotoFrameConfigActivity` still referenced for the unconfigured branch.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (standard debug).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] No provider code performs a live scan or network fetch (`Grep` for scanner/network loading patterns returns zero hits in the new widget files).

---

## Handoff Notes to Next Phase

The widget now knows how to render a cached photo and how to open it. Phase 03 only adds periodic refresh and lifecycle cleanup.

---

## Rollback Plan

Revert phase commit(s). Snapshot data lives in SharedPreferences only and can be cleared safely.