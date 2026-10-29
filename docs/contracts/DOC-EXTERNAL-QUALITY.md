<sub class="doc-stamp">26.10.29 05:03</sub>

# Pointer - `DOC-EXTERNAL-QUALITY`

| | |
| --- | --- |
| **Id** | `DOC-EXTERNAL-QUALITY` |
| **Version** | 0.13, draft; wire carrier: documentation corpus and web rendering structure. Owner: this product |
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
- Keep each page of the media exchange portal as one set of three variants (English, Russian, Ukrainian), each written whole in its language; the English original is maintained until the release and the Russian and Ukrainian variants are rebuilt from it before it (section 9 items F, G).
- Publish the portal as its single publisher: each program owns the sections of its own functions, the common part is open to the authors of every program, and a delivered page is judged by our gates like our own (item H).
- Draw every scheme in one visual form from one renderer, one label file per language with the same keys, and show a picture only on a page of its own language (item I).

## Where it lives here

- Corpus and web pages: `docs/content/`, `documentation/`, `sitemap.xml`.
- The shared media exchange portal and its schemes: `docs/media-exchange-portal/`, drawn by `scripts/docs/render-usage-schemes.ps1` from `labels/labels.<lang>.json`; its gaps against section 9 are dated exceptions in the catalog's registry.
- Generation scripts: `scripts/docs/generate-docs-pages.ps1`, `scripts/docs/generate-docs-search-index.ps1`, `scripts/docs/generate-glossary.ps1`, `scripts/docs/generate-subject-index.ps1`, `scripts/docs/build-docs-pdf.ps1`.
- Enforced by `scripts/quality/assert-docs-coverage.ps1`, `scripts/quality/assert-docs-translation-freshness.ps1`, `scripts/quality/assert-docs-termbase.ps1`, `scripts/quality/assert-localized-page-set.ps1`, `scripts/quality/assert-docs-search.ps1`.
