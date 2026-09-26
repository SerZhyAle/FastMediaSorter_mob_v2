#!/usr/bin/env bash
# build-dav1d-av1.sh
# ==============================================================================
# S1059 - software AV1 video decoder extension (dav1d) as a decoder-fallback
# backstop. Produces: app_v2/libs/fms-av1.aar
#
# Runs inside WSL2 / Linux. The Java half is compiled on the Windows side first
# by scripts/builders/compile-av1-classes.ps1, which also clones the media3
# checkout this script builds from.
#
# Usage (from the project root; MSYS_NO_PATHCONV keeps Git Bash from rewriting
# the /mnt path):
#   pwsh -NoProfile -File scripts/builders/compile-av1-classes.ps1
#   MSYS_NO_PATHCONV=1 wsl bash scripts/builders/build-dav1d-av1.sh /mnt/p/ANDROID/FastMediaSorter_mob_v2
#
# Environment overrides:
#   ANDROID_NDK   - default $HOME/android-ndk-r25c
#   MEDIA3_TAG    - default 1.11.0, must equal the app's media3 pin
#   MEDIA3_DIR    - default $HOME/media3-$MEDIA3_TAG
#   WORK_DIR      - default $HOME/av1-build
#
# Version pins - all load-bearing:
#   media3 1.11.0 : DefaultRenderersFactory reflects androidx.media3.decoder.av1.
#                   Libdav1dVideoRenderer by name. Media3 1.2.1 shipped a libgav1
#                   renderer under another class name, which 1.11.0 never loads.
#   dav1d 1.5.1, cpu_features v0.9.0 : fetched by tag and verified.
#   meson/ninja/nasm : dav1d's build tools, bootstrapped per user under
#                   $WORK_DIR/tools because the WSL guest has no root access.
#
# Exit codes:
#   0  the AAR was written to app_v2/libs/fms-av1.aar
#   1  bad arguments
#   2  a required input or tool is missing
#   3  the Java half is not staged yet
#   4  a source checkout is not at its pinned tag
#   5  a native build failed
#   6  a library has a LOAD segment below 16 KB alignment
#   7  AAR packaging failed
# ==============================================================================

set -euo pipefail

PROJECT_MOUNT="${1:-}"
if [[ -z "$PROJECT_MOUNT" || ! -d "$PROJECT_MOUNT" ]]; then
    echo "ERROR: pass the project mount path as the first argument" >&2
    exit 1
fi

: "${ANDROID_NDK:=$HOME/android-ndk-r25c}"
: "${MEDIA3_TAG:=1.11.0}"
: "${MEDIA3_DIR:=$HOME/media3-$MEDIA3_TAG}"
: "${WORK_DIR:=$HOME/av1-build}"
: "${AV1_STAGE_IN:=$WORK_DIR/stage-in}"
: "${CPU_FEATURES_TAG:=v0.9.0}"
: "${DAV1D_TAG:=1.5.1}"
MESON_VERSION=1.5.2
NINJA_VERSION=1.12.1
NASM_VERSION=2.16.03
# API 23: the legacy flavor (minSdk 23) bundles this AAR, so nothing may bind a
# symbol that API 23 devices do not export.
ANDROID_API=23

TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
READELF="$TOOLCHAIN/llvm-readelf"
STRIP="$TOOLCHAIN/llvm-strip"
AV1_MODULE="$MEDIA3_DIR/libraries/decoder_av1"
JNI_SOURCE="$WORK_DIR/decoder_av1_jni"
BUILD_ROOT="$WORK_DIR/out"
TOOLS="$WORK_DIR/tools"
OUT_AAR="$PROJECT_MOUNT/app_v2/libs/fms-av1.aar"
EVIDENCE_DIR="$PROJECT_MOUNT/temp/S1059"
ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

declare -A NDK_TARGET=(
    ["arm64-v8a"]="aarch64-linux-android$ANDROID_API"
    ["armeabi-v7a"]="armv7a-linux-androideabi$ANDROID_API"
    ["x86"]="i686-linux-android$ANDROID_API"
    ["x86_64"]="x86_64-linux-android$ANDROID_API"
)
declare -A CROSS_FILE=(
    ["arm64-v8a"]="aarch64-android.meson"
    ["armeabi-v7a"]="arm-android.meson"
    ["x86"]="x86-android.meson"
    ["x86_64"]="x86_64-android.meson"
)

require_tag() {
    local dir="$1" tag="$2"
    [[ "$(git -C "$dir" describe --tags --exact-match 2>/dev/null)" == "$tag" ]] || {
        echo "ERROR: $dir is not at pinned tag $tag" >&2
        exit 4
    }
}

preflight() {
    for required in "$READELF" "$STRIP" "$TOOLCHAIN/${NDK_TARGET[arm64-v8a]}-clang" \
        "$AV1_MODULE/src/main/jni/CMakeLists.txt" "$AV1_MODULE/proguard-rules.txt"; do
        [[ -e "$required" ]] || { echo "ERROR: required input missing: $required" >&2; exit 2; }
    done
    for tool in cmake git python3 curl make gcc; do
        command -v "$tool" >/dev/null || { echo "ERROR: $tool is required" >&2; exit 2; }
    done
    require_tag "$MEDIA3_DIR" "$MEDIA3_TAG"
    [[ -f "$AV1_STAGE_IN/classes.jar" && -f "$AV1_STAGE_IN/AndroidManifest.xml" ]] || {
        echo "ERROR: compile AV1 classes first with scripts/builders/compile-av1-classes.ps1" >&2
        exit 3
    }
}

bootstrap_tools() {
    mkdir -p "$TOOLS/bin"
    if [[ ! -f "$TOOLS/meson-$MESON_VERSION/meson.py" ]]; then
        curl -fsSL -o "$TOOLS/meson.tar.gz" \
            "https://github.com/mesonbuild/meson/releases/download/$MESON_VERSION/meson-$MESON_VERSION.tar.gz"
        tar -xzf "$TOOLS/meson.tar.gz" -C "$TOOLS"
    fi
    printf '#!/usr/bin/env bash\nexec python3 "%s" "$@"\n' "$TOOLS/meson-$MESON_VERSION/meson.py" > "$TOOLS/bin/meson"
    chmod +x "$TOOLS/bin/meson"
    if [[ "$("$TOOLS/bin/ninja" --version 2>/dev/null)" != "$NINJA_VERSION" ]]; then
        curl -fsSL -o "$TOOLS/ninja.zip" \
            "https://github.com/ninja-build/ninja/releases/download/v$NINJA_VERSION/ninja-linux.zip"
        python3 -m zipfile -e "$TOOLS/ninja.zip" "$TOOLS/bin"
        chmod +x "$TOOLS/bin/ninja"
    fi
    if ! "$TOOLS/bin/nasm" -v 2>/dev/null | grep -q "$NASM_VERSION"; then
        curl -fsSL -o "$TOOLS/nasm.tar.gz" \
            "https://www.nasm.us/pub/nasm/releasebuilds/$NASM_VERSION/nasm-$NASM_VERSION.tar.gz"
        tar -xzf "$TOOLS/nasm.tar.gz" -C "$TOOLS"
        (cd "$TOOLS/nasm-$NASM_VERSION" && ./configure --prefix="$TOOLS" >/dev/null && make -j "$(nproc)" >/dev/null && make install >/dev/null)
    fi
    export PATH="$TOOLS/bin:$PATH"
    meson --version >/dev/null && ninja --version >/dev/null && nasm -v >/dev/null || {
        echo "ERROR: meson/ninja/nasm bootstrap failed" >&2
        exit 2
    }
}

clone_at_tag() {
    local url="$1" tag="$2" target="$3"
    rm -rf "$target"
    git clone -q --depth 1 --branch "$tag" "$url" "$target"
    require_tag "$target" "$tag"
}

prepare_sources() {
    rm -rf "$JNI_SOURCE"
    mkdir -p "$WORK_DIR" "$EVIDENCE_DIR"
    cp -R "$AV1_MODULE/src/main/jni" "$JNI_SOURCE"
    clone_at_tag https://github.com/google/cpu_features.git "$CPU_FEATURES_TAG" "$JNI_SOURCE/cpu_features"
    clone_at_tag https://code.videolan.org/videolan/dav1d.git "$DAV1D_TAG" "$JNI_SOURCE/dav1d"
}

build_dav1d() {
    local abi="$1"
    local build_dir="$BUILD_ROOT/dav1d-$abi"
    local cross="$build_dir.meson"
    mkdir -p "$build_dir"
    sed -e "s|^c = .*|c = '$TOOLCHAIN/${NDK_TARGET[$abi]}-clang'|" \
        -e "s|^cpp = .*|cpp = '$TOOLCHAIN/${NDK_TARGET[$abi]}-clang++'|" \
        -e "s|^ar = .*|ar = '$TOOLCHAIN/llvm-ar'|" \
        -e "s|^strip = .*|strip = '$TOOLCHAIN/llvm-strip'|" \
        "$JNI_SOURCE/dav1d/package/crossfiles/${CROSS_FILE[$abi]}" > "$cross"
    meson setup "$build_dir" "$JNI_SOURCE/dav1d" --cross-file="$cross" --default-library=static \
        --buildtype=release -Denable_tools=false -Denable_tests=false >/dev/null
    ninja -C "$build_dir" >/dev/null
    mkdir -p "$JNI_SOURCE/nativelib/$abi"
    cp "$build_dir/src/libdav1d.a" "$JNI_SOURCE/nativelib/$abi/"
}

build_abis() {
    rm -rf "$BUILD_ROOT"
    for abi in "${ABIS[@]}"; do
        local jni_dir="$BUILD_ROOT/jni-$abi"
        if build_dav1d "$abi" && cmake -S "$JNI_SOURCE" -B "$jni_dir" \
            -DANDROID_ABI="$abi" \
            -DANDROID_PLATFORM="android-$ANDROID_API" \
            -DCMAKE_BUILD_TYPE=Release \
            -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
            -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' >/dev/null &&
            cmake --build "$jni_dir" --target dav1dJNI -j "$(nproc)" >/dev/null; then
            local library
            library="$(find "$jni_dir" -maxdepth 4 -type f -name libdav1dJNI.so -print -quit)"
            if [[ -n "$library" ]]; then
                mkdir -p "$BUILD_ROOT/libs/$abi"
                "$STRIP" --strip-unneeded -o "$BUILD_ROOT/libs/$abi/libdav1dJNI.so" "$library"
                echo "[OK] $abi"
                continue
            fi
        fi
        echo "ERROR: AV1 native build failed for $abi" >&2
        exit 5
    done
}

verify_alignment() {
    rm -f "$EVIDENCE_DIR"/readelf-av1-*.txt
    for abi in "${ABIS[@]}"; do
        local library="$BUILD_ROOT/libs/$abi/libdav1dJNI.so"
        "$READELF" -lW "$library" > "$EVIDENCE_DIR/readelf-av1-$abi.txt"
        while read -r align; do
            if (( 16#${align#0x} < 0x4000 )); then
                echo "ERROR: $library has a LOAD segment aligned $align" >&2
                exit 6
            fi
        done < <("$READELF" -lW "$library" | awk '/LOAD/ { print $NF }')
    done
}

package_aar() {
    local staging="$WORK_DIR/aar-staging"
    rm -rf "$staging"
    mkdir -p "$staging/jni"
    cp "$AV1_STAGE_IN/classes.jar" "$staging/classes.jar"
    cp "$AV1_STAGE_IN/AndroidManifest.xml" "$staging/AndroidManifest.xml"
    # The JNI half reads decoder buffer fields by name; without these consumer rules a
    # minified release build renames them and the native lookup fails.
    cp "$AV1_MODULE/proguard-rules.txt" "$staging/proguard.txt"
    for abi in "${ABIS[@]}"; do
        mkdir -p "$staging/jni/$abi"
        cp "$BUILD_ROOT/libs/$abi/libdav1dJNI.so" "$staging/jni/$abi/"
    done
    [[ ! -f "$OUT_AAR" ]] || cp "$OUT_AAR" "$EVIDENCE_DIR/fms-av1_$(date +%Y%m%d-%H%M%S).aar.bak"
    rm -f "$OUT_AAR"
    (cd "$staging" && python3 -m zipfile -c "$OUT_AAR" .)
    [[ -s "$OUT_AAR" ]] || { echo "ERROR: AAR packaging failed" >&2; exit 7; }
    {
        echo "fms-av1.aar $(stat -c%s "$OUT_AAR") bytes (media3 $MEDIA3_TAG, dav1d $DAV1D_TAG)"
        for abi in "${ABIS[@]}"; do
            echo "$abi/libdav1dJNI.so $(stat -c%s "$BUILD_ROOT/libs/$abi/libdav1dJNI.so") bytes"
        done
    } | tee "$EVIDENCE_DIR/av1-aar-size.txt"
}

preflight
bootstrap_tools
prepare_sources
build_abis
verify_alignment
package_aar
echo "SUCCESS: $OUT_AAR"
