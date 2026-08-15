# Phase 04 - Tab visibility logic and speed-preset wiring

**Strategic spec:** [`../S0670_compact-playback-control-dialog.md`](../S0670_compact-playback-control-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Make the active tab set context-aware (3D only on VR builds, Audio only with >1 track, Subs only when present) and wire the new speed-preset buttons - the behavioural core of S0670.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`MediaCapabilities.supportsVrMediaControls`).
- [ ] Phase 03 ✅ Done (`binding.btnSpeed05/btnSpeed15/btnSpeed20` exist).
- [ ] Research [`research/02__3d-tab-flavor-gate.md`](research/02__3d-tab-flavor-gate.md) read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | ≤ 720 |

---

## Steps

### Step 04.1 - Context-aware active tab set

**Files:** `PlaybackControlDialogFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Rework the `activeSections` computation for the video branch:
> - Compute three inputs once when the dialog opens (cache in `private var` fields set at the top of `onViewCreated`, before `setupSectionNavigation`):
>   - `supportsVrMediaControls` from the existing `MediaCapabilitiesEntryPoint` (`mediaCapabilities().supportsVrMediaControls`).
>   - `hasMultipleAudioTracks` = `host().videoPlayerHandle?.let { it.getAvailableAudioTracks().size > 1 } ?: true` (null handle -> keep tab, no false-hide; research §6.4).
>   - `hasSubtitles` = `host().videoPlayerHandle?.let { it.getAvailableSubtitleTracks().isNotEmpty() } ?: true`.
> - In the video branch of `activeSections`: always add `VOLUME`; add `AUDIO` only if `hasMultipleAudioTracks`; add `SUBTITLES` only if `hasSubtitles`; add `STEREO` only if `supportsVrMediaControls` (remove the old `supportsVrPlayer || isStereoContent` gate and the `EntryPointAccessors`/`isStereoContent` block); always add `HUE`, `BRIGHTNESS`, `SPEED`.
> - Keep selection robust: `resolveInitialSection` / `selectedSectionName` already fall back to the first active section, so a hidden saved section degrades gracefully - verify no path checks a now-removed section.

**Verification:**

- `Grep` - `supportsVrPlayer` returns zero hits in `PlaybackControlDialogFragment.kt`.
- `Grep` - `supportsVrMediaControls` present; `hasMultipleAudioTracks` and `hasSubtitles` present.
- `Grep` - `isStereoContent` returns zero hits in this file.
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. activeSections gates AUDIO on >1 track, SUBTITLES on present, STEREO on supportsVrMediaControls; supportsVrPlayer/isStereoContent removed; snapshot in onViewCreated. `.\a.ps1 fk` OK.

---

### Step 04.2 - Wire speed-preset buttons

**Files:** `PlaybackControlDialogFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `setupSpeedTab`, wire the three new buttons to a private `applySpeedPreset(speed: Float)` helper that snaps `seekSpeed.progress` to the matching `speedSteps` index, calls `host().videoPlayerHandle?.setPlaybackSpeed(speed)`, and `updateSpeedLabel(speed)`: `btnSpeed05 -> 0.5f`, `btnSpeed15 -> 1.5f`, `btnSpeed20 -> 2.0f`. Reuse the existing `speedSteps` list and `abs`/index lookup pattern already in `setupSpeedTab`. Apply immediately, consistent with the volume presets - no confirm.

**Verification:**

- `Grep` - `btnSpeed05`, `btnSpeed15`, `btnSpeed20` each referenced once in `PlaybackControlDialogFragment.kt`.
- `Grep` - `applySpeedPreset` declared once.
- `.\a.ps1 fk` compiles; `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. applySpeedPreset snaps seekSpeed + applies speed; btnSpeed05/15/20 wired. Standard fk + noLegal nd BUILD SUCCESSFUL (validates code + S0670 debug tag).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard) and a VR build (`.\a.ps1 nd` noLegal) to prove the 3D gate compiles on both sides.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Debug tag `Timber.d("S0670: ..")` present at the dialog flow entry (ticket enters `BlockNeedUserTest`).
- [ ] Dev log entries batched in Phase 05.

---

## Handoff Notes to Next Phase

Behaviour complete. Phase 05 runs catalog sync, strings audit, dev log, and the device-test handoff. The `S0670:` debug tag stays until `/spec-check` flips the ticket to `Verified`.

---

## Rollback Plan

Revert phase commit(s) - single-file logic change; `activeSections` returns to the prior gate. No data migration.
