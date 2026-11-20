<sub class="doc-stamp">26.11.20 20:21</sub>

# Pointer - `PRODUCT-IDENTITY`

| | |
| --- | --- |
| **Id** | `PRODUCT-IDENTITY` |
| **Version** | 0.4, draft. Owner: shared |
| **Home** | `product-identity/README.md` in the shared contracts catalog |
| **Role here** | consumer - the phone app, its listing sources, the site, the README and the in-app help |

## What this repository must do to stay conformant

- One master of the product mark lives in `assets/identity/master/` (rule 1): `mark.svg`, `mark-dark.svg` and the flat
  `mark-mono.svg`. It is composed from the `fastmediasorter` family master, which the catalog holds once and
  `scripts/identity/sync-family-mark.ps1` copies byte for byte into `assets/identity/family/` (section 3.3, rules 17 to
  32); the small and reduced forms sit under `assets/identity/family/masters/`.
- Every picture of the product is a rendition written by `pwsh -NoProfile -File scripts/identity/build-identity.ps1`
  and listed with its SHA-256 in `assets/identity/identity-manifest.json` (rule 2, section 5); a rendition is never
  edited by hand, and `-Check` proves a rerun changes no byte.
- The store icon is cut from the same master as the launcher layers (rule 4); listing images reach the store only with
  a release (rule 10).
- Every help page shows the `help.head` rendition beside its title, and an element entry shows the glyph of the
  control it names (rule 8), through `scripts/docs/help/generate-help-pages.ps1`.
- A diagram is kept as its source and generated (rules 12-15); any other image is recorded in the manifest as
  `other.<name>` with its source and licence (rule 16).
- Since 0.3 (section 3.4, rules 33 to 37) the manifest declares its master and renditions in one shape: `family.sha256`
  names the family master, `master.reduced[]` lists each reduced form with its size range, a rendition the build writes
  and the repository does not keep is `buildOnly` and still carries its `sha256`, a byte-identical copy in another
  folder is a row of its own, and `web.favicon-svg` is cut from the master or its small form, never the mono form.
  External: the catalog's reference checker `Test-ProductIdentity.ps1` answers `PASS` for
  `assets/identity/identity-manifest.json`.
- Since 0.4 (additive, 2026-11-20): the surface ids `app.installer-banner`, `app.installer-dialog` and
  `app.shell-entry`, the `bmp` rendition format and the optional `renderedFrom` field, with the checker's
  `-Release` switch; a manifest without them reads as before, and this product declares none of them.
