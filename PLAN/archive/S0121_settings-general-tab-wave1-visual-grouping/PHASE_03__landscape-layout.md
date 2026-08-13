# Phase 03 — Reorganize Landscape Layout

**Strategic spec:** [`../S0121_settings-general-tab-wave1-visual-grouping.md`](../S0121_settings-general-tab-wave1-visual-grouping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** —
**Depends on:** Phase 02
**Blocks:** Phase 04

---

## Objective

Mirror all M4 + M5 structural changes from Phase 02 into the landscape layout counterpart. The landscape layout uses `ScrollView` root and slightly different padding/margin dimens (`@dimen/settings_margin_standard` vs `@dimen/margin_small`) but the same section card pattern.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 550 LOC after edit |

> File is currently ~446 LOC. Budget allows up to ~100 LOC net increase.

---

## Steps

### Step 3.1 — M4a: Network Actions sub-section (landscape)

**File:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

**Depends on:** Phase 02 done

**Action:** Apply the same `headerNetworkActions` + `containerNetworkActions` wrapping as Phase 02 Step 2.1, adapted for landscape structure. In the landscape layout, `layoutSyncControls` is inside `containerSync` which is a horizontal `LinearLayout`. The `btnSyncNow` is inside `layoutSyncControls` (right weight column). Place `headerNetworkActions` above `containerSync` and wrap both in `containerNetworkActions`.

**Verification:**
- `Grep` — `headerNetworkActions` found in `layout-land/fragment_settings_general.xml`.
- `Grep` — `btnSyncNow` still present in same file.

**Status:** —

---

### Step 3.2 — M4b: Cache Management sub-section (landscape)

**File:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

**Depends on:** Step 3.1

**Action:** Apply the same `headerCacheManagement` + `containerCacheManagement` wrapping as Phase 02 Step 2.2, adapted for landscape. Locate `btnClearStreamingCache` and `containerCache` in the landscape `containerSystem`. Add header above `btnClearStreamingCache` and wrap both in container.

**Verification:**
- `Grep` — `headerCacheManagement` found in `layout-land/fragment_settings_general.xml`.
- `Grep` — `btnClearStreamingCache` still present in same file.
- `Grep` — `btnAutoCalculateCache` still present in same file.

**Status:** —

---

### Step 3.3 — M4c: Settings Data sub-section (landscape)

**File:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

**Depends on:** Step 3.2

**Action:** Apply the same `headerSettingsData` + `containerSettingsData` wrapping as Phase 02 Step 2.3, adapted for landscape `containerAppData`.

**Verification:**
- `Grep` — `headerSettingsData` found in `layout-land/fragment_settings_general.xml`.
- `Grep` — `btnExportSettings` still present in same file.
- `Grep` — `btnResetSettings` still present in same file.

**Status:** —

---

### Step 3.4 — M4d: Cloud Backup sub-section (landscape, standard only)

**File:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

**Depends on:** Step 3.3

**Action:** Apply the same `headerCloudBackup` wrapping as Phase 02 Step 2.4, adapted for landscape `containerAppData`. Preserve BuildConfig gate.

**Verification:**
- `Grep` — `headerCloudBackup` found in `layout-land/fragment_settings_general.xml`.
- `Grep` — `btnBackup` still present in a gated container.

**Status:** —

---

### Step 3.5 — M5: About section card (landscape)

**File:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

**Depends on:** Step 3.4

**Action:** The landscape `containerDocLinks` is a `LinearLayout` (not ConstraintLayout + Flow). Wrap it in an ABOUT `MaterialCardView` matching the landscape card pattern (`android:layout_marginHorizontal="@dimen/settings_margin_standard"`). Add `headerAbout` and `containerAbout` following the same structure as Phase 02 Step 2.5. Place the card between DEBUG section and Version Info row.

**Verification:**
- `Grep` — `headerAbout` found in `layout-land/fragment_settings_general.xml`.
- `Grep` — `containerAbout` found in same file.
- `Grep` — `btnUserGuide` still present in same file.
- `Grep` — `btnOpenWelcome` still present in same file.

**Status:** —

---

## Phase Done Criteria

- [ ] All five Step 3.* above are done.
- [ ] `Grep` for `headerNetworkActions`, `headerCacheManagement`, `headerSettingsData`, `headerCloudBackup`, `headerAbout` all match in `layout-land/fragment_settings_general.xml`.
- [ ] All 16 button IDs verified present in landscape layout.
- [ ] Dev log entry added.
