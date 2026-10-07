<sub class="doc-stamp">26.10.07 04:59</sub>

# Pointer - `SITE-STRUCTURE`, `SITE-EXPERIENCE` and `SITE-REPRESENTATION`

| | |
| --- | --- |
| **Id** | `SITE-STRUCTURE`, `SITE-EXPERIENCE`, `SITE-REPRESENTATION` |
| **Version** | 0.1, 0.3, 0.1, draft (in the order of the ids above); wire carrier: none - a page set, a stylesheet layer and a content model. Owner: this product |
| **Home** | `product-site/README.md` in the shared contracts catalog |
| **Role here** | owner and reference implementation - the product site: landing, sideload pages, documentation portal and the `docs/` reference pages |

## What this repository must do to stay conformant

- Publish the page types its tier owes (portal), every public page reachable from the landing by links, and a not-found page; keep every address that the app, the store listings or a README holds answering, and leave a forwarder when a page moves.
- Offer a language only where the page exists in it, and give the landing, the portal and the trust pages the locale sets the registry's adoption row declares.
- Release a function with its page, and retire the page of a removed function in the same change.
- Build the portal layer on the family kit with tokens taken from it; keep language and theme state, the search, the keyboard reach and the alternative text to `SITE-EXPERIENCE`.
- Fill the screen on every page - landing, documentation, portal, trust pages, generated and theme-drawn pages: no `max-width`, `min(NNNpx, NNvw)` or centred `margin: auto` on a page wrapper (`SITE-EXPERIENCE` rule 20, S4106).
- Declare every third-party origin the site contacts and name each on the privacy page; load advertising on the landing only.
- Write every external surface from `docs/POSITIONING.md`, take editions, versions and availability from the generated matrix and the build pins, give each function one page of the fixed anatomy, and name controls in the app's own words.
- Run `SITE-CHECKLIST.md` in a browser before a release that touches the site and record the verdict in the registry's adoption row. Gaps found are dated exceptions there, never softer rules.

## Where it lives here

- Landing and sideload pages: `index.html` and its locale pages, `nolegal.html` and its translations at the repository root; generated landing locales by `scripts/site/generate-landing-pages.ps1`.
- Portal: `documentation/` with its generated recipe pages (`scripts/docs/generate-docs-pages.ps1`), subject index and glossary generators, `documentation/assets/docs.css` and `search.js`.
- Reference, trust and release pages: `docs/*.md` rendered by Jekyll; the sitemap by the document registry.
- Positioning source `docs/POSITIONING.md`; capability inventory `docs/ALL_FEATURES.jsonl`; coverage manifest `docs/coverage-manifest.jsonl`; flavor matrix `docs/FLAVOR_MATRIX.md`; settings manifest `docs/settings/settings-manifest.json`.
- Public editions and their display names: `docs/flavors/public-editions.psd1`, read through `scripts/docs/lib/site-facts.ps1`.
- Third-party origins: `scripts/quality/site-origins.psd1`; their description on `docs/PRIVACY_POLICY.md` and its translations, section "This Website".
- Addresses the app holds: `SupportIntentFactory`, `GeneralSettingsLinkButtonsSetupHelper`, `InputHelpLinkResolver`, `BroadcastShareLinkFactory` and the watch's `WearPortalLinks`; the list of every address held outside the site (those, the store listings and the READMEs) is `docs/site-held-addresses.jsonl`.
- Address scheme: the page groups and the form of their language suffix, `<name>-<lang>.html`, are declared in `docs/site-address-groups.json`; a page that moves leaves a forwarder from `docs/site-redirects.jsonl` (`scripts/docs/migrate-locale-addresses.ps1` moved the legacy suffixes).

## Gate

- Held by gates today: `scripts/quality/assert-page-style.ps1`, `assert-page-content.ps1` (SITE-EXPERIENCE rule 6 also on every portal page and script), `assert-site-family-map.ps1`, `assert-positioning-consistency.ps1`, `assert-localized-page-set.ps1`, `assert-site-languages-current.ps1`, `assert-docs-coverage.ps1`, `assert-docs-portal-ui-ux.ps1` (SITE-EXPERIENCE rules 2, 3, 4, 12 and 15 on the portal layer), `assert-docs-crosslinks.ps1`, `assert-docs-search.ps1`, `assert-settings-doc-sync.ps1`, `assert-site-addresses.ps1` (SITE-STRUCTURE rules 8 forwarders, held-address list and language-suffix form, 10 suffix form, 11 resolver, 13 retirement, 15 not-found page), `assert-site-origins.ps1` (SITE-EXPERIENCE rule 14 third-party origins, declared in `scripts/quality/site-origins.psd1`). `assert-site-facts.ps1` (SITE-REPRESENTATION rules 3 and 4: the public editions declared in `docs/flavors/public-editions.psd1`, their number, the minimum Android version and the edition spelling checked in HTML and Markdown). The conformance table at the end of each contract says which rule each one holds and which rule has none.
- The deviations found by the first conformance pass are dated exceptions in the catalog's registry, each with a ticket.
