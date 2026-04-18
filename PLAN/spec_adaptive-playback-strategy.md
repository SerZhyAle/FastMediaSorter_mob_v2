# Specification: Adaptive Playback Strategy For Progressive Video Sources

**Status:** Draft  
**Date:** 2026-04-17  
**Tier:** 1 — Medium  
**Scope:** Video playback policy for progressive sources in FMS player. No code changes in this task.

---

## 1. Summary

FastMediaSorter should not pursue universal "adaptive resolution" for ordinary video files.

For the current architecture, the practical feature is an **adaptive playback strategy** for progressive sources:

- local files
- SMB
- SFTP
- FTP
- direct cloud file playback

The feature must use existing resource intelligence such as speed-test results, protocol type, buffer presets, and runtime buffering signals to choose a better playback policy.

This specification explicitly drops the idea of changing playback quality based on the incoming file's resolution. For ordinary single-file playback, that is not a real or reliable capability.

---

## 2. Product Decision

### 2.1 Approved Direction

The feature direction is:

- adaptive startup and buffering policy for progressive sources
- optional user-selectable playback strategy modes
- warnings and safe defaults for slow resources and heavy files

### 2.2 Explicitly Rejected Direction

This specification does **not** include:

- auto-switching video resolution for single-file playback
- YouTube-style quality switching for MP4 / MKV / AVI / similar single streams
- UI based on incoming file resolution variants
- pretending that smaller screen size reduces network bitrate for a single encoded file

### 2.3 Future Separate Track

True adaptive quality remains possible only as a separate future feature for:

- HLS / DASH / SmoothStreaming
- real multi-variant sources
- containers with multiple selectable video tracks

That future scope is intentionally excluded here.

---

## 3. Problem Statement

FMS already plays video from sources with very different behaviour:

- local storage with near-zero latency
- NAS / SMB with medium latency and variable throughput
- SFTP / FTP with higher protocol overhead
- cloud playback with higher startup and rebuffer risk

Current player configuration already includes protocol-specific buffering and connection tuning, but the app still lacks a user-facing and policy-driven layer that says:

- how aggressively playback should start
- when playback should prefer smoothness over fast start
- when the user should be warned that a file is too heavy for the current resource
- how to expose a simple, understandable playback mode instead of fake "quality" controls

---

## 4. Goals

1. Define a new feature as `Adaptive Playback Strategy`, not `Adaptive Resolution`.
2. Reuse current speed-test and throttle infrastructure as input signals.
3. Introduce a simple set of playback strategy modes for progressive video sources.
4. Improve startup reliability on slow or unstable resources.
5. Reduce unnecessary buffering stalls for heavy files.
6. Avoid misleading UX language about "quality" where no true quality switching exists.
7. Keep the design implementation-ready without changing code in this task.

---

## 5. Non-Goals

- No code changes in this task.
- No HLS / DASH implementation in this task.
- No transcoding proxy in this task.
- No new server-side media pipeline in this task.
- No resolution picker for ordinary single-file playback.
- No attempt to derive bitrate savings from device screen size alone.

---

## 6. Fast-Win Recommendations

These are the recommended fast wins because they align with the current architecture and do not require reinventing the player.

### 6.1 Fast Win A — Introduce Playback Strategy Modes

Add a small user-facing mode set for progressive video playback:

- `Auto`
- `Fast start`
- `Prefer smoothness`
- `Buffer more before play`

Why this is a fast win:

- it matches what the player can actually control today
- it avoids fake "quality" UX
- it can reuse current buffer and protocol hooks

### 6.2 Fast Win B — Use Speed-Test Result As Startup Heuristic

Use stored speed-test result only as an initial policy hint for:

- startup buffer target
- rebuffer threshold
- warning severity

Why this is a fast win:

- the data already exists
- the connection manager already stores recommended threads, buffer size, and measured speed
- this gives immediate product value without new media formats

### 6.3 Fast Win C — Add Heavy-File Warning On Slow Resources

Before autoplay, detect obvious mismatch cases such as:

- slow SMB / cloud resource
- selected video track reports very high bitrate
- previous playback on this resource recently rebuffered repeatedly

Recommended UX:

- show a small warning or suggestion, not a blocking error
- offer `Play now` and `Buffer first`

Why this is a fast win:

- improves perceived intelligence immediately
- requires no transcoding and no fake resolution switching

### 6.4 Fast Win D — Add Runtime Stall Feedback Into Policy

Let runtime playback events override historical speed-test assumptions.

Example:

- if repeated rebuffers happen, the player should stop trusting the saved speed-test result for the current session and move toward a more conservative policy

Why this is a fast win:

- speed tests are historical
- runtime buffering is closer to the real user problem

### 6.5 Fast Win E — Keep "Quality" UI Hidden For Progressive Sources

If the source has only one effective video stream, do not show quality or resolution selection.

Why this is a fast win:

- prevents misleading UX
- reduces support confusion
- keeps the product honest

---

## 7. UX Definition

### 7.1 Naming

For progressive sources, use:

- `Playback strategy`
- `Startup mode`
- `Prefer smoothness`
- `Fast start`

Do not use:

- `Auto quality`
- `Resolution`
- `720p / 1080p / 4K` unless multiple real choices exist

### 7.2 Visibility Rules

- `Playback strategy` is visible for progressive video sources.
- `Video quality` remains hidden unless multiple real video variants or tracks exist.
- Warnings should be lightweight and dismissible.

### 7.3 Behaviour Rules

- `Auto` chooses strategy from resource history + current file metadata + runtime events.
- `Fast start` prioritizes shorter startup delay.
- `Prefer smoothness` tolerates longer startup in exchange for fewer stalls.
- `Buffer more before play` is an explicit conservative mode for problematic resources.

---

## 8. Functional Model

### 8.1 Inputs

The future implementation may use these inputs:

- resource type: local / SMB / SFTP / FTP / cloud
- saved speed-test read speed
- saved recommended buffer size
- saved recommended threads
- currently selected video track bitrate if available
- currently selected video track width / height if available
- recent rebuffer count in current session
- startup delay / time to first frame

### 8.2 Outputs

The feature does not output a new resolution.

It outputs a playback policy such as:

- short startup buffer
- medium startup buffer
- aggressive startup with normal rebuffer target
- conservative startup with larger rebuffer target
- warning: file may stall on current resource
- suggested action: `Play now` or `Buffer first`

### 8.3 Decision Rule

Target formula:

- `resource speed history + protocol type + file heaviness + runtime buffering signals -> playback strategy`

Not:

- `resource speed -> new file resolution`

---

## 9. Phasing

### Phase 1 — Fast-Win MVP

Scope:

- define playback strategy modes
- map modes to buffer/startup behaviour
- use speed-test result as initial heuristic
- use runtime rebuffer signal as override
- hide any fake quality selector for progressive sources
- optionally show heavy-file warning on obvious mismatch cases

Expected value:

- better startup behaviour
- fewer stalls on weak resources
- clearer UX language

### Phase 2 — Better Session Intelligence

Scope:

- per-session adaptation based on repeated stall events
- smarter warnings and suggestions
- telemetry-driven threshold tuning

Expected value:

- less reliance on stale speed-test data
- more robust behaviour under changing Wi‑Fi / NAS / cloud conditions

### Phase 3 — Separate Future Feature: Real Video Quality

Scope:

- only if adaptive-stream sources or true multi-variant video sources are introduced

This phase is not part of the current spec.

---

## 10. Architecture Direction

Expected responsibility split:

### Better Controlled By FMS

- source-type-aware policy selection
- playback strategy mode persistence
- warning visibility
- whether quality UI should be shown at all
- session-level interpretation of rebuffer history

### Better Left To Media3 / ExoPlayer

- actual buffering mechanics
- actual track-selection enforcement once constraints are supplied
- decoder choice and fallback
- low-level adaptive behaviour for future HLS / DASH sources

---

## 11. Future Implementation Touchpoints

Likely implementation areas:

- player settings UI or control dialog where playback strategy is exposed
- `VideoPlayerManager` for strategy-to-buffer mapping
- `PlayerViewModel` for session signals and policy orchestration
- runtime playback listener path for collecting rebuffer events and startup timing
- existing connection / throttle infrastructure for resource heuristics

This section is intentionally high-level because this task is specification-only.

---

## 12. ADRs

### ADR-1: Drop "Adaptive Resolution" As Main Scope

Reason:

- for progressive sources it promises a capability the product does not actually have

### ADR-2: Use Strategy Language Instead Of Quality Language

Reason:

- strategy is honest and actionable with current architecture
- quality switching is not

### ADR-3: Treat Speed Test As Heuristic, Not Truth

Reason:

- saved bandwidth measurements become stale
- runtime buffering is a more trustworthy signal for current-session conditions

### ADR-4: Hide Quality Controls When No Real Choice Exists

Reason:

- cleaner UX
- fewer false expectations

---

## 13. Acceptance Criteria

- The specification defines the feature as `Adaptive Playback Strategy`.
- The specification explicitly excludes changing incoming file resolution for progressive sources.
- The specification defines a fast-win MVP centered on playback strategy modes.
- The specification uses existing speed-test and runtime buffering signals as the main decision inputs.
- The specification states that quality UI must remain hidden when no real variants exist.
- The specification stays implementation-ready without any code change in this task.

---

## 14. Implementation Plan — Phase 1 Fast-Win MVP

> **Note on Section 5:** Section 5 (Non-Goals) contains no developer tasks — it is a scope fence only. Nothing to implement there.

Phase 1 is the only phase in active scope. All steps below are sequential; each depends on the previous.

---

### Step 1 — Define PlaybackStrategy Domain Model

**What:**

Create a sealed class or enum representing the four user-facing strategy modes.

```
PlaybackStrategy: AUTO | FAST_START | PREFER_SMOOTHNESS | BUFFER_MORE
```

**Files to create / modify:**

- New file: `domain/model/PlaybackStrategy.kt`

**Details:**

- `AUTO` — strategy is selected at runtime from speed-test and rebuffer signals
- `FAST_START` — minimise time-to-first-frame, accept higher rebuffer risk
- `PREFER_SMOOTHNESS` — tolerate longer startup, prioritise stall-free playback
- `BUFFER_MORE` — explicit conservative mode; user-selected or forced by heavy-file warning

**Done when:** model compiles; no other wiring yet.

---

### Step 2 — Define Buffer Policy Table

**What:**

Map each `PlaybackStrategy` to concrete `DefaultLoadControl` parameter sets.

**Files to create / modify:**

- New file: `ui/player/helpers/PlaybackStrategyPolicy.kt`

**Details:**

Create a data class:

```
data class BufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)
```

Create a lookup function:

```
fun resolveBufferPolicy(strategy: PlaybackStrategy, resourceType: ResourceType): BufferPolicy
```

Reference values from existing constants in `VideoPlayerManager` (local: 15/30/5/8 s; cloud: 20/45/8/12 s).

Example target mapping:

| Strategy | Local | Network (SMB/SFTP/FTP) | Cloud |
|---|---|---|---|
| FAST_START | 5/15/2/4 s | 10/20/3/6 s | 12/30/5/8 s |
| AUTO | (current defaults) | (current defaults) | (current defaults) |
| PREFER_SMOOTHNESS | 15/40/8/12 s | 20/50/10/15 s | 25/60/12/18 s |
| BUFFER_MORE | 20/60/12/20 s | 30/90/15/25 s | 35/120/20/30 s |

Exact numbers are tunable; table structure is the deliverable here.

**Done when:** `resolveBufferPolicy` returns a `BufferPolicy` for every strategy + resource-type combination.

---

### Step 3 — Persist Strategy Preference

**What:**

Save and restore the user's selected `PlaybackStrategy`.

**Files to modify:**

- `ui/player/helpers/PlayerSettingsManager.kt` — add `getPlaybackStrategy()` / `setPlaybackStrategy(strategy)` using SharedPreferences or DataStore (whichever is already in use)

**Details:**

- Default value: `AUTO`
- Key: `pref_playback_strategy`
- Serialize as string enum name

**Done when:** strategy survives app restart.

---

### Step 4 — Wire Strategy Into VideoPlayerManager

**What:**

Apply the resolved `BufferPolicy` when building each `ExoPlayer` instance.

**Files to modify:**

- `ui/player/VideoPlayerManager.kt`

**Details:**

- Inject or receive `PlayerSettingsManager` (already injected or add via constructor/Hilt)
- Before creating `DefaultLoadControl`, call `resolveBufferPolicy(strategy, resourceType)`
- Replace hardcoded buffer constants with values from returned `BufferPolicy`
- Do this for each of the four ExoPlayer builder paths (local, cloud, SMB/SFTP/FTP)

**Constraint:** File is >500 lines — back up to `temp/` before editing.

**Done when:** changing strategy in settings causes measurably different `DefaultLoadControl` configuration in the next player session.

---

### Step 5 — Implement AUTO Heuristic From Speed-Test Data

**What:**

When strategy is `AUTO`, derive the effective strategy from saved speed-test data rather than using fixed defaults.

**Files to modify:**

- `ui/player/PlayerViewModel.kt` — add function `resolveAutoStrategy(resourceKey): PlaybackStrategy`

**Details:**

Use `ConnectionThrottleManager.getLastSpeedMbps(resourceKey)`:

| Saved speed | AUTO selects |
|---|---|
| ≥ 100 Mbps (local / fast NAS) | FAST_START |
| 20–100 Mbps | AUTO defaults (current presets) |
| 5–20 Mbps | PREFER_SMOOTHNESS |
| < 5 Mbps | BUFFER_MORE |

If no speed data is saved, fall back to current hardcoded defaults (no regression).

Call `resolveAutoStrategy` at player launch, before passing strategy to `VideoPlayerManager`.

**Done when:** opening a file on a resource with a known slow speed-test result automatically selects BUFFER_MORE without user interaction.

---

### Step 6 — Add Runtime Stall Telemetry And Session Override

**What:**

Track rebuffer events during playback. If stalls exceed a threshold, escalate strategy within the current session.

**Files to modify:**

- `ui/player/VideoPlayerManager.kt` — attach `Player.Listener`, count transitions to `STATE_BUFFERING` after first play
- `ui/player/PlayerViewModel.kt` — expose `sessionStallCount: StateFlow<Int>`, implement escalation logic

**Details:**

- Count `STATE_BUFFERING` events that occur after `STATE_READY` was first reached (i.e., exclude initial startup)
- If stall count ≥ 2 in the current session → override effective strategy toward PREFER_SMOOTHNESS
- If stall count ≥ 4 → override toward BUFFER_MORE
- Override is session-only — not persisted, does not change user's saved preference
- Escalation triggers re-configuration of `DefaultLoadControl`; ExoPlayer must be rebuilt or parameters updated (check Media3 API for live update support vs. rebuild)

**Done when:** a video that stalls twice automatically shifts to a smoother-but-slower buffer policy for the rest of the session.

---

### Step 7 — Add Heavy-File Warning On Slow Resources

**What:**

Before autoplay starts, detect a mismatch between file heaviness and resource capability, and show a lightweight dismissible warning.

**Files to modify:**

- `ui/player/PlayerViewModel.kt` — add `checkHeavyFileRisk(fileMetadata, resourceKey): HeavyFileRisk?`
- Player UI (control dialog or player overlay) — add warning view / dialog

**Details:**

Risk detection logic (in ViewModel, not in UI):

```
if (savedSpeedMbps < SLOW_THRESHOLD_MBPS
    && estimatedBitrateKbps > HEAVY_THRESHOLD_KBPS) {
    return HeavyFileRisk(message, suggestBufferMode = true)
}
```

Suggested thresholds (tunable):

- `SLOW_THRESHOLD_MBPS = 10`
- `HEAVY_THRESHOLD_KBPS = 8000` (~ 8 Mbit/s video)

UX:

- small snackbar or a non-blocking overlay, not a blocking dialog
- two actions: `Play now` (ignores warning) and `Buffer first` (forces BUFFER_MORE for this session)

`estimatedBitrateKbps` — read from `onTracksChanged` / `Format.averageBitrate` (already available in `VideoPlayerManager`).

**Done when:** opening a heavy MKV on a resource with < 10 Mbps saved speed shows the warning before playback starts.

---

### Step 8 — Hide Quality UI For Progressive Sources

**What:**

Ensure no "quality" or "resolution" selector appears in the player UI when the source has only one video rendition.

**Files to modify:**

- `ui/player/VideoTrackSelectionManager.kt` — add `hasMultipleVideoRenditions(): Boolean`
- Player control dialog — conditionally hide quality selector based on this flag

**Details:**

- If `tracks.groups` contains only one enabled video group with one selectable format → hide quality UI entirely
- If `tracks` has ≥ 2 selectable video formats → show quality selector (future HLS/DASH path)
- Default: hidden (safe side)

**Done when:** no quality/resolution UI is shown for any current progressive source in the app.

---

### Step 9 — Expose Playback Strategy In Player UI

**What:**

Add a `Playback strategy` option to the player settings or control dialog.

**Files to modify:**

- Player control dialog or settings screen (wherever audio/subtitle track selection is currently shown)

**Details:**

- Show four options: `Auto`, `Fast start`, `Prefer smoothness`, `Buffer more`
- Selecting a mode calls `PlayerSettingsManager.setPlaybackStrategy(strategy)`
- Current effective mode is highlighted (including AUTO-derived mode, not just saved preference)
- This is visible for all progressive video sources
- No quality/resolution naming anywhere in this UI

**Done when:** user can open the control dialog, select `Buffer more`, and the change persists to next launch.

---

## 15. Phase 2 Scope (Deferred — Do Not Start Before Phase 1 Is Complete)

Phase 2 adds session intelligence beyond manual strategy modes.

Candidate items (not ready for implementation planning):

- per-resource stall history stored in DB, not just in-session
- smarter AUTO threshold tuning from accumulated playback sessions
- proactive suggestion: "This resource is often slow — would you like to set Buffer more as default?"
- startup timing telemetry (time-to-first-frame) used as secondary signal alongside rebuffer count

Phase 2 planning starts only after Phase 1 is shipped and validated.

---

## 16. Phase 3 — Real Video Quality (Separate Future Feature)

Out of current spec scope. Requires:

- HLS / DASH sources
- real multi-variant manifests
- separate specification

No planning or implementation until Phase 1 + Phase 2 are stable.
