# Phase 02 - Stream Capability Contract

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Introduce a flavor-gated `StreamProtocolSupport` contract: a `src/main` interface, a full implementation in `src/streamingEnabled/java` (HLS/DASH/RTSP), a progressive-only implementation in `src/streamingDisabled/java`, and Hilt bindings in each bucket. This isolates the `RtspMediaSource` class reference so lite/photos compile without the RTSP module.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`media3-exoplayer-rtsp` on classpath for streamingEnabled flavors; `ResourceType.RTSP_STREAM` exists).
- [ ] Reviewed the existing flavor pattern: `src/cloudEnabled/.../di/IdentityModule.kt` (real `@Binds`) vs `src/cloudDisabled/.../di/NoOpIdentityModule.kt` (no-op `@Binds`). Existing buckets `src/streamingEnabled` / `src/streamingDisabled` already carry `di/StreamingModule.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/player/StreamProtocolSupport.kt` | New | ≤ 60 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/player/streaming/FullStreamProtocolSupport.kt` | New | ≤ 90 |
| `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/player/streaming/ProgressiveOnlyStreamProtocolSupport.kt` | New | ≤ 60 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/player/streaming/di/StreamProtocolModule.kt` | New | ≤ 35 |
| `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/player/streaming/di/StreamProtocolModule.kt` | New | ≤ 35 |

> **Flavor placement.** The contract interface stays in `src/main`. The two impls and their Hilt modules live ONLY in the flavor buckets - never `src/main`. No `BuildConfig.IS_*` guards anywhere. See `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

## Steps

### Step 02.1 - Define `StreamProtocolSupport` contract in `src/main`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/player/StreamProtocolSupport.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `StreamProtocolSupport` exposing: `val supportsSegmentedStreaming: Boolean` (HLS/DASH module present), `val supportsRtsp: Boolean`, and `fun createRtspMediaSource(uri: Uri, dataSourceFactory: DataSource.Factory): MediaSource?` returning `null` when RTSP is unsupported. Use `androidx.media3.datasource.DataSource` and `androidx.media3.exoplayer.source.MediaSource` types - these are in `media3-exoplayer` which is on the classpath for ALL flavors, so the interface compiles everywhere. Do NOT reference `RtspMediaSource`/`HlsMediaSource` in this `src/main` interface.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `interface StreamProtocolSupport` matches exactly once.
- `Grep` - no `RtspMediaSource` import / `.Factory` usage (only the method name `createRtspMediaSource` + KDoc carry the substring).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: domain/player/StreamProtocolSupport.kt (New, ~30 LOC). Uses MediaSource/DataSource (all-flavor classpath); no RtspMediaSource class reference.

---

### Step 02.2 - Full impl (streamingEnabled): RTSP over TCP + segmented support

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/player/streaming/FullStreamProtocolSupport.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class FullStreamProtocolSupport @Inject constructor() : StreamProtocolSupport`. Set `supportsSegmentedStreaming = true`, `supportsRtsp = true`. Implement `createRtspMediaSource` with `androidx.media3.exoplayer.rtsp.RtspMediaSource.Factory().setForceUseRtpTcp(true)` (baseline = RTP-over-RTSP/TCP interleaved, research §6 item 3) `.createMediaSource(MediaItem.fromUri(uri))`. This file is the only place `RtspMediaSource` is referenced; it compiles because `media3-exoplayer-rtsp` is on the classpath in these flavors.

**Verification:**

- `Glob` - the file exists under `src/streamingEnabled/java/`.
- `Grep` - `RtspMediaSource.Factory` present and `setForceUseRtpTcp(true)` present.
- `Grep` - `supportsRtsp` is `true`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: streamingEnabled/.../FullStreamProtocolSupport.kt (New). RtspMediaSource.Factory + setForceUseRtpTcp(true); @UnstableApi for media3 rtsp API.

---

### Step 02.3 - Progressive-only impl (streamingDisabled)

**Files:** `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/player/streaming/ProgressiveOnlyStreamProtocolSupport.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class ProgressiveOnlyStreamProtocolSupport @Inject constructor() : StreamProtocolSupport` for lite/photos. Set `supportsSegmentedStreaming = false`, `supportsRtsp = false`, and `createRtspMediaSource(...) = null`. No `RtspMediaSource`/`HlsMediaSource` reference - those modules are absent in these flavors. Progressive http(s) audio still plays via core extractors (handled by the caller's `DefaultMediaSourceFactory` path), so this impl deliberately returns null for segmented/RTSP.

**Verification:**

- `Glob` - the file exists under `src/streamingDisabled/java/`.
- `Grep` - `supportsRtsp` is `false` and `createRtspMediaSource` returns `null`.
- `Grep` - `RtspMediaSource` and `HlsMediaSource` imports/Factory return zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Files: streamingDisabled/.../ProgressiveOnlyStreamProtocolSupport.kt (New). All caps false; createRtspMediaSource null; no segmented/rtsp class refs.

---

### Step 02.4 - Hilt bindings in both buckets

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/player/streaming/di/StreamProtocolModule.kt`, `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/player/streaming/di/StreamProtocolModule.kt`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> In each bucket add an `@Module @InstallIn(SingletonComponent::class) abstract class StreamProtocolModule` with one `@Binds abstract fun bindStreamProtocolSupport(impl: <BucketImpl>): StreamProtocolSupport`. streamingEnabled binds `FullStreamProtocolSupport`; streamingDisabled binds `ProgressiveOnlyStreamProtocolSupport`. AGP mounts exactly one bucket per flavor, so exactly one binding exists per variant - no duplicate-binding conflict. Use a distinct module name from the existing `StreamingModule` in each bucket.

**Verification:**

- `Grep` - `interface\|class StreamProtocolModule` (the `@Module`) present in both bucket paths.
- `Grep` - `provideStreamProtocolSupport` present in both files; binds `FullStreamProtocolSupport` in enabled, `ProgressiveOnlyStreamProtocolSupport` in disabled.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Files: streamingEnabled + streamingDisabled .../di/StreamProtocolModule.kt (New). Object module + @Provides @Singleton (matching existing StreamingModule convention, not @Binds).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles for both a streamingEnabled flavor (standard, `.\a.ps1 fk`) and a streamingDisabled flavor (lite, `compileLiteDebugKotlin` via `/build`) - proving no `RtspMediaSource` symbol leaks into the disabled bucket.
- [ ] `Grep` for `RtspMediaSource` in `src/main` and `src/streamingDisabled` returns zero hits.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

- Inject `StreamProtocolSupport` wherever a stream MediaSource is built (Phase 04 helper) or where UI must reject an unsupported scheme (Phase 06).
- `supportsRtsp` / `supportsSegmentedStreaming` are the only capability signals - never branch on flavor name or `BuildConfig.IS_*`.

---

## Rollback Plan

Revert phase commit(s). New files only plus two flavor Hilt modules - no schema or user-facing surface; safe to delete.
