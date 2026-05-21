---
name: project_catalog_scan_source_sets
description: dev/CATALOG/scripts/scan.ps1 source-set roots - extend when a new source set is added
metadata:
  type: project
---

`dev/CATALOG/scripts/scan.ps1` enumerates a hard-coded list of source roots and silently skips anything else. Until 2026-05-17 it covered only `main`, `vr`, `noLegal`, `streamingEnabled`. S0200 introduced `cloudEnabled`/`cloudDisabled` - without the extension, every class under those source sets was invisible to the catalog (and to `set.ps1`, `query.ps1`, `render.ps1`).

**Why:** The script was authored before flavor-shared source sets were a regular pattern. New shared source sets (e.g. `cloudEnabled`, future split-feature buckets) must be added manually - there is no auto-discovery from `build.gradle.kts`.

**How to apply:** When implementation work adds a new `src/<bucketName>/java/` source set (referenced from `sourceSets {}` in `app_v2/build.gradle.kts`) and the first `.kt` file lands in it:
1. Open `dev/CATALOG/scripts/scan.ps1` and add `(Join-Path $Root "$Module\\src\\<bucketName>\\java")` to the `$srcRoots` array near line 26.
2. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (one process, scan + render).
3. For each newly-visible class, fill `role`/`status` via `set.ps1` - wrapping in try/catch per [[project_catalog_set_ps1_stops_on_error]].
If `query.ps1 -ClassMatches "*MyNewClass*"` returns nothing after the first build, the source set is not in `$srcRoots` - patch the script first, not the workaround.

Related: see [[project_catalog_set_ps1_stops_on_error]] for set.ps1 batch behaviour when paths don't exist yet.
