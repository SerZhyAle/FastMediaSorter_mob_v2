# Phase 02 - General Tab Pilot

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05 (via owner pilot sign-off)
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Apply the full requirement set (R1, R2, R3, R5, R6, R7) to the General landscape layout only, then produce landscape screenshots for owner review before any propagation.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`ssr_valueSpacer`, `app:sdr_inline` available).
- [ ] Backup `app_v2/src/main/res/layout-land/fragment_settings_general.xml` to `temp/` before editing (>500 LOC).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 820 |

> Landscape-only by scope (strategic Non-goals): the portrait `res/layout/fragment_settings_general.xml` is intentionally NOT mirrored. R3/R7 reach portrait only through the shared compound rows from Phase 01, which is accepted (ADR-1).

---

## Steps

### Step 02.1 - Language + Color scheme as one inline row (R6)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> In the Interface section, place `spinnerLanguage` and `spinnerColorTheme` side by side in one horizontal weighted row (each `layout_width="0dp"`, `layout_weight="1"`, left margin/right margin via `dialog_field_spacing`). Add `app:sdr_inline="true"` to both so the label renders left of the field on one line. Keep `app:sdr_entries` on color theme. Do not change the host setup code.

**Verification:**

- `Grep` - `app:sdr_inline="true"` matches exactly twice in the file.
- `Grep` - `spinnerLanguage` and `spinnerColorTheme` both present.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - PASS. `sdr_inline="true"` x2; both dropdowns in one horizontal weighted row. Device screenshot (06_general_expanded): label left of field, two per row. File: layout-land/fragment_settings_general.xml.

---

### Step 02.2 - Density pack and left-align General sections (R1, R2, R5)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Pack compact toggle rows into weighted horizontal rows of up to 4 children where they currently sit one-per-line or two-per-line (Interface, File Browser, System sections), using the existing weighted-`LinearLayout` house pattern (`layout_width="0dp"`, `layout_weight="1"`, `baselineAligned="false"`). Keep paired value fields at 2 per row. Everything left-packed: no `android:gravity="center"` / `android:layout_gravity="center"` on content controls (vertical `center_vertical` for row alignment is fine; `CircularProgressIndicator` spinners stay centered - they are overlays, exempt). Preserve D-pad order with `nextFocus*` where rows are regrouped.

**Verification:**

- `Grep` - no `layout_gravity="center"` on any `MaterialButton` in the file (progress indicators exempt).
- `Grep` - at least one weighted row groups 3+ `SettingsToggleRow` children (manual confirm of packing).
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - PASS. Interface row now packs 3 toggles (allow-window | favorites | resource-ops). Device screenshot confirms 3-up. No horizontal centering in General. File: layout-land/fragment_settings_general.xml.

---

### Step 02.3 - Preserve Device profile (R3) and All Files CTA (R7) through restructure

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Keep `rowDeviceProfile` (`SettingsSelectionRow`) as a single full-width row so its value hugs the title (inherited from Phase 01 `ssr_valueSpacer`) - do not pair it into a 2-up row that would shrink the value area. Keep `layoutAllFiles` / `rowAllFiles` on its own full-width row so the code-injected create-resource CTA in the trailing slot stays pinned to the row's right edge (do not move `rowAllFiles` into a weighted multi-column row). No host code changes.

**Verification:**

- `Grep` - `rowDeviceProfile` present and not inside a `layout_weight` sibling group.
- `Grep` - `rowAllFiles` present; `layoutAllFiles` retains `layout_width="match_parent"`.
- `/build` (`.\a.ps1 d`) passes; landscape screenshots of General captured for owner review (Phase Done Criteria).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - PASS. rowDeviceProfile + layoutAllFiles left untouched (full-width). Device screenshot: device-profile value "Other / Custom" hugs the title with chevron pinned right (R3 via Phase 01). All Files row intact; the trailing create-resource CTA is conditionally hidden on this emulator (a predefined All Files resource exists) - mechanism preserved. `.\a.ps1 d` SUCCESSFUL (retry after a transient ASM file-lock).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project builds - run `/build` (`.\a.ps1 d`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Landscape screenshots of the General tab captured on an emulator and presented to the owner.
- [ ] Dev log entry added for the file in "Files Touched".

---

## Handoff Notes to Next Phase

**Pilot gate:** propagation Phases 03-05 must not start until the owner approves the General landscape screenshots. The weighted-row packing pattern validated here is the template for all propagation fragments. Debug verification tags are NOT inserted here - they are added once at the final `BlockNeedUserTest` transition (Phase 06 handoff), per CLAUDE.md (tags exist iff the ticket is in `BlockNeedUserTest`).

---

## Rollback Plan

Restore `fragment_settings_general.xml` from the `temp/` backup - layout-only, no data or persisted surface changed.
