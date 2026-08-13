# Phase 01 - Icon-style 1x1 layouts

**Strategic spec:** [`../S0348_home-widget-icon-refresh.md`](../S0348_home-widget-icon-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Make all compact `1x1` action-widget layouts read as launcher icons: drop the truncatable label `TextView`, enlarge and center the icon `ImageView`, and move the visible text into a root-container `contentDescription` for TalkBack. Two widgets have provider code that writes into the removed text views and must be updated in lockstep: `ResourceLaunchWidgetProvider` (writes the resource name) and `GameLaunchWidgetProvider` (writes the label and toggles a disabled label). No sizing or registry changes here.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] No `res/layout-land/widget_*.xml` counterparts exist (confirmed 2026-06-04) - these widget layouts are portrait-only, so landscape parity does not apply.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/dimens.xml` | Modified | +1 dimen |
| `app_v2/src/main/res/layout/widget_calculator.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout/widget_camera_photos.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout/widget_continue_reading.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout/widget_random_music.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout/widget_camera_ocr_translate.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout/widget_resource_launch.xml` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetProvider.kt` | Modified | ≤ 170 |
| `app_v2/src/main/res/layout/widget_game_launch.xml` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/GameLaunchWidgetProvider.kt` | Modified | ≤ 130 |
| `app_v2/src/main/res/xml/widget_calculator_info.xml` | Modified | +1 attr |
| `app_v2/src/main/res/xml/widget_camera_photos_info.xml` | Modified | +1 attr |
| `app_v2/src/main/res/xml/widget_continue_reading_info.xml` | Modified | +1 attr |
| `app_v2/src/main/res/xml/widget_random_music_info.xml` | Modified | +1 attr |
| `app_v2/src/main/res/xml/widget_resource_launch_info.xml` | Modified | +1 attr |
| `app_v2/src/main/res/xml/widget_game_launch_info.xml` | Modified | +1 attr |

> Landscape variants: none of these layouts has a `res/layout-land/` counterpart (verified). No landscape edit required.
> All files are well under 500 lines - no backup step needed.

---

## Steps

### Step 01.1 - Icon-style the four pure-layout widgets + add shared dimen

**Files:** `dimens.xml`, `widget_calculator.xml`, `widget_camera_photos.xml`, `widget_continue_reading.xml`, `widget_random_music.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> These four widgets have NO provider code writing into their label (verified: their providers only bind the click `PendingIntent`). Add a shared dimen `widget_icon_size_large` = `40dp` to `res/values/dimens.xml` (one value for all icon widgets). In each of the four layouts: delete the label `<TextView>` element entirely; set the icon `<ImageView>` `layout_width`/`layout_height` to `@dimen/widget_icon_size_large` and keep it centered in the root container; add `android:contentDescription` on the **root container** pointing at the same string the removed TextView used (`@string/widget_calculator_label`, `@string/widget_camera_photos_label`, `@string/continue_reading`, `@string/widget_random_music_label` respectively). Keep `@drawable/widget_background` and the existing root ids. Do not remove any `@string` resource.

**Verification:**

- `Grep -n "<TextView"` in each of the four files returns zero hits.
- `Grep -n "widget_icon_size_large"` matches in `res/values/dimens.xml` and in each of the four layouts.
- `Grep -n "android:contentDescription"` on the root container element matches once in each file.
- Build: `.\build-debug.PS1` (standardDebug) compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): `<TextView` 0 hits across 4 layouts; `widget_icon_size_large` in dimens + 4 layouts; root `contentDescription` added. Build deferred to phase gate. Files: dimens.xml, widget_calculator/camera_photos/continue_reading/random_music.xml.

---

### Step 01.2 - Icon-style the Camera-OCR layout

**Files:** `widget_camera_ocr_translate.xml`
**Depends on:** Step 01.1 (shared `widget_icon_size_large` dimen exists)

**Prompt for developer:**

> Camera-OCR provider binds only the click intent on the container (verified - no `setTextViewText`). Delete `widget_camera_ocr_translate_label` TextView, enlarge/center `widget_camera_ocr_translate_icon` to `@dimen/widget_icon_size_large`, and set `android:contentDescription="@string/setting_camera_ocr_translation_title"` on `widget_camera_ocr_translate_container`. Keep the container id - `CameraOcrTranslateWidgetProvider` binds its click `PendingIntent` to it.

**Verification:**

- `Grep -n "<TextView"` in `widget_camera_ocr_translate.xml` returns zero hits.
- `Grep -n "widget_camera_ocr_translate_container"` still matches (root id preserved).
- `Grep -n "android:contentDescription"` on the root matches once.
- Build: `.\build-debug.PS1` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): `<TextView` 0 hits; container id `widget_camera_ocr_translate_container` preserved; icon enlarged + root contentDescription. Build deferred to phase gate.

---

### Step 01.3 - Icon-style Resource-Launch (layout + provider)

**Files:** `widget_resource_launch.xml`, `ResourceLaunchWidgetProvider.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> The resource shortcut already carries source/type identity through `resolveIcon(resourcePath, resourceTypeName)` (provider line ~102), so per strategic §6.2.2 the visible name can go and the icon stays as the identity. In `widget_resource_launch.xml`: delete `widget_resource_name` TextView, enlarge/center `widget_resource_icon` to `@dimen/widget_icon_size_large`, add `android:contentDescription="@string/resource"` on `widget_resource_container`. In `ResourceLaunchWidgetProvider.kt`: remove the two `views.setTextViewText(R.id.widget_resource_name, ...)` calls (the configured branch ~line 100 and the unconfigured branch ~line 117-120). Keep the `resolveIcon` / `setImageViewResource` / `setColorFilter` calls and both `setOnClickPendingIntent` branches unchanged. Do not change the config flow or pin callback.

**Verification:**

- `Grep -n "<TextView"` in `widget_resource_launch.xml` returns zero hits.
- `Grep -n "widget_resource_name"` in `ResourceLaunchWidgetProvider.kt` returns zero hits.
- `Grep -n "resolveIcon"` in `ResourceLaunchWidgetProvider.kt` still matches (identity preserved).
- `Grep -n "widget_resource_container"` still matches in the layout (root id preserved).
- Build: `.\build-debug.PS1` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): layout `<TextView` 0; provider `widget_resource_name` 0 hits; `resolveIcon` preserved (identity); container id preserved. Removed 2 setTextViewText blocks. Build deferred to phase gate. Files: widget_resource_launch.xml, ResourceLaunchWidgetProvider.kt.

---

### Step 01.4 - Icon-style Game-Launch (layout + provider, disabled state via icon dim)

**Files:** `widget_game_launch.xml`, `GameLaunchWidgetProvider.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> The game widget shows a name label (`widget_game_label`) and a separate disabled-state text label (`widget_game_disabled_label`) toggled by the provider. Icon-only assumption (strategic §3.3 delegation): convey disabled state by **dimming the icon**, not by a text label. In `widget_game_launch.xml`: delete BOTH `widget_game_label` and `widget_game_disabled_label` TextViews; enlarge/center `widget_game_icon` to `@dimen/widget_icon_size_large`; add `android:contentDescription="@string/game_widget_label"` on `widget_game_container`. In `GameLaunchWidgetProvider.updateAppWidget(...)`: remove the `views.setTextViewText(R.id.widget_game_label, ...)` line and the `views.setViewVisibility(R.id.widget_game_disabled_label, ...)` block; instead set the icon alpha to reflect state via `views.setInt(R.id.widget_game_icon, "setImageAlpha", if (enabled) 255 else 110)`. Keep the existing `setOnClickPendingIntent(R.id.widget_game_container, ...)` (disabled still routes to the game-toggle setting).

**Verification:**

- `Grep -n "<TextView"` in `widget_game_launch.xml` returns zero hits.
- `Grep -n "widget_game_label\b|widget_game_disabled_label"` in `GameLaunchWidgetProvider.kt` returns zero hits.
- `Grep -n "setImageAlpha"` in `GameLaunchWidgetProvider.kt` matches once.
- `Grep -n "setOnClickPendingIntent"` in `GameLaunchWidgetProvider.kt` still matches (click preserved).
- Build: `.\build-debug.PS1` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): layout `<TextView` 0; provider `widget_game_label`/`widget_game_disabled_label` 0 hits; `setImageAlpha` + `setOnClickPendingIntent` present; removed unused `View` import. No `R.id.<removed>` references remain anywhere in `src/main/java`. Build deferred to phase gate. Files: widget_game_launch.xml, GameLaunchWidgetProvider.kt.

---

### Step 01.5 - Preview parity: add previewLayout

**Files:** `widget_calculator_info.xml`, `widget_camera_photos_info.xml`, `widget_continue_reading_info.xml`, `widget_random_music_info.xml`, `widget_resource_launch_info.xml`, `widget_game_launch_info.xml`
**Depends on:** Step 01.1, 01.2, 01.3, 01.4

**Prompt for developer:**

> The existing `android:previewImage="@drawable/widget_preview_*"` rasters depict the OLD card-with-label form and are now stale (strategic §11.4 preview parity). Add `android:previewLayout="@layout/widget_<name>"` to each of the six icon-refreshed `1x1` info XMLs so launchers on API 31+ render the live icon-only layout as the picker preview. Keep the existing `previewImage` line as the legacy (< API 31) fallback. The stale `widget_preview_*` raster art cannot be regenerated from code - this is a follow-up art task noted for the device-test checklist, not a blocker (modern launchers use `previewLayout`).

**Verification:**

- `Grep -n "previewLayout"` across the six info XMLs - expected: present in all six | actual: present in all six.
- `Glob res/layout/widget_*_preview.xml` returns empty (no stale preview layout files).

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS: `previewLayout` added to all six icon-refreshed `1x1` info XMLs; no stale `widget_*_preview.xml` layout files. Note: `widget_preview_*` raster art still depicts old card form - follow-up art task for legacy launchers (< API 31).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` (standardDebug) BUILD SUCCESSFUL 1m50s.
- [x] `Grep -n "<TextView"` across all seven `1x1` `widget_*.xml` returns zero hits.
- [x] `Grep -n "Log\.d\("` in the two touched providers returns zero hits (Timber only).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1613 records).

---

## Handoff Notes to Next Phase

Icon-only `1x1` layouts are in place; `ResourceLaunch` and `GameLaunch` providers updated to match (name removed / disabled state via icon dim). Phase 02 changes only Camera-OCR provider sizing (info XML). Phase 03 builds the registry that lists these widgets for the picker.

---

## Rollback Plan

Revert the phase commit(s) - layout/dimens plus two small provider edits. Already-placed widgets keep their click action because provider ids and click bindings are unchanged.
