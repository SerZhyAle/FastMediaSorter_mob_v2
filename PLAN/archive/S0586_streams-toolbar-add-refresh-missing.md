**Status:** Archived

# S0586 - Streams toolbar: missing "add stream" and "refresh" actions

## 0. Raw capture

User report (RU, verbatim):
1. Кнопка "+" добавить свой стрим пропала с верхней панели?
2. Кнопка "оновить" (refresh) пропала с верхней панели?

Evidence (screenshot, Streams screen): the top app bar shows only the back arrow, the "Streams" title and a single download/import icon on the right. No "+" (add), no refresh icon, and no overflow (three-dot) menu are visible.

## 1. Symptom

- `menu_streams.xml` declares three actions, all `showAsAction="ifRoom"`:
  - `action_stream_add` (`ic_add`, `streams_add`)
  - `action_stream_import` (`ic_import`, `streams_import`)
  - `action_stream_refresh` (`ic_refresh`, `streams_refresh`)
- At runtime only `action_stream_import` is rendered. `action_stream_add` and `action_stream_refresh` are not on the bar and there is no overflow chevron to reach them.

## 2. Open questions / research

- Why do only one of three `ifRoom` actions render? Candidates: toolbar width constraint, a recent change to menu inflation / toolbar setup in `StreamsActivity`, an `onPrepareOptionsMenu` that hides items, or items being consumed by another component.
- Confirm the toolbar is a real `MaterialToolbar` with `setSupportActionBar`/`inflateMenu`, and that overflow is enabled.
- Decide intended layout: should all three be `always` on the bar (icons are small) or should an overflow be guaranteed so collapsed items remain reachable.
- Note: `action_stream_refresh` currently only calls `rvStreams.scrollToPosition(0)` - its handler and label/intent should be reviewed while fixing visibility (separate UX concern, see also S0587 which adds explicit scroll-nav buttons).

## 3. Acceptance

- "+" (add stream) action is reachable from the Streams top bar.
- "Refresh" action is reachable from the Streams top bar.
- No regression to the import (download) action.
- Behaviour consistent in portrait and landscape.

## Last Audit

**Date:** 2026-06-24 · **Mode:** strategic (sweep finalize) · **Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Device evidence (below) confirms all three toolbar actions (add +, import, refresh) render visibly with correct contrast and no clipping across light/dark x portrait/landscape on both standard and noLegal flavours. Objective acceptance (§3) fully met, no owner-gate. `/spec-check` (via `/spec-sweep`) flips BlockNeedUserTest -> Verified and removes the `Timber.d("S0586:` probe from `StreamsActivity.kt`.

### 2026-06-24 - Manual device test (emulator-5554, sdk_gphone16k_x86_64, Android 17/API 37, standard debug)

Build: REUSE of the standard-debug APK from this session's prior sweep (no rebuild). Installed `versionName=2.60.6241.447-DEBUG` matches `FastMediaSorter_standard_debug_v2.60.6241.447-DEBUG.apk` and the Settings footer (`2.60.6241.447-DEBUG | Build 260624144`). Device 1080x2280, density 440. This is the `standard` flavour (the 2026-06-23 sweep below was `noLegal`); `menu_streams.xml` + `StreamsActivity.tintToolbarMenuIcons()` live in `src/main`, so both flavours render identically - this run is the standard-flavour confirmation. Reached Streams from the main 3-dot dropdown ("Streams" item, feature toggle ON). Light/dark switched via device night mode with the app's Color theme on "Auto (follow device)"; orientation forced via `user_rotation`.

All three top-app-bar actions are present in the accessibility tree and render as crisp, fully visible, correctly-tinted icons in every combo - `action_stream_add` ("Add stream"), `action_stream_import` ("Import list"), `action_stream_refresh` ("Refresh"). White icons on the Light-theme indigo `colorPrimary` header; dark-navy icons on the Dark-theme lavender header. No overflow chevron needed, no clipping.

Matrix (all-three-visible?):
- light-portrait -> YES (`light_portrait.png`; add/import/refresh bounds 684/816/948 within 1080w)
- light-landscape -> YES (`light_landscape.png`; bounds 1884/2016/2148 within 2280w)
- dark-portrait -> YES (`dark_portrait.png`; bounds 684/816/948)
- dark-landscape -> YES (`dark_landscape.png`; bounds 1884/2016/2148)

Screenshots: `temp/S0586_screens/{light_portrait,light_landscape,dark_portrait,dark_landscape}.png`. Scenario + run log: `temp/S0586_mobile_test_scenario_20260624_1530.md`. Captured logcat: `temp/S0586_run_20260624_1530.log`.

Fired probe (logcat, `StreamsActivity.kt:134`, one per Streams entry, D-level):
- `06-24 15:31:45.262 D StreamsActivity: S0586: tinting streams toolbar menu icons`
- `06-24 15:34:45.471 D StreamsActivity: S0586: tinting streams toolbar menu icons`
- `06-24 15:35:40.535 D StreamsActivity: S0586: tinting streams toolbar menu icons`

No crash, no app-code exception in the run window. E-level logcat lines are emulator/system noise (android.xr absent, ethernet service, RECOMMEND_NETWORKS) or expected `am force-stop` fallout, none in the Streams render path.

Verdict: PASS - add (+), import (download), refresh all render visibly with correct contrast and no clipping/overflow in all four theme x orientation combos on the standard flavour. Spec left in `BlockNeedUserTest` (no status flip in this device-test run); `Timber.d("S0586:` tag retained.

### 2026-06-23 - Manual device test (emulator-5554, Android 17 x86_64, noLegal debug)

Build: installed `com.sza.fastmediasorter.debug` (noLegal). Streams toolbar + `menu_streams.xml` live in `src/main`, so this renders identically to standard - valid evidence. Navigated to Streams via Settings -> Media -> "Streams" section -> "Streams" button (`btnStreams`). Theme switched via Settings -> General -> Color theme (plain Light / Dark, not a custom tint), app restarted between themes. Device natural orientation is landscape: `user_rotation 0` = landscape (2560x1600, ROTATION_0), `user_rotation 1` = portrait (1600x2560, ROTATION_90, with `accelerometer_rotation` disabled so the forced rotation takes effect).

All three top-app-bar actions are present in the accessibility tree in every combo, distinct and clickable: `action_stream_add` ("Add stream"), `action_stream_import` ("Import list"), `action_stream_refresh` ("Refresh"). All three render as crisp, fully visible icons with correct contrast (the `tintToolbarMenuIcons()` fix applies `colorOnPrimary`: white icons on the Light-theme colorPrimary header, dark-navy icons on the Dark-theme header). No overflow chevron needed, no clipping.

Matrix (all-three-visible?):
- light-portrait -> YES (`light_portrait.png`; toolbar crop `crop_lp_toolbar.png`; add/import/refresh bounds 1296/1392/1488 within 1600w)
- light-landscape -> YES (`light_landscape.png`; bounds 2256/2352/2448 within 2560w)
- dark-portrait -> YES (`dark_portrait.png`; toolbar crop `crop_dp_toolbar.png`; bounds 1296/1392/1488)
- dark-landscape -> YES (`dark_landscape.png`; bounds 2256/2352/2448)

Screenshots: `temp/S0586_sweep/{light_portrait,light_landscape,dark_portrait,dark_landscape}.png`, zoomed toolbar crops `temp/S0586_sweep/{crop_lp_toolbar,crop_dp_toolbar}.png`.

Fired probe (logcat, `StreamsActivity.kt:134`, one per Streams entry):
- `06-23 18:00:55.973 D StreamsActivity: S0586: tinting streams toolbar menu icons`
- `06-23 18:05:08.310 D StreamsActivity: S0586: tinting streams toolbar menu icons`

Verdict: PASS - add (+), import (download), refresh all render visibly with no clipping/overflow in all four theme x orientation combos. Spec left in `BlockNeedUserTest` (no status flip in this sweep); `Timber.d("S0586:` tag retained.

## Revision History

- **2026-06-24** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 17/API 37)
  - Scenario: temp/S0586_mobile_test_scenario_20260624_1530.md - PASS 4/4 combos (light/dark x portrait/landscape), standard flavour - Errors in log: 0 app-code
