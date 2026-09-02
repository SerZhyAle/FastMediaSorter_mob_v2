# On-demand delivery inventory (S0386)

Human-readable index of the heavy payloads delivered on demand. The binaries themselves are **not**
committed here (that would bloat the git history); they live as **GitHub Release assets** at a
permanent address, which is free and does not count against the repo size. This file is the visible
inventory of what is hosted.

## Permanent address

```
https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/<asset>
```

The release tag `delivery-so-v1` is a **permanent home**, not a version - every app version fetches
from here. (The `-v1` in the tag is legacy naming; element versioning lives in the file name, below.)

## Naming convention - version is in the FILE NAME

```
<abi>-<soname>-<rev>.so      e.g.  arm64-v8a-libtesseract-v1.so
<name>-<rev>.<ext>           e.g.  anim_audio_bg_1-v1.mp4
```

- `<abi>` ∈ { arm64-v8a, armeabi-v7a, x86, x86_64 } (Paddle: arm64-v8a only).
- `<rev>` is the element revision. When an element changes incompatibly (e.g. a new OCR engine that
  needs app changes), upload a **new file** `…-v2.so` next to the old one; the old `…-v1.so` stays
  forever so already-released app versions keep working. The app's compiled
  `DeliverableDescriptorCatalog` pins, per app version, the exact file name + its SHA-256 + size, so a
  new app version requests `-v2` while older ones keep requesting `-v1`. Never delete an old rev.
- On the device the file is stored under its plain name (`libtesseract.so`); only the remote asset
  name carries the ABI + rev.
- **Exception (S1483) - artwork payloads carry no rev.** The channel-preview and stream-logo atlases
  are published under stable names (`channel-preview-tiles.zip`, `stream-logo-tiles.zip` and their
  `-coords.json` sidecars), so a rebuild lands exactly where existing installs already look. They are
  refreshed in place rather than versioned alongside.

## Authoritative integrity anchors

The pinned SHA-256 + min-size for every file live in
`app_v2/.../data/delivery/DeliverableDescriptorCatalog.kt` (compiled into the APK). The mirror cannot
substitute a payload - a mismatch fails verification and the app falls back to the bundled descriptor.

**Exception (S1483) - the artwork payloads are not hash-pinned.** The two atlases are verified by
structure and minimum size instead of by a hash frozen into the build, which is what lets them be
refreshed in place under a stable name. Every other payload is pinned as described above.

## What state a set is in

An asset sitting on the release proves nothing about whether the app still uses it: revisions are
**never deleted**, so a withdrawn payload looks exactly like a live one. The only thing that decides
whether a set travels over the network is whether it appears in `bundledSets()` of the flavor's
`app_v2/src/<flavor>/java/com/sza/fastmediasorter/di/*BundledDeliverableSetsModule.kt` - a set listed
there reports as already installed and its download is suppressed. The three sections below are keyed
on that, so each set appears in exactly one of them (S2066).

## Delivered over the network (absent from `bundledSets()`)

- **Set C - audio-player background videos**: `anim_audio_bg_1-v1.mp4` .. `anim_audio_bg_11-v1.mp4` (H.264 Constrained Baseline, 1024x576, 24fps, ~800k cap - tuned for low-power car/cheap audio decoders; S0407)
- **Channel-preview atlas (S1154)** - streams flavors: `channel-preview-tiles.zip`,
  `channel-preview-coords.json`. A pure data payload, so it is not gated as native code and downloads
  on Play installs too. Stable names, no rev, no pinned hash - see the S1483 note above.
- **Stream logo atlas (S1201)** - streams flavors: `stream-logo-tiles.zip`,
  `stream-logo-coords.json`. Same shape and same S1483 policy as the preview atlas; it exists
  separately because a station with no video track can never have a captured frame, and the two
  payloads are worth refusing independently.
- **Set E - libVLC decoder (noLegal, arm64-v8a only)** - `arm64-v8a-libvlc-v1.so` (46,087,168 bytes),
  `arm64-v8a-libvlcjni-v1.so` (94,440 bytes). Extracted from `org.videolan.android:libvlc-all:3.7.5`
  and republished by `pwsh -NoProfile -File scripts/builders/publish-libvlc-so.ps1`, which refuses to
  upload when a measured hash differs from the pin compiled into `DeliverableDescriptorCatalog.kt`.
  This set is **downloaded, never bundled**: it is the reason S1971 exists, since the two files are
  27 % of the noLegal APK. Descriptor order matters - `libvlc.so` loads before `libvlcjni.so`, because
  libVLC's own `loadLibraries()` answers a failed load with `System.exit(1)` rather than an exception.
  `libc++_shared.so` is **not** part of the set: the APK's copy comes from another dependency and the
  runtime pairing is left exactly as it is today.

## Bundled in the APK - asset retained for older installs

S0971 returned these to the APK for every flavor, so no shipped version fetches them over the network.
All four flavor modules list them in `bundledSets()`. The assets stay hosted because app versions
released before that change still request them.

- **Set B - OCR engines (Tesseract)** - all 4 ABIs, 4 libs each:
  `<abi>-libjpeg-v1.so`, `<abi>-libpngx-v1.so`, `<abi>-libleptonica-v1.so`, `<abi>-libtesseract-v1.so`
- **Set D - FFmpeg DTS decoder** - all 4 ABIs: `<abi>-libffmpegJNI-v1.so`
- **Set A - ML Kit translation** is bundled too, and is NOT hosted here at all: on store it ships via
  a Play dynamic-feature; on sideload/VR it stays bundled in the base (Google `.so` are not re-hosted).

## Withdrawn from the app

The app carries no descriptor for these, so nothing requests them. The assets remain on the release
only because a revision is never deleted - an install from before the withdrawal must keep working.

- **PaddleOCR (was part of Set B, noLegal)** - arm64-v8a only:
  `arm64-v8a-libpaddle_light_api_shared-v1.so`, `arm64-v8a-libpaddle_lite_jni-v1.so`. Withdrawn by
  S1703; `DeliverableDescriptorCatalog.kt` no longer holds a `PADDLE` map, and Tesseract is now the
  only OCR engine any flavor delivers.

## Build-time asset (not an on-demand payload)

`fms-ffmpeg-dts.aar` (11,495,586 bytes, sha256 `decba6f7f40fb823bb108246e53a1740ad4a6ebe95fbdfaba1ea2f782b5f961a`)
shares this release but plays a different role: **no shipped app version fetches it.** It is a
build-time dependency, declared in `app_v2/build.gradle.kts` for the standard, noLegal, legacy and vr
flavors, and hosted here only so GitHub Actions can build the app - `app_v2/libs/` is gitignored, so
a CI checkout has no copy and every run died resolving it (S1539).

Two consequences follow from it being build-time only, and they invert the rules above:

- The name carries **no rev** and the asset is **clobbered** on every rebuild. No released app pins
  it, so there is no old revision to keep alive - CI always wants the current binary.
- Its hash is **not** in `DeliverableDescriptorCatalog.kt`. The hash above is recorded here for human
  comparison only; nothing verifies it at runtime because nothing downloads it at runtime.

Republish after rebuilding the AAR: `pwsh -NoProfile -File scripts/builders/publish-ffmpeg-dts-aar.ps1`.
Consumed by `scripts/ci/fetch-prebuilt-libs.sh`, which every build job in `android-ci.yml` and
`maestro-tests.yml` runs before Gradle.

## Optional URL-override manifest

`delivery-so-v1/delivery-manifest.json` is an optional, stationary file that can override source URLs
(not hashes) to move endpoints without an app release. It is currently absent (the app falls back to
the compiled URLs above), which is a handled, non-error condition.
