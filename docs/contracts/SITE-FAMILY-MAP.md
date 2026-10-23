<sub class="doc-stamp">26.10.22 02:15</sub>

# Pointer - `SITE-FAMILY-MAP`

| | |
| --- | --- |
| **Id** | `SITE-FAMILY-MAP` |
| **Version** | 1.9, active. Owner: the sza.od.ua hub |
| **Home** | `product-web-pages/SITE-FAMILY-MAP.md` in the shared contracts catalog |
| **Role here** | consumer - the footer of the product site |

## What this repository must do to stay conformant

- The footer grid carries every sibling product of the map's section 2, with the `Tool` name and the URL the map states: a corrective step of the map (1.3, 1.4) changes a label and nothing else, so a name left at its old spelling still links the right page and misstates only the label.
- The `Tool` cell is the product's own display name, so the Windows row reads *Fast Media Sorter & Sharing for
  Windows* (1.9). A help-index cell (1.6, 1.7) is a column of its row and never a footer link (rule 6), and the
  shared pages this product owns are the map's section 2a records (1.8); their canonical addresses stay permanent.
- One contact everywhere: the email and the GitHub account the map names.
- A cross-link to a sibling inside body copy stays body copy, not a second footer.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- The footer of `index.html`, `nolegal.html` and their `-ru` / `-uk` translations.
- The grid's one copy is `scripts/site/family-footer.json`; `scripts/site/render-family-footer.ps1` renders it into the six pages between the `family-footer` markers. A map change is an edit of that file plus a render.
- `scripts/quality/assert-site-family-map.ps1` (release scope) holds the pages to the source, the source to the map's section 2 (URL and `Tool` name), this product's row to `_config.yml`, and rule 3 on the pages and `README.md`.
