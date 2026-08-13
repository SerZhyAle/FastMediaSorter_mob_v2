# Phase 02 - Gradle Source Parser

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Produce a self-contained PowerShell module that extracts every Class-1/2/3 pin from the canonical Gradle sources (`gradle/wrapper/gradle-wrapper.properties`, root `build.gradle.kts`, `app_v2/build.gradle.kts`) and returns a flat hashtable `pinName → version`. No documentation reading, no comparison logic.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`DECISIONS.md` exists).
- [ ] No new external PowerShell modules introduced.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/GradleParser.ps1` | New | ≤ 220 |
| `scripts/doc-drift/` (folder) | New | - |

---

## Steps

### Step 02.1 - Create folder + dispatch function skeleton

**Files:** `scripts/doc-drift/GradleParser.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create folder `scripts/doc-drift/` (mkdir if absent). Author `GradleParser.ps1` exposing a single public function `Get-GradlePins` that accepts a parameter `-RepoRoot <string>` (defaults to current location) and returns a `[ordered]@{}` hashtable. The function is the only entry point - all parsing helpers stay private (`script:` scope). Top of file: comment block with purpose, contract reference to `DECISIONS.md D-1`, and the literal note `# No external module dependencies. PowerShell 7+. -NoProfile safe.` File must be dot-sourceable (`. ./scripts/doc-drift/GradleParser.ps1`) without side effects.

**Verification:**

- `Glob` - `scripts/doc-drift/GradleParser.ps1` exists.
- `Grep` - `function Get-GradlePins` matches exactly once.
- `Grep` - `# No external module dependencies` present.
- `Grep` - `param\(` present with `RepoRoot` parameter.

**Status:** `[x] done`

---

### Step 02.2 - Implement extractors for Class 1 pins (build tools)

**Files:** `scripts/doc-drift/GradleParser.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add private helpers and wire them into `Get-GradlePins`. Class-1 coverage (strategic §5.1):
>
> - `gradle.wrapper` - from `gradle/wrapper/gradle-wrapper.properties`, match `distributionUrl=.*gradle-(?<v>[\d\.]+)-(bin|all)\.zip`.
> - `agp` - from root `build.gradle.kts`, prefer `id("com.android.application") version "..."`; fallback to `classpath("com.android.tools.build:gradle:...")`.
> - `kotlin` - from root `build.gradle.kts`, match `kotlin-gradle-plugin:(?<v>[\d\.]+)`.
> - `ksp` - from root `build.gradle.kts`, match `id("com.google.devtools.ksp") version "(?<v>[\d\.]+)"`.
> - `hilt-plugin` - from root `build.gradle.kts`, match `id("com.google.dagger.hilt.android") version "(?<v>[\d\.]+)"`.
> - `compose-plugin` - match `id("org.jetbrains.kotlin.plugin.compose") version "(?<v>[\d\.]+)"`.
> - `navigation-safe-args` - match `androidx.navigation:navigation-safe-args-gradle-plugin:(?<v>[\d\.]+)`.
> - `chaquopy` - match `id("com.chaquo.python") version "(?<v>[\d\.]+)"`.
>
> Helpers return the captured version string or `$null` (never an empty string). Missing source file is a thrown exception with the path and the expected matcher in the message - parser is not silent on missing input. Add private regex constants at the top of the file.

**Verification:**

- `Grep` - `gradle\.wrapper` (the hashtable key, with literal dot escape) referenced in code.
- `Grep` - all eight pin names referenced as quoted strings: `'gradle.wrapper'`, `'agp'`, `'kotlin'`, `'ksp'`, `'hilt-plugin'`, `'compose-plugin'`, `'navigation-safe-args'`, `'chaquopy'`.
- `Grep` - `throw` present (parser raises on missing source).
- Run `pwsh -NoProfile -Command '. ./scripts/doc-drift/GradleParser.ps1; (Get-GradlePins).Count'` - expected: `>= 8` | actual: capture value.

**Status:** `[x] done`

---

### Step 02.3 - Implement extractors for Class 2 (SDK) and Class 3 (library) pins

**Files:** `scripts/doc-drift/GradleParser.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend `Get-GradlePins` to extract pins from `app_v2/build.gradle.kts`. Class-2:
>
> - `compile-sdk` - match `compileSdk = (?<v>\d+)`.
> - `target-sdk` - match `targetSdk = (?<v>\d+)`.
> - `min-sdk.<flavor>` - for each flavor block (`standard`, `lite`, `photos`, `legacy`, `vr`, `noLegal`), extract per-flavor `minSdk` override; fallback to `defaultConfig.minSdk` if a flavor block does not override.
> - `ndk-version` - match `ndkVersion = "(?<v>[\d\.]+)"`.
> - `jvm-target` - match `jvmTarget = "(?<v>[\d\.]+)"`.
> - `source-compat` - match `sourceCompatibility = JavaVersion\.VERSION_(?<v>\d+)`.
>
> Class-3 (libraries): generic Maven-coordinate extractor. For every line matching `(?:implementation|api|kapt|ksp|coreLibraryDesugaring)(?:Platform)?\(\s*"(?<group>[\w\.\-]+):(?<artifact>[\w\-]+):(?<v>[\d\.\-A-Za-z]+)"\s*\)`, record key `lib.<group>:<artifact>` → version. Strip BOM-style declarations (`platform(...)`) - they are not regular pins. The pin manifest (Phase 03) will consume `lib.*` keys by exact Maven coordinate, so the parser does not need to know which libraries the documentation cares about.
>
> Final `Get-GradlePins` returns the merged ordered hashtable: Class-1 keys first, Class-2 next, Class-3 last (sorted by coordinate).

**Verification:**

- `Grep` - `compile-sdk` and `target-sdk` both present as quoted strings.
- `Grep` - `min-sdk\.` substring present (per-flavor key pattern).
- `Grep` - `lib\.` substring present (generic library key pattern).
- Run `pwsh -NoProfile -Command '. ./scripts/doc-drift/GradleParser.ps1; $p = Get-GradlePins; "agp=$($p[''agp'']) kotlin=$($p[''kotlin'']) hilt-plugin=$($p[''hilt-plugin''])"'` - expected: `agp=9.2.1 kotlin=2.2.10 hilt-plugin=2.59` | actual: capture value.
- Run `pwsh -NoProfile -Command '. ./scripts/doc-drift/GradleParser.ps1; (Get-GradlePins).Keys | Where-Object { $_ -like "lib.*" } | Measure-Object | Select-Object -ExpandProperty Count'` - expected: `>= 30` (project pulls many implementation deps) | actual: capture value.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -Command '. ./scripts/doc-drift/GradleParser.ps1; $null = Get-GradlePins; $LASTEXITCODE'` returns `0`.
- [ ] No `Log\.d\(` introduced (N/A - PowerShell file).
- [ ] No emoji or ANSI escapes in source.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `GradleParser.ps1` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`Get-GradlePins` returns the canonical hashtable Phase 03's document parser and Phase 04's comparator both consume. The parser does not know the manifest exists; the manifest decides which pins to compare. Adding a new pin = optionally adding a new helper here (if it is not a generic Maven coordinate) + adding one entry to `pins.psd1`.

---

## Rollback Plan

Delete `scripts/doc-drift/GradleParser.ps1`. No data migration. No build artefacts depend on it.
