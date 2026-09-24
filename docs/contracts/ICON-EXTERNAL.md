# Pointer - `ICON-EXTERNAL`

| | |
| --- | --- |
| **Id** | `ICON-EXTERNAL` |
| **Version** | 0.9, draft. Owner: this product |
| **Home** | `iconography/README.md` section 4 in the shared contracts catalog |
| **Role here** | owner; phone, launcher and watch |

## What this repository must do to stay conformant

- A third-party mark is shown only as its owner's asset or published variant - never redrawn, never
  recoloured into a theme colour.
- Another installed app is shown by the icon the system reports for it, untinted; when that icon
  cannot be read, the `content.apps` glyph stands in.
- A downloaded picture - favicon, album art, thumbnail, contact photo - is content: it sits in the box
  of the glyph it replaces, scaled to fit, and is never used as an action glyph.
- A downloaded picture that is missing, not yet loaded, refused or broken falls back to a picture made
  from the item's own data, or to the glyph of what it stands for - never a blank box, never an error
  glyph. The fallback keeps the picture's place, size and accessible description.
- A new surface that shows a downloaded picture enters the surface inventory in the ticket that
  creates it.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Third-party brand assets and attributions (rule 1): `docs/legal/THIRD_PARTY_BRAND_ASSETS.md`.
- Surface inventory for rule 4: `docs/icons/external-picture-surfaces.md`.
- Other apps' icons (rule 2): `ShareTargetIconResolver` in `core/share/`, launcher gadgets in `ui/launcher/gadget/`.
- Stream favicons (rule 3): the favicon atlas of `STREAM-BANK`.

