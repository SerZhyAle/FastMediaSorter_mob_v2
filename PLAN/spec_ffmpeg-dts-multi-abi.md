# Specification: Multi-ABI FFmpeg/DTS AAR Build

**Status:** Implemented (2026-04-22)
**Date:** 2026-04-22
**Tier:** 3 — Moderate (medium risk)
**Roadmap entry:** Build-pipeline extension — successor to `temp/spec_done/spec_ffmpeg-custom-build-dts.md` §7. Goal: produce `fms-ffmpeg-dts.aar` with native libs for all four production ABIs so that DTS / APE / WMA / WavPack / TTA / DSD decoding works on every device covered by the restored `abiFilters` in [app_v2/build.gradle.kts](app_v2/build.gradle.kts).

---

## 1. Problem Statement

`app_v2/libs/fms-ffmpeg-dts.aar` currently ships a single native slice, `jni/arm64-v8a/libffmpegJNI.so` (7.6 MB). After the recent build-rule fix that re-enabled `armeabi-v7a`, `x86`, and `x86_64` in [app_v2/build.gradle.kts](app_v2/build.gradle.kts#L388-L401) (recovering ~5 234 Play Console devices), users on 32-bit ARM phones, TV boxes, car head units, older Chromebooks, and x86 emulators receive an APK without the FFmpeg renderer. ExoPlayer silently falls back to MediaCodec for those users, so DTS, APE, WMA, WavPack, TTA, and DSD files stop playing with no diagnostic. The build script [scripts/builders/build-ffmpeg-dts.sh](scripts/builders/build-ffmpeg-dts.sh) is hard-coded to a single ABI (`ABIS=("arm64-v8a")` at line 41) and lacks case arms for x86/x86_64.

---

## 2. Goals

1. Produce a single `fms-ffmpeg-dts.aar` containing `jni/<abi>/libffmpegJNI.so` for all four production ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
2. Every slice must be 16 KB page-size aligned (ELF `LOAD Align=0x4000`) — the same Play Store requirement enforced today for arm64.
3. Keep the same codec inventory as the current single-ABI build (DTS, AC-3, E-AC-3, TrueHD, AAC, MP3, FLAC, ALAC, PCM family, APE, WMA v1/v2, WavPack, TTA, DSD, Musepack) — no new codecs introduced.
4. Extend `verify_16kb_alignment` in the build script to loop across all ABIs, failing the build if any slice is non-compliant.
5. Extend `check_deps` to require `nasm` (only needed once we enable `x86asm`).
6. Keep Gradle side untouched: `implementation(files("libs/fms-ffmpeg-dts.aar"))` in `standard`, `legacy`, `vr`, `vrUnlicensed` — AGP auto-splits per-ABI at AAB assembly.
7. Preserve VR flavor behavior: flavor-level `abiFilters = ["arm64-v8a"]` on `vr` and `vrUnlicensed` intersects with the multi-ABI AAR so non-arm64 slices are stripped from the final VR AAB — zero size impact on Quest users.

**Non-goals for this spec:**

- No new codecs beyond the current set.
- No media3 version bump (stays on 1.2.1).
- No `libavdevice`, `libswscale`, `libpostproc`, or Vulkan paths.
- No libgav1 (AV1) / libvpx (VP9) software decoders — separate commented-out deps in [app_v2/build.gradle.kts](app_v2/build.gradle.kts#L703-L715).
- No change to VR distribution strategy or ADR-004 from the predecessor spec.
- No runtime code changes in the player (FfmpegAudioRenderer is already loaded via reflection; missing `.so` degrades gracefully).
- No changes to `standardImplementation`/`legacyImplementation`/`vrImplementation`/`vrUnlicensedImplementation` lines.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | All four ABI slices delivered via AAB per-ABI split |
| `lite` | ❌ | `ENABLE_DTS_DECODER = false`; no FFmpeg AAR dependency |
| `photos` | ❌ | `ENABLE_DTS_DECODER = false`; no FFmpeg AAR dependency |
| `legacy` | ✅ | Same four slices; minSdk=23 but JNI bridge does not reference API 24+ symbols |
| `vr` | ⚠️ | Consumes multi-ABI AAR but flavor-level `abiFilters=["arm64-v8a"]` strips non-arm64 slices — no runtime change |
| `vrUnlicensed` | ⚠️ | Same as `vr` |

No new `BuildConfig` flag required. `ENABLE_DTS_DECODER` already gates the renderer-mode call in [PlayerSetupHelper.kt:53](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt#L53).

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23-25 (legacy minSdk) | FFmpeg built against API 26 sysroot still runs; JNI bridge restricts itself to symbols available in API 23. Verify at link stage via `readelf --dyn-syms libffmpegJNI.so \| grep UND`  — no API 24+ UND symbols expected |
| 26+ (standard minSdk) | Default path. `API_LEVEL=26` in the build script is the canonical target |

No further API-level forks: the AAR contains a self-contained FFmpeg static link, no system codec APIs are called.

### 3.3 Wear OS Impact

No Wear OS changes required. The `wear/` module does not depend on `fms-ffmpeg-dts.aar` and does not play DTS content.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| Build script (bash) | [scripts/builders/build-ffmpeg-dts.sh](scripts/builders/build-ffmpeg-dts.sh) | End-to-end: clone FFmpeg + media3, configure/build per ABI, package AAR, verify 16 KB alignment |
| Windows wrapper | [scripts/builders/build-ffmpeg-dts-wsl.ps1](scripts/builders/build-ffmpeg-dts-wsl.ps1) | Invokes the bash script inside WSL2 Ubuntu |
| Output AAR | [app_v2/libs/fms-ffmpeg-dts.aar](app_v2/libs/fms-ffmpeg-dts.aar) | 7.6 MB, contains `jni/arm64-v8a/libffmpegJNI.so`, `classes.jar`, `AndroidManifest.xml` (classes.jar sourced from stock `media3-decoder-ffmpeg-1.2.1.aar` via Gradle cache or Maven Central) |
| Gradle wiring | [app_v2/build.gradle.kts:724-727](app_v2/build.gradle.kts#L724-L727) | `implementation(files("libs/fms-ffmpeg-dts.aar"))` for `standard`, `legacy`, `vr`, `vrUnlicensed` |
| Release ABI filter | [app_v2/build.gradle.kts:388-401](app_v2/build.gradle.kts#L388-L401) | `abiFilters = ["arm64-v8a","armeabi-v7a","x86","x86_64"]` |
| VR flavor ABI filter | [app_v2/build.gradle.kts:208](app_v2/build.gradle.kts#L208), [app_v2/build.gradle.kts:258](app_v2/build.gradle.kts#L258) | `abiFilters += ["arm64-v8a"]` — intersects with release filter, strips other slices |
| Player wiring | [PlayerSetupHelper.kt:47-56](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt#L47-L56) | Enables extension-renderer mode when `BuildConfig.ENABLE_DTS_DECODER` is true |

**Gap:** `ABIS=("arm64-v8a")` on [scripts/builders/build-ffmpeg-dts.sh:41](scripts/builders/build-ffmpeg-dts.sh#L41). `build_ffmpeg_abi` has a case arm for `armeabi-v7a` (currently unused) but none for `x86` / `x86_64`. `verify_16kb_alignment` extracts and inspects only the `arm64-v8a` slice ([scripts/builders/build-ffmpeg-dts.sh:492-507](scripts/builders/build-ffmpeg-dts.sh#L492-L507)).

---

## 5. Proposed Architecture

### 5.1 Multi-ABI build loop

The top-level `main()` already iterates over `ABIS` for both `build_ffmpeg_abi` and `build_jni_bridge`. The change is mechanical:

1. Expand `ABIS` from a single element to four: `ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")`.
2. Add `x86` and `x86_64` arms to the case block in `build_ffmpeg_abi`:

```bash
x86)
    host_triple="i686-linux-android"
    arch="i686"
    extra_flags="--disable-asm"   # fallback until nasm confirmed; see 5.2
    ;;
x86_64)
    host_triple="x86_64-linux-android"
    arch="x86_64"
    extra_flags=""                # x86_64 NDK toolchain includes nasm-compatible assembler path
    ;;
```

3. Re-enable `armeabi-v7a` arm (already present — it just needs to stop being skipped). Existing flags `--cpu=armv7-a --enable-neon` are correct.

### 5.2 SIMD / nasm for x86 ABIs

FFmpeg's configure uses `--disable-x86asm` globally today (line 246 of the script) because the arm targets don't need it. For x86/x86_64 slices, x86asm provides large perf wins on h.264/HEVC/VP9 paths — but we are an **audio-only** FFmpeg build (we disabled video decoders in configure), so asm is a size/complexity cost without a compensating benefit.

**Decision (see ADR-1):** keep `--disable-x86asm` for x86/x86_64 too. This removes the `nasm` dependency entirely and keeps the build reproducible. We pay a small CPU cost only on DTS/APE/WMA decoding — well within headroom for real-time audio on any Android-8+ x86 device.

Consequence: `check_deps` does **not** need a new tool. Drop step 5 of the original task list.

### 5.3 Per-ABI 16 KB alignment verification

Rewrite `verify_16kb_alignment` to loop across `ABIS` rather than hard-coding arm64:

```bash
verify_16kb_alignment() {
    local aar_path="$OUT_DIR/fms-ffmpeg-dts.aar"
    local verify_dir="$WORK_DIR/aar-verify-16kb"
    rm -rf "$verify_dir" && mkdir -p "$verify_dir"

    # Extract all .so files from the AAR
    AAR_PATH="$aar_path" VERIFY_DIR="$verify_dir" python3 - <<'PYEOF'
import zipfile, os
for n in zipfile.ZipFile(os.environ['AAR_PATH']).namelist():
    if n.endswith('.so'):
        zipfile.ZipFile(os.environ['AAR_PATH']).extract(n, os.environ['VERIFY_DIR'])
PYEOF

    local fail=0
    for abi in "${ABIS[@]}"; do
        local so="$verify_dir/jni/$abi/libffmpegJNI.so"
        [[ -f "$so" ]] || { echo "FAIL: missing $abi slice"; fail=1; continue; }
        local load; load=$(readelf -l "$so" | grep -E '^\s+LOAD\s+')
        if echo "$load" | grep -qE '\s0x1000\s*$'; then
            echo "FAIL: $abi has 4 KB LOAD alignment"; fail=1
        elif echo "$load" | grep -qE '\s0x4000\s*$'; then
            echo "[OK] $abi — 16 KB aligned"
        else
            echo "WARN: $abi — alignment not confirmed (inspect manually)"
        fi
    done
    [[ $fail -eq 0 ]] || exit 1
}
```

### 5.4 Linker flag enforcement

The 16 KB alignment requires `-Wl,-z,max-page-size=16384` on every link step. This is already set in two places and applies to all ABIs once they are in the loop:

- FFmpeg configure: `--extra-ldflags="-lm -Wl,-z,max-page-size=16384"` ([scripts/builders/build-ffmpeg-dts.sh:248](scripts/builders/build-ffmpeg-dts.sh#L248))
- CMake JNI bridge: `-DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384"` ([scripts/builders/build-ffmpeg-dts.sh:314](scripts/builders/build-ffmpeg-dts.sh#L314))

No change needed — the flags are ABI-agnostic.

### 5.5 Package assembly

`package_aar` already iterates `ABIS`:

```bash
for abi in "${ABIS[@]}"; do
    jni_so=$(find "$WORK_DIR/jni-build/$abi" -name "libffmpegJNI.so" 2>/dev/null | head -1)
    if [[ -n "$jni_so" ]]; then
        mkdir -p "$staging/jni/$abi"
        cp "$jni_so" "$staging/jni/$abi/"
    else
        echo "WARN: libffmpegJNI.so not found for $abi"
    fi
done
```

When all four `jni-build/<abi>/libffmpegJNI.so` exist, packaging produces a 4-slice AAR automatically. No edits needed here.

### 5.6 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| (none — build-script change only) | — | — |

### 5.7 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | No app-side code changes |
| New classes follow naming conventions | N/A | No new classes |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Unchanged |
| No `Log.d()` — Timber only | ✅ | Build script only; outputs to stdout |
| Room schema version incremented | N/A | No DB changes |
| `StateFlow`/`SharedFlow` conventions | N/A | No state changes |
| Hilt DI bindings | N/A | No new bindings |
| File size ≤ 1000 lines | ✅ | `build-ffmpeg-dts.sh` currently 576 lines; estimated +40 lines after edits → ~620 lines |
| 16 KB page-size alignment for every `.so` | ✅ | Enforced by extended `verify_16kb_alignment` |

---

## 6. Data Flow

```
WSL2 Ubuntu host
   │
   ├─► clone_sources() ──► FFmpeg release/6.0, media3 1.2.1
   │
   ├─► for abi in arm64-v8a armeabi-v7a x86 x86_64:
   │       ├─► build_ffmpeg_abi(abi) ──► ffmpeg-out/<abi>/lib/{libavcodec,libavformat,libavutil,libswresample}.a
   │       └─► build_jni_bridge(abi) ──► jni-build/<abi>/libffmpegJNI.so
   │
   ├─► package_aar() ──► aar-staging/jni/<abi>/libffmpegJNI.so ×4
   │                     + classes.jar (from media3-decoder-ffmpeg-1.2.1.aar)
   │                     + AndroidManifest.xml
   │                   └─► zip into app_v2/libs/fms-ffmpeg-dts.aar
   │
   └─► verify_16kb_alignment() ──► readelf -l ×4; fail build if any LOAD Align != 0x4000

AGP at AAB assembly (Windows host, gradle)
   │
   ├─► standard / legacy flavors
   │       └─► AAB contains 4 per-ABI slices; Play delivers one slice per user
   │
   └─► vr / vrUnlicensed flavors  (abiFilters intersect = [arm64-v8a])
           └─► AAB contains 1 slice; x86/x86_64/armeabi-v7a .so stripped
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| [scripts/builders/build-ffmpeg-dts.sh](scripts/builders/build-ffmpeg-dts.sh) | Expand `ABIS`; add x86/x86_64 case arms; rewrite `verify_16kb_alignment` as loop; update comments | ~620 lines |
| [scripts/builders/build-ffmpeg-dts-wsl.ps1](scripts/builders/build-ffmpeg-dts-wsl.ps1) | Review whether WSL disk/RAM requirements need bumping (×4 FFmpeg builds run serially — disk goes from ~2 GB to ~8 GB under `ffmpeg-android-build/`) | minor |
| [app_v2/libs/fms-ffmpeg-dts.aar](app_v2/libs/fms-ffmpeg-dts.aar) | Regenerated output — contains 4 slices | ~30 MB (binary, not tracked line-wise) |

Gradle side unchanged:

| File | Reason not modified |
|------|---------------------|
| [app_v2/build.gradle.kts](app_v2/build.gradle.kts) | Dependency lines already reference `files("libs/fms-ffmpeg-dts.aar")`; AGP auto-splits by ABI. `abiFilters` in release buildType already set. |

No source file is modified or exceeds 500 lines — no backup step required.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| FFmpeg configure rejects x86/x86_64 target with current codec flags | Low | Dry-run each target before merging — FFmpeg supports all enabled decoders across arches; `--disable-x86asm` removes the nasm dependency that would otherwise be ABI-specific |
| NDK r27c x86/x86_64 sysroot missing on WSL2 setup | Low | `temp/wsl2-phase1-setup.sh` installs the full NDK; if `toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/i686-linux-android/26/` is absent, the configure fails fast with a clear error |
| 16 KB LOAD alignment not produced on x86 slices | Low | `-Wl,-z,max-page-size=16384` is architecture-agnostic in lld 16+; extended `verify_16kb_alignment` catches regressions before the AAR ships |
| AAR size breaches Play Store per-AAB upload cap | Very Low | Play's AAB cap is 200 MB; projected AAR ~30 MB fits comfortably. Per-user delivery stays ~8 MB (one slice) |
| armeabi-v7a Thumb2/ARM mode mismatch with JNI bridge | Medium | Existing script comment at line 39-40 flags this as a prior issue. Mitigation: build with `--cpu=armv7-a --enable-neon` (already in script) and verify JNI bridge CMake uses same ABI mode via `-DANDROID_ABI=armeabi-v7a` (NDK default: Thumb2). Run smoke test on a real armeabi-v7a device before release |
| Legacy flavor (minSdk=23) hits unresolved symbol on API 23 because FFmpeg built against API 26 sysroot | Low | JNI bridge and FFmpeg do not call `android-24+` symbols — only libc/libm/libdl (all API 21+). Verify post-build: `readelf --dyn-syms jni/<abi>/libffmpegJNI.so \| grep UND` must not list any API 24+ symbols |
| VR AAB grows unexpectedly because AGP fails to strip non-arm64 slices | Low | `abiFilters` intersection is enforced at packaging time; verify post-build: `bundletool extract-apks` on vr AAB must show only arm64-v8a slice. If not, flavor-level abiFilter can be re-asserted at buildType level for vr |
| Build time ×4 makes iteration painful | Accepted | Script already supports `[[ -f .../libavcodec.a ]] && [[ SKIP ]]` for incremental re-runs. No mitigation needed beyond that |

---

## 9. Testing Plan

### 9.1 Unit Tests

No unit tests required. This is a build-pipeline change with no Kotlin/Java surface. The existing `PlayerSetupHelper` logic (which consumes the AAR) is unaffected and is already covered by integration/manual testing.

### 9.2 Manual Test Cases

1. **Build success, all four slices present:** run `.\scripts\builders\build-ffmpeg-dts-wsl.ps1` in WSL2. Verify `fms-ffmpeg-dts.aar` contains exactly `jni/arm64-v8a/libffmpegJNI.so`, `jni/armeabi-v7a/libffmpegJNI.so`, `jni/x86/libffmpegJNI.so`, `jni/x86_64/libffmpegJNI.so` via `python -m zipfile -l app_v2/libs/fms-ffmpeg-dts.aar`.
2. **16 KB alignment per slice:** extend `verify_16kb_alignment` prints `[OK] <abi> — 16 KB aligned` four times. Manually re-check: `readelf -l /tmp/aar-verify/jni/<abi>/libffmpegJNI.so | grep LOAD` — every LOAD row ends in `0x4000`.
3. **Gradle build (standard):** `.\gradlew.bat bundleStandardRelease`. Inspect output AAB with `bundletool dump resources` or `python -m zipfile -l`: expect four `base/lib/<abi>/libffmpegJNI.so` entries.
4. **Gradle build (legacy):** `.\gradlew.bat bundleLegacyRelease`. Same inspection as #3.
5. **Gradle build (vr):** `.\gradlew.bat bundleVrRelease`. Inspect output AAB: expect **only one** `base/lib/arm64-v8a/libffmpegJNI.so` — confirms flavor-level abiFilter stripped x86/x86_64/armeabi-v7a.
6. **Per-ABI device playback — happy path:**
   - Install `standardRelease` split for `arm64-v8a` on a Pixel → play DTS MKV → audio plays.
   - Install `standardRelease` split for `armeabi-v7a` on an older ARMv7 device (e.g. Samsung Galaxy Tab A 7", TV box with Amlogic S905W32) → play DTS MKV → audio plays.
   - Install `standardRelease` split for `x86_64` on a Chromebook or Android Studio x86_64 emulator → play DTS MKV → audio plays.
   - Install `standardRelease` split for `x86` on an API 27 x86 AVD → play DTS MKV → audio plays.
7. **Error state — corrupt DTS track:** play an MKV with a deliberately truncated DTS track on each ABI. Player must fail gracefully with the existing silent-video + toast fallback (see [PlayerSetupHelper.kt:47-56](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt#L47-L56) behavior), not crash.
8. **Error state — missing slice simulation:** manually delete `jni/armeabi-v7a/libffmpegJNI.so` from the AAR, rebuild app, install on armeabi-v7a device. Player must start, DTS files must fail gracefully (ExoPlayer swallows `UnsatisfiedLinkError` from `FfmpegAudioRenderer` init — this is the existing fallback path). Confirms degradation is still clean if a slice is ever missing.
9. **VR regression:** install `vrRelease` on Quest 3 → play existing DTS sample → audio plays. No regression vs current arm64-only AAR.
10. **Legacy flavor on API 23:** install `legacyRelease` for `armeabi-v7a` on an API 23 device → launch app → no UnsatisfiedLinkError at startup. Play DTS if hardware supports.

### 9.3 Maestro E2E

No Maestro tests needed. DTS audio playback requires real device hardware and specific media files; Maestro runs on CI emulators without that media present. Manual tests 6-10 cover device verification.

---

## 10. Accessibility

No accessibility changes. This is a native-library packaging change with zero UI surface — no new views, layouts, strings, or user-facing affordances.

---

## 11. User-Facing Feature Update

No FEATURES doc update required. The feature set (DTS / APE / WMA / WavPack / TTA / DSD playback) is already documented as a capability of the standard/legacy/VR editions. This spec restores that capability for non-arm64 users without adding new user-visible functionality. The existing VR limitations entry in [docs/LIMITATIONS.md](docs/LIMITATIONS.md) already notes the arm64 / XR constraint for VR flavors.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Keep `--disable-x86asm` for x86 and x86_64 FFmpeg builds.**
- **Decision:** Do not enable x86asm even though it would improve SIMD perf.
- **Alternatives considered:** (a) enable x86asm + require `nasm` on the WSL2 host; (b) accept no SIMD.
- **Reason:** Our FFmpeg build is audio-only (video decoders disabled in configure). x86asm gains are largest for H.264/HEVC/VP9 paths that we don't ship. For audio codecs, real-time decoding is comfortably within any Android-8+ x86 device's CPU budget. Skipping x86asm keeps the WSL2 prerequisite list identical across all ABIs — easier docs, easier onboarding.

**ADR-2: Ship four ABI slices in one AAR rather than four separate AARs.**
- **Decision:** Single `fms-ffmpeg-dts.aar` containing `jni/<abi>/libffmpegJNI.so` for each ABI.
- **Alternatives considered:** per-ABI AARs wired via `<abi>Implementation(files(...))` per Gradle flavor/ABI matrix.
- **Reason:** AGP's AAB pipeline already splits per-ABI automatically when one AAR contains multiple slices. Per-ABI AARs would force Gradle coordination for every download/install combination and break the simple `files("libs/fms-ffmpeg-dts.aar")` declaration. One AAR = one Gradle line = one artefact in Git-LFS (if we ever adopt it).

**ADR-3: Let VR flavors consume the full multi-ABI AAR instead of producing a separate VR-only AAR.**
- **Decision:** VR flavors use the same `fms-ffmpeg-dts.aar` as standard/legacy.
- **Alternatives considered:** maintain `fms-ffmpeg-dts-arm64.aar` alongside the multi-ABI one, wire it for `vr`/`vrUnlicensed`.
- **Reason:** AGP's packaging step strips non-matching ABIs via the flavor-level `abiFilters`. The VR AAB ends up containing only `arm64-v8a/libffmpegJNI.so` regardless of what the AAR carries. Double-maintenance of two AARs is not worth any theoretical byte savings in the build tree.

---

## 13. Implementation Steps

1. Create a timestamped backup of the existing single-ABI AAR: `cp app_v2/libs/fms-ffmpeg-dts.aar temp/fms-ffmpeg-dts_arm64-only_<YYYYMMDD_HHmm>.aar`. This is the rollback artefact if the multi-ABI build ever needs to be reverted.
2. Edit [scripts/builders/build-ffmpeg-dts.sh](scripts/builders/build-ffmpeg-dts.sh):
   1. Line 41 — change `ABIS=("arm64-v8a")` to `ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")`.
   2. Lines 37-40 comment — update to reflect four-ABI coverage and remove the "armeabi-v7a removed" note.
   3. In `build_ffmpeg_abi` case block, add arms for `x86` (host_triple=`i686-linux-android`, arch=`i686`) and `x86_64` (host_triple=`x86_64-linux-android`, arch=`x86_64`), both with `extra_flags=""` (keep `--disable-x86asm` globally).
   4. Replace `verify_16kb_alignment` body with the per-ABI loop from §5.3.
3. Run `.\scripts\add_to_dev_log.ps1 "scripts/builders/build-ffmpeg-dts.sh" "build-ffmpeg-dts" "Multi-ABI FFmpeg build (arm64 + armeabi-v7a + x86 + x86_64); per-ABI 16 KB alignment check"`.
4. Inspect [scripts/builders/build-ffmpeg-dts-wsl.ps1](scripts/builders/build-ffmpeg-dts-wsl.ps1): if it hard-codes disk/RAM budget messages, bump the disk estimate comment from ~20 GB to ~30 GB (four parallel-ish FFmpeg trees). If it only launches the bash script, no change.
5. If step 4 modifies the .ps1 wrapper, run `.\scripts\add_to_dev_log.ps1 "scripts/builders/build-ffmpeg-dts-wsl.ps1" "build-ffmpeg-dts-wsl" "Bump disk budget for 4-ABI build"`.
6. Execute the full build in WSL2: `.\scripts\builders\build-ffmpeg-dts-wsl.ps1`. Expect one FFmpeg configure + compile per ABI (serial), then one CMake JNI bridge per ABI, then packaging, then 16 KB verification.
7. Inspect the regenerated [app_v2/libs/fms-ffmpeg-dts.aar](app_v2/libs/fms-ffmpeg-dts.aar): `python -m zipfile -l app_v2/libs/fms-ffmpeg-dts.aar` must list four `jni/<abi>/libffmpegJNI.so` entries.
8. Run all test cases in §9.2 (1-5 on the build host, 6-10 on devices/emulators).
9. Run `.\scripts\add_to_dev_log.ps1 "app_v2/libs/fms-ffmpeg-dts.aar" "fms-ffmpeg-dts.aar" "Regenerated multi-ABI AAR (arm64-v8a + armeabi-v7a + x86 + x86_64, all 16 KB aligned)"`.
10. Commit the .sh, optional .ps1, and .aar together in one commit so Git history shows the AAR bump alongside the script change that produced it.

**Mandatory step checklist:**

- [ ] String resources added in EN/RU/UK — **N/A, no UI changes**
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated — **N/A, no new user-facing capability**
- [ ] Room DB migration added + version incremented — **N/A, no DB changes**
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file — **required**, see steps 3, 5, 9

---

## 13.1 Implementation Notes (post-merge)

Three non-trivial findings emerged during the build:

1. **`make distclean` is mandatory between ABIs.** The original script had `ABIS=("arm64-v8a")` so the FFmpeg source tree was only ever configured once. With a multi-ABI loop, `make -j` saw stale `.o` files from the previous architecture and kept them — producing a `libavcodec.a` with mixed AArch64 + ARM objects. Downstream JNI linker failed with `incompatible with armelf_linux_eabi` on individual objects. Fix: `make distclean 2>/dev/null || true` before every `./configure` invocation inside `build_ffmpeg_abi`. Script change committed with this spec.

2. **`readelf -l` splits ELF64 LOAD rows across two lines.** `verify_16kb_alignment`'s regex looked for `Align=0x4000` at end of the LOAD line, which worked for 32-bit slices (armeabi-v7a, x86) but missed the Align field on ELF64 (arm64-v8a, x86_64) because readelf broke each LOAD across two lines for wide 64-bit addresses. Fix: `readelf -lW` (wide output) keeps LOAD on one line regardless of ELF class. Manual verification confirms all 4 slices are aligned at `0x4000` (3 LOAD segments each). Script change committed.

3. **`media3-decoder-ffmpeg-1.2.1.aar` is not published to Maven.** The script's download fallback URL returned 404 — both `dl.google.com/android/maven2/...` and `dl.google.com/dl/android/maven2/...` miss. media3 extensions are source-only by design. Fix: added two extra fallbacks in `package_aar` search order — first the project's own `app_v2/libs/fms-ffmpeg-dts.aar` (if already built), then the most recent `temp/fms-ffmpeg-dts_*.aar` backup. Both paths expose the same `classes.jar` because all downstream AARs inherit it verbatim from the original media3 build. Direct-download remains as a last-resort fallback for future compatibility.

4. **AGP merges `ndk.abiFilters` as UNION across buildType + flavor, not intersection.** The first Gradle wiring attempt put `abiFilters = [arm64-v8a, armeabi-v7a, x86, x86_64]` at `release` buildType level, expecting VR flavor's `abiFilters = [arm64-v8a]` to intersect it down to arm64. The actual AAB contained all 4 slices — AGP took the union. Two consequences:
   - VR AABs leaked non-arm64 slices (bloat + store review risk).
   - CMake configure was invoked per buildType ABI, so `openxr_native` tried to configure for armeabi-v7a/x86/x86_64 where the OpenXR loader AAR has no slices, failing the build.
   - Fix: move ABI configuration to flavor level — `disableNativeBuild()` helper applies all-4-ABIs to standard/lite/photos/legacy; `vr`/`vrUnlicensed` declare `arm64-v8a` only. `release` and `debug` buildTypes no longer set `abiFilters`. Also added `externalNativeBuild.cmake.abiFilters = [arm64-v8a]` to VR flavors so CMake doesn't try to configure for ABIs the OpenXR AAR lacks.

All four findings were integrated into the script and `build.gradle.kts` before this spec flipped to `Implemented`.

### Validation results

- **AAR built:** `app_v2/libs/fms-ffmpeg-dts.aar` — 11 MB compressed, 4 slices (arm64-v8a 7.68 MB, armeabi-v7a 6.87 MB, x86 6.61 MB, x86_64 7.58 MB uncompressed).
- **16 KB alignment:** verified per ABI via `readelf -lW` loop — every slice shows 3 × `LOAD Align=0x4000`, zero 4 KB segments.
- **`bundleStandardRelease`:** `BUILD SUCCESSFUL`. AAB contains 4 `base/lib/<abi>/libffmpegJNI.so` entries (1.7–1.9 MB each after Play Bundle compression). ABIs present: `[arm64-v8a, armeabi-v7a, x86, x86_64]`.
- **`bundleVrRelease`:** `BUILD SUCCESSFUL` after flavor-local ABI fix. AAB contains a single `base/lib/arm64-v8a/libffmpegJNI.so` slice. ABIs present: `[arm64-v8a]`. Non-arm64 slices correctly stripped for Meta Horizon / Google Play XR distribution.

---

## 14. Out of Scope (future items)

- Building and shipping `libgav1` (AV1 SW decoder) via the same pipeline. The dependency is commented out at [app_v2/build.gradle.kts:703-708](app_v2/build.gradle.kts#L703-L708) awaiting a parallel FFmpeg-style build task.
- Building and shipping `libvpx` (VP9 + VP9 Profile 2 10-bit HDR) SW decoder. See [app_v2/build.gradle.kts:710-715](app_v2/build.gradle.kts#L710-L715).
- Migrating FFmpeg from release/6.0 to release/7.x — couples to a media3 version bump and a JNI-bridge ABI re-verification.
- Adding DTS:X Object Audio support. Explicitly declined in the predecessor spec §4 (too complex, negligible demand).
- Enabling x86asm with nasm on the WSL2 host. Deferred until profiling shows audio decode is a bottleneck on x86 devices.
- GitHub Actions / CI job that rebuilds the AAR on every FFmpeg commit bump. Currently a manual local job by design (multi-hour compile, specialised WSL2 setup).
- Migrating the AAR to Git-LFS. The current repo has it as a plain binary blob (checked in alongside the backup). A multi-slice ~30 MB AAR is still below GitHub's 100 MB per-file soft limit but would benefit from LFS if iterations become frequent.
