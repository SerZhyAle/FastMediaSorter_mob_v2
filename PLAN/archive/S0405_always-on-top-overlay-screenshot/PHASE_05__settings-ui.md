# Phase 05 - Settings UI (toggles + destination + permission)

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-11
**Completed:** 2026-06-11
**Completed:** -

---

## Objective

On the Playback settings page, add a new collapsible group "Системные приложения" containing a "Жесты экрана" subgroup with the three settings - "Оверлей жестов" toggle, "Скриншот жестом вниз" toggle, "Загружать скриншоты в.." destination picker (any registered resource incl. network). The whole group is gated by capability availability (empty controller set → group hidden). Enabling the overlay routes the user to grant the draw-over-apps permission. Add all strings in EN/RU/UK.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (settings fields + controller injection point).
- [ ] Phase 04 ✅ Done (noLegal binding contributes to the set).
- [ ] Confirm landscape counterpart `res/layout-land/fragment_settings_playback.xml` exists (it does) - any portrait layout edit must be mirrored.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> The fragment is flavor-agnostic; capability gating uses the injected `Set<ScreenGestureOverlayController>.firstOrNull()` - NOT a `BuildConfig` flavor guard. On standard the set is empty → the whole group is hidden. `PlaybackSettingsFragment` is large and may carry uncommitted edits; add the rows as a self-contained group and keep row-wiring in a small private helper to respect the LOC budget.

---

## Steps

### Step 05.1 - Add the screenshot/overlay settings group to both layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a collapsible group "Системные приложения" (collapsible header + content container, mirroring the existing collapsible-group pattern on this page) containing a "Жесты экрана" subgroup header and three rows: a switch row for "Оверлей жестов", a switch row for "Скриншот жестом вниз", and a clickable destination row ("Загружать скриншоты в.." with a current-value label) mirroring the existing destination rows in this fragment. Give the whole group a stable id so it can be shown/hidden at runtime. Edit BOTH the portrait and landscape variants identically. Use `?attr/`/`@color/` for colours - no hardcoded `#hex` (CLAUDE.md Rule 19). Ensure rows are `focusable`/`clickable` with `nextFocus*` for D-pad/TV (CLAUDE.md Rule 16).

**Verification:**

- `Grep` - the new group id (e.g. `groupSystemApps`) present in `res/layout/fragment_settings_playback.xml`.
- `Grep` - the same group id present in `res/layout-land/fragment_settings_playback.xml`.
- `Grep` - the destination row id + the two switch ids present in both layout files.
- `Grep` - no `="#` literal hex colour added in the touched layout regions.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Added `groupSystemApps` card (collapsible `headerSystemApps`/`containerSystemApps`) with "Screen gestures" subgroup: `rowGestureOverlayEnabled`, `rowScreenshotGestureDown`, destination row `rowScreenshotDestination` + `tvScreenshotDestinationValue`. Portrait + landscape identical; `?attr/`/`@color/` only, no hex. Files: layout + layout-land fragment_settings_playback.xml. Dev log recorded.

---

### Step 05.2 - Add EN/RU/UK strings (one lockstep call)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the new keys across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`. Keys: `setting_group_system_apps_title` ("Системные приложения"), `setting_subgroup_screen_gestures_title` ("Жесты экрана"), `setting_gesture_overlay_title`, `setting_gesture_overlay_summary`, `setting_screenshot_gesture_down_title`, `setting_screenshot_gesture_down_summary`, `setting_screenshot_destination_title`, `screenshot_overlay_permission_rationale`. (Capture-flow strings - `screen_capture_*` - are added in Phase 03, which consumes them.) RU uses `ё`/`Ё` and `..` (not `...`). All user-visible copy must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and the §6 tone checklist.

**Verification:**

- `Grep` - each key present in all three `strings.xml` files (incl. `setting_group_system_apps_title`, `setting_subgroup_screen_gestures_title`).
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_gesture_overlay"` → exit 0; repeat for `setting_group_system_apps` and `setting_screenshot`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS (check_strings_localized exit 0 for `setting_gesture_overlay`, `setting_group_system_apps`, `setting_screenshot`). Added the 8 listed keys plus `setting_screenshot_destination_default` (value-fallback label required by the destination row's `refreshDestinationLabel` call in Step 05.3). EN/RU/UK lockstep; RU uses `ё`/`..`. Dev log recorded.

---

### Step 05.3 - Wire the rows to settings + controller in the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Inject `Set<ScreenGestureOverlayController>`; take `controller = set.firstOrNull()`. If `controller == null`, hide the whole group and return. Otherwise: bind the two switches to `gestureOverlayEnabled` / `screenshotGestureDownEnabled` via `viewModel.updateSettings(settings.value.copy(...))`; bind the destination row to the existing `showDestinationPicker(currentResourceId) { resource -> updateSettings(copy(screenshotDestinationResourceId = resource?.id?.toString())) }` and `refreshDestinationLabel(...)`. When the overlay switch is turned on and `controller.isOverlayPermissionGranted(context)` is false, show the rationale string and launch `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`; only call `controller.setEnabled(true)` once permission is granted. Turning off calls `controller.setEnabled(false)`. Collect settings with `collectOnLifecycle`/`repeatOnLifecycle` - no bare `lifecycleScope.launch { collect }` (CLAUDE.md Rule 19). No business logic beyond delegation.

**Verification:**

- `Grep` - `Set<` and `ScreenGestureOverlayController` referenced in the fragment.
- `Grep` - `ACTION_MANAGE_OVERLAY_PERMISSION` referenced.
- `Grep` - `showDestinationPicker` referenced for the screenshot destination.
- `Grep -n "lifecycleScope.launch"` shows no bare view-bound `collect` (uses `collectOnLifecycle`/`repeatOnLifecycle`).
- `Grep -n "Log\.d\("` in the fragment returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 5/5 PASS. Injected `Set<@JvmSuppressWildcards ScreenGestureOverlayController>`; `setupSystemAppsSection()` hides `groupSystemApps` when the set is empty, else wires the two toggles, the destination picker, and the draw-over-apps permission flow (`ACTION_MANAGE_OVERLAY_PERMISSION` via `overlayPermissionLauncher`). Settings collected through existing `collectOnLifecycle`; new collapsible section registered. The pre-existing `lifecycleScope.launch` at the refreshDestinationLabel resource lookup is a suspend call, not a view-bound Flow collect. File 805→898 LOC (table budget ≤700 was already exceeded by pre-existing code; well under the 1500 hard limit). Dev log recorded.

---

### Step 05.4 - Register the new settings in search (if applicable)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> If `SettingsSearchRegistry` enumerates settings rows for in-app search, register the three new rows with their title string keys so they are discoverable, gating availability so they only appear when the capability is present (mirror the existing per-flavor `NoLegalSettingsSearchAvailabilityModule` availability pattern). If the registry does not cover this fragment's rows, skip with a one-line note in the dev log.

**Verification:**

- `Grep` - either the three new title keys appear in `SettingsSearchRegistry.kt`, or the dev log records "settings-search registry does not enumerate playback rows - skipped".

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 1/1 PASS (dev-log skip phrase recorded). Registry uses an auto XML-scan indexer (`LayoutSettingsSearchSource`) with no manual row list; `SettingsSearchAvailability` gates only media sections, the `playback` section is always available and has no per-row capability gate. The new toggle rows auto-index exactly like existing conditionally-hidden playback rows (camera-OCR, mic-recording, PiP); the destination row is a `LinearLayout` and is not an indexable kind. No code change.

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] Project compiles - standard debug BUILD SUCCESSFUL (group hidden, empty controller set) + noLegal debug BUILD SUCCESSFUL (group shown).
- [x] `check_strings_localized.ps1` exits 0 for the new key prefixes.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Full noLegal flow is user-drivable from Settings. Phase 06 finalises docs (FEATURES_noLegal), catalog, and flavor hints, and flips the ticket to BlockNeedUserTest.

---

## Rollback Plan

Revert phase commit(s) - layout/string/fragment edits only; remove the added strings via `set-android-string.ps1 -Action remove`.
