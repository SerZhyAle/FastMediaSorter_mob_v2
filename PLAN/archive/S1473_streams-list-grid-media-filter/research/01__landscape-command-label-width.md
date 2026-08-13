# S1473 research 01 - landscape labels on the streams command row

**Question (strategic §6.1):** can the visible toolbar commands show text labels in landscape, and does the label fit next to the relocated search group?

**Verdict:** yes, and the mechanism is a runtime flag, not a resource variant. One non-obvious trap must be handled or the feature works on launch and silently dies on rotation.

---

## Evidence

Disassembled `androidx.appcompat.view.menu.ActionMenuItemView` from `appcompat-1.7.1.aar`
(`javap -c -p`, gradle cache copy). Two facts decide the design.

**1. The framework's own text gate is satisfied by landscape alone.**

`private boolean shouldAllowTextWithIcon()` reads `Resources.getConfiguration()` and returns true when any of:

- `screenWidthDp >= 480`
- `screenWidthDp >= 640` and `screenHeightDp >= 480`
- `orientation == ORIENTATION_LANDSCAPE`

The third clause has no width condition, so in landscape the gate is open on every supported device,
including the narrowest `legacy` hardware. No custom width measurement is needed.

**2. The gate is evaluated once, in the item view's constructor.**

The disassembled constructor computes `shouldAllowTextWithIcon()` and stores it into the field
`mAllowTextWithIcon` at construction time; nothing re-reads the configuration afterwards. The streams
window declares orientation config changes as self-handled, so it does not recreate on rotation and the
existing `ActionMenuItemView` instances survive with the value they were born with. An item view
constructed in portrait therefore keeps `mAllowTextWithIcon = false` permanently, and setting
`SHOW_AS_ACTION_WITH_TEXT` on it after a rotation changes nothing on screen.

## Consequence for the plan

- Request the label with `MenuItemCompat.setShowAsAction(item, SHOW_AS_ACTION_ALWAYS or SHOW_AS_ACTION_WITH_TEXT)`
  in landscape and plain `SHOW_AS_ACTION_ALWAYS` in portrait.
- On every orientation change, clear and re-inflate the toolbar menu so the item views are reconstructed
  under the new configuration. Without this the labels appear only when the screen is opened already
  horizontal.
- Re-apply the two existing post-inflate fixups after every re-inflate: the icon tint pass and the
  list/grid toggle's icon-and-title swap. Both currently run once during setup and would otherwise be
  lost with the discarded views.

## Residual risk - width, not visibility

`SHOW_AS_ACTION_ALWAYS` keeps an item out of the overflow unconditionally, so a label that does not fit
is squeezed rather than hidden. The visible set is deliberately small after the command-row
consolidation - list/grid toggle plus refresh - and the search group next to it uses a fixed width in
landscape. Crowding on the narrowest supported landscape configuration stays a device-test item; the
failure mode is cosmetic and the fallback is today's icon-only appearance.

## Status

Resolved. Feeds strategic §5.1 pillar B and §6 item 1.
