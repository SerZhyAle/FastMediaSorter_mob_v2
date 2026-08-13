# Research: S0500 - Button inventory & unification baseline (app_v2)

**Date:** 2026-06-18
**Method:** catalog query (`query.ps1 -ClassMatches "*Button*"`) + Grep over `res/layout*` + read of `values/themes.xml`, `values/styles.xml`.
**Scope:** `app_v2` main + flavor source sets (noLegal overlay), portrait + landscape.

---

## 1. Widget inventory (XML layouts)

- `ImageButton` - dominant icon-button widget, ~295 instances across player / text-viewer / standalone / browse / list-item surfaces. Most use `?attr/selectableItemBackgroundBorderless` (intentional borderless media-surface look).
- Plain `<Button>` - scattered in dialogs / bottom sheets / welcome pages. Filled-default, `textAllCaps=true`, no Material ripple/shape.
- `com.google.android.material.button.MaterialButton` - the correct M3 widget; used inconsistently alongside plain `<Button>` for the same semantic role.
- `MaterialButtonToggleGroup` - welcome / filter surfaces (correct usage).
- `ExtendedFloatingActionButton` - `fragment_duplicates.xml` (has a hardcoded `"Delete Selected"` string - parked, see below).
- noLegal flavor overrides 3 list-item layouts, each containing `ImageButton` - must be edited in lockstep with `src/main`.

## 2. Style inventory (`themes.xml`)

- 7 project styles `Widget.FastMediaSorter.SettingsButton.*`, split across two Material generations:
  - `SettingsButton.Outlined` parent = `Widget.MaterialComponents.Button.OutlinedButton` (MC, `themes.xml:69`).
  - `SettingsButton.OutlinedM3` parent = `Widget.Material3.Button.OutlinedButton` (M3, `themes.xml:111`).
  - The MC vs M3 split yields different default corner radius / elevation / state-layer with no logical usage distinction.
- Co-exist with raw `Widget.MaterialComponents.*` and `Widget.Material3.*` references at the same semantic level; also `Widget.AppCompat.Button.Borderless` and `?android:attr/borderlessButtonStyle` (pre-M3).

## 3. The "zoo" - concrete inconsistency evidence

- Same semantic role uses different widget classes: `page_welcome_permissions.xml:70-85` mixes bare `<Button>` (primary) with `Button style=Widget.MaterialComponents.Button.TextButton` (secondary).
- `dialog_stream_offload_offer.xml:69-89` - three plain `<Button>` (no Material ripple/shape), one borderless.
- `bottom_sheet_permission_rationale.xml:44-64` - bare `<Button>` + `?android:attr/borderlessButtonStyle`, hardcoded "Grant"/"Not now" strings.
- Hardcoded hex in layouts (CLAUDE.md Rule 19 violation): `activity_camera_ocr_translate.xml` (dark-only, ~18 hex sites), `player_draw_overlay_toolbar_content.xml:57,68,79,90` (pseudo-button swatches), `activity_camera_capture.xml:46,52,78`, `item_destination_button.xml:10` (`@color/white` inline textColor).

## 4. Good patterns already present (templates)

- `dialog_delete.xml:31-45` - TextButton (cancel) + filled MaterialButton (destructive, `app:backgroundTint=@color/delete_button`).
- `dialog_filter.xml:26-56` - `?attr/materialIconButtonStyle` + theme-attr tints, no hex.
- `item_destination.xml:83-126` - `Widget.Material3.Button.IconButton`, zero-inset, `app:iconTint=?attr/colorError`.
- `page_welcome_enhanced.xml:88-182` - `MaterialButtonToggleGroup` + `Widget.Material3.Button.OutlinedButton`, `minHeight=48dp`, `focusable=true`.
- `activity_browse.xml` control bar - `?attr/materialIconButtonStyle` + `nextFocusDown` + focus drawable (correct D-pad/TV).

## 5. Recommended unified taxonomy (Material 3)

A minimal named-style family in `styles.xml`, all M3 parents:

- `Widget.FastMediaSorter.Button.Filled` (primary/confirm) - `Widget.Material3.Button`.
- `Widget.FastMediaSorter.Button.Tonal` (secondary emphasis) - `Widget.Material3.Button.TonalButton`.
- `Widget.FastMediaSorter.Button.Outlined` (secondary) - `Widget.Material3.Button.OutlinedButton`.
- `Widget.FastMediaSorter.Button.Text` (low-emphasis / cancel / links) - `Widget.Material3.Button.TextButton`.
- `Widget.FastMediaSorter.Button.Icon` (icon-only, where a Material icon button is wanted) - `Widget.Material3.Button.IconButton` / `?attr/materialIconButtonStyle`.

Replacement strategy: plain `<Button>` -> `MaterialButton` + one of the above; collapse the 7 `SettingsButton.*` styles into the new family; convert hardcoded hex to `?attr/`/`@color/`; keep landscape parity (Rule 11) in the same pass.

## 6. Design forks requiring owner decision (-> spec §6)

Conservative defaults proposed; owner confirms:

1. **Camera viewfinder surfaces** (`activity_camera_ocr_translate.xml`, `activity_camera_capture.xml`): intentional dark-on-dark. *Default: EXEMPT from the standard taxonomy* (separate themed pass if desired).
2. **295 player/media `ImageButton`** using `selectableItemBackgroundBorderless`: migrating to `materialIconButtonStyle` changes tap-target size / ripple on the highest-traffic screen. *Default: OUT OF SCOPE - leave ImageButtons as-is; unify only `Button`/`MaterialButton`.*
3. **ExoPlayer reserved-id controls** (`custom_player_controls*.xml`, `@id/exo_*`): library layout contract. *Default: leave as-is.*
4. **`SettingsButton.*` 7-style family**: *Default: consolidate into the new M3 family.*
5. **`?android:attr/borderlessButtonStyle` on plain `<Button>`** (2 dialogs + 1 bottom sheet): *Default: replace with `Widget.FastMediaSorter.Button.Text`.*
6. **Landscape parity** (settings layouts 847 + 975 lines, both have `layout-land/` twins): *Default: single batch pass per screen, portrait+land together.*
7. **`item_destination_button.xml` inline `@color/white`**: *Default: switch to `?attr/colorOnPrimary` (theme-safe).*

## 7. Scale & risk

- Affected layout files: dozens (settings, dialogs, welcome, bottom sheets, list items, standalone players + landscape twins + noLegal overlays).
- High-risk: player controls (focus/ripple regressions), standalone-activity id parity (managed by `DestinationButtonsManager` / `PlayerBigButtonsModeManager` - ids must not change), the two >800-line settings layouts (do not grow them).
- No layout/button UI tests exist; verification is manual on-device.

## /spec-draft candidates (out-of-scope, park separately)

1. Hardcoded `"Delete Selected"` string on `ExtendedFloatingActionButton` - `fragment_duplicates.xml:168` (localization break).
2. `activity_camera_ocr_translate.xml` monolithic dark-only layout (475 lines, all hex) - breaks on light theme; needs its own theming spec.
