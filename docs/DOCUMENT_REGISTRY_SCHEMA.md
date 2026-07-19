# Document Registry Schema

`docs/DOCUMENT_REGISTRY.jsonl` is the source of truth for maintained project documents and public site pages. One JSON object per line describes one logical document group. Generated indexes must not be edited by hand.

Required fields:

- `id` - stable kebab-case identifier.
- `title` - short English display name.
- `category` - `public`, `technical`, `process`, `inventory`, or `site`.
- `audience` - `user`, `developer`, `contributor`, or `mixed`.
- `paths` - repository-relative file paths or glob patterns owned by the record.
- `published` - whether the material is public.
- `indexable` - whether a public URL belongs in `sitemap.xml`.
- `product_areas` - stable areas used to discover related docs.
- `update_triggers` - stable change kinds that require a review.
- `generated` - whether a generator owns the material.

Optional fields:

- `url` - canonical public URL path, beginning with `/`.
- `languages` - maintained language codes.
- `mirrors` - expected repository-relative language mirror paths.
- `notes` - concise ownership or exclusion detail.

Rules:

- A path must be inside the repository and match at least one file.
- A public indexable record must have `url` and `published: true`.
- Internal process, planning, and agent artifacts must use `indexable: false`.
- Add a record before adding a maintained document or public page.
- Use `product_areas` for durable links to code and functionality. Do not bind routine documents to individual implementation files.
- `update_triggers` names the event, such as `user-feature`, `setting`, `architecture`, `dependency`, `release`, `workflow`, `site`, or `documentation`.
