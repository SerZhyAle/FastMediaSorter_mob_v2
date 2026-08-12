---
name: document-registry
description: Mandatory FastMediaSorter documentation-context loop. Use at task start, material scope changes, phase boundaries, and before final response for every task, including code, build, research, audit, release, and documentation work.
---

# Document Registry Loop

`docs/DOCUMENT_REGISTRY.jsonl` is the source of truth for maintained documents, instructions, and site pages.

Run this loop at task start, after a material scope change, at every engineering phase boundary, and before the final response:

1. Identify the product area and change trigger.
2. Query the registry:

```powershell
pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "<area>"
pwsh -NoProfile -File scripts/document_registry/query.ps1 -Trigger "<trigger>"
pwsh -NoProfile -File scripts/document_registry/query.ps1 -ListVocabulary
```

A miss is never the end of the loop: the query resolves a near-miss value itself and prints a `resolved:` line, and a value it cannot resolve is answered with the vocabulary of both facets. Re-query from that list in the same turn before concluding nothing is registered.

3. Read every returned record before deciding or editing.
4. State which returned records are affected and why the remaining matches are unchanged.

When a registered document, public page, registry record, or documentation automation changes, close with:

```powershell
pwsh -NoProfile -File scripts/document_registry/validate.ps1
pwsh -NoProfile -File scripts/document_registry/generate.ps1
pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check
```

Never hand-edit generated `docs/DOCS_MAP.md` or `sitemap.xml`. Register maintained documents and pages before relying on them in a workflow.
