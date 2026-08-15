# Phase 04 - Command/menu wiring + resources + debug tags

**Status:** Pending

Surface the command in both overflow menus, add icon + strings, wire taps to `rotateSession90()` + apply, insert the S0995 device-test probes.

## Files touched

- `ui/player/helpers/CommandPanelLayoutPlanner.kt` (enum `:34-193`, `buildActiveCommands()` `:212-330`)
- `ui/player/CommandPanelController.kt` (`handleOverflowCommand()` `:684-727`; `CommandPanelCallback`)
- `ui/player/standalone/PhotoVideoStandaloneActivity.kt` (overflow inflate `:638-639`, isVisible gate `:652-675`, dispatch `:677-712`)
- `res/menu/overflow_menu_standalone_player.xml`
- `res/drawable/ic_rotate_90.xml` (NEW)
- `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`

## Steps

1. **Internal command:** add a new low-priority overflow-only `PlayerCommand` entry (e.g. `ROTATE_CONTENT`), pattern of `DRAW_OVERLAY(650)` - `barCapable=false`, `priority` high number (low priority), `iconResId = R.drawable.ic_rotate_90`, `titleResId = R.string.rotate_content_90_title`, a fresh `menuItemId` (add `menu_rotate_content` id to the ID-pool XML `overflow_menu_player.xml`). Gate in `buildActiveCommands()` on `isImage || (isVideo && !isAudio)`.
   - Verify: enum entry present, `barViewForCommand()` NOT extended (overflow-only), gate added.
2. **Internal dispatch:** add a `handleOverflowCommand()` branch for the new `menuItemId` -> `callback.onRotateContent90()` (new `CommandPanelCallback` method) -> `PlayerViewModel.rotateSession90()` then apply via the handle. Insert probe `Timber.d("S0995: internal rotate90 tap -> ${'$'}newAngle")` at this entry.
   - Verify: tap path reaches `rotateSession90()`; probe present.
3. **Standalone command:** add `<item android:id="@+id/menu_rotate_content_standalone" android:title="@string/rotate_content_90_title" .../>` to `overflow_menu_standalone_player.xml` (text-only per existing convention - do NOT add an icon there unless extending the convention; keep scope minimal). In `PhotoVideoStandaloneActivity`: `popup.menu.findItem(R.id.menu_rotate_content_standalone).isVisible = isImage || isVideo`; add `when` branch -> `viewModel.rotateSession90()` + apply. Insert probe `Timber.d("S0995: standalone rotate90 tap -> ${'$'}newAngle")`.
   - Verify: item added, isVisible gated to photo/video host media, dispatch wired, probe present.
4. **Drawable:** create `res/drawable/ic_rotate_90.xml` - a "rotate frame clockwise 90" vector (distinct from the sensor padlock `ic_rotation_*`). Use `?attr` tint via the planner's existing tint path (no hardcoded hex - Rule 19).
   - Verify: file exists, no hardcoded `#hex` fill.
5. **Strings (EN/RU/UK):** add `rotate_content_90_title` (e.g. EN "Rotate 90°" / RU "Повернуть на 90°" / UK "Повернути на 90°") and `rotate_content_90_desc` (contentDescription for the icon). Use a distinct key - NOT `big_btn_short_rotation` (taken by the sensor toggle). Add via `scripts/utils/set-android-string.ps1 -Action add -En .. -Ru .. -Uk ..` then `scripts/check_strings_localized.ps1 -KeyPrefix rotate_content_90`.
   - Verify: string-audit exit 0; all three locales present.

## Done criteria

- "Rotate 90°" appears in overflow of internal player AND `PhotoVideoStandaloneActivity`, for video and images, low priority.
- Tap -> cumulative angle -> frame rotates (image via Phase 02, video via Phase 03); controls unaffected; file untouched.
- `ic_rotate_90` + three-locale strings present; string-audit clean.
- Two S0995 probes present (one per family entry) - this is the last code edit before the final build (BlockNeedUserTest contract).
- `standard debug` builds green; detekt-clean (log lines <=120, no magic numbers).
