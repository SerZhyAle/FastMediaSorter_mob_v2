<sub class="doc-stamp">26.10.18 17:47</sub>

# Pointer - `BUTTONS`

| | |
| --- | --- |
| **Id** | `BUTTONS` |
| **Version** | 2.0, active. Owner: shared |
| **Home** | `buttons/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher, watch and the help pages |

## What this repository must do to stay conformant

- Command buttons size to their content and keep the contract's order and roles; a confirm/cancel pair is not stretched to equal widths.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4352.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- The dialog action styles `Widget.FastMediaSorter.Button.Dialog*` (`docs/ARCHITECTURE.md` "Button Taxonomy").
