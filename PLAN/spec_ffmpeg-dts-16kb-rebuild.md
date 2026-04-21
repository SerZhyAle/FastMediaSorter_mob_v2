# Specification: COMPLIANCE.16KB — Permanent FFmpeg DTS 16 KB Rebuild And Re-enable

**Status:** Draft
**Date:** 2026-04-21
**Tier:** 3 — Moderate (4-8h, medium risk)
**Roadmap entry:** Ad hoc compliance hotfix requested by user — rebuild the custom FFmpeg DTS AAR for 16 KB page-size compatibility and re-enable it for Play-facing flavors.

---

## 1. Problem Statement

The checked-in `app_v2/libs/fms-ffmpeg-dts.aar` currently packages `libffmpegJNI.so` with ELF `LOAD Align=0x1000`, which makes Play Console reject APK/AAB artifacts targeting Android 15+ devices with the 16 KB page-size requirement. The project already contains a partial permanent solution in `scripts/builders/build-ffmpeg-dts.sh`, but the actual AAR in source control predates that fix, while the bootstrap script `temp/wsl2-phase1-setup.sh` still prepares an older r25c-oriented environment that does not match the current r27c requirement. A temporary workaround is already active in `app_v2/build.gradle.kts`: `standard`, `legacy`, and `vr` no longer package the DTS AAR, so DTS support is effectively disabled in Play-facing flavors until the native artifact is rebuilt correctly.

---

## 2. Goals

1. Rebuild `app_v2/libs/fms-ffmpeg-dts.aar` so that `jni/arm64-v8a/libffmpegJNI.so` and bundled FFmpeg `.so` libraries are 16 KB page-size compatible.
2. Align the WSL Phase 1 bootstrap with the current native build contract: Linux NDK r27c, `~/ffmpeg-android-build` workspace, and media3 1.2.1 source checkout expected by `scripts/builders/build-ffmpeg-dts.sh`.
3. Add a deterministic validation step that proves compatibility before Gradle packaging changes are reverted: `readelf -l` must show `LOAD` segments aligned to `0x4000` where required, and the rebuilt AAR must replace the stale one in `app_v2/libs/`.
4. Re-enable the DTS AAR only for the intended flavors in `app_v2/build.gradle.kts` after the rebuilt artifact is validated.
5. Produce final verification artifacts for both native and Gradle layers: rebuilt AAR metadata, APK/AAB contents check, and a successful Play-facing flavor build without 16 KB violations.

Non-goals for this spec: adding new codecs beyond the already approved DTS/extended-audio scope, changing player UI/UX, altering ExoPlayer renderer strategy beyond restoring the previous DTS-enabled path, or redesigning the legal flavor strategy established in `PLAN/spec_ffmpeg-custom-build-dts.md`.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | DTS must be restored only after rebuilt AAR passes 16 KB validation. Gated by `BuildConfig.ENABLE_DTS_DECODER`. |
| `lite`     | ❌ | Keeps `ENABLE_DTS_DECODER=false`; no DTS packaging planned. |
| `photos`   | ❌ | No audio playback pipeline; DTS AAR remains out of scope. |
| `legacy`   | ✅ | Re-enable after rebuilt AAR validation; keep legacy-specific ABI expectations under review. |
| `vr`       | ✅ | Play-facing VR build must regain DTS only after the rebuilt AAR is verified. |
| `vrUnlicensed` | ✅ | Already packages the local AAR; must be updated to the rebuilt artifact as the first verification target. |

Relevant build flag: `BuildConfig.ENABLE_DTS_DECODER` in `app_v2/build.gradle.kts` is the single feature gate that determines whether FFmpeg extension renderers are preferred in `PlayerSetupHelper.kt`.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | `legacy` still consumes the rebuilt AAR, so armeabi-v7a packaging and runtime smoke coverage must remain intact. |
| 26+ (standard minSdk) | Default path for `standard`, `vr`, and `vrUnlicensed`; arm64-v8a 16 KB compatibility is mandatory for Play-facing outputs. |
| 34+ (Android 14 / target 35 deployment path) | Play Console enforces 16 KB device compatibility for Android 15+ targets, so release artifacts must not include 4 KB-aligned native libs. |

### 3.3 Wear OS Impact

No Wear OS changes required.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `build-ffmpeg-dts.sh` | `scripts/builders/build-ffmpeg-dts.sh` | Main Linux/WSL native build pipeline for FFmpeg libs, JNI bridge, and final AAR packaging. |
| `build-ffmpeg-dts-wsl.ps1` | `scripts/builders/build-ffmpeg-dts-wsl.ps1` | Windows launcher for Phases 2-3; currently assumes Phase 1 was already prepared in WSL. |
| `wsl2-phase1-setup.sh` | `temp/wsl2-phase1-setup.sh` | Legacy WSL bootstrap script; still references NDK r25c and an outdated setup contract. |
| `build.gradle.kts` | `app_v2/build.gradle.kts` | Declares per-flavor `ENABLE_DTS_DECODER` flags and local AAR dependencies. |
| `PlayerSetupHelper.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt` | Enables extension renderers when `BuildConfig.ENABLE_DTS_DECODER` is true. |
| `fms-ffmpeg-dts.aar` | `app_v2/libs/fms-ffmpeg-dts.aar` | Current checked-in custom FFmpeg artifact; confirmed stale and not 16 KB compatible. |
| `spec_ffmpeg-custom-build-dts.md` | `PLAN/spec_ffmpeg-custom-build-dts.md` | Original DTS build and integration specification; now partially outdated on NDK/bootstrap details. |

The key architectural gap is not in the player code anymore, but in the native artifact supply chain: the code expects a valid rebuilt AAR, while the repository still carries an incompatible binary and an outdated WSL bootstrap path. Because of that mismatch, the project had to fall back to disabling DTS in Play-facing flavors instead of shipping the intended decoder pipeline.

---

## 5. Proposed Architecture

### 5.1 Permanent native-artifact supply chain for DTS

The fix should be implemented as a closed loop with four deterministic stages:

1. **Bootstrap WSL Phase 1 correctly**
   `temp/wsl2-phase1-setup.sh` must be upgraded to prepare the exact environment expected by `scripts/builders/build-ffmpeg-dts.sh`: Linux-visible NDK r27c, `~/ffmpeg-android-build/media`, matching FFmpeg checkout, and any minimum packages required by the build.

2. **Rebuild the native libs and JNI bridge**
   `scripts/builders/build-ffmpeg-dts.sh` remains the source of truth for Phase 2-3, including `-Wl,-z,max-page-size=16384` for FFmpeg and `APP_LDFLAGS` for `libffmpegJNI.so`.

3. **Validate before integration**
   Add an explicit verification contract to the workflow:

```text
WSL build output
  → extracted AAR temp dir
  → readelf -l jni/arm64-v8a/libffmpegJNI.so
  → assert no LOAD Align=0x1000 remains in the rebuilt arm64 artifact
  → only then replace app_v2/libs/fms-ffmpeg-dts.aar
```

4. **Re-enable Gradle flavor wiring**
   Restore `standard`, `legacy`, and `vr` to the local AAR only after the rebuilt artifact passes the native verification step and APK inspection confirms the rebuilt lib is what Gradle packages.

### 5.2 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `spec_ffmpeg-dts-16kb-rebuild.md` | `PLAN/` | ≤ 260 |
| `readelf` verification helper (optional script) | `scripts/builders/` or `temp/` | ≤ 80 |

No new application runtime classes are required. The work is confined to build scripts, build configuration, and validation helpers.

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | No Activity/Fragment changes required; runtime path already uses `BuildConfig.ENABLE_DTS_DECODER`. |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ✅ | No new app-layer classes expected. |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Runtime data flow remains unchanged; only build-time/native artifact generation changes. |
| No `Log.d()` — Timber only | ✅ | No runtime logging additions needed. Script output remains shell/PowerShell logging. |
| Room schema version incremented (if DB changes) | N/A | No database changes. |
| `StateFlow` for state, `SharedFlow` for one-shot events | N/A | No ViewModel state changes. |
| Hilt DI: new bindings declared in module file | N/A | No DI changes required. |

---

## 6. Data Flow

```text
Developer runs WSL Phase 1 bootstrap
  → temp/wsl2-phase1-setup.sh prepares Linux NDK r27c + ffmpeg-android-build workspace
  → scripts/builders/build-ffmpeg-dts-wsl.ps1 launches scripts/builders/build-ffmpeg-dts.sh
  → FFmpeg .so build + media3 JNI bridge build
  → package rebuilt app_v2/libs/fms-ffmpeg-dts.aar
  → extract rebuilt AAR to temp/ for readelf validation
  → if arm64 libffmpegJNI.so passes 16 KB alignment check
      → restore Gradle dependencies for standard/legacy/vr
      → assemble Play-facing flavors
      → inspect APK/AAB contents for rebuilt ffmpeg libs
  ←—— PlayerSetupHelper uses BuildConfig.ENABLE_DTS_DECODER on the restored flavors
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `scripts/builders/build-ffmpeg-dts.sh` | Keep as authoritative build pipeline; finalize any remaining bootstrap/package assumptions and optionally add built-in post-build `readelf` checks | ~390 lines |
| `scripts/builders/build-ffmpeg-dts-wsl.ps1` | Update Phase 1 guidance and success output to match the r27c workflow and restored Gradle instructions | ~110 lines |
| `temp/wsl2-phase1-setup.sh` | Replace r25c bootstrap with r27c-compatible Phase 1 setup contract expected by the main build script | ~140 lines |
| `app_v2/build.gradle.kts` | Revert temporary compliance fallback only after native validation succeeds; restore intended AAR dependency lines and `ENABLE_DTS_DECODER=true` where applicable | ~720 lines |
| `PLAN/spec_ffmpeg-custom-build-dts.md` | Update outdated sections to reference the new permanent 16 KB-compliant workflow after implementation stabilizes | ~650 lines |

Because `app_v2/build.gradle.kts` exceeds 500 lines, a timestamped backup in `temp/` is required before further implementation changes.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Rebuilt AAR still contains 4 KB-aligned `libffmpegJNI.so` despite updated linker flags | Med | Make `readelf -l` verification mandatory before copying the AAR into `app_v2/libs/` or re-enabling Gradle dependencies. |
| WSL bootstrap remains inconsistent with the main build script | High | Treat `scripts/builders/build-ffmpeg-dts.sh` as source of truth and update `temp/wsl2-phase1-setup.sh` to match it exactly. |
| Re-enabling the AAR breaks `standard` or `legacy` due to ABI/package regression | Med | Validate `vrUnlicensed` first, then `standard`, then `legacy`, with APK content inspection and runtime smoke checks. |
| Missing `media3-decoder-ffmpeg-1.2.1.aar` in WSL Gradle cache blocks packaging | Med | Document a pre-step that warms Gradle cache or copies the needed prebuilt AAR into a deterministic staging path. |
| Release build regains DTS but Play-facing outputs still fail hidden compliance checks | Med | Run both APK inspection and release/AAB validation before reverting the temporary fallback in mainline flavors. |

---

## 9. Testing Plan

### 9.1 Unit Tests

No new JVM unit tests are required for the core fix, because the failure is in native packaging rather than Kotlin business logic. Existing player/runtime tests should remain green after the Gradle wiring is restored.

Recommended regression checks:
- `CommandPanelLayoutPlannerTest`, `BrowseDialogHelperTest`, and other existing JVM tests continue to pass after `app_v2/build.gradle.kts` is reverted.
- If a helper script is added for native validation, keep it deterministic and shell-validated rather than introducing app-layer tests.

### 9.2 Manual Test Cases

1. Run WSL Phase 1 bootstrap from a clean environment and confirm `~/ffmpeg-android-build/media` and the Linux NDK r27c path exist afterward.
2. Run `scripts/builders/build-ffmpeg-dts-wsl.ps1` and confirm a new `app_v2/libs/fms-ffmpeg-dts.aar` is generated with a fresh timestamp.
3. Extract the rebuilt AAR into `temp/` and run `readelf -l` on `jni/arm64-v8a/libffmpegJNI.so`; verify the stale `Align 0x1000` pattern is gone.
4. Restore the AAR dependency for `vrUnlicensed`, assemble that flavor, and confirm the packaged APK contains `libffmpegJNI.so` from the rebuilt AAR rather than no FFmpeg lib at all.
5. Re-enable `standard`, `legacy`, and `vr`, then assemble the Play-facing outputs and confirm native-lib packaging succeeds.
6. Error-state scenario: if WSL packaging cannot find `media3-decoder-ffmpeg-1.2.1.aar`, verify the process fails early with a clear remediation step instead of silently producing a broken AAR.
7. Error-state scenario: if `readelf` still reports 4 KB alignment, verify the Gradle fallback remains in place and Play-facing flavors are not re-enabled prematurely.

### 9.3 Maestro E2E (if applicable)

No Maestro tests needed.

---

## 10. Accessibility

No accessibility changes. This work only affects native build artifacts, per-flavor packaging, and compliance validation.

---

## 11. User-Facing Feature Update

No FEATURES doc update required.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Keep the temporary DTS disablement until the rebuilt AAR is proven valid**
- **Decision:** Do not re-enable `standard`, `legacy`, or `vr` DTS packaging until the rebuilt arm64 AAR passes `readelf` validation.
- **Alternatives considered:** Re-enable flavors immediately after a successful rebuild command, trusting linker flags without artifact inspection.
- **Reason:** The repository already contains a script that looks correct on paper while the checked-in binary remains invalid. Artifact-level verification is the only safe gate.

**ADR-2: Align WSL Phase 1 to the current build script instead of preserving the old r25c bootstrap**
- **Decision:** Update `temp/wsl2-phase1-setup.sh` to match the r27c contract expected by `scripts/builders/build-ffmpeg-dts.sh`.
- **Alternatives considered:** Keep the legacy r25c setup and patch around the mismatch ad hoc during each rebuild.
- **Reason:** The current mismatch is the main reason the permanent fix is not reproducible. One authoritative setup path is lower risk than repeated manual repair.

**ADR-3: Validate the rebuilt artifact first on `vrUnlicensed` before restoring Play-facing flavors**
- **Decision:** Use `vrUnlicensed` as the first consumer of the rebuilt AAR.
- **Alternatives considered:** Restore `standard` or `vr` first.
- **Reason:** `vrUnlicensed` is already the only flavor packaging the AAR, so it provides the narrowest, least risky smoke-test path before reintroducing the artifact to Play-facing builds.

---

## 13. Implementation Steps

1. Create a timestamped backup of `app_v2/build.gradle.kts` in `temp/` before reverting the temporary compliance fallback.
2. Update `temp/wsl2-phase1-setup.sh` so it prepares Linux NDK r27c, the expected `~/ffmpeg-android-build` layout, and any required package prerequisites for the main build script.
3. Update `scripts/builders/build-ffmpeg-dts-wsl.ps1` so its prerequisite checks and user guidance match the new r27c Phase 1 flow.
4. Finalize `scripts/builders/build-ffmpeg-dts.sh` so packaging prerequisites are explicit and a post-build verification step can run deterministically.
5. Run Phase 1 in WSL and verify the expected workspace exists before attempting the rebuild.
6. Run the DTS rebuild and replace `app_v2/libs/fms-ffmpeg-dts.aar` only with the freshly validated artifact.
7. Extract the rebuilt AAR to `temp/` and validate `jni/arm64-v8a/libffmpegJNI.so` with `readelf -l`; capture the result in a temporary log artifact under `temp/`.
8. Restore `vrUnlicensedImplementation(files("libs/fms-ffmpeg-dts.aar"))` smoke coverage if any temporary restrictions were added there, then assemble `vrUnlicensed` or the closest available verification target and inspect packaged native libs.
9. Revert the temporary fallback in `app_v2/build.gradle.kts` for `standard`, `legacy`, and `vr`: restore the local AAR dependencies and set `ENABLE_DTS_DECODER=true` only after the native validation passes.
10. Assemble `standard`, `legacy`, and `vr` outputs and inspect APK/AAB contents for the rebuilt FFmpeg libs.
11. Update `PLAN/spec_ffmpeg-custom-build-dts.md` so its NDK/bootstrap sections reflect the implemented permanent workflow rather than the stale r25c instructions.
12. Run `.\scripts\add_to_dev_log.ps1` for every modified file, including the rebuilt spec updates and any build-script changes.

Mandatory step checklist at the end:
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated (if user-facing)
- [ ] Room DB migration added + version incremented (if DB schema changes)
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- Re-evaluating additional codec scope such as AV1/VPX source builds beyond the already documented placeholders.
- Changing the legal channel strategy for DTS distribution beyond restoring the previously approved flavor matrix.
- Any player UI polish, track-picker UX changes, or new runtime diagnostics unrelated to the 16 KB compliance rebuild.
- CI automation of the native rebuild; this spec only establishes the local reproducible workflow and validation gate.