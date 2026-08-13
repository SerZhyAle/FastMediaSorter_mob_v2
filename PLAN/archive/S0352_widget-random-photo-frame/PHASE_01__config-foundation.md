# Phase 01 - Config foundation

**Strategic spec:** [`../S0352_widget-random-photo-frame.md`](../S0352_widget-random-photo-frame.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log:**
> - 2026-06-04 - 01.1 PASS. Added 6 `widget_random_photo_frame_*` keys in `values/strings_widget.xml`, `values-ru/strings_widget.xml`, and `values-uk/strings_widget.xml`; `check_strings_localized.ps1 -Module app_v2 -KeyPrefix widget_random_photo_frame` returned OK for EN/RU/UK parity.
> - 2026-06-04 - 01.2 PASS. Added `widget_random_photo_frame.xml` and `widget_random_photo_frame_info.xml`; grep verified the required ids and `2x2` metadata attributes with no XML diagnostics.
> - 2026-06-04 - 01.3 PASS. Added `RandomPhotoFrameSnapshotStore.kt`; grep confirmed `Snapshot/read/write/clear/updateWidgets`, and the Kotlin file has no diagnostics.
> - 2026-06-04 - 01.4 PASS. Added `RandomPhotoFrameConfigActivity.kt`; grep verified `EXTRA_APPWIDGET_ID`, local `resourceDao().getAllResources()`, snapshot write, and `RESULT_OK`, with no Kotlin diagnostics.
> - 2026-06-04 - 01.5 PASS. Added `RandomPhotoFrameWidgetProvider.kt`; grep verified the provider class, `updateAppWidget`, `RandomPhotoFrameConfigActivity` reference, and zero `Log.d` hits.
> - 2026-06-04 - 01.6 PASS. Registered `RandomPhotoFrameConfigActivity` and `RandomPhotoFrameWidgetProvider` in `AndroidManifest.xml`, plus `RandomPhotoFrameWidgetProvider::class.java` in `HomeWidgetCatalog.kt`, with no manifest/Kotlin diagnostics.
> - 2026-06-04 - Phase closure PASS. `assembleStandardDebug` succeeded; `check_strings_localized.ps1 -Module app_v2 -KeyPrefix widget_random_photo_frame` returned OK; `TODO(phase-01)` grep returned zero hits.

---

## Objective

Create the resource-bound widget contract: strings, layout, appwidget metadata, snapshot store, configuration activity, provider skeleton, manifest declarations, and in-app picker registration.

---

## Prerequisites

- [ ] Strategic research remains Resolved: cache-first only, fixed cadence, explicit fallback.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_widget.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_widget.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_widget.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/widget_random_photo_frame.xml` | New | ≤ 160 |
| `app_v2/src/main/res/xml/widget_random_photo_frame_info.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameSnapshotStore.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameConfigActivity.kt` | New | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` | Modified | ≤ 220 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |

> App widgets render through `RemoteViews`; there is no `res/layout-land/` variant for widget layouts and none is needed. Landscape parity rule satisfied by launcher-resized reuse of the same layout.

---

## Steps

### Step 01.1 - Add trilingual widget strings

**Files:** `app_v2/src/main/res/values/strings_widget.xml`, `app_v2/src/main/res/values-ru/strings_widget.xml`, `app_v2/src/main/res/values-uk/strings_widget.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add EN/RU/UK keys in `strings_widget.xml`: `widget_random_photo_frame_label`, `widget_random_photo_frame_description`, `widget_random_photo_frame_empty_title`, `widget_random_photo_frame_empty_subtitle`, `widget_random_photo_frame_resource_missing`, `widget_random_photo_frame_cache_empty`, and any content-description strings needed by the widget surface. Use the widget string files, not `strings.xml`. Keep copy concise and consistent with `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - each `widget_random_photo_frame_*` key added in all three `strings_widget.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "widget_random_photo_frame"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.2 - Create the widget layout and metadata xml

**Files:** `app_v2/src/main/res/layout/widget_random_photo_frame.xml`, `app_v2/src/main/res/xml/widget_random_photo_frame_info.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a `RemoteViews` layout with one hero `ImageView`, a small overlay title/subtitle area for empty/error states, and a single root container id for click handling. Create `widget_random_photo_frame_info.xml` with `targetCellWidth="2"`, `targetCellHeight="2"`, resizable `2x2 -> 3x3`, `updatePeriodMillis="0"`, `initialLayout=@layout/widget_random_photo_frame`, `configure="com.sza.fastmediasorter.widget.RandomPhotoFrameConfigActivity"`, `widgetCategory="home_screen"`, and `theme="@style/Widget.FastMediaSorter"`.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `@+id/widget_random_photo_frame_container` and `@+id/widget_random_photo_frame_image` present in `widget_random_photo_frame.xml`.
- `Grep` - `targetCellWidth="2"`, `targetCellHeight="2"`, and `RandomPhotoFrameConfigActivity` present in `widget_random_photo_frame_info.xml`.

**Status:** `[x]` done

---

### Step 01.3 - Create the snapshot store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameSnapshotStore.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a lightweight SharedPreferences-backed snapshot store, modeled on `AudioNowPlayingSnapshotStore`, for one widget instance per `appWidgetId`. Store at least: `resourceId`, `resourceName`, `selectedFilePath`, `selectedThumbnailUri`, `hasRenderablePhoto`, and the current fallback message. Expose `read(appWidgetId)`, `write(appWidgetId, snapshot)`, `clear(appWidgetId)`, and `updateWidgets(context)` helpers. The store owns no database or cache lookups.

**Verification:**

- `Glob` - `RandomPhotoFrameSnapshotStore.kt` exists.
- `Grep` - `data class Snapshot` matches exactly once.
- `Grep` - `fun read`, `fun write`, `fun clear`, and `fun updateWidgets` present.

**Status:** `[x]` done

---

### Step 01.4 - Add the configuration activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameConfigActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create a Compose-based configuration activity by copying the structure of `ResourceLaunchWidgetConfigActivity`. Read resources from `AppDatabase.resourceDao().getAllResources().first()`, let the user pick one resource, write the initial snapshot for the `appWidgetId`, call `RandomPhotoFrameWidgetProvider.updateAppWidget(...)`, and return `RESULT_OK` with `AppWidgetManager.EXTRA_APPWIDGET_ID`.

**Verification:**

- `Glob` - `RandomPhotoFrameConfigActivity.kt` exists.
- `Grep` - `AppWidgetManager.EXTRA_APPWIDGET_ID` present.
- `Grep` - `getAllResources().first()` or `resourceDao().getAllResources()` present.
- `Grep` - `RESULT_OK` present.

**Status:** `[x]` done

---

### Step 01.5 - Create the provider skeleton

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt`
**Depends on:** Step 01.2, Step 01.3, Step 01.4

**Prompt for developer:**

> Create an `AppWidgetProvider` skeleton that reads the per-widget snapshot, renders the placeholder/empty state from `widget_random_photo_frame.xml`, opens `RandomPhotoFrameConfigActivity` when the widget is unconfigured, and exposes `updateAppWidget(context, appWidgetManager, appWidgetId)` as the single render entry point. Keep the class free of cache scans and heavy bitmap work; Phase 02/03 add that.

**Verification:**

- `Glob` - `RandomPhotoFrameWidgetProvider.kt` exists.
- `Grep` - `class RandomPhotoFrameWidgetProvider` matches exactly once.
- `Grep` - `updateAppWidget` present.
- `Grep` - `RandomPhotoFrameConfigActivity` referenced.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 01.6 - Register the widget in the manifest and picker catalog

**Files:** `app_v2/src/main/AndroidManifest.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt`
**Depends on:** Step 01.4, Step 01.5

**Prompt for developer:**

> Declare the config activity (`APPWIDGET_CONFIGURE`) and the widget receiver in `AndroidManifest.xml`, with `<meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_random_photo_frame_info" />`. Add a `HomeWidgetEntry` for `RandomPhotoFrameWidgetProvider` to `HomeWidgetCatalog`, using manifest presence as the flavor gate and no new `BuildConfig` checks in `src/main`.

**Verification:**

- `Grep` - `RandomPhotoFrameConfigActivity` present in `AndroidManifest.xml` with `android.appwidget.action.APPWIDGET_CONFIGURE`.
- `Grep` - `RandomPhotoFrameWidgetProvider` and `@xml/widget_random_photo_frame_info` present in `AndroidManifest.xml`.
- `Grep` - `RandomPhotoFrameWidgetProvider::class.java` present in `HomeWidgetCatalog.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (standard debug).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "widget_random_photo_frame"` exits 0.

---

## Handoff Notes to Next Phase

The widget can now be placed and configured; Phase 02 teaches it to resolve a cached random photo and wire the correct fullscreen/browse tap behavior.

---

## Rollback Plan

Revert phase commit(s) and remove the new manifest declarations. No schema changes or data migration.