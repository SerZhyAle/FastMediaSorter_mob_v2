# Tactical Plan: S0112 — Android TV Launcher Visibility + D-pad Focus

**Ticket:** S0112  
**Status:** Done  
**Parent:** `PLAN/S0112_android-tv-launcher-support.md`
**Last updated:** 2026-05-14

---

## Phase 1 — Manifest + Banner

- [x] **1.1** In `app_v2/src/main/AndroidManifest.xml`, add to MainActivity intent-filter:
  ```xml
  <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
  ```
  Insert after the existing `LAUNCHER` category line (line ~91).
  **Verification:** `grep -n LEANBACK_LAUNCHER app_v2/src/main/AndroidManifest.xml` returns 1 match.

- [x] **1.2** In `AndroidManifest.xml`, add `android:banner="@drawable/tv_banner"` attribute to the `<application>` tag.
  **Verification:** `grep -n "android:banner" app_v2/src/main/AndroidManifest.xml` returns 1 match.

- [x] **1.3** Create `app_v2/src/main/res/drawable/tv_banner.xml`:
  ```xml
  <!-- TV launcher banner: 320x180 dp effective size.
       Replace with a designed 320x180 px PNG (res/drawable-xhdpi/tv_banner.png) when available. -->
  <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
      <item android:drawable="@color/tv_banner_bg" />
      <item android:gravity="center">
          <bitmap android:src="@mipmap/ic_launcher"
                  android:gravity="center" />
      </item>
  </layer-list>
  ```
  **Verification:** file exists at path above.

- [x] **1.4** Add `tv_banner_bg` color to `res/values/colors.xml`:
  ```xml
  <color name="tv_banner_bg">#1A1A2E</color>
  ```
  **Verification:** `grep -n "tv_banner_bg" app_v2/src/main/res/values/colors.xml` returns 1 match.

- [x] **1.5** Add Timber debug tag to `FastMediaSorterApp.onCreate()` (or the closest init entry point):
  ```kotlin
  Timber.d("S0112: TV launcher support active — LEANBACK_LAUNCHER registered")
  ```
  **Verification:** tag appears in logcat on app startup.

---

## Phase 2 — RecyclerView Item Focus

For each of the following files, add `android:focusable="true"` to the root view element.
Do NOT add `android:focusableInTouchMode`.
If a `res/layout-land/` counterpart exists, apply the same change there.

- [x] **2.1** `res/layout/item_duplicate_file.xml`  
  **Verification:** root element has `android:focusable="true"`.

- [x] **2.2** `res/layout/item_epub_search_result.xml`  
  **Verification:** root element has `android:focusable="true"`.

- [x] **2.3** `res/layout/item_epub_toc.xml`  
  **Verification:** root element has `android:focusable="true"`.

- [x] **2.4** `res/layout/item_duplicate_group.xml`  
  **Verification:** root element has `android:focusable="true"`.

- [x] **2.5** `res/layout/item_rename_file.xml` — SKIPPED: root is TextInputLayout; focus goes natively to inner TextInputEditText; adding focusable to the container would interfere  
  **Verification:** root element has `android:focusable="true"`.

- [x] **2.6** `res/layout/item_scheduled_operation.xml`  
  **Verification:** root element has `android:focusable="true"`.

---

## Phase 3 — Player Controls Focus

- [x] **3.1** Read `res/layout/custom_player_controls.xml`. For every interactive button (play/pause, next track, prev track, seek bar, subtitle toggle, settings), verify `android:focusable="true"` is set. Add where missing.  
  **Verification:** all `<ImageButton>`, `<Button>` elements in the file have `android:focusable="true"`.

- [x] **3.2** Read `res/layout/custom_player_controls_large.xml`. All ImageButtons use Widget.AppCompat.Button.Borderless style (focusable by default); exo_repeat/btnPlaybackControl already have nextFocusLeft/Right. No changes needed. Apply same check and fixes.  
  **Verification:** all interactive buttons have `android:focusable="true"`.

---

## Phase 4 — Post-change Housekeeping

- [x] **4.1** Run `.\scripts\add_to_dev_log.ps1` for all changed files.
- [x] **4.2** Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` — 961 files scanned.
- [x] **4.3** Run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` — 961 records rendered.
- [x] **4.4** Implementation payload already exists in repository history; no additional commit was required during the 2026-05-14 alignment pass.

## Change Log

- 2026-05-14 — Tactical index aligned to `Done` after confirming the S0112 payload already exists in code and repository history.
