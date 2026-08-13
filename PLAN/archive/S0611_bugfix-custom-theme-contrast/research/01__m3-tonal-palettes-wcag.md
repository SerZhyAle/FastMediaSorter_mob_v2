# S0611 research: M3 tonal palettes + WCAG contrast (locked values)

Source of truth for implementation. Every value below verified numerically by `temp/wcag_s0611.ps1`
(WCAG 2.1 relative-luminance contrast). All 66 text/graphical pairs PASS (text >= 4.5:1, graphical >= 3.0:1).

## Theme application + night forcing (from codebase research)

| Theme value | Overlay style | Forced night mode | Active color bucket |
| --- | --- | --- | --- |
| AUTO (default) | none | FOLLOW_SYSTEM | values/ or values-night/ per system |
| LIGHT | none | NIGHT_NO | values/ |
| DARK | none | NIGHT_YES | values-night/ |
| DARK_GREEN | ThemeOverlay.FastMediaSorter.DarkGreen | NIGHT_YES | values-night/ + overlay (values/themes.xml) |
| DARK_BLUE | ThemeOverlay.FastMediaSorter.DarkBlue | NIGHT_YES | same |
| DARK_RED | ThemeOverlay.FastMediaSorter.DarkRed | NIGHT_YES | same |
| LIGHT_GREEN | ThemeOverlay.FastMediaSorter.LightGreen | NIGHT_NO | values/ + overlay |
| LIGHT_BLUE | ThemeOverlay.FastMediaSorter.LightBlue | NIGHT_NO | same |
| LIGHT_RED | ThemeOverlay.FastMediaSorter.LightRed | NIGHT_NO | same |

Key consequences:
- Overlay styles live ONLY in `values/themes.xml` (no values-night/ variant). `applyStyle(resId, true)` applies that single definition regardless of night mode. So per-theme container roles must reference fixed-brightness `theme_*` color tokens in `values/colors.xml` (night-agnostic), exactly like the existing `theme_dark_green_surface` etc. Do NOT put per-theme container tokens in values-night/.
- The confirm-button fix is the exception: it is global (affects plain DARK + AUTO-night too), so its tokens DO get a day value in `values/colors.xml` and a night override in `values-night/colors.xml`.
- ColorThemePrefs.applyThemeOverlay() at BaseActivity.onCreate (after super, before setContentView). Theme value enum incl. AUTO/LIGHT/DARK + 6 custom.

## Confirm button (decoupled from success_color)

New tokens (replace `backgroundTint=@color/success_color` + `textColor=@color/white` in `Widget.FastMediaSorter.Button.DialogConfirm`):

- `confirm_button_bg`  : values/ `#FF2E7D32` (Green 800) | values-night/ `#FF81C784` (Green 300)
- `confirm_button_on`  : values/ `#FFFFFFFF` (white)      | values-night/ `#FF0A2E0A` (near-black green)

| Mode | text on bg | contrast |
| --- | --- | --- |
| Day (LIGHT, LIGHT_*, AUTO-day) | #FFFFFF on #2E7D32 | 5.13:1 |
| Night (DARK, DARK_*, AUTO-night) | #0A2E0A on #81C784 | 7.41:1 |

`success_color` itself is UNCHANGED. Its three remaining consumers stay as-is: color-picker preview
(`dialog_color_picker.xml` + land), ExtensionsManagerFragment status, ResourceEditorOutcomeRenderer
connection status (via `success_green` alias) - all text/preview roles, not filled-button backgrounds.

## Player toolbar fix (folded in - blocker for primary lightening)

`activity_player_unified.xml` toolbar sets `app:titleTextColor="@color/white"` (hardcoded). All other
toolbars use `?attr/colorOnPrimary`. After DARK_* primary is lightened, white title on a light primary
fails. Change that one attribute to `?attr/colorOnPrimary`. (No layout-land counterpart for this file.)

## Per-theme M3 tonal token set (fixed-brightness, in values/colors.xml)

Roles added per theme (overlay maps M3 attr -> token):
surfaceContainerLowest/Low/Container/High/Highest, surfaceVariant, onSurfaceVariant, outline, outlineVariant,
primaryContainer/onPrimaryContainer, secondaryContainer/onSecondaryContainer. DARK_* also re-point primary/onPrimary.
Existing tokens kept: background, surface, onSurface (and primaryVariant).

### DARK_GREEN (night)
- background `#0F1A0F` (keep) | surface `#152015` (keep) | onSurface `#E6E6E6` (keep)
- surfaceContainerLowest `#0B140B` | Low `#182418` | Container `#1C2B1C` | High `#263826` | Highest `#314A31`
- surfaceVariant `#1C2B1C` | onSurfaceVariant `#C2D0C2` | outline `#8A9C8A` | outlineVariant `#455045`
- primary `#81C784` | onPrimary `#0A2E0A` | primaryContainer `#2E5A30` | onPrimaryContainer `#C8E6C9`
- secondaryContainer `#33472F` | onSecondaryContainer `#D6E8C8`

### DARK_BLUE (night)
- background `#0F1420` (keep) | surface `#151B2A` (keep) | onSurface `#E6E6E6` (keep)
- surfaceContainerLowest `#0A1018` | Low `#172033` | Container `#1B2638` | High `#243551` | Highest `#2E4060`
- surfaceVariant `#1B2638` | onSurfaceVariant `#C0C8D6` | outline `#8893A0` | outlineVariant `#404A58`
- primary `#64B5F6` | onPrimary `#06243B` | primaryContainer `#1565C0` | onPrimaryContainer `#D6E8FF`
- secondaryContainer `#2A3F5A` | onSecondaryContainer `#CAD9EC`

### DARK_RED (night)
- background `#1A0F0F` (keep) | surface `#241515` (keep) | onSurface `#E6E6E6` (keep)
- surfaceContainerLowest `#160B0B` | Low `#261818` | Container `#2D1C1C` | High `#3F2626` | Highest `#4A3131`
- surfaceVariant `#2D1C1C` | onSurfaceVariant `#D6C2C2` | outline `#9C8A8A` | outlineVariant `#504545`
- primary `#EF9A9A` | onPrimary `#3B0A0A` | primaryContainer `#C62828` | onPrimaryContainer `#FFEAE7`
- secondaryContainer `#4A2F2F` | onSecondaryContainer `#ECCACA`

### LIGHT_GREEN (day)
- background `#F1F8E9` (keep) | surface `#FFFFFF` (keep) | onSurface `#1B1B1B` (keep)
- surfaceContainerLowest `#FFFFFF` | Low `#F2F7EC` | Container `#EDF4E5` | High `#EDF4E5` | Highest `#E6F0DC`
- surfaceVariant `#EDF4E5` | onSurfaceVariant `#42493E` | outline `#72796C` | outlineVariant `#C2C9BC`
- primary `#2E7D32` (keep) | onPrimary `#FFFFFF` (keep) | primaryContainer `#B8E6BA` | onPrimaryContainer `#0A2E0A`
- secondaryContainer `#D6E8C8` | onSecondaryContainer `#26331C`

### LIGHT_BLUE (day)
- background `#E3F2FD` (keep) | surface `#FFFFFF` (keep) | onSurface `#1B1B1B` (keep)
- surfaceContainerLowest `#FFFFFF` | Low `#EFF4FB` | Container `#E6EFF8` | High `#DCE8F4` | Highest `#D2E1F0`
- surfaceVariant `#E6EFF8` | onSurfaceVariant `#3E4650` | outline `#6C747F` | outlineVariant `#BFC7D2`
- primary `#1565C0` (keep) | onPrimary `#FFFFFF` (keep) | primaryContainer `#CFE3FB` | onPrimaryContainer `#06243B`
- secondaryContainer `#CAD9EC` | onSecondaryContainer `#1B2A3A`

### LIGHT_RED (day)
- background `#FFEBEE` (keep) | surface `#FFFFFF` (keep) | onSurface `#1B1B1B` (keep)
- surfaceContainerLowest `#FFFFFF` | Low `#FCF1F2` | Container `#F9E8E9` | High `#F9E8E9` | Highest `#F5DEDF`
- surfaceVariant `#F9E8E9` | onSurfaceVariant `#50403F` | outline `#7F6C6C` | outlineVariant `#D6C2C2`
- primary `#C62828` (keep) | onPrimary `#FFFFFF` (keep) | primaryContainer `#F8CFCF` | onPrimaryContainer `#3B0A0A`
- secondaryContainer `#ECCACA` | onSecondaryContainer `#3A1B1B`

## Contrast verification (excerpt - all PASS)

| Theme | onSurface/surfaceHigh (dialog text) | onSurfaceVariant/surfaceHigh (2nd text) | primary/surface (accent) | onPrimary/primary (toolbar) |
| --- | --- | --- | --- | --- |
| DARK_GREEN | 10.04 | 7.81 | 8.35 | 7.41 |
| DARK_BLUE | 9.88 | 7.32 | 7.76 | 7.17 |
| DARK_RED | 11.09 | 8.15 | 8.18 | 7.91 |
| LIGHT_GREEN | 15.32 | 8.28 | 5.13 | 5.13 |
| LIGHT_BLUE | 13.86 | 7.69 | 5.75 | 5.75 |
| LIGHT_RED | 14.56 | 8.27 | 5.62 | 5.62 |

Full 66-pair table: `temp/wcag_s0611.out.txt`. Recompute anytime via `pwsh -NoProfile -File temp/wcag_s0611.ps1`.

## outlineVariant note

`outlineVariant` is a low-emphasis divider role (no text). Values above are picked tonally between
surface and outline; not contrast-gated (decorative dividers). Kept for completeness so M3 dividers tint with the theme.

## M3 attr -> overlay item mapping (for themes.xml)

Each overlay adds (android namespace only where noted):
`colorSurfaceContainerLowest, colorSurfaceContainerLow, colorSurfaceContainer, colorSurfaceContainerHigh,
colorSurfaceContainerHighest, colorSurfaceVariant, colorOnSurfaceVariant, colorOutline, colorOutlineVariant,
colorPrimaryContainer, colorOnPrimaryContainer, colorSecondaryContainer, colorOnSecondaryContainer`.
DARK_* additionally override existing `colorPrimary` + `colorOnPrimary` to the lightened pair.
