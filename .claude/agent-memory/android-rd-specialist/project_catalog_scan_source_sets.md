---
name: catalog-scan-source-sets
description: dev/CATALOG/scripts/scan.ps1 source-set roots - extend when a new source set is added
metadata:
  type: project
---

`dev/CATALOG/scripts/scan.ps1` enumerates a hard-coded list of source roots and silently skips anything else. Until 2026-05-17 it covered only `main`, `vr`, `noLegal`, `streamingEnabled`. S0200 introduced `cloudEnabled`/`cloudDisabled` - without the extension, every class under those source sets was invisible to the catalog (and to `set.ps1`, `query.ps1`, `render.ps1`). On 2026-06-11 (S0400) I added the still-missing `translationDynamicFeature`, `translationMlKit`, `vrOnly` roots (+11 records); the symptom was `set.ps1` "No record found" for a class that built fine. On 2026-07-17 (S0404) I added `launcherEnabled`/`launcherDisabled` - the entire launcher feature had been invisible for FIVE phases.

**`catalog-sync` PASS proves nothing about coverage - a missing root is indistinguishable from an empty one.** That is why this keeps recurring: the gate is green the whole time, and the miss only surfaces later as `set.ps1` "No record found" for a class that builds fine. `catalog_sync.ps1` had reported PASS after every S0404 phase while cataloguing zero of its classes. **Do not wait for a gate to tell you** - adding a source set to `build.gradle.kts` and adding it to `$srcRoots` are one action, not two.

Catalog stores **package-relative** paths (no `src/<bucket>/java/` prefix): pass `set.ps1 -Path "com/sza/.../Foo.kt"`, not the source-set-prefixed path. Params are `-Module` + `-Path` (NOT `-Class`), and `-Status` takes only `new|tested|legacy|todo|unknown`.

Test source sets are deliberately NOT scanned (no `src/test*` root for any flavor), so a new test class never needs a catalog entry.

**Why:** The script was authored before flavor-shared source sets were a regular pattern. New shared source sets (e.g. `cloudEnabled`, future split-feature buckets) must be added manually - there is no auto-discovery from `build.gradle.kts`.

**How to apply:** The moment you add a `src/<bucketName>/java/` source set to `sourceSets {}` in `app_v2/build.gradle.kts`, in the SAME change:
1. Add `(Join-Path $Root "$Module\\src\\<bucketName>\\java")` to the `$srcRoots` array in `scan.ps1`.
2. Re-run `scripts/catalog_sync.ps1 -Module app_v2`.
3. Verify by class name, not by path - `Select-String 'launcherEnabled'` over the JSONL returns 0 even when it worked, because paths are package-relative. Grep `'"class":"Foo"'` instead.
4. Fill `role`/`status` for the newly-visible classes via `set.ps1`.

Related: see [[catalog-set-ps1-stops-on-error]] for set.ps1 batch behaviour when paths don't exist yet.
