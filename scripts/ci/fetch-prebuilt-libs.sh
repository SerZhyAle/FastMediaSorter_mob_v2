#!/usr/bin/env bash
# fetch-prebuilt-libs.sh
# =============================================================================
# Downloads the prebuilt binaries a CI build needs but the repository does not
# carry, into the paths Gradle expects.
#
# Why this exists: app_v2/libs/ is gitignored (.gitignore "libs/"), so the
# FFmpeg DTS AAR that app_v2/build.gradle.kts declares as a hard dependency for
# the standard, noLegal, legacy and vr flavors never reaches a CI checkout.
# Every run resolved an absent file and died before lint or tests could run -
# 69 red runs in a row before S1539. The asset lives at a permanent address in
# the delivery release; publisher: scripts/builders/publish-ffmpeg-dts-aar.ps1.
#
# Shared by every job in android-ci.yml so the three of them cannot drift.
#
# Exit: 0 - every artifact present and non-empty
#       1 - a download failed or produced an empty file
# =============================================================================
set -euo pipefail

DELIVERY_TAG="delivery-so-v1"
REPO="${GITHUB_REPOSITORY:-SerZhyAle/FastMediaSorter_mob_v2}"

mkdir -p app_v2/libs

echo "Fetching fms-ffmpeg-dts.aar from release ${DELIVERY_TAG} .."
gh release download "${DELIVERY_TAG}" \
  --repo "${REPO}" \
  --pattern "fms-ffmpeg-dts.aar" \
  --dir app_v2/libs \
  --clobber

# A truncated or zero-byte download fails much later inside an opaque Gradle
# transform, so assert the shape here where the error still names the cause.
if [ ! -s app_v2/libs/fms-ffmpeg-dts.aar ]; then
  echo "::error::fms-ffmpeg-dts.aar is missing or empty after download" >&2
  exit 1
fi

echo "app_v2/libs/fms-ffmpeg-dts.aar $(stat -c%s app_v2/libs/fms-ffmpeg-dts.aar) bytes"
sha256sum app_v2/libs/fms-ffmpeg-dts.aar
