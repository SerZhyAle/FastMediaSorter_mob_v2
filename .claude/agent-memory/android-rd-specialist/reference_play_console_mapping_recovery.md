---
name: play-console-mapping-recovery
description: Play Console never returns an uploaded R8 mapping - recover it from the release AAB's Original file download instead; plus the merged-class trap when reading it.
metadata:
  type: reference
---

To deobfuscate a symbol from an old release, get that release's mapping out of the **AAB**, not out of the mapping row.

**Where it is not.** Play Console -> Test and release -> **Latest releases and bundles** (the App bundle explorer; there is no separate menu item any more) -> pick the artifact by versionCode -> **Downloads** -> **Assets**. The row `ReTrace mapping file / mapping.txt` carries a **trash icon, not a download icon**. Play accepts the mapping and lets you delete it; it never hands it back. Do not click it - deleting the server copy destroys the last copy of a release's mapping.

**Where it is.** The `Original file` row in the same Assets table is the shipped AAB, and an AAB embeds the mapping at `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`. Download it and extract:

```bash
unzip -p <versionCode>.aab "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map" > temp/Sxxxx/mapping-<versionCode>.txt
```

Verify you got the right build before trusting a resolution: the extracted size matches the MB figure the console shows on the `ReTrace mapping file` row, and the header carries `compiler_version`, `min_api` (26 = standard flavor, 23 = legacy) and `pg_map_id`.

**The local AAB is never an older release.** `DOWNLOADS/FastMediaSorter_standard_release.aab` is written under a **fixed** name by [build-aab-release.ps1](../../../scripts/builders/build-aab-release.ps1) and the directory is gitignored, so it is always the latest build - the same overwrite trap as `build/outputs`. Measured 2026-08-15 (S1695): the bundle also embeds the native symbols at `BUNDLE-METADATA/com.android.tools.build.debugsymbols/<abi>/*.so.dbg`, and inside the zip the mapping costs **14.2 MB** compressed (from 178.9 MB, 92%) against ~7.9 MB for all four ABIs of symbols - so a full deobfuscation set is ~22 MB, not the ~200 MB the raw files suggest.

**Finding the artifact.** versionCode is `yyMMddHH` + first digit of minutes ([scripts/builders/build-aab-release.ps1](../../../scripts/builders/build-aab-release.ps1)), so versionName `2.60.7221.704` (2026-07-22 17:04) is artifact `260722170`. `DOWNLOADS/builds_versions.lst` logs every release build with its versionName.

**Two traps when reading the mapping.**

- R8 names are **per build**. A mapping from any other build resolves the same symbols to real-looking but unrelated classes, and nothing marks the answer as wrong. Match the release exactly.
- A residual class may be a **horizontal merge** of several originals - its block carries a `$r8$classId` field marked `com.android.tools.r8.synthesized`. The class name on the `-> xx:` line is then just the first constituent and can be wildly misleading; resolve the **member**, and read the inlined frames, rather than trusting the class name.

Related: [[project_s0002_decomposition_toolkit]] for other large-artifact tooling.
