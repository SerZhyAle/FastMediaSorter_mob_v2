<sub class="doc-stamp">26.11.20 20:03</sub>

# scripts/identity

The product mark and every rendition of it (contract `PRODUCT-IDENTITY`, family `fastmediasorter`).

- `sync-family-mark.ps1` - copies the family master folder from the contracts catalog into `assets/identity/family/`.
- `build-identity.ps1` - writes every rendition of the product mark and `assets/identity/identity-manifest.json`; `-Check` compares instead.
- `build_identity.py` - the generator `build-identity.ps1` runs: the surface rows and their renderers.
- `identity_family.py` - reads the family folder and composes the product master from it.
- `identity_lockup.py` - the wide lockup of the mark and the name in Manrope ExtraBold outlines.
