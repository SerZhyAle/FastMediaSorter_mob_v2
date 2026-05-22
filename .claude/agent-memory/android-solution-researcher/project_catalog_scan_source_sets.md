---
name: catalog-scan-source-sets
description: dev/CATALOG/scripts/scan.ps1 source-set roots - extend when a new source set is added
metadata:
  type: project
---

`dev/CATALOG/scripts/scan.ps1` enumerates a hard-coded list of source roots and silently skips anything else. Until 2026-05-17 it covered only `main`, `vr`, `noLegal`, `streamingEnabled`. S0200 introduced `cloudEnabled`/`cloudDisabled` - without the extension, every class under those source sets was invisible to the catalog (and to `set.ps1`, `query.ps1`, `render.ps1`).

**Why:** The script was authored before flavor-shared source sets were a regular pattern. New shared source sets (e.g. `cloudEnabled`, future split-feature buckets) must be added manually - there is no auto-discovery from `build.gradle.kts`.

**How to apply:** When the research scope touches a new or unfamiliar source set:
- If `query.ps1`/`*.jsonl` returns zero hits for a class you can see on disk under `src/<bucketName>/java/`, suspect the catalog scan has not been extended for that bucket. Confirm by checking the `$srcRoots` array near line 26 of `scan.ps1`.
- Do NOT run `scan.ps1`/`render.ps1` from the research agent - flag the gap in the report under "Open Questions" so a writer-class agent fixes the script and re-runs the sync.
- For evidence in the meantime, fall back to `Grep`/`Glob` over the missing bucket path directly and label the citation as "catalog gap - direct grep".

Related: see [[catalog-set-ps1-stops-on-error]] for set.ps1 batch behaviour when paths don't exist yet.
