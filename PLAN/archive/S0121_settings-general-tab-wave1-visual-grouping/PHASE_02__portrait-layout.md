# Phase 02 — Reorganize Portrait Layout

**Strategic spec:** [`../S0121_settings-general-tab-wave1-visual-grouping.md`](../S0121_settings-general-tab-wave1-visual-grouping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** —
**Depends on:** Phase 01
**Blocks:** Phase 04

---

## Objective

Apply M4 + M5 visual grouping changes to the portrait layout. All button IDs and behavior preserved. BuildConfig gate on `btnBackup`/`btnRestore` preserved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 600 LOC after edit |

> File is currently ~490 LOC. Budget allows up to ~110 LOC net increase for sub-section wrappers.

---

## Steps

### Step 2.1 — M4a: Network Actions sub-section (Network section)

**File:** `app_v2/src/main/res/layout/fragment_settings_general.xml`

**Depends on:** Phase 01 done

**Action:** Inside the SYSTEM SECTION card's `containerSystem`, locate `layoutSyncControls` which contains `btnSyncNow`. Add a sub-section header `TextView` immediately before `layoutSyncControls`. Wrap the sub-section header + `layoutSyncControls` + `tvSyncLastStatus` in a new `LinearLayout` container `containerNetworkActions`.

Sub-section header pattern (matches existing headers but smaller textSize):
```xml
<TextView
    android:id="@+id/headerNetworkActions"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/colorSurfaceVariant"
    android:clickable="true"
    android:focusable="true"
    android:padding="@dimen/settings_padding_vertical"
    android:text="@string/settings_section_network_actions"
    android:textSize="@dimen/settings_subsection_header_text_size"
    android:textStyle="bold" />
```

Note: `@dimen/settings_subsection_header_text_size` — check if this dimen exists; if not, use `12sp` inline.

Container wrapper `containerNetworkActions`:
```xml
<LinearLayout
    android:id="@+id/containerNetworkActions"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">
```

**Verification:**
- `Grep` — `headerNetworkActions` found in `layout/fragment_settings_general.xml`.
- `Grep` — `containerNetworkActions` found in same file.
- `Grep` — `btnSyncNow` still present in same file.

**Status:** —

---

### Step 2.2 — M4b: Cache Management sub-section (System/Cache section)

**File:** `app_v2/src/main/res/layout/fragment_settings_general.xml`

**Depends on:** Step 2.1

**Action:** In the SYSTEM SECTION card's `containerSystem`, locate `btnClearStreamingCache` (currently above `containerSync`) and `containerCache` (containing `btnAutoCalculateCache`, `btnClearCache`, `btnResetSmbConnections`).

- Add a sub-section header `headerCacheManagement` immediately above `btnClearStreamingCache`.
- Wrap `headerCacheManagement` + `btnClearStreamingCache` + `containerCache` in a new `LinearLayout containerCacheManagement`.

**Verification:**
- `Grep` — `headerCacheManagement` found in `layout/fragment_settings_general.xml`.
- `Grep` — `containerCacheManagement` found in same file.
- `Grep` — `btnClearStreamingCache` still present in same file.
- `Grep` — `btnAutoCalculateCache` still present in same file.

**Status:** —

---

### Step 2.3 — M4c: Settings Data sub-section (App Data section) + Settings Reset sub-section (System section)

**File:** `app_v2/src/main/res/layout/fragment_settings_general.xml`

**Depends on:** Step 2.2

**Within-section only rule:** `btnExportSettings`/`btnImportSettings` stay in App Data section. `btnResetGeneralSection`/`btnResetSettings` (`containerGeneralActions`) stay in System section. No cross-section moves.

**Action A — App Data section:**
In `containerAppData`, locate the `ConstraintLayout` containing `btnExportSettings`/`btnImportSettings`.
- Add sub-section header `headerSettingsData` immediately before the Export/Import ConstraintLayout.
- Wrap `headerSettingsData` + Export/Import ConstraintLayout in a new `LinearLayout containerSettingsData`.

**Action B — System section:**
In `containerSystem`, locate `containerGeneralActions` containing `btnResetGeneralSection`/`btnResetSettings`.
- Add sub-section header `headerSettingsReset` immediately before `containerGeneralActions`.
- Wrap `headerSettingsReset` + `containerGeneralActions` in a new `LinearLayout containerSettingsResetGroup`.

**Verification:**
- `Grep` — `headerSettingsData` found in `layout/fragment_settings_general.xml`.
- `Grep` — `containerSettingsData` found in same file.
- `Grep` — `btnExportSettings` still present in same file.
- `Grep` — `btnResetSettings` still present in same file.

**Status:** —

---

### Step 2.4 — M4d: Cloud Backup sub-section (App Data section, standard only)

**File:** `app_v2/src/main/res/layout/fragment_settings_general.xml`

**Depends on:** Step 2.3

**Action:** In `containerAppData`, locate the divider + `btnBackup`/`btnRestore` cluster. Add sub-section header `headerCloudBackup` immediately before this cluster. The existing BuildConfig gate that controls visibility of this area must be preserved — verify the gate mechanism in the layout (likely a container with `tools:visibility` or direct BuildConfig field reference) and wrap `headerCloudBackup` inside the same gated container.

> If the current BuildConfig gate is applied to the `LinearLayout` wrapping `btnBackup`/`btnRestore`, then `headerCloudBackup` must be inside or sibling-but-equally-gated so it only appears in standard flavor. Do NOT make the header always-visible while the buttons are gated.

**Verification:**
- `Grep` — `headerCloudBackup` found in `layout/fragment_settings_general.xml`.
- `Grep` — `btnBackup` still present and inside a gated container.
- `Grep` — `btnRestore` still present and inside a gated container.

**Status:** —

---

### Step 2.5 — M5: About section card (Doc Links)

**File:** `app_v2/src/main/res/layout/fragment_settings_general.xml`

**Depends on:** Step 2.4

**Action:** The current `containerDocLinks` ConstraintLayout is outside any MaterialCardView. Wrap it in a new `MaterialCardView` matching the style of existing section cards. Add a `TextView headerAbout` header inside the card, followed by `containerAbout` LinearLayout containing all five buttons.

Replace current `containerDocLinks` ConstraintLayout + `flowDocLinks` Flow widget with a plain `LinearLayout` wrapped in Flow if needed, or a `FlexboxLayout` / direct horizontal `LinearLayout`. The existing `app:constraint_referenced_ids` + `Flow` setup may be kept inside the card's content layout.

The new card:
```xml
<!-- ABOUT SECTION -->
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/margin_small"
    app:cardCornerRadius="@dimen/card_corner_radius"
    app:cardElevation="2dp"
    app:contentPadding="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/headerAbout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="?attr/colorSurfaceVariant"
            android:clickable="true"
            android:focusable="true"
            android:padding="@dimen/settings_padding_vertical"
            android:text="@string/settings_category_about"
            android:textSize="@dimen/settings_group_header_text_size"
            android:textStyle="bold" />

        <LinearLayout
            android:id="@+id/containerAbout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="@dimen/margin_small"
            android:paddingEnd="@dimen/margin_small"
            android:paddingBottom="@dimen/margin_small">

            <!-- five buttons here — containerDocLinks content migrated -->

        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

Place the ABOUT card between the DEBUG section card and the Version Info row. Keep `containerDocLinks` id on the outer ConstraintLayout or LinearLayout that holds the Flow/buttons so existing Kotlin code binding `containerDocLinks` still resolves. Alternatively, rename the outer container to `containerAboutButtons` and check if `containerDocLinks` is referenced in Kotlin before removing.

> Before Step 2.5: grep for `containerDocLinks` in all `.kt` files to confirm if it needs to be preserved.

**Verification:**
- `Grep` — `headerAbout` found in `layout/fragment_settings_general.xml`.
- `Grep` — `containerAbout` found in same file.
- `Grep` — `btnUserGuide` still present in same file.
- `Grep` — `btnOpenSourceLicenses` still present in same file.
- `Grep` — `btnOpenWelcome` still present in same file.

**Status:** —

---

## Phase Done Criteria

- [ ] All five Step 2.* above are done.
- [ ] `Grep` for `headerNetworkActions`, `headerCacheManagement`, `headerSettingsData`, `headerCloudBackup`, `headerAbout` all match in `layout/fragment_settings_general.xml`.
- [ ] `Grep` for `btnSyncNow`, `btnAutoCalculateCache`, `btnClearCache`, `btnClearStreamingCache`, `btnResetSmbConnections`, `btnExportSettings`, `btnImportSettings`, `btnResetSettings`, `btnResetGeneralSection`, `btnBackup`, `btnRestore`, `btnUserGuide`, `btnHowToGuides`, `btnOpenWelcome`, `btnPrivacyPolicy`, `btnOpenSourceLicenses` all still present in the file.
- [ ] `Grep` for `BuildConfig` or flavor gate near `btnBackup` — gate preserved.
- [ ] Dev log entry added.
