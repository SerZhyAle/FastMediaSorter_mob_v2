# VR Video Features — Quick Navigation Index

**Created**: 2026-04-17  
**Status**: All three specs ready  

---

## 📋 Specification Files

### 1. **Unified Playback Settings Dialog** (DEVELOPMENT)

- **File**: [`spec_vr-video-controls.md`](spec_vr-video-controls.md)
- **What**: New unified dialog (6 tabs) for playback control in VR mode
- **Status**: ✅ Ready for development
- **v2.62**: 🚢 SHIP
- **Why**: Fixes scattered controls + broken audio/subtitle selection
- **LOE**: 3–4 days dev + 2 days QA

---

### 2. **3D Video Support (SBS) — IMPLEMENTATION TASK** ⭐ **NEW**

- **File**: [`task_3d-sbs-support-implementation.md`](task_3d-sbs-support-implementation.md)
- **What**: Complete implementation spec with auto-detection + FULL documentation updates (EN/RU/UK)
- **Status**: ✅ **READY TO IMPLEMENT** (based on research findings)
- **v2.63**: 🚀 **NEXT SPRINT**
- **Why**: Enables viewing 60–70% of real-world 3D videos in proper stereo
- **LOE**: 5–7 days (1 engineer + QA)
- **Coverage**: SBS horizontal stereo (auto-detection + manual override)
- **Key Documentation Sections**:
  - ✅ `docs/FEATURES.md` (EN/RU/UK) — feature inventory updates
  - ✅ `docs/HOW_TO.md` (EN/RU/UK) — new "Watching 3D Videos" guides
  - ✅ String resources (EN/RU/UK) — UI labels
  - ✅ CHANGELOG — 3 entries via `add_to_dev_log.ps1`
  - ✅ Release notes (EN/RU/UK) — v2.63 announcements
- **Acceptance Criteria**: 100+ items (Section 11 of task file)
- **Risk**: 🟢 LOW (well-researched, proven technology)

---

### 3. **3D Video Support Research** (RESEARCH SPIKE — FINDINGS)

- **File**: [`spec_vr-video-3d-research.md`](spec_vr-video-3d-research.md) — Section 2.1
- **What**: Feasibility study for stereoscopic video (SBS, OU formats)
- **Status**: 🔬 Research findings → integrated into implementation task (above)
- **Decision**: ✅ **CONDITIONAL GO** for SBS in v2.63
- **Key Finding**: HIGH feasibility (Media3 VideoProcessor), <5% CPU impact, 94% detection accuracy
- **Deferred**: OU (Over-Under), frame-sequential, anaglyph to v2.64+

---

### 4. **VR Pointer Overlay Research** (RESEARCH SPIKE)

- **File**: [`spec_vr-video-3d-research.md`](spec_vr-video-3d-research.md) — Section 2.2
- **What**: Joystick input detection + pointer overlay in fullscreen VR
- **Status**: 🔬 Pending research (not yet started)
- **v2.63**: Research in progress
- **v2.64**: Decision-based (Go/No-Go/Defer)
- **Key Questions**: Latency acceptable? Overlay doesn't stutter? Works with multiple controllers?
- **Go Criteria**: <150ms latency, ≥55 FPS overlay, 3+ device types

---

### 5. **Strategy & Roadmap** (META)

- **File**: [`spec_vr-video-strategy.md`](spec_vr-video-strategy.md)
- **What**: Overall VR enhancement roadmap (v2.62–v2.64+)
- **Status**: ✅ Complete
- **Purpose**: Decision matrix + timeline + resource allocation
- **Next Steps**: Approve implementation task → launch v2.63 sprint

---

## 🎯 Release Timeline

```
v2.62 (Current release)
├─ Unified Dialog ......................... SHIP ✅
├─ 3D Video Research ..................... COMPLETE ✅
└─ Pointer Overlay Research .............. IN PROGRESS 🔬
   └─ Decision: 3D Support → GO for v2.63 ✅

v2.63 (Next release, ~4 weeks after v2.62)
├─ SBS 3D Support ........................ IMPLEMENT 🚀
│  ├─ Auto-detection (aspect ratio + metadata)
│  ├─ VideoProcessor (stereo crop rendering)
│  ├─ "3D" tab in PlaybackSettingsDialog
│  └─ Full documentation updates (EN/RU/UK)
├─ If Pointer research = "Go":
│  └─ Pointer Overlay .................... IMPLEMENT
└─ If either = "Defer":
   └─ Continue Phase 2 priorities

v2.64+ (Future releases)
└─ OU 3D format, Eye strain features, Performance optimization
```

---

## 📊 Quick Reference

| Feature | Spec | Type | v2.62 | v2.63 | Effort | Risk |
|---------|------|------|-------|-------|--------|------|
| **Unified Dialog** | [Controls](spec_vr-video-controls.md) | Dev | 🚢 Ship | — | 3–4d | 🟢 Low |
| **3D Video (SBS)** | [Task](task_3d-sbs-support-implementation.md) | Dev | Research | 🚀 Implement | 5–7d | 🟢 Low |
| **Pointer Overlay** | [Research](spec_vr-video-3d-research.md#22-vr-pointerjoystick-research) | Research | Research | TBD | 4d+3d | 🟡 Medium |

---

## 🗺️ Related Documentation

**Architecture & Design**:

- [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) — Player architecture overview
- [`docs/DEV_OPS.md`](../docs/DEV_OPS.md) — Build commands
- [`docs/TECH_STACK.md`](../docs/TECH_STACK.md) — Dependencies (ExoPlayer, Media3, etc.)

**Project Navigation**:

- [`dev/PROJECT_OPERATIONS_INDEX.md`](../dev/PROJECT_OPERATIONS_INDEX.md) — Workspace routing
- [`IMPROVEMENT_ROADMAP.md`](IMPROVEMENT_ROADMAP.md) — Overall v2 roadmap

**Flavor & Build Info**:

- `app_v2/build.gradle.kts` — SDK 35, Kotlin 1.9+, flavors (standard/lite/photos/legacy)
- `dev/TECH_REQUIREMENTS.md` — Min/recommended device specs

---

## ✅ Acceptance Gates

### Before Unified Dialog Implements (v2.62)

- [ ] Spec reviewed by Engineering Lead + Product Owner
- [ ] Resource allocated (Engineer A, ~3–4 days)
- [ ] Test scenarios prepared (Maestro)
- [ ] Research spikes launched in parallel

### After 3D Research Completes

- [ ] Research doc finalized (`dev/research_3d_video_support.md`)
- [ ] Go/No-Go decision made by Product Owner
- [ ] If "Go": implementation spec created (`spec_vr-video-3d.md`)
- [ ] If "No-Go": archived with rationale

### After Pointer Research Completes

- [ ] Research doc finalized (`dev/research_vr_pointer_interaction.md`)
- [ ] Go/No-Go decision made by Product Owner
- [ ] If "Go": implementation spec created (`spec_vr-pointer-overlay.md`)
- [ ] If "No-Go": archived with rationale

---

## 🚀 How to Use This Index

1. **Planning Phase**: Read [`spec_vr-video-strategy.md`](spec_vr-video-strategy.md) for overall vision
2. **Development Phase**: Start with [`spec_vr-video-controls.md`](spec_vr-video-controls.md)
3. **Research Phase**: Reference [`spec_vr-video-3d-research.md`](spec_vr-video-3d-research.md) for spike execution
4. **Architecture Questions**: See [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
5. **Build Commands**: See [`docs/DEV_OPS.md`](../docs/DEV_OPS.md)

---

## 📞 Contact & Escalation

| Role | Responsibility |
|------|-----------------|
| **Product Owner** | Approve specs, make Go/No-Go decisions |
| **Engineering Lead** | Resource allocation, architecture review |
| **Developer (Dialog)** | Implement unified playback settings |
| **Developer (Research A)** | 3D video feasibility study |
| **Developer (Research B)** | VR pointer interaction study |
| **QA Lead** | Test scenarios, Maestro automation |

---

**Document Owner**: [TBD]  
**Last Updated**: 2026-04-17  
**Next Review**: 2026-04-24 (after Week 1 completion)
