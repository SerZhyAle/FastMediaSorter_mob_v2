# Phase 04 — apply-manifest-fixes

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05 (verify-tv-visibility)
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Apply all manifest changes confirmed by Phases 01–03 as necessary to make the app visible in Google TV Play Store. Preserve compatibility with phone / tablet / Wear OS / VR form factors.

> **Note:** The exact set of changes in this phase is determined by Phase 01–03 findings. The steps below represent the most likely fixes based on static pre-analysis; mark any step `⏭️ Skipped` if Phase 01–03 research determined it is not a blocker.

---

## Prerequisites

- [x] Phase 01 ✅ Done — manifest finding list compiled (2026-05-16).
- [ ] Phase 02 ✅ Done — Play Console audit complete. **← PENDING MANUAL**
- [ ] Phase 03 ✅ Done — device sideload test complete. **← PENDING MANUAL**
- [x] All INDEX Pre-Implementation Blockers checked — §6.1, §6.2, §6.3, §6.5, §6.7 all Resolved by Phase 01.
- [ ] Working tree is clean on `DEBUG-v003` branch.

> **Decision (2026-05-16):** Phase 01 research identified one confirmed blocker that can be fixed immediately without waiting for Phases 02 and 03: the TV banner must be replaced with a proper raster PNG. Phase 04 Step 4.5 (banner fix) is unblocked. Steps 4.1–4.3 are skipped per Phase 01 findings. Step 4.3b addresses any additional findings from Phase 02–03 (to be filled in after manual phases complete). Phase 04 can be partially executed now; fully closed only after Phase 02–03 are done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | current file ≈ 250 lines, well under 500 |

> File is under 500 lines — no backup required.

---

## Steps

### Step 4.1 — Fix `screenOrientation` on all activities (SKIPPED)

**Status:** `⏭️` Skipped
**Reason:** Phase 01 Step 1.3 finding: `android:screenOrientation` is **not declared** in any activity in the manifest. No portrait-family value is set. Not a TV Play Store blocker. No action required.

---

### Step 4.2 — Evaluate and mitigate `MANAGE_EXTERNAL_STORAGE` (SKIPPED)

**Status:** `⏭️` Skipped
**Reason:** Phase 01 Step 1.2 finding: `MANAGE_EXTERNAL_STORAGE` does not trigger a hardware feature filter. It is not a TV Play Store visibility blocker. No manifest change needed.

---

### Step 4.3 — Additional fixes from Phase 02–03 findings (PENDING MANUAL)

**Files:** `app_v2/src/main/AndroidManifest.xml` (possibly)
**Depends on:** Phase 02 ✅ Done, Phase 03 ✅ Done
**Condition:** Apply any confirmed blockers from Play Console Device Catalog reason (Phase 02 Step 2.1) or Play Store message (Phase 03 Step 3.4).

> Owner must fill in this step after completing Phases 02 and 03. If Device Catalog shows a specific incompatibility reason, map it to a concrete manifest attribute fix here and apply it. If no additional blockers emerge beyond BLOCKER A (banner), mark this step skipped.

**Verification:**

- Document: all Phase 02–03 confirmed blockers resolved OR step skipped with justification.

**Status:** `[ ]` not done — awaiting Phase 02 and 03 completion.

---

### Step 4.5 — Replace TV banner XML placeholder with proper raster PNG

**Files:**
- `app_v2/src/main/res/drawable/tv_banner.xml` — to be replaced / removed
- `app_v2/src/main/res/drawable-xhdpi/tv_banner.png` — **new** (320×180 px raster)
- `app_v2/src/main/res/drawable-hdpi/tv_banner.png` — **new** (240×135 px raster)
- `app_v2/src/main/res/drawable-mdpi/tv_banner.png` — **new** (160×90 px raster)
- `app_v2/src/main/res/drawable-xxhdpi/tv_banner.png` — **new** (480×270 px raster)
- `app_v2/src/main/res/drawable-xxxhdpi/tv_banner.png` — **new** (640×360 px raster)

**Depends on:** Phase 01 Step 1.6 (BLOCKER A confirmed). Unblocked — no dependency on Phase 02/03.

**Prompt for developer:**

> The current `res/drawable/tv_banner.xml` is a layer-list XML placeholder. TV launchers and the Google Play TV review process require a 16:9 raster bitmap. The xhdpi canonical size is 320×180 px.
>
> Design requirements: dark background (`#1A1A1A` or similar), app name "Fast Media Sorter" in white text on the right half, app icon (or simplified logo mark) on the left. The banner must include text (Play Store requirement for multi-language banner).
>
> Steps:
> 1. Create the banner PNG files at all required densities (xhdpi at 320×180 is the minimum; also mdpi 160×90, hdpi 240×135, xxhdpi 480×270, xxxhdpi 640×360).
> 2. Place them in the corresponding `res/drawable-<density>/` folders.
> 3. Remove (or keep but rename) `res/drawable/tv_banner.xml` — if kept, rename it to `tv_banner_placeholder.xml` so the manifest reference `@drawable/tv_banner` resolves to the PNG. Actually: delete `res/drawable/tv_banner.xml` once the PNG exists, because Android resource resolution for `@drawable/tv_banner` will prefer the density-qualified PNG over the baseline drawable XML.
> 4. Verify: in Android Studio, preview `@drawable/tv_banner` in the layout editor with an xhdpi device configuration — it must show the raster banner, not a placeholder.
>
> **Note:** If a professional-grade PNG cannot be produced immediately, an acceptable interim banner is: dark background + white app name text. Even a minimal proper PNG is better than the XML placeholder.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable-xhdpi/tv_banner.png` exists. ✅ PASS
- `Glob` — `app_v2/src/main/res/drawable/tv_banner.xml` does not exist (deleted). ✅ PASS expected: file absent | actual: file absent
- PNG created at all 5 densities: mdpi 160×90, hdpi 240×135, xhdpi 320×180, xxhdpi 480×270, xxxhdpi 640×360. ✅

**Status:** `[x]` done — 2026-05-16

---

### Step 4.4 — Build standardDebug and verify phone compatibility

**Files:** build output
**Depends on:** Step 4.5 (banner PNG must exist before build)

**Prompt for developer:**

> Run `.\build-debug.PS1` for `standardDebug`. Verify: (1) build succeeds — no resource not found errors for `@drawable/tv_banner`; (2) no new lint errors.

**Verification:**

- Build exits with code 0 — expected: `BUILD SUCCESSFUL` | actual: `BUILD SUCCESSFUL in 28s` ✅ PASS

**Status:** `[x]` done — 2026-05-16

---

## Phase Done Criteria

- [x] Steps 4.1 and 4.2 — `⏭️ Skipped` (per Phase 01 findings).
- [ ] Step 4.3 — awaiting Phase 02–03 completion (manual).
- [x] Step 4.5 — `[x] done` — TV banner PNG exists at xhdpi, xml placeholder removed.
- [x] Step 4.4 — `[x] done` — `standardDebug` build passes.
- [x] Dev log entry added for banner.
- [x] Dev log entry added for phase.

> Phase 04 partially done: Steps 4.5 + 4.4 complete. Step 4.3 closes after Phases 02–03.

---

## Handoff Notes to Next Phase

Phase 04 produces an updated manifest. Phase 05 publishes a new build and verifies that the app becomes visible in Play Store on Panasonic MX700.

---

## Rollback Plan

Revert `app_v2/src/main/AndroidManifest.xml` to pre-Phase-04 state via `git revert` of the phase commit. No data migration or database change is involved.
