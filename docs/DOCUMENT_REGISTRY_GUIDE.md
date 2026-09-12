# Document Registry Guide

Every agent must use the document registry at task start, after a material scope change, at each engineering phase boundary, and before the final response. This is not limited to documentation tasks.

1. Query by product area or update trigger.
2. Read every returned record before deciding or editing.
3. State which records are affected and why other returned records are unchanged.
4. Add or adjust registry records when a maintained document or public page changes ownership, scope, publication, or localization.
5. Run validation and generated-view drift checks when a registered material changes.

Commands:

```powershell
pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea settings
pwsh -NoProfile -File scripts/document_registry/query.ps1 -Trigger user-feature
pwsh -NoProfile -File scripts/document_registry/query.ps1 -ListVocabulary
pwsh -NoProfile -File scripts/document_registry/validate.ps1
pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check
```

Area and trigger values are free-form and discovered from the registry, so a query never needs to be guessed twice (S1597). A value that matches nothing exactly is retried against the other word form, then against the opposite facet, then as a substring, and the applied resolution is printed as a `resolved:` line above the matches - a substituted query never reads as an exact hit. A value that survives all of that is answered with the vocabulary of both facets, which `-ListVocabulary` also prints on demand. Re-query from that list in the same turn rather than concluding the material is unregistered.

`DOCUMENT_REGISTRY.jsonl` is the source of truth. `DOCS_MAP.md` and `sitemap.xml` are generated views. Do not add a maintained file without either registering it or documenting why it is outside the registry.

## Coverage is checked in both directions (S2618)

That last sentence was prose only until S2618, and prose does not fail a build: `validate.ps1` checks **registry -> disk** (every record glob must find a live file) and nothing checked the reverse, so a directory full of documents that no record named survived indefinitely - which is how the missing `.claude/rules/*.md` glob lasted until S2607.

`scripts/quality/assert-document-registry-coverage.ps1` now checks **disk -> registry** at release scope:

- A document is a `.md` file under the roots the gate declares; the root list is written out in the script because git cannot decide the question (`.claude/` is gitignored yet holds the largest registered surface here, `node_modules/` is tracked and holds nothing maintained).
- It judges directories, not individual files, and a covered parent does not cover an unreached child.
- Every directory holding documents must be reached by a record glob or named in `scripts/quality/document-registry-coverage-baseline.txt` with a reason of at least four words - the same floor `validate.ps1` applies to a `sitemap_exclude` reason.
- A baseline row naming a directory that no longer holds documents fails as stale.

When the gate refuses, register the directory or add a baseline row. Registering is the better answer; a baseline row records that this repository agreed to stop maintaining that tree as documentation.
