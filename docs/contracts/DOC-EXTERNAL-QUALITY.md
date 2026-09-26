# Pointer - `DOC-EXTERNAL-QUALITY`

| | |
| --- | --- |
| **Id** | `DOC-EXTERNAL-QUALITY` |
| **Version** | 0.9, draft; wire carrier: documentation corpus and web rendering structure. Owner: this product |
| **Home** | `documentation-quality/README.md` section 3, in the shared contracts catalog |
| **Role here** | owner and reference implementation |

## What this repository must do to stay conformant

- Maintain task-oriented external documentation corpus (105 recipes, portal, subject index, glossary).
- Mirror primary markdown content into web HTML and PDF with automated search index and `sitemap.xml` generation.
- Track localization synchronization and translation freshness mechanically (source fingerprints); maintain parallel page sets across active locales.
- Enforce terminology and termbase consistency through glossary validation.
- Provide complete SEO and discoverability instrumentation on every public page (canonical URLs, meta descriptions, JSON-LD, hreflang).
- Verify screenshot fidelity and match UI captures to locale and current theme.
- Ensure link integrity and web hygiene across all published HTML pages.

## Where it lives here

- Corpus and web pages: `docs/content/`, `documentation/`, `sitemap.xml`.
- Generation scripts: `scripts/docs/generate-docs-pages.ps1`, `scripts/docs/generate-docs-search-index.ps1`, `scripts/docs/generate-glossary.ps1`, `scripts/docs/generate-subject-index.ps1`, `scripts/docs/build-docs-pdf.ps1`.
- Enforced by `scripts/quality/assert-docs-coverage.ps1`, `scripts/quality/assert-docs-translation-freshness.ps1`, `scripts/quality/assert-docs-termbase.ps1`, `scripts/quality/assert-localized-page-set.ps1`, `scripts/quality/assert-docs-search.ps1`.
