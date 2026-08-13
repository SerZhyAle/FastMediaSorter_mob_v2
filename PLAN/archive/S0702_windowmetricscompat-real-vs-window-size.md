# Стратегическая спецификация: S0702 - WindowMetricsCompat отдаёт real-screen size вместо window size

**Ticket:** S0702
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - parked from S0693 research on 2026-06-25
**Tactical spec:** `PLAN/S0702_windowmetricscompat-real-vs-window-size/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC skeleton. Captured idea only - no research/approval/spec-tech chaining yet.

---

## 0. Captured material (inbox)

**Captured:** 2026-06-25 (parked during S0693 research, per CLAUDE.md §3.1)

**Symptom / evidence:**

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/WindowMetricsCompat.kt` returns the REAL screen size: API 28-29 via deprecated `defaultDisplay.getRealSize()`, API 30+ via `WindowManager.currentWindowMetrics.bounds`. Both ignore multi-window: in split-screen/freeform the player computes against the full physical screen, not its own window.
- Consequence: any player-helper sizing decision derived from `WindowMetricsCompat` is wrong when the player runs in a non-fullscreen window (split-screen, freeform, foldable app-pair).
- `getRealSize()` is also deprecated at API 31.

**Why out of scope of S0693:** S0693 explicitly excludes the player family and uses `Configuration.screenWidthDp` (already window-aware), so it never touches this helper. This is a latent player-side correctness bug needing its own audit of every `WindowMetricsCompat` caller.

**Open angles (for later research):**

- Enumerate every caller of `WindowMetricsCompat.getScreen*` in `ui/player/**` and classify which need window size vs real-screen size.
- Decide replacement: `Configuration.screenWidthDp/heightDp`-derived px, or `WindowMetrics.bounds` minus insets, per caller intent.
- Whether the helper should be split into "window size" and "real-screen size" variants rather than one ambiguous API.

**Attachments: none.**

---

## 1. Problem

`WindowMetricsCompat` had two inconsistent branches. API 30+ used `currentWindowMetrics.bounds` (already window-aware). The legacy path (< API 30) used `defaultDisplay.getRealSize()`, which returns the full physical display and ignores multi-window. All 8 callers (image scale-type on rotation + Glide decode-size caps in `ImageLoadingManager` and `AudioSlideshowPhotoModeManager`) need the player WINDOW size, so on legacy devices in split-screen/freeform they over-sized the decode and mis-computed scale type against the whole screen.

> §0 stated both branches ignored multi-window; on inspection only the legacy `getRealSize` branch did.

---

## 2. Resolution (Implemented 2026-06-26)

- Legacy branch (< API 30) switched from `display.getRealSize()` to `display.getSize()` - the app-usable, multi-window-aware size - in all three methods (`getScreenWidth` / `getScreenHeight` / `getScreenSize`). API 30+ `currentWindowMetrics.bounds` left as-is (already correct).
- KDoc/comments corrected: the object returns the current app window size, not the physical display.
- No caller changes needed - the 8 call sites already wanted window size; their behavior is now correct in multi-window and unchanged in fullscreen (window == display).
- Validation: `.\a.ps1 fk` BUILD SUCCESSFUL.

**Non-goals:** splitting the helper into window/real-screen variants (no caller needs the physical display); changing the API 30+ branch.

---

## 3. Related

- **Related tickets:** S0693 (parked this finding during its research).

---

## 12. Outcome

Implemented directly (primitive fix, one file) and archived - no tactical plan needed.
