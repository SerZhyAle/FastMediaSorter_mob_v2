# Phase 03 - Render Quality

**Strategic spec:** [`../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md`](../S0291_vr_diagnostic_stereo_and_lifecycle_round2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Reduce flat-image shimmer by improving static texture filtering without changing controller rays or HUD placement.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1400 |

---

## Steps

### Step 03.1 - Configure mipmap filtering for static image texture

**Files:** `xr_session.cpp`
**Depends on:** start of phase

**Prompt for developer:**

> Configure the main static image texture with trilinear minification filtering and generate mipmaps after each static image upload.

**Verification:**

- `Grep` - `GL_LINEAR_MIPMAP_LINEAR` appears in `xr_session.cpp`.
- `Grep` - `glGenerateMipmap(GL_TEXTURE_2D)` appears in `xr_session.cpp`.

**Status:** `[x]` done

### Step 03.2 - Add anisotropic filtering when supported

**Files:** `xr_session.cpp`
**Depends on:** Step 03.1

**Prompt for developer:**

> Detect `GL_EXT_texture_filter_anisotropic` and apply a conservative anisotropy level to the main static image texture only.

**Verification:**

- `Grep` - `GL_EXT_texture_filter_anisotropic` appears in `xr_session.cpp`.
- `Grep` - `GL_TEXTURE_MAX_ANISOTROPY_EXT` appears in `xr_session.cpp`.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Native noLegal build covers C++ changes.

## Handoff Notes to Next Phase

Flat image shimmer should be reduced for still textures. Video external OES filtering remains unchanged.

## Rollback Plan

Revert phase commit(s). No data migration.
