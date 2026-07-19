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
pwsh -NoProfile -File scripts/document_registry/validate.ps1
pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check
```

`DOCUMENT_REGISTRY.jsonl` is the source of truth. `DOCS_MAP.md` and `sitemap.xml` are generated views. Do not add a maintained file without either registering it or documenting why it is outside the registry.
