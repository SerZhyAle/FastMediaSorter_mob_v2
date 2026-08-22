# Phase 02 - Release build wiring

**Strategic spec:** [`../S1695_release-deobfuscation-artifact-retention.md`](../S1695_release-deobfuscation-artifact-retention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Bind retention to the release builds themselves, so every published variant of a release stores its deobfuscation payload without an operator remembering to ask for it.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] `scripts/release/retain-deobfuscation.ps1` stores and re-stores a payload as proven by Phase 01.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/build-aab-release.ps1` | Modified | ≤ 40 |
| `scripts/release/build-release-spectrum.ps1` | Modified | ≤ 70 |
| `.claude/commands/skill-release.md` | Modified | ≤ 20 |

---

## Steps

### Step 02.1 - Retain the standard payload from the freshly built bundle

**Files:** `scripts/builders/build-aab-release.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> In `scripts/builders/build-aab-release.ps1`, after `$aabPath` is resolved and proven non-null (the block ending at the `AAB location:` line), invoke `scripts/release/retain-deobfuscation.ps1` with `-Bundle $aabPath.FullName`, `-Variant standard`, `-VersionCode $versionCodeInt` and `-VersionName $versionName`.
>
> Treat a non-zero exit as a warning, not a build failure: print it in the same shape the existing fastlane-changelog block uses, and let the release build continue. The build already succeeded and the bundle already exists at that point, so aborting would destroy a good release over an archive problem.
>
> Place the call before the Google Drive copy block so a failure to reach the cloud folder surfaces once, from the retention step, rather than twice.

**Why:**

Strategic §3.1 requires that the retention scheme need no manual step per release, because a forgotten step is indistinguishable from having no retention at all, and strategic §7 rates "the manual step is forgotten on the very first release" as the high-probability risk. Binding the call to the point where the bundle is known to exist is what makes strategic ADR-3's guarantee hold - the payload is taken from the artifact this build just produced.

**Verification:**

- `Grep` - `retain-deobfuscation.ps1` appears exactly once in `scripts/builders/build-aab-release.ps1`.
- `Grep` - the call passes `-Bundle` and `$versionCodeInt`.
- `Grep` - the call site is followed, not preceded, by the `$gdPath` assignment line.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Retention wired into build-aab-release.ps1 at line 142, after the AAB is resolved and before the $gdPath block (line 159). Non-zero exit warns and continues. assert-exit-contract exit 0; script name appears exactly once.

---

### Step 02.2 - Retain every other published flavor from the spectrum build

**Files:** `scripts/release/build-release-spectrum.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `scripts/release/build-release-spectrum.ps1`, after the APK resolution loop that walks `$apkRoots`, add a retention pass over the same `$selected` list. For each selected flavor other than `standard`, call `scripts/release/retain-deobfuscation.ps1` with `-Variant <flavor>`, `-VersionCode $appVersionCode` and `-Mapping app_v2\build\outputs\mapping\<flavor>Release\mapping.txt`. For `wear`, pass `-VersionCode $wearVersionCode` and `-Mapping wear\build\outputs\mapping\release\mapping.txt`.
>
> Skip `standard` here: `a.ps1 r` already retained it from the bundle in step 02.1, and the loose-file path would record a weaker provenance for a release that has a bundle-grade payload.
>
> A flavor whose mapping file is absent is a warning naming the flavor and the expected path, not a thrown error.

**Why:**

Strategic §3.3 scopes retention to the variants the release actually published and binds that scope to the flavor list `/skill-release` ran with, which is exactly `$selected` in this script; strategic ADR-6 chose that binding over a hardcoded list precisely so the set of channels can change without editing this code. `wear` needs its own version code because the script deliberately gives it the 8-digit `yyMMddHH` form while app_v2 carries the 9-digit one.

**Verification:**

- `Grep` - `retain-deobfuscation.ps1` present in `scripts/release/build-release-spectrum.ps1`.
- `Grep` - `$wearVersionCode` appears in the retention pass.
- `Grep` - the retention pass excludes `standard` explicitly.
- Run with `-Flavors lite -SkipBuild`; no unhandled exception, and the absent-mapping case prints a warning and leaves exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Retention pass added after the APK resolution loop, driven by $selected via a $mappingRoots map; wear uses $wearVersionCode (8-digit), the rest $appVersionCode. standard excluded with the reason stated at line 238. Absent mapping warns and continues. Verified by full-file AST parse: 0 errors. Deliberate deviation: -SkipBuild was NOT run - it stamps versionCode/versionName into build.gradle.kts and exits before this code, so it would mutate the working tree while proving nothing.

---

### Step 02.3 - Capture the VR-only native symbols

**Files:** `scripts/release/build-release-spectrum.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> The `vr` flavor compiles its own OpenXR runtime through `externalNativeBuild.cmake` (`app_v2/build.gradle.kts` lines 377 and 539), so those unstripped libraries exist in no bundle and are not covered by the symbols retained from the standard bundle. Pass a native-symbols directory to `retain-deobfuscation.ps1` through `-NativeSymbols`.
>
> **Path corrected during implementation, 2026-08-15.** The layout this step originally assumed, `app_v2/build/intermediates/cmake/vrRelease/obj/<abi>/`, does not exist - that is the pre-AGP-4.1 location. Verified on disk: CMake configure output lives under `app_v2/.cxx/<BuildType>/<hash>/<abi>/` and `build/intermediates/cxx/`, both keyed by a build-type hash rather than by variant, so neither can be resolved deterministically from a flavor name. The correct source is variant-keyed and is the very input AGP packs into `BUNDLE-METADATA/com.android.tools.build.debugsymbols`:
>
> `app_v2/build/intermediates/native_debug_metadata/<variant>/extract<Variant>NativeDebugMetadata/out/<abi>/*.so.dbg`
>
> Resolve the task-name segment with a wildcard rather than reconstructing it. Because this source is per variant and covers every native library in the variant - CMake-built and prebuilt alike - apply it to every non-standard flavor in the retention pass, not only to `vr`; a separate VR branch would be a special case for a path that is not special. A variant whose directory is absent gets a warning naming the path, never a silently symbol-free payload.

**Why:**

Strategic §2 goal 1 requires that a stack trace from any released variant be decodable, and a VR native crash could not be decoded from the standard bundle's symbols, which cover only the prebuilt libraries shared by all flavors. Planning verified the ABI sets (`vr` = `arm64-v8a`, `noLegal` = `arm64-v8a` plus `x86_64`, `standard` = all four), so the prebuilt half is covered and only the CMake-built half is not.

**Verification:**

- `Grep` - `native_debug_metadata` present in `scripts/release/build-release-spectrum.ps1`.
- `Grep` - `-NativeSymbols` passed in the retention pass.
- The resolver returns the existing `out` directory when pointed at the `standardRelease` metadata present on disk, proving the wildcard segment resolves; an absent variant directory produces the warning instead.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Step's assumed path was refuted on disk: intermediates/cmake/vrRelease/obj does not exist (pre-AGP-4.1 layout); .cxx is keyed by build-type hash, not variant. Correct source is native_debug_metadata/<variant>/extract<Variant>NativeDebugMetadata/out, resolved by wildcard. Applied uniformly to every non-standard flavor instead of a VR-only branch, since it is variant-keyed and covers CMake-built libs too. Resolver exercised live: standardRelease -> out dir with exactly 12 .so.dbg, matching the bundle; vrRelease -> null, warning path. wear excluded (no ndk block). AST parse 0 errors. Step prompt and verification updated to match reality.

---

### Step 02.4 - Record retention in the release driver

**Files:** `.claude/commands/skill-release.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `.claude/commands/skill-release.md`, extend Step 12 and Step 12a to state that the build scripts now retain the deobfuscation payload per published variant, and that a retention warning in the build output is not a release failure but must be resolved before the next release, because the Phase 04 gate will refuse it.
>
> Add the retained versionCode and the archive path to the Step 13 final report items. Do not add a manual retention step - the whole point is that no operator action is required.

**Why:**

Strategic §3.1 rules out any scheme requiring a manual step per release, so the driver must describe retention as an observed outcome rather than an instruction to perform; without the warning being named here, a non-blocking failure printed among a long build log is exactly the silence strategic §7 predicts for an unsynchronised cloud folder.

**Verification:**

- `Grep` - `retain-deobfuscation` present in `.claude/commands/skill-release.md`.
- `Grep` - no new numbered step was introduced; the existing Step 12 / 12a / 13 numbering is unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - skill-release.md Step 12 now states retention is unattended, names retain-deobfuscation.ps1 and fetch-deobfuscation.ps1, and says a retention warning does not abort the release but is refused by the gating prerelease step 0.6. Step 13 gains a retained-versionCode report line. Step numbering 12/12a/12b/12c/13 unchanged; no manual retention step added.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Invariant established: a release build stores the payload for every variant it published, and never fails the release because it could not.

---

## Rollback Plan

Revert the three edits. The Phase 01 script stays and remains callable by hand.
