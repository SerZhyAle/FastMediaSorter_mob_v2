# Phase 02 — Picker Metadata

**Strategic spec:** [`../S0134_widget-picker-and-home-polish.md`](../S0134_widget-picker-and-home-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Wire each of the five widget receivers in AndroidManifest to its own label and icon, and remove `previewLayout` from every widget metadata file so the system picker renders the prepared static `previewImage` PNGs.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — five `ic_widget_*` drawables and both label strings exist.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | +10 |
| `app_v2/src/main/res/xml/widget_favorites_info.xml` | Modified | -1 |
| `app_v2/src/main/res/xml/widget_resource_launch_info.xml` | Modified | -1 |
| `app_v2/src/main/res/xml/widget_camera_photos_info.xml` | Modified | -1 |
| `app_v2/src/main/res/xml/widget_random_music_info.xml` | Modified | -1 |
| `app_v2/src/main/res/xml/widget_continue_reading_info.xml` | Modified | -1 |

---

## Steps

### Step 02.1 — Register all five widget receivers and add `android:label`/`android:icon`

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> Two of the five widget providers (`CameraPhotosWidgetProvider`, `RandomMusicWidgetProvider`) exist as Kotlin classes but are not declared in the manifest — register them next to the existing widget receivers. Final state: five `<receiver>` blocks, one per provider class, each with `android:exported="true"`, an `intent-filter` for `android.appwidget.action.APPWIDGET_UPDATE`, a `meta-data` for `android.appwidget.provider` pointing to the matching `widget_*_info.xml`, plus `android:label="@string/widget_<name>_label"` and `android:icon="@drawable/ic_widget_<name>"`. The photos flavor overlay already removes `RandomMusicWidgetProvider` via `tools:node="remove"` — leave the overlay untouched. Do not modify any other receiver in the manifest.

**Verification:**

- `Grep` — `android:label="@string/widget_favorites_label"` matches exactly once in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `android:label="@string/widget_resource_launch_label"` matches exactly once.
- `Grep` — `android:label="@string/widget_camera_photos_label"` matches exactly once.
- `Grep` — `android:label="@string/widget_random_music_label"` matches exactly once.
- `Grep` — `android:label="@string/widget_continue_reading_label"` matches exactly once.
- `Grep` — `android:icon="@drawable/ic_widget_favorites"` matches exactly once.
- `Grep` — `android:icon="@drawable/ic_widget_resource_launch"` matches exactly once.
- `Grep` — `android:icon="@drawable/ic_widget_camera_photos"` matches exactly once.
- `Grep` — `android:icon="@drawable/ic_widget_random_music"` matches exactly once.
- `Grep` — `android:icon="@drawable/ic_widget_continue_reading"` matches exactly once.
- `Grep` — `.widget.CameraPhotosWidgetProvider` matches exactly once in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `.widget.RandomMusicWidgetProvider` matches exactly once in `app_v2/src/main/AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 12/12 PASS (5 labels + 5 icons + 2 new receivers registered). Files: app_v2/src/main/AndroidManifest.xml (+18 LOC for two new receiver blocks; 3 existing receivers gained label/icon attrs). Dev log recorded.

---

### Step 02.2 — Remove `android:previewLayout` from all widget metadata files

**Files:** `app_v2/src/main/res/xml/widget_favorites_info.xml`, `app_v2/src/main/res/xml/widget_resource_launch_info.xml`, `app_v2/src/main/res/xml/widget_camera_photos_info.xml`, `app_v2/src/main/res/xml/widget_random_music_info.xml`, `app_v2/src/main/res/xml/widget_continue_reading_info.xml`
**Depends on:** — independent of 02.1

**Prompt for developer:**

> Delete the `android:previewLayout="@layout/widget_preview_*"` attribute line from each of the five `widget_*_info.xml` files. Keep `android:previewImage="@drawable/widget_preview_*"` untouched. Do not delete the corresponding `widget_preview_*.xml` layout files in this phase — Phase 05 (cleanup) handles dead-resource removal.

**Verification:**

- `Grep` — `previewLayout` returns zero hits in `app_v2/src/main/res/xml/`.
- `Grep` — `android:previewImage="@drawable/widget_preview_favorites"` matches exactly once across `app_v2/src/main/res/xml/`.
- `Grep` — `android:previewImage="@drawable/widget_preview_resource_launch"` matches exactly once.
- `Grep` — `android:previewImage="@drawable/widget_preview_camera_photos"` matches exactly once.
- `Grep` — `android:previewImage="@drawable/widget_preview_random_music"` matches exactly once.
- `Grep` — `android:previewImage="@drawable/widget_preview_continue_reading"` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS (zero previewLayout in xml/, all 5 previewImage intact). Files: 5 widget_*_info.xml (-1 LOC each). Dev log recorded.

---

### Step 02.3 — Insert Timber tag in `FavoritesWidgetProvider.updateAppWidget`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `Timber.d("S0134: FavoritesWidget picker metadata applied")` at the top of `updateAppWidget` companion method, before the `RemoteViews` instantiation. Add `import timber.log.Timber` if not already imported. This single tag covers exercising the picker metadata path on widget update. Do not add tags to other widget providers — picker-side rendering happens in the system process and produces no logs.

**Verification:**

- `Grep` — `Timber.d("S0134:` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt`.
- `Grep` — `import timber.log.Timber` matches at least once in the same file.
- `Grep` — `Log.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS (Timber tag added once, import added once, zero Log.d). Files: app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` BUILD SUCCESSFUL (1m 6s, standard flavor).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -File scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Picker now shows distinct labels and chip icons for all five providers, and renders the static `widget_preview_*.png` artworks. Phase 03 reshapes the on-device background of the placed widgets and Phase 04 fixes the empty Favorites surface — both build on the now-stable picker UX.

---

## Rollback Plan

Revert the manifest edits and re-add the `android:previewLayout` lines. No data, no schema, no code-flow change.
