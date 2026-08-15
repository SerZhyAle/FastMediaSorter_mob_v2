# Phase 01 - Foundations

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Establish build, manifest, security, and model foundations for stream playback: RTSP Gradle module per flavor, a `SUPPORT_STREAMS` BuildConfig flag, relaxed cleartext policy, and new `ResourceType` stream cases. No playback logic yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Research artifact read: HLS/DASH already wired for standard/legacy/noLegal/vr; RTSP module absent (research §6 item 1, §9).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ +25 |
| `app_v2/src/main/res/xml/network_security_config.xml` | Modified | ≤ +6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/player/StreamUri.kt` | New | ≤ 25 |

> No layout files. No flavor source-set files in this phase.

---

## Steps

### Step 01.1 - Add `media3-exoplayer-rtsp` to streaming-capable flavors only

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `androidx.media3:media3-exoplayer-rtsp:1.2.1` only to the flavors that already carry `media3-exoplayer-hls`/`-dash`: standard, noLegal, legacy, vr. Place the four lines next to the existing per-flavor HLS/DASH block (search `media3-exoplayer-hls`). Do NOT add it to lite or photos - they keep their APK budget and stay RTSP-free.

**Verification:**

- `Grep` - `media3-exoplayer-rtsp:1.2.1` matches exactly 4 times in `app_v2/build.gradle.kts`.
- `Grep` - the 4 matches are prefixed `standardImplementation` / `noLegalImplementation` / `legacyImplementation` / `vrImplementation` (no `liteImplementation` / `photosImplementation`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Files: app_v2/build.gradle.kts (+6 LOC, 4 rtsp deps standard/noLegal/legacy/vr). Dev log at phase boundary.

---

### Step 01.2 - Add `SUPPORT_STREAMS` BuildConfig flag per flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `buildConfigField("boolean", "SUPPORT_STREAMS", "<value>")` to each flavor block alongside the existing `SUPPORT_AUDIO` line. Values: `true` for standard, noLegal, legacy, vr, lite; `false` for photos. This flag gates only entry-point visibility (the "Трансляции" surface), not protocol richness - lite is `true` but stays progressive-only via the streamingDisabled source set (Phase 02). photos is `false` because it has `SUPPORT_AUDIO=false` and `SUPPORT_VIDEO=false` and gets no stream surface.

**Verification:**

- `Grep` - `SUPPORT_STREAMS` matches exactly 6 times in `app_v2/build.gradle.kts`.
- `Grep` - `"SUPPORT_STREAMS", "false"` matches exactly once (photos).
- `Grep` - `"SUPPORT_STREAMS", "true"` matches exactly 5 times.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: app_v2/build.gradle.kts (+6 LOC). standard/noLegal/lite/legacy/vr=true, photos=false.

---

### Step 01.3 - Relax base cleartext policy for arbitrary public stream hosts

**Files:** `app_v2/src/main/res/xml/network_security_config.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Per owner decision (strategic §0 / §3.3 HTTP cleartext policy, research §4 P0, §6 item 1): public `http://` streams are permitted without an allowlist. Set the base-config to `cleartextTrafficPermitted="true"`. Keep the existing private-range, cloud-domain, and debug-override blocks intact. Add a short EN comment above the base-config explaining that cleartext is permitted app-wide to support arbitrary public radio/stream hosts (S0565) - explain WHY, not WHAT.

**Verification:**

- `Grep` - `<base-config cleartextTrafficPermitted="true">` present in the file.
- `Grep` - `<base-config cleartextTrafficPermitted="false">` returns zero hits.
- `Grep` - existing `192.168` private-range domain-config still present (no regression).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: network_security_config.xml. base-config -> true (owner-approved); private/cloud/debug blocks intact.

---

### Step 01.4 - Stream URI classifier (scheme-based routing, NOT a ResourceType value)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/player/StreamUri.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> **Design decision (revised during execution).** A first attempt added `HTTP_STREAM`/`RTSP_STREAM` to the shared `ResourceType` enum; it broke ~28 exhaustive `when` sites across scan/hash/icon/browse/widget code - subsystems that model browsable file resources and never receive a stream. Streams are URLs to play, not browsable resources, so routing them by URI scheme in the player dispatch (Phase 04) is both lower-risk and better-modeled (research §2 "Альтернатива"). `ResourceType` is left unchanged.
> Create `object StreamUri` with `isRtsp(path)`, `isHttp(path)`, and `isStream(path)` (rtsp OR http(s), case-insensitive). This is the single source of truth the player dispatch consumes.

**Verification:**

- `Glob` - `StreamUri.kt` exists.
- `Grep` - `object StreamUri` + `fun isStream(` present.
- `Grep` - `HTTP_STREAM` / `RTSP_STREAM` return zero hits in `Models.kt` (enum unchanged - no exhaustiveness fan-out).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Initial enum approach reverted after build FAIL (28 exhaustive `when` sites broke). Replaced with `StreamUri` scheme classifier - `ResourceType` untouched, no fan-out. Verification 3/3 PASS.

---

### Step 01.5 - Confirm WAKE_LOCK + INTERNET manifest readiness

**Files:** (no edit unless missing) `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Background radio uses `setWakeMode(C.WAKE_MODE_NETWORK)`, which requires `WAKE_LOCK`. Confirm `WAKE_LOCK`, `INTERNET`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` are declared in the main manifest. They already exist (WAKE_LOCK has a `tools:remove="android:maxSdkVersion"` override). Add any that are missing; otherwise make no edit and record the grep result.

**Verification:**

- `Grep` - `android.permission.WAKE_LOCK`, `android.permission.INTERNET`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` each present in `AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 1/1 PASS (grep 1/1/1). No edit - all required permissions already declared.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles for the default variant - run `/build` (`.\a.ps1 fk`); the `ResourceType` and gradle changes resolve.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the foundations change via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `StreamUri.isStream/isRtsp/isHttp` is the dispatch seam (Phase 04 short-circuits `playVideo()` on `StreamUri.isStream(path)` before the `when(resourceType)` block). `ResourceType` is unchanged - no enum branch, no determineResourceType change needed.
- `media3-exoplayer-rtsp` on classpath for standard/noLegal/legacy/vr only - any direct `RtspMediaSource` reference MUST live in `src/streamingEnabled/java` (Phase 02), never `src/main`.
- `BuildConfig.SUPPORT_STREAMS` available for entry-point gating (Phase 07).

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. Cleartext relaxation is the only posture change; reverting restores HTTPS-only base.
