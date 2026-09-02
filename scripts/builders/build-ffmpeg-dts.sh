#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# build-ffmpeg-dts.sh - Custom FFmpeg build for FMS Android (DTS + extended codecs)
#
# Produces: fms-ffmpeg-dts.aar
#   → Copy to app_v2/libs/ and uncomment dependencies in app_v2/build.gradle.kts
#
# Run on: WSL2 Ubuntu 22.04 (or native Linux x86-64, macOS 13+)
# NDK required: r27c Linux toolchain (minimum version with 16 KB-compatible runtime libs)
# Disk: ≥ 20 GB free. RAM: ≥ 16 GB.
#
# Spec: PLAN/spec_ffmpeg-custom-build-dts.md §7
# ══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Project mount path ────────────────────────────────────────────────────────
# Where this repository is visible from inside WSL/Linux. Supplied as the first argument by
# scripts/builders/build-ffmpeg-dts-wsl.ps1, which resolves it from the live project root; the
# FMS_PROJECT_MOUNT variable is the hand-run fallback. Keeping a literal copy here would let this
# script and its launcher disagree the first time the tree moves (S2326 step 04.4).
PROJECT_MOUNT="${1:-${FMS_PROJECT_MOUNT:-}}"
if [[ -z "$PROJECT_MOUNT" ]]; then
    echo "ERROR: project mount path not supplied."
    echo "  Pass it as the first argument, or export FMS_PROJECT_MOUNT=/mnt/<drive>/<path-to-repo>."
    echo "  Normally you run the launcher instead: scripts\\builders\\build-ffmpeg-dts-wsl.ps1"
    exit 1
fi

# ── Configuration ─────────────────────────────────────────────────────────────
# NDK Linux toolchain - expected at ~/android-ndk-r27c (prepared by temp/wsl2-phase1-setup.sh).
# r27c is the first NDK to ship 16 KB-aligned libc++_shared.so - required for Android 15+.
# r25c is accepted as fallback: its lld supports -Wl,-z,max-page-size=16384 for our own .so
# files (libffmpegJNI.so + FFmpeg libs). Because FFmpeg JNI bridge uses c++_static, libc++_shared
# is NOT bundled in the AAR, so r25c is sufficient for Play compliance.
: "${ANDROID_NDK:=${HOME}/android-ndk-r27c}"
# Auto-fallback to r25c if r27c not available (r25c lld supports all needed linker flags)
if [[ ! -d "$ANDROID_NDK" ]] && [[ -d "${HOME}/android-ndk-r25c" ]]; then
    ANDROID_NDK="${HOME}/android-ndk-r25c"
    echo "[INFO] NDK r27c not found; falling back to r25c at $ANDROID_NDK"
fi
: "${API_LEVEL:=26}"                                # minSdk for standard/vr flavors
: "${WORK_DIR:=$(pwd)/ffmpeg-android-build}"
: "${OUT_DIR:=$(pwd)/app_v2/libs}"                  # where to place the final AAR

MEDIA3_TAG="1.2.1"
FFMPEG_REPO="https://github.com/FFmpeg/FFmpeg.git"
MEDIA3_REPO="https://github.com/androidx/media.git"

# ABIs to build - all four production ABIs for multi-ABI AAR distribution.
#   arm64-v8a   - modern 64-bit ARM (primary; Quest 2/3/Pro)
#   armeabi-v7a - older 32-bit ARM (old phones, TV boxes, car units, low-end tablets)
#   x86_64      - Chromebooks, modern x86 emulators
#   x86         - legacy Chromebooks / API 27 AVDs
# AAB splits per-device ABI at Play distribution - one user receives one slice only.
# VR flavor forces arm64-v8a at flavor level (Quest target), so non-arm64 slices are
# stripped from VR AABs automatically. noLegal carries arm64-v8a + x86_64 (sideload
# universal). See PLAN/spec_ffmpeg-dts-multi-abi.md.
ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

# ── Dependency check ──────────────────────────────────────────────────────────
check_deps() {
    local missing=()
    for cmd in git python3 make gcc; do
        command -v "$cmd" &>/dev/null || missing+=("$cmd")
    done
    # nasm: only needed for x86 SIMD; ARM cross-compile works without it.
    # pkg-config: not needed when linking only internal FFmpeg libs.
    # zip: replaced by python3 -m zipfile in package_aar().
    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "ERROR: Missing tools: ${missing[*]}"
        echo "  Ubuntu: sudo apt-get install -y ${missing[*]}"
        exit 1
    fi
    if [[ ! -d "$ANDROID_NDK" ]]; then
        echo "ERROR: NDK not found at $ANDROID_NDK"
        echo "  Run temp/wsl2-phase1-setup.sh to download the Linux NDK automatically."
        exit 1
    fi
    echo "[OK] All dependencies found."
    echo "[OK] NDK: $ANDROID_NDK"
}

# ── Clone / update sources ────────────────────────────────────────────────────
clone_sources() {
    mkdir -p "$WORK_DIR" && cd "$WORK_DIR"

    if [[ ! -d media ]]; then
        echo "[CLONE] media3 @ $MEDIA3_TAG .."
        git clone "$MEDIA3_REPO" --branch "$MEDIA3_TAG" --depth 1 media
    else
        echo "[SKIP] media3 already cloned."
    fi

    # Read required FFmpeg commit pinned by this Media3 version.
    # In media3 1.2.1 the extension is in libraries/decoder_ffmpeg.
    local readme="$WORK_DIR/media/libraries/decoder_ffmpeg/README.md"
    FFMPEG_COMMIT=$(grep -oP '(?<=commit )[0-9a-f]{40}' "$readme" | head -1 || true)
    if [[ -z "$FFMPEG_COMMIT" ]]; then
        echo "WARN: Could not auto-detect FFmpeg commit from README."
        echo "      media3 1.2.1 targets FFmpeg release/6.0 - using that branch."
        FFMPEG_COMMIT="release/6.0"
    fi
    echo "[INFO] FFmpeg commit: $FFMPEG_COMMIT"

    if [[ ! -d ffmpeg ]] || [[ ! -f ffmpeg/configure ]]; then
        rm -rf ffmpeg
        # Determine branch to clone. Pinned sha commits are unreliable via shallow HTTPS;
        # fall back to the release branch media3 1.2.1 documents (release/6.0).
        local clone_ref="$FFMPEG_COMMIT"
        if [[ -z "$clone_ref" ]] || [[ "${#clone_ref}" -eq 40 ]]; then
            clone_ref="release/6.0"
            echo "[INFO] Cloning FFmpeg branch release/6.0 (media3 1.2.1 requirement)"
        else
            echo "[INFO] Cloning FFmpeg @ $clone_ref"
        fi
        local cloned=false
        for attempt in 1 2 3; do
            rm -rf ffmpeg
            git clone "$FFMPEG_REPO" --branch "$clone_ref" --depth 1 ffmpeg && cloned=true && break \
                || echo "[WARN] Clone attempt $attempt failed, retrying.."
            sleep 5
        done
        if [[ "$cloned" != "true" ]]; then
            echo "ERROR: FFmpeg clone failed after 3 attempts."
            exit 1
        fi
        echo "[OK] FFmpeg cloned."
    else
        echo "[SKIP] FFmpeg already cloned."
    fi
}

# ── Configure + build FFmpeg for one ABI ─────────────────────────────────────
build_ffmpeg_abi() {
    local abi="$1"
    local prefix="$WORK_DIR/ffmpeg-out/$abi"
    mkdir -p "$prefix"

    # Map ABI to NDK triple and configure flags
    local host_triple arch extra_flags=""
    case "$abi" in
        arm64-v8a)
            host_triple="aarch64-linux-android"
            arch="aarch64"
            ;;
        armeabi-v7a)
            host_triple="armv7a-linux-androideabi"
            arch="arm"
            extra_flags="--cpu=armv7-a --enable-neon"
            ;;
        x86)
            # i686 configure target; NDK cross-prefix "i686-linux-android${API_LEVEL}-clang"
            host_triple="i686-linux-android"
            arch="i686"
            # --disable-x86asm kept globally (see configure args); avoids nasm dependency.
            # Audio-only FFmpeg build - SIMD loss is negligible for DTS/APE/WMA decoders.
            ;;
        x86_64)
            host_triple="x86_64-linux-android"
            arch="x86_64"
            # Same --disable-x86asm rationale as x86.
            ;;
        *)
            echo "ERROR: Unsupported ABI: $abi"
            exit 1
            ;;
    esac

    local toolchain="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"
    local cross_prefix="${toolchain}/bin/${host_triple}${API_LEVEL}-"
    # nm, ar, ranlib and strip are provided by LLVM tools - not prefixed like clang/ld
    local llvm_nm="${toolchain}/bin/llvm-nm"
    local llvm_ar="${toolchain}/bin/llvm-ar"
    local llvm_ranlib="${toolchain}/bin/llvm-ranlib"
    local llvm_strip="${toolchain}/bin/llvm-strip"

    echo ""
    echo "════════════════════════════════════════"
    echo " FFmpeg configure: $abi"
    echo "════════════════════════════════════════"

    cd "$WORK_DIR/ffmpeg"
    # CRITICAL: clean the tree before reconfiguring for a different ABI.
    # Without this, make -j sees stale .o files from the previous ABI and skips
    # rebuilds, producing libavcodec.a with mixed arch objects (e.g. some arm,
    # some aarch64). The downstream JNI bridge link then fails with
    # "incompatible with armelf_linux_eabi" or similar on partial objects.
    # distclean removes .o, generated .h, config files, and dependency data.
    make distclean 2>/dev/null || true
    ./configure \
        --cross-prefix="${cross_prefix}" \
        --sysroot="${toolchain}/sysroot" \
        --target-os=android \
        --arch="$arch" \
        --enable-cross-compile \
        --prefix="$prefix" \
        --nm="${llvm_nm}" \
        --ar="${llvm_ar}" \
        --ranlib="${llvm_ranlib}" \
        --strip="${llvm_strip}" \
        --disable-everything \
        --disable-programs \
        --disable-doc \
        --disable-avdevice \
        --disable-swscale \
        --disable-postproc \
        --disable-network \
        --disable-vulkan \
        \
        --enable-avcodec \
        --enable-avformat \
        --enable-avutil \
        --enable-swresample \
        \
        --enable-small \
        \
        --enable-decoder=vorbis \
        --enable-decoder=opus \
        --enable-decoder=flac \
        --enable-decoder=alac \
        --enable-decoder=pcm_mulaw \
        --enable-decoder=pcm_alaw \
        --enable-decoder=pcm_s16le \
        --enable-decoder=pcm_s24le \
        \
        --enable-decoder=dca \
        --enable-parser=dca \
        \
        --enable-decoder=truehd \
        --enable-decoder=eac3 \
        --enable-decoder=ac3 \
        --enable-parser=ac3 \
        \
        --enable-decoder=mp3 \
        --enable-decoder=aac \
        \
        --enable-decoder=ape \
        --enable-demuxer=ape \
        \
        --enable-decoder=wmav1 \
        --enable-decoder=wmav2 \
        --enable-demuxer=asf \
        \
        --enable-decoder=wavpack \
        --enable-demuxer=wv \
        \
        --enable-decoder=tta \
        --enable-demuxer=tta \
        \
        --enable-decoder=dsd_lsbf \
        --enable-decoder=dsd_msbf \
        --enable-decoder=dsd_lsbf_planar \
        --enable-decoder=dsd_msbf_planar \
        --enable-demuxer=dsf \
        --enable-demuxer=iff \
        \
        --enable-decoder=mpc7 \
        --enable-decoder=mpc8 \
        --enable-demuxer=mpc \
        --enable-demuxer=mpc8 \
        \
        --enable-demuxer=matroska \
        --enable-demuxer=mov \
        --enable-demuxer=ogg \
        --enable-demuxer=flac \
        --enable-demuxer=wav \
        --enable-demuxer=aiff \
        --enable-demuxer=mp3 \
        --enable-demuxer=aac \
        \
        --enable-parser=vorbis \
        --enable-parser=opus \
        --enable-parser=flac \
        --enable-parser=mpeg4video \
        --enable-parser=h264 \
        --enable-parser=hevc \
        --enable-parser=aac \
        --enable-parser=mp3 \
        \
        --enable-protocol=file \
        \
        --disable-x86asm \
        --extra-cflags="-O2 -fPIC -DANDROID -ffunction-sections -fdata-sections" \
        --extra-ldflags="-lm -Wl,-z,max-page-size=16384" \
        $extra_flags
        # --disable-x86asm: nasm/yasm not required for ARM cross-compile.
        # -Wl,-z,max-page-size=16384: align ELF LOAD segments to 16 KB boundaries.
        # Required for Android 15+ (Google Play enforcement since Nov 1 2025).
        # Without this flag, Play Console rejects the APK with "LOAD segments not
        # aligned at 16 KB boundaries" even when useLegacyPackaging=false is set.

    echo "[BUILD] make -j$(nproc) for $abi .."
    make -j"$(nproc)"
    make install
    echo "[OK] FFmpeg $abi done → $prefix"
    cd "$WORK_DIR"
}

# ── Build JNI bridge via CMake ─────────────────────────────────────────────────
# media3's CMakeLists.txt expects:
#   <jni_dir>/ffmpeg/               ← FFmpeg headers (include_directories)
#   <jni_dir>/ffmpeg/android-libs/<abi>/*.a  ← pre-built static libs
#
# We symlink our FFmpeg source tree and copy pre-built .a files there, then
# invoke CMake directly - skipping build_ffmpeg.sh (which would re-run configure
# without --disable-vulkan and fail on NDK r25c).
# The 16 KB page-size flag is injected via CMAKE_SHARED_LINKER_FLAGS.
build_jni_bridge() {
    local abi="$1"
    local ffmpeg_src="$WORK_DIR/ffmpeg"
    local ffmpeg_out="$WORK_DIR/ffmpeg-out/$abi"
    local jni_dir="$WORK_DIR/media/libraries/decoder_ffmpeg/src/main/jni"
    local ffmpeg_jni_link="$jni_dir/ffmpeg"
    local android_libs="$ffmpeg_jni_link/android-libs/$abi"
    local build_dir="$WORK_DIR/jni-build/$abi"

    echo ""
    echo "════════════════════════════════════════"
    echo " JNI bridge (CMake): $abi"
    echo "════════════════════════════════════════"

    # 1. Create symlink so CMakeLists.txt can find headers at jni/ffmpeg/
    if [[ ! -L "$ffmpeg_jni_link" ]] && [[ ! -d "$ffmpeg_jni_link" ]]; then
        ln -s "$ffmpeg_src" "$ffmpeg_jni_link"
        echo "[OK] Symlinked ffmpeg source → $ffmpeg_jni_link"
    fi

    # 2. Copy pre-built static libs to the location CMakeLists.txt expects
    mkdir -p "$android_libs"
    if [[ ! -f "$ffmpeg_out/lib/libavcodec.a" ]]; then
        echo "ERROR: Pre-built FFmpeg libs not found at $ffmpeg_out/lib/"
        echo "       Run build_ffmpeg_abi first."
        exit 1
    fi
    cp "$ffmpeg_out/lib/libavcodec.a" \
       "$ffmpeg_out/lib/libavutil.a" \
       "$ffmpeg_out/lib/libswresample.a" \
       "$android_libs/"
    echo "[OK] Copied .a libs → $android_libs"

    # 3. Build libffmpegJNI.so via CMake with NDK toolchain
    #    -Wl,-z,max-page-size=16384 ensures 16 KB LOAD alignment (Play Store requirement)
    rm -rf "$build_dir" && mkdir -p "$build_dir"
    cmake -S "$jni_dir" \
        -B "$build_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-${API_LEVEL}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
        -DCMAKE_C_COMPILER="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" \
        -DCMAKE_CXX_COMPILER="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" \
        2>&1

    cmake --build "$build_dir" -j"$(nproc)" 2>&1

    # 4. Verify output
    local so_file
    so_file=$(find "$build_dir" -name "libffmpegJNI.so" | head -1)
    if [[ -z "$so_file" ]]; then
        echo "ERROR: libffmpegJNI.so not produced by CMake build"
        exit 1
    fi
    echo "[OK] JNI bridge $abi done → $so_file"
}

# ── Package AAR ───────────────────────────────────────────────────────────────
package_aar() {
    echo ""
    echo "════════════════════════════════════════"
    echo " Packaging AAR"
    echo "════════════════════════════════════════"

    local staging="$WORK_DIR/aar-staging"
    rm -rf "$staging" && mkdir -p "$staging/jni"

    # Grab classes.jar + AndroidManifest.xml from the prebuilt media3-decoder-ffmpeg AAR.
    # We need the Java bytecode (FfmpegAudioRenderer etc.) but replace native libs with ours.
    # Search order:
    #   1. Linux Gradle cache (~/.gradle/caches)
    #   2. Windows Gradle cache (/mnt/c/Users/.gradle/caches)
    #   3. Project's own fms-ffmpeg-dts.aar (or its backup in temp/) - same classes.jar
    #      because both share the media3-decoder-ffmpeg bytecode verbatim.
    #   4. Direct download from Google Maven (usually 404 - media3-decoder-ffmpeg is
    #      source-only and not published to Maven; keep the step for future compatibility).
    local prebuilt_aar
    prebuilt_aar=$(find "${HOME}/.gradle/caches" -name "media3-decoder-ffmpeg-1.2.1.aar" 2>/dev/null | head -1 || true)
    if [[ -z "$prebuilt_aar" ]]; then
        prebuilt_aar=$(find /mnt/c/Users -name "media3-decoder-ffmpeg-1.2.1.aar" -path "*media3*" 2>/dev/null | head -1 || true)
    fi
    # Fallback to an already-built fms-ffmpeg-dts.aar from the project tree (or backups).
    # Both contain classes.jar extracted from media3-decoder-ffmpeg-1.2.1.aar, so bytecode matches.
    if [[ -z "$prebuilt_aar" ]]; then
        local project_aar="${PROJECT_MOUNT}/app_v2/libs/fms-ffmpeg-dts.aar"
        [[ -f "$project_aar" ]] && prebuilt_aar="$project_aar"
    fi
    if [[ -z "$prebuilt_aar" ]]; then
        # Pick the most recent backup in temp/ matching the naming convention.
        local backup_aar
        backup_aar=$(ls -t "${PROJECT_MOUNT}"/temp/fms-ffmpeg-dts_*.aar 2>/dev/null | head -1)
        [[ -n "$backup_aar" ]] && prebuilt_aar="$backup_aar"
    fi
    if [[ -z "$prebuilt_aar" ]]; then
        local maven_aar="${WORK_DIR}/media3-decoder-ffmpeg-1.2.1.aar"
        if [[ ! -f "$maven_aar" ]]; then
            echo "[INFO] media3-decoder-ffmpeg-1.2.1.aar not in Gradle cache - downloading from Maven Central .."
            curl -L --progress-bar \
                -o "$maven_aar" \
                "https://dl.google.com/android/maven2/androidx/media3/media3-decoder-ffmpeg/1.2.1/media3-decoder-ffmpeg-1.2.1.aar"
            echo "[OK] Downloaded media3-decoder-ffmpeg-1.2.1.aar"
        else
            echo "[SKIP] media3-decoder-ffmpeg-1.2.1.aar already downloaded at $maven_aar"
        fi
        prebuilt_aar="$maven_aar"
    fi

    echo "[INFO] Extracting classes.jar + AndroidManifest.xml from: $prebuilt_aar .."
    PREBUILT_AAR="$prebuilt_aar" STAGING_DIR="$staging" python3 - <<'PYEOF'
import os
import sys
import zipfile

aar_path = os.environ.get('PREBUILT_AAR')
staging_dir = os.environ.get('STAGING_DIR')
if not aar_path or not staging_dir:
    print('ERROR: PREBUILT_AAR or STAGING_DIR env vars not set')
    sys.exit(1)

with zipfile.ZipFile(aar_path) as zf:
    for name in ('classes.jar', 'AndroidManifest.xml'):
        try:
            zf.extract(name, staging_dir)
        except KeyError:
            print(f'ERROR: {name} missing in {aar_path}')
            sys.exit(1)

print(f'[OK] Extracted classes.jar + AndroidManifest.xml from {aar_path}')
PYEOF

    # Copy native libs from all built ABIs
    for abi in "${ABIS[@]}"; do
        # libffmpegJNI.so is produced by CMake in jni-build/<abi>/
        local jni_so
        jni_so=$(find "$WORK_DIR/jni-build/$abi" -name "libffmpegJNI.so" 2>/dev/null | head -1)

        if [[ -n "$jni_so" ]]; then
            mkdir -p "$staging/jni/$abi"
            cp "$jni_so" "$staging/jni/$abi/"
            echo "[OK] Packed $abi: libffmpegJNI.so"
        else
            echo "WARN: libffmpegJNI.so not found for $abi in $WORK_DIR/jni-build/$abi"
            echo "      Did build_jni_bridge (CMake) succeed? Check logs above."
        fi
    done

    # Assemble the AAR (it's a ZIP file - use python3 to avoid needing the 'zip' package)
    mkdir -p "$OUT_DIR"
    local aar_path="$OUT_DIR/fms-ffmpeg-dts.aar"
    export STAGING_DIR="$staging"
    export AAR_PATH="$aar_path"
    python3 - <<'PYEOF'
import zipfile, os, sys
staging = os.environ.get('STAGING_DIR', '')
aar_path = os.environ.get('AAR_PATH', '')
if not staging or not aar_path:
    print('ERROR: STAGING_DIR or AAR_PATH env vars not set')
    sys.exit(1)
with zipfile.ZipFile(aar_path, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(staging):
        for f in files:
            abs_path = os.path.join(root, f)
            arc_name = os.path.relpath(abs_path, staging)
            zf.write(abs_path, arc_name)
print(f'[OK] AAR written: {aar_path} ({os.path.getsize(aar_path)//1024} KB)')
PYEOF

    echo ""
    echo "══════════════════════════════════════════════════════════════"
    echo " SUCCESS: $aar_path"
    echo ""
    echo " Next steps (Phase 4) - after 16 KB alignment check passes:"
    echo "   1. Copy rebuilt AAR to app_v2/libs/ (already done if OUT_DIR is the project)."
    echo "   2. Confirm app_v2/build.gradle.kts already has:"
    echo "        standardImplementation(files(\"libs/fms-ffmpeg-dts.aar\"))"
    echo "        noLegalImplementation(files(\"libs/fms-ffmpeg-dts.aar\"))"
    echo "        legacyImplementation(files(\"libs/fms-ffmpeg-dts.aar\"))"
    echo "        vrImplementation(files(\"libs/fms-ffmpeg-dts.aar\"))"
    echo "   3. Rebuild: ./gradlew.bat assembleStandardDebug"
    echo "   4. Inspect APK: python -m zipfile -l <apk> | grep ffmpeg"
    echo "   5. Verify DTS track plays on Quest 3 (DTS-only MKV)."
    echo "══════════════════════════════════════════════════════════════"
}

# ── Verify DTS export ─────────────────────────────────────────────────────────
verify_dca_export() {
    echo ""
    echo "[VERIFY] Checking libavcodec exports dca decoder .."
    local lib
    lib=$(find "$WORK_DIR/ffmpeg-out" -name "libavcodec.so" | head -1)
    if [[ -n "$lib" ]]; then
        if nm -D "$lib" 2>/dev/null | grep -q "ff_dca_decoder\|dca_decode_frame"; then
            echo "[OK] libavcodec.so contains DTS (dca) decoder."
        else
            echo "WARN: DTS decoder symbol not found in libavcodec.so."
            echo "      Check configure output for 'dca' in enabled decoders."
        fi
    else
        echo "WARN: libavcodec.so not found; skipping symbol check."
    fi
}

# ── Verify 16 KB page-size alignment ─────────────────────────────────────────
# Google Play rejects APK/AAB artifacts targeting Android 15+ when any native lib
# has ELF LOAD segments aligned below 16 KB (Align=0x1000 / 4 KB).
# This function extracts the just-packaged AAR, runs readelf -l on the arm64
# libffmpegJNI.so, and fails the build if any LOAD segment has Align < 0x4000.
verify_16kb_alignment() {
    local aar_path="$OUT_DIR/fms-ffmpeg-dts.aar"
    local verify_dir="$WORK_DIR/aar-verify-16kb"

    echo ""
    echo "════════════════════════════════════════"
    echo " 16 KB alignment check (readelf -l) - all ABIs"
    echo "════════════════════════════════════════"

    if ! command -v readelf &>/dev/null; then
        echo "WARN: readelf not found - skipping 16 KB alignment check."
        echo "      Install binutils: sudo apt-get install -y binutils"
        echo "      Run manually after build:"
        echo "        python3 -m zipfile -e $aar_path /tmp/aar-verify"
        echo "        for abi in arm64-v8a armeabi-v7a x86 x86_64; do"
        echo "            readelf -l /tmp/aar-verify/jni/\$abi/libffmpegJNI.so | grep LOAD"
        echo "        done"
        return 0
    fi

    if [[ ! -f "$aar_path" ]]; then
        echo "ERROR: AAR not found at $aar_path - cannot verify alignment."
        exit 1
    fi

    rm -rf "$verify_dir" && mkdir -p "$verify_dir"

    # Extract every .so from the AAR (which is a ZIP)
    AAR_PATH="$aar_path" VERIFY_DIR="$verify_dir" python3 - <<'PYEOF'
import zipfile, os
with zipfile.ZipFile(os.environ['AAR_PATH']) as z:
    for name in z.namelist():
        if name.endswith('.so'):
            z.extract(name, os.environ['VERIFY_DIR'])
PYEOF

    local fail=0
    local checked=0
    for abi in "${ABIS[@]}"; do
        local so_path="$verify_dir/jni/$abi/libffmpegJNI.so"
        if [[ ! -f "$so_path" ]]; then
            echo "FAIL: $abi - libffmpegJNI.so missing from AAR."
            fail=1
            continue
        fi

        # -W (wide): prevents readelf from splitting the LOAD row across two lines
        # on ELF64 targets (arm64-v8a, x86_64), which would hide the Align field.
        local load_lines
        load_lines=$(readelf -lW "$so_path" 2>&1 | grep -E "^\s+LOAD\s+" || true)

        if echo "$load_lines" | grep -qE "\s0x1000\s*$"; then
            echo "FAIL: $abi - 4 KB LOAD alignment (Align=0x1000). Play will reject."
            echo "--- LOAD segments ---"
            echo "$load_lines"
            echo "---------------------"
            fail=1
        elif echo "$load_lines" | grep -qE "\s0x4000\s*$"; then
            echo "[OK] $abi - 16 KB aligned (Align=0x4000). Play-safe. ✓"
            checked=$((checked + 1))
        else
            echo "WARN: $abi - alignment not confirmed from readelf output:"
            echo "$load_lines"
        fi
    done

    rm -rf "$verify_dir"

    if [[ $fail -eq 1 ]]; then
        echo ""
        echo "FAIL: one or more ABIs are NOT 16 KB compliant. AAR cannot ship."
        echo "Fix: verify -Wl,-z,max-page-size=16384 in CMAKE_SHARED_LINKER_FLAGS"
        echo "     and NDK r27c (or r25c) lld is used for every JNI bridge link step."
        exit 1
    fi

    echo ""
    echo "[OK] 16 KB alignment verified for $checked/${#ABIS[@]} ABI slices."
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
    echo "═══════════════════════════════════════════════════════════"
    echo " FMS custom FFmpeg build (DTS + extended codecs)"
    echo " NDK:   $ANDROID_NDK"
    echo " API:   $API_LEVEL"
    echo " Work:  $WORK_DIR"
    echo " Out:   $OUT_DIR"
    echo "═══════════════════════════════════════════════════════════"

    check_deps
    clone_sources

    for abi in "${ABIS[@]}"; do
        # Skip if pre-built .a files already exist from a previous run
        if [[ -f "$WORK_DIR/ffmpeg-out/$abi/lib/libavcodec.a" ]]; then
            echo "[SKIP] FFmpeg $abi .a files already present, skipping build_ffmpeg_abi."
        else
            build_ffmpeg_abi "$abi"
        fi
    done

    verify_dca_export

    for abi in "${ABIS[@]}"; do
        build_jni_bridge "$abi"
    done

    package_aar
    verify_16kb_alignment

    echo ""
    echo "[DONE] Build complete."
}

main "$@"
