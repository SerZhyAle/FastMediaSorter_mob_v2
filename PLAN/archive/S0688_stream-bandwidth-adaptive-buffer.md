# Strategic spec: S0688 - Bandwidth-adaptive runtime buffer for streams

**Ticket:** S0688
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Parked from S0685 (§13.3)

> **Scope:** Tier-3 ad-hoc. Tactical breakdown inline in §13 (no separate directory).

---

## 0. Captured material (inbox)

**Captured:** 2026-06-25 (parked while implementing S0685)

S0685 delivered the user-facing wait-phase messaging plus a safe static buffer retune (min 20s / post-rebuffer 8s), but deferred a true bandwidth-adaptive runtime buffer. Media3 1.2.1 has no built-in mechanism that grows/shrinks the stream buffer in response to a live `BandwidthMeter` estimate - it needs a custom `LoadControl` that widens the cushion when the measured throughput is low/unstable and tightens it when the link is healthy, all within bounds that never pin the playhead next to the expiring live segment (must not regress the S0634 live-edge tracking).

Key reason this is a separate ticket: its benefit is only observable on a real weak/unstable signal. A stable emulator cannot exercise or validate adaptive buffering, so it cannot be verified in the S0685 emulator sweep.

**Open angles (for later research):**

- Custom `LoadControl` wrapping/replacing `DefaultLoadControl`, reading the shared `BandwidthMeter`.
- Bound the adaptive max against the live window so live-edge tracking holds (S0634 baseline is immutable).
- On-device weak-signal test methodology (network throttling profiles, real cellular).

**Attachments: none.**

---

## 1. Problem

The S0685 stream buffer is static: it fills to a fixed 30s cushion for every stream regardless of link quality. On a weak/unstable link that cushion is too shallow - the playhead drains it during a throughput dip and stalls into the loading animation. A deeper static cushion is not the answer either, because for live HLS/DASH an oversized backlog pins the playhead next to the expiring segment and revives `BehindLiveWindow` drops (the exact failure S0634/S0685 guard against). The buffer depth needs to respond to the measured throughput at runtime - deeper when the link is weak, modest when it is healthy - while staying live-safe.

Area: stream playback (Streams), the load-control layer of the isolated stream player.

---

## 2. Goal

Make the stream player's steady-state buffer depth adapt at runtime to the measured bandwidth: widen the cushion on a weak/unstable link to ride out gaps, tighten it on a healthy link, and never deepen a live stream past its proven live-safe depth.

**Non-goals:**

- Adapting the start / post-rebuffer thresholds (kept at proven S0685 values - adapting them risks delaying resume indefinitely on a perpetually weak link).
- Per-content-type load-control split (radio vs live) - parked as S0689.
- Any ABR / track-selection change (default adaptive selection already active, confirmed in S0685).
- Flavors without stream support (lite, photos).

---

## 3. Constraints

- **Flavor:** only `SUPPORT_STREAMS` variants (standard, legacy, noLegal, vr). Code lives in `src/main` and is gated at the call site, so no flavor source-set split is needed.
- **API level:** Media3 1.2.1 mechanisms, available from minSdk 23 (legacy).
- **Live-edge immutability (S0634):** the recovery machine and live `LoadControl` depth are not to be regressed. The adaptive layer must be a no-op for live content.
- **Performance:** reading the estimate runs on the playback thread per `shouldContinueLoading` call - it must be cheap (a single volatile read), no allocation, no main-thread work.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0685 (parent - static buffer retune this builds on), S0634 (archived live-edge baseline that must not regress), S0689 (sibling - radio vs live load-control split).
- **Scope confirmation:** non-live deepening only; live depth immutable at the S0685 live-safe 30s.

---

## 4. Current architecture context

`StreamPlaybackHelper.playStreamVideo` builds an isolated stream `ExoPlayer`. S0685 attached a static `DefaultLoadControl` (min 20s / max 30s / start 2.5s / post-rebuffer 8s) and a `LiveConfiguration` for live-edge tracking. The project already has the `LoadControl`-by-delegate pattern in `PauseAwareLoadControl` (wraps `DefaultLoadControl`, overrides one decision method, and explicitly re-dispatches the cross-recursive Java default overloads to avoid `StackOverflowError`).

Media3 1.2.1 `DefaultLoadControl` reads its buffer durations once at construction - there is no runtime hook to change them. The only per-call decision surface is `shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)`.

---

## 5. Approach

A `BandwidthAdaptiveLoadControl` decorator over `DefaultLoadControl` (same delegation pattern as `PauseAwareLoadControl`):

- The delegate's continue-loading band spans the full `[floor, VOD ceiling]` range so the delegate permits loading up to the deepest target.
- `shouldContinueLoading` caps loading at a *dynamic* target derived from the live `BandwidthMeter.getBitrateEstimate()`: deep ceiling at/below a weak-bitrate threshold, modest floor at/above a healthy threshold, linear in between.
- A shared `DefaultBandwidthMeter` is set on the `ExoPlayer.Builder` (so the http data source actually measures throughput) and handed to the load control.

### 5.1 Live-edge guard

Liveness is read from `targetLiveOffsetUs` in `shouldStartPlayback` (`!= C.TIME_UNSET` -> live), defaulting to live (conservative) until first known. For live content the ceiling is clamped to the floor, so the dynamic target is constant at the proven live-safe 30s - the adaptive growth is a pure no-op for live and cannot regress S0634. Only non-live streams (VOD, progressive audio/video, RTSP) get the deepening.

### 5.2 Depth band

- Floor (healthy signal, and the constant for live) = 30s = S0685 live-safe max -> no change on a healthy link or for any live stream.
- VOD/progressive ceiling (weak signal) = 60s -> the new deepening.
- Bitrate band: weak <= 1.5 Mbps (deepest), healthy >= 5 Mbps (floor), linear between. An absent/zero estimate is treated as weak (deepest cushion).

---

## 9. ADR

None - established project patterns (isolated stream player, `LoadControl`-by-delegate, source-gated stream capability).

---

## 10. Related specs

- **S0685** (adaptive-stream-buffering) - parent; delivered messaging + static buffer retune, deferred this.
- **S0634** (stream-live-hls-robustness, archived) - live-edge recovery baseline that must not regress.
- **S0689** (stream-radio-vs-live-loadcontrol-split) - sibling parked from S0685; separate radio vs live load-control profiles.

---

## 11. Done criteria

1. Stream buffer depth tracks the measured bandwidth at runtime: deeper on a weak link, modest on a healthy one.
2. Live streams never deepen past the S0685 live-safe depth - no new `BehindLiveWindow` drops.
3. No regression to stream playback on a healthy link.

---

## 13. Iteration scope and tactics (resolved 2026-06-25)

### 13.1 Delivered

1. `BandwidthAdaptiveLoadControl` (`ui/player/helpers/`) - decorator over `DefaultLoadControl`; `shouldContinueLoading` caps the cushion at a bandwidth-derived dynamic target; live-clamped to the floor; cross-recursive default overloads re-dispatched to the delegate (mirrors `PauseAwareLoadControl`).
2. `playStreamVideo` wiring - a shared `DefaultBandwidthMeter` feeds both `ExoPlayer.Builder.setBandwidthMeter` and the load control; the static S0685 `DefaultLoadControl` and its four now-dead buffer constants removed.

### 13.2 Tactical phases (done)

1. New decorator class with depth band + live guard constants.
2. Wire shared bandwidth meter + adaptive control into `playStreamVideo`; drop superseded constants.
3. Compile-verify (`fk`), warning-clean.

### 13.3 Not emulator-verifiable (-> device test, BlockNeedUserTest)

The deeper-buffer benefit only manifests on a real weak/unstable signal; a stable emulator cannot exercise it. See the Status note for the device-test checklist.

---

## 14. Verification

- `.\a.ps1 fk` (compileStandardDebugKotlin): BUILD SUCCESSFUL, warning-clean (delegate deprecation suppressions in place).
- Live guard is correct by construction: for live content floor == ceiling == 30s, so the dynamic target is constant at the S0685 live-safe depth - the adaptive path is a no-op for live and cannot regress S0634.
- Weak-signal benefit and live-edge non-regression on a real link: deferred to on-device test (Status note).
