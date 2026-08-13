# S1228 - Immersive HUD reworked into a readable bottom strip with a working ray and a close button

**Status:** Archived
**Priority:** 75

## 0. Raw capture

Owner, 2026-07-27, in the headset:

> "в иммерсив диалог HUD слишком маленький - шрифт как маленькие кубики. На луч от джойстика реагирует, но джойстик нужно наводить значительно выше элемента которым управляешь. HUD желательно пересетить вниз чтобы видеть содержимое и выполнить в одну горизонтальную панель. С кнопкой закрытия"

> "ты там не умничай - говорю тебе управление неочевидное - значит чиним"

## 1. Four problems, one of them a defect

**1.1 The ray was mirrored vertically.** `HudInteractionDispatcher` converted the ray's UV to canvas pixels with `py = uvY * HEIGHT`, above a comment asserting "OpenXR UV coordinates have V=0 at the top". That assumption is wrong: UV.y is 0 at the quad **bottom** (GL convention, `xr_raycast.cpp`), while the renderer's rects are Canvas-space with y=0 at the top. Every hit was mirrored, so the owner had to aim well above the control - which is exactly what was reported.

The browser's dispatcher already flips (`ImmersiveBrowseInteractionDispatcher`, S1132). The player call site kept the opposite assumption stated as a confident comment, which is why it survived review. **S1132's own status note deferred this follow-up "until this confirms flip direction" - the owner's report is that confirmation.**

**1.2 The panel was unreadable.** 1024x640 canvas on a 0.48x0.30 m quad puts glyphs at ~0.015 m tall at the 1.5 m watch distance - under 0.6 degrees of arc.

**1.3 It covered the film.** The quad was centred in view. That centring was a deliberate S0290 round-3 decision so the *diagnostic banner* lands on screenshots without a head tilt - correct for the banner, wrong for a player panel.

**1.4 It could not be dismissed.** No visibility control existed anywhere in the stack (see **S1223** for the wider discoverability problem).

## 2. Change

**Renderer** - `ui/xr/helpers/HudCanvasRenderer.kt`, rewritten:

- 1024x640 block to a 2560x360 strip; every control on one row: transport, AUDIO, SUBS, VOLUME, STEREO DEPTH.
- Fonts 32/40 to 48/52 px; combined with the wider quad this is ~0.028 m glyph height, roughly 1.9x the previous angular size.
- Close button top-right. Collapsed state paints only a centred "HUD" pill and clears the rest of the quad, so restoring needs no new controller binding - the ray already addresses this texture.
- All layout literals are named constants (the previous version had inline magic numbers).

**Dispatcher** - `ui/xr/helpers/HudInteractionDispatcher.kt`:

- Y flip, with the corrected convention documented and cross-referenced to `xr_raycast.cpp`.
- `onCollapseToggled(collapsed)` added to the listener with a no-op default, so the banner-only diagnostic call site is unaffected.
- A collapsed panel consumes the click and dispatches nothing else - controls that are not painted cannot be pressed.

**Placement** - the quad's vertical offset became per-mode rather than global:

- `xr_hud_set_quad_size(w, h, verticalOffsetMeters)` - signature extended so size and placement travel together and a caller cannot set one and forget the other. Threaded through `xr_session_set_hud_quad_size`, the JNI bridge, `DiagnosticXrRuntime` and `NativeDiagnosticXrRuntime`.
- Player panel: 1.40 x 0.197 m at dy = -0.30 m. It spans -0.3985..-0.2015 m and so clears the S0986 subtitle quad at -0.5625..-0.4375 m.
- Diagnostic banner: dy = 0.0 - the S0290 centring is preserved deliberately.
- Immersive browser: dy = 0.0 - the browser *is* the content being read, not an overlay on it.

## 3. Verification

- Build: `.\a.ps1 nd` - BUILD SUCCESSFUL (Kotlin + native), APK `v2.60.7272.259` then `.309`, installed on Quest 3 `2G0YC5ZG5608DL`.
- Probes in place: `S1228: panel quad ..` on mode assert, `S1228: HUD panel collapsed=..` on toggle.

Device checks owed:

1. Aim directly at a control - it activates. No vertical offset needed. If aiming is now wrong in the opposite direction, the flip is inverted and must be reverted rather than doubled.
2. Text is readable at watch distance.
3. The strip sits below the film and does not cover the centre of view.
4. Close collapses to the pill; the pill restores the strip; sliders and track rows still work after a restore.
5. The diagnostic playlist banner is unchanged - still centred, still the small filename strip.
6. The immersive browser panel is unchanged.

## 4. Related

- **S1223** - controls discoverability: the bindings still have no in-headset legend. This ticket adds a close button; it does not answer "how would a user learn any of this".
- **S1132** - the browser-side half of the same inversion, whose deferred follow-up this closes.
- **S0964** - the panel whose rows were relaid out here.
- **S0986** - the subtitle quad the new placement had to clear.
