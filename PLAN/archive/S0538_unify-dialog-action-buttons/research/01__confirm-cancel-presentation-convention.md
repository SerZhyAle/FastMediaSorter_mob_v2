# Research 01 - Confirm/Cancel presentation convention

Bound to §6 item 1. Read-only investigation; informs §3.3 owner sign-off and §5.1 pillar A.

## Existing project taxonomy (authoritative base)

`docs/ARCHITECTURE.md` -> "Button Taxonomy (MANDATORY)" + `app_v2/src/main/res/values/themes.xml` lines 104-127 define one named Material3 style per semantic role:

- `Widget.FastMediaSorter.Button.Filled` - Primary / confirm (Save, OK, Grant). At most one per surface.
- `Widget.FastMediaSorter.Button.Tonal` - secondary emphasis.
- `Widget.FastMediaSorter.Button.Outlined` - neutral secondary paired with a Filled primary.
- `Widget.FastMediaSorter.Button.Text` - low-emphasis / cancel / dismiss.
- `Widget.FastMediaSorter.Button.Icon` - icon-only.

Rules already mandated: pick by role; colors come from `?attr/color*` / `@color/`, never hardcoded hex on a button (Rule 19); preserve >=48dp touch target and D-pad/TV focus (Rule 16); a new role is added as a new `Widget.FastMediaSorter.Button.*` style, not an ad-hoc layout style.

Conclusion: the unification must extend this family, not introduce a parallel style system (ADR-2).

## Most-compliant existing call-sites (the template)

- `dialog_scheduled_operation.xml` and `dialog_translation_settings.xml`: `btnCancel style=Widget.FastMediaSorter.Button.Text` + confirm `style=Widget.FastMediaSorter.Button.Filled`.
- `dialog_folder_browser.xml`: Filled (Select) + Outlined (Cancel).
- `dialog_delete.xml`: `btnDelete backgroundTint=@color/delete_button` (red) + `btnCancel` text button - the destructive pattern already exists.

The owner's complaint is that the default sizing/emphasis of this pair is too small and the confirm does not stand out next to the cancel - i.e. the taxonomy is right but under-emphasized for finger/driving use.

## Visual target referenced by owner

`exo_next_file` in `custom_player_controls.xml` (~line 113): a 96dp-wide `ImageButton`, `src=ic_next_circle_selector` -> `ic_next_circle_red.xml` (solid `#FF0000` fill, white glyph). It is an ImageButton, not a MaterialButton - coloring comes from the drawable fill. It is explicitly exempt from the button taxonomy (media/icon controls). So the goal is to reproduce the *unmissable* feel (size + memorable color), not the round icon form.

## Color tokens already in the theme

- `?attr/colorPrimary` -> `@color/blue_500` (light `#1976D2`, dark `#64B5F6`).
- `@color/delete_button` -> light `#D32F2F`, dark `#EF5350` (red; already used for destructive confirm).
- `@color/success_color` -> light `#2E7D32`, dark `#81C784` (green; currently unused in dialogs).
- No custom `alertDialogButtonBarButtonStyle` / `dialogButtonBarStyle` override in `values/themes.xml` or `values-night/themes.xml`.

## Recommendation (pending owner sign-off, §3.3)

- Confirm (OK): high-emphasis Filled, memorable color = saturated green `success_color` (semantic "go/confirm"), since red is reserved for destructive and primary blue collides with other on-screen primaries.
- Cancel: neutral lower-emphasis (Outlined or Text) - clearly different color and weight, never adjacent same-size text.
- Destructive confirm (delete): red `delete_button` in the confirm slot.
- Min height ~56dp (matches existing `destination_button_min_height` dimen) + explicit horizontal gap between the pair.
- Non-color differentiation preserved: emphasis (filled vs outlined), position, label - required for colorblind / TalkBack.

Final palette and exact min-size are an owner aesthetic decision -> §3.3 Owner sign-off.
