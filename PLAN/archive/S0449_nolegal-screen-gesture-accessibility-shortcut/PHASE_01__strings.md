# Phase 01 - Strings

**Strategic spec:** [`../S0449_nolegal-screen-gesture-accessibility-shortcut.md`](../S0449_nolegal-screen-gesture-accessibility-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Add the trilingual strings for the short instruction and the accessibility-shortcut button; no layout or code references yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +2 |

---

## Steps

### Step 01.1 - Add instruction and button strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two new string keys in lockstep across all three locales using one parity-enforced call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key setting_screenshot_accessibility_shortcut_hint -En "..." -Ru "..." -Uk "..."` and a second call for `-Key setting_screenshot_accessibility_shortcut_button`.
> `..._hint`: one short sentence stating that the screenshot gesture relies on the accessibility service and may need to be turned on again. `..._button`: a short action label for opening accessibility settings (e.g. "Open accessibility settings").
> Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (instruction/CTA message formula) and §6 (tone checklist): plain, calm, no blame, no exclamation. Keep the hint distinct from the longer educational dialog text in `setting_gesture_overlay_help_message` - do not restate the full tap sequence.

**Verification:**

- `Grep` - `setting_screenshot_accessibility_shortcut_hint` matches exactly once in each of the three `strings.xml` files.
- `Grep` - `setting_screenshot_accessibility_shortcut_button` matches exactly once in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_screenshot_accessibility_shortcut"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 4/4 PASS. Added `setting_screenshot_accessibility_shortcut_hint` + `..._button` to EN/RU/UK strings.xml (6 occurrences). Parity exit 0.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "setting_screenshot_accessibility_shortcut"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Two string keys (`setting_screenshot_accessibility_shortcut_hint`, `setting_screenshot_accessibility_shortcut_button`) exist in all three locales and are ready to be referenced by the layouts in Phase 02.

---

## Rollback Plan

Remove the two added keys from all three `strings.xml` files - no code references them yet.
