# Documentation Search & PDF Generation Tooling Spike (S2970 / S2971 Preparation)

This evaluation spike investigates search indexing options (for ticket S2970) and static/offline PDF compilation options (for ticket S2971) based on FastMediaSorter v2's full-width documentation architecture.

---

## 1. Search Engine Evaluation (S2970 Candidate Analysis)

The documentation corpus will span ~75 detailed recipe pages with localized translations.

| Tool | Architecture | Pros | Cons | Recommendation |
|------|--------------|------|------|----------------|
| **Pagefind** | Rust-based static index generator with client-side chunked search | Zero runtime dependencies, builds static chunked index (<50KB initial payload), multlingual support, exact element ranking (`data-pagefind-body`) | Requires Node/binary during build step | **Recommended primary engine for S2970** |
| **Fuse.js** | Client-side fuzzy search JSON dictionary | Lightweight, runs 100% in-browser without build tools | Loads full corpus JSON into memory on client; poor stemming for non-Latin languages | Candidate for lightweight offline/in-app webview search |
| **Lunr.js** | Pre-built inverted index for JavaScript | Established, simple schema | Inflexible language stemming, heavier index download sizes | Refuted (Pagefind is strictly superior for static sites) |

### Search Integration Architecture for S2970
- Wrap all recipe content in `<main class="doc-content" data-pagefind-body>`.
- Exclude navigation and chrome using `data-pagefind-ignore`.
- Index metadata via `<meta data-pagefind-meta="category">`.

---

## 2. PDF & Printable Documentation Evaluation (S2971 Candidate Analysis)

The project requires a downloadable, beautifully formatted offline PDF cookbook of the complete documentation.

| Tool | Approach | Pros | Cons | Recommendation |
|------|----------|------|------|----------------|
| **Headless Chrome / Playwright (`@media print` CSS)** | Direct HTML to PDF print export | Exact 1:1 fidelity with site CSS, preserves custom fonts (`Outfit`, `Plus Jakarta Sans`), canvas wave headers, and code block styling | Requires Chromium during release build | **Recommended primary engine for S2971** |
| **Pandoc + Weasyprint / Typst** | Markdown to PDF engine | Semantic page breaks, customizable LaTeX/Typst templates | Diverges from web CSS design system; requires maintaining duplicate styling | Alternative for monochrome text-heavy printouts |
| **Asciidoctor PDF** | AsciiDoc toolchain | Robust book structure | Requires converting HTML/Markdown corpus to AsciiDoc format | Refuted due to syntax friction |

### PDF Print Architecture for S2971
- Add `@media print` rules to `documentation/assets/docs.css` (hiding theme toggles, sidebar, language picker, and expanding content to full width with print page breaks).
- Run an automated headless print script during release preparation (not written yet - S2971 decides its name and where it lives).
