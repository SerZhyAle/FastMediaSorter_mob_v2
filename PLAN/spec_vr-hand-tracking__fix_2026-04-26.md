# Spec Fix Run: vr-hand-tracking

**Source audit:** `spec_vr-hand-tracking__audit_2026-04-26.md`
**Fix date:** 2026-04-26
**Mode:** full
**Auto-applied:** 4
**Manual follow-ups:** 2

---

## 1. Auto-applied Fixes

| # | Origin | Category | Files | Outcome |
|---|--------|----------|-------|:-------:|
| 1 | WARN §2.5 — Catalog | Catalog regeneration | `dev/CATALOG/app_v2.jsonl` | ⚠️ PARTIAL — scan ran (802 files), but `src/vr/java` is outside scan scope; `VrHandRayManager` and `XrInputSource` not added automatically |
| 2 | FAIL §2.4 — FEATURES.md | FEATURES trilingual EN | `docs/FEATURES.md` | ✅ |
| 3 | FAIL §2.4 — FEATURES_RU.md | FEATURES trilingual RU | `docs/FEATURES_RU.md` | ✅ (TODO translate placeholder) |
| 4 | FAIL §2.4 — FEATURES_UK.md | FEATURES trilingual UK | `docs/FEATURES_UK.md` | ✅ (TODO translate placeholder) |

---

## 2. Manual Follow-ups

### Follow-up 1 — [FAIL §C12 — §3.5 UX] VrCheatsheetOverlayManager hand-tracking section

- **What the audit said:** `VrCheatsheetOverlayManager.buildContent()` has no hand-tracking section. No `vr_cheatsheet_section_hands` or related string resources in EN/RU/UK `strings.xml`.
- **Why not auto-fixed:** Requires adding new string resource content (design decision: exact wording of gesture descriptions) + modifying `buildContent()` method body — both prohibited by spec-fix constraints.
- **Suggested next action:** Add string resources `vr_cheatsheet_section_hands`, `vr_cheatsheet_hands_pinch`, `vr_cheatsheet_hands_double_pinch`, `vr_cheatsheet_hands_swipe`, `vr_cheatsheet_hands_ray` to `values/strings.xml` + `values-ru/strings.xml` + `values-uk/strings.xml`. Then append a hands section to `VrCheatsheetOverlayManager.buildContent()` after the mouse section.

### Follow-up 2 — [WARN §2.5 — Catalog] VrHandRayManager + XrInputSource not in catalog

- **What the audit said:** `VrHandRayManager` (`vr/ui/VrHandRayManager.kt`) and `XrInputSource` (object in `vr/openxr/XrInputEventType.kt`) missing from `dev/CATALOG/app_v2.jsonl`.
- **Why not auto-fixed:** `scan.ps1` does not scan `src/vr/java` (only `src/main/java`), confirmed by 0 hits post-run. Requires `set.ps1` manual entry or scan.ps1 scope fix.
- **Suggested next action:**
  ```powershell
  pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "com/sza/fastmediasorter/vr/ui/VrHandRayManager.kt" -Class "VrHandRayManager" -Role "Layer E pointer bridge: translates OpenXR hand-tracking NDC ray coords into Android MotionEvents for VR overlay hit-testing" -Status "new"
  ```
  And add `XrInputSource` object entry manually or extend scan.ps1 to include `src/vr/`.

---

## 3. PRE-RESOLVED

_(none)_

---

## 4. Next Steps

1. Address Follow-up 1 (cheatsheet strings + `buildContent()` extension).
2. Address Follow-up 2 (catalog entries via `set.ps1`).
3. Translate RU/UK FEATURES placeholders (search for `TODO translate: VR hand tracking`).
4. Run `/spec-check vr-hand-tracking` to confirm Verified.
