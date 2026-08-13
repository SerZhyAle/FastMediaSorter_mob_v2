# Phase 02 - Licence manifest

**Strategic spec:** [`../S1495_oss-notices-incomplete.md`](../S1495_oss-notices-incomplete.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Create the coordinate-to-licence manifest covering every shipping artifact in both modules, plus the transitive entries whose licence obliges a notice.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `temp/S1495/census.txt` exists from Step 01.3.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/docs/oss-licenses.psd1` | New | ≤ 900 |

---

## Steps

### Step 02.1 - Define the manifest schema

**Files:** `scripts/docs/oss-licenses.psd1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/docs/oss-licenses.psd1` as a PowerShell data file whose top level is a hashtable keyed by `group:artifact`. Each value is a hashtable with `Name` (human-readable library name), `Spdx` (SPDX identifier), `LicenseUrl`, `SourceUrl`, and optional `Transitive = $true` plus `Via = 'group:artifact'` for entries that no declaration names. Head the file with a comment block stating that a coordinate missing here is a fatal generator error, and that a new dependency must be added here in the same change.

**Why:**

The strategic spec ADR-2 places the licence outside the build files because a declaration carries no licence and reading it from the POM would require the gradle resolution that ADR-4 rules out, so this manifest is the only source the generator can consult.

**Verification:**

- `Glob` - `scripts/docs/oss-licenses.psd1` exists.
- Run `pwsh -NoProfile -Command "(Import-PowerShellDataFile scripts/docs/oss-licenses.psd1).Count"` - exit 0, prints a number.
- `Grep` - `Spdx` present.

**Status:** `[x] done`

---

### Step 02.2 - Populate every shipping coordinate

**Files:** `scripts/docs/oss-licenses.psd1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Fill one entry for every line of `temp/S1495/census.txt`. Take each licence from the library's own published metadata - the Maven POM or the project's repository - never from a guess by artifact name. Record `com.github.TeamNewPipe:NewPipeExtractor` as `GPL-3.0-only` with its source URL, and keep the existing SMBJ and epub4j entries as `LGPL-2.1` matching what `docs/OPEN_SOURCE.md` publishes today. Group AndroidX and Kotlin artifacts together in file order for readability, but do not collapse several coordinates into one entry.

**Why:**

Strategic §2.1 requires the notice list to cover what is actually shipped rather than an arbitrary selection, and §11.1 makes per-coordinate coverage an observable completion criterion, so an entry missing here becomes a missing notice on a published legal page.

**Verification:**

- Run `pwsh -NoProfile -Command "& { . ./scripts/docs/OssDependencyParser.ps1; $m = Import-PowerShellDataFile scripts/docs/oss-licenses.psd1; $c = @(Get-OssDependencies -GradleFile app_v2/build.gradle.kts -Module app_v2) + @(Get-OssDependencies -GradleFile wear/build.gradle.kts -Module wear); @($c | Where-Object Shipping | ForEach-Object { \"$($_.Group):$($_.Artifact)\" } | Sort-Object -Unique | Where-Object { -not $m.ContainsKey($_) }).Count }"` - prints `0`.
- `Grep` - `GPL-3.0-or-later` matches at least once. Predicate corrected during execution: the plan expected `GPL-3.0-only`, but the NewPipeExtractor LICENSE and its source headers both read "either version 3 of the License, or (at your option) any later version", so `-or-later` is the accurate identifier.

**Status:** `[x] done`

---

### Step 02.3 - Add the entries no declaration carries

**Files:** `scripts/docs/oss-licenses.psd1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add an entry for `org.bouncycastle:bcprov-jdk18on` marked `Transitive = $true` with `Via = 'com.hierynomus:smbj'`, and for any further transitive artifact whose licence obliges a notice. Determine the set from the version assertions at the end of `app_v2/build.gradle.kts` and from the direct dependencies' own declared transitives.
>
> Add a `Bundled = $true` entry for the local `app_v2/libs/fms-ffmpeg-dts.aar`, keyed `local:fms-ffmpeg-dts`, recording FFmpeg's licence as built by `scripts/builders/build-ffmpeg-dts.sh` and listing `ShippedInOverride` as the four flavors that declare it: `standard`, `noLegal`, `legacy`, `vr`. Read the build script to establish which licence the produced binary actually carries rather than assuming one.

**Why:**

Strategic ADR-4 accepts that text parsing cannot see a transitive dependency and names the manifest as the place where such an artifact is carried by hand, and strategic §2.1 binds the obligation to what is actually inside the distributed artifact - the bundled FFmpeg AAR is inside it while carrying no Maven coordinate at all.

**Verification:**

- `Grep` - `bouncycastle` matches in `scripts/docs/oss-licenses.psd1`.
- `Grep` - `Transitive` matches at least once.
- `Grep` - `fms-ffmpeg-dts` matches in `scripts/docs/oss-licenses.psd1`.
- Run `pwsh -NoProfile -Command "(Import-PowerShellDataFile scripts/docs/oss-licenses.psd1).Count"` - exit 0.

**Status:** `[x] done`

---

## Step Log

- 2026-08-10 - Step 02.1 done. Manifest schema written; `Oss` and `Bundled` fields added beyond the planned set once research established that four Google artifact groups ship under vendor terms rather than an open source licence, and that a bundled binary needs a flavor override.
- 2026-08-10 - Steps 02.2 and 02.3 done. 99 entries: 97 shipping coordinates, one transitive (BouncyCastle via SMBJ), one bundled (`local:fms-ffmpeg-dts`). Coverage checked mechanically - 0 missing, 0 entries that do not ship.
- 2026-08-10 - Two long-published licence claims were found wrong and corrected here: SMBJ 0.12.1 and epub4j-core 4.2 are Apache-2.0, not LGPL-2.1. The same wrong claim still ships inside the app - parked as S1562, out of this ticket's stated scope.
- 2026-08-10 - NewPipeExtractor is `GPL-3.0-or-later`, not `-only`. GPLv3 obliges providing corresponding source to recipients of the noLegal build, which is an action beyond notices - parked as S1563.
- 2026-08-10 - FFmpeg AAR licence established from `scripts/builders/build-ffmpeg-dts.sh` rather than assumed: its configure invocation passes neither `--enable-gpl` nor `--enable-nonfree` and disables postproc, so the binary is LGPL-2.1-or-later.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, no compiled source touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG` regeneration - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every shipping coordinate now resolves to a licence. Phase 03 may treat a lookup miss as a fatal condition rather than a gap to tolerate.

---

## Rollback Plan

Delete the manifest - nothing consumes it until Phase 03.
