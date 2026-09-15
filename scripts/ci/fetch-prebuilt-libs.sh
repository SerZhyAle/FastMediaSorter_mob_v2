#!/usr/bin/env bash
# fetch-prebuilt-libs.sh
# =============================================================================
# Downloads the prebuilt binaries a CI build needs but the repository does not
# carry, into the paths Gradle expects.
#
# Why this exists: app_v2/libs/ is gitignored (.gitignore "libs/"), so the
# native AARs that app_v2/build.gradle.kts declares as hard dependencies for
# the standard, noLegal, legacy and vr flavors never reach a CI checkout. The
# FFmpeg DTS AAR failed loudly - every run resolved an absent file and died
# before lint or tests could run, 69 red runs in a row before S1539. The VP9
# AAR failed silently: Gradle answers an absent files("libs/..") with an empty
# collection, so CI stayed green and built an artifact with no software VP9
# decoder in it (S2879).
#
# WHICH AARs is not this script's list: scripts/ci/prebuilt-native-aars.txt is,
# and the publisher and the build's verifyPrebuiltNativeAars task read the same
# file. The asset name is each path's base name. Publisher:
# scripts/builders/publish-prebuilt-native-aar.ps1.
#
# Shared by every job in android-ci.yml so the three of them cannot drift.
#
# Exit: 0 - every artifact present and non-empty
#       1 - a download failed, produced an empty file, or the manifest is absent
# =============================================================================
set -euo pipefail

DELIVERY_TAG="delivery-so-v1"
REPO="${GITHUB_REPOSITORY:-SerZhyAle/FastMediaSorter_mob_v2}"
MANIFEST="scripts/ci/prebuilt-native-aars.txt"

# In CI the bare name is right and stays first. On a developer machine the CLI is installed but on
# no PATH this repository's shells see, so the release met "gh: command not found" with the binary
# in Program Files (S3029). GH_CLI overrides both, for a machine that puts it somewhere else.
if [ -n "${GH_CLI:-}" ]; then
  GH="${GH_CLI}"
elif command -v gh >/dev/null 2>&1; then
  GH="gh"
elif [ -x "/c/Program Files/GitHub CLI/gh.exe" ]; then
  GH="/c/Program Files/GitHub CLI/gh.exe"
elif [ -x "/mnt/c/Program Files/GitHub CLI/gh.exe" ]; then
  GH="/mnt/c/Program Files/GitHub CLI/gh.exe"
else
  echo "::error::the GitHub CLI was not found - not on PATH, not at the known install paths, and GH_CLI is unset" >&2
  exit 1
fi

if [ ! -s "${MANIFEST}" ]; then
  echo "::error::${MANIFEST} is missing or empty - it is the list of build-time AARs" >&2
  exit 1
fi

fetched=0

while IFS= read -r raw || [ -n "${raw}" ]; do
  entry="${raw%%#*}"
  entry="$(echo "${entry}" | tr -d '\r' | xargs || true)"
  [ -z "${entry}" ] && continue

  asset="$(basename "${entry}")"
  dir="$(dirname "${entry}")"
  mkdir -p "${dir}"

  echo "Fetching ${asset} from release ${DELIVERY_TAG} .."
  "${GH}" release download "${DELIVERY_TAG}" \
    --repo "${REPO}" \
    --pattern "${asset}" \
    --dir "${dir}" \
    --clobber

  # A truncated or zero-byte download fails much later inside an opaque Gradle
  # transform, so assert the shape here where the error still names the cause.
  if [ ! -s "${entry}" ]; then
    echo "::error::${entry} is missing or empty after download" >&2
    exit 1
  fi

  echo "${entry} $(stat -c%s "${entry}") bytes"
  sha256sum "${entry}"
  fetched=$((fetched + 1))
done < "${MANIFEST}"

if [ "${fetched}" -eq 0 ]; then
  echo "::error::${MANIFEST} lists no AAR - nothing was fetched" >&2
  exit 1
fi

echo "Fetched ${fetched} prebuilt native AAR(s)."
