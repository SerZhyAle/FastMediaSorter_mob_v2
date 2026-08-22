# Phase 01 - Archive store

**Strategic spec:** [`../S1695_release-deobfuscation-artifact-retention.md`](../S1695_release-deobfuscation-artifact-retention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Introduce `scripts/release/retain-deobfuscation.ps1`, which stores one release variant's deobfuscation payload into the cloud archive under a `versionCode` key. No release script calls it yet.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [x] Strategic §6 research items blocking this phase are Resolved - all four are.
- [x] Working tree is clean or on a feature branch.
- [x] A release AAB is present on disk for the measurement step - `app_v2/build/outputs/bundle/standardRelease/*.aab` in the release worktree, or any archived bundle.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/retain-deobfuscation.ps1` | New | ≤ 520 |

> Budget corrected at audit, 2026-08-15: the original `≤ 260` was estimated before the phase had to carry two source paths. Actual 503 lines, of which 313 are executable - the remainder is the comment-based help block and the rationale comments. Well under Rule 2's 1500-line ceiling.

---

## Steps

### Step 01.1 - Create the script skeleton, parameters and archive layout

**Files:** `scripts/release/retain-deobfuscation.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/release/retain-deobfuscation.ps1` following the conventions of the neighbouring release scripts: `#!/usr/bin/env pwsh`, a comment-based help block, `[CmdletBinding()]`, `$ErrorActionPreference = 'Stop'`.
>
> Parameters: `-VersionCode <int>` (mandatory), `-VersionName <string>`, `-Variant <string>` (mandatory, one of `standard`, `lite`, `photos`, `legacy`, `vr`, `noLegal`, `wear`), `-Bundle <path>`, `-Mapping <path>`, `-NativeSymbols <path>`, `-ArchiveRoot <path>` defaulting to `c:\GD\WORK\FastMediaSorter\deobfuscation`, `-Force`, `-DryRun`, `-Help`.
>
> Resolve the per-release destination as `<ArchiveRoot>\<VersionCode>\` and the per-variant payload as `<ArchiveRoot>\<VersionCode>\<Variant>-deobfuscation.zip`. Create the directory chain when absent. When `-ArchiveRoot` cannot be created or written, exit 2, not 1.
>
> Document the exit codes in the help header and honour Rule 7: write `Write-Error $msg -ErrorAction Continue` before any `exit N` where N is not 1. Codes: 0 = payload stored or already present and identical, 1 = retention failed (source unreadable, or an existing entry differs and `-Force` was not passed), 2 = cannot verify (archive root unreachable, no source given).

**Why:**

Strategic §5.3 fixes `versionCode` as the address so that artifacts of other modules and variants can join the same scheme without changing the addressing, and strategic §3.3 fixes `c:\GD\WORK\FastMediaSorter` as the archive location because the build scripts already write there and it survives a machine reinstall. Distinguishing exit 2 from exit 1 matters because "the cloud folder was not mounted" and "the artifact is missing" call for different operator actions, and strategic §7 lists an unsynchronised cloud folder as the medium-probability risk.

**Verification:**

- `Glob` - `scripts/release/retain-deobfuscation.ps1` exists.
- `Grep` - `param(` block contains `$VersionCode`, `$Variant`, `$ArchiveRoot`, `$DryRun`.
- `Grep` - `c:\\GD\\WORK\\FastMediaSorter\\deobfuscation` appears exactly once, in the `-ArchiveRoot` default.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Skeleton written: param block with VersionCode/Variant/ArchiveRoot/DryRun, archive layout <root>/<code>/<variant>-deobfuscation.zip, Exit-Retain helper. assert-exit-contract exit 0; archive default present once at line 81.

---

### Step 01.2 - Implement the bundle extraction path

**Files:** `scripts/release/retain-deobfuscation.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> When `-Bundle` names an existing `.aab`, open it with `System.IO.Compression.ZipFile::OpenRead` and copy two entry classes into the destination zip: the single entry `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`, stored as `mapping.txt`, and every entry matching `BUNDLE-METADATA/com.android.tools.build.debugsymbols/*/*.so.dbg`, stored under `symbols/<abi>/<name>`.
>
> Stream each entry from the source archive straight into the destination entry with `CopyTo`; never read an entry into a variable or an array first. Compress the destination with `CompressionLevel.Optimal`.
>
> A bundle that carries no `proguard.map` entry is a hard error - exit 1 naming the bundle - because an unminified bundle must never be recorded as retained. A bundle carrying no `.so.dbg` entries is not an error; record the symbol count as zero.

**Why:**

Strategic ADR-3 makes the released AAB the extraction source because the bundle is exactly what reached the user, so the artifact's correspondence with the release is guaranteed by how it was obtained rather than by discipline; planning measured a second reason, recorded in `INDEX.md`, that `build/outputs` holds mutually inconsistent artifacts. The streaming requirement follows from the measured 178.9 MB size of `proguard.map`, which is the same constraint that made the S1674 mapping reader use a `StreamReader` instead of loading the file.

**Verification:**

- `Grep` - `com.android.tools.build.obfuscation/proguard.map` present in the file.
- `Grep` - `debugsymbols` present in the file.
- `Grep` - `CopyTo` present; `Get-Content` does not appear anywhere in the extraction path.
- Run against the bundle on disk with `-DryRun`; the printed plan names `mapping.txt` and 12 `.so.dbg` entries, and the exit code is 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Bundle path implemented via ZipFile::OpenRead + CopyTo streaming into a .tmp staging zip moved into place. Dry-run against the real 85.8 MB AAB: mapping.txt 178901251 bytes + 12 .so.dbg entries across 4 ABIs, exit 0. No Get-Content anywhere in the file.

---

### Step 01.3 - Implement the loose-file source path

**Files:** `scripts/release/retain-deobfuscation.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> When `-Bundle` is absent, accept `-Mapping <path-to-mapping.txt>` and the optional `-NativeSymbols <path>`, which may name either a directory of `<abi>/<lib>.so.dbg` files or an AGP `native-debug-symbols.zip`. Write the same destination layout as the bundle path: `mapping.txt` at the root, symbols under `symbols/<abi>/`.
>
> Neither `-Bundle` nor `-Mapping` supplied is exit 2, not exit 1: nothing was inspected, so nothing can be asserted about retention.
>
> Record which source produced the payload - the literal `bundle` or `outputs` - so the manifest written in step 01.4 can carry it.

**Why:**

Strategic §3.3 scopes retention to the variants a release actually published, and only `standard` is built with `bundleStandardRelease`; every other flavor and `wear` is built by `assemble*Release` in `scripts/release/build-release-spectrum.ps1`, so no bundle exists to extract from and the mapping has to be taken from `build/outputs`. Recording the source kind keeps the weaker provenance of that path visible in the archive instead of letting it pass as bundle-grade evidence.

**Verification:**

- `Grep` - the script contains both literals `'bundle'` and `'outputs'` as source-kind values.
- Run with neither `-Bundle` nor `-Mapping`; exit code equals 2.
- Run with `-Mapping` pointing at `app_v2/build/outputs/mapping/standardRelease/mapping.txt` and `-DryRun`; exit code equals 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Loose-file path implemented; -NativeSymbols accepts a directory or an AGP native-debug-symbols.zip. No source = exit 2 (verified), -Mapping dry-run = exit 0 with 12 symbols resolved from the zip. Fixed a defect this step exposed: archive dir creation moved behind source validation into Initialize-ArchiveDir, so a failed invocation no longer leaves an empty release folder for the phase-04 gate to misread.

---

### Step 01.4 - Write the release manifest and guard idempotence

**Files:** `scripts/release/retain-deobfuscation.ps1`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> After the payload zip is written, update `<ArchiveRoot>\<VersionCode>\manifest.json` with one record per variant holding: `variant`, `versionName`, `versionCode`, `source` (`bundle` or `outputs`), `mappingSha256`, `mappingBytes`, `symbolCount`, `payloadBytes`, `storedUtc`. Merge into an existing manifest rather than replacing it, because variants of one release are stored by separate invocations.
>
> Make re-invocation safe. When a payload for this `versionCode` and `variant` already exists and its recorded `mappingSha256` equals the incoming one, print that it is already retained and exit 0 without rewriting. When the hashes differ, exit 1 and name both hashes unless `-Force` was passed.
>
> Compute the hash on the mapping stream while it is being copied, not by a second pass over the file.

**Why:**

Strategic §11 criterion 2 requires that the previous release's artifacts stay available after the next release, so an invocation that silently overwrote a stored payload would reintroduce exactly the overwrite loss the ticket exists to end, and strategic §7 lists "artifact stored but not matching the shipped build" as a risk whose consequence is worse than having nothing. The manifest is what makes strategic §11 criterion 3 checkable by Phase 04 without reopening every zip.

**Verification:**

- `Grep` - `manifest.json` present in the file.
- `Grep` - `mappingSha256` present in the file.
- Run twice against the same bundle and versionCode; the second run prints the already-retained message and exits 0.
- `manifest.json` parses as JSON and its record for `standard` carries a 64-character `mappingSha256`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Manifest + idempotence done. SHA-256 computed in the copy pass via CryptoStream and independently re-verified from the stored zip: 7afc7e01..66d2 matches. Re-run prints already-retained, exit 0. A differing mapping is refused with exit 1, short-circuited on byte size before any extraction. manifest.json parses, symbolCount 12, payloadBytes 22041196.

---

### Step 01.5 - Measure the retention cost against the real bundle

**Files:** `scripts/release/retain-deobfuscation.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Run the script for real against a release bundle on disk, into a throwaway archive root, and record wall-clock duration and resulting payload size in this phase's Handoff Notes - the numbers are the durable artifact, the scratch archive is not.
>
> If the run exceeds 120 seconds, do not background it and do not accept it: reduce the cost, and if it cannot be reduced below that, record the measurement in `dev/REFUTED_APPROACHES.md` together with the alternative that shipped.

**Why:**

Strategic §3.2 names release build time and disk space as the performance dimensions this ticket is measured on, and strategic §5.1 asserts the step costs one decompression pass rather than a recompression of 178.9 MB of text - an assertion that has to be measured rather than assumed, because `System.IO.Compression` offers no way to copy an entry without re-deflating it. Strategic §4 puts the expected payload at roughly 22 MB, so a result far from that means the extraction selected the wrong entries.

**Verification:**

Reproducing command, against any release bundle (`<aab>`) and a throwaway archive root (`<scratch>`):

```powershell
pwsh -NoProfile -File scripts/release/retain-deobfuscation.ps1 `
    -Variant standard -VersionCode <code> -VersionName <name> -Bundle <aab> -ArchiveRoot <scratch>
```

Expected result, as measured 2026-08-15 and recorded in the Handoff Notes below:

- Exit code 0, wall-clock under 120 s (measured 1.7 s).
- `<scratch>/<code>/standard-deobfuscation.zip` between 15 MB and 35 MB (measured 21.02 MB).
- `<scratch>/<code>/manifest.json` records `symbolCount` 12 and `mappingBytes` 178901251.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Measured: 1.7 s, payload 21.02 MB (22041196 bytes), mapping 178901251 bytes, symbolCount 12. Well under the 120 s threshold and within the 15-35 MB expected band; reproduces strategic section 4's independent ~22 MB estimate. Recorded in Handoff Notes.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Measurement, 2026-08-15, against the shipped bundle `app_v2-standard-release.aab` (85.8 MB, versionCode 260812204):

- Duration: **1.7 s** end to end, well inside the 120 s foreground threshold. The strategic §5.1 concern about "recompressing 179 MB of text" is real but cheap - `System.IO.Compression` offers no way to copy an entry without re-deflating it, and re-deflating it costs under two seconds, so no alternative was needed and nothing was recorded in `dev/REFUTED_APPROACHES.md`.
- Payload: **21.02 MB** (22,041,196 bytes) holding 13 files totalling 200,598,995 bytes raw. Strategic §4 predicted "about 22 MB" from an independent `unzip -v` measurement; this reproduces it.
- `mapping.txt`: 178,901,251 bytes, matching the bundle entry exactly.
- `symbolCount`: 12, across `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Hash provenance verified independently: the manifest's `mappingSha256` was recomputed by reopening the stored zip and hashing the decompressed entry, and matched. This proves the `CryptoStream` hashes the plaintext rather than the deflated bytes.

At this cost, strategic ADR-5's decision to keep every release without a pruning window is comfortable: a hundred releases occupy about 2.1 GB.

Invariant established: one command stores one variant's deobfuscation payload under a `versionCode` key, is safe to re-run, and refuses to overwrite a differing payload.

---

## Rollback Plan

Delete `scripts/release/retain-deobfuscation.ps1` and any throwaway archive root used while measuring. No release script calls it yet, so nothing else regresses.
