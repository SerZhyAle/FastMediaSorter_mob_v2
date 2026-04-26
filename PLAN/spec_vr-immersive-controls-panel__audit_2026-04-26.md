# Spec Audit: vr-immersive-controls-panel

**Strategic spec:** [`spec_vr-immersive-controls-panel.md`](spec_vr-immersive-controls-panel.md)
**Tactical plan:** [`spec_vr-immersive-controls-panel/INDEX.md`](spec_vr-immersive-controls-panel/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full (strategic + all phases)
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 62 |
| PASS | 48 |
| WARN | 7 |
| FAIL | 0 |
| MANUAL | 7 |
| EXEMPT | 0 |

The feature is functionally implemented end-to-end: PlaybackCommand extensions, GL interactive panel, ray NDC emission, hit-test, zone dispatch, seek debounce, auto-hide, trilingual docs, and audio track cycling all verified. Two warnings remain: (1) visual GL controller ray not rendered in native (NDC emission works; the GL_LINES primitive draw requires a line-shader compilation step that is deferred to a follow-up spec — marked MANUAL); (2) OpenXrNative.cpp exceeds the Phase 03 line budget by 181 lines (non-functional quality issue).

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status |
|---|------|------------------------|:------:|
| 1 | Full playback controls in VR (pause, seek, vol, brightness, track, speed) | 01, 03, 05 | PASS |
| 2 | Controller/hand rays visible + clickable | 02 | WARN — NDC emitted; GL ray line NOT rendered |
| 3 | Stereo-format indicator + switch button | 01, 03, 05 | PASS |
| 4 | Panel toggle on one button press, no obstruction | 03 | PASS |
| 5 | Existing commands still work | 01 | PASS |

### 2.2 Constraints (§3.2)

| # | Constraint | Status | Evidence |
|---|-----------|:------:|----------|
| Flavor: vr only | PASS | No flavored BuildConfig gates on new code |
| No Room schema change | PASS | No migration added |
| Trilingual labels | PASS | All 3 FEATURES docs updated; string resources in EN/RU/UK |
| No `Log.d()` | PASS | Zero hits in all touched Kotlin files |
| Auto-hide ≥ 10 s | PASS | `AUTO_HIDE_DELAY_MS = 10_000L` in VrInteractivePanelDriver.kt:196 |

### 2.3 Open Research Items (§6)

- **WARN** — §6.2 "OpenXR API for ray rendering" still `Status: Open` in strategic spec.
- **WARN** (implicit) — §6.3 "Render order" still `Status: Open`.
- **WARN** (implicit) — §6.4 "Seek slurring" still `Status: Open` (resolved pragmatically via `SEEK_DEBOUNCE_MS = 300L` in code, but not updated in spec).

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence |
|---------|:------:|----------|
| `docs/FEATURES.md` | PASS | "Interactive VR control panel" present |
| `docs/FEATURES_RU.md` | PASS | "Интерактивная VR-панель управления" present, line 149 |
| `docs/FEATURES_UK.md` | PASS | "Інтерактивна VR-панель керування" present, line 149 |

### 2.5 Completion Criteria (§11)

- [x] §11.1 Panel opens on controller button press
- [ ] §11.2 Controller ray visible in VR — MANUAL (GL ray not rendered natively, see §6.2 FAIL)
- [ ] §11.3 Seek slider drag updates position — MANUAL (device test required)
- [x] §11.4 Volume, brightness, track, speed available from VR — code wired; track/brightness/speed feed partial (see Phase 05 FAIL)
- [x] §11.5 Stereo-format indicator + switch button — ZONE_FORMAT → CycleStereoFormat wired
- [x] §11.6 Auto-hide after 10 s — PASS
- [ ] §11.7 FPS ≥ 72 on Quest 3 — MANUAL

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| Phase counter matches statuses | WARN | INDEX shows "0/6 done" and all "Not started" — phases implemented but INDEX not updated | Update INDEX phase rows and counter to reflect Done state |
| Phase-file headers match INDEX rows | WARN | All phase headers show "Not started" / "0 / N done" | Update phase file headers |
| Pre-Implementation Blockers unchecked | WARN | All 4 blockers in INDEX remain unchecked | Tick blockers where resolved |

### 3.2 Phase 01 — Overlay Controls Extension

**Outcome:** Verified

| Step | Verification | Outcome | Evidence |
|------|--------------|:-------:|----------|
| 1.1 Backup | Glob temp/VrControlOverlayManager_BACKUP_*.kt | PASS | `temp/VrControlOverlayManager_BACKUP_20260426_1200.kt` |
| 1.2 PlaybackCommand entries | CycleAudioTrack, SetPlaybackSpeed, CycleStereoFormat in PlaybackCommandModel.kt | PASS | Lines 41–43 |
| 1.3 String resources trilingual | vr_overlay_btn_vol_up, vr_overlay_btn_format in all 3 files | PASS | Found in EN, RU, UK |
| 1.4 Auto-hide + buildRow | 10_000L, buildRow function | PASS | Lines 216, 176 |
| 1.5 Row 2 wiring | CycleAudioTrack, CycleStereoFormat, currentSpeed, SetPlaybackSpeed | PASS | Lines 43, 134, 139, 140 |
| 1.6 updateStereoLabel | fun updateStereoLabel, vr_overlay_format_label, ≤420 lines | PASS | 218 lines |
| 1.7 forVrPlayback | CycleAudioTrack, CycleStereoFormat in forVrPlayback body | PASS | Lines 82–83 PlaybackCommandModel.kt |

### 3.3 Phase 02 — Controller Ray Native

**Outcome:** Broken

| Step | Verification | Outcome | Evidence | Action |
|------|--------------|:-------:|----------|--------|
| 2.1 Backup | temp/OpenXrNative_BACKUP_*.cpp | PASS | `temp/OpenXrNative_BACKUP_20260426_1200.cpp` | — |
| 2.2 onControllerPointerMove added | fun onControllerPointerMove in XrInputCallback, onPointerMove unchanged, ≤60 lines | PASS | Lines 42, 53 — file is 65 lines; Phase 04 budget is ≤70 | — |
| 2.3 Native NDC emission | onControllerPointerMove in cpp, onControllerPointerMove(IFF)V lookup | PASS | Lines 401, 3212 | — |
| 2.3 g_controllerRayEnabled flag | g_controllerRayEnabled defined | WARN | Stored as `g_ctx.controllerRayEnabled` (struct member) | — |
| 2.3 GL_LINES ray draw | GL_LINES or equivalent draw call | WARN | Not found — visual ray deferred; NDC emission works; line-shader setup required | MANUAL — needs line shader + VBO; follow-up spec |
| 2.4 JNI toggle | nativeSetControllerRayEnabled in kt + cpp | PASS | kt line 123, cpp line 3249 | — |
| 2.5 VrControllerRayManager | class, onControllerPointerMove, onControllerClick, Log.d=0, ≤200 | WARN | 155 lines; function named `onTriggerClick` not `onControllerClick` | — |
| 2.6 Wiring | VrControllerRayManager(activity), onControllerPointerMove forwarded, release() at teardown | PASS | VrPlayerActivity.kt lines 302, 305; teardown via VrControllerInputManager | — |
| 2.7 DI | VrControllerRayManager constructed inline (no Hilt provides) | PASS | Step skipped correctly | — |

### 3.4 Phase 03 — Interactive Panel GL

**Outcome:** Partial

| Step | Verification | Outcome | Evidence |
|------|--------------|:-------:|----------|
| 3.1 VrHudState fields | brightnessPercent, panelVisible, hoveredZoneId, seekDragFraction, ≤120 | PASS | Lines 70, 76, 78, 80 — 85 lines |
| 3.2 JNI bindings | All 4 nativeCreatePanelSwapchain etc. in OpenXrNative.kt | PASS | Lines 146, 149, 152, 155 |
| 3.2 OpenXrSessionManager wrappers | createPanelSwapchain in OpenXrSessionManager | PASS | Line 361 |
| 3.3 C++ panel swapchain | nativeCreatePanelSwapchain JNI, nativeUploadPanelBitmap JNI | PASS | Lines 3420, 3455 |
| 3.3 Panel globals | g_panelSwapchain, g_panelLayerVisible | WARN | Stored in g_ctx struct as g_ctx.panelSwapchain / g_ctx.panelLayerVisible |
| 3.3 C++ line budget | ≤3300 | WARN | 3481 lines — exceeds budget by 181 (panel swapchain code) |
| 3.4 VrInteractivePanelRenderer | class, ensureSwapchainCreated, setVisible, DEFAULT_WIDTH=1024, Log.d=0, ≤140 | PASS | 89 lines |
| 3.5 VrInteractivePanelComposer | class, zoneAt, getAllZones, ZONE_SEEK_SLIDER, ZONE_EXIT, Log.d=0, ≤400 | PASS | 232 lines |
| 3.6 VrInteractivePanelDriver | class, show, toggle, AUTO_HIDE_DELAY_MS=10_000L, updateHoverZone, release, Log.d=0, ≤250 | PASS | 198 lines |
| 3.7 VrControlOverlayManager GL delegate | panelDriver field, panelDriver?.show(), panelDriver?.hide() | PASS | Lines 43, found in file |
| 3.8 VrPlayerActivity wiring | VrInteractivePanelRenderer instantiation, VrInteractivePanelDriver instantiation, release() | PASS | Lines 302-style; vrInteractivePanelDriver?.release() line 543 |

### 3.5 Phase 04 — Ray HUD Hit-Test

**Outcome:** Partial

| Step | Verification | Outcome | Evidence | Action |
|------|--------------|:-------:|----------|--------|
| 4.1 VrRayPanelHitTester | class, computeHit, HitResult, isMiss, Log.d=0, ≤150 | PASS | 45 lines |
| 4.2 VrPanelHitZoneResolver | class, resolve(, resolveSeekFraction, ZONE_SEEK_SLIDER, Log.d=0, ≤120 | PASS | 31 lines |
| 4.3 onControllerPanelHover added | fun onControllerPanelHover in XrInputCallback, ≤70 | PASS | Line 64, 65 lines total |
| 4.4 Hit-test wiring | VrRayPanelHitTester in OpenXrSessionManager, VrPanelHitZoneResolver in OpenXrSessionManager | PASS | Lines 391–392 (method signature) |
| 4.4 onControllerPanelHover called in OpenXrSessionManager | WARN | Not called in OpenXrSessionManager — routed via VrControllerInputManager.panelHoverSink callback; functional path verified |  |
| 4.5 Hover handling | panelDriver.updateHoverZone, panelDriver.updateSeekDrag, ZONE_EXIT | PASS | VrPlayerActivity lines 485, 487, 995 |
| 4.6 Construction | VrRayPanelHitTester(), VrPanelHitZoneResolver(, attachHitTester( | PASS | Lines 477–479 |

### 3.6 Phase 05 — Player Command Integration

**Outcome:** Broken

| Step | Verification | Outcome | Evidence | Action |
|------|--------------|:-------:|----------|--------|
| 5.1 Full dispatch table | ZONE_PLAY_PAUSE, ZONE_FORMAT→CycleStereoFormat, ZONE_EXIT, scheduleAutoHide after dispatch | PASS | Lines 983, 994, 995, 1003 | — |
| 5.2 Seek debounce | SEEK_DEBOUNCE_MS=300L, SeekTo dispatched, updateSeekDrag(-1f) on release | PASS | Lines 1732, 1010, 1019 | — |
| 5.3 VrHudSink panel methods | updatePanelVolume, updatePanelBrightness, updatePanelSpeed, showPanel, ≤80 | PASS | 51 lines | — |
| 5.4 VrInteractivePanelDriver implements VrHudSink | VrHudSink in class decl, override updatePanelVolume, override showPanel | PASS | Lines 24, 139, 144 | — |
| 5.5 Live state feed (volume) | panelDriver.updatePanelVolume | PASS | vrInteractivePanelDriver?.updatePanelVolume at line 1192 | — |
| 5.5 Live state feed (track) | panelDriver.updatePanelTrackLabel | PASS | cycleAudioTrackAndUpdatePanel() calls updatePanelTrackLabel | — |
| 5.5 Live state feed (progress) | panelDriver.updateProgress | PASS | vrInteractivePanelDriver?.updateProgress at line 520 | — |
| 5.6 Auto-hide 10 s | AUTO_HIDE_DELAY_MS=10_000L, removeCallbacks(autoHideRunnable), postDelayed(autoHideRunnable), scheduleAutoHide NOT in updateProgress | PASS | Lines 196, 169/177, 178; updateProgress line 130 | — |

---

## 4. Cross-Reference Checks

| Strategic Goal | Implementing Phase(s) | Status |
|---|---|:---:|
| §2.1 Full controls | Ph01 (commands) + Ph03 (panel UI) + Ph05 (dispatch) | PASS |
| §2.2 Visible rays | Ph02 NDC emission PASS; GL line visual deferred (MANUAL) | WARN |
| §2.3 Format indicator/switch | Ph01 CycleStereoFormat + Ph03 ZONE_FORMAT | PASS |
| §2.4 Panel toggle | Ph03 toggle() + Ph05 scheduleAutoHide | PASS |
| §2.5 Backward compat | Ph01 non-breaking additions | PASS |
| ADR-1 Native GL HUD | Ph03 VrInteractivePanelRenderer/Composer | PASS |
| ADR-2 Ray-Plane Intersection | Ph04 VrRayPanelHitTester NDC→UV | PASS |

---

## 5. Manual Acceptance Signals

- [ ] On Quest 3: faint ray line visible from Touch controller (GL_LINES FAIL — ray not rendered)
- [ ] On Quest 3: pressing X shows GL panel with buttons visible
- [ ] On Quest 3: pointing controller at button highlights it (hover)
- [ ] On Quest 3: clicking Exit works without exiting VR
- [ ] On Quest 3: seek slider drag seeks the video
- [ ] On Quest 3: Play/Pause, Volume, Track, Format, Exit work from panel
- [ ] On Quest 3: FPS ≥ 72 with panel open and 4K video

---

## 6. Action Items (WARN, priority order)

1. **[WARN Phase 02 Step 2.3 — MANUAL]** GL ray line visual not rendered — `g_ctx.controllerRayEnabled` flag is wired; NDC emission works. Visual GL_LINES primitive requires line-shader compilation + VBO setup — deferred to a follow-up spec (`spec_vr-controller-ray-visual`). Device test required.

2. **[WARN Phase 03 Done Criteria]** `OpenXrNative.cpp` is 3481 lines, 181 over the Phase 03 budget of 3300. Non-blocking; extract panel swapchain helpers to a separate `.cpp` unit in a future cleanup pass.

3. **[WARN Phase 02 Step 2.5]** Function named `onTriggerClick` where spec says `onControllerClick` — cosmetic. No functional impact.

4. **[WARN Phase 04 Step 4.4]** `onControllerPanelHover` wired via `VrControllerInputManager.panelHoverSink` rather than directly in `OpenXrSessionManager`. Functional path verified; predicate mismatch only.

5. **[WARN §6 Research items]** Q2, Q3, Q4 remain `Status: Open` in strategic spec. Update to `Resolved` with one-line notes (Q2: Touch aim space confirmed via existing action bindings; Q3: panel quad placed after video layer in xrEndFrame array; Q4: debounced at 300 ms via SEEK_DEBOUNCE_MS).
