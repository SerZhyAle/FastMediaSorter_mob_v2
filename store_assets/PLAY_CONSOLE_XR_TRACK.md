<sub class="doc-stamp">26.09.26 10:04</sub>

# Play Console - Android XR dedicated track (S0556)

Operator runbook for publishing the `xr` flavor to Google Play's dedicated Android XR release track under the phone's package, `com.sza.fastmediasorter`. The phone listing, reviews and cloud sign-in registrations stay shared; only the artifact and its track differ.

## What the artifact is

- Flavor `xr`: the OpenXR playback core of the `vr` flavor, built for Play instead of Meta - Google Play Services, Drive and Cast stay on.
- Package `com.sza.fastmediasorter` - no suffix. A suffix would publish a separate app with no reviews and no cloud sign-in.
- versionCode = the phone's code of the same release + 1 000 000 000. Build the XR bundle with the phone bundle's `-VersionCode` and the two never collide.
- Manifest: `android.software.xr.api.openxr` required, `libopenxr.google.so` declared, the two immersive screens start in Full Space, every other screen opens as a Home Space panel. Gate: `scripts/quality/assert-android-xr-manifest.ps1`.
- ABI: `arm64-v8a` only - every shipping Android XR headset is arm64 and the OpenXR loader ships no other slice.

## Build

1. Build the phone release first (`/skill-release`) and note its versionName and versionCode.
2. Build the XR bundle at the same version:
   `pwsh -NoProfile -File scripts/builders/build-xr-release.ps1 -VersionName <name> -VersionCode <phone code>`
3. The builder refuses to hand over a bundle whose merged manifest or native libraries fail their gate. The bundle lands in `DOWNLOADS/FastMediaSorter_xr_release.aab`.

## Play Console, once

- [ ] Advanced settings -> Form factors: add Android XR.
- [ ] Accept the Android XR program terms if the Console asks.
- [ ] Choose the dedicated Android XR track (not "use the mobile track"): the phone bundle declares `android.software.xr.api.spatial` optional and already floats as a panel on a headset; the dedicated track is what carries the immersive build.
- [ ] Store listing -> Android XR: upload the XR screenshots (see below). The mobile listing texts are shared.

## Each release

- [ ] Upload `FastMediaSorter_xr_release.aab` to the Android XR track - internal testing first, then production.
- [ ] Confirm in the bundle explorer: versionCode starts with `1`, one ABI (`arm64-v8a`), required feature `android.software.xr.api.openxr`.
- [ ] Device catalog for the XR track lists Android XR headsets only; no phone or tablet.
- [ ] Release notes: `store_assets/whats_new*.txt` of the same release.

## Store assets

- [ ] At least one screenshot captured on an Android XR device or the Android XR emulator, of the panel (Home Space) UI.
- [ ] At least one screenshot of immersive playback (Full Space).
- [ ] Optional: a 180, 360 or stereoscopic preview video for the XR listing.

## Regression checklist on an Android XR device or emulator

Install: `pwsh -NoProfile -File scripts/devtest/adb.ps1 install -Apk <xr debug or release apk>`.

- [ ] Launch opens the file browser as a Home Space panel; the panel can be moved and resized without the UI breaking.
- [ ] Every control is reachable with gaze-and-pinch and with a controller; touch targets are at least 48dp.
- [ ] Starting immersive playback of a 180/360 or side-by-side video moves the user into Full Space.
- [ ] Head motion in Full Space shows no judder or tearing; playback keeps its frame rate.
- [ ] Back / exit from immersive playback returns to the same panel, at the same folder.
- [ ] Google sign-in (Drive) and Cast work from the panel.
- [ ] `adb.ps1 log -Grep "OpenXR|fms_diagnostic_xr"` shows the session starting and ending cleanly, with no loader error.
