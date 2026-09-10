#!/usr/bin/env bash
# build-libvpx-vp9.sh
# ==============================================================================
# S1126 - software VP9 video decoder extension (libvpx) as a decoder-fallback
# backstop. Produces: app_v2/libs/fms-vpx.aar
#
# Runs inside WSL2 / Linux. Companion to build-ffmpeg-dts.sh, same shape:
# the native halves are cross-compiled here, the Java half is compiled on the
# Windows side by scripts/builders/compile-vp9-classes.ps1 (there is no JDK and
# no Android SDK in the WSL guest, and unlike FFmpeg no prebuilt VP9 AAR exists
# on any Maven repository - androidx.media3 publishes no decoder extension at
# all, only the media3-decoder base artifact).
#
# Usage (from the project root, via WSL):
#   wsl bash scripts/builders/build-libvpx-vp9.sh /mnt/p/ANDROID/FastMediaSorter_mob_v2
#
# Environment overrides:
#   ANDROID_NDK   - default $HOME/android-ndk-r25c
#   MEDIA3_DIR    - default $HOME/ffmpeg-android-build/media  (must be media3 1.2.1)
#   WORK_DIR      - default $HOME/vpx-build
#   VP9_STAGE_IN  - default $WORK_DIR/stage-in, holds classes.jar + AndroidManifest.xml
#   VPX_TAG       - default v1.8.0, the libvpx tag the media3 VP9 module is tested against
#
# Version pins - both are load-bearing:
#   media3 1.2.1 : the renderer ABI the app links against. An AAR built from a
#                  different media3 branch links but diverges at runtime.
#   libvpx v1.8.0: the media3 VP9 module README states compatibility with other
#                  libvpx versions is not guaranteed.
#
# Exit codes:
#   0  the AAR was written to app_v2/libs/fms-vpx.aar
#   1  usage error - the project mount path argument is missing or not a directory
#   2  preflight failed - NDK, media3 checkout or a host tool is missing
#   3  libvpx could not be fetched or is not at the pinned tag
#   4  the libvpx Android config generation failed
#   5  every ABI failed to build
#   6  a produced .so has a LOAD segment aligned below 16 KB (Play would reject it)
#   7  packaging failed - classes.jar or AndroidManifest.xml absent from VP9_STAGE_IN
# ==============================================================================

set -uo pipefail

PROJECT_MOUNT="${1:-}"
if [[ -z "$PROJECT_MOUNT" || ! -d "$PROJECT_MOUNT" ]]; then
    echo "ERROR: pass the project mount path as the first argument, e.g. /mnt/p/ANDROID/FastMediaSorter_mob_v2" >&2
    exit 1
fi

: "${ANDROID_NDK:=$HOME/android-ndk-r25c}"
: "${MEDIA3_DIR:=$HOME/ffmpeg-android-build/media}"
: "${WORK_DIR:=$HOME/vpx-build}"
: "${VP9_STAGE_IN:=$WORK_DIR/stage-in}"
: "${VPX_TAG:=v1.8.0}"

VP9_MODULE="$MEDIA3_DIR/libraries/decoder_vp9/src/main"
JNI_DIR="$VP9_MODULE/jni"
LIBS_OUT="$WORK_DIR/out/libs"
OBJ_OUT="$WORK_DIR/out/obj"
OUT_AAR="$PROJECT_MOUNT/app_v2/libs/fms-vpx.aar"
EVIDENCE_DIR="$PROJECT_MOUNT/temp/S1126"

# All four production ABIs. The strategic spec named arm64-v8a alone; the extra
# three are here because acceptance criterion 3 - a bad VP9 stream falls back to
# software decode - is only reproducible on an emulator, which is x86_64, and an
# AAB splits per ABI so no user downloads a slice they cannot run.
ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")
BUILT_ABIS=()

READELF="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
MIN_ALIGN_HEX="4000"

banner() {
    echo ""
    echo "=============================================================="
    echo " $1"
    echo "=============================================================="
}

preflight() {
    banner "Preflight"
    local missing=0
    [[ -d "$ANDROID_NDK" ]] || { echo "ERROR: NDK not found at $ANDROID_NDK" >&2; missing=1; }
    [[ -x "$ANDROID_NDK/ndk-build" ]] || { echo "ERROR: ndk-build not executable in $ANDROID_NDK" >&2; missing=1; }
    [[ -x "$READELF" ]] || { echo "ERROR: llvm-readelf not found at $READELF" >&2; missing=1; }
    [[ -d "$JNI_DIR" ]] || { echo "ERROR: media3 VP9 module not found at $JNI_DIR" >&2; missing=1; }
    for tool in git make perl python3; do
        command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: host tool '$tool' is not installed" >&2; missing=1; }
    done
    if [[ -d "$MEDIA3_DIR/.git" ]]; then
        local ver
        ver="$(git -C "$MEDIA3_DIR" describe --tags 2>/dev/null || echo unknown)"
        echo "[INFO] media3 checkout at $ver"
        if [[ "$ver" != "1.2.1" ]]; then
            echo "WARNING: media3 checkout is '$ver', the app is pinned to 1.2.1 - the AAR may diverge at runtime." >&2
        fi
    fi
    (( missing == 0 )) || exit 2
    mkdir -p "$WORK_DIR" "$EVIDENCE_DIR" "$(dirname "$OUT_AAR")"
    echo "[OK] preflight passed"
}

fetch_libvpx() {
    banner "libvpx $VPX_TAG"
    local src="$WORK_DIR/libvpx"
    if [[ -d "$src/.git" ]]; then
        local have
        have="$(git -C "$src" describe --tags 2>/dev/null || echo none)"
        if [[ "$have" == "$VPX_TAG" ]]; then
            echo "[SKIP] libvpx already at $VPX_TAG"
        else
            echo "[INFO] libvpx is at '$have', re-fetching $VPX_TAG .."
            rm -rf "$src"
        fi
    fi
    if [[ ! -d "$src/.git" ]]; then
        git clone --depth 1 --branch "$VPX_TAG" https://chromium.googlesource.com/webm/libvpx "$src" || exit 3
    fi
    [[ -x "$src/configure" ]] || { echo "ERROR: libvpx checkout has no configure script" >&2; exit 3; }
    ln -sfn "$src" "$JNI_DIR/libvpx"
    echo "[OK] $JNI_DIR/libvpx -> $src"
}

generate_configs() {
    banner "libvpx Android configs"
    # The module's own generator owns the configure flags - it is what encodes the
    # decoder-only build (--disable-vp8 --disable-vp9-encoder --disable-webm-io),
    # which is what keeps the library small. Never reimplement them here.
    ( cd "$JNI_DIR" && rm -rf libvpx_android_configs && bash ./generate_libvpx_android_configs.sh ) \
        > "$WORK_DIR/genconfig.log" 2>&1
    local rc=$?
    if (( rc != 0 )); then
        echo "ERROR: config generation failed (rc=$rc); see $WORK_DIR/genconfig.log" >&2
        tail -20 "$WORK_DIR/genconfig.log" >&2
        exit 4
    fi
    for abi in "${ABIS[@]}"; do
        if [[ ! -f "$JNI_DIR/libvpx_android_configs/$abi/vpx_config.h" ]]; then
            echo "ERROR: no generated config for $abi" >&2
            exit 4
        fi
    done
    echo "[OK] configs generated for: ${ABIS[*]}"
}

build_abis() {
    banner "ndk-build"
    rm -rf "$LIBS_OUT" "$OBJ_OUT"
    for abi in "${ABIS[@]}"; do
        echo "[BUILD] $abi"
        # APP_PLATFORM android-23: the module ships Application.mk with android-16,
        # which is below every flavor this AAR reaches (legacy is minSdk 23).
        # APP_LDFLAGS: 16 KB LOAD alignment, the Play packaging requirement.
        ( cd "$JNI_DIR" && "$ANDROID_NDK/ndk-build" \
            APP_ABI="$abi" \
            APP_PLATFORM=android-23 \
            APP_LDFLAGS="-Wl,-z,max-page-size=$((0x$MIN_ALIGN_HEX))" \
            NDK_LIBS_OUT="$LIBS_OUT" \
            NDK_OUT="$OBJ_OUT" \
            -j"$(nproc)" ) > "$WORK_DIR/ndk-$abi.log" 2>&1
        if [[ -f "$LIBS_OUT/$abi/libvpxV2JNI.so" && -f "$LIBS_OUT/$abi/libvpx.so" ]]; then
            BUILT_ABIS+=("$abi")
            echo "[OK]    $abi"
        else
            # One ABI failing must not cost the other three: the deliverable is
            # still shippable, it simply covers fewer devices, and the summary
            # says so out loud rather than leaving a silent gap.
            echo "[FAIL]  $abi - dropped from the deliverable; see $WORK_DIR/ndk-$abi.log" >&2
        fi
    done
    if (( ${#BUILT_ABIS[@]} == 0 )); then
        echo "ERROR: no ABI produced a library" >&2
        exit 5
    fi
    local dropped=$(( ${#ABIS[@]} - ${#BUILT_ABIS[@]} ))
    if (( dropped > 0 )); then
        echo "[SUMMARY] built: ${BUILT_ABIS[*]} | dropped: $dropped ABI(s)"
    else
        echo "[SUMMARY] built all ${#BUILT_ABIS[@]} ABIs: ${BUILT_ABIS[*]}"
    fi
}

verify_alignment() {
    banner "16 KB alignment"
    local violations=0
    for abi in "${BUILT_ABIS[@]}"; do
        local evidence="$EVIDENCE_DIR/readelf-$abi.txt"
        : > "$evidence"
        for so in "$LIBS_OUT/$abi"/*.so; do
            echo "== $so" >> "$evidence"
            "$READELF" -l "$so" >> "$evidence" 2>&1
            while read -r align; do
                # Hex compare via arithmetic: a LOAD segment below 0x4000 is what
                # Play rejects, and the check lives inside the build because a
                # manual pass is the one that gets skipped when the build looks fine.
                if (( align < 0x$MIN_ALIGN_HEX )); then
                    echo "ERROR: $so has a LOAD segment aligned $align (< 0x$MIN_ALIGN_HEX)" >&2
                    violations=$(( violations + 1 ))
                fi
            done < <("$READELF" -l "$so" 2>/dev/null | awk '/^  LOAD/ { print strtonum($NF) }')
        done
        echo "[OK] $abi - evidence in $evidence"
    done
    if (( violations > 0 )); then
        echo "ERROR: $violations non-compliant LOAD segment(s)" >&2
        exit 6
    fi
    echo "[OK] every LOAD segment is 16 KB aligned or better"
}

package_aar() {
    banner "Packaging fms-vpx.aar"
    local classes="$VP9_STAGE_IN/classes.jar"
    local manifest="$VP9_STAGE_IN/AndroidManifest.xml"
    if [[ ! -f "$classes" || ! -f "$manifest" ]]; then
        echo "ERROR: expected classes.jar and AndroidManifest.xml in $VP9_STAGE_IN" >&2
        echo "       Produce them first:  pwsh -NoProfile -File scripts/builders/compile-vp9-classes.ps1" >&2
        exit 7
    fi
    local staging="$WORK_DIR/aar-staging"
    rm -rf "$staging"
    mkdir -p "$staging/jni"
    cp "$classes" "$staging/classes.jar"
    cp "$manifest" "$staging/AndroidManifest.xml"
    for abi in "${BUILT_ABIS[@]}"; do
        mkdir -p "$staging/jni/$abi"
        cp "$LIBS_OUT/$abi"/*.so "$staging/jni/$abi/"
    done
    if [[ -f "$OUT_AAR" ]]; then
        cp "$OUT_AAR" "$EVIDENCE_DIR/fms-vpx_$(date +%Y%m%d-%H%M%S).aar.bak"
    fi
    rm -f "$OUT_AAR"
    ( cd "$staging" && python3 -m zipfile -c "$OUT_AAR" . ) || exit 7
    [[ -f "$OUT_AAR" ]] || exit 7
    {
        echo "fms-vpx.aar  $(stat -c%s "$OUT_AAR") bytes"
        for abi in "${BUILT_ABIS[@]}"; do
            for so in "$LIBS_OUT/$abi"/*.so; do
                echo "  $abi/$(basename "$so")  $(stat -c%s "$so") bytes"
            done
        done
    } > "$EVIDENCE_DIR/aar-size.txt"
    echo "[OK] $OUT_AAR"
    cat "$EVIDENCE_DIR/aar-size.txt"
}

preflight
fetch_libvpx
generate_configs
build_abis
verify_alignment
package_aar

banner "SUCCESS"
echo "Next: wire the AAR into app_v2/build.gradle.kts, then build the standard flavor."
exit 0
