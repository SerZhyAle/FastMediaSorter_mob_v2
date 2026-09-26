# Pointer - `ICON-RENDER`

| | |
| --- | --- |
| **Id** | `ICON-RENDER` |
| **Version** | 0.11, draft. Owner: this product |
| **Home** | `iconography/README.md` sections 3 and 10 in the shared contracts catalog |
| **Role here** | owner and reference implementation - phone, launcher, watch, documentation and the website |

## What this repository must do to stay conformant

- A glyph takes its colour from the theme role its record names; never a baked white or black.
- Every glyph stays legible on light, dark and all six accent themes (3 : 1 against its surface).
- A toggle shows its state, a live transport control shows its action.
- Direction glyphs mirror in right-to-left layouts; media transport never does.
- One drawing at every size tier (16, 20, 24, 32, 40, 48); a plate changes the scale, never the shape.
- A glyph-only control carries its meaning's canonical name as its accessible name.
- Every edition's launcher icon is adaptive and has a monochrome layer.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Measured style: `scripts/quality/assert-icon-style.ps1` and its exceptions file.
- Accessible names: the `accessible-name` dimension of `scripts/quality/assert-icon-contract.ps1`.
- Contrast on every theme: the contrast report of `scripts/docs/export-icon-contract.ps1`.
- Looks and palette: `docs/icons/README.md`.
