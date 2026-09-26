# Pointer - `UPDATE-MANIFEST`

| | |
| --- | --- |
| **Id** | `UPDATE-MANIFEST` |
| **Version** | 0.9, draft. Owner: sza.od.ua hub |
| **Home** | `app-update-feed/README.md` in the shared contracts catalog |
| **Role here** | consumer (update check endpoint and release metadata discovery) |

## What this repository must do to stay conformant

- Query manifest at `https://sza.od.ua/updates/<product-id>.json`.
- Forward-tolerant parsing: reject higher `schemaVersion` cleanly, ignore unrecognized channel and package properties.
- Silent degradation on network timeout or HTTP failure - never disrupt startup or block navigation.
- Verify package SHA-256 before initiating local APK install.

## Where it lives here

- Documentation and download portals (`docs/DOWNLOADS*.md`, `docs/INSTALL_TRUST*.md`).
- Release scripts (`scripts/release/`).
