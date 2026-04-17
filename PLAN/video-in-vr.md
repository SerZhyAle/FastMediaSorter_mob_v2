# VR Video Features — Research & Development Specifications

## Raw Notes (Original)

need to have the new dialog button in video controls (before PiP):
inside of this dialog in deifernt tabs:

- add adjustment volume
- move here choice of audio
- move here choice of subs
- add adjustment HUE
- add adjustment brightness
- move here choice speed

choice of audio in exist edit dialog - working, but cannot see what is hoisen
choice of subs in exist edit dialog - not working at all

3d content possible to show video as 3D ? I know there are few different standarts - i we can show - we can add into dialog (first note) additional tab 3D and give user to select the option how to view (vertical/horizontal), actually we can try to calculate it by movie sizes if user opted in 3D - NEED the research

in vr helmet - we have like mouse - the pointer from joystick - can show the touch-sensetive map when the mouse or joystic is over the videocontent in fullscreen mode

---

## Specifications Created (2026-04-17)

### 1. Development Specification

**File**: [`spec_vr-video-controls.md`](spec_vr-video-controls.md)

**Scope**: Unified Playback Settings Dialog

**Features**:

- New button in player control bar (before PiP) → opens settings dialog
- 6 tabs: Audio, Subtitles, Speed, Volume, Brightness, HUE
- Fixes current bugs: audio track selection doesn't show current choice; subtitle selection broken
- Immediate application of changes (no OK button)
- VR-friendly: large touch targets (≥48dp), single-tap access

**Status**: Ready for development (v2.62)

**Timeline**: 3–4 days dev + 2 days QA/testing

**Related**: Player architecture in [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)

---

### 2. Research Specification — 3D Video Support

**File**: [`spec_vr-video-3d-research.md`](spec_vr-video-3d-research.md) (Section 2.1)

**Scope**: Feasibility study for stereoscopic video (SBS, OU formats)

**Key Questions**:

- What 3D formats are in real-world use?
- Can ExoPlayer/Media3 render 3D natively?
- How do we detect SBS vs. OU automatically?
- Performance impact on low-end devices (Snapdragon 400–600)?

**Deliverable**: `dev/research_3d_video_support.md`

**Timeline**: ~5 days research spike

**Go/No-Go Criteria**:

- ✅ Go: ExoPlayer can render stereo, <10% frame-rate impact, reliable detection
- ❌ No-Go: Cannot render stereo, >15% impact, unreliable detection

**Decision Point**: End of v2.62 week 2; if "Go", implement in v2.63

---

### 3. Research Specification — VR Pointer Interaction

**File**: [`spec_vr-video-3d-research.md`](spec_vr-video-3d-research.md) (Section 2.2)

**Scope**: Joystick pointer input + on-screen overlay in fullscreen VR mode

**Key Questions**:

- Can we detect joystick/pointer input reliably?
- What's the pointer latency (input → display)?
- How should overlay behave (auto-hide, always visible, etc.)?
- Performance impact on low-end devices?

**Deliverable**: `dev/research_vr_pointer_interaction.md`

**Timeline**: ~4 days research spike

**Go/No-Go Criteria**:

- ✅ Go: Latency <150ms, works on 3+ device types, ≥55 FPS overlay
- ❌ No-Go: Latency >250ms, works on <2 types, FPS drops below 45

**Decision Point**: End of v2.62 week 2; if "Go", implement in v2.63

---

### 4. Strategy & Roadmap

**File**: [`spec_vr-video-strategy.md`](spec_vr-video-strategy.md)

**Scope**: Overall VR video enhancement roadmap

**Contents**:

- v2.62 priorities (Unified Dialog → ship; Research in parallel)
- v2.63 decision matrix (based on research outcomes)
- Resource allocation and timeline
- Risk assessment and mitigation

**Next Steps**:

1. Approve Unified Dialog spec → start dev
2. Launch 3D & Pointer research spikes (parallel)
3. End of week 2: research docs + go/no-go decisions
4. v2.63 scope finalized based on research

---

## Summary

| Feature | Type | Status | v2.62 | v2.63 |
|---------|------|--------|-------|-------|
| Unified Dialog | Dev | Ready | ✅ Ship | — |
| 3D Video (SBS) | Dev | ✅ **Ready to Implement** | Research | **🚀 NEXT** |
| Pointer Overlay | Research | Pending | Research | TBD |

---

## 5. Implementation Task (Ready to Start) — NEW ✨

**File**: [`task_3d-sbs-support-implementation.md`](task_3d-sbs-support-implementation.md)

**Status**: ✅ **COMPLETE & READY FOR SPRINT**

**What Gets Built**:

- ✅ **StereoDetector** class: auto-detect SBS format via aspect ratio + MKV metadata
- ✅ **StereoVideoProcessor** class: crop-based stereo rendering (left 50% + right 50%)
- ✅ **"3D" Tab** in PlaybackSettingsDialog: Auto/SBS/Mono options
- ✅ **Integration** with PlayerViewModel for persistent user preferences
- ✅ **Full documentation** updates (EN/RU/UK)

**Execution Phases**:

1. **Phase 1** (3 days): Core implementation (StereoDetector, VideoProcessor)
2. **Phase 2** (2 days): Integration + testing (Maestro E2E, device testing)
3. **Phase 3** (1 day): Documentation updates (**MANDATORY**)

**Documentation Requirements** (Section 3 of task):

- ✅ `docs/FEATURES.md` (EN) + `docs/FEATURES_RU.md` (RU) + `docs/FEATURES_UK.md` (UK)
- ✅ `docs/HOW_TO.md` (EN) + `docs/HOW_TO_RU.md` (RU) + `docs/HOW_TO_UK.md` (UK)
- ✅ String resources: `strings.xml` (EN), `strings-ru.xml` (RU), `strings-uk.xml` (UK)
- ✅ CHANGELOG logged via `add_to_dev_log.ps1` (3 entries)
- ✅ Release notes (EN/RU/UK)

**Effort**: 5–7 days (1 engineer + QA)  
**Performance**: <5% CPU overhead (budget devices)  
**Coverage**: 60–70% of real-world 3D videos (SBS format)  
**Risk**: 🟢 LOW (well-researched, proven technology)

**Acceptance Criteria**: 100+ items (see Section 11 of task)

---

**See also**:

- Player architecture: [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- Build commands: [`docs/DEV_OPS.md`](../docs/DEV_OPS.md)
- Research findings: Refer to [`spec_vr-video-3d-research.md`](spec_vr-video-3d-research.md)
