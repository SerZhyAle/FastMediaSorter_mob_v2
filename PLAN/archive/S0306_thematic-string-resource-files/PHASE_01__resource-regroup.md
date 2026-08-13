# Phase 01 - Resource Regroup

**Strategic spec:** [`../S0306_thematic-string-resource-files.md`](../S0306_thematic-string-resource-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Move existing S-ticket string groups into domain-named Android resource files without changing resource keys or user-visible text.

---

## Prerequisites

- [x] Strategic §6.1 resolved: S0160 moves to resource operations.
- [x] Strategic §6.2 deferred outside S0306 implementation.
- [x] Working tree may contain unrelated owner changes; touch only files listed in this phase.
- [x] No layout files are edited.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_link_auth.xml` | New | ≤ 80 |
| `app_v2/src/main/res/values-ru/strings_link_auth.xml` | New | ≤ 80 |
| `app_v2/src/main/res/values-uk/strings_link_auth.xml` | New | ≤ 80 |
| `app_v2/src/main/res/values/strings_s0140.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0140.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0140.xml` | Deleted | 0 |
| `app_v2/src/main/res/values/strings_s0155.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0155.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0155.xml` | Deleted | 0 |
| `app_v2/src/main/res/values/strings_s0157.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0157.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0157.xml` | Deleted | 0 |
| `app_v2/src/main/res/values/strings_google_account.xml` | New | ≤ 120 |
| `app_v2/src/main/res/values-ru/strings_google_account.xml` | New | ≤ 120 |
| `app_v2/src/main/res/values-uk/strings_google_account.xml` | New | ≤ 120 |
| `app_v2/src/main/res/values/strings_s0200.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0200.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0200.xml` | Deleted | 0 |
| `app_v2/src/main/res/values/strings_s0294.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0294.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0294.xml` | Deleted | 0 |
| `app_v2/src/main/res/values/strings_vr.xml` | New | ≤ 40 |
| `app_v2/src/main/res/values-ru/strings_vr.xml` | New | ≤ 40 |
| `app_v2/src/main/res/values-uk/strings_vr.xml` | New | ≤ 40 |
| `app_v2/src/main/res/values/strings_s0292.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0292.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0292.xml` | Deleted | 0 |
| `app_v2/src/main/res/values/strings_resource_operations.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-ru/strings_resource_operations.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-uk/strings_resource_operations.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values/strings_s0160.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-ru/strings_s0160.xml` | Deleted | 0 |
| `app_v2/src/main/res/values-uk/strings_s0160.xml` | Deleted | 0 |

> File projected >500 lines after change → backup step required. No target file is projected above 500 lines.

---

## Steps

### Step 01.1 - Move link auth strings

**Files:** `strings_link_auth.xml`, `strings_s0140.xml`, `strings_s0155.xml`, `strings_s0157.xml` in EN/RU/UK resource folders
**Depends on:** - start of phase

**Prompt for developer:**

> Create `strings_link_auth.xml` in `values`, `values-ru`, and `values-uk`. Move all string declarations from S0140, S0155, and S0157 into the matching locale file without changing keys, values, or comments except for grouping comments. Delete the old S0140/S0155/S0157 resource files after the new files contain the same keys.

**Verification:**

- `PowerShell` - expected new files: 3 | actual: 3.
- `PowerShell` - expected old S0140/S0155/S0157 files remaining: 0 | actual: 0.
- `PowerShell` - expected `strings_link_auth.xml` key count per locale: 21 | actual: 21.
- `PowerShell` - expected XML-normalized diff between HEAD S0140/S0155/S0157 keys and current `strings_link_auth.xml`: 0 | actual: 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "s0140_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "s0155_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "s0157_"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 7/7 PASS. Files: `strings_link_auth.xml` created in EN/RU/UK; S0140/S0155/S0157 resource files deleted in EN/RU/UK. Dev log recorded.

---

### Step 01.2 - Move Google account strings

**Files:** `strings_google_account.xml`, `strings_s0200.xml`, `strings_s0294.xml` in EN/RU/UK resource folders
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `strings_google_account.xml` in `values`, `values-ru`, and `values-uk`. Move all string declarations from S0200 and S0294 into the matching locale file without changing keys, values, or comments except for grouping comments. Keep S0234-prefixed strings in this thematic file because they are currently inside S0200 and belong to Google Drive sign-in error surfacing. Delete the old S0200/S0294 resource files after the new files contain the same keys.

**Verification:**

- `PowerShell` - expected new files: 3 | actual: 3.
- `PowerShell` - expected old S0200/S0294 files remaining: 0 | actual: 0.
- `PowerShell` - expected `strings_google_account.xml` key count per locale: 33 | actual: 33.
- `PowerShell` - expected XML-normalized diff between HEAD S0200/S0294 keys and current `strings_google_account.xml`: 0 | actual: 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "s0200_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "s0234_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "s0294_"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 7/7 PASS. Files: `strings_google_account.xml` created in EN/RU/UK; S0200/S0294 resource files deleted in EN/RU/UK. Dev log recorded.

---

### Step 01.3 - Move VR strings

**Files:** `strings_vr.xml`, `strings_s0292.xml` in EN/RU/UK resource folders
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `strings_vr.xml` in `values`, `values-ru`, and `values-uk`. Move all string declarations from S0292 into the matching locale file without changing keys or values. Delete the old S0292 resource files after the new files contain the same keys.

**Verification:**

- `PowerShell` - expected new files: 3 | actual: 3.
- `PowerShell` - expected old S0292 files remaining: 0 | actual: 0.
- `PowerShell` - expected `strings_vr.xml` key count per locale: 8 | actual: 8.
- `PowerShell` - expected XML-normalized diff between HEAD S0292 keys and current `strings_vr.xml`: 0 | actual: 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "player_vr_"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 5/5 PASS. Files: `strings_vr.xml` created in EN/RU/UK; S0292 resource files deleted in EN/RU/UK. Dev log recorded.

---

### Step 01.4 - Move resource operations strings

**Files:** `strings_resource_operations.xml`, `strings_s0160.xml` in EN/RU/UK resource folders
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `strings_resource_operations.xml` in `values`, `values-ru`, and `values-uk`. Move all string declarations from S0160 into the matching locale file without changing keys, values, or comments except for the grouping comment. Delete the old S0160 resource files after the new files contain the same keys.

**Verification:**

- `PowerShell` - expected new files: 3 | actual: 3.
- `PowerShell` - expected old S0160 files remaining: 0 | actual: 0.
- `PowerShell` - expected `strings_resource_operations.xml` key count per locale: 4 | actual: 4.
- `PowerShell` - expected XML-normalized diff between HEAD S0160 keys and current `strings_resource_operations.xml`: 0 | actual: 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "resource_ops_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "setting_resource_ops_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "action_refresh_resource"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "resource_unavailable_name"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 8/8 PASS. Files: `strings_resource_operations.xml` created in EN/RU/UK; S0160 resource files deleted in EN/RU/UK. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` exit 0; APK `2.60.5301.753`.
- [x] `PowerShell` - expected remaining S-ticket resource files for S0140/S0155/S0157/S0160/S0200/S0292/S0294: 0 | actual: 0.
- [x] `PowerShell` - expected thematic resource files in EN/RU/UK: 12 | actual: 12.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

Resource keys and values remain stable while file grouping becomes domain-based.

---

## Rollback Plan

Revert phase commit(s) or restore deleted S-ticket XML files and remove the thematic XML files. No data migration or user-facing behavior changes.
