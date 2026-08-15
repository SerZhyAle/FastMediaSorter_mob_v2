# Phase 06 - Render Quality (MSAA + sRGB + Mipmap + Anisotropy)

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Apply four independent quality improvements to the native render pipeline: MSAA 4x on swapchain, sRGB-aware texture format with gamma alignment, mipmap chain with `LINEAR_MIPMAP_LINEAR`, anisotropic filtering 8x. These improvements are orthogonal to Phase 04 / 05; they can land in any order after Phase 01.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (UV convention fixed; required for sRGB sanity).
- [ ] Working tree is clean or on a feature branch.
- [ ] Quest 3 device available for benchmarking after each step.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1340 (current 1275 + Phase 01 delta) |

> Backup `xr_session.cpp` to `temp/` again at the start of this phase (cumulative changes since Phase 01 will be significant).

---

## Steps

### Step 06.1 - Backup xr_session.cpp before Phase 06 edits

**Files:** `temp/xr_session.cpp.*.bak`
**Depends on:** start of phase

**Prompt for developer:**

> Create a fresh timestamped copy of `app_v2/src/vr/cpp/xr_session.cpp` in `temp/` (file is over 500 LOC per CLAUDE.md Rule 5).

**Verification:**

- `Glob` - `temp/xr_session.cpp.*.bak` returns at least one new match (timestamp newer than Phase 01 backup).

**Status:** `[ ]` not done

---

### Step 06.2 - Enable MSAA 4x on swapchain

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 06.1

**Prompt for developer:**

> In `createSwapchains()`, stop assuming `sampleCount = 4` is universally available. Read `viewConfig.maxSwapchainSampleCount` for each eye and choose a `desiredSampleCount` via fallback ladder `4 -> 2 -> 1` bounded by the runtime maximum. Use the chosen value for `sci.sampleCount`. If the runtime still rejects the requested sample count, retry with the next lower rung and log a neutral warning (for example, `DiagnosticXrSession: MSAA 4x rejected, falling back to 2x`). Add a one-time neutral info log after successful swapchain creation (for example, `DiagnosticXrSession: swapchain sampleCount=%d`).

**Verification:**

- `Grep` - `maxSwapchainSampleCount` matches at least once in `xr_session.cpp`.
- `Grep` - `swapchain sampleCount=` matches at least once in `xr_session.cpp`.
- On-device: app launches, logcat shows the actual chosen sample count (`4`, `2`, or `1`) and the app remains stable.
- Visual: edges of stereo-TB content no longer show ladder-stepping at default viewing distance.

**Status:** `[ ]` not done

---

### Step 06.3 - Enable mipmap chain + LINEAR_MIPMAP_LINEAR for photo textures

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 06.2

**Prompt for developer:**

> After every `glTexImage2D` call that uploads the **photo content texture** (`g.texture`) from Bitmap / RGBA bytes (NOT the SurfaceTexture/video path which is external OES, and NOT the HUD texture), insert `glGenerateMipmap(GL_TEXTURE_2D)`. Change the `GL_TEXTURE_MIN_FILTER` parameter for `g.texture` from `GL_LINEAR` to `GL_LINEAR_MIPMAP_LINEAR`. Keep `GL_TEXTURE_MAG_FILTER` at `GL_LINEAR`. Apply this both to the initial placeholder / first image upload path and to subsequent runtime image updates. The HUD texture (1024×128 or 1024×512 depending on phase) does NOT benefit from mipmaps — skip generation there and keep `GL_LINEAR` for both filters.

**Verification:**

- `Grep` - `glGenerateMipmap\(GL_TEXTURE_2D\)` matches at least once in `xr_session.cpp` (for the photo texture upload).
- `Grep` - `GL_LINEAR_MIPMAP_LINEAR` matches at least once in `xr_session.cpp`.
- Visual: 360° equirect content shows no shimmer / moiré on high-frequency content (rocks, branches, water reflections).

**Status:** `[ ]` not done

---

### Step 06.4 - Enable anisotropic filtering 8x for photo textures

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 06.3

**Prompt for developer:**

> Right after the mipmap parameter setup (Step 06.3), query the max anisotropy supported via `GLfloat maxAniso; glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, &maxAniso)`, then set `glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, std::min(maxAniso, 8.0f))` on the photo texture. Guard the whole block with the extension availability check (`GL_EXT_texture_filter_anisotropic` / `GL_TEXTURE_MAX_ANISOTROPY_EXT`). If the extension is unavailable, keep the pipeline functional and log one neutral warning; do not fail the phase on devices that simply omit anisotropy.

**Verification:**

- `Grep` - `GL_TEXTURE_MAX_ANISOTROPY_EXT` matches at least twice (once in query, once in set).
- `Grep` - `8\.0f` near `glTexParameterf.*ANISOTROPY` matches at least once.
- Visual: at oblique viewing angles on the equirect sphere (looking up/down toward poles), texel sharpness no longer collapses to mush.

**Status:** `[ ]` not done

---

### Step 06.5 - Switch photo texture format to GL_SRGB8_ALPHA8 + align shader gamma

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 06.4

**Prompt for developer:**

> Two coordinated changes:
> 1. Change the `internalformat` of `glTexImage2D` for the **photo texture** (NOT HUD) from `GL_RGBA8` to `GL_SRGB8_ALPHA8` when the GL context supports it. With sRGB-tagged textures, OpenGL ES samples in linear space automatically. The swapchain is already selected as `GL_SRGB8_ALPHA8` when the runtime offers it, so output also goes through automatic linear→sRGB encode — gamma is now end-to-end correct. If `GL_SRGB8_ALPHA8` is unavailable for the texture path, keep `GL_RGBA8` and log one neutral warning rather than failing.
> 2. Confirm `kFragmentShader` does NOT manually apply gamma (it currently does not — just `outColor = texture(u_tex, uv)`). If any manual gamma operation is present after recent edits, remove it.
>
> Keep HUD texture at `GL_RGBA8` — HUD is UI text/icons rendered in sRGB-space by Android Canvas, so an sRGB→linear→sRGB chain would double-apply gamma. HUD path remains as-is.

**Verification:**

- `Grep` - `GL_SRGB8_ALPHA8.*GL_RGBA` (regex matching the photo `glTexImage2D` call) matches at least once in `xr_session.cpp`.
- `Grep` - HUD `glTexImage2D` retains `GL_RGBA8` (one occurrence remains in the HUD code path).
- Visual: contrast of the bundled lakeside.jpg now matches the source on a calibrated monitor; previously washed-out look is gone.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd`).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Bench / log notes record the actual chosen sample count and whether anisotropy / sRGB were enabled or gracefully skipped.
- [ ] Bench: VrApi log shows stable 72 FPS on bundled 8K equirect with MSAA 4x active. If sustained drops observed → lower MSAA to 2x (one-line change in Step 06.2) and re-document.

---

## Handoff Notes to Next Phase

The render pipeline is now baseline-quality: anti-aliased, sRGB-correct, mipmap-filtered, anisotropic. Phase 07 layers on top — compositor-side Equirect2 (if owner-approved) replaces the sphere-mesh path for 360°-mode only and inherits all these quality settings automatically (compositor handles MSAA/sRGB internally). FFR addition further reduces GPU cost.

---

## Rollback Plan

Each step is independent; revert in reverse order (sRGB → anisotropic → mipmap → MSAA) to bisect any visual regression. All changes confined to `xr_session.cpp` — no shader source / mesh code touched.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 3. Proposed (DISCUSS): 0.
