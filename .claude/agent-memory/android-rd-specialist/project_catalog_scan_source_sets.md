---
name: catalog-scan-source-sets
description: dev/CATALOG/scripts/scan.ps1 source-set roots - extend when a new source set is added
metadata:
  type: project
---

`dev/CATALOG/scripts/scan.ps1` enumerates a hard-coded list of source roots and silently skips anything else. Until 2026-05-17 it covered only `main`, `vr`, `noLegal`, `streamingEnabled`. S0200 introduced `cloudEnabled`/`cloudDisabled` - without the extension, every class under those source sets was invisible to the catalog (and to `set.ps1`, `query.ps1`, `render.ps1`).

**Why:** The script was authored before flavor-shared source sets were a regular pattern. New shared source sets (e.g. `cloudEnabled`, future split-feature buckets) must be added manually - there is no auto-discovery from `build.gradle.kts`.

**How to apply:** When introducing a new `src/<bucketName>/java/` source set referenced from `sourceSets {}` in `app_v2/build.gradle.kts`:
1. Add `(Join-Path $Root "$Module\\src\\<bucketName>\\java")` to the `$srcRoots` array near line 26 of `scan.ps1`.
2. Re-run `scan.ps1 -Module app_v2` then `render.ps1 -Module app_v2`.
3. Fill `role`/`status` for the newly-visible classes via `set.ps1`.

Related: see [[catalog-set-ps1-stops-on-error]] for set.ps1 batch behaviour when paths don't exist yet.
