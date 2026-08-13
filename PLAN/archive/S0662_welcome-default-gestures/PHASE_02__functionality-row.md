# Phase 02 - Functionality Row (strings + layout)

**Strategic spec:** [`../S0662_welcome-default-gestures.md`](../S0662_welcome-default-gestures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Add the "Включить жесты" toggle row (trilingual strings + portrait and landscape layout) to the Welcome functionality page; no behavior wired yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/page_welcome_functionality.xml` | Modified | ≤ 270 |
| `app_v2/src/main/res/layout-land/page_welcome_functionality.xml` | Modified | ≤ 320 |

> Landscape variant exists - both layouts must add `rowGestures` (Rule 11). New row uses the existing `SettingsToggleRow` widget (already focusable / D-pad reachable / non-color state), satisfying the Accessibility constraint with no extra work.

---

## Steps

### Step 02.1 - Add trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys in lockstep with `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one call per key, parity-enforced across EN/RU/UK):
> - `welcome_func_gestures` - EN `Enable gestures`, RU `Включить жесты`, UK `Увімкнути жести`.
> - `welcome_func_gestures_summary` - EN `Swipe from the left edge: menu, screenshot, silent screenshot`, RU `Свайпы от левого края: меню, скриншот, тихий скриншот`, UK `Свайпи від лівого краю: меню, знімок, тихий знімок`.
>
> Verify the copy against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist) before committing. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_func_gestures"` and fix any gap (exit 1 = stop).

**Verification:**

- `Grep` - `welcome_func_gestures"` present in all three `strings.xml`.
- `Grep` - `welcome_func_gestures_summary"` present in all three `strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "welcome_func_gestures"` exits 0.
- Strings pass the COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Added `welcome_func_gestures` + `welcome_func_gestures_summary` to strings_setup.xml (EN/RU/UK) via set-android-string.ps1 add; check_strings_localized exit 0; Cyrillic verified via Grep.

---

### Step 02.2 - Add rowGestures to the portrait layout

**Files:** `app_v2/src/main/res/layout/page_welcome_functionality.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` with `android:id="@+id/rowGestures"`, `app:str_title="@string/welcome_func_gestures"`, `app:str_subtitle="@string/welcome_func_gestures_summary"`, `match_parent` width / `wrap_content` height, inside the toggle `LinearLayout` after `rowStatistics` and before `btnElements`. No hardcoded hex colors.

**Verification:**

- `Grep` - `@+id/rowGestures` matches exactly once in `layout/page_welcome_functionality.xml`.
- `Grep` - `@string/welcome_func_gestures` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Added `rowGestures` SettingsToggleRow after rowStatistics, before btnElements, in layout/page_welcome_functionality.xml.

---

### Step 02.3 - Add rowGestures to the landscape layout (parity)

**Files:** `app_v2/src/main/res/layout-land/page_welcome_functionality.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add the identical `SettingsToggleRow` `rowGestures` (same id, same `str_title` / `str_subtitle`) to `layout-land/page_welcome_functionality.xml` at the matching position in the toggle stack, keeping all ids in sync with the portrait variant.

**Verification:**

- `Grep` - `@+id/rowGestures` matches exactly once in `layout-land/page_welcome_functionality.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS. Added matching `rowGestures` row to layout-land variant (id parity with portrait).

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Resources compile - `.\a.ps1 fr` (BUILD SUCCESSFUL).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [~] Dev log entry - batched in Phase 04 finalization.

---

## Handoff Notes to Next Phase

The generated `PageWelcomeFunctionalityBinding.rowGestures` field is now available for Phase 03 to bind behavior. The row currently renders inert (no listener, always visible) until Phase 03 gates and wires it.

---

## Rollback Plan

Revert the phase commit(s). Removing the row from both layouts and the two string keys fully reverts; no code depends on the row until Phase 03.
