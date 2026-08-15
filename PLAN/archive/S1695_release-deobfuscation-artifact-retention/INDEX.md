# Tactical Plan: S1695 - release-deobfuscation-artifact-retention

**Strategic spec:** [`../S1695_release-deobfuscation-artifact-retention.md`](../S1695_release-deobfuscation-artifact-retention.md)
**Research inputs:** none (strategic §6 items all Resolved by the 2026-08-15 owner quiz)
**Feature:** Retention of R8 mapping and native debug symbols, keyed by versionCode
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | archive-store | - | ✅ Done | 5/5 | [PHASE_01__archive-store.md](PHASE_01__archive-store.md) |
| 02 | release-build-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__release-build-wiring.md](PHASE_02__release-build-wiring.md) |
| 03 | fetch-command | 01 | ✅ Done | 3/3 | [PHASE_03__fetch-command.md](PHASE_03__fetch-command.md) |
| 04 | retention-gate | 01, 03 | ✅ Done | 3/3 | [PHASE_04__retention-gate.md](PHASE_04__retention-gate.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 research item carries `Status: Resolved` (owner quiz 2026-08-15).

---

## Artifact source map (established during planning, 2026-08-15)

Measured against the release worktree `/p/ANDROID/FastMediaSorter_release` and the shipped bundle
`app_v2/build/outputs/bundle/standardRelease/app_v2-standard-release.aab` (85.8 MB, 2026-08-12 20:40):

- `standard` carries a real AAB, so both payloads come out of it - `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`
  (178,901,251 bytes raw) and `BUNDLE-METADATA/com.android.tools.build.debugsymbols/<abi>/*.so.dbg`
  across `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Every other flavor and `wear` is built with `assemble*Release` only (`scripts/release/build-release-spectrum.ps1:174-176`),
  so no bundle exists for them and their mapping must come from `build/outputs/mapping/<variant>/mapping.txt`.
- `build/outputs` artifacts are NOT mutually consistent: on the measured worktree
  `native-debug-symbols/standardRelease/` is dated 2026-07-07 while `mapping/standardRelease/` is dated 2026-08-12,
  because the native metadata task was UP-TO-DATE across the later build. This is the concrete reason strategic ADR-3
  prefers the bundle as the source wherever a bundle exists.
- `wear/build.gradle.kts` sets `isMinifyEnabled = true` but declares no `ndk` block, so wear retains a mapping and no symbols.
- Flavor ABI sets are subsets of `standard`'s four (`vr` = `arm64-v8a`, `noLegal` = `arm64-v8a` + `x86_64`), so the
  prebuilt libraries' symbols retained from the standard bundle cover them. `vr` additionally compiles its own OpenXR
  runtime through `externalNativeBuild.cmake`, whose symbols exist in no bundle.
- **Superseded during Phase 02.** The plan assumed the VR case needed a special branch reading CMake object files. It
  does not: `app_v2/build/intermediates/native_debug_metadata/<variant>/extract<Variant>NativeDebugMetadata/out/` is
  variant-keyed and holds the symbols for every native library in the variant, CMake-built and prebuilt alike - it is
  the very input AGP packs into the bundle's `debugsymbols` metadata. Retention therefore reads it uniformly for every
  non-standard flavor. The originally planned path, `intermediates/cmake/vrRelease/obj/`, is the pre-AGP-4.1 layout and
  does not exist; the modern CMake output under `.cxx/` is keyed by a build-type hash, not by variant, and so cannot be
  resolved from a flavor name at all.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, this ticket touches no Kotlin.
- [ ] `/spec-check S1695` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1695`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-15 - Initial tactical plan authored by `/spec-tech`.
