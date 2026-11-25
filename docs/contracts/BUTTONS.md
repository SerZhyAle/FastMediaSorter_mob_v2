<sub class="doc-stamp">26.11.20 20:17</sub>

# Pointer - `BUTTONS`

| | |
| --- | --- |
| **Id** | `BUTTONS` |
| **Version** | 2.3, active. Owner: shared |
| **Home** | `buttons/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher, watch and the help pages |

## What this repository must do to stay conformant

- Command buttons size to their content and keep the contract's order and roles; a confirm/cancel pair is not stretched to equal widths.
- Watch app (2.2, section 10 item A): buttons use the toolkit's own chip and button components, whose pill ends,
  round-display edge scaling and palette roles are a permanent variant recorded in the size-profile record of
  `UI-SIZING`; every other rule binds the watch unchanged, and a button the product draws itself keeps section 3
  rule 2 (no pill ends). The variant and the product-drawn buttons are listed in `docs/ui/WEAR_SIZE_PROFILE.md`.
- Since 2.3 (section 11 items F and G) the text reads a Windows surface and a floating Windows command panel; neither
  binds the phone, the launcher, the watch or the help pages.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4619.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- The dialog action styles `Widget.FastMediaSorter.Button.Dialog*` (`docs/ARCHITECTURE.md` "Button Taxonomy").
