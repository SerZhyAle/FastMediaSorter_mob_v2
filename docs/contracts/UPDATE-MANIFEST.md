<sub class="doc-stamp">26.10.02 12:24</sub>

# Pointer - `UPDATE-MANIFEST`

| | |
| --- | --- |
| **Id** | `UPDATE-MANIFEST` |
| **Version** | 0.10, draft. Owner: sza.od.ua hub |
| **Home** | `app-update-feed/README.md` in the shared contracts catalog |
| **Role here** | declared consumer; no feed client ships here yet (section 1) |

## What this repository must do to stay conformant

- Nothing in this repository reads the feed today; the rules below bind the first client.
- Query manifest at `https://sza.od.ua/updates/<product-id>.json`.
- `channels.<channel>.version` is authoritative; `latestVersion` only mirrors `stable` (rule 2).
- An install managed by a package manager or a store is left to it (section 5).
- Forward-tolerant parsing: reject higher `schemaVersion` cleanly, ignore unrecognized channel and package properties.
- Silent degradation on network timeout or HTTP failure - never disrupt startup or block navigation.
- Verify package SHA-256 before initiating local APK install.

## Where it lives here

- Documentation and download portals (`docs/DOWNLOADS*.md`, `docs/INSTALL_TRUST*.md`).
- Release scripts (`scripts/release/`).
