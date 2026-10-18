<sub class="doc-stamp">26.10.18 21:05</sub>

# S4368 - canon harness change request

This is input for the canon update; hand it to `sza-unified-rules` S0008 (contract sync implementation,
`DOC-INTERNAL-QUALITY` 0.10 applicability).

- Harness version read: `sza` plugin `2026.1017.2`, `tools/harness/`.
- Consumer: FastMediaSorter_mob_v2. Every local path is a generated forwarder, so none of these
  changes touches a call site here.
- Why a request and not an edit: harness bodies are overwritten by the next plugin update and never
  reach the other consumers (S3151 ADR-2).

## 1. A second, local document registry path

`DOC-INTERNAL-QUALITY` 0.10 ("Amendment 2026-10-17 - proposal consolidation", paragraph 2, in
`documentation-quality/README.md` of the shared contracts catalog) requires:

- an internal engineering corpus may be local and gitignored;
- its registry is local at `dev/DOCUMENT_REGISTRY.jsonl`, while the tracked
  `docs/DOCUMENT_REGISTRY.jsonl` indexes published/forker-facing documents only;
- reverse coverage and links are validated independently in both corpora;
- no index of inaccessible files is published.

The shipped harness cannot express this: `document_registry/validate.ps1` and `query.ps1` resolve one
registry through the single profile key `paths.documentRegistry` (default `docs/DOCUMENT_REGISTRY.jsonl`,
defined in the harness `_profile.ps1`, External: that file ships in the canon plugin, not this
repository), and no `localRegistry` or similar key exists anywhere under
`tools/harness/**` (audited in S4353 research 04, section 4.3).

Requested:

1. A profile key for the local registry, for example `paths.documentRegistryLocal`, default
   `dev/DOCUMENT_REGISTRY.jsonl`. A project that never sets it keeps today's behavior exactly.
2. `validate.ps1` validates both corpora in one run when the local registry exists: the same record
   checks (required fields, id shape, duplicate ids, glob resolution) per corpus, errors labelled with
   the corpus they came from, exit 1 if either corpus fails, exit 0 only when both pass.
3. `query.ps1` answers from both registries, each row naming its corpus, so the document-registry loop
   keeps seeing the internal records it served before the split.
4. `generate.ps1` stays tracked-registry only: `docs/DOCS_MAP.md` and `sitemap.xml` must never render
   the local corpus - that is the "no index of inaccessible files" clause.

Consumer state until this ships (S4368, closed 2026-10-18): the split is done repo-side - local records
moved to a gitignored `dev/DOCUMENT_REGISTRY.jsonl`, tracked records narrowed to tracked paths, and the
local corpus is validated by a repo-side interim check that mirrors the validator's record checks. The
dated exception and the remediation reference live in the catalog's `_meta/REGISTRY.md`
(`DOC-INTERNAL-QUALITY` row, FastMediaSorter Android, until 2026-12-31).
