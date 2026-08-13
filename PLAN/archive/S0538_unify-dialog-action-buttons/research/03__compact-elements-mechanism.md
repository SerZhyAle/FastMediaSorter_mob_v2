# Research 03 - "Compact elements (global)" mechanism

Bound to §6 item 4. Triggered by owner reminder (2026-06-19): the unified dialog buttons must shrink with the global compact toggle, like the rest of the UI.

## The setting

- `AppSettings.useCompactElements: Boolean = false` (default large/expanded). Persisted in DataStore: `SettingsRepositoryImpl` `KEY_USE_COMPACT_ELEMENTS ?: false`.
- Settings UI label: `setting_compact_elements_desc` = "Reduce element sizes and spacing across the app by 50%". So the contract is an exact 50% reduction.
- Toggling it requires an app restart (the toggle handler in `GeneralSettingsViewSetupHelper` confirms restart, writes both DataStore and a synchronous prefs mirror, then restarts).

## How compact is applied today - per-view code, NO global theme switch

There is no single theme attribute or dimens swap that all elements read. Compact is applied imperatively, per surface:

- Lists: `MediaFileAdapter` / `PagingMediaFileAdapter` / `ResourceAdapter` - explicit `if (useCompactElements) size / 2` (thumbnail size, disable-overlay size, text size, cube size, button size). `setUseCompactElements()` re-binds.
- Toolbars: `MainActivity` / `SettingsActivity` - `applyCompactToolbar(settings.useCompactElements)`.
- Player text overlays: `PlayerCompactElementsManager.apply()/restore()` - sets `setTextSize(..)` smaller.
- Player command buttons: `CommandPanelController.applySmallControlsIfNeeded` / `showSmallControls` path.
- Player controls LAYOUT (DefaultTimeBar bar/touch/scrubber): the ONE theme-overlay case - `Theme.FastMediaSorter.LargePlayerControls` applied via `theme.applyStyle(...)` in `PlayerActivity.onCreate` BEFORE `super.onCreate`, only when compact is OFF. This overlay swaps only `customPlayerControlsLayout`; it is player-specific, NOT a global element-size overlay.

Implication: dialogs currently do NOT follow compact at all. A fixed-56dp dialog-button style would ignore the toggle.

## The synchronous-flag pattern (reusable)

`PlayerLayoutModePrefs` (`ui/player/helpers/`) is a dedicated SharedPreferences file mirroring `useCompactElements`, because the canonical DataStore setting is async and theme decisions must be made synchronously at `onCreate` before inflate:

- `PlayerLayoutModePrefs.isCompact(context): Boolean` (default false).
- `setCompact(context, useCompact)` - written by the settings toggle alongside DataStore, right before restart.
- `applyControlsThemeOverlay(activity)` - `activity.theme.applyStyle(R.style.Theme_FastMediaSorter_LargePlayerControls, true)` when NOT compact.

This is exactly the hook shape needed for dialog buttons: a synchronous flag readable at Activity creation + `theme.applyStyle`.

## Chosen design for dialog buttons (DRY, attribute-driven)

1. Theme attributes (new): `dialogActionButtonMinHeight`, `dialogActionButtonGap` (reference/dimension).
2. Dimens: large `dialog_action_button_min_height` 56dp + `dialog_action_button_gap` 16dp; compact `..._compact` = exactly half (28dp / 8dp), honoring the documented 50% rule.
3. Base theme `Theme.FastMediaSorter.App` sets the attrs to the LARGE dimens (default, since compact default is false).
4. New `ThemeOverlay.FastMediaSorter.CompactDialogButtons` sets the attrs to the COMPACT dimens.
5. `BaseActivity.onCreate` applies the compact overlay when `PlayerLayoutModePrefs.isCompact(this)` is true - ONE hook covers every dialog-hosting activity (all extend `BaseActivity`, confirmed `core/ui/BaseActivity.kt`).
6. The dialog action-button styles read `?attr/dialogActionButtonMinHeight` / `?attr/dialogActionButtonGap` instead of fixed dimens. Builder dialogs (via `materialAlertDialogTheme` seam) and custom layouts both resolve the attr from the activity theme chain, so they auto-follow compact.

## Why attribute + BaseActivity over per-view code

- Single application point vs touching ~94 dialog call-sites with runtime sizing.
- Works with the builder theme seam (the seam's button styles read `?attr`).
- Avoids post-`show()` `getButton(...)` mutation, which research 02 flagged as fighting `DialogAccessibilityHelper`'s post-show focus wiring.
- Reuses the project's existing synchronous-flag + `theme.applyStyle` pattern (`PlayerLayoutModePrefs`).

## Files of record

- `domain/model/AppSettings.kt` (`useCompactElements`), `data/repository/SettingsRepositoryImpl.kt` (persistence).
- `ui/player/helpers/PlayerLayoutModePrefs.kt` (sync flag + overlay pattern).
- `core/ui/BaseActivity.kt` (common host; `onCreate` hook point).
- `res/values/themes.xml` (`Theme.FastMediaSorter.App`, `Theme.FastMediaSorter.LargePlayerControls`), `res/values/attrs.xml` (new attrs), `res/values/dimens.xml`.
