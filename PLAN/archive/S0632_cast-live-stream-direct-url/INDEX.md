# Tactical Plan: S0632 - Chrome Cast for live video stream (direct URL)

**Strategic spec:** `PLAN/S0632_cast-live-stream-direct-url.md`
**Status:** BlockNeedUserTest
**Priority:** 60

## Goal (from strategic §2)

Make the Cast button actually cast a live video stream: send the stream URL directly to the Cast
receiver as live, bypassing the temp-file download and local proxy. Reject RTSP (receiver does not
support it) with a clear message instead of a silent failure. Non-stream local/network/cloud files
keep their existing proxy path.

## Research inputs

- `research/01__cast-receiver-protocol-support.md` - Default Media Receiver accepts HLS, DASH,
  SmoothStreaming, progressive http; rejects RTSP/RTMP. Drives the 3-way cast decision.

## Pre-Implementation Blockers

- None. §6 protocol question resolved (see research input).

## Phases

| Phase | Title | Status |
| --- | --- | --- |
| 01 | Direct-URL cast branch for live streams | Done |
| 02 | Strings, unit test, capability record | Done |

## Phase Map to Goals

- Goal 1 (direct URL, no proxy) -> Phase 01.
- Goal 2 (live playback for HLS/DASH/http) -> Phase 01.
- Goal 3 (RTSP unsupported message) -> Phase 01 (logic) + Phase 02 (string).

## ADR application

- ADR-1 (direct URL vs proxy) -> Phase 01 `CastStreamResolver` + `loadStreamOnReceiver`.
- ADR-2 (Default Media Receiver, no custom receiver) -> Phase 01 RTSP rejection branch.
