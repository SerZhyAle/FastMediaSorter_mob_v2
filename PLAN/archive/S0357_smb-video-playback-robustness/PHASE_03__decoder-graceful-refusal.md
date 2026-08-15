# Phase 03 - Decoder Graceful Refusal

**Strategic spec:** [`../S0357_smb-video-playback-robustness.md`](../S0357_smb-video-playback-robustness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - independent of Phase 01/02 (different error class)
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Recognise a hardware decoder-initialisation failure (errorCode 4001, `MediaCodecRenderer.DecoderInitializationException` - e.g. 8K AVC on a device without hardware support) in the shared player error ladder and show a clear, named user message instead of the generic engine error.

---

## Prerequisites

- [ ] Pre-Implementation Blocker R3 (strategic §6.3) is Resolved - the decoder support boundary on Quest is known well enough to classify the failure from the exception (codec/mime/resolution), not from a resolution heuristic (strategic risk: false positives).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt` | Modified | ≤ 340 |

> The user-visible refusal string is added in Phase 04 (trilingual, single lockstep step). This phase wires the classification and references the string key by name; the key is created in Phase 04 before this branch is exercised at runtime.
>
> **Flavor placement.** errorCode 4001 reaches every video flavor (the failing decoder init is device-driven, not flavor-driven), so the classification and the user message live in `src/main` - no flavor `BuildConfig` guard (CLAUDE.md Rule 15). The existing VR-only 8K diagnostic in `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` already inspects `DecoderInitializationException` and remains the VR-specific surface; this phase does not move or duplicate it. No edit to `src/vr` is required for the base refusal message.

---

## Steps

### Step 03.1 - Classify the decoder-initialisation failure in the error ladder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `handlePlayerError`, the MediaCodec branch (`error.errorCode in 4000..4999`) currently only marks the source via `decoderFailureTracker.markFailed` and logs a warning, then falls through to the generic `onPlaybackError`. Add a narrower classification: when the error cause chain contains `androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException` (the errorCode 4001 case from the 2026-06-04 log: `c2.qti.avc.decoder`, 8K AVC), treat it as an unsupported-decoder refusal. Use the R3-resolved boundary to confirm the case rather than guessing by resolution. Do not change the existing audio-renderer Variant B path (that handles `TRACK_TYPE_AUDIO` and must stay).

**Verification:**

- `Grep` - `DecoderInitializationException` referenced in `VideoPlayerErrorHandler.kt`.
- `Grep` - `decoderFailureTracker` and the existing audio-renderer Variant B (`setTrackTypeDisabled`) both still present (no regression).
- `Grep -n "Log\.d\("` on `VideoPlayerErrorHandler.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 03.2 - Route the refusal to a named user message via the callback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> For the unsupported-decoder refusal classified in Step 03.1, surface a clear named message to the user via the existing `manager.playerCallback` (use `onPlaybackError(error, userMessage)` with a userMessage built from `R.string.video_decoder_unsupported_hardware` - the key created in Phase 04 - including the file name like the existing `video_playback_failed_with_name` pattern), call `onBuffering(false)`, and return `true` so it does not fall through to the generic engine error. Do not show a raw codec name to the user (codec details stay in the log only). Reference `docs/COMMUNICATION_POLICY.md` §2 (error message formula) when phrasing the message; the actual string text and its §6 tone-checklist pass are owned by Phase 04.

**Verification:**

- `Grep` - `R.string.video_decoder_unsupported_hardware` referenced in `VideoPlayerErrorHandler.kt`.
- `Grep` - `onPlaybackError(error, userMessage)` (or an equivalent named-message call) present in the decoder-refusal branch.
- `Grep` - the decoder-refusal branch ends with `return true` (within `-C 6` context of `video_decoder_unsupported_hardware`).

**Status:** `[ ]` not done

---

### Step 03.3 - Keep decoder codec details in the log at honest severity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Log the decoder refusal at `Timber.i` (recoverable, user-informed fallback - matches the project rule that expected device-capability fallbacks log at INFO, not ERROR), including codec name, mime, and frame size from the `DecoderInitializationException` for diagnostics. The message must describe the subject in plain English and must NOT embed `S0357` (persistent logs never carry a ticket id). The BlockNeedUserTest probe tag is added separately in Step 03.4.

**Verification:**

- `Grep` - a `Timber.i(` call referencing the decoder/codec is present in the refusal branch.
- `Grep` - the persistent log line in this branch does not contain `S0357` (only the Step 03.4 `Timber.d` probe may).
- `Grep -n "Log\.d\("` on `VideoPlayerErrorHandler.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 03.4 - Insert BlockNeedUserTest verification tag at the decoder-refusal entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> At the entry of the unsupported-decoder refusal branch (the changed flow entry for this phase), insert exactly one `Timber.d("S0357: unsupported decoder refusal shown")`. One tag per changed flow entry. Do not add `S0357:` to any `Timber.i/w/e` line.

**Verification:**

- `Grep` - `Timber.d("S0357: unsupported decoder refusal shown")` matches exactly once in `VideoPlayerErrorHandler.kt`.
- `Grep` - the tag sits inside the decoder-refusal branch (within `-C 8` context of `DecoderInitializationException`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly). Compilation requires the Phase 04 string key to exist; if running Phase 03 before Phase 04, add the EN key first or run the two phases together.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `VideoPlayerErrorHandler.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] No public API change - catalog regen not required for this phase alone (the cleanup phase regenerates regardless).

---

## Handoff Notes to Next Phase

- The error ladder now classifies an unsupported-decoder failure and calls a named user message keyed `R.string.video_decoder_unsupported_hardware`.
- Phase 04 must create that key in EN/RU/UK before this branch is exercised on device.

---

## Rollback Plan

Revert the phase commit(s). The change is a single added branch in `VideoPlayerErrorHandler`; reverting restores the prior fall-through to the generic engine error with no data migration or schema change.
