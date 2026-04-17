# Specification: VR Video Strategy & Roadmap

**Status:** Draft  
**Date:** 2026-04-17  
**Scope:** Overall strategy for VR video enhancement; roadmap for v2.62–v2.64

---

## 1. Executive Summary

FastMediaSorter v2 has basic video playback, but lacks features needed for comfortable VR headset usage:

1. **Scattered controls** → unified dialog (Development spec)
2. **No 3D support** → research needed (Research spec)
3. **No VR pointer interaction** → research needed (Research spec)

This document outlines:

- **Immediate priority** (v2.62): Unified playback settings dialog
- **Research phase** (concurrent): 3D video + VR pointer interaction
- **Roadmap** (v2.63–v2.64): Based on research outcomes

---

## 2. Feature Breakdown

### 2.1 Unified Playback Settings Dialog (Development)

**Spec**: `PLAN/spec_vr-video-controls.md`

**What**: New dialog with 6 tabs (Audio, Subtitles, Speed, Volume, Brightness, HUE)

**Why**: VR users need quick, unified access to all playback controls without exiting fullscreen.

**Status**: Ready for development

**Timeline**: v2.62 (current release, 2–3 weeks)

**Owner**: Engineer TBD

---

### 2.2 3D Video Support (Research Phase)

**Spec**: `PLAN/spec_vr-video-3d-research.md`

**What**: Investigate support for stereoscopic video (SBS, OU formats)

**Why**: Users with 3D video files want to view them in VR mode with proper 3D rendering

**Status**: Research spike (pending decision)

**Timeline**: 1 week research → decision → v2.63 or defer

**Owner**: Engineer TBD

**Key Questions**:

- Can ExoPlayer/Media3 render stereo natively?
- What's the performance cost?
- How do we detect SBS vs. OU automatically?
- User UX: how do we expose 3D mode selection?

---

### 2.3 VR Pointer / Joystick Interaction (Research Phase)

**Spec**: `PLAN/spec_vr-video-3d-research.md` (Section 2.2)

**What**: Detect joystick input and show pointer overlay on video in fullscreen

**Why**: VR helmet users control video with a pointer/joystick; current UI is unresponsive to this.

**Status**: Research spike (pending decision)

**Timeline**: 1 week research → decision → v2.63 or defer

**Owner**: Engineer TBD

**Key Questions**:

- How do we detect joystick input reliably?
- What's the pointer latency (input → display)?
- How should the overlay behave (always visible, auto-hide, etc.)?
- Performance impact on low-end devices?

---

## 3. Release Timeline

```
v2.62 (Current, ~3 weeks)
├─ Unified Playback Settings Dialog (SHIP)
├─ 3D Video Research (in progress)
└─ VR Pointer Research (in progress)

v2.63 (~4 weeks after v2.62)
├─ If 3D research + decision = "ship":
│  └─ SBS 3D Support Implementation
├─ If Pointer research + decision = "ship":
│  └─ VR Pointer Overlay Implementation
└─ If either deferred: continue Phase 2 priorities

v2.64+ 
└─ Additional VR features, performance optimization
```

---

## 4. Prioritization Matrix

| Feature | Effort | Impact | User Feedback | Priority | v2.62 | v2.63 |
|---------|--------|--------|---------------|----------|-------|-------|
| Unified Dialog | Medium | High | "Controls scattered" | **P0** | ✅ Ship | — |
| 3D Video | Medium-High | Medium | "Have 3D videos, can't watch in VR" | **P1** | Research | Decide |
| Pointer Overlay | Low-Medium | High (UX) | "Can't control from VR headset easily" | **P1** | Research | Decide |

---

## 5. Research Outcomes & Decisions

### 5.1 After 3D Video Research Completes

**Scenario A: "Go" Decision**

- Commitment: Implement SBS (horizontal stereo) support in v2.63
- Implementation spec: `spec_vr-video-3d.md` (to be created)
- LOE: ~5–7 days
- Risk: Low (proven technology, feature not critical to core use case)

**Scenario B: "No-Go" Decision**

- Document: Why 3D support not feasible (e.g., ExoPlayer limitation, performance risk)
- Recommendation: Users can use external VR player for 3D content
- Defer to v2.64+ or archive

**Scenario C: "Defer" Decision**

- Document: 3D support is feasible but lower priority than other features
- Re-evaluate in v2.64 planning

### 5.2 After VR Pointer Research Completes

**Scenario A: "Go" Decision**

- Commitment: Implement pointer overlay + auto-hide in v2.63
- Implementation spec: `spec_vr-pointer-overlay.md` (to be created)
- LOE: ~3–5 days
- Risk: Medium (latency sensitivity, compatibility with various controllers)

**Scenario B: "No-Go" Decision**

- Document: Pointer input not reliably detectable or latency unacceptable
- Recommendation: Users use touchpad/external controller
- Defer indefinitely or re-evaluate with new VR SDK

**Scenario C: "Defer" Decision**

- Document: Feasible but lower priority
- Re-evaluate in v2.64 planning

---

## 6. Resource Allocation

### Current Sprint (v2.62 Development)

| Task | Engineer | Duration | Start | End |
|------|----------|----------|-------|-----|
| Unified Dialog Dev | A | 3–4 days | Week 1 | Week 1–2 |
| 3D Research | B | 5 days | Week 1 | Week 1–2 |
| Pointer Research | C | 4 days | Week 1 | Week 2 |
| Testing + Fixes | A | 2 days | Week 2 | Week 2 |
| Consolidation + Roadmap | A, B, C | 1 day | Week 2 | Week 2 |

**Total Effort**: ~2.5 engineers-weeks

---

## 7. Success Criteria for v2.62

### Development Features

- [x] Unified Playback Settings Dialog ships
- [x] All 6 tabs functional and tested
- [x] Audio/subtitle selection fixed (no crashes, shows current)
- [x] No regression in existing player features

### Research Phases

- [x] 3D video research document complete + recommendation
- [x] VR pointer research document complete + recommendation
- [x] Combined strategy document ready for decision meeting
- [x] Roadmap for v2.63 clear (which feature ships next?)

---

## 8. Go/No-Go Decision Criteria

### 3D Video Support

**"Go" if**:

- ExoPlayer can render stereo (either native or via VideoProcessor)
- Frame rate impact < 10% on budget devices (Snapdragon 600)
- Automatic detection heuristic reliable enough (95%+ accuracy on test set)
- LOE estimate ≤ 7 days

**"No-Go" if**:

- ExoPlayer cannot render stereo without major custom work
- Frame rate impact > 15% on budget devices
- False-positive detection rate > 5%
- LOE estimate > 10 days

### VR Pointer Interaction

**"Go" if**:

- Pointer latency < 150ms on budget device
- Input API works reliably on ≥3 common VR setups
- Overlay rendering doesn't cause jank (stays ≥55 FPS)
- LOE estimate ≤ 5 days

**"No-Go" if**:

- Pointer latency > 250ms (unacceptable for VR)
- Input API works on <2 device types
- Overlay rendering drops FPS below 45
- LOE estimate > 7 days

---

## 9. Communication Plan

### Stakeholders

- **Product Owner**: Decides go/no-go after research
- **Engineering Lead**: Allocates resources
- **QA**: Prepares test scenarios for Maestro
- **Users**: Feature announcements in release notes (EN/RU/UK)

### Milestones

- **End of Week 2**: Research docs + recommendation ready
- **End of Week 2 (Thursday)**: Decision meeting
- **End of Week 2 (Friday)**: Roadmap finalized + v2.62 release candidate
- **End of Week 3**: v2.62 released with Unified Dialog

---

## 10. Risk Management

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Research takes longer than 1 week | Medium | v2.62 release delayed | Timebox strictly; cut scope if needed (e.g., pointer only, defer 3D) |
| 3D video research reveals no viable path | Low | 3D support deferred indefinitely | Document findings; check back in 12 months with new Media3 versions |
| Pointer latency unacceptable | Medium | Feature cut | Defer to Phase 2; use alternative UX (e.g., button on screen) |
| Unified Dialog introduces regression | Low | Critical bug fix required | Extensive testing on multiple devices before v2.62 release |

---

## 11. Future Enhancements (v2.64+)

If both research features ship in v2.63, next priorities:

1. **VR Comfort Features**:
   - Eye strain reduction (blue light filter, brightness limiter, break reminders)
   - Head movement tracking (if device has sensors)
   - Field-of-view options (zoom, pan)

2. **Advanced Video Features**:
   - Over-Under (OU) 3D format support (if SBS ships in v2.63)
   - Frame-sequential stereo (advanced users)
   - Video aspect ratio adjustment (crop, zoom, fit)

3. **Performance Optimization**:
   - GPU-accelerated filters (HUE, brightness) if not already
   - Adaptive bitrate streaming for VR (lower latency)
   - Thermal throttling detection + warning

4. **Accessibility**:
   - Closed captioning styling (font size, contrast for VR)
   - Colorblind-friendly HUE presets
   - High-contrast mode

---

## 12. Sign-Off & Approval

| Role | Name | Approval | Date |
|------|------|----------|------|
| Product Owner | [TBD] | Pending research | — |
| Engineering Lead | [TBD] | Pending roadmap review | — |
| QA Lead | [TBD] | Ready to support | — |

---

## 13. Appendices

### A. Related Documentation

- [spec_vr-video-controls.md](spec_vr-video-controls.md) — Development spec (Unified Dialog)
- [spec_vr-video-3d-research.md](spec_vr-video-3d-research.md) — Research spike (3D + Pointer)
- [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) — Player architecture overview
- [docs/DEV_OPS.md](../docs/DEV_OPS.md) — Build commands and scripts

### B. External References

- [ExoPlayer GitHub](https://github.com/google/ExoPlayer)
- [Media3 Documentation](https://developer.android.com/guide/topics/media/media3)
- [Android MotionEvent API](https://developer.android.com/reference/android/view/MotionEvent)
- [Stereoscopy — Wikipedia](https://en.wikipedia.org/wiki/Stereoscopy#Video)
- [Kodi 3D Video Guide](https://kodi.wiki/view/3D_Video)

---

**Document Owner**: [TBD]  
**Last Updated**: 2026-04-17  
**Next Review**: After research phase completion (Week 2)
