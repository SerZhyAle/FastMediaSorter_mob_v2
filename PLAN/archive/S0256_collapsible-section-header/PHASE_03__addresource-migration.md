# Phase 03 — AddResource migration

**Strategic spec:** [`../S0256_collapsible-section-header.md`](../S0256_collapsible-section-header.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Replace the chevron-style collapsible headers in the AddResource SMB and SFTP forms (6 groups in total) with `CollapsibleSectionHeader`. This converts the rotating `ic_expand_more` ImageView pattern to the canonical `▼/▶` symbol-prefix style and removes the duplicated `setupCollapsibleSections` / `setupHeader` logic from the form manager.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] `temp/research/collapsible_groups_inventory.md` groups 22–27 understood.
- [ ] Existing prefs file `add_resource_ui_state` keys known (pattern: `add_<smb|sftp>_<port|land>_<id>`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_add_resource.xml` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified (only if it directly references the removed setup methods) | ≤ 100 of delta |

`activity_add_resource.xml` has no landscape counterpart — explicit note: landscape variant absent, not needed (the activity layout adapts via attributes).

---

## Steps

### Step 03.1 — Migrate the three SMB form groups

**Files:** `activity_add_resource.xml`, `AddResourceFormManager.kt`

**Prompt for developer:**

> In the SMB section of the layout (groups 22–24 of the inventory): replace each header row (`headerSmbConditions` + `ivSmbConditionsExpand`, `headerSmbMediaTypes` + `ivSmbMediaTypesExpand`, `headerSmbAdditional` + `ivSmbAdditionalExpand`) with a single `CollapsibleSectionHeader` instance:
>
> - `app:csh_title="@string/label_scanning_settings"` (or `label_media_types`, `label_additional_options`).
> - `app:csh_showHelp="false"` for now (TODO comment marking each as a candidate for the next content pass — strategic ADR-2 makes this trivial to add later).
>
> Keep the three sibling content containers (`contentSmbConditions`, `contentSmbMediaTypes`, `contentSmbAdditional`) intact — they remain the toggled targets.
>
> In `AddResourceFormManager.kt`: in `setupCollapsibleSections`, replace the per-section call to `setupHeader(headerView, expandIcon, contentView, sectionKey)` with the new listener pattern: read saved bool, call `header.setExpanded(saved, notify = false)`, attach `header.setOnExpandedChangeListener { expanded -> contentView.isVisible = expanded; prefs.edit().putBoolean(sectionKey, expanded).apply() }`. Drop the `setupHeader` helper, drop the chevron-rotation animator code if it was inline.
>
> Prefs file `add_resource_ui_state` and the orientation-suffix keys remain bit-for-bit identical (today's pattern `add_smb_<port|land>_<id>` is preserved; the orientation suffix is structurally redundant since AddResource has no landscape layout, but normalizing the keys is out of this spec's scope per Non-goals).

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count ≥ 3 in the SMB section of `activity_add_resource.xml`.
- `Grep` — `@+id/ivSmbConditionsExpand` not present in the layout (the chevron view is gone).
- `Grep` — `@+id/ivSmbMediaTypesExpand` not present in the layout.
- `Grep` — `@+id/ivSmbAdditionalExpand` not present in the layout.
- `Grep` — `setupHeader(` not called in `AddResourceFormManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count >= 3 in AddResource layout | actual: 3; expected: `ivSmbConditionsExpand` absent | actual: absent; expected: `ivSmbMediaTypesExpand` absent | actual: absent; expected: `ivSmbAdditionalExpand` absent | actual: absent; expected: `setupHeader(` absent in `AddResourceFormManager.kt` | actual: absent. Files: `app_v2/src/main/res/layout/activity_add_resource.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`. Dev log recorded.

---

### Step 03.2 — Migrate the three SFTP form groups

**Files:** `activity_add_resource.xml`, `AddResourceFormManager.kt`

**Prompt for developer:**

> Same pattern as Step 03.1 applied to SFTP groups (groups 25–27 of the inventory): `headerSftpConditions` + `ivSftpConditionsExpand`, `headerSftpMediaTypes` + `ivSftpMediaTypesExpand`, `headerSftpAdditional` + `ivSftpAdditionalExpand`. Same listener wiring. Prefs keys (`add_sftp_<port|land>_<id>`) preserved verbatim.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 6 in `activity_add_resource.xml` (3 SMB + 3 SFTP).
- `Grep` — `@+id/ivSftpConditionsExpand` not present in the layout.
- `Grep` — `@+id/ivSftpMediaTypesExpand` not present in the layout.
- `Grep` — `@+id/ivSftpAdditionalExpand` not present in the layout.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count 6 in AddResource layout | actual: 6; expected: `ivSftpConditionsExpand` absent | actual: absent; expected: `ivSftpMediaTypesExpand` absent | actual: absent; expected: `ivSftpAdditionalExpand` absent | actual: absent. Files: `app_v2/src/main/res/layout/activity_add_resource.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`. Dev log recorded.

---

### Step 03.3 — Catalog sync + dev log for Phase 03

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then `.\scripts\add_to_dev_log.ps1` once per file touched.

**Verification:**

- `Grep` — `S0256 Phase 03` count ≥ 2 in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `S0256 Phase 03` entries in `dev/CHANGELOG.md` >= 2 | actual: 5. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`. Catalog sync recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles.
- [ ] Manual smoke: open AddResource → SMB form, expand each section, close form, re-open → previously expanded sections restored. Same for SFTP.
- [ ] Dev log entries added.

---

## Handoff Notes to Next Phase

The chevron-style headers in the SMB/SFTP forms now use the canonical symbol-prefix indicator. Visually this is a noticeable change — release notes (Phase 07) must mention it.

---

## Rollback Plan

Revert the layout and the FormManager changes. No data migration; prefs keys are unchanged.
