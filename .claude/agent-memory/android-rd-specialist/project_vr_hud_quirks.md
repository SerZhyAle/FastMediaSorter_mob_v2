---
name: project-vr-hud-quirks
description: VR HUD pitfalls (S0290) + S0961/S0964 invariants - matrix convention, no per-frame queueHud, direct ByteBuffer, Skia RGBA, hudContentUploaded gate, panel-vs-banner visual ownership, quad-size override API.
metadata:
  type: project
---

VR diagnostic HUD has four hidden gotchas that all manifested simultaneously and produced "HUD totally invisible / black quad" symptoms for hours. All four discovered + fixed during S0290 round 3 (2026-05-22 16:30-16:45). Future work in `app_v2/src/vr/cpp/xr_hud_world.cpp` and `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` must respect these constraints or HUD regresses.

**Why:** owner spent ~3 hours testing v500..v624 seeing "HUD invisible" with us swapping byte orders, changing colors, moving the HUD geometrically before finding the real root causes were higher up the stack.

**How to apply:**

1. **`multiply_matrices` in `xr_hud_world.cpp` must use column-major formula** (`out[c*4+row] = sum a[k*4+row] * b[c*4+k]`) - same as `multiply4x4` in `xr_session.cpp`. The original code used row-major indexing (`temp[i*4+j] = sum a[i*4+k] * b[k*4+j]`) on column-major data → computed `B*A` instead of `A*B` → MVP became `model*view*proj` instead of `proj*view*model` → HUD quad rendered with nonsense MVP placing it outside FOV.

2. **Do NOT add per-frame `queueHud` calls from native callbacks.** `onNativeRayInteraction` previously rebuilt full HudCanvasRenderer panel and called `runtime.queueHud(hudRgbaBytes, 1024, 512)` on every native ray-tick (~12 ms). Two problems combined: (a) the rendered bitmap content didn't actually make it into the buffer (see point 3), so it pushed all-zero 1024×512 bytes; (b) even if it had content, it would have overwritten the `queueFilenameHud` banner queued on slide change. Native-driven UI updates must be VERY rare (state transitions only), never per-frame.

3. **`Bitmap.copyPixelsToBuffer(ByteBuffer.wrap(byteArray))` produces all-zero output** on heap-backed buffers. Always use `ByteBuffer.allocateDirect(w*h*4)` then read back via `buf.rewind() + buf.get(bytes)`. The `wrap()` path looks identical syntactically and gets you a `HeapByteBuffer`, but `Bitmap.copyPixelsToBuffer` either silently fails or writes to internal storage instead of the wrapped array. Confirmed via native logcat showing `xr_session_queue_hud STORED 1024x512 first pixel RGBA=0,0,0,0` after a fully-rendered Canvas.

4. **Android Skia ARGB_8888 + `Bitmap.copyPixelsToBuffer` produces RGBA byte stream directly compatible with OpenGL `GL_RGBA`/`GL_UNSIGNED_BYTE`** - NO R/B swap needed. Documentation gave the impression Skia stores BGRA in memory (and on Linux/Skia desktop it does), but on Android the framework normalizes to OpenGL-friendly RGBA on `copyPixelsToBuffer`. Verified via debug build with `drawColor(Color.argb(255, 255, 220, 0))` (yellow), pushed raw bytes via JNI to `glTexImage2D(GL_RGBA, GL_UNSIGNED_BYTE)`, displayed on headset as yellow (not blue or any swap variant). Adding an R↔B byte swap as a "Skia BGRA workaround" produces visible CYAN where the source was MAGENTA = guaranteed regression.

Regression test on any future HUD code change: build APK, install, attach `adb logcat`, look for three log lines confirming pipeline:

```
S0249.XrSession: xr_session_queue_hud STORED 1024x128 (524288 bytes); first pixel RGBA=<expected color>
S0249.XrSession: hud upload: 1024x128 to texture=8
DiagnosticXrHud: xr_hud_render ok: eye=0 hudTex=8 quad.center=(X,Y,Z) wh=1.20x0.68
```

If any of these are missing or `EARLY RETURN`, HUD is broken - diagnose using the same log markers.

**S0961/S0964 additions (2026-07-11):**

5. **HUD texture is born as an opaque grey 1x1 `{64,64,64,255}` placeholder** (`createGlAssets`). Since S0961 the quad is HIDDEN until the first real `hud upload` - `g.hudContentUploaded` flag in `xr_session.cpp`; the render call site passes `hudTex=0` before that, so `xr_hud_render EARLY RETURN .. hudTex=0` while pending is EXPECTED, not a bug. Flag disarms in createGlAssets (placeholder rebirth) and at shutdown.
6. **Visual ownership is launch-mode-split (S0964):** `FILE_URI` (user launches: player / Browse VR Cinema) renders the full interactive `HudCanvasRenderer` panel (1024x640) into the quad; `DIAGNOSTIC_PLAYLIST` keeps the 1024x128 filename banner. Before S0964 the panel was interaction-model-only (blind clicks) - do not "restore" banner-only visuals on FILE_URI.
7. **Quad size is runtime-settable**: `runtime.setHudQuadSize(w,h)` -> `xr_hud_set_quad_size` (override persists across sessions in-process; `xr_hud_init` re-applies it). Each mode must set its own size explicitly on session ready (panel 0.48x0.30, banner 0.3x0.113) - relying on defaults breaks after a mode switch within one process.
8. **Panel repaints are debounced (~100 ms) and state-driven** (play/pause, volume, tracks changed, cycle clicks). Volume slider drag fires per ray-tick - never wire it straight to `queueHud`.

See also [[user_author_style]], [[project_functionality_log]].
