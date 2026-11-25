<sub class="doc-stamp">26.11.24 21:52</sub>

# Pointer - `PRODUCT-IDENTITY`

| | |
| --- | --- |
| **Id** | `PRODUCT-IDENTITY` |
| **Version** | 0.7, draft. Owner: shared |
| **Home** | `product-identity/README.md` in the shared contracts catalog |
| **Role here** | consumer - the phone app and the watch app, their listing sources (Play, and Meta for the `vr` flavor), the site, the README and the in-app help |

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
- Since 0.5 (additive, 2026-11-23): the surface id `app.notification` for the notification small icon of Android and
  Wear OS (rule 2). The manifest lists the phone icon and the watch copy as two rows under `app.notification`
  (rule 36); the generator writes them, and the catalog's reference checker answers `PASS`.
- Since 0.6 (rule 10 amended, 2026-11-23): the hub's card follows the product's current `hub.card` rendition as the
  repository lists it, released or not, copied byte for byte with its SHA-256 recorded (rule 9). Store listing images
  still reach the store only with a release. The `hub.card` rendition and its hash are unchanged here, so nothing is owed.
- Since 0.7 (additive, 2026-11-24): the reference checker's `-Sheet` page draws an Android drawable XML - a `<vector>`
  as inline SVG, an `<adaptive-icon>` over its background plate - and names a cell it cannot draw. The verdict and the
  manifest schema do not change, so nothing is owed here; the XML rows of `assets/identity/identity-manifest.json`
  are drawn on the page, and a `-Sheet` run is the one place to look at them side by side (rule 3).
