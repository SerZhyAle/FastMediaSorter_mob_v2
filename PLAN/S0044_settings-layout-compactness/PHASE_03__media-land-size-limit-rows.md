# Phase 03 — Media Land Size Limit Rows

**Strategic spec:** [../S0044_settings-layout-compactness.md](../S0044_settings-layout-compactness.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-01
**Completed:** 2026-05-01

---

## Objective

Add landscape-specific layouts for the `Audio`, `Video`, and `Images` settings fragments so the size-limit inputs become single-row groups while the rest of each fragment inherits the compact settings spacing. The invariant is that every media settings fragment affected by S0044 has a dedicated `layout-land` file and a horizontal `min/max` input group.

## Files Touched

| File | Action | Note |
|------|--------|------|
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | New | Landscape-only compact audio settings with one-row size inputs. |
| `app_v2/src/main/res/layout-land/fragment_settings_video.xml` | New | Landscape-only compact video settings with one-row size inputs. |
| `app_v2/src/main/res/layout-land/fragment_settings_images.xml` | New | Landscape-only compact image settings with one-row size inputs. |

---

## Steps

### Step 3.1 — Add landscape Audio settings layout

**Status:** `[x] done`
**Depends on:** none
**Blocks:** Step 3.2

**Prompt for developer:**

Create `layout-land/fragment_settings_audio.xml` by adapting the portrait file to the compact landscape settings dimensions. Keep all existing ids unchanged, preserve helper button ids, and change `layoutAudioSizeInputs` to a horizontal container with two equal-width `TextInputLayout` children sized by settings resources.

**Files Touched:** `app_v2/src/main/res/layout-land/fragment_settings_audio.xml`

**Verification:**

```text
Glob: app_v2/src/main/res/layout-land/fragment_settings_audio.xml -> 1 result
Grep: "android:id=\"@\+id/layoutAudioSizeInputs\"" in app_v2/src/main/res/layout-land/fragment_settings_audio.xml -> 1 hit
Grep: "android:orientation=\"horizontal\"" in app_v2/src/main/res/layout-land/fragment_settings_audio.xml -> 1+ hits
```

### Step 3.2 — Add landscape Video settings layout

**Status:** `[x] done`
**Depends on:** Step 3.1
**Blocks:** Step 3.3

**Prompt for developer:**

Create `layout-land/fragment_settings_video.xml` with the same compact landscape pattern used for audio: existing ids preserved, shared settings dims applied, and `layoutVideoSizeInputs` converted to a horizontal min/max row.

**Files Touched:** `app_v2/src/main/res/layout-land/fragment_settings_video.xml`

**Verification:**

```text
Glob: app_v2/src/main/res/layout-land/fragment_settings_video.xml -> 1 result
Grep: "android:id=\"@\+id/layoutVideoSizeInputs\"" in app_v2/src/main/res/layout-land/fragment_settings_video.xml -> 1 hit
Grep: "android:id=\"@\+id/iconHelpVideoSizeLimits\"" in app_v2/src/main/res/layout-land/fragment_settings_video.xml -> 1 hit
```

### Step 3.3 — Add landscape Images settings layout

**Status:** `[x] done`
**Depends on:** Step 3.2
**Blocks:** Phase 04

**Prompt for developer:**

Create `layout-land/fragment_settings_images.xml` with compact landscape spacing and a horizontal `layoutImageSizeInputs` group. Preserve all existing ids from the portrait file and do not introduce new strings or behavior changes.

**Files Touched:** `app_v2/src/main/res/layout-land/fragment_settings_images.xml`

**Verification:**

```text
Glob: app_v2/src/main/res/layout-land/fragment_settings_images.xml -> 1 result
Grep: "android:id=\"@\+id/layoutImageSizeInputs\"" in app_v2/src/main/res/layout-land/fragment_settings_images.xml -> 1 hit
Grep: "android:orientation=\"horizontal\"" in app_v2/src/main/res/layout-land/fragment_settings_images.xml -> 1+ hits
```

---

## Phase Done Criteria

- [x] Project compiles (BUILD-REQUIRED — run `/build standard-debug`).
- [x] Dedicated `layout-land` files exist for `audio`, `video`, and `images` settings fragments.
- [x] Each landscape media layout contains a horizontal size-limit input group with preserved view ids.

---

## Step Log

- 2026-05-01 — Step 3.1 done. Added `layout-land/fragment_settings_audio.xml` with preserved ids and a horizontal `layoutAudioSizeInputs` group. Verification PASS.
- 2026-05-01 — Step 3.2 done. Bootstrapped `layout-land/fragment_settings_video.xml` from portrait and converted `layoutVideoSizeInputs` to a horizontal fixed-width row. Verification PASS.
- 2026-05-01 — Step 3.3 done. Bootstrapped `layout-land/fragment_settings_images.xml` from portrait and converted `layoutImageSizeInputs` to a horizontal fixed-width row. Verification PASS.
- 2026-05-01 — Phase done. `assembleStandardDebug` PASS after Phase 03 media layout changes.
