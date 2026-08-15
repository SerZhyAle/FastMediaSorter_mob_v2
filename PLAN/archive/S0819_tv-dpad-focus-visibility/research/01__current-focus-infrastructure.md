# Research 01 - Current focus infrastructure (S0819)

**Артефакт исследования** (android-solution-researcher, 2026-07-01). Read-only. Вход для `/spec-tech` (F2).

## Central wiring point

- `BaseActivity<VB>` (`core/ui/BaseActivity.kt`, 582 LOC) extended by **26 activities** (Browse, Main, Player, StandalonePlayer, Settings, Streams, Duplicates, Statistics, AddResource, ResourceEditor, Calculator, Game, ..). Already hosts: `FocusTargetResolver` wiring (initial focus + `dispatchKeyEvent` guard, S0504/S0506), `TvKeyRouter`, gamepad nav translator, `isTvDevice()` (`FEATURE_LEANBACK` + `UI_MODE_TYPE_TELEVISION`), `ActivityMouseDispatchHelper`.
- Direct `AppCompatActivity`/`ComponentActivity` (NOT BaseActivity): widget-config, consent, cloud-auth, share, print/standalone-dispatch, VR `DiagnosticXrActivity`, debug. Mostly transient/headless - lower priority for the frame.

## Existing focus-visualization mechanisms (TWO, inconsistent)

1. **Declarative XML selectors** (majority pattern, `ui/browse/`):
   - `res/drawable/item_focus_selector.xml` + `item_interaction_overlay.xml` -> `android:background`/`android:foreground` on `item_media_file*.xml`, `item_resource*.xml`.
   - `res/drawable/focus_button_background.xml` (S0289): `state_focused`/`state_hovered` -> 2dp stroke `@color/focus_button_stroke`, transparent default, applied via `android:foreground`. Used on toolbar/action buttons and `activity_player_unified.xml` `playbackButtonRow`, but NOT on `custom_player_controls*.xml` transport row.
2. **Imperative runtime ring** (`ui/common/`): `FocusManager` + `FocusRingHelper` build a `GradientDrawable` ring at runtime, assign to `view.foreground`, used only by `MainActivity` resource list - stacked on top of `item_focus_selector` (redundant double mechanism).

## Focus color tokens - hardcoded, theme-blind

- `colors.xml:182-191`: `focus_indicator` / `focus_button_stroke` / `item_focused` = hardcoded hex (`#FF1976D2`, `#FFBBDEFB`). NOT tied to `?attr/colorPrimary`. No `values-night/colors.xml` override. => stays fixed blue across all 6 accent themes (`theme_dark/light_green/blue/red`) and day/night. **Owner wants accent color** - overlay must resolve a theme attr, not this token.

## High-severity runtime-overwrite bugs (overlay sidesteps these visually)

- `MediaFileAdapter.kt:854` `ListViewHolder.applySelectionVisual` unconditionally `setBackgroundColor()` -> kills `item_focus_selector` for every list row once bound.
- `MediaFileAdapter.kt:667-668` `applyInlineHighlight()` sets `root.foreground = null` for non-active audio rows -> removes focus stroke.
- `PagingMediaFileAdapter.kt:198-204` same pattern.
- A window-level overlay draws OUTSIDE the item, tracking `View` bounds -> immune to these overwrites.

## Player (special case)

- D-pad reaches `PlayerActivity.dispatchKeyEvent` -> `PlayerInputDispatcher` -> `PlayerKeyboardHandler` -> `KeyBindingManager.resolve(.., PLAYER)`.
- `assets/input/default_bindings.json`: `DPAD_LEFT/RIGHT` -> `navigation.previous_file`/`next_file`; `DPAD_UP/DOWN` -> `audio.volume_up`/`down`. All consumed (`return true`) -> event never reaches `FocusFinder`/`requestFocus` traversal.
- `PlayerActivity` sets `shouldGuardContainerFocus()=false`, `shouldHandleGamepadNavigation()=false` -> opts out of BaseActivity focus movement.
- => Focus never moves among player controls via D-pad; only the initial-focus target ever holds focus.
- `PlayerActivity.getInitialFocusView()` returns `binding.btnPlayPause`, inside `controlsOverlay` (`activity_player_unified.xml:224`, `visibility=gone` by default). So initial `requestFocus()` is often a silent no-op (target GONE). Sibling `StandalonePlayerActivity.getInitialFocusView()` (`:237-248`, S0289) already fixed this bug class by falling back to always-visible `btnBack`; `PlayerActivity` never updated. But `PlayerActivity.topCommandPanel` is ALSO gone-by-default (gated same state), so needs a different always-visible anchor.
- `custom_player_controls*.xml` transport buttons: only a subtle icon-tint swap on focus (`selector_player_overlay_button_tint.xml`, red `#FF3333`), no stroke/background. `custom_player_controls_large.xml` missing `nextFocus*` attrs present in the small variant.

## Other findings

- `BrowseStateManager.getCurrentFocusPosition()` (`:27-35`) does NOT read View focus - returns `findFirstVisibleItemPosition()` (list) or hardcoded `0` (grid). => keyboard delete/copy/move/rename can act on the WRONG item once D-pad focus moved. Overlay showing true focus will EXPOSE this mismatch. (candidate follow-up ticket)
- No tests for `MediaFileAdapter`, `PagingMediaFileAdapter`, `KeyboardNavigationManager` (browse), `PlayerKeyboardHandler`, `FocusRingHelper`, `BrowseStateManager`, settings-row widgets, `ListSelectionDialog`.

## /spec-draft candidates (out of scope for S0819)

- `ListSelectionDialog` cancel button uses `Widget.Material3.Button.TextButton` not mandated `Widget.FastMediaSorter.Button.DialogCancel` (`dialog_list_selection.xml:59-64`). Policy deviation. Dedup before drafting.
- `FocusManager.moveFocus()` dead sub-expression `focusHighlightEnabled && false` (`FocusManager.kt:159`). Readability nit; fold in if touching FocusManager.
- Player D-pad focus traversal redesign (researcher Q2/Q3) - product decision, separable.
- `BrowseStateManager.getCurrentFocusPosition()` wrong-item bug - functional, separable.

## Confirmed wiring facts (2026-07-01, follow-up)

- **Accent attr = `?attr/colorPrimary`**: each of the 6 theme overlays in `themes.xml` overrides `colorPrimary` (`theme_dark/light_green/blue/red_primary`). Overlay frame resolves `?attr/colorPrimary` for the per-theme accent - NOT the hardcoded `@color/focus_button_stroke` (fixed blue). Owner wants accent color -> use `?attr/colorPrimary` (or a new `?attr/focusFrameColor` defaulting to it per theme).
- **No `BaseDialogFragment`/`BaseBottomSheet`/`BaseFragment`** exists (glob empty). DialogFragments/BottomSheets create their own `Window` -> an Activity-decor `OnGlobalFocusChangeListener` will NOT see focus inside a separate dialog window. Dialog/bottom-sheet coverage needs its own attach path.
- **App-wide attach candidate**: `Application.registerActivityLifecycleCallbacks(onActivityCreated -> attach overlay to window decor)` covers ALL 26 BaseActivity subclasses AND the direct-`AppCompatActivity` ones (widgets/consent/share/dispatch) with ZERO per-activity edits and no touch to the 583-LOC `BaseActivity`. Preferred over editing BaseActivity. Dialogs still need a separate helper (e.g. attach on `DialogFragment.onStart` via a shared extension, or a `FragmentManager.FragmentLifecycleCallbacks`).
- `focus_button_background.xml` (S0289) = 2dp stroke selector using hardcoded `@color/focus_button_stroke`. Existing per-view focus visuals; overlay is additive/app-wide and should avoid double-drawing (e.g. suppress `FocusRingHelper` ring where overlay active).

## Open questions for spec author (researcher)

1. Standardize on ONE mechanism app-wide and retire `FocusManager`/`FocusRingHelper`, or keep both?
2. Player: keep D-pad = transport (only fix initial-focus target), or give D-pad focus-traversal among visible transport buttons (redesign)?
3. Reliable always-visible initial-focus fallback for `PlayerActivity` (both overlays gone-by-default)?
4. Rewrite `BrowseStateManager.getCurrentFocusPosition()` to query real View focus?
5. Per-theme accent focus color vs fixed high-contrast blue? (Owner answered: accent per theme.)
