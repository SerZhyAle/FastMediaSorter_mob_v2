# 11 - Accessibility and Input Modes

Strategic item: S0395 §6.11. Phase: 04, step 04.4.

## Question

What do the new form pages need for TalkBack, D-pad/TV and mouse, and what are the landscape obligations?

## Sources

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` key handling (S0289 slider, via Phase-01/02 reports)
- `app_v2/src/main/res/layout/page_welcome*.xml` + `layout-land/` counterparts, `activity_welcome.xml` qualifier set
- `research/03__page1-device-profiles.md` (row-edge flip hazard), `research/01__current-flow-inventory.md` (rotation/recreate facts, missing land variants)
- CLAUDE.md Rules 11, 16, 17; strategic §3.2 (non-colour state indication)

## Findings

### D-pad / TV / mouse

- WelcomeActivity owns ALL navigation keys in `dispatchKeyEvent` (S0289 edge-aware slider): UP/DOWN traverse focusables inside the current page via FocusFinder (falling to the bottom bar), LEFT/RIGHT find a horizontal neighbour and FLIP THE PAGE at the scope's horizontal edge, ENTER activates, TAB cycles. Mouse wheel is routed to the pager; clicks work as touch.
- New focusable controls inside a page participate automatically (existing language group proves it). Two gaps for form pages: (a) grids - LEFT/RIGHT at a row edge flips the page accidentally (artifact 03); the page-level focus scope needs an explicit "consume at row edge unless on outermost column intent" rule or `nextFocus*` chains per row; (b) toggles - switch rows must be reachable in a predictable top-to-bottom order (`nextFocusDown` chains or focus-container ordering).
- TV form factor uses the same key path (TV_MEDIA_BOX detection exists); no separate TV layout - sw-qualifier integers govern density.

### TalkBack

- Toggle rows: use widgets with native switch semantics (`SwitchMaterial`/`MaterialSwitch` announce on/off; checked state changes announced) plus a one-line content description carrying the toggle's meaning AND its availability reason when disabled ("OCR - unavailable on this device"). Never bake state into the visual only.
- Profile tiles: existing tile already sets `contentDescription` from the label (feature-grid precedent); selected/recommended must be part of the announced state (`stateDescription` or appended text), not only a stroke colour.
- Download progress chips: announce via live-region politely; completion/failure announced once, not spammed.
- Non-colour indication (strategic §3.2): checked state = Material checked styles (fill+icon, not hue alone); recommended badge = text badge (already text today); selected tile = stroke + icon, stroke alone is colour-adjacent - add a check glyph.

### Landscape and safe bounds

- Every page layout has a `layout-land` counterpart today; every NEW page must ship both in lockstep (Rule 11). Known debt to absorb: `fragment_permissions_management.xml` has NO landscape variant - converting permissions into a page (recommended order) is the moment to create the land layout.
- Rotation caveat (artifact 01): `configChanges` means layouts do NOT swap on rotation mid-flow - land variants apply at launch orientation only. Form pages must therefore remain USABLE (not just pretty) in the "wrong" orientation: ScrollView-based bodies absorb this; fixed grids must not clip.
- Rule 17: pages already render inside the activity's safe insets; new pages inherit; the bottom nav bar is the only chrome - keep form controls out of the gesture/cutout zones via existing paddings.
- Forced light mode (artifact 02) is an a11y wash: high-contrast pastel palette in both modes; dark-theme users get a bright first screen - acceptable, documented.

## Options

- Row-edge D-pad handling: consume LEFT/RIGHT inside grids (page flip only from page edges/bottom bar) vs keep flip-at-edge (status quo, accident-prone on grids). Consuming inside grids is the safer default; the bottom bar and first/last column remain flip points.
- Switch widget: `MaterialSwitch` rows (settings parity) vs checkable cards (tile parity) - switches announce state natively, cards need manual `stateDescription`; switches recommended for toggle pages.

## Conclusion

The existing S0289 key slider gives new form pages D-pad/TV/mouse support nearly for free; the two real work items are grid row-edge key consumption and deterministic `nextFocus*` ordering on toggle rows. TalkBack needs native switch semantics, `stateDescription` for tiles, availability reasons in descriptions, and a polite live-region for download progress. Every new page ships portrait+landscape in lockstep, and the permissions page conversion must create the currently-missing landscape layout. These form the accessibility acceptance checklist for every page dev ticket.

## Impact on recommendation

- Acceptance checklist (inherited by every page ticket): native switch semantics; stateDescription on tiles; availability reason in disabled-control descriptions; live-region progress; grid row-edge key consumption; nextFocus chains; portrait+land lockstep; safe-inset compliance; zero colour-only state.
- Dev-ticket split: checklist is a shared section in the skeleton ticket, referenced by page tickets; permissions-page ticket explicitly includes the new landscape layout.
