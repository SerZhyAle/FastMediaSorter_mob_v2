<sub class="doc-stamp">26.11.14 21:30</sub>

# Phone size-profile record

The size-profile record of the `app_v2` module (phone and launcher), as `UI-SIZING` 1.2 rule 4 and section 3 ask of
every product. Written from the source on 2026-11-14 (S4625). The watch has its own record,
`docs/ui/WEAR_SIZE_PROFILE.md`.

## Profile

- Input profile: touch, fixed at opening on every surface. Keyboard, D-pad and mouse operate the same touch-sized
  targets; no surface switches to a pointer profile, so no target moves under an active interaction.
- Ordinary target floor: 48 by 48 dp (`touch_target_min_height`), the `UI-SIZING` touch baseline.
- Row action buttons in ordinary mode: the browse list row buttons (`MediaFileAdapter`, overflow included) and the
  resource card buttons (`ResourceAdapter`, overflow and the five inline actions) are 48 by 48 dp, around a glyph that
  keeps the size it had. The quick-slideshow resource icon keeps its drawn size and gets a 48 dp hit area from a
  touch delegate on its parent.
- Compact mode: the "Compact elements" setting (`use_compact_elements`, `AppSettings.useCompactElements`), off by
  default. It is an explicit user choice and a permanent variant under `UI-SIZING` section 3.
- Interface scale: one scale for every covered component, the system display size and font scale. Text is in sp and
  grows independently; a row whose height is a dp value takes the larger of that value and the text line height, so
  large text is never shrunk back to fit.

## Declared compact floor

- Smallest supported target: **24 by 24 dp**, in compact mode only. No compact target is drawn smaller.
- Compact sizes in use, all at or above the floor:
  - 24 dp: browse list action buttons (`MediaFileAdapter`), resource icons (`ResourceAdapter`), the portrait settings
    title row (`toolbar_row_height_compact`).
  - Resource card buttons keep their declared `item_icon_size_small` in compact mode (24 dp on a phone, larger on a
    tablet).
  - 28 to 44 dp: player control buttons (`control_button_size_compact`, by smallest-width bucket).
  - 28 dp: compact dialog action buttons (`dialog_action_button_min_height_compact`).
  - 40 dp floor: the landscape settings toolbar content row (`settings_tabs_height_compact`, 65% of the action bar).
- The 20 dp list placeholder is a drawing inside a row, not a target; the row around it is the target.
- Compactness keeps every target focusable and labelled, so keyboard and screen-reader access is unchanged.

## Profile change - applied at the next opening

Turning "Compact elements" on or off asks for an app restart: the player theme overlay chooses its controls layout at
inflate time (`PlayerLayoutModePrefs`, `GeneralSettingsViewSetupHelper`). The new profile is applied at the next
opening of every surface, which is the "selected at opening, or through an explicit user choice" form of `UI-SIZING`
rule 2.1; no surface changes size while it is in use.

## Open deviations

Carried by S4619 (migration of the shared UI contracts, deadline 2026-12-31).

1. The declared compact floor and the ordinary floor are measured on a real build only at the release: the
   `UI-SIZING` section 4 evidence for this record is pending (S4625).
