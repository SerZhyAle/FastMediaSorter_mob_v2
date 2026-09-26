# Documentation Linking, Termbase & External Reference Rules

This guide defines linking rules across documentation pages, termbase references (S2974), unwritten page bookmarks, and external links.

## 1. Internal Cross-Links (Written Pages)

Links to existing, published pages should use standard relative URLs:
```html
<a href="sample-settings-recipe.html" class="doc-link">Customizing Settings</a>
```
- A relative link resolves from the address of the page that holds it, and a page is served at its front-matter `permalink:`, not at its file path. A footer link `docs/PRIVACY_POLICY.html` on `/documentation/wear/x.html` points at `/documentation/wear/docs/PRIVACY_POLICY.html`.
- Link to the target's permalink, never to its file name: `docs/PRIVACY_POLICY_RU.md` is served as `/docs/PRIVACY_POLICY.ru.html`.
- `scripts/quality/assert-docs-crosslinks.ps1` resolves every `href` and `src` this way against the addresses the Jekyll source publishes. Known broken targets sit in `scripts/quality/docs-crosslinks-baseline.txt`, which only shrinks: a new broken target fails the gate, and so does a row whose target now resolves until the row is deleted.

## 2. Bookmarks for Unwritten Pages

When referencing a topic planned in another thematic ticket that has not yet been published, use a non-breaking bookmark span tagged with `data-page-id`:
```html
<span class="doc-bookmark" data-page-id="storage.batch-renaming" title="Covered in S2949">Batch Renaming Patterns [Planned]</span>
```
- During documentation writing, `scripts/quality/assert-docs-crosslinks.ps1` treats valid bookmarks as non-fatal warnings.
- The closure (`scripts/post-change.ps1`) runs it, fatal, whenever the changed set carries a file under `documentation/`, `docs/content/` or `docs/docs-pages-manifest.jsonl`.
- In final release verification (S2975), `-Strict` mode treats remaining bookmarks as errors once all thematic tickets are complete.

## 3. Termbase References (S2974)

When introducing a specialized technical concept or domain term defined in the project glossary (S2974), link using the termbase convention:
```html
<a href="terms.html#term-saf" class="doc-term-link" data-term="SAF" title="View definition in glossary">Storage Access Framework</a>
```

## 4. Authoritative External References

When citing platform documentation, protocols, or standards, use external link classes with `target="_blank"` and `rel="noopener"`:
- **Android Developers**: `https://developer.android.com/...`
- **RFC Specifications**: `https://datatracker.ietf.org/doc/html/rfc...`
- **Wikipedia / Standards**: For general concepts (e.g., DLNA, HLS, DASH, WebDAV).

```html
<a href="https://developer.android.com/training/data-storage/shared/documents-files"
   target="_blank"
   rel="noopener"
   class="doc-link-external">Android Storage Access Framework Documentation</a>
```
