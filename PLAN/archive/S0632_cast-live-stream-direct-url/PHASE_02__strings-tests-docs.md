# Phase 02 - Strings, unit test, capability record

**Strategic spec:** `PLAN/S0632_cast-live-stream-direct-url.md`
**Status:** Done
**Depends on:** Phase 01

## Objective

Add the trilingual unsupported-protocol string, a pure unit test for `CastStreamResolver`, and the
capability inventory record.

## Steps

### Step 1 - Unsupported-protocol string (EN/RU/UK)

Add key `cast_stream_unsupported_protocol` across EN/RU/UK in lockstep via the string tool.

- EN: `This stream protocol can't be cast`
- RU: `Этот протокол трансляции нельзя транслировать на Chromecast`
- UK: `Цей протокол трансляції не можна транслювати на Chromecast`

Command:

```powershell
pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key cast_stream_unsupported_protocol -En "This stream protocol can't be cast" -Ru "..." -Uk "..."
```

(RU/UK literals carry Cyrillic - author them in the .ps1 call run from PowerShell, not via a bash->pwsh arg boundary.)

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix cast_stream_unsupported_protocol` exits 0.
- Grep `app_v2/src/main/res/values/strings.xml` for `cast_stream_unsupported_protocol` - present.

### Step 2 - CastStreamResolver unit test

Create `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CastStreamResolverTest.kt`.

Assert:
- `http://h/live.m3u8` -> `Direct("application/x-mpegurl")`.
- `https://h/live.mpd` -> `Direct("application/dash+xml")`.
- `http://h/movie.mp4` -> `Direct("video/mp4")`.
- `https://h/live` (no extension) -> `Direct("application/x-mpegurl")`.
- `rtsp://h/live` -> `UnsupportedProtocol`.
- `/storage/emulated/0/v.mp4` -> `NotAStream`.
- `smb://server/share/v.mp4` -> `NotAStream`.
- `cloud://drive/v.mp4` -> `NotAStream`.

**Verification:**

- Glob test file exists.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*CastStreamResolverTest"` passes (per-class XML report green).

### Step 3 - Capability inventory record

Add an `ALL_FEATURES` record for the delivered capability.

```powershell
pwsh -NoProfile -File scripts/all_features/add.ps1 -Spec S0632 -Op CHANGE -Desc "Cast a live video stream (HLS/DASH/HTTP) directly to Chromecast; RTSP reported as unsupported"
```

**Verification:**

- Grep `docs/ALL_FEATURES.jsonl` for `S0632` - one record present.

## Phase Done Criteria

1. `cast_stream_unsupported_protocol` present in EN/RU/UK; locale audit exits 0.
2. `CastStreamResolverTest` passes (all 8 cases).
3. `docs/ALL_FEATURES.jsonl` has an S0632 record.
4. `.\a.ps1 fc` (code + resources) passes for the change set.
