# Phase 04 — Build Gate + Cleanup

**Strategic spec:** [`../S0121_settings-general-tab-wave1-visual-grouping.md`](../S0121_settings-general-tab-wave1-visual-grouping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** —
**Depends on:** Phase 02, Phase 03
**Blocks:** —

---

## Objective

Run a clean build to confirm no regressions from the layout and string changes.

---

## Steps

### Step 4.1 — Build standard debug

**Action:** Run `standard` debug build via `/build standard debug`.

**Verification:**
- Build exits 0 with no errors.
- No `R.id` resolution errors for any of the 16 button IDs.
- No missing string resource errors for any of the five new keys.

**Status:** —

---

### Step 4.2 — String locale audit

**Action:** Run locale parity check:

```powershell
pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_section_"
pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_about"
```

**Verification:**
- Both commands exit 0 (no missing keys in any locale).

**Status:** —

---

### Step 4.3 — Catalog sync

**Action:** Run catalog scan and render for `app_v2` module.

**Verification:**
- `scan.ps1` and `render.ps1` exit 0.

**Status:** —

---

## Phase Done Criteria

- [ ] Build passes (exit 0).
- [ ] String locale audit passes (exit 0) for both prefixes.
- [ ] Catalog synced.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
