# Pointer - `DOC-INTERNAL-QUALITY`

| | |
| --- | --- |
| **Id** | `DOC-INTERNAL-QUALITY` |
| **Version** | 0.9, draft; wire carrier: file tree layout and check invariants. Owner: this product |
| **Home** | `documentation-quality/README.md` section 2, in the shared contracts catalog |
| **Role here** | owner and reference implementation |

## What this repository must do to stay conformant

- Maintain a machine-readable document registry (`docs/DOCUMENT_REGISTRY.jsonl`) with product areas, change triggers, and source/render roles.
- Enforce reverse coverage: all maintained markdown documentation must be registered or listed as an explicit exclusion.
- Keep cross-links and anchors valid across all documentation files.
- Synchronize derived documentation (settings reference, script cheatsheets, icon legend) with code changes.
- Comply with house text style: plain hyphens, no `...`, proper language diacritics (`ё`/`Ё`), and English-only code comments.
- Keep image and screenshot bookmarks intact without dead references.
- Preserve offline safety and avoid unapproved third-party scripts or CDNs.

## Where it lives here

- Registry CLI: `scripts/document_registry/` (`query.ps1`, `validate.ps1`, `generate.ps1`).
- Enforced by `scripts/quality/assert-docs-crosslinks.ps1`, `scripts/quality/assert-neuroslop.ps1`, `scripts/quality/assert-settings-doc-sync.ps1`, `scripts/quality/assert-script-cheatsheet-sync.ps1`, `scripts/quality/assert-docs-screenshots.ps1`, `scripts/quality/assert-docs-external-content.ps1`.
